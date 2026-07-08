package com.defang.launcher.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.defang.launcher.R
import com.defang.launcher.ui.onboarding.OnboardingActivity
import com.defang.launcher.ui.settings.apptier.AppTierScreen
import com.defang.launcher.ui.theme.DefangTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DefangTheme {
                SettingsScreen(
                    onWatchedApps = { /* Already shown below inline via AppTierScreen */ },
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
    onWatchedApps: () -> Unit,
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
            Text(
                text = stringResource(R.string.tier_config_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            // Inline app tier config — the primary settings action
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
