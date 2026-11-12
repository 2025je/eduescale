import logging
import sys
from collections.abc import Callable

from PySide6.QtGui import QFont, QFontDatabase, QGuiApplication, QIcon
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuickControls2 import QQuickStyle
from PySide6.QtWebEngineQuick import QtWebEngineQuick

from eduescale.gui.controller import SchoolFinderController
from eduescale.gui.export_runner import ExportRunner
from eduescale.gui.location_service import LocationService
from eduescale.gui.ports import ExportRunnerPort, LocationServicePort, ScrapeRunnerPort
from eduescale.gui.resources import resource_path, resource_url
from eduescale.gui.scrape_runner import ScrapeRunner
from eduescale.logging_config import configure_logging

LOGGER = logging.getLogger(__name__)


def configure_application_font(application: QGuiApplication) -> bool:
    available_families = set(QFontDatabase.families())
    fluent_font = next(
        (
            family
            for family in ("Segoe UI Variable", "Segoe UI")
            if family in available_families
        ),
        None,
    )
    if fluent_font is not None:
        application.setFont(QFont(fluent_font))
        LOGGER.info("Using Fluent UI system font %s.", fluent_font)
        return True

    font_path = resource_path("fonts/Roboto-Variable.ttf")
    font_id = QFontDatabase.addApplicationFont(font_path)
    if font_id < 0:
        LOGGER.error("Could not load Roboto font from %s", font_path)
        return False

    font_families = QFontDatabase.applicationFontFamilies(font_id)
    if "Roboto" not in font_families:
        LOGGER.error(
            "Roboto font file registered unexpected families: %s",
            font_families,
        )
        return False

    application.setFont(QFont("Roboto"))
    LOGGER.info("Using Roboto font from %s", font_path)
    return True


def configure_application_icon(application: QGuiApplication) -> bool:
    icon_path = resource_path("assets/edutrack-logo.png")
    icon = QIcon(icon_path)
    if icon.isNull():
        LOGGER.error("Could not load application icon from %s", icon_path)
        return False

    application.setWindowIcon(icon)
    LOGGER.info("Using application icon from %s", icon_path)
    return True


def create_controller(
    location_service: LocationServicePort | None = None,
    scrape_runner: ScrapeRunnerPort | None = None,
    export_runner: ExportRunnerPort | None = None,
) -> SchoolFinderController:
    return SchoolFinderController(
        location_service=(
            LocationService() if location_service is None else location_service
        ),
        scrape_runner=ScrapeRunner() if scrape_runner is None else scrape_runner,
        export_runner=ExportRunner() if export_runner is None else export_runner,
    )


def main(
    controller_factory: Callable[[], SchoolFinderController] = create_controller,
) -> int:
    QtWebEngineQuick.initialize()
    QQuickStyle.setStyle("Universal")
    application = QGuiApplication(sys.argv)
    if not configure_application_font(application):
        return 1
    if not configure_application_icon(application):
        return 1
    application.setOrganizationName("2025je")
    application.setApplicationName("EduEscale")
    application.setApplicationDisplayName("Buscador de colegios - ESCALE")
    log_path = configure_logging()
    LOGGER.info("Starting EduEscale; log file: %s", log_path)
    engine = QQmlApplicationEngine()
    controller = controller_factory()
    engine.setInitialProperties({"controller": controller})
    qml_url = resource_url("qml/Main.qml")
    engine.load(qml_url)
    if not engine.rootObjects():
        LOGGER.error("Could not load the main QML interface from %s", qml_url.toString())
        controller.shutdown()
        return 1
    return application.exec()


if __name__ == "__main__":
    raise SystemExit(main())
