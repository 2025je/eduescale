import sqlite3
import sys
from pathlib import Path

import pytest
from PySide6.QtCore import QCoreApplication, QProcess

from eduescale.application.export_policy import android_data_path
from eduescale.domain.schools import SchoolFilters
from eduescale.gui.adapters import scrape_runner as scrape_runner_module
from eduescale.gui.adapters.scrape_runner import (
    DATABASE_SCRAPE_TIMEOUT_MS,
    MAX_PROCESS_LOG_CHARS,
    MAX_PROGRESS_LINE_CHARS,
    SCRAPE_TIMEOUT_MS,
    ScrapeRunner,
)
from eduescale.gui.contracts.ports import (
    OfflineDatabaseDownloadPort,
    ScrapeRunnerPort,
)
from eduescale.gui.services.progress import ProgressMetrics


@pytest.fixture
def application():
    return QCoreApplication.instance() or QCoreApplication([])


class FakeProcess:
    def __init__(self):
        self._state = QProcess.ProcessState.NotRunning
        self.program = None
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

    def start(self, program, arguments):
        self.program = program
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
    mocker, application
):
    mocker.patch.object(sys, "frozen", True, create=True)
    mocker.patch.object(sys, "platform", "win32")

    runner = ScrapeRunner()
    try:
        assert runner._frozen
    finally:
        runner.shutdown()
    assert application is not None


def test_scrape_runner_satisfies_split_runner_ports(application):
    runner = ScrapeRunner()

    try:
        assert isinstance(runner, ScrapeRunnerPort)
        assert isinstance(runner, OfflineDatabaseDownloadPort)
    finally:
        runner.shutdown()
    assert application is not None


def test_frozen_windows_runner_uses_onedir_scraper(tmp_path, mocker, application):
    distribution_directory = tmp_path / "dist"
    worker_directory = distribution_directory / "eduescale-scraper"
    worker_directory.mkdir(parents=True)
    gui_executable = distribution_directory / "eduescale-gui.exe"
    gui_executable.touch()
    worker_executable = worker_directory / "eduescale-scraper.exe"
    worker_executable.touch()
    mocker.patch.object(sys, "frozen", True, create=True)
    mocker.patch.object(sys, "platform", "win32")
    mocker.patch.object(sys, "executable", str(gui_executable))

    runner = ScrapeRunner()
    process = _replace_process(runner)

    try:
        runner.start(
            tmp_path / "schools.xlsx",
            [SchoolFilters(management=("publica",), level=("primaria",))],
            purpose="online_search",
        )

        assert process.program == str(worker_executable)
    finally:
        runner.shutdown()
    assert application is not None


def test_runner_emits_structured_progress_metrics(application):
    runner = ScrapeRunner()
    progress = []
    runner.progressChanged.connect(progress.append)

    runner._update_progress("SCRAPE_PROGRESS=50/100 UNIQUE=48 PAGES=1 FAILED=1")

    assert progress == [
        ProgressMetrics(
            received=50,
            total=100,
            unique=48,
            pages=1,
            failed=1,
        )
    ]
    runner.shutdown()
    assert application is not None


def test_runner_emits_structured_live_school_batches(application):
    runner = ScrapeRunner()
    received = []
    runner.schoolsReceived.connect(received.extend)

    runner._update_progress(
        'INFO: SCRAPE_SCHOOLS=[{"nombre":"Colegio SCRAPE_TOTAL=999"}]'
    )

    assert received == [{"nombre": "Colegio SCRAPE_TOTAL=999"}]
    assert runner._scrape_total is None
    runner.shutdown()
    assert application is not None


def test_database_update_uses_purpose_for_timeout_and_specific_message(
    tmp_path,
    application,
):
    class FakeCheckpointGateway:
        def prepare(self, output_path, filters):
            return tmp_path / "checkpoint", "fingerprint"

        def complete(self, checkpoint_directory):
            return None

    runner = ScrapeRunner(FakeCheckpointGateway())
    process = _replace_process(runner)
    failures = []
    runner.failed.connect(lambda title, details: failures.append((title, details)))

    try:
        runner.start(
            tmp_path / "offline.xlsx",
            [SchoolFilters()],
            purpose="database_update",
        )
        assert runner._is_database_update
        assert runner._scrape_timeout_ms == DATABASE_SCRAPE_TIMEOUT_MS

        runner._scrape_timed_out = True
        process._state = QProcess.ProcessState.NotRunning
        runner._scrape_finished(-1, QProcess.ExitStatus.CrashExit)

        assert failures == [
            (
                "Descarga detenida",
                "La descarga completa de la base superó el límite de 60 minutos.",
            )
        ]
    finally:
        runner.shutdown()
    assert application is not None


