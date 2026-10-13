package com.defang.launcher.util

import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.domain.model.PersonalUsage
import com.defang.launcher.util.PersonalLine.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalTidbitsTest {

    private val min = 60_000L
    private val day = 24 * 60 * min
    private val dayStart = 100 * day
    private val now = dayStart + 12 * 60 * min

    private fun session(pkg: String, start: Long, minutes: Long, pattern: String? = null, open: Boolean = false) =
        SessionEntity(
            packageName = pkg,
            startTime = start,
            endTime = if (open) 0L else start + minutes * min,
            watchedPattern = pattern,
        )

    private fun usage(sessions: List<SessionEntity>, pkg: String = "ig", pattern: String? = null) =
        PersonalUsage.from(sessions, pkg, pattern, now, dayStart)

    @Test
    fun `counts only this app, today vs week, and skips open rows for time`() {
        val u = usage(listOf(
            session("ig", dayStart + 60 * min, 20),
            session("ig", dayStart + 120 * min, 10),
            session("ig", dayStart + 180 * min, 0, open = true),
            session("ig", dayStart - 2 * day, 40),
            session("other", dayStart + 60 * min, 99),
        ))

        assertEquals(30, u.todayMinutes)
        assertEquals(3, u.opensToday)
        assertEquals(70, u.weekMinutes)
        assertEquals(3, u.daysOfHistory)
        assertEquals(70 / 3, u.avgDailyMinutes)
    }

    @Test
    fun `site usage is keyed by pattern, not the whole browser`() {
        val u = usage(
            listOf(
                session("browser", dayStart + min, 20, pattern = "reddit.com"),
                session("browser", dayStart + min, 50, pattern = "news.com"),
                session("browser", dayStart + min, 50),
            ),
            pkg = "browser", pattern = "reddit.com",
        )

        assertEquals(20, u.todayMinutes)
        assertEquals(1, u.opensToday)
    }

    @Test
    fun `app usage excludes the same browser's site sessions`() {
        val u = usage(
            listOf(
                session("browser", dayStart + min, 20, pattern = "reddit.com"),
                session("browser", dayStart + min, 5),
            ),
            pkg = "browser",
        )

        assertEquals(5, u.todayMinutes)
    }

    @Test
    fun `history window caps at seven days`() {
        val u = usage(listOf(session("ig", dayStart - 10 * day, 70)))

        assertEquals(7, u.daysOfHistory)
        assertEquals(10, u.avgDailyMinutes)
    }

    @Test
    fun `no history yields no lines`() {
        assertTrue(PersonalTidbits.candidates(usage(emptyList())).isEmpty())
    }

    @Test
    fun `small numbers don't qualify`() {
        val u = PersonalUsage(todayMinutes = 14, opensToday = 3, weekMinutes = 59, avgDailyMinutes = 9, daysOfHistory = 7)

        assertTrue(PersonalTidbits.candidates(u).isEmpty())
    }

    @Test
    fun `heavy use qualifies for every line, year in hours`() {
        val u = PersonalUsage(todayMinutes = 50, opensToday = 9, weekMinutes = 300, avgDailyMinutes = 43, daysOfHistory = 7)

        val lines = PersonalTidbits.candidates(u).associate { it.kind to it.value }

        assertEquals(43, lines[Kind.DAILY_AVERAGE])
        assertEquals(43 * 365 / 60, lines[Kind.YEAR])
        assertEquals(50, lines[Kind.TODAY])
        assertEquals(9, lines[Kind.OPENS_TODAY])
        assertEquals(300, lines[Kind.WEEK])
    }

    @Test
    fun `a single day of history gives no average, week or year line`() {
        val u = PersonalUsage(todayMinutes = 90, opensToday = 2, weekMinutes = 90, avgDailyMinutes = 90, daysOfHistory = 1)

        assertEquals(listOf(Kind.TODAY), PersonalTidbits.candidates(u).map { it.kind })
    }
}
