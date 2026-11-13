import json

import scrapy
from scrapy.http import Response, TextResponse

from eduescale.infrastructure.scrapy.spiders.schools import (
    RECORD_FIELDS,
    SchoolsSpider,
)


class TestSchoolsSpiderPage:
    def test_replays_checkpoint_pages_before_requesting_missing_pages(self, tmp_path):
        checkpoint_dir = tmp_path / "download-checkpoint"
        count_url = "https://escale.minedu.gob.pe/padron/rest/instituciones/cuenta"
        page_body = b"""<instituciones><items>
            <cenEdu>Colegio reanudado</cenEdu>
            <dirCen>Direccion</dirCen><localidad>Localidad</localidad>
            <cenPob>Centro Poblado</cenPob>
            <turno><valor>Manana</valor></turno>
            <estadistica><talumno>10</talumno><tseccion>1</tseccion></estadistica>
            <nlongIE>-77</nlongIE><nlatIE>-12</nlatIE>
            <nivelModalidad><idCodigo>B0</idCodigo><valor>Primaria</valor></nivelModalidad>
        </items></instituciones>"""

        first_spider = SchoolsSpider(
            output=str(tmp_path / "offline.sqlite3"),
            checkpoint_dir=str(checkpoint_dir),
            checkpoint_fingerprint="full-database",
        )
        count_request = scrapy.Request(count_url, meta={"filter_index": 0})
        count_response = TextResponse(
            count_url,
            request=count_request,
            body=b"1",
            encoding="utf-8",
        )
        page_request = next(iter(first_spider.parse_count(count_response)))
        first_page = Response(page_request.url, request=page_request, body=page_body)

        assert len(list(first_spider.parse_page(first_page))) == 1
        first_spider.closed("finished")

        resumed_spider = SchoolsSpider(
            output=str(tmp_path / "offline.sqlite3"),
            checkpoint_dir=str(checkpoint_dir),
            checkpoint_fingerprint="full-database",
        )
        resumed_output = list(resumed_spider.parse_count(count_response))

        assert len(resumed_output) == 1
        assert isinstance(resumed_output[0], dict)
        assert resumed_output[0]["nombre"] == "Colegio reanudado"
        assert resumed_spider.resumed_pages == 1
        assert resumed_spider.pages_received == 1
        assert resumed_spider.items_received == 1
        resumed_spider.closed("finished")

    def test_removes_identical_filter_sets_before_querying(self):
        spider = SchoolsSpider(
            output="unused.xlsx",
            filters_json=json.dumps(
                [
                    {
                        "region_code": "01",
                        "management": "publica,privada",
                        "level": "primaria,cetpro",
                    },
                    {
                        "region_code": "01",
                        "management": "privada,publica",
                        "level": "cetpro,primaria",
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
                        "level": "primaria",
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

    def test_accepts_source_records_with_unsupported_levels_in_full_download(self):
        spider = SchoolsSpider(output="unused.sqlite3")
        spider.expected_total = 2
        request = scrapy.Request(
            "https://escale.minedu.gob.pe/padron/rest/instituciones?start=0",
            meta={
                "filter_index": 0,
                "filter_total": 2,
                "offset": 0,
                "validation_retry_times": 0,
            },
        )
        response = Response(
            request.url,
            request=request,
            body=(
                b"<instituciones>"
                b"<items><cenEdu>Primaria</cenEdu><nivelModalidad>"
                b"<idCodigo>B0</idCodigo><valor>Primaria</valor>"
                b"</nivelModalidad></items>"
                b"<items><cenEdu>Inicial</cenEdu><nivelModalidad>"
                b"<idCodigo>A2</idCodigo><valor>Inicial - Jardin</valor>"
                b"</nivelModalidad></items>"
                b"</instituciones>"
            ),
        )

        try:
            output = list(spider.parse_page(response))

            assert len(output) == 1
            assert spider.items_received == 2
            assert spider.pages_received == 1
            assert spider.unique_items_received == 1
        finally:
            spider.closed("finished")

    def test_invalid_numeric_field_retries_page(self):
        spider = SchoolsSpider(output="unused.xlsx")
        request = scrapy.Request(
            "https://escale.minedu.gob.pe/padron/rest/instituciones?start=0",
            meta={
                "filter_index": 0,
                "filter_total": 1,
                "offset": 0,
                "validation_retry_times": 0,
            },
        )
        response = Response(
            request.url,
            request=request,
            body=(
                b"<instituciones><items><cenEdu>Colegio</cenEdu>"
                b"<nivelModalidad><idCodigo>B0</idCodigo>"
                b"<valor>Primaria</valor></nivelModalidad>"
                b"<estadistica><talumno>no-numero</talumno></estadistica>"
                b"</items></instituciones>"
            ),
        )

        output = list(spider.parse_page(response))

        assert len(output) == 1
        assert isinstance(output[0], scrapy.Request)
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
            <nivelModalidad><idCodigo>B0</idCodigo><valor>Primaria</valor></nivelModalidad>
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

        try:
            assert len(outputs) == 1
            assert spider.items_received == 2
            assert spider.unique_items_received == 1
        finally:
            spider.closed("finished")

    def test_disk_deduplication_matches_tuple_equality_and_cleans_up(self):
        spider = SchoolsSpider(output="unused.xlsx")
        base_record = {
            "nombre": "Colegio Ñ",
            "direccion": "Av. Principal",
            "localidad": "Localidad",
            "cp": "Centro poblado",
            "turno": "Mañana",
            "alumnos": 10,
            "secciones": 1,
            "longitud": 0.0,
            "latitud": -0.0,
            "distrito": "Distrito",
            "provincia": "Provincia",
            "region": "Región",
            "gestion": "publica",
            "nivel": "secundaria",
            "codigo_local": "000123",
        }
        changed_records = []
        for field in RECORD_FIELDS:
            value = base_record[field]
            if isinstance(value, str):
                changed_value = f"{value} 2"
            elif isinstance(value, float):
                changed_value = value + 1.0
            else:
                changed_value = value + 1
            changed_records.append({**base_record, field: changed_value})
        records = [
            base_record,
            base_record.copy(),
            *changed_records,
            {**base_record, "longitud": -0.0},
            {**base_record, "alumnos": None},
        ]
        reference = set()
        expected = []
        for record in records:
            key = tuple(record[field] for field in RECORD_FIELDS)
            expected.append(key not in reference)
            reference.add(key)

        actual = [spider._is_new_record(record) for record in records]
        store_path = spider._seen_store_path

        try:
            assert (
                actual
                == expected
                == ([True, False] + [True] * len(RECORD_FIELDS) + [False, True])
            )
            assert store_path is not None
            assert store_path.exists()
        finally:
            spider.closed("finished")

        assert store_path is not None
        assert not store_path.exists()
