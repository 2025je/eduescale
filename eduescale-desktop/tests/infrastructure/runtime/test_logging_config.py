import json
import logging
from logging.handlers import RotatingFileHandler

from eduescale.infrastructure.runtime import logging_config
from eduescale.infrastructure.runtime.logging_config import (
    LOG_BACKUP_COUNT,
    MAX_LOG_BYTES,
    configure_logging,
)


def test_configures_a_rotating_file_handler(tmp_path):
    logger = logging.getLogger("eduescale")
    previous_handlers = tuple(logger.handlers)
    previous_level = logger.level
    log_path = tmp_path / "logs" / "eduescale.log"

    try:
        assert configure_logging(log_path.parent) == log_path
        logger.info("startup test message", extra={"operation": "search"})
        try:
            raise ValueError("example failure")
        except ValueError:
            logger.exception("operation failed")
        for handler in logger.handlers:
            handler.flush()

        entries = [
            json.loads(line)
            for line in log_path.read_text(encoding="utf-8").splitlines()
        ]
        assert entries[0]["event"] == "startup test message"
        assert entries[0]["logger"] == "eduescale"
        assert entries[0]["level"] == "info"
        assert entries[0]["operation"] == "search"
        assert entries[0]["timestamp"].endswith("Z")
        assert entries[1]["level"] == "error"
        assert "ValueError: example failure" in entries[1]["exception"]
        matching_handlers = [
            handler
            for handler in logger.handlers
            if isinstance(handler, RotatingFileHandler)
            and handler.baseFilename == str(log_path.resolve())
        ]
        assert len(matching_handlers) == 1
        assert matching_handlers[0].maxBytes == MAX_LOG_BYTES
        assert matching_handlers[0].backupCount == LOG_BACKUP_COUNT
        assert configure_logging(log_path.parent) == log_path
        assert (
            len(
                [
                    handler
                    for handler in logger.handlers
                    if isinstance(handler, RotatingFileHandler)
                    and handler.baseFilename == str(log_path.resolve())
                ]
            )
            == 1
        )
    finally:
        for handler in tuple(logger.handlers):
            if handler not in previous_handlers:
                logger.removeHandler(handler)
                handler.close()
        logger.setLevel(previous_level)


def test_log_rotation_preserves_json_entries(tmp_path, mocker):
    logger = logging.getLogger("eduescale")
    previous_handlers = tuple(logger.handlers)
    previous_level = logger.level
    mocker.patch.object(logging_config, "MAX_LOG_BYTES", 200)

    try:
        log_path = configure_logging(tmp_path)
        logger.info("first " + "x" * 80)
        logger.info("second " + "y" * 80)

        assert json.loads(log_path.read_text(encoding="utf-8"))["event"].startswith(
            "second "
        )
        backup = log_path.with_name(log_path.name + ".1")
        assert json.loads(backup.read_text(encoding="utf-8"))["event"].startswith(
            "first "
        )
    finally:
        for handler in tuple(logger.handlers):
            if handler not in previous_handlers:
                logger.removeHandler(handler)
                handler.close()
        logger.setLevel(previous_level)
