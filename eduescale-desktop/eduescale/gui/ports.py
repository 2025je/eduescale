from pathlib import Path
from typing import Protocol

from PySide6.QtCore import Signal

from eduescale.client import SchoolFilters


class LocationServicePort(Protocol):
    loaded: Signal
    failed: Signal

    def load(self, kind: str, parent_code: str = "") -> None: ...

    def shutdown(self) -> None: ...


class ScrapeRunnerPort(Protocol):
    started: Signal
    progressChanged: Signal
    completed: Signal
    failed: Signal
    cancelled: Signal
    warning: Signal

    def start(self, output_path: Path, filter_sets: list[SchoolFilters]) -> None: ...

    def cancel(self) -> None: ...

    def shutdown(self) -> None: ...


class ExportRunnerPort(Protocol):
    started: Signal
    completed: Signal
    failed: Signal

    def start(
        self,
        export_type: str,
        database_path: Path,
        output_path: Path,
    ) -> None: ...

    def shutdown(self) -> None: ...
