package com.defang.launcher.util

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.defang.launcher.service.notification.DefangNotificationListenerService

/**
 * Notification access for [DefangNotificationListenerService].
 *
 * Unlike the accessibility service, there is no silent self-enable: the
 * notification manager keeps its own list of approved listeners and ignores
 * writes to the `enabled_notification_listeners` Secure setting (verified on
 * a Galaxy S22 / Android 16). Writing it anyway looked like success, so the
 * Settings row never opened the system screen and the feature silently did
 * nothing (#62). The user has to grant it on the system screen.
 */
object NotificationListenerHelper {

    private fun component(context: Context): ComponentName =
        ComponentName(context, DefangNotificationListenerService::class.java)

    /** Asks the notification manager itself, not the Secure-settings mirror. */
    fun isEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return context.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(component(context))
        }
        // API 26 has no direct query; the mirror is accurate when the user
        // granted access on the system screen.
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val flat = component(context).flattenToString()
        return enabled.split(':').any { it.equals(flat, ignoreCase = true) }
    }

    /** Where notification access stands, for the Settings row. */
    enum class Status { RUNNING, NOT_RUNNING, NOT_GRANTED }

    fun status(context: Context): Status = when {
        !isEnabled(context) -> Status.NOT_GRANTED
        DefangNotificationListenerService.isConnected -> Status.RUNNING
        else -> Status.NOT_RUNNING
    }

    /**
     * Granted doesn't mean bound: after an update or under memory pressure
     * the system can leave the listener unbound. Ask for a rebind.
     */
    fun requestRebind(context: Context) {
        try {
            NotificationListenerService.requestRebind(component(context))
        } catch (_: RuntimeException) {
            // Not granted, or the system refused. Settings shows the status.
        }
    }

    /**
     * Background check on app start and launcher resume: never opens a
     * screen. Rebinds when access is granted but the listener isn't running.
     */
    fun ensureRunningSilently(context: Context) {
        if (DefangNotificationListenerService.isConnected) return
        if (isEnabled(context)) requestRebind(context)
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

    /**
     * The user asked for it (Settings tap or switch): rebind if granted but
     * not running, otherwise open the system screen. Returns true if no user
     * action is needed.
     */
    fun ensureEnabled(context: Context): Boolean {
        if (DefangNotificationListenerService.isConnected) return true
        if (isEnabled(context)) {
            requestRebind(context)
            return true
        }
        openNotificationAccessSettings(context)
        return false
    }
}
