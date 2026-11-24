import json

from eduescale.gui.adapters.resources import read_resource_bytes, resource_path
from eduescale.gui.contracts.ports import ProvinceMapMetadata


class DistrictMapCatalog:
    def __init__(self):
        map_path = resource_path("assets/districts_map.json")
        map_data = json.loads(read_resource_bytes("assets/districts_map.json"))
        features = map_data.get("features")
        if map_data.get("type") != "FeatureCollection" or not isinstance(
            features, list
        ):
            raise ValueError(f"Invalid district map data in {map_path}")

        self.district_metadata: dict[str, dict[str, str]] = {}
        for feature in features:
            properties = feature["properties"]
            code = properties["ubigeo"]
            if code in self.district_metadata:
                raise ValueError(f"Duplicate district code {code} in {map_path}")
            self.district_metadata[code] = {
                "department": properties["department"],
                "departmentCode": code[:2],
                "province": properties["province"],
                "provinceCode": code[:4],
                "district": properties["district"],
                "boundary_quality": properties["boundary_quality"],
            }
        if len(self.district_metadata) != 1892:
            raise ValueError(f"Incomplete district map data in {map_path}")

        self.district_codes = sorted(self.district_metadata)
        self.district_codes_by_province: dict[str, list[str]] = {}
        for code in self.district_codes:
            self.district_codes_by_province.setdefault(code[:4], []).append(code)

        hierarchy_path = resource_path("assets/districts_map_hierarchy.json")
        hierarchy_data = json.loads(
            read_resource_bytes("assets/districts_map_hierarchy.json")
        )
        department_features = hierarchy_data["departments"]["features"]
        province_features = hierarchy_data["provinces"]["features"]
        if (
            hierarchy_data.get("metadata", {}).get("department_count") != 25
            or hierarchy_data.get("metadata", {}).get("province_count") != 196
            or len(department_features) != 25
            or len(province_features) != 196
        ):
            raise ValueError(f"Incomplete hierarchy map data in {hierarchy_path}")

        self.map_department_metadata = {
            feature["properties"]["code"]: {
                "name": feature["properties"]["name"],
            }
            for feature in department_features
        }
        self.map_province_metadata: dict[str, ProvinceMapMetadata] = {
            feature["properties"]["code"]: {
                "name": feature["properties"]["name"],
                "department": feature["properties"]["department"],
                "departmentCode": feature["properties"]["departmentCode"],
                "districtCount": len(
                    self.district_codes_by_province.get(
                        feature["properties"]["code"], []
                    )
                ),
            }
            for feature in province_features
        }
        if (
            len(self.map_department_metadata) != 25
            or len(self.map_province_metadata) != 196
            or any(
                province["departmentCode"] not in self.map_department_metadata
                for province in self.map_province_metadata.values()
            )
            or any(
                province["districtCount"] == 0
                for province in self.map_province_metadata.values()
            )
        ):
            raise ValueError(f"Invalid hierarchy map data in {hierarchy_path}")

    def effective_district_codes(
        self,
        department_codes: list[str],
        province_codes: list[str],
        district_codes: list[str],
    ) -> list[str]:
        if district_codes:
            return list(dict.fromkeys(str(code) for code in district_codes))

        selected_provinces = {str(code) for code in province_codes}
        if not selected_provinces:
            selected_departments = {str(code) for code in department_codes}
            selected_provinces = {
                code
                for code, province in self.map_province_metadata.items()
                if province["departmentCode"] in selected_departments
            }
        return [
            district_code
            for province_code in sorted(selected_provinces)
            for district_code in self.district_codes_by_province.get(province_code, [])
        ]

    def district_properties(self, code: str) -> dict[str, str] | None:
        return self.district_metadata.get(code)
