import json
import os
import sqlite3
import tempfile
from contextlib import closing
from datetime import datetime
from pathlib import Path

from PySide6.QtCore import QMarginsF, QRect, Qt
from PySide6.QtGui import (
    QColor,
    QFont,
    QFontMetrics,
    QGuiApplication,
    QPageLayout,
    QPageSize,
    QPainter,
    QPdfWriter,
)

from eduescale.infrastructure.exports.excel import (
    EXCEL_FIELDS,
    EXCEL_LEVEL_LABELS,
    HEADERS,
)

PAGE_RESOLUTION = 120
PAGE_MARGIN_MM = 5
FOOTER_HEIGHT = 18
CELL_PADDING = 5
COLUMN_WIDTH_RATIOS = (0.35, 1.4, 1.8, 1.0, 0.65, 0.7, 0.55, 0.65)
WRAP_FLAGS = int(Qt.TextFlag.TextWrapAnywhere)
CELL_FLAGS = (
    int(Qt.AlignmentFlag.AlignLeft) | int(Qt.AlignmentFlag.AlignVCenter) | WRAP_FLAGS
)
HEADER_FLAGS = (
    int(Qt.AlignmentFlag.AlignHCenter) | int(Qt.AlignmentFlag.AlignVCenter) | WRAP_FLAGS
)


