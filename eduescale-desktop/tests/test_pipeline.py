import json
import tempfile
from pathlib import Path
from types import SimpleNamespace

import pytest
from openpyxl import load_workbook

from eduescale.export_files import android_data_path
from eduescale.exports import export_eduescale, export_excel
from eduescale.pipelines import ExcelPipeline


def test_saves_one_excel_sheet_per_district_with_tables_per_level():
    first_record = {
        "nombre": "Colegio Ejemplo",
        "direccion": '=HYPERLINK("https://example.invalid", "clic")',
        "localidad": "Localidad",
        "cp": "Centro Poblado",
        "turno": "Mañana",
        "alumnos": 10,
        "secciones": 1,
        "gestion": "publica",
        "nivel": "primaria",
        "codigo_local": "000123",
        "longitud": -77.2,
        "latitud": -12.1,
        "distrito": "Iquitos",
        "provincia": "Maynas",
        "region": "Loreto",
    }
    second_record = {
        **first_record,
        "nombre": "Colegio Secundario",
        "nivel": "secundaria",
    }
    third_record = {
        **first_record,
        "nombre": "Colegio CETPRO",
        "distrito": "Nauta",
        "nivel": "cetpro",
    }
    with tempfile.TemporaryDirectory() as temp_dir:
        database_path = Path(temp_dir) / "colegios.sqlite3"
        output_path = Path(temp_dir) / "colegios.xlsx"
        spider = SimpleNamespace(
            output=str(database_path),
            count_received=True,
            expected_total=3,
            items_received=3,
            unique_items_received=3,
            pages_received=3,
            failed_pages={},
        )
        pipeline = ExcelPipeline()
        pipeline.crawler = SimpleNamespace(spider=spider)
        pipeline.open_spider()
        pipeline.process_item(first_record)
        pipeline.process_item(second_record)
        pipeline.process_item(third_record)
        pipeline.close_spider()

        assert database_path.is_file()
        assert not output_path.exists()
        android_path = android_data_path(output_path)
        assert not android_path.exists()
        export_excel(database_path, output_path)
        export_eduescale(database_path, android_path)

        workbook = load_workbook(output_path)
        try:
            assert workbook.sheetnames == [
                "Iquitos - Maynas - Loreto",
                "Nauta - Maynas - Loreto",
            ]
            iquitos_sheet = workbook["Iquitos - Maynas - Loreto"]
            iquitos_tables = list(iquitos_sheet.tables.values())
            assert [table.displayName for table in iquitos_tables] == [
                "DistrictTable0001",
                "DistrictTable0002",
            ]
            assert [table.ref for table in iquitos_tables] == ["A2:G3", "A6:G7"]
            assert iquitos_sheet["A1"].value == "Departamento: Loreto"
            assert iquitos_sheet["B1"].value == "Provincia: Maynas"
            assert iquitos_sheet["C1"].value == "Distrito: Iquitos"
            assert iquitos_sheet["D1"].value == "Nivel: Primaria"
            assert [cell.value for cell in iquitos_sheet[2]] == [
                "Nombre del colegio",
                "Dirección",
                "Localidad",
                "CP",
                "Turno",
                "Alumnos",
                "Secciones",
            ]
            assert iquitos_sheet.max_column == 7
            assert iquitos_sheet["A3"].value == "Colegio Ejemplo"
            assert iquitos_sheet["B3"].data_type == "s"
            assert iquitos_sheet["B3"].value.startswith("=HYPERLINK")
            assert iquitos_sheet["F3"].value == 10
            assert iquitos_sheet["G3"].value == 1
            assert iquitos_sheet["D5"].value == "Nivel: Secundaria"
            assert iquitos_sheet["A7"].value == "Colegio Secundario"

            nauta_sheet = workbook["Nauta - Maynas - Loreto"]
            nauta_tables = list(nauta_sheet.tables.values())
            assert [table.displayName for table in nauta_tables] == [
                "DistrictTable0003",
            ]
            assert [table.ref for table in nauta_tables] == ["A2:G3"]
            assert nauta_sheet["D1"].value == "Nivel: CETPRO"
            assert nauta_sheet["A3"].value == "Colegio CETPRO"
        finally:
            workbook.close()
        android_json = android_path.read_text(encoding="utf-8")
        assert android_json.startswith('{"formato":"eduescale"')
        document = json.loads(android_json)
        assert document["formato"] == "eduescale"
        assert document["version"] == 1
        assert document["colegios"] == [
            {
                "departamento": "Loreto",
                "provincia": "Maynas",
                "distrito": "Iquitos",
                "nombre": "Colegio Ejemplo",
                "direccion": '=HYPERLINK("https://example.invalid", "clic")',
                "localidad": "Localidad",
                "cp": "Centro Poblado",
                "turno": "Mañana",
                "alumnos": 10,
                "secciones": 1,
                "gestion": "publica",
                "nivel": "primaria",
                "codigo_local": "000123",
                "longitud": -77.2,
                "latitud": -12.1,
            },
            {
                "departamento": "Loreto",
                "provincia": "Maynas",
                "distrito": "Iquitos",
                "nombre": "Colegio Secundario",
                "direccion": '=HYPERLINK("https://example.invalid", "clic")',
                "localidad": "Localidad",
                "cp": "Centro Poblado",
                "turno": "Mañana",
                "alumnos": 10,
                "secciones": 1,
                "gestion": "publica",
                "nivel": "secundaria",
                "codigo_local": "000123",
                "longitud": -77.2,
                "latitud": -12.1,
            },
            {
                "departamento": "Loreto",
                "provincia": "Maynas",
                "distrito": "Nauta",
                "nombre": "Colegio CETPRO",
                "direccion": '=HYPERLINK("https://example.invalid", "clic")',
                "localidad": "Localidad",
                "cp": "Centro Poblado",
                "turno": "Mañana",
                "alumnos": 10,
                "secciones": 1,
                "gestion": "publica",
                "nivel": "cetpro",
                "codigo_local": "000123",
                "longitud": -77.2,
                "latitud": -12.1,
            },
        ]
        assert "google maps" not in android_json.lower()


