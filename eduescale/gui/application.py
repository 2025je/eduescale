import logging
import sys
from collections.abc import Callable
from pathlib import Path

from PySide6.QtCore import QUrl
from PySide6.QtGui import QGuiApplication
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuickControls2 import QQuickStyle

from eduescale.gui.controller import SchoolFinderController
from eduescale.gui.location_service import LocationService
from eduescale.gui.ports import LocationServicePort, ScrapeRunnerPort
from eduescale.gui.scrape_runner import ScrapeRunner
from eduescale.logging_config import configure_logging

LOGGER = logging.getLogger(__name__)


def create_controller(
    location_service: LocationServicePort | None = None,
    scrape_runner: ScrapeRunnerPort | None = None,
) -> SchoolFinderController:
    return SchoolFinderController(
        location_service=(
            LocationService() if location_service is None else location_service
        ),
        scrape_runner=ScrapeRunner() if scrape_runner is None else scrape_runner,
    )


def main(
    controller_factory: Callable[[], SchoolFinderController] = create_controller,
) -> int:
    QQuickStyle.setStyle("Material")
    application = QGuiApplication(sys.argv)
    application.setOrganizationName("2025je")
    application.setApplicationName("EduScale")
    application.setApplicationDisplayName("Buscador de colegios - ESCALE")
    log_path = configure_logging()
    LOGGER.info("Starting EduScale; log file: %s", log_path)
    engine = QQmlApplicationEngine()
    controller = controller_factory()
    engine.setInitialProperties({"controller": controller})
    qml_path = Path(__file__).resolve().parent / "qml" / "Main.qml"
    engine.load(QUrl.fromLocalFile(str(qml_path)))
    if not engine.rootObjects():
        LOGGER.error("Could not load the main QML interface from %s", qml_path)
        controller.shutdown()
        return 1
    return application.exec()


if __name__ == "__main__":
    raise SystemExit(main())