class _PdfRenderer:
    def __init__(self, writer: QPdfWriter, painter: QPainter):
        self.writer = writer
        self.painter = painter
        # QPdfWriter's painter starts at the printable area's origin.
        self.page_rect = QRect(0, 0, writer.width(), writer.height())
        self.content_bottom = (
            self.page_rect.y() + self.page_rect.height() - FOOTER_HEIGHT
        )
        self.y = self.page_rect.y()
        self.page_number = 1
        self.page_has_content = False
        self.current_heading: tuple[str, str, str, str] | None = None
        self.row_index = 0
        self.title_font = QFont("Sans Serif")
        self.title_font.setPointSizeF(8.5)
        self.title_font.setBold(True)
        self.summary_title_font = QFont("Sans Serif")
        self.summary_title_font.setPointSizeF(14)
        self.summary_title_font.setBold(True)
        self.summary_font = QFont("Sans Serif")
        self.summary_font.setPointSizeF(9)
        self.header_font = QFont("Sans Serif")
        self.header_font.setPointSizeF(7.5)
        self.header_font.setBold(True)
        self.body_font = QFont("Sans Serif")
        self.body_font.setPointSizeF(8)
        self.footer_font = QFont("Sans Serif")
        self.footer_font.setPointSizeF(7)
        self.widths = self._column_widths()

    def draw_summary(
        self,
        generated_at: str,
        school_count: int,
        region_count: int,
        province_count: int,
        district_count: int,
        level_counts: dict[str, int],
    ):
        full_width = (sum(self.widths),)
        self._draw_row(
            ("Resumen del padrón educativo",),
            self.summary_title_font,
            QColor("#1E4D73"),
            CELL_FLAGS,
            widths=full_width,
            text_color=QColor("#FFFFFF"),
            minimum=36,
        )
        self._draw_row(
            (f"Generado: {generated_at} (hora local)",),
            self.summary_font,
            QColor("#F1F5F8"),
            CELL_FLAGS,
            widths=full_width,
        )
        self._draw_row(
            (
                " | ".join(
                    (
                        f"Colegios registrados: {_format_count(school_count)}",
                        f"Departamentos: {_format_count(region_count)}",
                        f"Provincias: {_format_count(province_count)}",
                        f"Distritos: {_format_count(district_count)}",
                    )
                ),
            ),
            self.summary_font,
            QColor("#F1F5F8"),
            CELL_FLAGS,
            widths=full_width,
        )
        levels = " | ".join(
            f"{label}: {_format_count(level_counts.get(level, 0))}"
            for level, label in EXCEL_LEVEL_LABELS.items()
        )
        self._draw_row(
            (f"Colegios por nivel: {levels}",),
            self.summary_font,
            QColor("#F1F5F8"),
            CELL_FLAGS,
            widths=full_width,
        )
        self.y += 8

    def start_table(self, region, province, district, level, first_row):
        self.current_heading = (
            f"Departamento: {region}",
            f"Provincia: {province}",
            f"Distrito: {district}",
            f"Nivel: {level}",
        )
        self.row_index = 0
        required_height = (
            self._heading_height()
            + self._row_height(HEADERS, self.header_font)
            + self._row_height((1, *first_row), self.body_font)
        )
        page_height = self.content_bottom - self.page_rect.y()
        if required_height > page_height:
            raise RuntimeError(
                "Una fila del colegio es demasiado alta para caber en una página PDF."
            )
        if self.y + required_height > self.content_bottom:
            self._next_page()
        self._draw_table_headers()

    def draw_row(self, values):
        row_height = self._row_height(values, self.body_font)
        table_header_height = self._heading_height() + self._row_height(
            HEADERS, self.header_font
        )
        if row_height + table_header_height > self.content_bottom - self.page_rect.y():
            raise RuntimeError(
                "Una fila del colegio es demasiado alta para caber en una página PDF."
            )
        if self.y + row_height > self.content_bottom:
            self._next_page()
            self._draw_table_headers()
        fill = QColor("#FFFFFF" if self.row_index % 2 == 0 else "#F1F5F8")
        self._draw_row((self.row_index + 1, *values), self.body_font, fill, CELL_FLAGS)
        self.row_index += 1

    def draw_empty(self):
        self._draw_row(HEADERS, self.header_font, QColor("#E5EDF3"), HEADER_FLAGS)

    def finish(self):
        self._draw_footer()

    def _column_widths(self):
        available_width = self.page_rect.width()
        total_ratio = sum(COLUMN_WIDTH_RATIOS)
        widths = [
            available_width * ratio / total_ratio for ratio in COLUMN_WIDTH_RATIOS
        ]
        widths[-1] = available_width - sum(widths[:-1])
        return widths

    def _heading_height(self):
        return self._row_height(
            (self._heading_text(),),
            self.title_font,
            (sum(self.widths),),
            minimum=24,
        )

    def _row_height(self, values, font, widths=None, minimum=24):
        widths = self.widths if widths is None else widths
        metrics = QFontMetrics(font, self.painter.device())
        heights = []
        for value, width in zip(values, widths):
            text_rect = QRect(
                0,
                0,
                max(1, int(width - 2 * CELL_PADDING)),
                max(1, self.page_rect.height()),
            )
            text = "" if value is None else str(value)
            heights.append(
                metrics.boundingRect(text_rect, WRAP_FLAGS, text).height()
                + 2 * CELL_PADDING
            )
        return max(minimum, *heights)

    def _draw_table_headers(self):
        self._draw_row(
            (self._heading_text(),),
            self.title_font,
            QColor("#1E4D73"),
            CELL_FLAGS,
            widths=(sum(self.widths),),
            text_color=QColor("#FFFFFF"),
            minimum=24,
        )
        self._draw_row(
            HEADERS,
            self.header_font,
            QColor("#E5EDF3"),
            HEADER_FLAGS,
        )
        self.page_has_content = True

    def _heading_text(self):
        return ", ".join(self.current_heading or ())

    def _draw_row(
        self,
        values,
        font,
        fill,
        text_flags,
        widths=None,
        text_color=None,
        minimum=24,
    ):
        widths = self.widths if widths is None else widths
        row_height = self._row_height(values, font, widths, minimum)
        x = self.page_rect.x()
        self.painter.setFont(font)
        self.painter.setPen(QColor("#AEBBC5"))
        for value, width in zip(values, widths):
            rect = QRect(round(x), round(self.y), round(width), round(row_height))
            self.painter.fillRect(rect, fill)
            self.painter.drawRect(rect)
            self.painter.setPen(text_color or QColor("#172B3A"))
            self.painter.drawText(
                rect.adjusted(CELL_PADDING, 1, -CELL_PADDING, -1),
                text_flags,
                "" if value is None else str(value),
            )
            self.painter.setPen(QColor("#AEBBC5"))
            x += width
        self.y += row_height
        self.page_has_content = True

    def _draw_footer(self):
        self.painter.save()
        self.painter.setFont(self.footer_font)
        self.painter.setPen(QColor("#667784"))
        footer_rect = QRect(
            self.page_rect.x(),
            self.content_bottom,
            self.page_rect.width(),
            FOOTER_HEIGHT,
        )
        self.painter.drawText(
            footer_rect,
            int(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter),
            f"Página {self.page_number}",
        )
        self.painter.restore()

    def _next_page(self):
        self._draw_footer()
        if not self.writer.newPage():
            raise RuntimeError("No se pudo crear una página del archivo PDF.")
        self.page_number += 1
        self.y = self.page_rect.y()
        self.page_has_content = False


