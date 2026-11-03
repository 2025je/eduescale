import sys
from pathlib import Path

from PySide6.QtCore import QUrl
from PySide6.QtGui import QGuiApplication
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuickControls2 import QQuickStyle

from eduescale.gui.controller import SchoolFinderController


def main() -> int:
    QQuickStyle.setStyle("Material")
    application = QGuiApplication(sys.argv)
    application.setApplicationName("Buscador de colegios - ESCALE")
    engine = QQmlApplicationEngine()
    controller = SchoolFinderController()
    engine.setInitialProperties({"controller": controller})
    qml_path = Path(__file__).resolve().parent / "qml" / "Main.qml"
    engine.load(QUrl.fromLocalFile(str(qml_path)))
    if not engine.rootObjects():
        controller.shutdown()
        return 1
    return application.exec()


if __name__ == "__main__":
    raise SystemExit(main())
