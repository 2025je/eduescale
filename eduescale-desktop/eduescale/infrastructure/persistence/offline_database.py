import sqlite3
from pathlib import Path

from sqlalchemy import Column, Index, MetaData, Table, Text, create_engine
from sqlalchemy.engine import URL, Engine
from sqlalchemy.pool import NullPool

from eduescale.domain.search_text import normalize_search_text

metadata = MetaData()
records = Table(
    "records",
    metadata,
    Column("region", Text, nullable=False),
    Column("province", Text, nullable=False),
    Column("district", Text, nullable=False),
    Column("payload", Text, nullable=False),
    Column("region_key", Text, nullable=False),
    Column("province_key", Text, nullable=False),
    Column("district_key", Text, nullable=False),
    Column("management_key", Text, nullable=False),
    Column("level_key", Text, nullable=False),
)
Index(
    "ix_records_location",
    records.c.region_key,
    records.c.province_key,
    records.c.district_key,
)
Index(
    "ix_records_management_level",
    records.c.management_key,
    records.c.level_key,
)
database_metadata = Table(
    "metadata",
    metadata,
    Column("key", Text, primary_key=True),
    Column("value", Text, nullable=False),
)

SEARCH_KEY_COLUMNS = {
    "region_key",
    "province_key",
    "district_key",
    "management_key",
    "level_key",
}


def create_database_engine(database_path: Path, *, read_only: bool = False) -> Engine:
    if read_only:
        uri = f"{database_path.resolve().as_uri()}?mode=ro"
        return create_engine(
            "sqlite://",
            creator=lambda: sqlite3.connect(uri, uri=True),
            poolclass=NullPool,
        )
    return create_engine(
        URL.create("sqlite", database=str(database_path)),
        poolclass=NullPool,
    )


def record_search_keys(
    region: str,
    province: str,
    district: str,
    payload: dict[str, object],
) -> dict[str, str]:
    return {
        "region_key": normalize_search_text(region),
        "province_key": normalize_search_text(province),
        "district_key": normalize_search_text(district),
        "management_key": normalize_search_text(str(payload.get("gestion") or "")),
        "level_key": normalize_search_text(str(payload.get("nivel") or "")),
    }
