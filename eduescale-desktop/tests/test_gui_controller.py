import json
import sqlite3
from contextlib import closing
from pathlib import Path
from types import SimpleNamespace

import pytest
from PySide6.QtCore import QCoreApplication, QObject, QSettings, QUrl, Signal

from eduescale.application.export_policy import android_data_path
from eduescale.application.use_cases.offline_database import (
    OfflineDatabaseUseCases,
)
from eduescale.domain.schools import SchoolFilters
from eduescale.gui import controller as controller_module
from eduescale.gui.application import create_controller
from eduescale.gui.controller import SchoolFinderController
from eduescale.gui.saved_searches import PortableSavedSearch
from eduescale.infrastructure.persistence.local_database import (
    SqliteOfflineDatabaseRepository,
)


class FakeLocationService(QObject):
    loaded = Signal(str, str, object)
    failed = Signal(str, str, str)

    def load(self, kind, parent_code=""):
        pass

    def shutdown(self):
        pass


class FakeScrapeRunner(QObject):
    started = Signal()
    progressChanged = Signal(int, int, int, str)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)
    cancelled = Signal()
    warning = Signal(str, str)

    def __init__(self):
        super().__init__()
        self.started_requests = []
        self.cancel_requests = 0

    def start(self, output_path, filter_sets):
        self.started_requests.append((output_path, filter_sets))

    def cancel(self):
        self.cancel_requests += 1

    def complete_database_download(self):
        return None

    def shutdown(self):
        pass


class FakeLocalDatabaseSearchRunner(QObject):
    started = Signal()
    progressChanged = Signal(int, int, int, str)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)
    cancelled = Signal()

    def __init__(self):
        super().__init__()
        self.started_requests = []
        self.cancel_requests = 0

    def start(self, source_path, filters):
        self.started_requests.append((source_path, filters))

    def cancel(self):
        self.cancel_requests += 1

    def shutdown(self):
        pass


class FakeExportRunner(QObject):
    started = Signal(str)
    completed = Signal(str, str)
    failed = Signal(str, str, str)

    def __init__(self):
        super().__init__()
        self.started_requests = []

    def start(
        self,
        export_type,
        database_path,
        output_path,
        password=None,
        compressed=False,
    ):
        request = (export_type, database_path, output_path)
        if password is not None or compressed:
            request += (password, compressed)
        self.started_requests.append(request)

    def shutdown(self):
        pass


def test_create_controller_accepts_injected_services():
    location_service = FakeLocationService()
    scrape_runner = FakeScrapeRunner()
    export_runner = FakeExportRunner()
    local_database_search_runner = FakeLocalDatabaseSearchRunner()
    controller = create_controller(
        location_service,
        scrape_runner,
        export_runner,
        local_database_search_runner,
    )

    try:
        assert controller._location_service is location_service
        assert controller._scrape_runner is scrape_runner
        assert controller._export_runner is export_runner
        assert controller._local_database_search_runner is local_database_search_runner
    finally:
        controller.shutdown()


@pytest.fixture
def controller_components(tmp_path):
    application = QCoreApplication.instance() or QCoreApplication([])
    location_service = FakeLocationService()
    scrape_runner = FakeScrapeRunner()
    local_database_search_runner = FakeLocalDatabaseSearchRunner()
    export_runner = FakeExportRunner()
    settings = QSettings(
        str(tmp_path / "settings.ini"),
        QSettings.Format.IniFormat,
    )
    controller = SchoolFinderController(
        location_service=location_service,
        scrape_runner=scrape_runner,
        settings=settings,
        export_runner=export_runner,
        local_database_search_runner=local_database_search_runner,
        offline_database_use_cases=OfflineDatabaseUseCases(
            SqliteOfflineDatabaseRepository()
        ),
        data_directory=tmp_path,
    )
    errors = []
    controller.errorRaised.connect(
        lambda title, details: errors.append((title, details))
    )
    yield controller, scrape_runner, errors
    controller.shutdown()
    assert application is not None


