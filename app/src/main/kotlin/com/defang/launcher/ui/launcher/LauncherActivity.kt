package com.defang.launcher.ui.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.defang.launcher.ui.onboarding.OnboardingActivity
import com.defang.launcher.ui.settings.SettingsActivity
import com.defang.launcher.ui.theme.DefangTheme
import com.defang.launcher.util.AccessibilityServiceHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    // true = app drawer is visible, false = home screen is visible
    private var showDrawer by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DefangTheme {
                val state by viewModel.uiState.collectAsState()

                if (state.needsOnboarding) {
                    startActivity(Intent(this, OnboardingActivity::class.java))
                }

                if (showDrawer) {
                    LauncherScreen(
                        apps = viewModel.filteredApps,
                        query = state.query,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onAppTap = { pkg -> launchApp(pkg) },
                        onClose = { showDrawer = false },
                    )
                } else {
                    val homeMode by viewModel.homeMode.collectAsState()
                    val homeUsage by viewModel.homeUsage.collectAsState()
                    HomeScreen(
                        tidbit = state.homeTidbit,
                        mode = homeMode,
                        usage = homeUsage,
                        onUsageTap = {
                            startActivity(
                                Intent(this, SettingsActivity::class.java)
                                    .putExtra(SettingsActivity.EXTRA_OPEN_USAGE, true)
                            )
                        },
                        onAppsTap = { showDrawer = true },
                    )
                }
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            )
        }

        // Needed to post the sanitized notification summaries on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }

    /** Home button press while already in the launcher — return to home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            showDrawer = false
            viewModel.onQueryChange("") // clear any active search
        }
    }

    override fun onResume() {
        super.onResume()
        // Self-enables silently if WRITE_SECURE_SETTINGS was granted via adb;
        // otherwise opens accessibility settings with our row highlighted.
        AccessibilityServiceHelper.ensureEnabled(this)
        viewModel.refreshHomeTidbit()
        viewModel.refreshHomeUsage()
    }

    private fun launchApp(packageName: String) {
        // Our own drawer entry opens settings — the launcher itself is already open
        if (packageName == this.packageName) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
