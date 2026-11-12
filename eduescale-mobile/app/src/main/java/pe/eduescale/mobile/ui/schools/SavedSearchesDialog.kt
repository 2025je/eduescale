package pe.eduescale.mobile.ui.schools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pe.eduescale.mobile.data.SavedSearch
import pe.eduescale.mobile.data.SchoolFilters

@Composable
internal fun SavedSearchesDialog(
    searches: List<SavedSearch>,
    canSave: Boolean,
    canApply: Boolean,
    message: String?,
    messageIsError: Boolean,
    onDismiss: () -> Unit,
    onApply: (SchoolFilters) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (SavedSearch) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Búsquedas guardadas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Guarda y comparte solo filtros; el archivo no incluye datos de colegios.")
                if (!canSave) Text("Carga un archivo de colegios para guardar o aplicar filtros.")
                message?.let {
                    Text(
                        it,
                        color = if (messageIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Nombre") },
                        singleLine = true,
                    )
                    TextButton(
                        onClick = {
                            onSave(name.trim())
                            name = ""
                        },
                        enabled = canSave && name.isNotBlank(),
                    ) { Text("Guardar") }
                }
                if (searches.isEmpty()) {
                    Text("No hay búsquedas guardadas.")
                } else {
                    LazyColumn {
                        items(searches, key = SavedSearch::name) { search ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = canApply,
                                        role = Role.Button,
                                        onClick = { onApply(search.toSchoolFilters()) },
                                    )
                                    .padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    search.name,
                                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                                )
                                IconButton(
                                    onClick = { onDelete(search) },
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar búsqueda ${search.name}",
                                    )
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onImport) { Text("Importar") }
                    TextButton(onClick = onExport, enabled = searches.isNotEmpty()) {
                        Text("Exportar")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
