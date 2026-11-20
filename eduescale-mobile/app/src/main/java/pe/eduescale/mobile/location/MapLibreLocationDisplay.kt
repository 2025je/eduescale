package pe.eduescale.mobile.location

import android.content.Context
import android.graphics.Color
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

internal interface LocationDisplay {
    fun enable(): Boolean
    fun trackCamera()
    fun disable()
}

internal class MapLibreLocationDisplay(
    private val context: Context,
    private val currentMap: () -> MapLibreMap?,
    private val currentStyle: () -> Style?,
) : LocationDisplay {
    override fun enable(): Boolean {
        val map = currentMap() ?: return false
        val style = currentStyle() ?: return false
        val component = map.locationComponent
        if (!component.isLocationComponentActivated) {
            val options = LocationComponentOptions.builder(context)
                .pulseEnabled(true)
                .pulseColor(Color.rgb(24, 132, 255))
                .foregroundTintColor(Color.rgb(12, 72, 160))
                .backgroundTintColor(Color.WHITE)
                .build()
            component.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .locationComponentOptions(options)
                    .build(),
            )
        }
        component.isLocationComponentEnabled = true
        component.renderMode = RenderMode.NORMAL
        return true
    }

    override fun trackCamera() {
        currentMap()?.locationComponent?.cameraMode = CameraMode.TRACKING
    }

    override fun disable() {
        currentMap()?.locationComponent?.let { component ->
            if (component.isLocationComponentActivated) component.isLocationComponentEnabled = false
        }
    }
}
