package com.defang.launcher.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.defang.launcher.ui.onboarding.OnboardingActivity
import com.defang.launcher.ui.settings.apptier.AppTierScreen
import com.defang.launcher.ui.theme.DefangTheme
import com.defang.launcher.util.AccessibilityServiceHelper
import com.defang.launcher.util.NotificationListenerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

private enum class SettingsPage { Menu, Timing, Apps }

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val globalVm: GlobalSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DefangTheme {
                var page by remember { mutableStateOf(SettingsPage.Menu) }

                // Back from a sub-page returns to the menu, not out of settings
                BackHandler(enabled = page != SettingsPage.Menu) {
                    page = SettingsPage.Menu
                }

                when (page) {
                    SettingsPage.Menu -> {
                        val grayscaleOn by globalVm.grayscaleEnabled.collectAsStateWithLifecycle()
                        val sanitizeOn by globalVm.notificationSanitizeEnabled
                            .collectAsStateWithLifecycle()
                        SettingsMenuScreen(
                            grayscaleOn = grayscaleOn,
                            onGrayscaleChange = globalVm::setGrayscaleEnabled,
                            sanitizeOn = sanitizeOn,
                            onSanitizeChange = globalVm::setNotificationSanitizeEnabled,
                            onTiming = { page = SettingsPage.Timing },
                            onApps = { page = SettingsPage.Apps },
                            onReplayOnboarding = {
                                startActivity(Intent(this, OnboardingActivity::class.java))
                            },
                            onAccessibility = {
                                if (!AccessibilityServiceHelper.tryEnableSelf(this)) {
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
                }
            }
        }
    }
}

// ── Menu ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMenuScreen(
    grayscaleOn: Boolean,
    onGrayscaleChange: (Boolean) -> Unit,
    sanitizeOn: Boolean,
    onSanitizeChange: (Boolean) -> Unit,
    onTiming: () -> Unit,
    onApps: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onAccessibility: () -> Unit,
    onNotificationAccess: () -> Unit,
) {
    // (title, body) of the currently open "Hvorfor?" explanation, or null
    var whyDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    whyDialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { whyDialog = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = { whyDialog = null }) { Text("OK") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ListItem(
                headlineContent = { Text("Standardtider") },
                supportingContent = {
                    Text("Ventetid ved åpning, maks økt-lengde og nedkjølingstid")
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
                headlineContent = { Text("Gråtoner") },
                supportingContent = {
                    Text("Overvåkede apper vises i svart-hvitt under økten")
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WhyButton {
                            whyDialog = "Hvorfor gråtoner?" to
                                "Fargene i sosiale apper er ikke tilfeldige. Rød på varsler " +
                                "og mettede farger i feeden er valgt fordi hjernen tolker dem " +
                                "som belønning. I svart-hvitt er innholdet det samme, men " +
                                "belønningssignalet er borte — suget etter å bla videre svekkes " +
                                "målbart. Kamera, kart og resten av telefonen beholder farger."
                        }
                        Switch(checked = grayscaleOn, onCheckedChange = onGrayscaleChange)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Nøytrale varsler") },
                supportingContent = {
                    Text("Varsler fra overvåkede apper dempes: grå, stille, uten forhåndsvisning")
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WhyButton {
                            whyDialog = "Hvorfor nøytrale varsler?" to
                                "Varsler er plattformenes sterkeste verktøy for å dra deg " +
                                "tilbake: rød merkevarefarge, lyd, vibrasjon og hastverksspråk " +
                                "(«X venter på deg») — ofte generert masseutsendelse forkledd " +
                                "som personlig melding. Defang bytter dem ut med én grå, stille " +
                                "oppsummering per app, uten forhåndsvisning av innholdet. Du får " +
                                "vite at noe venter — uten agnet. Å trykke på varselet åpner " +
                                "appen gjennom vanlig inngang, ikke dyplenken varselet ville " +
                                "sendt deg til."
                        }
                        Switch(checked = sanitizeOn, onCheckedChange = onSanitizeChange)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Varseltilgang") },
                supportingContent = {
                    Text("Kreves for nøytrale varsler — gi Defang tilgang til varsler")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNotificationAccess() },
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
                title = { Text("Standardtider") },
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
                text = "Gjelder alle overvåkede apper med mindre du endrer dem enkeltvis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            )

            SettingSlider(
                label = "Ventetid ved åpning",
                value = gateDelay,
                valueLabel = "$gateDelay sek",
                valueRange = 3f..30f,
                steps = 26,  // (30-3)/1 - 1 = 26 steps for step=1
                onValueChange = { onGateDelayChange(it.roundToInt().coerceIn(3, 30)) },
            )

            SettingSlider(
                label = "Maks økt-lengde",
                value = sessionLimit,
                valueLabel = "$sessionLimit min",
                valueRange = 5f..60f,
                steps = 10,  // (60-5)/5 - 1 = 10 steps for step=5
                onValueChange = { onSessionLimitChange(it.roundToInt()) },
            )

            SettingSlider(
                label = "Nedkjølingstid",
                value = cooldown,
                valueLabel = "$cooldown min",
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

// ── Shared pieces ─────────────────────────────────────────────────────────────

@Composable
private fun WhyButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Hvorfor?",
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun BackIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Tilbake",
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
