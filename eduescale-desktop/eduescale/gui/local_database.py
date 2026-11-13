import logging
import shutil
import tempfile
import threading
from pathlib import Path

from PySide6.QtCore import QObject, QThread, Signal

from eduescale.application.errors import DataAccessError, SearchCancelled
from eduescale.application.use_cases.offline_database import (
    OfflineDatabaseUseCases,
)
from eduescale.domain.offline_search import LocalSearchFilter

LOGGER = logging.getLogger(__name__)


class _LocalSearchWorker(QThread):
    progressChanged = Signal(int, int, int, str)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)
    cancelled = Signal()

    def __init__(
        self,
        use_cases: OfflineDatabaseUseCases,
        source_path: Path,
        output_path: Path,
        filters: list[LocalSearchFilter],
    ):
        super().__init__()
        self._use_cases = use_cases
        self._source_path = source_path
        self._output_path = output_path
        self._filters = filters
        self._cancel_event = threading.Event()

    def run(self):
        try:
            total, matched = self._use_cases.search(
                self._source_path,
                self._output_path,
                self._filters,
                self._cancel_event.is_set,
                self._report_progress,
            )
        except SearchCancelled:
            self.cancelled.emit()
        except (DataAccessError, OSError, TypeError, ValueError) as error:
            LOGGER.exception("Offline search failed for %s.", self._source_path)
            self.failed.emit("Error en la base offline", str(error))
        else:
            self.completed.emit(str(self._output_path), total, matched, 0)

    def cancel(self):
        self._cancel_event.set()

    def _report_progress(self, processed: int, total: int, matched: int):
        message = (
            f"Base offline · {processed:,} de {total:,} registros · "
            f"{matched:,} coinciden"
        )
        self.progressChanged.emit(processed, total, 0, message)


class LocalDatabaseSearchRunner(QObject):
    started = Signal()
    progressChanged = Signal(int, int, int, str)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)
    cancelled = Signal()

    def __init__(
        self,
        use_cases: OfflineDatabaseUseCases,
        parent: QObject | None = None,
    ):
        super().__init__(parent)
        self._use_cases = use_cases
        self._worker: _LocalSearchWorker | None = None
        self._temporary_directory: Path | None = None

    def start(
        self,
        source_path: Path,
        filters: list[LocalSearchFilter],
    ):
        if self._worker is not None and self._worker.isRunning():
            self.failed.emit(
                "Búsqueda en curso",
                "Ya hay una búsqueda local activa.",
            )
            return
        cleanup_error = self._cleanup_temporary_directory()
        if cleanup_error:
            self.failed.emit("No se pudo limpiar la búsqueda anterior", cleanup_error)
            return
        try:
            temporary_directory = Path(tempfile.mkdtemp(prefix="eduescale-offline-"))
        except OSError as error:
            self.failed.emit("No se pudo preparar la búsqueda offline", str(error))
            return

        self._temporary_directory = temporary_directory
        output_path = temporary_directory / "results.sqlite3"
        worker = _LocalSearchWorker(
            self._use_cases,
            source_path,
            output_path,
            filters,
        )
        worker.progressChanged.connect(self.progressChanged)
        worker.completed.connect(self.completed)
        worker.failed.connect(self.failed)
        worker.cancelled.connect(self.cancelled)
        worker.finished.connect(self._worker_finished)
        self._worker = worker
        self.started.emit()
        worker.start()

    def cancel(self):
        if self._worker is not None and self._worker.isRunning():
            self._worker.cancel()

    def shutdown(self):
        worker = self._worker
        if worker is not None:
            if worker.isRunning():
                worker.cancel()
                worker.wait()
            worker.deleteLater()
            self._worker = None
        cleanup_error = self._cleanup_temporary_directory()
        if cleanup_error:
            LOGGER.error("Could not clean offline search data: %s", cleanup_error)

    def _cleanup_temporary_directory(self) -> str | None:
        if self._temporary_directory is None:
            return None
        try:
            shutil.rmtree(self._temporary_directory)
        except FileNotFoundError:
            pass
        except OSError as error:
            return str(error)
        self._temporary_directory = None
        return None

    def _worker_finished(self):
        worker = self.sender()
        if isinstance(worker, _LocalSearchWorker):
            if worker is self._worker:
                self._worker = None
            worker.deleteLater()
