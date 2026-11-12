import json
import logging
import unicodedata
from datetime import datetime
from pathlib import Path

from pydantic import TypeAdapter, ValidationError
from PySide6.QtCore import (
    Property,
    QIODevice,
    QObject,
    QSaveFile,
    QSettings,
    Qt,
    QUrl,
    Signal,
    Slot,
)
from PySide6.QtGui import QDesktopServices, QGuiApplication

from eduescale.client import SchoolFilters
from eduescale.export_files import android_data_path
from eduescale.gui.export_runner import ExportRunner
from eduescale.gui.ports import ExportRunnerPort, LocationServicePort, ScrapeRunnerPort
from eduescale.gui.resources import read_resource_bytes, resource_path
from eduescale.gui.saved_searches import PortableSavedSearches

LOGGER = logging.getLogger(__name__)
MAX_SAVED_SEARCH_FILE_BYTES = 1_048_576

LOCATION_PLACEHOLDERS = {
    "regions": "Todos los departamentos",
    "provinces": "Todas las provincias",
    "districts": "Todos los distritos",
}
FILTER_SETS_ADAPTER = TypeAdapter(list[SchoolFilters])


class SchoolFinderController(QObject):
    regionsChanged = Signal()
    provincesChanged = Signal()
    districtsChanged = Signal()
    darkThemeChanged = Signal()
    themeModeChanged = Signal()
    savedSearchesLoaded = Signal(str)
    exportHistoryChanged = Signal()
    stateChanged = Signal()
    errorRaised = Signal(str, str)

    def __init__(
        self,
        location_service: LocationServicePort,
        scrape_runner: ScrapeRunnerPort,
        parent: QObject | None = None,
        settings: QSettings | None = None,
        export_runner: ExportRunnerPort | None = None,
    ):
        super().__init__(parent)
        self._settings = settings if settings is not None else QSettings()
        self._theme_mode = self._read_theme_mode()
        self._dark_theme = self._effective_dark_theme()
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
        map_path = resource_path("assets/districts_map.json")
        map_data = json.loads(read_resource_bytes("assets/districts_map.json"))
        features = map_data.get("features")
        if map_data.get("type") != "FeatureCollection" or not isinstance(
            features, list
        ):
            raise ValueError(f"Invalid district map data in {map_path}")

        self._district_metadata: dict[str, dict[str, str]] = {}
        for feature in features:
            properties = feature["properties"]
            code = properties["ubigeo"]
            department_code = code[:2]
            province_code = code[:4]
            if code in self._district_metadata:
                raise ValueError(f"Duplicate district code {code} in {map_path}")
            self._district_metadata[code] = {
                "department": properties["department"],
                "departmentCode": department_code,
                "province": properties["province"],
                "provinceCode": province_code,
                "district": properties["district"],
                "boundary_quality": properties["boundary_quality"],
            }
        if len(self._district_metadata) != 1892:
            raise ValueError(f"Incomplete district map data in {map_path}")
        self._district_codes = sorted(self._district_metadata)
        self._district_codes_by_province: dict[str, list[str]] = {}
        for code in self._district_codes:
            self._district_codes_by_province.setdefault(code[:4], []).append(code)

        hierarchy_path = resource_path("assets/districts_map_hierarchy.json")
        hierarchy_data = json.loads(
            read_resource_bytes("assets/districts_map_hierarchy.json")
        )
        department_features = hierarchy_data["departments"]["features"]
        province_features = hierarchy_data["provinces"]["features"]
        if (
            hierarchy_data.get("metadata", {}).get("department_count") != 25
            or hierarchy_data.get("metadata", {}).get("province_count") != 196
            or len(department_features) != 25
            or len(province_features) != 196
        ):
            raise ValueError(f"Incomplete hierarchy map data in {hierarchy_path}")
        self._map_department_metadata = {
            feature["properties"]["code"]: {
                "name": feature["properties"]["name"],
            }
            for feature in department_features
        }
        self._map_province_metadata = {
            feature["properties"]["code"]: {
                "name": feature["properties"]["name"],
                "department": feature["properties"]["department"],
                "departmentCode": feature["properties"]["departmentCode"],
                "districtCount": len(
                    self._district_codes_by_province.get(
                        feature["properties"]["code"], []
                    )
                ),
            }
            for feature in province_features
        }
        if (
            len(self._map_department_metadata) != 25
            or len(self._map_province_metadata) != 196
            or any(
                province["departmentCode"] not in self._map_department_metadata
                for province in self._map_province_metadata.values()
            )
            or any(
                province["districtCount"] == 0
                for province in self._map_province_metadata.values()
            )
        ):
            raise ValueError(f"Invalid hierarchy map data in {hierarchy_path}")
        self._status_text = "Límites distritales cargados."
        self._progress_visible = False
        self._progress_value = 0
        self._progress_maximum = 0
        self._progress_message = ""
        self._progress_indeterminate = True
        self._scrape_running = False
        self._cancel_requested = False
        self._export_running = False
        self._exports_available = False
        self._output_path: Path | None = None
        self._dataset_path: Path | None = None
        self._last_search_summary = ""
        self._export_history, self._export_history_warning = self._read_export_history()

        self._location_service = location_service
        self._scrape_runner = scrape_runner
        self._export_runner = ExportRunner() if export_runner is None else export_runner
        for dependency in (location_service, scrape_runner, self._export_runner):
            if isinstance(dependency, QObject) and dependency.parent() is None:
                dependency.setParent(self)
        self._location_service.loaded.connect(self._locations_loaded)
        self._location_service.failed.connect(self._locations_failed)
        self._scrape_runner.started.connect(self._scrape_started)
        self._scrape_runner.progressChanged.connect(self._scrape_progress)
        self._scrape_runner.completed.connect(self._scrape_completed)
        self._scrape_runner.failed.connect(self._scrape_failed)
        self._scrape_runner.cancelled.connect(self._scrape_cancelled)
        self._scrape_runner.warning.connect(self._scrape_warning)
        self._export_runner.completed.connect(self._export_completed)
        self._export_runner.failed.connect(self._export_failed)
        LOGGER.info("Loaded local district boundary data.")
        self._location_service.load("regions")

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
        if district_codes:
            return list(dict.fromkeys(str(code) for code in district_codes))

        selected_provinces = {str(code) for code in province_codes}
        if not selected_provinces:
            selected_departments = {str(code) for code in department_codes}
            selected_provinces = {
                code
                for code, province in self._map_province_metadata.items()
                if province["departmentCode"] in selected_departments
            }
        return [
            district_code
            for province_code in sorted(selected_provinces)
            for district_code in self._district_codes_by_province.get(province_code, [])
        ]

    @Slot(str, result="QVariant")
    def mapDistrictProperties(self, code: str) -> dict[str, str] | None:
        properties = self._district_metadata.get(code)
        if properties is None:
            LOGGER.error("Requested metadata for unknown district code %s.", code)
        return properties

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

    def _read_export_history(self) -> tuple[list[dict[str, str]], str]:
        value = self._settings.value("export/history", "[]")
        if not isinstance(value, str):
            LOGGER.error("Saved export history has an unexpected value type.")
            return [], "No se pudo leer el historial guardado."
        try:
            history = json.loads(value)
        except json.JSONDecodeError as error:
            LOGGER.error("Saved export history is invalid JSON: %s", error)
            return [], "El historial guardado está dañado."
        if not isinstance(history, list):
            LOGGER.error("Saved export history is not a list.")
            return [], "El historial guardado tiene un formato no válido."
        entries = []
        keys = {"timestamp", "format", "file", "status", "filters"}
        for entry in history[:50]:
            if (
                not isinstance(entry, dict)
                or set(entry) != keys
                or any(not isinstance(entry[key], str) for key in keys)
            ):
                LOGGER.error("Ignoring malformed saved export history entry.")
                return [], "El historial guardado contiene una entrada no válida."
            entries.append(entry)
        return entries, ""

    def _record_export_history(
        self,
        export_type: str,
        file_name: str,
        status: str,
    ):
        export_labels = {
            "excel": "Excel",
            "pdf": "PDF",
            "eduescale": ".eduescale",
        }
        entry = {
            "timestamp": datetime.now().astimezone().isoformat(timespec="seconds"),
            "format": export_labels.get(export_type, export_type),
            "file": Path(file_name).name,
            "status": status,
            "filters": self._last_search_summary,
        }
        self._export_history = [entry, *self._export_history][:50]
        self._settings.setValue(
            "export/history",
            json.dumps(self._export_history, ensure_ascii=False),
        )
        self._settings.sync()
        if self._settings.status() != QSettings.Status.NoError:
            LOGGER.error("Could not save export history.")
            self._export_history_warning = "No se pudo guardar el historial."
            self.errorRaised.emit(
                "No se pudo guardar el historial",
                "La exportación terminó, pero su registro no se pudo guardar.",
            )
        self.exportHistoryChanged.emit()

    def _summarize_searches(self, filter_sets: list[SchoolFilters]) -> str:
        locations = []
        management: set[str] = set()
        levels: set[str] = set()
        for filters in filter_sets:
            if filters.district_code in self._district_metadata:
                details = self._district_metadata[filters.district_code]
                location = " / ".join(
                    (details["department"], details["province"], details["district"])
                )
            elif filters.province_code in self._map_province_metadata:
                province = self._map_province_metadata[filters.province_code]
                location = f"{province['department']} / {province['name']}"
            elif filters.region_code in self._map_department_metadata:
                location = self._map_department_metadata[filters.region_code]["name"]
            else:
                location = "Todo el Perú"
            if location not in locations:
                locations.append(location)
            management.update(filters.management)
            levels.update(filters.level)
        location_summary = ", ".join(locations[:5])
        if len(locations) > 5:
            location_summary += f" y {len(locations) - 5} más"
        return (
            f"{len(filter_sets)} combinaciones · {location_summary} · "
            f"Gestión: {', '.join(sorted(management))} · "
            f"Nivel: {', '.join(sorted(levels))}"
        )

    def _read_theme_mode(self) -> str:
        theme_mode = self._settings.value("appearance/themeMode")
        if theme_mode is None:
            if self._settings.contains("appearance/darkTheme"):
                return (
                    "dark"
                    if self._settings.value(
                        "appearance/darkTheme",
                        False,
                        type=bool,
                    )
                    else "light"
                )
            return "system"
        if theme_mode in ("system", "light", "dark"):
            return theme_mode
        LOGGER.error("Ignoring invalid saved theme mode %r.", theme_mode)
        return "system"

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
        self._settings.setValue("appearance/themeMode", theme_mode)
        self._settings.sync()
        self._theme_mode = theme_mode
        self._dark_theme = self._effective_dark_theme()
        self.themeModeChanged.emit()
        if self._dark_theme != old_dark_theme:
            self.darkThemeChanged.emit()
        if self._settings.status() != QSettings.Status.NoError:
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
        directory = self._settings.value("export/lastDirectory", str(Path.home()))
        if not isinstance(directory, str):
            output_directory = Path.home()
        else:
            output_directory = Path(directory)
        if not output_directory.is_dir():
            output_directory = Path.home()
        output_path = output_directory / "colegios_escale.xlsx"
        suffix = 1
        while (
            output_path.exists()
            or android_data_path(output_path).exists()
            or output_path.with_suffix(".pdf").exists()
        ):
            output_path = output_directory / f"colegios_escale_{suffix}.xlsx"
            suffix += 1
        return output_path

    @Property(int, notify=stateChanged)
    def windowWidth(self):
        return self._window_dimension("window/width", 1280, 560)

    @Property(int, notify=stateChanged)
    def windowHeight(self):
        return self._window_dimension("window/height", 760, 600)

    def _window_dimension(self, key: str, default: int, minimum: int) -> int:
        value = self._settings.value(key, default)
        if not isinstance(value, (str, int, float)):
            return default
        try:
            dimension = int(value)
        except (TypeError, ValueError):
            return default
        return max(dimension, minimum)

    @Slot(int, int)
    def saveWindowSize(self, width: int, height: int):
        self._settings.setValue("window/width", max(width, 560))
        self._settings.setValue("window/height", max(height, 600))
        self._settings.sync()
        if self._settings.status() != QSettings.Status.NoError:
            LOGGER.error("No se pudo guardar el tamaño de la ventana.")

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
            self._location_service.load("provinces", region_code)

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
            self._location_service.load("districts", province_code)

    @Slot(str, str, object)
    def _locations_loaded(self, kind, parent_code, locations):
        LOGGER.info(
            "Loaded %d %s for parent code %s.",
            len(locations),
            kind,
            parent_code or "(none)",
        )
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

        self._status_text = "Ubicaciones cargadas desde ESCALE."
        self.stateChanged.emit()

    @Slot(str, str, str)
    def _locations_failed(self, kind, parent_code, message):
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
    def saveSavedSearches(self, output_url: str, document_json: str) -> bool:
        output_path = self._local_path(output_url)
        if output_path is None:
            self.errorRaised.emit(
                "Ruta no válida",
                "Selecciona un archivo JSON local para compartir las búsquedas.",
            )
            return False
        try:
            document = PortableSavedSearches.model_validate_json(document_json)
            self._normalize_saved_searches(document.searches)
        except ValidationError as error:
            LOGGER.error("Refusing to export invalid saved searches: %s", error)
            self.errorRaised.emit("Búsquedas no válidas", str(error))
            return False
        except ValueError as error:
            LOGGER.error("Refusing to export unresolved saved searches: %s", error)
            self.errorRaised.emit("Ubicación no válida", str(error))
            return False

        payload = document.model_dump_json(indent=2).encode("utf-8")
        if len(payload) > MAX_SAVED_SEARCH_FILE_BYTES:
            self.errorRaised.emit(
                "Archivo demasiado grande",
                "El archivo de búsquedas supera el límite de 1 MiB.",
            )
            return False
        save_file = QSaveFile(str(output_path))
        if not save_file.open(QIODevice.OpenModeFlag.WriteOnly):
            LOGGER.error("Could not open saved-search file %s.", output_path)
            self.errorRaised.emit(
                "No se pudo guardar",
                f"No se pudo crear el archivo de búsquedas: {save_file.errorString()}",
            )
            return False
        if save_file.write(payload) != len(payload) or not save_file.commit():
            error_message = save_file.errorString()
            save_file.cancelWriting()
            LOGGER.error(
                "Could not commit saved-search file %s: %s",
                output_path,
                error_message,
            )
            self.errorRaised.emit(
                "No se pudo guardar",
                f"No se pudo completar el archivo de búsquedas: {error_message}",
            )
            return False
        LOGGER.info(
            "Exported %d saved searches to %s.",
            len(document.searches),
            output_path,
        )
        return True

    @Slot(str, result=bool)
    def loadSavedSearches(self, input_url: str) -> bool:
        input_path = self._local_path(input_url)
        if input_path is None:
            self.errorRaised.emit(
                "Ruta no válida",
                "Selecciona un archivo JSON local de búsquedas.",
            )
            return False
        try:
            if input_path.stat().st_size > MAX_SAVED_SEARCH_FILE_BYTES:
                raise ValueError("El archivo supera el límite de 1 MiB.")
            document = PortableSavedSearches.model_validate_json(
                input_path.read_bytes()
            )
            searches = self._normalize_saved_searches(document.searches)
        except (OSError, ValidationError, ValueError) as error:
            LOGGER.error("Could not load saved searches from %s: %s", input_path, error)
            self.errorRaised.emit("No se pudieron importar las búsquedas", str(error))
            return False
        self.savedSearchesLoaded.emit(
            json.dumps(searches, ensure_ascii=False, separators=(",", ":"))
        )
        LOGGER.info("Imported %d saved searches from %s.", len(searches), input_path)
        return True

    @staticmethod
    def _local_path(file_url: str) -> Path | None:
        url = QUrl(file_url)
        return Path(url.toLocalFile()) if url.isLocalFile() else None

    def _normalize_saved_searches(self, searches):
        normalized = []
        for index, search in enumerate(searches, start=1):
            location = self._resolve_saved_search_location(search)
            normalized.append(
                {
                    "name": search.name,
                    "regionCode": location["department_code"] or "",
                    "provinceCode": location["province_code"] or "",
                    "districtCode": location["district_code"] or "",
                    "regionLabel": location["department"],
                    "provinceLabel": location["province"],
                    "districtLabel": location["district"] or "Todos los distritos",
                    "management": ",".join(search.management or ("publica", "privada")),
                    "level": ",".join(
                        search.levels or ("primaria", "secundaria", "cetpro")
                    ),
                }
            )
        return normalized

    def _resolve_saved_search_location(self, search):
        requested = {
            "department": search.department,
            "province": search.province,
            "district": search.district,
        }
        codes = {
            "department_code": search.department_code,
            "province_code": search.province_code,
            "district_code": search.district_code,
        }
        if codes["district_code"]:
            properties = self._district_metadata.get(codes["district_code"])
            if properties is None:
                raise ValueError(
                    f"Ubigeo distrital no reconocido: {codes['district_code']}."
                )
            if (
                codes["province_code"]
                and codes["province_code"] != properties["provinceCode"]
            ):
                raise ValueError(
                    f"El ubigeo provincial {codes['province_code']} no corresponde "
                    f"al distrito {codes['district_code']}."
                )
            if (
                codes["department_code"]
                and codes["department_code"] != properties["departmentCode"]
            ):
                raise ValueError(
                    f"El ubigeo departamental {codes['department_code']} no "
                    f"corresponde al distrito {codes['district_code']}."
                )
            codes["province_code"] = (
                codes["province_code"] or properties["provinceCode"]
            )
            codes["department_code"] = (
                codes["department_code"] or properties["departmentCode"]
            )
        if codes["province_code"]:
            province = self._map_province_metadata.get(codes["province_code"])
            if province is None:
                raise ValueError(
                    f"Ubigeo provincial no reconocido: {codes['province_code']}."
                )
            if (
                codes["department_code"]
                and codes["department_code"] != province["departmentCode"]
            ):
                raise ValueError(
                    f"El ubigeo departamental {codes['department_code']} no "
                    f"corresponde a la provincia {codes['province_code']}."
                )
            codes["department_code"] = (
                codes["department_code"] or province["departmentCode"]
            )
        if codes["department_code"] and (
            codes["department_code"] not in self._map_department_metadata
        ):
            raise ValueError(
                f"Ubigeo departamental no reconocido: {codes['department_code']}."
            )

        if not any(codes.values()) and any(requested.values()):

            def matches(actual: str, expected: str | None) -> bool:
                return expected is None or self._normalized_label(actual) == (
                    self._normalized_label(expected)
                )

            if requested["district"]:
                candidates = [
                    (code, properties)
                    for code, properties in self._district_metadata.items()
                    if matches(properties["district"], requested["district"])
                    and matches(properties["province"], requested["province"])
                    and matches(properties["department"], requested["department"])
                ]
                if len(candidates) != 1:
                    raise ValueError(
                        f"No se pudo resolver de forma única el distrito "
                        f"«{requested['district']}»."
                    )
                district_code, properties = candidates[0]
                codes["district_code"] = district_code
                codes["province_code"] = properties["provinceCode"]
                codes["department_code"] = properties["departmentCode"]
            elif requested["province"]:
                candidates = [
                    (code, properties)
                    for code, properties in self._map_province_metadata.items()
                    if matches(properties["name"], requested["province"])
                    and matches(properties["department"], requested["department"])
                ]
                if len(candidates) != 1:
                    raise ValueError(
                        f"No se pudo resolver de forma única la provincia "
                        f"«{requested['province']}»."
                    )
                province_code, properties = candidates[0]
                codes["province_code"] = province_code
                codes["department_code"] = properties["departmentCode"]
            elif requested["department"]:
                candidates = [
                    code
                    for code, properties in self._map_department_metadata.items()
                    if matches(properties["name"], requested["department"])
                ]
                if len(candidates) != 1:
                    raise ValueError(
                        f"No se pudo resolver de forma única el departamento "
                        f"«{requested['department']}»."
                    )
                codes["department_code"] = candidates[0]

        department = self._map_department_metadata.get(codes["department_code"], {})
        province = self._map_province_metadata.get(codes["province_code"], {})
        district = self._district_metadata.get(codes["district_code"], {})
        resolved = {
            "department": department.get("name", requested["department"]),
            "province": province.get("name", requested["province"]),
            "district": district.get("district", requested["district"]),
        }
        for key, value in requested.items():
            if (
                value
                and resolved[key]
                and (
                    self._normalized_label(value)
                    != self._normalized_label(resolved[key])
                )
            ):
                raise ValueError(f"La etiqueta de ubicación «{value}» no coincide.")
        return {**resolved, **codes}

    @staticmethod
    def _normalized_label(value: str) -> str:
        return unicodedata.normalize("NFKC", value).strip().casefold()

    @Slot(str, str)
    def startScrape(self, output_url: str, filter_sets_json: str):
        if self._scrape_running:
            return

        try:
            filter_sets = FILTER_SETS_ADAPTER.validate_json(filter_sets_json)
        except ValidationError as error:
            self.errorRaised.emit(
                "Filtros no válidos",
                str(error),
            )
            return

        if not filter_sets:
            self.errorRaised.emit(
                "Filtros requeridos",
                "Agrega al menos una combinación de filtros.",
            )
            return

        invalid_filter_sets = []
        for index, filters in enumerate(filter_sets, start=1):
            missing = []
            if not filters.management:
                missing.append("Gestión")
            if not filters.level:
                missing.append("Nivel educativo")
            if missing:
                invalid_filter_sets.append(f"Filtro {index}: {', '.join(missing)}")
        if invalid_filter_sets:
            self.errorRaised.emit(
                "Selección requerida",
                "Cada combinación debe incluir al menos una opción en Gestión "
                "y Nivel educativo. " + "; ".join(invalid_filter_sets),
            )
            return

        self._last_search_summary = self._summarize_searches(filter_sets)
        selected_url = QUrl(output_url)
        if selected_url.isLocalFile():
            output_path = selected_url.toLocalFile()
        elif selected_url.scheme():
            self.errorRaised.emit(
                "Ruta no válida",
                "Selecciona un archivo local para guardar el Excel.",
            )
            return
        else:
            output_path = selected_url.toString()
        if not output_path:
            self.errorRaised.emit(
                "Ruta no válida", "Selecciona un archivo Excel válido."
            )
            return

        self._output_path = Path(output_path)
        if self._output_path.suffix.lower() != ".xlsx":
            self._output_path = self._output_path.with_suffix(".xlsx")
        self._settings.setValue("export/lastDirectory", str(self._output_path.parent))
        self._settings.sync()
        if self._settings.status() != QSettings.Status.NoError:
            LOGGER.error("No se pudo guardar la carpeta de exportación.")
        self._cancel_requested = False
        self._dataset_path = None
        self._exports_available = False
        self._scrape_running = True
        self._progress_visible = True
        self._progress_value = 0
        self._progress_maximum = 0
        self._progress_indeterminate = True
        self._progress_message = "Conectando con ESCALE..."
        self._status_text = "Consultando el total de registros en ESCALE..."
        self.stateChanged.emit()

        LOGGER.info(
            "Starting extraction for %s with filters %s.",
            self._output_path,
            [filters.model_dump(mode="json") for filters in filter_sets],
        )
        self._scrape_runner.start(self._output_path, filter_sets)

    @Slot()
    def cancelScrape(self):
        if self._scrape_running and not self._cancel_requested:
            LOGGER.info("Cancellation requested by the user.")
            self._cancel_requested = True
            self.stateChanged.emit()
            self._scrape_runner.cancel()

    @Slot()
    def _scrape_started(self):
        self._scrape_running = True
        self.stateChanged.emit()

    @Slot(int, int, int, str)
    def _scrape_progress(self, received, total, pages, message):
        LOGGER.debug(
            "Scrape progress: %d/%d results received across %d pages.",
            received,
            total,
            pages,
        )
        self._progress_value = received if total else 1
        self._progress_maximum = max(total, received, 1)
        self._progress_indeterminate = False
        self._progress_message = message
        self._status_text = message
        self.stateChanged.emit()

    @Slot(str, int, int, int)
    def _scrape_completed(self, database_path, total, records, pages):
        LOGGER.info(
            "Extraction completed: %d unique records across %d pages; data=%s",
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
        self._status_text = (
            f"Datos listos para exportar · {records:,} colegios en {pages} páginas."
        )
        self._exports_available = True
        self.stateChanged.emit()

    @Slot()
    def _scrape_cancelled(self):
        LOGGER.info("Extraction cancelled; previous output was preserved.")
        self._scrape_running = False
        self._cancel_requested = False
        self._progress_visible = False
        self._progress_indeterminate = False
        self._progress_message = ""
        self._dataset_path = None
        self._exports_available = False
        self._status_text = (
            "Extracción cancelada. Los archivos anteriores se conservaron."
        )
        self.stateChanged.emit()

    @Slot(str, str)
    def _scrape_failed(self, title, details):
        LOGGER.error("Extraction failed (%s): %s", title, details)
        self._scrape_running = False
        self._dataset_path = None
        self._exports_available = False
        self._cancel_requested = False
        self._progress_visible = False
        self._status_text = title
        self.stateChanged.emit()
        self.errorRaised.emit(title, details)

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
        while (
            output_path.exists()
            or android_data_path(output_path).exists()
            or output_path.with_suffix(".pdf").exists()
        ):
            output_path = directory / f"colegios_escale_{timestamp}_{suffix}.xlsx"
            suffix += 1
        return output_path

    def _start_export(
        self,
        export_type: str,
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

        selected_url = QUrl(output_url)
        if not selected_url.isLocalFile():
            self.errorRaised.emit(
                "Ruta no válida",
                "Selecciona un archivo local para guardar la exportación.",
            )
            return
        output_path = Path(selected_url.toLocalFile())
        if not output_path.name:
            self.errorRaised.emit(
                "Ruta no válida",
                "Selecciona un nombre de archivo para la exportación.",
            )
            return
        expected_suffix = {
            "excel": ".xlsx",
            "eduescale": ".eduescale",
            "pdf": ".pdf",
        }[export_type]
        if output_path.suffix.lower() != expected_suffix:
            output_path = output_path.with_suffix(expected_suffix)
        self._output_path = output_path.with_suffix(".xlsx")
        self._settings.setValue("export/lastDirectory", str(output_path.parent))
        self._settings.sync()
        if self._settings.status() != QSettings.Status.NoError:
            LOGGER.error("No se pudo guardar la carpeta de exportación.")

        self._export_running = True
        self._progress_visible = True
        self._progress_indeterminate = True
        self._progress_message = {
            "excel": "Generando archivo Excel...",
            "eduescale": "Generando archivo .eduescale...",
            "pdf": "Generando archivo PDF...",
        }[export_type]
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
        export_label = {
            "excel": "Excel",
            "eduescale": ".eduescale",
            "pdf": "PDF",
        }.get(export_type, export_type)
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
        self._scrape_runner.shutdown()
        self._location_service.shutdown()
