package com.mimora.app.feature.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mimora.app.MainActivity
import com.mimora.app.R
import com.mimora.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

private const val CHANNEL_ID = "mimora_evening_writing"
private const val ALARM_REQUEST_CODE = 4101
private const val NOTIFICATION_ID = 4102
const val WRITING_REMINDER_ACTION = "com.mimora.app.EVENING_WRITING_REMINDER"

/**
 * A gentle, once-a-day nudge at 7:00 PM. Exactly one alarm per install is kept:
 * rescheduling cancels the previous one first, so an old alarm can never fire
 * after the user turns the reminder off.
 */
object WritingReminder {

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Evening writing reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A quiet daily nudge to write in your private diary after 7 PM"
            }
        )
    }

    fun schedule(context: Context, enabled: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = alarmPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        if (!enabled) return

        createChannel(context)
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)

        // Inexact repeating avoids needing the restricted exact-alarm permission
        // while still firing reliably in the evening on a daily cadence.
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WritingReminderReceiver::class.java)
            .setAction(WRITING_REMINDER_ACTION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
    }
}

class WritingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WRITING_REMINDER_ACTION) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        WritingReminder.createChannel(context)
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, openApp, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("A moment for MIMORA")
            .setContentText("Your diary is waiting. How was your day?")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the alarm and the broadcast.
        }
    }
}

/** Re-arms the daily reminder after the device restarts. */
class WritingReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = UserPreferencesRepository.writingReminderEnabledOnce(context.applicationContext)
                WritingReminder.schedule(context.applicationContext, enabled)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
