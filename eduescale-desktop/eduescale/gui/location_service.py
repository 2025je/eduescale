import logging
from typing import Protocol
from xml.etree import ElementTree

import requests
from PySide6.QtCore import QObject, Qt, QThread, QTimer, Signal, Slot

from eduescale.client import fetch_districts, fetch_provinces, fetch_regions

LOGGER = logging.getLogger(__name__)
LocationList = list[tuple[str, str]]


class LocationProvider(Protocol):
    def fetch(self, kind: str, parent_code: str) -> LocationList: ...


class ESCALELocationProvider:
    def fetch(self, kind: str, parent_code: str) -> LocationList:
        if kind == "regions":
            return fetch_regions()
        if kind == "provinces":
            return fetch_provinces(parent_code)
        if kind == "districts":
            return fetch_districts(parent_code)
        raise ValueError(f"Tipo de ubicación desconocido: {kind}")


class _LocationWorker(QObject):
    loaded = Signal(str, str, object)
    failed = Signal(str, str, str)

    def __init__(
        self,
        provider: LocationProvider,
        kind: str,
        parent_code: str,
    ):
        super().__init__()
        self._provider = provider
        self._kind = kind
        self._parent_code = parent_code

    @Slot()
    def run(self):
        try:
            locations = self._provider.fetch(self._kind, self._parent_code)
        except (requests.RequestException, ElementTree.ParseError, ValueError) as error:
            LOGGER.exception(
                "Failed to fetch %s for parent code %s.",
                self._kind,
                self._parent_code or "(none)",
            )
            self.failed.emit(self._kind, self._parent_code, str(error))
        else:
            self.loaded.emit(self._kind, self._parent_code, locations)
        finally:
            QTimer.singleShot(0, QThread.currentThread().quit)


class LocationService(QObject):
    loaded = Signal(str, str, object)
    failed = Signal(str, str, str)

    def __init__(
        self,
        provider: LocationProvider | None = None,
        parent: QObject | None = None,
    ):
        super().__init__(parent)
        self._provider = provider or ESCALELocationProvider()
        self._threads: dict[QThread, _LocationWorker] = {}
        self._cache: dict[tuple[str, str], tuple[tuple[str, str], ...]] = {}
        self._in_flight: set[tuple[str, str]] = set()

    def load(self, kind: str, parent_code: str = ""):
        key = (kind, parent_code)
        cached_locations = self._cache.get(key)
        if cached_locations is not None:
            self.loaded.emit(kind, parent_code, list(cached_locations))
            return
        if key in self._in_flight:
            return

        self._in_flight.add(key)
        thread = QThread(self)
        worker = _LocationWorker(self._provider, kind, parent_code)
        worker.moveToThread(thread)
        thread.started.connect(worker.run)
        worker.loaded.connect(
            self._locations_loaded,
            Qt.ConnectionType.QueuedConnection,
        )
        worker.failed.connect(
            self._locations_failed,
            Qt.ConnectionType.QueuedConnection,
        )
        thread.finished.connect(worker.deleteLater)
        thread.finished.connect(self._thread_finished)
        thread.finished.connect(thread.deleteLater)
        self._threads[thread] = worker
        thread.start()

    @Slot(str, str, object)
    def _locations_loaded(self, kind, parent_code, locations):
        key = (kind, parent_code)
        self._in_flight.discard(key)
        cached_locations = tuple(locations)
        self._cache[key] = cached_locations
        LOGGER.info(
            "Cached %d %s for parent code %s.",
            len(cached_locations),
            kind,
            parent_code or "(none)",
        )
        self.loaded.emit(kind, parent_code, list(cached_locations))

    @Slot(str, str, str)
    def _locations_failed(self, kind, parent_code, message):
        self._in_flight.discard((kind, parent_code))
        LOGGER.error(
            "Unable to load %s for parent code %s: %s",
            kind,
            parent_code or "(none)",
            message,
        )
        self.failed.emit(kind, parent_code, message)

    @Slot()
    def _thread_finished(self):
        thread = self.sender()
        if isinstance(thread, QThread):
            self._threads.pop(thread, None)

    def shutdown(self):
        for thread in list(self._threads):
            thread.quit()
            thread.wait()
