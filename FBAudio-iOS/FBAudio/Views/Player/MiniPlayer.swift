import SwiftUI

struct MiniPlayer: View {
    @ObservedObject var player: AudioPlayer
    let onExpand: () -> Void

    var body: some View {
        if player.isVisible, let talk = player.currentTalk {
            VStack(spacing: 0) {
                // Progress bar
                if player.duration > 0 {
                    GeometryReader { geo in
                        let progress = (player.currentPosition / player.duration).safeFraction()
                        Rectangle()
                            .fill(Color.saffronOrange)
                            .frame(width: geo.size.width * CGFloat(progress))
                    }
                    .frame(height: 2)
                }

                HStack(spacing: 10) {
                    if !talk.imageUrl.isEmpty {
                        AsyncImage(url: URL(string: talk.imageUrl)) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Color.gray.opacity(0.3)
                        }
                        .frame(width: 40, height: 40)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    }

                    VStack(alignment: .leading, spacing: 1) {
                        Text(talk.title)
                            .font(.subheadline)
                            .lineLimit(1)
                        Text(talk.speaker)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Button(action: player.togglePlayPause) {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.title3)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .contentShape(Rectangle())
                .onTapGesture(perform: onExpand)
            }
            .background(.thinMaterial)
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
