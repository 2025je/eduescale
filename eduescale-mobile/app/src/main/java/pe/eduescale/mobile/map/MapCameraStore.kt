package pe.eduescale.mobile.map

import android.content.Context
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

internal class MapCameraStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): CameraPosition? {
        val latitude = preferences.getString(LATITUDE, null)?.toDoubleOrNull()
            ?: return null
        val longitude = preferences.getString(LONGITUDE, null)?.toDoubleOrNull()
            ?: return null
        val zoom = preferences.getString(ZOOM, null)?.toDoubleOrNull()
            ?: return null
        val bearing = preferences.getString(BEARING, null)?.toDoubleOrNull()
            ?: return null
        val tilt = preferences.getString(TILT, null)?.toDoubleOrNull()
            ?: return null
        if (
            !latitude.isFinite() ||
            latitude !in -90.0..90.0 ||
            !longitude.isFinite() ||
            longitude !in -180.0..180.0 ||
            !zoom.isFinite() ||
            !bearing.isFinite() ||
            !tilt.isFinite()
        ) {
            return null
        }
        return CameraPosition.Builder()
            .target(LatLng(latitude, longitude))
            .zoom(zoom)
            .bearing(bearing)
            .tilt(tilt)
            .build()
    }

    fun save(position: CameraPosition) {
        val target = position.target ?: return
        preferences
            .edit()
            .putString(LATITUDE, target.latitude.toString())
            .putString(LONGITUDE, target.longitude.toString())
            .putString(ZOOM, position.zoom.toString())
            .putString(BEARING, position.bearing.toString())
            .putString(TILT, position.tilt.toString())
            .apply()
    }

    private companion object {
        const val PREFERENCES = "map_camera"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val ZOOM = "zoom"
        const val BEARING = "bearing"
        const val TILT = "tilt"
    }
}
