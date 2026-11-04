import json

import pytest
from PySide6.QtCore import QCoreApplication, QObject, Signal

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

    def __init__(self):
        super().__init__()
        self.started_requests = []

    def start(self, output_path, filter_sets):
        self.started_requests.append((output_path, filter_sets))

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
def controller_components():
    application = QCoreApplication.instance() or QCoreApplication([])
    location_service = FakeLocationService()
    scrape_runner = FakeScrapeRunner()
    controller = SchoolFinderController(
        location_service=location_service,
        scrape_runner=scrape_runner,
    )
    errors = []
    controller.errorRaised.connect(
        lambda title, details: errors.append((title, details))
    )
    yield controller, scrape_runner, errors
    controller.shutdown()
    assert application is not None


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
