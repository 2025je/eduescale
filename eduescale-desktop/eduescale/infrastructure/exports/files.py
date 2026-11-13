import os
import tempfile
from pathlib import Path


def create_temporary_export_path(output_path: Path, suffix: str) -> Path:
    with tempfile.NamedTemporaryFile(
        prefix=".eduescale-export-",
        suffix=suffix,
        dir=output_path.parent,
        delete=False,
    ) as export_file:
        return Path(export_file.name)


def replace_export_pair(
    temporary_excel_path: Path,
    excel_path: Path,
    temporary_data_path: Path,
    data_path: Path,
) -> str | None:
    backup_path = None
    if data_path.exists():
        descriptor, backup_name = tempfile.mkstemp(
            prefix=".eduescale-backup-",
            suffix=".tmp",
            dir=data_path.parent,
        )
        os.close(descriptor)
        backup_path = Path(backup_name)
        try:
            os.replace(data_path, backup_path)
        except OSError:
            backup_path.unlink(missing_ok=True)
            raise

    data_published = False
    try:
        os.replace(temporary_data_path, data_path)
        data_published = True
        os.replace(temporary_excel_path, excel_path)
    except OSError as publish_error:
        try:
            if data_published:
                data_path.unlink(missing_ok=True)
            if backup_path is not None:
                os.replace(backup_path, data_path)
        except OSError as rollback_error:
            raise OSError(
                f"{publish_error}\nNo se pudo restaurar {data_path}; "
                f"el respaldo anterior se conserva en {backup_path}: "
                f"{rollback_error}"
            ) from publish_error
        raise

    if backup_path is not None:
        try:
            backup_path.unlink(missing_ok=True)
        except OSError as error:
            return f"No se pudo eliminar el respaldo temporal {backup_path}: {error}"
    return None
