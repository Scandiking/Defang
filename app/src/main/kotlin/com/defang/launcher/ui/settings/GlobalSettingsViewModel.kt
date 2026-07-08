package com.defang.launcher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.repository.AppConfigRepository
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
