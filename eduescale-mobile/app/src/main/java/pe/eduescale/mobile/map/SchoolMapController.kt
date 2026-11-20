package pe.eduescale.mobile.map

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.widget.FrameLayout
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.ui.map.SchoolMapPort

/** Owns the MapLibre map: camera, school markers, selection and sheet alignment. */
internal class SchoolMapController(
    private val context: Context,
    private val mapView: MapView,
    private val mapCameraStore: MapCameraStore,
    private val onStyleReady: () -> Unit,
    private val selectedSchool: () -> SchoolRecord?,
    private val onVisibleMapBoundsChanged: (OfflineMapBounds) -> Unit,
    private val onSchoolTapped: (SchoolRecord) -> Unit,
) : SchoolMapPort {
    var map: MapLibreMap? = null
        private set
    var style: Style? = null
        private set
    var bottomSystemInsetPx: Int
        get() = camera.bottomSystemInsetPx
        set(value) { camera.bottomSystemInsetPx = value }
    var schoolFile: SchoolFile? = null
    var schoolsById: Map<Int, SchoolRecord> = emptyMap()
    var visibleSchools: List<SchoolRecord> = emptyList()
    var shouldFocusOnNextMapDataUpdate = true
    var hasPendingUserFileImport = false
    private val clusterCountOverlay = ClusterCountOverlay(context)
    private val camera = SchoolMapCamera(context, mapView, { map }, selectedSchool)

    override fun onSchoolFileLoaded(file: SchoolFile?, schools: List<SchoolRecord>) {
        schoolFile = file
        visibleSchools = schools
        schoolsById = file?.schools?.associateBy(SchoolRecord::id).orEmpty()
    }

    override fun clearSelectionState() {
        camera.clearSelection()
    }

    override fun focusOnNextFile() {
        hasPendingUserFileImport = true
        shouldFocusOnNextMapDataUpdate = true
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun configureMap(mapLibreMap: MapLibreMap) {
        map = mapLibreMap
        mapView.addView(
            clusterCountOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        mapView.addOnDidFinishRenderingFrameListener(
            MapView.OnDidFinishRenderingFrameListener { _, _, _ ->
                clusterCountOverlay.updateFromMap(mapLibreMap, style, mapView)
            },
        )
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
            camera.onCameraIdle()
        }
        mapLibreMap.addOnMapClickListener { coordinate ->
            if (zoomClusterAtTap(mapLibreMap, coordinate)) {
                return@addOnMapClickListener true
            }
            val school = schoolAtTap(mapLibreMap, coordinate) ?: return@addOnMapClickListener false
            onSchoolTapped(school)
            true
        }
        mapLibreMap.setStyle(Style.Builder().fromUri(MapStyle.OPEN_FREE_MAP_STYLE_URL)) { loadedStyle ->
            style = loadedStyle
            updateMapData()
            updateVisibleMapBounds(mapLibreMap)
            onStyleReady()
        }
    }

    private fun zoomClusterAtTap(mapLibreMap: MapLibreMap, coordinate: LatLng): Boolean {
        val tapPoint = mapLibreMap.projection.toScreenLocation(coordinate)
        val cluster = mapLibreMap.queryRenderedFeatures(tapPoint, CLUSTER_LAYER).firstOrNull()
            ?: return false
        val point = cluster.geometry() as? Point ?: return false
        mapLibreMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude(), point.longitude()),
                minOf(mapLibreMap.cameraPosition.zoom + 2.0, mapLibreMap.maxZoomLevel),
            ),
            250,
        )
        return true
    }

    private fun updateVisibleMapBounds(mapLibreMap: MapLibreMap) {
        if (mapView.width == 0 || mapView.height == 0) {
            return
        }
        val bounds = mapLibreMap.projection.visibleRegion.latLngBounds
        onVisibleMapBoundsChanged(
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
    }

    fun updateCompassPosition() = camera.updateCompassPosition()

    override fun focusSelectedSchoolFromList(school: SchoolRecord) {
        camera.focusSelectedSchool(school)
    }

    fun alignSelectedSchoolWithSheet(
        school: SchoolRecord,
        sheetTopOnScreen: Float,
        mapTopOverlayBottomPx: Float,
    ) {
        camera.alignSelectedSchool(school, sheetTopOnScreen, mapTopOverlayBottomPx)
    }

    override fun updateMapData() {
        if (map == null) return
        val currentStyle = style ?: return
        if (schoolFile == null) {
            return
        }

        currentStyle.getLayer(SCHOOL_LAYER)?.let(currentStyle::removeLayer)
        currentStyle.getLayer(CLUSTER_LAYER)?.let(currentStyle::removeLayer)
        currentStyle.getLayer(SELECTED_SCHOOL_LAYER)?.let(currentStyle::removeLayer)
        currentStyle.getLayer(SELECTED_SCHOOL_ACCENT_LAYER)?.let(currentStyle::removeLayer)
        currentStyle.getSource(SCHOOL_SOURCE)?.let(currentStyle::removeSource)
        currentStyle.getSource(SELECTED_SCHOOL_SOURCE)?.let(currentStyle::removeSource)
        clusterCountOverlay.clear()

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
                GeoJsonOptions()
                    .withCluster(true)
                    .withClusterRadius(48)
                    .withClusterMaxZoom(15),
            ),
        )
        currentStyle.addLayer(
            CircleLayer(CLUSTER_LAYER, SCHOOL_SOURCE).withFilter(
                Expression.has("point_count"),
            ).withProperties(
                circleColor(
                    Expression.step(
                        Expression.get("point_count"),
                        Expression.literal("#358B67"),
                        Expression.stop(25, "#D28B25"),
                        Expression.stop(100, "#B5473B"),
                    ),
                ),
                circleRadius(
                    Expression.step(
                        Expression.get("point_count"),
                        Expression.literal(16),
                        Expression.stop(10, 19),
                        Expression.stop(100, 24),
                        Expression.stop(1000, 30),
                    ),
                ),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(2f),
            ),
        )
        currentStyle.addLayer(
            CircleLayer(SCHOOL_LAYER, SCHOOL_SOURCE).withFilter(
                Expression.not(Expression.has("point_count")),
            ).withProperties(
                circleColor(Expression.get(MARKER_COLOR_PROPERTY)),
                circleRadius(Expression.get(MARKER_RADIUS_PROPERTY)),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(2f),
            ),
        )
        val selectedSchool = selectedSchool()
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
            camera.focusSchools(locatedSchools)
        }
    }

    companion object {
        const val SCHOOL_SOURCE = "eduescale-schools"
        const val SCHOOL_LAYER = "eduescale-school-markers"
        const val CLUSTER_LAYER = "eduescale-school-clusters"
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
    }

}
