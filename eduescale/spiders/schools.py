from urllib.parse import urlencode
from xml.etree import ElementTree

import scrapy
from scrapy.exceptions import CloseSpider

from eduescale.client import API_URL, SchoolFilters, build_search_params, parse_institutions


PAGE_SIZE = 50
EXCEL_MAX_DATA_ROWS = 1_048_575
PAGE_VALIDATION_RETRIES = 3


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
        *args,
        **kwargs,
    ):
        super().__init__(*args, **kwargs)
        self.output = output
        self.filters = SchoolFilters(
            region_code=region_code,
            province_code=province_code,
            district_code=district_code,
            management=management,
            level=level,
        )
        self.expected_total = 0
        self.items_received = 0
        self.pages_received = 0
        self.failed_pages = {}
        self.count_received = False

    async def start(self):
        params = urlencode(build_search_params(self.filters))
        yield scrapy.Request(
            f"{API_URL}/instituciones/cuenta?{params}",
            callback=self.parse_count,
            errback=self.count_request_failed,
            meta={"handle_httpstatus_all": True},
        )

    def count_request_failed(self, failure):
        self.logger.error(
            "No se pudo consultar el total de resultados de ESCALE: %s",
            failure.getErrorMessage(),
        )

    def parse_count(self, response):
        if response.status != 200:
            raise CloseSpider(f"ESCALE respondió con HTTP {response.status} al contar resultados.")
        try:
            self.expected_total = int(response.text.strip())
        except ValueError as error:
            raise CloseSpider("ESCALE devolvió una cantidad de resultados no válida.") from error
        self.count_received = True

        if self.expected_total > EXCEL_MAX_DATA_ROWS:
            raise CloseSpider("Los resultados superan el límite de filas de una hoja de Excel.")

        self.logger.info("SCRAPE_TOTAL=%d", self.expected_total)
        for offset in range(0, self.expected_total, PAGE_SIZE):
            params = urlencode(build_search_params(self.filters, offset))
            yield scrapy.Request(
                f"{API_URL}/instituciones?{params}",
                callback=self.parse_page,
                errback=self.page_request_failed,
                meta={
                    "offset": offset,
                    "validation_retry_times": 0,
                    "handle_httpstatus_all": True,
                },
            )

    def page_request_failed(self, failure):
        offset = failure.request.meta["offset"]
        reason = failure.getErrorMessage()
        self.failed_pages[offset] = reason
        self.logger.error("Falló la página offset=%d después de reintentos: %s", offset, reason)

    def parse_page(self, response):
        offset = response.meta["offset"]
        if response.status != 200:
            self.failed_pages[offset] = f"ESCALE respondió con HTTP {response.status}."
            self.logger.error(
                "Falló la página offset=%d después de reintentos: HTTP %d",
                offset,
                response.status,
            )
            return
        try:
            records = parse_institutions(response.body)
        except ElementTree.ParseError as error:
            retry = self._retry_or_fail_page(response, f"XML no válido: {error}")
            if retry is not None:
                yield retry
            return

        expected_page_size = min(PAGE_SIZE, self.expected_total - offset)
        if len(records) != expected_page_size:
            retry = self._retry_or_fail_page(
                response,
                f"ESCALE entregó {len(records)} registros; se esperaban {expected_page_size}.",
            )
            if retry is not None:
                yield retry
            return

        self.pages_received += 1
        self.items_received += len(records)
        self.logger.info(
            "SCRAPE_PROGRESS=%d/%d PAGES=%d",
            self.items_received,
            self.expected_total,
            self.pages_received,
        )
        yield from records

    def _retry_or_fail_page(self, response, reason):
        request = response.request.copy()
        retry_times = response.meta.get("validation_retry_times", 0)
        if retry_times < PAGE_VALIDATION_RETRIES:
            request.dont_filter = True
            request.meta["validation_retry_times"] = retry_times + 1
            self.logger.warning(
                "Reintentando página offset=%d (%d/%d): %s",
                response.meta["offset"],
                retry_times + 1,
                PAGE_VALIDATION_RETRIES,
                reason,
            )
            return request

        offset = response.meta["offset"]
        self.failed_pages[offset] = reason
        self.logger.error("Página offset=%d incompleta tras reintentos: %s", offset, reason)
        return None
