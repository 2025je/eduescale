import json
import logging
import os
import re
from pathlib import Path
from typing import BinaryIO

from PySide6.QtCore import Property, QObject, Signal, Slot
from PySide6.QtQuick import QQuickTextDocument

from eduescale.gui.adapters.log_highlighter import LogHighlighter
from eduescale.infrastructure.runtime.logging_config import (
    LOG_BACKUP_COUNT,
    LOG_FILE_NAME,
)

LOGGER = logging.getLogger(__name__)
LOG_PAGE_BYTES = 16 * 1024
LINE_LOOKAHEAD_BYTES = LOG_PAGE_BYTES // 2 - 1
_LEGACY_LOG_LINE = re.compile(
    r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) "
    r"(DEBUG|INFO|WARNING|ERROR|CRITICAL) ([^:\s]+): (.*)$"
)


def _character_boundary(stream: BinaryIO, offset: int) -> int:
    for _ in range(3):
        if offset == 0:
            break
        stream.seek(offset)
        byte = stream.read(1)
        if not byte or byte[0] & 0xC0 != 0x80:
            break
        offset -= 1
    if offset > 0:
        stream.seek(offset - 1)
        if stream.read(2) == b"\r\n":
            offset -= 1
    return offset


def _page_boundary(stream: BinaryIO, offset: int, size: int) -> int:
    if offset <= 0 or offset >= size:
        return min(offset, size)
    stream.seek(offset - 1)
    if stream.read(1) == b"\n":
        return offset
    stream.seek(offset)
    following = stream.read(min(LINE_LOOKAHEAD_BYTES, size - offset))
    line_end = following.find(b"\n")
    if line_end >= 0 and offset + line_end + 1 < size:
        return offset + line_end + 1
    lookbehind = min(LINE_LOOKAHEAD_BYTES, offset)
    stream.seek(offset - lookbehind)
    previous = stream.read(lookbehind)
    line_end = previous.rfind(b"\n")
    if line_end >= 0:
        return offset - lookbehind + line_end + 1
    return _character_boundary(stream, offset)


def _format_log_text(content: str) -> str:
    result = []
    for line in content.splitlines(keepends=True):
        if not line.lstrip().startswith("{"):
            legacy = _LEGACY_LOG_LINE.match(line.rstrip("\r\n"))
            if legacy:
                timestamp, level, logger, event = legacy.groups()
                result.append(f"{timestamp} · {level} · {logger}\n{event}\n\n")
            else:
                result.append(line)
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError:
            result.append(line)
            continue
        if not isinstance(record, dict) or "event" not in record:
            result.append(line)
            continue
        header = []
        for key in ("timestamp", "level", "logger"):
            if key in record:
                value = str(record[key])
                header.append(value.upper() if key == "level" else value)
        event = record["event"]
        details = [
            event if isinstance(event, str) else json.dumps(event, ensure_ascii=False)
        ]
        for key, value in record.items():
            if key not in {"timestamp", "level", "logger", "event"}:
                rendered = (
                    value
                    if isinstance(value, str)
                    else json.dumps(value, ensure_ascii=False)
                )
                details.append(f"  {key}: {rendered}")
        if header:
            details.insert(0, " · ".join(header))
        result.append("\n".join(details) + "\n\n")
    return "".join(result)


