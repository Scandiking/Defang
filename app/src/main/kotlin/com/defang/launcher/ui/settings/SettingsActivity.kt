package com.defang.launcher.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defang.launcher.R
import com.defang.launcher.ui.onboarding.OnboardingActivity
import com.defang.launcher.ui.settings.apptier.AppTierScreen
import com.defang.launcher.ui.theme.DefangTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val globalVm: GlobalSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DefangTheme {
                val gateDelay by globalVm.gateDelay.collectAsStateWithLifecycle()
                val sessionLimit by globalVm.sessionLimit.collectAsStateWithLifecycle()
                val cooldown by globalVm.cooldown.collectAsStateWithLifecycle()

                SettingsScreen(
                    gateDelay = gateDelay,
                    sessionLimit = sessionLimit,
                    cooldown = cooldown,
                    onGateDelayChange = globalVm::setGateDelay,
                    onSessionLimitChange = globalVm::setSessionLimit,
                    onCooldownChange = globalVm::setCooldown,
                    onReplayOnboarding = {
                        startActivity(Intent(this, OnboardingActivity::class.java))
                    },
                    onAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    gateDelay: Int,
    sessionLimit: Int,
    cooldown: Int,
    onGateDelayChange: (Int) -> Unit,
    onSessionLimitChange: (Int) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onReplayOnboarding: () -> Unit,
    onAccessibility: () -> Unit,
) {
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
            // ── Global timing defaults ────────────────────────────────────────
            Text(
                text = "Standardtider",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            Text(
                text = "Gjelder alle overvåkede apper med mindre du endrer dem enkeltvis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── App setup ────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.tier_config_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            AppTierScreen()

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
