
from PySide6.QtCore import QUrl

from eduescale.gui.services.progress import ProgressMetrics


def test_management_selection_is_required(view_model_components):
    view_model, scrape_runner, errors = view_model_components
    view_model.startScrape(
        "file:///tmp/schools.xlsx",
        [{"management": "", "level": "primaria"}],
    )

    assert len(errors) == 1
    assert "Gestión" in errors[0][1]
    assert scrape_runner.started_requests == []

def test_education_level_selection_is_required(view_model_components):
    view_model, scrape_runner, errors = view_model_components
    view_model.startScrape(
        "file:///tmp/schools.xlsx",
        [{"management": "publica", "level": ""}],
    )

    assert len(errors) == 1
    assert "Nivel educativo" in errors[0][1]
    assert scrape_runner.started_requests == []

def test_rejects_non_local_output_urls(view_model_components):
    view_model, scrape_runner, errors = view_model_components
    view_model.startScrape(
        "https://example.invalid/schools.xlsx",
        [{"management": "publica", "level": "primaria"}],
    )

    assert errors == [
        ("Ruta no válida", "Selecciona un archivo local para guardar el Excel.")
    ]
    assert scrape_runner.started_requests == []

def test_starts_scrape_with_multiple_filter_sets(view_model_components):
    view_model, scrape_runner, _errors = view_model_components
    view_model.startScrape(
        "file:///tmp/schools.xlsx",
        [
            {
                "region_code": "01",
                "management": "publica",
                "level": "primaria",
            },
            {
                "region_code": "02",
                "management": "privada",
                "level": "primaria,secundaria",
            },
        ],
    )

    assert len(scrape_runner.started_requests) == 1
    filters = scrape_runner.started_requests[0][1]
    assert len(filters) == 2
    assert filters[0].region_code == "01"
    assert filters[1].region_code == "02"
    assert filters[1].level == ("primaria", "secundaria")
    scrape_runner.progressChanged.emit(
        ProgressMetrics(
            received=50,
            total=100,
            unique=48,
            pages=1,
            failed=1,
        )
    )
    assert view_model.progressMessage == (
        "50 de 100 resultados · 48 colegios únicos · 1 página · 1 página fallida"
    )

def test_cancel_scrape_updates_status_after_runner_confirms(view_model_components):
    view_model, scrape_runner, _errors = view_model_components
    view_model.startScrape(
        "file:///tmp/schools.xlsx",
        [{"management": "publica", "level": "primaria"}],
    )

    view_model.cancelScrape()
    assert scrape_runner.cancel_requests == 1
    assert view_model.scrapeRunning

    scrape_runner.cancelled.emit()

    assert not view_model.scrapeRunning
    assert "cancelada" in view_model.statusText

def test_school_batches_add_one_point_and_update_aggregated_details(
    view_model_components,
):
    view_model, scrape_runner, _errors = view_model_components
    locations = []
    view_model.schoolLocationsReceived.connect(locations.extend)
    first_record = {
        "nombre": "Colegio Ejemplo",
        "direccion": "Av. Principal",
        "localidad": "Localidad",
        "cp": "Centro poblado",
        "turno": "Mañana",
        "alumnos": 10,
        "secciones": 2,
        "longitud": -77.2,
        "latitud": -12.1,
        "distrito": "Distrito",
        "provincia": "Provincia",
        "region": "Departamento",
        "gestion": "publica",
        "nivel": "primaria",
        "codigo_local": "000123",
    }
    second_record = {
        **first_record,
        "turno": "Tarde",
        "alumnos": 20,
        "secciones": 3,
        "nivel": "secundaria",
    }

    scrape_runner.schoolsReceived.emit([first_record])
    assert len(locations) == 1
    school_id = locations[0]["schoolId"]
    scrape_runner.schoolsReceived.emit([second_record])

    assert len(locations) == 1
    details = view_model.schoolDetails(school_id)
    assert details["niveles"] == ["primaria", "secundaria"]
    assert details["alumnos"] == 30
    assert details["secciones"] == 5

def test_scrape_completed_makes_results_available_without_exporting_files(
    view_model_components,
    tmp_path,
):
    view_model, scrape_runner, _errors = view_model_components
    database_path = tmp_path / "schools.sqlite3"

    view_model.startScrape(
        QUrl.fromLocalFile(str(tmp_path / "request.xlsx")).toString(),
        [{"management": "publica", "level": "primaria"}],
    )
    scrape_runner.completed.emit(str(database_path), 2, 2, 1)

    assert "2 colegios" in view_model.statusText
    assert not (tmp_path / "schools.xlsx").exists()
    assert not (tmp_path / "schools.eduescale").exists()
    assert not (tmp_path / "schools.pdf").exists()
    assert view_model._dataset_path == database_path
    assert view_model.exportsAvailable
