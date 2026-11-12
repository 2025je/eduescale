import pytest
from pydantic import ValidationError

from eduescale.gui.saved_searches import PortableSavedSearches


def test_portable_search_document_round_trips():
    document = PortableSavedSearches.model_validate(
        {
            "format": "eduescale.saved-searches",
            "version": 1,
            "searches": [
                {
                    "name": "  Lima centro  ",
                    "department": "Lima",
                    "province": "Lima",
                    "district": "Miraflores",
                    "department_code": None,
                    "province_code": None,
                    "district_code": None,
                    "management": ["publica"],
                    "levels": ["primaria", "secundaria"],
                }
            ],
        }
    )

    decoded = PortableSavedSearches.model_validate_json(document.model_dump_json())

    assert decoded.searches[0].name == "Lima centro"
    assert decoded.searches[0].department == "Lima"
    assert decoded.searches[0].management == ("publica",)
    assert decoded.searches[0].levels == ("primaria", "secundaria")


@pytest.mark.parametrize(
    "document",
    [
        {
            "format": "eduescale.saved-searches",
            "version": 2,
            "searches": [{"name": "Lima"}],
        },
        {
            "format": "eduescale.saved-searches",
            "version": 1,
            "searches": [{"name": " ", "management": ["publica"]}],
        },
        {
            "format": "eduescale.saved-searches",
            "version": 1,
            "searches": [{"name": "Lima", "levels": ["inicial"]}],
        },
        {
            "format": "eduescale.saved-searches",
            "version": 1,
            "searches": [{"name": "Lima", "unexpected": True}],
        },
    ],
)
def test_portable_search_document_rejects_invalid_entries(document):
    with pytest.raises(ValidationError):
        PortableSavedSearches.model_validate(document)
