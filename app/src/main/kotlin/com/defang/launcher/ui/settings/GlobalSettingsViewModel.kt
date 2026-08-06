package com.defang.launcher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.domain.model.HomeScreenMode
import com.defang.launcher.domain.model.QrScanMode
import com.defang.launcher.service.notification.BatchWindowScheduler
import com.defang.launcher.util.MathProblemGenerator
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

    // ── NFC tag unlock ────────────────────────────────────────────────────────
    val nfcUnlockEnabled: StateFlow<Boolean> = prefs.nfcUnlockEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    /** The registered tag UID, or null if none registered yet. */
    val nfcTagUid: StateFlow<String?> = prefs.nfcTagUid.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    fun setNfcUnlockEnabled(on: Boolean) {
        viewModelScope.launch {
            prefs.setNfcUnlockEnabled(on)
            // NFC, QR and math are mutually exclusive — one unlock method at a
            // time. Enabling NFC switches the others off (registrations are kept).
            if (on) {
                prefs.setQrUnlockEnabled(false)
                prefs.setMathUnlockEnabled(false)
            }
        }
    }

    fun forgetNfcTag() {
        viewModelScope.launch {
            prefs.clearNfcTagUid()
            // No tag means nothing to require — turn the gate back to slide.
            prefs.setNfcUnlockEnabled(false)
        }
    }

    // ── QR / barcode unlock ─────────────────────────────────────────────────────
    val qrUnlockEnabled: StateFlow<Boolean> = prefs.qrUnlockEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    /** The registered code's raw value, or null if none registered yet. */
    val qrValue: StateFlow<String?> = prefs.qrValue.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )
    val qrScanMode: StateFlow<QrScanMode> = prefs.qrScanMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, QrScanMode.AFTER_COUNTDOWN
    )

    fun setQrUnlockEnabled(on: Boolean) {
        viewModelScope.launch {
            prefs.setQrUnlockEnabled(on)
            // Mutually exclusive — enabling QR switches NFC and math off
            // (registrations are kept so the user can flip back).
            if (on) {
                prefs.setNfcUnlockEnabled(false)
                prefs.setMathUnlockEnabled(false)
            }
        }
    }

    fun setQrScanMode(mode: QrScanMode) {
        viewModelScope.launch { prefs.setQrScanMode(mode) }
    }

    fun forgetQrCode() {
        viewModelScope.launch {
            prefs.clearQrValue()
            // No code means nothing to require — turn the gate back to slide.
            prefs.setQrUnlockEnabled(false)
        }
    }

    // ── Math-problem unlock ─────────────────────────────────────────────────────
    val mathUnlockEnabled: StateFlow<Boolean> = prefs.mathUnlockEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    val mathDifficulty: StateFlow<Int> = prefs.mathDifficulty.stateIn(
        viewModelScope, SharingStarted.Eagerly, MathProblemGenerator.DEFAULT_LEVEL
    )

    fun setMathUnlockEnabled(on: Boolean) {
        viewModelScope.launch {
            prefs.setMathUnlockEnabled(on)
            // Mutually exclusive — enabling math switches NFC and QR off.
            if (on) {
                prefs.setNfcUnlockEnabled(false)
                prefs.setQrUnlockEnabled(false)
            }
        }
    }

    fun setMathDifficulty(level: Int) {
        viewModelScope.launch { prefs.setMathDifficulty(level) }
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

    // ── Work profile apps ─────────────────────────────────────────────────────
    val workProfileAppsEnabled: StateFlow<Boolean> = prefs.workProfileAppsEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    fun setWorkProfileAppsEnabled(on: Boolean) {
        viewModelScope.launch { prefs.setWorkProfileAppsEnabled(on) }
    }
}
