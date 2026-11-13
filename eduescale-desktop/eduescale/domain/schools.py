import math
from typing import Literal

from pydantic import BaseModel, ConfigDict, field_validator

SchoolManagement = Literal["publica", "privada"]
SchoolLevel = Literal["primaria", "secundaria", "cetpro"]


class SchoolRecord(BaseModel):
    model_config = ConfigDict(frozen=True)

    nombre: str
    direccion: str
    localidad: str
    cp: str
    turno: str
    alumnos: int | None
    secciones: int | None
    longitud: float | None
    latitud: float | None
    distrito: str = "Distrito no identificado"
    provincia: str = "Provincia no identificada"
    region: str = "Departamento no identificado"
    gestion: SchoolManagement | None = None
    nivel: SchoolLevel
    codigo_local: str = ""

    @field_validator("longitud", "latitud")
    @classmethod
    def validate_finite_coordinates(cls, value: float | None) -> float | None:
        if value is not None and not math.isfinite(value):
            raise ValueError("Las coordenadas deben ser números finitos.")
        return value


class SchoolFilters(BaseModel):
    model_config = ConfigDict(frozen=True)

    region_code: str = ""
    province_code: str = ""
    district_code: str = ""
    management: tuple[SchoolManagement, ...] = ()
    level: tuple[SchoolLevel, ...] = ()

    @field_validator("management", "level", mode="before")
    @classmethod
    def split_selections(cls, value: object) -> object:
        if isinstance(value, str):
            value = value.split(",")
        if isinstance(value, (tuple, list)):
            return tuple(
                item.strip() if isinstance(item, str) else item
                for item in value
                if not isinstance(item, str) or item.strip()
            )
        return value

    @field_validator("management", "level")
    @classmethod
    def remove_duplicate_selections(cls, value: tuple[str, ...]) -> tuple[str, ...]:
        return tuple(dict.fromkeys(value))
