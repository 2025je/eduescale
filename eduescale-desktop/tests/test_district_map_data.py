import json
from pathlib import Path

MAP_DATA_PATH = (
    Path(__file__).parents[1] / "eduescale" / "gui" / "assets" / "districts_map.json"
)
HIERARCHY_DATA_PATH = MAP_DATA_PATH.with_name("districts_map_hierarchy.json")
ESCALE_CODES_PATH = Path(__file__).parent / "data" / "escale_district_codes.txt"
MAPLIBRE_ASSETS_PATH = (
    Path(__file__).parents[1] / "eduescale" / "gui" / "assets" / "maplibre"
)


def test_district_map_covers_the_verified_escale_catalog():
    map_data = json.loads(MAP_DATA_PATH.read_text(encoding="utf-8"))
    map_codes = {feature["properties"]["ubigeo"] for feature in map_data["features"]}
    escale_codes = {
        line.strip()
        for line in ESCALE_CODES_PATH.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    }

    assert map_data["metadata"]["official_district_count"] == 1890
    assert map_data["metadata"]["coverage_count"] == 1892
    assert len(map_codes) == 1892
    assert map_codes == escale_codes


def test_derived_districts_are_identified_and_have_closed_boundaries():
    map_data = json.loads(MAP_DATA_PATH.read_text(encoding="utf-8"))
    derived = {
        feature["properties"]["ubigeo"]: feature
        for feature in map_data["features"]
        if feature["properties"]["boundary_quality"] != "official"
    }

    assert set(derived) == {"130112", "160405"}
    assert derived["130112"]["properties"]["boundary_source"] == "Ley 31644 (2022)"
    assert derived["160405"]["properties"]["boundary_source"] == "Ley 32403 (2025)"
    for feature in derived.values():
        for polygon in feature["geometry"]["coordinates"]:
            for ring in polygon:
                assert len(ring) >= 4
                assert ring[0] == ring[-1]


def test_hierarchy_map_covers_all_departments_and_provinces():
    map_data = json.loads(MAP_DATA_PATH.read_text(encoding="utf-8"))
    hierarchy = json.loads(HIERARCHY_DATA_PATH.read_text(encoding="utf-8"))
    departments = hierarchy["departments"]["features"]
    provinces = hierarchy["provinces"]["features"]
    department_codes = {feature["properties"]["code"] for feature in departments}
    province_codes = {feature["properties"]["code"] for feature in provinces}

    assert hierarchy["metadata"] == {
        "department_count": 25,
        "province_count": 196,
    }
    assert len(department_codes) == 25
    assert len(province_codes) == 196
    assert all(
        feature["properties"]["departmentCode"] in department_codes
        for feature in provinces
    )
    assert all(
        feature["properties"]["ubigeo"][:2] in department_codes
        and feature["properties"]["ubigeo"][:4] in province_codes
        for feature in map_data["features"]
    )


def test_maplibre_uses_unlabeled_basemap_and_centered_boundary_labels():
    map_page = (MAPLIBRE_ASSETS_PATH / "map.html").read_text(encoding="utf-8")

    assert "maplibre-gl.js" in map_page
    assert "maplibre-gl.css" in map_page
    assert 'fetch("../districts_map_hierarchy.json")' in map_page
    assert 'fetch("../districts_map.json")' in map_page
    assert 'https://tiles.openfreemap.org/styles/liberty' in map_page
    for style in ("bright", "positron", "dark", "fiord"):
        assert f"https://tiles.openfreemap.org/styles/{style}" in map_page
    assert "https://tile.openstreetmap.org/{z}/{x}/{y}.png" in map_page
    assert "https://a.tile.opentopomap.org/{z}/{x}/{y}.png" in map_page
    assert "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}" in map_page
    assert 'type: "raster"' in map_page
    assert "OpenStreetMap</a> contributors" in map_page
    assert "OpenTopoMap</a>" in map_page
    assert "Vantor, Earthstar Geographics, and the GIS User Community" in map_page
    assert "window.setMapStyle = function(styleName)" in map_page
    assert "map.setStyle(style)" in map_page
    assert 'layer.type === "symbol"' in map_page
    assert '"fill-opacity": [' in map_page
    assert '["boolean", ["feature-state", "selected"], false]' in map_page
    assert "0.5" in map_page
    assert 'id: "boundary-labels"' in map_page
    assert '"text-field": ["get", "mapLabel"]' in map_page
    assert '"text-halo-color": "#FFFFFF"' in map_page
    assert 'properties.mapLabel = level === "district"' in map_page
    assert "window.setMapView" in map_page
    for asset in ("maplibre-gl.js", "maplibre-gl.css", "LICENSE.txt"):
        assert (MAPLIBRE_ASSETS_PATH / asset).is_file()
