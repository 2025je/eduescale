import json
import sqlite3
from pathlib import Path

from openpyxl import load_workbook
from PySide6.QtCore import QCoreApplication, QEventLoop, QTimer

from eduescale.gui.export_runner import ExportRunner


def _create_database(path: Path):
    database = sqlite3.connect(path)
    try:
        database.execute(
            """
            CREATE TABLE records (
                region TEXT NOT NULL,
                province TEXT NOT NULL,
                district TEXT NOT NULL,
                payload TEXT NOT NULL
            )
            """
        )
        database.execute(
            """
            INSERT INTO records (region, province, district, payload)
            VALUES (?, ?, ?, ?)
            """,
            (
                "Loreto",
                "Maynas",
                "Iquitos",
                json.dumps(
                    {
                        "nombre": "Colegio Ejemplo",
                        "direccion": "Av. Principal",
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
                    ensure_ascii=False,
                ),
            ),
        )
        database.commit()
    finally:
        database.close()


def _run_export(runner, export_type, database_path, output_path):
    application = QCoreApplication.instance() or QCoreApplication([])
    loop = QEventLoop()
    timer = QTimer()
    timer.setSingleShot(True)
    timer.timeout.connect(loop.quit)
    result = []

    def completed(kind, path):
        result.append(("completed", kind, path))
        loop.quit()

    def failed(kind, details, file_name):
        result.append(("failed", kind, details, file_name))
        loop.quit()

    runner.completed.connect(completed)
    runner.failed.connect(failed)
    runner.start(export_type, database_path, output_path)
    timer.start(10_000)
    loop.exec()
    timer.stop()
    runner.completed.disconnect(completed)
    runner.failed.disconnect(failed)
    assert application is not None
    assert result, f"{export_type} export did not finish in time"
    return result[0]


def test_export_runner_generates_each_requested_format(tmp_path):
    database_path = tmp_path / "schools.sqlite3"
    excel_path = tmp_path / "schools.xlsx"
    eduescale_path = tmp_path / "schools.eduescale"
    _create_database(database_path)
    runner = ExportRunner()

    try:
        assert _run_export(runner, "excel", database_path, excel_path) == (
            "completed",
            "excel",
            str(excel_path),
        )
        assert _run_export(runner, "eduescale", database_path, eduescale_path) == (
            "completed",
            "eduescale",
            str(eduescale_path),
        )

        workbook = load_workbook(excel_path, read_only=True)
        try:
            assert workbook["Colegios"]["A3"].value == "Colegio Ejemplo"
        finally:
            workbook.close()
        document = json.loads(eduescale_path.read_text(encoding="utf-8"))
        assert document["colegios"][0]["nombre"] == "Colegio Ejemplo"
    finally:
        runner.shutdown()
