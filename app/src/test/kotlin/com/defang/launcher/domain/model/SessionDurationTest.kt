package com.defang.launcher.domain.model

import com.defang.launcher.data.local.db.entity.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDurationTest {

    private val minute = 60_000L
    private val start = 1_000_000_000L
    private val cap = SessionDuration.capMs(15)

    private fun row(lengthMin: Long?, pausedMin: Long = 0) = SessionEntity(
        packageName = "com.a",
        startTime = start,
        endTime = lengthMin?.let { start + it * minute } ?: 0L,
        pausedMs = pausedMin * minute,
    )

    @Test
    fun `cap is the limit or an extension, whichever is longer, plus a minute`() {
        assertEquals(16 * minute, SessionDuration.capMs(15))
        assertEquals(11 * minute, SessionDuration.capMs(1))
        assertEquals(31 * minute, SessionDuration.capMs(30))
    }

    @Test
    fun `closed row counts its length`() {
        assertEquals(12 * minute, SessionDuration.countedMs(row(12), now = start + 99 * minute, cap))
    }

    @Test
    fun `paused time is left out`() {
        assertEquals(8 * minute, SessionDuration.countedMs(row(14, pausedMin = 6), now = start, cap))
    }

    @Test
    fun `row longer than the cap is capped`() {
        assertEquals(cap, SessionDuration.countedMs(row(8 * 60), now = start, cap))
    }

    @Test
    fun `open row runs to now`() {
        assertEquals(5 * minute, SessionDuration.countedMs(row(null), now = start + 5 * minute, cap))
    }

    @Test
    fun `stale open row counts nothing`() {
        val now = start + SessionDuration.STALE_OPEN_SESSION_MS + minute
        assertEquals(0L, SessionDuration.countedMs(row(null), now, cap))
    }

    @Test
    fun `paused longer than the row never goes negative`() {
        assertEquals(0L, SessionDuration.countedMs(row(3, pausedMin = 5), now = start, cap))
    }
}
