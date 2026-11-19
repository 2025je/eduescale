from collections.abc import Callable, Mapping
from pathlib import Path
from types import MappingProxyType

from eduescale.application.ports import ExportType
from eduescale.infrastructure.exports.eduescale import export_eduescale
from eduescale.infrastructure.exports.excel import export_excel
from eduescale.infrastructure.exports.pdf import export_pdf

ExportHandler = Callable[[Path, Path, str | None, bool], None]


def _export_excel(
    database_path: Path,
    output_path: Path,
    password: str | None,
    compressed: bool,
) -> None:
    _ = password, compressed
    export_excel(database_path, output_path)


def _export_eduescale(
    database_path: Path,
    output_path: Path,
    password: str | None,
    compressed: bool,
) -> None:
    export_eduescale(
        database_path,
        output_path,
        password,
        compressed=compressed,
    )


def _export_pdf(
    database_path: Path,
    output_path: Path,
    password: str | None,
    compressed: bool,
) -> None:
    _ = password, compressed
    export_pdf(database_path, output_path)


EXPORT_HANDLERS: Mapping[ExportType, ExportHandler] = MappingProxyType(
    {
        "excel": _export_excel,
        "eduescale": _export_eduescale,
        "pdf": _export_pdf,
    }
)


class FilesystemExportGateway:
    def export(
        self,
        export_type: ExportType,
        database_path: Path,
        output_path: Path,
        password: str | None = None,
        compressed: bool = False,
    ) -> None:
        handler = EXPORT_HANDLERS.get(export_type)
        if handler is None:
            raise ValueError(f"Formato de exportación desconocido: {export_type}")
        handler(database_path, output_path, password, compressed)
