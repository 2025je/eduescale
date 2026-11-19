import logging
from datetime import datetime
from pathlib import Path

from PySide6.QtCore import (
    Property,
    QObject,
    Qt,
    QUrl,
    Signal,
    Slot,
)
from PySide6.QtGui import QDesktopServices, QGuiApplication

from eduescale.application.errors import DataAccessError
from eduescale.application.export_policy import android_data_path
from eduescale.application.models import OfflineDatabaseLocationSummary
from eduescale.application.ports import ExportType
from eduescale.gui.contracts.ports import (
    DataSource,
    DistrictMapCatalogPort,
    ExportRunnerPort,
    LocationServicePort,
    OfflineDatabaseUseCasesPort,
)
from eduescale.gui.models.school_map import SchoolMapAccumulator
from eduescale.gui.services.export_formats import (
    EXPORT_FORMATS,
    export_format_for_signal,
)
from eduescale.gui.services.export_history import (
    MAX_EXPORT_HISTORY_ENTRIES,
    ExportHistoryEntry,
    ExportHistoryReadError,
    ExportHistoryStorePort,
)
from eduescale.gui.services.export_request_service import (
    ExportRequestError,
    ExportRequestServicePort,
)
from eduescale.gui.services.offline_database_update import (
    OfflineDatabaseUpdateServicePort,
)
from eduescale.gui.services.preferences import DesktopPreferencesPort
from eduescale.gui.services.saved_search_service import (
    PortableSavedSearchServicePort,
    SavedSearchError,
)
from eduescale.gui.services.school_search_service import (
    SchoolSearchRequestError,
    SchoolSearchRequestServicePort,
)
from eduescale.gui.services.school_search_workflow import (
    SchoolSearchWorkflowPort,
    SearchProgress,
)

LOGGER = logging.getLogger(__name__)

LOCATION_PLACEHOLDERS = {
    "regions": "Todos los departamentos",
    "provinces": "Todas las provincias",
    "districts": "Todos los distritos",
}
EXPORT_HISTORY_READ_WARNINGS: dict[ExportHistoryReadError, str] = {
    "unexpected_value_type": "No se pudo leer el historial guardado.",
    "invalid_json": "El historial guardado está dañado.",
    "not_a_list": "El historial guardado tiene un formato no válido.",
    "invalid_entry": "El historial guardado contiene una entrada no válida.",
}


