import re
import sys
from pathlib import Path
from xml.etree import ElementTree

import requests
from PySide6.QtCore import (
    QObject,
    Property,
    QProcess,
    QThread,
    QTimer,
    QUrl,
    Signal,
    Slot,
)
from PySide6.QtGui import QDesktopServices, QGuiApplication
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuickControls2 import QQuickStyle

from eduescale.client import (
    SchoolFilters,
    fetch_districts,
    fetch_provinces,
    fetch_regions,
)


PROJECT_ROOT = Path(__file__).resolve().parent
LOCATION_PLACEHOLDERS = {
    "regions": "Todos los departamentos",
    "provinces": "Todas las provincias",
    "districts": "Todos los distritos",
}
SCRAPE_TOTAL_PATTERN = re.compile(r"SCRAPE_TOTAL=(\d+)")
SCRAPE_PROGRESS_PATTERN = re.compile(r"SCRAPE_PROGRESS=(\d+)/(\d+)\s+PAGES=(\d+)")


class LocationLoader(QObject):
    loaded = Signal(str, str, object)
    failed = Signal(str, str, str)

    def __init__(self, kind: str, parent_code: str):
        super().__init__()
        self.kind = kind
        self.parent_code = parent_code

    @Slot()
    def run(self):
        try:
            if self.kind == "regions":
                locations = fetch_regions()
            elif self.kind == "provinces":
                locations = fetch_provinces(self.parent_code)
            elif self.kind == "districts":
                locations = fetch_districts(self.parent_code)
            else:
                raise ValueError(f"Tipo de ubicación desconocido: {self.kind}")
        except (requests.RequestException, ElementTree.ParseError, ValueError) as error:
            self.failed.emit(self.kind, self.parent_code, str(error))
        else:
            self.loaded.emit(self.kind, self.parent_code, locations)
        finally:
            QThread.currentThread().quit()


