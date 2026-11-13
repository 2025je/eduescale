from pathlib import Path


def android_data_path(path: Path) -> Path:
    return path.with_suffix(".eduescale")
