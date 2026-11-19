from collections.abc import Mapping
from dataclasses import dataclass
from types import MappingProxyType

from eduescale.application.ports import ExportType


@dataclass(frozen=True)
class ExportFormat:
    extension: str
    label: str
    progress_message: str


EXPORT_FORMATS: Mapping[str, ExportFormat] = MappingProxyType(
    {
        "excel": ExportFormat(
            extension=".xlsx",
            label="Excel",
            progress_message="Generando archivo Excel...",
        ),
        "eduescale": ExportFormat(
            extension=".eduescale",
            label=".eduescale",
            progress_message="Generando archivo .eduescale...",
        ),
        "pdf": ExportFormat(
            extension=".pdf",
            label="PDF",
            progress_message="Generando archivo PDF...",
        ),
    }
)


def get_export_format(export_type: ExportType) -> ExportFormat:
    return EXPORT_FORMATS[export_type]


def export_format_for_signal(export_type: str) -> ExportFormat | None:
    return EXPORT_FORMATS.get(export_type)
