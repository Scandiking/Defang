package com.defang.launcher.ui.settings.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.data.repository.ExtensionJustification
import com.defang.launcher.data.repository.SessionRepository
import com.defang.launcher.domain.model.RetentionLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

enum class ChartType { LINE, BAR }

/** Time window for the trend view. [days] drives the query cutoff;
 *  [bucket] controls how the series is re-grouped for readability. */
enum class ReportRange(val days: Long, val bucket: Bucket) {
    LAST_7_DAYS(7, Bucket.DAY),
    LAST_3_MONTHS(90, Bucket.WEEK),
    LAST_6_MONTHS(180, Bucket.MONTH),
    LAST_12_MONTHS(365, Bucket.MONTH),
}

enum class Bucket { DAY, WEEK, MONTH }

/**
 * Aggregates the session log into the usage trend report (PRD goal 5:
 * legible data on the user's own patterns — evidence, not guilt), and issue
 * #17: long-term trends (3/6/12 months) plus reviewable gate-extension
 * justifications, not just a rolling 7-day window.
 */
@HiltViewModel
class UsageReportViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val appConfigRepo: AppConfigRepository,
    private val prefs: PreferencesDataStore,
) : ViewModel() {

    data class AppUsage(
        val packageName: String,
        val label: String,
        val minutes: Long,
        val sessionCount: Int,
    )

    /** One point in the trend series — a day, week, or month depending on
     *  [ReportRange.bucket]. */
    data class UsagePoint(
        val bucketStart: LocalDate,
        val label: String,
        val minutes: Long,
        val opens: Int,
    )

    data class Report(
        val range: ReportRange,
        val totalMinutes: Long,
        val totalMinutesPrevPeriod: Long,
        val totalOpens: Int,
        val perApp: List<AppUsage>,       // sorted by minutes desc
        val series: List<UsagePoint>,     // oldest → most recent
    )

    private val _range = MutableStateFlow(ReportRange.LAST_7_DAYS)
    val range: StateFlow<ReportRange> = _range

    private val _chartType = MutableStateFlow(ChartType.LINE)
    val chartType: StateFlow<ChartType> = _chartType

    private val _smoothingEnabled = MutableStateFlow(false)
    val smoothingEnabled: StateFlow<Boolean> = _smoothingEnabled

    private val _report = MutableStateFlow<Report?>(null)
    val report: StateFlow<Report?> = _report

    private val _justifications = MutableStateFlow<List<ExtensionJustification>>(emptyList())
    val justifications: StateFlow<List<ExtensionJustification>> = _justifications

    val retentionLevel: StateFlow<RetentionLevel> = prefs.retentionLevel.stateIn(
        viewModelScope, SharingStarted.Eagerly, RetentionLevel.INDEFINITE
    )

    init {
        viewModelScope.launch { reload() }
    }

    fun setRange(newRange: ReportRange) {
        _range.value = newRange
        viewModelScope.launch { reload() }
    }

    fun setChartType(type: ChartType) {
        _chartType.value = type
    }

    fun setSmoothingEnabled(enabled: Boolean) {
        _smoothingEnabled.value = enabled
    }

    fun setRetentionLevel(level: RetentionLevel) {
        viewModelScope.launch {
            prefs.setRetentionLevel(level)
            sessionRepo.applyRetention(level)
        }
    }

    suspend fun exportCsv(): String = sessionRepo.exportCsv()

    private suspend fun reload() {
        _report.value = buildReport(_range.value)
        _justifications.value =
            sessionRepo.getExtensionJustificationsSince(periodStart(_range.value, offsetPeriods = 0))
    }

    /** Start of the period [offsetPeriods] ranges back (0 = current period),
     *  inclusive of today — e.g. for a 7-day range, offset 0 starts 6 days
     *  ago, offset 1 starts 13 days ago. */
    private fun periodStart(range: ReportRange, offsetPeriods: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = LocalDate.now(zone)
        return today.minusDays(range.days - 1 + range.days * offsetPeriods)
            .atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private suspend fun buildReport(range: ReportRange): Report {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val periodStart = periodStart(range, offsetPeriods = 0, zone)
        val prevPeriodStart = periodStart(range, offsetPeriods = 1, zone)
        val now = System.currentTimeMillis()

        val sessions = sessionRepo.getSessionsSince(prevPeriodStart)
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

        val current = sessions.filter { it.startTime >= periodStart }
        val previous = sessions.filter { it.startTime < periodStart }

        val perApp = current
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

        val series = buildSeries(current, today, range, zone)

        return Report(
            range = range,
            totalMinutes = current.sumOf { it.durationMs() } / 60_000,
            totalMinutesPrevPeriod = previous.sumOf { it.durationMs() } / 60_000,
            totalOpens = current.size,
            perApp = perApp,
            series = series,
        )
    }

    private fun buildSeries(
        sessions: List<SessionEntity>,
        today: LocalDate,
        range: ReportRange,
        zone: ZoneId,
    ): List<UsagePoint> = when (range.bucket) {
        Bucket.DAY -> (range.days - 1 downTo 0).map { daysAgo ->
            val day = today.minusDays(daysAgo)
            dayBucket(sessions, day, day.plusDays(1), zone) {
                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
        }

        Bucket.WEEK -> {
            val weeks = (range.days / 7).toInt()
            (weeks - 1 downTo 0).map { weeksAgo ->
                val weekStart = today.minusWeeks(weeksAgo.toLong()).minusDays(6)
                val weekEnd = weekStart.plusDays(7)
                dayBucket(sessions, weekStart, weekEnd, zone) {
                    "${weekStart.monthValue}/${weekStart.dayOfMonth}"
                }
            }
        }

        Bucket.MONTH -> {
            val months = (range.days / 30).toInt()
            (months - 1 downTo 0).map { monthsAgo ->
                val monthAnchor = today.minusMonths(monthsAgo.toLong())
                val monthStart = monthAnchor.withDayOfMonth(1)
                val monthEnd = monthStart.plusMonths(1)
                dayBucket(sessions, monthStart, monthEnd, zone) {
                    monthStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                }
            }
        }
    }

    private inline fun dayBucket(
        sessions: List<SessionEntity>,
        start: LocalDate,
        endExclusive: LocalDate,
        zone: ZoneId,
        label: () -> String,
    ): UsagePoint {
        val startMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val inBucket = sessions.filter { it.startTime in startMs until endMs }
        return UsagePoint(
            bucketStart = start,
            label = label(),
            minutes = inBucket.sumOf { it.durationMs() } / 60_000,
            opens = inBucket.size,
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
