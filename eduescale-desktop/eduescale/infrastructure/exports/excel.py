import json
import os
import re
import sqlite3
import warnings
from contextlib import closing
from pathlib import Path
from typing import TypedDict

from openpyxl import Workbook, load_workbook
from openpyxl.cell import WriteOnlyCell
from openpyxl.styles import Font, PatternFill
from openpyxl.utils import get_column_letter
from openpyxl.utils.exceptions import WorkbookAlreadySaved
from openpyxl.worksheet.table import Table, TableColumn, TableStyleInfo
from openpyxl.worksheet.worksheet import Worksheet

from eduescale.infrastructure.exports.files import create_temporary_export_path

HEADERS = (
    "#",
    "Nombre del colegio",
    "Dirección",
    "Localidad",
    "CP",
    "Turno",
    "Alumnos",
    "Secciones",
)
EXCEL_MAX_ROWS = 1_048_576
EXCEL_LEVEL_LABELS = {
    "primaria": "Primaria",
    "secundaria": "Secundaria",
    "cetpro": "CETPRO",
}
EXCEL_FIELDS = (
    "nombre",
    "direccion",
    "localidad",
    "cp",
    "turno",
    "alumnos",
    "secciones",
)
class _DistrictSheetState(TypedDict):
    worksheet: Worksheet
    current_level: str | None
    table_start_row: int
    sheet_row_count: int
    table_order: int


def export_excel(database_path: Path, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = create_temporary_export_path(output_path, ".xlsx")
    workbook = Workbook(write_only=True)
    worksheets = []
    used_sheet_names: set[str] = set()
    current_state: _DistrictSheetState | None = None
    current_district = None
    table_number = 0
    workbook_saved = False

    def create_sheet_state(sheet_name: str) -> _DistrictSheetState:
        worksheet = workbook.create_sheet(sheet_name)
        worksheets.append(worksheet)
        worksheet.page_setup.orientation = "landscape"
        worksheet.page_setup.paperSize = Worksheet.PAPERSIZE_A4
        worksheet.page_setup.fitToWidth = 1
        worksheet.page_setup.fitToHeight = 0
        worksheet.sheet_properties.pageSetUpPr.fitToPage = True
        worksheet.page_margins.left = 0.25
        worksheet.page_margins.right = 0.25
        worksheet.page_margins.top = 0.4
        worksheet.page_margins.bottom = 0.4
        for column, width in (
            ("A", 8),
            ("B", 45),
            ("C", 55),
            ("D", 30),
            ("E", 30),
            ("F", 20),
            ("G", 14),
            ("H", 14),
        ):
            worksheet.column_dimensions[column].width = width
        return {
            "worksheet": worksheet,
            "current_level": None,
            "table_start_row": 0,
            "sheet_row_count": 0,
            "table_order": 0,
        }

    def append_row(state: _DistrictSheetState, values):
        if state["sheet_row_count"] >= EXCEL_MAX_ROWS:
            raise RuntimeError(
                "Las tablas por distrito superan el límite de filas de una hoja de Excel."
            )
        state["worksheet"].append(values)
        state["sheet_row_count"] += 1

    def finish_table(state: _DistrictSheetState):
        nonlocal table_number
        if state["current_level"] is None:
            return
        table_number += 1
        _add_district_table(
            state["worksheet"],
            table_number,
            state["table_start_row"],
            state["sheet_row_count"],
        )

    try:
        with closing(sqlite3.connect(database_path)) as database:
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
            for region, province, district, payload in cursor:
                item = json.loads(payload)
                level = item.get("nivel")
                if level not in EXCEL_LEVEL_LABELS:
                    raise ValueError(
                        f"Nivel educativo no admitido para Excel: {level!r}."
                    )

                district_key = (region, province, district)
                if district_key != current_district:
                    if current_state is not None:
                        finish_table(current_state)
                    sheet_name = _district_sheet_name(
                        district, province, region, used_sheet_names
                    )
                    current_state = create_sheet_state(sheet_name)
                    current_district = district_key

                assert current_state is not None
                if level != current_state["current_level"]:
                    if current_state["current_level"] is not None:
                        finish_table(current_state)
                        append_row(current_state, [])
                    append_row(
                        current_state,
                        _district_heading(
                            current_state["worksheet"],
                            region,
                            province,
                            district,
                            EXCEL_LEVEL_LABELS[level],
                        ),
                    )
                    append_row(
                        current_state,
                        [
                            _safe_cell(current_state["worksheet"], header)
                            for header in HEADERS
                        ],
                    )
                    current_state["table_start_row"] = current_state["sheet_row_count"]
                    current_state["current_level"] = level
                    current_state["table_order"] = 0

                current_state["table_order"] += 1
                append_row(
                    current_state,
                    [
                        _safe_cell(
                            current_state["worksheet"], current_state["table_order"]
                        ),
                        *(
                            _safe_cell(current_state["worksheet"], item[field])
                            for field in EXCEL_FIELDS
                        ),
                    ],
                )

        if current_state is None:
            current_state = create_sheet_state("Sin datos")
            append_row(
                current_state,
                [_safe_cell(current_state["worksheet"], header) for header in HEADERS],
            )
        else:
            finish_table(current_state)

        workbook.save(temporary_path)
        workbook_saved = True
        _validate_workbook(temporary_path)
        os.replace(temporary_path, output_path)
    finally:
        if not workbook_saved:
            for worksheet in worksheets:
                try:
                    worksheet.close()
                except WorkbookAlreadySaved:
                    pass
        temporary_path.unlink(missing_ok=True)


def _district_sheet_name(district, province, region, used_names):
    location = " - ".join(
        str(value).strip()
        for value in (district, province, region)
        if value is not None and str(value).strip()
    )
    base = re.sub(r"[\[\]:*?/\\]", "-", location).strip().strip("'") or "Distrito"
    candidate = base[:31].rstrip("'")
    suffix_number = 2
    while candidate.casefold() in used_names:
        suffix = f" ({suffix_number})"
        prefix = base[: 31 - len(suffix)].rstrip("'")
        candidate = f"{prefix}{suffix}"
        suffix_number += 1
    used_names.add(candidate.casefold())
    return candidate


def _safe_cell(worksheet, value):
    cell = WriteOnlyCell(worksheet, value=value)
    if isinstance(value, str) and value.startswith("="):
        cell.data_type = "s"
    return cell


def _district_heading(worksheet, region, province, district, level):
    fill = PatternFill(fill_type="solid", fgColor="1E4D73")
    row = []
    for index, value in enumerate(
        (
            f"Departamento: {region}",
            f"Provincia: {province}",
            f"Distrito: {district}",
            f"Nivel: {level}",
        )
        + ("",) * (len(HEADERS) - 4)
    ):
        cell = _safe_cell(worksheet, value)
        if index < 4:
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
        if not workbook.sheetnames:
            raise RuntimeError("El archivo temporal no contiene hojas por distrito.")
    finally:
        workbook.close()
