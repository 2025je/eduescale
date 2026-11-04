from nox import Session, options
from nox_uv import session

options.default_venv_backend = "uv"


@session(uv_groups=["test"])
def tests(session: Session) -> None:
    session.run("pytest")


@session(uv_groups=["lint"])
def lint(session: Session) -> None:
    session.run("ruff", "check", ".")
    session.run("ruff", "format", "--check", ".")


@session(uv_groups=["lint"])
def format(session: Session) -> None:
    session.run("ruff", "check", "--fix", ".")
    session.run("ruff", "format", ".")


@session(uv_groups=["build"])
def build(session: Session) -> None:
    session.run(
        "pyinstaller",
        "--noconfirm",
        "--clean",
        "--onefile",
        "--windowed",
        "--name",
        "eduescale-gui",
        "--collect-data",
        "PySide6",
        "--add-data",
        "eduescale/gui/qml/Main.qml;eduescale/gui/qml",
        "app.py",
    )
    session.run(
        "pyinstaller",
        "--noconfirm",
        "--clean",
        "--onefile",
        "--console",
        "--name",
        "eduescale-scraper",
        "--collect-submodules",
        "eduescale.spiders",
        "--hidden-import",
        "eduescale.scrapy_settings",
        "--hidden-import",
        "eduescale.pipelines",
        "--copy-metadata",
        "Scrapy",
        "--copy-metadata",
        "lxml",
        "--copy-metadata",
        "cssselect",
        "--copy-metadata",
        "parsel",
        "--copy-metadata",
        "w3lib",
        "--copy-metadata",
        "Twisted",
        "--copy-metadata",
        "pyOpenSSL",
        "--copy-metadata",
        "cryptography",
        "eduescale/scrape_worker.py",
    )
