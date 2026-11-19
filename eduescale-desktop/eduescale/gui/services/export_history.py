import json
import logging
from typing import Literal, Protocol, TypedDict

from PySide6.QtCore import QSettings

LOGGER = logging.getLogger(__name__)
MAX_EXPORT_HISTORY_ENTRIES = 50

ExportHistoryReadError = Literal[
    "unexpected_value_type",
    "invalid_json",
    "not_a_list",
    "invalid_entry",
]


class ExportHistoryEntry(TypedDict):
    timestamp: str
    format: str
    file: str
    status: str
    filters: str


class ExportHistoryStorePort(Protocol):
    def load(
        self,
    ) -> tuple[list[ExportHistoryEntry], ExportHistoryReadError | None]: ...

    def save(self, entries: list[ExportHistoryEntry]) -> bool: ...


class QSettingsExportHistoryStore:
    def __init__(self, settings: QSettings):
        self._settings = settings

    def load(
        self,
    ) -> tuple[list[ExportHistoryEntry], ExportHistoryReadError | None]:
        value = self._settings.value("export/history", "[]")
        if not isinstance(value, str):
            LOGGER.error("Saved export history has an unexpected value type.")
            return [], "unexpected_value_type"
        try:
            history = json.loads(value)
        except json.JSONDecodeError as error:
            LOGGER.error("Saved export history is invalid JSON: %s", error)
            return [], "invalid_json"
        if not isinstance(history, list):
            LOGGER.error("Saved export history is not a list.")
            return [], "not_a_list"

        entries = []
        keys = {"timestamp", "format", "file", "status", "filters"}
        for entry in history[:MAX_EXPORT_HISTORY_ENTRIES]:
            if (
                not isinstance(entry, dict)
                or set(entry) != keys
                or any(not isinstance(entry[key], str) for key in keys)
            ):
                LOGGER.error("Ignoring malformed saved export history entry.")
                return [], "invalid_entry"
            entries.append(entry)
        return entries, None

    def save(self, entries: list[ExportHistoryEntry]) -> bool:
        self._settings.setValue(
            "export/history",
            json.dumps(entries, ensure_ascii=False),
        )
        self._settings.sync()
        return self._settings.status() == QSettings.Status.NoError
