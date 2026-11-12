from pathlib import Path

from PySide6.QtCore import QByteArray, QFile, QIODevice, QResource, QUrl
from PySide6.QtWebEngineCore import QWebEngineUrlScheme

RESOURCE_PREFIX = "/eduescale/gui"
RESOURCE_BUNDLE_PATH = Path(__file__).with_name("resources.rcc")

qrc_scheme = QWebEngineUrlScheme(QByteArray(b"qrc"))
qrc_scheme.setSyntax(QWebEngineUrlScheme.Syntax.Path)
qrc_scheme.setFlags(
    QWebEngineUrlScheme.Flag.SecureScheme
    | QWebEngineUrlScheme.Flag.LocalScheme
    | QWebEngineUrlScheme.Flag.LocalAccessAllowed
    | QWebEngineUrlScheme.Flag.CorsEnabled
    | QWebEngineUrlScheme.Flag.FetchApiAllowed
)
QWebEngineUrlScheme.registerScheme(qrc_scheme)

if not QResource.registerResource(str(RESOURCE_BUNDLE_PATH)):
    raise RuntimeError(f"Could not register Qt resources from {RESOURCE_BUNDLE_PATH}")


def resource_path(relative_path: str) -> str:
    return f":{RESOURCE_PREFIX}/{relative_path}"


def resource_url(relative_path: str) -> QUrl:
    return QUrl(f"qrc{resource_path(relative_path)}")


def read_resource_bytes(relative_path: str) -> bytes:
    path = resource_path(relative_path)
    resource = QFile(path)
    if not resource.open(QIODevice.OpenModeFlag.ReadOnly):
        raise OSError(f"Could not open Qt resource {path}: {resource.errorString()}")
    try:
        return bytes(resource.readAll().data())
    finally:
        resource.close()
