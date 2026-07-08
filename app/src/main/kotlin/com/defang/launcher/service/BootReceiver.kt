package com.defang.launcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores any state that needs to survive reboots.
 * Cool-down end times are stored in Room with epoch millis, so they persist automatically.
 * This receiver exists as a hook for future Phase 2 work (e.g. re-scheduling notification batching).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Phase 2: restart notification batch AlarmManager jobs here.
    }
}
