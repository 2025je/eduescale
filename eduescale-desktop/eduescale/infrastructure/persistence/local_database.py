import json
import os
import sqlite3
import unicodedata
from collections.abc import Callable
from pathlib import Path

from sqlalchemy import (
    MetaData,
    Table,
    and_,
    func,
    insert,
    inspect,
    or_,
    select,
    true,
)
from sqlalchemy.engine import Connection
from sqlalchemy.exc import SQLAlchemyError

from eduescale.application.errors import DataAccessError, SearchCancelled
from eduescale.domain.location_names import canonical_department_name
from eduescale.domain.offline_search import LocalSearchFilter
from eduescale.domain.search_text import (
    normalize_search_text,
    normalize_search_text_legacy,
)
from eduescale.infrastructure.persistence.offline_database import (
    SEARCH_KEY_COLUMNS,
    create_database_engine,
    database_metadata,
    metadata,
    record_search_keys,
    records,
)

REQUIRED_RECORD_COLUMNS = {"region", "province", "district", "payload"}
SEARCH_BATCH_SIZE = 500
PROGRESS_INTERVAL = 100


def validate_local_database(database_path: Path) -> None:
    if not database_path.is_file():
        raise ValueError(f"No existe la base de datos: {database_path}")
    engine = create_database_engine(database_path, read_only=True)
    try:
        inspector = inspect(engine)
        table_names = set(inspector.get_table_names())
        if "records" not in table_names:
            raise ValueError("La base no contiene la tabla records de EduEscale.")
        columns = {column["name"] for column in inspector.get_columns("records")}
        missing_columns = REQUIRED_RECORD_COLUMNS - columns
        if missing_columns:
            raise ValueError(
                "La tabla records no tiene las columnas requeridas: "
                + ", ".join(sorted(missing_columns))
                + "."
            )
        records_table = Table("records", MetaData(), autoload_with=engine)
        with engine.connect() as connection:
            integrity = connection.exec_driver_sql("PRAGMA quick_check").scalar_one()
            if integrity != "ok":
                raise ValueError(f"La base SQLite no pasó quick_check: {integrity}")
            record_count = int(
                connection.execute(
                    select(func.count()).select_from(records_table)
                ).scalar_one()
            )
            if "metadata" in table_names:
                metadata_columns = {
                    column["name"] for column in inspector.get_columns("metadata")
                }
                if {"key", "value"}.issubset(metadata_columns):
                    metadata_table = Table(
                        "metadata",
                        MetaData(),
                        autoload_with=engine,
                    )
                    stored_values = dict(
                        connection.execute(
                            select(
                                metadata_table.c.key,
                                metadata_table.c.value,
                            )
                        ).all()
                    )
                    schema_version = stored_values.get("schema_version")
                    if schema_version in {"2", "3"} and not SEARCH_KEY_COLUMNS.issubset(
                        columns
                    ):
                        raise ValueError(
                            "La versión de esquema de la base local no coincide "
                            "con sus columnas."
                        )
                    unique_records = stored_values.get("unique_records")
                    if (
                        unique_records is not None
                        and int(unique_records) != record_count
                    ):
                        raise ValueError(
                            "La cantidad de colegios no coincide con la metadata "
                            "de la base local."
                        )
                    total_records = stored_values.get("total_records")
                    if (
                        total_records is not None
                        and unique_records is not None
                        and int(total_records) < int(unique_records)
                    ):
                        raise ValueError(
                            "La cantidad total de resultados es menor que los "
                            "colegios almacenados."
                        )
                    pages = stored_values.get("pages")
                    verified_pages = stored_values.get("verified_pages")
                    if (
                        pages is not None
                        and verified_pages is not None
                        and int(pages) != int(verified_pages)
                    ):
                        raise ValueError(
                            "La cantidad de páginas no coincide con el diagnóstico "
                            "de integridad."
                        )
                    if int(stored_values.get("failed_pages", "0")) != 0:
                        raise ValueError(
                            "La base local contiene páginas fallidas y no se considera "
                            "completa."
                        )
                    for key in (
                        "download_duration_seconds",
                        "pages_retried",
                        "resumed_pages",
                        "verified_pages",
                    ):
                        value = stored_values.get(key)
                        if value is not None and int(value) < 0:
                            raise ValueError(
                                f"La metadata de la base contiene un valor negativo "
                                f"para {key}."
                            )
    except (sqlite3.Error, SQLAlchemyError) as error:
        raise ValueError(f"No se pudo leer la base de datos: {error}") from error
    finally:
        engine.dispose()


