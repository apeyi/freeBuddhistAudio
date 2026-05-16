package com.fba.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.local.DownloadEntity
import com.fba.app.data.repository.DownloadRepository
import com.fba.app.data.repository.TalkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val talkRepository: TalkRepository,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = downloadRepository
        .observeAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteDownload(catNum: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(catNum)
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            downloadRepository.deleteAllDownloads()
        }
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch {
            // Delete the failed entry first
            downloadRepository.deleteDownload(download.catNum)
            // Fetch talk detail to get audio/track/transcript URLs
            val talk = talkRepository.getTalkDetail(download.catNum)
            downloadRepository.startDownload(
                catNum = download.catNum,
                title = talk?.title ?: download.title,
                speaker = talk?.speaker ?: download.speaker,
                imageUrl = talk?.imageUrl ?: download.imageUrl,
                audioUrl = talk?.audioUrl ?: "",
                trackUrls = talk?.tracks?.map { it.audioUrl } ?: emptyList(),
                transcriptUrl = talk?.transcriptUrl ?: "",
            )
        }
    }
}
