import json
import sqlite3
from contextlib import closing

from openpyxl import load_workbook

from eduescale.infrastructure.exports.excel import export_excel


def test_excel_orders_levels_and_management_as_requested(tmp_path):
    database_path = tmp_path / "schools.sqlite3"
    output_path = tmp_path / "schools.xlsx"
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
    export_excel(database_path, output_path)

    workbook = load_workbook(output_path, read_only=True)
    try:
        headings = [
            row
            for row in workbook["Iquitos - Maynas - Loreto"].iter_rows(
                min_col=4,
                max_col=5,
                values_only=True,
            )
            if row[0] and row[0].startswith("Nivel:")
        ]
    finally:
        workbook.close()

    assert headings == [
        ("Nivel: Secundaria", "Gestión: Pública"),
        ("Nivel: Secundaria", "Gestión: Particular"),
        ("Nivel: Secundaria", "Gestión: Parroquial"),
        ("Nivel: Primaria", "Gestión: Pública"),
        ("Nivel: Primaria", "Gestión: Particular"),
        ("Nivel: Primaria", "Gestión: Parroquial"),
        ("Nivel: CETPRO", "Gestión: Parroquial"),
        ("Nivel: Inicial", "Gestión: Pública"),
    ]
