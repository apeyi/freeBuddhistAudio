import SwiftUI

struct DownloadsScreen: View {
    @ObservedObject private var downloadManager = DownloadManager.shared
    let onTalkClick: (String) -> Void

    @State private var deleteConfirmCatNum: String?
    @State private var showDeleteAllConfirm = false

    private var downloads: [DownloadManager.DownloadState] {
        Array(downloadManager.downloads.values).sorted { $0.catNum < $1.catNum }
    }

    private var totalBytes: Int64 {
        downloads.filter { $0.status == .complete }.reduce(0) { $0 + $1.totalBytes }
    }

    var body: some View {
        Group {
            if downloads.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "arrow.down.circle")
                        .font(.largeTitle)
                        .foregroundStyle(.secondary)
                    Text("No downloads yet")
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(downloads) { download in
                    let subtitle: String? = {
                        switch download.status {
                        case .complete: return download.totalBytes > 0 ? formatFileSize(download.totalBytes) : nil
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
                .listStyle(.plain)
            }
        }
        .navigationTitle("Downloads")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 8) {
                    if totalBytes > 0 {
                        Text("Total: \(formatFileSize(totalBytes))")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    if !downloads.isEmpty {
                        Button(action: { showDeleteAllConfirm = true }) {
                            Image(systemName: "trash.circle")
                        }
                    }
                }
            }
        }
        .alert("Delete download?", isPresented: Binding(
            get: { deleteConfirmCatNum != nil },
            set: { if !$0 { deleteConfirmCatNum = nil } }
        )) {
            Button("Delete", role: .destructive) {
                if let catNum = deleteConfirmCatNum {
                    downloadManager.deleteDownload(catNum: catNum)
                }
                deleteConfirmCatNum = nil
            }
            Button("Cancel", role: .cancel) { deleteConfirmCatNum = nil }
        } message: {
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
                ProgressView(value: Float(download.progress) / 100)
                    .progressViewStyle(.circular)
                    .controlSize(.small)
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
