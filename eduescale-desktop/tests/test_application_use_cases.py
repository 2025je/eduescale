from pathlib import Path

from eduescale.application.use_cases.offline_database import (
    OfflineDatabaseUseCases,
)


class _RecordingOfflineDatabaseRepository:
    def __init__(self):
        self.calls = []

    def inspect(self, database_path: Path):
        self.calls.append(("inspect", database_path))
        return {"source": "ESCALE"}, [
            ("Loreto", "Maynas", "Iquitos", 2),
            ("Lima", "Lima", "Miraflores", 1),
        ]

    def validate(self, database_path: Path):
        self.calls.append(("validate", database_path))

    def read_metadata(self, database_path: Path):
        self.calls.append(("read_metadata", database_path))
        return {"source": "ESCALE"}

    def write_metadata(
        self,
        database_path: Path,
        *,
        updated_at: str,
        total_records: int,
        unique_records: int,
        pages: int,
    ):
        self.calls.append(
            (
                "write_metadata",
                database_path,
                updated_at,
                total_records,
                unique_records,
                pages,
            )
        )

    def filter(
        self,
        source_path,
        output_path,
        filters,
        cancelled,
        progress,
    ):
        self.calls.append(("filter", source_path, output_path))
        return 2, 1

    def publish(self, staged_path: Path, target_path: Path):
        self.calls.append(("publish", staged_path, target_path))


def test_publish_download_verifies_staged_metadata_before_replacing_target(
    tmp_path,
):
    repository = _RecordingOfflineDatabaseRepository()
    use_cases = OfflineDatabaseUseCases(repository)
    staged_path = tmp_path / "staged.sqlite3"
    target_path = tmp_path / "escale.sqlite3"

    metadata = use_cases.publish_download(
        staged_path,
        target_path,
        total_records=120,
        unique_records=100,
        pages=3,
        updated_at="2025-01-02T03:04:05+00:00",
    )

    assert metadata == {"source": "ESCALE"}
    assert repository.calls == [
        (
            "write_metadata",
            staged_path,
            "2025-01-02T03:04:05+00:00",
            120,
            100,
            3,
        ),
        ("read_metadata", staged_path),
        ("publish", staged_path, target_path),
    ]


def test_inspect_returns_validated_location_summary(tmp_path):
    repository = _RecordingOfflineDatabaseRepository()
    metadata, summary = OfflineDatabaseUseCases(repository).inspect(
        tmp_path / "schools.sqlite3"
    )

    assert metadata == {"source": "ESCALE"}
    assert summary.total_schools == 3
    assert summary.department_count == 2
    assert summary.province_count == 2
    assert summary.district_count == 2
    assert [
        (location.region, location.province, location.district, location.schools)
        for location in summary.locations
    ] == [
        ("Loreto", "Maynas", "Iquitos", 2),
        ("Lima", "Lima", "Miraflores", 1),
    ]
