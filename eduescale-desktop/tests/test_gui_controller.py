import json
from pathlib import Path

import pytest
from PySide6.QtCore import QCoreApplication, QObject, QSettings, QUrl, Signal

from eduescale.gui.application import create_controller
from eduescale.gui.controller import SchoolFinderController


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

    def shutdown(self):
        pass


def test_create_controller_accepts_injected_services():
    location_service = FakeLocationService()
    scrape_runner = FakeScrapeRunner()
    controller = create_controller(location_service, scrape_runner)

    try:
        assert controller._location_service is location_service
        assert controller._scrape_runner is scrape_runner
    finally:
        controller.shutdown()


@pytest.fixture
def controller_components(tmp_path):
    application = QCoreApplication.instance() or QCoreApplication([])
    location_service = FakeLocationService()
    scrape_runner = FakeScrapeRunner()
    settings = QSettings(
        str(tmp_path / "settings.ini"),
        QSettings.Format.IniFormat,
    )
    controller = SchoolFinderController(
        location_service=location_service,
        scrape_runner=scrape_runner,
        settings=settings,
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
    )

    try:
        assert Path(QUrl(controller.defaultOutputUrl).toLocalFile()) == (
            tmp_path / "colegios_escale.xlsx"
        )
        assert controller.windowWidth == 560
        assert controller.windowHeight == 860

        controller.saveWindowSize(900, 700)

        assert settings.value("window/width", type=int) == 900
        assert settings.value("window/height", type=int) == 700

        controller.startScrape(
            QUrl.fromLocalFile(str(tmp_path / "export.xlsx")).toString(),
            json.dumps([{"management": "publica", "level": "inicial"}]),
        )

        assert settings.value("export/lastDirectory") == str(tmp_path)
        assert application is not None
    finally:
        controller.shutdown()


def test_management_selection_is_required(controller_components):
    controller, scrape_runner, errors = controller_components
    controller.startScrape(
        "file:///tmp/schools.xlsx",
        json.dumps([{"management": "", "level": "inicial"}]),
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


def test_rejects_non_local_output_urls(controller_components):
    controller, scrape_runner, errors = controller_components
    controller.startScrape(
        "https://example.invalid/schools.xlsx",
        json.dumps([{"management": "publica", "level": "inicial"}]),
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
                    "level": "inicial",
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


def test_cancel_scrape_updates_status_after_runner_confirms(controller_components):
    controller, scrape_runner, _errors = controller_components
    controller.startScrape(
        "file:///tmp/schools.xlsx",
        json.dumps([{"management": "publica", "level": "inicial"}]),
    )

    controller.cancelScrape()
    assert scrape_runner.cancel_requests == 1
    assert controller.scrapeRunning

    scrape_runner.cancelled.emit()

    assert not controller.scrapeRunning
    assert "cancelada" in controller.statusText


def test_scrape_completed_reports_both_export_files(controller_components, tmp_path):
    controller, _scrape_runner, _errors = controller_components
    output_path = tmp_path / "schools.xlsx"

    controller._scrape_completed(str(output_path), 2, 2, 1)

    assert "schools.xlsx" in controller.statusText
    assert "schools.eduescale" in controller.statusText