def test_online_search_does_not_infer_database_update_from_extension(
    tmp_path,
    application,
):
    class FakeCheckpointGateway:
        def __init__(self):
            self.prepare_requests = []

        def prepare(self, output_path, filters):
            self.prepare_requests.append((output_path, filters))
            return tmp_path / "checkpoint", "fingerprint"

        def complete(self, _checkpoint_directory):
            return None

    checkpoint_gateway = FakeCheckpointGateway()
    runner = ScrapeRunner(checkpoint_gateway)
    _replace_process(runner)

    try:
        runner.start(
            tmp_path / "schools.sqlite3",
            [SchoolFilters()],
            purpose="online_search",
        )

        assert not runner._is_database_update
        assert runner._scrape_timeout_ms == SCRAPE_TIMEOUT_MS
        assert checkpoint_gateway.prepare_requests == []
    finally:
        runner.shutdown()
    assert application is not None


def test_cleanup_retries_transient_windows_file_locks(
    tmp_path,
    mocker,
    application,
):
    runner = ScrapeRunner()
    staging_directory = tmp_path / ".eduescale-locked"
    staging_directory.mkdir()
    runner._staging_directory = staging_directory
    runner._staging_path = staging_directory / "colegios.sqlite3"
    original_rmtree = scrape_runner_module.shutil.rmtree
    attempts = 0
    delays = []

    def rmtree_with_transient_lock(path):
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            error = OSError("file is being used")
            error.winerror = 32
            raise error
        original_rmtree(path)

    mocker.patch.object(
        scrape_runner_module.shutil, "rmtree", rmtree_with_transient_lock
    )
    mocker.patch.object(scrape_runner_module.time, "sleep", delays.append)

    try:
        assert runner._cleanup_staging() is None
        assert attempts == 2
        assert delays == [0.1]
        assert not staging_directory.exists()
    finally:
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
    android_path.write_bytes(b"previous EduEscale data")
    runner = ScrapeRunner()
    process = _replace_process(runner)
    completed = []
    runner.completed.connect(lambda *args: completed.append(args))

    try:
        runner.start(
            output_path,
            [SchoolFilters(management=("publica",), level=("primaria",))],
            purpose="online_search",
        )
        staging_directory = runner._staging_directory
        assert output_path.read_bytes() == b"previous workbook"
        assert android_path.read_bytes() == b"previous EduEscale data"
        assert process.output_path is not None and process.output_path.is_file()

        process._state = QProcess.ProcessState.NotRunning
        runner._scrape_finished(0, QProcess.ExitStatus.NormalExit)

        assert output_path.read_bytes() == b"previous workbook"
        assert android_path.read_bytes() == b"previous EduEscale data"
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
    android_path.write_bytes(b"previous EduEscale data")
    runner = ScrapeRunner()
    process = _replace_process(runner)
    cancelled = []
    runner.cancelled.connect(lambda: cancelled.append(True))

    try:
        runner.start(
            output_path,
            [SchoolFilters(management=("publica",), level=("primaria",))],
            purpose="online_search",
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
        assert android_path.read_bytes() == b"previous EduEscale data"
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
    android_path.write_bytes(b"previous EduEscale data")
    runner = ScrapeRunner()
    process = _replace_process(runner)
    failures = []
    runner.failed.connect(lambda title, details: failures.append((title, details)))

    try:
        runner.start(
            output_path,
            [SchoolFilters(management=("publica",), level=("primaria",))],
            purpose="online_search",
        )
        assert runner._staging_path is not None
        runner._staging_path.unlink()
        process._state = QProcess.ProcessState.NotRunning
        runner._scrape_finished(0, QProcess.ExitStatus.NormalExit)

        assert failures and failures[0][0] == "No se guardaron los datos"
        assert output_path.read_bytes() == b"previous workbook"
        assert android_path.read_bytes() == b"previous EduEscale data"
    finally:
        runner.shutdown()
    assert application is not None
