package pe.eduescale.mobile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapView
import org.maplibre.android.offline.OfflineManager
import pe.eduescale.mobile.activity.SchoolActivityController
import pe.eduescale.mobile.data.ActivityBackupController
import pe.eduescale.mobile.data.SchoolActivityRepository
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolFileStorage
import pe.eduescale.mobile.data.SchoolImportController
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.location.LocationController
import pe.eduescale.mobile.map.MapCameraStore
import pe.eduescale.mobile.map.OfflineMapController
import pe.eduescale.mobile.map.SchoolMapController
import pe.eduescale.mobile.ui.map.TouchRoutingFrameLayout
import pe.eduescale.mobile.navigation.SchoolNavigator
import pe.eduescale.mobile.reminders.ReminderController
import pe.eduescale.mobile.ui.EduEscaleApp
import pe.eduescale.mobile.ui.EduEscaleViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: EduEscaleViewModel by viewModels()
    private lateinit var mapView: MapView
    private val schoolNavigator by lazy { SchoolNavigator(this) }
    private lateinit var mapController: SchoolMapController
    @Inject lateinit var schoolFileStorage: SchoolFileStorage
    @Inject lateinit var schoolActivityRepository: SchoolActivityRepository
    @Inject lateinit var offlineManager: OfflineManager

    private val schoolImport by lazy {
        SchoolImportController(
            contentResolver = contentResolver,
            storage = schoolFileStorage,
            preferences = getSharedPreferences(
                SchoolImportController.FILE_PREFERENCES,
                MODE_PRIVATE,
            ),
            isActive = { !isFinishing && !isDestroyed },
            onLoaded = { file, fileName, fromUser ->
                if (fromUser) {
                    mapController.hasPendingUserFileImport = true
                    mapController.shouldFocusOnNextMapDataUpdate = true
                }
                viewModel.setSchoolFile(file, fileName)
            },
            onImporting = { viewModel.setImporting(true) },
            onError = viewModel::setImportError,
        )
    }
    private val mapCameraStore by lazy { MapCameraStore(this) }
    private val reminderController by lazy {
        ReminderController(
            activity = this,
            viewModel = viewModel,
            repositoryProvider = { schoolActivityRepository },
            activityProvider = { activityController },
            showReminderMessage = ::showReminderMessage,
        )
    }
    private val activityController by lazy {
        SchoolActivityController(
            scope = lifecycleScope,
            repository = schoolActivityRepository,
            viewModel = viewModel,
            showMessage = ::showReminderMessage,
        )
    }
    private val activityBackup by lazy {
        ActivityBackupController(
            scope = lifecycleScope,
            contentResolver = contentResolver,
            repository = schoolActivityRepository,
            showMessage = ::showReminderMessage,
            onRestored = {
                reminderController.restorePendingReminders(showPermissionMessage = true)
                val state = viewModel.state.value
                state.selectedSchool?.let { activityController.refreshSchoolActivity(it, state.commentSearchQuery) }
            },
        )
    }
    private val locationController by lazy {
        LocationController(this, viewModel, { mapController.map }, { mapController.style })
    }
    private val offlineMapController by lazy {
        OfflineMapController(this, viewModel, offlineManager) { mapController.style != null }
    }
    private lateinit var externalFileBackCallback: OnBackPressedCallback

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            schoolImport.import(uri)
        }
    }

    private val createActivityBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(activityBackup::export)
    }

    private val openActivityBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(activityBackup::import)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Activity result launchers must be registered before STARTED, and need the ViewModel store.
        reminderController
        locationController
        externalFileBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (!moveTaskToBack(true)) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, externalFileBackCallback)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        mapView = MapView(this)
        mapController = SchoolMapController(
            context = this,
            mapView = mapView,
            viewModel = viewModel,
            mapCameraStore = mapCameraStore,
            offlineMapController = { offlineMapController },
            locationController = { locationController },
            onSchoolTapped = ::showSchoolDetails,
        )
        mapView.onCreate(savedInstanceState)

        val root = TouchRoutingFrameLayout(this, mapView) { mapController.bottomSystemInsetPx }
        root.addView(
            mapView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            ComposeView(this).apply {
                setContent {
                    EduEscaleApp(
                        viewModel = viewModel,
                        onMapTouchEnabled = { enabled -> root.mapTouchEnabled = enabled },
                        onMapTopOverlayBottomChanged = {
                            root.mapTopOverlayBottomPx = it
                        },
                        onSchoolDetailsSheetShown = { school, sheetTopOnScreen ->
                            mapController.alignSelectedSchoolWithSheet(
                                school,
                                sheetTopOnScreen,
                                root.mapTopOverlayBottomPx,
                            )
                        },
                        onMapDownloadControlBoundsChanged = { left, top, right, bottom ->
                            root.mapDownloadControlBounds.set(left, top, right, bottom)
                        },
                        onPickFile = { openDocument.launch(arrayOf("*/*")) },
                        onLocate = locationController::showMyLocation,
                        onApplyFilters = viewModel::setFilters,
                        onSchoolSelected = { school ->
                            showSchoolDetails(school)
                            mapController.focusSelectedSchoolFromList(school)
                        },
                        onDirections = schoolNavigator::showNavigationOptions,
                        onShare = schoolNavigator::shareSchoolLocation,
                        onAddComment = activityController::addComment,
                        onDeleteComment = activityController::deleteComment,
                        onEditComment = activityController::editComment,
                        onSearchComments = activityController::searchComments,
                        onAddReminder = reminderController::createSchoolReminder,
                        onDeleteReminder = reminderController::deleteSchoolReminder,
                        onExportActivityBackup = {
                            createActivityBackup.launch("EduEscale-copia.json")
                        },
                        onImportActivityBackup = {
                            openActivityBackup.launch(arrayOf("application/json"))
                        },
                        onDownloadVisibleArea = offlineMapController::downloadVisibleMapArea,
                        onToggleOfflineMapArea = offlineMapController::setOfflineMapAreaDownloadState,
                        onDeleteOfflineMapArea = offlineMapController::deleteOfflineMapArea,
                        onRefreshActivityHistory = activityController::refreshActivityHistory,
                        onEditHistoryComment = activityController::editHistoryComment,
                        onEditHistoryReminder = reminderController::editHistoryReminder,
                        onPickReminderDateTime = reminderController::pickReminderDateTime,
                        onClearSelection = {
                            mapController.clearSelectionState()
                            viewModel.selectSchool(null)
                        },
                    )
                }
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            mapController.bottomSystemInsetPx = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            mapController.updateCompassPosition()
            insets
        }
        setContentView(root)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true
        mapView.getMapAsync(mapController::configureMap)

        var observedFile: SchoolFile? = null
        var observedVisibleSchools: List<SchoolRecord>? = null
        var observedSelectedSchoolId: Int? = null
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    mapController.onSchoolFileLoaded(state.schoolFile, state.visibleSchools)
                    val selectedSchoolId = state.selectedSchool?.id
                    if (
                        observedFile !== state.schoolFile ||
                        observedVisibleSchools != state.visibleSchools ||
                        observedSelectedSchoolId != selectedSchoolId
                    ) {
                        observedFile = state.schoolFile
                        observedVisibleSchools = state.visibleSchools
                        observedSelectedSchoolId = selectedSchoolId
                        mapController.updateMapData()
                    }
                }
            }
        }
        schoolImport.restore()
        handleIncomingIntent(intent)
        reminderController.restorePendingReminders()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incomingIntent: Intent): Boolean {
        val uri = incomingIntent.data.takeIf {
            incomingIntent.action == Intent.ACTION_VIEW
        }
        externalFileBackCallback.isEnabled = uri != null
        uri?.let(schoolImport::import)
        return uri != null
    }

    private fun showSchoolDetails(school: SchoolRecord) {
        viewModel.selectSchool(school)
        activityController.refreshSchoolActivity(school, "")
    }

    private fun showReminderMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        locationController.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        locationController.refreshLocationServices()
        reminderController.restorePendingReminders()
        activityController.refreshActivityHistory()
        val state = viewModel.state.value
        state.selectedSchool?.let { activityController.refreshSchoolActivity(it, state.commentSearchQuery) }
    }

    override fun onPause() {
        mapController.map?.let { mapCameraStore.save(it.cameraPosition) }
        locationController.onPause()
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        locationController.onStop()
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        schoolImport.shutdown()
        offlineMapController.release()
        mapView.onDestroy()
        super.onDestroy()
    }
}
