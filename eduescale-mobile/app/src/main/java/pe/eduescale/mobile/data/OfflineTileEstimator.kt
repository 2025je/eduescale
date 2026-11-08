package pe.eduescale.mobile.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

object OfflineTileEstimator {
    const val MAX_TILE_COUNT = 3_000
    const val MAX_ZOOM = 14
    private const val VECTOR_SOURCE_MAX_ZOOM = 14
    private const val SHADED_RELIEF_SOURCE_MAX_ZOOM = 6
    private const val MAX_MERCATOR_LATITUDE = 85.05112878

    fun estimate(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        minZoom: Int,
        maxZoom: Int,
    ): Long {
        require(west in -180.0..180.0 && east in -180.0..180.0)
        require(south in -90.0..90.0 && north in -90.0..90.0 && south <= north)
        require(minZoom >= 0 && maxZoom in minZoom..MAX_ZOOM)

        return countTiles(
            west,
            south,
            east,
            north,
            minZoom,
            minOf(maxZoom, VECTOR_SOURCE_MAX_ZOOM),
        ) + countTiles(
            west,
            south,
            east,
            north,
            minZoom,
            minOf(maxZoom, SHADED_RELIEF_SOURCE_MAX_ZOOM),
        )
    }

    private fun countTiles(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        minZoom: Int,
        maxZoom: Int,
    ): Long {
        if (minZoom > maxZoom) {
            return 0
        }
        return (minZoom..maxZoom).sumOf { zoom ->
            val tileCount = 1L shl zoom
            val westX = tileX(west, tileCount)
            val eastX = tileX(east, tileCount)
            val northY = tileY(north, tileCount)
            val southY = tileY(south, tileCount)
            val horizontalCount = if (eastX >= westX) {
                eastX - westX + 1
            } else {
                tileCount - westX + eastX + 1
            }
            val verticalCount = southY - northY + 1
            horizontalCount * verticalCount
        }
    }

    private fun tileX(longitude: Double, tileCount: Long): Long =
        floor(((longitude + 180.0) / 360.0) * tileCount)
            .toLong()
            .coerceIn(0, tileCount - 1)

    private fun tileY(latitude: Double, tileCount: Long): Long {
        val radians = Math.toRadians(latitude.coerceIn(
            -MAX_MERCATOR_LATITUDE,
            MAX_MERCATOR_LATITUDE,
        ))
        val mercatorY = (1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0
        return floor(mercatorY * tileCount)
            .toLong()
            .coerceIn(0, tileCount - 1)
    }
}
