package com.defang.launcher.service.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.service.accessibility.DefangAccessibilityService
import com.defang.launcher.util.ContactNameCache
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Notification sanitization + batching (Phase 2).
 *
 * Notifications from watched apps are cancelled and counted. Depending on
 * settings the calm per-app summary ("N notifications waiting") is posted
 * either immediately (instant mode) or at the configured delivery windows
 * (batch mode — see [BatchWindowScheduler] / [BatchDeliveryReceiver]).
 *
 * Never intercepted:
 *  - ongoing / foreground-service notifications (media playback, uploads)
 *  - call-category notifications (incoming calls via messenger apps)
 *  - notifications whose title matches an address-book contact
 *  - anything from non-watched apps
 *
 * Counts are persisted in DataStore so batch mode survives process death.
 */
@AndroidEntryPoint
class DefangNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var appConfigRepo: AppConfigRepository
    @Inject lateinit var prefs: PreferencesDataStore
    @Inject lateinit var summaryPoster: NotificationSummaryPoster
    @Inject lateinit var contactNames: ContactNameCache

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val countsMutex = Mutex()

    @Volatile private var watchedPackages: Set<String> =
        DefangAccessibilityService.DEFAULT_WATCHED_PACKAGES
    @Volatile private var sanitizeEnabled = true
    @Volatile private var batchingActive = false

    private val appLabels = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        summaryPoster.ensureChannel()
        contactNames.refreshIfStale()
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
        serviceScope.launch {
            prefs.batchWindow1.collect { w1 ->
                batchingActive = w1 >= 0 || prefs.batchWindow2.first() >= 0
            }
        }
        serviceScope.launch {
            prefs.batchWindow2.collect { w2 ->
                batchingActive = w2 >= 0 || prefs.batchWindow1.first() >= 0
            }
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

        // Contact bypass: DMs from real people arrive untouched
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        if (contactNames.isContact(title)) return

        cancelNotification(posted.key)

        // Group summaries carry no content of their own — cancel but don't count
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        serviceScope.launch {
            val count = incrementCount(posted.packageName)
            // Batch mode: stay silent now; BatchDeliveryReceiver posts at the window
            if (!batchingActive) {
                summaryPoster.postSummary(posted.packageName, count)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val removed = sbn ?: return
        // Our summary was tapped or swiped away — reset that app's counter.
        // (The tag of our summaries is the watched app's package name.)
        if (removed.packageName == packageName) {
            val pkg = removed.tag ?: return
            serviceScope.launch { clearCount(pkg) }
        }
    }

    private suspend fun incrementCount(pkg: String): Int = countsMutex.withLock {
        val counts = prefs.suppressedCounts.first().toMutableMap()
        val next = (counts[pkg] ?: 0) + 1
        counts[pkg] = next
        prefs.setSuppressedCounts(counts)
        next
    }

    private suspend fun clearCount(pkg: String) = countsMutex.withLock {
        val counts = prefs.suppressedCounts.first().toMutableMap()
        if (counts.remove(pkg) != null) {
            prefs.setSuppressedCounts(counts)
        }
    }
}
