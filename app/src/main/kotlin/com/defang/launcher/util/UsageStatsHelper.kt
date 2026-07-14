package com.defang.launcher.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Checks and requests the "Usage access" special permission
 * (`PACKAGE_USAGE_STATS`), needed by [com.defang.launcher.service.accessibility.DefangAccessibilityService]'s
 * usage-stats foreground poll — the fallback path for watched apps that never
 * deliver a `TYPE_WINDOW_STATE_CHANGED` accessibility event (e.g. apps with
 * anti-accessibility-service detection).
 *
 * Unlike accessibility/notification access, this permission has no secure-settings
 * string to write — it's an AppOps mode third-party apps can't grant themselves
 * even with WRITE_SECURE_SETTINGS, so there is no silent-enable path. The only
 * option is sending the user to the system settings screen.
 */
object UsageStatsHelper {

    fun isEnabled(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Sends the user to the settings screen if not already granted. Returns current state. */
    fun ensureEnabled(context: Context): Boolean {
        if (isEnabled(context)) return true
        openUsageAccessSettings(context)
        return false
    }
}
