import logging
from pathlib import Path
from types import SimpleNamespace

import pytest

from eduescale.gui import application as app_module


@pytest.mark.parametrize("roots, expected_exit", [([object()], 0), ([], 1)])
def test_application_logs_startup_and_exit_or_load_failure(
    monkeypatch, caplog, roots, expected_exit
):
    calls = []
    app = SimpleNamespace(
        setOrganizationName=lambda value: None,
        setApplicationName=lambda value: None,
        setApplicationDisplayName=lambda value: None,
        exec=lambda: 0,
    )
    engine = SimpleNamespace(
        addImportPath=lambda path: None,
        setInitialProperties=lambda properties: None,
        load=lambda url: None,
        rootObjects=lambda: roots,
    )
    view_model = SimpleNamespace(shutdown=lambda: calls.append("shutdown"))
    monkeypatch.setattr(app_module, "configure_logging", lambda: Path("eduescale.log"))
    monkeypatch.setattr(app_module, "qInstallMessageHandler", lambda handler: None)
    monkeypatch.setattr(
        app_module, "QtWebEngineQuick", SimpleNamespace(initialize=lambda: None)
    )
    monkeypatch.setattr(
        app_module, "QQuickStyle", SimpleNamespace(setStyle=lambda _: None)
    )
    monkeypatch.setattr(app_module, "QGuiApplication", lambda _: app)
    monkeypatch.setattr(app_module, "configure_application_font", lambda _: True)
    monkeypatch.setattr(app_module, "configure_application_icon", lambda _: True)
    monkeypatch.setattr(app_module, "QQmlApplicationEngine", lambda: engine)

    with caplog.at_level(logging.INFO, logger="eduescale"):
        assert app_module.main(lambda: view_model) == expected_exit

    assert "Starting EduEscale" in caplog.text
    if roots:
        assert "EduEscale exited with code 0" in caplog.text
        assert calls == []
    else:
        assert "Could not load the main QML interface" in caplog.text
        assert calls == ["shutdown"]


def test_initialization_exception_is_logged_and_reraised(monkeypatch, caplog):
    handlers = []
    previous_handler = lambda *_: None
    monkeypatch.setattr(app_module, "configure_logging", lambda: Path("eduescale.log"))

    def install_handler(handler):
        handlers.append(handler)
        return previous_handler

    def initialize():
        raise RuntimeError("initialization failed")

    monkeypatch.setattr(app_module, "qInstallMessageHandler", install_handler)
    monkeypatch.setattr(
        app_module, "QtWebEngineQuick", SimpleNamespace(initialize=initialize)
    )
    with (
        caplog.at_level(logging.INFO, logger="eduescale"),
        pytest.raises(RuntimeError, match="initialization failed"),
    ):
        app_module.main()

    assert "EduEscale terminated unexpectedly" in caplog.text
    assert "initialization failed" in caplog.text
    assert handlers == [app_module.log_qt_message, previous_handler]
