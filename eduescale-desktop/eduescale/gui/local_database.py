import json
import logging
import shutil
import sqlite3
import tempfile
import threading
import unicodedata
from collections.abc import Callable
from contextlib import closing
from dataclasses import dataclass
from pathlib import Path

from PySide6.QtCore import QObject, QThread, Signal

LOGGER = logging.getLogger(__name__)
REQUIRED_RECORD_COLUMNS = {"region", "province", "district", "payload"}
SEARCH_BATCH_SIZE = 500
PROGRESS_INTERVAL = 100


@dataclass(frozen=True)
class LocalSearchFilter:
    region: str = ""
    province: str = ""
    district: str = ""
    management: tuple[str, ...] = ()
    level: tuple[str, ...] = ()


class SearchCancelled(Exception):
    pass


def validate_local_database(database_path: Path) -> None:
    if not database_path.is_file():
        raise ValueError(f"No existe la base de datos: {database_path}")
    try:
        with closing(
            sqlite3.connect(
                f"{database_path.resolve().as_uri()}?mode=ro",
                uri=True,
            )
        ) as database:
            table = database.execute(
                "SELECT type FROM sqlite_master WHERE name = 'records'"
            ).fetchone()
            if table is None or table[0] != "table":
                raise ValueError("La base no contiene la tabla records de EduEscale.")
            columns = {row[1] for row in database.execute("PRAGMA table_info(records)")}
    except sqlite3.Error as error:
        raise ValueError(f"No se pudo leer la base de datos: {error}") from error
    missing_columns = REQUIRED_RECORD_COLUMNS - columns
    if missing_columns:
        raise ValueError(
            "La tabla records no tiene las columnas requeridas: "
            + ", ".join(sorted(missing_columns))
            + "."
        )


def read_local_database_metadata(database_path: Path) -> dict[str, str]:
    validate_local_database(database_path)
    with closing(
        sqlite3.connect(
            f"{database_path.resolve().as_uri()}?mode=ro",
            uri=True,
        )
    ) as database:
        table = database.execute(
            "SELECT type FROM sqlite_master WHERE name = 'metadata'"
        ).fetchone()
        if table is None:
            return {}
        if table[0] != "table":
            raise ValueError("La tabla metadata de la base local no es válida.")
        columns = {row[1] for row in database.execute("PRAGMA table_info(metadata)")}
        if not {"key", "value"}.issubset(columns):
            raise ValueError("La tabla metadata de la base local no es válida.")
        metadata = {}
        for key, value in database.execute("SELECT key, value FROM metadata"):
            if not isinstance(key, str) or not isinstance(value, str):
                raise TypeError("La tabla metadata de la base local no es válida.")
            metadata[key] = value
        return metadata


def write_local_database_metadata(
    database_path: Path,
    *,
    updated_at: str,
    total_records: int,
    unique_records: int,
    pages: int,
) -> None:
    with closing(sqlite3.connect(database_path)) as database:
        database.execute(
            """
            CREATE TABLE IF NOT EXISTS metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """
        )
        database.executemany(
            "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
            (
                ("source", "ESCALE"),
                ("updated_at", updated_at),
                ("total_records", str(total_records)),
                ("unique_records", str(unique_records)),
                ("pages", str(pages)),
            ),
        )
        database.commit()


def filter_local_database(
    source_path: Path,
    output_path: Path,
    filters: list[LocalSearchFilter],
    cancelled: Callable[[], bool],
    progress: Callable[[int, int, int], None],
) -> tuple[int, int]:
    if not filters:
        raise ValueError("Agrega al menos una combinación de filtros.")
    if source_path.resolve() == output_path.resolve():
        raise ValueError("La base de origen y el resultado local deben ser distintos.")
    validate_local_database(source_path)
    source_uri = f"{source_path.resolve().as_uri()}?mode=ro"
    total_records = 0
    matched_records = 0
    processed_records = 0
    batch: list[tuple[str, str, str, str]] = []

    with closing(sqlite3.connect(source_uri, uri=True)) as source:
        total_row = source.execute("SELECT COUNT(*) FROM records").fetchone()
        total_records = int(total_row[0]) if total_row is not None else 0
        cursor = source.execute(
            "SELECT region, province, district, payload FROM records"
        )
        with closing(sqlite3.connect(output_path)) as output:
            output.execute(
                """
                CREATE TABLE records (
                    region TEXT NOT NULL,
                    province TEXT NOT NULL,
                    district TEXT NOT NULL,
                    payload TEXT NOT NULL
                )
                """
            )
            for region, province, district, payload in cursor:
                if cancelled():
                    raise SearchCancelled
                if not isinstance(payload, str):
                    raise TypeError(
                        f"El registro {processed_records + 1} no contiene texto JSON."
                    )
                record = json.loads(payload)
                if not isinstance(record, dict):
                    raise TypeError(
                        f"El registro {processed_records + 1} no contiene un objeto JSON."
                    )
                region_value = str(region or "")
                province_value = str(province or "")
                district_value = str(district or "")
                if _matches_filters(
                    region_value,
                    province_value,
                    district_value,
                    record,
                    filters,
                ):
                    batch.append(
                        (
                            region_value,
                            province_value,
                            district_value,
                            payload,
                        )
                    )
                    matched_records += 1
                processed_records += 1

                if len(batch) >= SEARCH_BATCH_SIZE:
                    output.executemany(
                        """
                        INSERT INTO records (region, province, district, payload)
                        VALUES (?, ?, ?, ?)
                        """,
                        batch,
                    )
                    batch.clear()
                if processed_records % PROGRESS_INTERVAL == 0:
                    progress(processed_records, total_records, matched_records)

            if batch:
                output.executemany(
                    """
                    INSERT INTO records (region, province, district, payload)
                    VALUES (?, ?, ?, ?)
                    """,
                    batch,
                )
            output.commit()

    progress(processed_records, total_records, matched_records)
    return total_records, matched_records


