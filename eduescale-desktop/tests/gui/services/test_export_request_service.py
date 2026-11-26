import pytest
from PySide6.QtCore import QUrl

from eduescale.gui.services.export_formats import EXPORT_FORMATS
from eduescale.gui.services.export_request_service import (
    ExportRequestError,
    ExportRequestService,
)


@pytest.mark.parametrize(
    ("export_type", "extension"),
    [(export_type, policy.extension) for export_type, policy in EXPORT_FORMATS.items()],
)
def test_prepares_export_path_using_registered_extension(
    tmp_path,
    export_type,
    extension,
):
    output_path = tmp_path / "schools.invalid"
    request = ExportRequestService().prepare(
        QUrl.fromLocalFile(str(output_path)).toString(),
        export_type,
    )

    assert request.output_path == output_path.with_suffix(extension)
    assert request.base_output_path == output_path.with_suffix(".xlsx")
    assert request.format.extension == extension


def test_rejects_non_local_export_urls():
    with pytest.raises(ExportRequestError) as error:
        ExportRequestService().prepare(
            "https://example.invalid/schools.xlsx",
            "excel",
        )

    assert error.value.title == "Ruta no válida"
    assert "archivo local" in error.value.details
