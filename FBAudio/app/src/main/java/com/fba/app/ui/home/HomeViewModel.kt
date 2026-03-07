package com.fba.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.local.DownloadDao
import com.fba.app.data.local.DownloadStatus
import com.fba.app.data.local.RecentlyListenedDao
import com.fba.app.data.local.RecentlyListenedEntity
import com.fba.app.domain.model.SangharakshitaData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentlyListened: List<RecentlyListenedEntity> = emptyList(),
    val downloadedCatNums: Set<String> = emptySet(),
    val sangharakshitaImageUrl: String = "https://www.freebuddhistaudio.com/m/uKp8bNgbigXw.jpg",
    val sangharakshitaTalkCount: Int = SangharakshitaData.allTalksAsSearchResults().size,
    val sangharakshitaSeriesCount: Int = SangharakshitaData.series.size,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recentlyListenedDao: RecentlyListenedDao,
    private val downloadDao: DownloadDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        observeRecentlyListened()
    }

    private fun observeRecentlyListened() {
        viewModelScope.launch {
            combine(
                recentlyListenedDao.getRecentlyListened(),
                downloadDao.getCompletedDownloads(),
            ) { recent, downloads ->
                val downloadedSet = downloads.filter { it.status == DownloadStatus.COMPLETE }.map { it.catNum }.toSet()
                recent to downloadedSet
            }.collect { (recent, downloadedSet) ->
                _uiState.value = _uiState.value.copy(
                    recentlyListened = recent,
                    downloadedCatNums = downloadedSet,
                )
            }
        }
    }
}
