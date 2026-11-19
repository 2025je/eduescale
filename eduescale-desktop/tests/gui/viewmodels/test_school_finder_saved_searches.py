import json

import pytest
from PySide6.QtCore import QUrl

from eduescale.gui.services.saved_searches import PortableSavedSearch


def test_imports_and_exports_portable_saved_searches(view_model_components, tmp_path):
    view_model, _scrape_runner, errors = view_model_components
    location = view_model._district_metadata["150122"]
    document = {
        "format": "eduescale.saved-searches",
        "version": 1,
        "searches": [
            {
                "name": "Lima centro",
                "department": location["department"],
                "province": location["province"],
                "district": location["district"],
                "department_code": None,
                "province_code": None,
                "district_code": None,
                "management": ["publica"],
                "levels": ["primaria"],
            }
        ],
    }
    imported = []
    view_model.savedSearchesLoaded.connect(imported.extend)
    path = tmp_path / "saved-searches.json"
    file_url = QUrl.fromLocalFile(str(path)).toString()

    assert view_model.saveSavedSearches(
        file_url,
        [
            {
                "name": "Lima centro",
                "regionCode": "",
                "provinceCode": "",
                "districtCode": "",
                "regionLabel": location["department"],
                "provinceLabel": location["province"],
                "districtLabel": location["district"],
                "management": "publica",
                "level": "primaria",
            }
        ],
    )
    assert view_model.loadSavedSearches(file_url)
    assert json.loads(path.read_text(encoding="utf-8")) == document

    assert imported == [
        {
            "name": "Lima centro",
            "regionCode": location["departmentCode"],
            "provinceCode": location["provinceCode"],
            "districtCode": "150122",
            "regionLabel": location["department"],
            "provinceLabel": location["province"],
            "districtLabel": location["district"],
            "management": "publica",
            "level": "primaria",
        }
    ]
    assert errors == []

def test_rejects_invalid_portable_saved_search_file(view_model_components, tmp_path):
    view_model, _scrape_runner, errors = view_model_components
    path = tmp_path / "invalid.json"
    path.write_text(
        json.dumps(
            {
                "format": "eduescale.saved-searches",
                "version": 2,
                "searches": [{"name": "Lima"}],
            }
        ),
        encoding="utf-8",
    )

    assert not view_model.loadSavedSearches(QUrl.fromLocalFile(str(path)).toString())
    assert errors
    assert errors[-1][0] == "No se pudieron importar las búsquedas"

def test_rejects_ambiguous_saved_search_location(view_model_components):
    view_model, _scrape_runner, _errors = view_model_components
    locations_by_name = {}
    for location in view_model._district_metadata.values():
        name = view_model._saved_search_service.normalized_label(
            location["district"]
        )
        locations_by_name.setdefault(name, []).append(location["district"])
    ambiguous_names = [
        names[0] for names in locations_by_name.values() if len(names) > 1
    ]
    assert ambiguous_names

    with pytest.raises(ValueError, match="No se pudo resolver de forma única"):
        view_model._saved_search_service.normalize_searches(
            [PortableSavedSearch(name="Ubicación ambigua", district=ambiguous_names[0])]
        )

def test_rejects_inconsistent_saved_search_location_codes(view_model_components):
    view_model, _scrape_runner, _errors = view_model_components
    location = view_model._district_metadata["150122"]
    other_province_code = next(
        code
        for code in view_model._map_province_metadata
        if code != location["provinceCode"]
    )
    other_department_code = next(
        code
        for code in view_model._map_department_metadata
        if code != location["departmentCode"]
    )

    with pytest.raises(ValueError, match="no corresponde al distrito"):
        view_model._saved_search_service.normalize_searches(
            [
                PortableSavedSearch(
                    name="Códigos inconsistentes",
                    district_code="150122",
                    province_code=other_province_code,
                )
            ]
        )
    with pytest.raises(ValueError, match="no corresponde al distrito"):
        view_model._saved_search_service.normalize_searches(
            [
                PortableSavedSearch(
                    name="Departamento inconsistente",
                    district_code="150122",
                    department_code=other_department_code,
                )
            ]
        )
    with pytest.raises(ValueError, match="no reconocido"):
        view_model._saved_search_service.normalize_searches(
            [PortableSavedSearch(name="Distrito inválido", district_code="999999")]
        )
