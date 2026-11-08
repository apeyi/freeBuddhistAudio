import SwiftUI

/// Settings (SPEC "Settings"): Playback (Download over Wi-Fi only), Talks (Talk languages),
/// Listening history (Clear listening history), Storage (Downloads, Remove all downloads) and
/// About (Contact support, Version). No remastered setting: the switch is on each remastered
/// talk. Mirrors Android `SettingsScreen`.
struct SettingsScreen: View {
    var onDownloadsClick: () -> Void = {}
    var onContactSupport: () -> Void = {}

    @ObservedObject private var settings = AppSettings.shared
    @ObservedObject private var auth = AuthRepository.shared
    @ObservedObject private var downloadManager = DownloadManager.shared
    @State private var clearHistory = false
    @State private var removeDownloads = false

    private var completed: [DownloadManager.DownloadState] {
        downloadManager.downloads.values.filter { $0.status == .complete && !$0.transcriptOnly }
    }
    private var bytes: Int64 { completed.reduce(0) { $0 + $1.totalBytes } }
    private var version: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "" }

    var body: some View {
        Form {
            Section {
                Toggle("Download over Wi-Fi only", isOn: $settings.wifiOnlyDownloads)
            } header: { Text("Playback") } footer: {
                Text("Downloads wait for Wi-Fi, so they don't use mobile data.")
            }

            Section("Talks") {
                Picker("Talk languages", selection: $settings.englishOnly) {
                    Text("English").tag(true)
                    Text("All languages").tag(false)
                }
                .pickerStyle(.navigationLink)
                .onChange(of: settings.englishOnly) { _ in ContentRepository.shared.invalidateMemo() }
            }

            Section {
                Button("Clear listening history", role: .destructive) { clearHistory = true }
            } header: { Text("Listening history") } footer: {
                Text("Removes the talks you've played and your place in them from this iPhone.")
            }

            Section("Storage") {
                Button(action: onDownloadsClick) {
                    LabeledContent("Downloads", value: MyFbaRules.formatBytes(bytes))
                }
                .foregroundStyle(FbaColors.black)
                if !completed.isEmpty {
                    Button("Remove all downloads", role: .destructive) { removeDownloads = true }
                }
            }

            Section("About") {
                Button("Contact support", action: onContactSupport).foregroundStyle(FbaColors.black)
                LabeledContent("Version", value: version)
            }
        }
        .miniPlayerClearance()
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.large)
        // Action sheet (SPEC): clear on this iPhone; when signed in, also from the FBA account; Cancel.
        .confirmationDialog("Clear listening history?", isPresented: $clearHistory, titleVisibility: .visible) {
            Button("Clear on this iPhone", role: .destructive) { clear(alsoAccount: false) }
            if auth.state.loggedIn {
                Button("Also clear from my FBA account", role: .destructive) { clear(alsoAccount: true) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This can't be undone.")
        }
        .alert("Remove all downloads?", isPresented: $removeDownloads) {
            Button("Remove", role: .destructive) { downloadManager.deleteAllDownloads() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("\(MyFbaRules.downloadsSummary(count: completed.count, bytes: bytes)) will be removed from this iPhone. You can download them again at any time.")
        }
    }

    private func clear(alsoAccount: Bool) {
        let cats = PersistenceManager.shared.getRecentlyListened().map(\.catNum)
        PersistenceManager.shared.clearRecentlyListened()
        if alsoAccount { Task { await HistoryRepository.shared.deleteFromAccount(cats) } }
    }
}
