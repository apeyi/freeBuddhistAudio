package com.fba.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fba.app.data.auth.MembershipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-wide state needed by the shell (tab bar): membership for download gating. */
@HiltViewModel
class AppViewModel @Inject constructor(
    membership: MembershipRepository,
) : ViewModel() {
    val isMember: StateFlow<Boolean> = membership.isMember
        .stateIn(viewModelScope, SharingStarted.Eagerly, membership.isMember.value)
}