def read_local_database_metadata(database_path: Path) -> dict[str, str]:
    validate_local_database(database_path)
    engine = create_database_engine(database_path, read_only=True)
    try:
        inspector = inspect(engine)
        if "metadata" not in inspector.get_table_names():
            return {}
        columns = {column["name"] for column in inspector.get_columns("metadata")}
        if not {"key", "value"}.issubset(columns):
            raise ValueError("La tabla metadata de la base local no es válida.")
        table = Table("metadata", MetaData(), autoload_with=engine)
        values: dict[str, str] = {}
        with engine.connect() as connection:
            for key, value in connection.execute(select(table.c.key, table.c.value)):
                if not isinstance(key, str) or not isinstance(value, str):
                    raise TypeError("La tabla metadata de la base local no es válida.")
                values[key] = value
        return values
    except (sqlite3.Error, SQLAlchemyError) as error:
        raise ValueError(f"No se pudo leer la base de datos: {error}") from error
    finally:
        engine.dispose()


def write_local_database_metadata(
    database_path: Path,
    *,
    updated_at: str,
    total_records: int,
    unique_records: int,
    pages: int,
) -> None:
    engine = create_database_engine(database_path)
    try:
        database_metadata.create(engine, checkfirst=True)
        schema_columns = {
            column["name"] for column in inspect(engine).get_columns("records")
        }
        schema_version = "3" if SEARCH_KEY_COLUMNS.issubset(schema_columns) else "1"
        with engine.begin() as connection:
            for key, value in (
                ("source", "ESCALE"),
                ("updated_at", updated_at),
                ("total_records", str(total_records)),
                ("unique_records", str(unique_records)),
                ("pages", str(pages)),
                ("schema_version", schema_version),
            ):
                connection.execute(
                    database_metadata.delete().where(database_metadata.c.key == key)
                )
                connection.execute(
                    insert(database_metadata).values(key=key, value=value)
                )
    except (sqlite3.Error, SQLAlchemyError) as error:
        raise ValueError(
            f"No se pudo guardar la metadata de la base: {error}"
        ) from error
    finally:
        engine.dispose()


def filter_local_database(
    source_path: Path,
    output_path: Path,
    filters: list[LocalSearchFilter],
    cancelled: Callable[[], bool],
    progress: Callable[[int, int, int], None],
) -> tuple[int, int]:
    if not filters:
        raise ValueError("Agrega al menos una combinación de filtros.")
    if source_path.resolve() == output_path.resolve():
        raise ValueError("La base de origen y el resultado local deben ser distintos.")
    validate_local_database(source_path)
    source_engine = create_database_engine(source_path, read_only=True)
    output_engine = create_database_engine(output_path)
    total_records = 0
    matched_records = 0
    processed_records = 0
    batch: list[dict[str, str]] = []
    try:
        source_records = Table("records", MetaData(), autoload_with=source_engine)
        source_columns = set(source_records.c.keys())
        has_search_keys = SEARCH_KEY_COLUMNS.issubset(source_columns)
        metadata.create_all(output_engine)
        with source_engine.connect() as source, output_engine.begin() as output:
            if cancelled():
                raise SearchCancelled
            total_records = int(
                source.execute(
                    select(func.count()).select_from(source_records)
                ).scalar_one()
            )
            where_clause = None
            if has_search_keys:
                key_variants = _stored_search_key_variants(
                    source,
                    source_records,
                    filters,
                )
                where_clause = _build_filter_clause(
                    source_records,
                    filters,
                    key_variants,
                )
                matched_records = int(
                    source.execute(
                        select(func.count())
                        .select_from(source_records)
                        .where(where_clause)
                    ).scalar_one()
                )
                cursor = source.execute(
                    select(
                        source_records.c.region,
                        source_records.c.province,
                        source_records.c.district,
                        source_records.c.payload,
                        *(source_records.c[name] for name in SEARCH_KEY_COLUMNS),
                    ).where(where_clause)
                )
                for row in cursor.mappings():
                    if cancelled():
                        raise SearchCancelled
                    batch.append(dict(row))
                    processed_records += 1
                    if len(batch) >= SEARCH_BATCH_SIZE:
                        output.execute(insert(records), batch)
                        batch.clear()
                    if processed_records % PROGRESS_INTERVAL == 0:
                        progress(processed_records, total_records, matched_records)
            else:
                cursor = source.execute(
                    select(
                        source_records.c.region,
                        source_records.c.province,
                        source_records.c.district,
                        source_records.c.payload,
                    )
                )
                matched_records = 0
                for region, province, district, payload in cursor:
                    if cancelled():
                        raise SearchCancelled
                    if not isinstance(payload, str):
                        raise TypeError(
                            f"El registro {processed_records + 1} no contiene texto JSON."
                        )
                    record = json.loads(payload)
                    if not isinstance(record, dict):
                        raise TypeError(
                            f"El registro {processed_records + 1} "
                            "no contiene un objeto JSON."
                        )
                    region_value = str(region or "")
                    province_value = str(province or "")
                    district_value = str(district or "")
                    if _matches_filters(
                        region_value,
                        province_value,
                        district_value,
                        record,
                        filters,
                    ):
                        batch.append(
                            {
                                "region": region_value,
                                "province": province_value,
                                "district": district_value,
                                "payload": payload,
                                **record_search_keys(
                                    region_value,
                                    province_value,
                                    district_value,
                                    record,
                                ),
                            }
                        )
                        matched_records += 1
                    processed_records += 1
                    if len(batch) >= SEARCH_BATCH_SIZE:
                        output.execute(insert(records), batch)
                        batch.clear()
                    if processed_records % PROGRESS_INTERVAL == 0:
                        progress(processed_records, total_records, matched_records)

            if batch:
                output.execute(insert(records), batch)
        progress(processed_records, total_records, matched_records)
        return total_records, matched_records
    finally:
        source_engine.dispose()
        output_engine.dispose()


