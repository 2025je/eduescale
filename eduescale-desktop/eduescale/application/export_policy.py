from importlib.metadata import version
from pathlib import Path

EDUESCALE_FORMAT_VERSION = 1


def eduescale_application_version() -> str:
    return version("eduescale")


def android_data_path(path: Path) -> Path:
    return path.with_suffix(".eduescale")
