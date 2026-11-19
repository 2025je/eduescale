import json
import logging

from eduescale.gui.viewmodels.log_viewer import (
    LINE_LOOKAHEAD_BYTES,
    LOG_PAGE_BYTES,
    LogViewerViewModel,
)


def test_refreshes_selected_file_and_tracks_rotation(tmp_path):
    log_path = tmp_path / "eduescale.log"
    backup_path = tmp_path / "eduescale.log.1"
    log_path.write_text("active\n", encoding="utf-8")
    viewer = LogViewerViewModel(tmp_path)
    changes = []
    viewer.contentChanged.connect(lambda: changes.append(viewer.content))

    viewer.refresh()
    viewer.refresh()
    assert viewer.content == "active\n"
    assert changes == ["active\n"]

    log_path.write_text("active\nnew\n", encoding="utf-8")
    viewer.refresh()
    assert viewer.content == "active\nnew\n"

    log_path.rename(backup_path)
    log_path.write_text("after rotation\n", encoding="utf-8")
    viewer.refresh()
    assert viewer.content == "after rotation\n"
    viewer.selectFile(1)
    assert viewer.content == "active\nnew\n"
    viewer.selectFile(2)
    assert viewer.content == ""
    assert viewer.error == ""
    viewer.selectFile(0)
    assert viewer.content == "after rotation\n"


def test_read_error_is_visible_logged_once_and_recovers(tmp_path, monkeypatch, caplog):
    log_path = tmp_path / "eduescale.log"
    log_path.write_text("available", encoding="utf-8")
    viewer = LogViewerViewModel(tmp_path)
    original_open = type(log_path).open

    def fail_open(path, *args, **kwargs):
        if path == log_path:
            raise PermissionError("access denied")
        return original_open(path, *args, **kwargs)

    with monkeypatch.context() as patch:
        patch.setattr(type(log_path), "open", fail_open)
        with caplog.at_level(logging.WARNING, logger="eduescale"):
            viewer.refresh()
            viewer.refresh()
        assert "No se pudo leer el registro" in viewer.error
        assert sum("Could not read log file" in r.message for r in caplog.records) == 1

    viewer.refresh()
    assert viewer.content == "available"
    assert viewer.error == ""


def test_invalid_selection_does_not_read_arbitrary_files(tmp_path, caplog):
    viewer = LogViewerViewModel(tmp_path)
    with caplog.at_level(logging.WARNING, logger="eduescale"):
        viewer.selectFile(-1)
        viewer.selectFile(len(viewer.files))
    assert len(caplog.records) == 2