def _stored_search_key_variants(
    connection: Connection,
    records_table: Table,
    filters: list[LocalSearchFilter],
) -> dict[tuple[str, str], tuple[str, ...]]:
    requested_values: dict[str, set[str]] = {}
    for search in filters:
        for column_name, value in (
            ("region_key", search.region),
            ("province_key", search.province),
            ("district_key", search.district),
        ):
            if value:
                requested_values.setdefault(column_name, set()).add(value)
        for column_name, selected_values in (
            ("management_key", search.management),
            ("level_key", search.level),
        ):
            for value in selected_values:
                requested_values.setdefault(column_name, set()).add(value)

    variants_by_value: dict[tuple[str, str], tuple[str, ...]] = {}
    for column_name, requested_for_column in requested_values.items():
        column = records_table.c[column_name]
        normalized_values: dict[str, list[str]] = {}
        for stored_value in connection.execute(select(column).distinct()).scalars():
            if isinstance(stored_value, str):
                normalized_values.setdefault(
                    normalize_search_text(stored_value),
                    [],
                ).append(stored_value)
        for value in requested_for_column:
            variants = (
                *_search_key_variants(value),
                *normalized_values.get(normalize_search_text(value), ()),
            )
            variants_by_value[(column_name, value)] = tuple(dict.fromkeys(variants))
    return variants_by_value


def _build_filter_clause(
    records_table: Table,
    filters: list[LocalSearchFilter],
    variants_by_value: dict[tuple[str, str], tuple[str, ...]],
):
    filter_groups = []

    def variants(column_name: str, value: str) -> tuple[str, ...]:
        return variants_by_value.get(
            (column_name, value),
            _search_key_variants(value),
        )

    for search in filters:
        conditions = []
        if search.region:
            conditions.append(
                records_table.c.region_key.in_(variants("region_key", search.region))
            )
        if search.province:
            conditions.append(
                records_table.c.province_key.in_(
                    variants("province_key", search.province)
                )
            )
        if search.district:
            conditions.append(
                records_table.c.district_key.in_(
                    variants("district_key", search.district)
                )
            )
        if search.management:
            conditions.append(
                records_table.c.management_key.in_(
                    [
                        key
                        for value in search.management
                        for key in variants("management_key", value)
                    ]
                )
            )
        if search.level:
            conditions.append(
                records_table.c.level_key.in_(
                    [
                        key
                        for value in search.level
                        for key in variants("level_key", value)
                    ]
                )
            )
        filter_groups.append(and_(*conditions) if conditions else true())
    return or_(*filter_groups)


