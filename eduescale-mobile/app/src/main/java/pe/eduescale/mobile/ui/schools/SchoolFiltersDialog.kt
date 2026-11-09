package pe.eduescale.mobile.ui.schools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SchoolRecord

@Composable
internal fun FilterButton(count: Int, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.height(56.dp),
    ) {
        Icon(Icons.Default.FilterList, contentDescription = null)
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
        filters.niveles.forEach { add(it.replaceFirstChar(Char::uppercase)) }
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
    schools: List<SchoolRecord>,
    filters: SchoolFilters,
    onDismiss: () -> Unit,
    onApply: (SchoolFilters) -> Unit,
) {
    var department by remember(filters) { mutableStateOf(filters.departamento) }
    var province by remember(filters) { mutableStateOf(filters.provincia) }
    var district by remember(filters) { mutableStateOf(filters.distrito) }
    var management by remember(filters) { mutableStateOf(filters.gestiones) }
    var levels by remember(filters) { mutableStateOf(filters.niveles) }
    val departments = schools.mapNotNull(SchoolRecord::departamento).distinct().sorted()
    val provinces = schools.asSequence()
        .filter { department == null || it.departamento == department }
        .mapNotNull(SchoolRecord::provincia)
        .distinct()
        .sorted()
        .toList()
    val districts = schools.asSequence()
        .filter { department == null || it.departamento == department }
        .filter { province == null || it.provincia == province }
        .mapNotNull(SchoolRecord::distrito)
        .distinct()
        .sorted()
        .toList()
    val allManagement = schools.mapNotNull(SchoolRecord::gestion).distinct().sorted()
    val allLevels = schools.flatMap(SchoolRecord::niveles).distinct().sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros avanzados") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterDropdown("Departamento", department, departments) {
                        department = it
                        province = null
                        district = null
                    }
                    FilterDropdown("Provincia", province, provinces) {
                        province = it
                        district = null
                    }
                    FilterDropdown("Distrito", district, districts) { district = it }
                }
                item {
                    Text("Gestión", style = MaterialTheme.typography.titleSmall)
                    allManagement.forEach { option ->
                        FilterCheckbox(
                            label = managementLabel(option),
                            checked = option in management,
                            onCheckedChange = {
                                management = if (it) management + option else management - option
                            },
                        )
                    }
                }
                item {
                    Text("Nivel educativo", style = MaterialTheme.typography.titleSmall)
                    allLevels.forEach { option ->
                        FilterCheckbox(
                            label = option.replaceFirstChar(Char::uppercase),
                            checked = option in levels,
                            onCheckedChange = {
                                levels = if (it) levels + option else levels - option
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        SchoolFilters(
                            departamento = department,
                            provincia = province,
                            distrito = district,
                            gestiones = management,
                            niveles = levels,
                        ),
                    )
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
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
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
            modifier = Modifier.height(300.dp),
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
