package com.defang.launcher

import android.app.Application
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.repository.SessionRepository
import com.defang.launcher.service.notification.BatchWindowScheduler
import com.defang.launcher.util.NotificationListenerHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DefangApplication : Application() {

    @Inject lateinit var sessionRepo: SessionRepository
    @Inject lateinit var prefs: PreferencesDataStore
    @Inject lateinit var batchWindowScheduler: BatchWindowScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Prune usage history down to the configured retention level once per
        // process start — no WorkManager job needed for a launcher that's
        // already resident.
        appScope.launch { sessionRepo.applyRetention() }
        appScope.launch {
            // Re-arm the delivery windows: covers updates from builds that
            // used inexact repeating alarms, and any alarm the OS dropped.
            batchWindowScheduler.rescheduleFromPrefs()
            // Notification access can be lost (or never given) without the
            // user noticing; get the listener running again where we can.
            if (prefs.notificationSanitizeEnabled.first()) {
                NotificationListenerHelper.ensureRunningSilently(this@DefangApplication)
            }
        }
    }
}
