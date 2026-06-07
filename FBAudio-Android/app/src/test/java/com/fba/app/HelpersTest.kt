package com.fba.app

import com.fba.app.ui.components.formatDuration
import com.fba.app.ui.components.formatFileSize
import com.fba.app.ui.components.safeFraction
import org.junit.Assert.*
import org.junit.Test

class HelpersTest {

    @Test
    fun `safeFraction passes through valid fractions`() {
        assertEquals(0f, 0f.safeFraction())
        assertEquals(0.5f, 0.5f.safeFraction())
        assertEquals(1f, 1f.safeFraction())
    }

    @Test
    fun `safeFraction coerces out-of-range into 0_1`() {
        assertEquals(0f, (-0.3f).safeFraction())
        assertEquals(1f, 1.7f.safeFraction())
    }

    @Test
    fun `safeFraction maps NaN and infinity to 0 (the crash guard)`() {
        // These are the values that crash a Slider / progress bar with
        // "current must not be NaN" if they reach it unguarded.
        assertEquals(0f, Float.NaN.safeFraction())
        assertEquals(0f, (0f / 0f).safeFraction())          // 0/0 -> NaN
        assertEquals(0f, (1f / 0f).safeFraction())          // +Inf
        assertEquals(0f, Float.POSITIVE_INFINITY.safeFraction())
        assertEquals(0f, Float.NEGATIVE_INFINITY.safeFraction())
    }

    @Test
    fun `formatDuration minutes and seconds`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:05", formatDuration(65))
        assertEquals("10:00", formatDuration(600))
    }

    @Test
    fun `formatDuration hours`() {
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:01:01", formatDuration(3661))
        assertEquals("2:00:00", formatDuration(7200))
    }

    @Test
    fun `formatFileSize various sizes`() {
        assertEquals("", formatFileSize(0))
        assertEquals("512 B", formatFileSize(512))
        assertTrue(formatFileSize(1500).contains("KB"))
        assertTrue(formatFileSize(1_500_000).contains("MB"))
        assertTrue(formatFileSize(1_500_000_000).contains("GB"))
    }
}
