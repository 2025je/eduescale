import argparse
import json
import math
import sqlite3
import struct
from pathlib import Path

EXPECTED_DISTRICTS = 1892
SIMPLIFICATION_TOLERANCE = 0.0004

SANTA_ROSA_UTM = (
    (373629, 9548106),
    (374013, 9547508),
    (375131, 9546784),
    (376143, 9546481),
    (376973, 9546323),
    (378233, 9545923),
    (379117, 9545748),
    (380211, 9546057),
    (381242, 9546801),
    (381976, 9547080),
    (391999, 9527007),
    (391346, 9527654),
    (391223, 9527747),
    (391034, 9528039),
    (391048, 9528361),
    (391080, 9528583),
    (391167, 9528845),
    (391215, 9529059),
    (391215, 9529210),
    (391152, 9529432),
    (391191, 9529623),
    (391239, 9529960),
    (391054, 9530648),
    (389162, 9530924),
    (388679, 9531669),
    (384201, 9533289),
    (382179, 9534252),
    (380411, 9535435),
    (376200, 9535937),
    (371548, 9537853),
    (368952, 9538529),
    (366791, 9541517),
    (367725, 9545099),
)

ALTO_TRUJILLO_UTM = (
    (716500, 9108800),
    (717365, 9109924),
    (721500, 9112400),
    (723185, 9111004),
    (720910, 9108871),
    (720391, 9107894),
    (720308, 9107879),
    (718038, 9107463),
    (716795, 9107236),
    (716769, 9108156),
    (716722, 9108160),
    (716722, 9108328),
    (716797, 9108330),
    (716838, 9108327),
    (716839, 9108355),
    (716810, 9108360),
    (716810, 9108386),
    (716805, 9108401),
    (716803, 9108440),
    (716734, 9108412),
    (716685, 9108420),
    (716642, 9108460),
)


def _read_uint32(data: bytes, offset: int, byte_order: str) -> tuple[int, int]:
    return struct.unpack_from(f"{byte_order}I", data, offset)[0], offset + 4


def _read_wkb(
    data: bytes, offset: int
) -> tuple[list[list[list[tuple[float, float]]]], int]:
    byte_order = "<" if data[offset] == 1 else ">"
    offset += 1
    geometry_type, offset = _read_uint32(data, offset, byte_order)
    geometry_type &= 0xFF

    if geometry_type == 6:
        polygon_count, offset = _read_uint32(data, offset, byte_order)
        polygons = []
        for _ in range(polygon_count):
            polygon, offset = _read_wkb(data, offset)
            polygons.extend(polygon)
        return polygons, offset

    if geometry_type != 3:
        raise ValueError(f"Tipo WKB no admitido: {geometry_type}")

    ring_count, offset = _read_uint32(data, offset, byte_order)
    rings = []
    for _ in range(ring_count):
        point_count, offset = _read_uint32(data, offset, byte_order)
        ring = []
        for _ in range(point_count):
            longitude, latitude = struct.unpack_from(f"{byte_order}dd", data, offset)
            offset += 16
            ring.append((longitude, latitude))
        rings.append(ring)
    return [rings], offset


def read_geopackage_geometry(blob: bytes) -> list[list[list[tuple[float, float]]]]:
    if blob[:2] != b"GP":
        raise ValueError("La geometría no tiene una cabecera GeoPackage válida.")
    flags = blob[3]
    envelope_type = (flags >> 1) & 0b111
    envelope_sizes = {0: 0, 1: 32, 2: 48, 3: 48, 4: 64}
    try:
        envelope_size = envelope_sizes[envelope_type]
    except KeyError as error:
        raise ValueError(
            f"Tipo de envolvente GeoPackage no admitido: {envelope_type}"
        ) from error
    polygons, _ = _read_wkb(blob, 8 + envelope_size)
    return polygons


def _point_segment_distance_squared(
    point: tuple[float, float],
    start: tuple[float, float],
    end: tuple[float, float],
) -> float:
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    if dx == 0 and dy == 0:
        return (point[0] - start[0]) ** 2 + (point[1] - start[1]) ** 2
    position = max(
        0.0,
        min(
            1.0,
            ((point[0] - start[0]) * dx + (point[1] - start[1]) * dy)
            / (dx * dx + dy * dy),
        ),
    )
    nearest_x = start[0] + position * dx
    nearest_y = start[1] + position * dy
    return (point[0] - nearest_x) ** 2 + (point[1] - nearest_y) ** 2


