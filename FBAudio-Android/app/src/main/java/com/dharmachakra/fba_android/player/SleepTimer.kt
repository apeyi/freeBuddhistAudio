package com.dharmachakra.fba_android.player

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Sleep timer choices (SPEC "Sleep timer sheet"). Mirrors iOS `SleepOption`. */
enum class SleepOption(val label: String, val minutes: Int?) {
    OFF("Off", null),
    MIN_5("5 minutes", 5),
    MIN_15("15 minutes", 15),
    MIN_30("30 minutes", 30),
    MIN_45("45 minutes", 45),
    HOUR_1("1 hour", 60),
    END_OF_TRACK("End of this track", null),
    END_OF_TALK("End of this talk", null);

    /** Short form for the More sheet row ("Sleep timer  15 min"). */
    val shortLabel: String get() = when (this) {
        OFF -> "Off"
        HOUR_1 -> "1 hr"
        END_OF_TRACK -> "End of track"
        END_OF_TALK -> "End of talk"
        else -> "$minutes min"
    }
}

data class SleepTimerState(val option: SleepOption = SleepOption.OFF, val endsAtMs: Long? = null)

/** Pure fade maths, unit-tested. */
object SleepFade {
    const val FADE_MS = 10_000L

    /** Volume (0..1) with [remainingMs] left: full until the last [FADE_MS], then a linear fade. */
    fun volume(remainingMs: Long): Float = when {
        remainingMs >= FADE_MS -> 1f
        remainingMs <= 0 -> 0f
        else -> remainingMs.toFloat() / FADE_MS
    }
}

/**
 * Pauses playback after a while, gently (a short fade out), or at the end of the track or talk.
 * Lives with the service's player, not the UI, so it keeps working when the app is closed.
 */
@OptIn(UnstableApi::class)
@Singleton
class SleepTimer @Inject constructor() {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state

    private var player: ExoPlayer? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                _state.value.option == SleepOption.END_OF_TRACK) {
                cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && _state.value.option == SleepOption.END_OF_TALK) cancel()
        }
    }

    /** Called by the service once its player exists. */
    fun attach(player: ExoPlayer, scope: CoroutineScope) {
        this.player = player
        this.scope = scope
        player.addListener(listener)
    }

    fun set(option: SleepOption, nowMs: Long = System.currentTimeMillis()) {
        cancel()
        val p = player ?: return
        when (option) {
            SleepOption.OFF -> return
            SleepOption.END_OF_TRACK -> {
                p.pauseAtEndOfMediaItems = true
                _state.value = SleepTimerState(option)
            }
            SleepOption.END_OF_TALK -> _state.value = SleepTimerState(option)
            else -> {
                val endsAt = nowMs + option.minutes!! * 60_000L
                _state.value = SleepTimerState(option, endsAt)
                job = scope?.launch {
                    while (isActive) {
                        val remaining = endsAt - System.currentTimeMillis()
                        p.volume = SleepFade.volume(remaining)
                        if (remaining <= 0) {
                            p.pause()
                            p.volume = 1f
                            _state.value = SleepTimerState()
                            break
                        }
                        delay(if (remaining > SleepFade.FADE_MS + 1_000) 1_000 else 100)
                    }
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        player?.let {
            it.volume = 1f
            it.pauseAtEndOfMediaItems = false
        }
        _state.value = SleepTimerState()
    }
}
