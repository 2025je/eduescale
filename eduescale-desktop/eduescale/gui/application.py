import logging
import sys
from collections.abc import Callable

from dependency_injector import containers, providers
from PySide6.QtCore import QSettings, qInstallMessageHandler
from PySide6.QtGui import QFont, QFontDatabase, QGuiApplication, QIcon
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuickControls2 import QQuickStyle
from PySide6.QtWebEngineQuick import QtWebEngineQuick

from eduescale.application.use_cases.export_data import ExportData
from eduescale.application.use_cases.offline_database import (
    OfflineDatabaseUseCases,
)
from eduescale.gui.adapters.export_runner import ExportRunner
from eduescale.gui.adapters.local_database import LocalDatabaseSearchRunner
from eduescale.gui.adapters.location_service import LocationService
from eduescale.gui.adapters.qt_logging import log_qt_message
from eduescale.gui.adapters.resources import resource_path, resource_url
from eduescale.gui.adapters.scrape_runner import ScrapeRunner
from eduescale.gui.contracts.ports import (
    DistrictMapCatalogPort,
    ExportRunnerPort,
    LocalDatabaseSearchRunnerPort,
    LocationServicePort,
    OfflineDatabaseUseCasesPort,
    ScrapeRunnerPort,
)
from eduescale.gui.services.district_map_catalog import DistrictMapCatalog
from eduescale.gui.services.export_history import (
    ExportHistoryStorePort,
    QSettingsExportHistoryStore,
)
from eduescale.gui.services.export_request_service import (
    ExportRequestService,
    ExportRequestServicePort,
)
from eduescale.gui.services.offline_database_update import (
    OfflineDatabaseUpdateService,
    OfflineDatabaseUpdateServicePort,
)
from eduescale.gui.services.preferences import (
    DesktopPreferencesPort,
    QSettingsDesktopPreferences,
)
from eduescale.gui.services.saved_search_service import (
    PortableSavedSearchService,
    PortableSavedSearchServicePort,
)
from eduescale.gui.services.school_search_service import (
    SchoolSearchRequestService,
    SchoolSearchRequestServicePort,
)
from eduescale.gui.services.school_search_workflow import (
    SchoolSearchWorkflow,
    SchoolSearchWorkflowPort,
)
from eduescale.gui.viewmodels import SchoolFinderViewModel
from eduescale.infrastructure.escale.location_provider import ESCALELocationProvider
from eduescale.infrastructure.exports.gateway import FilesystemExportGateway
from eduescale.infrastructure.persistence.checkpoints import (
    DownloadCheckpointGateway,
)
from eduescale.infrastructure.persistence.local_database import (
    SqliteOfflineDatabaseRepository,
)
from eduescale.infrastructure.runtime.logging_config import configure_logging
from eduescale.infrastructure.runtime.paths import application_data_directory

LOGGER = logging.getLogger(__name__)


class ApplicationContainer(containers.DeclarativeContainer):
    settings = providers.Singleton(QSettings)
    location_gateway = providers.Singleton(ESCALELocationProvider)
    location_service = providers.Factory(
        LocationService,
        provider=location_gateway,
    )
    checkpoint_gateway = providers.Singleton(DownloadCheckpointGateway)
    scrape_runner = providers.Singleton(
        ScrapeRunner,
        checkpoint_gateway=checkpoint_gateway,
    )
    export_gateway = providers.Singleton(FilesystemExportGateway)
    export_data = providers.Singleton(
        ExportData,
        gateway=export_gateway,
    )
    export_runner = providers.Factory(
        ExportRunner,
        use_case=export_data,
    )
    offline_database_repository = providers.Singleton(SqliteOfflineDatabaseRepository)
    offline_database_use_cases = providers.Singleton(
        OfflineDatabaseUseCases,
        repository=offline_database_repository,
    )
    local_database_search_runner = providers.Factory(
        LocalDatabaseSearchRunner,
        use_cases=offline_database_use_cases,
    )
    school_search_workflow = providers.Factory(
        SchoolSearchWorkflow,
        scrape_runner=scrape_runner,
        local_database_search_runner=local_database_search_runner,
    )
    data_directory = providers.Callable(application_data_directory)
    export_history_store = providers.Factory(
        QSettingsExportHistoryStore,
        settings=settings,
    )
    preferences = providers.Factory(
        QSettingsDesktopPreferences,
        settings=settings,
    )
    district_map_catalog = providers.Singleton(DistrictMapCatalog)
    saved_search_service = providers.Factory(
        PortableSavedSearchService,
        district_map_catalog=district_map_catalog,
    )
    school_search_service = providers.Factory(
        SchoolSearchRequestService,
        district_map_catalog=district_map_catalog,
        offline_database_use_cases=offline_database_use_cases,
    )
    offline_database_update_service = providers.Factory(
        OfflineDatabaseUpdateService,
        offline_database_use_cases=offline_database_use_cases,
        offline_database_download=scrape_runner,
    )
    export_request_service = providers.Factory(ExportRequestService)
    school_finder_view_model = providers.Factory(
        SchoolFinderViewModel,
        location_service=location_service,
        school_search_workflow=school_search_workflow,
        preferences=preferences,
        export_runner=export_runner,
        offline_database_use_cases=offline_database_use_cases,
        offline_database_update_service=offline_database_update_service,
        export_history_store=export_history_store,
        district_map_catalog=district_map_catalog,
        saved_search_service=saved_search_service,
        school_search_service=school_search_service,
        export_request_service=export_request_service,
        data_directory=data_directory,
    )


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


