package com.defang.launcher.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.defang.launcher.service.notification.DefangNotificationListenerService

/**
 * Same strategy as [AccessibilityServiceHelper], but for notification access:
 * silently self-enable via WRITE_SECURE_SETTINGS when the adb grant is present,
 * otherwise open the system screen — directly on our app's detail page where
 * the platform supports it (API 30+).
 */
object NotificationListenerHelper {

    private const val ENABLED_LISTENERS = "enabled_notification_listeners"

    private fun component(context: Context): ComponentName =
        ComponentName(context, DefangNotificationListenerService::class.java)

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, ENABLED_LISTENERS
        ) ?: return false
        val flat = component(context).flattenToString()
        return enabled.split(':').any { it.equals(flat, ignoreCase = true) }
    }

    fun tryEnableSelf(context: Context): Boolean {
        val granted = context.checkSelfPermission(
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        if (isEnabled(context)) return true

        return try {
            val resolver = context.contentResolver
            val flat = component(context).flattenToString()
            val current = Settings.Secure.getString(resolver, ENABLED_LISTENERS).orEmpty()
            val updated = if (current.isBlank()) flat else "$current:$flat"
            Settings.Secure.putString(resolver, ENABLED_LISTENERS, updated)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun openNotificationAccessSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                component(context).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Self-enable when permitted, otherwise open the system screen. */
    fun ensureEnabled(context: Context): Boolean {
        if (isEnabled(context)) return true
        if (tryEnableSelf(context)) return true
        openNotificationAccessSettings(context)
        return false
    }
}
