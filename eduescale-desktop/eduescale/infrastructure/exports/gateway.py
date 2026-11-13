from pathlib import Path

from eduescale.infrastructure.exports.eduescale import export_eduescale
from eduescale.infrastructure.exports.excel import export_excel
from eduescale.infrastructure.exports.pdf import export_pdf


class FilesystemExportGateway:
    def export(
        self,
        export_type: str,
        database_path: Path,
        output_path: Path,
        password: str | None = None,
        compressed: bool = False,
    ) -> None:
        if export_type == "excel":
            export_excel(database_path, output_path)
        elif export_type == "pdf":
            export_pdf(database_path, output_path)
        elif export_type == "eduescale":
            export_eduescale(
                database_path,
                output_path,
                password,
                compressed=compressed,
            )
        else:
            raise ValueError(f"Formato de exportación desconocido: {export_type}")
