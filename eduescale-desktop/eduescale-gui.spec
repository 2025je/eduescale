# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_data_files

datas = [
    ('eduescale/gui/qml/Main.qml', 'eduescale/gui/qml'),
    ('eduescale/gui/qml/DistrictMap.qml', 'eduescale/gui/qml'),
    ('eduescale/gui/assets/edutrack-logo.png', 'eduescale/gui/assets'),
    ('eduescale/gui/assets/districts_map.json', 'eduescale/gui/assets'),
    ('eduescale/gui/assets/districts_map_hierarchy.json', 'eduescale/gui/assets'),
    ('eduescale/gui/assets/maplibre/map.html', 'eduescale/gui/assets/maplibre'),
    ('eduescale/gui/assets/maplibre/maplibre-gl.js', 'eduescale/gui/assets/maplibre'),
    ('eduescale/gui/assets/maplibre/maplibre-gl.css', 'eduescale/gui/assets/maplibre'),
    ('eduescale/gui/assets/maplibre/LICENSE.txt', 'eduescale/gui/assets/maplibre'),
    ('eduescale/gui/fonts/Roboto-Variable.ttf', 'eduescale/gui/fonts'),
    ('eduescale/gui/fonts/OFL.txt', 'eduescale/gui/fonts'),
]
datas += collect_data_files('PySide6')


a = Analysis(
    ['app.py'],
    pathex=[],
    binaries=[],
    datas=datas,
    hiddenimports=[
        'PySide6.QtWebChannel',
        'PySide6.QtWebEngineCore',
        'PySide6.QtWebEngineQuick',
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
a.binaries = [
    binary
    for binary in a.binaries
    if not binary[0].lower().replace('\\', '/').endswith(
        '/plugins/designer/qwebengineview.dll'
    )
]
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='eduescale-gui',
    icon='eduescale/gui/assets/edutrack-logo.ico',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
