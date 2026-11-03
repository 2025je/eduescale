import unittest

from eduescale.client import (
    SchoolFilters,
    build_search_params,
    parse_institutions,
)


class BuildSearchParamsTests(unittest.TestCase):
    def test_selects_most_specific_location_and_public_initial(self):
        params = build_search_params(
            SchoolFilters(
                region_code="15",
                province_code="1501",
                district_code="150101",
                management="publica",
                level="inicial",
            ),
            offset=50,
        )

        self.assertIn(("ubigeo", "150101"), params)
        self.assertNotIn(("ubigeo", "15"), params)
        self.assertIn(("gestiones", "A4"), params)
        self.assertIn(("niveles", "A5"), params)
        self.assertIn(("estados", "1"), params)
        self.assertIn(("start", "50"), params)

    def test_private_secondary_filters(self):
        params = build_search_params(SchoolFilters(management="privada", level="secundaria"))

        self.assertEqual(
            [value for key, value in params if key == "gestiones"],
            ["B1", "B2", "B3", "B4", "B5", "B6"],
        )
        self.assertEqual(
            [value for key, value in params if key == "niveles"],
            ["F0"],
        )

    def test_all_management_and_levels_omit_category_filters(self):
        params = build_search_params(SchoolFilters())

        self.assertFalse(any(key in {"gestiones", "niveles"} for key, _ in params))


class ParseInstitutionsTests(unittest.TestCase):
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
        </items></instituciones>""".encode("utf-8")

        self.assertEqual(
            parse_institutions(xml),
            [
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
                }
            ],
        )


if __name__ == "__main__":
    unittest.main()
