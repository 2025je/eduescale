import sqlite3
import sys
from pathlib import Path

import pytest
from PySide6.QtCore import QCoreApplication, QProcess

from eduescale.client import SchoolFilters
from eduescale.export_files import android_data_path
from eduescale.gui.scrape_runner import (
    MAX_PROCESS_LOG_CHARS,
    MAX_PROGRESS_LINE_CHARS,
    ScrapeRunner,
)


@pytest.fixture
def application():
    return QCoreApplication.instance() or QCoreApplication([])


class FakeProcess:
    def __init__(self):
        self._state = QProcess.ProcessState.NotRunning
        self.output_path = None
        self.terminate_requested = False
        self.kill_requested = False
        self.stdout_chunk = b""
        self.stderr_chunk = b""

    def state(self):
        return self._state

    def setProcessEnvironment(self, _environment):
        pass

    def setWorkingDirectory(self, _working_directory):
        pass

    def start(self, _program, arguments):
        self._state = QProcess.ProcessState.Running
        output_argument = next(
            argument for argument in arguments if argument.startswith("output=")
        )
        self.output_path = Path(output_argument.removeprefix("output="))
        database = sqlite3.connect(self.output_path)
        try:
            database.execute(
                """
                CREATE TABLE records (
                    region TEXT NOT NULL,
                    province TEXT NOT NULL,
                    district TEXT NOT NULL,
                    payload TEXT NOT NULL
                )
                """
            )
        finally:
            database.close()

    def readAllStandardOutput(self):
        chunk = self.stdout_chunk
        self.stdout_chunk = b""
        return chunk

    def readAllStandardError(self):
        chunk = self.stderr_chunk
        self.stderr_chunk = b""
        return chunk

    def terminate(self):
        self.terminate_requested = True

    def kill(self):
        self.kill_requested = True

    def waitForFinished(self, _timeout):
        self._state = QProcess.ProcessState.NotRunning
        return True


def _replace_process(runner):
    process = FakeProcess()
    runner._process = process
    return process


def test_frozen_windows_runner_initializes_without_qprocess_modifier(
    monkeypatch, application
):
    monkeypatch.setattr(sys, "frozen", True, raising=False)
    monkeypatch.setattr(sys, "platform", "win32")

    runner = ScrapeRunner()
    try:
        assert runner._frozen
    finally:
        runner.shutdown()
    assert application is not None


def test_runner_includes_failed_pages_in_progress_message(application):
    runner = ScrapeRunner()
    progress = []
    runner.progressChanged.connect(
        lambda received, total, pages, message: progress.append(
            (received, total, pages, message)
        )
    )

    runner._update_progress("SCRAPE_PROGRESS=50/100 UNIQUE=48 PAGES=1 FAILED=1")

    assert progress == [
        (
            50,
            100,
            1,
            ("50 de 100 resultados · 48 colegios únicos · 1 página · 1 página fallida"),
        )
    ]
    runner.shutdown()
    assert application is not None


def test_runner_bounds_process_log_and_partial_progress_buffer(application):
    runner = ScrapeRunner()
    process = _replace_process(runner)
    process.stdout_chunk = b"x" * (
        MAX_PROCESS_LOG_CHARS + MAX_PROGRESS_LINE_CHARS + 100
    )

    runner._read_process_output()

    assert len(runner._process_log) <= MAX_PROCESS_LOG_CHARS
    assert len(runner._progress_buffers["stdout"]) <= MAX_PROGRESS_LINE_CHARS
    runner.shutdown()
    assert application is not None


def test_runner_completes_scrape_without_exporting_files(tmp_path, application):
    output_path = tmp_path / "schools.xlsx"
    android_path = android_data_path(output_path)
    output_path.write_bytes(b"previous workbook")
    android_path.write_bytes(b"previous EduScale data")
    runner = ScrapeRunner()
    process = _replace_process(runner)
    completed = []
    runner.completed.connect(lambda *args: completed.append(args))

    try:
        runner.start(
            output_path,
            [SchoolFilters(management=("publica",), level=("inicial",))],
        )
        staging_directory = runner._staging_directory
        assert output_path.read_bytes() == b"previous workbook"
        assert android_path.read_bytes() == b"previous EduScale data"
        assert process.output_path is not None and process.output_path.is_file()

        process._state = QProcess.ProcessState.NotRunning
        runner._scrape_finished(0, QProcess.ExitStatus.NormalExit)

        assert output_path.read_bytes() == b"previous workbook"
        assert android_path.read_bytes() == b"previous EduScale data"
        assert staging_directory is not None and staging_directory.is_dir()
        assert process.output_path is not None and process.output_path.is_file()
        assert completed == [(str(process.output_path), 0, 0, 0)]
    finally:
        runner.shutdown()
    assert application is not None


def test_runner_cancels_without_replacing_existing_excel(tmp_path, application):
    output_path = tmp_path / "schools.xlsx"
    android_path = android_data_path(output_path)
    output_path.write_bytes(b"previous workbook")
    android_path.write_bytes(b"previous EduScale data")
    runner = ScrapeRunner()
    process = _replace_process(runner)
    cancelled = []
    runner.cancelled.connect(lambda: cancelled.append(True))

    try:
        runner.start(
            output_path,
            [SchoolFilters(management=("publica",), level=("inicial",))],
        )
        staging_directory = runner._staging_directory

        runner.cancel()
        assert process.terminate_requested
        assert not process.kill_requested
        runner._force_stop_process()
        assert process.kill_requested
        process._state = QProcess.ProcessState.NotRunning
        runner._scrape_finished(-1, QProcess.ExitStatus.CrashExit)

        assert cancelled == [True]
        assert output_path.read_bytes() == b"previous workbook"
        assert android_path.read_bytes() == b"previous EduScale data"
        assert staging_directory is not None and not staging_directory.exists()
    finally:
        runner.shutdown()
    assert application is not None


def test_runner_rejects_missing_dataset_without_replacing_exports(
    tmp_path, application
):
    output_path = tmp_path / "schools.xlsx"
    android_path = android_data_path(output_path)
    output_path.write_bytes(b"previous workbook")
    android_path.write_bytes(b"previous EduScale data")
    runner = ScrapeRunner()
    process = _replace_process(runner)
    failures = []
    runner.failed.connect(lambda title, details: failures.append((title, details)))

    try:
        runner.start(
            output_path,
            [SchoolFilters(management=("publica",), level=("inicial",))],
        )
        assert runner._staging_path is not None
        runner._staging_path.unlink()
        process._state = QProcess.ProcessState.NotRunning
        runner._scrape_finished(0, QProcess.ExitStatus.NormalExit)

        assert failures and failures[0][0] == "No se guardaron los datos"
        assert output_path.read_bytes() == b"previous workbook"
        assert android_path.read_bytes() == b"previous EduScale data"
    finally:
        runner.shutdown()
    assert application is not None
