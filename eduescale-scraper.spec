# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_submodules
from PyInstaller.utils.hooks import copy_metadata

datas = []
hiddenimports = ['eduescale.scrapy_settings', 'eduescale.pipelines']
datas += copy_metadata('Scrapy')
datas += copy_metadata('lxml')
datas += copy_metadata('cssselect')
datas += copy_metadata('parsel')
datas += copy_metadata('w3lib')
datas += copy_metadata('Twisted')
datas += copy_metadata('pyOpenSSL')
datas += copy_metadata('cryptography')
hiddenimports += collect_submodules('eduescale.spiders')


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
    a.binaries,
    a.datas,
    [],
    name='eduescale-scraper',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
