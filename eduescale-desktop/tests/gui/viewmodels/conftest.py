import pytest
from PySide6.QtCore import QCoreApplication, QObject, QSettings, Signal

from eduescale.application.use_cases.offline_database import (
    OfflineDatabaseUseCases,
)
from eduescale.gui.services.district_map_catalog import DistrictMapCatalog
from eduescale.gui.services.export_history import QSettingsExportHistoryStore
from eduescale.gui.services.export_request_service import ExportRequestService
from eduescale.gui.services.offline_database_update import OfflineDatabaseUpdateService
from eduescale.gui.services.preferences import QSettingsDesktopPreferences
from eduescale.gui.services.saved_search_service import PortableSavedSearchService
from eduescale.gui.services.school_search_service import (
    SchoolSearchRequestService,
)
from eduescale.gui.services.school_search_workflow import SchoolSearchWorkflow
from eduescale.gui.viewmodels import SchoolFinderViewModel
from eduescale.infrastructure.persistence.local_database import (
    SqliteOfflineDatabaseRepository,
)


class FakeLocationService(QObject):
    loaded = Signal(str, str, object)
    failed = Signal(str, str, str)

    def load(self, kind, parent_code=""):
        pass

    def shutdown(self):
        pass


class FakeScrapeRunner(QObject):
    started = Signal()
    progressChanged = Signal(object)
    schoolsReceived = Signal(object)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)
    cancelled = Signal()
    warning = Signal(str, str)

    def __init__(self):
        super().__init__()
        self.started_requests = []
        self.cancel_requests = 0

    def start(self, output_path, filter_sets, *, purpose):
        self.started_requests.append((output_path, filter_sets, purpose))

    def cancel(self):
        self.cancel_requests += 1

    def complete_database_download(self):
        return None

    def shutdown(self):
        pass


class FakeLocalDatabaseSearchRunner(QObject):
    started = Signal()
    progressChanged = Signal(object)
    schoolsReceived = Signal(object)
    completed = Signal(str, int, int, int)
    failed = Signal(str, str)
    cancelled = Signal()

    def __init__(self):
        super().__init__()
        self.started_requests = []
        self.cancel_requests = 0

    def start(self, source_path, filters):
        self.started_requests.append((source_path, filters))

    def cancel(self):
        self.cancel_requests += 1

    def shutdown(self):
        pass


class FakeExportRunner(QObject):
    started = Signal(str)
    completed = Signal(str, str)
    failed = Signal(str, str, str)

    def __init__(self):
        super().__init__()
        self.started_requests = []

    def start(
        self,
        export_type,
        database_path,
        output_path,
        password=None,
        compressed=False,
    ):
        request = (export_type, database_path, output_path)
        if password is not None or compressed:
            request += (password, compressed)
        self.started_requests.append(request)

    def shutdown(self):
        pass


@pytest.fixture
def fake_location_service():
    return FakeLocationService()


@pytest.fixture
def fake_scrape_runner():
    return FakeScrapeRunner()


@pytest.fixture
def fake_local_database_search_runner():
    return FakeLocalDatabaseSearchRunner()


@pytest.fixture
def fake_export_runner():
    return FakeExportRunner()


@pytest.fixture
def view_model_components(tmp_path):
    application = QCoreApplication.instance() or QCoreApplication([])
    location_service = FakeLocationService()
    scrape_runner = FakeScrapeRunner()
    local_database_search_runner = FakeLocalDatabaseSearchRunner()
    export_runner = FakeExportRunner()
    settings = QSettings(
        str(tmp_path / "settings.ini"),
        QSettings.Format.IniFormat,
    )
    district_map_catalog = DistrictMapCatalog()
    offline_database_use_cases = OfflineDatabaseUseCases(
        SqliteOfflineDatabaseRepository()
    )
    view_model = SchoolFinderViewModel(
        location_service=location_service,
        school_search_workflow=SchoolSearchWorkflow(
            scrape_runner,
            local_database_search_runner,
        ),
        preferences=QSettingsDesktopPreferences(settings),
        export_runner=export_runner,
        offline_database_update_service=OfflineDatabaseUpdateService(
            offline_database_use_cases,
            scrape_runner,
        ),
        export_history_store=QSettingsExportHistoryStore(settings),
        district_map_catalog=district_map_catalog,
        saved_search_service=PortableSavedSearchService(district_map_catalog),
        school_search_service=SchoolSearchRequestService(
            district_map_catalog,
            offline_database_use_cases,
        ),
        export_request_service=ExportRequestService(),
        offline_database_use_cases=offline_database_use_cases,
        data_directory=tmp_path,
    )
    errors = []
    view_model.errorRaised.connect(
        lambda title, details: errors.append((title, details))
    )
    yield view_model, scrape_runner, errors
    view_model.shutdown()
    assert application is not None
