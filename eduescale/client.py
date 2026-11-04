from typing import Literal
from xml.etree import ElementTree

import requests
from pydantic import BaseModel, ConfigDict, field_validator

API_URL = "https://escale.minedu.gob.pe/padron/rest"
REQUEST_TIMEOUT = 30


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


MANAGEMENT_CODES = {
    "publica": ("A1", "A2", "A3", "A4"),
    "privada": ("B1", "B2", "B3", "B4", "B5", "B6"),
}
LEVEL_CODES = {
    "inicial": ("A1", "A2", "A3", "A5"),
    "primaria": ("B0",),
    "secundaria": ("F0",),
}


class SchoolFilters(BaseModel):
    model_config = ConfigDict(frozen=True)

    region_code: str = ""
    province_code: str = ""
    district_code: str = ""
    management: tuple[Literal["publica", "privada"], ...] = ()
    level: tuple[Literal["inicial", "primaria", "secundaria"], ...] = ()

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


def _get_xml(
    path: str,
    params: list[tuple[str, str]] | dict[str, str] | None = None,
) -> ElementTree.Element:
    response = requests.get(
        f"{API_URL}/{path}",
        params=params,
        headers={"User-Agent": "eduescale/0.1"},
        timeout=REQUEST_TIMEOUT,
    )
    response.raise_for_status()
    return ElementTree.fromstring(response.content)


def _locations(
    path: str,
    params: list[tuple[str, str]] | dict[str, str] | None,
    item_tag: str,
    code_tag: str,
    name_tag: str,
) -> list[tuple[str, str]]:
    root = _get_xml(path, params)
    return [
        (
            item.findtext(code_tag, default="").strip(),
            item.findtext(name_tag, default="").strip(),
        )
        for item in root.findall(f"./{item_tag}")
        if item.findtext(code_tag, default="").strip()
    ]


def fetch_regions() -> list[tuple[str, str]]:
    return _locations("regiones", None, "region", "idRegion", "nombreRegion")


def fetch_provinces(region_code: str) -> list[tuple[str, str]]:
    return _locations(
        "provincias",
        {"idRegion": region_code},
        "provincia",
        "idProvincia",
        "nombreProvincia",
    )


def fetch_districts(province_code: str) -> list[tuple[str, str]]:
    return _locations(
        "distritos",
        {"idProvincia": province_code},
        "distrito",
        "idDistrito",
        "nombreDistrito",
    )


def build_search_params(
    filters: SchoolFilters, offset: int = 0
) -> list[tuple[str, str]]:
    ubigeo = filters.district_code or filters.province_code or filters.region_code
    params = [("start", str(offset)), ("estados", "1")]
    if ubigeo:
        params.append(("ubigeo", ubigeo))
    params.extend(
        ("gestiones", code)
        for code in _selection_codes(filters.management, MANAGEMENT_CODES)
    )
    params.extend(
        ("niveles", code) for code in _selection_codes(filters.level, LEVEL_CODES)
    )
    return params


def _selection_codes(selection, code_mapping):
    return tuple(code for value in selection for code in code_mapping.get(value, ()))


def _text(element: ElementTree.Element, path: str) -> str:
    value = element.findtext(path, default="")
    return value.strip()


def _integer(element: ElementTree.Element, path: str) -> int | None:
    value = _text(element, path)
    return int(value) if value else None


def _float(element: ElementTree.Element, path: str) -> float | None:
    value = _text(element, path)
    return float(value) if value else None


def parse_institutions(xml: bytes) -> list[dict[str, object]]:
    root = ElementTree.fromstring(xml)
    records: list[dict[str, object]] = []
    for item in root.findall("./items"):
        record = SchoolRecord(
            nombre=_text(item, "cenEdu"),
            direccion=_text(item, "dirCen"),
            localidad=_text(item, "localidad"),
            cp=_text(item, "cenPob"),
            turno=_text(item, "turno/valor"),
            alumnos=_integer(item, "estadistica/talumno"),
            secciones=_integer(item, "estadistica/tseccion"),
            longitud=_float(item, "nlongIE"),
            latitud=_float(item, "nlatIE"),
        )
        records.append(record.model_dump())
    return records
