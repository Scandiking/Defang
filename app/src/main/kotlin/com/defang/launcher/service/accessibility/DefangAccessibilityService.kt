package com.defang.launcher.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.domain.model.AppConfig
import com.defang.launcher.domain.model.AppTier
import com.defang.launcher.domain.model.ContentTrack
import com.defang.launcher.domain.model.toDomain
import com.defang.launcher.domain.usecase.GetDailyExtensionStatusUseCase
import com.defang.launcher.domain.usecase.RecordSessionUseCase
import com.defang.launcher.domain.usecase.SelectContentTrackUseCase
import com.defang.launcher.service.overlay.EndCardOverlay
import com.defang.launcher.service.overlay.IntentGateOverlay
import com.defang.launcher.service.overlay.OverlayManager
import com.defang.launcher.service.overlay.SessionTimerOverlay
import com.defang.launcher.util.BrowserUrlExtractor
import com.defang.launcher.util.OfflinePromptSelector
import com.defang.launcher.util.TidbitSelector
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

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Current session state
    private var currentWatchedPackage: String? = null
    private var currentSessionId: Long? = null
    private var sessionStartMs: Long = 0L
    private var currentTimerOverlay: SessionTimerOverlay? = null
    private var currentGateOverlay: IntentGateOverlay? = null
    private var currentEndCard: EndCardOverlay? = null
    private var extensionUsedThisSession = false

    // Packages currently showing the intent gate (prevents re-trigger on internal windows)
    private val pendingGate = mutableSetOf<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return // ignore our own overlays

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
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
        val config = resolveConfig(pkg) ?: return
        if (config.tier != AppTier.WATCHED) return
        if (pkg in pendingGate) return

        if (config.isInCooldown) {
            showCooldownScreen(pkg, config.cooldownEndsAt)
            return
        }

        pendingGate.add(pkg)
        val contentTrack = selectContentTrack.forPackage(pkg)
        showIntentGate(pkg, contentTrack, config)
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

        pendingGate.add(pkg)
        showIntentGate(pkg, ContentTrack.ADULT, defaultBrowserConfig(pkg))
    }

    // ── Intent gate ───────────────────────────────────────────────────────────

    private fun showIntentGate(pkg: String, contentTrack: ContentTrack, config: AppConfig) {
        serviceScope.launch {
            currentGateOverlay = IntentGateOverlay(
                context = this@DefangAccessibilityService,
                config = config,
                contentTrack = contentTrack,
                tidbitSelector = tidbitSelector,
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
                    currentGateOverlay?.cancel()
                    overlayManager.dismissAll()
                    goHome()
                },
            )
            overlayManager.showFullscreen(currentGateOverlay!!.view)
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

        val durationMs = System.currentTimeMillis() - sessionStartMs
        val config = resolveConfig(pkg) ?: defaultBrowserConfig(pkg)
        val extensionAvailable = getDailyExtensionStatus.isExtensionAvailable()
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
        currentSessionId?.let { id -> recordSession.end(id, extensionUsedThisSession) }
        currentSessionId = null
        currentWatchedPackage = null
        currentTimerOverlay?.cancel()
        currentTimerOverlay = null
        currentGateOverlay?.cancel()
        currentGateOverlay = null
        currentEndCard?.cancel()
        currentEndCard = null
        overlayManager.dismissAll()
    }

    private suspend fun startCooldown(pkg: String, cooldownMinutes: Int) {
        val endsAt = System.currentTimeMillis() + cooldownMinutes * 60_000L
        appConfigRepo.setCooldown(pkg, endsAt)
    }

    private fun showCooldownScreen(pkg: String, cooldownEndsAt: Long) {
        // TODO Phase 2: proper cool-down screen. For now: go home.
        goHome()
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
        runBlocking { endCurrentSession() }
    }

    companion object {
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
