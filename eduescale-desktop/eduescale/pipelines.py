import json
import logging
import sqlite3
import tempfile
import warnings
from pathlib import Path

from openpyxl import Workbook, load_workbook
from openpyxl.cell import WriteOnlyCell
from openpyxl.styles import Font, PatternFill
from openpyxl.utils import get_column_letter
from openpyxl.utils.exceptions import WorkbookAlreadySaved
from openpyxl.worksheet.table import Table, TableColumn, TableStyleInfo
from scrapy.exceptions import DropItem

from eduescale.export_files import android_data_path, replace_export_pair

LOGGER = logging.getLogger(__name__)

HEADERS = (
    "Nombre del colegio",
    "Dirección",
    "Localidad",
    "CP",
    "Turno",
    "Alumnos",
    "Secciones",
)
EXCEL_MAX_ROWS = 1_048_576
EXCEL_FIELDS = (
    "nombre",
    "direccion",
    "localidad",
    "cp",
    "turno",
    "alumnos",
    "secciones",
)
FIELDS = EXCEL_FIELDS + (
    "longitud",
    "latitud",
)
ANDROID_FIELDS = (
    "nombre",
    "direccion",
    "localidad",
    "cp",
    "turno",
    "alumnos",
    "secciones",
    "gestion",
    "nivel",
    "codigo_local",
    "longitud",
    "latitud",
)


def _safe_cell(worksheet, value):
    cell = WriteOnlyCell(worksheet, value=value)
    if isinstance(value, str) and value.startswith("="):
        cell.data_type = "s"
    return cell


