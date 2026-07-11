package com.defang.launcher.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defang.launcher.R
import com.defang.launcher.domain.model.HomeScreenMode
import com.defang.launcher.ui.onboarding.OnboardingActivity
import com.defang.launcher.ui.settings.apptier.AppTierScreen
import com.defang.launcher.ui.theme.DefangTheme
import com.defang.launcher.util.AccessibilityServiceHelper
import com.defang.launcher.util.NotificationListenerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

private enum class SettingsPage { Menu, Timing, Apps, Library, Tasks, Usage }

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val globalVm: GlobalSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialPage = if (intent.getBooleanExtra(EXTRA_OPEN_USAGE, false)) {
            SettingsPage.Usage
        } else {
            SettingsPage.Menu
        }
        setContent {
            DefangTheme {
                var page by remember { mutableStateOf(initialPage) }

                // Back from a sub-page returns to the menu, not out of settings
                BackHandler(enabled = page != SettingsPage.Menu) {
                    page = SettingsPage.Menu
                }

                when (page) {
                    SettingsPage.Menu -> {
                        val grayscaleOn by globalVm.grayscaleEnabled.collectAsStateWithLifecycle()
                        val sanitizeOn by globalVm.notificationSanitizeEnabled
                            .collectAsStateWithLifecycle()
                        val grayscaleSetupNeeded = remember {
                            checkSelfPermission(
                                android.Manifest.permission.WRITE_SECURE_SETTINGS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        val homeMode by globalVm.homeScreenMode.collectAsStateWithLifecycle()
                        val batchWindow1 by globalVm.batchWindow1.collectAsStateWithLifecycle()
                        val batchWindow2 by globalVm.batchWindow2.collectAsStateWithLifecycle()
                        val homeUsageOn by globalVm.homeUsageEnabled.collectAsStateWithLifecycle()
                        SettingsMenuScreen(
                            homeMode = homeMode,
                            onHomeModeChange = globalVm::setHomeScreenMode,
                            homeUsageOn = homeUsageOn,
                            onHomeUsageChange = globalVm::setHomeUsageEnabled,
                            grayscaleOn = grayscaleOn,
                            onGrayscaleChange = globalVm::setGrayscaleEnabled,
                            grayscaleSetupNeeded = grayscaleSetupNeeded,
                            sanitizeOn = sanitizeOn,
                            onSanitizeChange = globalVm::setNotificationSanitizeEnabled,
                            batchWindow1 = batchWindow1,
                            batchWindow2 = batchWindow2,
                            onBatchWindow1Change = globalVm::setBatchWindow1,
                            onBatchWindow2Change = globalVm::setBatchWindow2,
                            onTiming = { page = SettingsPage.Timing },
                            onApps = { page = SettingsPage.Apps },
                            onLibrary = { page = SettingsPage.Library },
                            onTasks = { page = SettingsPage.Tasks },
                            onUsage = { page = SettingsPage.Usage },
                            onReplayOnboarding = {
                                startActivity(Intent(this, OnboardingActivity::class.java))
                            },
                            onAccessibility = {
                                when {
                                    AccessibilityServiceHelper.isEnabled(this) -> {
                                        // Already on — say so and land the user on the
                                        // system row where it can be turned off.
                                        android.widget.Toast.makeText(
                                            this,
                                            R.string.accessibility_already_on,
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        AccessibilityServiceHelper.openAccessibilitySettings(this)
                                    }

                                    AccessibilityServiceHelper.tryEnableSelf(this) ->
                                        android.widget.Toast.makeText(
                                            this,
                                            R.string.accessibility_now_on,
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()

                                    else ->
                                        AccessibilityServiceHelper.openAccessibilitySettings(this)
                                }
                            },
                            onNotificationAccess = {
                                NotificationListenerHelper.ensureEnabled(this)
                            },
                        )
                    }

                    SettingsPage.Timing -> {
                        val gateDelay by globalVm.gateDelay.collectAsStateWithLifecycle()
                        val sessionLimit by globalVm.sessionLimit.collectAsStateWithLifecycle()
                        val cooldown by globalVm.cooldown.collectAsStateWithLifecycle()

                        TimingSettingsScreen(
                            gateDelay = gateDelay,
                            sessionLimit = sessionLimit,
                            cooldown = cooldown,
                            onGateDelayChange = globalVm::setGateDelay,
                            onSessionLimitChange = globalVm::setSessionLimit,
                            onCooldownChange = globalVm::setCooldown,
                            onBack = { page = SettingsPage.Menu },
                        )
                    }

                    SettingsPage.Apps -> AppTierSettingsScreen(
                        onBack = { page = SettingsPage.Menu },
                    )

                    SettingsPage.Library -> AwarenessLibraryScreen(
                        onBack = { page = SettingsPage.Menu },
                    )

                    SettingsPage.Tasks -> {
                        val tasks by globalVm.customTasks.collectAsStateWithLifecycle()
                        CustomTasksScreen(
                            tasks = tasks,
                            onAdd = globalVm::addCustomTask,
                            onRemove = globalVm::removeCustomTask,
                            onBack = { page = SettingsPage.Menu },
                        )
                    }

                    SettingsPage.Usage -> com.defang.launcher.ui.settings.usage.UsageReportScreen(
                        onBack = { page = SettingsPage.Menu },
                    )
                }
            }
        }
    }

    companion object {
        /** Intent extra: open directly on the usage report page (widget tap). */
        const val EXTRA_OPEN_USAGE = "open_usage"
    }
}

// ── Menu ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMenuScreen(
    homeMode: HomeScreenMode,
    onHomeModeChange: (HomeScreenMode) -> Unit,
    homeUsageOn: Boolean,
    onHomeUsageChange: (Boolean) -> Unit,
    grayscaleOn: Boolean,
    onGrayscaleChange: (Boolean) -> Unit,
    grayscaleSetupNeeded: Boolean,
    sanitizeOn: Boolean,
    onSanitizeChange: (Boolean) -> Unit,
    batchWindow1: Int,
    batchWindow2: Int,
    onBatchWindow1Change: (Int) -> Unit,
    onBatchWindow2Change: (Int) -> Unit,
    onTiming: () -> Unit,
    onApps: () -> Unit,
    onLibrary: () -> Unit,
    onTasks: () -> Unit,
    onUsage: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onAccessibility: () -> Unit,
    onNotificationAccess: () -> Unit,
) {
    // (title, body) of the currently open "Why?" explanation, or null
    var whyDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showHomeModeDialog by remember { mutableStateOf(false) }

    if (showHomeModeDialog) {
        AlertDialog(
            onDismissRequest = { showHomeModeDialog = false },
            title = { Text(stringResource(R.string.settings_home_mode)) },
            text = {
                Column {
                    HomeScreenMode.entries.forEach { mode ->
                        val (label, desc) = homeModeStrings(mode)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onHomeModeChange(mode)
                                    showHomeModeDialog = false
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = mode == homeMode,
                                onClick = {
                                    onHomeModeChange(mode)
                                    showHomeModeDialog = false
                                },
                            )
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHomeModeDialog = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    whyDialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { whyDialog = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = { whyDialog = null }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    val grayscaleWhy = stringResource(R.string.settings_grayscale_why_title) to
        stringResource(R.string.settings_grayscale_why_body)
    val sanitizeWhy = stringResource(R.string.settings_sanitize_why_title) to
        stringResource(R.string.settings_sanitize_why_body)
    val packageName = androidx.compose.ui.platform.LocalContext.current.packageName
    val grayscaleSetup = stringResource(R.string.settings_grayscale_setup_title) to
        stringResource(R.string.settings_grayscale_setup_body, packageName)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_timing_title)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_timing_desc))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTiming() },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.tier_config_title)) },
                supportingContent = { Text(stringResource(R.string.tier_config_subtitle)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onApps() },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_home_mode)) },
                supportingContent = { Text(homeModeStrings(homeMode).first) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showHomeModeDialog = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_home_usage)) },
                supportingContent = { Text(stringResource(R.string.settings_home_usage_desc)) },
                trailingContent = {
                    Switch(checked = homeUsageOn, onCheckedChange = onHomeUsageChange)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_tasks_title)) },
                supportingContent = { Text(stringResource(R.string.settings_tasks_desc)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTasks() },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_grayscale)) },
                supportingContent = {
                    Text(
                        if (grayscaleSetupNeeded)
                            stringResource(R.string.settings_grayscale_setup)
                        else
                            stringResource(R.string.settings_grayscale_desc)
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WhyButton { whyDialog = grayscaleWhy }
                        if (!grayscaleSetupNeeded) {
                            Switch(checked = grayscaleOn, onCheckedChange = onGrayscaleChange)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (grayscaleSetupNeeded)
                            Modifier.clickable { whyDialog = grayscaleSetup }
                        else Modifier
                    ),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_sanitize)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_sanitize_desc))
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WhyButton { whyDialog = sanitizeWhy }
                        Switch(checked = sanitizeOn, onCheckedChange = onSanitizeChange)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (sanitizeOn) {
                BatchWindowRow(
                    label = stringResource(R.string.settings_batch_window_1),
                    minutesOfDay = batchWindow1,
                    onChange = onBatchWindow1Change,
                )
                BatchWindowRow(
                    label = stringResource(R.string.settings_batch_window_2),
                    minutesOfDay = batchWindow2,
                    onChange = onBatchWindow2Change,
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notification_access)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_notification_access_desc))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNotificationAccess() },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.usage_report_title)) },
                supportingContent = { Text(stringResource(R.string.usage_report_desc)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUsage() },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_awareness_library)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_awareness_library_desc))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLibrary() },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_onboarding_replay)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReplayOnboarding() },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.perm_accessibility_title)) },
                supportingContent = { Text(stringResource(R.string.perm_accessibility_body)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAccessibility() },
            )
        }
    }
}

