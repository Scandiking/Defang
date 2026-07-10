package com.defang.launcher.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
     */
    fun tryEnableSelf(context: Context): Boolean {
        val granted = context.checkSelfPermission(
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        return try {
            val resolver = context.contentResolver
            val flat = component(context).flattenToString()
            val current = Settings.Secure.getString(
                resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()

            // Append rather than overwrite — keep TalkBack etc. intact
            if (current.split(':').none { it.equals(flat, ignoreCase = true) }) {
                val updated = if (current.isBlank()) flat else "$current:$flat"
                Settings.Secure.putString(
                    resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated
                )
            }
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
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

    /**
     * One call for "make sure the service is on": silently self-enables when
     * permitted, otherwise sends the user to the highlighted settings row.
     * Returns true if the service is (now) enabled without user action.
     */
    fun ensureEnabled(context: Context): Boolean {
        if (isEnabled(context)) return true
        if (tryEnableSelf(context)) return true
        openAccessibilitySettings(context)
        return false
    }
}
