package pe.eduescale.mobile.activity

import android.database.sqlite.SQLiteException
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pe.eduescale.mobile.data.SchoolActivityRepository
import pe.eduescale.mobile.data.SchoolComment
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.activityKey
import pe.eduescale.mobile.ui.EduEscaleViewModel
import java.io.IOException

/** Comments and history operations, keeping the ViewModel in sync with Room. */
class SchoolActivityController(
    private val scope: LifecycleCoroutineScope,
    private val repository: SchoolActivityRepository,
    private val viewModel: EduEscaleViewModel,
    private val showMessage: (String) -> Unit,
) {
    private var schoolActivityLoadJob: Job? = null
    private var activityHistoryLoadJob: Job? = null

    fun addComment(school: SchoolRecord, text: String) {
        scope.launch {
            try {
                repository.addComment(school.activityKey(), text)
                refreshSchoolActivity(school, viewModel.state.value.commentSearchQuery)
            } catch (error: IllegalArgumentException) {
                showMessage(error.message ?: "No se pudo guardar el comentario.")
            } catch (error: IOException) {
                showMessage(error.message ?: "No se pudo guardar el comentario.")
            } catch (error: SQLiteException) {
                showMessage(error.message ?: "No se pudo guardar el comentario.")
            }
        }
    }

    fun deleteComment(school: SchoolRecord, commentId: String) {
        scope.launch {
            try {
                repository.deleteComment(school.activityKey(), commentId)
                refreshSchoolActivity(school, viewModel.state.value.commentSearchQuery)
            } catch (error: IOException) {
                showMessage(error.message ?: "No se pudo eliminar el comentario.")
            } catch (error: SQLiteException) {
                showMessage(error.message ?: "No se pudo eliminar el comentario.")
            }
        }
    }

    fun editComment(school: SchoolRecord, commentId: String, text: String) {
        scope.launch {
            try {
                repository.updateComment(school.activityKey(), commentId, text)
                refreshSchoolActivity(school, viewModel.state.value.commentSearchQuery)
            } catch (error: IllegalArgumentException) {
                showMessage(error.message ?: "No se pudo editar el comentario.")
            } catch (error: IOException) {
                showMessage(error.message ?: "No se pudo editar el comentario.")
            } catch (error: SQLiteException) {
                showMessage(error.message ?: "No se pudo editar el comentario.")
            }
        }
    }

    fun searchComments(school: SchoolRecord, query: String) {
        viewModel.setCommentSearchQuery(query)
        refreshSchoolActivity(school, query)
    }

    fun refreshSchoolActivity(school: SchoolRecord, query: String) {
        val schoolKey = school.activityKey()
        schoolActivityLoadJob?.cancel()
        schoolActivityLoadJob = scope.launch {
            try {
                val comments = repository.commentsFor(schoolKey, query)
                val reminders = repository.remindersFor(schoolKey)
                val current = viewModel.state.value
                if (
                    current.selectedSchool?.activityKey() == schoolKey &&
                    current.commentSearchQuery == query
                ) {
                    viewModel.setSchoolActivity(comments, reminders)
                }
            } catch (error: IOException) {
                showMessage(error.message ?: "No se pudieron leer los datos del colegio.")
            } catch (error: SQLiteException) {
                showMessage(error.message ?: "No se pudieron leer los datos del colegio.")
            }
        }
    }

    fun refreshActivityHistory() {
        activityHistoryLoadJob?.cancel()
        activityHistoryLoadJob = scope.launch {
            viewModel.setActivityHistoryLoading(true)
            try {
                repository.expirePastReminders()
                val comments = repository.allComments()
                val reminders = repository.allReminders()
                viewModel.setActivityHistory(comments, reminders)
            } catch (error: IOException) {
                viewModel.setActivityHistoryError(
                    error.message ?: "No se pudo cargar el historial.",
                )
            } catch (error: SQLiteException) {
                viewModel.setActivityHistoryError(
                    error.message ?: "No se pudo leer el historial guardado.",
                )
            }
        }
    }

    fun editHistoryComment(comment: SchoolComment, text: String) {
        scope.launch {
            try {
                repository.updateComment(comment.schoolKey, comment.id, text)
                refreshActivityHistory()
                viewModel.state.value.selectedSchool
                    ?.takeIf { it.activityKey() == comment.schoolKey }
                    ?.let { refreshSchoolActivity(it, viewModel.state.value.commentSearchQuery) }
                showMessage("Comentario actualizado.")
            } catch (error: IllegalArgumentException) {
                showMessage(error.message ?: "No se pudo editar el comentario.")
            } catch (error: IOException) {
                showMessage(error.message ?: "No se pudo editar el comentario.")
            } catch (error: SQLiteException) {
                showMessage(error.message ?: "No se pudo editar el comentario.")
            }
        }
    }
}
