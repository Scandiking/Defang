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
 * Schedules the alarms for the notification delivery windows.
 *
 * Each window is a one-shot alarm with a short explicit delivery window,
 * re-armed for the next day by [BatchDeliveryReceiver] when it fires. A daily
 * `setRepeating` alarm is inexact with a window of 75 % of the interval, so a
 * 12:00 window could fire as late as 06:00 the next day. Exact alarms need an
 * extra permission on API 31+, and ten minutes late is fine.
 *
 * Called whenever a window setting changes, when a window fires, on app start,
 * and from BootReceiver (alarms do not survive reboots).
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
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            nextTrigger(minutesOfDay, System.currentTimeMillis()),
            WINDOW_LENGTH_MS,
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

        /** The platform minimum for setWindow on API 31+. */
        private const val WINDOW_LENGTH_MS = 10 * 60_000L

        /**
         * Next occurrence of [minutesOfDay] strictly after [nowMs] (local
         * time). Strictly after, so a window that just fired is re-armed for
         * tomorrow instead of firing again.
         */
        internal fun nextTrigger(minutesOfDay: Int, nowMs: Long): Long =
            Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
                set(Calendar.MINUTE, minutesOfDay % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= nowMs) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
    }
}
