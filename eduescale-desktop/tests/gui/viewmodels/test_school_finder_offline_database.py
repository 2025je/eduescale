import sqlite3
from contextlib import closing
from pathlib import Path

from PySide6.QtCore import QUrl

from eduescale.domain.schools import SchoolFilters
from eduescale.gui.application import create_view_model
from eduescale.gui.services.progress import ProgressMetrics


def test_default_offline_database_uses_application_data_directory(
    monkeypatch,
    tmp_path,
    fake_location_service,
    fake_scrape_runner,
    fake_export_runner,
    fake_local_database_search_runner,
):
    app_data_directory = tmp_path / "AppData" / "Local"
    monkeypatch.setenv("LOCALAPPDATA", str(app_data_directory))
    view_model = create_view_model(
        fake_location_service,
        fake_scrape_runner,
        fake_export_runner,
        fake_local_database_search_runner,
    )

    try:
        default_path = Path(QUrl(view_model.defaultOfflineDatabaseUrl).toLocalFile())

        assert default_path == app_data_directory / "EduEscale" / "escale.sqlite3"
    finally:
        view_model.shutdown()

def test_offline_source_filters_the_selected_local_database(
    view_model_components,
    tmp_path,
):
    view_model, scrape_runner, _errors = view_model_components
    local_runner = (
        view_model._school_search_workflow._local_database_search_runner
    )
    database_path = tmp_path / "offline.sqlite3"
    with closing(sqlite3.connect(database_path)) as database:
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
                ("Loreto", "Maynas", "Iquitos", "{}"),
                ("Loreto", "Maynas", "Iquitos", "{}"),
                ("Lima", "Lima", "Miraflores", "{}"),
            ],
        )
        database.commit()

    assert view_model.selectOfflineDatabase(
        QUrl.fromLocalFile(str(database_path)).toString()
    )
    view_model.startScrape(
        QUrl.fromLocalFile(str(tmp_path / "schools.xlsx")).toString(),
        [
            {
                "region_code": "15",
                "management": "publica",
                "level": "primaria",
            }
        ],
    )

    assert view_model.dataSource == "offline"
    assert view_model._preferences.offline_database_path() == database_path
    summary = view_model.offlineDatabaseLocationSummary
    assert summary["total_schools"] == 3
    assert summary["department_count"] == 2
    assert summary["district_count"] == 2
    assert scrape_runner.started_requests == []
    assert local_runner.started_requests[0][0] == database_path
    offline_filter = local_runner.started_requests[0][1][0]
    assert offline_filter.region == view_model.mapDepartmentMetadata["15"]["name"]
    assert offline_filter.management == ("publica",)
    assert offline_filter.level == ("primaria",)
    local_runner.progressChanged.emit(
        ProgressMetrics(received=2, total=5, unique=1, pages=0)
    )
    assert view_model.progressMessage == "Base offline · 2 de 5 registros · 1 coinciden"

def test_updates_offline_database_and_records_download_statistics(
    view_model_components,
    tmp_path,
):
    view_model, scrape_runner, _errors = view_model_components
    database_path = tmp_path / "offline.sqlite3"
    staged_path = tmp_path / "staged.sqlite3"

    assert view_model.updateOfflineDatabase(
        QUrl.fromLocalFile(str(database_path)).toString()
    )
    assert scrape_runner.started_requests == [
        (database_path, [SchoolFilters()], "database_update")
    ]

    with closing(sqlite3.connect(staged_path)) as database:
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
        database.execute(
            "INSERT INTO records VALUES (?, ?, ?, ?)",
            ("Loreto", "Maynas", "Iquitos", '{"nombre":"Ejemplo"}'),
        )
        database.commit()

    scrape_runner.completed.emit(str(staged_path), 2, 1, 1)

    assert database_path.is_file()
    assert not staged_path.exists()
    assert "1 colegio único" in view_model.statusText
    assert "1 página" in view_model.statusText
    assert "1 colegio" in view_model.offlineDatabaseStats
    assert "2 resultados" in view_model.offlineDatabaseStats

def test_cancelled_offline_database_update_preserves_existing_file(
    view_model_components,
    tmp_path,
):
    view_model, scrape_runner, _errors = view_model_components
    database_path = tmp_path / "offline.sqlite3"
    database_path.write_bytes(b"existing database")

    assert view_model.updateOfflineDatabase(
        QUrl.fromLocalFile(str(database_path)).toString()
    )
    scrape_runner.cancelled.emit()

    assert database_path.read_bytes() == b"existing database"
    assert view_model._database_update_target is None
    assert "base anterior se conservó" in view_model.statusText

def test_invalid_offline_update_does_not_replace_existing_database(
    view_model_components,
    monkeypatch,
    tmp_path,
):
    view_model, scrape_runner, _errors = view_model_components
    database_path = tmp_path / "offline.sqlite3"
    database_path.write_bytes(b"existing database")

    def reject_metadata(*_args, **_kwargs):
        raise TypeError("metadata inválida")

    monkeypatch.setattr(
        view_model._offline_database_use_cases,
        "publish_download",
        reject_metadata,
    )
    assert view_model.updateOfflineDatabase(
        QUrl.fromLocalFile(str(database_path)).toString()
    )
    scrape_runner.completed.emit(str(tmp_path / "staged.sqlite3"), 2, 1, 1)

    assert database_path.read_bytes() == b"existing database"
    assert not view_model.scrapeRunning
    assert view_model._database_update_target is None
    assert "No se pudo actualizar la base offline" in view_model.statusText

def test_offline_search_keeps_database_update_statistics(
    view_model_components, tmp_path
):
    view_model, _scrape_runner, _errors = view_model_components
    view_model._offline_database_stats = (
        "1 colegio único de 2 resultados · 1 página verificada · actualizada ayer"
    )

    view_model._search_completed(str(tmp_path / "filtered.sqlite3"), 2, 1, 0, True)

    assert "1 de 2 colegios coinciden" in view_model.statusText
    assert view_model.offlineDatabaseStats == (
        "1 colegio único de 2 resultados · 1 página verificada · actualizada ayer"
    )
