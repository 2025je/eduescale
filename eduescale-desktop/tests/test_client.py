import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest
from pydantic import ValidationError

from eduescale import client
from eduescale.client import (
    SchoolFilters,
    build_search_params,
    parse_institutions,
)


class _RetryTestHandler(BaseHTTPRequestHandler):
    attempts = 0
    user_agent = None

    def do_GET(self):
        type(self).attempts += 1
        type(self).user_agent = self.headers.get("User-Agent")
        if type(self).attempts == 1:
            self.send_response(503)
            self.send_header("Retry-After", "0")
            self.end_headers()
            return

        payload = (
            b"<regiones><region><idRegion>15</idRegion>"
            b"<nombreRegion>Lima</nombreRegion></region></regiones>"
        )
        self.send_response(200)
        self.send_header("Content-Type", "application/xml")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format, *args):
        pass


class TestDirectRequestRetries:
    def test_retries_temporary_http_errors_and_honors_retry_after(self, monkeypatch):
        _RetryTestHandler.attempts = 0
        _RetryTestHandler.user_agent = None
        server = ThreadingHTTPServer(("127.0.0.1", 0), _RetryTestHandler)
        server_thread = threading.Thread(target=server.serve_forever, daemon=True)
        server_thread.start()
        monkeypatch.setattr(
            client,
            "API_URL",
            f"http://127.0.0.1:{server.server_port}/padron/rest",
        )

        try:
            assert client.fetch_regions() == [("15", "Lima")]
            assert _RetryTestHandler.attempts == 2
            assert _RetryTestHandler.user_agent == "eduescale/0.1"
        finally:
            server.shutdown()
            server.server_close()
            server_thread.join()


class TestBuildSearchParams:
    def test_selects_most_specific_location_and_public_primary(self):
        params = build_search_params(
            SchoolFilters(
                region_code="15",
                province_code="1501",
                district_code="150101",
                management=("publica",),
                level=("primaria",),
            ),
            offset=50,
        )

        assert ("ubigeo", "150101") in params
        assert ("ubigeo", "15") not in params
        assert ("gestiones", "A4") in params
        assert ("niveles", "B0") in params
        assert ("estados", "1") in params
        assert ("start", "50") in params

    def test_private_secondary_filters(self):
        params = build_search_params(
            SchoolFilters(management=("privada",), level=("secundaria",))
        )

        assert [value for key, value in params if key == "gestiones"] == [
            "B1",
            "B2",
            "B3",
            "B4",
            "B5",
            "B6",
        ]
        assert [value for key, value in params if key == "niveles"] == ["F0"]

    def test_all_management_and_levels_omit_category_filters(self):
        params = build_search_params(SchoolFilters())

        assert not any(key in {"gestiones", "niveles"} for key, _ in params)

    def test_selected_management_and_multiple_levels_add_all_codes(self):
        params = build_search_params(
            SchoolFilters(
                management=("publica",),
                level=("primaria", "cetpro"),
            )
        )

        assert [value for key, value in params if key == "gestiones"] == [
            "A1",
            "A2",
            "A3",
            "A4",
        ]
        assert [value for key, value in params if key == "niveles"] == [
            "B0",
            "L0",
        ]

    def test_all_checkbox_values_send_all_selected_codes(self):
        params = build_search_params(
            SchoolFilters(
                management=("publica", "privada"),
                level=("primaria", "secundaria", "cetpro"),
            )
        )

        assert [value for key, value in params if key == "gestiones"] == [
            "A1",
            "A2",
            "A3",
            "A4",
            "B1",
            "B2",
            "B3",
            "B4",
            "B5",
            "B6",
        ]
        assert [value for key, value in params if key == "niveles"] == [
            "B0",
            "F0",
            "L0",
        ]

    def test_pydantic_normalizes_comma_separated_selections(self):
        filters = SchoolFilters.model_validate(
            {
                "management": "publica, privada,publica",
                "level": "primaria,cetpro",
            }
        )

        assert filters.management == ("publica", "privada")
        assert filters.level == ("primaria", "cetpro")

    def test_pydantic_rejects_unknown_filter_values(self):
        with pytest.raises(ValidationError):
            SchoolFilters(management=("mixta",))

        with pytest.raises(ValidationError):
            SchoolFilters(level=("inicial",))