def _simplify_open_line(
    points: list[tuple[float, float]],
    tolerance_squared: float,
) -> list[tuple[float, float]]:
    if len(points) <= 2:
        return points

    keep = {0, len(points) - 1}
    pending = [(0, len(points) - 1)]
    while pending:
        start, end = pending.pop()
        farthest_index = start
        farthest_distance = tolerance_squared
        for index in range(start + 1, end):
            distance = _point_segment_distance_squared(
                points[index],
                points[start],
                points[end],
            )
            if distance > farthest_distance:
                farthest_index = index
                farthest_distance = distance
        if farthest_index != start:
            keep.add(farthest_index)
            pending.extend(((start, farthest_index), (farthest_index, end)))
    return [points[index] for index in sorted(keep)]


def simplify_ring(
    ring: list[tuple[float, float]],
    tolerance: float = SIMPLIFICATION_TOLERANCE,
) -> list[list[float]]:
    if len(ring) < 4:
        raise ValueError("Un anillo debe tener al menos cuatro coordenadas.")
    is_closed = ring[0] == ring[-1]
    points = ring[:-1] if is_closed else ring[:]
    if len(points) < 3:
        raise ValueError("Un anillo debe tener al menos tres vértices distintos.")

    anchor = max(
        range(1, len(points)),
        key=lambda index: (
            (points[index][0] - points[0][0]) ** 2
            + (points[index][1] - points[0][1]) ** 2
        ),
    )
    first_half = _simplify_open_line(points[: anchor + 1], tolerance**2)
    second_half = _simplify_open_line(points[anchor:] + [points[0]], tolerance**2)
    simplified = first_half[:-1] + second_half[:-1]
    if len(simplified) < 3:
        simplified = points
    closed = simplified + [simplified[0]]
    return [[round(x, 6), round(y, 6)] for x, y in closed]


def bounds_for_polygons(
    polygons: list[list[list[tuple[float, float]]]],
) -> list[float]:
    points = [point for polygon in polygons for ring in polygon for point in ring]
    return [
        min(point[0] for point in points),
        min(point[1] for point in points),
        max(point[0] for point in points),
        max(point[1] for point in points),
    ]


def utm_to_lon_lat(
    easting: float,
    northing: float,
    zone: int,
) -> tuple[float, float]:
    semi_major = 6378137.0
    eccentricity_squared = 0.0066943799901413165
    scale = 0.9996
    x = easting - 500000.0
    y = northing - 10000000.0
    meridional_arc = y / scale
    mu = meridional_arc / (
        semi_major
        * (
            1
            - eccentricity_squared / 4
            - 3 * eccentricity_squared**2 / 64
            - 5 * eccentricity_squared**3 / 256
        )
    )
    e1 = (1 - math.sqrt(1 - eccentricity_squared)) / (
        1 + math.sqrt(1 - eccentricity_squared)
    )
    phi1 = (
        mu
        + (3 * e1 / 2 - 27 * e1**3 / 32) * math.sin(2 * mu)
        + (21 * e1**2 / 16 - 55 * e1**4 / 32) * math.sin(4 * mu)
        + (151 * e1**3 / 96) * math.sin(6 * mu)
        + (1097 * e1**4 / 512) * math.sin(8 * mu)
    )
    eccentricity_prime_squared = eccentricity_squared / (1 - eccentricity_squared)
    sin_phi = math.sin(phi1)
    cos_phi = math.cos(phi1)
    tan_phi = math.tan(phi1)
    n1 = semi_major / math.sqrt(1 - eccentricity_squared * sin_phi**2)
    t1 = tan_phi**2
    c1 = eccentricity_prime_squared * cos_phi**2
    r1 = (
        semi_major
        * (1 - eccentricity_squared)
        / (1 - eccentricity_squared * sin_phi**2) ** 1.5
    )
    d = x / (n1 * scale)
    latitude = phi1 - (
        n1
        * tan_phi
        / r1
        * (
            d**2 / 2
            - (5 + 3 * t1 + 10 * c1 - 4 * c1**2 - 9 * eccentricity_prime_squared)
            * d**4
            / 24
            + (
                61
                + 90 * t1
                + 298 * c1
                + 45 * t1**2
                - 252 * eccentricity_prime_squared
                - 3 * c1**2
            )
            * d**6
            / 720
        )
    )
    central_meridian = math.radians(zone * 6 - 183)
    longitude = (
        central_meridian
        + (
            d
            - (1 + 2 * t1 + c1) * d**3 / 6
            + (
                5
                - 2 * c1
                + 28 * t1
                - 3 * c1**2
                + 8 * eccentricity_prime_squared
                + 24 * t1**2
            )
            * d**5
            / 120
        )
        / cos_phi
    )
    return math.degrees(longitude), math.degrees(latitude)


