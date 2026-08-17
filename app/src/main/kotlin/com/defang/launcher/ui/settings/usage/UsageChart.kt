package com.defang.launcher.ui.settings.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Renders [points] as either a line or bar chart. Line mode can additionally
 * show a moving-average-smoothed series instead of the raw one, to make the
 * long-range (3/6/12 month) trend legible over noisy day-to-day data.
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
    val maxValue = values.maxOf { it }.coerceAtLeast(1)

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val w = size.width
            val h = size.height

            repeat(GRID_LINES + 1) { i ->
                val y = h * i / GRID_LINES
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            val stepX = if (values.size > 1) w / (values.size - 1) else 0f

            when (chartType) {
                ChartType.BAR -> {
                    val barWidth = (w / values.size) * 0.6f
                    values.forEachIndexed { i, v ->
                        val barHeight = h * (v.toFloat() / maxValue)
                        val cx = if (values.size > 1) i * stepX else w / 2f
                        drawRect(
                            color = lineColor,
                            topLeft = Offset(cx - barWidth / 2, h - barHeight),
                            size = Size(barWidth, barHeight),
                        )
                    }
                }

                ChartType.LINE -> {
                    val path = Path()
                    values.forEachIndexed { i, v ->
                        val x = if (values.size > 1) i * stepX else w / 2f
                        val y = h - h * (v.toFloat() / maxValue)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round),
                    )
                    values.forEachIndexed { i, v ->
                        val x = if (values.size > 1) i * stepX else w / 2f
                        val y = h - h * (v.toFloat() / maxValue)
                        drawCircle(color = lineColor, radius = 4f, center = Offset(x, y))
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            points.forEach { point ->
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val GRID_LINES = 4
private const val SMOOTHING_WINDOW = 3

internal fun movingAverage(values: List<Long>, window: Int = SMOOTHING_WINDOW): List<Long> =
    values.indices.map { i ->
        val lo = (i - window / 2).coerceAtLeast(0)
        val hi = (i + window / 2).coerceAtMost(values.lastIndex)
        values.subList(lo, hi + 1).average().toLong()
    }
