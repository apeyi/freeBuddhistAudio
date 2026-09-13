package com.dharmachakra.fba_android.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.dharmachakra.fba_android.data.local.AppSettings
import com.dharmachakra.fba_android.data.local.DownloadEntity
import com.dharmachakra.fba_android.data.local.DownloadStatus
import com.dharmachakra.fba_android.data.repository.DownloadRepository
import com.dharmachakra.fba_android.data.repository.TalkRepository
import com.dharmachakra.fba_android.download.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** A download plus what's actually stored for it, for the Downloads list. */
data class DownloadRow(
    val download: DownloadEntity,
    val hasAudio: Boolean,
    val hasTranscript: Boolean,
) {
    val catNum: String get() = download.catNum
}

enum class DownloadFilter { ALL, TALKS, TRANSCRIPTS }

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val talkRepository: TalkRepository,
    private val settings: AppSettings,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadRow>> = downloadRepository
        .observeAllDownloads()
        .map { list ->
            list.map { d ->
                DownloadRow(
                    download = d,
                    hasAudio = d.status == DownloadStatus.COMPLETE && d.filePath.isNotBlank(),
                    hasTranscript = File(DownloadWorker.transcriptFilePath(context, d.catNum)).exists(),
                )
            }
        }
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
            val useRemaster = talk?.hasRemaster == true && settings.useRemaster(download.catNum)
            downloadRepository.startDownload(
                catNum = download.catNum,
                title = talk?.title ?: download.title,
                speaker = talk?.speaker ?: download.speaker,
                imageUrl = talk?.imageUrl ?: download.imageUrl,
                audioUrl = talk?.audioUrl ?: "",
                trackUrls = talk?.tracks?.map { if (useRemaster && it.hasRemaster) it.remasterAudioUrl else it.audioUrl } ?: emptyList(),
                transcriptUrl = talk?.transcriptUrl ?: "",
                audioVersion = if (useRemaster) "remastered" else "original",
            )
        }
    }
}
