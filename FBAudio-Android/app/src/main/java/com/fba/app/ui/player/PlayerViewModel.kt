package com.fba.app.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fba.app.data.local.DownloadEntity
import com.fba.app.data.local.DownloadStatus
import com.fba.app.data.local.RecentlyListenedDao
import com.fba.app.data.local.RecentlyListenedEntity
import com.fba.app.data.repository.DownloadRepository
import com.fba.app.data.repository.TalkRepository
import com.fba.app.domain.model.Talk
import com.fba.app.download.DownloadWorker
import com.fba.app.player.PlaybackService
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
import java.io.File
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
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val talkRepository: TalkRepository,
    private val downloadRepository: DownloadRepository,
    private val recentlyListenedDao: RecentlyListenedDao,
    private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
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
    private var lastSaveTime: Long = 0
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
                savePlaybackState()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The whole talk is queued as one playlist; track changes (user seeks
            // and automatic chapter advance — which now also works while the app
            // UI is dead, since the queue lives in the service's player) land here.
            val index = mediaController?.currentMediaItemIndex ?: return
            if (index != _uiState.value.currentTrackIndex) {
                _uiState.value = _uiState.value.copy(currentTrackIndex = index)
                savePlaybackState()
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
                // Whole queue finished (chapter advance is handled by the player).
                val state = _uiState.value
                val talk = state.currentTalk ?: return
                // Clear the saved resume point so replaying starts from the
                // beginning instead of "resuming" the final 10 seconds.
                prefs.edit()
                    .remove("last_position_${talk.catNum}")
                    .remove("last_track_index_${talk.catNum}")
                    .commit()
                if (state.downloadStatus == DownloadStatus.COMPLETE) {
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
            // Fetch talk (may already be cached in Room)
            val talk = talkRepository.getTalkDetail(restore.catNum) ?: return@launch
            val download = downloadRepository.getDownload(restore.catNum)
            // Bail if the user started their own playback while we were fetching.
            if (userInitiatedPlayback || controller.mediaItemCount > 0) return@launch

            val items = buildMediaItems(talk, download)
            if (items.isEmpty()) return@launch
            val startIndex = restore.trackIndex.coerceIn(0, items.size - 1)
            val startPos = (restore.position - 10_000).coerceAtLeast(0)

            // setMediaItems with an explicit start position — no seek-on-ready
            // listener needed (the old one leaked and could hijack later playback).
            controller.setMediaItems(items, startIndex, startPos)
            controller.prepare()
            controller.pause()

            _uiState.value = _uiState.value.copy(
                currentTalk = talk,
                isVisible = true,
                currentTrackIndex = startIndex,
                downloadStatus = download?.status,
            )
            observeDownloadStatus(restore.catNum)
        }
    }

    /**
     * Resolve the playable URI for one track of a talk, preferring offline files:
     * per-track download file, then (track 0 only) the whole-talk download file,
     * then the stream URL.
     */
    private fun resolveTrackUri(
        catNum: String,
        trackIndex: Int,
        streamUrl: String,
        download: DownloadEntity?,
    ): Uri? {
        val trackFile = File(DownloadWorker.trackFilePath(context, catNum, trackIndex))
        if (trackFile.exists()) return Uri.fromFile(trackFile)
        if (trackIndex == 0 && download?.status == DownloadStatus.COMPLETE && download.filePath.isNotBlank()) {
            val mainFile = File(download.filePath)
            if (mainFile.exists()) return Uri.fromFile(mainFile)
        }
        return if (streamUrl.isNotBlank()) Uri.parse(streamUrl) else null
    }

    /** Build the full playlist for a talk — one MediaItem per chapter (or one for single-track talks). */
    private fun buildMediaItems(talk: Talk, download: DownloadEntity?): List<MediaItem> {
        fun item(uri: Uri, chapterTitle: String): MediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(talk.title)
                    .setSubtitle(chapterTitle.takeIf { it.isNotBlank() } ?: "")
                    .setArtist(talk.speaker)
                    .setArtworkUri(if (talk.imageUrl.isNotBlank()) Uri.parse(talk.imageUrl) else null)
                    .build()
            )
            .build()

        if (talk.tracks.isEmpty()) {
            val uri = resolveTrackUri(talk.catNum, 0, talk.audioUrl, download) ?: return emptyList()
            return listOf(item(uri, ""))
        }
        return talk.tracks.mapIndexedNotNull { index, track ->
            resolveTrackUri(talk.catNum, index, track.audioUrl, download)?.let { item(it, track.title) }
        }
    }

    /** Save current playback state immediately (uses commit for reliability). */
    private fun savePlaybackState() {
        val state = _uiState.value
        val talk = state.currentTalk ?: return
        val catNum = talk.catNum
        val controller = mediaController
        val pos = controller?.currentPosition?.coerceAtLeast(0) ?: state.currentPosition
        val dur = controller?.duration?.coerceAtLeast(0) ?: state.duration
        if (pos <= 0 && dur <= 0) return // don't overwrite good data with zeros
        prefs.edit()
            .putString("last_cat_num", catNum)
            .putLong("last_position_$catNum", pos)
            .putInt("last_track_index_$catNum", state.currentTrackIndex)
            .putLong("last_duration_$catNum", dur)
            .commit() // sync write — survives process death

        // Update recently listened — compute cumulative position across all chapters.
        // App scope, not viewModelScope: this is also called from onCleared, where
        // viewModelScope is already cancelled and the write would be dropped.
        appScope.launch {
            val trackIndex = state.currentTrackIndex
            val tracks = talk.tracks
            val cumulativePos = PlaybackMath.cumulativePositionMs(tracks, trackIndex, pos)
            val totalDur = PlaybackMath.totalDurationSeconds(talk.durationSeconds, tracks, dur)

            recentlyListenedDao.upsert(
                RecentlyListenedEntity(
                    catNum = catNum,
                    title = talk.title,
                    speaker = talk.speaker,
                    imageUrl = talk.imageUrl,
                    positionMs = cumulativePos,
                    durationMs = dur,
                    trackIndex = trackIndex,
                    totalDurationSeconds = totalDur,
                )
            )
            recentlyListenedDao.pruneOld()
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
                    applyPendingRestore()
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
        val pos = controller.currentPosition.coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(
            currentPosition = pos,
            duration = controller.duration.coerceAtLeast(0),
            isPlaying = controller.isPlayingForUi(),
        )
        // Save position every 5 seconds for resume
        if (controller.isPlaying) {
            val now = System.currentTimeMillis()
            if (now - lastSaveTime > 5000) {
                lastSaveTime = now
                savePlaybackState()
            }
        }
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
            val talk = talkRepository.getTalkDetail(catNum)
            val download = downloadRepository.getDownload(catNum)

            // Build a Talk for the UI/queue even if network fetch failed (offline, downloaded)
            val effectiveTalk = talk ?: Talk(
                catNum = catNum,
                title = download?.title ?: "",
                speaker = download?.speaker ?: "",
                year = 0, genre = "", durationSeconds = 0,
                imageUrl = download?.imageUrl ?: "",
                audioUrl = "",
                description = "",
            )

            val items = buildMediaItems(effectiveTalk, download)
            if (items.isEmpty()) return@launch

            val savedTrackIndex = prefs.getInt("last_track_index_$catNum", 0)
            val savedPos = prefs.getLong("last_position_$catNum", 0)
            val startIndex = (trackIndex ?: savedTrackIndex).coerceIn(0, items.size - 1)
            // Resume position only applies to the track it was saved against.
            // INTENTIONAL: resume 10s BEFORE the saved position — after time away
            // from a talk, a short repeat re-establishes context. Don't "fix" this
            // to resume exactly. (Same convention on iOS.)
            val startPos = if (startIndex == savedTrackIndex && savedPos > 10_000) {
                savedPos - 10_000
            } else {
                C.TIME_UNSET
            }

            controller.setMediaItems(items, startIndex, startPos)
            controller.prepare()
            controller.play()

            _uiState.value = _uiState.value.copy(
                currentTalk = effectiveTalk,
                isVisible = true,
                downloadStatus = download?.status,
                currentTrackIndex = startIndex,
                currentPosition = 0,
                duration = 0,
            )

            observeDownloadStatus(catNum)
        }
    }

    fun downloadCurrentTalk() {
        val talk = _uiState.value.currentTalk ?: return
        viewModelScope.launch {
            downloadRepository.startDownload(
                catNum = talk.catNum,
                title = talk.title,
                speaker = talk.speaker,
                imageUrl = talk.imageUrl,
                audioUrl = talk.audioUrl,
                trackUrls = talk.tracks.map { it.audioUrl },
                transcriptUrl = talk.transcriptUrl,
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
        savePlaybackState()
        mediaController?.removeListener(playerListener)
        // releaseFuture handles both the connected and the still-connecting case
        // (a controller that connects after clearing would otherwise leak and
        // keep the PlaybackService bound forever).
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onCleared()
    }
}
