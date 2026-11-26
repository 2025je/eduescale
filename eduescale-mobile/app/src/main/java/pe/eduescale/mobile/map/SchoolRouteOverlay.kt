package pe.eduescale.mobile.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pe.eduescale.mobile.data.SchoolCoordinates
import pe.eduescale.mobile.routing.RoadRoute

internal class SchoolRouteOverlay {
    var route: RoadRoute? = null
        private set

    fun setRoute(style: Style?, route: RoadRoute?) {
        if (this.route == route) return
        this.route = route
        if (style != null) render(style)
    }

    fun render(style: Style) {
        listOf(ACCESS_LAYER, ROUTE_LAYER, ROUTE_OUTLINE_LAYER).forEach { id ->
            style.getLayer(id)?.let(style::removeLayer)
        }
        style.getSource(SOURCE)?.let(style::removeSource)
        val current = route ?: return
        fun SchoolCoordinates.point(): Point = Point.fromLngLat(longitude, latitude)
        fun feature(points: List<SchoolCoordinates>, kind: String): Feature =
            Feature.fromGeometry(LineString.fromLngLats(points.map { it.point() })).apply {
                addStringProperty("kind", kind)
            }
        val features = listOf(
            feature(current.points, "route"),
            feature(listOf(current.origin, current.points.first()), "access"),
            feature(listOf(current.points.last(), current.destination), "access"),
        )
        style.addSource(GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(features)))
        fun addLayer(layer: LineLayer) {
            if (style.getLayer(SchoolMapController.CLUSTER_LAYER) != null) {
                style.addLayerBelow(layer, SchoolMapController.CLUSTER_LAYER)
            } else {
                style.addLayer(layer)
            }
        }
        addLayer(LineLayer(ROUTE_OUTLINE_LAYER, SOURCE)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("route")))
            .withProperties(lineColor("#FFFFFF"), lineWidth(8f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        addLayer(LineLayer(ROUTE_LAYER, SOURCE)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("route")))
            .withProperties(lineColor("#1766D1"), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        addLayer(LineLayer(ACCESS_LAYER, SOURCE)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("access")))
            .withProperties(lineColor("#1766D1"), lineWidth(4f), lineDasharray(arrayOf(1f, 2f))))
    }

    private companion object {
        const val SOURCE = "eduescale-road-route"
        const val ROUTE_OUTLINE_LAYER = "eduescale-road-route-outline"
        const val ROUTE_LAYER = "eduescale-road-route-line"
        const val ACCESS_LAYER = "eduescale-road-route-access"
    }
}
