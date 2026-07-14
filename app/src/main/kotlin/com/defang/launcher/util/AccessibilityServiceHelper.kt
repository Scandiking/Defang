package com.defang.launcher.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.defang.launcher.service.accessibility.DefangAccessibilityService

/**
 * Helpers for getting DefangAccessibilityService enabled with as little
 * friction as possible.
 *
 * Enable strategy, in order of preference:
 *  1. [tryEnableSelf] — works only if WRITE_SECURE_SETTINGS has been granted
 *     once via adb (`adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`).
 *     The grant survives reinstalls, so after Android Studio deploys the app
 *     silently re-enables its own service.
 *  2. [openAccessibilitySettings] — falls back to the system settings screen,
 *     scrolled to and pulse-highlighting our service row via the
 *     `:settings:fragment_args_key` extra (same hint other gatekeeper apps use).
 */
object AccessibilityServiceHelper {

    private fun component(context: Context): ComponentName =
        ComponentName(context, DefangAccessibilityService::class.java)

    fun isEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val flat = component(context).flattenToString()
        return enabledServices.split(':').any { it.equals(flat, ignoreCase = true) }
    }

    /**
     * Enables the service by writing secure settings directly. Returns true on
     * success. No-ops (returns false) without the WRITE_SECURE_SETTINGS grant.
     *
     * If the service is already listed in the setting but has no live binding
     * (the post-reinstall "Crashed services" wedge), it is removed and re-added
     * after a short delay — two immediate writes would coalesce and the
     * accessibility manager would never see the removal, so no rebind happens.
     */
    fun tryEnableSelf(context: Context): Boolean {
        val granted = context.checkSelfPermission(
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        return try {
            val resolver = context.contentResolver
            val flat = component(context).flattenToString()
            val services = Settings.Secure.getString(
                resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty().split(':').filter { it.isNotBlank() }
            // Keep TalkBack etc. intact — only our own entry is touched
            val without = services.filterNot { it.equals(flat, ignoreCase = true) }

            fun enable() = runCatching {
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    (without + flat).joinToString(":"),
                )
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }

            if (without.size != services.size) {
                // Listed but dead (callers only reach here when there is no
                // live binding) — clear the entry, then re-add to force a rebind
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    without.joinToString(":"),
                )
                Handler(Looper.getMainLooper()).postDelayed({ enable() }, 300)
            } else {
                enable()
            }
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Opens the system accessibility settings scrolled to our service, with the
     * pulsating highlight. The extras are undocumented but honoured by AOSP
     * Settings and most OEM skins; unknown extras are simply ignored, so the
     * worst case is the plain accessibility screen.
     */
    fun openAccessibilitySettings(context: Context) {
        val flat = component(context).flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(":settings:fragment_args_key", flat)
            putExtra(":settings:show_fragment_args", Bundle().apply {
                putString(":settings:fragment_args_key", flat)
            })
        }
        context.startActivity(intent)
    }

    /** Last time we auto-navigated to the settings screen after a failed silent rebind. */
    private var lastAutoPromptMs = 0L
    private const val AUTO_PROMPT_COOLDOWN_MS = 60_000L

    /**
     * One call for "make sure the service is on": silently self-enables when
     * permitted, otherwise sends the user to the highlighted settings row.
     * Returns true if the service is (now) enabled without user action.
     *
     * [tryEnableSelf] only reports whether the settings write succeeded, not
     * whether the accessibility manager actually rebound the service — on some
     * OEM builds the remove-then-re-add trick is silently ignored and the
     * service stays in "Crashed services" forever with no live binding. So a
     * successful write still needs to be verified against [isRunning] after
     * the rebind has had time to land; only then do we know the silent path
     * actually worked.
     */
    fun ensureEnabled(context: Context): Boolean {
        // The live binding is the ground truth — the setting string can claim
        // "enabled" while the binding is dead after a reinstall
        if (DefangAccessibilityService.isRunning) return true
        if (tryEnableSelf(context)) {
            verifyRebindLanded(context.applicationContext)
            return true
        }
        if (isEnabled(context)) return true // listed, no way to force a rebind ourselves
        openAccessibilitySettings(context)
        return false
    }

    /**
     * Checks back after the internal rebind delay to confirm the service
     * actually reconnected. If it didn't, the silent path has failed on this
     * device — fall back to the settings screen so the user isn't left with
     * a dead gate and no signal that anything is wrong.
     */
    private fun verifyRebindLanded(appContext: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (DefangAccessibilityService.isRunning) return@postDelayed
            val now = System.currentTimeMillis()
            if (now - lastAutoPromptMs < AUTO_PROMPT_COOLDOWN_MS) return@postDelayed
            lastAutoPromptMs = now
            openAccessibilitySettings(appContext)
        }, 1_500)
    }
}
