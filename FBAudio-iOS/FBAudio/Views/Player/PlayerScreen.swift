import SwiftUI

struct PlayerScreen: View {
    @ObservedObject var player: AudioPlayer
    @Environment(\.dismiss) private var dismiss
    @State private var showSpeedSlider = false
    @State private var showChapterSheet = false
    // Local scrub state: while dragging, the slider follows the finger only —
    // binding it to the live position would yank the thumb back on every
    // 0.5s position tick and fire a seek per drag tick.
    @State private var isScrubbing = false
    @State private var scrubPosition: Double = 0

    let onNavigateToDetail: (String) -> Void
    let onSpeakerClick: (String) -> Void

    var body: some View {
        let talk = player.currentTalk
        let tracks = talk?.tracks ?? []
        let hasMultipleTracks = tracks.count > 1

        VStack(spacing: 0) {
            // Top bar
            HStack {
                Button(action: { dismiss() }) {
                    Image(systemName: "chevron.down")
                        .font(.title3)
                }
                Spacer()
                if let talk {
                    Button(action: { onNavigateToDetail(talk.catNum) }) {
                        Image(systemName: "info.circle")
                    }
                }
                // Download button
                downloadButton
            }
            .padding(.horizontal)
            .padding(.top, 8)

            Spacer()

            // Album art
            if let imageUrl = talk?.imageUrl, !imageUrl.isEmpty {
                AsyncImage(url: URL(string: imageUrl)) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.gray.opacity(0.2)
                }
                .frame(width: 200, height: 200)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            // Title & speaker
            VStack(spacing: 4) {
                Text(talk?.title ?? "")
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                Button(action: {
                    if let speaker = talk?.speaker, !speaker.isEmpty {
                        onSpeakerClick(speaker)
                    }
                }) {
                    Text(talk?.speaker ?? "")
                        .font(.subheadline)
                        .foregroundStyle(Color.saffronOrange)
                }
                if hasMultipleTracks {
                    Text("Chapter \(player.currentTrackIndex + 1) of \(tracks.count)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if let err = player.playbackError {
                    Text(err)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding(.top, 8)
                    Button("Retry") { player.retry() }
                        .font(.caption)
                        .tint(.saffronOrange)
                }
            }
            .padding(.top, 16)

            Spacer()

            // Seek bar
            VStack(spacing: 4) {
                Slider(
                    value: Binding(
                        get: {
                            if isScrubbing { return scrubPosition }
                            return (player.duration > 0 ? player.currentPosition / player.duration : 0).safeFraction()
                        },
                        set: { scrubPosition = $0 }
                    ),
                    in: 0...1,
                    onEditingChanged: { editing in
                        if editing {
                            isScrubbing = true
                        } else {
                            player.seekTo(scrubPosition * player.duration)
                            isScrubbing = false
                        }
                    }
                )
                .tint(.saffronOrange)

                HStack {
                    Text(formatDuration(Int(player.currentPosition)))
                        .font(.caption2).foregroundStyle(.secondary)
                    Spacer()
                    Text(formatDuration(Int(player.duration)))
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 24)

            // Transport controls
            HStack(spacing: 0) {
                if hasMultipleTracks {
                    Button(action: player.previousTrack) {
                        Image(systemName: "backward.end.fill").font(.title2)
                    }
                    .disabled(player.currentTrackIndex == 0)
                    .frame(width: 44)
                }

                Spacer()

                VStack(spacing: 2) {
                    Button(action: player.seekBack) {
                        Image(systemName: "gobackward.10").font(.title2)
                    }
                    Text("10s").font(.caption2).foregroundStyle(.secondary)
                }

                Spacer()

                Button(action: player.togglePlayPause) {
                    Image(systemName: player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 56))
                }

                Spacer()

                VStack(spacing: 2) {
                    Button(action: player.seekForward) {
                        Image(systemName: "goforward.10").font(.title2)
                    }
                    Text("10s").font(.caption2).foregroundStyle(.secondary)
                }

                Spacer()

                if hasMultipleTracks {
                    Button(action: player.nextTrack) {
                        Image(systemName: "forward.end.fill").font(.title2)
                    }
                    .disabled(player.currentTrackIndex >= tracks.count - 1)
                    .frame(width: 44)
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 8)

            // Speed & chapters
            HStack {
                Button(action: { showSpeedSlider.toggle() }) {
                    Text("\(String(format: "%.2g", player.playbackSpeed))x")
                        .font(.subheadline)
                }
                Spacer()
                if hasMultipleTracks {
                    Button(action: { showChapterSheet = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "list.bullet")
                            Text("Chapters (\(tracks.count))")
                        }
                        .font(.subheadline)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            // Speed slider
            if showSpeedSlider {
                VStack {
                    Text("\(String(format: "%.2g", player.playbackSpeed))x")
                        .font(.headline)
                    HStack {
                        Text("0.5x").font(.caption2)
                        Slider(value: Binding(
                            get: { ((player.playbackSpeed - 0.5) / 1.5).safeFraction() },
                            set: {
                                let speed = 0.5 + $0 * 1.5
                                let snapped = (speed * 20).rounded() / 20
                                player.setPlaybackSpeed(Float(snapped))
                            }
                        ), in: 0...1)
                        Text("2.0x").font(.caption2)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 8)
            }
        }
        .tint(.saffronOrange)
        .sheet(isPresented: $showChapterSheet) {
            chapterSheet(tracks: tracks)
        }
    }

    @ViewBuilder
    private var downloadButton: some View {
        let status = player.currentTalk.flatMap { DownloadManager.shared.downloads[$0.catNum]?.status }
        switch status {
        case .complete:
            Image(systemName: "arrow.down.circle.fill")
                .foregroundStyle(Color.saffronOrange)
        case .downloading, .pending:
            ProgressView().controlSize(.small)
        default:
            Button(action: {
                if let talk = player.currentTalk {
                    DownloadManager.shared.startDownload(talk: talk)
                }
            }) {
                Image(systemName: "arrow.down.circle")
            }
        }
    }

    private func chapterSheet(tracks: [Track]) -> some View {
        NavigationStack {
            List(Array(tracks.enumerated()), id: \.offset) { index, track in
                ChapterRow(
                    index: index,
                    track: track,
                    isCurrent: index == player.currentTrackIndex,
                    onTap: {
                        player.playTrackByIndex(index)
                        showChapterSheet = false
                    }
                )
                .listRowBackground(index == player.currentTrackIndex ? Color.saffronOrange.opacity(0.1) : nil)
            }
            .navigationTitle("Chapters")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}
