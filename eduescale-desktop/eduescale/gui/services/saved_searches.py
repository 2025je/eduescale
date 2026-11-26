from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from eduescale.domain.schools import SchoolLevel, SchoolManagementFilter

SAVED_SEARCHES_FORMAT: Literal["eduescale.saved-searches"] = "eduescale.saved-searches"
SAVED_SEARCHES_VERSION: Literal[1] = 1


class PortableSavedSearch(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1, max_length=80)
    department: str | None = None
    province: str | None = None
    district: str | None = None
    department_code: str | None = None
    province_code: str | None = None
    district_code: str | None = None
    management: tuple[SchoolManagementFilter, ...] = ()
    levels: tuple[SchoolLevel, ...] = ()

    @field_validator("name")
    @classmethod
    def normalize_name(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("El nombre de la búsqueda no puede estar vacío.")
        return normalized

    @field_validator("department", "province", "district")
    @classmethod
    def normalize_location_label(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None


class PortableSavedSearches(BaseModel):
    model_config = ConfigDict(extra="forbid")

    format: Literal["eduescale.saved-searches"] = SAVED_SEARCHES_FORMAT
    version: Literal[1, 2] = SAVED_SEARCHES_VERSION
    searches: list[PortableSavedSearch] = Field(min_length=1, max_length=500)

    @model_validator(mode="after")
    def validate_version(self) -> "PortableSavedSearches":
        if self.version == 1 and any(
            set(search.management).difference({"publica", "privada"})
            or set(search.levels).difference({"primaria", "secundaria", "cetpro"})
            for search in self.searches
        ):
            raise ValueError("La versión 1 no admite estos filtros.")
        return self
