package pe.eduescale.mobile.ui.schools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.eduescale.mobile.data.SavedSearch
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SavedSearchFile

interface SavedSearchStore {
    suspend fun load(): List<SavedSearch>
    suspend fun save(searches: List<SavedSearch>)
}

data class SavedSearchUiState(
    val searches: List<SavedSearch> = emptyList(),
    val message: String? = null,
    val messageIsError: Boolean = false,
    val isBusy: Boolean = true,
)

@HiltViewModel
class SavedSearchViewModel @Inject constructor(
    private val store: SavedSearchStore,
    private val file: SavedSearchFile,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SavedSearchUiState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var pendingOperations = 1

    init {
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val searches = store.load()
                    mutableState.update { it.copy(searches = searches) }
                } catch (error: IllegalArgumentException) {
                    setMessage(error.message ?: "No se pudieron leer las búsquedas guardadas.", true)
                } catch (error: IOException) {
                    setMessage(error.message ?: "No se pudieron leer las búsquedas guardadas.", true)
                } finally {
                    operationFinished()
                }
            }
        }
    }

    fun saveSearch(name: String, filters: SchoolFilters) {
        val search = SavedSearch.from(name, filters)
        replaceSearches("Búsqueda guardada.") { searches ->
            searches.filterNot { it.name.equals(search.name, ignoreCase = true) } + search
        }
    }

    fun deleteSearch(search: SavedSearch) {
        replaceSearches(null) { it - search }
    }

    fun importFile(uri: String) {
        pendingOperations++
        mutableState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val searches = file.read(uri)
                    store.save(searches)
                    mutableState.update {
                        it.copy(searches = searches, message = "Se importaron ${searches.size} búsquedas.", messageIsError = false)
                    }
                } catch (error: IllegalArgumentException) {
                    setMessage(error.message ?: "No se pudo importar el archivo de búsquedas.", true)
                } catch (error: IOException) {
                    setMessage(error.message ?: "No se pudo importar el archivo de búsquedas.", true)
                } catch (error: SecurityException) {
                    setMessage("Android no permitió leer el archivo de búsquedas.", true)
                } finally {
                    operationFinished()
                }
            }
        }
    }

    fun exportFile(uri: String) {
        if (state.value.isBusy) {
            setMessage("Espera a que terminen las búsquedas pendientes.", true)
            return
        }
        pendingOperations++
        mutableState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            mutex.withLock {
                try {
                    file.write(uri, state.value.searches)
                    setMessage("Búsquedas exportadas.")
                } catch (error: IllegalArgumentException) {
                    setMessage(error.message ?: "No se pudo exportar el archivo de búsquedas.", true)
                } catch (error: IOException) {
                    setMessage(error.message ?: "No se pudo exportar el archivo de búsquedas.", true)
                } catch (error: SecurityException) {
                    setMessage("Android no permitió escribir el archivo de búsquedas.", true)
                } finally {
                    operationFinished()
                }
            }
        }
    }

    private fun setMessage(message: String, isError: Boolean = false) {
        mutableState.update { it.copy(message = message, messageIsError = isError) }
    }

    private fun replaceSearches(
        successMessage: String?,
        transform: (List<SavedSearch>) -> List<SavedSearch>,
    ) {
        pendingOperations++
        mutableState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val searches = transform(state.value.searches)
                    store.save(searches)
                    mutableState.update {
                        it.copy(searches = searches, message = successMessage, messageIsError = false)
                    }
                } catch (error: IllegalArgumentException) {
                    setMessage(error.message ?: "No se pudo guardar la búsqueda.", true)
                } catch (error: IOException) {
                    setMessage(error.message ?: "No se pudo guardar la búsqueda.", true)
                } finally {
                    operationFinished()
                }
            }
        }
    }

    private fun operationFinished() {
        pendingOperations--
        mutableState.update { it.copy(isBusy = pendingOperations > 0) }
    }
}
