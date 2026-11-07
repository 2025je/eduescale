package pe.eduescale.mobile

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
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
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
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
import pe.eduescale.mobile.data.SchoolDataReader
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.ui.EduEscaleApp
import pe.eduescale.mobile.ui.EduEscaleViewModel
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val viewModel: EduEscaleViewModel by viewModels()
    private lateinit var mapView: MapView

    private val importExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var bottomSystemInsetPx = 0
    private var schoolFile: SchoolFile? = null
    private var schoolsById: Map<Int, SchoolRecord> = emptyMap()
    private var visibleSchools: List<SchoolRecord> = emptyList()
    private lateinit var externalFileBackCallback: OnBackPressedCallback

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importFile(uri)
        }
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) {
            enableMyLocation()
        } else {
            Toast.makeText(
                this,
                "Permite el acceso a la ubicación para mostrarla en el mapa.",
                Toast.LENGTH_LONG,
            ).show()
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
                        onPickFile = { openDocument.launch(arrayOf("*/*")) },
                        onLocate = ::showMyLocation,
                        onApplyFilters = viewModel::setFilters,
                        onSchoolSelected = { school ->
                            showSchoolDetails(school)
                            map?.let { focusSchools(it, listOf(school)) }
                        },
                        onDirections = ::showNavigationOptions,
                        onShare = ::shareSchoolLocation,
                        onClearSelection = {
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    schoolFile = state.schoolFile
                    visibleSchools = state.visibleSchools
                    schoolsById = state.schoolFile?.schools
                        ?.associateBy(SchoolRecord::id)
                        .orEmpty()
                    if (
                        observedFile !== state.schoolFile ||
                        observedVisibleSchools != state.visibleSchools
                    ) {
                        observedFile = state.schoolFile
                        observedVisibleSchools = state.visibleSchools
                        updateMapData()
                    }
                }
            }
        }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incomingIntent: Intent) {
        val uri = incomingIntent.data.takeIf {
            incomingIntent.action == Intent.ACTION_VIEW
        }
        externalFileBackCallback.isEnabled = uri != null
        uri?.let(::importFile)
    }

    private fun configureMap(mapLibreMap: MapLibreMap) {
        map = mapLibreMap
        updateCompassPosition()
        mapLibreMap.cameraPosition = CameraPosition.Builder()
            .target(LatLng(-9.19, -75.015))
            .zoom(4.0)
            .build()
        mapLibreMap.addOnMapClickListener { coordinate ->
            val school = schoolAtTap(mapLibreMap, coordinate)
            if (school == null) {
                false
            } else {
                showSchoolDetails(school)
                true
            }
        }
        mapLibreMap.setStyle(Style.Builder().fromJson(OSM_STYLE)) { loadedStyle ->
            style = loadedStyle
            updateMapData()
            if (hasLocationPermission()) {
                enableMyLocation()
            }
        }
    }

    private fun schoolAtTap(mapLibreMap: MapLibreMap, coordinate: LatLng): SchoolRecord? {
        val tapPoint = mapLibreMap.projection.toScreenLocation(coordinate)
        val hitRadius = dp(MARKER_TOUCH_RADIUS_DP).toFloat()
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
            .minByOrNull { school ->
                val markerPoint = mapLibreMap.projection.toScreenLocation(
                    LatLng(school.latitud, school.longitud),
                )
                val dx = markerPoint.x - tapPoint.x
                val dy = markerPoint.y - tapPoint.y
                dx * dx + dy * dy
            }
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

    private fun showSchoolDetails(school: SchoolRecord) {
        viewModel.selectSchool(school)
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
        enableMyLocation()
    }

    private fun enableMyLocation() {
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
            locationComponent.cameraMode = CameraMode.TRACKING
        } catch (error: SecurityException) {
            Toast.makeText(
                this,
                "Android no permitió acceder a la ubicación.",
                Toast.LENGTH_LONG,
            ).show()
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
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("No se pudo abrir el archivo seleccionado.")
                val parsed = input.use { stream -> SchoolDataReader.read(stream) }
                val fileName = displayName(uri)
                mainHandler.post {
                    if (isFinishing || isDestroyed) {
                        return@post
                    }
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
        currentStyle.getSource(SCHOOL_SOURCE)?.let(currentStyle::removeSource)

        if (visibleSchools.isEmpty()) {
            return
        }

        val features = visibleSchools.map { school ->
            Feature.fromGeometry(Point.fromLngLat(school.longitud, school.latitud)).apply {
                addStringProperty(SCHOOL_ID_PROPERTY, school.id.toString())
                addStringProperty(MARKER_COLOR_PROPERTY, markerColor(school.gestion))
                addNumberProperty(MARKER_RADIUS_PROPERTY, markerRadius(school.niveles))
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
        focusSchools(currentMap, visibleSchools)
    }

    private fun focusSchools(map: MapLibreMap, schools: List<SchoolRecord>) {
        if (schools.isEmpty()) {
            return
        }
        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
        var minLatitude = Double.POSITIVE_INFINITY
        var maxLatitude = Double.NEGATIVE_INFINITY
        var minLongitude = Double.POSITIVE_INFINITY
        var maxLongitude = Double.NEGATIVE_INFINITY
        schools.forEach { school ->
            val position = LatLng(school.latitud, school.longitud)
            bounds.include(position)
            minLatitude = minOf(minLatitude, school.latitud)
            maxLatitude = maxOf(maxLatitude, school.latitud)
            minLongitude = minOf(minLongitude, school.longitud)
            maxLongitude = maxOf(maxLongitude, school.longitud)
        }

        val samePoint = maxLatitude - minLatitude < MIN_COORDINATE_SPAN &&
            maxLongitude - minLongitude < MIN_COORDINATE_SPAN
        if (schools.size == 1 || samePoint) {
            val school = schools.first()
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(school.latitud, school.longitud),
                    SINGLE_POINT_ZOOM,
                ),
            )
        } else {
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(56)),
            )
        }
    }

    private fun markerColor(gestion: String?): String =
        when (gestion) {
            "publica" -> PUBLIC_MARKER_COLOR
            "privada" -> PRIVATE_MARKER_COLOR
            else -> UNKNOWN_MARKER_COLOR
        }

    private fun markerRadius(niveles: List<String>): Double =
        when {
            "secundaria" in niveles -> SECONDARY_MARKER_RADIUS
            "primaria" in niveles -> PRIMARY_MARKER_RADIUS
            "inicial" in niveles -> INITIAL_MARKER_RADIUS
            else -> UNKNOWN_MARKER_RADIUS
        }

    private fun showNavigationOptions(school: SchoolRecord) {
        AlertDialog.Builder(this)
            .setTitle("Cómo llegar en auto")
            .setItems(arrayOf("Google Maps", "Waze")) { _, choice ->
                val url = when (choice) {
                    0 -> googleMapsDirectionsUrl(school)
                    1 -> wazeDirectionsUrl(school)
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
        val schoolName = school.nombre?.takeIf(String::isNotBlank) ?: "Ubicación del colegio"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, schoolName)
            putExtra(
                Intent.EXTRA_TEXT,
                "$schoolName\n${googleMapsUrl(school)}",
            )
        }
        startActivity(Intent.createChooser(sendIntent, "Compartir ubicación"))
    }

    private fun googleMapsUrl(school: SchoolRecord): String {
        val coordinates = Uri.encode("${school.latitud},${school.longitud}")
        return "https://www.google.com/maps/search/?api=1&query=$coordinates"
    }

    private fun googleMapsDirectionsUrl(school: SchoolRecord): String =
        Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter(
                "destination",
                "${school.latitud},${school.longitud}",
            )
            .appendQueryParameter("travelmode", "driving")
            .build()
            .toString()

    private fun wazeDirectionsUrl(school: SchoolRecord): String =
        Uri.parse("https://waze.com/ul").buildUpon()
            .appendQueryParameter("ll", "${school.latitud},${school.longitud}")
            .appendQueryParameter("navigate", "yes")
            .build()
            .toString()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
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
        mapView.onDestroy()
        super.onDestroy()
    }

    private inner class TouchRoutingFrameLayout : FrameLayout(this@MainActivity) {
        var mapTouchEnabled = false
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
            val startsInMap = yDp >= MAP_TOUCH_TOP_EXCLUSION_DP &&
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
            return mapTouchEnabled && startsInMap && !startsOnLocationButton
        }
    }

    private companion object {
        const val SCHOOL_SOURCE = "eduescale-schools"
        const val SCHOOL_LAYER = "eduescale-school-markers"
        const val SCHOOL_ID_PROPERTY = "school_id"
        const val MARKER_COLOR_PROPERTY = "marker_color"
        const val MARKER_RADIUS_PROPERTY = "marker_radius"
        const val PUBLIC_MARKER_COLOR = "#176B50"
        const val PRIVATE_MARKER_COLOR = "#D97706"
        const val UNKNOWN_MARKER_COLOR = "#7B8794"
        const val INITIAL_MARKER_RADIUS = 6.0
        const val PRIMARY_MARKER_RADIUS = 8.0
        const val SECONDARY_MARKER_RADIUS = 10.0
        const val UNKNOWN_MARKER_RADIUS = 7.0
        const val MARKER_TOUCH_RADIUS_DP = 24
        const val SINGLE_POINT_ZOOM = 12.0
        const val MIN_COORDINATE_SPAN = 0.00001
        const val MAP_TOUCH_TOP_EXCLUSION_DP = 320
        const val MAP_TOUCH_BOTTOM_EXCLUSION_DP = 104
        const val MAP_NAVIGATION_BAR_HEIGHT_DP = 80
        const val MAP_LOCATION_BUTTON_SIZE_DP = 56
        const val MAP_LOCATION_BUTTON_RIGHT_MARGIN_DP = 16
        const val MAP_LOCATION_BUTTON_BOTTOM_MARGIN_DP = 16
        const val MAP_CONTROL_TOUCH_SLOP_DP = 8
        const val COMPASS_BOTTOM_OFFSET_DP = 164
        const val OSM_STYLE = """
            {
              "version": 8,
              "sources": {
                "openstreetmap": {
                  "type": "raster",
                  "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                  "tileSize": 256,
                  "minzoom": 0,
                  "maxzoom": 19,
                  "attribution": "© OpenStreetMap contributors"
                }
              },
              "layers": [
                {
                  "id": "openstreetmap-raster",
                  "type": "raster",
                  "source": "openstreetmap",
                  "minzoom": 0,
                  "maxzoom": 19
                }
              ]
            }
        """
    }
}
