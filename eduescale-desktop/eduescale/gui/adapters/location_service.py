import logging

from PySide6.QtCore import QObject, Qt, QThread, Signal, Slot

from eduescale.application.errors import LocationFetchError
from eduescale.application.ports import LocationGateway

LOGGER = logging.getLogger(__name__)


class _LocationWorker(QThread):
    loaded = Signal(str, str, object)
    failed = Signal(str, str, str)

    def __init__(
        self,
        provider: LocationGateway,
        kind: str,
        parent_code: str,
        parent: QObject | None = None,
    ):
        super().__init__(parent)
        self._provider = provider
        self._kind = kind
        self._parent_code = parent_code

    def run(self):
        try:
            locations = self._provider.fetch(self._kind, self._parent_code)
        except (LocationFetchError, ValueError) as error:
            LOGGER.exception(
                "Failed to fetch %s for parent code %s.",
                self._kind,
                self._parent_code or "(none)",
            )
            self.failed.emit(self._kind, self._parent_code, str(error))
        else:
            self.loaded.emit(self._kind, self._parent_code, locations)


class LocationService(QObject):
    loaded = Signal(str, str, object)
    failed = Signal(str, str, str)

    def __init__(
        self,
        provider: LocationGateway,
        parent: QObject | None = None,
    ):
        super().__init__(parent)
        self._provider = provider
        self._threads: set[_LocationWorker] = set()
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
        worker = _LocationWorker(self._provider, kind, parent_code, self)
        worker.loaded.connect(
            self._locations_loaded,
            Qt.ConnectionType.QueuedConnection,
        )
        worker.failed.connect(
            self._locations_failed,
            Qt.ConnectionType.QueuedConnection,
        )
        worker.finished.connect(self._thread_finished)
        worker.finished.connect(worker.deleteLater)
        self._threads.add(worker)
        worker.start()

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
        if isinstance(thread, _LocationWorker):
            self._threads.discard(thread)

    def shutdown(self):
        for thread in list(self._threads):
            thread.wait()