def test_restores_export_folder_and_window_size(tmp_path):
    application = QCoreApplication.instance() or QCoreApplication([])
    settings = QSettings(
        str(tmp_path / "settings.ini"),
        QSettings.Format.IniFormat,
    )
    settings.setValue("export/lastDirectory", str(tmp_path))
    settings.setValue("window/width", 400)
    settings.setValue("window/height", "invalid")
    controller = SchoolFinderController(
        location_service=FakeLocationService(),
        scrape_runner=FakeScrapeRunner(),
        settings=settings,
        export_runner=FakeExportRunner(),
        local_database_search_runner=FakeLocalDatabaseSearchRunner(),
        offline_database_use_cases=OfflineDatabaseUseCases(
            SqliteOfflineDatabaseRepository()
        ),
        data_directory=tmp_path,
    )

    try:
        assert Path(QUrl(controller.defaultOutputUrl).toLocalFile()) == (
            tmp_path / "colegios_escale.xlsx"
        )
        assert controller.windowWidth == 560
        assert controller.windowHeight == 760

        controller.saveWindowSize(900, 700)

        assert settings.value("window/width", type=int) == 900
        assert settings.value("window/height", type=int) == 700

        controller.startScrape(
            QUrl.fromLocalFile(str(tmp_path / "export.xlsx")).toString(),
            json.dumps([{"management": "publica", "level": "primaria"}]),
        )

        assert settings.value("export/lastDirectory") == str(tmp_path)
        assert application is not None
    finally:
        controller.shutdown()


def test_restores_legacy_theme_and_persists_theme_mode(tmp_path):
    application = QCoreApplication.instance() or QCoreApplication([])
    settings = QSettings(
        str(tmp_path / "settings.ini"),
        QSettings.Format.IniFormat,
    )
    settings.setValue("appearance/darkTheme", True)
    controller = SchoolFinderController(
        location_service=FakeLocationService(),
        scrape_runner=FakeScrapeRunner(),
        settings=settings,
        export_runner=FakeExportRunner(),
        local_database_search_runner=FakeLocalDatabaseSearchRunner(),
        offline_database_use_cases=OfflineDatabaseUseCases(
            SqliteOfflineDatabaseRepository()
        ),
        data_directory=tmp_path,
    )
    changes = []
    controller.darkThemeChanged.connect(lambda: changes.append(controller.darkTheme))

    try:
        assert controller.darkTheme is True
        assert controller.themeMode == "dark"

        controller.setThemeMode("light")

        assert controller.darkTheme is False
        assert controller.themeMode == "light"
        assert settings.value("appearance/themeMode") == "light"
        assert changes == [False]
        assert application is not None
    finally:
        controller.shutdown()


def test_imports_and_exports_portable_saved_searches(controller_components, tmp_path):
    controller, _scrape_runner, errors = controller_components
    location = controller._district_metadata["150122"]
    document = {
        "format": "eduescale.saved-searches",
        "version": 1,
        "searches": [
            {
                "name": "Lima centro",
                "department": location["department"],
                "province": location["province"],
                "district": location["district"],
                "department_code": None,
                "province_code": None,
                "district_code": None,
                "management": ["publica"],
                "levels": ["primaria"],
            }
        ],
    }
    imported = []
    controller.savedSearchesLoaded.connect(
        lambda value: imported.extend(json.loads(value))
    )
    path = tmp_path / "saved-searches.json"
    file_url = QUrl.fromLocalFile(str(path)).toString()

    assert controller.saveSavedSearches(file_url, json.dumps(document))
    assert controller.loadSavedSearches(file_url)

    assert imported == [
        {
            "name": "Lima centro",
            "regionCode": location["departmentCode"],
            "provinceCode": location["provinceCode"],
            "districtCode": "150122",
            "regionLabel": location["department"],
            "provinceLabel": location["province"],
            "districtLabel": location["district"],
            "management": "publica",
            "level": "primaria",
        }
    ]
    assert errors == []


def test_rejects_invalid_portable_saved_search_file(controller_components, tmp_path):
    controller, _scrape_runner, errors = controller_components
    path = tmp_path / "invalid.json"
    path.write_text(
        json.dumps(
            {
                "format": "eduescale.saved-searches",
                "version": 2,
                "searches": [{"name": "Lima"}],
            }
        ),
        encoding="utf-8",
    )

    assert not controller.loadSavedSearches(QUrl.fromLocalFile(str(path)).toString())
    assert errors
    assert errors[-1][0] == "No se pudieron importar las búsquedas"


