package com.fba.app.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the user is a paying member (downloads, later transcript search).
 * Membership = an active store subscription OR an entitlement on the FBA account.
 * Neither exists yet: the store products are not set up and the FBA API has no
 * membership field — so this is a single seam that both can plug into later.
 */
class MembershipRepository {
    private val _isMember = MutableStateFlow(false)
    val isMember: StateFlow<Boolean> = _isMember

    /** Called by the store billing integration when it lands. */
    fun setStoreSubscriptionActive(active: Boolean) {
        _isMember.value = active
    }
}
