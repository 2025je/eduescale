package pe.eduescale.mobile.routing

import pe.eduescale.mobile.data.SchoolCoordinates

interface RoadRoutingPort {
    val supportsRoadRouting: Boolean
    fun isAvailable(): Boolean
    fun storedBytes(): Long
    suspend fun download(progress: (Long, Long) -> Unit)
    suspend fun delete()
    suspend fun distances(origin: SchoolCoordinates, targets: List<SchoolCoordinates>): List<Double?>
    suspend fun route(origin: SchoolCoordinates, destination: SchoolCoordinates): RoadRoute
}

data class RoadRoute(
    val origin: SchoolCoordinates,
    val destination: SchoolCoordinates,
    val points: List<SchoolCoordinates>,
    val distanceMeters: Double,
    val durationSeconds: Double,
)
