package pe.eduescale.mobile.map

import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

internal object MapStyle {
    const val OPEN_FREE_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
}

class MapLibreOfflineMapStore @Inject constructor(
    private val manager: OfflineManager,
) : OfflineMapStore {
    private val handler = Handler(Looper.getMainLooper())
    private val regions = mutableMapOf<Long, OfflineRegion>()
    private var onStatus: (Long, OfflineMapStatus) -> Unit = { _, _ -> }
    private var errorHandler: (String) -> Unit = {}
    private var closed = false
    private var listGeneration = 0

    override fun listen(onStatus: (Long, OfflineMapStatus) -> Unit, onError: (String) -> Unit) {
        this.onStatus = onStatus
        this.errorHandler = onError
    }

    override fun list(callback: (Result<List<OfflineMapRegion>>) -> Unit) {
        val generation = ++listGeneration
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                post {
                    if (generation != listGeneration) return@post
                    if (offlineRegions == null) {
                        callback(Result.failure(IllegalStateException("MapLibre no devolvió la lista de mapas sin conexión.")))
                        return@post
                    }
                    this@MapLibreOfflineMapStore.regions.values.forEach { it.setObserver(null) }
                    this@MapLibreOfflineMapStore.regions.clear()
                    val mapped = offlineRegions.map { region ->
                        this@MapLibreOfflineMapStore.regions[region.id] = region
                        val metadata = OfflineAreaMetadata.fromBytes(region.metadata) ?: run {
                            errorHandler("Una región sin conexión tiene metadatos no válidos.")
                            OfflineAreaMetadata.UNNAMED
                        }
                        OfflineMapRegion(region.id, metadata.name, metadata.minZoom, metadata.maxZoom, metadata.estimatedTiles)
                    }
                    callback(Result.success(mapped))
                    offlineRegions.forEach { observe(it); refreshStatus(it) }
                }
            }

            override fun onError(error: String) = post {
                if (generation == listGeneration) callback(Result.failure(IllegalStateException(error)))
            }
        })
    }

    override fun create(bounds: OfflineMapBounds, region: OfflineMapRegion, callback: (Result<OfflineMapRegion>) -> Unit) {
        val metadata = OfflineAreaMetadata(region.name, region.minZoom, region.maxZoom, region.estimatedTiles)
        try {
            val regionBounds = LatLngBounds.Builder()
                .include(LatLng(bounds.south, bounds.west))
                .include(LatLng(bounds.north, bounds.east))
                .build()
            val definition = OfflineTilePyramidRegionDefinition(
                MapStyle.OPEN_FREE_MAP_STYLE_URL, regionBounds,
                region.minZoom.toDouble(), region.maxZoom.toDouble(), 1f,
            )
            manager.createOfflineRegion(definition, metadata.toBytes(), object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) = post {
                    listGeneration++
                    regions[offlineRegion.id] = offlineRegion
                    observe(offlineRegion)
                    callback(Result.success(region.copy(id = offlineRegion.id)))
                }
                override fun onError(error: String) = post {
                    callback(Result.failure(IllegalStateException(error)))
                }
            })
        } catch (error: IllegalArgumentException) {
            callback(Result.failure(error))
        } catch (error: SecurityException) {
            callback(Result.failure(error))
        }
    }

    override fun setActive(id: Long, active: Boolean, callback: (Result<Unit>) -> Unit) {
        val region = regions[id]
        if (region == null) {
            callback(Result.failure(IllegalStateException("No se encontró el área guardada. Actualiza la lista.")))
            return
        }
        try {
            region.setDownloadState(if (active) OfflineRegion.STATE_ACTIVE else OfflineRegion.STATE_INACTIVE)
            callback(Result.success(Unit))
            refreshStatus(region)
        } catch (error: IllegalStateException) {
            callback(Result.failure(error))
        } catch (error: SecurityException) {
            callback(Result.failure(error))
        }
    }

    override fun delete(id: Long, callback: (Result<Unit>) -> Unit) {
        val region = regions[id]
        if (region == null) {
            callback(Result.failure(IllegalStateException("No se encontró el área que quieres eliminar.")))
            return
        }
        try {
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() = post {
                    listGeneration++
                    regions.remove(id)
                    callback(Result.success(Unit))
                }
                override fun onError(error: String) = post {
                    callback(Result.failure(IllegalStateException(error)))
                }
            })
        } catch (error: IllegalStateException) {
            callback(Result.failure(error))
        } catch (error: SecurityException) {
            callback(Result.failure(error))
        }
    }

    private fun observe(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) = post { publish(region, status) }
            override fun onError(error: OfflineRegionError) = post { errorHandler("Error al descargar el mapa: ${error.message}") }
            override fun mapboxTileCountLimitExceeded(limit: Long) = post {
                errorHandler("Se alcanzó el límite local de mapas sin conexión ($limit teselas).")
            }
        })
    }

    private fun refreshStatus(region: OfflineRegion) {
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) = post {
                if (status == null) errorHandler("No se pudo consultar el progreso del mapa.")
                else publish(region, status)
            }
            override fun onError(error: String?) = post {
                errorHandler("No se pudo consultar el mapa sin conexión: ${error ?: "error desconocido"}")
            }
        })
    }

    private fun publish(region: OfflineRegion, status: OfflineRegionStatus) {
        if (regions[region.id] !== region) return
        onStatus(region.id, OfflineMapStatus(
            status.completedTileCount, status.completedResourceSize, status.completedResourceCount,
            status.requiredResourceCount, status.isRequiredResourceCountPrecise,
            status.downloadState == OfflineRegion.STATE_ACTIVE,
        ))
    }

    private fun post(action: () -> Unit) {
        handler.post { if (!closed) action() }
    }

    override fun close() {
        closed = true
        regions.values.forEach { it.setObserver(null) }
        regions.clear()
    }
}
