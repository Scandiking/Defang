package com.defang.launcher.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.defang.launcher.ui.onboarding.screens.SetupScreen
import com.defang.launcher.ui.onboarding.screens.TheLoopScreen
import com.defang.launcher.ui.onboarding.screens.WhatItIsNotScreen
import com.defang.launcher.ui.onboarding.screens.WhatItIsScreen
import com.defang.launcher.ui.onboarding.screens.WhySmallTasksScreen
import com.defang.launcher.ui.settings.SettingsActivity
import com.defang.launcher.ui.theme.DefangTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DefangTheme {
                val screen by viewModel.currentScreen.collectAsState()

                when (screen) {
                    0 -> WhatItIsScreen(
                        onNext = { viewModel.next() },
                        onSkip = { viewModel.skip() },
                    )
                    1 -> WhatItIsNotScreen(
                        onNext = { viewModel.next() },
                        onSkip = { viewModel.skip() },
                    )
                    2 -> TheLoopScreen(
                        onNext = { viewModel.next() },
                        onSkip = { viewModel.skip() },
                    )
                    3 -> WhySmallTasksScreen(
                        onNext = { viewModel.next() },
                        onSkip = { viewModel.skip() },
                    )
                    4 -> SetupScreen(
                        onContinue = {
                            viewModel.complete()
                            startActivity(Intent(this, SettingsActivity::class.java))
                            finish()
                        },
                    )
                }
            }
        }
    }
}
