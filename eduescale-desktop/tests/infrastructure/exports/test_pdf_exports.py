from unittest.mock import Mock

from PySide6.QtCore import QRect

from eduescale.infrastructure.exports.pdf import _PdfRenderer


def test_pdf_row_order_increments_and_restarts_for_each_table():
    renderer = object.__new__(_PdfRenderer)
    renderer.page_rect = QRect(0, 0, 100, 100)
    renderer.content_bottom = 100
    renderer.y = 0
    renderer.row_index = 0
    renderer.title_font = object()
    renderer.header_font = object()
    renderer.body_font = object()
    renderer._row_height = Mock(return_value=10)
    renderer._heading_height = Mock(return_value=10)
    renderer._draw_table_headers = Mock()
    renderer._draw_row = Mock()
    renderer._next_page = Mock()

    renderer.start_table("Loreto", "Maynas", "Iquitos", "Primaria", ["Colegio 1"])
    renderer.draw_row(["Colegio 1"])
    renderer.draw_row(["Colegio 2"])
    renderer.start_table("Loreto", "Maynas", "Iquitos", "Secundaria", ["Colegio 3"])
    renderer.draw_row(["Colegio 3"])

    assert [call.args[0] for call in renderer._draw_row.call_args_list] == [
        (1, "Colegio 1"),
        (2, "Colegio 2"),
        (1, "Colegio 3"),
    ]


def test_pdf_keeps_next_district_table_on_page_when_it_fits():
    renderer = object.__new__(_PdfRenderer)
    renderer.page_rect = QRect(0, 0, 100, 100)
    renderer.content_bottom = 100
    renderer.y = 40
    renderer.page_has_content = True
    renderer.title_font = object()
    renderer.header_font = object()
    renderer.body_font = object()
    renderer._row_height = Mock(return_value=10)
    renderer._heading_height = Mock(return_value=10)
    renderer._draw_table_headers = Mock()
    renderer._next_page = Mock()

    renderer.start_table(
        "Loreto",
        "Maynas",
        "Nauta",
        "Primaria",
        ["Colegio de otro distrito"],
    )

    renderer._next_page.assert_not_called()
    renderer._draw_table_headers.assert_called_once()


def test_pdf_summary_includes_generation_time_and_dataset_counts():
    renderer = object.__new__(_PdfRenderer)
    renderer.widths = [50, 50]
    renderer.y = 0
    renderer.summary_title_font = object()
    renderer.summary_font = object()
    renderer._draw_row = Mock()

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
