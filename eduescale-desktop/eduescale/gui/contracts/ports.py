from pathlib import Path
from typing import Literal, Protocol, runtime_checkable

from PySide6.QtCore import SignalInstance

from eduescale.application.models import OfflineDatabaseLocationSummary
from eduescale.application.ports import ExportType
from eduescale.domain.offline_search import LocalSearchFilter
from eduescale.domain.schools import SchoolFilters

ScrapePurpose = Literal["online_search", "database_update"]
DataSource = Literal["online", "offline"]


class OfflineDatabaseValidationPort(Protocol):
    def validate(self, database_path: Path) -> None: ...


class OfflineDatabaseUseCasesPort(OfflineDatabaseValidationPort, Protocol):
    def read_metadata(self, database_path: Path) -> dict[str, str]: ...

    def inspect(
        self,
        database_path: Path,
    ) -> tuple[dict[str, str], OfflineDatabaseLocationSummary]: ...

    def publish_download(
        self,
        staged_path: Path,
        target_path: Path,
        *,
        total_records: int,
        unique_records: int,
        pages: int,
    ) -> dict[str, str]: ...


class LocationServicePort(Protocol):
    loaded: SignalInstance
    failed: SignalInstance

    def load(self, kind: str, parent_code: str = "") -> None: ...

    def shutdown(self) -> None: ...


@runtime_checkable
class ScrapeRunnerPort(Protocol):
    started: SignalInstance
    progressChanged: SignalInstance
    schoolsReceived: SignalInstance
    completed: SignalInstance
    failed: SignalInstance
    cancelled: SignalInstance
    warning: SignalInstance

    def start(
        self,
        output_path: Path,
        filter_sets: list[SchoolFilters],
        *,
        purpose: ScrapePurpose,
    ) -> None: ...

    def cancel(self) -> None: ...

    def shutdown(self) -> None: ...


@runtime_checkable
class OfflineDatabaseDownloadPort(Protocol):
    def complete_database_download(self) -> str | None: ...


class LocalDatabaseSearchRunnerPort(Protocol):
    started: SignalInstance
    progressChanged: SignalInstance
    schoolsReceived: SignalInstance
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
        export_type: ExportType,
        database_path: Path,
        output_path: Path,
        password: str | None = None,
        compressed: bool = False,
    ) -> None: ...

    def shutdown(self) -> None: ...


class DistrictMapCatalogPort(Protocol):
    district_metadata: dict[str, dict[str, str]]
    district_codes: list[str]
    district_codes_by_province: dict[str, list[str]]
    map_department_metadata: dict[str, dict[str, str]]
    map_province_metadata: dict[str, dict[str, str | int]]

    def effective_district_codes(
        self,
        department_codes: list[str],
        province_codes: list[str],
        district_codes: list[str],
    ) -> list[str]: ...

    def district_properties(self, code: str) -> dict[str, str] | None: ...
