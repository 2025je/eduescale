import pytest

from eduescale.gui.adapters.scrapy_output_parser import (
    SchoolBatch,
    ScrapeTotal,
    ScrapyOutputParseError,
    ScrapyOutputParser,
)
from eduescale.gui.services.progress import ProgressMetrics


def test_parses_total_and_progress_metrics():
    parser = ScrapyOutputParser()

    assert parser.parse_line("INFO: SCRAPE_TOTAL=100") == ScrapeTotal(100)
    assert parser.parse_line(
        "SCRAPE_PROGRESS=50/100 UNIQUE=48 PAGES=2 RESUMED=1 RETRIED=3 FAILED=4"
    ) == ProgressMetrics(
        received=50,
        total=100,
        unique=48,
        pages=2,
        failed=4,
        resumed=1,
        retried=3,
    )


def test_parses_live_school_batches():
    parser = ScrapyOutputParser()

    assert parser.parse_line(
        'INFO: SCRAPE_SCHOOLS=[{"codigo_local":"000123"}]'
    ) == SchoolBatch([{"codigo_local": "000123"}])


@pytest.mark.parametrize(
    "line",
    (
        "SCRAPE_SCHOOLS={invalid json}",
        'SCRAPE_SCHOOLS={"codigo_local":"000123"}',
        'SCRAPE_SCHOOLS=[{"codigo_local":"000123"}, null]',
    ),
)
def test_rejects_malformed_live_school_batches(line):
    with pytest.raises(ScrapyOutputParseError):
        ScrapyOutputParser().parse_line(line)
