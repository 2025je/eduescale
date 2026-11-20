package pe.eduescale.mobile.routing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valhalla.valhalla.ValhallaException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import pe.eduescale.mobile.data.SchoolCoordinates
import pe.eduescale.mobile.data.SchoolRecord

data class RoadRoutingState(
    val available: Boolean = false,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val calculating: Boolean = false,
    val distances: Map<Int, Double?> = emptyMap(),
    val orderedSchools: List<SchoolRecord> = emptyList(),
    val inputSchools: List<SchoolRecord> = emptyList(),
    val inputOrigin: SchoolCoordinates? = null,
    val error: String? = null,
)

@HiltViewModel
class RoadRoutingViewModel @Inject constructor(
    private val routing: RoadRoutingPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RoadRoutingState(available = routing.isAvailable()))
    val state = mutableState.asStateFlow()
    private var calculation: Job? = null
    private var schools: List<SchoolRecord> = emptyList()
    private var origin: SchoolCoordinates? = null

    fun download() {
        if (mutableState.value.downloading) return
        mutableState.update { it.copy(downloading = true, error = null) }
        viewModelScope.launch {
            try {
                routing.download { downloaded, total ->
                    mutableState.update { it.copy(downloadedBytes = downloaded, totalBytes = total) }
                }
                mutableState.update { it.copy(available = true) }
                calculate()
            } catch (error: IOException) {
                mutableState.update { it.copy(error = error.message ?: "No se pudieron descargar las carreteras.") }
            } catch (error: SecurityException) {
                mutableState.update { it.copy(error = error.message ?: "Android no permitió guardar las carreteras.") }
            } finally {
                mutableState.update { it.copy(downloading = false) }
            }
        }
    }

    fun updateInputs(schools: List<SchoolRecord>, origin: SchoolCoordinates?) {
        if (this.schools == schools && this.origin == origin) return
        this.schools = schools
        this.origin = origin
        calculate()
    }

    private fun calculate() {
        calculation?.cancel()
        val location = origin
        val snapshot = schools
        val targets = snapshot.filter { it.coordinates != null }
        mutableState.update {
            it.copy(
                distances = emptyMap(), orderedSchools = snapshot, calculating = false,
                inputSchools = snapshot, inputOrigin = location, error = null,
            )
        }
        if (location == null || !mutableState.value.available || targets.isEmpty()) return
        mutableState.update { it.copy(calculating = true, error = null) }
        calculation = viewModelScope.launch {
            try {
                val distances = routing.distances(location, targets.mapNotNull { it.coordinates })
                currentCoroutineContext().ensureActive()
                if (distances.size != targets.size) {
                    throw IOException("El resultado de rutas está incompleto.")
                }
                val byId = targets.mapIndexed { index, school -> school.id to distances[index] }.toMap()
                mutableState.update { it.copy(
                    distances = byId,
                    orderedSchools = snapshot.sortedBy { school -> byId[school.id] ?: Double.POSITIVE_INFINITY },
                    calculating = false,
                ) }
            } catch (error: IOException) {
                mutableState.update { it.copy(calculating = false, error = error.message ?: "No se pudieron calcular las rutas.") }
            } catch (error: ValhallaException) {
                mutableState.update { it.copy(calculating = false, error = error.message ?: "No se pudieron calcular las rutas.") }
            } catch (error: JSONException) {
                mutableState.update { it.copy(calculating = false, error = error.message ?: "La respuesta de rutas no es válida.") }
            }
        }
    }
}
