package com.defang.launcher.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.defang.launcher.R
import com.defang.launcher.data.repository.AppConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the calm per-app summary notifications ("N notifications waiting").
 * Shared between the listener service (instant mode) and the batch delivery
 * receiver (window mode) so both produce identical output.
 */
@Singleton
class NotificationSummaryPoster @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfigRepo: AppConfigRepository,
) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW, // shade only: no sound, no heads-up
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    suspend fun postSummary(pkg: String, count: Int) {
        if (count <= 0) return
        ensureChannel()

        val pm = context.packageManager
        val label = appConfigRepo.getConfig(pkg)?.appLabel ?: runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        // Neutral entry point: the app's front door, not the deep link the
        // notification wanted. The intent gate fires on open as usual.
        val contentIntent = pm.getLaunchIntentForPackage(pkg)?.let {
            PendingIntent.getActivity(
                context, pkg.hashCode(), it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val text = context.resources.getQuantityString(R.plurals.notif_pending, count, count)
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_defang)
            .setContentTitle(label)
            .setContentText(text)
            .setColor(Color.GRAY)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()

        try {
            // Tag = watched package, so dismissal can be mapped back to it
            NotificationManagerCompat.from(context).notify(pkg, SUMMARY_ID, summary)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+) — suppression still works,
            // the summary just isn't shown.
        }
    }

    companion object {
        const val CHANNEL_ID = "defang_sanitized"
        const val SUMMARY_ID = 1001
    }
}
