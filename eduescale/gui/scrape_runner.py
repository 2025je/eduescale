import json
import logging
import re
import shutil
import sys
import tempfile
from pathlib import Path

from PySide6.QtCore import QObject, QProcess, QProcessEnvironment, QTimer, Signal, Slot

from eduescale.client import SchoolFilters

LOGGER = logging.getLogger(__name__)

SCRAPE_TOTAL_PATTERN = re.compile(r"SCRAPE_TOTAL=(\d+)")
SCRAPE_PROGRESS_PATTERN = re.compile(
    r"SCRAPE_PROGRESS=(\d+)/(\d+)\s+UNIQUE=(\d+)\s+PAGES=(\d+)\s+FAILED=(\d+)"
)
SCRAPE_TIMEOUT_MS = 15 * 60 * 1000
PROCESS_TERMINATE_GRACE_MS = 3000
MAX_PROCESS_LOG_CHARS = 64 * 1024
MAX_PROGRESS_LINE_CHARS = 8 * 1024


class ScrapeRunner(QObject):
    started = Signal()
    progressChanged = Signal(int, int, int, str)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)
    cancelled = Signal()
    warning = Signal(str, str)

    def __init__(self, parent: QObject | None = None):
        super().__init__(parent)
        self._process = QProcess(self)
        self._process.readyReadStandardError.connect(self._read_process_output)
        self._process.readyReadStandardOutput.connect(self._read_process_output)
        self._process.finished.connect(self._scrape_finished)
        self._process.errorOccurred.connect(self._process_error)
        self._timeout_timer = QTimer(self)
        self._timeout_timer.setSingleShot(True)
        self._timeout_timer.timeout.connect(self._stop_timed_out_scrape)
        self._termination_timer = QTimer(self)
        self._termination_timer.setSingleShot(True)
        self._termination_timer.timeout.connect(self._force_stop_process)
        self._working_directory = Path(__file__).resolve().parents[1]
        self._frozen = getattr(sys, "frozen", False)
        self._output_path: Path | None = None
        self._staging_directory: Path | None = None
        self._staging_path: Path | None = None
        self._process_log = ""
        self._progress_buffers = {"stdout": "", "stderr": ""}
        self._scrape_total: int | None = None
        self._records_received = 0
        self._pages_received = 0
        self._scrape_timed_out = False
        self._cancel_requested = False
        self._shutting_down = False
        self._running = False

    def start(self, output_path: Path, filter_sets: list[SchoolFilters]):
        if self._running or self._process.state() != QProcess.ProcessState.NotRunning:
            self.failed.emit("Extracción en curso", "Ya hay una extracción activa.")
            return

        cleanup_error = self._cleanup_staging()
        if cleanup_error:
            self.failed.emit(
                "No se pudieron limpiar archivos temporales",
                cleanup_error,
            )
            return

        try:
            output_path.parent.mkdir(parents=True, exist_ok=True)
            self._staging_directory = Path(
                tempfile.mkdtemp(prefix=".eduescale-", dir=output_path.parent)
            )
        except OSError as error:
            self.failed.emit(
                "No se puede preparar el archivo Excel",
                str(error),
            )
            return

        staging_path = self._staging_directory / "colegios.xlsx"
        self._staging_path = staging_path
        self._output_path = output_path
        self._process_log = ""
        self._progress_buffers = {"stdout": "", "stderr": ""}
        self._scrape_total = None
        self._records_received = 0
        self._pages_received = 0
        self._scrape_timed_out = False
        self._cancel_requested = False
        self._shutting_down = False
        scrapy_arguments = [
            "crawl",
            "colegios",
            "-a",
            f"output={staging_path}",
            "-a",
            "filters_json="
            + json.dumps(
                [filters.model_dump(mode="json") for filters in filter_sets],
                separators=(",", ":"),
            ),
            "-s",
            "LOG_LEVEL=INFO",
            "-s",
            "LOGSTATS_INTERVAL=1",
        ]
        environment = QProcessEnvironment.systemEnvironment()
        environment.insert("SCRAPY_SETTINGS_MODULE", "eduescale.scrapy_settings")
        self._process.setProcessEnvironment(environment)

        if self._frozen:
            worker_name = (
                "eduescale-scraper.exe"
                if sys.platform == "win32"
                else "eduescale-scraper"
            )
            worker_path = Path(sys.executable).resolve().with_name(worker_name)
            if not worker_path.is_file():
                self._fail(
                    "No se encontró el ejecutable de Scrapy",
                    f"Se esperaba encontrar el worker junto a la aplicación:\n{worker_path}",
                )
                return
            program = str(worker_path)
            arguments = scrapy_arguments
            working_directory = self._staging_directory
        else:
            program = sys.executable
            arguments = ["-m", "scrapy", *scrapy_arguments]
            working_directory = self._working_directory

        self._running = True
        self._process.setWorkingDirectory(str(working_directory))
        self._process.start(program, arguments)
        self._timeout_timer.start(SCRAPE_TIMEOUT_MS)
        self.started.emit()

    def cancel(self):
        if (
            not self._running
            or self._cancel_requested
            or self._process.state() == QProcess.ProcessState.NotRunning
        ):
            return
        self._cancel_requested = True
        self._timeout_timer.stop()
        self._request_process_stop()

    @Slot()
    def _read_process_output(self):
        streams = (
            ("stdout", bytes(self._process.readAllStandardOutput())),
            ("stderr", bytes(self._process.readAllStandardError())),
        )
        for stream_name, chunk in streams:
            text = chunk.decode(errors="replace")
            if text:
                self._process_log = (self._process_log + text)[-MAX_PROCESS_LOG_CHARS:]
            buffered_text = self._progress_buffers[stream_name] + text
            lines = buffered_text.split("\n")
            remainder = lines.pop()
            self._progress_buffers[stream_name] = remainder[-MAX_PROGRESS_LINE_CHARS:]
            for line in lines:
                self._update_progress(line)
                self._log_process_line(stream_name, line)

    @staticmethod
    def _log_process_line(stream_name: str, line: str):
        line = line.strip()
        if not line:
            return
        if " ERROR" in line:
            LOGGER.error("Scrapy: %s", line)
        elif " WARNING" in line:
            LOGGER.warning("Scrapy: %s", line)
        elif stream_name == "stderr" or "SCRAPE_" in line:
            LOGGER.info("Scrapy: %s", line)
        else:
            LOGGER.debug("Scrapy: %s", line)

    def _update_progress(self, line: str):
        total_match = SCRAPE_TOTAL_PATTERN.search(line)
        if total_match:
            self._scrape_total = int(total_match.group(1))
            self._emit_progress(0, self._scrape_total, 0, 0)
            return

        progress_match = SCRAPE_PROGRESS_PATTERN.search(line)
        if progress_match:
            received, total, unique, pages, failed = map(int, progress_match.groups())
            self._scrape_total = total
            self._emit_progress(received, total, unique, pages, failed)

    def _emit_progress(
        self,
        received: int,
        total: int,
        unique: int,
        pages: int,
        failed: int = 0,
    ):
        self._records_received = unique
        self._pages_received = pages
        if total:
            page_label = "página" if pages == 1 else "páginas"
            message = (
                f"{received:,} de {total:,} resultados · "
                f"{unique:,} colegios únicos · {pages} {page_label}"
            )
        else:
            message = "Sin registros"
        if failed:
            failed_label = "página fallida" if failed == 1 else "páginas fallidas"
            message += f" · {failed} {failed_label}"
        self.progressChanged.emit(received, total, pages, message)

    @Slot(QProcess.ProcessError)
    def _process_error(self, error):
        if error == QProcess.ProcessError.FailedToStart:
            self._timeout_timer.stop()
            self._termination_timer.stop()
            self._running = False
            details = (
                "No se pudo iniciar eduescale-scraper.exe. "
                "Mantén el worker junto al ejecutable de la interfaz."
                if self._frozen
                else "Verifica que las dependencias estén instaladas con «uv sync»."
            )
            self._fail(
                "No se pudo iniciar Scrapy",
                f"{details}\n{self._process.errorString()}",
            )

    @Slot(int, QProcess.ExitStatus)
    def _scrape_finished(self, exit_code, exit_status):
        self._timeout_timer.stop()
        self._termination_timer.stop()
        self._read_process_output()
        for stream_name, remaining_text in self._progress_buffers.items():
            if remaining_text:
                self._update_progress(remaining_text)
                self._log_process_line(stream_name, remaining_text)
            self._progress_buffers[stream_name] = ""
        self._running = False

        if self._shutting_down:
            cleanup_error = self._cleanup_staging()
            if cleanup_error:
                self.failed.emit(
                    "No se pudieron limpiar archivos temporales",
                    cleanup_error,
                )
            return
        if self._scrape_timed_out:
            self._fail(
                "Extracción detenida",
                "La extracción superó el límite de 15 minutos. "
                "Prueba con una ubicación más específica.",
            )
        elif self._cancel_requested:
            cleanup_error = self._cleanup_staging()
            self.cancelled.emit()
            if cleanup_error:
                self.warning.emit(
                    "No se pudieron limpiar archivos temporales",
                    cleanup_error,
                )
        elif exit_status != QProcess.ExitStatus.NormalExit or exit_code != 0:
            details = self._process_log.strip() or "Scrapy terminó con un error."
            self._fail("Error de extracción", details)
        else:
            try:
                staging_ready = (
                    self._staging_path is not None
                    and self._staging_path.is_file()
                    and self._staging_path.stat().st_size > 0
                )
            except OSError as error:
                self._fail("No se pudo validar el Excel temporal", str(error))
                return

            if not staging_ready:
                if self._staging_path is None:
                    details = "No se preparó un archivo temporal para la extracción."
                else:
                    details = (
                        "Scrapy terminó sin generar un Excel válido.\n"
                        f"Archivo solicitado: {self._output_path}\n"
                        f"Código de salida: {exit_code}"
                    )
                self._fail("No se generó el Excel", details)
                return

            try:
                self._publish_staging_file()
            except OSError as error:
                self._fail(
                    "No se pudo guardar el Excel",
                    f"{error}\nCierra el archivo de destino si está abierto en Excel.",
                )
            else:
                cleanup_error = self._cleanup_staging()
                self.completed.emit(
                    str(self._output_path),
                    self._scrape_total or 0,
                    self._records_received,
                    self._pages_received,
                )
                if cleanup_error:
                    self.warning.emit(
                        "Excel creado, pero no se pudieron limpiar temporales",
                        cleanup_error,
                    )

    def _publish_staging_file(self):
        if self._staging_path is None or self._output_path is None:
            raise RuntimeError("No hay un archivo temporal para publicar.")
        self._staging_path.replace(self._output_path)

    def _cleanup_staging(self) -> str | None:
        if self._staging_directory is None:
            return None
        try:
            shutil.rmtree(self._staging_directory)
        except FileNotFoundError:
            pass
        except OSError as error:
            return str(error)
        self._staging_directory = None
        self._staging_path = None
        return None

    def _fail(self, title: str, details: str):
        cleanup_error = self._cleanup_staging()
        if cleanup_error:
            details = (
                f"{details}\nNo se pudieron limpiar los temporales: {cleanup_error}"
            )
        self.failed.emit(title, details)

    def _request_process_stop(self):
        if self._process.state() != QProcess.ProcessState.NotRunning:
            self._process.terminate()
            self._termination_timer.start(PROCESS_TERMINATE_GRACE_MS)

    @Slot()
    def _force_stop_process(self):
        if self._process.state() != QProcess.ProcessState.NotRunning:
            self._process.kill()

    @Slot()
    def _stop_timed_out_scrape(self):
        if self._process.state() != QProcess.ProcessState.NotRunning:
            self._scrape_timed_out = True
            self._request_process_stop()

    def shutdown(self):
        self._shutting_down = True
        self._timeout_timer.stop()
        self._termination_timer.stop()
        if self._process.state() != QProcess.ProcessState.NotRunning:
            self._process.terminate()
            if not self._process.waitForFinished(PROCESS_TERMINATE_GRACE_MS):
                self._process.kill()
                if not self._process.waitForFinished(PROCESS_TERMINATE_GRACE_MS):
                    self.failed.emit(
                        "No se pudo detener Scrapy",
                        self._process.errorString(),
                    )
        self._running = False
        cleanup_error = self._cleanup_staging()
        if cleanup_error:
            self.failed.emit(
                "No se pudieron limpiar archivos temporales",
                cleanup_error,
            )
