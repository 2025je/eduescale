import hashlib
import json
import sqlite3
import zipfile
import zlib
from datetime import datetime
from pathlib import Path

import pytest
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

from eduescale.encrypted_export import HEADER, MAGIC
from eduescale.exports import export_eduescale


def _database(path: Path) -> None:
    with sqlite3.connect(path) as database:
        database.execute(
            """
            CREATE TABLE records (
                region TEXT,
                province TEXT,
                district TEXT,
                payload TEXT
            )
            """
        )
        database.execute(
            "INSERT INTO records VALUES (?, ?, ?, ?)",
            (
                "Loreto",
                "Maynas",
                "Iquitos",
                json.dumps(
                    {
                        "nombre": "Colegio Ejemplo",
                        "codigo_local": "000123",
                        "longitud": -73.253,
                        "latitud": -3.749,
                    }
                ),
            ),
        )


def _assert_metadata(document: dict) -> None:
    assert document["formato"] == "eduescale"
    assert document["version"] == 1
    metadata = document["metadatos"]
    assert metadata["cantidad_colegios"] == 1
    assert metadata["crs"] == "EPSG:4326"
    assert metadata["aplicacion"] == "EduEscale Desktop"
    assert metadata["version_aplicacion"]
    assert datetime.fromisoformat(metadata["generado_en"].replace("Z", "+00:00"))
    assert document["colegios"][0]["nombre"] == "Colegio Ejemplo"


def test_exports_json_with_validated_metadata(tmp_path):
    database_path = tmp_path / "schools.sqlite3"
    output_path = tmp_path / "schools.eduescale"
    _database(database_path)

    export_eduescale(database_path, output_path)

    _assert_metadata(json.loads(output_path.read_text(encoding="utf-8")))


def test_exports_optional_compressed_zip(tmp_path):
    database_path = tmp_path / "schools.sqlite3"
    output_path = tmp_path / "schools.eduescale"
    _database(database_path)

    export_eduescale(database_path, output_path, compressed=True)

    with zipfile.ZipFile(output_path) as archive:
        assert archive.namelist() == ["colegios.json"]
        _assert_metadata(json.loads(archive.read("colegios.json")))


def test_exports_authenticated_encrypted_payload(tmp_path):
    database_path = tmp_path / "schools.sqlite3"
    output_path = tmp_path / "schools.eduescale"
    _database(database_path)
    password = "contraseña segura"

    export_eduescale(database_path, output_path, password=password)

    contents = output_path.read_bytes()
    header = contents[: HEADER.size]
    magic, version, iterations, salt, nonce = HEADER.unpack(header)
    assert magic == MAGIC
    assert version == 1
    assert iterations == 600_000
    ciphertext, tag = contents[HEADER.size : -16], contents[-16:]
    key = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt, iterations, dklen=32
    )
    decryptor = Cipher(algorithms.AES(key), modes.GCM(nonce, tag)).decryptor()
    decryptor.authenticate_additional_data(header)
    plaintext = decryptor.update(ciphertext) + decryptor.finalize()
    _assert_metadata(json.loads(zlib.decompress(plaintext)))


def test_encrypted_export_requires_a_minimum_password(tmp_path):
    database_path = tmp_path / "schools.sqlite3"
    output_path = tmp_path / "schools.eduescale"
    _database(database_path)

    with pytest.raises(ValueError, match="al menos 8 caracteres"):
        export_eduescale(database_path, output_path, password="corta")

    assert not output_path.exists()
