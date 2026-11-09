package pe.eduescale.mobile

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolFileStorage
import pe.eduescale.mobile.data.SchoolComment
import pe.eduescale.mobile.data.SchoolCoordinates
import pe.eduescale.mobile.data.SchoolReminder
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.SchoolActivityRepository
import pe.eduescale.mobile.data.SchoolActivityBackup
import pe.eduescale.mobile.data.OfflineTileEstimator
import pe.eduescale.mobile.data.activityKey
import pe.eduescale.mobile.reminders.SchoolReminderAlarms
import pe.eduescale.mobile.map.MapCameraStore
import pe.eduescale.mobile.map.OfflineAreaMetadata
import pe.eduescale.mobile.map.SchoolMarkerStyle
import pe.eduescale.mobile.navigation.SchoolNavigationLinks
import pe.eduescale.mobile.reminders.PendingReminder
import pe.eduescale.mobile.ui.EduEscaleApp
import pe.eduescale.mobile.ui.EduEscaleViewModel
import pe.eduescale.mobile.ui.OfflineMapAreaUi
import pe.eduescale.mobile.ui.OfflineMapBounds
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.InputStreamReader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.Executors

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: EduEscaleViewModel by viewModels()
    private lateinit var mapView: MapView
    @Inject lateinit var schoolFileStorage: SchoolFileStorage
    @Inject lateinit var schoolActivityRepository: SchoolActivityRepository
    @Inject lateinit var offlineManager: OfflineManager
    private var schoolActivityLoadJob: Job? = null
    private var activityHistoryLoadJob: Job? = null
    private var pendingReminder: PendingReminder? = null
    private var selectedSchoolCameraFocusInProgress = false
    private var pendingSchoolSheetAlignment: PendingSchoolSheetAlignment? = null

    private val importExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mapCameraStore by lazy { MapCameraStore(this) }
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    private val currentLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            viewModel.setUserLocation(location.latitude, location.longitude)
            locationManager.removeUpdates(this)
        }

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    private val locationProviderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshLocationServices()
        }
    }
    private var locationProviderReceiverRegistered = false
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var bottomSystemInsetPx = 0
    private var schoolFile: SchoolFile? = null
    private var schoolsById: Map<Int, SchoolRecord> = emptyMap()
    private var visibleSchools: List<SchoolRecord> = emptyList()
    private var shouldFocusOnNextMapDataUpdate = true
    private var hasPendingUserFileImport = false
    private val offlineRegions = mutableMapOf<Long, OfflineRegion>()
    private val offlineAreaMetadata = mutableMapOf<Long, OfflineAreaMetadata>()
    private val invalidOfflineRegions = mutableSetOf<Long>()
    private lateinit var externalFileBackCallback: OnBackPressedCallback

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importFile(uri)
        }
    }

    private val createActivityBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(::exportActivityBackup)
    }

    private val openActivityBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(::importActivityBackup)
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) {
            showMyLocation()
        } else {
            Toast.makeText(
                this,
                "Permite el acceso a la ubicación para mostrarla en el mapa.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val reminder = pendingReminder ?: return@registerForActivityResult
        if (granted) {
            requestExactAlarmPermission(reminder)
        } else {
            pendingReminder = null
            showNotificationSettings()
        }
    }

    private val requestExactAlarmSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val reminder = pendingReminder ?: return@registerForActivityResult
        if (SchoolReminderAlarms.canScheduleExactAlarms(this)) {
            restorePendingReminders()
            saveAndScheduleReminder(reminder)
        } else {
            pendingReminder = null
            showReminderMessage("No se concedió el permiso para programar alarmas exactas.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val root = TouchRoutingFrameLayout()
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
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
                            alignSelectedSchoolWithSheet(
                                school,
                                sheetTopOnScreen,
                                root.mapTopOverlayBottomPx,
                            )
                        },
                        onMapDownloadControlBoundsChanged = { left, top, right, bottom ->
                            root.mapDownloadControlBounds.set(left, top, right, bottom)
                        },
                        onPickFile = { openDocument.launch(arrayOf("*/*")) },
                        onLocate = ::showMyLocation,
                        onApplyFilters = viewModel::setFilters,
                        onSchoolSelected = { school ->
                            showSchoolDetails(school)
                            focusSelectedSchoolFromList(school)
                        },
                        onDirections = ::showNavigationOptions,
                        onShare = ::shareSchoolLocation,
                        onAddComment = ::addSchoolComment,
                        onDeleteComment = ::deleteSchoolComment,
                        onEditComment = ::editSchoolComment,
                        onSearchComments = ::searchSchoolComments,
                        onAddReminder = ::createSchoolReminder,
                        onDeleteReminder = ::deleteSchoolReminder,
                        onExportActivityBackup = {
                            createActivityBackup.launch("EduEscale-copia.json")
                        },
                        onImportActivityBackup = {
                            openActivityBackup.launch(arrayOf("application/json"))
                        },
                        onDownloadVisibleArea = ::downloadVisibleMapArea,
                        onToggleOfflineMapArea = ::setOfflineMapAreaDownloadState,
                        onDeleteOfflineMapArea = ::deleteOfflineMapArea,
                        onRefreshActivityHistory = ::refreshActivityHistory,
                        onEditHistoryComment = ::editHistoryComment,
                        onEditHistoryReminder = ::editHistoryReminder,
                        onPickReminderDateTime = ::pickReminderDateTime,
                        onClearSelection = {
                            selectedSchoolCameraFocusInProgress = false
                            pendingSchoolSheetAlignment = null
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
            bottomSystemInsetPx = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            updateCompassPosition()
            insets
        }
        setContentView(root)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true
        mapView.getMapAsync(::configureMap)

        var observedFile: SchoolFile? = null
        var observedVisibleSchools: List<SchoolRecord>? = null
        var observedSelectedSchoolId: Int? = null
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    schoolFile = state.schoolFile
                    visibleSchools = state.visibleSchools
                    schoolsById = state.schoolFile?.schools
                        ?.associateBy(SchoolRecord::id)
                        .orEmpty()
                    val selectedSchoolId = state.selectedSchool?.id
                    if (
                        observedFile !== state.schoolFile ||
                        observedVisibleSchools != state.visibleSchools ||
                        observedSelectedSchoolId != selectedSchoolId
                    ) {
                        observedFile = state.schoolFile
                        observedVisibleSchools = state.visibleSchools
                        observedSelectedSchoolId = selectedSchoolId
                        updateMapData()
                    }
                }
            }
        }
        restoreSavedFile()
        handleIncomingIntent(intent)
        restorePendingReminders()
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
        uri?.let(::importFile)
        return uri != null
    }

    private fun restoreSavedFile() {
        importExecutor.execute {
            try {
                val parsed = schoolFileStorage.restore() ?: return@execute
                val fileName = getSharedPreferences(FILE_PREFERENCES, MODE_PRIVATE)
                    .getString(LAST_FILE_NAME, null)
                    ?: "Archivo EduScale"
                mainHandler.post {
                    if (isFinishing || isDestroyed) {
                        return@post
                    }
                    viewModel.setSchoolFile(parsed, fileName)
                }
            } catch (error: IOException) {
                showImportError(error.message ?: "No se pudo restaurar el archivo guardado.")
            } catch (error: IllegalArgumentException) {
                showImportError(error.message ?: "El archivo guardado no es válido.")
            }
        }
    }

    private fun restorePendingReminders(showPermissionMessage: Boolean = false) {
        lifecycleScope.launch {
            try {
                val reminders = schoolActivityRepository.pendingReminders()
                if (
                    reminders.isNotEmpty() &&
                    !NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                ) {
                    if (showPermissionMessage) {
                        showNotificationSettings()
                    }
                    return@launch
                }
                if (
                    reminders.isNotEmpty() &&
                    !SchoolReminderAlarms.canScheduleExactAlarms(this@MainActivity)
                ) {
                    if (showPermissionMessage) {
                        showExactAlarmSettings()
                    }
                    return@launch
                }
                reminders.forEach { reminder ->
                    SchoolReminderAlarms.schedule(this@MainActivity, reminder)
                }
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudieron restaurar las alarmas.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudieron leer los recordatorios guardados.")
            } catch (error: SecurityException) {
                showReminderMessage(error.message ?: "Android no permitió restaurar las alarmas.")
            }
        }
    }

    private fun configureMap(mapLibreMap: MapLibreMap) {
        map = mapLibreMap
        updateCompassPosition()
        val savedCameraPosition = mapCameraStore.read()
        shouldFocusOnNextMapDataUpdate =
            savedCameraPosition == null || hasPendingUserFileImport
        mapLibreMap.cameraPosition = savedCameraPosition ?: CameraPosition.Builder()
            .target(LatLng(-9.19, -75.015))
            .zoom(4.0)
            .build()
        mapLibreMap.addOnCameraIdleListener {
            mapCameraStore.save(mapLibreMap.cameraPosition)
            updateVisibleMapBounds(mapLibreMap)
            if (selectedSchoolCameraFocusInProgress) {
                selectedSchoolCameraFocusInProgress = false
                alignPendingSchoolWithSheet()
            }
        }
        mapLibreMap.addOnMapClickListener { coordinate ->
            val school = schoolAtTap(mapLibreMap, coordinate)
            if (school == null) {
                false
            } else {
                showSchoolDetails(school)
                true
            }
        }
        mapLibreMap.setStyle(Style.Builder().fromUri(OPEN_FREE_MAP_STYLE_URL)) { loadedStyle ->
            style = loadedStyle
            updateMapData()
            updateVisibleMapBounds(mapLibreMap)
            refreshOfflineMapAreas()
            refreshLocationServices()
        }
    }

    private fun updateVisibleMapBounds(mapLibreMap: MapLibreMap) {
        if (mapView.width == 0 || mapView.height == 0) {
            return
        }
        val bounds = mapLibreMap.projection.visibleRegion.latLngBounds
        viewModel.setVisibleMapBounds(
            OfflineMapBounds(
                west = bounds.longitudeWest,
                south = bounds.latitudeSouth,
                east = bounds.longitudeEast,
                north = bounds.latitudeNorth,
                cameraZoom = mapLibreMap.cameraPosition.zoom,
            ),
        )
    }

    private fun refreshOfflineMapAreas() {
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                if (regions == null) {
                    showOfflineMapMessage("MapLibre no devolvió la lista de mapas sin conexión.")
                    return
                }
                mainHandler.post {
                    if (isDestroyed) {
                        return@post
                    }
                    offlineRegions.clear()
                    offlineAreaMetadata.clear()
                    invalidOfflineRegions.clear()
                    val areas = regions.map { region ->
                        offlineRegions[region.id] = region
                        val metadata = OfflineAreaMetadata.fromBytes(region.metadata) ?: run {
                            showOfflineMapMessage("Una región sin conexión tiene metadatos no válidos.")
                            OfflineAreaMetadata.UNNAMED
                        }
                        offlineAreaMetadata[region.id] = metadata
                        observeOfflineRegion(region, metadata)
                        OfflineMapAreaUi(
                            id = region.id,
                            name = metadata.name,
                            minZoom = metadata.minZoom,
                            maxZoom = metadata.maxZoom,
                            estimatedTiles = metadata.estimatedTiles,
                            completedTiles = 0,
                            completedBytes = 0,
                            isDownloading = false,
                            isComplete = false,
                        )
                    }
                    viewModel.setOfflineMapAreas(areas)
                    regions.forEach(::refreshOfflineMapAreaStatus)
                }
            }

            override fun onError(error: String) {
                showOfflineMapMessage("No se pudieron cargar los mapas sin conexión: $error")
            }
        })
    }

    private fun observeOfflineRegion(
        region: OfflineRegion,
        metadata: OfflineAreaMetadata,
    ) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                mainHandler.post {
                    if (!isDestroyed) {
                        handleOfflineMapStatus(region, metadata, status)
                    }
                }
            }

            override fun onError(error: OfflineRegionError) {
                showOfflineMapMessage(
                    "Error al descargar ${metadata.name}: ${error.message}",
                )
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                showOfflineMapMessage(
                    "Se alcanzó el límite local de mapas sin conexión ($limit teselas).",
                )
            }
        })
    }

    private fun refreshOfflineMapAreaStatus(region: OfflineRegion) {
        val metadata = offlineAreaMetadata[region.id] ?: return
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status == null) {
                    showOfflineMapMessage("No se pudo consultar el progreso del mapa.")
                    return
                }
                mainHandler.post {
                    if (!isDestroyed) {
                        handleOfflineMapStatus(region, metadata, status)
                    }
                }
            }

            override fun onError(error: String?) {
                showOfflineMapMessage(
                    "No se pudo consultar el mapa sin conexión: ${error ?: "error desconocido"}",
                )
            }
        })
    }

    private fun handleOfflineMapStatus(
        region: OfflineRegion,
        metadata: OfflineAreaMetadata,
        status: OfflineRegionStatus,
    ) {
        if (
            status.completedTileCount > OfflineTileEstimator.MAX_TILE_COUNT ||
            status.completedResourceSize > MAX_OFFLINE_AREA_BYTES
        ) {
            rejectOfflineMapArea(region, metadata)
            return
        }
        val complete = status.isRequiredResourceCountPrecise &&
            status.requiredResourceCount > 0 &&
            status.completedResourceCount >= status.requiredResourceCount
        viewModel.updateOfflineMapArea(
            OfflineMapAreaUi(
                id = region.id,
                name = metadata.name,
                minZoom = metadata.minZoom,
                maxZoom = metadata.maxZoom,
                estimatedTiles = metadata.estimatedTiles,
                completedTiles = status.completedTileCount,
                completedBytes = status.completedResourceSize,
                isDownloading = status.downloadState == OfflineRegion.STATE_ACTIVE && !complete,
                isComplete = complete,
            ),
        )
    }

    private fun downloadVisibleMapArea(
        bounds: OfflineMapBounds,
        minZoom: Int,
        maxZoom: Int,
    ) {
        if (style == null) {
            showOfflineMapMessage("El mapa todavía se está preparando. Inténtalo de nuevo.")
            return
        }
        if (viewModel.state.value.offlineMapAreas.any(OfflineMapAreaUi::isDownloading)) {
            showOfflineMapMessage("Espera a que termine o pausa la descarga actual.")
            return
        }
        val estimatedTiles = try {
            OfflineTileEstimator.estimate(
                west = bounds.west,
                south = bounds.south,
                east = bounds.east,
                north = bounds.north,
                minZoom = minZoom,
                maxZoom = maxZoom,
            )
        } catch (error: IllegalArgumentException) {
            showOfflineMapMessage("El área o el rango de zoom seleccionado no es válido.")
            return
        }
        if (estimatedTiles > OfflineTileEstimator.MAX_TILE_COUNT) {
            showOfflineMapMessage("Reduce el área o el zoom: el máximo es 3.000 teselas.")
            return
        }

        val name = "Área ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}"
        val metadata = OfflineAreaMetadata(
            name = name,
            minZoom = minZoom,
            maxZoom = maxZoom,
            estimatedTiles = estimatedTiles.toInt(),
        )
        val regionBounds = LatLngBounds.Builder()
            .include(LatLng(bounds.south, bounds.west))
            .include(LatLng(bounds.north, bounds.east))
            .build()
        val definition = OfflineTilePyramidRegionDefinition(
            OPEN_FREE_MAP_STYLE_URL,
            regionBounds,
            minZoom.toDouble(),
            maxZoom.toDouble(),
            1f,
        )
        viewModel.setCreatingOfflineMapArea(true)
        viewModel.setOfflineMapMessage("Preparando la descarga del área…")
        try {
            offlineManager.createOfflineRegion(
                definition,
                metadata.toBytes(),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(region: OfflineRegion) {
                        mainHandler.post {
                            if (isDestroyed) {
                                return@post
                            }
                            offlineRegions[region.id] = region
                            offlineAreaMetadata[region.id] = metadata
                            viewModel.setCreatingOfflineMapArea(false)
                            val area = OfflineMapAreaUi(
                                id = region.id,
                                name = metadata.name,
                                minZoom = metadata.minZoom,
                                maxZoom = metadata.maxZoom,
                                estimatedTiles = metadata.estimatedTiles,
                                completedTiles = 0,
                                completedBytes = 0,
                                isDownloading = true,
                                isComplete = false,
                            )
                            viewModel.updateOfflineMapArea(area)
                            observeOfflineRegion(region, metadata)
                            try {
                                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                                refreshOfflineMapAreaStatus(region)
                                viewModel.setOfflineMapMessage("Descarga iniciada: $name")
                            } catch (error: IllegalStateException) {
                                viewModel.updateOfflineMapArea(area.copy(isDownloading = false))
                                showOfflineMapMessage(
                                    error.message ?: "No se pudo iniciar la descarga.",
                                )
                            } catch (error: SecurityException) {
                                viewModel.updateOfflineMapArea(area.copy(isDownloading = false))
                                showOfflineMapMessage(
                                    error.message ?: "Android bloqueó la descarga del mapa.",
                                )
                            }
                        }
                    }

                    override fun onError(error: String) {
                        viewModel.setCreatingOfflineMapArea(false)
                        showOfflineMapMessage("No se pudo crear el mapa sin conexión: $error")
                    }
                },
            )
        } catch (error: IllegalArgumentException) {
            viewModel.setCreatingOfflineMapArea(false)
            showOfflineMapMessage(error.message ?: "No se pudo preparar el área seleccionada.")
        } catch (error: SecurityException) {
            viewModel.setCreatingOfflineMapArea(false)
            showOfflineMapMessage(error.message ?: "Android bloqueó la descarga del mapa.")
        }
    }

    private fun setOfflineMapAreaDownloadState(id: Long, active: Boolean) {
        val region = offlineRegions[id]
        if (region == null) {
            showOfflineMapMessage("No se encontró el área guardada. Actualiza la lista.")
            refreshOfflineMapAreas()
            return
        }
        if (
            active &&
            viewModel.state.value.offlineMapAreas.any {
                it.id != id && it.isDownloading
            }
        ) {
            showOfflineMapMessage("Solo se puede descargar un área a la vez.")
            return
        }
        try {
            region.setDownloadState(
                if (active) OfflineRegion.STATE_ACTIVE else OfflineRegion.STATE_INACTIVE,
            )
            refreshOfflineMapAreaStatus(region)
            viewModel.setOfflineMapMessage(
                if (active) "Descarga reanudada." else "Descarga pausada.",
            )
        } catch (error: IllegalStateException) {
            showOfflineMapMessage(error.message ?: "No se pudo cambiar el estado de la descarga.")
        } catch (error: SecurityException) {
            showOfflineMapMessage(error.message ?: "Android bloqueó el cambio de descarga.")
        }
    }

    private fun deleteOfflineMapArea(id: Long) {
        val region = offlineRegions[id]
        if (region == null) {
            showOfflineMapMessage("No se encontró el área que quieres eliminar.")
            refreshOfflineMapAreas()
            return
        }
        try {
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() {
                    mainHandler.post {
                        offlineRegions.remove(id)
                        offlineAreaMetadata.remove(id)
                        invalidOfflineRegions.remove(id)
                        viewModel.removeOfflineMapArea(id)
                        viewModel.setOfflineMapMessage("Mapa sin conexión eliminado.")
                    }
                }

                override fun onError(error: String) {
                    showOfflineMapMessage("No se pudo eliminar el mapa guardado: $error")
                }
            })
        } catch (error: IllegalStateException) {
            showOfflineMapMessage(error.message ?: "No se pudo eliminar el mapa guardado.")
        } catch (error: SecurityException) {
            showOfflineMapMessage(error.message ?: "Android bloqueó la eliminación del mapa.")
        }
    }

    private fun rejectOfflineMapArea(region: OfflineRegion, metadata: OfflineAreaMetadata) {
        if (!invalidOfflineRegions.add(region.id)) {
            return
        }
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                mainHandler.post {
                    offlineRegions.remove(region.id)
                    offlineAreaMetadata.remove(region.id)
                    invalidOfflineRegions.remove(region.id)
                    viewModel.removeOfflineMapArea(region.id)
                    showOfflineMapMessage(
                        "${metadata.name} superó el límite de 3.000 teselas o 50 MB y se eliminó.",
                    )
                }
            }

            override fun onError(error: String) {
                mainHandler.post {
                    invalidOfflineRegions.remove(region.id)
                    showOfflineMapMessage(
                        "${metadata.name} superó el límite y se pausó, pero no se pudo eliminar: $error",
                    )
                }
            }
        })
    }

    private fun showOfflineMapMessage(message: String) {
        mainHandler.post {
            if (!isDestroyed) {
                viewModel.setCreatingOfflineMapArea(false)
                viewModel.setOfflineMapMessage(message, isError = true)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun schoolAtTap(mapLibreMap: MapLibreMap, coordinate: LatLng): SchoolRecord? {
        val tapPoint = mapLibreMap.projection.toScreenLocation(coordinate)
        val hitRadius = dp(MARKER_TOUCH_RADIUS_DP).toFloat()
        fun distanceSquared(school: SchoolRecord): Float {
            val coordinates = school.coordinates ?: return Float.POSITIVE_INFINITY
            val markerPoint = mapLibreMap.projection.toScreenLocation(
                LatLng(coordinates.latitude, coordinates.longitude),
            )
            val dx = markerPoint.x - tapPoint.x
            val dy = markerPoint.y - tapPoint.y
            return dx * dx + dy * dy
        }

        val hitBounds = RectF(
            tapPoint.x - hitRadius,
            tapPoint.y - hitRadius,
            tapPoint.x + hitRadius,
            tapPoint.y + hitRadius,
        )
        return mapLibreMap.queryRenderedFeatures(hitBounds, SCHOOL_LAYER)
            .mapNotNull { feature ->
                feature.getStringProperty(SCHOOL_ID_PROPERTY)
                    ?.toIntOrNull()
                    ?.let(schoolsById::get)
            }
            .distinctBy(SchoolRecord::id)
            .minByOrNull(::distanceSquared)
            ?: visibleSchools
                .asSequence()
                .map { school -> school to distanceSquared(school) }
                .filter { (_, distance) -> distance <= hitRadius * hitRadius }
                .minByOrNull { (_, distance) -> distance }
                ?.first
    }

    private fun updateCompassPosition() {
        val currentMap = map ?: return
        currentMap.uiSettings.apply {
            setCompassGravity(Gravity.BOTTOM or Gravity.END)
            setCompassMargins(
                0,
                0,
                dp(16),
                dp(COMPASS_BOTTOM_OFFSET_DP) + bottomSystemInsetPx,
            )
        }
    }

    private data class PendingSchoolSheetAlignment(
        val school: SchoolRecord,
        val sheetTopOnScreen: Float,
        val mapTopOverlayBottomPx: Float,
    )

    private fun showSchoolDetails(school: SchoolRecord) {
        viewModel.selectSchool(school)
        refreshSchoolActivity(school, "")
    }

    private fun focusSelectedSchoolFromList(school: SchoolRecord) {
        val currentMap = map ?: return
        if (!school.hasCoordinates) {
            return
        }
        selectedSchoolCameraFocusInProgress = true
        focusSchools(currentMap, listOf(school))
    }

    private fun alignSelectedSchoolWithSheet(
        school: SchoolRecord,
        sheetTopOnScreen: Float,
        mapTopOverlayBottomPx: Float,
    ) {
        pendingSchoolSheetAlignment = PendingSchoolSheetAlignment(
            school = school,
            sheetTopOnScreen = sheetTopOnScreen,
            mapTopOverlayBottomPx = mapTopOverlayBottomPx,
        )
        alignPendingSchoolWithSheet()
    }

    private fun alignPendingSchoolWithSheet() {
        if (selectedSchoolCameraFocusInProgress) {
            return
        }
        val pendingAlignment = pendingSchoolSheetAlignment ?: return
        val currentMap = map ?: return
        val coordinates = pendingAlignment.school.coordinates
        if (coordinates == null) {
            pendingSchoolSheetAlignment = null
            return
        }
        if (
            viewModel.state.value.selectedSchool?.id != pendingAlignment.school.id ||
            mapView.width == 0 ||
            mapView.height == 0
        ) {
            pendingSchoolSheetAlignment = null
            return
        }

        val mapScreenLocation = IntArray(2)
        mapView.getLocationOnScreen(mapScreenLocation)
        val sheetTopInMap = pendingAlignment.sheetTopOnScreen - mapScreenLocation[1]
        val availableMapTop = pendingAlignment.mapTopOverlayBottomPx
        val availableMapHeight = sheetTopInMap - availableMapTop
        if (availableMapHeight <= dp(MIN_VISIBLE_MAP_REGION_DP)) {
            pendingSchoolSheetAlignment = null
            return
        }

        val selectedPoint = currentMap.projection.toScreenLocation(
            LatLng(coordinates.latitude, coordinates.longitude),
        )
        val visibleCenter = PointF(
            mapView.width / 2f,
            availableMapTop + availableMapHeight / 2f,
        )
        val cameraTargetPoint = PointF(
            (mapView.width / 2f + selectedPoint.x - visibleCenter.x)
                .coerceIn(0f, mapView.width.toFloat()),
            (mapView.height / 2f + selectedPoint.y - visibleCenter.y)
                .coerceIn(0f, mapView.height.toFloat()),
        )
        val cameraTarget = currentMap.projection.fromScreenLocation(cameraTargetPoint)
        pendingSchoolSheetAlignment = null
        currentMap.animateCamera(CameraUpdateFactory.newLatLng(cameraTarget))
    }

    private fun addSchoolComment(school: SchoolRecord, text: String) {
        lifecycleScope.launch {
            try {
                schoolActivityRepository.addComment(school.activityKey(), text)
                refreshSchoolActivity(school, viewModel.state.value.commentSearchQuery)
            } catch (error: IllegalArgumentException) {
                showReminderMessage(error.message ?: "No se pudo guardar el comentario.")
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudo guardar el comentario.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudo guardar el comentario.")
            }
        }
    }

    private fun deleteSchoolComment(school: SchoolRecord, commentId: String) {
        lifecycleScope.launch {
            try {
                schoolActivityRepository.deleteComment(school.activityKey(), commentId)
                refreshSchoolActivity(school, viewModel.state.value.commentSearchQuery)
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudo eliminar el comentario.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudo eliminar el comentario.")
            }
        }
    }

    private fun editSchoolComment(school: SchoolRecord, commentId: String, text: String) {
        lifecycleScope.launch {
            try {
                schoolActivityRepository.updateComment(school.activityKey(), commentId, text)
                refreshSchoolActivity(school, viewModel.state.value.commentSearchQuery)
            } catch (error: IllegalArgumentException) {
                showReminderMessage(error.message ?: "No se pudo editar el comentario.")
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudo editar el comentario.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudo editar el comentario.")
            }
        }
    }

    private fun searchSchoolComments(school: SchoolRecord, query: String) {
        viewModel.setCommentSearchQuery(query)
        refreshSchoolActivity(school, query)
    }

    private fun refreshSchoolActivity(school: SchoolRecord, query: String) {
        val schoolKey = school.activityKey()
        schoolActivityLoadJob?.cancel()
        schoolActivityLoadJob = lifecycleScope.launch {
            try {
                val comments = schoolActivityRepository.commentsFor(schoolKey, query)
                val reminders = schoolActivityRepository.remindersFor(schoolKey)
                val current = viewModel.state.value
                if (
                    current.selectedSchool?.activityKey() == schoolKey &&
                    current.commentSearchQuery == query
                ) {
                    viewModel.setSchoolActivity(comments, reminders)
                }
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudieron leer los datos del colegio.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudieron leer los datos del colegio.")
            }
        }
    }

    private fun refreshActivityHistory() {
        activityHistoryLoadJob?.cancel()
        activityHistoryLoadJob = lifecycleScope.launch {
            viewModel.setActivityHistoryLoading(true)
            try {
                schoolActivityRepository.expirePastReminders()
                val comments = schoolActivityRepository.allComments()
                val reminders = schoolActivityRepository.allReminders()
                viewModel.setActivityHistory(comments, reminders)
            } catch (error: IOException) {
                viewModel.setActivityHistoryError(
                    error.message ?: "No se pudo cargar el historial.",
                )
            } catch (error: SQLiteException) {
                viewModel.setActivityHistoryError(
                    error.message ?: "No se pudo leer el historial guardado.",
                )
            }
        }
    }

    private fun editHistoryComment(comment: SchoolComment, text: String) {
        lifecycleScope.launch {
            try {
                schoolActivityRepository.updateComment(comment.schoolKey, comment.id, text)
                refreshActivityHistory()
                viewModel.state.value.selectedSchool
                    ?.takeIf { it.activityKey() == comment.schoolKey }
                    ?.let { refreshSchoolActivity(it, viewModel.state.value.commentSearchQuery) }
                showReminderMessage("Comentario actualizado.")
            } catch (error: IllegalArgumentException) {
                showReminderMessage(error.message ?: "No se pudo editar el comentario.")
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudo editar el comentario.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudo editar el comentario.")
            }
        }
    }

    private fun editHistoryReminder(
        reminder: SchoolReminder,
        message: String,
        scheduledAtMillis: Long,
    ) {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isEmpty()) {
            showReminderMessage("Escribe qué debes recordar.")
            return
        }
        if (scheduledAtMillis <= System.currentTimeMillis()) {
            showReminderMessage("Elige una fecha y hora futuras.")
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showNotificationSettings()
            return
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            showNotificationSettings()
            return
        }
        if (!SchoolReminderAlarms.canScheduleExactAlarms(this)) {
            showHistoryExactAlarmSettings()
            return
        }

        lifecycleScope.launch {
            var updatedReminder: SchoolReminder? = null
            try {
                SchoolReminderAlarms.cancel(this@MainActivity, reminder.id)
                updatedReminder = schoolActivityRepository.updateReminder(
                    reminderId = reminder.id,
                    message = normalizedMessage,
                    scheduledAtMillis = scheduledAtMillis,
                )
                SchoolReminderAlarms.createNotificationChannel(this@MainActivity)
                SchoolReminderAlarms.schedule(this@MainActivity, updatedReminder)
            } catch (error: IllegalArgumentException) {
                showHistoryReminderEditFailure(
                    reminder,
                    updatedReminder,
                    error.message ?: "No se pudo actualizar la alarma.",
                )
                return@launch
            } catch (error: IllegalStateException) {
                showHistoryReminderEditFailure(
                    reminder,
                    updatedReminder,
                    error.message ?: "No se pudo actualizar la alarma.",
                )
                return@launch
            } catch (error: IOException) {
                showHistoryReminderEditFailure(
                    reminder,
                    updatedReminder,
                    error.message ?: "No se pudo guardar la alarma.",
                )
                return@launch
            } catch (error: SQLiteException) {
                showHistoryReminderEditFailure(
                    reminder,
                    updatedReminder,
                    error.message ?: "No se pudo guardar la alarma.",
                )
                return@launch
            } catch (error: SecurityException) {
                showHistoryReminderEditFailure(
                    reminder,
                    updatedReminder,
                    error.message ?: "Android no permitió programar la alarma.",
                )
                return@launch
            }

            refreshActivityHistory()
            viewModel.state.value.selectedSchool
                ?.takeIf { it.activityKey() == reminder.schoolKey }
                ?.let { refreshSchoolActivity(it, viewModel.state.value.commentSearchQuery) }
            showReminderMessage("Alarma reactivada con la nueva fecha.")
        }
    }

    private suspend fun showHistoryReminderEditFailure(
        originalReminder: SchoolReminder,
        updatedReminder: SchoolReminder?,
        message: String,
    ) {
        val rollbackError = rollbackHistoryReminderEdit(originalReminder, updatedReminder)
        refreshActivityHistory()
        showReminderMessage(
            if (rollbackError == null) {
                message
            } else {
                "$message No se pudo restaurar correctamente la alarma anterior: $rollbackError"
            },
        )
    }

    private suspend fun rollbackHistoryReminderEdit(
        originalReminder: SchoolReminder,
        updatedReminder: SchoolReminder?,
    ): String? {
        var oldRecordRestored = updatedReminder == null
        var rollbackError: String? = null
        if (updatedReminder != null) {
            try {
                schoolActivityRepository.restoreReminder(originalReminder)
                oldRecordRestored = true
            } catch (error: IOException) {
                rollbackError = error.message ?: "Error de lectura o escritura."
            } catch (error: SQLiteException) {
                rollbackError = error.message ?: "Error de base de datos."
            } catch (error: IllegalStateException) {
                rollbackError = error.message ?: "El registro anterior ya no está disponible."
            }
        }
        SchoolReminderAlarms.cancel(this, originalReminder.id)
        if (
            oldRecordRestored &&
            originalReminder.status == SchoolReminder.STATUS_PENDING &&
            originalReminder.scheduledAtMillis > System.currentTimeMillis()
        ) {
            try {
                SchoolReminderAlarms.schedule(this, originalReminder)
            } catch (error: SecurityException) {
                rollbackError = error.message ?: "No se pudo reprogramar la alarma anterior."
            }
        }
        return rollbackError
    }

    private fun showHistoryExactAlarmSettings() {
        AlertDialog.Builder(this)
            .setTitle("Permitir alarmas exactas")
            .setMessage(
                "EduEscale necesita acceso a “Alarmas y recordatorios” para reactivar esta alarma.",
            )
            .setPositiveButton("Abrir configuración") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName"),
                )
                try {
                    startActivity(intent)
                } catch (error: ActivityNotFoundException) {
                    showReminderMessage("No se pudo abrir la configuración de alarmas exactas.")
                }
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun createSchoolReminder(school: SchoolRecord, message: String) {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isEmpty()) {
            showReminderMessage("Escribe qué debes recordar.")
            return
        }

        pickReminderDateTime(System.currentTimeMillis()) { scheduledAt ->
            prepareReminder(
                PendingReminder(
                    schoolKey = school.activityKey(),
                    schoolName = school.nombre?.takeIf(String::isNotBlank)
                        ?: "Colegio sin nombre",
                    message = normalizedMessage,
                    scheduledAtMillis = scheduledAt,
                ),
            )
        }
    }

    private fun pickReminderDateTime(
        initialScheduledAtMillis: Long,
        onSelected: (Long) -> Unit,
    ) {
        val now = Calendar.getInstance()
        val initial = Calendar.getInstance().apply {
            timeInMillis = maxOf(initialScheduledAtMillis, now.timeInMillis)
        }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        val scheduledAt = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, hourOfDay, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        if (scheduledAt <= System.currentTimeMillis()) {
                            showReminderMessage("Elige una fecha y hora futuras.")
                            return@TimePickerDialog
                        }
                        onSelected(scheduledAt)
                    },
                    initial.get(Calendar.HOUR_OF_DAY),
                    initial.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(this),
                ).show()
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.minDate = now.timeInMillis
        }.show()
    }

    private fun prepareReminder(reminder: PendingReminder) {
        pendingReminder = reminder
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(this)
                .setTitle("Permitir notificaciones")
                .setMessage(
                    "EduEscale necesita mostrar una notificación cuando llegue la hora del recordatorio.",
                )
                .setPositiveButton("Continuar") { _, _ ->
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .setNegativeButton("Ahora no") { _, _ -> pendingReminder = null }
                .show()
            return
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            pendingReminder = null
            showNotificationSettings()
            return
        }
        requestExactAlarmPermission(reminder)
    }

    private fun requestExactAlarmPermission(reminder: PendingReminder) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            pendingReminder = null
            showNotificationSettings()
            return
        }
        if (!SchoolReminderAlarms.canScheduleExactAlarms(this)) {
            showExactAlarmSettings()
            return
        }
        saveAndScheduleReminder(reminder)
    }

    private fun saveAndScheduleReminder(pending: PendingReminder) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            pendingReminder = null
            showNotificationSettings()
            return
        }
        lifecycleScope.launch {
            val reminder = try {
                schoolActivityRepository.addReminder(
                    schoolKey = pending.schoolKey,
                    schoolName = pending.schoolName,
                    message = pending.message,
                    scheduledAtMillis = pending.scheduledAtMillis,
                )
            } catch (error: IllegalArgumentException) {
                pendingReminder = null
                showReminderMessage(error.message ?: "No se pudo crear el recordatorio.")
                return@launch
            } catch (error: IOException) {
                pendingReminder = null
                showReminderMessage(error.message ?: "No se pudo guardar el recordatorio.")
                return@launch
            } catch (error: SQLiteException) {
                pendingReminder = null
                showReminderMessage(error.message ?: "No se pudo guardar el recordatorio.")
                return@launch
            }

            try {
                SchoolReminderAlarms.createNotificationChannel(this@MainActivity)
                SchoolReminderAlarms.schedule(this@MainActivity, reminder)
            } catch (error: SecurityException) {
                try {
                    schoolActivityRepository.deleteReminder(reminder.id)
                } catch (cleanupError: IOException) {
                    error.addSuppressed(cleanupError)
                } catch (cleanupError: SQLiteException) {
                    error.addSuppressed(cleanupError)
                }
                pendingReminder = null
                showReminderMessage(error.message ?: "Android no permitió programar la alarma.")
                return@launch
            }

            pendingReminder = null
            viewModel.state.value.selectedSchool
                ?.takeIf { it.activityKey() == pending.schoolKey }
                ?.let { refreshSchoolActivity(it, viewModel.state.value.commentSearchQuery) }
            showReminderMessage("Alarma programada.")
        }
    }

    private fun deleteSchoolReminder(school: SchoolRecord, reminderId: String) {
        lifecycleScope.launch {
            try {
                schoolActivityRepository.cancelReminder(reminderId)
                SchoolReminderAlarms.cancel(this@MainActivity, reminderId)
                viewModel.state.value.selectedSchool
                    ?.takeIf { it.activityKey() == school.activityKey() }
                    ?.let { refreshSchoolActivity(it, viewModel.state.value.commentSearchQuery) }
                showReminderMessage("Alarma cancelada y conservada en el historial.")
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudo cancelar el recordatorio.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudo cancelar el recordatorio.")
            }
        }
    }

    private fun showReminderMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun exportActivityBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val backup = schoolActivityRepository.exportBackup()
                val output = contentResolver.openOutputStream(uri)
                    ?: throw IOException("No se pudo crear el archivo de respaldo.")
                OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                    Gson().toJson(backup, writer)
                }
                showReminderMessage("Copia exportada correctamente.")
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudo exportar la copia.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudo leer el historial para exportarlo.")
            } catch (error: SecurityException) {
                showReminderMessage("Android no permitió escribir el archivo de respaldo.")
            }
        }
    }

    private fun importActivityBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val backup = contentResolver.openInputStream(uri)?.use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                        Gson().fromJson(reader, SchoolActivityBackup::class.java)
                    }
                } ?: throw IOException("No se pudo abrir el archivo de respaldo.")
                schoolActivityRepository.importBackup(backup)
                restorePendingReminders(showPermissionMessage = true)
                val state = viewModel.state.value
                state.selectedSchool?.let {
                    refreshSchoolActivity(it, state.commentSearchQuery)
                }
                showReminderMessage("Copia restaurada; los registros se combinaron con los actuales.")
            } catch (error: JsonParseException) {
                showReminderMessage("El archivo de respaldo no contiene un JSON válido.")
            } catch (error: IllegalArgumentException) {
                showReminderMessage(error.message ?: "El archivo de respaldo no es compatible.")
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudo leer el archivo de respaldo.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudo guardar la copia restaurada.")
            } catch (error: SecurityException) {
                showReminderMessage("Android no permitió leer el archivo de respaldo.")
            }
        }
    }

    private fun showNotificationSettings() {
        AlertDialog.Builder(this)
            .setTitle("Notificaciones desactivadas")
            .setMessage(
                "Activa las notificaciones de EduEscale para que las alarmas puedan avisarte.",
            )
            .setPositiveButton("Abrir configuración") { _, _ ->
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                } else {
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName"),
                    )
                }
                try {
                    startActivity(intent)
                } catch (error: ActivityNotFoundException) {
                    showReminderMessage("No se pudo abrir la configuración de notificaciones.")
                }
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun showExactAlarmSettings() {
        AlertDialog.Builder(this)
            .setTitle("Permitir alarmas exactas")
            .setMessage(
                "EduEscale necesita acceso a “Alarmas y recordatorios” para avisarte a la hora elegida, incluso si la app está cerrada.",
            )
            .setPositiveButton("Abrir configuración") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName"),
                )
                try {
                    requestExactAlarmSettings.launch(intent)
                } catch (error: ActivityNotFoundException) {
                    pendingReminder = null
                    showReminderMessage("No se pudo abrir la configuración de alarmas exactas.")
                }
            }
            .setNegativeButton("Ahora no") { _, _ -> pendingReminder = null }
            .show()
    }

    private fun showMyLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
            return
        }
        if (!isLocationServicesEnabled()) {
            openLocationSettings()
            return
        }
        enableMyLocation()
    }

    private fun isLocationServicesEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    private fun refreshLocationServices() {
        val enabled = isLocationServicesEnabled()
        viewModel.setLocationServicesEnabled(enabled)
        if (enabled) {
            if (hasLocationPermission()) {
                enableMyLocation(trackCamera = false)
            }
        } else {
            locationManager.removeUpdates(currentLocationListener)
            map?.locationComponent?.let { locationComponent ->
                if (locationComponent.isLocationComponentActivated) {
                    locationComponent.isLocationComponentEnabled = false
                }
            }
        }
    }

    private fun openLocationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No se pudo abrir la configuración de ubicación.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun enableMyLocation(trackCamera: Boolean = true) {
        if (!hasLocationPermission()) {
            return
        }
        val currentMap = map ?: return
        val currentStyle = style ?: return

        try {
            val locationComponent = currentMap.locationComponent
            if (!locationComponent.isLocationComponentActivated) {
                val locationOptions = LocationComponentOptions.builder(this)
                    .pulseEnabled(true)
                    .pulseColor(Color.rgb(24, 132, 255))
                    .foregroundTintColor(Color.rgb(12, 72, 160))
                    .backgroundTintColor(Color.WHITE)
                    .build()
                locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, currentStyle)
                        .locationComponentOptions(locationOptions)
                        .build(),
                )
            }
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.NORMAL
            updateCurrentLocation()
            if (trackCamera) {
                locationComponent.cameraMode = CameraMode.TRACKING
            }
        } catch (error: SecurityException) {
            Toast.makeText(
                this,
                "Android no permitió acceder a la ubicación.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun updateCurrentLocation() {
        if (!hasLocationPermission()) {
            return
        }
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).filter(locationManager::isProviderEnabled)
        providers.mapNotNull(locationManager::getLastKnownLocation)
            .maxByOrNull(Location::getTime)
            ?.let { location ->
                viewModel.setUserLocation(location.latitude, location.longitude)
            }
        if (providers.isNotEmpty()) {
            locationManager.removeUpdates(currentLocationListener)
            providers.forEach { provider ->
                locationManager.requestSingleUpdate(
                    provider,
                    currentLocationListener,
                    mainLooper,
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    private fun importFile(uri: Uri) {
        viewModel.setImporting(true)
        importExecutor.execute {
            try {
                val fileName = displayName(uri)
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("No se pudo abrir el archivo seleccionado.")
                val parsed = input.use(schoolFileStorage::save)
                getSharedPreferences(FILE_PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putString(LAST_FILE_NAME, fileName)
                    .apply()
                mainHandler.post {
                    if (isFinishing || isDestroyed) {
                        return@post
                    }
                    hasPendingUserFileImport = true
                    shouldFocusOnNextMapDataUpdate = true
                    viewModel.setSchoolFile(parsed, fileName)
                }
            } catch (error: IOException) {
                showImportError(error.message ?: "No se pudo leer el archivo.")
            } catch (error: IllegalArgumentException) {
                showImportError(error.message ?: "El archivo no es v\u00e1lido.")
            } catch (error: SecurityException) {
                showImportError("Android no permiti\u00f3 leer el archivo seleccionado.")
            }
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameColumn)?.takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
            ?: "Archivo EduScale"
    }

    private fun showImportError(message: String) {
        viewModel.setImportError(message)
    }

    private fun updateMapData() {
        val currentMap = map ?: return
        val currentStyle = style ?: return
        if (schoolFile == null) {
            return
        }

        currentStyle.getLayer(SCHOOL_LAYER)?.let(currentStyle::removeLayer)
        currentStyle.getLayer(SELECTED_SCHOOL_LAYER)?.let(currentStyle::removeLayer)
        currentStyle.getLayer(SELECTED_SCHOOL_ACCENT_LAYER)?.let(currentStyle::removeLayer)
        currentStyle.getSource(SCHOOL_SOURCE)?.let(currentStyle::removeSource)
        currentStyle.getSource(SELECTED_SCHOOL_SOURCE)?.let(currentStyle::removeSource)

        val locatedSchools = visibleSchools.filter(SchoolRecord::hasCoordinates)
        if (locatedSchools.isEmpty()) {
            return
        }

        val features = locatedSchools.mapNotNull { school ->
            val coordinates = school.coordinates ?: return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(coordinates.longitude, coordinates.latitude)).apply {
                addStringProperty(SCHOOL_ID_PROPERTY, school.id.toString())
                addStringProperty(MARKER_COLOR_PROPERTY, SchoolMarkerStyle.color(school.gestion))
                addNumberProperty(MARKER_RADIUS_PROPERTY, SchoolMarkerStyle.radius(school.niveles))
            }
        }
        currentStyle.addSource(
            GeoJsonSource(
                SCHOOL_SOURCE,
                FeatureCollection.fromFeatures(features),
            ),
        )
        currentStyle.addLayer(
            CircleLayer(SCHOOL_LAYER, SCHOOL_SOURCE).withProperties(
                circleColor(Expression.get(MARKER_COLOR_PROPERTY)),
                circleRadius(Expression.get(MARKER_RADIUS_PROPERTY)),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(2f),
            ),
        )
        val selectedSchool = viewModel.state.value.selectedSchool
            ?.takeIf { selected -> locatedSchools.any { it.id == selected.id } }
        val selectedCoordinates = selectedSchool?.coordinates
        if (selectedSchool != null && selectedCoordinates != null) {
            val selectedFeature = Feature.fromGeometry(
                Point.fromLngLat(
                    selectedCoordinates.longitude,
                    selectedCoordinates.latitude,
                ),
            )
            currentStyle.addSource(
                GeoJsonSource(
                    SELECTED_SCHOOL_SOURCE,
                    FeatureCollection.fromFeatures(listOf(selectedFeature)),
                ),
            )
            currentStyle.addLayer(
                CircleLayer(SELECTED_SCHOOL_LAYER, SELECTED_SCHOOL_SOURCE).withProperties(
                    circleColor(Color.TRANSPARENT),
                    circleRadius(SELECTED_MARKER_RADIUS),
                    circleStrokeColor(Color.WHITE),
                    circleStrokeWidth(SELECTED_MARKER_OUTLINE_STROKE_WIDTH),
                ),
            )
            currentStyle.addLayer(
                CircleLayer(
                    SELECTED_SCHOOL_ACCENT_LAYER,
                    SELECTED_SCHOOL_SOURCE,
                ).withProperties(
                    circleColor(Color.TRANSPARENT),
                    circleRadius(SELECTED_MARKER_RADIUS),
                    circleStrokeColor(Color.CYAN),
                    circleStrokeWidth(SELECTED_MARKER_ACCENT_STROKE_WIDTH),
                ),
            )
        }
        if (shouldFocusOnNextMapDataUpdate) {
            shouldFocusOnNextMapDataUpdate = false
            hasPendingUserFileImport = false
            focusSchools(currentMap, locatedSchools)
        }
    }

    private fun focusSchools(map: MapLibreMap, schools: List<SchoolRecord>) {
        val locatedSchools = schools.filter(SchoolRecord::hasCoordinates)
        if (locatedSchools.isEmpty()) {
            return
        }
        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
        var minLatitude = Double.POSITIVE_INFINITY
        var maxLatitude = Double.NEGATIVE_INFINITY
        var minLongitude = Double.POSITIVE_INFINITY
        var maxLongitude = Double.NEGATIVE_INFINITY
        locatedSchools.forEach { school ->
            val coordinates = school.coordinates ?: return@forEach
            val position = LatLng(coordinates.latitude, coordinates.longitude)
            bounds.include(position)
            minLatitude = minOf(minLatitude, coordinates.latitude)
            maxLatitude = maxOf(maxLatitude, coordinates.latitude)
            minLongitude = minOf(minLongitude, coordinates.longitude)
            maxLongitude = maxOf(maxLongitude, coordinates.longitude)
        }

        val samePoint = maxLatitude - minLatitude < MIN_COORDINATE_SPAN &&
            maxLongitude - minLongitude < MIN_COORDINATE_SPAN
        if (locatedSchools.size == 1 || samePoint) {
            val coordinates = locatedSchools.first().coordinates ?: return
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(coordinates.latitude, coordinates.longitude),
                    SINGLE_POINT_ZOOM,
                ),
            )
        } else {
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(56)),
            )
        }
    }

    private fun showNavigationOptions(school: SchoolRecord) {
        if (!school.hasCoordinates) {
            showMissingCoordinatesMessage()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Cómo llegar en auto")
            .setItems(arrayOf("Google Maps", "Waze")) { _, choice ->
                val coordinates = school.coordinates ?: return@setItems
                val url = when (choice) {
                    0 -> SchoolNavigationLinks.googleMapsDirections(coordinates)
                    1 -> SchoolNavigationLinks.wazeDirections(coordinates)
                    else -> return@setItems
                }
                openNavigationUrl(url)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openNavigationUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No se encontró una aplicación o navegador para abrir la ruta.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun shareSchoolLocation(school: SchoolRecord) {
        val coordinates = school.coordinates ?: run {
            showMissingCoordinatesMessage()
            return
        }
        val schoolName = school.nombre?.takeIf(String::isNotBlank) ?: "Ubicación del colegio"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, schoolName)
            putExtra(
                Intent.EXTRA_TEXT,
                "$schoolName\n${SchoolNavigationLinks.googleMapsLocation(coordinates)}",
            )
        }
        startActivity(Intent.createChooser(sendIntent, "Compartir ubicación"))
    }

    private fun showMissingCoordinatesMessage() {
        Toast.makeText(this, "Este colegio no tiene coordenadas disponibles.", Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        val locationProviderFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION).apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            this,
            locationProviderReceiver,
            locationProviderFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        locationProviderReceiverRegistered = true
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        refreshLocationServices()
        restorePendingReminders()
        refreshActivityHistory()
        val state = viewModel.state.value
        state.selectedSchool?.let { refreshSchoolActivity(it, state.commentSearchQuery) }
    }

    override fun onPause() {
        map?.let { mapCameraStore.save(it.cameraPosition) }
        locationManager.removeUpdates(currentLocationListener)
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (locationProviderReceiverRegistered) {
            unregisterReceiver(locationProviderReceiver)
            locationProviderReceiverRegistered = false
        }
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
        importExecutor.shutdownNow()
        offlineRegions.values.forEach { region -> region.setObserver(null) }
        mapView.onDestroy()
        super.onDestroy()
    }

    private inner class TouchRoutingFrameLayout : FrameLayout(this@MainActivity) {
        var mapTouchEnabled = false
        var mapTopOverlayBottomPx = 0f
        val mapDownloadControlBounds = RectF()
        private var forwardingMapTouch = false

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                forwardingMapTouch = shouldForwardMapTouch(event)
            }
            if (!forwardingMapTouch) {
                return super.dispatchTouchEvent(event)
            }

            mapView.dispatchTouchEvent(event)
            if (
                event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                forwardingMapTouch = false
            }
            return true
        }

        private fun shouldForwardMapTouch(event: MotionEvent): Boolean {
            val density = resources.displayMetrics.density
            val yDp = event.y / density
            val xDp = event.x / density
            val widthDp = width / density
            val heightDp = height / density
            val startsInMap = event.y >= mapTopOverlayBottomPx &&
                yDp <= heightDp - MAP_TOUCH_BOTTOM_EXCLUSION_DP
            val bottomInsetDp = bottomSystemInsetPx / density
            val locationButtonBottom = heightDp -
                MAP_NAVIGATION_BAR_HEIGHT_DP -
                bottomInsetDp -
                MAP_LOCATION_BUTTON_BOTTOM_MARGIN_DP
            val locationButtonTop = locationButtonBottom - MAP_LOCATION_BUTTON_SIZE_DP
            val touchesLocationButtonVertically =
                yDp >= locationButtonTop - MAP_CONTROL_TOUCH_SLOP_DP &&
                    yDp <= locationButtonBottom + MAP_CONTROL_TOUCH_SLOP_DP
            val startsOnLocationButton =
                xDp >= widthDp - MAP_LOCATION_BUTTON_RIGHT_MARGIN_DP -
                    MAP_LOCATION_BUTTON_SIZE_DP - MAP_CONTROL_TOUCH_SLOP_DP &&
                    touchesLocationButtonVertically
            val downloadControlTouchSlop = dp(MAP_CONTROL_TOUCH_SLOP_DP).toFloat()
            val startsOnDownloadControl = mapDownloadControlBounds.let { bounds ->
                !bounds.isEmpty &&
                    event.x >= bounds.left - downloadControlTouchSlop &&
                    event.x <= bounds.right + downloadControlTouchSlop &&
                    event.y >= bounds.top - downloadControlTouchSlop &&
                    event.y <= bounds.bottom + downloadControlTouchSlop
            }
            return mapTouchEnabled &&
                startsInMap &&
                !startsOnLocationButton &&
                !startsOnDownloadControl
        }
    }

    private companion object {
        const val FILE_PREFERENCES = "eduescale_file"
        const val LAST_FILE_NAME = "last_file_name"
        const val SCHOOL_SOURCE = "eduescale-schools"
        const val SCHOOL_LAYER = "eduescale-school-markers"
        const val SELECTED_SCHOOL_SOURCE = "eduescale-selected-school"
        const val SELECTED_SCHOOL_LAYER = "eduescale-selected-school-highlight"
        const val SELECTED_SCHOOL_ACCENT_LAYER = "eduescale-selected-school-accent"
        const val SCHOOL_ID_PROPERTY = "school_id"
        const val MARKER_COLOR_PROPERTY = "marker_color"
        const val MARKER_RADIUS_PROPERTY = "marker_radius"
        const val SELECTED_MARKER_RADIUS = 22f
        const val SELECTED_MARKER_OUTLINE_STROKE_WIDTH = 8f
        const val SELECTED_MARKER_ACCENT_STROKE_WIDTH = 4f
        const val MARKER_TOUCH_RADIUS_DP = 24
        const val SINGLE_POINT_ZOOM = 12.0
        const val MIN_VISIBLE_MAP_REGION_DP = 100
        const val MIN_COORDINATE_SPAN = 0.00001
        const val MAP_TOUCH_BOTTOM_EXCLUSION_DP = 104
        const val MAP_NAVIGATION_BAR_HEIGHT_DP = 80
        const val MAP_LOCATION_BUTTON_SIZE_DP = 56
        const val MAP_LOCATION_BUTTON_RIGHT_MARGIN_DP = 16
        const val MAP_LOCATION_BUTTON_BOTTOM_MARGIN_DP = 16
        const val MAP_CONTROL_TOUCH_SLOP_DP = 8
        const val COMPASS_BOTTOM_OFFSET_DP = 164
        const val OPEN_FREE_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val MAX_OFFLINE_AREA_BYTES = 50_000_000L
        const val TAG = "EduEscaleMap"
    }
}
