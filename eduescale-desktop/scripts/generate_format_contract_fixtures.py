import base64
import json
import sqlite3
import tempfile
from contextlib import closing
from pathlib import Path

from eduescale.infrastructure.exports.eduescale import export_eduescale

FIXTURES = Path(__file__).resolve().parents[2] / "test-fixtures"


def generate_fixtures(destination: Path) -> None:
    with tempfile.TemporaryDirectory() as temporary_directory:
        temporary_path = Path(temporary_directory)
        database_path = temporary_path / "schools.sqlite3"
        with closing(sqlite3.connect(database_path)) as database, database:
            database.execute(
                "CREATE TABLE records "
                "(region TEXT, province TEXT, district TEXT, payload TEXT)"
            )
            for record in (
                {
                    "nombre": "Colegio contrato",
                    "codigo_local": "000123",
                    "nivel": "secundaria",
                    "turno": "Mañana",
                    "gestion": "publica",
                    "alumnos": 120,
                    "secciones": 6,
                    "longitud": -73.253,
                    "latitud": -3.749,
                },
                {"nombre": "Sin coordenadas", "nivel": "primaria"},
            ):
                database.execute(
                    "INSERT INTO records VALUES (?, ?, ?, ?)",
                    ("Loreto", "Maynas", "Iquitos", json.dumps(record)),
                )

        for name, options in (
            ("json", {}),
            ("zip", {"compressed": True}),
            ("encrypted", {"password": "contraseña segura"}),
        ):
            output_path = temporary_path / f"{name}.eduescale"
            export_eduescale(database_path, output_path, **options)
            (destination / f"desktop-contract-{name}.base64").write_text(
                base64.b64encode(output_path.read_bytes()).decode("ascii") + "\n",
                encoding="ascii",
            )

        with closing(sqlite3.connect(database_path)) as database, database:
            database.execute(
                "UPDATE records SET payload = ? WHERE rowid = 1",
                (
                    json.dumps(
                        {
                            "nombre": "Colegio contrato v2",
                            "codigo_local": "000123",
                            "nivel": "inicial",
                            "gestion": "publica",
                            "gestion_dependencia": "A4",
                            "longitud": -73.253,
                            "latitud": -3.749,
                        }
                    ),
                ),
            )
        output_path = temporary_path / "v2.eduescale"
        export_eduescale(database_path, output_path)
        (destination / "desktop-contract-v2.base64").write_text(
            base64.b64encode(output_path.read_bytes()).decode("ascii") + "\n",
            encoding="ascii",
        )


if __name__ == "__main__":
    generate_fixtures(FIXTURES)
