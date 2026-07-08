package com.defang.launcher.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PreferencesDataStore,
) : ViewModel() {

    private val _currentScreen = MutableStateFlow(0)
    val currentScreen: StateFlow<Int> = _currentScreen

    val totalScreens = 5

    fun next() {
        if (_currentScreen.value < totalScreens - 1) {
            _currentScreen.value++
        }
    }

    fun skip() {
        _currentScreen.value = totalScreens - 1
    }

    fun complete() {
        viewModelScope.launch {
            prefs.setOnboardingDone()
        }
    }
}
