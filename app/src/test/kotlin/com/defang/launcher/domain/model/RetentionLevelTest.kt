package com.defang.launcher.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetentionLevelTest {

    private val now = 1_700_000_000_000L
    private val dayMs = 24 * 60 * 60_000L

    @Test
    fun `DONT_TRACK cutoff is now (nothing survives)`() {
        assertEquals(now, RetentionLevel.DONT_TRACK.cutoffMillis(now))
    }

    @Test
    fun `DAYS_7 cutoff is 7 days before now`() {
        assertEquals(now - 7 * dayMs, RetentionLevel.DAYS_7.cutoffMillis(now))
    }

    @Test
    fun `MONTHS_3 cutoff is 90 days before now`() {
        assertEquals(now - 90 * dayMs, RetentionLevel.MONTHS_3.cutoffMillis(now))
    }

    @Test
    fun `MONTHS_6 cutoff is 180 days before now`() {
        assertEquals(now - 180 * dayMs, RetentionLevel.MONTHS_6.cutoffMillis(now))
    }

    @Test
    fun `MONTHS_12 cutoff is 365 days before now`() {
        assertEquals(now - 365 * dayMs, RetentionLevel.MONTHS_12.cutoffMillis(now))
    }

    @Test
    fun `INDEFINITE never prunes`() {
        assertNull(RetentionLevel.INDEFINITE.cutoffMillis(now))
    }

    @Test
    fun `fromOrdinal round-trips valid ordinals`() {
        RetentionLevel.entries.forEach { level ->
            assertEquals(level, RetentionLevel.fromOrdinal(level.ordinal))
        }
    }

    @Test
    fun `fromOrdinal falls back to INDEFINITE when out of range`() {
        assertEquals(RetentionLevel.INDEFINITE, RetentionLevel.fromOrdinal(999))
        assertEquals(RetentionLevel.INDEFINITE, RetentionLevel.fromOrdinal(-1))
    }
}
