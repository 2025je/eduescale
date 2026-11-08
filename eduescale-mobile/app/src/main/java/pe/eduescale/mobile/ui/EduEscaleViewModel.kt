package pe.eduescale.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolComment
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SchoolReminder
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.filterSchools

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

data class OfflineMapBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val cameraZoom: Double,
)

data class EduEscaleUiState(
    val schoolFile: SchoolFile? = null,
    val fileName: String? = null,
    val isImporting: Boolean = false,
    val importError: String? = null,
    val query: String = "",
    val filters: SchoolFilters = SchoolFilters(),
    val visibleSchools: List<SchoolRecord> = emptyList(),
    val selectedSchool: SchoolRecord? = null,
    val schoolComments: List<SchoolComment> = emptyList(),
    val schoolReminders: List<SchoolReminder> = emptyList(),
    val commentSearchQuery: String = "",
    val activityHistoryComments: List<SchoolComment> = emptyList(),
    val activityHistoryReminders: List<SchoolReminder> = emptyList(),
    val isLoadingActivityHistory: Boolean = false,
    val activityHistoryError: String? = null,
    val offlineMapAreas: List<OfflineMapAreaUi> = emptyList(),
    val isCreatingOfflineMapArea: Boolean = false,
    val visibleMapBounds: OfflineMapBounds? = null,
    val offlineMapMessage: String? = null,
    val offlineMapMessageIsError: Boolean = false,
)

class EduEscaleViewModel(
    private val filteringDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EduEscaleUiState())
    val state: StateFlow<EduEscaleUiState> = mutableState.asStateFlow()
    private var filterJob: Job? = null

    fun setImporting(isImporting: Boolean) {
        mutableState.update { it.copy(isImporting = isImporting, importError = null) }
    }

    fun setImportError(message: String) {
        mutableState.update { it.copy(isImporting = false, importError = message) }
    }

    fun setSchoolFile(file: SchoolFile, fileName: String) {
        filterJob?.cancel()
        mutableState.update {
            it.copy(
                schoolFile = file,
                fileName = fileName,
                isImporting = false,
                importError = null,
                query = "",
                filters = SchoolFilters(),
                visibleSchools = file.schools,
                selectedSchool = null,
                schoolComments = emptyList(),
                schoolReminders = emptyList(),
                commentSearchQuery = "",
            )
        }
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
        filterSchoolsAsync()
    }

    fun setFilters(filters: SchoolFilters) {
        mutableState.update { it.copy(filters = filters) }
        filterSchoolsAsync()
    }

    fun selectSchool(school: SchoolRecord?) {
        mutableState.update {
            it.copy(
                selectedSchool = school,
                schoolComments = emptyList(),
                schoolReminders = emptyList(),
                commentSearchQuery = "",
            )
        }
    }

    fun setCommentSearchQuery(query: String) {
        mutableState.update { it.copy(commentSearchQuery = query) }
    }

    fun setSchoolActivity(
        comments: List<SchoolComment>,
        reminders: List<SchoolReminder>,
    ) {
        mutableState.update {
            it.copy(schoolComments = comments, schoolReminders = reminders)
        }
    }

    fun setActivityHistoryLoading(isLoading: Boolean) {
        mutableState.update {
            it.copy(isLoadingActivityHistory = isLoading, activityHistoryError = null)
        }
    }

    fun setActivityHistory(
        comments: List<SchoolComment>,
        reminders: List<SchoolReminder>,
    ) {
        mutableState.update {
            it.copy(
                activityHistoryComments = comments,
                activityHistoryReminders = reminders,
                isLoadingActivityHistory = false,
                activityHistoryError = null,
            )
        }
    }

    fun setActivityHistoryError(message: String) {
        mutableState.update {
            it.copy(isLoadingActivityHistory = false, activityHistoryError = message)
        }
    }

    fun setOfflineMapAreas(areas: List<OfflineMapAreaUi>) {
        mutableState.update { it.copy(offlineMapAreas = areas) }
    }

    fun updateOfflineMapArea(area: OfflineMapAreaUi) {
        mutableState.update { current ->
            current.copy(
                offlineMapAreas = current.offlineMapAreas
                    .filterNot { it.id == area.id } + area,
            )
        }
    }

    fun removeOfflineMapArea(id: Long) {
        mutableState.update {
            it.copy(offlineMapAreas = it.offlineMapAreas.filterNot { area -> area.id == id })
        }
    }

    fun setOfflineMapMessage(message: String?, isError: Boolean = false) {
        mutableState.update {
            it.copy(offlineMapMessage = message, offlineMapMessageIsError = isError)
        }
    }

    fun setCreatingOfflineMapArea(isCreating: Boolean) {
        mutableState.update { it.copy(isCreatingOfflineMapArea = isCreating) }
    }

    fun setVisibleMapBounds(bounds: OfflineMapBounds) {
        mutableState.update { it.copy(visibleMapBounds = bounds) }
    }

    private fun filterSchoolsAsync() {
        val snapshot = mutableState.value
        val file = snapshot.schoolFile ?: return
        val query = snapshot.query
        val filters = snapshot.filters
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            delay(FILTER_DEBOUNCE_MS)
            val matches = withContext(filteringDispatcher) {
                filterSchools(file.schools, query, filters)
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
                        schoolComments = if (selectedSchool == null) {
                            emptyList()
                        } else {
                            current.schoolComments
                        },
                        schoolReminders = if (selectedSchool == null) {
                            emptyList()
                        } else {
                            current.schoolReminders
                        },
                    )
                } else {
                    current
                }
            }
        }
    }

    private companion object {
        const val FILTER_DEBOUNCE_MS = 120L
    }
}
