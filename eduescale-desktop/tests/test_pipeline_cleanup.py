import tempfile
from pathlib import Path
from types import SimpleNamespace

import pytest

from eduescale.pipelines import ExcelPipeline


def test_incomplete_extraction_closes_and_removes_spool_database():
    with tempfile.TemporaryDirectory() as temp_dir:
        output_path = Path(temp_dir) / "incomplete.sqlite3"
        output_path.write_bytes(b"archivo anterior")
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
        spool_path = pipeline._spool_path

        with pytest.raises(RuntimeError, match="Extracción incompleta"):
            pipeline.close_spider()

        assert output_path.read_bytes() == b"archivo anterior"
        assert pipeline._temporary_output_path is None
        assert not spool_path.exists()
