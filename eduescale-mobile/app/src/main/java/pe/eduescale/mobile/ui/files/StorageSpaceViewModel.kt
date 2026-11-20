package pe.eduescale.mobile.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.eduescale.mobile.data.StorageSpace

data class StorageSpaceUiState(
    val availableBytes: Long? = null,
    val error: String? = null,
)

@HiltViewModel
class StorageSpaceViewModel @Inject constructor(
    private val storage: StorageSpace,
) : ViewModel() {
    private val mutableState = MutableStateFlow(StorageSpaceUiState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val bytes = storage.availableBytes()
                mutableState.value = StorageSpaceUiState(availableBytes = bytes)
            } catch (error: IOException) {
                mutableState.update { it.copy(availableBytes = null, error = error.message ?: "No se pudo consultar el almacenamiento disponible.") }
            } catch (error: SecurityException) {
                mutableState.update { it.copy(availableBytes = null, error = "Android no permitió consultar el almacenamiento.") }
            }
        }
    }
}
