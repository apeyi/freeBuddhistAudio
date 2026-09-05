import SwiftUI

struct DigitalLegacyScreen: View {
    let onSeriesClick: (String) -> Void
    let onDonateClick: () -> Void

    @EnvironmentObject private var player: AudioPlayer
    @ObservedObject private var settings = AppSettings.shared
    @State private var page: DigitalLegacy?
    @State private var isLoading = true
    @State private var sampleTalk: Talk?
    /// Version chosen for the sample before it starts playing
    @State private var sampleUseRemaster = true

    private var sample: String { page?.sampleCatNum ?? "" }
    private var isSamplePlaying: Bool { !sample.isEmpty && player.currentTalk?.catNum == sample }
    private var useRemaster: Bool { isSamplePlaying ? player.useRemaster : sampleUseRemaster }

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

                        if !sample.isEmpty { hearTheDifference }

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
        .task {
            page = await ContentRepository.shared.getDigitalLegacy()
            if let cat = page?.sampleCatNum, !cat.isEmpty {
                sampleUseRemaster = AppSettings.shared.useRemaster(cat)
                sampleTalk = await TalkRepository.shared.getTalkDetail(cat)
            }
            isLoading = false
        }
    }

    // MARK: - Inline A/B player on the sample talk

    private var hearTheDifference: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Hear the difference").font(.headline)
            if let talk = sampleTalk {
                Text([talk.title, talk.speaker].filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary)
            }
            HStack(spacing: 12) {
                // Original first, then Remastered — the same order as the player.
                // Switching while the sample plays swaps the audio at the same position.
                Picker("Version", selection: Binding(
                    get: { useRemaster },
                    set: { value in
                        AppSettings.shared.setRemasterChoice(sample, useRemaster: value)
                        sampleUseRemaster = value
                        if isSamplePlaying { player.setUseRemaster(value) }
                    }
                )) {
                    Text("Original").tag(false)
                    Text("Remastered").tag(true)
                }
                .pickerStyle(.segmented)
                .disabled(isSamplePlaying && player.versionLocked)

                Button(action: {
                    if isSamplePlaying {
                        player.togglePlayPause()
                    } else {
                        Task {
                            if let talk = sampleTalk ?? (await TalkRepository.shared.getTalkDetailForPlayback(sample)) {
                                player.playTalk(talk)
                            }
                        }
                    }
                }) {
                    Image(systemName: isSamplePlaying && player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 36))
                }
            }
            if isSamplePlaying, player.duration > 0 {
                ProgressView(value: (player.currentPosition / player.duration).safeFraction())
                Text("\(formatDuration(Int(player.currentPosition))) / \(formatDuration(Int(player.duration)))  ·  \(useRemaster ? "remastered" : "original")")
                    .font(.caption2).foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
