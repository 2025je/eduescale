import os
import subprocess
import sys
from pathlib import Path

QML_SMOKE_SCRIPT = r"""
import re
import sys
from pathlib import Path

from PySide6.QtCore import QMetaObject, QObject, QSettings, Signal, QUrl
from PySide6.QtGui import QGuiApplication, QImageReader
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuickControls2 import QQuickStyle

from eduescale.gui.application import (
    configure_application_font,
    configure_application_icon,
)
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

    def start(self, output_path, filter_sets):
        self.started_requests.append((output_path, filter_sets))

    def cancel(self):
        pass

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


QQuickStyle.setStyle("Material")
application = QGuiApplication([])
assert configure_application_font(application), "Roboto font could not be loaded"
assert configure_application_icon(application), "Application icon could not be loaded"
assert not application.windowIcon().isNull()
settings = QSettings(sys.argv[2], QSettings.Format.IniFormat)
settings.setValue("export/lastDirectory", str(Path(sys.argv[2]).parent))
scrape_runner = FakeScrapeRunner()
export_runner = FakeExportRunner()
controller = SchoolFinderController(
    FakeLocationService(),
    scrape_runner,
    settings=settings,
    export_runner=export_runner,
)
engine = QQmlApplicationEngine()
engine.setInitialProperties({"controller": controller})
engine.load(QUrl.fromLocalFile(sys.argv[1]))
assert engine.rootObjects(), "Main.qml did not load"
window = engine.rootObjects()[0]


def child(name):
    item = window.findChild(QObject, name)
    assert item is not None, f"Missing QML object: {name}"
    return item


filter_grid = child("filterGrid")
search_workspace = child("searchWorkspace")
criteria_card = child("criteriaCard")
searches_card = child("searchesCard")
scroll_view = child("filterScrollView")
action_bar = child("actionBar")
source_bar = child("sourceBar")
source_label = child("sourceLabel")
action_buttons = child("actionButtons")
progress_indicator = child("progressIndicator")
status_label = child("statusLabel")
help_text = child("searchHelpText")
filter_sets = child("filterSetsModel")
empty_state = child("emptySearchState")
brand_title = child("brandTitle")
brand_logo = child("brandLogo")
excel_button = child("downloadExcelButton")
eduescale_button = child("downloadEduScaleButton")
excel_dialog = child("excelSaveDialog")
eduescale_dialog = child("eduScaleSaveDialog")
download_button = child("scrapeButton")
combos = [child(name) for name in ("regionFilter", "provinceFilter", "districtFilter")]
assert brand_title.property("font").family() == "Roboto"
logo_source = brand_logo.property("source")
assert QImageReader(logo_source.toLocalFile()).canRead()
assert download_button.property("text") == "Ejecutar búsqueda"
assert excel_button.property("text") == "Exportar Excel"
assert eduescale_button.property("text") == "Exportar .eduescale"
assert excel_button.property("visible")
assert eduescale_button.property("visible")
assert not excel_button.property("enabled")
assert not eduescale_button.property("enabled")
assert not progress_indicator.property("visible")
assert status_label.property("visible")
assert source_bar.property("visible")
assert source_label.property("visible")
assert "Fuente: Padrón de IIEE de ESCALE" in source_label.property("text")
assert not excel_dialog.property("visible")
assert not eduescale_dialog.property("visible")
assert excel_dialog.property("defaultSuffix") == "xlsx"
assert eduescale_dialog.property("defaultSuffix") == "eduescale"

for width, height in ((560, 600), (1280, 720), (1366, 768), (1600, 900), (1920, 1080)):
    window.resize(width, height)
    application.processEvents()

    expected_columns = 1 if width < 720 else 2
    expected_workspace_columns = 1 if width < 1040 else 2
    expected_action_columns = 2 if width < 900 else 3
    assert filter_grid.property("columns") == expected_columns, (
        width,
        filter_grid.property("columns"),
    )
    assert search_workspace.property("columns") == expected_workspace_columns, (
        width,
        search_workspace.property("columns"),
    )
    assert action_buttons.property("columns") == expected_action_columns
    assert status_label.property("visible")
    assert status_label.property("y") + status_label.property("height") <= (
        action_buttons.property("y") + 1
    )
    assert filter_grid.property("width") > 0
    assert help_text.property("width") <= filter_grid.property("width") + 1
    if expected_workspace_columns == 2:
        assert abs(criteria_card.property("y") - searches_card.property("y")) <= 1
        assert (
            criteria_card.property("x") + criteria_card.property("width")
            <= searches_card.property("x") + 1
        )
    else:
        assert (
            criteria_card.property("y") + criteria_card.property("height")
            <= searches_card.property("y") + 1
        )
    assert empty_state.property("visible")
    assert abs(
        scroll_view.property("y")
        + scroll_view.property("height")
        - source_bar.property("y")
    ) <= 1
    assert abs(source_bar.property("y") + source_bar.property("height") - action_bar.property("y")) <= 1
    assert source_label.property("width") > 0
    assert (
        action_bar.property("y") + action_bar.property("height")
        <= window.height() + 1
    )
    for button in (download_button, excel_button, eduescale_button):
        assert button.property("visible")
        assert button.property("height") >= 48
        assert button.property("x") + button.property("width") <= (
            action_buttons.property("width") + 1
        )
        assert button.property("y") + button.property("height") <= (
            action_buttons.property("height") + 1
        )
    for combo in combos:
        assert combo.property("x") >= 0
        assert (
            combo.property("x") + combo.property("width")
            <= filter_grid.property("width") + 1
        )

child("publicManagement").setProperty("checked", True)
child("initialLevel").setProperty("checked", True)
assert QMetaObject.invokeMethod(window, "addFilterSet")
assert filter_sets.property("count") == 1
assert QMetaObject.invokeMethod(window, "addFilterSet")
assert filter_sets.property("count") == 1
assert "ya está en Mis búsquedas" in help_text.property("text")
download_button.click()
application.processEvents()
assert controller.scrapeRunning
cancel_button = child("cancelScrapeButton")
assert cancel_button.property("visible")
for width, expected_action_columns in ((560, 2), (1280, 3)):
    window.resize(width, 600)
    application.processEvents()
    assert progress_indicator.property("visible")
    assert progress_indicator.property("y") + progress_indicator.property("height") <= (
        status_label.property("y") + 1
    )
    assert status_label.property("visible")
    assert status_label.property("y") + status_label.property("height") <= (
        action_buttons.property("y") + 1
    )
    assert action_buttons.property("columns") == expected_action_columns
    for button in (download_button, excel_button, eduescale_button, cancel_button):
        assert button.property("visible")
        assert button.property("x") + button.property("width") <= (
            action_buttons.property("width") + 1
        )
        assert button.property("y") + button.property("height") <= (
            action_buttons.property("height") + 1
        )
assert len(scrape_runner.started_requests) == 1
scrape_output_path = scrape_runner.started_requests[0][0]
assert scrape_output_path.name == "colegios_escale.xlsx"
assert export_runner.started_requests == []

database_path = Path(sys.argv[2]).parent / "schools.sqlite3"
scrape_runner.completed.emit(str(database_path), 1, 1, 1)
application.processEvents()
assert excel_button.property("visible")
assert eduescale_button.property("visible")
assert excel_button.property("enabled")
assert eduescale_button.property("enabled")
assert progress_indicator.property("visible")
assert "Datos listos para exportar" in status_label.property("text")
default_export_path = controller._output_path
assert default_export_path is not None
assert re.fullmatch(
    r"colegios_escale_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}(?:_\d+)?\.xlsx",
    default_export_path.name,
)
assert Path(excel_dialog.property("currentFile").toLocalFile()) == (
    default_export_path
)
assert Path(eduescale_dialog.property("currentFile").toLocalFile()) == (
    default_export_path.with_suffix(".eduescale")
)

assert export_runner.started_requests == []
chosen_excel_path = default_export_path.parent / "colegios-personalizado.xlsx"
controller.exportExcelFile(QUrl.fromLocalFile(str(chosen_excel_path)).toString())
assert export_runner.started_requests[-1] == (
    "excel",
    database_path,
    chosen_excel_path,
)
controller._export_running = False
controller.stateChanged.emit()
eduescale_path = default_export_path.parent / "colegios-compartir.eduescale"
controller.exportEduScaleFile(QUrl.fromLocalFile(str(eduescale_path)).toString())
assert export_runner.started_requests[-1] == (
    "eduescale",
    database_path,
    eduescale_path,
)

controller.shutdown()
"""


def test_main_qml_fits_supported_window_sizes(tmp_path):
    qml_path = Path(__file__).parents[1] / "eduescale" / "gui" / "qml" / "Main.qml"
    environment = os.environ.copy()
    environment["QT_QPA_PLATFORM"] = "offscreen"
    result = subprocess.run(
        [
            sys.executable,
            "-c",
            QML_SMOKE_SCRIPT,
            str(qml_path),
            str(tmp_path / "settings.ini"),
        ],
        capture_output=True,
        check=False,
        env=environment,
        text=True,
        timeout=60,
    )

    assert result.returncode == 0, f"{result.stdout}\n{result.stderr}"
