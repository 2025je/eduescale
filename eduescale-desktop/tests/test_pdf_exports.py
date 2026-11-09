from unittest.mock import Mock

from PySide6.QtCore import QRect

from eduescale.pdf_exports import _PdfRenderer


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
