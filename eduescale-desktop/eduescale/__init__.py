"""Cliente para consultar el padrón oficial de ESCALE."""

from eduescale.client import (
    SchoolFilters,
    SchoolRecord,
    build_search_params,
    fetch_districts,
    fetch_provinces,
    fetch_regions,
    parse_institutions,
)

__all__ = [
    "SchoolFilters",
    "SchoolRecord",
    "build_search_params",
    "fetch_districts",
    "fetch_provinces",
    "fetch_regions",
    "parse_institutions",
]
