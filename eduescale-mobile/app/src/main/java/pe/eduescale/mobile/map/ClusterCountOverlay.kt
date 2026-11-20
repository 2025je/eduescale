package pe.eduescale.mobile.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Point

internal class ClusterCountOverlay(context: Context) : View(context) {
    private data class Label(val text: String, val x: Float, val y: Float)

    private var labels = emptyList<Label>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * resources.displayMetrics.density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        strokeWidth = 2f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
    }

    fun clear() = update(emptyList())

    fun updateFromMap(map: MapLibreMap, style: Style?, mapView: MapView) {
        if (style?.getLayer(SchoolMapController.CLUSTER_LAYER) == null ||
            mapView.width == 0 || mapView.height == 0
        ) {
            clear()
            return
        }
        val bounds = RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat())
        val labels = map.queryRenderedFeatures(bounds, SchoolMapController.CLUSTER_LAYER)
            .distinctBy { it.getNumberProperty("cluster_id") }
            .mapNotNull { feature ->
                val point = feature.geometry() as? Point ?: return@mapNotNull null
                val text = feature.getStringProperty("point_count_abbreviated") ?: return@mapNotNull null
                val position = map.projection.toScreenLocation(LatLng(point.latitude(), point.longitude()))
                Label(text, position.x, position.y)
            }
        update(labels)
    }

    private fun update(value: List<Label>) {
        if (labels != value) {
            labels = value
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val offset = (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        labels.forEach { label ->
            paint.color = Color.parseColor("#176B50")
            paint.style = Paint.Style.STROKE
            canvas.drawText(label.text, label.x, label.y - offset, paint)
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawText(label.text, label.x, label.y - offset, paint)
        }
    }
}
