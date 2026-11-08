package com.dharmachakra.fba_android.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dharmachakra.fba_android.data.auth.AuthRepository
import com.dharmachakra.fba_android.data.local.AppSettings
import com.dharmachakra.fba_android.data.local.DownloadEntity
import com.dharmachakra.fba_android.data.repository.DownloadRepository
import com.dharmachakra.fba_android.data.repository.TalkRepository
import com.dharmachakra.fba_android.domain.model.SearchResult
import com.dharmachakra.fba_android.domain.model.Talk
import com.dharmachakra.fba_android.player.PlaybackResolver
import com.dharmachakra.fba_android.ui.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val talk: Talk? = null,
    val download: DownloadEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Related talks (v2); null while loading. */
    val related: List<SearchResult>? = null,
    /** Enhanced (remastered) chosen for this talk; only meaningful when it has a remaster. */
    val useRemaster: Boolean = true,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val talkRepository: TalkRepository,
    private val downloadRepository: DownloadRepository,
    private val settings: AppSettings,
    private val auth: AuthRepository,
    private val resolver: PlaybackResolver,
) : ViewModel() {

    private val catNum: String = savedStateHandle["catNum"] ?: ""

    private val _uiState = MutableStateFlow(DetailUiState(useRemaster = settings.useRemaster(catNum)))
    val uiState: StateFlow<DetailUiState> = _uiState

    init {
        loadTalk()
        observeDownload()
        loadRelated()
    }

    fun loadTalk() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Throwing variant: a network failure shows the error + Retry UI
                // instead of being conflated with "talk not found" (null).
                // Logged in: always fetch fresh so the page carries the account's saved
                // position and Order-only visibility.
                val talk = talkRepository.fetchTalkDetail(catNum, forceRefresh = auth.isLoggedIn)
                _uiState.value = _uiState.value.copy(
                    talk = talk,
                    isLoading = false,
                    error = if (talk == null) "Talk not found" else null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = friendlyError(e),
                )
            }
        }
    }

    private fun loadRelated() {
        viewModelScope.launch {
            val related = runCatching { talkRepository.getRelatedTalks(catNum) }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(related = related.filter { it.catNum != catNum })
        }
    }

    /**
     * The saved point for this talk when it isn't the one loaded in the player: the account
     * checkpoint wins when signed in (as in PlaybackResolver.resumePoint), else the local one.
     */
    fun savedPoint(talk: Talk): Pair<Int, Long> {
        talk.checkpoint?.let { cp ->
            val index = talk.tracks.indexOfFirst { it.trackId == cp.trackId }
            if (index >= 0) return index to cp.timeSeconds * 1000L
        }
        return resolver.prefs.getInt("last_track_index_$catNum", 0) to
            resolver.prefs.getLong("last_position_$catNum", 0)
    }

    /** Original / Enhanced switch: remembered per talk (the player applies it when this talk plays). */
    fun setUseRemaster(useRemaster: Boolean) {
        settings.setRemasterChoice(catNum, useRemaster)
        _uiState.value = _uiState.value.copy(useRemaster = useRemaster)
    }

    private fun observeDownload() {
        viewModelScope.launch {
            downloadRepository.observeDownload(catNum).collectLatest { download ->
                _uiState.value = _uiState.value.copy(download = download)
            }
        }
    }

    fun startDownload() {
        val talk = _uiState.value.talk ?: return
        val useRemaster = talk.hasRemaster && settings.useRemaster(talk.catNum)
        viewModelScope.launch {
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
        }
    }

    fun startTranscriptDownload() {
        val talk = _uiState.value.talk ?: return
        viewModelScope.launch {
            downloadRepository.startTranscriptDownload(
                catNum = talk.catNum, title = talk.title, speaker = talk.speaker,
                imageUrl = talk.imageUrl, transcriptUrl = talk.transcriptUrl,
            )
        }
    }

    fun deleteDownload() {
        viewModelScope.launch {
            downloadRepository.deleteDownload(catNum)
        }
    }

    /** Cancel an in-flight download — stops the worker and removes partial files. */
    fun cancelDownload() = deleteDownload()
}
