from xml.etree import ElementTree

import requests

from eduescale.application.errors import LocationFetchError
from eduescale.infrastructure.escale.client import (
    fetch_districts,
    fetch_provinces,
    fetch_regions,
)


class ESCALELocationProvider:
    def fetch(self, kind: str, parent_code: str) -> list[tuple[str, str]]:
        try:
            if kind == "regions":
                return fetch_regions()
            if kind == "provinces":
                return fetch_provinces(parent_code)
            if kind == "districts":
                return fetch_districts(parent_code)
            raise ValueError(f"Tipo de ubicación desconocido: {kind}")
        except (requests.RequestException, ElementTree.ParseError) as error:
            raise LocationFetchError(str(error)) from error
