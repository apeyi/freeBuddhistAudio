import SwiftUI

struct DownloadsScreen: View {
    @ObservedObject private var downloadManager = DownloadManager.shared
    let onTalkClick: (String) -> Void

    @State private var deleteConfirmCatNum: String?
    @State private var showDeleteAllConfirm = false
    @State private var filter: Filter = .all

    enum Filter: String, CaseIterable { case all = "All", talks = "Talks", transcripts = "Transcripts" }

    private var allDownloads: [DownloadManager.DownloadState] {
        Array(downloadManager.downloads.values).sorted { $0.catNum < $1.catNum }
    }

    // All | Talks | Transcripts — a talk download that included its transcript counts as both
    private var downloads: [DownloadManager.DownloadState] {
        switch filter {
        case .all: return allDownloads
        case .talks: return allDownloads.filter { !$0.transcriptOnly }
        case .transcripts: return allDownloads.filter { downloadManager.hasTranscript($0.catNum) }
        }
    }

    private var totalBytes: Int64 {
        allDownloads.filter { $0.status == .complete }.reduce(0) { $0 + $1.totalBytes }
    }

    /// What's stored: "Audio · Transcript · Remastered"
    private func storedDescription(_ download: DownloadManager.DownloadState) -> String {
        var parts: [String] = []
        let hasAudio = download.status == .complete && !download.transcriptOnly
        if hasAudio { parts.append("Audio") }
        if downloadManager.hasTranscript(download.catNum) { parts.append("Transcript") }
        if hasAudio, !download.audioVersion.isEmpty { parts.append(download.audioVersion.capitalized) }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        Group {
            if allDownloads.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "arrow.down.circle")
                        .font(.largeTitle)
                        .foregroundStyle(.secondary)
                    Text("No downloads yet")
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    Picker("Filter", selection: $filter) {
                        ForEach(Filter.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 4, trailing: 16))
                    if downloads.isEmpty {
                        Text("Nothing in this filter").foregroundStyle(.secondary)
                            .listRowSeparator(.hidden)
                    }
                    ForEach(downloads) { download in
                    let subtitle: String? = {
                        switch download.status {
                        case .complete:
                            let stored = storedDescription(download)
                            let size = download.totalBytes > 0 ? formatFileSize(download.totalBytes) : ""
                            let joined = [stored, size].filter { !$0.isEmpty }.joined(separator: " · ")
                            return joined.isEmpty ? nil : joined
                        case .failed: return download.progress > 0 ? "Failed at \(download.progress)%" : "Failed"
                        case .downloading: return "Downloading... \(download.progress)%"
                        case .pending: return "Waiting..."
                        }
                    }()

                    TalkCard(
                        title: download.title,
                        speaker: download.speaker,
                        imageUrl: download.imageUrl,
                        subtitle: subtitle,
                        onClick: { onTalkClick(download.catNum) },
                        trailing: trailingView(download)
                    )
                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                    .listRowSeparator(.hidden)
                    }
                }
                .listStyle(.plain)
            }
        }
        .miniPlayerClearance()
        .navigationTitle("Downloads")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 8) {
                    if totalBytes > 0 {
                        Text("Total: \(formatFileSize(totalBytes))")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    if !allDownloads.isEmpty {
                        Button(action: { showDeleteAllConfirm = true }) {
                            Image(systemName: "trash.circle")
                        }
                    }
                }
            }
        }
        // presenting: hands the catNum to the action directly — reading the
        // @State var inside the action raced with the binding's set(false),
        // which could nil it first and silently skip the delete.
        .alert("Delete download?", isPresented: Binding(
            get: { deleteConfirmCatNum != nil },
            set: { if !$0 { deleteConfirmCatNum = nil } }
        ), presenting: deleteConfirmCatNum) { catNum in
            Button("Delete", role: .destructive) {
                downloadManager.deleteDownload(catNum: catNum)
            }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("This will remove the offline files.")
        }
        .alert("Delete all downloads?", isPresented: $showDeleteAllConfirm) {
            Button("Delete All", role: .destructive) {
                downloadManager.deleteAllDownloads()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will remove all offline files.")
        }
    }

    private func trailingView(_ download: DownloadManager.DownloadState) -> AnyView {
        switch download.status {
        case .complete:
            return AnyView(
                Button(action: { deleteConfirmCatNum = download.catNum }) {
                    Image(systemName: "trash").foregroundStyle(.red)
                }
            )
        case .downloading, .pending:
            return AnyView(
                HStack(spacing: 8) {
                    ProgressView(value: (Float(download.progress) / 100).safeFraction())
                        .progressViewStyle(.circular)
                        .controlSize(.small)
                    // Cancel: stops the transfer and removes partial files —
                    // no confirm needed for an in-flight download.
                    Button(action: { downloadManager.cancelDownload(catNum: download.catNum) }) {
                        Image(systemName: "xmark.circle").foregroundStyle(.secondary)
                    }
                }
            )
        case .failed:
            return AnyView(
                HStack(spacing: 8) {
                    Button(action: {
                        Task {
                            if let talk = await TalkRepository.shared.getTalkDetail(download.catNum) {
                                downloadManager.retryDownload(catNum: download.catNum, talk: talk)
                            }
                        }
                    }) {
                        Image(systemName: "arrow.clockwise").foregroundStyle(Color.saffronOrange)
                    }
                    Button(action: { deleteConfirmCatNum = download.catNum }) {
                        Image(systemName: "trash").foregroundStyle(.red)
                    }
                }
            )
        }
    }
}
