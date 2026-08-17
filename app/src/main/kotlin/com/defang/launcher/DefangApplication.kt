package com.defang.launcher

import android.app.Application
import com.defang.launcher.data.repository.SessionRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DefangApplication : Application() {

    @Inject lateinit var sessionRepo: SessionRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Prune usage history down to the configured retention level once per
        // process start — no WorkManager job needed for a launcher that's
        // already resident.
        appScope.launch { sessionRepo.applyRetention() }
    }
}
