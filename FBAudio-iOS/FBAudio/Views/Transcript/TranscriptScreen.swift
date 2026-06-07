import SwiftUI

struct TranscriptScreen: View {
    let transcriptUrl: String
    let catNum: String

    @State private var text = ""
    @State private var isLoading = true
    @State private var error: String?

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
            } else if let error {
                VStack(spacing: 16) {
                    Text(error).foregroundStyle(.secondary)
                    Button("Retry") { loadTranscript() }
                }
            } else {
                ScrollView {
                    Text(text)
                        .font(.body)
                        .padding(16)
                        .textSelection(.enabled)
                }
            }
        }
        .miniPlayerClearance()
        .navigationTitle("Transcript")
        .navigationBarTitleDisplayMode(.inline)
        .task { loadTranscript() }
    }

    private func loadTranscript() {
        isLoading = true
        error = nil
        Task {
            // Check for downloaded transcript
            let localPath = DownloadManager.shared.transcriptFilePath(catNum: catNum)
            if FileManager.default.fileExists(atPath: localPath.path),
               let localText = try? String(contentsOf: localPath, encoding: .utf8), !localText.isEmpty {
                text = localText
                isLoading = false
                return
            }

            // Fetch from network
            do {
                text = try await TalkRepository.shared.fetchTranscript(transcriptUrl)
            } catch {
                self.error = friendlyError(error)
            }
            isLoading = false
        }
    }
}
