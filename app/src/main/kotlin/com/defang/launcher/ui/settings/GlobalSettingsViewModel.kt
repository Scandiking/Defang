package com.defang.launcher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.domain.model.HomeScreenMode
import com.defang.launcher.service.notification.BatchWindowScheduler
import com.defang.launcher.util.GrayscaleController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlobalSettingsViewModel @Inject constructor(
    private val prefs: PreferencesDataStore,
    private val appConfigRepo: AppConfigRepository,
    private val grayscale: GrayscaleController,
    private val batchWindowScheduler: BatchWindowScheduler,
) : ViewModel() {

    val gateDelay: StateFlow<Int> = prefs.gateDelaySeconds.stateIn(
        viewModelScope, SharingStarted.Eagerly, 8
    )
    val sessionLimit: StateFlow<Int> = prefs.sessionLimitMinutes.stateIn(
        viewModelScope, SharingStarted.Eagerly, 15
    )
    val cooldown: StateFlow<Int> = prefs.cooldownMinutes.stateIn(
        viewModelScope, SharingStarted.Eagerly, 30
    )
    val grayscaleEnabled: StateFlow<Boolean> = prefs.grayscaleEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )

    fun setGrayscaleEnabled(on: Boolean) {
        viewModelScope.launch {
            prefs.setGrayscaleEnabled(on)
            // Turning it off mid-session should restore color immediately
            if (!on) grayscale.disable()
        }
    }

    val notificationSanitizeEnabled: StateFlow<Boolean> =
        prefs.notificationSanitizeEnabled.stateIn(
            viewModelScope, SharingStarted.Eagerly, true
        )

    fun setNotificationSanitizeEnabled(on: Boolean) {
        viewModelScope.launch { prefs.setNotificationSanitizeEnabled(on) }
    }

    // Delivery windows in minutes-since-midnight; -1 = window off.
    val batchWindow1: StateFlow<Int> = prefs.batchWindow1.stateIn(
        viewModelScope, SharingStarted.Eagerly, -1
    )
    val batchWindow2: StateFlow<Int> = prefs.batchWindow2.stateIn(
        viewModelScope, SharingStarted.Eagerly, -1
    )

    fun setBatchWindow1(minutesOfDay: Int) {
        viewModelScope.launch {
            prefs.setBatchWindow1(minutesOfDay)
            batchWindowScheduler.rescheduleFromPrefs()
        }
    }

    fun setBatchWindow2(minutesOfDay: Int) {
        viewModelScope.launch {
            prefs.setBatchWindow2(minutesOfDay)
            batchWindowScheduler.rescheduleFromPrefs()
        }
    }

    val homeUsageEnabled: StateFlow<Boolean> = prefs.homeUsageEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    fun setHomeUsageEnabled(on: Boolean) {
        viewModelScope.launch { prefs.setHomeUsageEnabled(on) }
    }

    val homeScreenMode: StateFlow<HomeScreenMode> = prefs.homeScreenMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, HomeScreenMode.CLOCK_AND_TIDBIT
    )

    fun setHomeScreenMode(mode: HomeScreenMode) {
        viewModelScope.launch { prefs.setHomeScreenMode(mode) }
    }

    val customTasks: StateFlow<List<String>> = prefs.customTasks.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    fun addCustomTask(task: String) {
        viewModelScope.launch { prefs.addCustomTask(task) }
    }

    fun removeCustomTask(task: String) {
        viewModelScope.launch { prefs.removeCustomTask(task) }
    }

    fun setGateDelay(seconds: Int) {
        viewModelScope.launch {
            prefs.setGateDelay(seconds)
            appConfigRepo.applyDefaultsToAllWatched(seconds, sessionLimit.value, cooldown.value)
        }
    }

    fun setSessionLimit(minutes: Int) {
        viewModelScope.launch {
            prefs.setSessionLimit(minutes)
            appConfigRepo.applyDefaultsToAllWatched(gateDelay.value, minutes, cooldown.value)
        }
    }

    fun setCooldown(minutes: Int) {
        viewModelScope.launch {
            prefs.setCooldown(minutes)
            appConfigRepo.applyDefaultsToAllWatched(gateDelay.value, sessionLimit.value, minutes)
        }
    }
}
