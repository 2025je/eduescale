import unittest

from openpyxl import Workbook

from eduescale.pipelines import google_maps_cell


class GoogleMapsCellTests(unittest.TestCase):
    def setUp(self):
        self.workbook = Workbook()
        self.worksheet = self.workbook.active

    def tearDown(self):
        self.workbook.close()

    def test_creates_google_maps_hyperlink_from_latitude_and_longitude(self):
        cell = google_maps_cell(
            self.worksheet,
            {"latitud": -5.773595, "longitud": -77.25584},
        )

        self.assertEqual(cell.value, "Ver en Google Maps")
        self.assertEqual(
            cell.hyperlink.target,
            "https://www.google.com/maps/search/?api=1&query=-5.773595,-77.25584",
        )
        self.assertEqual(cell.style, "Hyperlink")

    def test_leaves_cell_blank_when_coordinates_are_missing(self):
        self.assertIsNone(
            google_maps_cell(self.worksheet, {"latitud": None, "longitud": -77.2})
        )


if __name__ == "__main__":
    unittest.main()
