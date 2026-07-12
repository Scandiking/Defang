package com.defang.launcher.ui.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.defang.launcher.R

/**
 * Shared scaffold used by all five onboarding screens.
 */
@Composable
fun OnboardingPage(
    heading: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    showSkip: Boolean = true,
) {
    // Surface, not raw Column: the window background is hardcoded black
    // (themes.xml), so without this the light color scheme paints dark
    // text on a black window.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
    ) {
        if (showSkip) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = heading,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(48.dp))

        // Deliberately quiet CTA — a bright accent here would be the exact
        // dopamine cue this app exists to remove.
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        ) {
            Text(text = primaryLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
    }
}

// ── Screen 1 ────────────────────────────────────────────────────────────────

@Composable
fun WhatItIsScreen(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingPage(
        heading = stringResource(R.string.onboarding_1_heading),
        body = stringResource(R.string.onboarding_1_body),
        primaryLabel = stringResource(R.string.onboarding_next),
        onPrimary = onNext,
        onSkip = onSkip,
    )
}

// ── Screen 2 ────────────────────────────────────────────────────────────────

@Composable
fun WhatItIsNotScreen(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingPage(
        heading = stringResource(R.string.onboarding_2_heading),
        body = stringResource(R.string.onboarding_2_body),
        primaryLabel = stringResource(R.string.onboarding_next),
        onPrimary = onNext,
        onSkip = onSkip,
    )
}

// ── Screen 3 ────────────────────────────────────────────────────────────────

@Composable
fun TheLoopScreen(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingPage(
        heading = stringResource(R.string.onboarding_3_heading),
        body = stringResource(R.string.onboarding_3_body),
        primaryLabel = stringResource(R.string.onboarding_next),
        onPrimary = onNext,
        onSkip = onSkip,
    )
}

// ── Screen 4 ────────────────────────────────────────────────────────────────

@Composable
fun WhySmallTasksScreen(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingPage(
        heading = stringResource(R.string.onboarding_4_heading),
        body = stringResource(R.string.onboarding_4_body),
        primaryLabel = stringResource(R.string.onboarding_next),
        onPrimary = onNext,
        onSkip = onSkip,
    )
}

// ── Screen 5 ────────────────────────────────────────────────────────────────

@Composable
fun SetupScreen(onContinue: () -> Unit) {
    OnboardingPage(
        heading = stringResource(R.string.onboarding_5_heading),
        body = stringResource(R.string.onboarding_5_body),
        primaryLabel = stringResource(R.string.onboarding_continue),
        onPrimary = onContinue,
        onSkip = {}, // no skip on last screen
        showSkip = false,
    )
}