class LogViewerViewModel(QObject):
    contentChanged = Signal()

    def __init__(self, directory: Path, parent: QObject | None = None):
        super().__init__(parent)
        self._directory = directory
        self._files = [LOG_FILE_NAME] + [
            f"{LOG_FILE_NAME}.{index}" for index in range(1, LOG_BACKUP_COUNT + 1)
        ]
        self._selected_index = 0
        self._signature: tuple[int, int, int, int, int] | None = None
        self._content = ""
        self._raw_content = ""
        self._error = ""
        self._page_index = 0
        self._page_count = 1
        self._follow_latest = True
        self._highlighter = LogHighlighter(self)

    @Slot(QObject, "QVariantMap", bool)
    def configureHighlighting(
        self, text_document: QObject, colors: dict[str, str], enabled: bool
    ) -> None:
        if not isinstance(text_document, QQuickTextDocument):
            LOGGER.error("Cannot highlight logs: invalid Qt Quick text document.")
            return
        document = text_document.textDocument()
        if document is None:
            LOGGER.error("Cannot highlight logs: Qt text document is unavailable.")
            return
        self._highlighter.configure(document, colors, enabled)

    @Property(list, constant=True)
    def files(self) -> list[str]:
        return self._files

    @Property(str, notify=contentChanged)
    def content(self) -> str:
        return self._content

    @Property(str, notify=contentChanged)
    def rawContent(self) -> str:
        return self._raw_content

    @Property(str, notify=contentChanged)
    def error(self) -> str:
        return self._error

    @Property(int, notify=contentChanged)
    def pageNumber(self) -> int:
        return self._page_index + 1

    @Property(int, notify=contentChanged)
    def pageCount(self) -> int:
        return self._page_count

    @Property(bool, notify=contentChanged)
    def canPrevious(self) -> bool:
        return self._page_index > 0

    @Property(bool, notify=contentChanged)
    def canNext(self) -> bool:
        return self._page_index + 1 < self._page_count

    @Slot(int)
    def selectFile(self, index: int) -> None:
        if not 0 <= index < len(self._files):
            LOGGER.warning("Invalid log file selection: %s", index)
            return
        if index != self._selected_index:
            self._selected_index = index
            self._signature = None
            self._follow_latest = True
        self.refresh()

    @Slot()
    def previousPage(self) -> None:
        if self.canPrevious:
            self.goToPage(self._page_index)

    @Slot()
    def nextPage(self) -> None:
        if self.canNext:
            self.goToPage(self._page_index + 2)

    @Slot(int)
    def goToPage(self, number: int) -> None:
        if not 1 <= number <= self._page_count:
            LOGGER.warning("Invalid log page selection: %s", number)
            return
        self._follow_latest = number == self._page_count
        self._load_page(number - 1)

    @Slot()
    def refresh(self) -> None:
        self._load_page(self._page_index)

    def _load_page(self, requested_index: int) -> None:
        path = self._directory / self._files[self._selected_index]
        try:
            with path.open("rb") as stream:
                stat = os.fstat(stream.fileno())
                signature = (
                    stat.st_dev,
                    stat.st_ino,
                    stat.st_size,
                    stat.st_mtime_ns,
                    stat.st_ctime_ns,
                )
                if (
                    signature == self._signature
                    and requested_index == self._page_index
                    and not self._error
                ):
                    return
                if self._signature is not None and (
                    signature[:2] != self._signature[:2]
                    or signature[2] < self._signature[2]
                ):
                    self._follow_latest = True
                page_count = max(
                    1, (stat.st_size + LOG_PAGE_BYTES - 1) // LOG_PAGE_BYTES
                )
                page_index = (
                    page_count - 1
                    if self._follow_latest
                    else min(requested_index, page_count - 1)
                )
                start = _page_boundary(
                    stream, page_index * LOG_PAGE_BYTES, stat.st_size
                )
                end = _page_boundary(
                    stream, (page_index + 1) * LOG_PAGE_BYTES, stat.st_size
                )
                stream.seek(start)
                content = (
                    stream.read(end - start)
                    .decode("utf-8", errors="replace")
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                )
        except FileNotFoundError:
            self._signature = None
            self._follow_latest = True
            self._set_content("", "", 0, 1)
            return
        except OSError as error:
            self._signature = None
            message = f"No se pudo leer el registro: {error}"
            if message != self._error:
                LOGGER.warning("Could not read log file %s: %s", path, error)
            self._set_content("", message, 0, 1)
            return
        self._signature = signature
        self._set_content(content, "", page_index, page_count)

    def _set_content(self, content: str, error: str, index: int, count: int) -> None:
        if (content, error, index, count) != (
            self._raw_content,
            self._error,
            self._page_index,
            self._page_count,
        ):
            self._raw_content = content
            self._content = _format_log_text(content)
            self._error = error
            self._page_index = index
            self._page_count = count
            self.contentChanged.emit()
