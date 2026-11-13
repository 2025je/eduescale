import json
import os
import re
import sqlite3
from pathlib import Path

from openpyxl import load_workbook
from PySide6.QtCore import QEventLoop, QTimer
from PySide6.QtGui import QGuiApplication

from eduescale.application.use_cases.export_data import ExportData
from eduescale.gui.export_runner import ExportRunner
from eduescale.infrastructure.exports.gateway import FilesystemExportGateway


def _create_database(path: Path, record_count: int = 1):
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
        database.executemany(
            """
            INSERT INTO records (region, province, district, payload)
            VALUES (?, ?, ?, ?)
            """,
            [
                (
                    "Loreto",
                    "Maynas",
                    "Iquitos",
                    json.dumps(
                        {
                            "nombre": (
                                "Colegio Ejemplo"
                                if index == 0
                                else f"Colegio Ejemplo {index}"
                            ),
                            "direccion": "Av. Principal",
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
                        ensure_ascii=False,
                    ),
                )
                for index in range(record_count)
            ],
        )
        database.commit()
    finally:
        database.close()


def _run_export(runner, export_type, database_path, output_path):
    os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
    application = QGuiApplication.instance() or QGuiApplication([])
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
    pdf_path = tmp_path / "schools.pdf"
    _create_database(database_path, record_count=60)
    runner = ExportRunner(ExportData(FilesystemExportGateway()))

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
        assert _run_export(runner, "pdf", database_path, pdf_path) == (
            "completed",
            "pdf",
            str(pdf_path),
        )

        workbook = load_workbook(excel_path, read_only=True)
        try:
            worksheet = workbook["Iquitos - Maynas - Loreto"]
            assert worksheet["A2"].value == "#"
            assert worksheet["A3"].value == 1
            assert worksheet["B3"].value == "Colegio Ejemplo"
            assert worksheet["A62"].value == 60
        finally:
            workbook.close()
        document = json.loads(eduescale_path.read_text(encoding="utf-8"))
        assert document["colegios"][0]["nombre"] == "Colegio Ejemplo"
        pdf_bytes = pdf_path.read_bytes()
        assert pdf_bytes.startswith(b"%PDF-")
        assert pdf_bytes.count(b"/Type /Page") >= 2
        media_boxes = re.findall(rb"/MediaBox\s*\[([^\]]+)\]", pdf_bytes)
        assert media_boxes
        assert all(
            float(width) > float(height)
            for left, bottom, width, height in [
                map(float, media_box.split()) for media_box in media_boxes
            ]
        )
    finally:
        runner.shutdown()
