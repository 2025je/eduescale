from collections.abc import Callable
from datetime import datetime, timezone
from pathlib import Path

from eduescale.application.models import (
    LocationSchoolCount,
    OfflineDatabaseLocationSummary,
)
from eduescale.application.ports import OfflineDatabaseRepository, SearchProgress
from eduescale.domain.offline_search import LocalSearchFilter


class OfflineDatabaseUseCases:
    def __init__(self, repository: OfflineDatabaseRepository):
        self._repository = repository

    def validate(self, database_path: Path) -> None:
        self._repository.validate(database_path)

    def read_metadata(self, database_path: Path) -> dict[str, str]:
        return self._repository.read_metadata(database_path)

    def inspect(
        self,
        database_path: Path,
    ) -> tuple[dict[str, str], OfflineDatabaseLocationSummary]:
        metadata, grouped_locations = self._repository.inspect(database_path)
        locations = tuple(
            LocationSchoolCount(
                region=region,
                province=province,
                district=district,
                schools=schools,
            )
            for region, province, district, schools in grouped_locations
        )
        return metadata, OfflineDatabaseLocationSummary(
            total_schools=sum(location.schools for location in locations),
            department_count=len({location.region for location in locations}),
            province_count=len(
                {(location.region, location.province) for location in locations}
            ),
            district_count=len(
                {
                    (location.region, location.province, location.district)
                    for location in locations
                }
            ),
            locations=locations,
        )

    def publish_download(
        self,
        staged_path: Path,
        target_path: Path,
        *,
        total_records: int,
        unique_records: int,
        pages: int,
        updated_at: str | None = None,
    ) -> dict[str, str]:
        self._repository.write_metadata(
            staged_path,
            updated_at=updated_at
            or datetime.now(timezone.utc).isoformat(timespec="seconds"),
            total_records=total_records,
            unique_records=unique_records,
            pages=pages,
        )
        metadata = self._repository.read_metadata(staged_path)
        self._repository.publish(staged_path, target_path)
        return metadata

    def search(
        self,
        source_path: Path,
        output_path: Path,
        filters: list[LocalSearchFilter],
        cancelled: Callable[[], bool],
        progress: SearchProgress,
    ) -> tuple[int, int]:
        return self._repository.filter(
            source_path,
            output_path,
            filters,
            cancelled,
            progress,
        )
