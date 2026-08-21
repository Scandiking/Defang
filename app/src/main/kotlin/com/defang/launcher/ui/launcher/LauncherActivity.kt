package com.defang.launcher.ui.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.defang.launcher.R
import com.defang.launcher.domain.model.HomeScreenMode
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

                state.renamePrompt?.let { candidate ->
                    var renameText by remember(candidate.packageName) { mutableStateOf("") }
                    val installSource by androidx.compose.runtime.produceState(
                        initialValue = "",
                        candidate.packageName,
                    ) {
                        value = viewModel.installSourceLabel(candidate.packageName)
                    }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { viewModel.dismissRenamePrompt(candidate.packageName) },
                        title = {
                            androidx.compose.material3.Text(
                                stringResource(R.string.duplicate_name_title)
                            )
                        },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                androidx.compose.material3.Text(
                                    stringResource(R.string.duplicate_name_body, candidate.label)
                                )
                                androidx.compose.foundation.layout.Spacer(
                                    modifier = androidx.compose.ui.Modifier.height(8.dp)
                                )
                                if (installSource.isNotEmpty()) {
                                    androidx.compose.material3.Text(
                                        text = stringResource(
                                            R.string.rename_dialog_source,
                                            installSource,
                                            candidate.packageName,
                                        ),
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                                            .copy(alpha = 0.6f),
                                    )
                                }
                                androidx.compose.foundation.layout.Spacer(
                                    modifier = androidx.compose.ui.Modifier.height(12.dp)
                                )
                                androidx.compose.material3.OutlinedTextField(
                                    value = renameText,
                                    onValueChange = { renameText = it },
                                    placeholder = {
                                        androidx.compose.material3.Text(candidate.rawLabel)
                                    },
                                    singleLine = true,
                                )
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.renameApp(candidate.packageName, renameText) },
                            ) {
                                androidx.compose.material3.Text(
                                    stringResource(R.string.duplicate_name_rename)
                                )
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.dismissRenamePrompt(candidate.packageName) },
                            ) {
                                androidx.compose.material3.Text(
                                    stringResource(R.string.duplicate_name_not_now)
                                )
                            }
                        },
                    )
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

                val homeMode by viewModel.homeMode.collectAsState()

                if (showDrawer) {
                    val hiddenPackages by viewModel.hiddenPackages.collectAsState()
                    val letterRailScale by viewModel.letterRailScale.collectAsState()
                    val letterRailXOffsetDp by viewModel.letterRailXOffsetDp.collectAsState()
                    LauncherScreen(
                        apps = viewModel.filteredApps(hiddenPackages),
                        query = state.query,
                        ownPackageName = packageName,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onAppTap = { app -> launchApp(app) },
                        onAppInfo = { app -> openAppInfo(app) },
                        onUninstall = { app -> uninstallApp(app) },
                        onRename = { pkg, newLabel -> viewModel.renameApp(pkg, newLabel) },
                        getInstallSource = { pkg -> viewModel.installSourceLabel(pkg) },
                        onClose = { showDrawer = false },
                        searchOnOpen = homeMode == HomeScreenMode.SEARCH_ONLY,
                        letterRailScale = letterRailScale,
                        letterRailXOffsetDp = letterRailXOffsetDp,
                    )
                } else {
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

    private fun launchApp(app: AppInfo) {
        // Our own drawer entry opens settings — the launcher itself is already open.
        // Scoped to our own profile: a work-profile app can share our package name
        // (e.g. if Defang itself were installed there) without being us.
        if (app.packageName == this.packageName && app.userHandle == Process.myUserHandle()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        // Work profile (or any other secondary user profile) app: same-package
        // lookups via PackageManager only ever resolve the calling profile, so
        // launching here needs LauncherApps with the target profile's handle.
        if (app.userHandle != Process.myUserHandle()) {
            val launcherApps = getSystemService(LauncherApps::class.java) ?: return
            try {
                val component = launcherApps.getActivityList(app.packageName, app.userHandle)
                    .firstOrNull()?.componentName ?: return
                launcherApps.startMainActivity(component, app.userHandle, null, null)
            } catch (e: SecurityException) {
                // Profile paused/removed between drawer refresh and this tap.
            }
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /**
     * Long-press → App info: the system's per-app details screen.
     * For a work app, the generic Settings intent + EXTRA_USER silently falls
     * back to showing the *personal* copy on some Android builds (verified by
     * comparing on-disk data sizes — the "EXTRA_USER" contract isn't honored
     * by every Settings implementation). LauncherApps.startAppDetailsActivity
     * is the API actually built for this — cross-profile app details from a
     * launcher, no extra permission needed beyond holding the launcher role —
     * and it reliably lands on the right profile, confirmed on-device via the
     * work-profile briefcase badge and matching storage figures.
     */
    private fun openAppInfo(app: AppInfo) {
        if (app.userHandle != Process.myUserHandle()) {
            val launcherApps = getSystemService(LauncherApps::class.java) ?: return
            try {
                val component = launcherApps.getActivityList(app.packageName, app.userHandle)
                    .firstOrNull()?.componentName ?: return
                launcherApps.startAppDetailsActivity(component, app.userHandle, null, null)
            } catch (e: SecurityException) {
                // Profile paused/removed between drawer refresh and this tap.
            }
            return
        }
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${app.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Long-press → Uninstall: fires the standard uninstaller (scoped via
     *  EXTRA_USER for a work app), which shows its own confirmation dialog.
     *  The package-change receiver refreshes the drawer once it completes. */
    private fun uninstallApp(app: AppInfo) {
        startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                .putExtra(Intent.EXTRA_USER, app.userHandle)
                // Without this the uninstaller never launches from our singleTask
                // home activity — the menu just closes and nothing happens.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
