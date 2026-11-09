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
import java.util.Calendar

/** Dialogs and pickers shared by the reminder flows. */
object ReminderDialogs {
    fun pickDateTime(
        context: Context,
        initialScheduledAtMillis: Long,
        showMessage: (String) -> Unit,
        onSelected: (Long) -> Unit,
    ) {
        val now = Calendar.getInstance()
        val initial = Calendar.getInstance().apply {
            timeInMillis = maxOf(initialScheduledAtMillis, now.timeInMillis)
        }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val scheduledAt = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, hourOfDay, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        if (scheduledAt <= System.currentTimeMillis()) {
                            showMessage("Elige una fecha y hora futuras.")
                            return@TimePickerDialog
                        }
                        onSelected(scheduledAt)
                    },
                    initial.get(Calendar.HOUR_OF_DAY),
                    initial.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(context),
                ).show()
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.minDate = now.timeInMillis
        }.show()
    }

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
            .show()
    }
}
