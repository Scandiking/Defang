package com.defang.launcher.ui.settings.usage

import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.data.repository.FakeSessionDao
import com.defang.launcher.data.repository.FakeSessionExtensionDao
import com.defang.launcher.data.repository.SessionRepository
import com.defang.launcher.domain.model.RetentionLevel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class UsageReportViewModelTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val sessionDao = FakeSessionDao()
    private val extensionDao = FakeSessionExtensionDao()
    private val prefs = mockk<PreferencesDataStore>()
    private val appConfigRepo = mockk<AppConfigRepository>()

    private lateinit var sessionRepo: SessionRepository
    private lateinit var viewModel: UsageReportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { prefs.retentionLevel } returns flowOf(RetentionLevel.INDEFINITE)
        coEvery { appConfigRepo.getConfig(any()) } returns null
        sessionRepo = SessionRepository(sessionDao, extensionDao, prefs)
        viewModel = UsageReportViewModel(sessionRepo, appConfigRepo, prefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun millisAt(date: LocalDate, hour: Long = 0): Long =
        date.atStartOfDay(zone).plusHours(hour).toInstant().toEpochMilli()

    // ── periodStart off-by-one contract ─────────────────────────────────────

    @Test
    fun `periodStart offset 0 for a 7-day range starts 6 days ago`() {
        val today = LocalDate.now(zone)
        val expected = millisAt(today.minusDays(6))

        assertEquals(expected, viewModel.periodStart(ReportRange.LAST_7_DAYS, offsetPeriods = 0, zone = zone))
    }

    @Test
    fun `periodStart offset 1 for a 7-day range starts 13 days ago`() {
        val today = LocalDate.now(zone)
        val expected = millisAt(today.minusDays(13))

        assertEquals(expected, viewModel.periodStart(ReportRange.LAST_7_DAYS, offsetPeriods = 1, zone = zone))
    }

    @Test
    fun `periodStart offset 1 is exactly one range width before offset 0`() {
        // Compared as calendar days, not raw millis: a DST transition inside
        // the window would otherwise make a millis-diff assertion flaky.
        for (range in ReportRange.entries) {
            val date0 = java.time.Instant.ofEpochMilli(viewModel.periodStart(range, offsetPeriods = 0, zone = zone))
                .atZone(zone).toLocalDate()
            val date1 = java.time.Instant.ofEpochMilli(viewModel.periodStart(range, offsetPeriods = 1, zone = zone))
                .atZone(zone).toLocalDate()
            assertEquals(
                "range=$range should step back by exactly range.days",
                range.days,
                java.time.temporal.ChronoUnit.DAYS.between(date1, date0),
            )
        }
    }

    // ── buildSeries / day bucketing ──────────────────────────────────────────

    @Test
    fun `DAY bucket for a 7-day range produces exactly 7 points`() {
        val today = LocalDate.now(zone)
        val series = viewModel.buildSeries(emptyList(), today, ReportRange.LAST_7_DAYS, zone)

        assertEquals(7, series.size)
    }

    @Test
    fun `sessions land in the correct day bucket and sum minutes and opens`() {
        val today = LocalDate.now(zone)
        val threeDaysAgo = today.minusDays(3)
        val sessions = listOf(
            // Two sessions on the same day: 10 + 20 = 30 minutes, 2 opens.
            SessionEntity(
                id = 1, packageName = "a", startTime = millisAt(threeDaysAgo, hour = 1),
                endTime = millisAt(threeDaysAgo, hour = 1) + 10 * 60_000,
            ),
            SessionEntity(
                id = 2, packageName = "a", startTime = millisAt(threeDaysAgo, hour = 2),
                endTime = millisAt(threeDaysAgo, hour = 2) + 20 * 60_000,
            ),
            // A session on a different day must not bleed into that bucket.
            SessionEntity(
                id = 3, packageName = "a", startTime = millisAt(today),
                endTime = millisAt(today) + 5 * 60_000,
            ),
        )

        val series = viewModel.buildSeries(sessions, today, ReportRange.LAST_7_DAYS, zone)
        val bucket = series.single { it.bucketStart == threeDaysAgo }

        assertEquals(30L, bucket.minutes)
        assertEquals(2, bucket.opens)
    }

    // ── buildReport: current vs previous period, per-app, stale-open filter ──

    @Test
    fun `buildReport separates current and previous period and sums per app`() = runTest {
        val today = LocalDate.now(zone)
        sessionDao.rows += SessionEntity(
            id = 1, packageName = "com.a", startTime = millisAt(today, hour = 1),
            endTime = millisAt(today, hour = 1) + 15 * 60_000,
        )
        sessionDao.rows += SessionEntity(
            id = 2, packageName = "com.b", startTime = millisAt(today, hour = 2),
            endTime = millisAt(today, hour = 2) + 5 * 60_000,
        )
        // 10 days ago: inside the previous 7-day period, not the current one.
        sessionDao.rows += SessionEntity(
            id = 3, packageName = "com.a", startTime = millisAt(today.minusDays(10)),
            endTime = millisAt(today.minusDays(10)) + 40 * 60_000,
        )

        val report = viewModel.buildReport(ReportRange.LAST_7_DAYS)

        assertEquals(20L, report.totalMinutes) // 15 + 5, current period only
        assertEquals(40L, report.totalMinutesPrevPeriod)
        assertEquals(2, report.totalOpens)
        assertEquals(listOf("com.a", "com.b"), report.perApp.map { it.packageName }) // sorted by minutes desc
        assertEquals(15L, report.perApp[0].minutes)
        assertEquals(5L, report.perApp[1].minutes)
    }

    @Test
    fun `buildReport keeps a recently-opened still-open session, running until now`() = runTest {
        val now = System.currentTimeMillis()
        sessionDao.rows += SessionEntity(
            id = 1, packageName = "com.a", startTime = now - 60 * 60_000, endTime = 0L,
        )

        val report = viewModel.buildReport(ReportRange.LAST_7_DAYS)

        assertEquals(1, report.totalOpens)
        assertEquals(60L, report.totalMinutes)
    }

    @Test
    fun `buildReport drops a stale open session older than 24h`() = runTest {
        val now = System.currentTimeMillis()
        sessionDao.rows += SessionEntity(
            id = 1, packageName = "com.a",
            startTime = now - (UsageReportViewModel.STALE_OPEN_SESSION_MS + 60_000), endTime = 0L,
        )

        val report = viewModel.buildReport(ReportRange.LAST_7_DAYS)

        assertEquals(0, report.totalOpens)
        assertEquals(0L, report.totalMinutes)
    }
}
