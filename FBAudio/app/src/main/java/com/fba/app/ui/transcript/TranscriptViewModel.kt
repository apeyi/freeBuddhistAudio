package com.fba.app.ui.transcript

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.repository.TalkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranscriptUiState(
    val text: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class TranscriptViewModel @Inject constructor(
    private val repository: TalkRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranscriptUiState())
    val uiState: StateFlow<TranscriptUiState> = _uiState

    init {
        val url: String? = savedStateHandle["transcriptUrl"]
        if (!url.isNullOrBlank()) {
            val decodedUrl = java.net.URLDecoder.decode(url, "UTF-8")
            loadTranscript(decodedUrl)
        } else {
            _uiState.value = TranscriptUiState(isLoading = false, error = "No transcript URL")
        }
    }

    private fun loadTranscript(url: String) {
        viewModelScope.launch {
            try {
                val text = repository.fetchTranscript(url)
                _uiState.value = TranscriptUiState(text = text, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = TranscriptUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load transcript",
                )
            }
        }
    }
}
