package com.fba.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.auth.AuthRepository
import com.fba.app.data.local.AppSettings
import com.fba.app.data.local.DownloadEntity
import com.fba.app.data.repository.DownloadRepository
import com.fba.app.data.repository.TalkRepository
import com.fba.app.domain.model.Talk
import com.fba.app.ui.friendlyError
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
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val talkRepository: TalkRepository,
    private val downloadRepository: DownloadRepository,
    private val settings: AppSettings,
    private val auth: AuthRepository,
) : ViewModel() {

    private val catNum: String = savedStateHandle["catNum"] ?: ""

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    init {
        loadTalk()
        observeDownload()
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
