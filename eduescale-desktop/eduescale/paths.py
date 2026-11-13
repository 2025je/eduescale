import os
from pathlib import Path


def application_data_directory() -> Path:
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        base_directory = Path(local_app_data)
    else:
        base_directory = Path(
            os.environ.get(
                "XDG_STATE_HOME",
                str(Path.home() / ".local" / "state"),
            )
        )
    return base_directory / "EduEscale"