def test_rejects_ambiguous_saved_search_location(controller_components):
    controller, _scrape_runner, _errors = controller_components
    locations_by_name = {}
    for location in controller._district_metadata.values():
        name = controller._normalized_label(location["district"])
        locations_by_name.setdefault(name, []).append(location["district"])
    ambiguous_names = [
        names[0] for names in locations_by_name.values() if len(names) > 1
    ]
    assert ambiguous_names

    with pytest.raises(ValueError, match="No se pudo resolver de forma única"):
        controller._normalize_saved_searches(
            [PortableSavedSearch(name="Ubicación ambigua", district=ambiguous_names[0])]
        )


def test_rejects_inconsistent_saved_search_location_codes(controller_components):
    controller, _scrape_runner, _errors = controller_components
    location = controller._district_metadata["150122"]
    other_province_code = next(
        code
        for code in controller._map_province_metadata
        if code != location["provinceCode"]
    )
    other_department_code = next(
        code
        for code in controller._map_department_metadata
        if code != location["departmentCode"]
    )

    with pytest.raises(ValueError, match="no corresponde al distrito"):
        controller._normalize_saved_searches(
            [
                PortableSavedSearch(
                    name="Códigos inconsistentes",
                    district_code="150122",
                    province_code=other_province_code,
                )
            ]
        )
    with pytest.raises(ValueError, match="no corresponde al distrito"):
        controller._normalize_saved_searches(
            [
                PortableSavedSearch(
                    name="Departamento inconsistente",
                    district_code="150122",
                    department_code=other_department_code,
                )
            ]
        )
    with pytest.raises(ValueError, match="no reconocido"):
        controller._normalize_saved_searches(
            [PortableSavedSearch(name="Distrito inválido", district_code="999999")]
        )


def test_persists_only_the_latest_50_export_metadata_entries(controller_components):
    controller, _scrape_runner, _errors = controller_components
    controller._last_search_summary = "Lima / Miraflores · primaria"

    for index in range(55):
        controller._record_export_history(
            "excel",
            f"colegios_{index}.xlsx",
            "Completada",
        )

    assert len(controller.exportHistory) == 50
    assert controller.exportHistory[0]["file"] == "colegios_54.xlsx"
    assert controller.exportHistory[-1]["file"] == "colegios_5.xlsx"
    assert controller.exportHistory[0]["filters"] == ("Lima / Miraflores · primaria")
    assert not any("password" in key for key in controller.exportHistory[0])


def test_default_output_url_avoids_overwriting_existing_exports(
    controller_components,
    tmp_path,
):
    controller, _scrape_runner, _errors = controller_components
    output_path = tmp_path / "colegios_escale.xlsx"
    output_path.write_bytes(b"existing Excel")
    android_data_path(output_path).write_bytes(b"existing EduEscale")
    output_path.with_suffix(".pdf").write_bytes(b"existing PDF")
    controller._settings.setValue("export/lastDirectory", str(tmp_path))

    next_output = Path(QUrl(controller.defaultOutputUrl).toLocalFile())

    assert next_output == tmp_path / "colegios_escale_1.xlsx"


def test_management_selection_is_required(controller_components):
    controller, scrape_runner, errors = controller_components
    controller.startScrape(
        "file:///tmp/schools.xlsx",
        json.dumps([{"management": "", "level": "primaria"}]),
    )

    assert len(errors) == 1
    assert "Gestión" in errors[0][1]
    assert scrape_runner.started_requests == []


def test_education_level_selection_is_required(controller_components):
    controller, scrape_runner, errors = controller_components
    controller.startScrape(
        "file:///tmp/schools.xlsx",
        json.dumps([{"management": "publica", "level": ""}]),
    )

    assert len(errors) == 1
    assert "Nivel educativo" in errors[0][1]
    assert scrape_runner.started_requests == []


def test_default_offline_database_uses_application_data_directory(
    monkeypatch,
    tmp_path,
):
    app_data_directory = tmp_path / "AppData" / "Local"
    monkeypatch.setenv("LOCALAPPDATA", str(app_data_directory))
    controller = create_controller(
        FakeLocationService(),
        FakeScrapeRunner(),
        FakeExportRunner(),
        FakeLocalDatabaseSearchRunner(),
    )

    try:
        default_path = Path(QUrl(controller.defaultOfflineDatabaseUrl).toLocalFile())

        assert default_path == app_data_directory / "EduEscale" / "escale.sqlite3"
    finally:
        controller.shutdown()