class SchoolFinderViewModel(QObject):
    regionsChanged = Signal()
    provincesChanged = Signal()
    districtsChanged = Signal()
    darkThemeChanged = Signal()
    themeModeChanged = Signal()
    savedSearchesLoaded = Signal(list)
    exportHistoryChanged = Signal()
    schoolLocationsReceived = Signal(list)
    schoolSearchStarted = Signal()
    schoolSearchCompleted = Signal()
    schoolSearchStopped = Signal(str)
    stateChanged = Signal()
    errorRaised = Signal(str, str)

    def __init__(
        self,
        location_service: LocationServicePort,
        school_search_workflow: SchoolSearchWorkflowPort,
        parent: QObject | None = None,
        export_runner: ExportRunnerPort | None = None,
        *,
        offline_database_use_cases: OfflineDatabaseUseCasesPort,
        offline_database_update_service: OfflineDatabaseUpdateServicePort,
        export_history_store: ExportHistoryStorePort,
        district_map_catalog: DistrictMapCatalogPort,
        preferences: DesktopPreferencesPort,
        saved_search_service: PortableSavedSearchServicePort,
        school_search_service: SchoolSearchRequestServicePort,
        export_request_service: ExportRequestServicePort,
        data_directory: Path,
    ):
        super().__init__(parent)
        if export_runner is None:
            raise ValueError("El runner de exportación es obligatorio.")
        self._offline_database_use_cases = offline_database_use_cases
        self._offline_database_update_service = offline_database_update_service
        self._export_history_store = export_history_store
        self._district_map_catalog = district_map_catalog
        self._data_directory = data_directory
        self._preferences = preferences
        self._saved_search_service = saved_search_service
        self._school_search_service = school_search_service
        self._export_request_service = export_request_service
        self._theme_mode = preferences.theme_mode()
        self._dark_theme = self._effective_dark_theme()
        self._data_source: DataSource = preferences.data_source()
        self._offline_database_path = preferences.offline_database_path()
        self._offline_database_stats = self._database_stats_text(
            self._offline_database_path
        )
        self._offline_database_location_summary = OfflineDatabaseLocationSummary()
        if (
            self._offline_database_path is not None
            and self._offline_database_path.is_file()
        ):
            try:
                self._offline_database_stats = self._database_stats_text(
                    self._offline_database_path,
                    self._offline_database_use_cases.read_metadata(
                        self._offline_database_path
                    ),
                )
            except (DataAccessError, OSError, TypeError, ValueError) as error:
                LOGGER.error(
                    "Could not read offline database statistics for %s: %s",
                    self._offline_database_path,
                    error,
                )
        self._database_update_target: Path | None = None
        application = QGuiApplication.instance()
        if isinstance(application, QGuiApplication):
            application.styleHints().colorSchemeChanged.connect(
                self._system_color_scheme_changed
            )
        self._regions = [{"label": "Cargando departamentos...", "code": ""}]
        self._provinces = [{"label": LOCATION_PLACEHOLDERS["provinces"], "code": ""}]
        self._districts = [{"label": LOCATION_PLACEHOLDERS["districts"], "code": ""}]
        self._regions_enabled = False
        self._provinces_enabled = False
        self._districts_enabled = False
        self._selected_region_code = ""
        self._selected_province_code = ""
        self._district_metadata = district_map_catalog.district_metadata
        self._district_codes = district_map_catalog.district_codes
        self._district_codes_by_province = (
            district_map_catalog.district_codes_by_province
        )
        self._map_department_metadata = district_map_catalog.map_department_metadata
        self._map_province_metadata = district_map_catalog.map_province_metadata
        self._status_text = "Límites distritales cargados."
        self._progress_visible = False
        self._progress_value = 0
        self._progress_maximum = 0
        self._progress_message = ""
        self._progress_indeterminate = True
        self._scrape_running = False
        self._school_search_active = False
        self._school_map_accumulator = SchoolMapAccumulator()
        self._school_details: dict[str, dict[str, object]] = {}
        self._school_points_sent: set[str] = set()
        self._cancel_requested = False
        self._export_running = False
        self._exports_available = False
        self._output_path: Path | None = None
        self._dataset_path: Path | None = None
        self._last_search_summary = ""
        self._export_history, history_error = self._export_history_store.load()
        self._export_history_warning = (
            EXPORT_HISTORY_READ_WARNINGS[history_error]
            if history_error is not None
            else ""
        )

        self._location_service = location_service
        self._school_search_workflow = school_search_workflow
        self._export_runner = export_runner
        for dependency in (
            location_service,
            school_search_workflow,
            self._export_runner,
        ):
            if isinstance(dependency, QObject) and dependency.parent() is None:
                dependency.setParent(self)
        self._location_service.loaded.connect(self._locations_loaded)
        self._location_service.failed.connect(self._locations_failed)
        self._school_search_workflow.started.connect(self._scrape_started)
        self._school_search_workflow.progressChanged.connect(self._scrape_progress)
        self._school_search_workflow.schoolsReceived.connect(
            self._receive_school_batch
        )
        self._school_search_workflow.searchCompleted.connect(
            self._search_completed
        )
        self._school_search_workflow.databaseUpdateCompleted.connect(
            self._database_update_completed
        )
        self._school_search_workflow.cancelled.connect(self._scrape_cancelled)
        self._school_search_workflow.failed.connect(self._scrape_failed)
        self._school_search_workflow.warning.connect(self._scrape_warning)
        self._export_runner.completed.connect(self._export_completed)
        self._export_runner.failed.connect(self._export_failed)
        LOGGER.info("Loaded local district boundary data.")
        self._request_locations("regions")

    @Property(list, notify=regionsChanged)
    def regions(self):
        return self._regions

    @Property(list, notify=provincesChanged)
    def provinces(self):
        return self._provinces

    @Property(list, notify=districtsChanged)
    def districts(self):
        return self._districts

    @Property(list, notify=stateChanged)
    def districtCodes(self):
        return self._district_codes

    @Property(int, notify=stateChanged)
    def districtCount(self):
        return len(self._district_codes)

    @Property(dict, notify=stateChanged)
    def mapDepartmentMetadata(self):
        return self._map_department_metadata

    @Property(dict, notify=stateChanged)
    def mapProvinceMetadata(self):
        return self._map_province_metadata

    @Slot(list, list, list, result=list)
    def effectiveDistrictCodes(
        self,
        department_codes: list[str],
        province_codes: list[str],
        district_codes: list[str],
    ) -> list[str]:
        return self._district_map_catalog.effective_district_codes(
            department_codes,
            province_codes,
            district_codes,
        )

    @Slot(str, result="QVariant")
    def mapDistrictProperties(self, code: str) -> dict[str, str] | None:
        properties = self._district_map_catalog.district_properties(code)
        if properties is None:
            LOGGER.error("Requested metadata for unknown district code %s.", code)
        return properties

    @Slot(str, result="QVariant")
    def schoolDetails(self, school_id: str) -> dict[str, object]:
        details = self._school_details.get(school_id)
        if details is None:
            LOGGER.warning("Requested details for unknown school %s.", school_id)
            return {}
        return details.copy()

    @Property(bool, notify=stateChanged)
    def regionsEnabled(self):
        return self._regions_enabled

    @Property(bool, notify=stateChanged)
    def provincesEnabled(self):
        return self._provinces_enabled

    @Property(bool, notify=stateChanged)
    def districtsEnabled(self):
        return self._districts_enabled

    @Property(str, notify=themeModeChanged)
    def themeMode(self):
        return self._theme_mode

    @Property(bool, notify=darkThemeChanged)
    def darkTheme(self):
        return self._dark_theme

    @Property(list, notify=exportHistoryChanged)
    def exportHistory(self):
        return self._export_history

    @Property(str, notify=exportHistoryChanged)
    def exportHistoryWarning(self):
        return self._export_history_warning

    def _record_export_history(
        self,
        export_type: str,
        file_name: str,
        status: str,
    ):
        export_format = export_format_for_signal(export_type)
        entry: ExportHistoryEntry = {
            "timestamp": datetime.now().astimezone().isoformat(timespec="seconds"),
            "format": export_format.label if export_format else export_type,
            "file": Path(file_name).name,
            "status": status,
            "filters": self._last_search_summary,
        }
        self._export_history = [entry, *self._export_history][
            :MAX_EXPORT_HISTORY_ENTRIES
        ]
        if not self._export_history_store.save(self._export_history):
            LOGGER.error("Could not save export history.")
            self._export_history_warning = "No se pudo guardar el historial."
            self.errorRaised.emit(
                "No se pudo guardar el historial",
                "La exportación terminó, pero su registro no se pudo guardar.",
            )
        self.exportHistoryChanged.emit()

    @staticmethod
    def _system_prefers_dark_theme() -> bool:
        application = QGuiApplication.instance()
        return (
            isinstance(application, QGuiApplication)
            and application.styleHints().colorScheme() == Qt.ColorScheme.Dark
        )

    def _effective_dark_theme(self) -> bool:
        if self._theme_mode == "dark":
            return True
        if self._theme_mode == "light":
            return False
        return self._system_prefers_dark_theme()

    def _system_color_scheme_changed(self, *_args: object):
        if self._theme_mode != "system":
            return
        dark_theme = self._system_prefers_dark_theme()
        if dark_theme != self._dark_theme:
            self._dark_theme = dark_theme
            self.darkThemeChanged.emit()

    @Slot(str)
    def setThemeMode(self, theme_mode: str):
        if theme_mode not in ("system", "light", "dark"):
            LOGGER.error("Ignoring invalid theme mode %r.", theme_mode)
            self.errorRaised.emit(
                "Tema no válido",
                "Elige Sistema, Claro u Oscuro.",
            )
            return
        if self._theme_mode == theme_mode:
            return
        old_dark_theme = self._dark_theme
        settings_saved = self._preferences.save_theme_mode(theme_mode)
        self._theme_mode = theme_mode
        self._dark_theme = self._effective_dark_theme()
        self.themeModeChanged.emit()
        if self._dark_theme != old_dark_theme:
            self.darkThemeChanged.emit()
        if not settings_saved:
            LOGGER.error("Could not save the selected appearance mode.")
            self.errorRaised.emit(
                "No se pudo guardar el tema",
                "La preferencia se aplicó, pero no se pudo guardar para la próxima vez.",
            )

    @Slot(bool)
    def setDarkTheme(self, dark_theme: bool):
        self.setThemeMode("dark" if dark_theme else "light")

    @Property(str, notify=stateChanged)
    def statusText(self):
        return self._status_text

    @Property(bool, notify=stateChanged)
    def progressVisible(self):
        return self._progress_visible

    @Property(int, notify=stateChanged)
    def progressValue(self):
        return self._progress_value

    @Property(int, notify=stateChanged)
    def progressMaximum(self):
        return self._progress_maximum

    @Property(bool, notify=stateChanged)
    def progressIndeterminate(self):
        return self._progress_indeterminate

    @Property(str, notify=stateChanged)
    def progressMessage(self):
        return self._progress_message

    @Property(bool, notify=stateChanged)
    def scrapeRunning(self):
        return self._scrape_running

    @Property(bool, notify=stateChanged)
    def cancelRequested(self):
        return self._cancel_requested

    @Property(bool, notify=stateChanged)
    def exportRunning(self):
        return self._export_running

    @Property(bool, notify=stateChanged)
    def exportsAvailable(self):
        return self._exports_available

    @Property(str, notify=stateChanged)
    def dataSource(self):
        return self._data_source

    @Property(str, notify=stateChanged)
    def offlineDatabaseName(self):
        if self._offline_database_path is None:
            return "Ninguna base local seleccionada"
        if not self._offline_database_path.is_file():
            return f"No se encontró {self._offline_database_path.name}"
        return self._offline_database_path.name

    @Property(str, notify=stateChanged)
    def offlineDatabaseUrl(self):
        if self._offline_database_path is None:
            return ""
        return QUrl.fromLocalFile(str(self._offline_database_path)).toString()

    @Property(str, notify=stateChanged)
    def defaultOfflineDatabaseUrl(self):
        path = self._offline_database_path
        if path is None:
            path = self._data_directory / "escale.sqlite3"
        return QUrl.fromLocalFile(str(path)).toString()

    @Property(str, notify=stateChanged)
    def offlineDatabaseStats(self):
        return self._offline_database_stats

    @Property(dict, notify=stateChanged)
    def offlineDatabaseLocationSummary(self) -> dict[str, object]:
        return self._offline_database_location_summary.model_dump(mode="json")

    @Property(bool, notify=stateChanged)
    def offlineDatabaseAvailable(self):
        return (
            self._offline_database_path is not None
            and self._offline_database_path.is_file()
        )

    @Property(str, notify=stateChanged)
    def defaultOutputUrl(self):
        return QUrl.fromLocalFile(str(self._default_output_path())).toString()

    def _default_output_path(self) -> Path:
        return (
            self._output_path
            if self._exports_available and self._output_path is not None
            else self._next_output_path()
        )

    @Property(str, notify=stateChanged)
    def defaultEduEscaleOutputUrl(self):
        return QUrl.fromLocalFile(
            str(android_data_path(self._default_output_path()))
        ).toString()

    @Property(str, notify=stateChanged)
    def defaultPdfOutputUrl(self):
        return QUrl.fromLocalFile(
            str(self._default_output_path().with_suffix(".pdf"))
        ).toString()

    def _next_output_path(self) -> Path:
        output_directory = self._preferences.last_export_directory()
        output_path = output_directory / "colegios_escale.xlsx"
        suffix = 1
        while self._export_path_exists(output_path):
            output_path = output_directory / f"colegios_escale_{suffix}.xlsx"
            suffix += 1
        return output_path

    @staticmethod
    def _export_path_exists(base_output_path: Path) -> bool:
        return (
            base_output_path.exists()
            or android_data_path(base_output_path).exists()
            or any(
                base_output_path.with_suffix(export_format.extension).exists()
                for export_format in EXPORT_FORMATS.values()
            )
        )

    @Property(int, notify=stateChanged)
    def windowWidth(self):
        return self._preferences.window_size()[0]

    @Property(int, notify=stateChanged)
    def windowHeight(self):
        return self._preferences.window_size()[1]

    @Slot(int, int)
    def saveWindowSize(self, width: int, height: int):
        if not self._preferences.save_window_size(width, height):
            LOGGER.error("No se pudo guardar el tamaño de la ventana.")

    @Slot(str)
    def setDataSource(self, source: str):
        if source not in {"online", "offline"}:
            self.errorRaised.emit(
                "Fuente no válida",
                "Selecciona ESCALE en línea o una base local.",
            )
            return
        if self._scrape_running or self._export_running:
            return
        if source == self._data_source:
            return

        selected_source: DataSource = "offline" if source == "offline" else "online"
        self._data_source = selected_source
        if not self._preferences.save_data_source(selected_source):
            LOGGER.error("No se pudo guardar la fuente de datos.")
        if selected_source == "offline":
            self._offline_database_stats = self._database_stats_text(
                self._offline_database_path
            )
            self._status_text = (
                "Selecciona una base local de EduEscale."
                if self._offline_database_path is None
                else f"Fuente local: {self.offlineDatabaseName}."
            )
        else:
            self._status_text = "Fuente de datos: ESCALE en línea."
        self._request_locations("regions")
        if self._selected_region_code:
            self._request_locations("provinces", self._selected_region_code)
        if self._selected_province_code:
            self._request_locations("districts", self._selected_province_code)
        self.stateChanged.emit()

    @Slot(str, result=bool)
    def selectOfflineDatabase(self, database_url: str) -> bool:
        selected_url = QUrl(database_url)
        if not selected_url.isLocalFile():
            self.errorRaised.emit(
                "Ruta no válida",
                "Selecciona una base de datos SQLite local.",
            )
            return False
        database_path = Path(selected_url.toLocalFile())
        if database_path.suffix.lower() != ".sqlite3":
            self.errorRaised.emit(
                "Formato no válido",
                "Selecciona un archivo .sqlite3 de EduEscale.",
            )
            return False
        try:
            metadata, location_summary = self._offline_database_use_cases.inspect(
                database_path
            )
        except (DataAccessError, OSError, TypeError, ValueError) as error:
            LOGGER.error(
                "Refusing invalid offline database %s: %s", database_path, error
            )
            self.errorRaised.emit("Base de datos no válida", str(error))
            return False

        self._offline_database_path = database_path
        self._offline_database_stats = self._database_stats_text(
            database_path,
            metadata,
        )
        self._offline_database_location_summary = location_summary
        self._data_source = "offline"
        if not self._preferences.save_offline_database_selection(database_path):
            LOGGER.error("No se pudo guardar la ruta de la base local.")
        self._status_text = f"Base local seleccionada: {database_path.name}."
        self._request_locations("regions")
        if self._selected_region_code:
            self._request_locations("provinces", self._selected_region_code)
        if self._selected_province_code:
            self._request_locations("districts", self._selected_province_code)
        self.stateChanged.emit()
        return True

    @staticmethod
    def _database_stats_text(
        database_path: Path | None,
        metadata: dict[str, str] | None = None,
    ) -> str:
        if database_path is None or not database_path.is_file():
            return "Sin base local disponible."
        values = metadata or {}
        unique_records = values.get("unique_records")
        total_records = values.get("total_records")
        pages = values.get("pages")
        updated_at = values.get("updated_at")
        retries = values.get("pages_retried", "0")
        resumed_pages = values.get("resumed_pages", "0")
        duration_seconds = values.get("download_duration_seconds")
        if (
            unique_records is None
            or total_records is None
            or pages is None
            or updated_at is None
        ):
            return "Estadísticas pendientes de una actualización."
        try:
            unique_count = int(unique_records)
            total_count = int(total_records)
            page_count = int(pages)
            retry_count = int(retries)
            resumed_count = int(resumed_pages)
            updated = datetime.fromisoformat(updated_at.replace("Z", "+00:00"))
            formatted_date = updated.astimezone().strftime("%Y-%m-%d %H:%M")
            unique_label = "colegio único" if unique_count == 1 else "colegios únicos"
            pages_label = (
                "página verificada" if page_count == 1 else "páginas verificadas"
            )
            stats = (
                f"{unique_count:,} {unique_label} de {total_count:,} resultados · "
                f"{page_count:,} {pages_label}"
            )
            diagnostics = []
            if resumed_count:
                diagnostics.append(f"{resumed_count:,} páginas reanudadas")
            if retry_count:
                diagnostics.append(f"{retry_count:,} reintentos")
            if duration_seconds is not None:
                duration_minutes = int(duration_seconds) // 60
                diagnostics.append(f"{duration_minutes:,} min de descarga")
            if diagnostics:
                stats += " · " + " · ".join(diagnostics)
            return f"{stats} · actualizada {formatted_date}"
        except (ValueError, OverflowError) as error:
            LOGGER.error("La estadística de actualización no es válida: %s", error)
            return "La base local no contiene estadísticas válidas."

    @staticmethod
    def _location_options(placeholder: str, locations):
        return [{"label": placeholder, "code": ""}] + [
            {"label": name, "code": code} for code, name in locations
        ]

    @Slot(str)
    def selectRegion(self, region_code: str):
        LOGGER.info("Selected department code %s.", region_code or "(all)")
        self._selected_region_code = region_code
        self._selected_province_code = ""
        self._provinces = self._location_options(
            "Cargando provincias..."
            if region_code
            else LOCATION_PLACEHOLDERS["provinces"],
            [],
        )
        self._districts = self._location_options(LOCATION_PLACEHOLDERS["districts"], [])
        self._provinces_enabled = bool(region_code)
        self._districts_enabled = False
        self.provincesChanged.emit()
        self.districtsChanged.emit()
        self.stateChanged.emit()
        if region_code:
            self._request_locations("provinces", region_code)

    @Slot(str)
    def selectProvince(self, province_code: str):
        LOGGER.info("Selected province code %s.", province_code or "(all)")
        self._selected_province_code = province_code
        self._districts = self._location_options(
            "Cargando distritos..."
            if province_code
            else LOCATION_PLACEHOLDERS["districts"],
            [],
        )
        self._districts_enabled = bool(province_code)
        self.districtsChanged.emit()
        self.stateChanged.emit()
        if province_code:
            self._request_locations("districts", province_code)

    def _request_locations(self, kind: str, parent_code: str = ""):
        if self._data_source == "online":
            self._location_service.load(kind, parent_code)
            return

        if kind == "regions":
            locations = [
                (code, department["name"])
                for code, department in self._map_department_metadata.items()
            ]
        elif kind == "provinces":
            locations = [
                (code, province["name"])
                for code, province in self._map_province_metadata.items()
                if province["departmentCode"] == parent_code
            ]
        elif kind == "districts":
            locations = [
                (code, district["district"])
                for code, district in self._district_metadata.items()
                if district["provinceCode"] == parent_code
            ]
        else:
            raise ValueError(f"Tipo de ubicación desconocido: {kind}")
        self._apply_locations(kind, parent_code, locations, "localmente")

    @Slot(str, str, object)
    def _locations_loaded(self, kind, parent_code, locations):
        if self._data_source != "online":
            return
        LOGGER.info(
            "Loaded %d %s for parent code %s.",
            len(locations),
            kind,
            parent_code or "(none)",
        )
        self._apply_locations(kind, parent_code, locations, "desde ESCALE")

    def _apply_locations(self, kind, parent_code, locations, source: str):
        if kind == "regions" and not parent_code:
            self._regions = self._location_options(
                LOCATION_PLACEHOLDERS["regions"], locations
            )
            self._regions_enabled = True
            self.regionsChanged.emit()
        elif kind == "provinces" and parent_code == self._selected_region_code:
            self._provinces = self._location_options(
                LOCATION_PLACEHOLDERS["provinces"], locations
            )
            self.provincesChanged.emit()
        elif kind == "districts" and parent_code == self._selected_province_code:
            self._districts = self._location_options(
                LOCATION_PLACEHOLDERS["districts"], locations
            )
            self.districtsChanged.emit()
        else:
            return

        self._status_text = f"Ubicaciones cargadas {source}."
        self.stateChanged.emit()

    @Slot(str, str, str)
    def _locations_failed(self, kind, parent_code, message):
        if self._data_source != "online":
            return
        LOGGER.error(
            "Failed to load %s for parent code %s: %s",
            kind,
            parent_code or "(none)",
            message,
        )
        if kind == "regions" and not parent_code:
            self._regions = [{"label": "No se pudo cargar", "code": ""}]
            self._regions_enabled = False
            self.regionsChanged.emit()
        elif kind == "provinces" and parent_code == self._selected_region_code:
            self._provinces = [{"label": "No se pudo cargar", "code": ""}]
            self.provincesChanged.emit()
        elif kind == "districts" and parent_code == self._selected_province_code:
            self._districts = [{"label": "No se pudo cargar", "code": ""}]
            self.districtsChanged.emit()
        else:
            return

        self._status_text = f"No se pudo cargar la ubicación desde ESCALE: {message}"
        self.stateChanged.emit()

    @Slot(str, str, result=bool)
    @Slot(str, "QVariantList", result=bool)
    def saveSavedSearches(
        self,
        output_url: str,
        searches: list[dict[str, object]],
    ) -> bool:
        try:
            self._saved_search_service.save(output_url, searches)
        except SavedSearchError as error:
            self.errorRaised.emit(error.title, error.details)
            return False
        return True

    @Slot("QVariantMap", result=str)
    def savedSearchKey(self, search: dict[str, object]) -> str:
        return self._saved_search_service.filter_set_key(search)

    @Slot(str, result=bool)
    def loadSavedSearches(self, input_url: str) -> bool:
        try:
            searches = self._saved_search_service.load(input_url)
        except SavedSearchError as error:
            self.errorRaised.emit(error.title, error.details)
            return False
        self.savedSearchesLoaded.emit(searches)
        return True

    @Slot(str, "QVariantList")
    def startScrape(
        self,
        output_url: str,
        filter_sets: list[dict[str, object]],
    ):
        if self._scrape_running:
            return

        try:
            request = self._school_search_service.prepare(
                output_url,
                filter_sets,
                self._data_source,
                self._offline_database_path,
            )
        except SchoolSearchRequestError as error:
            self.errorRaised.emit(error.title, error.details)
            return

        self._last_search_summary = request.summary
        self._output_path = request.output_path
        if not self._preferences.save_last_export_directory(
            self._output_path.parent
        ):
            LOGGER.error("No se pudo guardar la carpeta de exportación.")
        self._cancel_requested = False
        self._school_search_active = True
        self._school_map_accumulator.clear()
        self._school_details.clear()
        self._school_points_sent.clear()
        self._database_update_target = None
        self._dataset_path = None
        self._exports_available = False
        self._scrape_running = True
        self._progress_visible = True
        self._progress_value = 0
        self._progress_maximum = 0
        self._progress_indeterminate = True
        if self._data_source == "offline":
            self._progress_message = "Buscando en la base offline..."
            self._status_text = "Leyendo la base local de EduEscale..."
        else:
            self._progress_message = "Conectando con ESCALE..."
            self._status_text = "Consultando el total de registros en ESCALE..."
        self.stateChanged.emit()

        LOGGER.info(
            "Starting extraction for %s with filters %s.",
            self._output_path,
            [filters.model_dump(mode="json") for filters in request.filter_sets],
        )
        self._school_search_workflow.start_search(request, self._data_source)

    @Slot(str, result=bool)
    def updateOfflineDatabase(self, output_url: str) -> bool:
        if self._scrape_running or self._export_running:
            self.errorRaised.emit(
                "Operación en curso",
                "Espera a que termine la operación actual antes de actualizar la base.",
            )
            return False
        selected_url = QUrl(output_url)
        if not selected_url.isLocalFile():
            self.errorRaised.emit(
                "Ruta no válida",
                "Selecciona dónde guardar la base SQLite descargada.",
            )
            return False
        database_path = Path(selected_url.toLocalFile())
        if database_path.suffix.lower() != ".sqlite3":
            self.errorRaised.emit(
                "Formato no válido",
                "La base offline debe usar la extensión .sqlite3.",
            )
            return False

        self._database_update_target = database_path
        self._cancel_requested = False
        self._scrape_running = True
        self._progress_visible = True
        self._progress_value = 0
        self._progress_maximum = 0
        self._progress_indeterminate = True
        self._progress_message = (
            "Descargando la base completa de ESCALE; puede tardar hasta 60 minutos..."
        )
        self._status_text = "Consultando el total de registros de ESCALE..."
        self.stateChanged.emit()
        LOGGER.info("Updating offline database at %s.", database_path)
        self._school_search_workflow.start_database_update(database_path)
        return True

    @Slot()
    def cancelScrape(self):
        if self._scrape_running and not self._cancel_requested:
            LOGGER.info("Cancellation requested by the user.")
            self._cancel_requested = True
            self.stateChanged.emit()
            self._school_search_workflow.cancel()

    @Slot()
    def _scrape_started(self):
        self._scrape_running = True
        if self._school_search_active:
            self.schoolSearchStarted.emit()
        self.stateChanged.emit()

    @Slot(object)
    def _receive_school_batch(self, records):
        if not isinstance(records, list):
            LOGGER.error("Received an invalid batch of school records.")
            return
        try:
            details_batch = self._school_map_accumulator.add_batch(records)
        except (TypeError, ValueError) as error:
            LOGGER.exception("Could not prepare school locations for the map.")
            self.errorRaised.emit(
                "No se pudieron mostrar colegios en el mapa",
                str(error),
            )
            return

        new_points = []
        for details in details_batch:
            school_id = str(details["schoolId"])
            self._school_details[school_id] = details
            if school_id in self._school_points_sent:
                continue
            self._school_points_sent.add(school_id)
            new_points.append(
                {
                    "schoolId": school_id,
                    "longitud": details["longitud"],
                    "latitud": details["latitud"],
                }
            )
        if new_points:
            self.schoolLocationsReceived.emit(new_points)

    @Slot(object)
    def _scrape_progress(self, progress: SearchProgress):
        metrics = progress.metrics
        LOGGER.debug(
            "Scrape progress: %d/%d results received across %d pages.",
            metrics.received,
            metrics.total,
            metrics.pages,
        )
        self._progress_value = metrics.received if metrics.total else 1
        self._progress_maximum = max(metrics.total, metrics.received, 1)
        self._progress_indeterminate = False
        message = self._format_progress_message(progress)
        self._progress_message = message
        self._status_text = message
        self.stateChanged.emit()

    @staticmethod
    def _format_progress_message(progress: SearchProgress) -> str:
        metrics = progress.metrics
        if progress.operation == "offline_search":
            return (
                f"Base offline · {metrics.received:,} de {metrics.total:,} registros · "
                f"{metrics.unique:,} coinciden"
            )

        if metrics.total:
            page_label = "página" if metrics.pages == 1 else "páginas"
            message = (
                f"{metrics.received:,} de {metrics.total:,} resultados · "
                f"{metrics.unique:,} colegios únicos · "
                f"{metrics.pages} {page_label}"
            )
        else:
            message = "Sin registros"
        if metrics.failed:
            failed_label = (
                "página fallida" if metrics.failed == 1 else "páginas fallidas"
            )
            message += f" · {metrics.failed} {failed_label}"
        if metrics.resumed:
            message += f" · {metrics.resumed} páginas reutilizadas"
        if metrics.retried:
            message += f" · {metrics.retried} reintentos"
        return message

    @Slot(str, int, int, int, bool)
    def _search_completed(self, database_path, total, records, pages, offline):
        self._complete_search(database_path, total, records, pages, offline=offline)

    @Slot(str, int, int, int)
    def _database_update_completed(self, database_path, total, records, pages):
        self._offline_database_updated(
            Path(database_path),
            total,
            records,
            pages,
        )

    def _complete_search(self, database_path, total, records, pages, *, offline):
        LOGGER.info(
            "%s completed: %d unique records across %d pages; data=%s",
            "Offline search" if offline else "Extraction",
            records,
            pages,
            database_path,
        )
        self._scrape_running = False
        self._cancel_requested = False
        self._progress_visible = True
        self._progress_indeterminate = False
        self._progress_maximum = max(total, records, 1)
        self._progress_value = self._progress_maximum
        self._progress_message = "Completado"
        self._dataset_path = Path(database_path)
        output_directory = (
            self._output_path.parent
            if self._output_path is not None
            else self._next_output_path().parent
        )
        self._output_path = self._timestamped_output_path(output_directory)
        if offline:
            self._status_text = (
                f"Búsqueda local completada · {records:,} de {total:,} "
                "colegios coinciden."
            )
            self._exports_available = records > 0
        else:
            self._status_text = (
                f"Datos listos para exportar · {records:,} colegios en {pages} páginas."
            )
            self._exports_available = True
        self.stateChanged.emit()
        if self._school_search_active:
            self._school_search_active = False
            self.schoolSearchCompleted.emit()

    @Slot(str)
    def _scrape_cancelled(self, operation):
        if operation == "database_update":
            LOGGER.info("Offline database update cancelled; existing data preserved.")
            self._database_update_target = None
            self._scrape_running = False
            self._cancel_requested = False
            self._progress_visible = False
            self._progress_indeterminate = False
            self._progress_message = ""
            self._status_text = (
                "Actualización cancelada. La base anterior se conservó; "
                "el progreso completo podrá reanudarse al actualizar de nuevo."
            )
            self.stateChanged.emit()
            return
        LOGGER.info("Extraction cancelled; previous output was preserved.")
        self._scrape_running = False
        self._cancel_requested = False
        self._progress_visible = False
        self._progress_indeterminate = False
        self._progress_message = ""
        self._dataset_path = None
        self._exports_available = False
        self._status_text = (
            "Búsqueda cancelada. El mapa muestra resultados parciales; "
            "no se puede exportar una búsqueda incompleta."
        )
        self.stateChanged.emit()
        if self._school_search_active:
            self._school_search_active = False
            self.schoolSearchStopped.emit("cancelled")

    @Slot(str, str, str)
    def _scrape_failed(self, operation, title, details):
        LOGGER.error("Extraction failed (%s): %s", title, details)
        if operation == "database_update":
            self._database_update_target = None
            self._scrape_running = False
            self._cancel_requested = False
            self._progress_visible = False
            self._progress_indeterminate = False
            self._progress_message = ""
            self._status_text = (
                f"No se pudo actualizar la base offline: {title}. "
                "La base anterior se conservó; las páginas completas podrán "
                "reutilizarse al reintentar."
            )
            self.stateChanged.emit()
            self.errorRaised.emit(title, details)
            return
        self._scrape_running = False
        self._dataset_path = None
        self._exports_available = False
        self._cancel_requested = False
        self._progress_visible = False
        self._status_text = title
        self.stateChanged.emit()
        self.errorRaised.emit(title, details)
        if self._school_search_active:
            self._school_search_active = False
            self.schoolSearchStopped.emit("failed")

    def _offline_database_updated(
        self,
        staged_path: Path,
        total: int,
        records: int,
        pages: int,
    ):
        target_path = self._database_update_target
        if target_path is None:
            raise RuntimeError("No hay una actualización de base offline activa.")
        try:
            update_result = self._offline_database_update_service.publish(
                staged_path,
                target_path,
                total_records=total,
                unique_records=records,
                pages=pages,
            )
        except (DataAccessError, OSError, TypeError, ValueError) as error:
            LOGGER.exception("Could not publish offline database %s.", target_path)
            self._database_update_target = None
            self._scrape_running = False
            self._cancel_requested = False
            self._progress_visible = False
            self._progress_indeterminate = False
            self._progress_message = ""
            self._status_text = "No se pudo actualizar la base offline."
            self.stateChanged.emit()
            self.errorRaised.emit(
                "No se pudo actualizar la base offline",
                str(error),
            )
            return

        metadata = update_result.metadata
        checkpoint_cleanup_error = update_result.checkpoint_cleanup_error
        if checkpoint_cleanup_error:
            LOGGER.warning(
                "Could not clean completed offline download checkpoint: %s",
                checkpoint_cleanup_error,
            )
        self._database_update_target = None
        self._offline_database_path = target_path
        self._offline_database_stats = self._database_stats_text(
            target_path,
            metadata,
        )
        if not self._preferences.save_offline_database_path(target_path):
            LOGGER.error("Could not save offline database path.")
        self._scrape_running = False
        self._cancel_requested = False
        self._progress_visible = True
        self._progress_indeterminate = False
        self._progress_maximum = max(total, records, 1)
        self._progress_value = self._progress_maximum
        self._progress_message = "Actualización completada"
        unique_label = "colegio único" if records == 1 else "colegios únicos"
        self._status_text = (
            f"Base offline actualizada · {records:,} {unique_label} "
            f"de {total:,} · {pages:,} "
            f"página{'s' if pages != 1 else ''}."
        )
        if checkpoint_cleanup_error:
            self._status_text += " No se pudo limpiar el progreso temporal."
        self.stateChanged.emit()

    @Slot(str, str)
    def _scrape_warning(self, title, details):
        LOGGER.warning("%s: %s", title, details)
        self.errorRaised.emit(title, details)

    @Slot(str)
    def exportExcelFile(self, output_url: str):
        self._start_export("excel", output_url)

    @Slot(str)
    @Slot(str, str)
    @Slot(str, str, bool)
    def exportEduEscaleFile(
        self,
        output_url: str,
        password: str = "",
        compressed: bool = False,
    ):
        self._start_export(
            "eduescale",
            output_url,
            password=password or None,
            compressed=compressed,
        )

    @Slot(str)
    def exportPdfFile(self, output_url: str):
        self._start_export("pdf", output_url)

    @staticmethod
    def _timestamped_output_path(directory: Path) -> Path:
        timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
        output_path = directory / f"colegios_escale_{timestamp}.xlsx"
        suffix = 1
        while SchoolFinderViewModel._export_path_exists(output_path):
            output_path = directory / f"colegios_escale_{timestamp}_{suffix}.xlsx"
            suffix += 1
        return output_path

    def _start_export(
        self,
        export_type: ExportType,
        output_url: str,
        password: str | None = None,
        compressed: bool = False,
    ):
        if self._export_running:
            return
        if not self._exports_available or self._dataset_path is None:
            self.errorRaised.emit(
                "Datos no disponibles",
                "Completa una descarga antes de exportar los archivos.",
            )
            return
        if self._output_path is None:
            self.errorRaised.emit(
                "Ruta no disponible",
                "No se encontró una carpeta válida para guardar la exportación.",
            )
            return

        try:
            request = self._export_request_service.prepare(
                output_url,
                export_type,
            )
        except ExportRequestError as error:
            self.errorRaised.emit(error.title, error.details)
            return
        output_path = request.output_path
        self._output_path = request.base_output_path
        if not self._preferences.save_last_export_directory(output_path.parent):
            LOGGER.error("No se pudo guardar la carpeta de exportación.")

        self._export_running = True
        self._progress_visible = True
        self._progress_indeterminate = True
        self._progress_message = request.format.progress_message
        self._status_text = self._progress_message
        self.stateChanged.emit()
        if password is not None or compressed:
            self._export_runner.start(
                export_type,
                self._dataset_path,
                output_path,
                password=password,
                compressed=compressed,
            )
        else:
            self._export_runner.start(export_type, self._dataset_path, output_path)

    @Slot(str, str)
    def _export_completed(self, export_type: str, output_path: str):
        self._export_running = False
        path = Path(output_path)
        LOGGER.info("Exported %s to %s.", export_type, path)
        self._record_export_history(export_type, path.name, "Completada")
        self._progress_visible = False
        self._progress_indeterminate = False
        self._progress_message = ""
        self._status_text = f"Archivo creado: {path.name}"
        self.stateChanged.emit()
        self._open_export_file(path)

    @Slot(str, str, str)
    def _export_failed(self, export_type: str, details: str, file_name: str):
        self._export_running = False
        self._record_export_history(export_type, file_name, "Fallida")
        self._progress_visible = False
        self._progress_indeterminate = False
        self._progress_message = ""
        export_format = export_format_for_signal(export_type)
        export_label = export_format.label if export_format else export_type
        self._status_text = f"No se pudo generar {export_label}"
        self.stateChanged.emit()
        error_details = details
        if file_name:
            error_details = f"{details}\nArchivo: {file_name}"
        LOGGER.error("Failed to export %s: %s", export_type, error_details)
        self.errorRaised.emit(f"No se pudo exportar {export_label}", error_details)

    def _open_export_file(self, path: Path | None):
        if path is None:
            self.errorRaised.emit(
                "Archivo no disponible",
                "Completa una descarga antes de abrir el archivo.",
            )
            return
        if not path.is_file():
            LOGGER.error("Exported file is missing: %s", path)
            self.errorRaised.emit(
                "Archivo no encontrado",
                f"No se encontró el archivo exportado:\n{path}",
            )
            return
        if not QDesktopServices.openUrl(QUrl.fromLocalFile(str(path))):
            LOGGER.error("Could not open exported file: %s", path)
            self.errorRaised.emit(
                "No se pudo abrir el archivo",
                f"Windows no pudo abrir {path.name}. Comprueba que haya "
                "una aplicación asociada.",
            )

    @Slot()
    def shutdown(self):
        self._export_runner.shutdown()
        self._school_search_workflow.shutdown()
        self._location_service.shutdown()
