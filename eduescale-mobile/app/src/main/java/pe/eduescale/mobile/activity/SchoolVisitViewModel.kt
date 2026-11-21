package pe.eduescale.mobile.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SchoolVisitViewModel @Inject constructor(
    private val visitStore: SchoolVisitStore,
) : ViewModel() {

    private val _visitsForSchool = MutableStateFlow<List<SchoolVisit>>(emptyList())
    val visitsForSchool: StateFlow<List<SchoolVisit>> = _visitsForSchool.asStateFlow()

    private val _pendingVisits = MutableStateFlow<List<SchoolVisit>>(emptyList())
    val pendingVisits: StateFlow<List<SchoolVisit>> = _pendingVisits.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadVisitsFor(schoolKey: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _visitsForSchool.value = visitStore.visitsFor(schoolKey)
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al cargar las visitas"
            } finally {
                _loading.value = false
            }
        }
    }

    fun markAsVisited(schoolKey: String, schoolName: String, observations: String? = null) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                visitStore.markAsVisited(schoolKey, schoolName, observations = observations)
                loadVisitsFor(schoolKey)
                loadPendingVisits()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al marcar como visitado"
            } finally {
                _loading.value = false
            }
        }
    }

    fun scheduleVisit(
        schoolKey: String,
        schoolName: String,
        scheduledDate: Long,
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                visitStore.scheduleVisit(schoolKey, schoolName, scheduledDate)
                loadVisitsFor(schoolKey)
                loadPendingVisits()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al programar la visita"
            } finally {
                _loading.value = false
            }
        }
    }

    fun reportPermissionDenied(
        schoolKey: String,
        schoolName: String,
        reason: String,
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                visitStore.reportPermissionDenied(schoolKey, schoolName, reason)
                loadVisitsFor(schoolKey)
                loadPendingVisits()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al registrar el permiso denegado"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadPendingVisits() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _pendingVisits.value = visitStore.visitsByStatus(VisitStatus.SCHEDULED)
            } catch (e: Exception) {
                _error.value = e.message ?: "Error al cargar visitas pendientes"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