def derived_feature(
    code: str,
    district: str,
    province: str,
    department: str,
    points: tuple[tuple[int, int], ...],
    zone: int,
    law: str,
    note: str,
) -> dict[str, object]:
    ring = [utm_to_lon_lat(easting, northing, zone) for easting, northing in points]
    ring.append(ring[0])
    polygons = [[ring]]
    simplified_ring = simplify_ring(ring, tolerance=0.000001)
    return {
        "type": "Feature",
        "properties": {
            "ubigeo": code,
            "district": district,
            "province": province,
            "department": department,
            "boundary_source": law,
            "boundary_quality": "approximate",
            "boundary_note": note,
            "bounds": bounds_for_polygons(polygons),
        },
        "geometry": {
            "type": "MultiPolygon",
            "coordinates": [[simplified_ring]],
        },
    }


def official_features(geopackage: Path) -> list[dict[str, object]]:
    with sqlite3.connect(geopackage) as connection:
        rows = connection.execute(
            "SELECT ubigeo, nombdist, nombprov, nombdep, geom FROM DISTRITO"
        ).fetchall()
    features = []
    for code, district, province, department, blob in rows:
        polygons = read_geopackage_geometry(blob)
        coordinates = [
            [simplify_ring(ring) for ring in polygon] for polygon in polygons
        ]
        points = [
            (longitude, latitude)
            for polygon in polygons
            for ring in polygon
            for longitude, latitude in ring
        ]
        features.append(
            {
                "type": "Feature",
                "properties": {
                    "ubigeo": code,
                    "district": district,
                    "province": province,
                    "department": department,
                    "boundary_source": "INEI, capa distrital actualizada al 2023",
                    "boundary_quality": "official",
                    "boundary_note": "",
                    "bounds": [
                        min(point[0] for point in points),
                        min(point[1] for point in points),
                        max(point[0] for point in points),
                        max(point[1] for point in points),
                    ],
                },
                "geometry": {
                    "type": "MultiPolygon",
                    "coordinates": coordinates,
                },
            }
        )
    return features


def build_map_data(geopackage: Path) -> dict[str, object]:
    features = official_features(geopackage)
    official_codes = {feature["properties"]["ubigeo"] for feature in features}
    if len(official_codes) != len(features):
        raise ValueError("La capa INEI contiene UBIGEO duplicados.")

    derived = [
        derived_feature(
            "130112",
            "ALTO TRUJILLO",
            "TRUJILLO",
            "LA LIBERTAD",
            ALTO_TRUJILLO_UTM,
            17,
            "Ley 31644 (2022)",
            "Límite aproximado; la ley describe hitos naturales sin coordenadas "
            "completas para todos los vértices.",
        ),
        derived_feature(
            "160405",
            "SANTA ROSA DE LORETO",
            "MARISCAL RAMÓN CASTILLA",
            "LORETO",
            SANTA_ROSA_UTM,
            19,
            "Ley 32403 (2025)",
            "Polígono aproximado a partir de los vértices UTM descritos en la ley; "
            "los tramos de ríos y frontera se interpolan.",
        ),
    ]
    if official_codes.intersection(
        feature["properties"]["ubigeo"] for feature in derived
    ):
        raise ValueError("Un distrito derivado ya existe en la capa INEI.")

    features.extend(derived)
    features.sort(
        key=lambda feature: (
            feature["properties"]["boundary_quality"] == "official",
            feature["properties"]["ubigeo"],
        ),
        reverse=True,
    )
    codes = [feature["properties"]["ubigeo"] for feature in features]
    if len(codes) != EXPECTED_DISTRICTS or len(set(codes)) != EXPECTED_DISTRICTS:
        raise ValueError(
            f"Se esperaban {EXPECTED_DISTRICTS} UBIGEO únicos; "
            f"se generaron {len(codes)}."
        )
    return {
        "metadata": {
            "official_district_count": len(official_codes),
            "derived_district_count": len(derived),
            "coverage_count": len(codes),
            "official_source": "https://ide.inei.gob.pe/files/Distrito.rar",
            "official_source_version": "Actualizado al 2023",
        },
        "type": "FeatureCollection",
        "features": features,
    }


