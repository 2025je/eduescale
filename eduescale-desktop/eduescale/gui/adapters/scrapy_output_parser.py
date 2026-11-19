import json
import re
from dataclasses import dataclass
from typing import TypeAlias

from eduescale.gui.services.progress import ProgressMetrics

SCRAPE_TOTAL_PATTERN = re.compile(r"SCRAPE_TOTAL=(\d+)")
SCRAPE_PROGRESS_PATTERN = re.compile(
    r"SCRAPE_PROGRESS=(\d+)/(\d+)\s+UNIQUE=(\d+)\s+PAGES=(\d+)"
    r"(?:\s+RESUMED=(\d+))?(?:\s+RETRIED=(\d+))?\s+FAILED=(\d+)"
)
SCHOOLS_MARKER = "SCRAPE_SCHOOLS="


@dataclass(frozen=True)
class ScrapeTotal:
    total: int


@dataclass(frozen=True)
class SchoolBatch:
    schools: list[dict[str, object]]


ScrapyOutputEvent: TypeAlias = ScrapeTotal | SchoolBatch | ProgressMetrics | None


class ScrapyOutputParseError(ValueError):
    pass


class ScrapyOutputParser:
    def parse_line(self, line: str) -> ScrapyOutputEvent:
        if SCHOOLS_MARKER in line:
            return self._parse_school_batch(line)

        total_match = SCRAPE_TOTAL_PATTERN.search(line)
        if total_match:
            return ScrapeTotal(int(total_match.group(1)))

        progress_match = SCRAPE_PROGRESS_PATTERN.search(line)
        if progress_match:
            values = [int(value or 0) for value in progress_match.groups()]
            received, total, unique, pages, resumed, retried, failed = values
            return ProgressMetrics(
                received=received,
                total=total,
                unique=unique,
                pages=pages,
                failed=failed,
                resumed=resumed,
                retried=retried,
            )
        return None

    @staticmethod
    def _parse_school_batch(line: str) -> SchoolBatch:
        payload = line.split(SCHOOLS_MARKER, 1)[1]
        try:
            parsed: object = json.loads(payload)
        except json.JSONDecodeError as error:
            raise ScrapyOutputParseError(
                "Scrapy emitted a live school batch with invalid JSON."
            ) from error

        if not isinstance(parsed, list):
            raise ScrapyOutputParseError(
                "Scrapy emitted a live school batch that is not a list."
            )

        schools: list[dict[str, object]] = []
        for school in parsed:
            if not isinstance(school, dict) or not all(
                isinstance(key, str) for key in school
            ):
                raise ScrapyOutputParseError(
                    "Scrapy emitted a live school batch with an invalid shape."
                )
            schools.append(
                {
                    key: value
                    for key, value in school.items()
                    if isinstance(key, str)
                }
            )
        return SchoolBatch(schools)
