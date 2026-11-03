from dataclasses import dataclass
from typing import TypedDict
from xml.etree import ElementTree

import requests


API_URL = "https://escale.minedu.gob.pe/padron/rest"
REQUEST_TIMEOUT = 30


class SchoolRecord(TypedDict):
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


@dataclass(frozen=True)
class SchoolFilters:
    region_code: str = ""
    province_code: str = ""
    district_code: str = ""
    management: str = ""
    level: str = ""


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
        (item.findtext(code_tag, default="").strip(), item.findtext(name_tag, default="").strip())
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
    params.extend(("gestiones", code) for code in MANAGEMENT_CODES.get(filters.management, ()))
    params.extend(("niveles", code) for code in LEVEL_CODES.get(filters.level, ()))
    return params


def _text(element: ElementTree.Element, path: str) -> str:
    value = element.findtext(path, default="")
    return value.strip()


def _integer(element: ElementTree.Element, path: str) -> int | None:
    value = _text(element, path)
    return int(value) if value else None


def _float(element: ElementTree.Element, path: str) -> float | None:
    value = _text(element, path)
    return float(value) if value else None


def parse_institutions(xml: bytes) -> list[SchoolRecord]:
    root = ElementTree.fromstring(xml)
    records: list[SchoolRecord] = []
    for item in root.findall("./items"):
        records.append(
            {
                "nombre": _text(item, "cenEdu"),
                "direccion": _text(item, "dirCen"),
                "localidad": _text(item, "localidad"),
                "cp": _text(item, "cenPob"),
                "turno": _text(item, "turno/valor"),
                "alumnos": _integer(item, "estadistica/talumno"),
                "secciones": _integer(item, "estadistica/tseccion"),
                "longitud": _float(item, "nlongIE"),
                "latitud": _float(item, "nlatIE"),
            }
        )
    return records
