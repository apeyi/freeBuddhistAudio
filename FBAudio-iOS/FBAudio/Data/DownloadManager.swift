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
        cleanupOrphanedFiles()
    }

    /// Remove track/transcript files whose download never completed (e.g. the app
    /// was suspended mid-download). Orphans otherwise pass the bare file-exists
    /// check in trackFileUrl, so a half-downloaded series would play tracks 0..k
    /// offline and then silently stream the rest.
    private func cleanupOrphanedFiles() {
        let completePrefixes = Set(
            downloads.values
                .filter { $0.status == .complete }
                .map { sanitize($0.catNum) + "_" }
        )
        guard let files = try? FileManager.default.contentsOfDirectory(at: downloadsDir, includingPropertiesForKeys: nil) else { return }
        for file in files {
            let name = file.lastPathComponent
            let belongsToComplete = completePrefixes.contains { name.hasPrefix($0) }
            if !belongsToComplete {
                try? FileManager.default.removeItem(at: file)
            }
        }
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

    // In-flight download tasks, keyed by catNum, so cancellation can reach them.
    private var activeTasks: [String: Task<Void, Never>] = [:]

    func startDownload(talk: Talk) {
        let catNum = talk.catNum
        // Replace any existing run for this talk rather than racing it
        activeTasks[catNum]?.cancel()
        downloads[catNum] = DownloadState(
            catNum: catNum, title: talk.title, speaker: talk.speaker,
            imageUrl: talk.imageUrl, status: .pending, progress: 0, totalBytes: 0
        )
        saveDownloads()

        activeTasks[catNum] = Task {
            await performDownload(talk: talk)
            activeTasks[catNum] = nil
        }
    }

    /// Cancel an in-flight download: stop the transfer, remove partial files,
    /// and forget the entry (back to "not downloaded", not "failed").
    func cancelDownload(catNum: String) {
        activeTasks[catNum]?.cancel()
        activeTasks[catNum] = nil
        removeFiles(forCatNum: catNum)
        downloads.removeValue(forKey: catNum)
        saveDownloads()
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
                // download(from:) streams to a temp file — never buffers the whole
                // MP3 in memory (multi-track talks are easily 100+ MB, which used
                // to spike RAM and freeze the main thread on the write).
                let (tempUrl, response) = try await URLSession.shared.download(from: url)
                guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                    let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                    print("DownloadManager: HTTP \(code) for \(urlString)")
                    try? FileManager.default.removeItem(at: tempUrl)
                    downloads[catNum]?.status = .failed
                    saveDownloads()
                    return
                }

                let filePath = trackFilePath(catNum: catNum, trackIndex: index)
                try? FileManager.default.removeItem(at: filePath)
                try FileManager.default.moveItem(at: tempUrl, to: filePath)
                let attrs = try? FileManager.default.attributesOfItem(atPath: filePath.path)
                totalBytes += (attrs?[.size] as? Int64) ?? 0

                let progress = ((index + 1) * 100) / validUrls.count
                downloads[catNum]?.progress = progress
            } catch {
                // Cancellation isn't a failure — cancelDownload already cleaned up;
                // don't resurrect the entry as "failed".
                if error is CancellationError || (error as? URLError)?.code == .cancelled {
                    return
                }
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
        activeTasks[catNum]?.cancel()
        activeTasks[catNum] = nil
        removeFiles(forCatNum: catNum)
        downloads.removeValue(forKey: catNum)
        saveDownloads()
    }

    private func removeFiles(forCatNum catNum: String) {
        let sanitized = sanitize(catNum)
        if let files = try? FileManager.default.contentsOfDirectory(at: downloadsDir, includingPropertiesForKeys: nil) {
            for file in files where file.lastPathComponent.hasPrefix("\(sanitized)_") {
                try? FileManager.default.removeItem(at: file)
            }
        }
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
