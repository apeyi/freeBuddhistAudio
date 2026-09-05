import SwiftUI

struct DigitalLegacyScreen: View {
    let onPlaySample: (String) -> Void
    let onSeriesClick: (String) -> Void
    let onDonateClick: () -> Void

    @State private var page: DigitalLegacy?
    @State private var isLoading = true

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

                        if let sample = page?.sampleCatNum, !sample.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Hear the difference").font(.headline)
                                HStack(spacing: 12) {
                                    Button(action: {
                                        AppSettings.shared.setRemasterChoice(sample, useRemaster: false)
                                        onPlaySample(sample)
                                    }) { Text("Original").frame(maxWidth: .infinity) }
                                        .buttonStyle(.bordered)
                                    Button(action: {
                                        AppSettings.shared.setRemasterChoice(sample, useRemaster: true)
                                        onPlaySample(sample)
                                    }) { Text("Remastered").frame(maxWidth: .infinity) }
                                        .buttonStyle(.borderedProminent)
                                }
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
            isLoading = false
        }
    }
}
