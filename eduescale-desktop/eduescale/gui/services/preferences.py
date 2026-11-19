import logging
from pathlib import Path
from typing import Protocol

from PySide6.QtCore import QSettings

from eduescale.gui.contracts.ports import DataSource

LOGGER = logging.getLogger(__name__)


class DesktopPreferencesPort(Protocol):
    def theme_mode(self) -> str: ...

    def save_theme_mode(self, mode: str) -> bool: ...

    def data_source(self) -> DataSource: ...

    def save_data_source(self, source: DataSource) -> bool: ...

    def offline_database_path(self) -> Path | None: ...

    def save_offline_database_path(self, path: Path) -> bool: ...

    def save_offline_database_selection(self, path: Path) -> bool: ...

    def last_export_directory(self) -> Path: ...

    def save_last_export_directory(self, directory: Path) -> bool: ...

    def window_size(self) -> tuple[int, int]: ...

    def save_window_size(self, width: int, height: int) -> bool: ...


class QSettingsDesktopPreferences:
    def __init__(self, settings: QSettings):
        self._settings = settings

    def theme_mode(self) -> str:
        theme_mode = self._settings.value("appearance/themeMode")
        if theme_mode is None:
            if self._settings.contains("appearance/darkTheme"):
                return (
                    "dark"
                    if self._settings.value(
                        "appearance/darkTheme",
                        False,
                        type=bool,
                    )
                    else "light"
                )
            return "system"
        if theme_mode in ("system", "light", "dark"):
            return theme_mode
        LOGGER.error("Ignoring invalid saved theme mode %r.", theme_mode)
        return "system"

    def save_theme_mode(self, mode: str) -> bool:
        self._settings.setValue("appearance/themeMode", mode)
        return self._sync()

    def data_source(self) -> DataSource:
        source = self._settings.value("data/source", "online")
        if source == "online":
            return "online"
        if source == "offline":
            return "offline"
        LOGGER.error("Ignoring invalid saved data source %r.", source)
        return "online"

    def save_data_source(self, source: DataSource) -> bool:
        self._settings.setValue("data/source", source)
        return self._sync()

    def offline_database_path(self) -> Path | None:
        value = self._settings.value("data/offlineDatabasePath")
        if value is None:
            return None
        if not isinstance(value, str) or not value:
            LOGGER.error("Ignoring invalid saved offline database path.")
            return None
        return Path(value)

    def save_offline_database_path(self, path: Path) -> bool:
        self._settings.setValue("data/offlineDatabasePath", str(path))
        return self._sync()

    def save_offline_database_selection(self, path: Path) -> bool:
        self._settings.setValue("data/source", "offline")
        self._settings.setValue("data/offlineDatabasePath", str(path))
        return self._sync()

    def last_export_directory(self) -> Path:
        value = self._settings.value("export/lastDirectory", str(Path.home()))
        if isinstance(value, str):
            directory = Path(value)
            if directory.is_dir():
                return directory
        return Path.home()

    def save_last_export_directory(self, directory: Path) -> bool:
        self._settings.setValue("export/lastDirectory", str(directory))
        return self._sync()

    def window_size(self) -> tuple[int, int]:
        return (
            self._window_dimension("window/width", 1280, 560),
            self._window_dimension("window/height", 760, 600),
        )

    def save_window_size(self, width: int, height: int) -> bool:
        self._settings.setValue("window/width", max(width, 560))
        self._settings.setValue("window/height", max(height, 600))
        return self._sync()

    def _window_dimension(self, key: str, default: int, minimum: int) -> int:
        value = self._settings.value(key, default)
        if not isinstance(value, (str, int, float)):
            return default
        try:
            dimension = int(value)
        except (TypeError, ValueError):
            return default
        return max(dimension, minimum)

    def _sync(self) -> bool:
        self._settings.sync()
        return self._settings.status() == QSettings.Status.NoError
