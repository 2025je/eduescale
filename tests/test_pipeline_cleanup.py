import tempfile
from pathlib import Path
from types import SimpleNamespace

import pytest

from eduescale.pipelines import ExcelPipeline


def test_incomplete_export_closes_openpyxl_temporary_file():
    with tempfile.TemporaryDirectory() as temp_dir:
        output_path = Path(temp_dir) / "incomplete.xlsx"
        spider = SimpleNamespace(
            output=str(output_path),
            count_received=True,
            expected_total=1,
            items_received=0,
            unique_items_received=0,
            pages_received=0,
            failed_pages={},
        )
        pipeline = ExcelPipeline()
        pipeline.crawler = SimpleNamespace(spider=spider)
        pipeline.open_spider()
        temporary_sheet = Path(pipeline.worksheet._writer.out)

        with pytest.raises(RuntimeError, match="Extracción incompleta"):
            pipeline.close_spider()

        assert not output_path.exists()
        temporary_sheet.unlink()
