package com.defang.launcher.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.domain.model.AppConfig
import com.defang.launcher.domain.model.AppTier
import com.defang.launcher.domain.model.ContentTrack
import com.defang.launcher.domain.model.toDomain
import com.defang.launcher.domain.usecase.GetDailyExtensionStatusUseCase
import com.defang.launcher.domain.usecase.RecordSessionUseCase
import com.defang.launcher.domain.usecase.SelectContentTrackUseCase
import com.defang.launcher.service.overlay.CooldownOverlay
import com.defang.launcher.service.overlay.EndCardOverlay
import com.defang.launcher.service.overlay.IntentGateOverlay
import com.defang.launcher.service.overlay.OverlayManager
import com.defang.launcher.service.overlay.SessionTimerOverlay
import com.defang.launcher.ui.widget.UsageWidgetProvider
import com.defang.launcher.util.BrowserUrlExtractor
import com.defang.launcher.util.GrayscaleController
import com.defang.launcher.util.OfflinePromptSelector
import com.defang.launcher.util.TidbitSelector
import com.defang.launcher.util.UsageStatsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Core of Defang's Phase 1 behaviour.
 *
 * Lifecycle per watched-app open:
 * 1. TYPE_WINDOW_STATE_CHANGED fires for a watched package or a browser.
 * 2. For browsers: URL is read from the address bar. If it matches an adult
 *    domain the gate fires, same as for any other watched app.
 * 3. If in cool-down → show cool-down lockout screen.
 * 4. Otherwise → show IntentGateOverlay.
 *    a. User taps declared intent or waits out countdown → dismiss gate, launch app,
 *       start SessionTimerOverlay + record session in DB.
 *    b. User taps "Go back" → dismiss gate, go to launcher.
 * 5. When session timer expires → cancel HUD, show EndCardOverlay.
 *    a. User requests extension (with friction) → add 10 min, mark extension used.
 *    b. User taps "Go home" → dismiss end card, go to launcher, start cool-down.
 * 6. User navigates away from watched app mid-session → end session in DB.
 */
@AndroidEntryPoint
class DefangAccessibilityService : AccessibilityService() {

    @Inject lateinit var overlayManager: OverlayManager
    @Inject lateinit var appConfigRepo: AppConfigRepository
    @Inject lateinit var recordSession: RecordSessionUseCase
    @Inject lateinit var getDailyExtensionStatus: GetDailyExtensionStatusUseCase
    @Inject lateinit var selectContentTrack: SelectContentTrackUseCase
    @Inject lateinit var tidbitSelector: TidbitSelector
    @Inject lateinit var offlinePromptSelector: OfflinePromptSelector
    @Inject lateinit var browserUrlExtractor: BrowserUrlExtractor
    @Inject lateinit var grayscale: GrayscaleController

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Current session state
    private var currentWatchedPackage: String? = null
    private var currentSessionId: Long? = null
    private var sessionStartMs: Long = 0L
    private var currentTimerOverlay: SessionTimerOverlay? = null
    private var currentGateOverlay: IntentGateOverlay? = null
    private var currentEndCard: EndCardOverlay? = null
    private var currentCooldownOverlay: CooldownOverlay? = null
    private var extensionUsedThisSession = false

    // Packages currently showing the intent gate (prevents re-trigger on internal windows)
    private val pendingGate = mutableSetOf<String>()

    // Package → suppress-gate deadline set when the user taps "Go back" on the gate.
    // Needed for browsers: the adult URL is still sitting in the URL bar after
    // dismissal, so without this the very next content-changed event would
    // re-fire the gate before the user can even clear the address bar.
    private val gateSuppressedUntilMs = mutableMapOf<String, Long>()

