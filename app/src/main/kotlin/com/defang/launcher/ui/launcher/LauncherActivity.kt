package com.defang.launcher.ui.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.defang.launcher.R
import com.defang.launcher.ui.onboarding.OnboardingActivity
import com.defang.launcher.ui.settings.SettingsActivity
import com.defang.launcher.ui.theme.DefangTheme
import com.defang.launcher.util.AccessibilityServiceHelper
import com.defang.launcher.util.UsageStatsHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    // true = app drawer is visible, false = home screen is visible
    private var showDrawer by mutableStateOf(false)

    // Keeps the drawer live: package add/remove fires an instant re-scan even
    // while the drawer is open. Manifest-declared receivers don't get these
    // implicit broadcasts on Oreo+, so we register at runtime.
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DefangTheme {
                val state by viewModel.uiState.collectAsState()

                // LaunchedEffect, not a bare call: startActivity during
                // composition re-fires on every recomposition and can stack
                // several OnboardingActivity instances.
                androidx.compose.runtime.LaunchedEffect(state.needsOnboarding) {
                    if (state.needsOnboarding) {
                        startActivity(Intent(this@LauncherActivity, OnboardingActivity::class.java))
                    }
                }

                if (state.showLockdownWarning) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { viewModel.dismissLockdownWarning() },
                        title = {
                            androidx.compose.material3.Text(
                                stringResource(R.string.lockdown_title)
                            )
                        },
                        text = {
                            androidx.compose.material3.Text(
                                stringResource(R.string.lockdown_body)
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.dismissLockdownWarning() },
                            ) {
                                androidx.compose.material3.Text(
                                    stringResource(R.string.lockdown_dismiss)
                                )
                            }
                        },
                    )
                }

                if (showDrawer) {
                    val hiddenPackages by viewModel.hiddenPackages.collectAsState()
                    LauncherScreen(
                        apps = viewModel.filteredApps(hiddenPackages),
                        query = state.query,
                        ownPackageName = packageName,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onAppTap = { pkg -> launchApp(pkg) },
                        onAppInfo = { pkg -> openAppInfo(pkg) },
                        onUninstall = { pkg -> uninstallApp(pkg) },
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
                        onAppsTap = {
                            // Re-scan on open so freshly installed/removed apps show
                            // up without waiting for a process restart.
                            viewModel.refresh()
                            showDrawer = true
                        },
                    )
                }
            }
        }

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            packageFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

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
        // Powers the usage-stats foreground poll fallback (apps that evade the
        // accessibility event entirely). No silent-enable path exists for this
        // permission — sends to settings once, then leaves it alone.
        UsageStatsHelper.ensureEnabled(this)
        viewModel.refreshHomeTidbit()
        viewModel.refreshHomeUsage()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageChangeReceiver)
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

    /** Long-press → App info: the system's per-app details screen. */
    private fun openAppInfo(packageName: String) {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Long-press → Uninstall: fires the standard uninstaller, which shows its
     *  own confirmation dialog. The package-change receiver refreshes the drawer
     *  once the removal completes. */
    private fun uninstallApp(packageName: String) {
        startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        )
    }
}
