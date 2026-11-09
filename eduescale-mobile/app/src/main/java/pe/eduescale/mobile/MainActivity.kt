package pe.eduescale.mobile

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import pe.eduescale.mobile.activity.SchoolActivityController
import pe.eduescale.mobile.data.ActivityBackupController
import pe.eduescale.mobile.data.SchoolActivityRepository
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolFileStorage
import pe.eduescale.mobile.data.SchoolImportController
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.location.LocationController
import pe.eduescale.mobile.map.MapCameraStore
import pe.eduescale.mobile.map.MapStyle
import pe.eduescale.mobile.map.OfflineMapController
import pe.eduescale.mobile.map.SchoolMarkerStyle
import pe.eduescale.mobile.navigation.SchoolNavigationLinks
import pe.eduescale.mobile.reminders.ReminderController
import pe.eduescale.mobile.ui.EduEscaleApp
import pe.eduescale.mobile.ui.EduEscaleViewModel
import pe.eduescale.mobile.ui.OfflineMapBounds

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: EduEscaleViewModel by viewModels()
    private lateinit var mapView: MapView
    @Inject lateinit var schoolFileStorage: SchoolFileStorage
    @Inject lateinit var schoolActivityRepository: SchoolActivityRepository
    @Inject lateinit var offlineManager: OfflineManager
    private var selectedSchoolCameraFocusInProgress = false
    private var pendingSchoolSheetAlignment: PendingSchoolSheetAlignment? = null

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
                    hasPendingUserFileImport = true
                    shouldFocusOnNextMapDataUpdate = true
                }
                viewModel.setSchoolFile(file, fileName)
            },
            onImporting = { viewModel.setImporting(true) },
            onError = viewModel::setImportError,
        )
    }
    private val mapCameraStore by lazy { MapCameraStore(this) }
    private val reminderController = ReminderController(
        activity = this,
        viewModel = viewModel,
        repositoryProvider = { schoolActivityRepository },
        activityProvider = { activityController },
        showReminderMessage = ::showReminderMessage,
    )
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
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private val locationController = LocationController(this, viewModel, { map }, { style })
    private var bottomSystemInsetPx = 0
    private var schoolFile: SchoolFile? = null
    private var schoolsById: Map<Int, SchoolRecord> = emptyMap()
    private var visibleSchools: List<SchoolRecord> = emptyList()
    private var shouldFocusOnNextMapDataUpdate = true
    private var hasPendingUserFileImport = false
    private val offlineMapController by lazy {
        OfflineMapController(this, viewModel, offlineManager) { style != null }
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
                        onLocate = locationController::showMyLocation,
                        onApplyFilters = viewModel::setFilters,
                        onSchoolSelected = { school ->
                            showSchoolDetails(school)
                            focusSelectedSchoolFromList(school)
                        },
                        onDirections = ::showNavigationOptions,
                        onShare = ::shareSchoolLocation,
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
        mapLibreMap.setStyle(Style.Builder().fromUri(MapStyle.OPEN_FREE_MAP_STYLE_URL)) { loadedStyle ->
            style = loadedStyle
            updateMapData()
            updateVisibleMapBounds(mapLibreMap)
            offlineMapController.refreshOfflineMapAreas()
            locationController.refreshLocationServices()
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
        activityController.refreshSchoolActivity(school, "")
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











    private fun showReminderMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        map?.let { mapCameraStore.save(it.cameraPosition) }
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
        const val TAG = "EduEscaleMap"
    }
}
