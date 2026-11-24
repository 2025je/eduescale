from pathlib import Path
from typing import cast

import pytest
from PySide6.QtCore import QCoreApplication, QSettings, QUrl

from eduescale.application.use_cases.offline_database import (
    OfflineDatabaseUseCases,
)
from eduescale.domain.schools import SchoolFilters
from eduescale.gui.application import create_view_model
from eduescale.gui.contracts.ports import (
    DataSource,
    OfflineDatabaseDownloadPort,
    ScrapeRunnerPort,
)
from eduescale.gui.services.district_map_catalog import DistrictMapCatalog
from eduescale.gui.services.export_history import QSettingsExportHistoryStore
from eduescale.gui.services.export_request_service import ExportRequestService
from eduescale.gui.services.offline_database_update import OfflineDatabaseUpdateService
from eduescale.gui.services.preferences import QSettingsDesktopPreferences
from eduescale.gui.services.saved_search_service import PortableSavedSearchService
from eduescale.gui.services.school_search_service import (
    SchoolSearchRequest,
    SchoolSearchRequestService,
)
from eduescale.gui.services.school_search_workflow import SchoolSearchWorkflow
from eduescale.gui.viewmodels import SchoolFinderViewModel
from eduescale.infrastructure.persistence.local_database import (
    SqliteOfflineDatabaseRepository,
)


def test_create_view_model_accepts_injected_services(
    fake_location_service,
    fake_scrape_runner,
    fake_export_runner,
    fake_local_database_search_runner,
):
    location_service = fake_location_service
    scrape_runner = fake_scrape_runner
    export_runner = fake_export_runner
    local_database_search_runner = fake_local_database_search_runner
    view_model = create_view_model(
        location_service,
        scrape_runner,
        export_runner,
        local_database_search_runner,
    )

    try:
        assert view_model._location_service is location_service
        assert view_model._school_search_workflow._scrape_runner is scrape_runner
        assert view_model._export_runner is export_runner
        assert (
            view_model._school_search_workflow._local_database_search_runner
            is local_database_search_runner
        )
    finally:
        view_model.shutdown()


def test_fake_scrape_runner_satisfies_split_runner_ports(fake_scrape_runner):
    scrape_runner = fake_scrape_runner

    assert isinstance(scrape_runner, ScrapeRunnerPort)
    assert isinstance(scrape_runner, OfflineDatabaseDownloadPort)


def test_search_workflow_rejects_overlapping_operations_without_replacing_active_one(
    fake_scrape_runner,
    fake_local_database_search_runner,
):
    scrape_runner = fake_scrape_runner
    local_database_search_runner = fake_local_database_search_runner
    workflow = SchoolSearchWorkflow(scrape_runner, local_database_search_runner)
    request = SchoolSearchRequest(
        output_path=Path("schools.xlsx"),
        filter_sets=[SchoolFilters(management=["publica"], level=["primaria"])],
        offline_database_path=None,
        local_filters=None,
        summary="",
    )

    with pytest.raises(ValueError, match="Unknown school data source"):
        workflow.start_search(request, cast(DataSource, "unknown"))

    workflow.start_search(request, "online")

    with pytest.raises(RuntimeError, match="another operation is active"):
        workflow.start_database_update(Path("database.sqlite3"))

    with pytest.raises(RuntimeError, match="another operation is active"):
        workflow.start_search(request, "offline")

    workflow.cancel()

    assert len(scrape_runner.started_requests) == 1
    assert scrape_runner.cancel_requests == 1
    assert local_database_search_runner.started_requests == []
    assert local_database_search_runner.cancel_requests == 0
    workflow.shutdown()


