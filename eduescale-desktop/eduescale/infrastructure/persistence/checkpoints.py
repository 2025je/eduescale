import hashlib
import json
import shutil
from pathlib import Path

from eduescale.domain.schools import SchoolFilters
from eduescale.infrastructure.persistence.download_checkpoints import (
    DownloadCheckpoint,
)
from eduescale.infrastructure.runtime.paths import application_data_directory


class DownloadCheckpointGateway:
    def prepare(
        self,
        output_path: Path,
        filters: list[SchoolFilters],
    ) -> tuple[Path, str]:
        target_mtime = output_path.stat().st_mtime_ns if output_path.exists() else None
        fingerprint_payload = json.dumps(
            {
                "filters": [item.model_dump(mode="json") for item in filters],
                "target_mtime_ns": target_mtime,
            },
            sort_keys=True,
            separators=(",", ":"),
        )
        fingerprint = hashlib.sha256(fingerprint_payload.encode("utf-8")).hexdigest()
        target_identity = str(output_path.resolve()).casefold()
        checkpoint_id = hashlib.sha256(target_identity.encode("utf-8")).hexdigest()[:20]
        checkpoint_directory = (
            application_data_directory() / "download-checkpoints" / checkpoint_id
        )
        return checkpoint_directory, fingerprint

    def complete(self, checkpoint_directory: Path) -> str | None:
        try:
            DownloadCheckpoint.mark_directory_complete(checkpoint_directory)
        except OSError as error:
            return str(error)
        try:
            shutil.rmtree(checkpoint_directory)
        except FileNotFoundError:
            pass
        except OSError as error:
            return str(error)
        return None
