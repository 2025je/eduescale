BOT_NAME = "eduescale"

SPIDER_MODULES = ["eduescale.infrastructure.scrapy.spiders"]
NEWSPIDER_MODULE = "eduescale.infrastructure.scrapy.spiders"

ROBOTSTXT_OBEY = True
TWISTED_REACTOR = "twisted.internet.asyncioreactor.AsyncioSelectorReactor"
CONCURRENT_REQUESTS = 4
CONCURRENT_REQUESTS_PER_DOMAIN = 4
DOWNLOAD_DELAY = 0.25
DOWNLOAD_TIMEOUT = 45
RETRY_TIMES = 5
USER_AGENT = "eduescale/0.1 (consulta educativa; contacto: usuario local)"

ITEM_PIPELINES = {
    "eduescale.infrastructure.scrapy.pipelines.ExcelPipeline": 300,
}
