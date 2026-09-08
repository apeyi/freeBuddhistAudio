package com.fba.app

/**
 * Compile-time switches for features that are built but waiting on a decision
 * or an account (see docs/spec-v1.0-pre-api.md). Flip here only.
 */
object FeatureFlags {
    /** Log in with the FBA (Triratna) account; My FBA account header, history sync. */
    const val AUTH = true

    /** Downloads require membership; Download buttons lead to Join when not a member. */
    const val MEMBERSHIP_GATING = false

    /** Show the Order-only talks entry point for logged-in Order members. */
    const val ORDER_TALKS = false
}
