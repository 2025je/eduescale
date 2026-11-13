from threading import Event

import pytest
from PySide6.QtCore import QCoreApplication, QEventLoop, QTimer

from eduescale.application.errors import LocationFetchError
from eduescale.gui.location_service import LocationService
from eduescale.infrastructure.escale import location_provider


def wait_for_signal(signal, start=None):
    event_loop = QEventLoop()
    received = []

    def handle_signal(*args):
        received.append(args)
        event_loop.quit()

    signal.connect(handle_signal)
    if start is not None:
        QTimer.singleShot(0, start)
    QTimer.singleShot(5000, event_loop.quit)
    event_loop.exec()
    signal.disconnect(handle_signal)
    return received


@pytest.fixture
def application():
    return QCoreApplication.instance() or QCoreApplication([])


def test_caches_loaded_locations(application):
    class Provider:
        def __init__(self):
            self.calls = []

        def fetch(self, kind, parent_code):
            self.calls.append((kind, parent_code))
            return [("01", "Lima")]

    provider = Provider()
    service = LocationService(provider)
    try:
        loaded = wait_for_signal(service.loaded, lambda: service.load("regions"))
        assert loaded == [("regions", "", [("01", "Lima")])]

        service.load("regions")

        assert service._cache[("regions", "")] == (("01", "Lima"),)
        assert provider.calls == [("regions", "")]
    finally:
        service.shutdown()


def test_suppresses_duplicate_requests_while_loading(application):
    class BlockingProvider:
        def __init__(self):
            self.calls = []
            self.started = Event()
            self.release = Event()

        def fetch(self, kind, parent_code):
            self.calls.append((kind, parent_code))
            self.started.set()
            self.release.wait(5)
            return [("01", "Lima")]

    provider = BlockingProvider()
    service = LocationService(provider)
    try:
        service.load("regions")
        assert provider.started.wait(5)
        service.load("regions")
        assert provider.calls == [("regions", "")]

        provider.release.set()
        loaded = wait_for_signal(service.loaded)
        assert loaded == [("regions", "", [("01", "Lima")])]
    finally:
        provider.release.set()
        service.shutdown()


def test_failed_location_request_can_be_retried(application):
    class FailingOnceProvider:
        def __init__(self):
            self.calls = 0

        def fetch(self, kind, parent_code):
            self.calls += 1
            if self.calls == 1:
                raise ValueError("respuesta temporal inválida")
            return [("01", "Lima")]

    provider = FailingOnceProvider()
    service = LocationService(provider)
    try:
        failed = wait_for_signal(service.failed, lambda: service.load("regions"))
        assert failed == [("regions", "", "respuesta temporal inválida")]

        loaded = wait_for_signal(service.loaded, lambda: service.load("regions"))
        assert loaded == [("regions", "", [("01", "Lima")])]
        assert provider.calls == 2
    finally:
        service.shutdown()


def test_escale_provider_translates_transport_errors(monkeypatch):
    def fail_request():
        raise location_provider.requests.ConnectionError("servicio no disponible")

    monkeypatch.setattr(location_provider, "fetch_regions", fail_request)

    with pytest.raises(LocationFetchError, match="servicio no disponible"):
        location_provider.ESCALELocationProvider().fetch("regions", "")
