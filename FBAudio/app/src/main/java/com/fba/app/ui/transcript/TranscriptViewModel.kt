package com.fba.app.ui.transcript

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.repository.TalkRepository
import com.fba.app.download.DownloadWorker
import com.fba.app.ui.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TranscriptUiState(
    val text: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class TranscriptViewModel @Inject constructor(
    private val repository: TalkRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranscriptUiState())
    val uiState: StateFlow<TranscriptUiState> = _uiState

    init {
        val url: String? = savedStateHandle["transcriptUrl"]
        val catNum: String? = savedStateHandle["catNum"]
        if (!url.isNullOrBlank()) {
            val decodedUrl = java.net.URLDecoder.decode(url, "UTF-8")
            loadTranscript(decodedUrl, catNum)
        } else {
            _uiState.value = TranscriptUiState(isLoading = false, error = "No transcript URL")
        }
    }

    private fun loadTranscript(url: String, catNum: String?) {
        viewModelScope.launch {
            // Check for downloaded transcript first
            if (!catNum.isNullOrBlank()) {
                val file = File(DownloadWorker.transcriptFilePath(context, catNum))
                if (file.exists()) {
                    val text = file.readText()
                    if (text.isNotBlank()) {
                        _uiState.value = TranscriptUiState(text = text, isLoading = false)
                        return@launch
                    }
                }
            }

            try {
                val text = repository.fetchTranscript(url)
                _uiState.value = TranscriptUiState(text = text, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = TranscriptUiState(
                    isLoading = false,
                    error = friendlyError(e),
                )
            }
        }
    }
}
