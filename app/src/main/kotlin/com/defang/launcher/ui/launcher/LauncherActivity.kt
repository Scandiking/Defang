package com.defang.launcher.ui.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.defang.launcher.ui.onboarding.OnboardingActivity
import com.defang.launcher.ui.settings.SettingsActivity
import com.defang.launcher.ui.theme.DefangTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DefangTheme {
                val state by viewModel.uiState.collectAsState()

                if (state.needsOnboarding) {
                    startActivity(Intent(this, OnboardingActivity::class.java))
                    // Don't finish — LauncherActivity remains the home
                }

                LauncherScreen(
                    apps = viewModel.filteredApps,
                    query = state.query,
                    onQueryChange = { viewModel.onQueryChange(it) },
                    onAppTap = { pkg -> launchApp(pkg) },
                    onSettingsTap = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                )
            }
        }

        // Request SYSTEM_ALERT_WINDOW on first run if not granted
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Prompt for accessibility service every time the launcher comes to foreground
        // until the user grants it. Without it, the intent gate never fires.
        if (!isAccessibilityServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        // Check if our specific service component is in the enabled list
        val component = "$packageName/com.defang.launcher.service.accessibility.DefangAccessibilityService"
        return enabledServices.split(":").any { it.equals(component, ignoreCase = true) }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        // DefangAccessibilityService intercepts TYPE_WINDOW_STATE_CHANGED
        // and shows the intent gate for watched apps.
    }
}
