import json
import tempfile
from pathlib import Path
from types import SimpleNamespace

import pytest
from openpyxl import load_workbook

from eduescale.export_files import android_data_path
from eduescale.exports import export_eduescale, export_excel
from eduescale.pipelines import ExcelPipeline


def test_saves_one_excel_table_per_district():
    first_record = {
        "nombre": "Colegio Ejemplo",
        "direccion": '=HYPERLINK("https://example.invalid", "clic")',
        "localidad": "Localidad",
        "cp": "Centro Poblado",
        "turno": "Mañana",
        "alumnos": 10,
        "secciones": 1,
        "gestion": "publica",
        "nivel": "inicial",
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
        "distrito": "Nauta",
    }
    with tempfile.TemporaryDirectory() as temp_dir:
        database_path = Path(temp_dir) / "colegios.sqlite3"
        output_path = Path(temp_dir) / "colegios.xlsx"
        spider = SimpleNamespace(
            output=str(database_path),
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

        assert database_path.is_file()
        assert not output_path.exists()
        android_path = android_data_path(output_path)
        assert not android_path.exists()
        export_excel(database_path, output_path)
        export_eduescale(database_path, android_path)

        workbook = load_workbook(output_path)
        try:
            worksheet = workbook["Colegios"]
            tables = list(worksheet.tables.values())
            assert [table.displayName for table in tables] == [
                "DistrictTable0001",
                "DistrictTable0002",
            ]
            assert [table.ref for table in tables] == ["A2:G3", "A6:G7"]
            assert worksheet["A1"].value == "Distrito: Iquitos"
            assert worksheet["A5"].value == "Distrito: Nauta"
            assert [cell.value for cell in worksheet[2]] == [
                "Nombre del colegio",
                "Dirección",
                "Localidad",
                "CP",
                "Turno",
                "Alumnos",
                "Secciones",
            ]
            assert worksheet.max_column == 7
            assert worksheet["A3"].value == "Colegio Ejemplo"
            assert worksheet["B3"].data_type == "s"
            assert worksheet["B3"].value.startswith("=HYPERLINK")
            assert worksheet["A7"].value == "Colegio Secundario"
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
                "nivel": "inicial",
                "codigo_local": "000123",
                "longitud": -77.2,
                "latitud": -12.1,
            },
            {
                "departamento": "Loreto",
                "provincia": "Maynas",
                "distrito": "Nauta",
                "nombre": "Colegio Secundario",
                "direccion": '=HYPERLINK("https://example.invalid", "clic")',
                "localidad": "Localidad",
                "cp": "Centro Poblado",
                "turno": "Mañana",
                "alumnos": 10,
                "secciones": 1,
                "gestion": "publica",
                "nivel": "inicial",
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
