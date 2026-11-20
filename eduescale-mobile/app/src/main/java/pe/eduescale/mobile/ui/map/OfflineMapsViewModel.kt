package pe.eduescale.mobile.ui.map

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import pe.eduescale.mobile.data.OfflineTileEstimator
import pe.eduescale.mobile.map.OfflineMapRegion
import pe.eduescale.mobile.map.OfflineMapStatus
import pe.eduescale.mobile.map.OfflineMapStore
import pe.eduescale.mobile.ui.OfflineMapAreaUi
import pe.eduescale.mobile.map.OfflineMapBounds

data class OfflineMapsUiState(
    val offlineMapAreas: List<OfflineMapAreaUi> = emptyList(),
    val isCreatingOfflineMapArea: Boolean = false,
    val visibleMapBounds: OfflineMapBounds? = null,
    val offlineMapMessage: String? = null,
    val offlineMapMessageIsError: Boolean = false,
)

@HiltViewModel
class OfflineMapsViewModel @Inject constructor(
    private val store: OfflineMapStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OfflineMapsUiState())
    val state = mutableState.asStateFlow()
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    val errors = messageChannel.receiveAsFlow()
    private val rejecting = mutableSetOf<Long>()
    private var styleReady = false

    init {
        store.listen(::onStatus, ::error)
    }

    fun setStyleReady(ready: Boolean) { styleReady = ready }

    fun setVisibleMapBounds(bounds: OfflineMapBounds) {
        mutableState.update { it.copy(visibleMapBounds = bounds) }
    }

    fun refreshOfflineMapAreas() {
        store.list { result ->
            result.fold(
                onSuccess = { regions ->
                    val existing = state.value.offlineMapAreas.associateBy { it.id }
                    mutableState.update {
                        it.copy(offlineMapAreas = regions.map { region ->
                            existing[region.id] ?: area(region)
                        })
                    }
                },
                onFailure = { error("No se pudieron cargar los mapas sin conexión: ${it.message}") },
            )
        }
    }

    fun downloadVisibleMapArea(bounds: OfflineMapBounds, minZoom: Int, maxZoom: Int) {
        if (!styleReady) {
            error("El mapa todavía se está preparando. Inténtalo de nuevo.")
            return
        }
        if (state.value.isCreatingOfflineMapArea || state.value.offlineMapAreas.any { it.isDownloading }) {
            error("Espera a que termine o pausa la descarga actual.")
            return
        }
        val estimated = try {
            OfflineTileEstimator.estimate(
                bounds.west, bounds.south, bounds.east, bounds.north, minZoom, maxZoom,
            )
        } catch (_: IllegalArgumentException) {
            error("El área o el rango de zoom seleccionado no es válido.")
            return
        }
        if (estimated > OfflineTileEstimator.MAX_TILE_COUNT) {
            error("Reduce el área o el zoom: el máximo es 3.000 teselas.")
            return
        }
        val region = OfflineMapRegion(
            id = 0,
            name = "Área ${DateFormat.getDateTimeInstance().format(Date())}",
            minZoom = minZoom,
            maxZoom = maxZoom,
            estimatedTiles = estimated.toInt(),
        )
        mutableState.update {
            it.copy(isCreatingOfflineMapArea = true, offlineMapMessage = "Preparando la descarga del área…", offlineMapMessageIsError = false)
        }
        store.create(bounds, region) { result ->
            mutableState.update { it.copy(isCreatingOfflineMapArea = false) }
            result.fold(
                onSuccess = { created ->
                    updateArea(area(created).copy(isDownloading = true))
                    store.setActive(created.id, true) { started ->
                        started.fold(
                            onSuccess = { message("Descarga iniciada: ${created.name}") },
                            onFailure = {
                                updateExisting(created.id) { area -> area.copy(isDownloading = false) }
                                error(it.message ?: "No se pudo iniciar la descarga.")
                            },
                        )
                    }
                },
                onFailure = { error(it.message ?: "No se pudo crear el mapa sin conexión.") },
            )
        }
    }

    fun setOfflineMapAreaDownloadState(id: Long, active: Boolean) {
        val current = state.value.offlineMapAreas.find { it.id == id }
        if (current == null) {
            error("No se encontró el área guardada. Actualiza la lista.")
            refreshOfflineMapAreas()
            return
        }
        if (active && (state.value.isCreatingOfflineMapArea ||
                state.value.offlineMapAreas.any { it.id != id && it.isDownloading })) {
            error("Solo se puede descargar un área a la vez.")
            return
        }
        updateArea(current.copy(isDownloading = active))
        store.setActive(id, active) { result ->
            result.fold(
                onSuccess = { message(if (active) "Descarga reanudada." else "Descarga pausada.") },
                onFailure = {
                    updateExisting(id) { area -> area.copy(isDownloading = current.isDownloading) }
                    error(it.message ?: "No se pudo cambiar el estado de la descarga.")
                },
            )
        }
    }

    fun deleteOfflineMapArea(id: Long) {
        store.delete(id) { result ->
            result.fold(
                onSuccess = {
                    removeArea(id)
                    message("Mapa sin conexión eliminado.")
                },
                onFailure = { error(it.message ?: "No se pudo eliminar el mapa guardado.") },
            )
        }
    }

    private fun onStatus(id: Long, status: OfflineMapStatus) {
        val current = state.value.offlineMapAreas.find { it.id == id } ?: return
        if (status.completedTiles > OfflineTileEstimator.MAX_TILE_COUNT || status.completedBytes > MAX_OFFLINE_AREA_BYTES) {
            reject(current)
            return
        }
        val complete = status.requiredCountPrecise && status.requiredResources > 0 &&
            status.completedResources >= status.requiredResources
        updateArea(current.copy(
            completedTiles = status.completedTiles,
            completedBytes = status.completedBytes,
            isDownloading = status.active && !complete,
            isComplete = complete,
        ))
    }

    private fun reject(area: OfflineMapAreaUi) {
        if (!rejecting.add(area.id)) return
        updateArea(area.copy(isDownloading = false))
        store.delete(area.id) { result ->
            rejecting.remove(area.id)
            result.fold(
                onSuccess = {
                    removeArea(area.id)
                    error("${area.name} superó el límite de 3.000 teselas o 50 MB y se eliminó.")
                },
                onFailure = { error("${area.name} superó el límite, pero no se pudo eliminar: ${it.message}") },
            )
        }
    }

    private fun area(region: OfflineMapRegion) = OfflineMapAreaUi(
        region.id, region.name, region.minZoom, region.maxZoom, region.estimatedTiles,
        0, 0, isDownloading = false, isComplete = false,
    )

    private fun updateExisting(id: Long, transform: (OfflineMapAreaUi) -> OfflineMapAreaUi) {
        mutableState.update { current ->
            current.copy(offlineMapAreas = current.offlineMapAreas.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun updateArea(area: OfflineMapAreaUi) {
        mutableState.update { current ->
            current.copy(offlineMapAreas = current.offlineMapAreas.filterNot { it.id == area.id } + area)
        }
    }

    private fun removeArea(id: Long) {
        mutableState.update { it.copy(offlineMapAreas = it.offlineMapAreas.filterNot { area -> area.id == id }) }
    }

    private fun message(text: String) {
        mutableState.update { it.copy(offlineMapMessage = text, offlineMapMessageIsError = false) }
    }

    private fun error(text: String) {
        mutableState.update { it.copy(offlineMapMessage = text, offlineMapMessageIsError = true) }
        messageChannel.trySend(text)
    }

    override fun onCleared() {
        store.close()
    }

    private companion object {
        const val MAX_OFFLINE_AREA_BYTES = 50_000_000L
    }
}
