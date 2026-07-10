package com.defang.launcher.service.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers daily repeating alarms for the notification delivery windows.
 * Inexact by design — a batched summary arriving a few minutes late is fine,
 * and exact alarms need an extra permission on API 31+.
 *
 * Called whenever a window setting changes, and from BootReceiver (alarms do
 * not survive reboots).
 */
@Singleton
class BatchWindowScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesDataStore,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun rescheduleFromPrefs() {
        scheduleWindow(REQUEST_WINDOW_1, prefs.batchWindow1.first())
        scheduleWindow(REQUEST_WINDOW_2, prefs.batchWindow2.first())
    }

    private fun scheduleWindow(requestCode: Int, minutesOfDay: Int) {
        val pi = pendingIntent(requestCode)
        if (minutesOfDay < 0) {
            alarmManager.cancel(pi)
            return
        }

        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            trigger.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pi,
        )
    }

    private fun pendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, BatchDeliveryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val REQUEST_WINDOW_1 = 2001
        private const val REQUEST_WINDOW_2 = 2002
    }
}
