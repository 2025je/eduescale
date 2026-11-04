import os
import subprocess
import sys
from pathlib import Path

QML_SMOKE_SCRIPT = r"""
import sys

from PySide6.QtCore import QObject, QSettings, Signal, QUrl
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

    def start(self, output_path, filter_sets):
        pass

    def cancel(self):
        pass

    def shutdown(self):
        pass


QQuickStyle.setStyle("Material")
application = QGuiApplication([])
assert configure_application_font(application), "Roboto font could not be loaded"
assert configure_application_icon(application), "Application icon could not be loaded"
assert not application.windowIcon().isNull()
settings = QSettings(sys.argv[2], QSettings.Format.IniFormat)
controller = SchoolFinderController(
    FakeLocationService(),
    FakeScrapeRunner(),
    settings=settings,
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
scroll_view = child("filterScrollView")
action_bar = child("actionBar")
help_text = child("searchHelpText")
empty_state = child("emptySearchState")
brand_title = child("brandTitle")
brand_logo = child("brandLogo")
combos = [child(name) for name in ("regionFilter", "provinceFilter", "districtFilter")]
assert brand_title.property("font").family() == "Roboto"
logo_source = brand_logo.property("source")
assert QImageReader(logo_source.toLocalFile()).canRead()

for width, height in ((560, 600), (1280, 720), (1366, 768), (1600, 900), (1920, 1080)):
    window.resize(width, height)
    application.processEvents()

    expected_columns = 1 if width < 720 else 2
    assert filter_grid.property("columns") == expected_columns, (
        width,
        filter_grid.property("columns"),
    )
    assert filter_grid.property("width") > 0
    assert help_text.property("width") <= filter_grid.property("width") + 1
    assert empty_state.property("visible")
    assert abs(
        scroll_view.property("y")
        + scroll_view.property("height")
        - action_bar.property("y")
    ) <= 1
    assert (
        action_bar.property("y") + action_bar.property("height")
        <= window.height() + 1
    )
    for combo in combos:
        assert combo.property("x") >= 0
        assert (
            combo.property("x") + combo.property("width")
            <= filter_grid.property("width") + 1
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
