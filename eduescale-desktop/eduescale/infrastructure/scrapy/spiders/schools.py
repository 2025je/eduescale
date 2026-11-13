import math
import os
import sqlite3
import tempfile
import time
from pathlib import Path
from urllib.parse import urlencode
from xml.etree import ElementTree

import scrapy
from pydantic import TypeAdapter
from scrapy.exceptions import CloseSpider
from scrapy.http import Response

from eduescale.domain.schools import SchoolFilters
from eduescale.infrastructure.escale.client import (
    API_URL,
    build_search_params,
    parse_institutions_page,
)
from eduescale.infrastructure.persistence.download_checkpoints import (
    DownloadCheckpoint,
)

PAGE_SIZE = 50
EXCEL_MAX_DATA_ROWS = 1_048_574
PAGE_VALIDATION_RETRIES = 3
FILTER_SETS_ADAPTER = TypeAdapter(list[SchoolFilters])
RECORD_FIELDS = (
    "nombre",
    "direccion",
    "localidad",
    "cp",
    "turno",
    "alumnos",
    "secciones",
    "longitud",
    "latitud",
    "distrito",
    "provincia",
    "region",
    "gestion",
    "nivel",
    "codigo_local",
)


class SchoolsSpider(scrapy.Spider):
    name = "colegios"

    def __init__(
        self,
        output,
        region_code="",
        province_code="",
        district_code="",
        management="",
        level="",
        filters_json=None,
        checkpoint_dir=None,
        checkpoint_fingerprint=None,
        *args,
        **kwargs,
    ):
        super().__init__(*args, **kwargs)
        self.output = output
        self._database_output = Path(self.output).suffix.lower() == ".sqlite3"
        if filters_json is None:
            filter_sets = [
                SchoolFilters.model_validate(
                    {
                        "region_code": region_code,
                        "province_code": province_code,
                        "district_code": district_code,
                        "management": management,
                        "level": level,
                    }
                )
            ]
        else:
            filter_sets = FILTER_SETS_ADAPTER.validate_json(filters_json)
        self.filters = []
        seen_filter_sets = set()
        for filters in filter_sets:
            key = tuple(sorted(build_search_params(filters)))
            if key not in seen_filter_sets:
                seen_filter_sets.add(key)
                self.filters.append(filters)
        if not self.filters:
            raise ValueError("Se requiere al menos una combinación de filtros.")
        self.expected_total = 0
        self.items_received = 0
        self.unique_items_received = 0
        self.pages_received = 0
        self.failed_pages = {}
        self.count_received = False
        self.filter_totals = [0] * len(self.filters)
        self._count_responses = 0
        self.pages_retried = 0
        self.resumed_pages = 0
        self.started_monotonic = time.monotonic()
        self._checkpoint = (
            DownloadCheckpoint(Path(checkpoint_dir), checkpoint_fingerprint)
            if checkpoint_dir and checkpoint_fingerprint
            else None
        )
        self._seen_store: sqlite3.Connection | None = None
        self._seen_store_path: Path | None = None

    async def start(self):
        for filter_index, filters in enumerate(self.filters):
            params = urlencode(build_search_params(filters))
            yield scrapy.Request(
                f"{API_URL}/instituciones/cuenta?{params}",
                callback=self.parse_count,
                errback=self.count_request_failed,
                meta={
                    "filter_index": filter_index,
                    "handle_httpstatus_all": True,
                },
            )

    def count_request_failed(self, failure):
        self.logger.error(
            "No se pudo consultar el total de resultados del filtro %d: %s",
            failure.request.meta["filter_index"] + 1,
            failure.getErrorMessage(),
        )

    def parse_count(self, response):
        filter_index = response.meta["filter_index"]
        if response.status != 200:
            raise CloseSpider(
                f"ESCALE respondió con HTTP {response.status} al contar "
                f"resultados del filtro {filter_index + 1}."
            )
        try:
            filter_total = int(response.text.strip())
        except ValueError as error:
            raise CloseSpider(
                "ESCALE devolvió una cantidad de resultados no válida."
            ) from error
        if filter_total < 0:
            raise CloseSpider("ESCALE devolvió una cantidad de resultados no válida.")
        self.filter_totals[filter_index] = filter_total
        self._count_responses += 1
        if self._count_responses != len(self.filters):
            return

        self.expected_total = sum(self.filter_totals)
        self.count_received = True
        if self._checkpoint is not None and self._checkpoint.set_filter_totals(
            self.filter_totals
        ):
            self.logger.warning(
                "El total de resultados de ESCALE cambió; "
                "se reiniciará la descarga desde cero."
            )
        if (
            not self._database_output
            and len(self.filters) == 1
            and self.expected_total > EXCEL_MAX_DATA_ROWS
        ):
            raise CloseSpider(
                "Los resultados superan el límite de filas de una hoja de Excel."
            )

        self.logger.info("SCRAPE_TOTAL=%d", self.expected_total)
        for filter_index, (filters, filter_total) in enumerate(
            zip(self.filters, self.filter_totals)
        ):
            for offset in range(0, filter_total, PAGE_SIZE):
                params = urlencode(build_search_params(filters, offset))
                page_url = f"{API_URL}/instituciones?{params}"
                request = scrapy.Request(
                    page_url,
                    callback=self.parse_page,
                    errback=self.page_request_failed,
                    meta={
                        "filter_index": filter_index,
                        "filter_total": filter_total,
                        "offset": offset,
                        "validation_retry_times": 0,
                        "handle_httpstatus_all": True,
                    },
                )
                cached_body = (
                    self._checkpoint.load_page(filter_index, offset)
                    if self._checkpoint is not None
                    else None
                )
                if cached_body is None:
                    yield request
                    continue
                request.meta["from_checkpoint"] = True
                self.resumed_pages += 1
                cached_response = Response(
                    page_url,
                    status=200,
                    body=cached_body,
                    request=request,
                )
                yield from self.parse_page(cached_response)

    def page_request_failed(self, failure):
        self.pages_retried += int(failure.request.meta.get("retry_times", 0))
        offset = failure.request.meta["offset"]
        filter_index = failure.request.meta["filter_index"]
        reason = failure.getErrorMessage()
        self.failed_pages[(filter_index, offset)] = reason
        self._log_progress()
        self.logger.error(
            "Falló la página offset=%d del filtro %d después de reintentos: %s",
            offset,
            filter_index + 1,
            reason,
        )

    def parse_page(self, response):
        self.pages_retried += int(response.meta.get("retry_times", 0))
        filter_index = response.meta["filter_index"]
        offset = response.meta["offset"]
        if response.status != 200:
            self.failed_pages[(filter_index, offset)] = (
                f"ESCALE respondió con HTTP {response.status}."
            )
            self._log_progress()
            self.logger.error(
                "Falló la página offset=%d del filtro %d después de reintentos: HTTP %d",
                offset,
                filter_index + 1,
                response.status,
            )
            return
        try:
            source_item_count, records = parse_institutions_page(response.body)
        except (ElementTree.ParseError, ValueError) as error:
            self._remove_invalid_cached_page(response)
            retry = self._retry_or_fail_page(response, f"XML no válido: {error}")
            if retry is not None:
                yield retry
            return

        expected_page_size = min(
            PAGE_SIZE,
            response.meta["filter_total"] - offset,
        )
        if source_item_count != expected_page_size:
            self._remove_invalid_cached_page(response)
            retry = self._retry_or_fail_page(
                response,
                f"ESCALE entregó {source_item_count} registros; "
                f"se esperaban {expected_page_size}.",
            )
            if retry is not None:
                yield retry
            return

        if self._checkpoint is not None and not response.meta.get(
            "from_checkpoint", False
        ):
            self._checkpoint.save_page(filter_index, offset, response.body)
        self.pages_received += 1
        self.items_received += source_item_count
        for record in records:
            if not self._is_new_record(record):
                continue
            self.unique_items_received += 1
            if (
                not self._database_output
                and self.unique_items_received > EXCEL_MAX_DATA_ROWS
            ):
                raise CloseSpider(
                    "Los resultados únicos superan el límite de filas de una hoja de Excel."
                )
            yield record
        self._log_progress()

    def _is_new_record(self, record):
        key_values = tuple(record[field] for field in RECORD_FIELDS)
        normalized_values = []
        for value in key_values:
            if isinstance(value, float):
                if not math.isfinite(value):
                    raise ValueError("Los campos numéricos deben ser finitos.")
                value = 0.0 if value == 0.0 else value
            normalized_values.append(value)
        key = repr(tuple(normalized_values))

        if self._seen_store is None:
            descriptor, path = tempfile.mkstemp(
                prefix="eduescale-dedupe-",
                suffix=".sqlite3",
            )
            os.close(descriptor)
            self._seen_store_path = Path(path)
            self._seen_store = sqlite3.connect(path)
            self._seen_store.execute("PRAGMA journal_mode=OFF")
            self._seen_store.execute("PRAGMA synchronous=OFF")
            self._seen_store.execute("PRAGMA cache_size=-4096")
            self._seen_store.execute(
                """
                CREATE TABLE seen_records (
                    record_key TEXT PRIMARY KEY
                ) WITHOUT ROWID
                """
            )

        cursor = self._seen_store.execute(
            "INSERT OR IGNORE INTO seen_records (record_key) VALUES (?)",
            (key,),
        )
        return cursor.rowcount == 1

    def closed(self, reason: str):
        self.logger.debug("Closing deduplication store (reason: %s).", reason)
        if self._checkpoint is not None and self.failed_pages:
            self._checkpoint.record_failures(
                [
                    f"filtro {filter_index + 1}, offset {offset}: {failure}"
                    for (filter_index, offset), failure in sorted(
                        self.failed_pages.items()
                    )
                ]
            )
        try:
            if self._seen_store is not None:
                self._seen_store.close()
                self._seen_store = None
        finally:
            if self._seen_store_path is not None:
                self._seen_store_path.unlink(missing_ok=True)
                self._seen_store_path = None

    def _log_progress(self):
        self.logger.info(
            "SCRAPE_PROGRESS=%d/%d UNIQUE=%d PAGES=%d RESUMED=%d RETRIED=%d FAILED=%d",
            self.items_received,
            self.expected_total,
            self.unique_items_received,
            self.pages_received,
            self.resumed_pages,
            self.pages_retried,
            len(self.failed_pages),
        )

    def _remove_invalid_cached_page(self, response: Response) -> None:
        if self._checkpoint is not None and response.meta.get("from_checkpoint", False):
            self._checkpoint.remove_page(
                response.meta["filter_index"],
                response.meta["offset"],
            )

    def _retry_or_fail_page(self, response, reason):
        request = response.request.copy()
        request.meta.pop("from_checkpoint", None)
        retry_times = response.meta.get("validation_retry_times", 0)
        if retry_times < PAGE_VALIDATION_RETRIES:
            request.dont_filter = True
            request.meta["validation_retry_times"] = retry_times + 1
            self.pages_retried += 1
            if self._checkpoint is not None:
                self._checkpoint.record_retry()
            self.logger.warning(
                "Reintentando página offset=%d (%d/%d): %s",
                response.meta["offset"],
                retry_times + 1,
                PAGE_VALIDATION_RETRIES,
                reason,
            )
            return request

        offset = response.meta["offset"]
        filter_index = response.meta["filter_index"]
        self.failed_pages[(filter_index, offset)] = reason
        self._log_progress()
        self.logger.error(
            "Página offset=%d del filtro %d incompleta tras reintentos: %s",
            offset,
            filter_index + 1,
            reason,
        )
        return None
