from pathlib import Path

from openpyxl import Workbook
from openpyxl.cell import WriteOnlyCell
from scrapy.exceptions import DropItem


HEADERS = (
    "Nombre del colegio",
    "Dirección",
    "Localidad",
    "CP",
    "Turno",
    "Alumnos",
    "Secciones",
    "Longitud",
    "Latitud",
    "Google Maps",
)
FIELDS = (
    "nombre",
    "direccion",
    "localidad",
    "cp",
    "turno",
    "alumnos",
    "secciones",
    "longitud",
    "latitud",
)


def google_maps_cell(worksheet, item):
    latitude = item.get("latitud")
    longitude = item.get("longitud")
    if latitude is None or longitude is None:
        return None

    cell = WriteOnlyCell(worksheet, value="Ver en Google Maps")
    cell.hyperlink = (
        "https://www.google.com/maps/search/?api=1"
        f"&query={latitude},{longitude}"
    )
    cell.style = "Hyperlink"
    return cell


class ExcelPipeline:
    @classmethod
    def from_crawler(cls, crawler):
        pipeline = cls()
        pipeline.crawler = crawler
        return pipeline

    def open_spider(self):
        self.output_path = Path(self.crawler.spider.output)
        self.output_path.parent.mkdir(parents=True, exist_ok=True)
        self.workbook = Workbook(write_only=True)
        self.worksheet = self.workbook.create_sheet("Colegios")
        self.worksheet.append(HEADERS)
        self.worksheet.column_dimensions["A"].width = 45
        self.worksheet.column_dimensions["B"].width = 55
        self.worksheet.column_dimensions["C"].width = 30
        self.worksheet.column_dimensions["D"].width = 30
        self.worksheet.column_dimensions["E"].width = 20
        self.worksheet.column_dimensions["F"].width = 14
        self.worksheet.column_dimensions["G"].width = 14
        self.worksheet.column_dimensions["H"].width = 14
        self.worksheet.column_dimensions["I"].width = 14
        self.worksheet.column_dimensions["J"].width = 24
        self.row_count = 0

    def process_item(self, item):
        try:
            row = [item[field] for field in FIELDS]
        except KeyError as error:
            raise DropItem(f"Falta el campo requerido para Excel: {error}") from error
        row.append(google_maps_cell(self.worksheet, item))
        self.worksheet.append(row)
        self.row_count += 1
        return item

    def close_spider(self):
        spider = self.crawler.spider
        if not spider.count_received:
            self.worksheet.close()
            raise RuntimeError("No se pudo obtener el total de resultados desde ESCALE.")
        if self.row_count != spider.expected_total:
            self.worksheet.close()
            message = (
                f"Extracción incompleta: se esperaban {spider.expected_total} "
                f"registros y se recibieron {self.row_count}; "
                f"páginas completas: {spider.pages_received}."
            )
            if spider.failed_pages:
                details = "; ".join(
                    f"offset {offset}: {reason}"
                    for offset, reason in sorted(spider.failed_pages.items())
                )
                message = f"{message} Páginas fallidas: {details}"
            raise RuntimeError(
                message
            )
        if self.row_count:
            self.worksheet.auto_filter.ref = f"A1:J{self.row_count + 1}"
        self.workbook.save(self.output_path)
