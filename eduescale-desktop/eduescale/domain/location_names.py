from eduescale.domain.search_text import normalize_search_text

DEPARTMENT_DISPLAY_NAMES = {
    "ancash": "ÁNCASH",
    "apurimac": "APURÍMAC",
    "huanuco": "HUÁNUCO",
    "junin": "JUNÍN",
    "san martin": "SAN MARTÍN",
}


def canonical_department_name(value: str) -> str:
    return DEPARTMENT_DISPLAY_NAMES.get(normalize_search_text(value), value)