class ExcelPipeline:
    @classmethod
    def from_crawler(cls, crawler):
        pipeline = cls()
        pipeline.crawler = crawler
        return pipeline

    def open_spider(self):
        self.output_path = Path(self.crawler.spider.output)
        self.android_output_path = android_data_path(self.output_path)
        self.output_path.parent.mkdir(parents=True, exist_ok=True)
        self.workbook = Workbook(write_only=True)
        self.worksheet = self.workbook.create_sheet("Colegios")
        self.worksheet.column_dimensions["A"].width = 45
        self.worksheet.column_dimensions["B"].width = 55
        self.worksheet.column_dimensions["C"].width = 30
        self.worksheet.column_dimensions["D"].width = 30
        self.worksheet.column_dimensions["E"].width = 20
        self.worksheet.column_dimensions["F"].width = 14
        self.worksheet.column_dimensions["G"].width = 14
        self.row_count = 0
        self._sheet_row_count = 0
        self._temporary_output_path: Path | None = None
        self._temporary_android_path: Path | None = None
        with tempfile.NamedTemporaryFile(
            prefix="eduescale-",
            suffix=".sqlite3",
            dir=self.output_path.parent,
            delete=False,
        ) as spool_file:
            self._spool_path = Path(spool_file.name)
        self._spool = sqlite3.connect(self._spool_path)
        self._spool.execute(
            """
            CREATE TABLE records (
                region TEXT NOT NULL,
                province TEXT NOT NULL,
                district TEXT NOT NULL,
                payload TEXT NOT NULL
            )
            """
        )

    def process_item(self, item):
        try:
            for field in FIELDS:
                item[field]
        except KeyError as error:
            raise DropItem(f"Falta el campo requerido para Excel: {error}") from error
        self._spool.execute(
            """
            INSERT INTO records (region, province, district, payload)
            VALUES (?, ?, ?, ?)
            """,
            (
                item.get("region") or "Departamento no identificado",
                item.get("provincia") or "Provincia no identificada",
                item.get("distrito") or "Distrito no identificado",
                json.dumps(item, ensure_ascii=False),
            ),
        )
        self.row_count += 1
        return item

    def close_spider(self):
        spider = self.crawler.spider
        workbook_saved = False
        try:
            if not spider.count_received:
                raise RuntimeError(
                    "No se pudo obtener el total de resultados desde ESCALE."
                )
            if (
                spider.items_received != spider.expected_total
                or self.row_count != spider.unique_items_received
            ):
                message = (
                    f"Extracción incompleta: se esperaban {spider.expected_total} "
                    f"resultados y se recibieron {spider.items_received}; "
                    f"se exportaron {self.row_count} colegios únicos; "
                    f"páginas completas: {spider.pages_received}."
                )
                if spider.failed_pages:
                    details = "; ".join(
                        f"filtro {filter_index + 1}, offset {offset}: {reason}"
                        for (filter_index, offset), reason in sorted(
                            spider.failed_pages.items()
                        )
                    )
                    message = f"{message} Páginas fallidas: {details}"
                raise RuntimeError(message)

            self._write_district_tables()
            temporary_output_path = self._create_temporary_output_path()
            self._temporary_output_path = temporary_output_path
            temporary_android_path = self._create_temporary_android_path()
            self._temporary_android_path = temporary_android_path
            self._write_android_file(temporary_android_path)
            self.workbook.save(temporary_output_path)
            workbook_saved = True
            self._validate_workbook(temporary_output_path)
            try:
                cleanup_warning = replace_export_pair(
                    temporary_output_path,
                    self.output_path,
                    temporary_android_path,
                    self.android_output_path,
                )
            except PermissionError as error:
                raise RuntimeError(
                    "No se pudieron reemplazar los archivos de exportación. "
                    "Cierra el archivo de Excel si está abierto y vuelve a intentar."
                ) from error
            if cleanup_warning:
                LOGGER.warning("%s", cleanup_warning)
        finally:
            try:
                if not workbook_saved:
                    try:
                        self.worksheet.close()
                    except WorkbookAlreadySaved:
                        pass
            finally:
                try:
                    if self._temporary_output_path is not None:
                        self._temporary_output_path.unlink(missing_ok=True)
                finally:
                    try:
                        if self._temporary_android_path is not None:
                            self._temporary_android_path.unlink(missing_ok=True)
                    finally:
                        self._close_spool()

    def _create_temporary_output_path(self):
        with tempfile.NamedTemporaryFile(
            prefix=".eduescale-export-",
            suffix=".xlsx",
            dir=self.output_path.parent,
            delete=False,
        ) as export_file:
            return Path(export_file.name)

    def _create_temporary_android_path(self):
        with tempfile.NamedTemporaryFile(
            prefix=".eduescale-export-",
            suffix=".eduescale",
            dir=self.output_path.parent,
            delete=False,
        ) as export_file:
            return Path(export_file.name)

    def _write_android_file(self, path):
        cursor = self._spool.execute(
            """
            SELECT region, province, district, payload
            FROM records
            ORDER BY region COLLATE NOCASE,
                     province COLLATE NOCASE,
                     district COLLATE NOCASE,
                     rowid
            """
        )
        with path.open("w", encoding="utf-8", newline="\n") as output_file:
            output_file.write('{"formato":"eduescale","version":1,"colegios":[')
            first_record = True
            for region, province, district, payload in cursor:
                item = json.loads(payload)
                record = {
                    "departamento": region,
                    "provincia": province,
                    "distrito": district,
                    **{field: item.get(field) for field in ANDROID_FIELDS},
                }
                if not first_record:
                    output_file.write(",")
                json.dump(
                    record,
                    output_file,
                    ensure_ascii=False,
                    allow_nan=False,
                    separators=(",", ":"),
                )
                first_record = False
            output_file.write("]}\n")

    @staticmethod
    def _validate_workbook(path):
        workbook = load_workbook(path, read_only=True)
        try:
            if "Colegios" not in workbook.sheetnames:
                raise RuntimeError("El archivo temporal no contiene la hoja Colegios.")
        finally:
            workbook.close()

    def _write_district_tables(self):
        cursor = self._spool.execute(
            """
            SELECT region, province, district, payload
            FROM records
            ORDER BY region COLLATE NOCASE,
                     province COLLATE NOCASE,
                     district COLLATE NOCASE,
                     rowid
            """
        )
        current_district = None
        table_start_row = 0
        table_number = 0

        for region, province, district, payload in cursor:
            district_key = (region, province, district)
            if district_key != current_district:
                if current_district is not None:
                    self._add_district_table(table_number, table_start_row)
                    self._append_row([])
                self._append_row(self._district_heading(region, province, district))
                self._append_row(HEADERS)
                table_start_row = self._sheet_row_count
                table_number += 1
                current_district = district_key

            item = json.loads(payload)
            row = [_safe_cell(self.worksheet, item[field]) for field in EXCEL_FIELDS]
            self._append_row(row)

        if current_district is not None:
            self._add_district_table(table_number, table_start_row)
        elif not self.row_count:
            self._append_row(HEADERS)

    def _append_row(self, values):
        if self._sheet_row_count >= EXCEL_MAX_ROWS:
            raise RuntimeError(
                "Las tablas por distrito superan el límite de filas de una hoja de Excel."
            )
        self.worksheet.append(values)
        self._sheet_row_count += 1

    def _district_heading(self, region, province, district):
        fill = PatternFill(fill_type="solid", fgColor="1E4D73")
        row = []
        for index, value in enumerate(
            (
                f"Distrito: {district}",
                f"Provincia: {province}",
                f"Departamento: {region}",
            )
            + ("",) * (len(HEADERS) - 3)
        ):
            cell = _safe_cell(self.worksheet, value)
            if index < 3:
                cell.font = Font(bold=True, color="FFFFFF", size=12)
            cell.fill = fill
            row.append(cell)
        return row

    def _add_district_table(self, table_number, start_row):
        table = Table(
            displayName=f"DistrictTable{table_number:04d}",
            ref=(
                f"A{start_row}:{get_column_letter(len(HEADERS))}{self._sheet_row_count}"
            ),
        )
        table.tableColumns = [
            TableColumn(id=index, name=header)
            for index, header in enumerate(HEADERS, start=1)
        ]
        table.tableStyleInfo = TableStyleInfo(
            name="TableStyleMedium2",
            showFirstColumn=False,
            showLastColumn=False,
            showRowStripes=True,
            showColumnStripes=False,
        )
        with warnings.catch_warnings():
            warnings.filterwarnings(
                "ignore",
                message="In write-only mode you must add table columns manually",
                category=UserWarning,
            )
            self.worksheet.add_table(table)

    def _close_spool(self):
        self._spool.close()
        self._spool_path.unlink(missing_ok=True)
