import logging
from pathlib import Path

from PySide6.QtCore import QObject, QRunnable, QThreadPool, Signal

from eduescale.application.use_cases.export_data import ExportData

LOGGER = logging.getLogger(__name__)


class _ExportSignals(QObject):
    completed = Signal(str, str)
    failed = Signal(str, str, str)


class _ExportJob(QRunnable):
    def __init__(
        self,
        export_type: str,
        database_path: Path,
        output_path: Path,
        use_case: ExportData,
        password: str | None = None,
        compressed: bool = False,
    ):
        super().__init__()
        self.export_type = export_type
        self.database_path = database_path
        self.output_path = output_path
        self.use_case = use_case
        self.password = password
        self.compressed = compressed
        self.signals = _ExportSignals()

    def run(self):
        try:
            self.use_case.execute(
                self.export_type,
                self.database_path,
                self.output_path,
                self.password,
                self.compressed,
            )
        except Exception as error:
            LOGGER.exception(
                "Failed to export %s to %s.", self.export_type, self.output_path
            )
            self.signals.failed.emit(
                self.export_type, str(error), self.output_path.name
            )
        else:
            self.signals.completed.emit(self.export_type, str(self.output_path))


class ExportRunner(QObject):
    started = Signal(str)
    completed = Signal(str, str)
    failed = Signal(str, str, str)

    def __init__(
        self,
        use_case: ExportData,
        parent: QObject | None = None,
    ):
        super().__init__(parent)
        self._use_case = use_case
        self._thread_pool = QThreadPool(self)
        self._active_job: _ExportJob | None = None

    def start(
        self,
        export_type: str,
        database_path: Path,
        output_path: Path,
        password: str | None = None,
        compressed: bool = False,
    ):
        if self._active_job is not None:
            self.failed.emit(export_type, "Ya hay una exportación en curso.", "")
            return
        job = _ExportJob(
            export_type,
            database_path,
            output_path,
            self._use_case,
            password,
            compressed=compressed,
        )
        job.signals.completed.connect(self._export_completed)
        job.signals.failed.connect(self._export_failed)
        self._active_job = job
        self.started.emit(export_type)
        self._thread_pool.start(job)

    def _export_completed(self, export_type: str, output_path: str):
        self._active_job = None
        self.completed.emit(export_type, output_path)

    def _export_failed(self, export_type: str, details: str, file_name: str):
        self._active_job = None
        self.failed.emit(export_type, details, file_name)

    def shutdown(self):
        self._thread_pool.waitForDone()
