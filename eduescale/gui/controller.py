import logging
from pathlib import Path

from pydantic import TypeAdapter, ValidationError
from PySide6.QtCore import Property, QObject, QSettings, QUrl, Signal, Slot
from PySide6.QtGui import QDesktopServices

from eduescale.client import SchoolFilters
from eduescale.gui.ports import LocationServicePort, ScrapeRunnerPort

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
    stateChanged = Signal()
    errorRaised = Signal(str, str)

    def __init__(
        self,
        location_service: LocationServicePort,
        scrape_runner: ScrapeRunnerPort,
        parent: QObject | None = None,
        settings: QSettings | None = None,
    ):
        super().__init__(parent)
        self._settings = settings if settings is not None else QSettings()
        self._regions = [{"label": "Cargando departamentos...", "code": ""}]
        self._provinces = [{"label": LOCATION_PLACEHOLDERS["provinces"], "code": ""}]
        self._districts = [{"label": LOCATION_PLACEHOLDERS["districts"], "code": ""}]
        self._regions_enabled = False
        self._provinces_enabled = False
        self._districts_enabled = False
        self._selected_region_code = ""
        self._selected_province_code = ""
        self._status_text = "Cargando departamentos desde ESCALE..."
        self._progress_visible = False
        self._progress_value = 0
        self._progress_maximum = 0
        self._progress_message = ""
        self._progress_indeterminate = True
        self._scrape_running = False
        self._cancel_requested = False
        self._open_folder_visible = False
        self._output_path: Path | None = None

        self._location_service = location_service
        self._scrape_runner = scrape_runner
        for dependency in (location_service, scrape_runner):
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
        self._location_service.load("regions")
        LOGGER.info("Requested ESCALE departments.")

    @Property("QVariantList", notify=regionsChanged)
    def regions(self):
        return self._regions

    @Property("QVariantList", notify=provincesChanged)
    def provinces(self):
        return self._provinces

    @Property("QVariantList", notify=districtsChanged)
    def districts(self):
        return self._districts

    @Property(bool, notify=stateChanged)
    def regionsEnabled(self):
        return self._regions_enabled

    @Property(bool, notify=stateChanged)
    def provincesEnabled(self):
        return self._provinces_enabled

    @Property(bool, notify=stateChanged)
    def districtsEnabled(self):
        return self._districts_enabled

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
    def openFolderVisible(self):
        return self._open_folder_visible

    @Property(str, notify=stateChanged)
    def defaultOutputUrl(self):
        directory = self._settings.value("export/lastDirectory", str(Path.home()))
        try:
            output_directory = Path(directory)
        except (TypeError, ValueError):
            output_directory = Path.home()
        if not output_directory.is_dir():
            output_directory = Path.home()
        return QUrl.fromLocalFile(
            str(output_directory / "colegios_escale.xlsx")
        ).toString()

    @Property(int, notify=stateChanged)
    def windowWidth(self):
        return self._window_dimension("window/width", 820, 560)

    @Property(int, notify=stateChanged)
    def windowHeight(self):
        return self._window_dimension("window/height", 860, 600)

    def _window_dimension(self, key: str, default: int, minimum: int) -> int:
        try:
            value = int(self._settings.value(key, default))
        except (TypeError, ValueError):
            return default
        return max(value, minimum)

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
        self._open_folder_visible = False
        self._scrape_running = True
        self._progress_visible = True
        self._progress_value = 0
        self._progress_maximum = 0
        self._progress_indeterminate = True
        self._progress_message = "Conectando con ESCALE..."
        self._status_text = "Consultando el total de registros en ESCALE..."
        self.stateChanged.emit()

        LOGGER.info(
            "Starting extraction to %s with filters %s.",
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
    def _scrape_completed(self, output_path, total, records, pages):
        LOGGER.info(
            "Extraction completed: %d unique records across %d pages; output=%s",
            records,
            pages,
            output_path,
        )
        self._scrape_running = False
        self._cancel_requested = False
        self._progress_visible = True
        self._progress_indeterminate = False
        self._progress_maximum = max(total, records, 1)
        self._progress_value = self._progress_maximum
        self._progress_message = "Completado"
        self._output_path = Path(output_path)
        self._status_text = (
            f"Excel creado: {self._output_path} · "
            f"{records:,} registros en {pages} páginas."
        )
        self._open_folder_visible = True
        self.stateChanged.emit()

    @Slot()
    def _scrape_cancelled(self):
        LOGGER.info("Extraction cancelled; previous output was preserved.")
        self._scrape_running = False
        self._cancel_requested = False
        self._progress_visible = False
        self._progress_indeterminate = False
        self._progress_message = ""
        self._status_text = "Extracción cancelada. El archivo anterior se conservó."
        self.stateChanged.emit()

    @Slot(str, str)
    def _scrape_failed(self, title, details):
        LOGGER.error("Extraction failed (%s): %s", title, details)
        self._scrape_running = False
        self._cancel_requested = False
        self._progress_visible = False
        self._status_text = title
        self.stateChanged.emit()
        self.errorRaised.emit(title, details)

    @Slot(str, str)
    def _scrape_warning(self, title, details):
        LOGGER.warning("%s: %s", title, details)
        self.errorRaised.emit(title, details)

    @Slot()
    def openOutputFolder(self):
        if self._output_path:
            QDesktopServices.openUrl(QUrl.fromLocalFile(str(self._output_path.parent)))

    @Slot()
    def shutdown(self):
        self._scrape_runner.shutdown()
        self._location_service.shutdown()
