import json
import logging
import os
import sqlite3
import tempfile
from pathlib import Path

from scrapy.exceptions import DropItem

from eduescale.export_files import android_data_path, replace_export_pair
from eduescale.exports import export_eduescale, export_excel

LOGGER = logging.getLogger(__name__)

FIELDS = (
    "nombre",
    "direccion",
    "localidad",
    "cp",
    "turno",
    "alumnos",
    "secciones",
    "longitud",
    "latitud",
)


class ExcelPipeline:
    @classmethod
    def from_crawler(cls, crawler):
        pipeline = cls()
        pipeline.crawler = crawler
        return pipeline

    def open_spider(self):
        self.output_path = Path(self.crawler.spider.output)
        self.android_output_path = android_data_path(self.output_path)
        self.output_path.parent.mkdir(parents=True, exist_ok=True)
        self._temporary_output_path: Path | None = None
        self._temporary_android_path: Path | None = None
        with tempfile.NamedTemporaryFile(
            prefix="eduescale-",
            suffix=".sqlite3",
            dir=self.output_path.parent,
            delete=False,
        ) as spool_file:
            self._spool_path: Path | None = Path(spool_file.name)
        self._spool = sqlite3.connect(self._spool_path)
        self._spool.execute(
            """
            CREATE TABLE records (
                region TEXT NOT NULL,
                province TEXT NOT NULL,
                district TEXT NOT NULL,
                payload TEXT NOT NULL
            )
            """
        )
        self.row_count = 0

    def process_item(self, item):
        try:
            for field in FIELDS:
                item[field]
        except KeyError as error:
            raise DropItem(f"Falta el campo requerido para Excel: {error}") from error
        self._spool.execute(
            """
            INSERT INTO records (region, province, district, payload)
            VALUES (?, ?, ?, ?)
            """,
            (
                item.get("region") or "Departamento no identificado",
                item.get("provincia") or "Provincia no identificada",
                item.get("distrito") or "Distrito no identificado",
                json.dumps(item, ensure_ascii=False),
            ),
        )
        self.row_count += 1
        return item

    def close_spider(self):
        spider = self.crawler.spider
        try:
            if not spider.count_received:
                raise RuntimeError(
                    "No se pudo obtener el total de resultados desde ESCALE."
                )
            if (
                spider.items_received != spider.expected_total
                or self.row_count != spider.unique_items_received
            ):
                message = (
                    f"Extracción incompleta: se esperaban {spider.expected_total} "
                    f"resultados y se recibieron {spider.items_received}; "
                    f"se guardaron {self.row_count} colegios únicos; "
                    f"páginas completas: {spider.pages_received}."
                )
                if spider.failed_pages:
                    details = "; ".join(
                        f"filtro {filter_index + 1}, offset {offset}: {reason}"
                        for (filter_index, offset), reason in sorted(
                            spider.failed_pages.items()
                        )
                    )
                    message = f"{message} Páginas fallidas: {details}"
                raise RuntimeError(message)

            self._spool.commit()
            if self.output_path.suffix.lower() == ".sqlite3":
                self._close_spool()
                os.replace(self._spool_path, self.output_path)
                self._spool_path = None
                return

            self._temporary_output_path = self._create_temporary_path(".xlsx")
            self._temporary_android_path = self._create_temporary_path(".eduescale")
            export_excel(self._spool_path, self._temporary_output_path)
            export_eduescale(self._spool_path, self._temporary_android_path)
            try:
                cleanup_warning = replace_export_pair(
                    self._temporary_output_path,
                    self.output_path,
                    self._temporary_android_path,
                    self.android_output_path,
                )
            except PermissionError as error:
                raise RuntimeError(
                    "No se pudieron reemplazar los archivos de exportación. "
                    "Cierra el archivo de Excel si está abierto y vuelve a intentar."
                ) from error
            if cleanup_warning:
                LOGGER.warning("%s", cleanup_warning)
        finally:
            self._close_spool()
            if self._spool_path is not None:
                self._spool_path.unlink(missing_ok=True)
            if self._temporary_output_path is not None:
                self._temporary_output_path.unlink(missing_ok=True)
            if self._temporary_android_path is not None:
                self._temporary_android_path.unlink(missing_ok=True)

    def _create_temporary_path(self, suffix: str) -> Path:
        with tempfile.NamedTemporaryFile(
            prefix=".eduescale-export-",
            suffix=suffix,
            dir=self.output_path.parent,
            delete=False,
        ) as export_file:
            return Path(export_file.name)

    def _close_spool(self):
        if self._spool is not None:
            self._spool.close()
            self._spool = None
