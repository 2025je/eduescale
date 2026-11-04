import json
import os
import sqlite3
import tempfile
import warnings
from contextlib import closing
from pathlib import Path

from openpyxl import Workbook, load_workbook
from openpyxl.cell import WriteOnlyCell
from openpyxl.styles import Font, PatternFill
from openpyxl.utils import get_column_letter
from openpyxl.utils.exceptions import WorkbookAlreadySaved
from openpyxl.worksheet.table import Table, TableColumn, TableStyleInfo

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


def export_excel(database_path: Path, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = _temporary_path(output_path, ".xlsx")
    workbook = Workbook(write_only=True)
    worksheet = workbook.create_sheet("Colegios")
    worksheet.column_dimensions["A"].width = 45
    worksheet.column_dimensions["B"].width = 55
    worksheet.column_dimensions["C"].width = 30
    worksheet.column_dimensions["D"].width = 30
    worksheet.column_dimensions["E"].width = 20
    worksheet.column_dimensions["F"].width = 14
    worksheet.column_dimensions["G"].width = 14
    sheet_row_count = 0
    workbook_saved = False

    def append_row(values):
        nonlocal sheet_row_count
        if sheet_row_count >= EXCEL_MAX_ROWS:
            raise RuntimeError(
                "Las tablas por distrito superan el límite de filas de una hoja de Excel."
            )
        worksheet.append(values)
        sheet_row_count += 1

    try:
        with closing(sqlite3.connect(database_path)) as database:
            cursor = database.execute(
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
                        _add_district_table(
                            worksheet, table_number, table_start_row, sheet_row_count
                        )
                        append_row([])
                    append_row(_district_heading(worksheet, region, province, district))
                    append_row([_safe_cell(worksheet, header) for header in HEADERS])
                    table_start_row = sheet_row_count
                    table_number += 1
                    current_district = district_key

                item = json.loads(payload)
                append_row(
                    [_safe_cell(worksheet, item[field]) for field in EXCEL_FIELDS]
                )

            if current_district is not None:
                _add_district_table(
                    worksheet, table_number, table_start_row, sheet_row_count
                )
            else:
                append_row([_safe_cell(worksheet, header) for header in HEADERS])

        workbook.save(temporary_path)
        workbook_saved = True
        _validate_workbook(temporary_path)
        os.replace(temporary_path, output_path)
    finally:
        if not workbook_saved:
            try:
                worksheet.close()
            except WorkbookAlreadySaved:
                pass
        temporary_path.unlink(missing_ok=True)


def export_eduescale(database_path: Path, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = _temporary_path(output_path, ".eduescale")
    try:
        with closing(sqlite3.connect(database_path)) as database:
            cursor = database.execute(
                """
                SELECT region, province, district, payload
                FROM records
                ORDER BY region COLLATE NOCASE,
                         province COLLATE NOCASE,
                         district COLLATE NOCASE,
                         rowid
                """
            )
            with temporary_path.open(
                "w", encoding="utf-8", newline="\n"
            ) as output_file:
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
        os.replace(temporary_path, output_path)
    finally:
        temporary_path.unlink(missing_ok=True)


def _temporary_path(output_path: Path, suffix: str) -> Path:
    with tempfile.NamedTemporaryFile(
        prefix=".eduescale-export-",
        suffix=suffix,
        dir=output_path.parent,
        delete=False,
    ) as export_file:
        return Path(export_file.name)


def _safe_cell(worksheet, value):
    cell = WriteOnlyCell(worksheet, value=value)
    if isinstance(value, str) and value.startswith("="):
        cell.data_type = "s"
    return cell


def _district_heading(worksheet, region, province, district):
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
        cell = _safe_cell(worksheet, value)
        if index < 3:
            cell.font = Font(bold=True, color="FFFFFF", size=12)
        cell.fill = fill
        row.append(cell)
    return row


def _add_district_table(worksheet, table_number, start_row, end_row):
    table = Table(
        displayName=f"DistrictTable{table_number:04d}",
        ref=f"A{start_row}:{get_column_letter(len(HEADERS))}{end_row}",
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
        worksheet.add_table(table)


def _validate_workbook(path: Path) -> None:
    workbook = load_workbook(path, read_only=True)
    try:
        if "Colegios" not in workbook.sheetnames:
            raise RuntimeError("El archivo temporal no contiene la hoja Colegios.")
    finally:
        workbook.close()
