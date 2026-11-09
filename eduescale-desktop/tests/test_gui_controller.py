import json
from pathlib import Path
from types import SimpleNamespace

import pytest
from PySide6.QtCore import QCoreApplication, QObject, QSettings, QUrl, Signal

from eduescale.export_files import android_data_path
from eduescale.gui import controller as controller_module
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


class FakeExportRunner(QObject):
    started = Signal(str)
    completed = Signal(str, str)
    failed = Signal(str, str, str)

    def __init__(self):
        super().__init__()
        self.started_requests = []

    def start(self, export_type, database_path, output_path):
        self.started_requests.append((export_type, database_path, output_path))

    def shutdown(self):
        pass


def test_create_controller_accepts_injected_services():
    location_service = FakeLocationService()
    scrape_runner = FakeScrapeRunner()
    export_runner = FakeExportRunner()
    controller = create_controller(location_service, scrape_runner, export_runner)

    try:
        assert controller._location_service is location_service
        assert controller._scrape_runner is scrape_runner
        assert controller._export_runner is export_runner
    finally:
        controller.shutdown()


@pytest.fixture
def controller_components(tmp_path):
    application = QCoreApplication.instance() or QCoreApplication([])
    location_service = FakeLocationService()
    scrape_runner = FakeScrapeRunner()
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
            json.dumps([{"management": "publica", "level": "primaria"}]),
        )

        assert settings.value("export/lastDirectory") == str(tmp_path)
        assert application is not None
    finally:
        controller.shutdown()


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

    controller.exportEduScaleFile(QUrl.fromLocalFile(str(eduescale_path)).toString())
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
