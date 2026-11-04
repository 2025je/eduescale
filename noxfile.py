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
