from typing import Protocol
from xml.etree import ElementTree

import requests
from PySide6.QtCore import QObject, QThread, Signal, Slot

from eduescale.client import fetch_districts, fetch_provinces, fetch_regions


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
            self.failed.emit(self._kind, self._parent_code, str(error))
        else:
            self.loaded.emit(self._kind, self._parent_code, locations)
        finally:
            QThread.currentThread().quit()


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

    def load(self, kind: str, parent_code: str = ""):
        thread = QThread(self)
        worker = _LocationWorker(self._provider, kind, parent_code)
        worker.moveToThread(thread)
        thread.started.connect(worker.run)
        worker.loaded.connect(self.loaded)
        worker.failed.connect(self.failed)
        thread.finished.connect(worker.deleteLater)
        thread.finished.connect(self._thread_finished)
        thread.finished.connect(thread.deleteLater)
        self._threads[thread] = worker
        thread.start()

    @Slot()
    def _thread_finished(self):
        thread = self.sender()
        if isinstance(thread, QThread):
            self._threads.pop(thread, None)

    def shutdown(self):
        for thread in list(self._threads):
            thread.quit()
            thread.wait()
