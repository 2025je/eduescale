package pe.eduescale.mobile.map

import android.content.Context
import android.graphics.PointF
import android.view.Gravity
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import pe.eduescale.mobile.data.SchoolRecord

internal class SchoolMapCamera(
    private val context: Context,
    private val mapView: MapView,
    private val currentMap: () -> MapLibreMap?,
    private val selectedSchool: () -> SchoolRecord?,
) {
    var bottomSystemInsetPx = 0
    private var selectedSchoolCameraFocusInProgress = false
    private var pendingAlignment: PendingAlignment? = null

    private data class PendingAlignment(
        val school: SchoolRecord,
        val sheetTopOnScreen: Float,
        val mapTopOverlayBottomPx: Float,
    )

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun clearSelection() {
        selectedSchoolCameraFocusInProgress = false
        pendingAlignment = null
    }

    fun onCameraIdle() {
        if (selectedSchoolCameraFocusInProgress) {
            selectedSchoolCameraFocusInProgress = false
            alignPending()
        }
    }

    fun updateCompassPosition() {
        currentMap()?.uiSettings?.apply {
            setCompassGravity(Gravity.BOTTOM or Gravity.END)
            setCompassMargins(0, 0, dp(16), dp(COMPASS_BOTTOM_OFFSET_DP) + bottomSystemInsetPx)
        }
    }

    fun focusSelectedSchool(school: SchoolRecord) {
        val map = currentMap() ?: return
        val coordinates = school.coordinates ?: return
        selectedSchoolCameraFocusInProgress = true
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(coordinates.latitude, coordinates.longitude),
                maxOf(map.cameraPosition.zoom, SELECTED_SCHOOL_ZOOM),
            ),
        )
    }

    fun alignSelectedSchool(school: SchoolRecord, sheetTopOnScreen: Float, mapTopOverlayBottomPx: Float) {
        pendingAlignment = PendingAlignment(school, sheetTopOnScreen, mapTopOverlayBottomPx)
        alignPending()
    }

    private fun alignPending() {
        if (selectedSchoolCameraFocusInProgress) return
        val pending = pendingAlignment ?: return
        val map = currentMap() ?: return
        val coordinates = pending.school.coordinates
        if (coordinates == null || selectedSchool()?.id != pending.school.id ||
            mapView.width == 0 || mapView.height == 0
        ) {
            pendingAlignment = null
            return
        }
        val mapScreenLocation = IntArray(2)
        mapView.getLocationOnScreen(mapScreenLocation)
        val availableMapTop = pending.mapTopOverlayBottomPx
        val availableMapHeight = pending.sheetTopOnScreen - mapScreenLocation[1] - availableMapTop
        if (availableMapHeight <= dp(MIN_VISIBLE_MAP_REGION_DP)) {
            pendingAlignment = null
            return
        }
        val selectedPoint = map.projection.toScreenLocation(
            LatLng(coordinates.latitude, coordinates.longitude),
        )
        val visibleCenter = PointF(mapView.width / 2f, availableMapTop + availableMapHeight / 2f)
        val targetPoint = PointF(
            (mapView.width / 2f + selectedPoint.x - visibleCenter.x).coerceIn(0f, mapView.width.toFloat()),
            (mapView.height / 2f + selectedPoint.y - visibleCenter.y).coerceIn(0f, mapView.height.toFloat()),
        )
        val target = map.projection.fromScreenLocation(targetPoint)
        pendingAlignment = null
        map.animateCamera(CameraUpdateFactory.newLatLng(target))
    }

    fun focusSchools(schools: List<SchoolRecord>) {
        val map = currentMap() ?: return
        val located = schools.filter(SchoolRecord::hasCoordinates)
        if (located.isEmpty()) return
        val bounds = LatLngBounds.Builder()
        var minLatitude = Double.POSITIVE_INFINITY
        var maxLatitude = Double.NEGATIVE_INFINITY
        var minLongitude = Double.POSITIVE_INFINITY
        var maxLongitude = Double.NEGATIVE_INFINITY
        located.forEach { school ->
            val coordinates = school.coordinates ?: return@forEach
            bounds.include(LatLng(coordinates.latitude, coordinates.longitude))
            minLatitude = minOf(minLatitude, coordinates.latitude)
            maxLatitude = maxOf(maxLatitude, coordinates.latitude)
            minLongitude = minOf(minLongitude, coordinates.longitude)
            maxLongitude = maxOf(maxLongitude, coordinates.longitude)
        }
        val samePoint = maxLatitude - minLatitude < MIN_COORDINATE_SPAN &&
            maxLongitude - minLongitude < MIN_COORDINATE_SPAN
        if (located.size == 1 || samePoint) {
            val coordinates = located.first().coordinates ?: return
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(coordinates.latitude, coordinates.longitude), SINGLE_POINT_ZOOM),
            )
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(56)))
        }

    }

    private companion object {
        const val COMPASS_BOTTOM_OFFSET_DP = 164
        const val SELECTED_SCHOOL_ZOOM = 16.0
        const val MIN_VISIBLE_MAP_REGION_DP = 100
        const val MIN_COORDINATE_SPAN = 0.00001
        const val SINGLE_POINT_ZOOM = 12.0
    }
}
