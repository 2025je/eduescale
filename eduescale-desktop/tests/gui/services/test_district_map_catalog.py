from eduescale.gui.services.district_map_catalog import DistrictMapCatalog


def test_catalog_loads_complete_hierarchical_map_data():
    catalog = DistrictMapCatalog()
    district = catalog.district_properties("150101")

    assert len(catalog.district_codes) == 1892
    assert len(catalog.map_department_metadata) == 25
    assert len(catalog.map_province_metadata) == 196
    assert district is not None
    assert district["provinceCode"] == "1501"


def test_effective_district_codes_follow_selected_hierarchy():
    catalog = DistrictMapCatalog()

    department_districts = catalog.effective_district_codes(["15"], [], [])
    province_districts = catalog.effective_district_codes([], ["1501"], [])

    assert "150101" in department_districts
    assert "130112" not in department_districts
    assert province_districts
    assert all(code.startswith("1501") for code in province_districts)
    assert catalog.effective_district_codes([], [], ["150101", "150101"]) == ["150101"]
