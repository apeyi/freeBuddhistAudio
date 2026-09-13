package com.dharmachakra.fba_android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dharmachakra.fba_android.data.auth.AuthRepository
import com.dharmachakra.fba_android.data.auth.AuthState
import com.dharmachakra.fba_android.data.repository.ContentRepository
import com.dharmachakra.fba_android.domain.model.DigitalLegacy
import com.dharmachakra.fba_android.domain.model.SangharakshitaData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val sangharakshitaTalkCount: Int = SangharakshitaData.allTalksAsSearchResults().size,
    val sangharakshitaSeriesCount: Int = SangharakshitaData.series.size,
    val digitalLegacy: DigitalLegacy? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val content: ContentRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
    val authState: StateFlow<AuthState> = auth.state

    init {
        viewModelScope.launch {
            // Warm the menu cache so Collections/Themes/People/Places open instantly.
            try { content.getMenu() } catch (_: Exception) { }
            _uiState.value = _uiState.value.copy(digitalLegacy = content.getDigitalLegacy())
        }
    }
}
