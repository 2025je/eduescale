import json
import re
import sys
import time
from pathlib import Path

from PySide6.QtCore import (
    Q_ARG,
    QMetaObject,
    QObject,
    QPointF,
    QSettings,
    Qt,
    QUrl,
    Signal,
)
from PySide6.QtGui import QGuiApplication, QImageReader
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuickControls2 import QQuickStyle
from PySide6.QtTest import QTest
from PySide6.QtWebEngineQuick import QtWebEngineQuick
from shiboken6 import delete

from eduescale.application.use_cases.offline_database import (
    OfflineDatabaseUseCases,
)
from eduescale.gui.adapters.local_database import LocalDatabaseSearchRunner
from eduescale.gui.application import (
    configure_application_font,
    configure_application_icon,
)
from eduescale.gui.services.district_map_catalog import DistrictMapCatalog
from eduescale.gui.services.export_history import QSettingsExportHistoryStore
from eduescale.gui.services.export_request_service import ExportRequestService
from eduescale.gui.services.offline_database_update import OfflineDatabaseUpdateService
from eduescale.gui.services.preferences import QSettingsDesktopPreferences
from eduescale.gui.services.progress import ProgressMetrics
from eduescale.gui.services.saved_search_service import PortableSavedSearchService
from eduescale.gui.services.school_search_service import SchoolSearchRequestService
from eduescale.gui.services.school_search_workflow import SchoolSearchWorkflow
from eduescale.gui.viewmodels import SchoolFinderViewModel
from eduescale.infrastructure.persistence.local_database import (
    SqliteOfflineDatabaseRepository,
)


class FakeLocationService(QObject):
    loaded = Signal(str, str, object)
    failed = Signal(str, str, str)

    def load(self, kind, parent_code=""):
        locations = {
            ("regions", ""): [("15", "Lima"), ("13", "La Libertad")],
            ("provinces", "15"): [("1501", "Lima")],
            ("provinces", "13"): [("1301", "Trujillo")],
            ("districts", "1501"): [("150101", "Lima")],
            ("districts", "1301"): [("130112", "Huanchaco")],
        }
        self.loaded.emit(kind, parent_code, locations.get((kind, parent_code), []))

    def shutdown(self):
        pass


class FakeScrapeRunner(QObject):
    started = Signal()
    progressChanged = Signal(object)
    schoolsReceived = Signal(object)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)
    cancelled = Signal()
    warning = Signal(str, str)

    def __init__(self):
        super().__init__()
        self.started_requests = []
        self.cancel_calls = 0

    def start(self, output_path, filter_sets, *, purpose):
        self.started_requests.append((output_path, filter_sets, purpose))
        self.started.emit()

    def cancel(self):
        self.cancel_calls += 1

    def complete_database_download(self):
        return None

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


