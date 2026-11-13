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
from eduescale.application.use_cases.offline_database import (
    OfflineDatabaseUseCases,
)
from eduescale.gui.local_database import LocalDatabaseSearchRunner
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
offline_database_use_cases = OfflineDatabaseUseCases(
    SqliteOfflineDatabaseRepository()
)
controller = SchoolFinderController(
    FakeLocationService(),
    scrape_runner,
    settings=settings,
    export_runner=export_runner,
    local_database_search_runner=LocalDatabaseSearchRunner(
        offline_database_use_cases
    ),
    offline_database_use_cases=offline_database_use_cases,
    data_directory=Path(sys.argv[2]).parent,
)
engine = QQmlApplicationEngine()
qml_warnings = []
engine.warnings.connect(lambda warnings: qml_warnings.extend(warnings))
engine.setInitialProperties({"controller": controller})
engine.load(QUrl(sys.argv[1]))
assert engine.rootObjects(), "\n".join(
    warning.toString() for warning in qml_warnings
) or "Main.qml did not load"
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
excel_button = child("downloadExcelButton")
excel_button_label = child("downloadExcelButtonLabel")
pdf_button = child("downloadPdfButton")
pdf_button_label = child("downloadPdfButtonLabel")
eduescale_button = child("downloadEduEscaleButton")
eduescale_button_label = child("downloadEduEscaleButtonLabel")
excel_dialog = child("excelSaveDialog")
pdf_dialog = child("pdfSaveDialog")
eduescale_dialog = child("eduEscaleSaveDialog")
download_button = child("scrapeButton")
scrape_button_label = child("scrapeButtonLabel")
history_button = child("exportHistoryButton")
history_dialog = child("exportHistoryDialog")
offline_database_summary_dialog = child("offlineDatabaseSummaryDialog")
add_filter_button_label = child("addFilterSetButtonLabel")
history_button.click()
application.processEvents()
assert history_dialog.property("visible")
assert child("exportHistoryWarning").property("visible") is False
child("closeExportHistoryButton").click()
application.processEvents()
assert not offline_database_summary_dialog.property("visible")
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
assert map_profile.property("storageName") == "eduescale-map"
assert map_profile.property("httpUserAgent") == (
    "EduEscaleDesktop/1.0 (+https://github.com/2025je/eduescale)"
)
assert map_style_selector.property("count") == 8
assert map_style_selector.property("currentText") == "Liberty"
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
assert excel_button_label.property("color").name() == "#4d5852"
assert pdf_button_label.property("color").name() == "#4d5852"
assert eduescale_button_label.property("color").name() == "#4d5852"
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
    if width >= 1200:
        action_row_positions = {
            round(button.property("y"))
            for button in (excel_button, pdf_button, eduescale_button, history_button)
        }
        assert len(action_row_positions) == 1, (
            width,
            action_row_positions,
        )
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
    action_surface_top = action_surface.mapToItem(
        window_content,
        QPointF(0, 0),
    ).y()
    action_bottom = action_bar.mapToItem(
        window_content,
        QPointF(0, action_bar.property("height")),
    ).y()
    assert scroll_top >= header_bottom
    assert scroll_bottom <= source_top + 1
    assert source_top - scroll_bottom <= 10
    assert abs(source_bottom - action_surface_top) <= 1
    assert action_bottom <= window.height() + 1
    assert source_label.property("width") > 0
    for button in (download_button, excel_button, pdf_button, eduescale_button):
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
            child("privateManagement"),
            child("primaryLevel"),
            child("secondaryLevel"),
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
assert window.findChild(QObject, "initialLevel") is None
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
assert confirmation_dialog.property("visible")
assert "primaria" in confirmation_dialog.property("searchSummary")
child("confirmActionButton").click()
application.processEvents()
assert controller.scrapeRunning
assert scrape_runner.started_requests[0][1][0].level == ("primaria", "cetpro")
assert [item.district_code for item in scrape_runner.started_requests[0][1]] == [
    "150101",
    "130112",
]
cancel_button = child("cancelScrapeButton")
assert cancel_button.property("visible")
assert not download_button.property("visible")
for width, expected_action_columns in ((560, 2), (1280, 4)):
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
    cancel_bottom = cancel_button.mapToItem(
        action_bar,
        QPointF(0, cancel_button.property("height")),
    ).y()
    assert cancel_bottom <= action_bar.property("height") + 1
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
controller.exportEduEscaleFile(QUrl.fromLocalFile(str(eduescale_path)).toString())
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
controller._export_running = False
controller.stateChanged.emit()

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
assert eduescale_button.property("visible")

theme_selector = child("themeModeSelector")
light_canvas = window.property("canvasColor")
assert theme_selector.property("currentText") == "Sistema"
assert QMetaObject.invokeMethod(theme_selector, "activated", Q_ARG(int, 2))
application.processEvents()
assert controller.darkTheme is True
assert controller.themeMode == "dark"
assert theme_selector.property("currentText") == "Oscuro"
assert window.property("canvasColor") != light_canvas
assert settings.value("appearance/themeMode") == "dark"

assert QMetaObject.invokeMethod(theme_selector, "activated", Q_ARG(int, 1))
application.processEvents()
assert controller.darkTheme is False
assert theme_selector.property("currentText") == "Claro"
assert controller.themeMode == "light"

data_source_combo = child("dataSourceCombo")
offline_database_controls = child("offlineDatabaseControls")
assert data_source_combo.property("currentText") == "ESCALE en línea"
controller.setDataSource("offline")
application.processEvents()
assert controller.dataSource == "offline"
assert data_source_combo.property("currentIndex") == 1
assert data_source_combo.property("displayText") == "Base offline", (
    data_source_combo.property("currentIndex"),
    data_source_combo.property("displayText"),
)
assert offline_database_controls.property("visible")
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
assert (
    action_bar.mapToItem(window.contentItem(), QPointF(0, action_bar.height())).y()
    <= window.height() + 1
)

controller.shutdown()
"""


def test_main_qml_fits_supported_window_sizes(tmp_path):
    assets_path = Path(__file__).parents[1] / "eduescale" / "gui" / "assets"
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
            "-",
            "qrc:/eduescale/gui/qml/Main.qml",
            str(tmp_path / "settings.ini"),
            str(assets_path),
        ],
        capture_output=True,
        check=False,
        env=environment,
        input="# -*- coding: utf-8 -*-\n" + QML_SMOKE_SCRIPT,
        encoding="utf-8",
        timeout=90,
    )

    assert result.returncode == 0, f"{result.stdout}\n{result.stderr}"
    assert "scrapeRunning' of null" not in result.stderr
    assert "exportHistory' of null" not in result.stderr
    assert "Cannot set " not in result.stderr
    assert "QML WebEngineProfile: Please use WebEngineProfilePrototype" not in (
        result.stderr
    )
