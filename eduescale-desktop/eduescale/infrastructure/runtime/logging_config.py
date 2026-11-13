"""Configure rotating log files for the application."""

import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path

from eduescale.infrastructure.runtime.paths import application_data_directory

MAX_LOG_BYTES = 5 * 1024 * 1024
LOG_BACKUP_COUNT = 3


def _default_log_directory() -> Path:
    return application_data_directory() / "logs"


def configure_logging(directory: Path | None = None) -> Path:
    log_directory = _default_log_directory() if directory is None else directory
    log_directory.mkdir(parents=True, exist_ok=True)
    log_path = log_directory / "eduescale.log"

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
            logging.Formatter(
                "%(asctime)s %(levelname)s %(name)s: %(message)s",
                datefmt="%Y-%m-%d %H:%M:%S",
            )
        )
        logger.addHandler(handler)
    return log_path
