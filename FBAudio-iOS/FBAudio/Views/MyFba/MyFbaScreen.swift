import SwiftUI

struct MyFbaScreen: View {
    let onTalkClick: (String) -> Void
    let onDonateClick: () -> Void
    let onLoginClick: () -> Void

    @ObservedObject private var auth = AuthRepository.shared
    @ObservedObject private var settings = AppSettings.shared
    @ObservedObject private var downloadManager = DownloadManager.shared
    @Environment(\.scenePhase) private var scenePhase
    @State private var recentlyListened: [PersistenceManager.RecentlyListened] = []

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                accountCard
                Button(action: onDonateClick) { Text("Donate").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent)
                    .tint(.saffronOrange)
                    .padding(.horizontal, 16)

                Text("Recently Listened")
                    .font(.headline)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                if recentlyListened.isEmpty {
                    Text("Talks you play will appear here.")
                        .font(.caption).foregroundStyle(.secondary)
                        .padding(.horizontal, 16)
                }
                ForEach(recentlyListened) { entry in
                    recentRow(entry)
                }

                Text("Settings")
                    .font(.headline)
                    .padding(.horizontal, 16)
                    .padding(.top, 24)
                Toggle(isOn: $settings.englishOnly) {
                    VStack(alignment: .leading) {
                        Text("English only")
                        Text(settings.englishOnly ? "Talks in other languages are hidden" : "Showing all languages")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding(.horizontal, 16)
                .onChange(of: settings.englishOnly) { _ in ContentRepository.shared.invalidateMemo() }
                Divider().padding(.horizontal, 16)
                Toggle(isOn: $settings.preferRemastered) {
                    VStack(alignment: .leading) {
                        Text("Prefer remastered audio")
                        Text("Play the remastered version when a talk has one")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 24)
        }
        .tint(.saffronOrange)
        .miniPlayerClearance()
        .navigationTitle("My FBA")
        .onAppear { refresh() }
        .onChange(of: scenePhase) { phase in if phase == .active { refresh() } }
        .onChange(of: auth.state.loggedIn) { _ in refresh() }
    }

    private func refresh() {
        recentlyListened = PersistenceManager.shared.getRecentlyListened()
        // Pull the account's web history each time the tab is opened while logged in.
        if auth.isLoggedIn {
            Task {
                await HistoryRepository.shared.syncFromServer()
                recentlyListened = PersistenceManager.shared.getRecentlyListened()
            }
        }
    }

    // MARK: - Account

    private var accountCard: some View {
        HStack(spacing: 12) {
            if auth.state.loggedIn, !auth.state.avatarUrl.isEmpty, let url = URL(string: auth.state.avatarUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: { Color.gray.opacity(0.2) }
                .frame(width: 48, height: 48)
                .clipShape(Circle())
            } else {
                Image(systemName: "person.crop.circle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(.secondary)
            }
            VStack(alignment: .leading, spacing: 2) {
                if auth.state.loggedIn {
                    Text(auth.state.username.isEmpty ? "Logged in" : auth.state.username).font(.headline)
                    Text(auth.state.isOrderMember ? "Order member" : "FBA account")
                        .font(.caption).foregroundStyle(.secondary)
                } else if FeatureFlags.auth {
                    Text("Not logged in").font(.headline)
                    Text("Log in with your FBA account to sync your listening history.")
                        .font(.caption).foregroundStyle(.secondary)
                } else {
                    Text("Your FBA").font(.headline)
                    Text("Account login arrives with the new FBA service.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if FeatureFlags.auth {
                if auth.state.loggedIn {
                    Button("Log out") { Task { await auth.logout() } }.buttonStyle(.bordered)
                } else {
                    Button("Log in", action: onLoginClick).buttonStyle(.borderedProminent)
                }
            }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(16)
    }

    // MARK: - Recently listened row (moved from Home)

    private func recentRow(_ entry: PersistenceManager.RecentlyListened) -> some View {
        let totalMs = Int64(entry.totalDurationSeconds) * 1000
        let progress = totalMs > 0 ? Float(entry.positionMs) / Float(totalMs) : 0
        let isCompleted = progress > 0.95
        let subtitle: String? = isCompleted
            ? "Completed · \(formatDuration(entry.totalDurationSeconds))"
            : (entry.totalDurationSeconds > 0
                ? "\(formatDuration(Int(entry.positionMs / 1000))) / \(formatDuration(entry.totalDurationSeconds))"
                : nil)
        let isDownloaded = downloadManager.isDownloaded(entry.catNum)

        return VStack(spacing: 0) {
            TalkCard(
                title: entry.title,
                speaker: entry.speaker,
                imageUrl: entry.imageUrl,
                subtitle: subtitle,
                onClick: { onTalkClick(entry.catNum) },
                trailing: (isCompleted || isDownloaded) ? AnyView(
                    HStack(spacing: 4) {
                        if isCompleted {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.saffronOrange).font(.caption)
                        }
                        if isDownloaded {
                            Image(systemName: "arrow.down.circle.fill").foregroundStyle(Color.saffronOrange).font(.caption)
                        }
                    }
                ) : nil
            )
            if progress > 0 && !isCompleted {
                ProgressView(value: progress.safeFraction())
                    .tint(.saffronOrange)
                    .padding(.horizontal, 12)
                    .padding(.top, 2)
            }
        }
        .padding(.horizontal, 16)
    }
}
