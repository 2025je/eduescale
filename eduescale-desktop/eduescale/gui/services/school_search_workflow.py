import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Protocol

from PySide6.QtCore import QObject, Signal, SignalInstance, Slot

from eduescale.domain.schools import SchoolFilters
from eduescale.gui.contracts.ports import (
    DataSource,
    LocalDatabaseSearchRunnerPort,
    ScrapeRunnerPort,
)
from eduescale.gui.services.progress import ProgressMetrics
from eduescale.gui.services.school_search_service import SchoolSearchRequest

LOGGER = logging.getLogger(__name__)
SearchOperation = Literal["online_search", "offline_search", "database_update"]


@dataclass(frozen=True)
class SearchProgress:
    operation: SearchOperation
    metrics: ProgressMetrics


class SchoolSearchWorkflowPort(Protocol):
    started: SignalInstance
    progressChanged: SignalInstance
    schoolsReceived: SignalInstance
    searchCompleted: SignalInstance
    databaseUpdateCompleted: SignalInstance
    cancelled: SignalInstance
    failed: SignalInstance
    warning: SignalInstance

    def start_search(
        self,
        request: SchoolSearchRequest,
        data_source: DataSource,
    ) -> None: ...

    def start_database_update(self, database_path: Path) -> None: ...

    def cancel(self) -> None: ...

    def shutdown(self) -> None: ...


class SchoolSearchWorkflow(QObject):
    started = Signal()
    progressChanged = Signal(object)
    schoolsReceived = Signal(object)
    searchCompleted = Signal(str, int, int, int, bool)
    databaseUpdateCompleted = Signal(str, int, int, int)
    cancelled = Signal(str)
    failed = Signal(str, str, str)
    warning = Signal(str, str)

    def __init__(
        self,
        scrape_runner: ScrapeRunnerPort,
        local_database_search_runner: LocalDatabaseSearchRunnerPort,
        parent: QObject | None = None,
    ):
        super().__init__(parent)
        self._scrape_runner = scrape_runner
        self._local_database_search_runner = local_database_search_runner
        self._active_operation: SearchOperation | None = None
        for runner in (scrape_runner, local_database_search_runner):
            if isinstance(runner, QObject) and runner.parent() is None:
                runner.setParent(self)

        self._scrape_runner.started.connect(self._started)
        self._scrape_runner.progressChanged.connect(self._progress_changed)
        self._scrape_runner.schoolsReceived.connect(self._schools_received)
        self._scrape_runner.completed.connect(self._scrape_completed)
        self._scrape_runner.failed.connect(self._scrape_failed)
        self._scrape_runner.cancelled.connect(self._scrape_cancelled)
        self._scrape_runner.warning.connect(self._warning)
        self._local_database_search_runner.started.connect(self._started)
        self._local_database_search_runner.progressChanged.connect(
            self._progress_changed
        )
        self._local_database_search_runner.schoolsReceived.connect(
            self._schools_received
        )
        self._local_database_search_runner.completed.connect(
            self._offline_search_completed
        )
        self._local_database_search_runner.failed.connect(self._local_search_failed)
        self._local_database_search_runner.cancelled.connect(
            self._local_search_cancelled
        )

    def start_search(
        self,
        request: SchoolSearchRequest,
        data_source: DataSource,
    ) -> None:
        self._ensure_idle()
        if data_source not in {"online", "offline"}:
            raise ValueError(f"Unknown school data source: {data_source}")
        if data_source == "offline":
            if request.offline_database_path is None or request.local_filters is None:
                raise RuntimeError("La solicitud de búsqueda offline está incompleta.")
            self._active_operation = "offline_search"
            self._local_database_search_runner.start(
                request.offline_database_path,
                request.local_filters,
            )
            return

        self._active_operation = "online_search"
        self._scrape_runner.start(
            request.output_path,
            request.filter_sets,
            purpose="online_search",
        )

    def start_database_update(self, database_path: Path) -> None:
        self._ensure_idle()
        self._active_operation = "database_update"
        self._scrape_runner.start(
            database_path,
            [SchoolFilters()],
            purpose="database_update",
        )

    def cancel(self) -> None:
        if self._active_operation == "offline_search":
            self._local_database_search_runner.cancel()
        elif self._active_operation is not None:
            self._scrape_runner.cancel()

    @Slot()
    def shutdown(self) -> None:
        self._local_database_search_runner.shutdown()
        self._scrape_runner.shutdown()

    @Slot()
    def _started(self) -> None:
        self.started.emit()

    @Slot(object)
    def _progress_changed(
        self,
        metrics: ProgressMetrics,
    ) -> None:
        operation = self._active_operation
        if operation is None:
            LOGGER.error("Progress received without an active operation.")
            return
        self.progressChanged.emit(SearchProgress(operation, metrics))

    @Slot(object)
    def _schools_received(self, records: object) -> None:
        self.schoolsReceived.emit(records)

    @Slot(str, int, int, int)
    def _scrape_completed(
        self,
        database_path: str,
        total: int,
        records: int,
        pages: int,
    ) -> None:
        operation = self._finish_operation()
        if operation == "database_update":
            self.databaseUpdateCompleted.emit(database_path, total, records, pages)
        elif operation == "online_search":
            self.searchCompleted.emit(database_path, total, records, pages, False)
        else:
            LOGGER.error("Scrape completion received without an active operation.")

    @Slot(str, int, int, int)
    def _offline_search_completed(
        self,
        database_path: str,
        total: int,
        records: int,
        pages: int,
    ) -> None:
        operation = self._finish_operation()
        if operation == "offline_search":
            self.searchCompleted.emit(database_path, total, records, pages, True)
        else:
            LOGGER.error(
                "Offline search completion received during operation %s.",
                operation,
            )

    @Slot(str, str)
    def _scrape_failed(self, title: str, details: str) -> None:
        self._fail_operation(title, details)

    @Slot(str, str)
    def _local_search_failed(self, title: str, details: str) -> None:
        self._fail_operation(title, details)

    def _fail_operation(self, title: str, details: str) -> None:
        operation = self._finish_operation()
        if operation is None:
            LOGGER.error("Runner failure received without an active operation.")
            return
        self.failed.emit(operation, title, details)

    @Slot()
    def _scrape_cancelled(self) -> None:
        self._cancel_operation()

    @Slot()
    def _local_search_cancelled(self) -> None:
        self._cancel_operation()

    def _cancel_operation(self) -> None:
        operation = self._finish_operation()
        if operation is None:
            LOGGER.warning("Runner cancellation received without an active operation.")
            return
        self.cancelled.emit(operation)

    def _finish_operation(self) -> SearchOperation | None:
        operation = self._active_operation
        self._active_operation = None
        return operation

    def _ensure_idle(self) -> None:
        if self._active_operation is not None:
            raise RuntimeError(
                "Cannot start a new school search while another operation is active."
            )

    @Slot(str, str)
    def _warning(self, title: str, details: str) -> None:
        self.warning.emit(title, details)
