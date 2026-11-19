import json
import logging

from PySide6.QtCore import qCritical, qInstallMessageHandler, qWarning

from eduescale.gui.adapters.qt_logging import log_qt_message
from eduescale.infrastructure.runtime.logging_config import configure_logging


def test_qt_messages_are_forwarded_to_python_logging(caplog, tmp_path):
    logger = logging.getLogger("eduescale")
    previous_handlers = tuple(logger.handlers)
    previous_level = logger.level
    log_path = configure_logging(tmp_path)
    previous_handler = qInstallMessageHandler(log_qt_message)
    try:
        with caplog.at_level(logging.WARNING, logger="eduescale.qt"):
            qWarning("QML warning")
            qCritical("Qt critical error")
    finally:
        qInstallMessageHandler(previous_handler)
        for handler in tuple(logger.handlers):
            if handler not in previous_handlers:
                logger.removeHandler(handler)
                handler.close()
        logger.setLevel(previous_level)

    assert [(record.levelno, record.message) for record in caplog.records] == [
        (logging.WARNING, "QML warning"),
        (logging.ERROR, "Qt critical error"),
    ]
    assert all(record.qt_category for record in caplog.records)
    entries = [
        json.loads(line) for line in log_path.read_text(encoding="utf-8").splitlines()
    ]
    assert [(entry["level"], entry["event"]) for entry in entries] == [
        ("warning", "QML warning"),
        ("error", "Qt critical error"),
    ]
