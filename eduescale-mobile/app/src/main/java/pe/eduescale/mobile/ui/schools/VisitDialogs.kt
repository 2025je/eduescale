package pe.eduescale.mobile.ui.schools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Diálogo para programar una visita.
 * Permite seleccionar fecha y agregar observaciones opcionales.
 */
@Composable
fun ScheduleVisitDialog(
    schoolName: String,
    onConfirm: (scheduledDateMillis: Long, observations: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var observations by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            initialDateMillis = selectedDateMillis ?: System.currentTimeMillis() + (24 * 60 * 60 * 1000),
            onDateSelected = { selectedDateMillis = it },
            onDismiss = { showDatePicker = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Programar visita a $schoolName",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selector de fecha
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Fecha",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Fecha programada",
                            style = MaterialTheme.typography.labelMedium
                        )
                        TextButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedDateMillis?.let { formatDate(it) }
                                    ?: "Seleccionar fecha",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Text(
                    "⚠️ Debe seleccionar una fecha futura",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Campo de observaciones (opcional)
                OutlinedTextField(
                    value = observations,
                    onValueChange = { observations = it },
                    label = { Text("Observaciones (opcional)") },
                    placeholder = { Text("Notas sobre la visita programada...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedDateMillis?.let {
                        onConfirm(it, observations)
                    }
                },
                enabled = selectedDateMillis != null
            ) {
                Text("Programar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Diálogo para marcar una visita como completada.
 * Permite agregar observaciones sobre la visita.
 */
@Composable
fun MarkVisitedDialog(
    schoolName: String,
    onConfirm: (observations: String) -> Unit,
    onDismiss: () -> Unit
) {
    var observations by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "✅ Registrar visita a $schoolName",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Observaciones sobre la visita (opcional)",
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = observations,
                    onValueChange = { observations = it },
                    placeholder = { Text("Detalles de la visita: estado del colegio, infraestructura, etc.") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                Text(
                    text = "Se registrará la fecha y hora actual: ${formatDateTime(System.currentTimeMillis())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(observations) }) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Diálogo para reportar permiso denegado.
 * Permite seleccionar razón y agregar detalles adicionales.
 */
@Composable
fun PermissionDeniedDialog(
    schoolName: String,
    onConfirm: (reason: String, details: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReason by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    val predefinedReasons = listOf(
        "Acceso negado por personal del colegio",
        "No hay persona disponible para atender",
        "Datos personales del colegio confidenciales",
        "Requerimiento de autorización escrita",
        "Otra razón"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "❌ Registrar permiso denegado",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "¿Por qué no se otorgó permiso en $schoolName?",
                    style = MaterialTheme.typography.labelMedium
                )

                // Razones predefinidas
                predefinedReasons.forEach { reason ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                if (selectedReason == "Otra razón") {
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Especificar razón") },
                        placeholder = { Text("Describa brevemente...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedReason.isNotEmpty()) {
                        val finalReason = if (selectedReason == "Otra razón") details else selectedReason
                        onConfirm(selectedReason, finalReason)
                    }
                },
                enabled = selectedReason.isNotEmpty()
            ) {
                Text("Registrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Diálogo simple para seleccionar fecha (DatePickerDialog simplificado).
 * En producción, usar Material3 DatePicker.
 */
@Composable
private fun DatePickerDialog(
    initialDateMillis: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var dayInput by remember { mutableStateOf("") }
    var monthInput by remember { mutableStateOf("") }
    var yearInput by remember { mutableStateOf("") }

    val calendar = Calendar.getInstance().apply { timeInMillis = initialDateMillis }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📅 Seleccionar fecha") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Ingrese la fecha (DD/MM/YYYY)", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = dayInput.ifEmpty { calendar.get(Calendar.DAY_OF_MONTH).toString() },
                        onValueChange = { dayInput = it.take(2) },
                        label = { Text("Día") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = monthInput.ifEmpty { (calendar.get(Calendar.MONTH) + 1).toString() },
                        onValueChange = { monthInput = it.take(2) },
                        label = { Text("Mes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = yearInput.ifEmpty { calendar.get(Calendar.YEAR).toString() },
                        onValueChange = { yearInput = it.take(4) },
                        label = { Text("Año") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.2f)
                    )
                }
                Text(
                    text = "Mínimo: ${formatDate(System.currentTimeMillis() + 24 * 60 * 60 * 1000)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val day = dayInput.ifEmpty { calendar.get(Calendar.DAY_OF_MONTH).toString() }.toInt()
                        val month = monthInput.ifEmpty { (calendar.get(Calendar.MONTH) + 1).toString() }.toInt() - 1
                        val year = yearInput.ifEmpty { calendar.get(Calendar.YEAR).toString() }.toInt()

                        val selectedCal = Calendar.getInstance().apply {
                            set(year, month, day, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        if (selectedCal.timeInMillis > System.currentTimeMillis()) {
                            onDateSelected(selectedCal.timeInMillis)
                            onDismiss()
                        }
                    } catch (e: Exception) {
                        // Error en validación
                    }
                }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Funciones auxiliares de formato
 */
private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(millis)
}

private fun formatDateTime(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(millis)
}
