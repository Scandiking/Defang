package com.defang.launcher.ui.settings.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.defang.launcher.R

/**
 * Renders [points] as either a line or bar chart. Line mode can additionally
 * show a moving-average-smoothed series instead of the raw one, to make the
 * long-range (3/6/12 month) trend legible over noisy day-to-day data.
 *
 * The Y-axis is labelled on the grid lines with a rounded step (see
 * [niceAxisStep]). Tapping a bar or point shows its exact value below.
 */
@Composable
fun UsageChart(
    points: List<UsageReportViewModel.UsagePoint>,
    chartType: ChartType,
    smoothingEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    val rawValues = points.map { it.minutes }
    val values = if (chartType == ChartType.LINE && smoothingEnabled) {
        movingAverage(rawValues)
    } else {
        rawValues
    }
    val step = niceAxisStep(values.maxOf { it }.coerceAtLeast(1), GRID_LINES)
    val axisMax = (step * GRID_LINES).toFloat()

    val lineColor = MaterialTheme.colorScheme.primary
    val dimColor = lineColor.copy(alpha = 0.4f)
    val gridColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    val guideColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Y-axis tick labels, measured up front so the gutter fits the widest one.
    val locale = LocalConfiguration.current.locales[0]
    val tickTexts = (0..GRID_LINES).map { formatAxisMinutes(it * step, locale) }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val textMeasurer = rememberTextMeasurer()
    val tickLayouts = remember(tickTexts, labelStyle) {
        tickTexts.map { textMeasurer.measure(it, labelStyle) }
    }
    val density = LocalDensity.current
    val labelGapPx = with(density) { LABEL_GAP.toPx() }
    val gutterPx = tickLayouts.maxOf { it.size.width } + labelGapPx
    val gutterDp = with(density) { gutterPx.toDp() }

    // X labels, thinned so dense ranges stay legible; the most recent is always shown.
    val n = values.size
    val every = (n + MAX_X_LABELS - 1) / MAX_X_LABELS
    val xLabelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val xLayouts = remember(points, xLabelStyle) {
        points.mapIndexed { i, p ->
            if ((n - 1 - i) % every == 0) textMeasurer.measure(p.label, xLabelStyle) else null
        }
    }
    val xLabelAreaPx = (xLayouts.firstNotNullOfOrNull { it }?.size?.height ?: 0) +
        with(density) { X_LABEL_GAP.toPx() }

    var selected by remember(points) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(PLOT_HEIGHT + with(density) { xLabelAreaPx.toDp() })
                .pointerInput(points, gutterPx) {
                    detectTapGestures { pos ->
                        val cellW = (size.width - gutterPx) / n
                        val i = ((pos.x - gutterPx) / cellW).toInt().coerceIn(0, n - 1)
                        selected = if (selected == i) null else i
                    }
                },
        ) {
            // Inset top/bottom by half a label so the 0 and max labels aren't clipped.
            val halfText = tickLayouts.first().size.height / 2f
            val top = halfText
            val bottom = size.height - xLabelAreaPx - halfText
            val plotH = bottom - top
            val left = gutterPx
            val right = size.width
            val cellW = (right - left) / n

            fun xAt(i: Int) = left + cellW * (i + 0.5f)
            fun yAt(v: Long) = bottom - plotH * (v / axisMax)

            repeat(GRID_LINES + 1) { i ->
                val y = bottom - plotH * i / GRID_LINES
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                val label = tickLayouts[i]
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(left - labelGapPx - label.size.width, y - label.size.height / 2f),
                )
            }

            xLayouts.forEachIndexed { i, label ->
                if (label == null) return@forEachIndexed
                val x = (xAt(i) - label.size.width / 2f)
                    .coerceIn(left, right - label.size.width)
                drawText(label, topLeft = Offset(x, size.height - label.size.height))
            }

            selected?.let { i ->
                drawLine(guideColor, Offset(xAt(i), top), Offset(xAt(i), bottom), strokeWidth = 2f)
            }

            when (chartType) {
                ChartType.BAR -> {
                    val barWidth = cellW * 0.6f
                    values.forEachIndexed { i, v ->
                        val y = yAt(v)
                        drawRect(
                            color = if (selected == null || selected == i) lineColor else dimColor,
                            topLeft = Offset(xAt(i) - barWidth / 2, y),
                            size = Size(barWidth, bottom - y),
                        )
                    }
                }

                ChartType.LINE -> {
                    val path = Path()
                    values.forEachIndexed { i, v ->
                        if (i == 0) path.moveTo(xAt(i), yAt(v)) else path.lineTo(xAt(i), yAt(v))
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round),
                    )
                    values.forEachIndexed { i, v ->
                        val radius = if (selected == i) 9f else 4f
                        drawCircle(color = lineColor, radius = radius, center = Offset(xAt(i), yAt(v)))
                    }
                }
            }
        }

        val sel = selected
        Text(
            text = if (sel != null) {
                stringResource(R.string.usage_chart_value, points[sel].label, formatMinutes(rawValues[sel]))
            } else {
                stringResource(R.string.usage_chart_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            modifier = Modifier.padding(start = gutterDp, top = 8.dp),
        )
    }
}

private const val GRID_LINES = 4
private const val SMOOTHING_WINDOW = 3
private const val MAX_X_LABELS = 7
private val LABEL_GAP = 6.dp
private val X_LABEL_GAP = 8.dp
private val PLOT_HEIGHT = 160.dp

internal fun movingAverage(values: List<Long>, window: Int = SMOOTHING_WINDOW): List<Long> =
    values.indices.map { i ->
        val lo = (i - window / 2).coerceAtLeast(0)
        val hi = (i + window / 2).coerceAtMost(values.lastIndex)
        values.subList(lo, hi + 1).average().toLong()
    }
