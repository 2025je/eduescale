package pe.eduescale.mobile.map

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import pe.eduescale.mobile.ui.EduEscaleViewModel
import pe.eduescale.mobile.ui.OfflineMapAreaUi
import pe.eduescale.mobile.ui.OfflineMapBounds

internal object MapStyle {
    const val OPEN_FREE_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
}

internal class OfflineMapController(
    private val activity: ComponentActivity,
    private val viewModel: EduEscaleViewModel,
    private val offlineManager: OfflineManager,
    private val isStyleReady: () -> Boolean,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val offlineRegions = mutableMapOf<Long, OfflineRegion>()
    private val offlineAreaMetadata = mutableMapOf<Long, OfflineAreaMetadata>()
    private val invalidOfflineRegions = mutableSetOf<Long>()

    fun release() {
        offlineRegions.values.forEach { region -> region.setObserver(null) }
    }

    fun refreshOfflineMapAreas() {
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                if (regions == null) {
                    showOfflineMapMessage("MapLibre no devolvió la lista de mapas sin conexión.")
                    return
                }
                mainHandler.post {
                    if (activity.isDestroyed) {
                        return@post
                    }
                    offlineRegions.clear()
                    offlineAreaMetadata.clear()
                    invalidOfflineRegions.clear()
                    val areas = regions.map { region ->
                        offlineRegions[region.id] = region
                        val metadata = OfflineAreaMetadata.fromBytes(region.metadata) ?: run {
                            showOfflineMapMessage("Una región sin conexión tiene metadatos no válidos.")
                            OfflineAreaMetadata.UNNAMED
                        }
                        offlineAreaMetadata[region.id] = metadata
                        observeOfflineRegion(region, metadata)
                        OfflineMapAreaUi(
                            id = region.id,
                            name = metadata.name,
                            minZoom = metadata.minZoom,
                            maxZoom = metadata.maxZoom,
                            estimatedTiles = metadata.estimatedTiles,
                            completedTiles = 0,
                            completedBytes = 0,
                            isDownloading = false,
                            isComplete = false,
                        )
                    }
                    viewModel.setOfflineMapAreas(areas)
                    regions.forEach(::refreshOfflineMapAreaStatus)
                }
            }

            override fun onError(error: String) {
                showOfflineMapMessage("No se pudieron cargar los mapas sin conexión: $error")
            }
        })
    }

    private fun observeOfflineRegion(
        region: OfflineRegion,
        metadata: OfflineAreaMetadata,
    ) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                mainHandler.post {
                    if (!activity.isDestroyed) {
                        handleOfflineMapStatus(region, metadata, status)
                    }
                }
            }

            override fun onError(error: OfflineRegionError) {
                showOfflineMapMessage(
                    "Error al descargar ${metadata.name}: ${error.message}",
                )
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                showOfflineMapMessage(
                    "Se alcanzó el límite local de mapas sin conexión ($limit teselas).",
                )
            }
        })
    }

    private fun refreshOfflineMapAreaStatus(region: OfflineRegion) {
        val metadata = offlineAreaMetadata[region.id] ?: return
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status == null) {
                    showOfflineMapMessage("No se pudo consultar el progreso del mapa.")
                    return
                }
                mainHandler.post {
                    if (!activity.isDestroyed) {
                        handleOfflineMapStatus(region, metadata, status)
                    }
                }
            }

            override fun onError(error: String?) {
                showOfflineMapMessage(
                    "No se pudo consultar el mapa sin conexión: ${error ?: "error desconocido"}",
                )
            }
        })
    }

    private fun handleOfflineMapStatus(
        region: OfflineRegion,
        metadata: OfflineAreaMetadata,
        status: OfflineRegionStatus,
    ) {
        if (
            status.completedTileCount > OfflineTileEstimator.MAX_TILE_COUNT ||
            status.completedResourceSize > MAX_OFFLINE_AREA_BYTES
        ) {
            rejectOfflineMapArea(region, metadata)
            return
        }
        val complete = status.isRequiredResourceCountPrecise &&
            status.requiredResourceCount > 0 &&
            status.completedResourceCount >= status.requiredResourceCount
        viewModel.updateOfflineMapArea(
            OfflineMapAreaUi(
                id = region.id,
                name = metadata.name,
                minZoom = metadata.minZoom,
                maxZoom = metadata.maxZoom,
                estimatedTiles = metadata.estimatedTiles,
                completedTiles = status.completedTileCount,
                completedBytes = status.completedResourceSize,
                isDownloading = status.downloadState == OfflineRegion.STATE_ACTIVE && !complete,
                isComplete = complete,
            ),
        )
    }

    fun downloadVisibleMapArea(
        bounds: OfflineMapBounds,
        minZoom: Int,
        maxZoom: Int,
    ) {
        if (!isStyleReady()) {
            showOfflineMapMessage("El mapa todavía se está preparando. Inténtalo de nuevo.")
            return
        }
        if (viewModel.state.value.offlineMapAreas.any(OfflineMapAreaUi::isDownloading)) {
            showOfflineMapMessage("Espera a que termine o pausa la descarga actual.")
            return
        }
        val estimatedTiles = try {
            OfflineTileEstimator.estimate(
                west = bounds.west,
                south = bounds.south,
                east = bounds.east,
                north = bounds.north,
                minZoom = minZoom,
                maxZoom = maxZoom,
            )
        } catch (error: IllegalArgumentException) {
            showOfflineMapMessage("El área o el rango de zoom seleccionado no es válido.")
            return
        }
        if (estimatedTiles > OfflineTileEstimator.MAX_TILE_COUNT) {
            showOfflineMapMessage("Reduce el área o el zoom: el máximo es 3.000 teselas.")
            return
        }

        val name = "Área ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}"
        val metadata = OfflineAreaMetadata(
            name = name,
            minZoom = minZoom,
            maxZoom = maxZoom,
            estimatedTiles = estimatedTiles.toInt(),
        )
        val regionBounds = LatLngBounds.Builder()
            .include(LatLng(bounds.south, bounds.west))
            .include(LatLng(bounds.north, bounds.east))
            .build()
        val definition = OfflineTilePyramidRegionDefinition(
            MapStyle.OPEN_FREE_MAP_STYLE_URL,
            regionBounds,
            minZoom.toDouble(),
            maxZoom.toDouble(),
            1f,
        )
        viewModel.setCreatingOfflineMapArea(true)
        viewModel.setOfflineMapMessage("Preparando la descarga del área…")
        try {
            offlineManager.createOfflineRegion(
                definition,
                metadata.toBytes(),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(region: OfflineRegion) {
                        mainHandler.post {
                            if (activity.isDestroyed) {
                                return@post
                            }
                            offlineRegions[region.id] = region
                            offlineAreaMetadata[region.id] = metadata
                            viewModel.setCreatingOfflineMapArea(false)
                            val area = OfflineMapAreaUi(
                                id = region.id,
                                name = metadata.name,
                                minZoom = metadata.minZoom,
                                maxZoom = metadata.maxZoom,
                                estimatedTiles = metadata.estimatedTiles,
                                completedTiles = 0,
                                completedBytes = 0,
                                isDownloading = true,
                                isComplete = false,
                            )
                            viewModel.updateOfflineMapArea(area)
                            observeOfflineRegion(region, metadata)
                            try {
                                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                                refreshOfflineMapAreaStatus(region)
                                viewModel.setOfflineMapMessage("Descarga iniciada: $name")
                            } catch (error: IllegalStateException) {
                                viewModel.updateOfflineMapArea(area.copy(isDownloading = false))
                                showOfflineMapMessage(
                                    error.message ?: "No se pudo iniciar la descarga.",
                                )
                            } catch (error: SecurityException) {
                                viewModel.updateOfflineMapArea(area.copy(isDownloading = false))
                                showOfflineMapMessage(
                                    error.message ?: "Android bloqueó la descarga del mapa.",
                                )
                            }
                        }
                    }

                    override fun onError(error: String) {
                        viewModel.setCreatingOfflineMapArea(false)
                        showOfflineMapMessage("No se pudo crear el mapa sin conexión: $error")
                    }
                },
            )
        } catch (error: IllegalArgumentException) {
            viewModel.setCreatingOfflineMapArea(false)
            showOfflineMapMessage(error.message ?: "No se pudo preparar el área seleccionada.")
        } catch (error: SecurityException) {
            viewModel.setCreatingOfflineMapArea(false)
            showOfflineMapMessage(error.message ?: "Android bloqueó la descarga del mapa.")
        }
    }

    fun setOfflineMapAreaDownloadState(id: Long, active: Boolean) {
        val region = offlineRegions[id]
        if (region == null) {
            showOfflineMapMessage("No se encontró el área guardada. Actualiza la lista.")
            refreshOfflineMapAreas()
            return
        }
        if (
            active &&
            viewModel.state.value.offlineMapAreas.any {
                it.id != id && it.isDownloading
            }
        ) {
            showOfflineMapMessage("Solo se puede descargar un área a la vez.")
            return
        }
        try {
            region.setDownloadState(
                if (active) OfflineRegion.STATE_ACTIVE else OfflineRegion.STATE_INACTIVE,
            )
            refreshOfflineMapAreaStatus(region)
            viewModel.setOfflineMapMessage(
                if (active) "Descarga reanudada." else "Descarga pausada.",
            )
        } catch (error: IllegalStateException) {
            showOfflineMapMessage(error.message ?: "No se pudo cambiar el estado de la descarga.")
        } catch (error: SecurityException) {
            showOfflineMapMessage(error.message ?: "Android bloqueó el cambio de descarga.")
        }
    }

    fun deleteOfflineMapArea(id: Long) {
        val region = offlineRegions[id]
        if (region == null) {
            showOfflineMapMessage("No se encontró el área que quieres eliminar.")
            refreshOfflineMapAreas()
            return
        }
        try {
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() {
                    mainHandler.post {
                        offlineRegions.remove(id)
                        offlineAreaMetadata.remove(id)
                        invalidOfflineRegions.remove(id)
                        viewModel.removeOfflineMapArea(id)
                        viewModel.setOfflineMapMessage("Mapa sin conexión eliminado.")
                    }
                }

                override fun onError(error: String) {
                    showOfflineMapMessage("No se pudo eliminar el mapa guardado: $error")
                }
            })
        } catch (error: IllegalStateException) {
            showOfflineMapMessage(error.message ?: "No se pudo eliminar el mapa guardado.")
        } catch (error: SecurityException) {
            showOfflineMapMessage(error.message ?: "Android bloqueó la eliminación del mapa.")
        }
    }

    private fun rejectOfflineMapArea(region: OfflineRegion, metadata: OfflineAreaMetadata) {
        if (!invalidOfflineRegions.add(region.id)) {
            return
        }
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                mainHandler.post {
                    offlineRegions.remove(region.id)
                    offlineAreaMetadata.remove(region.id)
                    invalidOfflineRegions.remove(region.id)
                    viewModel.removeOfflineMapArea(region.id)
                    showOfflineMapMessage(
                        "${metadata.name} superó el límite de 3.000 teselas o 50 MB y se eliminó.",
                    )
                }
            }

            override fun onError(error: String) {
                mainHandler.post {
                    invalidOfflineRegions.remove(region.id)
                    showOfflineMapMessage(
                        "${metadata.name} superó el límite y se pausó, pero no se pudo eliminar: $error",
                    )
                }
            }
        })
    }

    private fun showOfflineMapMessage(message: String) {
        mainHandler.post {
            if (!activity.isDestroyed) {
                viewModel.setCreatingOfflineMapArea(false)
                viewModel.setOfflineMapMessage(message, isError = true)
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private companion object {
        const val MAX_OFFLINE_AREA_BYTES = 50_000_000L
    }
}
