import hashlib
import json
import math
from dataclasses import dataclass, field

MAP_RECORD_FIELDS = (
    "nombre",
    "direccion",
    "localidad",
    "cp",
    "turno",
    "alumnos",
    "secciones",
    "longitud",
    "latitud",
    "distrito",
    "provincia",
    "region",
    "gestion",
    "nivel",
    "codigo_local",
)
LEVEL_ORDER = ("primaria", "secundaria", "cetpro")


def _text(record: dict[str, object], key: str) -> str:
    value = record.get(key)
    return value.strip() if isinstance(value, str) else ""


def _number(value: object) -> int | float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    return value if math.isfinite(value) else None


def _sum(current: float | None, value: object) -> int | float | None:
    number = _number(value)
    if number is None:
        return current
    return (current or 0) + number


@dataclass
class _Offer:
    alumnos: int | float | None = None
    secciones: int | float | None = None


@dataclass
class _School:
    school_id: str
    codigo_local: str
    nombre: str
    direccion: str
    localidad: str
    centro_poblado: str
    departamento: str
    provincia: str
    distrito: str
    gestion: str
    longitud: float | None = None
    latitud: float | None = None
    alumnos: int | float | None = None
    secciones: int | float | None = None
    niveles: list[str] = field(default_factory=list)
    turnos: list[str] = field(default_factory=list)
    oferta: dict[tuple[str, str], _Offer] = field(default_factory=dict)

    def add(self, record: dict[str, object]) -> None:
        level = _text(record, "nivel")
        shift = _text(record, "turno")
        if level and level not in self.niveles:
            self.niveles.append(level)
        if shift and shift not in self.turnos:
            self.turnos.append(shift)

        students = _number(record.get("alumnos"))
        sections = _number(record.get("secciones"))
        self.alumnos = _sum(self.alumnos, students)
        self.secciones = _sum(self.secciones, sections)
        if level or shift:
            offer = self.oferta.setdefault((level, shift), _Offer())
            offer.alumnos = _sum(offer.alumnos, students)
            offer.secciones = _sum(offer.secciones, sections)

        longitude = _number(record.get("longitud"))
        latitude = _number(record.get("latitud"))
        if (
            self.longitud is None
            and longitude is not None
            and -180 <= longitude <= 180
            and latitude is not None
            and -90 <= latitude <= 90
        ):
            self.longitud = longitude
            self.latitud = latitude

    def details(self) -> dict[str, object]:
        level_order = {level: index for index, level in enumerate(LEVEL_ORDER)}
        offers = [
            {
                "nivel": level,
                "turno": shift,
                "alumnos": offer.alumnos,
                "secciones": offer.secciones,
            }
            for (level, shift), offer in sorted(
                self.oferta.items(),
                key=lambda item: (
                    level_order.get(item[0][0], len(LEVEL_ORDER)),
                    item[0][1],
                ),
            )
        ]
        return {
            "schoolId": self.school_id,
            "codigoLocal": self.codigo_local,
            "nombre": self.nombre,
            "direccion": self.direccion,
            "localidad": self.localidad,
            "centroPoblado": self.centro_poblado,
            "departamento": self.departamento,
            "provincia": self.provincia,
            "distrito": self.distrito,
            "gestion": self.gestion,
            "longitud": self.longitud,
            "latitud": self.latitud,
            "alumnos": self.alumnos,
            "secciones": self.secciones,
            "niveles": sorted(
                self.niveles,
                key=lambda level: level_order.get(level, len(LEVEL_ORDER)),
            ),
            "turnos": self.turnos,
            "ofertaEducativa": offers,
        }


class SchoolMapAccumulator:
    def __init__(self) -> None:
        self._schools: dict[str, _School] = {}

    def clear(self) -> None:
        self._schools.clear()

    def add_batch(
        self,
        records: list[dict[str, object]],
    ) -> list[dict[str, object]]:
        changed: dict[str, _School] = {}
        for record in records:
            local_code = _text(record, "codigo_local")
            identity = [
                local_code,
                _text(record, "region"),
                _text(record, "provincia"),
                _text(record, "distrito"),
                _text(record, "nombre"),
                _text(record, "gestion"),
            ]
            if not local_code:
                identity.append(
                    [record.get(field) for field in MAP_RECORD_FIELDS]
                )
            identity_json = json.dumps(
                identity,
                ensure_ascii=False,
                separators=(",", ":"),
            )
            school_id = hashlib.sha256(identity_json.encode("utf-8")).hexdigest()
            school = self._schools.get(school_id)
            if school is None:
                school = _School(
                    school_id=school_id,
                    codigo_local=local_code,
                    nombre=_text(record, "nombre"),
                    direccion=_text(record, "direccion"),
                    localidad=_text(record, "localidad"),
                    centro_poblado=_text(record, "cp"),
                    departamento=_text(record, "region"),
                    provincia=_text(record, "provincia"),
                    distrito=_text(record, "distrito"),
                    gestion=_text(record, "gestion"),
                )
                self._schools[school_id] = school
            school.add(record)
            changed[school_id] = school

        return [
            school.details()
            for school in changed.values()
            if school.longitud is not None and school.latitud is not None
        ]
