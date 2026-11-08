package com.dharmachakra.fba_android.ui.myfba

import com.dharmachakra.fba_android.data.local.RecentlyListenedEntity
import com.dharmachakra.fba_android.ui.detail.TalkProgress
import com.dharmachakra.fba_android.ui.player.PlaybackMath

/**
 * My FBA rules and copy (SPEC "My FBA", "Continue listening (See all)"), pure so they're
 * unit-tested. Mirrors iOS `MyFbaRules`. Positions in [RecentlyListenedEntity] are cumulative
 * across chapters, so they compare directly with the talk's total length.
 */
object MyFbaRules {
    /** A talk leaves Continue listening after this long without a play. */
    const val MAX_AGE_DAYS = 90L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Started, not finished, and played within the last 90 days. */
    fun isInProgress(r: RecentlyListenedEntity, nowMs: Long): Boolean {
        if (r.positionMs < TalkProgress.STARTED_AFTER_MS) return false
        if (nowMs - r.listenedAt > MAX_AGE_DAYS * DAY_MS) return false
        return r.totalDurationSeconds <= 0 || remainingSeconds(r) > TalkProgress.FINISHED_WITHIN_SECONDS
    }

    fun isFinished(r: RecentlyListenedEntity): Boolean =
        r.totalDurationSeconds > 0 && remainingSeconds(r) <= TalkProgress.FINISHED_WITHIN_SECONDS

    /** Continue listening: talks in progress, most recent first. */
    fun continueListening(all: List<RecentlyListenedEntity>, nowMs: Long): List<RecentlyListenedEntity> =
        all.filter { isInProgress(it, nowMs) }.sortedByDescending { it.listenedAt }

    fun remainingSeconds(r: RecentlyListenedEntity): Int =
        (r.totalDurationSeconds - (r.positionMs / 1000).toInt()).coerceAtLeast(0)

    fun progress(r: RecentlyListenedEntity): Float =
        if (r.totalDurationSeconds <= 0) 0f else (r.positionMs / 1000f / r.totalDurationSeconds).coerceIn(0f, 1f)

    /** "7 talks in progress, most recent first". */
    fun inProgressLabel(count: Int): String =
        if (count == 1) "1 talk in progress" else "$count talks in progress, most recent first"

    /** "Speaker · Track 2 · 18 min left"; the track only for chapters after the first. */
    fun continueMeta(r: RecentlyListenedEntity): String = listOfNotNull(
        r.speaker.ifBlank { null },
        if (r.trackIndex > 0) "Track ${r.trackIndex + 1}" else null,
        if (r.totalDurationSeconds > 0) PlaybackMath.timeLeftLabel(remainingSeconds(r)) else null,
    ).joinToString(" · ")

    /** Library row value: "None yet", "1 talk · 64 MB", "12 talks · 1.2 GB". */
    fun downloadsSummary(count: Int, bytes: Long): String =
        if (count == 0) "None yet" else "$count ${if (count == 1) "talk" else "talks"} · ${formatBytes(bytes)}"

    /** "412 MB", "1.2 GB", "80 KB" (decimal units, as the system storage screens use). */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(java.util.Locale.UK, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
        bytes > 0 -> "${(bytes / 1000).coerceAtLeast(1)} KB"
        else -> "0 MB"
    }

    /** Listening history headings: "Today", "Yesterday", "Monday 3 November", "3 November 2025". */
    fun historyDayLabel(listenedAtMs: Long, nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val day = java.time.Instant.ofEpochMilli(listenedAtMs).atZone(zone).toLocalDate()
        val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val pattern = when {
            day == today -> return "Today"
            day == today.minusDays(1) -> return "Yesterday"
            day.year == today.year -> "EEEE d MMMM"
            else -> "d MMMM yyyy"
        }
        return day.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.UK))
    }
}
