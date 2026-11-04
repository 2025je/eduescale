import tempfile
from pathlib import Path
from types import SimpleNamespace

import pytest
from openpyxl import Workbook, load_workbook

from eduescale.pipelines import ExcelPipeline, google_maps_cell


@pytest.fixture
def worksheet():
    workbook = Workbook()
    yield workbook.active
    workbook.close()


def test_creates_google_maps_hyperlink_from_latitude_and_longitude(worksheet):
    cell = google_maps_cell(
        worksheet,
        {"latitud": -5.773595, "longitud": -77.25584},
    )

    assert cell.value == "Ver en Google Maps"
    assert (
        cell.hyperlink.target
        == "https://www.google.com/maps/search/?api=1&query=-5.773595,-77.25584"
    )
    assert cell.style == "Hyperlink"


def test_leaves_cell_blank_when_coordinates_are_missing(worksheet):
    assert google_maps_cell(worksheet, {"latitud": None, "longitud": -77.2}) is None


def test_saves_merged_unique_results_to_one_workbook():
    record = {
        "nombre": "Colegio Ejemplo",
        "direccion": "Av. Principal",
        "localidad": "Localidad",
        "cp": "Centro Poblado",
        "turno": "Mañana",
        "alumnos": 10,
        "secciones": 1,
        "longitud": -77.2,
        "latitud": -12.1,
    }
    with tempfile.TemporaryDirectory() as temp_dir:
        output_path = Path(temp_dir) / "colegios.xlsx"
        spider = SimpleNamespace(
            output=str(output_path),
            count_received=True,
            expected_total=2,
            items_received=2,
            unique_items_received=1,
            pages_received=2,
            failed_pages={},
        )
        pipeline = ExcelPipeline()
        pipeline.crawler = SimpleNamespace(spider=spider)
        pipeline.open_spider()
        pipeline.process_item(record)
        pipeline.close_spider()

        workbook = load_workbook(output_path, read_only=True)
        try:
            worksheet = workbook["Colegios"]
            rows = list(worksheet.iter_rows(values_only=True))
            assert len(rows) == 2
            assert rows[1][0] == "Colegio Ejemplo"
        finally:
            workbook.close()
