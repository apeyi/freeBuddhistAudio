package com.dharmachakra.fba_android.ui.components

/**
 * "Report a problem" (SPEC: Report a problem sheet): the reasons offered and the email that
 * goes to support, with the talk, its number, the playback position and the device filled in.
 * Pure so the wording is unit-tested; mirrors iOS `ReportProblem`.
 */
object ReportProblem {
    const val SUPPORT_EMAIL = "support@dharmachakra.com"

    val reasons = listOf(
        "It won't play, or it stops",
        "Sound quality",
        "Wrong title, speaker or details",
        "Problem with the transcript",
        "Something else",
    )

    data class Email(val to: String, val subject: String, val body: String)

    fun email(
        reason: String,
        talkTitle: String,
        catNum: String,
        positionSeconds: Int?,
        device: String,
        appVersion: String,
        signedIn: Boolean,
    ): Email {
        val position = positionSeconds?.takeIf { it > 0 }?.let { formatClock(it) }
        val details = listOfNotNull(
            "Talk: $talkTitle ($catNum)",
            position?.let { "Playback position: $it" },
            "App version: $appVersion",
            "Device: $device",
            "Signed in: ${if (signedIn) "yes" else "no"}",
        )
        return Email(
            to = SUPPORT_EMAIL,
            subject = "$reason: $talkTitle ($catNum)",
            body = "\n\n\n" + details.joinToString("\n"),
        )
    }

    /** 662 → "11:02", 3725 → "1:02:05". */
    fun formatClock(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
