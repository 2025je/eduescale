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


def test_saves_one_excel_table_per_district():
    first_record = {
        "nombre": "Colegio Ejemplo",
        "direccion": "Av. Principal",
        "localidad": "Localidad",
        "cp": "Centro Poblado",
        "turno": "Mañana",
        "alumnos": 10,
        "secciones": 1,
        "longitud": -77.2,
        "latitud": -12.1,
        "distrito": "Iquitos",
        "provincia": "Maynas",
        "region": "Loreto",
    }
    second_record = {
        **first_record,
        "nombre": "Colegio Secundario",
        "distrito": "Nauta",
    }
    with tempfile.TemporaryDirectory() as temp_dir:
        output_path = Path(temp_dir) / "colegios.xlsx"
        spider = SimpleNamespace(
            output=str(output_path),
            count_received=True,
            expected_total=2,
            items_received=2,
            unique_items_received=2,
            pages_received=2,
            failed_pages={},
        )
        pipeline = ExcelPipeline()
        pipeline.crawler = SimpleNamespace(spider=spider)
        pipeline.open_spider()
        pipeline.process_item(first_record)
        pipeline.process_item(second_record)
        pipeline.close_spider()

        workbook = load_workbook(output_path)
        try:
            worksheet = workbook["Colegios"]
            tables = list(worksheet.tables.values())
            assert [table.displayName for table in tables] == [
                "DistrictTable0001",
                "DistrictTable0002",
            ]
            assert [table.ref for table in tables] == ["A2:J3", "A6:J7"]
            assert worksheet["A1"].value == "Distrito: Iquitos"
            assert worksheet["A5"].value == "Distrito: Nauta"
            assert worksheet["A3"].value == "Colegio Ejemplo"
            assert worksheet["A7"].value == "Colegio Secundario"
        finally:
            workbook.close()
