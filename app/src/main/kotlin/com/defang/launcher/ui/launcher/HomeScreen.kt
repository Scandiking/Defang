package com.defang.launcher.ui.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.defang.launcher.domain.model.HomeScreenMode
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    tidbit: String,
    mode: HomeScreenMode,
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
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp),
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
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1.5f))
    }
}
