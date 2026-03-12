import SwiftUI

struct HomeScreen: View {
    @StateObject private var player = AudioPlayer.shared
    @StateObject private var downloadManager = DownloadManager.shared
    @State private var recentlyListened: [PersistenceManager.RecentlyListened] = []

    let onTalkClick: (String) -> Void
    let onSangharakshitaByYearClick: () -> Void
    let onSangharakshitaSeriesClick: () -> Void
    let onMitraStudyClick: () -> Void
    let onDonateClick: () -> Void

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
                // Sangharakshita section
                sangharakshitaSection

                // Mitra Study
                sectionCard(title: "Mitra Study", subtitle: "Structured study courses",
                           icon: "book", action: onMitraStudyClick)

                // Donate
                donateCard

                // Recently Listened
                if !recentlyListened.isEmpty {
                    recentlyListenedSection
                }
            }
            .padding(.bottom, 16)
        }
        .onAppear { recentlyListened = PersistenceManager.shared.getRecentlyListened() }
    }

    // MARK: - Sangharakshita Section

    private var sangharakshitaSection: some View {
        VStack(spacing: 0) {
            Image("sangharakshita")
                .resizable()
                .aspectRatio(16/9, contentMode: .fill)
                .clipped()

            VStack(alignment: .leading, spacing: 4) {
                Text("Sangharakshita")
                    .font(.title2).bold()
                Text("\(SharedDataLoader.sangharakshitaTalks.count) talks · \(SharedDataLoader.sangharakshitaSeries.count) series")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Divider().padding(.vertical, 8)

                Button(action: onSangharakshitaByYearClick) {
                    HStack {
                        VStack(alignment: .leading) {
                            Text("By Year").font(.body)
                            Text("Browse all talks by decade and year")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(.secondary)
                    }
                }
                .buttonStyle(.plain)

                Divider().padding(.vertical, 4)

                Button(action: onSangharakshitaSeriesClick) {
                    HStack {
                        VStack(alignment: .leading) {
                            Text("Series").font(.body)
                            Text("\(SharedDataLoader.sangharakshitaSeries.count) lecture series")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(.secondary)
                    }
                }
                .buttonStyle(.plain)
            }
            .padding(16)
        }
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 16)
        .padding(.top, 16)
    }

    // MARK: - Section Card

    private func sectionCard(title: String, subtitle: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.title2)
                VStack(alignment: .leading) {
                    Text(title).font(.headline)
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.secondary)
            }
            .padding(16)
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
    }

    // MARK: - Donate Card

    private var donateCard: some View {
        Button(action: onDonateClick) {
            HStack(spacing: 12) {
                Image("fba_logo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(height: 28)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                VStack(alignment: .leading) {
                    Text("Support Free Buddhist Audio").font(.subheadline)
                    Text("Donate to help keep FBA free for everyone")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
    }

    // MARK: - Recently Listened

    private var recentlyListenedSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Recently Listened")
                .font(.headline)
                .padding(.horizontal, 16)
                .padding(.top, 8)

            ForEach(recentlyListened) { entry in
                let totalMs = Int64(entry.totalDurationSeconds) * 1000
                let progress = totalMs > 0 ? Float(entry.positionMs) / Float(totalMs) : 0
                let isCompleted = progress > 0.95
                let subtitle = isCompleted
                    ? "Completed · \(formatDuration(entry.totalDurationSeconds))"
                    : (entry.totalDurationSeconds > 0
                        ? "\(formatDuration(Int(entry.positionMs / 1000))) / \(formatDuration(entry.totalDurationSeconds))"
                        : nil)
                let isDownloaded = downloadManager.isDownloaded(entry.catNum)

                VStack(spacing: 0) {
                    TalkCard(
                        title: entry.title,
                        speaker: entry.speaker,
                        imageUrl: entry.imageUrl,
                        subtitle: subtitle,
                        onClick: { onTalkClick(entry.catNum) },
                        trailing: trailingIcons(isCompleted: isCompleted, isDownloaded: isDownloaded)
                    )

                    if progress > 0 && !isCompleted {
                        ProgressView(value: progress)
                            .tint(.saffronOrange)
                            .padding(.horizontal, 12)
                            .padding(.top, 2)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func trailingIcons(isCompleted: Bool, isDownloaded: Bool) -> AnyView? {
        guard isCompleted || isDownloaded else { return nil }
        return AnyView(
            HStack(spacing: 4) {
                if isCompleted {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(Color.saffronOrange)
                        .font(.caption)
                }
                if isDownloaded {
                    Image(systemName: "arrow.down.circle.fill")
                        .foregroundStyle(Color.saffronOrange)
                        .font(.caption)
                }
            }
        )
    }
}
