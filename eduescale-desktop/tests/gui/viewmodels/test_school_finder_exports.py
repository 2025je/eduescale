import logging
from pathlib import Path
from types import SimpleNamespace

from PySide6.QtCore import QUrl

from eduescale.application.export_policy import android_data_path
from eduescale.gui.viewmodels import school_finder as view_model_module


def test_persists_only_the_latest_50_export_metadata_entries(view_model_components):
    view_model, _scrape_runner, _errors = view_model_components
    view_model._last_search_summary = "Lima / Miraflores · primaria"

    for index in range(55):
        view_model._record_export_history(
            "excel",
            f"colegios_{index}.xlsx",
            "Completada",
        )

    assert len(view_model.exportHistory) == 50
    assert view_model.exportHistory[0]["file"] == "colegios_54.xlsx"
    assert view_model.exportHistory[-1]["file"] == "colegios_5.xlsx"
    assert view_model.exportHistory[0]["filters"] == ("Lima / Miraflores · primaria")
    assert not any("password" in key for key in view_model.exportHistory[0])

def test_default_output_url_avoids_overwriting_existing_exports(
    view_model_components,
    tmp_path,
):
    view_model, _scrape_runner, _errors = view_model_components
    output_path = tmp_path / "colegios_escale.xlsx"
    output_path.write_bytes(b"existing Excel")
    android_data_path(output_path).write_bytes(b"existing EduEscale")
    output_path.with_suffix(".pdf").write_bytes(b"existing PDF")
    assert view_model._preferences.save_last_export_directory(tmp_path)

    next_output = Path(QUrl(view_model.defaultOutputUrl).toLocalFile())

    assert next_output == tmp_path / "colegios_escale_1.xlsx"

def test_export_buttons_generate_and_open_the_requested_file(
    view_model_components,
    monkeypatch,
    tmp_path,
):
    view_model, _scrape_runner, _errors = view_model_components
    export_runner = view_model._export_runner
    output_path = tmp_path / "schools.xlsx"
    eduescale_path = android_data_path(output_path)
    pdf_path = output_path.with_suffix(".pdf")
    opened_urls = []
    monkeypatch.setattr(
        view_model_module,
        "QDesktopServices",
        SimpleNamespace(openUrl=lambda url: opened_urls.append(url) or True),
    )
    view_model._output_path = output_path
    view_model._dataset_path = tmp_path / "schools.sqlite3"
    view_model._exports_available = True

    view_model.exportExcelFile(QUrl.fromLocalFile(str(output_path)).toString())
    assert export_runner.started_requests == [
        ("excel", view_model._dataset_path, output_path)
    ]
    output_path.write_bytes(b"Excel")
    export_runner.completed.emit("excel", str(output_path))

    view_model.exportEduEscaleFile(QUrl.fromLocalFile(str(eduescale_path)).toString())
    assert export_runner.started_requests[-1] == (
        "eduescale",
        view_model._dataset_path,
        eduescale_path,
    )
    eduescale_path.write_text("{}", encoding="utf-8")
    export_runner.completed.emit("eduescale", str(eduescale_path))

    view_model.exportPdfFile(QUrl.fromLocalFile(str(pdf_path)).toString())
    assert export_runner.started_requests[-1] == (
        "pdf",
        view_model._dataset_path,
        pdf_path,
    )
    pdf_path.write_bytes(b"%PDF-")
    export_runner.completed.emit("pdf", str(pdf_path))

    assert [Path(QUrl(url).toLocalFile()) for url in opened_urls] == [
        output_path,
        eduescale_path,
        pdf_path,
    ]

def test_starts_optional_compressed_and_protected_export(
    view_model_components, tmp_path, caplog
):
    view_model, _scrape_runner, _errors = view_model_components
    view_model._dataset_path = tmp_path / "schools.sqlite3"
    view_model._output_path = tmp_path / "schools.xlsx"
    view_model._exports_available = True
    output_path = tmp_path / "schools.eduescale"

    with caplog.at_level(logging.INFO, logger="eduescale"):
        view_model.exportEduEscaleFile(
            QUrl.fromLocalFile(str(output_path)).toString(),
            "contraseña segura",
            True,
        )

    start_record = next(
        record for record in caplog.records if record.message == "Starting export."
    )
    assert start_record.export_type == "eduescale"
    assert start_record.compressed is True
    assert "contraseña segura" not in repr(start_record.__dict__)
    assert view_model._export_runner.started_requests == [
        (
            "eduescale",
            view_model._dataset_path,
            output_path,
            "contraseña segura",
            True,
        )
    ]
