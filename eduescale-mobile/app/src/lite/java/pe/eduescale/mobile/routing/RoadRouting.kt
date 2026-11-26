package pe.eduescale.mobile.routing

import java.io.IOException
import javax.inject.Inject
import pe.eduescale.mobile.data.SchoolCoordinates

class RoadRouting @Inject constructor() : RoadRoutingPort {
    override val supportsRoadRouting = false

    override fun isAvailable() = false

    override fun storedBytes() = 0L

    override suspend fun download(progress: (Long, Long) -> Unit) {
        throw IOException("Las rutas por carretera solo están disponibles en EduEscale Completa.")
    }

    override suspend fun delete() = Unit

    override suspend fun distances(
        origin: SchoolCoordinates,
        targets: List<SchoolCoordinates>,
    ): List<Double?> = throw IOException("Las rutas por carretera solo están disponibles en EduEscale Completa.")

    override suspend fun route(
        origin: SchoolCoordinates,
        destination: SchoolCoordinates,
    ): RoadRoute = throw IOException("Las rutas por carretera solo están disponibles en EduEscale Completa.")
}