QtWebEngineQuick.initialize()
QQuickStyle.setStyle("Universal")
application = QGuiApplication([])
assert configure_application_font(application), (
    "No Fluent-compatible UI font could be loaded"
)
assert configure_application_icon(application), "Application icon could not be loaded"
assert not application.windowIcon().isNull()
settings = QSettings(sys.argv[2], QSettings.Format.IniFormat)
settings.setValue("export/lastDirectory", str(Path(sys.argv[2]).parent))
scrape_runner = FakeScrapeRunner()
export_runner = FakeExportRunner()
offline_database_use_cases = OfflineDatabaseUseCases(SqliteOfflineDatabaseRepository())
district_map_catalog = DistrictMapCatalog()
local_database_search_runner = LocalDatabaseSearchRunner(offline_database_use_cases)
view_model = SchoolFinderViewModel(
    FakeLocationService(),
    school_search_workflow=SchoolSearchWorkflow(
        scrape_runner,
        local_database_search_runner,
    ),
    preferences=QSettingsDesktopPreferences(settings),
    export_runner=export_runner,
    export_history_store=QSettingsExportHistoryStore(settings),
    district_map_catalog=district_map_catalog,
    saved_search_service=PortableSavedSearchService(district_map_catalog),
    school_search_service=SchoolSearchRequestService(
        district_map_catalog,
        offline_database_use_cases,
    ),
    offline_database_update_service=OfflineDatabaseUpdateService(
        offline_database_use_cases,
        scrape_runner,
    ),
    export_request_service=ExportRequestService(),
    offline_database_use_cases=offline_database_use_cases,
    data_directory=Path(sys.argv[2]).parent,
)
school_locations = []
view_model.schoolLocationsReceived.connect(school_locations.extend)
view_model_errors = []
view_model.errorRaised.connect(
    lambda title, details: view_model_errors.append((title, details))
)
engine = QQmlApplicationEngine()
engine.addImportPath("qrc:/eduescale/gui/qml")
qml_warnings = []
engine.warnings.connect(lambda warnings: qml_warnings.extend(warnings))
engine.setInitialProperties({"viewModel": view_model})
engine.load(QUrl(sys.argv[1]))
assert engine.rootObjects(), (
    "\n".join(warning.toString() for warning in qml_warnings) or "Main.qml did not load"
)
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
dashboard_shell = child("dashboardShell")
vertical_scrollbar = child("pageVerticalScrollBar")
search_list_view = child("searchListView")
search_list_scrollbar = child("searchListVerticalScrollBar")
action_bar = child("actionBar")
action_surface = child("actionSurface")
source_bar = child("sourceBar")
source_label = child("sourceLabel")
dashboard_header = child("dashboardHeader")
action_buttons = child("actionButtons")
progress_indicator = child("progressIndicator")
status_label = child("statusLabel")
help_text = child("searchHelpText")
filter_sets = child("filterSetsModel")
district_map = child("districtMap")
live_schools_map = child("liveSchoolsMap")
live_map_bridge = child("liveMapBridge")
live_map_style_selector = child("liveMapStyleSelector")
school_wizard_review_page = child("schoolWizardReviewPage")
school_wizard_page = child("schoolWizardPage")
school_wizard_status = child("schoolWizardStatusLabel")
school_wizard_progress = child("schoolWizardProgress")
school_wizard_point_count = child("schoolWizardPointCount")
school_wizard_cancel_button = child("schoolWizardCancelButton")
school_wizard_excel_button = child("schoolWizardExcelButton")
school_wizard_pdf_button = child("schoolWizardPdfButton")
school_wizard_eduescale_button = child("schoolWizardEduEscaleButton")
school_details_card = child("schoolDetailsCard")
selection_controls = child("selectionControls")
location_tabs = child("locationTabs")
map_location_tab = child("mapLocationTab")
combo_location_tab = child("comboLocationTab")
map_location_tab_label = child("mapLocationTabLabel")
combo_location_tab_label = child("comboLocationTabLabel")
map_location_tab_indicator = child("mapLocationTabIndicator")
combo_location_tab_indicator = child("comboLocationTabIndicator")
department_combo = child("departmentCombo")
province_combo = child("provinceCombo")
district_combo = child("districtCombo")
selected_district_count = child("selectedDistrictCountLabel")
district_webview = child("districtMapWebView")
map_profile = district_webview.property("profile")
map_level_label = child("mapLevelLabel")
map_style_selector = child("mapStyleSelector")
map_advance_button = child("mapAdvanceButton")
map_advance_button_label = child("mapAdvanceButtonLabel")
map_back_button = child("mapBackButton")
map_clear_button = child("mapClearButton")
map_clear_button_label = child("mapClearButtonLabel")
clear_all_filters_button = child("clearAllFiltersButton")
empty_state = child("emptySearchState")
brand_title = child("brandTitle")
brand_logo = child("brandLogo")
dashboard_header = child("dashboardHeader")
excel_dialog = child("excelSaveDialog")
pdf_dialog = child("pdfSaveDialog")
eduescale_dialog = child("eduEscaleSaveDialog")
version_info = child("eduEscaleVersionInfo")
assert version_info.property("text") == (
    f"Versión del formato: {view_model.property('eduEscaleFormatVersion')}\n"
    f"Versión de EduEscale Desktop: "
    f"{view_model.property('eduEscaleApplicationVersion')}"
)
download_button = child("scrapeButton")
scrape_button_label = child("scrapeButtonLabel")
history_button = child("exportHistoryButton")
history_dialog = child("exportHistoryDialog")
offline_database_summary_dialog = child("offlineDatabaseSummaryDialog")
add_filter_button_label = child("addFilterSetButtonLabel")
log_directory = Path(sys.argv[2]).parent / "logs"
log_directory.mkdir()
active_log = log_directory / "eduescale.log"
backup_log = log_directory / "eduescale.log.1"
active_log.write_text("primer mensaje\n", encoding="utf-8")
backup_log.write_text("mensaje anterior\n", encoding="utf-8")
logs_button = child("openLogsButton")
history_button = child("exportHistoryButton")
theme_selector = child("themeModeSelector")
logs_dialog = child("logViewerDialog")
log_selector = child("logFileSelector")
log_content = child("logViewerContent")
assert not logs_dialog.property("visible")
logs_button.click()
deadline = time.monotonic() + 5
while (
    log_content.property("text") != "primer mensaje\n" and time.monotonic() < deadline
):
    application.processEvents()
    time.sleep(0.025)
assert logs_dialog.property("visible")
assert log_content.property("readOnly")
assert log_content.property("selectByMouse")
assert log_content.property("text") == "primer mensaje\n"
active_log.write_text("mensaje actualizado\n", encoding="utf-8")
deadline = time.monotonic() + 5
while (
    log_content.property("text") != "mensaje actualizado\n"
    and time.monotonic() < deadline
):
    application.processEvents()
    time.sleep(0.025)
assert log_content.property("text") == "mensaje actualizado\n"
active_log.write_text("línea de registro\n" * 280000 + "último\n", encoding="utf-8")
deadline = time.monotonic() + 5
while (
    not log_content.property("text").endswith("último\n")
    and time.monotonic() < deadline
):
    application.processEvents()
    time.sleep(0.025)
assert log_content.property("text").endswith("último\n")
assert len(log_content.property("text").encode("utf-8")) <= 16387
previous_page_button = child("previousLogPageButton")
next_page_button = child("nextLogPageButton")
first_page_button = child("firstLogPageButton")
last_page_button = child("lastLogPageButton")
page_selector = child("logPageSelector")
assert previous_page_button.property("enabled")
assert not next_page_button.property("enabled")
initial_window_size = (window.width(), window.height())
window.resize(560, 600)
application.processEvents()
dialog_content = logs_dialog.property("contentItem")
assert (
    last_page_button.mapToItem(dialog_content, QPointF(last_page_button.width(), 0)).x()
    <= dialog_content.width() + 1
)
first_page_button.click()
application.processEvents()
assert page_selector.property("value") == 1
assert not first_page_button.property("enabled")
page_editor = page_selector.property("contentItem")
page_editor.forceActiveFocus()
QTest.keyClick(window, Qt.Key_A, Qt.ControlModifier)
QTest.keyClick(window, Qt.Key_7)
QTest.keyClick(window, Qt.Key_Return)
application.processEvents()
assert page_selector.property("value") == 7
assert view_model.logViewer.pageNumber == 7
last_page_button.click()
application.processEvents()
assert page_selector.property("value") == view_model.logViewer.pageCount
window.resize(*initial_window_size)
application.processEvents()
previous_page_button.click()
application.processEvents()
assert next_page_button.property("enabled")
assert not log_content.property("text").endswith("último\n")
next_page_button.click()
application.processEvents()
assert log_content.property("text").endswith("último\n")
log_selector.setProperty("currentIndex", 1)
assert QMetaObject.invokeMethod(log_selector, "activated", Q_ARG(int, 1))
application.processEvents()
assert log_content.property("text") == "mensaje anterior\n"
structured_log = (
    json.dumps(
        {
            "timestamp": "2026-11-19T15:00:00Z",
            "level": "warning",
            "logger": "eduescale.gui",
            "event": "Mensaje estructurado",
            "😀operation": "search",
        },
        ensure_ascii=False,
    )
    + "\n"
)
backup_log.write_text(structured_log, encoding="utf-8")
view_model.logViewer.refresh()
application.processEvents()
assert "WARNING" in log_content.property("text")
assert "Mensaje estructurado" in log_content.property("text")
assert "😀operation: search" in log_content.property("text")
assert structured_log not in log_content.property("text")
quick_log_document = log_content.property("textDocument")
log_document = quick_log_document.textDocument()


