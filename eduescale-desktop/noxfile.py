import os
import shutil
from pathlib import Path

from nox import Session, options
from nox_uv import session

options.default_venv_backend = "uv"

PROJECT_ROOT = Path(__file__).resolve().parent


def _find_inno_compiler() -> str | None:
    for executable in ("ISCC.exe", "ISCC"):
        compiler = shutil.which(executable)
        if compiler:
            return compiler

    for variable in ("ProgramFiles(x86)", "ProgramFiles"):
        program_files = os.environ.get(variable)
        if not program_files:
            continue
        compiler = Path(program_files) / "Inno Setup 6" / "ISCC.exe"
        if compiler.is_file():
            return str(compiler)

    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        compiler = (
            Path(local_app_data)
            / "Programs"
            / "Inno Setup 6"
            / "ISCC.exe"
        )
        if compiler.is_file():
            return str(compiler)
    return None


@session(uv_groups=["test"])
def tests(session: Session) -> None:
    session.run("pytest")


@session(uv_groups=["lint"])
def lint(session: Session) -> None:
    session.run("ruff", "check", ".")
    session.run("ruff", "format", "--check", ".")
    session.run("mypy", "eduescale")


@session(uv_groups=["lint"])
def format(session: Session) -> None:
    session.run("ruff", "check", "--fix", ".")
    session.run("ruff", "format", ".")


@session(uv_groups=["build"])
def build(session: Session) -> None:
    compiler = _find_inno_compiler()
    if compiler is None:
        session.error(
            "Inno Setup 6 is required to build the Windows installer. "
            "Install it and make ISCC.exe available on PATH."
        )

    session.run(
        "pyside6-rcc",
        "--binary",
        "eduescale/gui/resources.qrc",
        "-o",
        "eduescale/gui/resources.rcc",
    )
    session.run(
        "pyinstaller",
        "--noconfirm",
        "--clean",
        "eduescale-gui.spec",
    )
    session.run(
        "pyinstaller",
        "--noconfirm",
        "--clean",
        "eduescale-scraper.spec",
    )
    version = session.run(
        "uv",
        "version",
        "--short",
        external=True,
        silent=True,
    )
    if version is None:
        session.error("Could not read the project version with `uv version --short`.")
    session.run(
        compiler,
        f"/DAppVersion={version.strip()}",
        str(PROJECT_ROOT / "installer" / "eduescale.iss"),
        external=True,
    )
