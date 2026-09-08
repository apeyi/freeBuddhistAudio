package com.fba.app.ui.legacy

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * A/B player for the Digital Legacy sample: the original and the remastered
 * recording of one chapter are both prepared up front and play in lockstep,
 * with the inactive one muted — so switching version is instant, no
 * re-buffering. Lives in the UI process (it's a demo, not background listening)
 * and is released when the screen goes away.
 */
class SamplePlayer(context: Context) {
    private val original: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // Only one of the pair asks for audio focus — a second request would pause the first.
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        setHandleAudioBecomingNoisy(true)
    }
    private val remaster: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), false)
    }

    var useRemaster: Boolean = true
        private set

    private val active: ExoPlayer get() = if (useRemaster) remaster else original
    private val shadow: ExoPlayer get() = if (useRemaster) original else remaster

    val isPlaying: Boolean get() = active.playWhenReady && active.playbackState != Player.STATE_ENDED
    val isBuffering: Boolean get() = active.playbackState == Player.STATE_BUFFERING && active.playWhenReady
    val positionMs: Long get() = active.currentPosition.coerceAtLeast(0)
    val durationMs: Long get() = active.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
    val isEnded: Boolean get() = active.playbackState == Player.STATE_ENDED

    /** Prepare both versions (buffering starts immediately) without playing. */
    fun load(originalUrl: String, remasterUrl: String, startWithRemaster: Boolean) {
        useRemaster = startWithRemaster
        original.setMediaItem(MediaItem.fromUri(originalUrl))
        remaster.setMediaItem(MediaItem.fromUri(remasterUrl))
        original.prepare()
        remaster.prepare()
        applyVolumes()
    }

    fun play() {
        if (isEnded) { original.seekTo(0); remaster.seekTo(0) }
        // Start the shadow at the active position so the two stay in step
        shadow.seekTo(active.currentPosition)
        original.play()
        remaster.play()
    }

    fun pause() {
        original.pause()
        remaster.pause()
    }

    fun togglePlayPause() = if (isPlaying) pause() else play()

    /** Swap what's audible; the newly audible player is aligned to the current position. */
    fun setVersion(remastered: Boolean) {
        if (remastered == useRemaster) return
        val position = active.currentPosition
        useRemaster = remastered
        active.seekTo(position)
        applyVolumes()
    }

    fun seekTo(positionMs: Long) {
        original.seekTo(positionMs)
        remaster.seekTo(positionMs)
    }

    /** Called periodically: pull the muted player back into step if it has drifted. */
    fun resync() {
        if (!isPlaying) return
        if (kotlin.math.abs(shadow.currentPosition - active.currentPosition) > 750) {
            shadow.seekTo(active.currentPosition)
        }
    }

    private fun applyVolumes() {
        original.volume = if (useRemaster) 0f else 1f
        remaster.volume = if (useRemaster) 1f else 0f
    }

    fun release() {
        original.release()
        remaster.release()
    }
}
