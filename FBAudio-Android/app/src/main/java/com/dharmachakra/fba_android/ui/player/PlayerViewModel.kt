package com.dharmachakra.fba_android.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dharmachakra.fba_android.data.local.AppSettings
import com.dharmachakra.fba_android.data.local.DownloadStatus
import com.dharmachakra.fba_android.data.repository.DownloadRepository
import com.dharmachakra.fba_android.data.repository.TalkRepository
import com.dharmachakra.fba_android.domain.model.Talk
import com.dharmachakra.fba_android.player.PlaybackResolver
import com.dharmachakra.fba_android.player.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val currentTalk: Talk? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val isVisible: Boolean = false,
    val downloadStatus: DownloadStatus? = null,
    val playbackSpeed: Float = 1.0f,
    val currentTrackIndex: Int = 0,
    val showDeleteDownloadPrompt: Boolean = false,
    val playbackError: String? = null,
    val isReconnecting: Boolean = false,
    val isBuffering: Boolean = false,
    /** Remastered version selected (only meaningful when the talk has one). */
    val useRemaster: Boolean = false,
    /** Playing from a download, so the version can't be switched. */
    val versionLocked: Boolean = false,
)

/**
 * UI-side controller for playback. The queue itself is built by
 * [PlaybackResolver] and progress is recorded by the service's
 * PlaybackPersistence — both live in the media service so that Android Auto and
 * the notification work without this ViewModel; here we only drive the
 * MediaController and mirror its state for the screens.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val talkRepository: TalkRepository,
    private val downloadRepository: DownloadRepository,
    private val settings: AppSettings,
    private val resolver: PlaybackResolver,
) : ViewModel() {

    private val prefs: SharedPreferences = resolver.prefs
    private val savedSpeed = prefs.getFloat("playback_speed", 1.0f)

    private val _uiState = MutableStateFlow(PlayerUiState(playbackSpeed = savedSpeed))
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    // Completed once the controller connects; playback commands await this so a
    // play request issued before the (async) connection finishes isn't dropped.
    private val controllerReady = CompletableDeferred<MediaController>()
    private var downloadObservationJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var pendingRestore: RestoreState? = null
    // Set as soon as the user explicitly starts playback; gates the passive
    // last-session restore so it can never clobber a user-initiated talk.
    private var userInitiatedPlayback = false
    private var autoRetryCount: Int = 0

    private data class RestoreState(val catNum: String, val position: Long, val trackIndex: Int)

    /**
     * What the play/pause UI should show: the user's intent (playWhenReady), not
     * the raw isPlaying flag — seeks bounce through STATE_BUFFERING where
     * isPlaying flips false for a moment, which made the pause icon flash to play.
     */
    private fun Player.isPlayingForUi(): Boolean =
        playWhenReady && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mediaController?.let { _uiState.value = _uiState.value.copy(isPlaying = it.isPlayingForUi()) }
            if (isPlaying) {
                // Successful playback — clear any prior error and reset retry budget
                autoRetryCount = 0
                if (_uiState.value.playbackError != null || _uiState.value.isReconnecting) {
                    _uiState.value = _uiState.value.copy(playbackError = null, isReconnecting = false)
                }
                startPositionUpdates()
            } else {
                stopPositionUpdates()
                updatePosition() // one final update
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The whole talk is queued as one playlist; track changes (user seeks
            // and automatic chapter advance — which now also works while the app
            // UI is dead, since the queue lives in the service's player) land here.
            val index = mediaController?.currentMediaItemIndex ?: return
            if (index != _uiState.value.currentTrackIndex) {
                _uiState.value = _uiState.value.copy(currentTrackIndex = index)
            }
            // Playback started elsewhere (Android Auto, resumption): show that talk.
            val catNum = mediaItem?.mediaMetadata?.extras?.getString(PlaybackResolver.EXTRA_CAT_NUM)
            if (catNum != null && catNum != _uiState.value.currentTalk?.catNum) {
                viewModelScope.launch { showExternallyStartedTalk(catNum) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e("PlayerViewModel", "Playback error: ${error.errorCodeName}", error)
            // Transient network errors: auto-retry a few times (network may be flapping).
            val isNetwork = error.errorCode in intArrayOf(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            )
            if (isNetwork && autoRetryCount < 3) {
                autoRetryCount++
                // Visible feedback while retrying — without it the player just
                // flip-flops its icon and looks dead during the backoff waits.
                _uiState.value = _uiState.value.copy(isReconnecting = true)
                viewModelScope.launch {
                    delay(2000L * autoRetryCount)
                    mediaController?.run { prepare(); play() }
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    playbackError = if (isNetwork) "Couldn't load audio — check your connection."
                    else "Couldn't play this talk.",
                    isReconnecting = false,
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePosition()
            // Buffering with intent to play → show a spinner. Distinguishes
            // "loading, audio coming" from "playing with sound" (the icon now
            // reflects intent, so without this a buffering stream looks like it's
            // playing silently).
            _uiState.value = _uiState.value.copy(
                isBuffering = playbackState == Player.STATE_BUFFERING &&
                    mediaController?.playWhenReady == true,
            )
            if (playbackState == Player.STATE_ENDED) {
                // Whole queue finished. (The resume point is cleared by the service.)
                val state = _uiState.value
                if (state.currentTalk != null && state.downloadStatus == DownloadStatus.COMPLETE) {
                    _uiState.value = _uiState.value.copy(showDeleteDownloadPrompt = true)
                }
            }
        }
    }

    init {
        connectToService()
        restoreLastPlayback()
    }

    /** Restore the last played talk so the mini player shows on app restart. */
    private fun restoreLastPlayback() {
        val lastCatNum = prefs.getString("last_cat_num", null) ?: return
        val lastPos = prefs.getLong("last_position_$lastCatNum", 0)
        val lastTrackIndex = prefs.getInt("last_track_index_$lastCatNum", 0)
        val lastDuration = prefs.getLong("last_duration_$lastCatNum", 0)
        pendingRestore = RestoreState(lastCatNum, lastPos, lastTrackIndex)
        viewModelScope.launch {
            try {
                val talk = talkRepository.getTalkDetail(lastCatNum) ?: return@launch
                if (userInitiatedPlayback) return@launch // user already started something
                val trackDuration = talk.tracks.getOrNull(lastTrackIndex)?.durationSeconds?.let { it * 1000L }
                    ?: lastDuration.takeIf { it > 0 }
                    ?: (talk.durationSeconds * 1000L)
                _uiState.value = _uiState.value.copy(
                    currentTalk = talk,
                    isVisible = true,
                    currentPosition = lastPos.coerceAtMost(trackDuration),
                    duration = trackDuration,
                    currentTrackIndex = lastTrackIndex,
                    isPlaying = false,
                )
                // Live download status for the restored talk — a one-shot snapshot
                // here left the player's download icon stale (e.g. spinning forever
                // after the download finished elsewhere).
                observeDownloadStatus(lastCatNum)
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Failed to restore last playback", e)
                pendingRestore = null
            }
        }
    }

    /** Load media into the controller for a restored session (paused, at saved position). */
    private fun applyPendingRestore() {
        val restore = pendingRestore ?: return
        pendingRestore = null
        val controller = mediaController ?: return

        viewModelScope.launch {
            val prepared = resolver.prepare(restore.catNum) ?: return@launch
            // Bail if the user started their own playback while we were fetching,
            // or the service already has a queue (e.g. playing from the car).
            if (userInitiatedPlayback || controller.mediaItemCount > 0) return@launch

            val startIndex = restore.trackIndex.coerceIn(0, prepared.items.size - 1)
            val startPos = (restore.position - PlaybackResolver.RESUME_REWIND_MS).coerceAtLeast(0)

            // setMediaItems with an explicit start position — no seek-on-ready
            // listener needed (the old one leaked and could hijack later playback).
            controller.setMediaItems(prepared.items, startIndex, startPos)
            controller.prepare()
            controller.pause()

            _uiState.value = _uiState.value.copy(
                currentTalk = prepared.talk,
                isVisible = true,
                currentTrackIndex = startIndex,
                downloadStatus = prepared.download?.status,
                useRemaster = prepared.useRemaster,
                versionLocked = prepared.versionLocked,
            )
            observeDownloadStatus(restore.catNum)
        }
    }

    /** The service started a talk we didn't (Android Auto, resumption): mirror it in the UI. */
    private suspend fun showExternallyStartedTalk(catNum: String) {
        val talk = talkRepository.getTalkDetail(catNum) ?: return
        val download = downloadRepository.getDownload(catNum)
        _uiState.value = _uiState.value.copy(
            currentTalk = talk,
            isVisible = true,
            downloadStatus = download?.status,
            useRemaster = talk.hasRemaster && settings.useRemaster(catNum),
            versionLocked = resolver.hasOfflineAudio(catNum, download),
            playbackError = null,
        )
        observeDownloadStatus(catNum)
    }

    /**
     * Switch between the remastered and original recording of the current talk,
     * keeping the current chapter and position (clamped — the versions differ by
     * a few seconds). Remembered per talk.
     */
    fun setUseRemaster(useRemaster: Boolean) {
        val state = _uiState.value
        val talk = state.currentTalk ?: return
        if (!talk.hasRemaster || state.versionLocked || useRemaster == state.useRemaster) return
        settings.setRemasterChoice(talk.catNum, useRemaster)
        val controller = mediaController ?: return
        viewModelScope.launch {
            val prepared = resolver.prepare(talk.catNum, useRemasterOverride = useRemaster) ?: return@launch
            val index = state.currentTrackIndex.coerceIn(0, prepared.items.size - 1)
            val track = talk.tracks.getOrNull(index)
            val newDurationMs = (if (useRemaster) track?.remasterDurationSeconds else track?.durationSeconds)
                ?.takeIf { it > 0 }?.let { it * 1000L }
            val wasPlaying = controller.playWhenReady
            val position = PlaybackMath.clampPosition(controller.currentPosition, newDurationMs)
            controller.setMediaItems(prepared.items, index, position)
            controller.prepare()
            if (wasPlaying) controller.play()
            _uiState.value = _uiState.value.copy(useRemaster = useRemaster)
        }
    }

    private fun connectToService() {
        try {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(playerListener)
                    controller.setPlaybackSpeed(savedSpeed)
                    // Start polling only if already playing (e.g. after config change)
                    if (controller.isPlaying) startPositionUpdates()
                    controllerReady.complete(controller)
                    // Something is already queued (e.g. the car started a talk while
                    // the UI was dead): show it rather than restoring the last session.
                    val queued = controller.currentMediaItem?.mediaMetadata?.extras?.getString(PlaybackResolver.EXTRA_CAT_NUM)
                    if (queued != null) {
                        pendingRestore = null
                        viewModelScope.launch { showExternallyStartedTalk(queued) }
                        _uiState.value = _uiState.value.copy(currentTrackIndex = controller.currentMediaItemIndex)
                        updatePosition()
                    } else {
                        applyPendingRestore()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Failed to connect to PlaybackService", e)
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Failed to create session token", e)
        }
    }

    private fun startPositionUpdates() {
        if (positionUpdateJob?.isActive == true) return
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                updatePosition()
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updatePosition() {
        val controller = mediaController ?: return
        // Don't overwrite restored state if nothing is loaded in the player
        if (controller.mediaItemCount == 0) return
        _uiState.value = _uiState.value.copy(
            currentPosition = controller.currentPosition.coerceAtLeast(0),
            duration = controller.duration.coerceAtLeast(0),
            isPlaying = controller.isPlayingForUi(),
        )
    }

    /**
     * Start playing a talk, optionally at a specific chapter. Queues all chapters
     * so the player advances between them natively (works in the background too).
     */
    fun playTalk(catNum: String, trackIndex: Int? = null) {
        userInitiatedPlayback = true
        pendingRestore = null
        autoRetryCount = 0
        _uiState.value = _uiState.value.copy(playbackError = null, isReconnecting = false)
        viewModelScope.launch {
            val controller = controllerReady.await()
            val prepared = resolver.prepare(catNum) ?: return@launch
            val (startIndex, startPos) = resolver.resumePoint(prepared.talk, prepared.items.size, trackIndex)

            controller.setMediaItems(prepared.items, startIndex, startPos)
            controller.prepare()
            controller.play()

            _uiState.value = _uiState.value.copy(
                currentTalk = prepared.talk,
                isVisible = true,
                downloadStatus = prepared.download?.status,
                currentTrackIndex = startIndex,
                currentPosition = 0,
                duration = 0,
                useRemaster = prepared.useRemaster,
                versionLocked = prepared.versionLocked,
            )
            observeDownloadStatus(catNum)
        }
    }

    fun downloadCurrentTalk() {
        val talk = _uiState.value.currentTalk ?: return
        viewModelScope.launch {
            val useRemaster = talk.hasRemaster && settings.useRemaster(talk.catNum)
            downloadRepository.startDownload(
                catNum = talk.catNum,
                title = talk.title,
                speaker = talk.speaker,
                imageUrl = talk.imageUrl,
                audioUrl = talk.audioUrl,
                trackUrls = talk.tracks.map { if (useRemaster && it.hasRemaster) it.remasterAudioUrl else it.audioUrl },
                transcriptUrl = talk.transcriptUrl,
                audioVersion = if (useRemaster) "remastered" else "original",
            )
            observeDownloadStatus(talk.catNum)
        }
    }

    private fun observeDownloadStatus(catNum: String) {
        downloadObservationJob?.cancel()
        downloadObservationJob = viewModelScope.launch {
            downloadRepository.observeDownload(catNum).collect { download ->
                _uiState.value = _uiState.value.copy(downloadStatus = download?.status)
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) controller.pause()
            else controller.play()
        }
    }

    /** Explicit pause (e.g. when the Digital Legacy sample starts playing). */
    fun pause() {
        mediaController?.takeIf { it.isPlaying }?.pause()
    }

    /** Manual retry after a playback error (re-prepares the current media item). */
    fun retry() {
        autoRetryCount = 0
        _uiState.value = _uiState.value.copy(playbackError = null, isReconnecting = false)
        mediaController?.run {
            prepare()
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        // Reflect the seek target immediately: position polling pauses while the
        // player re-buffers, so without this a second +10s press wouldn't update
        // the displayed time until buffering finished.
        updatePosition()
    }

    fun seekForward() {
        mediaController?.let { it.seekTo(it.currentPosition + 10_000) }
        updatePosition()
    }

    fun seekBack() {
        mediaController?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) }
        updatePosition()
    }

    fun nextTrack() {
        val talk = _uiState.value.currentTalk ?: return
        val nextIndex = _uiState.value.currentTrackIndex + 1
        if (nextIndex < talk.tracks.size) playTrackByIndex(nextIndex)
    }

    fun previousTrack() {
        val prevIndex = _uiState.value.currentTrackIndex - 1
        if (prevIndex >= 0) playTrackByIndex(prevIndex)
    }

    fun dismissDeletePrompt() {
        _uiState.value = _uiState.value.copy(showDeleteDownloadPrompt = false)
    }

    fun confirmDeleteAfterPlayback() {
        val catNum = _uiState.value.currentTalk?.catNum ?: return
        _uiState.value = _uiState.value.copy(showDeleteDownloadPrompt = false)
        viewModelScope.launch {
            downloadRepository.deleteDownload(catNum)
            _uiState.value = _uiState.value.copy(downloadStatus = null)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        prefs.edit().putFloat("playback_speed", speed).apply()
    }

    /** Jump to a chapter. Uses the loaded queue when present; otherwise (re)loads the talk. */
    fun playTrackByIndex(index: Int) {
        val currentTalk = _uiState.value.currentTalk ?: return
        userInitiatedPlayback = true
        val controller = mediaController
        if (controller != null && index < controller.mediaItemCount && controller.mediaItemCount > 1) {
            controller.seekTo(index, C.TIME_UNSET)
            controller.play()
            _uiState.value = _uiState.value.copy(currentTrackIndex = index)
        } else {
            playTalk(currentTalk.catNum, trackIndex = index)
        }
    }

    override fun onCleared() {
        mediaController?.removeListener(playerListener)
        // releaseFuture handles both the connected and the still-connecting case
        // (a controller that connects after clearing would otherwise leak and
        // keep the PlaybackService bound forever).
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onCleared()
    }
}
