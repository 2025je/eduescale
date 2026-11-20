package pe.eduescale.mobile.reminders

import android.Manifest
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Bridges reminder events to Android dialogs and permission launchers. */
class ReminderController(
    private val activity: ComponentActivity,
    private val viewModel: ReminderViewModel,
    private val showMessage: (String) -> Unit,
    private val refresh: (String?) -> Unit,
) {
    private var shownPrompt: ReminderPrompt? = null
    private val requestNotificationPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.notificationPermissionResult(granted) }

    private val requestExactAlarmSettings = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.exactAlarmPermissionResult() }

    fun handle(event: ReminderEvent) {
        when (event) {
            is ReminderEvent.Message -> showMessage(event.text)
            is ReminderEvent.Refresh -> refresh(event.schoolKey)
            ReminderEvent.NotificationSettings ->
                ReminderDialogs.showNotificationSettings(activity, showMessage)
            ReminderEvent.HistoryExactAlarmSettings -> ReminderDialogs.showExactAlarmSettings(
                context = activity,
                message = "EduEscale necesita acceso a “Alarmas y recordatorios” para reactivar esta alarma.",
                openSettings = { activity.startActivity(it) },
                showMessage = showMessage,
            )
        }
    }

    fun showPrompt(prompt: ReminderPrompt?) {
        if (shownPrompt == prompt) return
        shownPrompt = prompt
        when (prompt) {
            null -> Unit
            is ReminderPrompt.PickDate -> Unit
            ReminderPrompt.NotificationPermission -> AlertDialog.Builder(activity)
                .setTitle("Permitir notificaciones")
                .setMessage("EduEscale necesita mostrar una notificación cuando llegue la hora del recordatorio.")
                .setPositiveButton("Continuar") { _, _ ->
                    viewModel.permissionRequestStarted()
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .setNegativeButton("Ahora no") { _, _ -> viewModel.cancelPending() }
                .setOnCancelListener { viewModel.cancelPending() }
                .show()
            ReminderPrompt.ExactAlarmPermission -> ReminderDialogs.showExactAlarmSettings(
                context = activity,
                message = "EduEscale necesita acceso a “Alarmas y recordatorios” para avisarte a la hora elegida, incluso si la app está cerrada.",
                openSettings = {
                    viewModel.permissionRequestStarted()
                    requestExactAlarmSettings.launch(it)
                },
                showMessage = showMessage,
                onCancelled = viewModel::cancelPending,
            )
        }
    }
}