def _matches_filters(
    region: str,
    province: str,
    district: str,
    record: dict[str, object],
    filters: list[LocalSearchFilter],
) -> bool:
    normalized_region = _normalize_name(region)
    normalized_province = _normalize_name(province)
    normalized_district = _normalize_name(district)
    management = _normalize_name(str(record.get("gestion") or ""))
    level = _normalize_name(str(record.get("nivel") or ""))

    for search in filters:
        if search.region and normalized_region != _normalize_name(search.region):
            continue
        if search.province and normalized_province != _normalize_name(search.province):
            continue
        if search.district and normalized_district != _normalize_name(search.district):
            continue
        if search.management and management not in {
            _normalize_name(value) for value in search.management
        }:
            continue
        if search.level and level not in {
            _normalize_name(value) for value in search.level
        }:
            continue
        return True
    return False


def _normalize_name(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).split()).casefold()


class _LocalSearchWorker(QThread):
    progressChanged = Signal(int, int, int, str)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)
    cancelled = Signal()

    def __init__(
        self,
        source_path: Path,
        output_path: Path,
        filters: list[LocalSearchFilter],
    ):
        super().__init__()
        self._source_path = source_path
        self._output_path = output_path
        self._filters = filters
        self._cancel_event = threading.Event()

    def run(self):
        try:
            total, matched = filter_local_database(
                self._source_path,
                self._output_path,
                self._filters,
                self._cancel_event.is_set,
                self._report_progress,
            )
        except SearchCancelled:
            self.cancelled.emit()
        except (OSError, sqlite3.Error, TypeError, ValueError) as error:
            LOGGER.exception("Offline search failed for %s.", self._source_path)
            self.failed.emit("Error en la base offline", str(error))
        else:
            self.completed.emit(str(self._output_path), total, matched, 0)

    def cancel(self):
        self._cancel_event.set()

    def _report_progress(self, processed: int, total: int, matched: int):
        message = (
            f"Base offline · {processed:,} de {total:,} registros · "
            f"{matched:,} coinciden"
        )
        self.progressChanged.emit(processed, total, 0, message)


class LocalDatabaseSearchRunner(QObject):
    started = Signal()
    progressChanged = Signal(int, int, int, str)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)
    cancelled = Signal()

    def __init__(self, parent: QObject | None = None):
        super().__init__(parent)
        self._worker: _LocalSearchWorker | None = None
        self._temporary_directory: Path | None = None

    def start(
        self,
        source_path: Path,
        filters: list[LocalSearchFilter],
    ):
        if self._worker is not None and self._worker.isRunning():
            self.failed.emit(
                "Búsqueda en curso",
                "Ya hay una búsqueda local activa.",
            )
            return
        cleanup_error = self._cleanup_temporary_directory()
        if cleanup_error:
            self.failed.emit("No se pudo limpiar la búsqueda anterior", cleanup_error)
            return
        try:
            temporary_directory = Path(tempfile.mkdtemp(prefix="eduescale-offline-"))
        except OSError as error:
            self.failed.emit("No se pudo preparar la búsqueda offline", str(error))
            return

        self._temporary_directory = temporary_directory
        output_path = temporary_directory / "results.sqlite3"
        worker = _LocalSearchWorker(source_path, output_path, filters)
        worker.progressChanged.connect(self.progressChanged)
        worker.completed.connect(self.completed)
        worker.failed.connect(self.failed)
        worker.cancelled.connect(self.cancelled)
        worker.finished.connect(self._worker_finished)
        self._worker = worker
        self.started.emit()
        worker.start()

    def cancel(self):
        if self._worker is not None and self._worker.isRunning():
            self._worker.cancel()

    def shutdown(self):
        worker = self._worker
        if worker is not None:
            if worker.isRunning():
                worker.cancel()
                worker.wait()
            worker.deleteLater()
            self._worker = None
        cleanup_error = self._cleanup_temporary_directory()
        if cleanup_error:
            LOGGER.error("Could not clean offline search data: %s", cleanup_error)

    def _cleanup_temporary_directory(self) -> str | None:
        if self._temporary_directory is None:
            return None
        try:
            shutil.rmtree(self._temporary_directory)
        except FileNotFoundError:
            pass
        except OSError as error:
            return str(error)
        self._temporary_directory = None
        return None

    def _worker_finished(self):
        worker = self.sender()
        if isinstance(worker, _LocalSearchWorker):
            if worker is self._worker:
                self._worker = None
            worker.deleteLater()
