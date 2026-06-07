import Foundation
import AVFoundation
import MediaPlayer
import Combine
import UIKit

@MainActor
class AudioPlayer: ObservableObject {
    static let shared = AudioPlayer()

    @Published var currentTalk: Talk?
    @Published var isPlaying = false
    @Published var currentPosition: TimeInterval = 0
    @Published var duration: TimeInterval = 0
    @Published var currentTrackIndex = 0
    @Published var playbackSpeed: Float = 1.0
    @Published var isVisible = false
    @Published var showDeleteDownloadPrompt = false
    @Published var playbackError: String?

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var statusObserver: NSKeyValueObservation?
    private var rateObserver: NSKeyValueObservation?
    private var itemEndObserver: NSObjectProtocol?
    private let persistence = PersistenceManager.shared
    private let downloadManager = DownloadManager.shared
    private var lastSaveTime: Date = .distantPast
    private var autoRetryCount = 0

    // Cached Lock Screen / Control Center artwork, keyed by the image URL it was loaded from
    private var nowPlayingArtwork: MPMediaItemArtwork?
    private var nowPlayingArtworkUrl: String?

    init() {
        playbackSpeed = persistence.playbackSpeed
        setupAudioSession()
        setupRemoteCommands()
        restoreLastPlayback()
    }

