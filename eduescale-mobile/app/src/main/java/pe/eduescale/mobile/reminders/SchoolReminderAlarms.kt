package pe.eduescale.mobile.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pe.eduescale.mobile.MainActivity
import pe.eduescale.mobile.R
import pe.eduescale.mobile.data.SchoolActivityRepository
import pe.eduescale.mobile.data.SchoolReminder
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SchoolReminderAlarms {
    const val CHANNEL_ID = "school_reminders"
    private const val ACTION_REMIND = "pe.eduescale.mobile.action.SCHOOL_REMINDER"
    private const val EXTRA_REMINDER_ID = "reminder_id"
    private const val EXTRA_SCHOOL_NAME = "school_name"
    private const val EXTRA_MESSAGE = "message"

    fun schedule(context: Context, reminder: SchoolReminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            throw SecurityException("Android no concedió permiso para programar alarmas exactas.")
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.scheduledAtMillis,
            reminderPendingIntent(context, reminder),
        )
    }

    fun cancel(context: Context, reminderId: String) {
        val pendingIntent = reminderPendingIntent(context, reminderId)
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios de colegios",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alarmas de recordatorios guardados para colegios."
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun reminderPendingIntent(
        context: Context,
        reminder: SchoolReminder,
    ): PendingIntent =
        reminderPendingIntent(
            context,
            reminder.id,
            reminder.schoolName,
            reminder.message,
        )

    private fun reminderPendingIntent(context: Context, reminderId: String): PendingIntent =
        reminderPendingIntent(context, reminderId, null, null)

    private fun reminderPendingIntent(
        context: Context,
        reminderId: String,
        schoolName: String?,
        message: String?,
    ): PendingIntent {
        val intent = Intent(context, SchoolReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMIND
            data = Uri.parse("eduescale://reminder/$reminderId")
            putExtra(EXTRA_REMINDER_ID, reminderId)
            if (schoolName != null) {
                putExtra(EXTRA_SCHOOL_NAME, schoolName)
            }
            if (message != null) {
                putExtra(EXTRA_MESSAGE, message)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    class SchoolReminderAlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
            val schoolName = intent.getStringExtra(EXTRA_SCHOOL_NAME)
            val message = intent.getStringExtra(EXTRA_MESSAGE)
            if (reminderId.isNullOrBlank() || schoolName.isNullOrBlank() || message.isNullOrBlank()) {
                Log.e(TAG, "Se recibió una alarma de colegio incompleta.")
                return
            }

            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    SchoolActivityRepository(context).markReminderTriggered(reminderId)
                } catch (error: IOException) {
                    Log.e(TAG, "No se pudo registrar la alarma activada.", error)
                } catch (error: SQLiteException) {
                    Log.e(TAG, "No se pudo registrar la alarma activada.", error)
                } finally {
                    pendingResult.finish()
                }
            }

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "No se mostró el recordatorio porque faltó permiso de notificaciones.")
                return
            }
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                Log.w(TAG, "No se mostró el recordatorio porque las notificaciones están desactivadas.")
                return
            }

            createNotificationChannel(context)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                reminderId.hashCode(),
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            try {
                NotificationManagerCompat.from(context).notify(
                    reminderId.hashCode(),
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(schoolName)
                        .setContentText(message)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build(),
                )
            } catch (error: SecurityException) {
                Log.e(TAG, "Android no permitió mostrar el recordatorio.", error)
            }
        }
    }

    class SchoolReminderRescheduleReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (
                intent.action != Intent.ACTION_BOOT_COMPLETED &&
                intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
                intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
            ) {
                return
            }
            if (!canScheduleExactAlarms(context)) {
                Log.w(TAG, "No se restauraron las alarmas porque falta permiso de alarmas exactas.")
                return
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "No se restauraron las alarmas porque falta permiso de notificaciones.")
                return
            }
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                Log.w(TAG, "No se restauraron las alarmas porque las notificaciones están desactivadas.")
                return
            }

            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val reminders = SchoolActivityRepository(context).pendingReminders()
                    reminders.forEach { reminder -> schedule(context, reminder) }
                } catch (error: IOException) {
                    Log.e(TAG, "No se pudieron restaurar los recordatorios guardados.", error)
                } catch (error: SQLiteException) {
                    Log.e(TAG, "No se pudieron restaurar los recordatorios guardados.", error)
                } catch (error: SecurityException) {
                    Log.e(TAG, "Android no permitió restaurar las alarmas.", error)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private const val TAG = "SchoolReminderAlarms"
}