def color_for_text(block, value):
    assert value in block.text()
    offset = len(block.text().split(value, 1)[0].encode("utf-16-le")) // 2
    for item in block.layout().formats():
        if item.start <= offset < item.start + item.length:
            return item.format.foreground().color().name()
    return None


header = log_document.firstBlock()
assert color_for_text(header, "2026") is not None
assert color_for_text(header, "WARNING") is not None
assert color_for_text(header, "eduescale.gui") is not None
detail = log_document.findBlockByNumber(2)
assert color_for_text(detail, "😀") == color_for_text(detail, "operation")
assert color_for_text(detail, "operation") is not None
assert color_for_text(detail, "search") is None
child("rawLogToggle").click()
application.processEvents()
assert log_content.property("text") == structured_log
assert not log_document.firstBlock().layout().formats()
child("rawLogToggle").click()
application.processEvents()
assert color_for_text(log_document.firstBlock(), "WARNING") is not None
records = [
    json.dumps(
        {
            "timestamp": "2026-11-19T15:00:00Z",
            "level": level,
            "logger": "eduescale.gui",
            "event": "Mensaje <seguro> 😀",
        },
        ensure_ascii=False,
    )
    for level in ("debug", "info", "warning", "error", "critical")
]
backup_log.write_text("\n".join(records) + "\n", encoding="utf-8")
view_model.setThemeMode("light")
view_model.logViewer.refresh()
application.processEvents()
light_colors = [
    color_for_text(log_document.findBlockByNumber(index * 3), level.upper())
    for index, level in enumerate(("debug", "info", "warning", "error", "critical"))
]
assert None not in light_colors and len(set(light_colors)) == 5
first_header = log_document.firstBlock()
assert color_for_text(first_header, "2026") is not None
assert color_for_text(first_header, "eduescale.gui") is not None
assert color_for_text(first_header, "2026") != color_for_text(
    first_header, "eduescale.gui"
)
assert "Mensaje <seguro> 😀" in log_content.property("text")
view_model.setThemeMode("dark")
application.processEvents()
dark_colors = [
    color_for_text(log_document.findBlockByNumber(index * 3), level.upper())
    for index, level in enumerate(("debug", "info", "warning", "error", "critical"))
]
assert None not in dark_colors and len(set(dark_colors)) == 5
assert all(light != dark for light, dark in zip(light_colors, dark_colors))
view_model.setThemeMode("system")
application.processEvents()
child("closeLogsButton").click()
application.processEvents()
assert not logs_dialog.property("visible")


def assert_logs_accessible(button):
    assert button.property("visible")
    button.click()
    application.processEvents()
    assert logs_dialog.property("visible")
    child("closeLogsButton").click()
    application.processEvents()
    assert not logs_dialog.property("visible")


def assert_persistent_header(page):
    for item in (brand_logo, logs_button, history_button, theme_selector):
        assert item.property("visible"), item.objectName()
    header_bottom = dashboard_header.mapToItem(
        window.contentItem(),
        QPointF(0, dashboard_header.property("height")),
    ).y()
    page_top = page.mapToItem(window.contentItem(), QPointF(0, 0)).y()
    assert page_top >= header_bottom


history_button.click()
application.processEvents()
assert history_dialog.property("visible")
assert child("exportHistoryWarning").property("visible") is False
child("closeExportHistoryButton").click()
application.processEvents()
assert not offline_database_summary_dialog.property("visible")
assert district_map.property("featureCount") == view_model.property("districtCount")
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
assert map_profile.property("storageName") == "eduescale-map"
assert map_profile.property("httpUserAgent") == (
    "EduEscaleDesktop/1.0 (+https://github.com/2025je/eduescale)"
)
assert map_style_selector.property("count") == 8
assert map_style_selector.property("currentText") == "Estándar OSM"
assert live_map_style_selector.property("currentText") == "Estándar OSM"
assert map_style_selector.property("enabled")
assert map_style_selector.property("width") == 148
assert map_style_selector.property("height") <= 40
assert map_style_selector.property("background").property("color").alpha() == 255
for popup_name in (
    "departmentComboPopup",
    "provinceComboPopup",
    "districtComboPopup",
    "mapStyleSelectorPopup",
):
    popup = window.findChild(QObject, popup_name)
    assert popup is not None
    assert popup.property("background").property("color").name() == "#ffffff"
    assert popup.property("background").property("radius") == 8
