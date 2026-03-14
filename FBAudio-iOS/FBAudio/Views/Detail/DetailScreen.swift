import SwiftUI

struct DetailScreen: View {
    let catNum: String
    let onPlay: (String) -> Void
    let onSpeakerClick: (String) -> Void
    let onSeriesClick: (String) -> Void
    let onTranscriptClick: (String, String) -> Void

    @ObservedObject private var player = AudioPlayer.shared
    @ObservedObject private var downloadManager = DownloadManager.shared
    @State private var talk: Talk?
    @State private var isLoading = true
    @State private var error: String?

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
    }

    private func loadTalk() {
        isLoading = true
        error = nil
        Task {
            if let result = await TalkRepository.shared.getTalkDetail(catNum) {
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
                        image.resizable().aspectRatio(16.0/9.0, contentMode: .fill)
                    } placeholder: {
                        Color.gray.opacity(0.2).aspectRatio(16.0/9.0, contentMode: .fill)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                Text(talk.title).font(.title2).bold()

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

                // Transcript
                if !talk.transcriptUrl.isEmpty {
                    Button(action: { onTranscriptClick(talk.transcriptUrl, catNum) }) {
                        Text("View Transcript")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }

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
    }

    @ViewBuilder
    private func downloadButton(talk: Talk) -> some View {
        let state = downloadManager.downloads[talk.catNum]
        switch state?.status {
        case .complete:
            HStack {
                Button(action: {}) {
                    Label("Downloaded", systemImage: "checkmark.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(true)

                Button(action: { downloadManager.deleteDownload(catNum: talk.catNum) }) {
                    Image(systemName: "trash")
                        .foregroundStyle(.red)
                }
            }
        case .downloading, .pending:
            Button(action: {}) {
                HStack {
                    ProgressView().controlSize(.small)
                    Text("Downloading... \(state?.progress ?? 0)%")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(true)
        default:
            Button(action: { downloadManager.startDownload(talk: talk) }) {
                Label("Download for offline", systemImage: "arrow.down.circle")
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
                let track = talk.tracks[index]
                let isActive = player.currentTalk?.catNum == catNum
                let isCurrent = isActive && player.currentTrackIndex == index

                Divider()
                Button(action: {
                    if isActive {
                        player.playTrackByIndex(index)
                    } else {
                        onPlay(catNum)
                        player.playTrackByIndex(index)
                    }
                }) {
                    HStack {
                        Text(track.title.isEmpty ? "Chapter \(index + 1)" : track.title)
                            .fontWeight(isCurrent ? .bold : .regular)
                            .foregroundStyle(isCurrent ? Color.saffronOrange : .primary)
                        Spacer()
                        if track.durationSeconds > 0 {
                            Text(formatDuration(track.durationSeconds))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
        }
    }
}
