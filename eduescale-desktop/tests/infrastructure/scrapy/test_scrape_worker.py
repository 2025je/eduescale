import os

from scrapy.settings import Settings

from eduescale import scrape_worker


def test_worker_disables_generator_source_inspection(mocker):
    settings = Settings()
    mocker.patch.dict(os.environ)
    os.environ.pop("SCRAPY_SETTINGS_MODULE", None)
    mocker.patch.object(scrape_worker, "get_project_settings", return_value=settings)
    execute = mocker.patch.object(scrape_worker, "execute")

    scrape_worker.main()

    assert settings.getbool("WARN_ON_GENERATOR_RETURN_VALUE") is False
    execute.assert_called_once_with(settings=settings)
    assert os.environ["SCRAPY_SETTINGS_MODULE"] == "eduescale.scrapy_settings"
