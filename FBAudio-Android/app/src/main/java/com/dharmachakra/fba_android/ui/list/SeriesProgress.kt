package com.dharmachakra.fba_android.ui.list

import com.dharmachakra.fba_android.ui.detail.TalkProgress

/**
 * Series page progress (SPEC "Series page, Resume with listened and in progress rows"), pure so
 * it's unit-tested; mirrors iOS `SeriesProgress`. Inputs are the listener's saved progress per
 * talk (from Recently listened): cumulative position, the talk's length and when it was played.
 */
object SeriesProgress {
    data class Saved(val positionMs: Long, val totalSeconds: Int, val listenedAt: Long)

    enum class State { NOT_STARTED, IN_PROGRESS, LISTENED }

    data class Resume(val index: Int, val catNum: String, val remainingSeconds: Int?)

    fun state(saved: Saved?): State {
        if (saved == null) return State.NOT_STARTED
        val total = saved.totalSeconds
        if (total > 0 && saved.positionMs / 1000 >= total - TalkProgress.FINISHED_WITHIN_SECONDS) return State.LISTENED
        return if (saved.positionMs >= TalkProgress.STARTED_AFTER_MS) State.IN_PROGRESS else State.NOT_STARTED
    }

    /**
     * Whenever part of the series has been heard the main button is "Resume Talk n": the talk
     * played most recently if it's unfinished, else the next talk after the last one finished.
     * Null (show "Play all") when nothing has been heard, or every talk is finished.
     */
    fun resume(members: List<String>, saved: Map<String, Saved>): Resume? {
        val states = members.map { state(saved[it]) }
        if (states.none { it != State.NOT_STARTED }) return null
        val latest = members.indices
            .filter { states[it] != State.NOT_STARTED }
            .maxByOrNull { saved[members[it]]!!.listenedAt } ?: return null
        val index = when (states[latest]) {
            State.IN_PROGRESS -> latest
            else -> (latest + 1 until members.size).firstOrNull { states[it] != State.LISTENED }
                ?: members.indices.firstOrNull { states[it] != State.LISTENED }
                ?: return null
        }
        val s = saved[members[index]]
        val remaining = if (states[index] == State.IN_PROGRESS && s != null && s.totalSeconds > 0)
            (s.totalSeconds - (s.positionMs / 1000).toInt()).coerceAtLeast(0) else null
        return Resume(index, members[index], remaining)
    }
}
