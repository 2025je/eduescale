import json
import sqlite3
from contextlib import closing

import pytest
from sqlalchemy import insert, inspect

from eduescale.application.use_cases.offline_database import (
    OfflineDatabaseUseCases,
)
from eduescale.infrastructure.persistence import offline_database
from eduescale.infrastructure.persistence.local_database import (
    LocalSearchFilter,
    SearchCancelled,
    SqliteOfflineDatabaseRepository,
    filter_local_database,
    read_local_database_metadata,
    summarize_local_database_by_location,
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


def test_uses_indexed_sql_filters_for_new_offline_databases(monkeypatch, tmp_path):
    source_path = tmp_path / "indexed-escale.sqlite3"
    output_path = tmp_path / "filtered.sqlite3"
    engine = offline_database.create_database_engine(source_path)
    offline_database.metadata.create_all(engine)
    with engine.begin() as connection:
        connection.execute(
            insert(offline_database.records),
            [
                {
                    "region": "Loreto",
                    "province": "Maynas",
                    "district": "Iquitos",
                    "payload": json.dumps(
                        {
                            "nombre": "Colegio indexado",
                            "gestion": "pública",
                            "nivel": "Primaria",
                        }
                    ),
                    **offline_database.record_search_keys(
                        "Loreto",
                        "Maynas",
                        "Iquitos",
                        {"gestion": "pública", "nivel": "Primaria"},
                    ),
                },
                {
                    "region": "Lima",
                    "province": "Lima",
                    "district": "Miraflores",
                    "payload": json.dumps(
                        {
                            "nombre": "Otro colegio",
                            "gestion": "privada",
                            "nivel": "secundaria",
                        }
                    ),
                    **offline_database.record_search_keys(
                        "Lima",
                        "Lima",
                        "Miraflores",
                        {"gestion": "privada", "nivel": "secundaria"},
                    ),
                },
            ],
        )
    index_names = {index["name"] for index in inspect(engine).get_indexes("records")}
    engine.dispose()
    assert {"ix_records_location", "ix_records_management_level"} <= index_names

    def reject_python_filter(*args):
        pytest.fail(f"Indexed database invoked the Python filter with {args!r}.")

    monkeypatch.setattr(
        "eduescale.infrastructure.persistence.local_database._matches_filters",
        reject_python_filter,
    )
    total, matched = filter_local_database(
        source_path,
        output_path,
        [
            LocalSearchFilter(
                region=" loreto ",
                management=("PÚBLICA",),
                level=("primaria",),
            )
        ],
        cancelled=lambda: False,
        progress=lambda *_: None,
    )

    assert (total, matched) == (2, 1)
    with closing(sqlite3.connect(output_path)) as database:
        assert database.execute(
            "SELECT json_extract(payload, '$.nombre') FROM records"
        ).fetchone() == ("Colegio indexado",)


def test_indexed_search_accepts_accented_and_legacy_location_keys(tmp_path):
    source_path = tmp_path / "legacy-indexed-escale.sqlite3"
    output_path = tmp_path / "filtered.sqlite3"
    engine = offline_database.create_database_engine(source_path)
    offline_database.metadata.create_all(engine)
    records = []
    for name, old_key in (("Colegio sin tilde", "ancash"), ("Colegio con tilde", "áncash")):
        keys = offline_database.record_search_keys(
            "ÁNCASH",
            "HUARAZ",
            "HUARAZ",
            {"gestion": "pública", "nivel": "primaria"},
        )
        keys["region_key"] = old_key
        records.append(
            {
                "region": "ÁNCASH",
                "province": "HUARAZ",
                "district": "HUARAZ",
                "payload": json.dumps({"nombre": name}),
                **keys,
            }
        )
    with engine.begin() as connection:
        connection.execute(insert(offline_database.records), records)
    engine.dispose()

    total, matched = filter_local_database(
        source_path,
        output_path,
        [LocalSearchFilter(region="ANCASH")],
        cancelled=lambda: False,
        progress=lambda *_: None,
    )

    assert (total, matched) == (2, 2)


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
        "schema_version": "1",
    }


def test_inspects_school_totals_by_region_province_and_district(tmp_path):
    database_path = tmp_path / "schools.sqlite3"
    _create_database(
        database_path,
        [
            ("ANCASH", "Huaraz", "Huaraz", {"nombre": "Colegio 1"}),
            ("Áncash", "Huaraz", "Huaraz", {"nombre": "Colegio 2"}),
            ("Loreto", "Maynas", "Iquitos", {"nombre": "Colegio 3"}),
            ("Loreto", "Maynas", "Iquitos", {"nombre": "Colegio 4"}),
            ("Lima", "Lima", "Miraflores", {"nombre": "Colegio 3"}),
        ],
    )

    metadata, summary = OfflineDatabaseUseCases(
        SqliteOfflineDatabaseRepository()
    ).inspect(database_path)

    assert metadata == {}
    assert summary.total_schools == 5
    assert summary.department_count == 3
    assert summary.province_count == 3
    assert summary.district_count == 3
    assert [
        (location.region, location.province, location.district, location.schools)
        for location in summary.locations
    ] == [
        ("ÁNCASH", "Huaraz", "Huaraz", 2),
        ("Lima", "Lima", "Miraflores", 1),
        ("Loreto", "Maynas", "Iquitos", 2),
    ]


def test_sorts_location_summary_using_spanish_collation(tmp_path):
    database_path = tmp_path / "spanish-sort.sqlite3"
    _create_database(
        database_path,
        [
            ("AREQUIPA", "A", "A", {}),
            ("APURÍMAC", "A", "A", {}),
            ("ÁNCASH", "A", "A", {}),
            ("HUÁNUCO", "A", "A", {}),
            ("JUNÍN", "A", "A", {}),
            ("NAZCA", "A", "A", {}),
            ("ÑAHUI", "A", "A", {}),
            ("OCOÑA", "A", "A", {}),
            ("SAN MARTÍN", "A", "A", {}),
        ],
    )

    locations = summarize_local_database_by_location(database_path)

    assert [location[0] for location in locations] == [
        "ÁNCASH",
        "APURÍMAC",
        "AREQUIPA",
        "HUÁNUCO",
        "JUNÍN",
        "NAZCA",
        "ÑAHUI",
        "OCOÑA",
        "SAN MARTÍN",
    ]


def test_rejects_database_without_eduescale_records_table(tmp_path):
    database_path = tmp_path / "invalid.sqlite3"
    with closing(sqlite3.connect(database_path)):
        pass

    with pytest.raises(ValueError, match="tabla records"):
        validate_local_database(database_path)


def test_rejects_database_with_mismatched_record_statistics(tmp_path):
    database_path = tmp_path / "inconsistent.sqlite3"
    _create_database(
        database_path,
        [("Loreto", "Maynas", "Iquitos", {"nombre": "Ejemplo"})],
    )
    write_local_database_metadata(
        database_path,
        updated_at="2026-11-12T12:30:00+00:00",
        total_records=2,
        unique_records=2,
        pages=1,
    )

    with pytest.raises(ValueError, match="cantidad de colegios no coincide"):
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