map_style_popup = window.findChild(QObject, "mapStyleSelectorPopup")
assert QMetaObject.invokeMethod(map_style_popup, "open")
application.processEvents()
assert map_style_popup.property("visible")
assert QMetaObject.invokeMethod(map_style_popup, "close")
application.processEvents()
hierarchy_path = Path(sys.argv[3]) / "districts_map_hierarchy.json"
department_features = json.loads(hierarchy_path.read_text(encoding="utf-8"))[
    "departments"
]["features"]
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
assert len(qml_value(view_model, "mapDepartmentMetadata")) == 25
assert len(qml_value(view_model, "mapProvinceMetadata")) == 196
assert qml_value(window, "mapLevel") == "department"
assert map_level_label.property("text") == "Paso 1 de 3 · Departamentos"
wait_for_map_count(district_map, 25)
assert location_tabs.property("currentIndex") == 0
assert map_location_tab.property("text") == "Mapa"
assert combo_location_tab.property("text") == "Por ubicación"
assert map_location_tab_indicator.property("visible")
assert not combo_location_tab_indicator.property("visible")
assert map_location_tab_label.property("color").name() == "#174c39"
assert combo_location_tab_label.property("color").name() == "#1a1a1a"
assert map_advance_button_label.property("color").name() == "#4d5852"
assert map_clear_button_label.property("color").name() == "#4d5852"
assert add_filter_button_label.property("color").name() == "#4d5852"
assert scrape_button_label.property("color").name() == "#4d5852"
assert department_combo.property("enabled")
assert department_combo.property("count") == 3

combo_location_tab.click()
application.processEvents()
assert location_tabs.property("currentIndex") == 1
assert not map_location_tab_indicator.property("visible")
assert combo_location_tab_indicator.property("visible")
assert map_location_tab_label.property("color").name() == "#1a1a1a"
assert combo_location_tab_label.property("color").name() == "#174c39"
department_combo.setProperty("currentIndex", 1)
assert QMetaObject.invokeMethod(department_combo, "activated", Q_ARG(int, 1))
application.processEvents()
assert province_combo.property("enabled")
assert province_combo.property("count") == 2
province_combo.setProperty("currentIndex", 1)
assert QMetaObject.invokeMethod(province_combo, "activated", Q_ARG(int, 1))
application.processEvents()
assert district_combo.property("enabled")
assert district_combo.property("count") == 2
district_combo.setProperty("currentIndex", 1)
assert QMetaObject.invokeMethod(district_combo, "activated", Q_ARG(int, 1))
application.processEvents()
assert district_combo.property("currentValue") == "150101"
assert selected_district_count.property("text") == "1 distritos"
map_location_tab.click()
application.processEvents()
assert location_tabs.property("currentIndex") == 0
assert selected_district_count.property("text") == "Todo el Perú"

select_map_codes(district_webview, district_map, ("15", "13"))
assert qml_value(window, "selectedDepartmentCodes") == ["15", "13"]
assert qml_value(district_map, "selectedCodes") == ["15", "13"]
assert map_advance_button.property("enabled")
map_advance_button.click()
application.processEvents()
expected_province_count = sum(
    province["departmentCode"] in {"13", "15"}
    for province in qml_value(view_model, "mapProvinceMetadata").values()
)
wait_for_map_count(district_map, expected_province_count)
assert qml_value(window, "mapLevel") == "province"

