import os

from scrapy.cmdline import execute
from scrapy.utils.project import get_project_settings


def main() -> None:
    os.environ.setdefault("SCRAPY_SETTINGS_MODULE", "eduescale.scrapy_settings")
    settings = get_project_settings()
    settings.set("WARN_ON_GENERATOR_RETURN_VALUE", False)
    execute(settings=settings)


if __name__ == "__main__":
    main()
