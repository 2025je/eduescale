from pathlib import Path
from typing import Protocol

from PySide6.QtCore import SignalInstance

from eduescale.client import SchoolFilters
from eduescale.gui.local_database import LocalSearchFilter


class LocationServicePort(Protocol):
    loaded: SignalInstance
    failed: SignalInstance

    def load(self, kind: str, parent_code: str = "") -> None: ...

    def shutdown(self) -> None: ...


class ScrapeRunnerPort(Protocol):
    started: SignalInstance
    progressChanged: SignalInstance
    completed: SignalInstance
    failed: SignalInstance
    cancelled: SignalInstance
    warning: SignalInstance

    def start(self, output_path: Path, filter_sets: list[SchoolFilters]) -> None: ...

    def cancel(self) -> None: ...

    def shutdown(self) -> None: ...


class LocalDatabaseSearchRunnerPort(Protocol):
    started: SignalInstance
    progressChanged: SignalInstance
    completed: SignalInstance
    failed: SignalInstance
    cancelled: SignalInstance

    def start(
        self,
        source_path: Path,
        filters: list[LocalSearchFilter],
    ) -> None: ...

    def cancel(self) -> None: ...

    def shutdown(self) -> None: ...


class ExportRunnerPort(Protocol):
    started: SignalInstance
    completed: SignalInstance
    failed: SignalInstance

    def start(
        self,
        export_type: str,
        database_path: Path,
        output_path: Path,
        password: str | None = None,
        compressed: bool = False,
    ) -> None: ...

    def shutdown(self) -> None: ...
