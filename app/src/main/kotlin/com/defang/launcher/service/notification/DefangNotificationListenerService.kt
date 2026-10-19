package com.defang.launcher.service.notification

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.local.db.entity.AppConfigEntity
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.domain.model.AppTier
import com.defang.launcher.service.accessibility.DefangAccessibilityService
import com.defang.launcher.util.ContactNameCache
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
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
 *  - person-to-person messages whose title matches an address-book contact
 *    (when the contact bypass setting is on)
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
    @Volatile private var contactBypassEnabled = true

    /** Completes once settings and the watched list have loaded. Until then
     *  posts are ignored; the connect-time sweep picks them up afterwards. */
    private val ready = CompletableDeferred<Unit>()

    private val appLabels = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        summaryPoster.ensureChannel()
        contactNames.refreshIfStale()
        serviceScope.launch {
            sanitizeEnabled = prefs.notificationSanitizeEnabled.first()
            contactBypassEnabled = prefs.notificationContactBypass.first()
            applyConfigs(appConfigRepo.observeAll().first())
            ready.complete(Unit)
        }
        serviceScope.launch {
            appConfigRepo.observeAll().collect { applyConfigs(it) }
        }
        serviceScope.launch {
            prefs.notificationSanitizeEnabled.collect { sanitizeEnabled = it }
        }
        serviceScope.launch {
            prefs.notificationContactBypass.collect { contactBypassEnabled = it }
        }
    }

    private fun applyConfigs(all: List<AppConfigEntity>) {
        watchedPackages = computeWatchedPackages(
            all,
            DefangAccessibilityService.DEFAULT_WATCHED_PACKAGES,
        )
        all.filter { it.tier == AppTier.WATCHED.dbValue }
            .forEach { appLabels[it.packageName] = it.appLabel }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        // Clean up whatever piled up in the shade while we weren't bound
        // (fresh grant, reinstall, process restart).
        serviceScope.launch {
            ready.await()
            val active = try {
                activeNotifications
            } catch (_: SecurityException) {
                null // unbound again in the meantime
            }
            active?.forEach { onNotificationPosted(it) }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        // The system unbinds us under memory pressure or after an update and
        // doesn't always come back on its own — ask for a rebind. Throws if
        // the user just revoked access, which is fine.
        try {
            requestRebind(ComponentName(this, DefangNotificationListenerService::class.java))
        } catch (_: RuntimeException) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (!ready.isCompleted) return
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

        // Contact bypass: DMs from people in the address book arrive untouched.
        // Only for person-to-person messages — app titles like "Snapchat" or
        // story/streak nags never match a contact by accident.
        if (contactBypassEnabled && isPersonMessage(notification)) {
            val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)
            if (contactNames.isContact(title)) return
        }

        cancelNotification(posted.key)

        // Group summaries carry no content of their own — cancel but don't count
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        serviceScope.launch {
            val count = incrementCount(posted.packageName)
            // Batch mode: stay silent now; BatchDeliveryReceiver posts at the window.
            // Read fresh from DataStore so a notification arriving right after
            // the service starts isn't treated as instant mode.
            if (!isBatchingActive()) {
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

    private suspend fun isBatchingActive(): Boolean =
        combine(prefs.batchWindow1, prefs.batchWindow2) { w1, w2 -> w1 >= 0 || w2 >= 0 }
            .first()

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

    companion object {
        /** True while the system has us bound. The Settings row reads this. */
        @Volatile
        var isConnected: Boolean = false
            private set

        /**
         * A message from one person to another: the MESSAGE category or a
         * MessagingStyle notification. Used to scope the contact bypass.
         */
        internal fun isPersonMessage(notification: Notification): Boolean {
            if (notification.category == Notification.CATEGORY_MESSAGE) return true
            val template = notification.extras?.getString(Notification.EXTRA_TEMPLATE)
            return template == Notification.MessagingStyle::class.java.name
        }

        /**
         * Merges DB-configured watched packages with the hardcoded default
         * list. A default is only used as a fallback for packages with no
         * [AppConfigEntity] row yet (cold start before seeding) — once a row
         * exists, its tier is authoritative, so a user downgrading a
         * default-watched app (e.g. Snapchat) to Utility actually takes
         * effect instead of being silently overridden by the default.
         */
        internal fun computeWatchedPackages(
            all: List<AppConfigEntity>,
            defaultWatchedPackages: Set<String>,
        ): Set<String> {
            val watched = all.filter { it.tier == AppTier.WATCHED.dbValue }
                .map { it.packageName }
                .toSet()
            val knownPackages = all.map { it.packageName }.toSet()
            val unseededDefaults = defaultWatchedPackages - knownPackages
            return watched + unseededDefaults
        }
    }
}
