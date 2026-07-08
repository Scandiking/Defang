package com.defang.launcher.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.defang.launcher.data.repository.AppConfigRepository
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
import com.defang.launcher.util.OfflinePromptSelector
import com.defang.launcher.util.TidbitSelector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Core of Defang's Phase 1 behaviour.
 *
 * Lifecycle per watched-app open:
 * 1. TYPE_WINDOW_STATE_CHANGED fires for a watched package.
 * 2. If in cool-down → show cool-down lockout screen.
 * 3. Otherwise → show IntentGateOverlay.
 *    a. User taps declared intent or waits out countdown → dismiss gate, launch app,
 *       start SessionTimerOverlay + record session in DB.
 *    b. User taps "Go back" → dismiss gate, go to launcher.
 * 4. When session timer expires → cancel HUD, show EndCardOverlay.
 *    a. User requests extension (with friction) → add 10 min, mark extension used.
 *    b. User taps "Go home" → dismiss end card, go to launcher, start cool-down.
 * 5. User navigates away from watched app mid-session → end session in DB.
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

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Current session state
    private var currentWatchedPackage: String? = null
    private var currentSessionId: Long? = null
    private var sessionStartMs: Long = 0L
    private var currentTimerOverlay: SessionTimerOverlay? = null
    private var currentGateOverlay: IntentGateOverlay? = null
    private var currentEndCard: EndCardOverlay? = null
    private var extensionUsedThisSession = false

    // Set of packages currently showing the intent gate (prevents re-trigger on internal windows)
    private val pendingGate = mutableSetOf<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // our own overlays

        serviceScope.launch {
            handleForegroundChange(pkg)
        }
    }

    private suspend fun handleForegroundChange(pkg: String) {
        // If user navigated away from a watched app mid-session, end it
        if (pkg != currentWatchedPackage && currentWatchedPackage != null) {
            endCurrentSession()
        }

        // Use DB config if available; fall back to hardcoded list to handle the case
        // where LauncherViewModel hasn't seeded Room yet (e.g. first launch, cold start).
        val config = appConfigRepo.getConfig(pkg)?.toDomain()
            ?: if (pkg in DEFAULT_WATCHED_PACKAGES) {
                com.defang.launcher.domain.model.AppConfig(
                    packageName = pkg,
                    appLabel = pkg,
                    tier = AppTier.WATCHED,
                    sessionLimitMinutes = 15,
                    cooldownMinutes = 30,
                    gateDelaySeconds = 8,
                    cooldownEndsAt = 0L,
                )
            } else {
                return
            }
        if (config.tier != AppTier.WATCHED) return
        if (pkg in pendingGate) return // gate already showing for this package

        // Cool-down check
        if (config.isInCooldown) {
            showCooldownScreen(pkg, config.cooldownEndsAt)
            return
        }

        // Intent gate
        pendingGate.add(pkg)
        val contentTrack = selectContentTrack.forPackage(pkg)
        showIntentGate(pkg, contentTrack, config.gateDelaySeconds.toLong(),
            config.sessionLimitMinutes, config.cooldownMinutes)
    }

    private fun showIntentGate(
        pkg: String,
        contentTrack: ContentTrack,
        gateDelaySeconds: Long,
        sessionLimitMinutes: Int,
        cooldownMinutes: Int,
    ) {
        serviceScope.launch {
            val config = appConfigRepo.getConfig(pkg)?.toDomain() ?: return@launch
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
                        startSession(pkg, intent, sessionLimitMinutes, cooldownMinutes, contentTrack)
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

        val limitMs = sessionLimitMinutes * 60_000L

        currentTimerOverlay = SessionTimerOverlay(
            context = this,
            sessionLimitMs = limitMs,
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
        val config = appConfigRepo.getConfig(pkg)?.toDomain()
        val extensionAvailable = getDailyExtensionStatus.isExtensionAvailable()
        offlinePromptSelector.resetSession()

        currentEndCard = EndCardOverlay(
            context = this,
            appLabel = config?.appLabel ?: pkg,
            sessionDurationMs = durationMs,
            intentDeclared = currentGateOverlay?.let { null }, // stored in session
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

        // Move the app to background so the end card is fully visible
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

        // Start a new session record for the extension period
        val newSessionId = recordSession.start(pkg, "Extension: $reason")
        currentSessionId = newSessionId
        sessionStartMs = System.currentTimeMillis()

        currentEndCard?.cancel()
        overlayManager.dismissFullscreen()

        val extensionMs = 10 * 60_000L
        currentTimerOverlay = SessionTimerOverlay(
            context = this,
            sessionLimitMs = extensionMs,
            onSessionExpired = {
                serviceScope.launch {
                    // No second extension — pass extensionAvailable = false
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
        val config = appConfigRepo.getConfig(pkg)?.toDomain()
        offlinePromptSelector.resetSession()

        currentEndCard = EndCardOverlay(
            context = this,
            appLabel = config?.appLabel ?: pkg,
            sessionDurationMs = durationMs,
            intentDeclared = null,
            tidbit = tidbitSelector.next(contentTrack),
            offlinePrompt = offlinePromptSelector.next(),
            extensionAvailable = false, // no second extension
            onRequestExtension = {},    // unreachable
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

    private suspend fun endCurrentSession() {
        currentSessionId?.let { id ->
            recordSession.end(id, extensionUsedThisSession)
        }
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
        // TODO Phase 2: show a proper cool-down lockout Compose screen.
        // For now: just go home so the app is inaccessible.
        goHome()
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    override fun onInterrupt() {
        overlayManager.dismissAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        runBlocking { endCurrentSession() }
    }

    companion object {
        // Mirrors the default watched set in LauncherViewModel.
        // Used as a fallback when Room hasn't been seeded yet on first cold start.
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
        )
    }
}