    // Fallback for watched apps that never deliver a TYPE_WINDOW_STATE_CHANGED
    // event at all (observed with Snapchat — window is structurally normal,
    // no FLAG_SECURE, but the event simply never arrives; likely an
    // anti-accessibility-service measure on the app's part). Polls
    // UsageStatsManager for the current foreground package as a backstop.
    // Routed through the same handleForegroundChange as real accessibility
    // events — that function already de-dupes via pendingGate/currentWatchedPackage,
    // so firing on both paths for apps that DO deliver events is harmless.
    //
    // Also covers a second, unrelated gap: swiping a watched app away in the
    // task switcher removes its task without any new, different app ever
    // coming to the foreground in a way accessibility events (or the poll's
    // own MOVE_TO_FOREGROUND tracking) would catch — the session would
    // otherwise survive indefinitely. Task removal still stops the app's
    // activity, which reliably emits MOVE_TO_BACKGROUND for that specific
    // package regardless of what (if anything) becomes foreground next, so
    // that's checked for directly rather than inferred from a foreground change.
    private var foregroundPollJob: Job? = null
    private var lastUsageEventQueryMs: Long = 0L

    // Lock screen desaturation: gray goes on at screen-off so the lock screen
    // (and everything glanced at before unlocking) renders colorless; color
    // returns at unlock unless a session or gate is active.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> serviceScope.launch { grayscale.enable() }
                Intent.ACTION_USER_PRESENT -> serviceScope.launch {
                    if (currentWatchedPackage == null && pendingGate.isEmpty()) {
                        grayscale.disable()
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        serviceScope.launch {
            // If we crashed mid-session with grayscale on, restore color now —
            // unless the device happens to be locked right now, in which case
            // recoverIfStale()'s disable() call already no-ops and this then
            // applies gray outright, so a service restart while locked can
            // never leave the lock screen in color.
            grayscale.recoverIfStale()
            if (grayscale.isDeviceLocked()) grayscale.enable()
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }

        lastUsageEventQueryMs = System.currentTimeMillis()
        foregroundPollJob = serviceScope.launch { pollForegroundAppLoop() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        foregroundPollJob?.cancel()
        foregroundPollJob = null
        return super.onUnbind(intent)
    }

    // ── Usage-stats foreground poll (fallback) ──────────────────────────────────

    private suspend fun pollForegroundAppLoop() {
        while (true) {
            delay(FOREGROUND_POLL_INTERVAL_MS)
            // Self-heals a missed SCREEN_OFF broadcast or an errant disable()
            // call: the lock screen must never sit in color for more than one
            // poll tick, independent of the usage-stats permission below.
            if (grayscale.isDeviceLocked()) grayscale.enable()
            if (!UsageStatsHelper.isEnabled(this)) continue
            processUsageEventsSinceLastPoll()
        }
    }

    /**
     * Walks every usage event since the last poll tick, in order — not just the
     * final state — so a close-then-reopen of the same watched app within a
     * single poll window (e.g. swipe away in recents, immediately relaunch)
     * is seen as two distinct transitions rather than collapsed into "no change."
     */
    private suspend fun processUsageEventsSinceLastPoll() {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(lastUsageEventQueryMs, now)
        lastUsageEventQueryMs = now
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // The watched app's task ending — including removal from the
                    // task switcher, which never fires a normal foreground-change
                    // event for anything else — reliably shows up here regardless
                    // of what (if anything) becomes foreground next.
                    if (pkg == currentWatchedPackage) {
                        Log.d(TAG, "usageStatsPoll backgrounded pkg=$pkg")
                        endCurrentSession()
                    }
                }
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (pkg == packageName) continue // our own UI, nothing to gate
                    Log.d(TAG, "usageStatsPoll pkg=$pkg")
                    handleForegroundChange(pkg, null)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) {
            // Ignore our own overlay windows — but when the *launcher activity*
            // comes to the foreground (user pressed home mid-session), lift
            // grayscale. The session itself keeps running.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.className?.toString()?.endsWith(".LauncherActivity") == true
            ) {
                serviceScope.launch { grayscale.disable() }
            }
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Volume panel, notification shade, keyboards: windows that
                // float above the current app without it losing the foreground.
                // Treating them as app switches would end the session and lift
                // grayscale mid-use.
                if (isTransientOverlay(pkg)) return
                // The task switcher hovers over the session the same way — keep
                // the session alive and render it colorless while it's open
                if (event.className?.toString().orEmpty().contains("RecentsActivity")) {
                    serviceScope.launch { grayscale.enable() }
                    return
                }
                Log.d(TAG, "windowState pkg=$pkg")
                val isBrowser = pkg in browserUrlExtractor.browserPackages
                // Extract URL synchronously while we still have the window context.
                // Reset the throttle so a fresh navigation always gets checked immediately.
                val browserUrl = if (isBrowser) {
                    browserUrlExtractor.resetThrottle(pkg)
                    browserUrlExtractor.extractUrl(this, pkg)
                } else null

                serviceScope.launch {
                    handleForegroundChange(pkg, browserUrl)
                    // If the URL bar wasn't populated yet (common — Chrome updates it
                    // asynchronously after the state-change event), retry once after 400 ms.
                    if (isBrowser && browserUrl == null) {
                        delay(400)
                        val retryUrl = browserUrlExtractor.extractUrl(
                            this@DefangAccessibilityService, pkg
                        )
                        if (retryUrl != null) handleBrowserUrl(pkg, retryUrl)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg !in browserUrlExtractor.browserPackages) return
                // event.source is null for most WebView content changes — don't filter on it.
                // Instead, throttle: extract at most once per 600 ms.
                event.source?.recycle() // recycle if non-null, ignore otherwise
                if (browserUrlExtractor.isThrottled(pkg)) return

                val url = browserUrlExtractor.extractUrl(this, pkg)
                if (url != null) {
                    serviceScope.launch { handleBrowserUrl(pkg, url) }
                }
            }
        }
    }

    // ── Foreground change ─────────────────────────────────────────────────────

    private suspend fun handleForegroundChange(pkg: String, browserUrl: String?) {
        Log.d(TAG, "fgChange pkg=$pkg watched=$currentWatchedPackage pending=$pendingGate")
        // Navigating away from whatever we were watching — end that session
        if (pkg != currentWatchedPackage && currentWatchedPackage != null) {
            endCurrentSession()
        }

        // Browser: delegate to URL-based logic
        if (pkg in browserUrlExtractor.browserPackages) {
            if (browserUrl != null) handleBrowserUrl(pkg, browserUrl)
            return
        }

        // Regular app: look up config in DB, fall back to hardcoded default list
        val config = resolveConfig(pkg)
        if (config == null || config.tier != AppTier.WATCHED) {
            // Unwatched app in front, no session — clear any gray lingering
            // from an abandoned gate (left via notification or lock screen)
            grayscale.disable()
            if (config == null) Log.d(TAG, "no config for $pkg")
            return
        }
        if (pkg in pendingGate) { Log.d(TAG, "gate already pending for $pkg"); return }
        if (pkg == currentWatchedPackage) {
            // Session active — in-app navigation or return from home screen.
            // Re-apply grayscale in case the launcher lifted it.
            grayscale.enable()
            return
        }

        if (config.isInCooldown) {
            Log.d(TAG, "cooldown active for $pkg until ${config.cooldownEndsAt}")
            showCooldownScreen(pkg, config.cooldownEndsAt)
            return
        }

        val contentTrack = selectContentTrack.forPackage(pkg)
        // Adult content gets a hard short leash regardless of configured defaults
        val effectiveConfig = if (contentTrack == ContentTrack.ADULT) {
            config.copy(sessionLimitMinutes = ADULT_SESSION_LIMIT_MINUTES)
        } else config

        Log.d(TAG, "showing intent gate for $pkg")
        // Gray before the gate is even visible — otherwise the app flashes
        // in full color for the moment between app start and unlock
        grayscale.enable()
        pendingGate.add(pkg)
        showIntentGate(pkg, contentTrack, effectiveConfig)
    }

    // ── Browser URL handling ──────────────────────────────────────────────────

    private suspend fun handleBrowserUrl(pkg: String, rawUrl: String) {
        val hostname = browserUrlExtractor.extractHostname(rawUrl) ?: return
        val track = selectContentTrack.forDomain(hostname)

        if (track != ContentTrack.ADULT) {
            // Non-adult page — end any active browser adult session
            if (currentWatchedPackage == pkg) endCurrentSession()
            return
        }

        // Adult domain detected
        if (pkg in pendingGate) return           // gate already showing
        if (currentWatchedPackage == pkg) return // already in an active browser adult session
        if (System.currentTimeMillis() < (gateSuppressedUntilMs[pkg] ?: 0L)) {
            // User just tapped "Go back" — the adult URL is still in the URL bar.
            // Give them time to navigate away without instantly re-firing the gate.
            return
        }

        val config = defaultBrowserConfig(pkg)
            .copy(sessionLimitMinutes = ADULT_SESSION_LIMIT_MINUTES)

        grayscale.enable() // gray before the gate is visible, as in the app path
        pendingGate.add(pkg)
        showIntentGate(pkg, ContentTrack.ADULT, config)
    }

    // ── Intent gate ───────────────────────────────────────────────────────────

    private fun showIntentGate(pkg: String, contentTrack: ContentTrack, config: AppConfig) {
        serviceScope.launch {
            currentGateOverlay = IntentGateOverlay(
                context = this@DefangAccessibilityService,
                contentTrack = contentTrack,
                tidbitSelector = tidbitSelector,
                offlinePrompt = offlinePromptSelector.next(),
                onIntentDeclared = { intent ->
                    pendingGate.remove(pkg)
                    currentGateOverlay?.cancel()
                    overlayManager.dismissFullscreen()
                    serviceScope.launch {
                        startSession(pkg, intent, config.sessionLimitMinutes,
                            config.cooldownMinutes, contentTrack)
                    }
                },
                onGoBack = {
                    pendingGate.remove(pkg)
                    gateSuppressedUntilMs[pkg] = System.currentTimeMillis() + GO_BACK_SUPPRESS_MS
                    currentGateOverlay?.cancel()
                    overlayManager.dismissAll()
                    goHome()
                },
            )
            overlayManager.showFullscreen(currentGateOverlay!!.view)
            Log.d(TAG, "gate overlay shown for $pkg")
        }
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private suspend fun startSession(
        pkg: String,
        intentDeclared: String?,
        sessionLimitMinutes: Int,
        cooldownMinutes: Int,
        contentTrack: ContentTrack,
    ) {
        val sessionId = recordSession.start(pkg, intentDeclared)
        currentSessionId = sessionId
        currentWatchedPackage = pkg
        sessionStartMs = System.currentTimeMillis()
        extensionUsedThisSession = false
        grayscale.enable()

        currentTimerOverlay = SessionTimerOverlay(
            context = this,
            sessionLimitMs = sessionLimitMinutes * 60_000L,
            onSessionExpired = {
                serviceScope.launch {
                    onSessionExpired(pkg, sessionId, cooldownMinutes, contentTrack)
                }
            },
        )
        overlayManager.showHud(currentTimerOverlay!!.view)
    }

    private suspend fun onSessionExpired(
        pkg: String,
        sessionId: Long,
        cooldownMinutes: Int,
        contentTrack: ContentTrack,
    ) {
        currentTimerOverlay?.cancel()
        overlayManager.dismissHud()
        grayscale.disable() // end card and home screen render in color

        val durationMs = System.currentTimeMillis() - sessionStartMs
        val config = resolveConfig(pkg) ?: defaultBrowserConfig(pkg)
        // No "more time" for the adult track — a 10-minute extension on a
        // 1-minute session would defeat the short leash entirely
        val extensionAvailable = contentTrack != ContentTrack.ADULT &&
            getDailyExtensionStatus.isExtensionAvailable()
        offlinePromptSelector.resetSession()

        currentEndCard = EndCardOverlay(
            context = this,
            appLabel = config.appLabel,
            sessionDurationMs = durationMs,
            intentDeclared = null,
            tidbit = tidbitSelector.next(contentTrack),
            offlinePrompt = offlinePromptSelector.next(),
            extensionAvailable = extensionAvailable,
            onRequestExtension = { reason ->
                serviceScope.launch {
                    handleExtensionRequest(pkg, sessionId, cooldownMinutes, contentTrack, reason)
                }
            },
            onGoHome = {
                serviceScope.launch {
                    endCurrentSession()
                    startCooldown(pkg, cooldownMinutes)
                    overlayManager.dismissAll()
                    goHome()
                }
            },
            onAnotherPrompt = {
                currentEndCard?.updateOfflinePrompt(offlinePromptSelector.next())
            },
        )
        overlayManager.showFullscreen(currentEndCard!!.view)
        goHome()
    }

    private suspend fun handleExtensionRequest(
        pkg: String,
        sessionId: Long,
        cooldownMinutes: Int,
        contentTrack: ContentTrack,
        reason: String,
    ) {
        extensionUsedThisSession = true
        recordSession.end(sessionId, extensionUsed = true)

        val newSessionId = recordSession.start(pkg, "Extension: $reason")
        currentSessionId = newSessionId
        sessionStartMs = System.currentTimeMillis()

        currentEndCard?.cancel()
        overlayManager.dismissFullscreen()
        grayscale.enable() // back into the watched app for the extension

        currentTimerOverlay = SessionTimerOverlay(
            context = this,
            sessionLimitMs = 10 * 60_000L,
            onSessionExpired = {
                serviceScope.launch {
                    onSessionExpiredNoExtension(pkg, newSessionId, cooldownMinutes, contentTrack)
                }
            },
        )
        overlayManager.showHud(currentTimerOverlay!!.view)
    }

    private suspend fun onSessionExpiredNoExtension(
        pkg: String,
        sessionId: Long,
        cooldownMinutes: Int,
        contentTrack: ContentTrack,
    ) {
        currentTimerOverlay?.cancel()
        overlayManager.dismissHud()
        grayscale.disable()
        val durationMs = System.currentTimeMillis() - sessionStartMs
        val config = resolveConfig(pkg) ?: defaultBrowserConfig(pkg)
        offlinePromptSelector.resetSession()

        currentEndCard = EndCardOverlay(
            context = this,
            appLabel = config.appLabel,
            sessionDurationMs = durationMs,
            intentDeclared = null,
            tidbit = tidbitSelector.next(contentTrack),
            offlinePrompt = offlinePromptSelector.next(),
            extensionAvailable = false,
            onRequestExtension = {},
            onGoHome = {
                serviceScope.launch {
                    endCurrentSession()
                    startCooldown(pkg, cooldownMinutes)
                    overlayManager.dismissAll()
                    goHome()
                }
            },
            onAnotherPrompt = {
                currentEndCard?.updateOfflinePrompt(offlinePromptSelector.next())
            },
        )
        overlayManager.showFullscreen(currentEndCard!!.view)
        goHome()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Installed keyboard packages — an IME opening inside a watched app fires
    // TYPE_WINDOW_STATE_CHANGED just like an app switch would
    private val imePackages: Set<String> by lazy {
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .inputMethodList.map { it.packageName }.toSet()
    }

    private fun isTransientOverlay(pkg: String): Boolean =
        pkg == "com.android.systemui" || pkg in imePackages

    /**
     * Looks up app config in Room; falls back to the hardcoded default watched list
     * for packages not yet seeded (e.g. cold start before LauncherViewModel runs).
     */
    private suspend fun resolveConfig(pkg: String): AppConfig? {
        val fromDb = appConfigRepo.getConfig(pkg)?.toDomain()
        if (fromDb != null) return fromDb
        if (pkg in DEFAULT_WATCHED_PACKAGES) {
            return AppConfig(
                packageName = pkg,
                appLabel = pkg,
                tier = AppTier.WATCHED,
                sessionLimitMinutes = 15,
                cooldownMinutes = 30,
                gateDelaySeconds = 8,
                cooldownEndsAt = 0L,
            )
        }
        return null
    }

    /** Synthetic config used when the "watched package" is a browser on an adult domain. */
    private fun defaultBrowserConfig(pkg: String) = AppConfig(
        packageName = pkg,
        appLabel = "Browser",
        tier = AppTier.WATCHED,
        sessionLimitMinutes = 15,
        cooldownMinutes = 30,
        gateDelaySeconds = 8,
        cooldownEndsAt = 0L,
    )

    private suspend fun endCurrentSession() {
        grayscale.disable()
        currentSessionId?.let { id ->
            recordSession.end(id, extensionUsedThisSession)
            UsageWidgetProvider.requestRefresh(this)
        }
        currentSessionId = null
        currentWatchedPackage = null
        currentTimerOverlay?.cancel()
        currentTimerOverlay = null
        currentGateOverlay?.cancel()
        currentGateOverlay = null
        currentEndCard?.cancel()
        currentEndCard = null
        currentCooldownOverlay?.cancel()
        currentCooldownOverlay = null
        overlayManager.dismissAll()
    }

    private suspend fun startCooldown(pkg: String, cooldownMinutes: Int) {
        val endsAt = System.currentTimeMillis() + cooldownMinutes * 60_000L
        appConfigRepo.setCooldown(pkg, endsAt)
    }

    private fun showCooldownScreen(pkg: String, cooldownEndsAt: Long) {
        serviceScope.launch {
            val config = resolveConfig(pkg) ?: defaultBrowserConfig(pkg)
            currentCooldownOverlay?.cancel()
            currentCooldownOverlay = CooldownOverlay(
                context = this@DefangAccessibilityService,
                appLabel = config.appLabel,
                cooldownEndsAt = cooldownEndsAt,
                tidbit = tidbitSelector.next(selectContentTrack.forPackage(pkg)),
                onGoHome = {
                    currentCooldownOverlay?.cancel()
                    currentCooldownOverlay = null
                    overlayManager.dismissFullscreen()
                    goHome()
                },
                onExpired = {
                    // Cool-down ran out while staring at the screen — drop the
                    // lockout and run the normal gate flow for the app beneath
                    currentCooldownOverlay?.cancel()
                    currentCooldownOverlay = null
                    overlayManager.dismissFullscreen()
                    serviceScope.launch { handleForegroundChange(pkg, null) }
                },
            )
            overlayManager.showFullscreen(currentCooldownOverlay!!.view)
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    override fun onInterrupt() = overlayManager.dismissAll()

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        foregroundPollJob?.cancel()
        foregroundPollJob = null
        runCatching { unregisterReceiver(screenReceiver) }
        runBlocking { endCurrentSession() }
    }

    companion object {
        private const val TAG = "DefangSvc"

        /** Cadence for the usage-stats foreground poll fallback. */
        const val FOREGROUND_POLL_INTERVAL_MS = 2_000L

        /**
         * True while the system holds a live binding to this service. The
         * secure setting alone can lie: after an `adb install -r` the service
         * can be listed as enabled yet sit dead in the accessibility manager
         * ("Crashed services") until it is toggled off and on again.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** Hard session limit for adult content, overriding configured defaults. */
        const val ADULT_SESSION_LIMIT_MINUTES = 1

        /** After "Go back" on the gate, don't re-fire for this long. */
        const val GO_BACK_SUPPRESS_MS = 45_000L

        val DEFAULT_WATCHED_PACKAGES = setOf(
            "com.instagram.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.X.android",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.google.android.youtube",
            "com.tinder",
            "com.bumble.app",
            "co.hinge.app",
            "com.okcupid.okcupid",
            "com.grindr.android",
            "com.badoo.mobile",
            "com.match.android",
            "com.poc.happn",
            "com.meetic.jconnecte",
            // Adult content (sideloaded)
            "com.pornhub.pornhub",
            "com.xvideos.app",
            "com.xhamster.android",
            "com.xnxx.app",
            "com.onlyfans.app",
            "com.fancentro.android",
        )
    }
}