// ── Timing sliders ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimingSettingsScreen(
    gateDelay: Int,
    sessionLimit: Int,
    cooldown: Int,
    onGateDelayChange: (Int) -> Unit,
    onSessionLimitChange: (Int) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_timing_title)) },
                navigationIcon = { BackIcon(onBack) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_timing_scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            )

            SettingSlider(
                label = stringResource(R.string.settings_gate_delay),
                value = gateDelay,
                valueLabel = stringResource(R.string.unit_seconds, gateDelay),
                valueRange = 3f..30f,
                steps = 26,  // (30-3)/1 - 1 = 26 steps for step=1
                onValueChange = { onGateDelayChange(it.roundToInt().coerceIn(3, 30)) },
            )

            SettingSlider(
                label = stringResource(R.string.settings_session_limit),
                value = sessionLimit,
                valueLabel = stringResource(R.string.unit_minutes, sessionLimit),
                valueRange = 5f..60f,
                steps = 10,  // (60-5)/5 - 1 = 10 steps for step=5
                onValueChange = { onSessionLimitChange(it.roundToInt()) },
            )

            SettingSlider(
                label = stringResource(R.string.settings_cooldown),
                value = cooldown,
                valueLabel = stringResource(R.string.unit_minutes, cooldown),
                valueRange = 10f..120f,
                steps = 10,  // (120-10)/10 - 1 = 10 steps for step=10
                onValueChange = { onCooldownChange(it.roundToInt() / 10 * 10) },
            )
        }
    }
}

