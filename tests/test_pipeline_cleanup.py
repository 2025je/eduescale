import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from eduescale.pipelines import ExcelPipeline


class ExcelPipelineCleanupTests(unittest.TestCase):
    def test_incomplete_export_closes_openpyxl_temporary_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            output_path = Path(temp_dir) / "incomplete.xlsx"
            spider = SimpleNamespace(
                output=str(output_path),
                count_received=True,
                expected_total=1,
                pages_received=0,
                failed_pages={},
            )
            pipeline = ExcelPipeline()
            pipeline.crawler = SimpleNamespace(spider=spider)
            pipeline.open_spider()
            temporary_sheet = Path(pipeline.worksheet._writer.out)

            with self.assertRaisesRegex(RuntimeError, "Extracción incompleta"):
                pipeline.close_spider()

            self.assertFalse(output_path.exists())
            temporary_sheet.unlink()


if __name__ == "__main__":
    unittest.main()
