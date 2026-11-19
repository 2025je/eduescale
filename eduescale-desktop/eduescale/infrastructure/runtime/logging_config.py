"""Configure rotating log files for the application."""

import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path

import structlog

from eduescale.infrastructure.runtime.paths import application_data_directory

MAX_LOG_BYTES = 5 * 1024 * 1024
LOG_BACKUP_COUNT = 3
LOG_FILE_NAME = "eduescale.log"


def _default_log_directory() -> Path:
    return application_data_directory() / "logs"


def configure_logging(directory: Path | None = None) -> Path:
    log_directory = _default_log_directory() if directory is None else directory
    log_directory.mkdir(parents=True, exist_ok=True)
    log_path = log_directory / LOG_FILE_NAME

    logger = logging.getLogger("eduescale")
    logger.setLevel(logging.INFO)
    has_handler = any(
        isinstance(handler, RotatingFileHandler)
        and Path(handler.baseFilename) == log_path.resolve()
        for handler in logger.handlers
    )
    if not has_handler:
        handler = RotatingFileHandler(
            log_path,
            maxBytes=MAX_LOG_BYTES,
            backupCount=LOG_BACKUP_COUNT,
            encoding="utf-8",
        )
        handler.setFormatter(
            structlog.stdlib.ProcessorFormatter(
                foreign_pre_chain=[
                    structlog.stdlib.add_logger_name,
                    structlog.stdlib.add_log_level,
                    structlog.processors.TimeStamper(fmt="iso", utc=True),
                    structlog.stdlib.ExtraAdder(),
                ],
                processors=[
                    structlog.stdlib.ProcessorFormatter.remove_processors_meta,
                    structlog.processors.format_exc_info,
                    structlog.processors.JSONRenderer(ensure_ascii=False),
                ],
            )
        )
        logger.addHandler(handler)
    return log_path
