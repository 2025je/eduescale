from pathlib import Path

from eduescale.application.ports import ExportGateway


class ExportData:
    def __init__(self, gateway: ExportGateway):
        self._gateway = gateway

    def execute(
        self,
        export_type: str,
        database_path: Path,
        output_path: Path,
        password: str | None = None,
        compressed: bool = False,
    ) -> None:
        self._gateway.export(
            export_type,
            database_path,
            output_path,
            password,
            compressed,
        )
