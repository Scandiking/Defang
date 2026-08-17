package com.defang.launcher.ui.settings.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageChartTest {

    @Test
    fun `centered moving average with default window of 3`() {
        val result = movingAverage(listOf(10L, 20L, 30L, 40L))

        // i=0: avg(10,20)=15  i=1: avg(10,20,30)=20  i=2: avg(20,30,40)=30  i=3: avg(30,40)=35
        assertEquals(listOf(15L, 20L, 30L, 35L), result)
    }

    @Test
    fun `single value list is unchanged`() {
        assertEquals(listOf(5L), movingAverage(listOf(5L)))
    }

    @Test
    fun `window of 1 is a no-op`() {
        val values = listOf(1L, 2L, 3L, 4L)
        assertEquals(values, movingAverage(values, window = 1))
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<Long>(), movingAverage(emptyList()))
    }
}
