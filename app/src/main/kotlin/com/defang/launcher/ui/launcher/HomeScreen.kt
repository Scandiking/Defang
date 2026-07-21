package com.defang.launcher.ui.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.defang.launcher.R
import com.defang.launcher.domain.model.HomeScreenMode
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    tidbit: String,
    mode: HomeScreenMode,
    usage: List<HomeUsageRow>,
    onUsageTap: () -> Unit,
    onAppsTap: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = LocalDateTime.now()
        }
    }

    val timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    // Locale-correct "weekday, day, month" ordering and punctuation
    val locale = Locale.getDefault()
    val datePattern = remember(locale) {
        DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
    }
    val dateStr = now.format(DateTimeFormatter.ofPattern(datePattern, locale))

    // Swipe-up threshold
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart  = { dragTotal = 0f },
                    onDragEnd    = { dragTotal = 0f },
                    onDragCancel = { dragTotal = 0f },
                    onVerticalDrag = { _, delta ->
                        dragTotal += delta
                        // Negative delta = upward
                        if (dragTotal < -swipeThresholdPx) {
                            onAppsTap()
                            dragTotal = 0f
                        }
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Clock in the upper quarter, tidbit in the lower half
        Spacer(modifier = Modifier.weight(0.5f))

        if (mode == HomeScreenMode.CLOCK_AND_TIDBIT) {
            Text(
                text = timeStr,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (usage.isNotEmpty()) {
            HomeUsagePanel(
                usage = usage,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .padding(horizontal = 48.dp)
                    .clickable(onClick = onUsageTap),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (mode != HomeScreenMode.EMPTY && tidbit.isNotBlank()) {
            Text(
                text = "“$tidbit”",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = 22.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1.5f))
    }
}

/**
 * Today's time in watched apps, rendered natively — the launcher hosts no
 * AppWidgetHost, so the system widget can't live here. Deliberately muted:
 * this is a mirror, not a scoreboard. Tapping opens the weekly report.
 */
@Composable
private fun HomeUsagePanel(
    usage: List<HomeUsageRow>,
    modifier: Modifier = Modifier,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        usage.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                            RoundedCornerShape(2.dp),
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(
                                (row.minutes.toFloat() / row.limitMinutes).coerceIn(0f, 1f)
                            )
                            .background(dim, RoundedCornerShape(2.dp)),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.widget_usage_value, row.minutes, row.limitMinutes,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                    modifier = Modifier
                        .width(76.dp)
                        .padding(start = 8.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
