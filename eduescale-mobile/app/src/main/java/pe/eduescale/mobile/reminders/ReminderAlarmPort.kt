package pe.eduescale.mobile.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import pe.eduescale.mobile.activity.SchoolReminder

interface ReminderAlarmPort {
    fun notificationPermissionGranted(): Boolean
    fun notificationsEnabled(): Boolean
    fun canScheduleExactAlarms(): Boolean
    fun schedule(reminder: SchoolReminder)
    fun cancel(reminderId: String)
}

class AndroidReminderAlarmPort @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderAlarmPort {
    override fun notificationPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    override fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun canScheduleExactAlarms(): Boolean =
        SchoolReminderAlarms.canScheduleExactAlarms(context)

    override fun schedule(reminder: SchoolReminder) {
        SchoolReminderAlarms.createNotificationChannel(context)
        SchoolReminderAlarms.schedule(context, reminder)
    }

    override fun cancel(reminderId: String) = SchoolReminderAlarms.cancel(context, reminderId)
}
