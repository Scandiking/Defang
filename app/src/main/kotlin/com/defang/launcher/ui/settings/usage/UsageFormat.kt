package com.defang.launcher.ui.settings.usage

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.defang.launcher.R
import java.text.NumberFormat
import java.util.Locale

/** "2 hours and 5 minutes" / "45 minutes". */
@Composable
internal fun formatMinutes(minutes: Long): String =
    if (minutes >= 60) {
        stringResource(R.string.usage_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.usage_minutes, minutes)
    }

/** Short Y-axis label: "0", "30 m" below an hour, otherwise "1.5 h" / "1,5 t". */
@Composable
internal fun formatAxisMinutes(minutes: Long, locale: Locale): String =
    if (minutes == 0L) {
        "0"
    } else if (minutes < 60) {
        stringResource(R.string.usage_axis_minutes, minutes.toString())
    } else {
        stringResource(R.string.usage_axis_hours, formatAxisHours(minutes, locale))
    }

/** Hours with at most one decimal, in the locale's number format. */
internal fun formatAxisHours(minutes: Long, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }.format(minutes / 60.0)

private val AXIS_STEPS = longArrayOf(15, 30, 60, 120, 240, 480, 720)

/**
 * Smallest "nice" step (minutes) so that [steps] steps reach [maxMinutes].
 * Past 12 h per step it keeps doubling, so long monthly totals still fit.
 */
internal fun niceAxisStep(maxMinutes: Long, steps: Int): Long {
    val needed = (maxMinutes + steps - 1) / steps
    AXIS_STEPS.firstOrNull { it >= needed }?.let { return it }
    var step = AXIS_STEPS.last()
    while (step < needed) step *= 2
    return step
}
