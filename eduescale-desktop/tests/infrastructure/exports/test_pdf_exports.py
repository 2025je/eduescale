import json
import sqlite3
from contextlib import closing

from PySide6.QtCore import QRect
from PySide6.QtGui import QGuiApplication

from eduescale.infrastructure.exports import pdf as pdf_module
from eduescale.infrastructure.exports.pdf import _PdfRenderer


def test_pdf_row_order_increments_and_restarts_for_each_table(mocker):
    renderer = object.__new__(_PdfRenderer)
    renderer.page_rect = QRect(0, 0, 100, 100)
    renderer.content_bottom = 100
    renderer.y = 0
    renderer.row_index = 0
    renderer.title_font = object()
    renderer.header_font = object()
    renderer.body_font = object()
    renderer._row_height = mocker.Mock(return_value=10)
    renderer._heading_height = mocker.Mock(return_value=10)
    renderer._draw_table_headers = mocker.Mock()
    renderer._draw_row = mocker.Mock()
    renderer._next_page = mocker.Mock()

    renderer.start_table(
        "Loreto",
        "Maynas",
        "Iquitos",
        "Primaria",
        "Pública",
        ["Colegio 1"],
    )
    renderer.draw_row(["Colegio 1"])
    renderer.draw_row(["Colegio 2"])
    renderer.start_table(
        "Loreto", "Maynas", "Iquitos", "Primaria", "Parroquial", ["Colegio 3"]
    )
    renderer.draw_row(["Colegio 3"])
    renderer.start_table(
        "Loreto",
        "Maynas",
        "Iquitos",
        "Secundaria",
        "Pública",
        ["Colegio 4"],
    )
    renderer.draw_row(["Colegio 4"])

    assert [call.args[0] for call in renderer._draw_row.call_args_list] == [
        (1, "Colegio 1"),
        (2, "Colegio 2"),
        (1, "Colegio 3"),
        (1, "Colegio 4"),
    ]
    assert renderer.current_heading[-1] == "Gestión: Pública"
    renderer.start_table(
        "Loreto", "Maynas", "Iquitos", "Primaria", "Parroquial", ["Colegio 5"]
    )
    assert renderer.current_heading[-1] == "Gestión: Parroquial"


def test_pdf_keeps_next_district_table_on_page_when_it_fits(mocker):
    renderer = object.__new__(_PdfRenderer)
    renderer.page_rect = QRect(0, 0, 100, 100)
    renderer.content_bottom = 100
    renderer.y = 40
    renderer.page_has_content = True
    renderer.title_font = object()
    renderer.header_font = object()
    renderer.body_font = object()
    renderer._row_height = mocker.Mock(return_value=10)
    renderer._heading_height = mocker.Mock(return_value=10)
    renderer._draw_table_headers = mocker.Mock()
    renderer._next_page = mocker.Mock()

    renderer.start_table(
        "Loreto",
        "Maynas",
        "Nauta",
        "Primaria",
        "Pública",
        ["Colegio de otro distrito"],
    )

    renderer._next_page.assert_not_called()
    renderer._draw_table_headers.assert_called_once()


def test_pdf_summary_includes_generation_time_and_dataset_counts(mocker):
    renderer = object.__new__(_PdfRenderer)
    renderer.widths = [50, 50]
    renderer.y = 0
    renderer.summary_title_font = object()
    renderer.summary_font = object()
    renderer._draw_row = mocker.Mock()

    renderer.draw_summary(
        "13/11/2026 11:38:33",
        school_count=1200,
        region_count=1,
        province_count=2,
        district_count=3,
        level_counts={"primaria": 1100, "secundaria": 100},
    )

    assert [call.args[0][0] for call in renderer._draw_row.call_args_list] == [
        "Resumen del padrón educativo",
        "Generado: 13/11/2026 11:38:33 (hora local)",
        "Colegios registrados: 1.200 | Departamentos: 1 | Provincias: 2 | Distritos: 3",
        "Colegios por nivel: Primaria: 1.100 | Secundaria: 100 | CETPRO: 0",
    ]


def test_pdf_orders_levels_and_management_as_requested(mocker, tmp_path):
    if QGuiApplication.instance() is None:
        QGuiApplication([])

    database_path = tmp_path / "schools.sqlite3"
    output_path = tmp_path / "schools.pdf"
    schools = [
        ("inicial", "publica", "A1"),
        ("secundaria", "publica", "A1"),
        ("secundaria", "privada", None),
        ("secundaria", "publica", "A4"),
        ("primaria", "publica", "A1"),
        ("primaria", "privada", None),
        ("primaria", "publica", "A4"),
        ("cetpro", "publica", "A4"),
    ]
    with closing(sqlite3.connect(database_path)) as database:
        database.execute(
            "CREATE TABLE records "
            "(region TEXT, province TEXT, district TEXT, payload TEXT)"
        )
        database.executemany(
            "INSERT INTO records (region, province, district, payload) "
            "VALUES (?, ?, ?, ?)",
            [
                (
                    "Loreto",
                    "Maynas",
                    "Iquitos",
                    json.dumps(
                        {
                            "nombre": f"Colegio {index}",
                            "direccion": "",
                            "localidad": "",
                            "cp": "",
                            "turno": "",
                            "alumnos": 1,
                            "secciones": 1,
                            "nivel": level,
                            "gestion": management,
                            "gestion_dependencia": dependency,
                        }
                    ),
                )
                for index, (level, management, dependency) in enumerate(schools)
            ],
        )
        database.commit()

    tables = []

    class _RendererSpy:
        def __init__(self, _writer, _painter):
            pass

        def draw_summary(self, *_args):
            pass

        def start_table(self, _region, _province, _district, level, management, _row):
            tables.append((level, management))

        def draw_row(self, _row):
            pass

        def draw_empty(self):
            pass

        def finish(self):
            pass

    mocker.patch.object(pdf_module, "_PdfRenderer", _RendererSpy)
    pdf_module.export_pdf(database_path, output_path)

    assert tables == [
        ("Secundaria", "Pública"),
        ("Secundaria", "Particular"),
        ("Secundaria", "Parroquial"),
        ("Primaria", "Pública"),
        ("Primaria", "Particular"),
        ("Primaria", "Parroquial"),
        ("CETPRO", "Parroquial"),
        ("Inicial", "Pública"),
    ]
