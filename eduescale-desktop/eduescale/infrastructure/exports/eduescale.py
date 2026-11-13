import json
import os
import sqlite3
import zipfile
from contextlib import closing
from datetime import datetime, timezone
from importlib.metadata import version as package_version
from pathlib import Path

from eduescale.infrastructure.exports.encryption import encrypt_file
from eduescale.infrastructure.exports.files import create_temporary_export_path

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


def export_eduescale(
    database_path: Path,
    output_path: Path,
    password: str | None = None,
    compressed: bool = False,
) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_json_path = create_temporary_export_path(output_path, ".json")
    temporary_output_path = create_temporary_export_path(output_path, ".eduescale")
    try:
        with closing(sqlite3.connect(database_path)) as database:
            record_count = database.execute("SELECT COUNT(*) FROM records").fetchone()[
                0
            ]
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
            generated_at = (
                datetime.now(timezone.utc)
                .isoformat(timespec="seconds")
                .replace("+00:00", "Z")
            )
            with temporary_json_path.open(
                "w", encoding="utf-8", newline="\n"
            ) as output_file:
                output_file.write('{"formato":"eduescale","version":1,"metadatos":')
                json.dump(
                    {
                        "generado_en": generated_at,
                        "cantidad_colegios": record_count,
                        "crs": "EPSG:4326",
                        "aplicacion": "EduEscale Desktop",
                        "version_aplicacion": package_version("eduescale"),
                    },
                    output_file,
                    ensure_ascii=False,
                    separators=(",", ":"),
                )
                output_file.write(',"colegios":[')
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
        if password is not None:
            encrypt_file(temporary_json_path, temporary_output_path, password)
        elif compressed:
            with zipfile.ZipFile(
                temporary_output_path,
                "w",
                compression=zipfile.ZIP_DEFLATED,
                compresslevel=6,
            ) as archive:
                archive.write(temporary_json_path, arcname="colegios.json")
        else:
            temporary_output_path.unlink(missing_ok=True)
            os.replace(temporary_json_path, output_path)
            return
        os.replace(temporary_output_path, output_path)
    finally:
        temporary_json_path.unlink(missing_ok=True)
        temporary_output_path.unlink(missing_ok=True)
