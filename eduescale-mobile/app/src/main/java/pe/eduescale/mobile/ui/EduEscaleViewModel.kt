package pe.eduescale.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.eduescale.mobile.data.SchoolCoordinates
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.filterSchools
import pe.eduescale.mobile.data.schoolLevelOptions

data class SchoolFilterOptions(
    val departments: List<String> = emptyList(),
    val provinces: List<String> = emptyList(),
    val districts: List<String> = emptyList(),
    val management: List<String> = emptyList(),
    val levels: List<String> = emptyList(),
)

data class OfflineMapAreaUi(
    val id: Long,
    val name: String,
    val minZoom: Int,
    val maxZoom: Int,
    val estimatedTiles: Int,
    val completedTiles: Long,
    val completedBytes: Long,
    val isDownloading: Boolean,
    val isComplete: Boolean,
)

data class EduEscaleUiState(
    val schoolFile: SchoolFile? = null,
    val fileName: String? = null,
    val query: String = "",
    val filters: SchoolFilters = SchoolFilters(),
    val filterDraft: SchoolFilters? = null,
    val filterOptions: SchoolFilterOptions = SchoolFilterOptions(),
    val filterError: String? = null,
    val visibleSchools: List<SchoolRecord> = emptyList(),
    val userLocation: SchoolCoordinates? = null,
    val locationServicesEnabled: Boolean? = null,
    val selectedSchool: SchoolRecord? = null,
    val routeSchool: SchoolRecord? = null,
)

class EduEscaleViewModel(
    private val filteringDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EduEscaleUiState())
    val state: StateFlow<EduEscaleUiState> = mutableState.asStateFlow()
    private var filterJob: Job? = null

    fun setSchoolFile(file: SchoolFile, fileName: String) {
        filterJob?.cancel()
        val schools = file.schools + file.schoolsWithoutCoordinates
        mutableState.update {
            it.copy(
                schoolFile = file,
                fileName = fileName,
                query = "",
                filters = SchoolFilters(),
                filterDraft = null,
                filterOptions = SchoolFilterOptions(),
                filterError = null,
                visibleSchools = schools,
                selectedSchool = null,
            )
        }
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
        filterSchoolsAsync()
    }

    fun setFilters(filters: SchoolFilters) {
        val file = mutableState.value.schoolFile
        val schools = file?.let { it.schools + it.schoolsWithoutCoordinates }.orEmpty()
        if (file?.formatVersion == 1 &&
            filters.niveles.any { it !in setOf("primaria", "secundaria", "cetpro") }
        ) {
            mutableState.update {
                it.copy(filterError = "El archivo antiguo no contiene todos los niveles. Exporta un archivo actualizado desde Desktop.")
            }
            return
        }
        if (filters.gestiones.any { it == "publica_directa" || it == "publica_convenio" } &&
            schools.any { it.gestionDependenciaDesconocida }
        ) {
            mutableState.update {
                it.copy(filterError = "El archivo no contiene el tipo de gestión de todos los colegios públicos. Exporta un archivo actualizado desde Desktop.")
            }
            return
        }
        mutableState.update { it.copy(filters = filters, filterError = null) }
        filterSchoolsAsync()
    }

    fun openFilters() {
        val snapshot = mutableState.value
        val file = snapshot.schoolFile ?: return
        val schools = file.schools + file.schoolsWithoutCoordinates
        mutableState.update {
            it.copy(
                filterDraft = it.filters,
                filterOptions = filterOptions(schools, it.filters),
            )
        }
    }

    fun updateFilterDraft(filters: SchoolFilters) {
        val snapshot = mutableState.value
        val file = snapshot.schoolFile ?: return
        val previous = snapshot.filterDraft ?: return
        val options = if (
            previous.departamento != filters.departamento || previous.provincia != filters.provincia
        ) {
            filterOptions(file.schools + file.schoolsWithoutCoordinates, filters)
        } else {
            snapshot.filterOptions
        }
        mutableState.update { it.copy(filterDraft = filters, filterOptions = options) }
    }

    fun dismissFilters() {
        mutableState.update { it.copy(filterDraft = null) }
    }

    fun setUserLocation(latitude: Double, longitude: Double) {
        mutableState.update {
            it.copy(userLocation = SchoolCoordinates(latitude, longitude))
        }
    }

    fun setLocationServicesEnabled(enabled: Boolean) {
        mutableState.update {
            it.copy(locationServicesEnabled = enabled, userLocation = if (enabled) it.userLocation else null)
        }
    }

    fun selectSchool(school: SchoolRecord?) {
        mutableState.update {
            it.copy(
                selectedSchool = school,
                routeSchool = school ?: it.routeSchool,
            )
        }
    }

    private fun filterSchoolsAsync() {
        val snapshot = mutableState.value
        val file = snapshot.schoolFile ?: return
        val schools = file.schools + file.schoolsWithoutCoordinates
        val query = snapshot.query
        val filters = snapshot.filters
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            delay(FILTER_DEBOUNCE_MS)
            val matches = withContext(filteringDispatcher) {
                filterSchools(schools, query, filters)
            }
            mutableState.update { current ->
                if (
                    current.schoolFile === file &&
                    current.query == query &&
                    current.filters == filters
                ) {
                    val selectedSchool = current.selectedSchool?.takeIf { selected ->
                        matches.any { it.id == selected.id }
                    }
                    current.copy(
                        visibleSchools = matches,
                        selectedSchool = selectedSchool,
                    )
                } else {
                    current
                }
            }
        }
    }

    private fun filterOptions(schools: List<SchoolRecord>, draft: SchoolFilters): SchoolFilterOptions {
        val departmentSchools = schools.filter {
            draft.departamento == null || it.departamento == draft.departamento
        }
        val provinceSchools = departmentSchools.filter {
            draft.provincia == null || it.provincia == draft.provincia
        }
        return SchoolFilterOptions(
            departments = schools.mapNotNull(SchoolRecord::departamento).distinct().sorted(),
            provinces = departmentSchools.mapNotNull(SchoolRecord::provincia).distinct().sorted(),
            districts = provinceSchools.mapNotNull(SchoolRecord::distrito).distinct().sorted(),
            management = buildList {
                if (schools.any { it.gestion == "publica" }) {
                    if (schools.any { it.gestionDependenciaDesconocida }) add("publica")
                    else {
                        add("publica_directa")
                        add("publica_convenio")
                    }
                }
                if (schools.any { it.gestion == "privada" }) add("privada")
            },
            levels = schoolLevelOptions(schools),
        )
    }

    private companion object {
        const val FILTER_DEBOUNCE_MS = 120L
    }
}
