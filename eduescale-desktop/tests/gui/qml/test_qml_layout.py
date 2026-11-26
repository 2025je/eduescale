import os
import subprocess
import sys
from pathlib import Path


def test_main_qml_fits_supported_window_sizes(tmp_path):
    assets_path = Path(__file__).parents[3] / "eduescale" / "gui" / "assets"
    environment = os.environ.copy()
    environment.setdefault("QT_QPA_PLATFORM", "offscreen")
    environment.setdefault(
        "QTWEBENGINE_CHROMIUM_FLAGS",
        "--use-gl=angle --use-angle=swiftshader --enable-webgl "
        "--enable-unsafe-swiftshader",
    )
    result = subprocess.run(
        [
            sys.executable,
            str(Path(__file__).with_name("qml_smoke.py")),
            "qrc:/eduescale/gui/qml/Main.qml",
            str(tmp_path / "settings.ini"),
            str(assets_path),
        ],
        capture_output=True,
        check=False,
        env=environment,
        encoding="utf-8",
        errors="replace",
        timeout=90,
    )

    assert result.returncode == 0, f"{result.stdout}\n{result.stderr}"
    assert 'unknown property "updateable"' not in result.stderr
    assert 'source "schools-live" not found' not in result.stderr
    assert "Cannot read property 'exportsAvailable' of null" not in result.stderr
    assert "scrapeRunning' of null" not in result.stderr
    assert "exportHistory' of null" not in result.stderr
    assert "Cannot set " not in result.stderr
    assert "QML WebEngineProfile: Please use WebEngineProfilePrototype" not in (
        result.stderr
    )
