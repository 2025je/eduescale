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
import pe.eduescale.mobile.data.distanceInMeters

data class RoadRoutingState(
    val available: Boolean = false,
    val storedBytes: Long = 0,
    val downloading: Boolean = false,
    val deleting: Boolean = false,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val calculating: Boolean = false,
    val distances: Map<Int, Double?> = emptyMap(),
    val orderedSchools: List<SchoolRecord> = emptyList(),
    val inputSchools: List<SchoolRecord> = emptyList(),
    val inputOrigin: SchoolCoordinates? = null,
    val error: String? = null,
    val routeSchoolId: Int? = null,
    val routeOrigin: SchoolCoordinates? = null,
    val routeDestination: SchoolCoordinates? = null,
    val route: RoadRoute? = null,
    val routeCalculating: Boolean = false,
    val routeError: String? = null,
)

@HiltViewModel
class RoadRoutingViewModel @Inject constructor(
    private val routing: RoadRoutingPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        RoadRoutingState(available = routing.isAvailable(), storedBytes = routing.storedBytes()),
    )
    val state = mutableState.asStateFlow()
    private var calculation: Job? = null
    private var routeCalculation: Job? = null
    private var schools: List<SchoolRecord> = emptyList()
    private var origin: SchoolCoordinates? = null
    private var selectedSchool: SchoolRecord? = null
    private var selectedOrigin: SchoolCoordinates? = null

    fun download() {
        if (mutableState.value.downloading || mutableState.value.deleting) return
        mutableState.update {
            it.copy(downloading = true, downloadedBytes = 0, totalBytes = 0, error = null)
        }
        viewModelScope.launch {
            try {
                routing.download { downloaded, total ->
                    mutableState.update { it.copy(downloadedBytes = downloaded, totalBytes = total) }
                }
                mutableState.update { it.copy(available = routing.isAvailable(), storedBytes = routing.storedBytes()) }
                calculate()
                calculateSelectedRoute()
            } catch (error: IOException) {
                mutableState.update { it.copy(error = error.message ?: "No se pudieron descargar las carreteras.") }
            } catch (error: SecurityException) {
                mutableState.update { it.copy(error = error.message ?: "Android no permitió guardar las carreteras.") }
            } finally {
                mutableState.update {
                    it.copy(
                        downloading = false,
                        available = routing.isAvailable(),
                        storedBytes = routing.storedBytes(),
                    )
                }
            }
        }
    }

    fun delete() {
        if (mutableState.value.downloading || mutableState.value.deleting || mutableState.value.storedBytes == 0L) return
        calculation?.cancel()
        routeCalculation?.cancel()
        mutableState.update {
            it.copy(deleting = true, calculating = false, routeCalculating = false, error = null)
        }
        viewModelScope.launch {
            var failure: String? = null
            try {
                routing.delete()
                mutableState.update {
                    it.copy(
                        available = routing.isAvailable(),
                        storedBytes = routing.storedBytes(),
                        distances = emptyMap(),
                        orderedSchools = schools,
                    )
                }
            } catch (error: IOException) {
                failure = error.message ?: "No se pudieron eliminar las carreteras."
            } catch (error: SecurityException) {
                failure = error.message ?: "Android no permitió eliminar las carreteras."
            } finally {
                mutableState.update {
                    it.copy(
                        deleting = false,
                        available = routing.isAvailable(),
                        storedBytes = routing.storedBytes(),
                    )
                }
            }
            if (failure != null) {
                calculate()
                if (mutableState.value.route == null) calculateSelectedRoute()
                mutableState.update { it.copy(error = failure) }
            } else {
                calculate()
            }
        }
    }

    fun updateInputs(schools: List<SchoolRecord>, origin: SchoolCoordinates?) {
        if (this.schools == schools && this.origin == origin) return
        this.schools = schools
        this.origin = origin
        calculate()
    }

    fun updateSelectedRoute(school: SchoolRecord?, origin: SchoolCoordinates?) {
        if (selectedSchool == school &&
            (selectedOrigin == origin || mutableState.value.route != null || mutableState.value.routeCalculating)
        ) return
        selectedSchool = school
        selectedOrigin = origin
        calculateSelectedRoute()
    }

    private fun calculateSelectedRoute() {
        routeCalculation?.cancel()
        val school = selectedSchool
        val location = selectedOrigin
        val destination = school?.coordinates
        mutableState.update {
            it.copy(
                routeSchoolId = school?.id,
                routeOrigin = location,
                routeDestination = destination,
                route = null,
                routeCalculating = false,
                routeError = null,
            )
        }
        if (location == null || destination == null || !mutableState.value.available || mutableState.value.deleting) {
            resumeInterruptedMatrix()
            return
        }
        if (calculation?.isActive == true) calculation?.cancel()
        mutableState.update { it.copy(routeCalculating = true) }
        routeCalculation = viewModelScope.launch {
            try {
                val route = routing.route(location, destination)
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(route = route, routeCalculating = false) }
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                mutableState.update {
                    it.copy(routeCalculating = false, routeError = error.message ?: "No se pudo calcular la ruta.")
                }
            } catch (error: ValhallaException) {
                currentCoroutineContext().ensureActive()
                mutableState.update {
                    it.copy(
                        routeCalculating = false,
                        routeError = error.message ?: "No se pudo calcular la ruta en automóvil.",
                    )
                }
            } catch (error: JSONException) {
                currentCoroutineContext().ensureActive()
                mutableState.update {
                    it.copy(routeCalculating = false, routeError = "La respuesta de la ruta no es válida.")
                }
            }
            resumeInterruptedMatrix()
        }
    }

    private fun resumeInterruptedMatrix() {
        if (mutableState.value.calculating && calculation?.isActive != true) calculate()
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
        if (location == null || mutableState.value.deleting || targets.isEmpty()) return
        if (!mutableState.value.available) {
            val byId = targets.mapNotNull { school ->
                distanceInMeters(school, location)?.let { school.id to it }
            }.toMap()
            mutableState.update {
                it.copy(
                    distances = byId,
                    orderedSchools = snapshot.sortedBy { school ->
                        byId[school.id] ?: Double.POSITIVE_INFINITY
                    },
                )
            }
            return
        }
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
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(calculating = false, error = error.message ?: "No se pudieron calcular las rutas.") }
            } catch (error: ValhallaException) {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(calculating = false, error = error.message ?: "No se pudieron calcular las rutas.") }
            } catch (error: JSONException) {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(calculating = false, error = error.message ?: "La respuesta de rutas no es válida.") }
            }
        }
    }
}
