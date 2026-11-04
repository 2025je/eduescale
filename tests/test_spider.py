import json

import scrapy
from scrapy.http import Response, TextResponse

from eduescale.spiders.schools import SchoolsSpider


class TestSchoolsSpiderPage:
    def test_removes_identical_filter_sets_before_querying(self):
        spider = SchoolsSpider(
            output="unused.xlsx",
            filters_json=json.dumps(
                [
                    {
                        "region_code": "01",
                        "management": "publica,privada",
                        "level": "inicial,primaria",
                    },
                    {
                        "region_code": "01",
                        "management": "privada,publica",
                        "level": "primaria,inicial",
                    },
                ]
            ),
        )

        assert len(spider.filters) == 1

    def test_waits_for_all_counts_then_schedules_each_filter(self):
        spider = SchoolsSpider(
            output="unused.xlsx",
            filters_json=json.dumps(
                [
                    {
                        "region_code": "01",
                        "management": "publica",
                        "level": "inicial",
                    },
                    {
                        "region_code": "02",
                        "management": "privada",
                        "level": "primaria",
                    },
                ]
            ),
        )

        first_request = scrapy.Request(
            "https://escale.minedu.gob.pe/padron/rest/instituciones/cuenta",
            meta={"filter_index": 0},
        )
        second_request = scrapy.Request(
            "https://escale.minedu.gob.pe/padron/rest/instituciones/cuenta",
            meta={"filter_index": 1},
        )
        first_response = TextResponse(
            first_request.url, request=first_request, body=b"1", encoding="utf-8"
        )
        second_response = TextResponse(
            second_request.url, request=second_request, body=b"2", encoding="utf-8"
        )

        assert list(spider.parse_count(first_response)) == []
        page_requests = list(spider.parse_count(second_response))

        assert spider.expected_total == 3
        assert len(page_requests) == 2
        assert "ubigeo=01" in page_requests[0].url
        assert "ubigeo=02" in page_requests[1].url
        assert [request.meta["filter_total"] for request in page_requests] == [1, 2]

    def test_short_page_is_retried_without_yielding_partial_records(self):
        spider = SchoolsSpider(output="unused.xlsx")
        spider.expected_total = 100
        spider.filter_totals[0] = 100
        request = scrapy.Request(
            "https://escale.minedu.gob.pe/padron/rest/instituciones?start=0",
            meta={
                "filter_index": 0,
                "filter_total": 100,
                "offset": 0,
                "validation_retry_times": 0,
            },
        )
        records = "<items><cenEdu>Colegio</cenEdu></items>" * 49
        response = Response(
            request.url,
            request=request,
            body=f"<instituciones>{records}</instituciones>".encode(),
        )

        output = list(spider.parse_page(response))

        assert len(output) == 1
        assert isinstance(output[0], scrapy.Request)
        assert output[0].dont_filter
        assert output[0].meta["validation_retry_times"] == 1
        assert spider.items_received == 0
        assert spider.pages_received == 0

    def test_deduplicates_records_across_filter_sets(self):
        spider = SchoolsSpider(
            output="unused.xlsx",
            filters_json=json.dumps([{}, {}]),
        )
        spider.expected_total = 2
        spider.filter_totals = [1, 1]
        xml = b"""<instituciones><items>
            <cenEdu>Colegio Ejemplo</cenEdu>
            <dirCen>Direccion</dirCen>
            <localidad>Localidad</localidad>
            <cenPob>Centro Poblado</cenPob>
            <turno><valor>Manana</valor></turno>
            <estadistica><talumno>10</talumno><tseccion>1</tseccion></estadistica>
            <nlongIE>-77</nlongIE><nlatIE>-12</nlatIE>
        </items></instituciones>"""

        outputs = []
        for filter_index in range(2):
            request = scrapy.Request(
                "https://escale.minedu.gob.pe/padron/rest/instituciones",
                meta={
                    "filter_index": filter_index,
                    "filter_total": 1,
                    "offset": 0,
                    "validation_retry_times": 0,
                },
            )
            response = Response(request.url, request=request, body=xml)
            outputs.extend(spider.parse_page(response))

        assert len(outputs) == 1
        assert spider.items_received == 2
        assert spider.unique_items_received == 1
