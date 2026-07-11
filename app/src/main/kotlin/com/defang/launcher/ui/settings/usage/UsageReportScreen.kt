package com.defang.launcher.ui.settings.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defang.launcher.R
import java.time.format.TextStyle
import java.util.Locale

/**
 * Weekly usage report: rolling last 7 days vs the 7 before, per-day bars,
 * per-app totals, and the intent drift figure. Data over guilt (PRD goal 5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageReportScreen(
    onBack: () -> Unit,
    viewModel: UsageReportViewModel = hiltViewModel(),
) {
    val report by viewModel.report.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.usage_report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val r = report ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ── Week total + comparison ──
            SectionHeader(stringResource(R.string.usage_this_week))
            Text(
                text = formatMinutes(r.totalMinutesThisWeek),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = weekComparison(r.totalMinutesThisWeek, r.totalMinutesPrevWeek),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )

            // ── Per-day bars ──
            SectionHeader(stringResource(R.string.usage_per_day))
            val maxDay = r.perDay.maxOf { it.minutes }.coerceAtLeast(1)
            r.perDay.forEach { day ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    Text(
                        text = day.date.dayOfWeek
                            .getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(44.dp),
                    )
                    UsageBar(
                        fraction = day.minutes.toFloat() / maxDay,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatMinutes(day.minutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier
                            .width(64.dp)
                            .padding(start = 8.dp),
                    )
                }
            }

            // ── Per-app totals ──
            if (r.perApp.isNotEmpty()) {
                SectionHeader(stringResource(R.string.usage_per_app))
                val maxApp = r.perApp.maxOf { it.minutes }.coerceAtLeast(1)
                r.perApp.forEach { app ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(
                                    R.string.usage_app_line,
                                    formatMinutes(app.minutes),
                                    app.sessionCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                        UsageBar(
                            fraction = app.minutes.toFloat() / maxApp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 3.dp),
                        )
                    }
                }
            } else {
                SectionHeader(stringResource(R.string.usage_per_app))
                Text(
                    text = stringResource(R.string.usage_no_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }

            // ── Intent honesty ──
            SectionHeader(stringResource(R.string.usage_drift_title))
            if (r.declaredSessions > 0) {
                val pct = r.driftSessions * 100 / r.declaredSessions
                Text(
                    text = stringResource(
                        R.string.usage_drift_body,
                        r.driftSessions, r.declaredSessions, pct,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.usage_drift_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = stringResource(R.string.usage_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun UsageBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(10.dp)
            .background(
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                RoundedCornerShape(5.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)),
        )
    }
}

@Composable
private fun weekComparison(thisWeek: Long, prevWeek: Long): String = when {
    prevWeek == 0L -> stringResource(R.string.usage_no_prev_week)
    thisWeek <= prevWeek -> stringResource(
        R.string.usage_down_vs_prev, (prevWeek - thisWeek) * 100 / prevWeek,
    )
    else -> stringResource(
        R.string.usage_up_vs_prev, (thisWeek - prevWeek) * 100 / prevWeek,
    )
}

@Composable
private fun formatMinutes(minutes: Long): String =
    if (minutes >= 60) {
        stringResource(R.string.usage_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.usage_minutes, minutes)
    }
