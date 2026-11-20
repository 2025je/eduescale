package pe.eduescale.mobile.reminders

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.TimePicker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

@Composable
internal fun ReminderDateTimePicker(
    initialScheduledAtMillis: Long,
    showMessage: (String) -> Unit,
    onSelected: (Long) -> Unit,
    onCancelled: () -> Unit,
) {
    val context = LocalContext.current
    val currentShowMessage by rememberUpdatedState(showMessage)
    val currentOnSelected by rememberUpdatedState(onSelected)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    var selectedMillis by rememberSaveable {
        mutableLongStateOf(maxOf(initialScheduledAtMillis, System.currentTimeMillis()))
    }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(context, pickingTime) {
        val selected = Calendar.getInstance().apply { timeInMillis = selectedMillis }
        val dialog = if (!pickingTime) {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    selectedMillis = Calendar.getInstance().apply {
                        timeInMillis = selectedMillis
                        set(year, month, day)
                    }.timeInMillis
                    pickingTime = true
                },
                selected.get(Calendar.YEAR),
                selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH),
            ).apply {
                datePicker.minDate = System.currentTimeMillis()
                datePicker.init(
                    selected.get(Calendar.YEAR),
                    selected.get(Calendar.MONTH),
                    selected.get(Calendar.DAY_OF_MONTH),
                ) { _, year, month, day ->
                    selectedMillis = Calendar.getInstance().apply {
                        timeInMillis = selectedMillis
                        set(year, month, day)
                    }.timeInMillis
                }
                setOnCancelListener { currentOnCancelled() }
            }
        } else {
            object : TimePickerDialog(
                context,
                { _, hour, minute ->
                    val scheduledAt = Calendar.getInstance().apply {
                        timeInMillis = selectedMillis
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (scheduledAt <= System.currentTimeMillis()) {
                        currentShowMessage("Elige una fecha y hora futuras.")
                        currentOnCancelled()
                    } else {
                        currentOnSelected(scheduledAt)
                    }
                },
                selected.get(Calendar.HOUR_OF_DAY),
                selected.get(Calendar.MINUTE),
                android.text.format.DateFormat.is24HourFormat(context),
            ) {
                override fun onTimeChanged(view: TimePicker, hour: Int, minute: Int) {
                    super.onTimeChanged(view, hour, minute)
                    selectedMillis = Calendar.getInstance().apply {
                        timeInMillis = selectedMillis
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                    }.timeInMillis
                }
            }.apply {
                setOnCancelListener { currentOnCancelled() }
            }
        }
        dialog.show()
        onDispose { dialog.dismiss() }
    }
}

/** Dialogs and pickers shared by the reminder flows. */
object ReminderDialogs {
    fun showNotificationSettings(context: Context, showMessage: (String) -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Notificaciones desactivadas")
            .setMessage(
                "Activa las notificaciones de EduEscale para que las alarmas puedan avisarte.",
            )
            .setPositiveButton("Abrir configuración") { _, _ ->
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                }
                try {
                    context.startActivity(intent)
                } catch (error: ActivityNotFoundException) {
                    showMessage("No se pudo abrir la configuración de notificaciones.")
                }
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    fun showExactAlarmSettings(
        context: Context,
        message: String,
        openSettings: (Intent) -> Unit,
        showMessage: (String) -> Unit,
        onCancelled: () -> Unit = {},
    ) {
        AlertDialog.Builder(context)
            .setTitle("Permitir alarmas exactas")
            .setMessage(message)
            .setPositiveButton("Abrir configuración") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}"),
                )
                try {
                    openSettings(intent)
                } catch (error: ActivityNotFoundException) {
                    onCancelled()
                    showMessage("No se pudo abrir la configuración de alarmas exactas.")
                }
            }
            .setNegativeButton("Ahora no") { _, _ -> onCancelled() }
            .setOnCancelListener { onCancelled() }
            .show()
    }
}
