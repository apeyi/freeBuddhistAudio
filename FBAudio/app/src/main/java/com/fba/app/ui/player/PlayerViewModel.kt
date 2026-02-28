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
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
    private val savedSpeed = prefs.getFloat("playback_speed", 1.0f)

    private val _uiState = MutableStateFlow(PlayerUiState(playbackSpeed = savedSpeed))
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var mediaController: MediaController? = null
    private var downloadObservationJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePosition()
        }
    }

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(playerListener)
            mediaController?.setPlaybackSpeed(savedSpeed)
            startPositionUpdates()
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
        _uiState.value = _uiState.value.copy(
            currentPosition = controller.currentPosition.coerceAtLeast(0),
            duration = controller.duration.coerceAtLeast(0),
            isPlaying = controller.isPlaying,
        )
    }

    fun playTalk(catNum: String) {
        viewModelScope.launch {
            val talk = talkRepository.getTalkDetail(catNum)
            val download = downloadRepository.getDownload(catNum)

            // Determine audio source: prefer downloaded file, fall back to stream
            val trackFile = DownloadWorker.trackFilePath(context, catNum, 0)
            val audioUri = when {
                download?.status == DownloadStatus.COMPLETE && java.io.File(trackFile).exists() ->
                    Uri.parse("file://$trackFile")
                download?.status == DownloadStatus.COMPLETE && download.filePath.isNotBlank() ->
                    Uri.parse("file://${download.filePath}")
                talk != null -> Uri.parse(talk.audioUrl)
                else -> return@launch // no talk info and no download
            }

            val title = talk?.title ?: download?.title ?: ""
            val speaker = talk?.speaker ?: download?.speaker ?: ""
            val imageUrl = talk?.imageUrl ?: download?.imageUrl ?: ""

            setMediaAndPlay(audioUri, title, speaker, imageUrl)

            // Build a Talk for the UI even if network fetch failed (offline)
            val displayTalk = talk ?: Talk(
                catNum = catNum, title = title, speaker = speaker,
                year = 0, genre = "", durationSeconds = 0,
                imageUrl = imageUrl, audioUrl = download?.filePath ?: "",
                description = "",
            )

            _uiState.value = _uiState.value.copy(
                currentTalk = displayTalk,
                isVisible = true,
                downloadStatus = download?.status,
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
            _uiState.value = _uiState.value.copy(currentTrackIndex = index)
        }
    }

    override fun onCleared() {
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        super.onCleared()
    }
}
