package pe.eduescale.mobile.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.eduescale.mobile.data.EduEscalePasswordRequiredException
import pe.eduescale.mobile.data.ImportedSchoolFile
import pe.eduescale.mobile.data.SchoolFileImportStore

data class SchoolFileImportState(
    val isImporting: Boolean = false,
    val error: String? = null,
    val passwordRequiredUri: String? = null,
)

sealed interface SchoolFileImportEvent {
    data class Loaded(val result: ImportedSchoolFile, val fromUser: Boolean) : SchoolFileImportEvent
}

@HiltViewModel
class SchoolFileImportViewModel @Inject constructor(
    private val store: SchoolFileImportStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SchoolFileImportState())
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<SchoolFileImportEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private val importMutex = Mutex()
    private var restoreStarted = false
    private var pendingImports = 0

    fun restore() {
        if (restoreStarted) return
        restoreStarted = true
        viewModelScope.launch {
            importMutex.withLock {
                try {
                    store.restore()?.let { eventChannel.send(SchoolFileImportEvent.Loaded(it, false)) }
                } catch (error: IOException) {
                    fail(error.message ?: "No se pudo restaurar el archivo guardado.")
                } catch (error: IllegalArgumentException) {
                    fail(error.message ?: "El archivo guardado no es válido.")
                }
            }
        }
    }

    fun importFile(uri: String, password: String? = null) {
        pendingImports++
        mutableState.update { it.copy(isImporting = true, error = null) }
        viewModelScope.launch {
            importMutex.withLock {
                try {
                    val result = store.importFile(uri, password)
                    mutableState.update { it.copy(error = null) }
                    eventChannel.send(SchoolFileImportEvent.Loaded(result, true))
                } catch (_: EduEscalePasswordRequiredException) {
                    mutableState.update { it.copy(passwordRequiredUri = uri) }
                } catch (error: IOException) {
                    fail(error.message ?: "No se pudo leer el archivo.")
                } catch (error: IllegalArgumentException) {
                    fail(error.message ?: "El archivo no es válido.")
                } catch (error: SecurityException) {
                    fail("Android no permitió leer el archivo seleccionado.")
                } finally {
                    pendingImports--
                    mutableState.update { it.copy(isImporting = pendingImports > 0) }
                }
            }
        }
    }

    fun clearPasswordRequest() {
        mutableState.update { it.copy(passwordRequiredUri = null) }
    }

    private fun fail(message: String) {
        mutableState.update { it.copy(error = message) }
    }
}
