import Foundation

@MainActor
class DownloadManager: ObservableObject {
    static let shared = DownloadManager()

    @Published var downloads: [String: DownloadState] = [:]

    struct DownloadState: Identifiable {
        let catNum: String
        let title: String
        let speaker: String
        let imageUrl: String
        var status: DownloadStatus
        var progress: Int // 0-100
        var totalBytes: Int64

        var id: String { catNum }
    }

    enum DownloadStatus: String, Codable {
        case pending, downloading, complete, failed
    }

    private var downloadsDir: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("downloads")
    }

    init() {
        try? FileManager.default.createDirectory(at: downloadsDir, withIntermediateDirectories: true)
        loadSavedDownloads()
    }

    // MARK: - File Paths

    func trackFilePath(catNum: String, trackIndex: Int) -> URL {
        let sanitized = sanitize(catNum)
        return downloadsDir.appendingPathComponent("\(sanitized)_track\(trackIndex).mp3")
    }

    func transcriptFilePath(catNum: String) -> URL {
        let sanitized = sanitize(catNum)
        return downloadsDir.appendingPathComponent("\(sanitized)_transcript.txt")
    }

    private func sanitize(_ catNum: String) -> String {
        catNum.replacing(/[^a-zA-Z0-9_-]/, with: "")
    }

    func isDownloaded(_ catNum: String) -> Bool {
        downloads[catNum]?.status == .complete
    }

    func trackFileUrl(catNum: String, trackIndex: Int) -> URL? {
        let path = trackFilePath(catNum: catNum, trackIndex: trackIndex)
        return FileManager.default.fileExists(atPath: path.path) ? path : nil
    }

    // MARK: - Download

    func startDownload(talk: Talk) {
        let catNum = talk.catNum
        downloads[catNum] = DownloadState(
            catNum: catNum, title: talk.title, speaker: talk.speaker,
            imageUrl: talk.imageUrl, status: .pending, progress: 0, totalBytes: 0
        )
        saveDownloads()

        Task {
            await performDownload(talk: talk)
        }
    }

    private func performDownload(talk: Talk) async {
        let catNum = talk.catNum
        downloads[catNum]?.status = .downloading
        downloads[catNum]?.progress = 0

        // Ensure downloads directory exists
        try? FileManager.default.createDirectory(at: downloadsDir, withIntermediateDirectories: true)

        let urls = talk.tracks.isEmpty ? [talk.audioUrl] : talk.tracks.map(\.audioUrl)
        let validUrls = urls.filter { !$0.isEmpty }
        guard !validUrls.isEmpty else {
            print("DownloadManager: No audio URLs for \(catNum)")
            downloads[catNum]?.status = .failed
            saveDownloads()
            return
        }
        var totalBytes: Int64 = 0

        for (index, urlString) in validUrls.enumerated() {
            guard let url = URL(string: urlString) else {
                print("DownloadManager: Invalid URL: \(urlString)")
                downloads[catNum]?.status = .failed
                saveDownloads()
                return
            }

            do {
                let (data, response) = try await URLSession.shared.data(from: url)
                guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                    let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                    print("DownloadManager: HTTP \(code) for \(urlString)")
                    downloads[catNum]?.status = .failed
                    saveDownloads()
                    return
                }

                let filePath = trackFilePath(catNum: catNum, trackIndex: index)
                try data.write(to: filePath)
                totalBytes += Int64(data.count)

                let progress = ((index + 1) * 100) / validUrls.count
                downloads[catNum]?.progress = progress
            } catch {
                print("DownloadManager: Download error for \(urlString): \(error)")
                downloads[catNum]?.status = .failed
                saveDownloads()
                return
            }
        }

        // Download transcript (best-effort)
        if !talk.transcriptUrl.isEmpty {
            do {
                let scraper = FBAScraper()
                let text = try await scraper.fetchTranscript(talk.transcriptUrl)
                if !text.isEmpty {
                    let transcriptPath = transcriptFilePath(catNum: catNum)
                    try text.write(to: transcriptPath, atomically: true, encoding: .utf8)
                }
            } catch {
                // Non-fatal
            }
        }

        downloads[catNum]?.status = .complete
        downloads[catNum]?.totalBytes = totalBytes
        saveDownloads()
    }

    func retryDownload(catNum: String, talk: Talk) {
        deleteDownload(catNum: catNum)
        startDownload(talk: talk)
    }

    func deleteDownload(catNum: String) {
        let sanitized = sanitize(catNum)
        let dir = downloadsDir
        if let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) {
            for file in files where file.lastPathComponent.hasPrefix("\(sanitized)_") {
                try? FileManager.default.removeItem(at: file)
            }
        }
        downloads.removeValue(forKey: catNum)
        saveDownloads()
    }

    func deleteAllDownloads() {
        let catNums = Array(downloads.keys)
        for catNum in catNums {
            deleteDownload(catNum: catNum)
        }
    }

    // MARK: - Persistence

    private let savedDownloadsKey = "saved_downloads"

    private struct SavedDownload: Codable {
        let catNum: String
        let title: String
        let speaker: String
        let imageUrl: String
        let status: DownloadStatus
        let totalBytes: Int64
    }

    private func saveDownloads() {
        let saved = downloads.values.filter { $0.status == .complete || $0.status == .failed }.map {
            SavedDownload(catNum: $0.catNum, title: $0.title, speaker: $0.speaker,
                         imageUrl: $0.imageUrl, status: $0.status, totalBytes: $0.totalBytes)
        }
        if let data = try? JSONEncoder().encode(saved) {
            UserDefaults.standard.set(data, forKey: savedDownloadsKey)
        }
    }

    private func loadSavedDownloads() {
        guard let data = UserDefaults.standard.data(forKey: savedDownloadsKey),
              let saved = try? JSONDecoder().decode([SavedDownload].self, from: data) else { return }
        for s in saved {
            downloads[s.catNum] = DownloadState(
                catNum: s.catNum, title: s.title, speaker: s.speaker,
                imageUrl: s.imageUrl, status: s.status, progress: s.status == .complete ? 100 : 0,
                totalBytes: s.totalBytes
            )
        }
    }
}
