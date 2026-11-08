package com.dharmachakra.fba_android

import com.dharmachakra.fba_android.ui.components.ReportProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC "Report a problem sheet". Parity with iOS ReportProblemTests. */
class ReportProblemTest {
    @Test fun reasonsMatchTheDesign() {
        assertEquals(
            listOf(
                "It won't play, or it stops",
                "Sound quality",
                "Wrong title, speaker or details",
                "Problem with the transcript",
                "Something else",
            ),
            ReportProblem.reasons,
        )
    }

    @Test fun emailCarriesTheTalkPositionAndDevice() {
        val e = ReportProblem.email(
            reason = "Sound quality", talkTitle = "The Rainbow Body", catNum = "LOC7132",
            positionSeconds = 3725, device = "Google Pixel 8, Android 16", appVersion = "1.0.0", signedIn = true,
        )
        assertEquals("support@dharmachakra.com", e.to)
        assertEquals("Sound quality: The Rainbow Body (LOC7132)", e.subject)
        assertTrue(e.body.contains("Talk: The Rainbow Body (LOC7132)"))
        assertTrue(e.body.contains("Playback position: 1:02:05"))
        assertTrue(e.body.contains("Device: Google Pixel 8, Android 16"))
        assertTrue(e.body.contains("App version: 1.0.0"))
        assertTrue(e.body.contains("Signed in: yes"))
        assertFalse(e.body.contains("—"))   // no dashes in copy
    }

    @Test fun noPositionWhenNotPlaying() {
        val e = ReportProblem.email("Something else", "T", "1", null, "d", "v", false)
        assertFalse(e.body.contains("Playback position"))
        assertEquals("11:02", ReportProblem.formatClock(662))
    }

    @Test fun supportEmailCarriesVersionDeviceAndSignIn() {
        val e = ReportProblem.supportEmail(device = "Google Pixel 8, Android 16", appVersion = "0.10.0", signedIn = true)
        assertEquals("support@dharmachakra.com", e.to)
        assertEquals("FBA app support", e.subject)
        assertTrue(e.body.startsWith("\n\n\n"))
        assertTrue(e.body.contains("App version: 0.10.0"))
        assertTrue(e.body.contains("Device: Google Pixel 8, Android 16"))
        assertTrue(e.body.contains("Signed in: yes"))
    }
}
