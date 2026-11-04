import sys

import pytest
from PySide6.QtCore import QCoreApplication

from eduescale.gui.scrape_runner import ScrapeRunner


@pytest.fixture
def application():
    return QCoreApplication.instance() or QCoreApplication([])


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