def test_rejects_non_local_output_urls(controller_components):
    controller, scrape_runner, errors = controller_components
    controller.startScrape(
        "https://example.invalid/schools.xlsx",
        json.dumps([{"management": "publica", "level": "primaria"}]),
    )

    assert errors == [
        ("Ruta no válida", "Selecciona un archivo local para guardar el Excel.")
    ]
    assert scrape_runner.started_requests == []


def test_starts_scrape_with_multiple_filter_sets(controller_components):
    controller, scrape_runner, _errors = controller_components
    controller.startScrape(
        "file:///tmp/schools.xlsx",
        json.dumps(
            [
                {
                    "region_code": "01",
                    "management": "publica",
                    "level": "primaria",
                },
                {
                    "region_code": "02",
                    "management": "privada",
                    "level": "primaria,secundaria",
                },
            ]
        ),
    )

    assert len(scrape_runner.started_requests) == 1
    filters = scrape_runner.started_requests[0][1]
    assert len(filters) == 2
    assert filters[0].region_code == "01"
    assert filters[1].region_code == "02"
    assert filters[1].level == ("primaria", "secundaria")


def test_offline_source_filters_the_selected_local_database(
    controller_components,
    tmp_path,
):
    controller, scrape_runner, _errors = controller_components
    local_runner = controller._local_database_search_runner
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

    assert controller.selectOfflineDatabase(
        QUrl.fromLocalFile(str(database_path)).toString()
    )
    controller.startScrape(
        QUrl.fromLocalFile(str(tmp_path / "schools.xlsx")).toString(),
        json.dumps(
            [
                {
                    "region_code": "15",
                    "management": "publica",
                    "level": "primaria",
                }
            ]
        ),
    )

    assert controller.dataSource == "offline"
    assert controller._settings.value("data/offlineDatabasePath") == str(database_path)
    summary = controller.offlineDatabaseLocationSummary
    assert summary["total_schools"] == 3
    assert summary["department_count"] == 2
    assert summary["district_count"] == 2
    assert scrape_runner.started_requests == []
    assert local_runner.started_requests[0][0] == database_path
    offline_filter = local_runner.started_requests[0][1][0]
    assert offline_filter.region == controller.mapDepartmentMetadata["15"]["name"]
    assert offline_filter.management == ("publica",)
    assert offline_filter.level == ("primaria",)


def test_updates_offline_database_and_records_download_statistics(
    controller_components,
    tmp_path,
):
    controller, scrape_runner, _errors = controller_components
    database_path = tmp_path / "offline.sqlite3"
    staged_path = tmp_path / "staged.sqlite3"

    assert controller.updateOfflineDatabase(
        QUrl.fromLocalFile(str(database_path)).toString()
    )
    assert scrape_runner.started_requests == [(database_path, [SchoolFilters()])]

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

    controller._scrape_completed(str(staged_path), 2, 1, 1)

    assert database_path.is_file()
    assert not staged_path.exists()
    assert "1 colegio único" in controller.statusText
    assert "1 página" in controller.statusText
    assert "1 colegio" in controller.offlineDatabaseStats
    assert "2 resultados" in controller.offlineDatabaseStats


def test_cancelled_offline_database_update_preserves_existing_file(
    controller_components,
    tmp_path,
):
    controller, _scrape_runner, _errors = controller_components
    database_path = tmp_path / "offline.sqlite3"
    database_path.write_bytes(b"existing database")

    assert controller.updateOfflineDatabase(
        QUrl.fromLocalFile(str(database_path)).toString()
    )
    controller._scrape_cancelled()

    assert database_path.read_bytes() == b"existing database"
    assert controller._database_update_target is None
    assert "base anterior se conservó" in controller.statusText


def test_invalid_offline_update_does_not_replace_existing_database(
    controller_components,
    monkeypatch,
    tmp_path,
):
    controller, _scrape_runner, _errors = controller_components
    database_path = tmp_path / "offline.sqlite3"
    database_path.write_bytes(b"existing database")

    def reject_metadata(*_args, **_kwargs):
        raise TypeError("metadata inválida")

    monkeypatch.setattr(
        controller._offline_database_use_cases,
        "publish_download",
        reject_metadata,
    )
    assert controller.updateOfflineDatabase(
        QUrl.fromLocalFile(str(database_path)).toString()
    )
    controller._scrape_completed(str(tmp_path / "staged.sqlite3"), 2, 1, 1)

    assert database_path.read_bytes() == b"existing database"
    assert not controller.scrapeRunning
    assert controller._database_update_target is None
    assert "No se pudo actualizar la base offline" in controller.statusText


