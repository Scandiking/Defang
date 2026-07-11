package com.defang.launcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.defang.launcher.service.notification.BatchWindowScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restores any state that needs to survive reboots.
 * Cool-down end times are stored in Room with epoch millis, so they persist automatically.
 * Notification batch alarms do not survive reboots — re-register them here.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var batchWindowScheduler: BatchWindowScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                batchWindowScheduler.rescheduleFromPrefs()
            } finally {
                pending.finish()
            }
        }
    }
}
