package com.fba.app

import com.fba.app.ui.components.formatDuration
import com.fba.app.ui.components.formatFileSize
import org.junit.Assert.*
import org.junit.Test

class HelpersTest {

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
