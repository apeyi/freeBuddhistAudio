package com.dharmachakra.fba_android.ui.myfba

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dharmachakra.fba_android.data.auth.AuthRepository
import com.dharmachakra.fba_android.data.auth.AuthState
import com.dharmachakra.fba_android.data.local.AppSettings
import com.dharmachakra.fba_android.data.local.DownloadDao
import com.dharmachakra.fba_android.data.local.DownloadStatus
import com.dharmachakra.fba_android.data.local.RecentlyListenedDao
import com.dharmachakra.fba_android.data.local.RecentlyListenedEntity
import com.dharmachakra.fba_android.data.repository.ContentRepository
import com.dharmachakra.fba_android.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyFbaUiState(
    val recentlyListened: List<RecentlyListenedEntity> = emptyList(),
    val downloadedCatNums: Set<String> = emptySet(),
    /** Completed audio downloads: count and bytes on the phone. */
    val downloadCount: Int = 0,
    val downloadBytes: Long = 0,
) {
    /** Unfinished talks played in the last 90 days, most recent first (Continue listening). */
    val continueListening: List<RecentlyListenedEntity>
        get() = MyFbaRules.continueListening(recentlyListened, System.currentTimeMillis())
    val isNewListener: Boolean get() = recentlyListened.isEmpty()
}

@HiltViewModel
class MyFbaViewModel @Inject constructor(
    downloadDao: DownloadDao,
    private val settings: AppSettings,
    private val auth: AuthRepository,
    private val history: HistoryRepository,
    private val content: ContentRepository,
    private val recentlyListenedDao: RecentlyListenedDao,
    private val downloads: com.dharmachakra.fba_android.data.repository.DownloadRepository,
    private val resolver: com.dharmachakra.fba_android.player.PlaybackResolver,
) : ViewModel() {

    /** Mark a talk finished (Continue listening swipe): it leaves the list. */
    fun markFinished(catNum: String) {
        viewModelScope.launch {
            recentlyListenedDao.markFinished(catNum)
            resolver.prefs.edit().remove("last_position_$catNum").remove("last_track_index_$catNum").apply()
        }
    }

    /** Clear listening history on the phone (resume points too), and optionally on the account. */
    fun clearHistory(alsoAccount: Boolean) {
        viewModelScope.launch {
            val cats = recentlyListenedDao.getAllOnce().map { it.catNum }
            if (alsoAccount) history.deleteFromAccount(cats)
            recentlyListenedDao.deleteAll()
            val prefs = resolver.prefs
            prefs.edit().apply {
                prefs.all.keys.filter { it.startsWith("last_position_") || it.startsWith("last_track_index_") || it.startsWith("last_duration_") }
                    .forEach { remove(it) }
            }.apply()
        }
    }

    fun removeAllDownloads() {
        viewModelScope.launch { downloads.deleteAllDownloads() }
    }

    private val _uiState = MutableStateFlow(MyFbaUiState())
    val uiState: StateFlow<MyFbaUiState> = _uiState
    val authState: StateFlow<AuthState> = auth.state
    val englishOnly: StateFlow<Boolean> = settings.englishOnly
    val preferRemastered: StateFlow<Boolean> = settings.preferRemastered
    val wifiOnlyDownloads: StateFlow<Boolean> = settings.wifiOnlyDownloads

    init {
        viewModelScope.launch {
            combine(
                recentlyListenedDao.getRecentlyListened(),
                downloadDao.getCompletedDownloads(),
            ) { recent, downloads ->
                recent to downloads.filter { it.status == DownloadStatus.COMPLETE && it.filePath.isNotBlank() }
            }.collect { (recent, done) ->
                _uiState.value = MyFbaUiState(
                    recentlyListened = recent,
                    downloadedCatNums = done.map { it.catNum }.toSet(),
                    downloadCount = done.size,
                    downloadBytes = done.sumOf { it.totalBytes.coerceAtLeast(it.downloadedBytes) },
                )
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

    fun setWifiOnlyDownloads(value: Boolean) = settings.setWifiOnlyDownloads(value)
}
