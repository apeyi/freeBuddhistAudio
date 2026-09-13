package com.dharmachakra.fba_android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dharmachakra.fba_android.data.auth.AuthRepository
import com.dharmachakra.fba_android.data.auth.MembershipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-wide state needed by the shell (tab bar): membership for download gating. */
@HiltViewModel
class AppViewModel @Inject constructor(
    membership: MembershipRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    val isMember: StateFlow<Boolean> = membership.isMember
        .stateIn(viewModelScope, SharingStarted.Eagerly, membership.isMember.value)

    init {
        // Re-verify the stored session on launch and load the account header
        // (name/email/avatar). refresh() no-ops when not logged in.
        viewModelScope.launch { auth.refresh() }
    }
}
