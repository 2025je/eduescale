package pe.eduescale.mobile.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.activityKey
import pe.eduescale.mobile.ui.history.ActivityHistoryEntry
import pe.eduescale.mobile.ui.history.ActivityHistoryFilter
import pe.eduescale.mobile.ui.history.buildActivityHistory

data class SchoolActivityUiState(
    val selectedSchoolKey: String? = null,
    val schoolComments: List<SchoolComment> = emptyList(),
    val schoolReminders: List<SchoolReminder> = emptyList(),
    val commentSearchQuery: String = "",
    val activityHistoryComments: List<SchoolComment> = emptyList(),
    val activityHistoryReminders: List<SchoolReminder> = emptyList(),
    val historyQuery: String = "",
    val historyFilter: ActivityHistoryFilter = ActivityHistoryFilter.ALL,
    val historyEntries: List<ActivityHistoryEntry> = emptyList(),
    val isLoadingActivityHistory: Boolean = false,
    val activityHistoryError: String? = null,
)

@HiltViewModel
class SchoolActivityViewModel @Inject constructor(
    private val repository: SchoolActivityStore,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        SchoolActivityUiState(
            historyQuery = savedState[HISTORY_QUERY] ?: "",
            historyFilter = savedState[HISTORY_FILTER] ?: ActivityHistoryFilter.ALL,
        ),
    )
    val state = mutableState.asStateFlow()
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var schoolActivityLoadJob: Job? = null
    private var activityHistoryLoadJob: Job? = null
    private var historySchoolNames: Map<String, String> = emptyMap()

    fun setHistorySchools(file: SchoolFile?) {
        historySchoolNames = file?.schools?.associate { school ->
            school.activityKey() to (school.nombre?.takeIf(String::isNotBlank) ?: "Colegio sin nombre")
        }.orEmpty()
        mutableState.update { it.withHistoryEntries() }
    }

    fun setHistoryQuery(query: String) {
        savedState[HISTORY_QUERY] = query
        mutableState.update { it.copy(historyQuery = query).withHistoryEntries() }
    }

    fun setHistoryFilter(filter: ActivityHistoryFilter) {
        savedState[HISTORY_FILTER] = filter
        mutableState.update { it.copy(historyFilter = filter).withHistoryEntries() }
    }

    private fun SchoolActivityUiState.withHistoryEntries() = copy(
        historyEntries = buildActivityHistory(
            activityHistoryComments, activityHistoryReminders, historySchoolNames,
            historyQuery, historyFilter, System.currentTimeMillis(),
        ),
    )

    fun selectSchool(school: SchoolRecord?) {
        schoolActivityLoadJob?.cancel()
        mutableState.update {
            it.copy(
                selectedSchoolKey = school?.activityKey(),
                schoolComments = emptyList(),
                schoolReminders = emptyList(),
                commentSearchQuery = "",
            )
        }
        if (school != null) refreshSchoolActivity(school, "")
    }

    fun synchronizeSelection(school: SchoolRecord?) {
        if (state.value.selectedSchoolKey != school?.activityKey()) selectSchool(school)
    }

    fun addComment(school: SchoolRecord, text: String) {
        viewModelScope.launch {
            try {
                repository.addComment(school.activityKey(), text)
                refreshSelectedSchool(school)
            } catch (error: IllegalArgumentException) {
                message(error.message ?: "No se pudo guardar el comentario.")
            } catch (error: IOException) {
                message(error.message ?: "No se pudo guardar el comentario.")
            }
        }
    }

    fun deleteComment(school: SchoolRecord, commentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteComment(school.activityKey(), commentId)
                refreshSelectedSchool(school)
            } catch (error: IOException) {
                message(error.message ?: "No se pudo eliminar el comentario.")
            }
        }
    }

    fun editComment(school: SchoolRecord, commentId: String, text: String) {
        viewModelScope.launch {
            try {
                repository.updateComment(school.activityKey(), commentId, text)
                refreshSelectedSchool(school)
            } catch (error: IllegalArgumentException) {
                message(error.message ?: "No se pudo editar el comentario.")
            } catch (error: IOException) {
                message(error.message ?: "No se pudo editar el comentario.")
            }
        }
    }

    fun searchComments(school: SchoolRecord, query: String) {
        if (state.value.selectedSchoolKey != school.activityKey()) return
        mutableState.update { it.copy(commentSearchQuery = query) }
        refreshSchoolActivity(school, query)
    }

    fun refreshSelectedSchool(school: SchoolRecord) {
        val current = state.value
        if (current.selectedSchoolKey == school.activityKey()) {
            refreshSchoolActivity(school, current.commentSearchQuery)
        }
    }

    private fun refreshSchoolActivity(school: SchoolRecord, query: String) {
        refreshSchoolActivityByKey(school.activityKey(), query)
    }

    fun refreshActivityHistory() {
        activityHistoryLoadJob?.cancel()
        activityHistoryLoadJob = viewModelScope.launch {
            mutableState.update { it.copy(isLoadingActivityHistory = true, activityHistoryError = null) }
            try {
                repository.expirePastReminders()
                val comments = repository.allComments()
                val reminders = repository.allReminders()
                mutableState.update {
                    it.copy(
                        activityHistoryComments = comments,
                        activityHistoryReminders = reminders,
                        isLoadingActivityHistory = false,
                        activityHistoryError = null,
                    ).withHistoryEntries()
                }
            } catch (error: IOException) {
                historyError(error.message ?: "No se pudo cargar el historial.")
            }
        }
    }

    fun editHistoryComment(comment: SchoolComment, text: String) {
        viewModelScope.launch {
            try {
                repository.updateComment(comment.schoolKey, comment.id, text)
                refreshActivityHistory()
                val current = state.value
                if (current.selectedSchoolKey == comment.schoolKey) {
                    refreshSchoolActivityByKey(comment.schoolKey, current.commentSearchQuery)
                }
                message("Comentario actualizado.")
            } catch (error: IllegalArgumentException) {
                message(error.message ?: "No se pudo editar el comentario.")
            } catch (error: IOException) {
                message(error.message ?: "No se pudo editar el comentario.")
            }
        }
    }

    private fun refreshSchoolActivityByKey(schoolKey: String, query: String) {
        schoolActivityLoadJob?.cancel()
        schoolActivityLoadJob = viewModelScope.launch {
            try {
                val comments = repository.commentsFor(schoolKey, query)
                val reminders = repository.remindersFor(schoolKey)
                mutableState.update { current ->
                    if (current.selectedSchoolKey == schoolKey && current.commentSearchQuery == query) {
                        current.copy(schoolComments = comments, schoolReminders = reminders)
                    } else {
                        current
                    }
                }
            } catch (error: IOException) {
                message(error.message ?: "No se pudieron leer los datos del colegio.")
            }
        }
    }

    private fun historyError(message: String) {
        mutableState.update { it.copy(isLoadingActivityHistory = false, activityHistoryError = message) }
    }

    private suspend fun message(text: String) {
        messageChannel.send(text)
    }

    private companion object {
        const val HISTORY_QUERY = "historyQuery"
        const val HISTORY_FILTER = "historyFilter"
    }
}