def _matches_filters(
    region: str,
    province: str,
    district: str,
    record: dict[str, object],
    filters: list[LocalSearchFilter],
) -> bool:
    normalized_region = _normalize_name(region)
    normalized_province = _normalize_name(province)
    normalized_district = _normalize_name(district)
    management = _normalize_name(str(record.get("gestion") or ""))
    level = _normalize_name(str(record.get("nivel") or ""))

    for search in filters:
        if search.region and normalized_region != _normalize_name(search.region):
            continue
        if search.province and normalized_province != _normalize_name(search.province):
            continue
        if search.district and normalized_district != _normalize_name(search.district):
            continue
        if search.management and management not in {
            _normalize_name(value) for value in search.management
        }:
            continue
        if search.level and level not in {
            _normalize_name(value) for value in search.level
        }:
            continue
        return True
    return False


def _normalize_name(value: str) -> str:
    return normalize_search_text(value)


def _search_key_variants(value: str) -> tuple[str, ...]:
    return tuple(
        dict.fromkeys(
            (normalize_search_text(value), normalize_search_text_legacy(value))
        )
    )


def _spanish_sort_key(value: str) -> tuple[tuple[int, ...], tuple[tuple[int, int], ...]]:
    primary: list[int] = []
    accents: list[tuple[int, int]] = []
    for character in unicodedata.normalize("NFD", value.casefold()):
        if unicodedata.combining(character):
            if (
                character == "\u0303"
                and primary
                and primary[-1] == ord("n") * 2
            ):
                primary[-1] += 1
            elif primary:
                accents.append((len(primary) - 1, ord(character)))
            continue
        primary.append(ord(character) * 2)
    return tuple(primary), tuple(accents)


def summarize_local_database_by_location(
    database_path: Path,
) -> list[tuple[str, str, str, int]]:
    engine = create_database_engine(database_path, read_only=True)
    try:
        records_table = Table("records", MetaData(), autoload_with=engine)
        region = func.coalesce(records_table.c.region, "Departamento no identificado")
        province = func.coalesce(records_table.c.province, "Provincia no identificada")
        district = func.coalesce(records_table.c.district, "Distrito no identificado")
        statement = (
            select(
                region.label("region"),
                province.label("province"),
                district.label("district"),
                func.count().label("schools"),
            )
            .group_by(region, province, district)
        )
        with engine.connect() as connection:
            location_counts: dict[tuple[str, str, str], int] = {}
            for row in connection.execute(statement):
                location = (
                    canonical_department_name(str(row.region)),
                    str(row.province),
                    str(row.district),
                )
                location_counts[location] = location_counts.get(location, 0) + int(
                    row.schools
                )
        locations = [
            (*location, schools) for location, schools in location_counts.items()
        ]
        return sorted(
            locations,
            key=lambda row: tuple(_spanish_sort_key(value) for value in row[:3]),
        )
    except (sqlite3.Error, SQLAlchemyError) as error:
        raise DataAccessError(str(error)) from error
    finally:
        engine.dispose()


class SqliteOfflineDatabaseRepository:
    def inspect(
        self,
        database_path: Path,
    ) -> tuple[dict[str, str], list[tuple[str, str, str, int]]]:
        metadata = read_local_database_metadata(database_path)
        locations = summarize_local_database_by_location(database_path)
        return metadata, locations

    def validate(self, database_path: Path) -> None:
        validate_local_database(database_path)

    def read_metadata(self, database_path: Path) -> dict[str, str]:
        return read_local_database_metadata(database_path)

    def write_metadata(
        self,
        database_path: Path,
        *,
        updated_at: str,
        total_records: int,
        unique_records: int,
        pages: int,
    ) -> None:
        write_local_database_metadata(
            database_path,
            updated_at=updated_at,
            total_records=total_records,
            unique_records=unique_records,
            pages=pages,
        )

    def filter(
        self,
        source_path: Path,
        output_path: Path,
        filters: list[LocalSearchFilter],
        cancelled: Callable[[], bool],
        progress: Callable[[int, int, int], None],
    ) -> tuple[int, int]:
        try:
            return filter_local_database(
                source_path,
                output_path,
                filters,
                cancelled,
                progress,
            )
        except (sqlite3.Error, SQLAlchemyError) as error:
            raise DataAccessError(str(error)) from error

    def publish(self, staged_path: Path, target_path: Path) -> None:
        os.replace(staged_path, target_path)
