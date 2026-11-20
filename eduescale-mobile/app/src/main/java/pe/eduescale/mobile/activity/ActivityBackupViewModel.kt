package pe.eduescale.mobile.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParseException
import com.google.gson.JsonIOException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.eduescale.mobile.data.ActivityBackupFile

sealed interface ActivityBackupEvent {
    data class Message(val text: String) : ActivityBackupEvent
    data object Restored : ActivityBackupEvent
}

@HiltViewModel
class ActivityBackupViewModel @Inject constructor(
    private val repository: ActivityBackupStore,
    private val file: ActivityBackupFile,
) : ViewModel() {
    private val channel = Channel<ActivityBackupEvent>(Channel.UNLIMITED)
    val events = channel.receiveAsFlow()
    private val mutex = Mutex()

    fun export(uri: String) {
        viewModelScope.launch {
            mutex.withLock {
                try {
                    file.write(uri, repository.exportBackup())
                    channel.send(ActivityBackupEvent.Message("Copia exportada correctamente."))
                } catch (error: JsonIOException) {
                    message(error.message ?: "No se pudo escribir el archivo de respaldo.")
                } catch (error: IOException) {
                    message(error.message ?: "No se pudo exportar la copia.")
                } catch (error: SecurityException) {
                    message("Android no permitió escribir el archivo de respaldo.")
                }
            }
        }
    }

    fun import(uri: String) {
        viewModelScope.launch {
            mutex.withLock {
                try {
                    repository.importBackup(file.read(uri))
                    channel.send(ActivityBackupEvent.Restored)
                    message("Copia restaurada; los registros se combinaron con los actuales.")
                } catch (error: JsonIOException) {
                    message(error.message ?: "No se pudo leer el archivo de respaldo.")
                } catch (error: JsonParseException) {
                    message("El archivo de respaldo no contiene un JSON válido.")
                } catch (error: IllegalArgumentException) {
                    message(error.message ?: "El archivo de respaldo no es compatible.")
                } catch (error: IOException) {
                    message(error.message ?: "No se pudo leer el archivo de respaldo.")
                } catch (error: SecurityException) {
                    message("Android no permitió leer el archivo de respaldo.")
                }
            }
        }
    }

    private suspend fun message(text: String) { channel.send(ActivityBackupEvent.Message(text)) }
}
