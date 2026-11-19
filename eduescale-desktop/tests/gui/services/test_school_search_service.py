from pathlib import Path
from typing import cast

import pytest
from PySide6.QtCore import QUrl

from eduescale.gui.contracts.ports import DataSource
from eduescale.gui.services.district_map_catalog import DistrictMapCatalog
from eduescale.gui.services.school_search_service import (
    SchoolSearchRequestError,
    SchoolSearchRequestService,
)


class FakeOfflineDatabaseValidator:
    def __init__(self):
        self.validated_paths: list[Path] = []

    def validate(self, database_path: Path) -> None:
        self.validated_paths.append(database_path)


def test_prepares_local_search_request_and_resolves_location(tmp_path):
    catalog = DistrictMapCatalog()
    validator = FakeOfflineDatabaseValidator()
    service = SchoolSearchRequestService(catalog, validator)
    database_path = tmp_path / "escale.sqlite3"
    output_path = tmp_path / "schools.xlsx"
    district_code = catalog.district_codes[0]
    location = catalog.district_metadata[district_code]

    request = service.prepare(
        QUrl.fromLocalFile(str(output_path)).toString(),
        [
            {
                "district_code": district_code,
                "management": ["publica"],
                "level": ["primaria"],
            }
        ],
        "offline",
        database_path,
    )

    assert request.output_path == output_path
    assert request.offline_database_path == database_path
    assert validator.validated_paths == [database_path]
    assert request.local_filters is not None
    assert len(request.local_filters) == 1
    assert request.local_filters[0].region == location["department"]
    assert request.local_filters[0].province == location["province"]
    assert request.local_filters[0].district == location["district"]


def test_rejects_unknown_data_source():
    service = SchoolSearchRequestService(
        DistrictMapCatalog(),
        FakeOfflineDatabaseValidator(),
    )

    with pytest.raises(SchoolSearchRequestError) as error:
        service.prepare(
            "schools.xlsx",
            [],
            cast(DataSource, "unknown"),
            None,
        )

    assert error.value.title == "Fuente no válida"


def test_rejects_empty_filter_sets():
    service = SchoolSearchRequestService(
        DistrictMapCatalog(),
        FakeOfflineDatabaseValidator(),
    )

    with pytest.raises(SchoolSearchRequestError) as error:
        service.prepare("schools.xlsx", [], "online", None)

    assert error.value.title == "Filtros requeridos"
    assert error.value.details == "Agrega al menos una combinación de filtros."
