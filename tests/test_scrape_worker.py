import os

from scrapy.settings import Settings

from eduescale import scrape_worker


def test_worker_disables_generator_source_inspection(monkeypatch):
    settings = Settings()
    executed = {}
    monkeypatch.delenv("SCRAPY_SETTINGS_MODULE", raising=False)
    monkeypatch.setattr(scrape_worker, "get_project_settings", lambda: settings)
    monkeypatch.setattr(
        scrape_worker,
        "execute",
        lambda settings: executed.setdefault("settings", settings),
    )

    scrape_worker.main()

    assert settings.getbool("WARN_ON_GENERATOR_RETURN_VALUE") is False
    assert executed["settings"] is settings
    assert os.environ["SCRAPY_SETTINGS_MODULE"] == "eduescale.scrapy_settings"
