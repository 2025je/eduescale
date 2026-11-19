import json
import logging
import unicodedata
from pathlib import Path
from typing import Protocol

from pydantic import ValidationError
from PySide6.QtCore import QIODevice, QSaveFile, QUrl

from eduescale.gui.contracts.ports import DistrictMapCatalogPort
from eduescale.gui.services.saved_searches import (
    PortableSavedSearch,
    PortableSavedSearches,
)

LOGGER = logging.getLogger(__name__)
MAX_SAVED_SEARCH_FILE_BYTES = 1_048_576


class SavedSearchError(Exception):
    def __init__(self, title: str, details: str):
        super().__init__(details)
        self.title = title
        self.details = details


class PortableSavedSearchServicePort(Protocol):
    def save(
        self,
        output_url: str,
        searches: list[dict[str, object]],
    ) -> None: ...

    def load(self, input_url: str) -> list[dict[str, str | None]]: ...

    def filter_set_key(self, search: dict[str, object]) -> str: ...


class PortableSavedSearchService:
    def __init__(self, district_map_catalog: DistrictMapCatalogPort):
        self._district_map_catalog = district_map_catalog

    def save(
        self,
        output_url: str,
        searches: list[dict[str, object]],
    ) -> None:
        output_path = self._local_path(output_url)
        if output_path is None:
            raise SavedSearchError(
                "Ruta no válida",
                "Selecciona un archivo JSON local para compartir las búsquedas.",
            )
        try:
            document = PortableSavedSearches.model_validate(
                {
                    "searches": [
                        self._portable_search(search)
                        for search in self._validated_searches(searches)
                    ]
                }
            )
            self.normalize_searches(document.searches)
        except ValidationError as error:
            LOGGER.error("Refusing to export invalid saved searches: %s", error)
            raise SavedSearchError("Búsquedas no válidas", str(error)) from error
        except ValueError as error:
            LOGGER.error("Refusing to export unresolved saved searches: %s", error)
            raise SavedSearchError("Ubicación no válida", str(error)) from error

        payload = document.model_dump_json(indent=2).encode("utf-8")
        if len(payload) > MAX_SAVED_SEARCH_FILE_BYTES:
            raise SavedSearchError(
                "Archivo demasiado grande",
                "El archivo de búsquedas supera el límite de 1 MiB.",
            )
        save_file = QSaveFile(str(output_path))
        if not save_file.open(QIODevice.OpenModeFlag.WriteOnly):
            LOGGER.error("Could not open saved-search file %s.", output_path)
            raise SavedSearchError(
                "No se pudo guardar",
                f"No se pudo crear el archivo de búsquedas: "
                f"{save_file.errorString()}",
            )
        if save_file.write(payload) != len(payload) or not save_file.commit():
            error_message = save_file.errorString()
            save_file.cancelWriting()
            LOGGER.error(
                "Could not commit saved-search file %s: %s",
                output_path,
                error_message,
            )
            raise SavedSearchError(
                "No se pudo guardar",
                f"No se pudo completar el archivo de búsquedas: {error_message}",
            )
        LOGGER.info(
            "Exported %d saved searches to %s.",
            len(document.searches),
            output_path,
        )

    @staticmethod
    def _validated_searches(
        searches: list[dict[str, object]],
    ) -> list[dict[str, object]]:
        if not isinstance(searches, list) or any(
            not isinstance(search, dict) for search in searches
        ):
            raise ValueError("La lista de búsquedas tiene un formato no válido.")
        return searches

    @staticmethod
    def filter_set_key(search: dict[str, object]) -> str:
        def text(value: object) -> str:
            return value if isinstance(value, str) else ""

        def selections(value: object) -> str:
            if isinstance(value, str):
                return ",".join(sorted(value.split(",")))
            if isinstance(value, (list, tuple)):
                return ",".join(sorted(str(item) for item in value))
            return ""

        return json.dumps(
            [
                text(search.get("regionCode")),
                text(search.get("provinceCode")),
                text(search.get("districtCode")),
                selections(search.get("management")),
                selections(search.get("level")),
            ],
            ensure_ascii=False,
            separators=(",", ":"),
        )

    @staticmethod
    def _portable_search(search: dict[str, object]) -> dict[str, object]:
        def text(key: str) -> str:
            value = search.get(key)
            return value if isinstance(value, str) else ""

        def selections(key: str) -> list[str]:
            value = search.get(key)
            if isinstance(value, str):
                values = value.split(",")
            elif isinstance(value, (list, tuple)):
                values = [str(item) for item in value]
            else:
                values = []
            return [item for item in values if item]

        region_code = text("regionCode")
        province_code = text("provinceCode")
        district_code = text("districtCode")
        return {
            "name": search.get("name"),
            "department": text("regionLabel") or None,
            "province": text("provinceLabel") or None,
            "district": text("districtLabel") or None,
            "department_code": region_code or None,
            "province_code": province_code or None,
            "district_code": district_code or None,
            "management": selections("management"),
            "levels": selections("level"),
        }

    def load(self, input_url: str) -> list[dict[str, str | None]]:
        input_path = self._local_path(input_url)
        if input_path is None:
            raise SavedSearchError(
                "Ruta no válida",
                "Selecciona un archivo JSON local de búsquedas.",
            )
        try:
            if input_path.stat().st_size > MAX_SAVED_SEARCH_FILE_BYTES:
                raise ValueError("El archivo supera el límite de 1 MiB.")
            document = PortableSavedSearches.model_validate_json(
                input_path.read_bytes()
            )
            searches = self.normalize_searches(document.searches)
        except (OSError, ValidationError, ValueError) as error:
            LOGGER.error("Could not load saved searches from %s: %s", input_path, error)
            raise SavedSearchError(
                "No se pudieron importar las búsquedas",
                str(error),
            ) from error
        LOGGER.info("Imported %d saved searches from %s.", len(searches), input_path)
        return searches

    def normalize_searches(
        self,
        searches: list[PortableSavedSearch],
    ) -> list[dict[str, str | None]]:
        normalized = []
        for search in searches:
            location = self._resolve_location(search)
            normalized.append(
                {
                    "name": search.name,
                    "regionCode": location["department_code"] or "",
                    "provinceCode": location["province_code"] or "",
                    "districtCode": location["district_code"] or "",
                    "regionLabel": location["department"],
                    "provinceLabel": location["province"],
                    "districtLabel": location["district"] or "Todos los distritos",
                    "management": ",".join(search.management or ("publica", "privada")),
                    "level": ",".join(
                        search.levels or ("primaria", "secundaria", "cetpro")
                    ),
                }
            )
        return normalized

    @staticmethod
    def _local_path(file_url: str) -> Path | None:
        url = QUrl(file_url)
        return Path(url.toLocalFile()) if url.isLocalFile() else None

    def _resolve_location(
        self,
        search: PortableSavedSearch,
    ) -> dict[str, str | None]:
        requested = {
            "department": search.department,
            "province": search.province,
            "district": search.district,
        }
        codes = {
            "department_code": search.department_code,
            "province_code": search.province_code,
            "district_code": search.district_code,
        }
        if codes["district_code"]:
            properties = self._district_map_catalog.district_properties(
                codes["district_code"]
            )
            if properties is None:
                raise ValueError(
                    f"Ubigeo distrital no reconocido: {codes['district_code']}."
                )
            if (
                codes["province_code"]
                and codes["province_code"] != properties["provinceCode"]
            ):
                raise ValueError(
                    f"El ubigeo provincial {codes['province_code']} no corresponde "
                    f"al distrito {codes['district_code']}."
                )
            if (
                codes["department_code"]
                and codes["department_code"] != properties["departmentCode"]
            ):
                raise ValueError(
                    f"El ubigeo departamental {codes['department_code']} no "
                    f"corresponde al distrito {codes['district_code']}."
                )
            codes["province_code"] = (
                codes["province_code"] or properties["provinceCode"]
            )
            codes["department_code"] = (
                codes["department_code"] or properties["departmentCode"]
            )
        if codes["province_code"]:
            province = self._district_map_catalog.map_province_metadata.get(
                codes["province_code"]
            )
            if province is None:
                raise ValueError(
                    f"Ubigeo provincial no reconocido: {codes['province_code']}."
                )
            if (
                codes["department_code"]
                and codes["department_code"] != province["departmentCode"]
            ):
                raise ValueError(
                    f"El ubigeo departamental {codes['department_code']} no "
                    f"corresponde a la provincia {codes['province_code']}."
                )
            codes["department_code"] = (
                codes["department_code"] or str(province["departmentCode"])
            )
        if codes["department_code"] and (
            codes["department_code"]
            not in self._district_map_catalog.map_department_metadata
        ):
            raise ValueError(
                f"Ubigeo departamental no reconocido: {codes['department_code']}."
            )

        if not any(codes.values()) and any(requested.values()):

            def matches(actual: str, expected: str | None) -> bool:
                return expected is None or self.normalized_label(actual) == (
                    self.normalized_label(expected)
                )

            if requested["district"]:
                candidates = [
                    (code, properties)
                    for code, properties in (
                        self._district_map_catalog.district_metadata.items()
                    )
                    if matches(properties["district"], requested["district"])
                    and matches(properties["province"], requested["province"])
                    and matches(properties["department"], requested["department"])
                ]
                if len(candidates) != 1:
                    raise ValueError(
                        f"No se pudo resolver de forma única el distrito "
                        f"«{requested['district']}»."
                    )
                district_code, properties = candidates[0]
                codes["district_code"] = district_code
                codes["province_code"] = properties["provinceCode"]
                codes["department_code"] = properties["departmentCode"]
            elif requested["province"]:
                candidates = [
                    (code, properties)
                    for code, properties in (
                        self._district_map_catalog.map_province_metadata.items()
                    )
                    if matches(str(properties["name"]), requested["province"])
                    and matches(str(properties["department"]), requested["department"])
                ]
                if len(candidates) != 1:
                    raise ValueError(
                        f"No se pudo resolver de forma única la provincia "
                        f"«{requested['province']}»."
                    )
                province_code, properties = candidates[0]
                codes["province_code"] = province_code
                codes["department_code"] = str(properties["departmentCode"])
            elif requested["department"]:
                candidates = [
                    code
                    for code, properties in (
                        self._district_map_catalog.map_department_metadata.items()
                    )
                    if matches(properties["name"], requested["department"])
                ]
                if len(candidates) != 1:
                    raise ValueError(
                        f"No se pudo resolver de forma única el departamento "
                        f"«{requested['department']}»."
                    )
                codes["department_code"] = candidates[0]

        department = self._district_map_catalog.map_department_metadata.get(
            codes["department_code"] or "",
            {},
        )
        province = self._district_map_catalog.map_province_metadata.get(
            codes["province_code"] or "",
            {},
        )
        district = self._district_map_catalog.district_metadata.get(
            codes["district_code"] or "",
            {},
        )
        resolved = {
            "department": department.get("name", requested["department"]),
            "province": province.get("name", requested["province"]),
            "district": district.get("district", requested["district"]),
        }
        for key, value in requested.items():
            resolved_value = resolved[key]
            if (
                value
                and resolved_value
                and self.normalized_label(value)
                != self.normalized_label(resolved_value)
            ):
                raise ValueError(f"La etiqueta de ubicación «{value}» no coincide.")
        return {**resolved, **codes}

    @staticmethod
    def normalized_label(value: str) -> str:
        return unicodedata.normalize("NFKC", value).strip().casefold()
