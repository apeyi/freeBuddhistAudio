import SwiftUI

struct DigitalLegacyScreen: View {
    let onSeriesClick: (String) -> Void
    let onDonateClick: () -> Void

    @EnvironmentObject private var player: AudioPlayer
    @ObservedObject private var settings = AppSettings.shared
    @StateObject private var sample = SamplePlayer()
    @State private var page: DigitalLegacy?
    @State private var isLoading = true
    @State private var sampleTitle = ""
    @State private var sampleSpeaker = ""
    @State private var sampleChapter = ""
    @State private var hasSample = false

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        Text(page?.description
                             ?? "Since 1967 the team at Dharmachakra has been sharing Sangharakshita's talks with the world. To celebrate 20 years of Free Buddhist Audio we are digitally remastering all of his talks for greatly enhanced listening.")
                            .font(.body)

                        if hasSample { hearTheDifference }

                        // Default for all talks (same setting as in My FBA → Settings)
                        Toggle(isOn: $settings.preferRemastered) {
                            VStack(alignment: .leading) {
                                Text("Prefer remastered audio")
                                Text("Play the remastered version whenever a talk has one")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }

                        Button(action: { onSeriesClick(page?.seriesPath ?? "/series/details?num=X16") }) {
                            Text("Listen: Buddhism for Today – and Tomorrow").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)

                        Button(action: onDonateClick) {
                            Text("Support the Digital Legacy").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(16)
                }
            }
        }
        .tint(.saffronOrange)
        .miniPlayerClearance()
        .navigationTitle("The Digital Legacy")
        .navigationBarTitleDisplayMode(.inline)
        // The sample is a demo: it stops when the page is left
        .onDisappear { sample.pause() }
        .task {
            page = await ContentRepository.shared.getDigitalLegacy()
            isLoading = false
            guard let cat = page?.sampleCatNum, !cat.isEmpty,
                  let talk = await TalkRepository.shared.getTalkDetail(cat),
                  let chapter = talk.tracks.first(where: { $0.hasRemaster }) else { return }
            // Use the first chapter that exists in both versions; prepare both right away
            sampleTitle = talk.title
            sampleSpeaker = talk.speaker
            sampleChapter = chapter.title
            hasSample = true
            sample.load(originalUrl: chapter.audioUrl, remasterUrl: chapter.remasterAudioUrl,
                        startWithRemaster: settings.preferRemastered,
                        knownDurationMs: Int64(chapter.durationSeconds) * 1000)
        }
    }

    // MARK: - Hear the difference: A/B player with both versions preloaded

    private var hearTheDifference: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Hear the difference").font(.headline)
            let subtitle = [sampleTitle, sampleChapter, sampleSpeaker].filter { !$0.isEmpty }.joined(separator: " · ")
            if !subtitle.isEmpty {
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            HStack(spacing: 12) {
                // Original first, then Remastered — the same order as the player.
                // Both versions play in step, so this swaps the sound instantly.
                Picker("Version", selection: Binding(
                    get: { sample.useRemaster },
                    set: { sample.setVersion($0) }
                )) {
                    Text("Original").tag(false)
                    Text("Remastered").tag(true)
                }
                .pickerStyle(.segmented)
                .disabled(!sample.isReady)

                ZStack {
                    Button(action: {
                        // One thing at a time: the demo pauses the main player
                        if !sample.isPlaying { player.pause() }
                        sample.togglePlayPause()
                    }) {
                        Image(systemName: sample.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 36))
                    }
                    .disabled(!sample.isReady)
                    if !sample.isReady || sample.isBuffering {
                        ProgressView()
                    }
                }
            }
            Slider(
                value: Binding(
                    get: { sample.durationMs > 0 ? (Double(sample.positionMs) / Double(sample.durationMs)).safeFraction() : 0 },
                    set: { sample.seek(toFraction: $0) }
                ),
                in: 0...1
            )
            .disabled(!sample.isReady)
            Text("\(formatDuration(Int(sample.positionMs / 1000))) / \(formatDuration(Int(sample.durationMs / 1000)))  ·  \(sample.useRemaster ? "remastered" : "original")")
                .font(.caption2).foregroundStyle(.secondary)
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