def test_large_log_pages_preserve_utf8_and_follow_only_the_latest(tmp_path):
    log_path = tmp_path / "eduescale.log"
    text = "é" * (LOG_PAGE_BYTES // 2 - 1) + "😀" + "registros\n" * 10000
    log_path.write_text(text, encoding="utf-8")
    viewer = LogViewerViewModel(tmp_path)
    viewer.refresh()

    assert viewer.pageCount > 2
    assert viewer.pageNumber == viewer.pageCount
    assert viewer.canPrevious
    assert not viewer.canNext
    pages = [viewer.content]
    while viewer.canPrevious:
        viewer.previousPage()
        pages.append(viewer.content)
        assert len(viewer.content.encode("utf-8")) <= (
            LOG_PAGE_BYTES + 2 * LINE_LOOKAHEAD_BYTES + 4
        )
    assert "".join(reversed(pages)) == text

    first_page = viewer.content
    with log_path.open("a", encoding="utf-8") as stream:
        stream.write("nuevo registro\n" * 2000)
    viewer.refresh()
    assert viewer.pageNumber == 1
    assert viewer.content == first_page
    while viewer.canNext:
        viewer.nextPage()
    assert viewer.content.endswith("nuevo registro\n")

    with log_path.open("a", encoding="utf-8") as stream:
        stream.write("registro en vivo\n")
    viewer.refresh()
    assert viewer.pageNumber == viewer.pageCount
    assert viewer.content.endswith("registro en vivo\n")

    viewer.previousPage()
    log_path.rename(tmp_path / "eduescale.log.1")
    log_path.write_text("archivo nuevo\n", encoding="utf-8")
    viewer.refresh()
    assert viewer.pageNumber == 1
    assert viewer.pageCount == 1
    assert viewer.content == "archivo nuevo\n"


def test_large_log_reads_only_one_bounded_page(tmp_path, monkeypatch):
    log_path = tmp_path / "eduescale.log"
    log_path.write_bytes(b"x" * (5 * 1024 * 1024))
    original_open = type(log_path).open
    reads = []

    class CountingStream:
        def __init__(self, stream):
            self.stream = stream

        def __enter__(self):
            return self

        def __exit__(self, *args):
            self.stream.close()

        def fileno(self):
            return self.stream.fileno()

        def seek(self, offset):
            return self.stream.seek(offset)

        def read(self, size):
            reads.append(size)
            return self.stream.read(size)

    def counting_open(path, *args, **kwargs):
        stream = original_open(path, *args, **kwargs)
        return CountingStream(stream) if path == log_path else stream

    monkeypatch.setattr(type(log_path), "open", counting_open)
    viewer = LogViewerViewModel(tmp_path)
    viewer.refresh()

    assert len(viewer.content) == LOG_PAGE_BYTES
    assert sum(reads) <= LOG_PAGE_BYTES + 4 * LINE_LOOKAHEAD_BYTES + 24


def test_pages_preserve_windows_newlines_at_boundaries(tmp_path):
    log_path = tmp_path / "eduescale.log"
    text = "a" * (LOG_PAGE_BYTES - 1) + "\r\nend"
    log_path.write_bytes(text.encode("utf-8"))
    viewer = LogViewerViewModel(tmp_path)
    viewer.refresh()
    last = viewer.content
    viewer.previousPage()

    assert viewer.content + last == text.replace("\r\n", "\n")


def test_jump_to_any_page_and_resume_following_latest(tmp_path, caplog):
    log_path = tmp_path / "eduescale.log"
    log_path.write_bytes(b"a" * (LOG_PAGE_BYTES * 4))
    viewer = LogViewerViewModel(tmp_path)
    viewer.refresh()

    viewer.goToPage(1)
    assert viewer.pageNumber == 1
    viewer.goToPage(3)
    assert viewer.pageNumber == 3
    with caplog.at_level(logging.WARNING, logger="eduescale"):
        viewer.goToPage(0)
        viewer.goToPage(5)
    assert viewer.pageNumber == 3
    assert len(caplog.records) == 2
    viewer.goToPage(viewer.pageCount)
    with log_path.open("ab") as stream:
        stream.write(b"new")
    viewer.refresh()
    assert viewer.pageNumber == 5
    assert viewer.content == "new"


def test_formats_json_preserves_original_and_legacy_records(tmp_path):
    log_path = tmp_path / "eduescale.log"
    record = {
        "timestamp": "2026-11-19T15:00:00Z",
        "level": "error",
        "logger": "eduescale.gui",
        "event": "Falló la búsqueda",
        "exception": "Traceback\nValueError: ejemplo",
        "filters": {"nivel": "primaria"},
    }
    original_json = json.dumps(record, ensure_ascii=False)
    legacy = "2026-11-13 11:06:47 INFO eduescale.gui: Búsqueda en curso\n"
    text = legacy + "registro antiguo\n" + original_json + "\n" + '{"invalid":\n'
    log_path.write_bytes(text.encode("utf-8"))
    viewer = LogViewerViewModel(tmp_path)
    viewer.refresh()

    assert viewer.rawContent == text
    assert "registro antiguo\n" in viewer.content
    assert "2026-11-13 11:06:47 · INFO · eduescale.gui\nBúsqueda en curso" in (
        viewer.content
    )
    assert "2026-11-19T15:00:00Z · ERROR · eduescale.gui" in viewer.content
    assert "Falló la búsqueda" in viewer.content
    assert "Traceback\nValueError: ejemplo" in viewer.content
    assert 'filters: {"nivel": "primaria"}' in viewer.content
    assert '{"invalid":\n' in viewer.content
    assert original_json not in viewer.content


def test_json_record_crossing_page_boundary_is_formatted_once(tmp_path):
    log_path = tmp_path / "eduescale.log"
    record = json.dumps(
        {"level": "warning", "event": "un mensaje largo " * 20}, ensure_ascii=False
    )
    text = "x" * (LOG_PAGE_BYTES - 40) + "\n" + record + "\n" + "final\n" * 100
    log_path.write_bytes(text.encode("utf-8"))
    viewer = LogViewerViewModel(tmp_path)
    viewer.refresh()
    last_raw = viewer.rawContent
    last_formatted = viewer.content
    viewer.goToPage(1)

    assert viewer.rawContent + last_raw == text
    assert record in viewer.rawContent
    assert record not in viewer.content
    assert viewer.content.count("WARNING") == 1
    assert "WARNING" not in last_formatted


def test_last_json_record_crossing_boundary_remains_intact(tmp_path):
    log_path = tmp_path / "eduescale.log"
    record = json.dumps({"level": "info", "event": "último " * 20}, ensure_ascii=False)
    text = "x" * (LOG_PAGE_BYTES - 40) + "\n" + record + "\n"
    log_path.write_bytes(text.encode("utf-8"))
    viewer = LogViewerViewModel(tmp_path)
    viewer.refresh()

    assert viewer.pageNumber == 2
    assert viewer.rawContent == record + "\n"
    assert "INFO\núltimo" in viewer.content
    viewer.goToPage(1)
    assert viewer.rawContent + record + "\n" == text
