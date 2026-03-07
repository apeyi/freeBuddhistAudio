package com.fba.app.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fba.app.data.local.DownloadStatus
import com.fba.app.data.local.RecentlyListenedDao
import com.fba.app.data.local.RecentlyListenedEntity
import com.fba.app.data.repository.DownloadRepository
import com.fba.app.data.repository.TalkRepository
import com.fba.app.domain.model.Talk
import com.fba.app.download.DownloadWorker
import com.fba.app.player.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val talkRepository: TalkRepository,
    private val downloadRepository: DownloadRepository,
    private val recentlyListenedDao: RecentlyListenedDao,
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
    private val savedSpeed = prefs.getFloat("playback_speed", 1.0f)

    private val _uiState = MutableStateFlow(PlayerUiState(playbackSpeed = savedSpeed))
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var mediaController: MediaController? = null
    private var downloadObservationJob: Job? = null
    private var lastSaveTime: Long = 0
    private var pendingRestore: RestoreState? = null

    private data class RestoreState(val catNum: String, val position: Long, val trackIndex: Int)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            // Save state when pausing (might be last chance before process death)
            if (!isPlaying) savePlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePosition()
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
            val talk = talkRepository.getTalkDetail(lastCatNum) ?: return@launch
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
        }
    }

    /** Load media into the controller for a restored session (paused, seeked to position). */
    private fun applyPendingRestore() {
        val restore = pendingRestore ?: return
        pendingRestore = null
        val controller = mediaController ?: return

        viewModelScope.launch {
            // Fetch talk (may already be cached in Room)
            val talk = talkRepository.getTalkDetail(restore.catNum) ?: return@launch
            val trackIndex = restore.trackIndex
            val track = talk.tracks.getOrNull(trackIndex)
            val audioUrl = track?.audioUrl ?: talk.audioUrl

            // Check for downloaded file
            val trackFile = java.io.File(com.fba.app.download.DownloadWorker.trackFilePath(context, restore.catNum, trackIndex))
            val download = downloadRepository.getDownload(restore.catNum)
            val uri = when {
                trackFile.exists() -> Uri.parse("file://${trackFile.absolutePath}")
                trackIndex == 0 && download?.status == DownloadStatus.COMPLETE && download.filePath.isNotBlank() ->
                    Uri.parse("file://${download.filePath}")
                else -> Uri.parse(audioUrl)
            }

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track?.title?.ifBlank { talk.title } ?: talk.title)
                        .setArtist(talk.speaker)
                        .setArtworkUri(if (talk.imageUrl.isNotBlank()) Uri.parse(talk.imageUrl) else null)
                        .build()
                )
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()

            // Seek after player is ready
            val seekPos = (restore.position - 10_000).coerceAtLeast(0)
            controller.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        controller.seekTo(seekPos)
                        controller.pause()
                        controller.removeListener(this)
                    }
                }
            })

            _uiState.value = _uiState.value.copy(
                currentTalk = talk,
                isVisible = true,
                currentTrackIndex = trackIndex,
                downloadStatus = download?.status,
            )
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

        // Update recently listened — compute cumulative position across all chapters
        viewModelScope.launch {
            val trackIndex = state.currentTrackIndex
            val tracks = talk.tracks
            val cumulativePos = if (tracks.size > 1) {
                val priorDuration = tracks.take(trackIndex).sumOf { it.durationSeconds.toLong() * 1000 }
                priorDuration + pos
            } else pos
            // Use talk.durationSeconds if valid, otherwise derive from player duration
            val totalDur = if (talk.durationSeconds > 0) {
                talk.durationSeconds
            } else if (tracks.size > 1) {
                tracks.sumOf { it.durationSeconds }
            } else {
                (dur / 1000).toInt()
            }

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
        }
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(playerListener)
            mediaController?.setPlaybackSpeed(savedSpeed)
            startPositionUpdates()
            applyPendingRestore()
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (isActive) {
                updatePosition()
                delay(500)
            }
        }
    }

    private fun updatePosition() {
        val controller = mediaController ?: return
        // Don't overwrite restored state if nothing is loaded in the player
        if (controller.mediaItemCount == 0) return
        val pos = controller.currentPosition.coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(
            currentPosition = pos,
            duration = controller.duration.coerceAtLeast(0),
            isPlaying = controller.isPlaying,
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

    fun playTalk(catNum: String) {
        viewModelScope.launch {
            val talk = talkRepository.getTalkDetail(catNum)
            val download = downloadRepository.getDownload(catNum)

            val savedTrackIndex = prefs.getInt("last_track_index_$catNum", 0)
            val savedPos = prefs.getLong("last_position_$catNum", 0)
            val tracks = talk?.tracks ?: emptyList()
            val resumeTrackIndex = if (savedTrackIndex < tracks.size) savedTrackIndex else 0

            // Determine audio source for the correct track
            val trackFile = java.io.File(DownloadWorker.trackFilePath(context, catNum, resumeTrackIndex))
            val mainDownloadFile = download?.filePath?.let { java.io.File(it) }
            val track = tracks.getOrNull(resumeTrackIndex)
            val audioUri = when {
                trackFile.exists() -> Uri.parse("file://${trackFile.absolutePath}")
                download?.status == DownloadStatus.COMPLETE && mainDownloadFile?.exists() == true ->
                    Uri.parse("file://${mainDownloadFile.absolutePath}")
                track != null -> Uri.parse(track.audioUrl)
                talk != null -> Uri.parse(talk.audioUrl)
                download?.status == DownloadStatus.COMPLETE && download.filePath.isNotBlank() ->
                    Uri.parse("file://${download.filePath}")
                else -> return@launch
            }

            val titleText = talk?.title ?: download?.title ?: ""
            val speakerText = talk?.speaker ?: download?.speaker ?: ""
            val imageUrlText = talk?.imageUrl ?: download?.imageUrl ?: ""

            setMediaAndPlay(audioUri, track?.title?.ifBlank { titleText } ?: titleText, speakerText, imageUrlText)

            // Resume from saved position (10s earlier for context)
            if (savedPos > 10_000) {
                mediaController?.seekTo((savedPos - 10_000).coerceAtLeast(0))
            }

            // Build a Talk for the UI even if network fetch failed (offline)
            val displayTalk = talk ?: Talk(
                catNum = catNum, title = titleText, speaker = speakerText,
                year = 0, genre = "", durationSeconds = 0,
                imageUrl = imageUrlText, audioUrl = download?.filePath ?: "",
                description = "",
            )

            _uiState.value = _uiState.value.copy(
                currentTalk = displayTalk,
                isVisible = true,
                downloadStatus = download?.status,
                currentTrackIndex = resumeTrackIndex,
            )

            observeDownloadStatus(catNum)
        }
    }

    /** Play a specific track URL (for multi-track talks). */
    fun playTrack(audioUrl: String, title: String, speaker: String, imageUrl: String, catNum: String) {
        viewModelScope.launch {
            val talk = talkRepository.getTalkDetail(catNum)
            setMediaAndPlay(Uri.parse(audioUrl), title, speaker, imageUrl)
            _uiState.value = _uiState.value.copy(
                currentTalk = talk,
                isVisible = true,
            )
        }
    }

    private fun setMediaAndPlay(uri: Uri, title: String, speaker: String, imageUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(speaker)
                    .setArtworkUri(if (imageUrl.isNotBlank()) Uri.parse(imageUrl) else null)
                    .build()
            )
            .build()
        mediaController?.run {
            setMediaItem(mediaItem)
            prepare()
            play()
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

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun seekForward() {
        mediaController?.let { it.seekTo(it.currentPosition + 10_000) }
    }

    fun seekBack() {
        mediaController?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) }
    }

    fun nextTrack() {
        val talk = _uiState.value.currentTalk ?: return
        val nextIndex = _uiState.value.currentTrackIndex + 1
        if (nextIndex < talk.tracks.size) playTrackByIndex(nextIndex)
    }

    fun previousTrack() {
        val talk = _uiState.value.currentTalk ?: return
        val prevIndex = _uiState.value.currentTrackIndex - 1
        if (prevIndex >= 0) playTrackByIndex(prevIndex)
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        prefs.edit().putFloat("playback_speed", speed).apply()
    }

    fun playTrackByIndex(index: Int) {
        val currentTalk = _uiState.value.currentTalk ?: return
        val track = currentTalk.tracks.getOrNull(index) ?: return
        viewModelScope.launch {
            // Check if this track is available offline via download
            val trackFile = java.io.File(DownloadWorker.trackFilePath(context, currentTalk.catNum, index))
            val uri = if (trackFile.exists()) {
                Uri.parse("file://${trackFile.absolutePath}")
            } else {
                Uri.parse(track.audioUrl)
            }
            setMediaAndPlay(
                uri,
                track.title.ifBlank { currentTalk.title },
                currentTalk.speaker,
                currentTalk.imageUrl,
            )
            // Resume saved position for this track
            val savedTrackIndex = prefs.getInt("last_track_index_${currentTalk.catNum}", -1)
            val savedPos = prefs.getLong("last_position_${currentTalk.catNum}", 0)
            if (savedTrackIndex == index && savedPos > 10_000) {
                mediaController?.seekTo((savedPos - 10_000).coerceAtLeast(0))
            }
            _uiState.value = _uiState.value.copy(currentTrackIndex = index)
        }
    }

    override fun onCleared() {
        savePlaybackState()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        super.onCleared()
    }
}