select_map_codes(district_webview, district_map, ("1501", "1301"))
assert qml_value(window, "selectedProvinceCodes") == ["1501", "1301"]
map_advance_button.click()
application.processEvents()
expected_district_count = sum(
    code[:4] in {"1301", "1501"} for code in qml_value(view_model, "districtCodes")
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
assert department_combo.property("currentIndex") == 0
assert province_combo.property("currentIndex") == 0
assert district_combo.property("currentIndex") == 0
assert brand_title.property("font").family() in (
    "Segoe UI Variable",
    "Segoe UI",
    "Roboto",
)
logo_source = brand_logo.property("source")
assert QImageReader(":" + logo_source.path()).canRead()
assert download_button.property("text").startswith("Revisar ")
assert window.findChild(QObject, "downloadExcelButton") is None
assert window.findChild(QObject, "downloadPdfButton") is None
assert window.findChild(QObject, "downloadEduEscaleButton") is None
assert not progress_indicator.property("visible")
assert not status_label.property("visible")
assert not school_wizard_review_page.property("visible")
assert not school_wizard_page.property("visible")
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
    expected_action_columns = 2 if width < 900 else 3 if width < 1200 else 4
    assert filter_grid.property("columns") == expected_columns, (
        width,
        filter_grid.property("columns"),
    )
    assert search_workspace.property("columns") == expected_workspace_columns, (
        width,
        search_workspace.property("columns"),
    )
    assert action_buttons.property("columns") == expected_action_columns
    page_has_overflow = (
        scroll_view.property("contentHeight")
        > scroll_view.property("availableHeight") + 1
    )
    assert vertical_scrollbar.property("visible") == page_has_overflow
    assert vertical_scrollbar.property("interactive")
    list_has_overflow = (
        search_list_view.property("contentHeight")
        > search_list_view.property("height") + 1
    )
    assert search_list_scrollbar.property("visible") == list_has_overflow
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
    assert not status_label.property("visible")
    assert filter_grid.property("width") > 0
    assert help_text.property("width") <= filter_grid.property("width") + 1
    criteria_top = criteria_card.mapToItem(search_workspace, QPointF(0, 0))
    searches_top = searches_card.mapToItem(search_workspace, QPointF(0, 0))
    criteria_bottom = criteria_card.mapToItem(
        search_workspace,
        QPointF(0, criteria_card.property("height")),
    )
    criteria_right = criteria_card.mapToItem(
        search_workspace,
        QPointF(criteria_card.property("width"), 0),
    ).x()
    if expected_workspace_columns == 2:
        assert abs(criteria_top.y() - searches_top.y()) <= 1
        assert criteria_right <= searches_top.x() + 1
    else:
        assert criteria_bottom.y() <= searches_top.y() + 1
    assert empty_state.property("visible")
    window_content = window.contentItem()
    scroll_top = scroll_view.mapToItem(window_content, QPointF(0, 0)).y()
    scroll_bottom = scroll_view.mapToItem(
        window_content,
        QPointF(0, scroll_view.property("height")),
    ).y()
    header_bottom = dashboard_header.mapToItem(
        window_content,
        QPointF(0, dashboard_header.property("height")),
    ).y()
    source_top = source_bar.mapToItem(window_content, QPointF(0, 0)).y()
    source_bottom = source_bar.mapToItem(
        window_content,
        QPointF(0, source_bar.property("height")),
    ).y()
    action_bar_top = action_bar.mapToItem(window_content, QPointF(0, 0)).y()
    review_button_bottom = download_button.mapToItem(
        action_surface,
        QPointF(0, download_button.property("height")),
    ).y()
    action_bottom = action_bar.mapToItem(
        window_content,
        QPointF(0, action_bar.property("height")),
    ).y()
    dashboard_bottom = dashboard_shell.mapToItem(
        window_content,
        QPointF(0, dashboard_shell.property("height")),
    ).y()
    assert scroll_top >= header_bottom
    assert source_top >= scroll_bottom
    assert source_bottom <= action_bar_top + 1
    assert source_bar.property("width") <= searches_card.property("width") + 1
    assert action_surface.property("height") - review_button_bottom >= 22, (
        action_surface.property("height"),
        review_button_bottom,
    )
    assert abs(action_bottom - dashboard_bottom) <= 1
    if height >= 720:
        assert district_map.property("height") >= 300, (
            width,
            height,
            district_map.property("height"),
        )
    assert action_bottom <= window.height() + 1, (
        width,
        height,
        action_bottom,
        window.height(),
    )
    assert source_label.property("width") > 0, (
        width,
        source_label.property("width"),
        source_bar.property("width"),
        window.property("workflowStep"),
    )
    assert download_button.property("visible")
    for button in (download_button,):
        assert button.property("visible")
        assert button.property("height") >= 40
        assert button.property("height") <= 44, (
            button.objectName(),
            button.property("height"),
        )
        assert button.property("x") + button.property("width") <= (
            action_buttons.property("width") + 1
        )
        assert button.property("y") + button.property("height") <= (
            action_buttons.property("height") + 1
        )
    assert download_button.property("width") > 0
    assert history_button.property("visible")
    history_left = history_button.mapToItem(dashboard_header, QPointF(0, 0)).x()
    logs_right = logs_button.mapToItem(
        dashboard_header,
        QPointF(logs_button.property("width"), 0),
    ).x()
    assert history_left >= logs_right - 1
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
        for control in (
            map_clear_button,
            child("addFilterSetButton"),
            clear_all_filters_button,
        ):
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
            map_level_label,
            map_advance_button,
            child("publicManagement"),
            child("publicConventionManagement"),
            child("privateManagement"),
            child("initialLevel"),
            child("primaryLevel"),
            child("secondaryLevel"),
            child("alternativeLevel"),
            child("specialLevel"),
            child("pedagogicalLevel"),
            child("artisticLevel"),
            child("technologicalLevel"),
            child("cetproLevel"),
            child("addFilterSetButton"),
            clear_all_filters_button,
        )
        map_bottom = district_map.mapToItem(
            viewport,
            QPointF(0, district_map.property("height")),
        ).y()
        assert map_bottom <= viewport_height + 1, (
            map_bottom,
            viewport_height,
            scroll_view.property("contentHeight"),
        )
        for item in visible_controls:
            bottom = item.mapToItem(
                viewport,
                QPointF(0, item.property("height")),
            ).y()
            assert bottom <= scroll_view.property("contentHeight") + 1, (
                item.objectName(),
                bottom,
                scroll_view.property("contentHeight"),
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
assert child("initialLevel")
assert child("publicConventionManagement")
assert child("alternativeLevel")
assert child("specialLevel")
assert child("pedagogicalLevel")
assert child("artisticLevel")
assert child("technologicalLevel")
child("primaryLevel").setProperty("checked", True)
child("cetproLevel").setProperty("checked", True)
assert add_filter_button_label.property("color").name() == "#ffffff"
assert QMetaObject.invokeMethod(window, "addFilterSet")
assert filter_sets.property("count") == 2
assert QMetaObject.invokeMethod(window, "addFilterSet")
assert filter_sets.property("count") == 2
assert "ya está en Mis búsquedas" in help_text.property("text")
assert scrape_button_label.property("color").name() == "#ffffff"
download_button.click()
application.processEvents()
confirmation_dialog = child("exportConfirmationDialog")
assert not confirmation_dialog.property("visible")
assert school_wizard_review_page.property("visible")
assert_persistent_header(school_wizard_review_page)
assert_logs_accessible(logs_button)
review_summary = child("searchReviewSummary")
assert "primaria" in review_summary.property("text")
original_summary = review_summary.property("text")
review_summary.setProperty(
    "text",
    "\n".join(f"Búsqueda {index}: " + "departamento " * 12 for index in range(1, 101)),
)
review_scroll = child("searchReviewScroll")
review_scrollbar = child("searchReviewScrollBar")
review_frame = child("searchReviewFrame")
for width, theme, thumb_color in (
    (560, "dark", "#78d9b2"),
    (1280, "light", "#0f6b4e"),
):
    view_model.setThemeMode(theme)
    window.resize(width, 600)
    application.processEvents()
    assert review_scroll.property("contentHeight") > review_scroll.property("height")
    assert review_scrollbar.property("visible")
    assert review_scrollbar.property("opacity") == 1
    assert review_scrollbar.property("contentItem").property("opacity") == 1
    assert review_scrollbar.property("visualSize") >= 0.08
    assert review_scrollbar.property("height") >= review_scroll.property("height") - 1
    assert review_scrollbar.property("contentItem").property("height") > 0
    assert (
        review_scrollbar.property("contentItem").property("color").name() == thumb_color
    )
    bar_right = review_scrollbar.mapToItem(
        review_scroll, QPointF(review_scrollbar.width(), 0)
    ).x()
    assert abs(bar_right - review_scroll.width()) <= 1, bar_right
    review_title = child("reviewTitle")
    assert_persistent_header(school_wizard_review_page)
    assert review_title.property("width") > 0
    back_button = child("backToSearchButton")
    start_button = child("startSearchButton")
    assert (
        review_frame.mapToItem(
            school_wizard_review_page, QPointF(0, review_frame.height())
        ).y()
        <= back_button.mapToItem(school_wizard_review_page, QPointF(0, 0)).y()
    )
    for button in (back_button, start_button):
        assert (
            button.mapToItem(
                school_wizard_review_page, QPointF(button.width(), button.height())
            ).y()
            <= school_wizard_review_page.height()
        )
    review_scrollbar.setProperty("position", 0.0)
    application.processEvents()
    track_click = review_scrollbar.mapToItem(
        window.contentItem(),
        QPointF(review_scrollbar.width() / 2, review_scrollbar.height() - 4),
    ).toPoint()
    QTest.mouseClick(window, Qt.LeftButton, Qt.NoModifier, track_click)
    application.processEvents()
    flickable = review_scroll.property("contentItem")
    assert flickable.property("contentY") > 0
    review_scrollbar.setProperty("position", 1.0 - review_scrollbar.property("size"))
    application.processEvents()
    assert flickable.property("contentY") + flickable.property("height") >= (
        flickable.property("contentHeight") - 1
    )
view_model.setThemeMode("system")
review_summary.setProperty("text", original_summary)
application.processEvents()
assert not action_bar.property("visible")
child("startSearchButton").click()
application.processEvents()
assert view_model.scrapeRunning, (
    view_model.statusText,
    view_model_errors,
    "\n".join(warning.toString() for warning in qml_warnings),
)
assert "Preparando" not in school_wizard_status.property("text"), (
    school_wizard_status.property("text"),
    qml_warnings,
)
assert scrape_runner.started_requests[0][1][0].level == ("primaria", "cetpro")
assert [item.district_code for item in scrape_runner.started_requests[0][1]] == [
    "150101",
    "130112",
]
cancel_button = child("cancelScrapeButton")
assert not cancel_button.property("visible")
assert school_wizard_page.property("visible")
assert_persistent_header(school_wizard_page)
assert_logs_accessible(logs_button)
assert school_wizard_cancel_button.property("visible")
assert not download_button.property("visible")
for width in (560, 1280):
    window.resize(width, 600)
    application.processEvents()
    assert not action_bar.property("visible")
    assert school_wizard_page.property("visible")
    assert live_schools_map.property("width") > 0
    assert live_schools_map.property("height") >= 180
    assert_persistent_header(school_wizard_page)
    wizard_title = child("schoolWizardTitle")
    assert wizard_title.property("width") > 0
    for button in (school_wizard_cancel_button,):
        assert button.property("visible")
        assert (
            button.mapToItem(
                school_wizard_page,
                QPointF(button.property("width"), 0),
            ).x()
            <= school_wizard_page.property("width") + 1
        )
assert len(scrape_runner.started_requests) == 1
scrape_output_path = scrape_runner.started_requests[0][0]
assert scrape_output_path.name == "colegios_escale.xlsx"
assert export_runner.started_requests == []

school_record = {
    "nombre": "Colegio Ejemplo",
    "direccion": "Av. Principal",
    "localidad": "Localidad",
    "cp": "Localidad",
    "turno": "Mañana",
    "alumnos": 10,
    "secciones": 2,
    "longitud": -77.2,
    "latitud": -12.1,
    "distrito": "Distrito",
    "provincia": "Provincia",
    "region": "Departamento",
    "gestion": "publica",
    "nivel": "primaria",
    "codigo_local": "000123",
}
scrape_runner.schoolsReceived.emit([school_record])
application.processEvents()
assert live_schools_map.property("schoolPointCount") == 1, (
    live_schools_map.property("schoolPointCount"),
    school_locations,
    [warning.toString() for warning in qml_warnings],
)
assert "1 colegios con coordenadas" in school_wizard_point_count.property("text"), (
    school_wizard_point_count.property("text"),
    live_schools_map.property("schoolPointCount"),
)
school_id = school_locations[0]["schoolId"]
assert QMetaObject.invokeMethod(
    live_map_bridge,
    "selectSchoolFromWeb",
    Q_ARG("QVariant", school_id),
)
application.processEvents()
assert school_details_card.property("visible")
assert view_model.schoolDetails(school_id)["nombre"] == "Colegio Ejemplo"
assert child("schoolDetailsName").property("text") == "Colegio Ejemplo"
assert child("schoolDetailsAddress").property("text") == "Av. Principal"
assert child("schoolDetailsSettlement").property("text") == "Localidad"
assert child("schoolDetailsAlumnos").property("text") == "10"
assert child("schoolDetailsSecciones").property("text") == "2"
assert child("schoolDetailsOfferCount").property("text") == "1 oferta"
assert 0 < school_details_card.property("height") < live_schools_map.property("height")
assert qml_value(district_map, "selectedCodes") == ["150101", "130112"]
child("closeSchoolDetailsButton").click()
application.processEvents()
assert not school_details_card.property("visible")

school_batch_sizes = []
live_schools_map.schoolLocationsBatchSent.connect(school_batch_sizes.append)
live_schools_map.clearSchoolLocations()
application.processEvents()
large_school_batch = [
    {
        "schoolId": "batch-" + str(index),
        "longitud": -81.3 if index == 0 else -68.7 if index == 1 else -77.2,
        "latitud": -18.3 if index == 0 else -0.05 if index == 1 else -12.1,
    }
    for index in range(1201)
]
live_schools_map.appendSchoolLocations(large_school_batch)
batch_deadline = time.monotonic() + 10
while (
    sum(school_batch_sizes) < len(large_school_batch)
    and time.monotonic() < batch_deadline
):
    application.processEvents()
    time.sleep(0.025)
assert sum(school_batch_sizes) == len(large_school_batch), (
    school_batch_sizes,
    qml_value(live_schools_map, "pendingSchoolBatches"),
    live_schools_map.property("mapReady"),
)
assert all(0 < size <= 500 for size in school_batch_sizes), school_batch_sizes

database_path = Path(sys.argv[2]).parent / "schools.sqlite3"
scrape_runner.completed.emit(str(database_path), 1, 1, 1)
application.processEvents()
fit_deadline = time.monotonic() + 10
while (
    not live_schools_map.property("schoolMapBoundsJson")
    and time.monotonic() < fit_deadline
):
    application.processEvents()
    time.sleep(0.025)
assert live_schools_map.property("schoolMapBoundsJson"), (
    live_schools_map.property("schoolPointCount"),
    live_schools_map.property("schoolFitPending"),
    qml_value(live_schools_map, "pendingSchoolBatches"),
    live_schools_map.property("mapReady"),
    live_schools_map.property("schoolMode"),
    live_schools_map.property("loadingError"),
    window.property("schoolWizardState"),
)
school_map_bounds = json.loads(live_schools_map.property("schoolMapBoundsJson"))
assert school_map_bounds[0][0] <= min(
    location["longitud"] for location in large_school_batch
)
assert school_map_bounds[0][1] <= min(
    location["latitud"] for location in large_school_batch
)
assert school_map_bounds[1][0] >= max(
    location["longitud"] for location in large_school_batch
)
assert school_map_bounds[1][1] >= max(
    location["latitud"] for location in large_school_batch
)
live_schools_map.clearSchoolLocations()
application.processEvents()
assert school_wizard_page.property("visible")
assert_persistent_header(school_wizard_page)
assert_logs_accessible(logs_button)
assert not school_wizard_progress.property("visible")
assert school_wizard_excel_button.property("visible")
assert school_wizard_pdf_button.property("visible")
assert school_wizard_eduescale_button.property("visible")
assert "Datos listos para exportar" in school_wizard_status.property("text")
default_export_path = view_model._output_path
assert default_export_path is not None
assert re.fullmatch(
    r"colegios_escale_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}(?:_\d+)?\.xlsx",
    default_export_path.name,
)
assert Path(excel_dialog.property("currentFile").toLocalFile()) == (default_export_path)
assert Path(eduescale_dialog.property("currentFile").toLocalFile()) == (
    default_export_path.with_suffix(".eduescale")
)
assert Path(pdf_dialog.property("currentFile").toLocalFile()) == (
    default_export_path.with_suffix(".pdf")
)
child("schoolWizardCloseButton").click()
application.processEvents()
assert not school_wizard_page.property("visible")
assert view_model.property("exportsAvailable")
assert window.findChild(QObject, "downloadExcelButton") is None
assert window.findChild(QObject, "downloadPdfButton") is None
assert window.findChild(QObject, "downloadEduEscaleButton") is None

assert export_runner.started_requests == []
chosen_excel_path = default_export_path.parent / "colegios-personalizado.xlsx"
view_model.exportExcelFile(QUrl.fromLocalFile(str(chosen_excel_path)).toString())
assert export_runner.started_requests[-1] == (
    "excel",
    database_path,
    chosen_excel_path,
)
view_model._export_running = False
view_model.stateChanged.emit()
eduescale_path = default_export_path.parent / "colegios-compartir.eduescale"
view_model.exportEduEscaleFile(QUrl.fromLocalFile(str(eduescale_path)).toString())
assert export_runner.started_requests[-1] == (
    "eduescale",
    database_path,
    eduescale_path,
)
view_model._export_running = False
view_model.stateChanged.emit()
pdf_path = default_export_path.parent / "colegios-informe.pdf"
view_model.exportPdfFile(QUrl.fromLocalFile(str(pdf_path)).toString())
assert export_runner.started_requests[-1] == (
    "pdf",
    database_path,
    pdf_path,
)
view_model._export_running = False
view_model.stateChanged.emit()

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
assert search_list_scrollbar.property("visible")

page_flickable = scroll_view.property("contentItem")
action_bar_y = action_bar.mapToItem(window_content, QPointF(0, 0)).y()
page_flickable.setProperty(
    "contentY",
    scroll_view.property("contentHeight") - scroll_view.property("availableHeight"),
)
application.processEvents()
assert abs(action_bar.mapToItem(window_content, QPointF(0, 0)).y() - action_bar_y) <= 1
assert view_model.property("exportsAvailable")

theme_selector = child("themeModeSelector")
light_canvas = window.property("canvasColor")
assert theme_selector.property("iconNames").toVariant() == [
    "brightness_auto",
    "light_mode",
    "dark_mode",
]
assert theme_selector.property("currentText") == "Sistema"
assert QMetaObject.invokeMethod(theme_selector, "activated", Q_ARG(int, 2))
application.processEvents()
assert view_model.darkTheme is True
assert view_model.themeMode == "dark"
assert theme_selector.property("currentText") == "Oscuro"
assert window.property("canvasColor") != light_canvas
assert settings.value("appearance/themeMode") == "dark"

assert QMetaObject.invokeMethod(theme_selector, "activated", Q_ARG(int, 1))
application.processEvents()
assert view_model.darkTheme is False
assert theme_selector.property("currentText") == "Claro"
assert view_model.themeMode == "light"

data_source_combo = child("dataSourceCombo")
offline_database_controls = child("offlineDatabaseControls")
assert data_source_combo.property("currentText") == "ESCALE en línea"
window.resize(1280, 720)
application.processEvents()
many_searches = []
for index in range(24):
    many_searches.append(
        {
            "name": "Búsqueda de prueba " + str(index + 1),
            "regionCode": "test-" + str(index + 1),
            "provinceCode": "",
            "districtCode": "",
            "regionLabel": "Región de prueba " + str(index + 1),
            "provinceLabel": "",
            "districtLabel": "",
            "management": "publica",
            "level": "primaria",
        }
    )
view_model.savedSearchesLoaded.emit(many_searches)
application.processEvents()
assert search_list_scrollbar.property("visible")
view_model.setDataSource("offline")
application.processEvents()
assert view_model.dataSource == "offline"
assert data_source_combo.property("currentIndex") == 1
assert data_source_combo.property("displayText") == "Base offline", (
    data_source_combo.property("currentIndex"),
    data_source_combo.property("displayText"),
)
assert offline_database_controls.property("visible")
assert child("selectOfflineDatabaseButton").property("visible")
assert child("updateOfflineDatabaseButton").property("visible")
update_path = Path(sys.argv[2]).parent / "update-test.sqlite3"
assert view_model.updateOfflineDatabase(QUrl.fromLocalFile(str(update_path)).toString())
application.processEvents()
assert progress_indicator.property("visible")
assert progress_indicator.property("indeterminate")
assert status_label.property("visible")
assert "Descargando la base" in status_label.property("text")
assert cancel_button.property("visible")
assert (
    cancel_button.mapToItem(
        window_content, QPointF(0, cancel_button.property("height"))
    ).y()
    <= window.height() + 1
)
scrape_runner.progressChanged.emit(
    ProgressMetrics(received=30, total=100, unique=28, pages=2)
)
application.processEvents()
assert not progress_indicator.property("indeterminate")
assert progress_indicator.property("value") == 30
assert progress_indicator.property("to") == 100
assert "30 de 100" in status_label.property("text")
cancel_button.click()
application.processEvents()
assert scrape_runner.cancel_calls == 1
assert not cancel_button.property("enabled")
scrape_runner.cancelled.emit()
application.processEvents()
assert not progress_indicator.property("visible")
assert not cancel_button.property("visible")
offline_controls_top = offline_database_controls.mapToItem(
    source_bar,
    QPointF(0, 0),
).y()
offline_controls_bottom = offline_database_controls.mapToItem(
    source_bar,
    QPointF(0, offline_database_controls.property("height")),
).y()
assert offline_controls_top >= 0
assert offline_controls_bottom <= source_bar.property("height") + 1
source_top = source_bar.mapToItem(window_content, QPointF(0, 0)).y()
source_bottom = source_bar.mapToItem(
    window_content,
    QPointF(0, source_bar.property("height")),
).y()
offline_action_top = action_bar.mapToItem(window_content, QPointF(0, 0)).y()
assert (
    source_top
    >= scroll_view.mapToItem(
        window_content,
        QPointF(0, scroll_view.property("height")),
    ).y()
)
assert source_bottom <= offline_action_top + 1
assert source_bottom <= window.height() + 1
assert not child("scrapeButton").property("enabled")
assert clear_all_filters_button.property("enabled")
clear_all_filters_button.click()
application.processEvents()
assert filter_sets.property("count") == 0
assert window.property("mapLevel") == "department"
assert qml_value(window, "selectedDepartmentCodes") == []
assert qml_value(window, "selectedProvinceCodes") == []
assert qml_value(window, "selectedDistrictCodes") == []
for control_name in (
    "publicManagement",
    "privateManagement",
    "primaryLevel",
    "secondaryLevel",
    "cetproLevel",
):
    assert not child(control_name).property("checked")
assert child("departmentCombo").property("currentIndex") == 0
assert child("provinceCombo").property("currentIndex") == 0
assert child("districtCombo").property("currentIndex") == 0
assert not clear_all_filters_button.property("enabled")
window.resize(560, 600)
application.processEvents()
subtitle = child("brandSubtitle")
assert (
    subtitle.mapToItem(dashboard_header, QPointF(subtitle.width(), 0)).x()
    <= logs_button.mapToItem(dashboard_header, QPointF(0, 0)).x()
)
assert (
    logs_button.mapToItem(dashboard_header, QPointF(logs_button.width(), 0)).x()
    <= theme_selector.mapToItem(dashboard_header, QPointF(0, 0)).x()
)
assert (
    action_bar.mapToItem(window.contentItem(), QPointF(0, action_bar.height())).y()
    <= window.height() + 1
)

view_model.shutdown()
logs_button.click()
application.processEvents()
assert logs_dialog.property("visible")
qml_warnings.clear()
delete(view_model.logViewer)
application.processEvents()
assert not qml_warnings, "\n".join(warning.toString() for warning in qml_warnings)
assert child("logViewerError").property("visible")
assert "no está disponible" in child("logViewerError").property("text")
assert not child("logRefreshTimer").property("running")
assert not log_selector.property("enabled")
assert not page_selector.property("enabled")
assert not first_page_button.property("enabled")
assert not last_page_button.property("enabled")
assert QMetaObject.invokeMethod(log_selector, "activated", Q_ARG(int, 0))
assert QMetaObject.invokeMethod(page_selector, "valueModified")
child("closeLogsButton").click()
application.processEvents()
assert not logs_dialog.property("visible")
logs_button.click()
application.processEvents()
assert logs_dialog.property("visible")
assert child("logViewerError").property("visible")
assert not qml_warnings, "\n".join(warning.toString() for warning in qml_warnings)
child("closeLogsButton").click()
application.processEvents()
