package pe.eduescale.mobile.map

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.view.Gravity
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
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
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.location.LocationController
import pe.eduescale.mobile.ui.EduEscaleViewModel
import pe.eduescale.mobile.ui.OfflineMapBounds

/** Owns the MapLibre map: camera, school markers, selection and sheet alignment. */
internal class SchoolMapController(
    private val context: Context,
    private val mapView: MapView,
    private val viewModel: EduEscaleViewModel,
    private val mapCameraStore: MapCameraStore,
    private val offlineMapController: () -> OfflineMapController,
    private val locationController: () -> LocationController,
    private val onSchoolTapped: (SchoolRecord) -> Unit,
) {
    var map: MapLibreMap? = null
        private set
    var style: Style? = null
        private set
    var bottomSystemInsetPx = 0
    var schoolFile: SchoolFile? = null
    var schoolsById: Map<Int, SchoolRecord> = emptyMap()
    var visibleSchools: List<SchoolRecord> = emptyList()
    var shouldFocusOnNextMapDataUpdate = true
    var hasPendingUserFileImport = false
    var selectedSchoolCameraFocusInProgress = false
    var pendingSchoolSheetAlignment: PendingSchoolSheetAlignment? = null

    fun onSchoolFileLoaded(file: SchoolFile?, schools: List<SchoolRecord>) {
        schoolFile = file
        visibleSchools = schools
        schoolsById = file?.schools?.associateBy(SchoolRecord::id).orEmpty()
    }

    fun clearSelectionState() {
        selectedSchoolCameraFocusInProgress = false
        pendingSchoolSheetAlignment = null
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun configureMap(mapLibreMap: MapLibreMap) {
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
                onSchoolTapped(school)
                true
            }
        }
        mapLibreMap.setStyle(Style.Builder().fromUri(MapStyle.OPEN_FREE_MAP_STYLE_URL)) { loadedStyle ->
            style = loadedStyle
            updateMapData()
            updateVisibleMapBounds(mapLibreMap)
            offlineMapController().refreshOfflineMapAreas()
            locationController().refreshLocationServices()
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

    fun updateCompassPosition() {
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

    data class PendingSchoolSheetAlignment(
        val school: SchoolRecord,
        val sheetTopOnScreen: Float,
        val mapTopOverlayBottomPx: Float,
    )

    fun focusSelectedSchoolFromList(school: SchoolRecord) {
        val currentMap = map ?: return
        if (!school.hasCoordinates) {
            return
        }
        selectedSchoolCameraFocusInProgress = true
        focusSchools(currentMap, listOf(school))
    }

    fun alignSelectedSchoolWithSheet(
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

    fun updateMapData() {
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

    companion object {
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
        const val COMPASS_BOTTOM_OFFSET_DP = 164
    }
}
