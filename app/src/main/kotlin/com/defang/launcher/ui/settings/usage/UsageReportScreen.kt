package com.defang.launcher.ui.settings.usage

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.defang.launcher.R
import com.defang.launcher.data.repository.ExtensionJustification
import com.defang.launcher.domain.model.RetentionLevel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Usage trend report: 7-day / 3-6-12-month ranges, line or bar chart with
 * optional smoothing, per-app totals, reviewable gate-extension
 * justifications, a retention control, and CSV export. Data over guilt
 * (PRD goal 5); long-range trend + justification review is issue #17.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageReportScreen(
    onBack: () -> Unit,
    viewModel: UsageReportViewModel = hiltViewModel(),
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val chartType by viewModel.chartType.collectAsStateWithLifecycle()
    val smoothingEnabled by viewModel.smoothingEnabled.collectAsStateWithLifecycle()
    val retentionLevel by viewModel.retentionLevel.collectAsStateWithLifecycle()
    val justifications by viewModel.justifications.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = viewModel.exportCsv()
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            Toast.makeText(context, R.string.usage_export_success, Toast.LENGTH_SHORT).show()
        }
    }

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
            // ── Range selector ──
            SectionHeader(stringResource(R.string.usage_this_week))
            RangeSelector(range = range, onRangeChange = viewModel::setRange)

            Text(
                text = formatMinutes(r.totalMinutes),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.usage_period_opens, r.totalOpens),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Text(
                text = periodComparison(r.totalMinutes, r.totalMinutesPrevPeriod),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )

            // ── Trend chart ──
            SectionHeader(stringResource(R.string.usage_per_day))
            ChartControls(
                chartType = chartType,
                onChartTypeChange = viewModel::setChartType,
                smoothingEnabled = smoothingEnabled,
                onSmoothingChange = viewModel::setSmoothingEnabled,
            )
            UsageChart(
                points = r.series,
                chartType = chartType,
                smoothingEnabled = smoothingEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

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
                TotalRow(formatMinutes(r.perApp.sumOf { it.minutes }))
            } else {
                SectionHeader(stringResource(R.string.usage_per_app))
                Text(
                    text = stringResource(R.string.usage_no_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }

            // ── Extension justifications ──
            SectionHeader(stringResource(R.string.usage_justifications_title))
            Text(
                text = stringResource(R.string.usage_justifications_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (justifications.isEmpty()) {
                Text(
                    text = stringResource(R.string.usage_justifications_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            } else {
                justifications.forEach { JustificationRow(it) }
            }

            // ── Retention + export ──
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader(stringResource(R.string.usage_retention_title))
            Text(
                text = stringResource(R.string.usage_retention_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            RetentionSlider(
                level = retentionLevel,
                onLevelChange = viewModel::setRetentionLevel,
            )

            Button(
                onClick = { exportLauncher.launch(CSV_FILE_NAME) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.usage_export_csv))
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

private const val CSV_FILE_NAME = "defang_usage.csv"

@Composable
private fun RangeSelector(range: ReportRange, onRangeChange: (ReportRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        val options = listOf(
            ReportRange.LAST_7_DAYS to stringResource(R.string.usage_range_7d),
            ReportRange.LAST_3_MONTHS to stringResource(R.string.usage_range_3mo),
            ReportRange.LAST_6_MONTHS to stringResource(R.string.usage_range_6mo),
            ReportRange.LAST_12_MONTHS to stringResource(R.string.usage_range_12mo),
        )
        options.forEach { (option, label) ->
            FilterChip(
                selected = range == option,
                onClick = { onRangeChange(option) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

@Composable
private fun ChartControls(
    chartType: ChartType,
    onChartTypeChange: (ChartType) -> Unit,
    smoothingEnabled: Boolean,
    onSmoothingChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilterChip(
            selected = chartType == ChartType.LINE,
            onClick = { onChartTypeChange(ChartType.LINE) },
            label = { Text(stringResource(R.string.usage_chart_line)) },
            modifier = Modifier.padding(end = 6.dp),
        )
        FilterChip(
            selected = chartType == ChartType.BAR,
            onClick = { onChartTypeChange(ChartType.BAR) },
            label = { Text(stringResource(R.string.usage_chart_bar)) },
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.usage_chart_smoothing),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 6.dp),
        )
        Switch(
            checked = smoothingEnabled,
            onCheckedChange = onSmoothingChange,
            enabled = chartType == ChartType.LINE,
        )
    }
}

@Composable
private fun JustificationRow(justification: ExtensionJustification) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = justification.reason,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "${justification.packageName.substringAfterLast('.')} · ${
                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(justification.timestamp)
            }",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

private val RETENTION_LEVELS = listOf(
    RetentionLevel.DONT_TRACK,
    RetentionLevel.DAYS_7,
    RetentionLevel.MONTHS_3,
    RetentionLevel.MONTHS_6,
    RetentionLevel.MONTHS_12,
    RetentionLevel.INDEFINITE,
)

@Composable
private fun retentionLabel(level: RetentionLevel): String = when (level) {
    RetentionLevel.DONT_TRACK -> stringResource(R.string.usage_retention_dont_track)
    RetentionLevel.DAYS_7 -> stringResource(R.string.usage_range_7d)
    RetentionLevel.MONTHS_3 -> stringResource(R.string.usage_range_3mo)
    RetentionLevel.MONTHS_6 -> stringResource(R.string.usage_range_6mo)
    RetentionLevel.MONTHS_12 -> stringResource(R.string.usage_range_12mo)
    RetentionLevel.INDEFINITE -> stringResource(R.string.usage_retention_indefinite)
}

@Composable
private fun RetentionSlider(level: RetentionLevel, onLevelChange: (RetentionLevel) -> Unit) {
    val index = RETENTION_LEVELS.indexOf(level).coerceAtLeast(0)
    Column {
        Text(
            text = retentionLabel(level),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = index.toFloat(),
            onValueChange = {
                onLevelChange(RETENTION_LEVELS[it.roundToInt().coerceIn(0, RETENTION_LEVELS.lastIndex)])
            },
            valueRange = 0f..(RETENTION_LEVELS.size - 1).toFloat(),
            steps = RETENTION_LEVELS.size - 2,
        )
    }
}

@Composable
private fun TotalRow(total: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.usage_total_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = total,
            style = MaterialTheme.typography.bodyMedium,
        )
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
private fun periodComparison(current: Long, previous: Long): String = when {
    previous == 0L -> stringResource(R.string.usage_no_prev_week)
    current <= previous -> stringResource(
        R.string.usage_down_vs_prev, (previous - current) * 100 / previous,
    )
    else -> stringResource(
        R.string.usage_up_vs_prev, (current - previous) * 100 / previous,
    )
}

@Composable
private fun formatMinutes(minutes: Long): String =
    if (minutes >= 60) {
        stringResource(R.string.usage_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.usage_minutes, minutes)
    }
