from collections.abc import Callable
from pathlib import Path
from typing import Protocol

from eduescale.domain.offline_search import LocalSearchFilter
from eduescale.domain.schools import SchoolFilters

LocationList = list[tuple[str, str]]
SearchProgress = Callable[[int, int, int], None]


class LocationGateway(Protocol):
    def fetch(self, kind: str, parent_code: str) -> LocationList: ...


class OfflineDatabaseRepository(Protocol):
    def inspect(
        self,
        database_path: Path,
    ) -> tuple[dict[str, str], list[tuple[str, str, str, int]]]: ...

    def validate(self, database_path: Path) -> None: ...

    def read_metadata(self, database_path: Path) -> dict[str, str]: ...

    def write_metadata(
        self,
        database_path: Path,
        *,
        updated_at: str,
        total_records: int,
        unique_records: int,
        pages: int,
    ) -> None: ...

    def filter(
        self,
        source_path: Path,
        output_path: Path,
        filters: list[LocalSearchFilter],
        cancelled: Callable[[], bool],
        progress: SearchProgress,
    ) -> tuple[int, int]: ...

    def publish(self, staged_path: Path, target_path: Path) -> None: ...


class ExportGateway(Protocol):
    def export(
        self,
        export_type: str,
        database_path: Path,
        output_path: Path,
        password: str | None = None,
        compressed: bool = False,
    ) -> None: ...


class CheckpointGateway(Protocol):
    def prepare(
        self,
        output_path: Path,
        filters: list[SchoolFilters],
    ) -> tuple[Path, str]: ...

    def complete(self, checkpoint_directory: Path) -> str | None: ...
