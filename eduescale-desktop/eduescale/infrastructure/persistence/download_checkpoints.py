"""Validated checkpoint manifests and cached ESCALE pages."""

import gzip
import os
import tempfile
import zlib
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError

CHECKPOINT_MAX_AGE = timedelta(hours=24)


class CheckpointManifest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: Literal[1] = 1
    fingerprint: str
    created_at: datetime
    filter_totals: list[int] | None = None
    retries: int = 0
    last_failures: list[str] = Field(default_factory=list)
    completed: bool = False


class DownloadCheckpoint:
    def __init__(self, directory: Path, fingerprint: str):
        self.directory = directory
        self._manifest_path = directory / "manifest.json"
        directory.mkdir(parents=True, exist_ok=True)
        manifest = self._read_manifest()
        if manifest is None or not self._can_resume(manifest, fingerprint):
            self._remove_page_files()
            manifest = CheckpointManifest(
                fingerprint=fingerprint,
                created_at=datetime.now(timezone.utc),
            )
        self._manifest: CheckpointManifest = manifest
        self._write_manifest()

    @classmethod
    def mark_directory_complete(cls, directory: Path) -> None:
        manifest_path = directory / "manifest.json"
        if not manifest_path.is_file():
            return
        try:
            manifest = CheckpointManifest.model_validate_json(
                manifest_path.read_bytes()
            )
        except (OSError, ValidationError):
            return
        manifest.completed = True
        cls._write_manifest_file(manifest_path, manifest)

    def set_filter_totals(self, totals: list[int]) -> bool:
        changed = False
        if self._manifest.filter_totals is None:
            self._manifest.filter_totals = totals
        elif self._manifest.filter_totals != totals:
            changed = True
            self._remove_page_files()
            self._manifest = CheckpointManifest(
                fingerprint=self._manifest.fingerprint,
                created_at=datetime.now(timezone.utc),
                filter_totals=totals,
                retries=self._manifest.retries,
            )
        self._write_manifest()
        return changed

    def load_page(self, filter_index: int, offset: int) -> bytes | None:
        page_path = self._page_path(filter_index, offset)
        try:
            return gzip.decompress(page_path.read_bytes())
        except FileNotFoundError:
            return None
        except (OSError, EOFError, zlib.error):
            page_path.unlink(missing_ok=True)
            return None

    def save_page(self, filter_index: int, offset: int, body: bytes) -> None:
        page_path = self._page_path(filter_index, offset)
        compressed_body = gzip.compress(body, mtime=0)
        temporary_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(
                prefix=f"{page_path.name}.",
                suffix=".tmp",
                dir=self.directory,
                delete=False,
            ) as temporary_file:
                temporary_path = Path(temporary_file.name)
                temporary_file.write(compressed_body)
            os.replace(temporary_path, page_path)
        finally:
            if temporary_path is not None:
                temporary_path.unlink(missing_ok=True)

    def remove_page(self, filter_index: int, offset: int) -> None:
        self._page_path(filter_index, offset).unlink(missing_ok=True)

    def record_retry(self) -> None:
        self._manifest.retries += 1
        self._write_manifest()

    def record_failures(self, failures: list[str]) -> None:
        self._manifest.last_failures = failures
        self._write_manifest()

    @property
    def cached_page_count(self) -> int:
        return sum(
            1
            for path in self.directory.iterdir()
            if path.is_file()
            and path.name.startswith("page-")
            and path.name.endswith(".xml.gz")
        )

    def _read_manifest(self) -> CheckpointManifest | None:
        try:
            return CheckpointManifest.model_validate_json(
                self._manifest_path.read_bytes()
            )
        except FileNotFoundError:
            return None
        except (OSError, ValidationError):
            return None

    @staticmethod
    def _can_resume(
        manifest: CheckpointManifest | None,
        fingerprint: str,
    ) -> bool:
        if (
            manifest is None
            or manifest.fingerprint != fingerprint
            or manifest.completed
            or manifest.created_at.tzinfo is None
        ):
            return False
        age = datetime.now(timezone.utc) - manifest.created_at
        return timedelta(0) <= age <= CHECKPOINT_MAX_AGE

    def _write_manifest(self) -> None:
        self._write_manifest_file(self._manifest_path, self._manifest)

    @staticmethod
    def _write_manifest_file(path: Path, manifest: CheckpointManifest) -> None:
        temporary_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(
                prefix="manifest-",
                suffix=".tmp",
                dir=path.parent,
                delete=False,
            ) as temporary_file:
                temporary_path = Path(temporary_file.name)
                temporary_file.write(manifest.model_dump_json().encode("utf-8"))
            os.replace(temporary_path, path)
        finally:
            if temporary_path is not None:
                temporary_path.unlink(missing_ok=True)

    def _remove_page_files(self) -> None:
        for path in self.directory.iterdir():
            if path.is_file() and (
                (
                    path.name.startswith("page-")
                    and path.name.endswith((".xml.gz", ".tmp"))
                )
                or path.name.startswith("manifest-")
            ):
                path.unlink()

    def _page_path(self, filter_index: int, offset: int) -> Path:
        return self.directory / f"page-{filter_index:03d}-{offset:08d}.xml.gz"
