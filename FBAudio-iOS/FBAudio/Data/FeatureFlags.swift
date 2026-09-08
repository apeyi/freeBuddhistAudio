import Foundation

/// Compile-time switches for features that are built but waiting on a decision
/// or an account (see docs/spec-v1.0-pre-api.md). Keep in step with Android.
enum FeatureFlags {
    /// Log in with the FBA (Triratna) account; My FBA account header, history sync.
    static let auth = true

    /// Downloads require membership; Download buttons lead to Join when not a member.
    static let membershipGating = false

    /// Show the Order-only talks entry point for logged-in Order members.
    static let orderTalks = false
}
