package com.fba.app.ui.myfba

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.auth.AuthRepository
import com.fba.app.data.auth.AuthState
import com.fba.app.data.local.AppSettings
import com.fba.app.data.local.DownloadDao
import com.fba.app.data.local.DownloadStatus
import com.fba.app.data.local.RecentlyListenedDao
import com.fba.app.data.local.RecentlyListenedEntity
import com.fba.app.data.repository.ContentRepository
import com.fba.app.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyFbaUiState(
    val recentlyListened: List<RecentlyListenedEntity> = emptyList(),
    val downloadedCatNums: Set<String> = emptySet(),
)

@HiltViewModel
class MyFbaViewModel @Inject constructor(
    recentlyListenedDao: RecentlyListenedDao,
    downloadDao: DownloadDao,
    private val settings: AppSettings,
    private val auth: AuthRepository,
    private val history: HistoryRepository,
    private val content: ContentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyFbaUiState())
    val uiState: StateFlow<MyFbaUiState> = _uiState
    val authState: StateFlow<AuthState> = auth.state
    val englishOnly: StateFlow<Boolean> = settings.englishOnly
    val preferRemastered: StateFlow<Boolean> = settings.preferRemastered

    init {
        viewModelScope.launch {
            combine(
                recentlyListenedDao.getRecentlyListened(),
                downloadDao.getCompletedDownloads(),
            ) { recent, downloads ->
                recent to downloads.filter { it.status == DownloadStatus.COMPLETE && it.filePath.isNotBlank() }.map { it.catNum }.toSet()
            }.collect { (recent, downloaded) ->
                _uiState.value = MyFbaUiState(recentlyListened = recent, downloadedCatNums = downloaded)
            }
        }
    }

    fun syncHistory() {
        viewModelScope.launch { history.syncFromServer() }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }

    fun setEnglishOnly(value: Boolean) {
        settings.setEnglishOnly(value)
        content.invalidateMemo()
    }

    fun setPreferRemastered(value: Boolean) = settings.setPreferRemastered(value)
}
