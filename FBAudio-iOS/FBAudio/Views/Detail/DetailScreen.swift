import SwiftUI

struct DetailScreen: View {
    let catNum: String
    let onPlay: (String) -> Void
    let onSpeakerClick: (String) -> Void
    let onSeriesClick: (String) -> Void
    let onTranscriptClick: (String, String) -> Void
    var onDonateClick: () -> Void = {}
    var onJoinClick: () -> Void = {}
    /// False when downloads are member-only and the user isn't a member.
    var canDownload: Bool = true

    @ObservedObject private var player = AudioPlayer.shared
    @ObservedObject private var downloadManager = DownloadManager.shared
    @State private var talk: Talk?
    @State private var isLoading = true
    @State private var error: String?
    @State private var showDeleteConfirm = false

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
            } else if let error {
                VStack(spacing: 16) {
                    Text(error).foregroundStyle(.secondary)
                    Button("Retry") { loadTalk() }
                }
            } else if let talk {
                talkContent(talk)
            }
        }
        .navigationTitle(talk?.title ?? "Talk")
        .navigationBarTitleDisplayMode(.inline)
        .task { loadTalk() }
        .alert("Delete download?", isPresented: $showDeleteConfirm) {
            Button("Delete", role: .destructive) {
                downloadManager.deleteDownload(catNum: catNum)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will remove the offline files.")
        }
    }

    private func loadTalk() {
        isLoading = true
        error = nil
        Task {
            // Logged in: fetch fresh so the page carries the account's saved position.
            if let result = await TalkRepository.shared.getTalkDetailForPlayback(catNum) {
                talk = result
            } else {
                error = "Could not load talk"
            }
            isLoading = false
        }
    }

    private func talkContent(_ talk: Talk) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                // Image
                if !talk.imageUrl.isEmpty {
                    AsyncImage(url: URL(string: talk.imageUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.gray.opacity(0.2)
                    }
                    .frame(maxWidth: .infinity)
                    .aspectRatio(16.0/9.0, contentMode: .fit)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                Text(talk.title).font(.title2).bold()
                if talk.hasRemaster { RemasterBadge() }

                // Speaker
                Button(action: { onSpeakerClick(talk.speaker) }) {
                    Text(talk.speaker).foregroundStyle(Color.saffronOrange)
                }

                // Series
                if !talk.series.isEmpty {
                    Button(action: { onSeriesClick(talk.seriesHref.isEmpty ? talk.series : talk.seriesHref) }) {
                        Text("Series: \(talk.series)")
                            .font(.caption).foregroundStyle(Color.deepSaffron)
                    }
                }

                // Metadata
                HStack(spacing: 16) {
                    if talk.durationSeconds > 0 {
                        Text(formatDuration(talk.durationSeconds))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    if talk.year > 0 {
                        Text("\(talk.year)").font(.caption).foregroundStyle(.secondary)
                    }
                    if !talk.genre.isEmpty {
                        Text(talk.genre).font(.caption).foregroundStyle(.secondary)
                    }
                }

                // Play button
                let isThisPlaying = player.currentTalk?.catNum == catNum && player.isPlaying
                Button(action: {
                    if player.currentTalk?.catNum == catNum {
                        player.togglePlayPause()
                    } else {
                        onPlay(catNum)
                    }
                }) {
                    Label(isThisPlaying ? "Pause" : "Play",
                          systemImage: isThisPlaying ? "pause.fill" : "play.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.saffronOrange)

                // Download button
                downloadButton(talk: talk)

                // Transcript: view, and save on its own (small — handy on retreat)
                if !talk.transcriptUrl.isEmpty {
                    HStack(spacing: 8) {
                        Button(action: { onTranscriptClick(talk.transcriptUrl, catNum) }) {
                            Text("View Transcript")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)

                        let entry = downloadManager.downloads[talk.catNum]
                        let transcriptSaved = entry?.status == .complete
                        if entry == nil || entry?.transcriptOnly == true {
                            Button(action: {
                                if canDownload { downloadManager.startTranscriptDownload(talk: talk) } else { onJoinClick() }
                            }) {
                                Text(transcriptSaved ? "Transcript saved" : "Save transcript")
                            }
                            .buttonStyle(.bordered)
                            .disabled(transcriptSaved)
                        }
                    }
                }

                // Donate — on every talk page, directly under the transcript
                Button(action: onDonateClick) {
                    Text("Donate").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.saffronOrange)

                // Description
                if !talk.description.isEmpty {
                    Text(talk.description)
                        .font(.body)
                        .padding(.top, 8)
                }

                // Chapters
                if talk.tracks.count > 1 {
                    chaptersSection(talk: talk)
                }
            }
            .padding(16)
        }
        .miniPlayerClearance()
    }

    @ViewBuilder
    private func downloadButton(talk: Talk) -> some View {
        // A transcript-only download doesn't count as the audio being saved.
        let state = downloadManager.downloads[talk.catNum].flatMap { $0.transcriptOnly ? nil : $0 }
        switch state?.status {
        case .complete:
            HStack {
                Button(action: {}) {
                    Label("Downloaded", systemImage: "checkmark.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(true)

                Button(action: { showDeleteConfirm = true }) {
                    Image(systemName: "trash")
                        .foregroundStyle(.red)
                }
            }
        case .downloading, .pending:
            Button(action: { downloadManager.cancelDownload(catNum: catNum) }) {
                HStack {
                    ProgressView().controlSize(.small)
                    Text("Downloading... \(state?.progress ?? 0)% — tap to cancel")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        default:
            // Downloads are a membership benefit when gating is on
            Button(action: { if canDownload { downloadManager.startDownload(talk: talk) } else { onJoinClick() } }) {
                Label(canDownload ? "Download for offline" : "Join to download", systemImage: "arrow.down.circle")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
    }

    private func chaptersSection(talk: Talk) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Chapters (\(talk.tracks.count))")
                .font(.headline)
                .padding(.top, 8)

            ForEach(0..<talk.tracks.count, id: \.self) { index in
                let isActive = player.currentTalk?.catNum == catNum
                Divider()
                ChapterRow(
                    index: index,
                    track: talk.tracks[index],
                    isCurrent: isActive && player.currentTrackIndex == index,
                    onTap: {
                        if isActive {
                            player.playTrackByIndex(index)
                        } else {
                            // Start THIS talk at the tapped chapter. The old
                            // onPlay(catNum) + playTrackByIndex(index) pair raced:
                            // playTrackByIndex ran against the previous talk, and
                            // the async load then discarded the tapped chapter.
                            player.playTalk(talk, fromTrackIndex: index)
                        }
                    }
                )
                .padding(.vertical, 8)
            }
        }
    }
}