class TestParseInstitutions:
    def test_reads_school_and_location_fields(self):
        xml = """<instituciones><items>
            <cenEdu>Colegio Ejemplo</cenEdu>
            <dirCen>Av. Principal 123</dirCen>
            <localidad>Localidad Ejemplo</localidad>
            <cenPob>Centro Poblado Ejemplo</cenPob>
            <turno><valor>Mañana</valor></turno>
            <estadistica><talumno>42</talumno><tseccion>3</tseccion></estadistica>
            <nlongIE>-77.25584</nlongIE><nlatIE>-5.773595</nlatIE>
            <gestion>Pública de gestión directa</gestion>
            <nivelModalidad>
              <idCodigo>B0</idCodigo><orden>0</orden><valor>Primaria</valor>
            </nivelModalidad>
            <codlocal>000123</codlocal>
            <distrito><nombreDistrito>Miraflores</nombreDistrito>
              <provincia><nombreProvincia>Lima</nombreProvincia>
                <region><nombreRegion>Lima</nombreRegion></region>
              </provincia>
            </distrito>
        </items></instituciones>""".encode()

        assert parse_institutions(xml) == [
            {
                "nombre": "Colegio Ejemplo",
                "direccion": "Av. Principal 123",
                "localidad": "Localidad Ejemplo",
                "cp": "Centro Poblado Ejemplo",
                "turno": "Mañana",
                "alumnos": 42,
                "secciones": 3,
                "longitud": -77.25584,
                "latitud": -5.773595,
                "distrito": "Miraflores",
                "provincia": "Lima",
                "region": "Lima",
                "gestion": "publica",
                "nivel": "primaria",
                "codigo_local": "000123",
            }
        ]

    @pytest.mark.parametrize(
        ("management", "modality", "expected_management", "expected_level"),
        [
            ("Pública", "B0Primaria", "publica", "primaria"),
            ("Pública", "F0Secundaria", "publica", "secundaria"),
            ("Pública", "Técnico Productiva", "publica", "cetpro"),
        ],
    )
    def test_classifies_school_management_and_level(
        self, management, modality, expected_management, expected_level
    ):
        xml = (
            "<instituciones><items>"
            f"<gestion>{management}</gestion>"
            f"<nivelModalidad>{modality}</nivelModalidad>"
            "</items></instituciones>"
        ).encode()

        record = parse_institutions(xml)[0]

        assert record["gestion"] == expected_management
        assert record["nivel"] == expected_level

    def test_reads_nested_level_and_code_from_escale_xml(self):
        xml = b"""<instituciones><items>
            <nivelModalidad>
              <idCodigo>B0</idCodigo><orden>0</orden><valor>Primaria</valor>
            </nivelModalidad>
        </items></instituciones>"""

        record = parse_institutions(xml)[0]

        assert record["nivel"] == "primaria"

    def test_classifies_level_by_code_when_value_is_missing(self):
        xml = b"""<instituciones><items>
            <nivelModalidad><idCodigo>F0</idCodigo></nivelModalidad>
        </items></instituciones>"""

        record = parse_institutions(xml)[0]

        assert record["nivel"] == "secundaria"

    def test_classifies_cetpro_by_escale_code(self):
        xml = """<instituciones><items>
            <nivelModalidad>
              <idCodigo>L0</idCodigo><valor>Técnico Productiva</valor>
            </nivelModalidad>
        </items></instituciones>""".encode("utf-8")

        record = parse_institutions(xml)[0]

        assert record["nivel"] == "cetpro"

    def test_omits_initial_level_records(self):
        xml = """<instituciones><items>
            <nivelModalidad>
              <idCodigo>A2</idCodigo><valor>Inicial - Jardín</valor>
            </nivelModalidad>
        </items></instituciones>""".encode("utf-8")

        assert parse_institutions(xml) == []

    @pytest.mark.parametrize("value", ["nan", "inf", "-inf"])
    def test_rejects_non_finite_coordinates(self, value):
        xml = (
            f"<instituciones><items><nlongIE>{value}</nlongIE></items></instituciones>"
        ).encode()

        with pytest.raises(ValueError, match="nlongIE debe ser un número finito"):
            parse_institutions(xml)