def test_restores_export_folder_and_window_size(
    fake_location_service,
    fake_scrape_runner,
    fake_export_runner,
    fake_local_database_search_runner,
    tmp_path,
):
    application = QCoreApplication.instance() or QCoreApplication([])
    settings = QSettings(
        str(tmp_path / "settings.ini"),
        QSettings.Format.IniFormat,
    )
    settings.setValue("export/lastDirectory", str(tmp_path))
    settings.setValue("window/width", 400)
    settings.setValue("window/height", "invalid")
    district_map_catalog = DistrictMapCatalog()
    offline_database_use_cases = OfflineDatabaseUseCases(
        SqliteOfflineDatabaseRepository()
    )
    scrape_runner = fake_scrape_runner
    view_model = SchoolFinderViewModel(
        location_service=fake_location_service,
        school_search_workflow=SchoolSearchWorkflow(
            scrape_runner,
            fake_local_database_search_runner,
        ),
        preferences=QSettingsDesktopPreferences(settings),
        export_runner=fake_export_runner,
        offline_database_update_service=OfflineDatabaseUpdateService(
            offline_database_use_cases,
            scrape_runner,
        ),
        export_history_store=QSettingsExportHistoryStore(settings),
        district_map_catalog=district_map_catalog,
        saved_search_service=PortableSavedSearchService(district_map_catalog),
        school_search_service=SchoolSearchRequestService(
            district_map_catalog,
            offline_database_use_cases,
        ),
        export_request_service=ExportRequestService(),
        offline_database_use_cases=offline_database_use_cases,
        data_directory=tmp_path,
    )

    try:
        assert Path(QUrl(view_model.defaultOutputUrl).toLocalFile()) == (
            tmp_path / "colegios_escale.xlsx"
        )
        assert view_model.windowWidth == 560
        assert view_model.windowHeight == 760

        view_model.saveWindowSize(900, 700)

        assert settings.value("window/width", type=int) == 900
        assert settings.value("window/height", type=int) == 700

        view_model.startScrape(
            QUrl.fromLocalFile(str(tmp_path / "export.xlsx")).toString(),
            [{"management": "publica", "level": "primaria"}],
        )

        assert settings.value("export/lastDirectory") == str(tmp_path)
        assert application is not None
    finally:
        view_model.shutdown()


def test_restores_legacy_theme_and_persists_theme_mode(
    fake_location_service,
    fake_scrape_runner,
    fake_export_runner,
    fake_local_database_search_runner,
    tmp_path,
):
    application = QCoreApplication.instance() or QCoreApplication([])
    settings = QSettings(
        str(tmp_path / "settings.ini"),
        QSettings.Format.IniFormat,
    )
    settings.setValue("appearance/darkTheme", True)
    district_map_catalog = DistrictMapCatalog()
    offline_database_use_cases = OfflineDatabaseUseCases(
        SqliteOfflineDatabaseRepository()
    )
    scrape_runner = fake_scrape_runner
    view_model = SchoolFinderViewModel(
        location_service=fake_location_service,
        school_search_workflow=SchoolSearchWorkflow(
            scrape_runner,
            fake_local_database_search_runner,
        ),
        preferences=QSettingsDesktopPreferences(settings),
        export_runner=fake_export_runner,
        offline_database_update_service=OfflineDatabaseUpdateService(
            offline_database_use_cases,
            scrape_runner,
        ),
        export_history_store=QSettingsExportHistoryStore(settings),
        district_map_catalog=district_map_catalog,
        saved_search_service=PortableSavedSearchService(district_map_catalog),
        school_search_service=SchoolSearchRequestService(
            district_map_catalog,
            offline_database_use_cases,
        ),
        export_request_service=ExportRequestService(),
        offline_database_use_cases=offline_database_use_cases,
        data_directory=tmp_path,
    )
    changes = []
    view_model.darkThemeChanged.connect(lambda: changes.append(view_model.darkTheme))

    try:
        assert view_model.darkTheme is True
        assert view_model.themeMode == "dark"

        view_model.setThemeMode("light")

        assert view_model.darkTheme is False
        assert view_model.themeMode == "light"
        assert settings.value("appearance/themeMode") == "light"
        assert changes == [False]
        assert application is not None
    finally:
        view_model.shutdown()
