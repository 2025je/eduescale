from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from eduescale.gui.contracts.ports import (
    OfflineDatabaseDownloadPort,
    OfflineDatabaseUseCasesPort,
)


@dataclass(frozen=True)
class OfflineDatabaseUpdateResult:
    metadata: dict[str, str]
    checkpoint_cleanup_error: str | None


class OfflineDatabaseUpdateServicePort(Protocol):
    def publish(
        self,
        staged_path: Path,
        target_path: Path,
        *,
        total_records: int,
        unique_records: int,
        pages: int,
    ) -> OfflineDatabaseUpdateResult: ...


class OfflineDatabaseUpdateService:
    def __init__(
        self,
        offline_database_use_cases: OfflineDatabaseUseCasesPort,
        offline_database_download: OfflineDatabaseDownloadPort,
    ):
        self._offline_database_use_cases = offline_database_use_cases
        self._offline_database_download = offline_database_download

    def publish(
        self,
        staged_path: Path,
        target_path: Path,
        *,
        total_records: int,
        unique_records: int,
        pages: int,
    ) -> OfflineDatabaseUpdateResult:
        metadata = self._offline_database_use_cases.publish_download(
            staged_path,
            target_path,
            total_records=total_records,
            unique_records=unique_records,
            pages=pages,
        )
        cleanup_error = (
            self._offline_database_download.complete_database_download()
        )
        return OfflineDatabaseUpdateResult(metadata, cleanup_error)
