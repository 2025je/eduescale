from pydantic import BaseModel, ConfigDict, Field


class LocationSchoolCount(BaseModel):
    model_config = ConfigDict(frozen=True)

    region: str
    province: str
    district: str
    schools: int = Field(ge=0)


class OfflineDatabaseLocationSummary(BaseModel):
    model_config = ConfigDict(frozen=True)

    total_schools: int = Field(default=0, ge=0)
    department_count: int = Field(default=0, ge=0)
    province_count: int = Field(default=0, ge=0)
    district_count: int = Field(default=0, ge=0)
    locations: tuple[LocationSchoolCount, ...] = ()