def build_hierarchy_data(map_data: dict[str, object]) -> dict[str, object]:
    from shapely import make_valid
    from shapely.geometry import mapping, shape
    from shapely.ops import unary_union

    features = map_data["features"]
    department_groups: dict[str, list[dict]] = {}
    province_groups: dict[str, list[dict]] = {}
    for feature in features:
        properties = feature["properties"]
        ubigeo = properties["ubigeo"]
        department_groups.setdefault(ubigeo[:2], []).append(feature)
        province_groups.setdefault(ubigeo[:4], []).append(feature)

    def merged_boundaries(
        groups: dict[str, list[dict]], level: str
    ) -> list[dict[str, object]]:
        merged_features = []
        for code, group in sorted(groups.items()):
            first = group[0]["properties"]
            department_code = code[:2]
            geometries = [shape(feature["geometry"]) for feature in group]
            merged = unary_union(
                [
                    geometry if geometry.is_valid else make_valid(geometry)
                    for geometry in geometries
                ]
            ).simplify(SIMPLIFICATION_TOLERANCE, preserve_topology=True)
            if merged.geom_type not in {"Polygon", "MultiPolygon"}:
                raise ValueError(
                    f"Geometría disuelta no válida para {level} {code}: "
                    f"{merged.geom_type}"
                )
            if merged.is_empty or not merged.is_valid:
                raise ValueError(f"Geometría disuelta inválida para {level} {code}.")
            properties = {
                "code": code,
                "name": (
                    first["department"] if level == "department" else first["province"]
                ),
                "department": first["department"],
                "departmentCode": department_code,
                "boundary_quality": (
                    "approximate"
                    if any(
                        feature["properties"]["boundary_quality"] != "official"
                        for feature in group
                    )
                    else "official"
                ),
            }
            if level == "province":
                properties["provinceCode"] = code
            merged_features.append(
                {
                    "type": "Feature",
                    "id": code,
                    "properties": properties,
                    "geometry": mapping(merged),
                }
            )
        return merged_features

    departments = merged_boundaries(department_groups, "department")
    provinces = merged_boundaries(province_groups, "province")
    if len(departments) != 25 or len(provinces) != 196:
        raise ValueError(
            "La capa jerárquica debe contener 25 departamentos y 196 provincias; "
            f"se generaron {len(departments)} y {len(provinces)}."
        )
    return {
        "metadata": {
            "department_count": len(departments),
            "province_count": len(provinces),
        },
        "departments": {"type": "FeatureCollection", "features": departments},
        "provinces": {"type": "FeatureCollection", "features": provinces},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("geopackage", type=Path)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).parents[1]
        / "eduescale"
        / "gui"
        / "assets"
        / "districts_map.json",
    )
    parser.add_argument("--hierarchy-output", type=Path)
    arguments = parser.parse_args()
    data = build_map_data(arguments.geopackage)
    hierarchy_data = build_hierarchy_data(data)
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(
        json.dumps(data, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    hierarchy_output = arguments.hierarchy_output or arguments.output.with_name(
        "districts_map_hierarchy.json"
    )
    hierarchy_output.parent.mkdir(parents=True, exist_ok=True)
    hierarchy_output.write_text(
        json.dumps(hierarchy_data, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(
        f"Escritos {data['metadata']['coverage_count']} UBIGEO en {arguments.output}; "
        f"{hierarchy_data['metadata']['department_count']} departamentos y "
        f"{hierarchy_data['metadata']['province_count']} provincias en "
        f"{hierarchy_output}."
    )


if __name__ == "__main__":
    main()
