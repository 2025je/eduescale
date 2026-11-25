import base64
import hashlib
import json
import sqlite3
import zipfile
import zlib
from datetime import datetime
from pathlib import Path

import pytest
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from jsonschema import Draft202012Validator, FormatChecker

from eduescale.application.export_policy import (
    EDUESCALE_FORMAT_VERSION,
    eduescale_application_version,
)
from eduescale.infrastructure.exports.eduescale import export_eduescale
from eduescale.infrastructure.exports.encryption import HEADER, MAGIC
from scripts.generate_format_contract_fixtures import generate_fixtures

SCHEMA_PATH = (
    Path(__file__).resolve().parents[4] / "format" / "eduescale-v1.schema.json"
)
CASES_PATH = (
    Path(__file__).resolve().parents[4] / "test-fixtures" / "eduescale-v1-cases.json"
)


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
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(document)
    assert document["formato"] == "eduescale"
    assert document["version"] == EDUESCALE_FORMAT_VERSION == 1
    metadata = document["metadatos"]
    assert metadata["cantidad_colegios"] == 1
    assert metadata["crs"] == "EPSG:4326"
    assert metadata["aplicacion"] == "EduEscale Desktop"
    assert metadata["version_aplicacion"] == eduescale_application_version()
    assert datetime.fromisoformat(metadata["generado_en"].replace("Z", "+00:00"))
    assert document["colegios"][0]["nombre"] == "Colegio Ejemplo"


def test_exports_json_with_validated_metadata(tmp_path):
    database_path = tmp_path / "schools.sqlite3"
    output_path = tmp_path / "schools.eduescale"
    _database(database_path)

    export_eduescale(database_path, output_path)

    _assert_metadata(json.loads(output_path.read_text(encoding="utf-8")))


def test_shared_format_cases_match_schema():
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    cases = json.loads(CASES_PATH.read_text(encoding="utf-8"))

    for case in cases:
        document = case["document"]
        valid = validator.is_valid(document)
        if valid and "metadatos" in document:
            valid = document["metadatos"]["cantidad_colegios"] == len(
                document["colegios"]
            )
        assert valid is case["valid"], case["name"]


def test_generated_android_fixtures_match_contract(tmp_path):
    generate_fixtures(tmp_path)
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    for name in ("json", "zip", "encrypted"):
        contents = base64.b64decode(
            (tmp_path / f"desktop-contract-{name}.base64").read_bytes().strip(),
            validate=True,
        )
        if name == "zip":
            output_path = tmp_path / "archive.zip"
            output_path.write_bytes(contents)
            with zipfile.ZipFile(output_path) as archive:
                assert archive.namelist() == ["colegios.json"]
                contents = archive.read("colegios.json")
        elif name == "encrypted":
            header = contents[: HEADER.size]
            magic, version, iterations, salt, nonce = HEADER.unpack(header)
            assert (magic, version, iterations) == (MAGIC, 1, 600_000)
            key = hashlib.pbkdf2_hmac(
                "sha256", "contraseña segura".encode(), salt, iterations, dklen=32
            )
            decryptor = Cipher(
                algorithms.AES(key), modes.GCM(nonce, contents[-16:])
            ).decryptor()
            decryptor.authenticate_additional_data(header)
            contents = zlib.decompress(
                decryptor.update(contents[HEADER.size : -16]) + decryptor.finalize()
            )
        document = json.loads(contents)
        validator.validate(document)
        assert document["metadatos"]["cantidad_colegios"] == len(document["colegios"])


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


def test_shared_android_fixture_uses_the_desktop_encrypted_format():
    fixture_path = (
        Path(__file__).resolve().parents[4]
        / "test-fixtures"
        / "desktop-encrypted-school-data.base64"
    )
    contents = base64.b64decode(
        fixture_path.read_text(encoding="ascii").strip(),
        validate=True,
    )
    header = contents[: HEADER.size]
    magic, version, iterations, salt, nonce = HEADER.unpack(header)

    assert magic == MAGIC
    assert version == 1
    ciphertext, tag = contents[HEADER.size : -16], contents[-16:]
    key = hashlib.pbkdf2_hmac("sha256", b"fixture password", salt, iterations, dklen=32)
    decryptor = Cipher(algorithms.AES(key), modes.GCM(nonce, tag)).decryptor()
    decryptor.authenticate_additional_data(header)
    plaintext = decryptor.update(ciphertext) + decryptor.finalize()
    document = json.loads(zlib.decompress(plaintext))

    assert document["formato"] == "eduescale"
    assert document["version"] == 1
    assert document["metadatos"]["cantidad_colegios"] == 1
    assert document["colegios"][0]["nombre"] == "Fixture cruzado"
