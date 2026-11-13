import logging
from logging.handlers import RotatingFileHandler

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
        logger.info("startup test message")
        for handler in logger.handlers:
            handler.flush()

        assert log_path.read_text(encoding="utf-8").endswith(
            "INFO eduescale: startup test message\n"
        )
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
