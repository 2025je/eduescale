import json
import sqlite3
from contextlib import closing

import pytest

from eduescale.gui.local_database import (
    LocalSearchFilter,
    SearchCancelled,
    filter_local_database,
    read_local_database_metadata,
    validate_local_database,
    write_local_database_metadata,
)


def _create_database(path, records):
    with closing(sqlite3.connect(path)) as database:
        database.execute(
            """
            CREATE TABLE records (
                region TEXT NOT NULL,
                province TEXT NOT NULL,
                district TEXT NOT NULL,
                payload TEXT NOT NULL
            )
            """
        )
        database.executemany(
            "INSERT INTO records VALUES (?, ?, ?, ?)",
            [
                (region, province, district, json.dumps(payload))
                for region, province, district, payload in records
            ],
        )
        database.commit()


def test_filters_local_records_by_location_management_and_level(tmp_path):
    source_path = tmp_path / "escale base.sqlite3"
    output_path = tmp_path / "filtered.sqlite3"
    _create_database(
        source_path,
        [
            (
                "Loreto",
                "Maynas",
                "Iquitos",
                {
                    "nombre": "Publica primaria",
                    "gestion": "publica",
                    "nivel": "primaria",
                },
            ),
            (
                "Loreto",
                "Maynas",
                "Iquitos",
                {
                    "nombre": "Privada secundaria",
                    "gestion": "privada",
                    "nivel": "secundaria",
                },
            ),
            (
                "Lima",
                "Lima",
                "Miraflores",
                {
                    "nombre": "Privada secundaria Lima",
                    "gestion": "privada",
                    "nivel": "secundaria",
                },
            ),
            (
                "Lima",
                "Lima",
                "Miraflores",
                {
                    "nombre": "Publica secundaria Lima",
                    "gestion": "publica",
                    "nivel": "secundaria",
                },
            ),
        ],
    )
    progress = []

    total, matched = filter_local_database(
        source_path,
        output_path,
        [
            LocalSearchFilter(
                region=" loreto ",
                management=("publica",),
                level=("primaria",),
            ),
            LocalSearchFilter(
                region="LIMA",
                management=("privada",),
                level=("secundaria",),
            ),
        ],
        cancelled=lambda: False,
        progress=lambda processed, count, matches: progress.append(
            (processed, count, matches)
        ),
    )

    assert (total, matched) == (4, 2)
    assert progress[-1] == (4, 4, 2)
    with closing(sqlite3.connect(output_path)) as database:
        names = [
            row[0]
            for row in database.execute(
                "SELECT json_extract(payload, '$.nombre') FROM records ORDER BY rowid"
            )
        ]
    assert names == ["Publica primaria", "Privada secundaria Lima"]


def test_validates_database_schema_and_persists_update_statistics(tmp_path):
    database_path = tmp_path / "escale.sqlite3"
    _create_database(
        database_path,
        [("Loreto", "Maynas", "Iquitos", {"nombre": "Ejemplo"})],
    )

    validate_local_database(database_path)
    write_local_database_metadata(
        database_path,
        updated_at="2026-11-12T12:30:00+00:00",
        total_records=3,
        unique_records=1,
        pages=1,
    )

    assert read_local_database_metadata(database_path) == {
        "source": "ESCALE",
        "updated_at": "2026-11-12T12:30:00+00:00",
        "total_records": "3",
        "unique_records": "1",
        "pages": "1",
    }


def test_rejects_database_without_eduescale_records_table(tmp_path):
    database_path = tmp_path / "invalid.sqlite3"
    with closing(sqlite3.connect(database_path)):
        pass

    with pytest.raises(ValueError, match="tabla records"):
        validate_local_database(database_path)


def test_rejects_non_text_local_database_metadata(tmp_path):
    database_path = tmp_path / "invalid-metadata.sqlite3"
    _create_database(database_path, [])
    with closing(sqlite3.connect(database_path)) as database:
        database.execute("CREATE TABLE metadata (key, value)")
        database.execute("INSERT INTO metadata VALUES ('pages', 1)")
        database.commit()

    with pytest.raises(TypeError, match="tabla metadata"):
        read_local_database_metadata(database_path)


def test_cancels_local_database_filtering(tmp_path):
    source_path = tmp_path / "escale.sqlite3"
    _create_database(
        source_path,
        [("Loreto", "Maynas", "Iquitos", {"gestion": "publica"})],
    )

    with pytest.raises(SearchCancelled):
        filter_local_database(
            source_path,
            tmp_path / "filtered.sqlite3",
            [LocalSearchFilter(management=("publica",))],
            cancelled=lambda: True,
            progress=lambda *_: None,
        )
