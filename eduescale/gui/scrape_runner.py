import json
import re
import sys
from pathlib import Path

from PySide6.QtCore import QObject, QProcess, QTimer, Signal, Slot

from eduescale.client import SchoolFilters

SCRAPE_TOTAL_PATTERN = re.compile(r"SCRAPE_TOTAL=(\d+)")
SCRAPE_PROGRESS_PATTERN = re.compile(
    r"SCRAPE_PROGRESS=(\d+)/(\d+)\s+UNIQUE=(\d+)\s+PAGES=(\d+)"
)
SCRAPE_TIMEOUT_MS = 15 * 60 * 1000


class ScrapeRunner(QObject):
    started = Signal()
    progressChanged = Signal(int, int, int, str)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)

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
        self._working_directory = Path(__file__).resolve().parents[1]
        self._output_path: Path | None = None
        self._process_log = ""
        self._progress_buffers = {"stdout": "", "stderr": ""}
        self._scrape_total: int | None = None
        self._records_received = 0
        self._pages_received = 0
        self._scrape_timed_out = False
        self._running = False

    def start(self, output_path: Path, filter_sets: list[SchoolFilters]):
        if self._running:
            self.failed.emit("Extracción en curso", "Ya hay una extracción activa.")
            return

        try:
            output_path.unlink(missing_ok=True)
        except OSError as error:
            self.failed.emit("No se puede guardar el Excel", str(error))
            return

        self._output_path = output_path
        self._process_log = ""
        self._progress_buffers = {"stdout": "", "stderr": ""}
        self._scrape_total = None
        self._records_received = 0
        self._pages_received = 0
        self._scrape_timed_out = False
        self._running = True

        arguments = [
            "-m",
            "scrapy",
            "crawl",
            "colegios",
            "-a",
            f"output={output_path}",
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
        self._process.setWorkingDirectory(str(self._working_directory))
        self._process.start(sys.executable, arguments)
        self._timeout_timer.start(SCRAPE_TIMEOUT_MS)
        self.started.emit()

    @Slot()
    def _read_process_output(self):
        streams = (
            ("stdout", bytes(self._process.readAllStandardOutput())),
            ("stderr", bytes(self._process.readAllStandardError())),
        )
        for stream_name, chunk in streams:
            text = chunk.decode(errors="replace")
            self._process_log += text
            buffered_text = self._progress_buffers[stream_name] + text
            lines = buffered_text.split("\n")
            self._progress_buffers[stream_name] = lines.pop()
            for line in lines:
                self._update_progress(line)

    def _update_progress(self, line: str):
        total_match = SCRAPE_TOTAL_PATTERN.search(line)
        if total_match:
            self._scrape_total = int(total_match.group(1))
            self._emit_progress(0, self._scrape_total, 0, 0)
            return

        progress_match = SCRAPE_PROGRESS_PATTERN.search(line)
        if progress_match:
            received, total, unique, pages = map(int, progress_match.groups())
            self._scrape_total = total
            self._emit_progress(received, total, unique, pages)

    def _emit_progress(self, received: int, total: int, unique: int, pages: int):
        self._records_received = unique
        self._pages_received = pages
        if total:
            message = (
                f"{received:,} de {total:,} resultados · "
                f"{unique:,} colegios únicos · {pages} páginas"
            )
        else:
            message = "Sin registros"
        self.progressChanged.emit(received, total, pages, message)

    @Slot(QProcess.ProcessError)
    def _process_error(self, error):
        if error == QProcess.ProcessError.FailedToStart:
            self._timeout_timer.stop()
            self._running = False
            self.failed.emit(
                "No se pudo iniciar Scrapy",
                "Verifica que las dependencias estén instaladas con «uv sync».",
            )

    @Slot(int, QProcess.ExitStatus)
    def _scrape_finished(self, exit_code, _exit_status):
        self._timeout_timer.stop()
        self._read_process_output()
        for stream_name, remaining_text in self._progress_buffers.items():
            if remaining_text:
                self._update_progress(remaining_text)
            self._progress_buffers[stream_name] = ""
        self._running = False

        if self._scrape_timed_out:
            self.failed.emit(
                "Extracción detenida",
                "La extracción superó el límite de 15 minutos. "
                "Prueba con una ubicación más específica.",
            )
        elif exit_code != 0:
            details = self._process_log.strip() or "Scrapy terminó con un error."
            self.failed.emit("Error de extracción", details)
        elif self._output_path and self._output_path.is_file():
            self.completed.emit(
                str(self._output_path),
                self._scrape_total or 0,
                self._records_received,
                self._pages_received,
            )
        else:
            details = self._process_log.strip()
            if not details:
                details = (
                    "Scrapy terminó sin crear el archivo esperado.\n"
                    f"Archivo solicitado: {self._output_path}\n"
                    f"Código de salida: {exit_code}"
                )
            self.failed.emit("No se generó el Excel", details)

    @Slot()
    def _stop_timed_out_scrape(self):
        if self._process.state() != QProcess.ProcessState.NotRunning:
            self._scrape_timed_out = True
            self._process.kill()

    def shutdown(self):
        self._timeout_timer.stop()
        if self._process.state() != QProcess.ProcessState.NotRunning:
            self._process.kill()
            self._process.waitForFinished(3000)
        self._running = False
