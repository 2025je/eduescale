from eduescale.infrastructure.persistence.download_checkpoints import (
    DownloadCheckpoint,
)


def test_checkpoint_reuses_valid_pages_and_invalidates_completed_downloads(tmp_path):
    checkpoint = DownloadCheckpoint(tmp_path, "same-search")
    checkpoint.set_filter_totals([51])
    checkpoint.save_page(0, 0, b"<page>first</page>")

    resumed_checkpoint = DownloadCheckpoint(tmp_path, "same-search")
    assert resumed_checkpoint.cached_page_count == 1
    assert resumed_checkpoint.load_page(0, 0) == b"<page>first</page>"

    DownloadCheckpoint.mark_directory_complete(tmp_path)
    completed_checkpoint = DownloadCheckpoint(tmp_path, "same-search")

    assert completed_checkpoint.cached_page_count == 0
    assert completed_checkpoint.load_page(0, 0) is None


def test_checkpoint_invalidates_pages_when_source_total_changes(tmp_path):
    checkpoint = DownloadCheckpoint(tmp_path, "same-search")
    checkpoint.set_filter_totals([50])
    checkpoint.save_page(0, 0, b"<page>first</page>")

    assert checkpoint.set_filter_totals([51])

    assert checkpoint.cached_page_count == 0
    assert checkpoint.load_page(0, 0) is None


def test_checkpoint_removes_corrupt_compressed_page(tmp_path):
    checkpoint = DownloadCheckpoint(tmp_path, "same-search")
    corrupt_page = tmp_path / "page-000-00000000.xml.gz"
    corrupt_page.write_bytes(b"not gzip")

    assert checkpoint.load_page(0, 0) is None
    assert not corrupt_page.exists()
