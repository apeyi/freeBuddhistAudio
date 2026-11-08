import SwiftUI

/// Now Playing bar (Figma MiniPlayer): sits above the tab bar once something has been played.
/// Artwork, one-line title, speaker (or "Continue listening · 18 min left" for the talk restored
/// at launch), play/pause. Tap opens the full-screen player; swiping it away while paused
/// clears it until something is played again. Mirrors Android `MiniPlayer`.
struct MiniPlayer: View {
    @ObservedObject var player: AudioPlayer
    let onExpand: () -> Void
    @State private var dragX: CGFloat = 0

    var body: some View {
        if player.isVisible, let talk = player.currentTalk {
            HStack(spacing: 12) {
                TalkArtwork(talk: talk)
                    .frame(width: 40, height: 40)
                    .clipShape(RoundedRectangle(cornerRadius: FbaDimens.radiusThumb))

                VStack(alignment: .leading, spacing: 1) {
                    Text(talk.title)
                        .font(.subheadline.weight(.medium))     // 15 Medium
                        .foregroundStyle(FbaColors.black)
                        .lineLimit(1)
                    Text(player.continueListeningLabel ?? talk.speaker)
                        .font(.footnote)                         // 13
                        .foregroundStyle(FbaColors.meta)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Button(action: player.togglePlayPause) {
                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                        .font(.title3)
                        .foregroundStyle(FbaColors.black)
                        .frame(width: FbaDimens.minTouch, height: FbaDimens.minTouch)
                }
                .accessibilityLabel(player.isPlaying ? "Pause" : "Play")
            }
            .padding(.leading, 10)
            .padding(.trailing, 4)
            .frame(height: 56)
            .background(FbaColors.grey50, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.black.opacity(0.06), lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: 12))
            .onTapGesture(perform: onExpand)
            .offset(x: dragX)
            .opacity(1 - min(abs(dragX) / 300, 0.6))
            .gesture(
                DragGesture(minimumDistance: 20)
                    .onChanged { value in
                        guard !player.isPlaying, abs(value.translation.width) > abs(value.translation.height) else { return }
                        dragX = value.translation.width
                    }
                    .onEnded { value in
                        if !player.isPlaying, abs(value.translation.width) > 120 {
                            player.dismissNowPlaying()
                        }
                        withAnimation(.spring()) { dragX = 0 }
                    }
            )
            .accessibilityElement(children: .contain)
            .accessibilityAction(named: "Open player", onExpand)
            .accessibilityAction(named: "Clear") { if !player.isPlaying { player.dismissNowPlaying() } }
            .padding(.horizontal, FbaDimens.margin)
            .padding(.vertical, 8)
        }
    }
}

extension View {
    /// Reserves bottom space matching the floating mini player's height (when a
    /// talk is loaded) so a scroll view's content isn't hidden behind it. The
    /// space is sized by an invisible copy of the real player — no hardcoded
    /// height — and the visible player itself is drawn once by ContentView.
    /// Apply this to each screen's own scroll container: a `safeAreaInset` on the
    /// surrounding NavigationStack does not reach its scroll views.
    func miniPlayerClearance() -> some View {
        modifier(MiniPlayerClearance())
    }
}

private struct MiniPlayerClearance: ViewModifier {
    @ObservedObject private var player = AudioPlayer.shared

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom, spacing: 0) {
            if player.isVisible {
                MiniPlayer(player: player, onExpand: {})
                    .hidden()
            }
        }
    }
}