    private func setupAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Audio session setup failed: \(error)")
        }
    }

    // MARK: - Playback

    func playTalk(_ talk: Talk, fromTrackIndex: Int? = nil) {
        let savedTrackIndex = fromTrackIndex ?? persistence.getLastTrackIndex(talk.catNum)
        let savedPos = persistence.getLastPosition(talk.catNum)
        let trackIndex = savedTrackIndex < talk.tracks.count ? savedTrackIndex : 0

        currentTalk = talk
        currentTrackIndex = trackIndex
        isVisible = true

        // Set duration from metadata immediately (player will update when ready)
        let metaDuration = talk.tracks[safe: trackIndex]?.durationSeconds ?? talk.durationSeconds
        if metaDuration > 0 {
            duration = TimeInterval(metaDuration)
        }

        let url = audioUrl(for: talk, trackIndex: trackIndex)
        guard let url else { return }

        setupPlayer(url: url)

        if fromTrackIndex == nil && savedPos > 10_000 {
            let seekTime = CMTime(milliseconds: max(savedPos - 10_000, 0))
            player?.seek(to: seekTime)
        }

        player?.play()
        player?.rate = playbackSpeed
        updateNowPlayingInfo()
    }

    func playTrackByIndex(_ index: Int) {
        guard let talk = currentTalk, index < talk.tracks.count else { return }
        currentTrackIndex = index

        let metaDuration = talk.tracks[safe: index]?.durationSeconds ?? talk.durationSeconds
        if metaDuration > 0 {
            duration = TimeInterval(metaDuration)
        }

        let url = audioUrl(for: talk, trackIndex: index)
        guard let url else { return }

        // Check for saved position on this track
        let savedTrackIndex = persistence.getLastTrackIndex(talk.catNum)
        let savedPos = persistence.getLastPosition(talk.catNum)

        setupPlayer(url: url)

        if savedTrackIndex == index && savedPos > 10_000 {
            let seekTime = CMTime(milliseconds: max(savedPos - 10_000, 0))
            player?.seek(to: seekTime)
        }

        player?.play()
        player?.rate = playbackSpeed
        updateNowPlayingInfo()
    }

    private func audioUrl(for talk: Talk, trackIndex: Int) -> URL? {
        // Prefer downloaded file
        if let localUrl = downloadManager.trackFileUrl(catNum: talk.catNum, trackIndex: trackIndex) {
            return localUrl
        }
        // Stream
        let urlString = talk.tracks.isEmpty ? talk.audioUrl : (talk.tracks[safe: trackIndex]?.audioUrl ?? talk.audioUrl)
        return URL(string: urlString)
    }

    private func setupPlayer(url: URL) {
        cleanupPlayer()

        let item = AVPlayerItem(url: url)
        player = AVPlayer(playerItem: item)
        player?.rate = playbackSpeed

        // Time observer
        timeObserver = player?.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] time in
            Task { @MainActor in
                self?.handleTimeUpdate(time)
            }
        }

        // Status observer
        statusObserver = item.observe(\.status) { [weak self] item, _ in
            Task { @MainActor in
                guard let self else { return }
                switch item.status {
                case .readyToPlay:
                    self.duration = item.duration.seconds.isFinite ? item.duration.seconds : 0
                    self.autoRetryCount = 0
                    self.playbackError = nil
                case .failed:
                    self.handlePlaybackError(item.error)
                default:
                    break
                }
            }
        }

        // Rate observer
        rateObserver = player?.observe(\.rate) { [weak self] player, _ in
            Task { @MainActor in
                self?.isPlaying = player.rate > 0
            }
        }

        // End of track
        itemEndObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.handleTrackEnded()
            }
        }
    }

    private func handleTimeUpdate(_ time: CMTime) {
        currentPosition = time.seconds.isFinite ? time.seconds : 0

        // Auto-save every 5 seconds
        if isPlaying && Date().timeIntervalSince(lastSaveTime) > 5 {
            lastSaveTime = Date()
            savePlaybackState()
        }
    }

    private func handleTrackEnded() {
        guard let talk = currentTalk else { return }
        let nextIndex = currentTrackIndex + 1
        if nextIndex < talk.tracks.count {
            playTrackByIndex(nextIndex)
        } else {
            isPlaying = false
            savePlaybackState()
            if downloadManager.isDownloaded(talk.catNum) {
                showDeleteDownloadPrompt = true
            }
        }
    }

    private func handlePlaybackError(_ error: Error?) {
        let ns = error as NSError?
        let isNetwork = ns?.domain == NSURLErrorDomain || {
            switch ns?.code {
            case NSURLErrorNotConnectedToInternet, NSURLErrorTimedOut,
                 NSURLErrorCannotFindHost, NSURLErrorNetworkConnectionLost:
                return true
            default:
                return false
            }
        }()
        print("Playback error: \(error?.localizedDescription ?? "unknown")")

        // Transient network errors: auto-retry a few times (network may be flapping).
        if isNetwork && autoRetryCount < 3 {
            autoRetryCount += 1
            let delay = Double(autoRetryCount) * 2.0
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                self?.rebuildAndPlay()
            }
        } else {
            playbackError = isNetwork
                ? "Couldn't load audio — check your connection."
                : "Couldn't play this talk."
        }
    }

    /// Manual retry after an error — resets the auto-retry budget then reloads.
    func retry() {
        autoRetryCount = 0
        rebuildAndPlay()
    }

    /// Rebuild the player for the current track at the current position and play.
    private func rebuildAndPlay() {
        guard let talk = currentTalk, let url = audioUrl(for: talk, trackIndex: currentTrackIndex) else { return }
        playbackError = nil
        let resumeAt = currentPosition
        setupPlayer(url: url)
        if resumeAt > 1 {
            player?.seek(to: CMTime(seconds: resumeAt, preferredTimescale: 600))
        }
        player?.play()
        player?.rate = playbackSpeed
        updateNowPlayingInfo()
    }

    func dismissDeletePrompt() {
        showDeleteDownloadPrompt = false
    }

    func confirmDeleteAfterPlayback() {
        guard let talk = currentTalk else { return }
        downloadManager.deleteDownload(catNum: talk.catNum)
        showDeleteDownloadPrompt = false
    }

    // MARK: - Controls

    func togglePlayPause() {
        guard let player else {
            // No AVPlayer yet — e.g. playback state was restored at launch but
            // never started. Begin the restored talk at its saved position.
            if let talk = currentTalk { playTalk(talk) }
            return
        }
        if player.rate > 0 {
            player.pause()
            isPlaying = false
            savePlaybackState()
        } else {
            player.play()
            player.rate = playbackSpeed
            isPlaying = true
        }
        updateNowPlayingInfo()
    }

    func seekTo(_ seconds: TimeInterval) {
        player?.seek(to: CMTime(seconds: seconds, preferredTimescale: 600))
        currentPosition = seconds
        updateNowPlayingInfo()
    }

    func seekForward() {
        let newPos = currentPosition + 10
        seekTo(min(newPos, duration))
    }

    func seekBack() {
        let newPos = currentPosition - 10
        seekTo(max(newPos, 0))
    }

    func nextTrack() {
        guard let talk = currentTalk, currentTrackIndex + 1 < talk.tracks.count else { return }
        playTrackByIndex(currentTrackIndex + 1)
    }

    func previousTrack() {
        guard currentTrackIndex > 0 else { return }
        playTrackByIndex(currentTrackIndex - 1)
    }

    func setPlaybackSpeed(_ speed: Float) {
        playbackSpeed = speed
        persistence.playbackSpeed = speed
        if let player, player.rate > 0 {
            player.rate = speed
        }
        updateNowPlayingInfo()
    }

    // MARK: - State Persistence

    private func savePlaybackState() {
        guard let talk = currentTalk else { return }
        let posMs = Int64(currentPosition * 1000)
        let durMs = Int64(duration * 1000)
        guard posMs > 0 || durMs > 0 else { return }

        persistence.savePlaybackState(
            catNum: talk.catNum, position: posMs,
            trackIndex: currentTrackIndex, duration: durMs
        )

        // Update recently listened
        let tracks = talk.tracks
        let cumulativePos: Int64
        if tracks.count > 1 {
            let priorDuration = tracks.prefix(currentTrackIndex).reduce(0) { $0 + Int64($1.durationSeconds) * 1000 }
            cumulativePos = priorDuration + posMs
        } else {
            cumulativePos = posMs
        }
        let totalDur = talk.durationSeconds > 0 ? talk.durationSeconds :
            (tracks.count > 1 ? tracks.reduce(0) { $0 + $1.durationSeconds } : Int(duration))

        persistence.updateRecentlyListened(PersistenceManager.RecentlyListened(
            catNum: talk.catNum, title: talk.title, speaker: talk.speaker,
            imageUrl: talk.imageUrl, positionMs: cumulativePos,
            trackIndex: currentTrackIndex, totalDurationSeconds: totalDur,
            timestamp: Date()
        ))
    }

    private func restoreLastPlayback() {
        guard let catNum = persistence.getLastCatNum() else { return }
        let lastPos = persistence.getLastPosition(catNum)
        let lastTrackIndex = persistence.getLastTrackIndex(catNum)
        let lastDuration = persistence.getLastDuration(catNum)

        Task {
            guard let talk = await TalkRepository.shared.getTalkDetail(catNum) else { return }
            let trackDuration = talk.tracks[safe: lastTrackIndex]
                .map { TimeInterval($0.durationSeconds) }
                ?? (lastDuration > 0 ? TimeInterval(lastDuration) / 1000 : TimeInterval(talk.durationSeconds))

            currentTalk = talk
            isVisible = true
            currentPosition = min(TimeInterval(lastPos) / 1000, trackDuration)
            duration = trackDuration
            currentTrackIndex = lastTrackIndex
            isPlaying = false
        }
    }

    // MARK: - Now Playing Info / Remote Commands

    private func updateNowPlayingInfo() {
        guard let talk = currentTalk else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: talk.tracks[safe: currentTrackIndex]?.title.nonEmpty ?? talk.title,
            MPMediaItemPropertyArtist: talk.speaker,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentPosition,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? playbackSpeed : 0,
        ]
        if talk.tracks.count > 1 {
            info[MPNowPlayingInfoPropertyChapterNumber] = currentTrackIndex
            info[MPNowPlayingInfoPropertyChapterCount] = talk.tracks.count
        }
        if let nowPlayingArtwork, nowPlayingArtworkUrl == talk.imageUrl {
            info[MPMediaItemPropertyArtwork] = nowPlayingArtwork
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info

        // Fetch artwork lazily the first time we see a new talk's image, then refresh
        if nowPlayingArtworkUrl != talk.imageUrl, !talk.imageUrl.isEmpty {
            loadNowPlayingArtwork(urlString: talk.imageUrl)
        }
    }

    private func loadNowPlayingArtwork(urlString: String) {
        guard let url = URL(string: urlString) else { return }
        Task { [weak self] in
            guard let (data, _) = try? await URLSession.shared.data(from: url),
                  let image = UIImage(data: data) else { return }
            let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            await MainActor.run {
                guard let self else { return }
                self.nowPlayingArtwork = artwork
                self.nowPlayingArtworkUrl = urlString
                // Re-apply now that artwork is ready, but only if it's still the current talk
                if self.currentTalk?.imageUrl == urlString {
                    self.updateNowPlayingInfo()
                }
            }
        }
    }

    private func setupRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.togglePlayPause() }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.togglePlayPause() }
            return .success
        }
        center.skipForwardCommand.preferredIntervals = [10]
        center.skipForwardCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.seekForward() }
            return .success
        }
        center.skipBackwardCommand.preferredIntervals = [10]
        center.skipBackwardCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.seekBack() }
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.nextTrack() }
            return .success
        }
        center.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.previousTrack() }
            return .success
        }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self?.seekTo(event.positionTime) }
            return .success
        }
    }

    // MARK: - Cleanup

    private func cleanupPlayer() {
        if let timeObserver { player?.removeTimeObserver(timeObserver) }
        if let itemEndObserver { NotificationCenter.default.removeObserver(itemEndObserver) }
        statusObserver?.invalidate()
        rateObserver?.invalidate()
        timeObserver = nil
        itemEndObserver = nil
        statusObserver = nil
        rateObserver = nil
    }
}

// MARK: - Helpers

private extension CMTime {
    init(milliseconds: Int64) {
        self = CMTime(seconds: Double(milliseconds) / 1000, preferredTimescale: 600)
    }
}

extension Collection {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
