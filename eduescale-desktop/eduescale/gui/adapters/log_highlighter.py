import re

from PySide6.QtCore import QObject
from PySide6.QtGui import (
    QColor,
    QFont,
    QSyntaxHighlighter,
    QTextCharFormat,
    QTextDocument,
)

_HEADER = re.compile(
    r"^(?:(?P<timestamp>\d{4}-\d{2}-\d{2}(?:[T ][^ ]+)?) · )?"
    r"(?P<level>DEBUG|INFO|WARNING|ERROR|CRITICAL|FATAL)"
    r"(?: · (?P<logger>.+))?$"
)
_DETAIL = re.compile(r"^  (?P<key>[^:\n]+):")


class LogHighlighter(QSyntaxHighlighter):
    def __init__(self, parent: QObject):
        super().__init__(parent)
        self._formats: dict[str, QTextCharFormat] = {}
        self._enabled = False

    def configure(
        self, document: QTextDocument, colors: dict[str, str], enabled: bool
    ) -> None:
        self._enabled = enabled
        self._formats = {}
        for name, color in colors.items():
            text_format = QTextCharFormat()
            text_format.setForeground(QColor(color))
            if name in {"debug", "info", "warning", "error", "critical"}:
                text_format.setFontWeight(QFont.Weight.DemiBold)
            self._formats[name] = text_format
        if self.document() != document:
            self.setDocument(document)
        self.rehighlight()

    def highlightBlock(self, text: str) -> None:
        if not self._enabled:
            return
        header = _HEADER.match(text)
        if header:
            self._highlight_group(text, header, "timestamp", "timestamp")
            level = header.group("level").lower()
            self._highlight_group(
                text, header, "level", "critical" if level == "fatal" else level
            )
            self._highlight_group(text, header, "logger", "source")
            return
        detail = _DETAIL.match(text)
        if detail:
            self._highlight_group(text, detail, "key", "detail")

    def _highlight_group(
        self, text: str, match: re.Match[str], group: str, color: str
    ) -> None:
        start, end = match.span(group)
        if start < 0:
            return
        utf16_start = len(text[:start].encode("utf-16-le")) // 2
        utf16_length = len(text[start:end].encode("utf-16-le")) // 2
        self.setFormat(utf16_start, utf16_length, self._formats[color])
