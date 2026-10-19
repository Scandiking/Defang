package com.defang.launcher.ui.settings.usage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UsageFormatTest {

    @Test
    fun `small values use the 15 minute step`() {
        assertEquals(15L, niceAxisStep(1, 4))
        assertEquals(15L, niceAxisStep(60, 4))
    }

    @Test
    fun `step is the smallest one whose four steps cover the max`() {
        assertEquals(30L, niceAxisStep(61, 4))
        assertEquals(30L, niceAxisStep(120, 4))
        assertEquals(60L, niceAxisStep(121, 4))
        assertEquals(120L, niceAxisStep(300, 4))
        assertEquals(240L, niceAxisStep(900, 4))
        assertEquals(480L, niceAxisStep(1_920, 4))
        assertEquals(720L, niceAxisStep(2_880, 4))
    }

    @Test
    fun `past 12 hours per step it keeps doubling`() {
        assertEquals(1_440L, niceAxisStep(2_881, 4))
        assertEquals(2_880L, niceAxisStep(10_000, 4))
    }

    @Test
    fun `four steps always reach the max`() {
        for (max in listOf(1L, 59L, 61L, 179L, 481L, 1_000L, 5_400L, 50_000L)) {
            val step = niceAxisStep(max, 4)
            assert(step * 4 >= max) { "max=$max step=$step" }
        }
    }

    @Test
    fun `hours use the locale decimal separator`() {
        assertEquals("1.5", formatAxisHours(90, Locale.ENGLISH))
        assertEquals("1,5", formatAxisHours(90, Locale.forLanguageTag("nb-NO")))
        assertEquals("2", formatAxisHours(120, Locale.ENGLISH))
        assertEquals("48", formatAxisHours(2_880, Locale.ENGLISH))
    }
}