def test_failed_excel_replace_preserves_both_existing_exports(monkeypatch):
    from eduescale import export_files

    record = {
        "nombre": "Colegio Ejemplo",
        "direccion": "Av. Principal",
        "localidad": "Localidad",
        "cp": "Centro Poblado",
        "turno": "Mañana",
        "nivel": "secundaria",
        "alumnos": 10,
        "secciones": 1,
        "longitud": -77.2,
        "latitud": -12.1,
        "distrito": "Iquitos",
        "provincia": "Maynas",
        "region": "Loreto",
    }
    with tempfile.TemporaryDirectory() as temp_dir:
        output_path = Path(temp_dir) / "colegios.xlsx"
        android_path = android_data_path(output_path)
        output_path.write_bytes(b"archivo anterior")
        android_path.write_bytes(b"archivo EduScale anterior")
        spider = SimpleNamespace(
            output=str(output_path),
            count_received=True,
            expected_total=1,
            items_received=1,
            unique_items_received=1,
            pages_received=1,
            failed_pages={},
        )
        pipeline = ExcelPipeline()
        pipeline.crawler = SimpleNamespace(spider=spider)
        pipeline.open_spider()
        pipeline.process_item(record)
        original_replace = export_files.os.replace

        def fail_excel_replace(source, destination):
            if Path(destination) == output_path:
                raise PermissionError("archivo en uso")
            return original_replace(source, destination)

        monkeypatch.setattr(export_files.os, "replace", fail_excel_replace)

        with pytest.raises(RuntimeError, match="Cierra el archivo"):
            pipeline.close_spider()

        assert output_path.read_bytes() == b"archivo anterior"
        assert android_path.read_bytes() == b"archivo EduScale anterior"
        assert not pipeline._temporary_output_path.exists()
        assert not pipeline._spool_path.exists()
        assert not list(Path(temp_dir).glob(".eduescale-backup-*"))
