package com.defang.launcher.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.defang.launcher.R
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.data.repository.SessionRepository
import com.defang.launcher.domain.model.AppTier
import com.defang.launcher.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Usage dashboard widget (PRD P1): today's time in each watched app vs its
 * session limit, as simple bars. Data comes from Defang's own session log —
 * it measures gated sessions, which is the number the friction design acts on.
 *
 * Refreshed by the 30-minute widget alarm and pushed immediately from the
 * accessibility service whenever a session ends. Tapping opens the weekly
 * usage report.
 */
@AndroidEntryPoint
class UsageWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var sessionRepo: SessionRepository
    @Inject lateinit var appConfigRepo: AppConfigRepository

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val views = buildViews(context)
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun buildViews(context: Context): RemoteViews {
        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        // Today's minutes per package, open sessions counted up to now
        val minutesByPkg = sessionRepo.getSessionsSince(dayStart)
            .groupBy { it.packageName }
            .mapValues { (_, sessions) ->
                sessions.sumOf { s ->
                    val end = if (s.endTime > s.startTime) s.endTime else now
                    end - s.startTime
                } / 60_000
            }

        val watched = appConfigRepo.observeAll().first()
            .filter { it.tier == AppTier.WATCHED.dbValue }

        // Most-used watched apps first, capped at the four fixed layout rows
        val rows = watched
            .sortedByDescending { minutesByPkg[it.packageName] ?: 0L }
            .take(ROW_IDS.size)
            .map { config ->
                WidgetRow(
                    label = config.appLabel,
                    minutes = minutesByPkg[config.packageName] ?: 0L,
                    limitMinutes = config.sessionLimitMinutes.coerceAtLeast(1),
                )
            }

        val views = RemoteViews(context.packageName, R.layout.widget_usage)
        val anyUsage = rows.any { it.minutes > 0 }
        views.setViewVisibility(R.id.widget_empty, if (anyUsage) View.GONE else View.VISIBLE)

        ROW_IDS.forEachIndexed { i, ids ->
            val row = if (anyUsage) rows.getOrNull(i) else null
            if (row == null) {
                views.setViewVisibility(ids.row, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(ids.row, View.VISIBLE)
            views.setTextViewText(ids.label, row.label)
            views.setTextViewText(
                ids.value,
                context.getString(R.string.widget_usage_value, row.minutes, row.limitMinutes),
            )
            views.setProgressBar(
                ids.bar,
                row.limitMinutes,
                row.minutes.toInt().coerceAtMost(row.limitMinutes),
                false,
            )
        }

        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_OPEN_USAGE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return views
    }

    private data class WidgetRow(val label: String, val minutes: Long, val limitMinutes: Int)

    private data class RowIds(val row: Int, val label: Int, val value: Int, val bar: Int)

    companion object {
        private val ROW_IDS = listOf(
            RowIds(R.id.widget_row_1, R.id.widget_label_1, R.id.widget_value_1, R.id.widget_bar_1),
            RowIds(R.id.widget_row_2, R.id.widget_label_2, R.id.widget_value_2, R.id.widget_bar_2),
            RowIds(R.id.widget_row_3, R.id.widget_label_3, R.id.widget_value_3, R.id.widget_bar_3),
            RowIds(R.id.widget_row_4, R.id.widget_label_4, R.id.widget_value_4, R.id.widget_bar_4),
        )

        /** Ask the system to redraw all instances of this widget now. */
        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, UsageWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, UsageWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