// ── App tiers ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTierSettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tier_config_title)) },
                navigationIcon = { BackIcon(onBack) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AppTierScreen()
        }
    }
}

// ── Awareness library ─────────────────────────────────────────────────────────

// One library entry: the tidbit and, for the awareness tracks, its citation.
private data class LibraryEntry(val text: String, val source: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AwarenessLibraryScreen(onBack: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val sections = remember {
        fun track(tidbitsRes: Int, sourcesRes: Int): List<LibraryEntry> {
            val tidbits = resources.getStringArray(tidbitsRes)
            val sources = resources.getStringArray(sourcesRes)
            return tidbits.mapIndexed { i, text ->
                LibraryEntry(text, sources.getOrNull(i))
            }
        }
        listOf(
            R.string.library_track_general to
                track(R.array.tidbits_general, R.array.tidbits_general_sources),
            R.string.library_track_social to
                track(R.array.tidbits_social, R.array.tidbits_social_sources),
            R.string.library_track_adult to
                track(R.array.tidbits_adult, R.array.tidbits_adult_sources),
            R.string.library_tasks_header to arrayOf(
                R.array.offline_prompts_movement,
                R.array.offline_prompts_space,
                R.array.offline_prompts_sensory,
                R.array.offline_prompts_social,
                R.array.offline_prompts_creative,
            ).flatMap { arrayRes ->
                resources.getStringArray(arrayRes).map { LibraryEntry(it, source = null) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_awareness_library)) },
                navigationIcon = { BackIcon(onBack) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            sections.forEach { (headerRes, entries) ->
                item {
                    Text(
                        text = stringResource(headerRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = 16.dp, end = 16.dp, top = 16.dp
                        ),
                    )
                }
                items(entries) { entry ->
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = entry.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            entry.source?.let { source ->
                                Text(
                                    text = source,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Custom tasks ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomTasksScreen(
    tasks: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_tasks_title)) },
                navigationIcon = { BackIcon(onBack) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stringResource(R.string.settings_tasks_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.tasks_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onAdd(input)
                        input = ""
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Text(stringResource(R.string.tasks_add))
                }
            }

            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.tasks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tasks) { task ->
                        ListItem(
                            headlineContent = { Text(task) },
                            trailingContent = {
                                IconButton(onClick = { onRemove(task) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.tasks_delete),
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ── Shared pieces ─────────────────────────────────────────────────────────────

/** (label, description) for a home screen mode option. */
@Composable
private fun homeModeStrings(mode: HomeScreenMode): Pair<String, String> = when (mode) {
    HomeScreenMode.EMPTY ->
        stringResource(R.string.home_mode_empty) to
            stringResource(R.string.home_mode_empty_desc)
    HomeScreenMode.TIDBIT ->
        stringResource(R.string.home_mode_tidbit) to
            stringResource(R.string.home_mode_tidbit_desc)
    HomeScreenMode.CLOCK_AND_TIDBIT ->
        stringResource(R.string.home_mode_clock) to
            stringResource(R.string.home_mode_clock_desc)
}

/**
 * One notification delivery window. Tap to pick a time; the clear icon
 * turns the window off (-1). Without any window set, suppressed
 * notifications are summarized immediately instead of batched.
 */
@Composable
private fun BatchWindowRow(
    label: String,
    minutesOfDay: Int,
    onChange: (Int) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSet = minutesOfDay >= 0
    val timeLabel = if (isSet) {
        "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)
    } else {
        stringResource(R.string.batch_window_off)
    }

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(timeLabel) },
        trailingContent = {
            if (isSet) {
                IconButton(onClick = { onChange(-1) }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.batch_window_clear),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val initial = if (isSet) minutesOfDay else 12 * 60
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute -> onChange(hour * 60 + minute) },
                    initial / 60,
                    initial % 60,
                    true,
                ).show()
            },
    )
}

@Composable
private fun WhyButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.settings_why),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun BackIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
        )
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
