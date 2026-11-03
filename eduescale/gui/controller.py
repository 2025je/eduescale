from pathlib import Path

from PySide6.QtCore import QObject, Property, QUrl, Signal, Slot
from PySide6.QtGui import QDesktopServices

from eduescale.client import SchoolFilters
from eduescale.gui.location_service import LocationService
from eduescale.gui.scrape_runner import ScrapeRunner


LOCATION_PLACEHOLDERS = {
    "regions": "Todos los departamentos",
    "provinces": "Todas las provincias",
    "districts": "Todos los distritos",
}


class SchoolFinderController(QObject):
    regionsChanged = Signal()
    provincesChanged = Signal()
    districtsChanged = Signal()
    stateChanged = Signal()
    errorRaised = Signal(str, str)

    def __init__(
        self,
        location_service: LocationService | None = None,
        scrape_runner: ScrapeRunner | None = None,
        parent: QObject | None = None,
    ):
        super().__init__(parent)
        self._regions = [{"label": "Cargando departamentos...", "code": ""}]
        self._provinces = [
            {"label": LOCATION_PLACEHOLDERS["provinces"], "code": ""}
        ]
        self._districts = [
            {"label": LOCATION_PLACEHOLDERS["districts"], "code": ""}
        ]
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
        self._open_folder_visible = False
        self._output_path: Path | None = None

        self._location_service = (
            location_service
            if location_service is not None
            else LocationService(parent=self)
        )
        self._scrape_runner = (
            scrape_runner if scrape_runner is not None else ScrapeRunner(parent=self)
        )
        self._location_service.loaded.connect(self._locations_loaded)
        self._location_service.failed.connect(self._locations_failed)
        self._scrape_runner.started.connect(self._scrape_started)
        self._scrape_runner.progressChanged.connect(self._scrape_progress)
        self._scrape_runner.completed.connect(self._scrape_completed)
        self._scrape_runner.failed.connect(self._scrape_failed)
        self._location_service.load("regions")

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
    def openFolderVisible(self):
        return self._open_folder_visible

    @Property(str, notify=stateChanged)
    def defaultOutputUrl(self):
        return QUrl.fromLocalFile(
            str(Path.home() / "colegios_escale.xlsx")
        ).toString()

    @staticmethod
    def _location_options(placeholder: str, locations):
        return [{"label": placeholder, "code": ""}] + [
            {"label": name, "code": code} for code, name in locations
        ]

    @Slot(str)
    def selectRegion(self, region_code: str):
        self._selected_region_code = region_code
        self._selected_province_code = ""
        self._provinces = self._location_options(
            "Cargando provincias..."
            if region_code
            else LOCATION_PLACEHOLDERS["provinces"],
            [],
        )
        self._districts = self._location_options(
            LOCATION_PLACEHOLDERS["districts"], []
        )
        self._provinces_enabled = bool(region_code)
        self._districts_enabled = False
        self.provincesChanged.emit()
        self.districtsChanged.emit()
        self.stateChanged.emit()
        if region_code:
            self._location_service.load("provinces", region_code)

    @Slot(str)
    def selectProvince(self, province_code: str):
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

    @Slot(str, str, str, str, str, str)
    def startScrape(
        self,
        output_url: str,
        region_code: str,
        province_code: str,
        district_code: str,
        management: str,
        level: str,
    ):
        if self._scrape_running:
            return

        selected_url = QUrl(output_url)
        output_path = (
            selected_url.toLocalFile()
            if selected_url.isLocalFile()
            else selected_url.toString()
        )
        if not output_path:
            self.errorRaised.emit(
                "Ruta no válida", "Selecciona un archivo Excel válido."
            )
            return

        self._output_path = Path(output_path)
        if self._output_path.suffix.lower() != ".xlsx":
            self._output_path = self._output_path.with_suffix(".xlsx")
        self._open_folder_visible = False
        self._scrape_running = True
        self._progress_visible = True
        self._progress_value = 0
        self._progress_maximum = 0
        self._progress_indeterminate = True
        self._progress_message = "Conectando con ESCALE..."
        self._status_text = "Consultando el total de registros en ESCALE..."
        self.stateChanged.emit()

        filters = SchoolFilters(
            region_code=region_code,
            province_code=province_code,
            district_code=district_code,
            management=management,
            level=level,
        )
        self._scrape_runner.start(self._output_path, filters)

    @Slot()
    def _scrape_started(self):
        self._scrape_running = True
        self.stateChanged.emit()

    @Slot(int, int, int, str)
    def _scrape_progress(self, received, total, pages, message):
        self._progress_value = received if total else 1
        self._progress_maximum = max(total, received, 1)
        self._progress_indeterminate = False
        self._progress_message = message
        self._status_text = message
        self.stateChanged.emit()

    @Slot(str, int, int, int)
    def _scrape_completed(self, output_path, total, records, pages):
        self._scrape_running = False
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

    @Slot(str, str)
    def _scrape_failed(self, title, details):
        self._scrape_running = False
        self._progress_visible = False
        self._status_text = title
        self.stateChanged.emit()
        self.errorRaised.emit(title, details)

    @Slot()
    def openOutputFolder(self):
        if self._output_path:
            QDesktopServices.openUrl(
                QUrl.fromLocalFile(str(self._output_path.parent))
            )

    @Slot()
    def shutdown(self):
        self._scrape_runner.shutdown()
        self._location_service.shutdown()
