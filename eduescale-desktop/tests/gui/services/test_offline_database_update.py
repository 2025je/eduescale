import pytest

from eduescale.gui.services.offline_database_update import OfflineDatabaseUpdateService


class FakeOfflineDatabaseUseCases:
    def __init__(self, error: Exception | None = None):
        self.error = error
        self.publish_requests = []

    def publish_download(
        self,
        staged_path,
        target_path,
        *,
        total_records,
        unique_records,
        pages,
    ):
        if self.error is not None:
            raise self.error
        request = (
            staged_path,
            target_path,
            total_records,
            unique_records,
            pages,
        )
        self.publish_requests.append(request)
        return {"updated_at": "2026-01-01T00:00:00+00:00"}


class FakeOfflineDatabaseDownload:
    def __init__(self, cleanup_error: str | None = None):
        self.cleanup_error = cleanup_error
        self.cleanup_requests = 0

    def complete_database_download(self):
        self.cleanup_requests += 1
        return self.cleanup_error


def test_publishes_database_then_cleans_completed_download_checkpoint(tmp_path):
    use_cases = FakeOfflineDatabaseUseCases()
    download = FakeOfflineDatabaseDownload("checkpoint could not be removed")
    service = OfflineDatabaseUpdateService(use_cases, download)
    staged_path = tmp_path / "staged.sqlite3"
    target_path = tmp_path / "escale.sqlite3"

    result = service.publish(
        staged_path,
        target_path,
        total_records=10,
        unique_records=8,
        pages=2,
    )

    assert use_cases.publish_requests == [(staged_path, target_path, 10, 8, 2)]
    assert result.metadata == {"updated_at": "2026-01-01T00:00:00+00:00"}
    assert result.checkpoint_cleanup_error == "checkpoint could not be removed"
    assert download.cleanup_requests == 1


def test_does_not_clean_checkpoint_when_database_publication_fails(tmp_path):
    use_cases = FakeOfflineDatabaseUseCases(ValueError("invalid staged database"))
    download = FakeOfflineDatabaseDownload()
    service = OfflineDatabaseUpdateService(use_cases, download)

    with pytest.raises(ValueError, match="invalid staged database"):
        service.publish(
            tmp_path / "staged.sqlite3",
            tmp_path / "escale.sqlite3",
            total_records=10,
            unique_records=8,
            pages=2,
        )

    assert download.cleanup_requests == 0
