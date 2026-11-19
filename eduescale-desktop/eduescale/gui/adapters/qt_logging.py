import logging

from PySide6.QtCore import QMessageLogContext, QtMsgType

LOGGER = logging.getLogger("eduescale.qt")

_LEVELS = {
    QtMsgType.QtDebugMsg: logging.DEBUG,
    QtMsgType.QtInfoMsg: logging.INFO,
    QtMsgType.QtWarningMsg: logging.WARNING,
    QtMsgType.QtCriticalMsg: logging.ERROR,
    QtMsgType.QtFatalMsg: logging.CRITICAL,
}


def log_qt_message(
    message_type: QtMsgType, context: QMessageLogContext, message: str
) -> None:
    LOGGER.log(
        _LEVELS[message_type],
        "%s",
        message,
        extra={"qt_category": context.category or "default"},
    )
