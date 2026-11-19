from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from PySide6.QtCore import QUrl

from eduescale.application.ports import ExportType
from eduescale.gui.services.export_formats import ExportFormat, get_export_format


@dataclass(frozen=True)
class ExportRequest:
    format: ExportFormat
    output_path: Path
    base_output_path: Path


class ExportRequestError(Exception):
    def __init__(self, title: str, details: str):
        super().__init__(details)
        self.title = title
        self.details = details


class ExportRequestServicePort(Protocol):
    def prepare(self, output_url: str, export_type: ExportType) -> ExportRequest: ...


class ExportRequestService:
    def prepare(self, output_url: str, export_type: ExportType) -> ExportRequest:
        export_format = get_export_format(export_type)
        selected_url = QUrl(output_url)
        if not selected_url.isLocalFile():
            raise ExportRequestError(
                "Ruta no válida",
                "Selecciona un archivo local para guardar la exportación.",
            )
        output_path = Path(selected_url.toLocalFile())
        if not output_path.name:
            raise ExportRequestError(
                "Ruta no válida",
                "Selecciona un nombre de archivo para la exportación.",
            )
        if output_path.suffix.lower() != export_format.extension:
            output_path = output_path.with_suffix(export_format.extension)
        base_output_path = output_path.with_suffix(".xlsx")
        return ExportRequest(export_format, output_path, base_output_path)
