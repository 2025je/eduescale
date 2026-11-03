import unittest

import scrapy
from scrapy.http import Response

from eduescale.spiders.schools import SchoolsSpider


class SchoolsSpiderPageTests(unittest.TestCase):
    def test_short_page_is_retried_without_yielding_partial_records(self):
        spider = SchoolsSpider(output="unused.xlsx")
        spider.expected_total = 100
        request = scrapy.Request(
            "https://escale.minedu.gob.pe/padron/rest/instituciones?start=0",
            meta={"offset": 0, "validation_retry_times": 0},
        )
        records = "<items><cenEdu>Colegio</cenEdu></items>" * 49
        response = Response(
            request.url,
            request=request,
            body=f"<instituciones>{records}</instituciones>".encode(),
        )

        output = list(spider.parse_page(response))

        self.assertEqual(len(output), 1)
        self.assertIsInstance(output[0], scrapy.Request)
        self.assertTrue(output[0].dont_filter)
        self.assertEqual(output[0].meta["validation_retry_times"], 1)
        self.assertEqual(spider.items_received, 0)
        self.assertEqual(spider.pages_received, 0)


if __name__ == "__main__":
    unittest.main()
