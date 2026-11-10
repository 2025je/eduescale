import os
import subprocess
import sys
from pathlib import Path

QML_SMOKE_SCRIPT = r"""
import json
import re
import sys
import time
from pathlib import Path

from PySide6.QtCore import QPointF, Q_ARG, QMetaObject, QObject, QSettings, Signal, QUrl
from PySide6.QtGui import QGuiApplication, QImageReader
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuickControls2 import QQuickStyle
from PySide6.QtWebEngineQuick import QtWebEngineQuick

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


QtWebEngineQuick.initialize()
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


def qml_value(item, name):
    value = item.property(name)
    return value.toVariant() if hasattr(value, "toVariant") else value


def select_map_codes(webview, map_item, codes):
    expected_codes = []
    for code in codes:
        expected_codes.append(code)
        assert QMetaObject.invokeMethod(
            webview,
            "runJavaScript",
            Q_ARG(str, "window.selectBoundary('" + code + "')"),
        )
        deadline = time.monotonic() + 5
        while (
            qml_value(map_item, "selectedCodes") != expected_codes
            and time.monotonic() < deadline
        ):
            application.processEvents()
            time.sleep(0.025)
        assert qml_value(map_item, "selectedCodes") == expected_codes


def wait_for_map_count(map_item, count):
    deadline = time.monotonic() + 10
    while (
        map_item.property("renderedFeatureCount") != count
        and time.monotonic() < deadline
    ):
        application.processEvents()
        time.sleep(0.025)
    assert map_item.property("renderedFeatureCount") == count, (
        map_item.property("loadingError"),
        map_item.property("renderedFeatureCount"),
        count,
    )


filter_grid = child("filterGrid")
search_workspace = child("searchWorkspace")
criteria_card = child("criteriaCard")
searches_card = child("searchesCard")
scroll_view = child("filterScrollView")
vertical_scrollbar = child("pageVerticalScrollBar")
search_list_view = child("searchListView")
search_list_scrollbar = child("searchListVerticalScrollBar")
action_bar = child("actionBar")
source_bar = child("sourceBar")
source_label = child("sourceLabel")
action_buttons = child("actionButtons")
progress_indicator = child("progressIndicator")
status_label = child("statusLabel")
help_text = child("searchHelpText")
filter_sets = child("filterSetsModel")
district_map = child("districtMap")
selection_controls = child("selectionControls")
district_webview = child("districtMapWebView")
map_level_label = child("mapLevelLabel")
map_advance_button = child("mapAdvanceButton")
map_back_button = child("mapBackButton")
map_clear_button = child("mapClearButton")
empty_state = child("emptySearchState")
brand_title = child("brandTitle")
brand_logo = child("brandLogo")
excel_button = child("downloadExcelButton")
pdf_button = child("downloadPdfButton")
eduescale_button = child("downloadEduScaleButton")
excel_dialog = child("excelSaveDialog")
pdf_dialog = child("pdfSaveDialog")
eduescale_dialog = child("eduScaleSaveDialog")
download_button = child("scrapeButton")
assert district_map.property("featureCount") == controller.property("districtCount")
deadline = time.monotonic() + 35
while (
    not district_map.property("mapReady")
    and not district_map.property("loadingError")
    and time.monotonic() < deadline
):
    application.processEvents()
    time.sleep(0.025)
assert district_map.property("mapReady"), (
    district_map.property("loadingError"),
    district_webview.property("url"),
    district_webview.property("loading"),
    district_webview.property("loadProgress"),
)
hierarchy_path = Path(sys.argv[1]).parents[1] / "assets" / "districts_map_hierarchy.json"
department_features = json.loads(
    hierarchy_path.read_text(encoding="utf-8")
)["departments"]["features"]
department_coordinates = []


def collect_coordinates(value):
    if isinstance(value[0], (int, float)):
        department_coordinates.append(value)
        return
    for child in value:
        collect_coordinates(child)


for feature in department_features:
    collect_coordinates(feature["geometry"]["coordinates"])
department_bounds = (
    min(point[0] for point in department_coordinates),
    min(point[1] for point in department_coordinates),
    max(point[0] for point in department_coordinates),
    max(point[1] for point in department_coordinates),
)
map_bounds = json.loads(district_map.property("mapBoundsJson"))
assert district_map.property("mapZoom") <= 4, district_map.property("mapZoom")
assert district_map.property("mapMinimumZoom") == 1
assert map_bounds[0][0] <= department_bounds[0], (map_bounds, department_bounds)
assert map_bounds[0][1] <= department_bounds[1], (map_bounds, department_bounds)
assert map_bounds[1][0] >= department_bounds[2], (map_bounds, department_bounds)
assert map_bounds[1][1] >= department_bounds[3], (map_bounds, department_bounds)
assert len(qml_value(controller, "mapDepartmentMetadata")) == 25
assert len(qml_value(controller, "mapProvinceMetadata")) == 196
assert qml_value(window, "mapLevel") == "department"
assert map_level_label.property("text") == "Paso 1 de 3 · Departamentos"
wait_for_map_count(district_map, 25)

select_map_codes(district_webview, district_map, ("15", "13"))
assert qml_value(window, "selectedDepartmentCodes") == ["15", "13"]
assert qml_value(district_map, "selectedCodes") == ["15", "13"]
assert map_advance_button.property("enabled")
map_advance_button.click()
application.processEvents()
expected_province_count = sum(
    province["departmentCode"] in {"13", "15"}
    for province in qml_value(controller, "mapProvinceMetadata").values()
)
wait_for_map_count(district_map, expected_province_count)
assert qml_value(window, "mapLevel") == "province"

select_map_codes(district_webview, district_map, ("1501", "1301"))
assert qml_value(window, "selectedProvinceCodes") == ["1501", "1301"]
map_advance_button.click()
application.processEvents()
expected_district_count = sum(
    code[:4] in {"1301", "1501"}
    for code in qml_value(controller, "districtCodes")
)
wait_for_map_count(district_map, expected_district_count)
select_map_codes(district_webview, district_map, ("150101", "130112"))
assert qml_value(window, "selectedDistrictCodes") == ["150101", "130112"]
assert qml_value(district_map, "selectedCodes") == ["150101", "130112"]

map_back_button.click()
application.processEvents()
wait_for_map_count(district_map, expected_province_count)
assert qml_value(district_map, "selectedCodes") == ["1501", "1301"]
map_advance_button.click()
application.processEvents()
wait_for_map_count(district_map, expected_district_count)
assert qml_value(district_map, "selectedCodes") == ["150101", "130112"]

map_clear_button.click()
application.processEvents()
wait_for_map_count(district_map, 25)
assert qml_value(window, "mapLevel") == "department"
assert qml_value(window, "selectedDepartmentCodes") == []
assert qml_value(window, "selectedProvinceCodes") == []
assert qml_value(window, "selectedDistrictCodes") == []
assert qml_value(district_map, "selectedCodes") == []
assert brand_title.property("font").family() == "Roboto"
logo_source = brand_logo.property("source")
assert QImageReader(logo_source.toLocalFile()).canRead()
assert download_button.property("text") == "Ejecutar búsqueda"
assert excel_button.property("text") == "Exportar Excel"
assert pdf_button.property("text") == "Exportar PDF"
assert eduescale_button.property("text") == "Exportar .eduescale"
assert excel_button.property("visible")
assert pdf_button.property("visible")
assert eduescale_button.property("visible")
assert not excel_button.property("enabled")
assert not pdf_button.property("enabled")
assert not eduescale_button.property("enabled")
assert not progress_indicator.property("visible")
assert status_label.property("visible")
assert source_bar.property("visible")
assert source_label.property("visible")
assert "Fuente: Padrón de IIEE de ESCALE" in source_label.property("text")
assert not excel_dialog.property("visible")
assert not pdf_dialog.property("visible")
assert not eduescale_dialog.property("visible")
assert excel_dialog.property("defaultSuffix") == "xlsx"
assert pdf_dialog.property("defaultSuffix") == "pdf"
assert eduescale_dialog.property("defaultSuffix") == "eduescale"

for width, height in ((560, 600), (1280, 720), (1366, 768), (1600, 900), (1920, 1080)):
    window.resize(width, height)
    application.processEvents()

    expected_columns = 1 if width < 720 else 2
    expected_workspace_columns = 1 if width < 1100 else 2
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
    assert vertical_scrollbar.property("visible")
    assert vertical_scrollbar.property("interactive")
    assert search_list_scrollbar.property("visible")
    assert search_list_scrollbar.property("interactive")
    page_scrollbar_right = vertical_scrollbar.mapToItem(
        scroll_view,
        QPointF(vertical_scrollbar.property("width"), 0),
    ).x()
    list_scrollbar_right = search_list_scrollbar.mapToItem(
        search_list_view,
        QPointF(search_list_scrollbar.property("width"), 0),
    ).x()
    assert page_scrollbar_right >= scroll_view.property("width") - 1, (
        width,
        page_scrollbar_right,
        scroll_view.property("width"),
    )
    assert list_scrollbar_right >= search_list_view.property("width") - 1, (
        width,
        list_scrollbar_right,
        search_list_view.property("width"),
    )
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
        - window.height()
    ) <= 1
    assert (
        source_bar.property("y") + source_bar.property("height")
        <= action_bar.property("y") + 1
    ), (
        source_bar.property("y"),
        source_bar.property("height"),
        action_bar.property("y"),
    )
    assert source_label.property("width") > 0
    assert (
        action_bar.property("y") + action_bar.property("height")
        <= scroll_view.property("contentHeight") + 1
    )
    for button in (download_button, excel_button, pdf_button, eduescale_button):
        assert button.property("visible")
        assert button.property("height") >= 48
        assert button.property("x") + button.property("width") <= (
            action_buttons.property("width") + 1
        )
        assert button.property("y") + button.property("height") <= (
            action_buttons.property("height") + 1
        )
    assert download_button.property("width") > excel_button.property("width")
    assert (
        download_button.property("y") + download_button.property("height")
        <= excel_button.property("y") + 1
    )
    assert district_map.property("x") >= 0
    assert district_map.property("width") > 0
    assert (
        district_map.property("x") + district_map.property("width")
        <= filter_grid.property("width") + 1
    )
    if filter_grid.property("columns") == 2:
        map_right = district_map.mapToItem(
            filter_grid,
            QPointF(district_map.property("width"), 0),
        ).x()
        controls_left = selection_controls.mapToItem(
            filter_grid,
            QPointF(0, 0),
        ).x()
        controls_right = selection_controls.mapToItem(
            filter_grid,
            QPointF(selection_controls.property("width"), 0),
        ).x()
        assert map_right <= controls_left + 1
        assert controls_right <= filter_grid.property("width") + 1
        for control in (map_clear_button, child("addFilterSetButton")):
            control_right = control.mapToItem(
                selection_controls,
                QPointF(control.property("width"), 0),
            ).x()
            assert control_right <= selection_controls.property("width") + 1, (
                control.objectName(),
                control_right,
                selection_controls.property("width"),
            )
    if width == 1280 and height == 720:
        viewport = scroll_view.property("contentItem")
        viewport_height = scroll_view.property("availableHeight")
        visible_controls = (
            district_map,
            map_level_label,
            map_advance_button,
            child("publicManagement"),
            child("privateManagement"),
            child("primaryLevel"),
            child("secondaryLevel"),
            child("cetproLevel"),
            child("addFilterSetButton"),
        )
        for item in visible_controls:
            bottom = item.mapToItem(
                viewport,
                QPointF(0, item.property("height")),
            ).y()
            assert bottom <= viewport_height + 1, (
                item.objectName(),
                bottom,
                viewport_height,
            )
select_map_codes(district_webview, district_map, ("15", "13"))
map_advance_button.click()
application.processEvents()
wait_for_map_count(district_map, expected_province_count)
select_map_codes(district_webview, district_map, ("1501", "1301"))
map_advance_button.click()
application.processEvents()
wait_for_map_count(district_map, expected_district_count)
select_map_codes(district_webview, district_map, ("150101", "130112"))
assert qml_value(district_map, "selectedCodes") == ["150101", "130112"]
child("publicManagement").setProperty("checked", True)
assert window.findChild(QObject, "initialLevel") is None
child("primaryLevel").setProperty("checked", True)
child("cetproLevel").setProperty("checked", True)
assert QMetaObject.invokeMethod(window, "addFilterSet")
assert filter_sets.property("count") == 2
assert QMetaObject.invokeMethod(window, "addFilterSet")
assert filter_sets.property("count") == 2
assert "ya está en Mis búsquedas" in help_text.property("text")
download_button.click()
application.processEvents()
assert controller.scrapeRunning
assert scrape_runner.started_requests[0][1][0].level == ("primaria", "cetpro")
assert [item.district_code for item in scrape_runner.started_requests[0][1]] == [
    "150101",
    "130112",
]
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
    for button in (
        download_button,
        excel_button,
        pdf_button,
        eduescale_button,
        cancel_button,
    ):
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
assert pdf_button.property("enabled")
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
assert Path(pdf_dialog.property("currentFile").toLocalFile()) == (
    default_export_path.with_suffix(".pdf")
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
controller._export_running = False
controller.stateChanged.emit()
pdf_path = default_export_path.parent / "colegios-informe.pdf"
controller.exportPdfFile(QUrl.fromLocalFile(str(pdf_path)).toString())
assert export_runner.started_requests[-1] == (
    "pdf",
    database_path,
    pdf_path,
)

for levels in (
    (True, False, False),
    (False, True, False),
    (False, False, True),
    (True, True, True),
):
    child("primaryLevel").setProperty("checked", levels[0])
    child("secondaryLevel").setProperty("checked", levels[1])
    child("cetproLevel").setProperty("checked", levels[2])
    assert QMetaObject.invokeMethod(window, "addFilterSet")
for management in ((False, True), (True, True)):
    child("publicManagement").setProperty("checked", management[0])
    child("privateManagement").setProperty("checked", management[1])
    for levels in (
        (True, False, False),
        (False, True, False),
        (False, False, True),
    ):
        child("primaryLevel").setProperty("checked", levels[0])
        child("secondaryLevel").setProperty("checked", levels[1])
        child("cetproLevel").setProperty("checked", levels[2])
        assert QMetaObject.invokeMethod(window, "addFilterSet")
application.processEvents()
assert filter_sets.property("count") >= 20
assert search_list_view.property("contentHeight") > search_list_view.property("height")
search_list_view.setProperty(
    "contentY",
    search_list_view.property("contentHeight") - search_list_view.property("height"),
)
application.processEvents()
assert search_list_view.property("contentY") > 0
assert (
    search_list_view.property("contentY") + search_list_view.property("height")
    >= search_list_view.property("contentHeight") - 1
)
assert search_list_scrollbar.property("position") >= (
    1 - search_list_scrollbar.property("size") - 0.01
)

page_flickable = scroll_view.property("contentItem")
page_flickable.setProperty(
    "contentY",
    scroll_view.property("contentHeight") - scroll_view.property("availableHeight"),
)
application.processEvents()
eduescale_bottom = eduescale_button.mapToItem(
    page_flickable,
    QPointF(0, eduescale_button.property("height")),
).y()
assert eduescale_bottom <= page_flickable.property("height") + 1

controller.shutdown()
"""


def test_main_qml_fits_supported_window_sizes(tmp_path):
    qml_path = Path(__file__).parents[1] / "eduescale" / "gui" / "qml" / "Main.qml"
    environment = os.environ.copy()
    environment.setdefault("QT_QPA_PLATFORM", "offscreen")
    environment.setdefault(
        "QTWEBENGINE_CHROMIUM_FLAGS",
        "--use-gl=angle --use-angle=swiftshader --enable-webgl "
        "--enable-unsafe-swiftshader",
    )
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
        timeout=90,
    )

    assert result.returncode == 0, f"{result.stdout}\n{result.stderr}"
