package pe.eduescale.mobile.ui.schools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.ui.SchoolFilterOptions

@Composable
internal fun FilterButton(count: Int, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.height(56.dp),
    ) {
        Icon(Icons.Default.FilterList, contentDescription = "Abrir filtros avanzados")
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(count.toString())
        }
    }
}

@Composable
internal fun ActiveFilterChips(filters: SchoolFilters, modifier: Modifier = Modifier) {
    val labels = buildList {
        filters.departamento?.let { add(it) }
        filters.provincia?.let { add(it) }
        filters.distrito?.let { add(it) }
        filters.gestiones.forEach { add(managementLabel(it)) }
        filters.niveles.forEach { add(schoolLevelLabel(it)) }
    }
    if (labels.isNotEmpty()) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(labels) { label ->
                AssistChip(onClick = {}, label = { Text(label) })
            }
        }
    }
}

@Composable
internal fun SchoolFiltersDialog(
    filters: SchoolFilters,
    options: SchoolFilterOptions,
    onChange: (SchoolFilters) -> Unit,
    onDismiss: () -> Unit,
    onApply: (SchoolFilters) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros avanzados") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterDropdown("Departamento", filters.departamento, options.departments) {
                        onChange(filters.copy(departamento = it, provincia = null, distrito = null))
                    }
                    FilterDropdown("Provincia", filters.provincia, options.provinces) {
                        onChange(filters.copy(provincia = it, distrito = null))
                    }
                    FilterDropdown("Distrito", filters.distrito, options.districts) {
                        onChange(filters.copy(distrito = it))
                    }
                }
                item {
                    Text("Gestión", style = MaterialTheme.typography.titleSmall)
                    options.management.forEach { option ->
                        FilterCheckbox(
                            label = managementLabel(option),
                            checked = option in filters.gestiones,
                            onCheckedChange = {
                                onChange(filters.copy(gestiones = if (it) filters.gestiones + option else filters.gestiones - option))
                            },
                        )
                    }
                }
                item {
                    Text("Nivel educativo", style = MaterialTheme.typography.titleSmall)
                    options.levels.forEach { option ->
                        FilterCheckbox(
                            label = schoolLevelLabel(option),
                            checked = option in filters.niveles,
                            onCheckedChange = {
                                onChange(filters.copy(niveles = if (it) filters.niveles + option else filters.niveles - option))
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(filters)
                },
            ) { Text("Aplicar") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onApply(SchoolFilters())
                    },
                ) { Text("Limpiar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
internal fun FilterDropdown(
    label: String,
    selected: String?,
    options: List<String>,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var buttonWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .onSizeChanged { buttonWidth = with(density) { it.width.toDp() } },
        ) {
            Text(
                "$label: ${selected ?: "Todos"}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ExpandMore, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(buttonWidth)
                .heightIn(max = 300.dp),
        ) {
            DropdownMenuItem(
                text = { Text("Todos") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun FilterCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

internal fun activeFilterCount(filters: SchoolFilters): Int =
    listOf(filters.departamento, filters.provincia, filters.distrito).count { it != null } +
        filters.gestiones.size + filters.niveles.size