class SchoolFinderController(QObject):
    regionsChanged = Signal()
    provincesChanged = Signal()
    districtsChanged = Signal()
    stateChanged = Signal()
    errorRaised = Signal(str, str)

    def __init__(self):
        super().__init__()
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
        self._status_text = "Cargando departamentos desde ESCALE..."
        self._progress_visible = False
        self._progress_value = 0
        self._progress_maximum = 0
        self._progress_message = ""
        self._scrape_running = False
        self._open_folder_visible = False
        self.location_threads = {}
        self.output_path = None
        self.process_log = ""
        self.progress_buffers = {"stdout": "", "stderr": ""}
        self.scrape_total = None
        self.records_received = 0
        self.pages_received = 0
        self.scrape_timed_out = False

        self.process = QProcess(self)
        self.process.readyReadStandardError.connect(self._read_process_output)
        self.process.readyReadStandardOutput.connect(self._read_process_output)
        self.process.finished.connect(self._scrape_finished)
        self.process.errorOccurred.connect(self._process_error)

        self.timeout_timer = QTimer(self)
        self.timeout_timer.setSingleShot(True)
        self.timeout_timer.timeout.connect(self._stop_timed_out_scrape)
        self._load_regions()

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
        return self._progress_maximum == 0

    @Property(str, notify=stateChanged)
    def progressMessage(self):
        return self._progress_message

    @Property(bool, notify=stateChanged)
    def scrapeRunning(self):
        return self._scrape_running

    @Property(bool, notify=stateChanged)
    def openFolderVisible(self):
        return self._open_folder_visible

    @staticmethod
    def _location_options(placeholder, locations):
        return [{"label": placeholder, "code": ""}] + [
            {"label": name, "code": code} for code, name in locations
        ]

    def _start_location_load(self, kind, parent_code=""):
        thread = QThread(self)
        worker = LocationLoader(kind, parent_code)
        worker.moveToThread(thread)
        thread.started.connect(worker.run)
        worker.loaded.connect(self._locations_loaded)
        worker.failed.connect(self._locations_failed)
        thread.finished.connect(worker.deleteLater)
        thread.finished.connect(self._location_thread_finished)
        thread.finished.connect(thread.deleteLater)
        self.location_threads[thread] = worker
        thread.start()

    def _load_regions(self):
        self._regions_enabled = False
        self._regions = [{"label": "Cargando departamentos...", "code": ""}]
        self.regionsChanged.emit()
        self.stateChanged.emit()
        self._start_location_load("regions")

    @Slot(str)
    def selectRegion(self, region_code):
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
            self._start_location_load("provinces", region_code)

    @Slot(str)
    def selectProvince(self, province_code):
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
            self._start_location_load("districts", province_code)

    @Slot(str, str, object)
    def _locations_loaded(self, kind, parent_code, locations):
        if kind == "regions":
            if parent_code:
                return
            self._regions = self._location_options(
                LOCATION_PLACEHOLDERS["regions"], locations
            )
            self._regions_enabled = True
            self.regionsChanged.emit()
        elif kind == "provinces":
            if not self._regions or self._regions[0].get("code") != "":
                return
            if not any(item["code"] == parent_code for item in self._regions):
                return
            self._provinces = self._location_options(
                LOCATION_PLACEHOLDERS["provinces"], locations
            )
            self.provincesChanged.emit()
        elif kind == "districts":
            if not any(item["code"] == parent_code for item in self._provinces):
                return
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
        if kind == "regions":
            self._regions = [{"label": "No se pudo cargar", "code": ""}]
            self._regions_enabled = False
            self.regionsChanged.emit()
        elif kind == "provinces":
            if not any(item["code"] == parent_code for item in self._regions):
                return
            self._provinces = [{"label": "No se pudo cargar", "code": ""}]
            self.provincesChanged.emit()
        elif kind == "districts":
            if not any(item["code"] == parent_code for item in self._provinces):
                return
            self._districts = [{"label": "No se pudo cargar", "code": ""}]
            self.districtsChanged.emit()
        else:
            return

        self._status_text = f"No se pudo cargar la ubicación desde ESCALE: {message}"
        self.stateChanged.emit()

    @Slot()
    def _location_thread_finished(self):
        thread = self.sender()
        if isinstance(thread, QThread):
            self.location_threads.pop(thread, None)

    @Slot(str, str, str, str, str, str)
    def startScrape(
        self, output_url, region_code, province_code, district_code, management, level
    ):
        output_url = QUrl(output_url)
        output_path = (
            output_url.toLocalFile()
            if output_url.isLocalFile()
            else output_url.toString()
        )
        if not output_path:
            self.errorRaised.emit("Ruta no válida", "Selecciona un archivo Excel válido.")
            return

        self.output_path = Path(output_path)
        if self.output_path.suffix.lower() != ".xlsx":
            self.output_path = self.output_path.with_suffix(".xlsx")
        try:
            self.output_path.unlink(missing_ok=True)
        except OSError as error:
            self.errorRaised.emit("No se puede guardar el Excel", str(error))
            return

        self.process_log = ""
        self.progress_buffers = {"stdout": "", "stderr": ""}
        self.scrape_total = None
        self.records_received = 0
        self.pages_received = 0
        self.scrape_timed_out = False
        self._open_folder_visible = False
        self._scrape_running = True
        self._progress_visible = True
        self._progress_value = 0
        self._progress_maximum = 0
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
        arguments = [
            "-m",
            "scrapy",
            "crawl",
            "colegios",
            "-a",
            f"output={self.output_path}",
            "-a",
            f"region_code={filters.region_code}",
            "-a",
            f"province_code={filters.province_code}",
            "-a",
            f"district_code={filters.district_code}",
            "-a",
            f"management={filters.management}",
            "-a",
            f"level={filters.level}",
            "-s",
            "LOG_LEVEL=INFO",
            "-s",
            "LOGSTATS_INTERVAL=1",
        ]
        self.process.setWorkingDirectory(str(PROJECT_ROOT))
        self.process.start(sys.executable, arguments)
        self.timeout_timer.start(15 * 60 * 1000)

    @Slot()
    def _read_process_output(self):
        streams = (
            ("stdout", bytes(self.process.readAllStandardOutput())),
            ("stderr", bytes(self.process.readAllStandardError())),
        )
        for stream_name, chunk in streams:
            text = chunk.decode(errors="replace")
            self.process_log += text
            buffered_text = self.progress_buffers[stream_name] + text
            lines = buffered_text.split("\n")
            self.progress_buffers[stream_name] = lines.pop()
            for line in lines:
                self._update_scrape_progress(line)

    def _update_scrape_progress(self, line):
        total_match = SCRAPE_TOTAL_PATTERN.search(line)
        if total_match:
            total = int(total_match.group(1))
            self._show_scrape_progress(0, total, 0)
            return

        progress_match = SCRAPE_PROGRESS_PATTERN.search(line)
        if progress_match:
            received, total, pages = map(int, progress_match.groups())
            self._show_scrape_progress(received, total, pages)

    def _show_scrape_progress(self, received, total, pages):
        self.records_received = received
        self.scrape_total = total
        self.pages_received = pages
        if total:
            self._progress_maximum = max(total, received)
            self._progress_value = received
            self._progress_message = (
                f"{received:,} de {total:,} registros · {pages} páginas"
            )
        else:
            self._progress_maximum = 1
            self._progress_value = 1
            self._progress_message = "Sin registros"
        self._progress_visible = True
        self._status_text = self._progress_message
        self.stateChanged.emit()

    def _finish_scrape(self, show_progress=False):
        self._scrape_running = False
        self._progress_visible = show_progress
        self.stateChanged.emit()

    @Slot(QProcess.ProcessError)
    def _process_error(self, error):
        if error == QProcess.ProcessError.FailedToStart:
            self.timeout_timer.stop()
            self._finish_scrape()
            self.errorRaised.emit(
                "No se pudo iniciar Scrapy",
                "Verifica que las dependencias estén instaladas con «uv sync».",
            )

    @Slot(int, QProcess.ExitStatus)
    def _scrape_finished(self, exit_code, _exit_status):
        self.timeout_timer.stop()
        self._read_process_output()
        for remaining_text in self.progress_buffers.values():
            if remaining_text:
                self._update_scrape_progress(remaining_text)

        if self.scrape_timed_out:
            self._finish_scrape()
            self._status_text = "Extracción detenida."
            self.stateChanged.emit()
            self.errorRaised.emit(
                "Extracción detenida",
                "La extracción superó el límite de 15 minutos. "
                "Prueba con una ubicación más específica.",
            )
        elif exit_code != 0:
            self._finish_scrape()
            details = self.process_log.strip() or "Scrapy terminó con un error."
            self.errorRaised.emit("Error de extracción", details)
        elif self.output_path and self.output_path.is_file():
            self._finish_scrape(show_progress=True)
            self._progress_maximum = max(
                self.scrape_total or self.records_received, 1
            )
            self._progress_value = self._progress_maximum
            self._progress_message = "Completado"
            self._status_text = (
                f"Excel creado: {self.output_path} · "
                f"{self.records_received:,} registros en "
                f"{self.pages_received} páginas."
            )
            self._open_folder_visible = True
            self.stateChanged.emit()
        else:
            self._finish_scrape()
            details = self.process_log.strip()
            if not details:
                details = (
                    "Scrapy terminó sin crear el archivo esperado.\n"
                    f"Archivo solicitado: {self.output_path}\n"
                    f"Código de salida: {exit_code}"
                )
            self.errorRaised.emit("No se generó el Excel", details)

    @Slot()
    def _stop_timed_out_scrape(self):
        if self.process.state() != QProcess.ProcessState.NotRunning:
            self.scrape_timed_out = True
            self.process.kill()

    @Slot()
    def openOutputFolder(self):
        if self.output_path:
            QDesktopServices.openUrl(
                QUrl.fromLocalFile(str(self.output_path.parent))
            )

    @Slot()
    def shutdown(self):
        self.timeout_timer.stop()
        if self.process.state() != QProcess.ProcessState.NotRunning:
            self.process.kill()
            self.process.waitForFinished(3000)
        for thread in list(self.location_threads):
            thread.quit()
            thread.wait()


def main():
    QQuickStyle.setStyle("Material")
    application = QGuiApplication(sys.argv)
    application.setApplicationName("Buscador de colegios - ESCALE")
    engine = QQmlApplicationEngine()
    controller = SchoolFinderController()
    engine.rootContext().setContextProperty("controller", controller)
    engine.load(QUrl.fromLocalFile(str(PROJECT_ROOT / "qml" / "Main.qml")))
    if not engine.rootObjects():
        return 1
    return application.exec()


if __name__ == "__main__":
    raise SystemExit(main())
