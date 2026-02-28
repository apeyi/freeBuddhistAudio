package com.fba.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.local.DownloadEntity
import com.fba.app.data.repository.DownloadRepository
import com.fba.app.data.repository.TalkRepository
import com.fba.app.domain.model.Talk
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
                val talk = talkRepository.getTalkDetail(catNum)
                _uiState.value = _uiState.value.copy(talk = talk, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load talk",
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
        viewModelScope.launch {
            downloadRepository.startDownload(
                catNum = talk.catNum,
                title = talk.title,
                speaker = talk.speaker,
                imageUrl = talk.imageUrl,
                audioUrl = talk.audioUrl,
                trackUrls = talk.tracks.map { it.audioUrl },
            )
        }
    }

    fun deleteDownload() {
        viewModelScope.launch {
            downloadRepository.deleteDownload(catNum)
        }
    }
}
