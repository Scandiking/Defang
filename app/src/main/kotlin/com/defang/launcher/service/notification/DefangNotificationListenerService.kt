package com.defang.launcher.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Color
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.defang.launcher.R
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.service.accessibility.DefangAccessibilityService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Notification sanitization (Phase 2, first slice).
 *
 * Notifications from watched apps are the platform's strongest re-engagement
 * dark pattern: brand-red badges, manufactured urgency ("X is waiting…"),
 * sound and vibration. This service cancels them and re-posts a single calm
 * replacement per app: gray accent, silent channel, no content preview, just
 * "N varsler venter". Tapping it launches the app the normal way — through
 * the intent gate, since the accessibility service sees the foreground change.
 *
 * Deliberately NOT intercepted:
 *  - ongoing / foreground-service notifications (media playback, uploads)
 *  - call-category notifications (incoming calls via messenger apps)
 *  - anything from non-watched apps
 *
 * Batching to delivery windows (full PRD Phase 2) can build on this later:
 * the interception point and counting are already here; only the scheduling
 * would be added.
 */
@AndroidEntryPoint
class DefangNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var appConfigRepo: AppConfigRepository
    @Inject lateinit var prefs: PreferencesDataStore

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile private var watchedPackages: Set<String> =
        DefangAccessibilityService.DEFAULT_WATCHED_PACKAGES
    @Volatile private var sanitizeEnabled = true

    // Package → count of suppressed notifications since the user last
    // opened/dismissed our summary. In-memory only: losing it on process death
    // just means the counter restarts, which is harmless.
    private val suppressedCounts = ConcurrentHashMap<String, Int>()
    private val appLabels = ConcurrentHashMap<String, String>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        serviceScope.launch {
            appConfigRepo.observeWatched().collect { list ->
                watchedPackages = list.map { it.packageName }.toSet() +
                    DefangAccessibilityService.DEFAULT_WATCHED_PACKAGES
                list.forEach { appLabels[it.packageName] = it.appLabel }
            }
        }
        serviceScope.launch {
            prefs.notificationSanitizeEnabled.collect { sanitizeEnabled = it }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (!sanitizeEnabled) return
        if (posted.packageName == packageName) return // our own summaries
        if (posted.packageName !in watchedPackages) return

        val notification = posted.notification
        // Never touch ongoing work or calls
        if (posted.isOngoing) return
        val protectedFlags = Notification.FLAG_FOREGROUND_SERVICE or
            Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_NO_CLEAR
        if (notification.flags and protectedFlags != 0) return
        if (notification.category == Notification.CATEGORY_CALL) return

        cancelNotification(posted.key)

        // Group summaries carry no content of their own — cancel but don't count
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val count = suppressedCounts.merge(posted.packageName, 1, Int::plus) ?: 1
        postSanitized(posted.packageName, count)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val removed = sbn ?: return
        // Our summary was tapped or swiped away — reset that app's counter.
        // (The tag of our summaries is the watched app's package name.)
        if (removed.packageName == packageName) {
            removed.tag?.let { suppressedCounts.remove(it) }
        }
    }

    private fun postSanitized(pkg: String, count: Int) {
        val label = appLabels[pkg] ?: runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg)

        // Neutral entry point: the app's front door, not the deep link the
        // notification wanted. The intent gate fires on open as usual.
        val contentIntent = packageManager.getLaunchIntentForPackage(pkg)?.let {
            PendingIntent.getActivity(
                this, pkg.hashCode(), it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val text = resources.getQuantityString(R.plurals.notif_pending, count, count)
        val summary = NotificationCompat.Builder(this, CHANNEL_ID)
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
            NotificationManagerCompat.from(this).notify(pkg, SUMMARY_ID, summary)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+) — suppression still worked,
            // the summary just isn't shown.
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW, // shade only: no sound, no heads-up
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "defang_sanitized"
        private const val SUMMARY_ID = 1001
    }
}
