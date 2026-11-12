import json
import logging
from datetime import datetime
from pathlib import Path

from pydantic import TypeAdapter, ValidationError
from PySide6.QtCore import Property, QObject, QSettings, QUrl, Signal, Slot
from PySide6.QtGui import QDesktopServices

from eduescale.client import SchoolFilters
from eduescale.export_files import android_data_path
from eduescale.gui.export_runner import ExportRunner
from eduescale.gui.ports import ExportRunnerPort, LocationServicePort, ScrapeRunnerPort
from eduescale.gui.resources import read_resource_bytes, resource_path

LOGGER = logging.getLogger(__name__)

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
        self._dark_theme = self._settings.value(
            "appearance/darkTheme",
            False,
            type=bool,
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

    @Property(bool, notify=darkThemeChanged)
    def darkTheme(self):
        return self._dark_theme

    @Slot(bool)
    def setDarkTheme(self, dark_theme: bool):
        if self._dark_theme == dark_theme:
            return
        self._settings.setValue("appearance/darkTheme", dark_theme)
        self._settings.sync()
        self._dark_theme = dark_theme
        self.darkThemeChanged.emit()
        if self._settings.status() != QSettings.Status.NoError:
            LOGGER.error("No se pudo guardar la preferencia del tema.")
            self.errorRaised.emit(
                "No se pudo guardar el tema",
                "La preferencia se aplicó, pero no se pudo guardar para la próxima vez.",
            )

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
        self._progress_visible = False
        self._progress_indeterminate = False
        self._progress_message = ""
        self._status_text = f"Archivo creado: {path.name}"
        self.stateChanged.emit()
        self._open_export_file(path)

    @Slot(str, str, str)
    def _export_failed(self, export_type: str, details: str, file_name: str):
        self._export_running = False
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