def export_pdf(database_path: Path, output_path: Path) -> None:
    if QGuiApplication.instance() is None:
        raise RuntimeError("La exportación PDF requiere una aplicación gráfica activa.")

    generated_at = datetime.now().astimezone().strftime("%d/%m/%Y %H:%M:%S")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        prefix=".eduescale-pdf-",
        suffix=".pdf",
        dir=output_path.parent,
        delete=False,
    ) as temporary_file:
        temporary_path = Path(temporary_file.name)

    writer = None
    painter = None
    renderer = None
    try:
        writer = QPdfWriter(str(temporary_path))
        writer.setTitle("Padrón de instituciones educativas")
        writer.setCreator("EduEscale")
        writer.setResolution(PAGE_RESOLUTION)
        writer.setPageLayout(
            QPageLayout(
                QPageSize(QPageSize.PageSizeId.A4),
                QPageLayout.Orientation.Landscape,
                QMarginsF(
                    PAGE_MARGIN_MM,
                    PAGE_MARGIN_MM,
                    PAGE_MARGIN_MM,
                    PAGE_MARGIN_MM,
                ),
                QPageLayout.Unit.Millimeter,
            )
        )
        painter = QPainter(writer)
        if not painter.isActive():
            raise RuntimeError("No se pudo iniciar la generación del archivo PDF.")
        renderer = _PdfRenderer(writer, painter)

        with closing(sqlite3.connect(database_path)) as database:
            (
                school_count,
                region_count,
                province_count,
                district_count,
            ) = database.execute(
                """
                SELECT COUNT(*),
                       COUNT(DISTINCT region),
                       (SELECT COUNT(*) FROM (
                           SELECT region, province FROM records
                           GROUP BY region, province
                       )),
                       (SELECT COUNT(*) FROM (
                           SELECT region, province, district FROM records
                           GROUP BY region, province, district
                       ))
                FROM records
                """
            ).fetchone()
            level_counts = dict(
                database.execute(
                    """
                    SELECT json_extract(payload, '$.nivel'), COUNT(*)
                    FROM records
                    GROUP BY json_extract(payload, '$.nivel')
                    """
                )
            )
            renderer.draw_summary(
                generated_at,
                school_count,
                region_count,
                province_count,
                district_count,
                level_counts,
            )
            cursor = database.execute(
                """
                SELECT region, province, district, payload
                FROM records
                ORDER BY region COLLATE NOCASE,
                         province COLLATE NOCASE,
                         district COLLATE NOCASE,
                         CASE json_extract(payload, '$.nivel')
                             WHEN 'primaria' THEN 0
                             WHEN 'secundaria' THEN 1
                             WHEN 'cetpro' THEN 2
                             ELSE 3
                         END,
                         rowid
                """
            )
            current_district = None
            current_level = None
            found_records = False
            for region, province, district, payload in cursor:
                item = json.loads(payload)
                level = item.get("nivel")
                if level not in EXCEL_LEVEL_LABELS:
                    raise ValueError(
                        f"Nivel educativo no admitido para PDF: {level!r}."
                    )

                district_key = (region, province, district)
                if district_key != current_district:
                    current_district = district_key
                    current_level = None

                values = [item[field] for field in EXCEL_FIELDS]
                if level != current_level:
                    renderer.start_table(
                        region,
                        province,
                        district,
                        EXCEL_LEVEL_LABELS[level],
                        values,
                    )
                    current_level = level
                renderer.draw_row(values)
                found_records = True

        if not found_records:
            renderer.draw_empty()
        renderer.finish()
        renderer = None
        if painter.isActive():
            painter.end()
        painter = None
        writer = None

        with temporary_path.open("rb") as pdf_file:
            if pdf_file.read(5) != b"%PDF-":
                raise RuntimeError("El archivo temporal no es un PDF válido.")
        os.replace(temporary_path, output_path)
    finally:
        if painter is not None and painter.isActive():
            painter.end()
        renderer = None
        painter = None
        writer = None
        temporary_path.unlink(missing_ok=True)


def _format_count(value: int) -> str:
    return f"{value:,}".replace(",", ".")