def test_offline_search_keeps_database_update_statistics(
    controller_components, tmp_path
):
    controller, _scrape_runner, _errors = controller_components
    controller._offline_database_stats = (
        "1 colegio único de 2 resultados · 1 página verificada · actualizada ayer"
    )

    controller._offline_search_completed(str(tmp_path / "filtered.sqlite3"), 2, 1, 0)

    assert "1 de 2 colegios coinciden" in controller.statusText
    assert controller.offlineDatabaseStats == (
        "1 colegio único de 2 resultados · 1 página verificada · actualizada ayer"
    )


def test_cancel_scrape_updates_status_after_runner_confirms(controller_components):
    controller, scrape_runner, _errors = controller_components
    controller.startScrape(
        "file:///tmp/schools.xlsx",
        json.dumps([{"management": "publica", "level": "primaria"}]),
    )

    controller.cancelScrape()
    assert scrape_runner.cancel_requests == 1
    assert controller.scrapeRunning

    scrape_runner.cancelled.emit()

    assert not controller.scrapeRunning
    assert "cancelada" in controller.statusText


def test_scrape_completed_makes_results_available_without_exporting_files(
    controller_components,
    tmp_path,
):
    controller, _scrape_runner, _errors = controller_components
    database_path = tmp_path / "schools.sqlite3"

    controller._scrape_completed(str(database_path), 2, 2, 1)

    assert "2 colegios" in controller.statusText
    assert not (tmp_path / "schools.xlsx").exists()
    assert not (tmp_path / "schools.eduescale").exists()
    assert not (tmp_path / "schools.pdf").exists()
    assert controller._dataset_path == database_path
    assert controller.exportsAvailable


def test_export_buttons_generate_and_open_the_requested_file(
    controller_components,
    monkeypatch,
    tmp_path,
):
    controller, _scrape_runner, _errors = controller_components
    export_runner = controller._export_runner
    output_path = tmp_path / "schools.xlsx"
    eduescale_path = android_data_path(output_path)
    pdf_path = output_path.with_suffix(".pdf")
    opened_urls = []
    monkeypatch.setattr(
        controller_module,
        "QDesktopServices",
        SimpleNamespace(openUrl=lambda url: opened_urls.append(url) or True),
    )
    controller._output_path = output_path
    controller._dataset_path = tmp_path / "schools.sqlite3"
    controller._exports_available = True

    controller.exportExcelFile(QUrl.fromLocalFile(str(output_path)).toString())
    assert export_runner.started_requests == [
        ("excel", controller._dataset_path, output_path)
    ]
    output_path.write_bytes(b"Excel")
    export_runner.completed.emit("excel", str(output_path))

    controller.exportEduEscaleFile(QUrl.fromLocalFile(str(eduescale_path)).toString())
    assert export_runner.started_requests[-1] == (
        "eduescale",
        controller._dataset_path,
        eduescale_path,
    )
    eduescale_path.write_text("{}", encoding="utf-8")
    export_runner.completed.emit("eduescale", str(eduescale_path))

    controller.exportPdfFile(QUrl.fromLocalFile(str(pdf_path)).toString())
    assert export_runner.started_requests[-1] == (
        "pdf",
        controller._dataset_path,
        pdf_path,
    )
    pdf_path.write_bytes(b"%PDF-")
    export_runner.completed.emit("pdf", str(pdf_path))

    assert [Path(QUrl(url).toLocalFile()) for url in opened_urls] == [
        output_path,
        eduescale_path,
        pdf_path,
    ]


def test_starts_optional_compressed_and_protected_export(
    controller_components, tmp_path
):
    controller, _scrape_runner, _errors = controller_components
    controller._dataset_path = tmp_path / "schools.sqlite3"
    controller._output_path = tmp_path / "schools.xlsx"
    controller._exports_available = True
    output_path = tmp_path / "schools.eduescale"

    controller.exportEduEscaleFile(
        QUrl.fromLocalFile(str(output_path)).toString(),
        "contraseña segura",
        True,
    )

    assert controller._export_runner.started_requests == [
        (
            "eduescale",
            controller._dataset_path,
            output_path,
            "contraseña segura",
            True,
        )
    ]
