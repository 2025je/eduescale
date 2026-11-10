import os
from pathlib import Path

import PySide6

_PYSIDE_DLL_SEARCH_HANDLES = []
if os.name == "nt":
    _PYSIDE_DLL_SEARCH_HANDLES.append(
        os.add_dll_directory(str(Path(PySide6.__file__).resolve().parent))
    )
