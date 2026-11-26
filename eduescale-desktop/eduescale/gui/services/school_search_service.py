import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from pydantic import TypeAdapter, ValidationError
from PySide6.QtCore import QUrl

from eduescale.application.errors import DataAccessError
from eduescale.domain.offline_search import LocalSearchFilter
from eduescale.domain.schools import SchoolFilters
from eduescale.gui.contracts.ports import (
    DataSource,
    DistrictMapCatalogPort,
    OfflineDatabaseValidationPort,
)

LOGGER = logging.getLogger(__name__)
FILTER_SETS_ADAPTER = TypeAdapter(list[SchoolFilters])


class SchoolSearchRequestError(Exception):
    def __init__(self, title: str, details: str):
        super().__init__(details)
        self.title = title
        self.details = details


@dataclass(frozen=True)
class SchoolSearchRequest:
    output_path: Path
    filter_sets: list[SchoolFilters]
    offline_database_path: Path | None
    local_filters: list[LocalSearchFilter] | None
    summary: str


class SchoolSearchRequestServicePort(Protocol):
    def prepare(
        self,
        output_url: str,
        filter_sets: list[dict[str, object]],
        data_source: DataSource,
        offline_database_path: Path | None,
    ) -> SchoolSearchRequest: ...


class SchoolSearchRequestService:
    def __init__(
        self,
        district_map_catalog: DistrictMapCatalogPort,
        offline_database_use_cases: OfflineDatabaseValidationPort,
    ):
        self._district_map_catalog = district_map_catalog
        self._offline_database_use_cases = offline_database_use_cases

    def prepare(
        self,
        output_url: str,
        filter_sets: list[dict[str, object]],
        data_source: DataSource,
        offline_database_path: Path | None,
    ) -> SchoolSearchRequest:
        if data_source not in {"online", "offline"}:
            raise SchoolSearchRequestError(
                "Fuente no válida",
                "Selecciona ESCALE en línea o una base local.",
            )
        try:
            validated_filter_sets = FILTER_SETS_ADAPTER.validate_python(filter_sets)
        except ValidationError as error:
            raise SchoolSearchRequestError("Filtros no válidos", str(error)) from error

        if not validated_filter_sets:
            raise SchoolSearchRequestError(
                "Filtros requeridos",
                "Agrega al menos una combinación de filtros.",
            )

        invalid_filter_sets = []
        for index, filters in enumerate(validated_filter_sets, start=1):
            missing = []
            if not filters.management:
                missing.append("Gestión")
            if not filters.level:
                missing.append("Nivel educativo")
            if missing:
                invalid_filter_sets.append(f"Filtro {index}: {', '.join(missing)}")
        if invalid_filter_sets:
            raise SchoolSearchRequestError(
                "Selección requerida",
                "Cada combinación debe incluir al menos una opción en Gestión "
                "y Nivel educativo. " + "; ".join(invalid_filter_sets),
            )

        local_filters: list[LocalSearchFilter] | None = None
        if data_source == "offline":
            if offline_database_path is None:
                raise SchoolSearchRequestError(
                    "Base offline requerida",
                    "Selecciona la base SQLite descargada de EduEscale.",
                )
            try:
                self._offline_database_use_cases.validate(offline_database_path)
                local_filters = self._local_search_filters(validated_filter_sets)
            except (DataAccessError, OSError, TypeError, ValueError) as error:
                LOGGER.error(
                    "Cannot search offline database %s: %s",
                    offline_database_path,
                    error,
                )
                raise SchoolSearchRequestError(
                    "No se pudo usar la base offline",
                    str(error),
                ) from error

        selected_url = QUrl(output_url)
        if selected_url.isLocalFile():
            output_path = selected_url.toLocalFile()
        elif selected_url.scheme():
            raise SchoolSearchRequestError(
                "Ruta no válida",
                "Selecciona un archivo local para guardar el Excel.",
            )
        else:
            output_path = selected_url.toString()
        if not output_path:
            raise SchoolSearchRequestError(
                "Ruta no válida",
                "Selecciona un archivo Excel válido.",
            )

        path = Path(output_path)
        if path.suffix.lower() != ".xlsx":
            path = path.with_suffix(".xlsx")
        return SchoolSearchRequest(
            output_path=path,
            filter_sets=validated_filter_sets,
            offline_database_path=offline_database_path,
            local_filters=local_filters,
            summary=self._summarize_searches(validated_filter_sets),
        )

    def _local_search_filters(
        self,
        filter_sets: list[SchoolFilters],
    ) -> list[LocalSearchFilter]:
        local_filters = []
        district_metadata = self._district_map_catalog.district_metadata
        province_metadata = self._district_map_catalog.map_province_metadata
        department_metadata = self._district_map_catalog.map_department_metadata
        for filters in filter_sets:
            region = ""
            province = ""
            district = ""
            if filters.district_code:
                location = district_metadata.get(filters.district_code)
                if location is None:
                    raise ValueError(
                        f"Ubigeo distrital no reconocido: {filters.district_code}."
                    )
                region = str(location["department"])
                province = location["province"]
                district = location["district"]
            elif filters.province_code:
                province_location = province_metadata.get(filters.province_code)
                if province_location is None:
                    raise ValueError(
                        f"Ubigeo provincial no reconocido: {filters.province_code}."
                    )
                region = str(province_location["department"])
                province = str(province_location["name"])
            elif filters.region_code:
                location = department_metadata.get(filters.region_code)
                if location is None:
                    raise ValueError(
                        f"Ubigeo departamental no reconocido: {filters.region_code}."
                    )
                region = location["name"]
            local_filters.append(
                LocalSearchFilter(
                    region=region,
                    province=province,
                    district=district,
                    management=tuple(filters.management),
                    level=tuple(filters.level),
                )
            )
        return local_filters

    def _summarize_searches(self, filter_sets: list[SchoolFilters]) -> str:
        locations = []
        management: set[str] = set()
        levels: set[str] = set()
        district_metadata = self._district_map_catalog.district_metadata
        province_metadata = self._district_map_catalog.map_province_metadata
        department_metadata = self._district_map_catalog.map_department_metadata
        for filters in filter_sets:
            if filters.district_code in district_metadata:
                details = district_metadata[filters.district_code]
                location = " / ".join(
                    (details["department"], details["province"], details["district"])
                )
            elif filters.province_code in province_metadata:
                province = province_metadata[filters.province_code]
                location = f"{province['department']} / {province['name']}"
            elif filters.region_code in department_metadata:
                location = department_metadata[filters.region_code]["name"]
            else:
                location = "Todo el Perú"
            if location not in locations:
                locations.append(location)
            management.update(filters.management)
            levels.update(filters.level)
        location_summary = ", ".join(locations[:5])
        if len(locations) > 5:
            location_summary += f" y {len(locations) - 5} más"
        return (
            f"{len(filter_sets)} combinaciones · {location_summary} · "
            f"Gestión: {', '.join(sorted(management))} · "
            f"Nivel: {', '.join(sorted(levels))}"
        )
