package pe.eduescale.mobile.map

import android.util.Log
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject
import pe.eduescale.mobile.data.OfflineTileEstimator

internal data class OfflineAreaMetadata(
    val name: String,
    val minZoom: Int,
    val maxZoom: Int,
    val estimatedTiles: Int,
) {
    fun toBytes(): ByteArray =
        JSONObject()
            .put("name", name)
            .put("minZoom", minZoom)
            .put("maxZoom", maxZoom)
            .put("estimatedTiles", estimatedTiles)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

    companion object {
        val UNNAMED = OfflineAreaMetadata(
            name = "Área sin nombre",
            minZoom = 0,
            maxZoom = OfflineTileEstimator.MAX_ZOOM,
            estimatedTiles = 0,
        )

        fun fromBytes(bytes: ByteArray): OfflineAreaMetadata? =
            try {
                val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
                OfflineAreaMetadata(
                    name = json.optString("name", UNNAMED.name),
                    minZoom = json.optInt("minZoom", 0).coerceIn(0, OfflineTileEstimator.MAX_ZOOM),
                    maxZoom = json.optInt("maxZoom", OfflineTileEstimator.MAX_ZOOM)
                        .coerceIn(0, OfflineTileEstimator.MAX_ZOOM),
                    estimatedTiles = json.optInt("estimatedTiles", 0)
                        .coerceIn(0, OfflineTileEstimator.MAX_TILE_COUNT),
                )
            } catch (error: JSONException) {
                Log.w("OfflineAreaMetadata", "Offline map region metadata is invalid", error)
                null
            }
    }
}
