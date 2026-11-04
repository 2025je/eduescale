import pytest
from pydantic import ValidationError

from eduescale.client import (
    SchoolFilters,
    build_search_params,
    parse_institutions,
)


class TestBuildSearchParams:
    def test_selects_most_specific_location_and_public_initial(self):
        params = build_search_params(
            SchoolFilters(
                region_code="15",
                province_code="1501",
                district_code="150101",
                management=("publica",),
                level=("inicial",),
            ),
            offset=50,
        )

        assert ("ubigeo", "150101") in params
        assert ("ubigeo", "15") not in params
        assert ("gestiones", "A4") in params
        assert ("niveles", "A5") in params
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
                level=("inicial", "primaria"),
            )
        )

        assert [value for key, value in params if key == "gestiones"] == [
            "A1",
            "A2",
            "A3",
            "A4",
        ]
        assert [value for key, value in params if key == "niveles"] == [
            "A1",
            "A2",
            "A3",
            "A5",
            "B0",
        ]

    def test_all_checkbox_values_send_all_selected_codes(self):
        params = build_search_params(
            SchoolFilters(
                management=("publica", "privada"),
                level=("inicial", "primaria", "secundaria"),
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
            "A1",
            "A2",
            "A3",
            "A5",
            "B0",
            "F0",
        ]

    def test_pydantic_normalizes_comma_separated_selections(self):
        filters = SchoolFilters.model_validate(
            {
                "management": "publica, privada,publica",
                "level": "inicial,primaria",
            }
        )

        assert filters.management == ("publica", "privada")
        assert filters.level == ("inicial", "primaria")

    def test_pydantic_rejects_unknown_filter_values(self):
        with pytest.raises(ValidationError):
            SchoolFilters(management=("mixta",))


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
            }
        ]
