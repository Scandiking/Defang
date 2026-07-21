package com.defang.launcher.ui.settings.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Aggregates the session log into the weekly usage report (PRD goal 5:
 * legible data on the user's own patterns — evidence, not guilt).
 *
 * "Week" is the rolling last 7 days, compared against the 7 days before that.
 */
@HiltViewModel
class UsageReportViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val appConfigRepo: AppConfigRepository,
) : ViewModel() {

    data class AppUsage(
        val packageName: String,
        val label: String,
        val minutes: Long,
        val sessionCount: Int,
    )

    data class DayUsage(
        val date: LocalDate,
        val minutes: Long,
    )

    data class Report(
        val totalMinutesThisWeek: Long,
        val totalMinutesPrevWeek: Long,
        val perApp: List<AppUsage>,       // sorted by minutes desc
        val perDay: List<DayUsage>,       // oldest → today, always 7 entries
    )

    private val _report = MutableStateFlow<Report?>(null)
    val report: StateFlow<Report?> = _report

    init {
        viewModelScope.launch { _report.value = buildReport() }
    }

    private suspend fun buildReport(): Report {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val prevWeekStart = today.minusDays(13).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val sessions = sessionRepo.getSessionsSince(prevWeekStart)
            .mapNotNull { s ->
                val end = when {
                    s.endTime > s.startTime -> s.endTime
                    // Still-open session: count it as running until now, but drop
                    // stale never-closed rows (service killed before endSession)
                    now - s.startTime < STALE_OPEN_SESSION_MS -> now
                    else -> return@mapNotNull null
                }
                s.copy(endTime = end)
            }

        val thisWeek = sessions.filter { it.startTime >= weekStart }
        val prevWeek = sessions.filter { it.startTime < weekStart }

        val perApp = thisWeek
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                AppUsage(
                    packageName = pkg,
                    label = labelFor(pkg),
                    minutes = list.sumOf { it.durationMs() } / 60_000,
                    sessionCount = list.size,
                )
            }
            .sortedByDescending { it.minutes }

        val perDay = (6 downTo 0).map { daysAgo ->
            val day = today.minusDays(daysAgo.toLong())
            val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            DayUsage(
                date = day,
                minutes = thisWeek
                    .filter { it.startTime in dayStart until dayEnd }
                    .sumOf { it.durationMs() } / 60_000,
            )
        }

        return Report(
            totalMinutesThisWeek = thisWeek.sumOf { it.durationMs() } / 60_000,
            totalMinutesPrevWeek = prevWeek.sumOf { it.durationMs() } / 60_000,
            perApp = perApp,
            perDay = perDay,
        )
    }

    private suspend fun labelFor(pkg: String): String =
        appConfigRepo.getConfig(pkg)?.appLabel ?: pkg.substringAfterLast('.')

    private fun SessionEntity.durationMs(): Long = endTime - startTime

    companion object {
        /** Open sessions older than this are treated as unclosed garbage. */
        const val STALE_OPEN_SESSION_MS = 24 * 60 * 60_000L
    }
}
