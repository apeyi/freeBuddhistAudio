import SwiftUI

struct DownloadsScreen: View {
    @ObservedObject private var downloadManager = DownloadManager.shared
    let onTalkClick: (String) -> Void

    private var downloads: [DownloadManager.DownloadState] {
        Array(downloadManager.downloads.values).sorted { $0.catNum < $1.catNum }
    }

    private var totalBytes: Int64 {
        downloads.filter { $0.status == .complete }.reduce(0) { $0 + $1.totalBytes }
    }

    var body: some View {
        Group {
            if downloads.isEmpty {
                ContentUnavailableView("No downloads yet", systemImage: "arrow.down.circle")
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
        .toolbar(content: {
            ToolbarItem(placement: .navigationBarTrailing) {
                if totalBytes > 0 {
                    Text("Total: \(formatFileSize(totalBytes))")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        })
    }

    private func trailingView(_ download: DownloadManager.DownloadState) -> AnyView {
        switch download.status {
        case .complete:
            return AnyView(
                Button(action: { downloadManager.deleteDownload(catNum: download.catNum) }) {
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
                    Button(action: { downloadManager.deleteDownload(catNum: download.catNum) }) {
                        Image(systemName: "trash").foregroundStyle(.red)
                    }
                }
            )
        }
    }
}
