package com.defang.launcher.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires at each delivery window: posts one summary per app with the count of
 * notifications suppressed since the user last cleared that app's summary.
 */
@AndroidEntryPoint
class BatchDeliveryReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesDataStore
    @Inject lateinit var summaryPoster: NotificationSummaryPoster

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                prefs.suppressedCounts.first().forEach { (pkg, count) ->
                    summaryPoster.postSummary(pkg, count)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