def create_view_model(
    location_service: LocationServicePort | None = None,
    scrape_runner: ScrapeRunnerPort | None = None,
    export_runner: ExportRunnerPort | None = None,
    local_database_search_runner: LocalDatabaseSearchRunnerPort | None = None,
    offline_database_use_cases: OfflineDatabaseUseCasesPort | None = None,
    offline_database_update_service: OfflineDatabaseUpdateServicePort | None = None,
    export_history_store: ExportHistoryStorePort | None = None,
    district_map_catalog: DistrictMapCatalogPort | None = None,
    preferences: DesktopPreferencesPort | None = None,
    saved_search_service: PortableSavedSearchServicePort | None = None,
    school_search_service: SchoolSearchRequestServicePort | None = None,
    export_request_service: ExportRequestServicePort | None = None,
    school_search_workflow: SchoolSearchWorkflowPort | None = None,
) -> SchoolFinderViewModel:
    container = ApplicationContainer()
    if location_service is not None:
        container.location_service.override(providers.Object(location_service))
    if scrape_runner is not None:
        container.scrape_runner.override(providers.Object(scrape_runner))
    if export_runner is not None:
        container.export_runner.override(providers.Object(export_runner))
    if local_database_search_runner is not None:
        container.local_database_search_runner.override(
            providers.Object(local_database_search_runner)
        )
    if offline_database_use_cases is not None:
        container.offline_database_use_cases.override(
            providers.Object(offline_database_use_cases)
        )
    if offline_database_update_service is not None:
        container.offline_database_update_service.override(
            providers.Object(offline_database_update_service)
        )
    if export_history_store is not None:
        container.export_history_store.override(
            providers.Object(export_history_store)
        )
    if district_map_catalog is not None:
        container.district_map_catalog.override(
            providers.Object(district_map_catalog)
        )
    if preferences is not None:
        container.preferences.override(providers.Object(preferences))
    if saved_search_service is not None:
        container.saved_search_service.override(
            providers.Object(saved_search_service)
        )
    if school_search_service is not None:
        container.school_search_service.override(
            providers.Object(school_search_service)
        )
    if export_request_service is not None:
        container.export_request_service.override(
            providers.Object(export_request_service)
        )
    if school_search_workflow is not None:
        container.school_search_workflow.override(
            providers.Object(school_search_workflow)
        )
    return container.school_finder_view_model()


def main(
    view_model_factory: Callable[[], SchoolFinderViewModel] = create_view_model,
) -> int:
    log_path = configure_logging()
    LOGGER.info("Starting EduEscale; log file: %s", log_path)
    previous_qt_handler = qInstallMessageHandler(log_qt_message)
    try:
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
        engine = QQmlApplicationEngine()
        engine.addImportPath(resource_url("qml").toString())
        view_model = view_model_factory()
        engine.setInitialProperties({"viewModel": view_model})
        qml_url = resource_url("qml/Main.qml")
        engine.load(qml_url)
        if not engine.rootObjects():
            LOGGER.error(
                "Could not load the main QML interface from %s", qml_url.toString()
            )
            view_model.shutdown()
            return 1
        exit_code = application.exec()
        LOGGER.info("EduEscale exited with code %d.", exit_code)
        return exit_code
    except Exception:
        LOGGER.exception("EduEscale terminated unexpectedly.")
        raise
    finally:
        qInstallMessageHandler(previous_qt_handler)


if __name__ == "__main__":
    raise SystemExit(main())
