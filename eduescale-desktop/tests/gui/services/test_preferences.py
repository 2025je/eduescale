from PySide6.QtCore import QCoreApplication, QSettings

from eduescale.gui.services.preferences import QSettingsDesktopPreferences


def test_preferences_read_defaults_and_legacy_theme(tmp_path):
    application = QCoreApplication.instance() or QCoreApplication([])
    settings = QSettings(
        str(tmp_path / "settings.ini"),
        QSettings.Format.IniFormat,
    )
    settings.setValue("appearance/darkTheme", True)
    preferences = QSettingsDesktopPreferences(settings)

    assert preferences.theme_mode() == "dark"
    assert preferences.data_source() == "online"
    assert preferences.offline_database_path() is None
    assert preferences.window_size() == (1280, 760)
    assert application is not None


def test_preferences_persist_values_and_clamp_window_size(tmp_path):
    application = QCoreApplication.instance() or QCoreApplication([])
    settings = QSettings(
        str(tmp_path / "settings.ini"),
        QSettings.Format.IniFormat,
    )
    preferences = QSettingsDesktopPreferences(settings)
    database_path = tmp_path / "schools.sqlite3"

    assert preferences.save_theme_mode("light")
    assert preferences.save_data_source("offline")
    assert preferences.save_offline_database_selection(database_path)
    assert preferences.save_window_size(400, 500)
    assert preferences.save_last_export_directory(tmp_path)

    assert preferences.theme_mode() == "light"
    assert preferences.data_source() == "offline"
    assert preferences.offline_database_path() == database_path
    assert preferences.window_size() == (560, 600)
    assert preferences.last_export_directory() == tmp_path
    assert application is not None
