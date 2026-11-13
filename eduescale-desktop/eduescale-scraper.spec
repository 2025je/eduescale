# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_submodules
from PyInstaller.utils.hooks import copy_metadata

datas = []
hiddenimports = [
    'eduescale.scrapy_settings',
    'eduescale.infrastructure.scrapy.pipelines',
]
datas += copy_metadata('Scrapy')
datas += copy_metadata('lxml')
datas += copy_metadata('cssselect')
datas += copy_metadata('parsel')
datas += copy_metadata('w3lib')
datas += copy_metadata('Twisted')
datas += copy_metadata('pyOpenSSL')
datas += copy_metadata('cryptography')
hiddenimports += collect_submodules('eduescale.infrastructure.scrapy.spiders')


a = Analysis(
    ['eduescale/scrape_worker.py'],
    pathex=[],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='eduescale-scraper',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name='eduescale-scraper',
)
