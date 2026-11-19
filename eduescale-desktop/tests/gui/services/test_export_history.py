import json

import pytest
from PySide6.QtCore import QCoreApplication, QSettings

from eduescale.gui.services.export_history import QSettingsExportHistoryStore


@pytest.fixture
def settings(tmp_path):
    application = QCoreApplication.instance() or QCoreApplication([])
    result = QSettings(
        str(tmp_path / "settings.ini"),
        QSettings.Format.IniFormat,
    )
    yield result
    assert application is not None


def test_export_history_store_round_trips_entries(settings):
    store = QSettingsExportHistoryStore(settings)
    entries = [
        {
            "timestamp": "2026-01-01T10:00:00+00:00",
            "format": "Excel",
            "file": "schools.xlsx",
            "status": "Completada",
            "filters": "Lima · primaria",
        }
    ]

    assert store.load() == ([], None)
    assert store.save(entries)
    assert store.load() == (entries, None)


@pytest.mark.parametrize(
    ("value", "expected_error"),
    [
        (["not", "json"], "unexpected_value_type"),
        ("{", "invalid_json"),
        ("{}", "not_a_list"),
        (json.dumps([{"file": "schools.xlsx"}]), "invalid_entry"),
    ],
)
def test_export_history_store_rejects_invalid_values(
    settings,
    value,
    expected_error,
):
    settings.setValue("export/history", value)
    entries, error = QSettingsExportHistoryStore(settings).load()

    assert entries == []
    assert error == expected_error
