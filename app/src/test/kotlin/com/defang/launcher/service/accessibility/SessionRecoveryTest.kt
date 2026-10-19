package com.defang.launcher.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRecoveryTest {

    private val minute = 60_000L
    private val limit = 15 * minute
    private val start = 1_000_000_000L

    @Test
    fun `in front with time left resumes with the remainder`() {
        val action = SessionRecovery.decide(start, limit, now = start + 5 * minute, appInForeground = true)

        assertEquals(RecoveryAction.Resume(10 * minute), action)
    }

    @Test
    fun `in front past the limit expires now`() {
        val action = SessionRecovery.decide(start, limit, now = start + 20 * minute, appInForeground = true)

        assertEquals(RecoveryAction.ExpireNow, action)
    }

    @Test
    fun `in front exactly at the limit expires now`() {
        val action = SessionRecovery.decide(start, limit, now = start + limit, appInForeground = true)

        assertEquals(RecoveryAction.ExpireNow, action)
    }

    @Test
    fun `not in front with time left closes at now`() {
        val now = start + 5 * minute

        val action = SessionRecovery.decide(start, limit, now, appInForeground = false)

        assertEquals(RecoveryAction.Close(now), action)
    }

    @Test
    fun `not in front past the limit closes at the limit`() {
        val action = SessionRecovery.decide(start, limit, now = start + 20 * minute, appInForeground = false)

        assertEquals(RecoveryAction.Close(start + limit), action)
    }

    @Test
    fun `six-day-old row with app not in front closes at start plus limit`() {
        val sixDays = 6 * 24 * 60 * minute

        val action = SessionRecovery.decide(start, limit, now = start + sixDays, appInForeground = false)

        assertEquals(RecoveryAction.Close(start + limit), action)
    }

    @Test
    fun `paused time does not count against the remainder`() {
        val action = SessionRecovery.decide(
            start, limit, now = start + 20 * minute, appInForeground = true, pausedMs = 10 * minute,
        )

        assertEquals(RecoveryAction.Resume(5 * minute), action)
    }

    @Test
    fun `not in front closes at limit plus paused time`() {
        val action = SessionRecovery.decide(
            start, limit, now = start + 60 * minute, appInForeground = false, pausedMs = 3 * minute,
        )

        assertEquals(RecoveryAction.Close(start + limit + 3 * minute), action)
    }
}
