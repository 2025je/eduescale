import json
import logging
import os
import tempfile
import time
from pathlib import Path
from typing import Protocol, cast

from scrapy.crawler import Crawler
from scrapy.exceptions import DropItem
from sqlalchemy import insert
from sqlalchemy.engine import Connection, Engine, Transaction

from eduescale.application.export_policy import android_data_path
from eduescale.infrastructure.exports.eduescale import export_eduescale
from eduescale.infrastructure.exports.excel import export_excel
from eduescale.infrastructure.exports.files import replace_export_pair
from eduescale.infrastructure.persistence.offline_database import (
    create_database_engine,
    database_metadata,
    metadata,
    record_search_keys,
    records,
)

LOGGER = logging.getLogger(__name__)

FIELDS = (
    "nombre",
    "direccion",
    "localidad",
    "cp",
    "turno",
    "alumnos",
    "secciones",
    "longitud",
    "latitud",
)


class _SchoolSpiderState(Protocol):
    output: str | Path
    count_received: bool
    items_received: int
    expected_total: int
    unique_items_received: int
    pages_received: int
    failed_pages: dict[tuple[int, int], str]
    pages_retried: int
    resumed_pages: int
    started_monotonic: float


class ExcelPipeline:
    crawler: Crawler

    @classmethod
    def from_crawler(cls, crawler):
        pipeline = cls()
        pipeline.crawler = crawler
        return pipeline

    def open_spider(self):
        self.output_path = Path(self._school_spider().output)
        self.android_output_path = android_data_path(self.output_path)
        self.output_path.parent.mkdir(parents=True, exist_ok=True)
        self._temporary_output_path: Path | None = None
        self._temporary_android_path: Path | None = None
        with tempfile.NamedTemporaryFile(
            prefix="eduescale-",
            suffix=".sqlite3",
            dir=self.output_path.parent,
            delete=False,
        ) as spool_file:
            spool_path = Path(spool_file.name)
            self._spool_path: Path | None = spool_path
        self._spool_engine: Engine | None = create_database_engine(spool_path)
        metadata.create_all(self._spool_engine)
        spool = self._spool_engine.connect()
        self._spool: Connection | None = spool
        self._spool_transaction: Transaction | None = spool.begin()
        self.row_count = 0

    def process_item(self, item):
        spool = self._spool
        if spool is None:
            raise RuntimeError("La base temporal de la extracción no está abierta.")
        try:
            for field in FIELDS:
                item[field]
        except KeyError as error:
            raise DropItem(f"Falta el campo requerido para Excel: {error}") from error
        region = item.get("region") or "Departamento no identificado"
        province = item.get("provincia") or "Provincia no identificada"
        district = item.get("distrito") or "Distrito no identificado"
        spool.execute(
            insert(records).values(
                region=region,
                province=province,
                district=district,
                payload=json.dumps(item, ensure_ascii=False),
                **record_search_keys(region, province, district, item),
            )
        )
        self.row_count += 1
        return item

    def close_spider(self):
        spider = self._school_spider()
        spool = self._spool
        spool_path = self._spool_path
        try:
            if spool is None or spool_path is None:
                raise RuntimeError("La base temporal de la extracción no está abierta.")
            if not spider.count_received:
                raise RuntimeError(
                    "No se pudo obtener el total de resultados desde ESCALE."
                )
            if (
                spider.items_received != spider.expected_total
                or self.row_count != spider.unique_items_received
            ):
                message = (
                    f"Extracción incompleta: se esperaban {spider.expected_total} "
                    f"resultados y se recibieron {spider.items_received}; "
                    f"se guardaron {self.row_count} colegios únicos; "
                    f"páginas completas: {spider.pages_received}."
                )
                if spider.failed_pages:
                    details = "; ".join(
                        f"filtro {filter_index + 1}, offset {offset}: {reason}"
                        for (filter_index, offset), reason in sorted(
                            spider.failed_pages.items()
                        )
                    )
                    message = f"{message} Páginas fallidas: {details}"
                raise RuntimeError(message)

            if self.output_path.suffix.lower() == ".sqlite3":
                spool.execute(
                    insert(database_metadata),
                    [
                        {"key": "schema_version", "value": "3"},
                        {
                            "key": "download_duration_seconds",
                            "value": str(
                                max(
                                    0,
                                    int(time.monotonic() - spider.started_monotonic),
                                )
                            ),
                        },
                        {
                            "key": "pages_retried",
                            "value": str(spider.pages_retried),
                        },
                        {
                            "key": "resumed_pages",
                            "value": str(spider.resumed_pages),
                        },
                        {
                            "key": "failed_pages",
                            "value": str(len(spider.failed_pages)),
                        },
                        {
                            "key": "verified_pages",
                            "value": str(spider.pages_received),
                        },
                    ],
                )
            transaction = self._spool_transaction
            if transaction is None:
                raise RuntimeError("La transacción de la extracción no está abierta.")
            transaction.commit()
            self._spool_transaction = None
            if self.output_path.suffix.lower() == ".sqlite3":
                self._close_spool()
                os.replace(spool_path, self.output_path)
                self._spool_path = None
                return

            self._temporary_output_path = self._create_temporary_path(".xlsx")
            self._temporary_android_path = self._create_temporary_path(".eduescale")
            export_excel(spool_path, self._temporary_output_path)
            export_eduescale(spool_path, self._temporary_android_path)
            try:
                cleanup_warning = replace_export_pair(
                    self._temporary_output_path,
                    self.output_path,
                    self._temporary_android_path,
                    self.android_output_path,
                )
            except PermissionError as error:
                raise RuntimeError(
                    "No se pudieron reemplazar los archivos de exportación. "
                    "Cierra el archivo de Excel si está abierto y vuelve a intentar."
                ) from error
            if cleanup_warning:
                LOGGER.warning("%s", cleanup_warning)
        finally:
            self._close_spool()
            if self._spool_path is not None:
                self._spool_path.unlink(missing_ok=True)
            if self._temporary_output_path is not None:
                self._temporary_output_path.unlink(missing_ok=True)
            if self._temporary_android_path is not None:
                self._temporary_android_path.unlink(missing_ok=True)

    def _create_temporary_path(self, suffix: str) -> Path:
        with tempfile.NamedTemporaryFile(
            prefix=".eduescale-export-",
            suffix=suffix,
            dir=self.output_path.parent,
            delete=False,
        ) as export_file:
            return Path(export_file.name)

    def _school_spider(self) -> _SchoolSpiderState:
        spider = self.crawler.spider
        if spider is None:
            raise RuntimeError("La extracción no tiene un spider activo.")
        return cast(_SchoolSpiderState, spider)

    def _close_spool(self):
        if self._spool is not None:
            self._spool.close()
            self._spool = None
        if self._spool_engine is not None:
            self._spool_engine.dispose()
            self._spool_engine = None
