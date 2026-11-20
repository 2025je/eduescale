package pe.eduescale.mobile.map

data class OfflineMapBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val cameraZoom: Double,
)

data class OfflineMapRegion(
    val id: Long,
    val name: String,
    val minZoom: Int,
    val maxZoom: Int,
    val estimatedTiles: Int,
)

data class OfflineMapStatus(
    val completedTiles: Long,
    val completedBytes: Long,
    val completedResources: Long,
    val requiredResources: Long,
    val requiredCountPrecise: Boolean,
    val active: Boolean,
)

interface OfflineMapStore {
    fun listen(onStatus: (Long, OfflineMapStatus) -> Unit, onError: (String) -> Unit)
    fun list(callback: (Result<List<OfflineMapRegion>>) -> Unit)
    fun create(bounds: OfflineMapBounds, region: OfflineMapRegion, callback: (Result<OfflineMapRegion>) -> Unit)
    fun setActive(id: Long, active: Boolean, callback: (Result<Unit>) -> Unit)
    fun delete(id: Long, callback: (Result<Unit>) -> Unit)
    fun close()
}
