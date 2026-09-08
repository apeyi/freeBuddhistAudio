import Foundation
import AVFoundation
import Combine

/// A/B player for the Digital Legacy sample: the original and the remastered
/// recording of one chapter are both prepared up front and play in lockstep,
/// with the inactive one muted — so switching version is instant, no
/// re-buffering. A demo, not background listening: paused when the page is
/// left, released with the view. Mirrors Android's SamplePlayer.
@MainActor
final class SamplePlayer: ObservableObject {
    @Published private(set) var useRemaster = true
    @Published private(set) var isPlaying = false
    @Published private(set) var isBuffering = false
    @Published private(set) var positionMs: Int64 = 0
    @Published private(set) var durationMs: Int64 = 0
    @Published private(set) var isReady = false

    private var original = AVPlayer()
    private var remaster = AVPlayer()
    private var timer: Timer?
    private var ticks = 0

    private var active: AVPlayer { useRemaster ? remaster : original }
    private var shadow: AVPlayer { useRemaster ? original : remaster }

    /// Prepare both versions (buffering starts immediately) without playing.
    func load(originalUrl: String, remasterUrl: String, startWithRemaster: Bool, knownDurationMs: Int64) {
        guard let o = URL(string: originalUrl), let r = URL(string: remasterUrl) else { return }
        useRemaster = startWithRemaster
        durationMs = knownDurationMs
        original.replaceCurrentItem(with: AVPlayerItem(url: o))
        remaster.replaceCurrentItem(with: AVPlayerItem(url: r))
        original.automaticallyWaitsToMinimizeStalling = false
        remaster.automaticallyWaitsToMinimizeStalling = false
        // Start filling both buffers now so Play is immediate
        original.preroll(atRate: 1.0)
        remaster.preroll(atRate: 1.0)
        applyVolumes()
        isReady = true
        startTimer()
    }

    func play() {
        try? AVAudioSession.sharedInstance().setActive(true)
        if let d = active.currentItem?.duration.seconds, d.isFinite, active.currentTime().seconds >= d - 0.5 {
            original.seek(to: .zero); remaster.seek(to: .zero)
        }
        shadow.seek(to: active.currentTime(), toleranceBefore: .zero, toleranceAfter: .zero)
        original.play()
        remaster.play()
        tick()
    }

    func pause() {
        original.pause()
        remaster.pause()
        tick()
    }

    func togglePlayPause() { isPlaying ? pause() : play() }

    /// Swap what's audible; the newly audible player is aligned to the current position.
    func setVersion(_ remastered: Bool) {
        guard remastered != useRemaster else { return }
        let position = active.currentTime()
        useRemaster = remastered
        active.seek(to: position, toleranceBefore: .zero, toleranceAfter: .zero)
        applyVolumes()
    }

    func seek(toFraction fraction: Double) {
        guard durationMs > 0 else { return }
        let seconds = Double(durationMs) / 1000 * min(max(fraction, 0), 1)
        let time = CMTime(seconds: seconds, preferredTimescale: 600)
        original.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero)
        remaster.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero)
        tick()
    }

    private func applyVolumes() {
        original.volume = useRemaster ? 0 : 1
        remaster.volume = useRemaster ? 1 : 0
    }

    private func startTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
    }

    private func tick() {
        isPlaying = active.timeControlStatus != .paused
        isBuffering = active.timeControlStatus == .waitingToPlayAtSpecifiedRate
        let pos = active.currentTime().seconds
        positionMs = pos.isFinite ? Int64(pos * 1000) : 0
        if let d = active.currentItem?.duration.seconds, d.isFinite, d > 0 { durationMs = Int64(d * 1000) }
        // Pull the muted player back into step if it has drifted
        ticks += 1
        if isPlaying, ticks % 10 == 0 {
            let drift = abs(shadow.currentTime().seconds - active.currentTime().seconds)
            if drift.isFinite, drift > 0.75 {
                shadow.seek(to: active.currentTime(), toleranceBefore: .zero, toleranceAfter: .zero)
            }
        }
    }

    deinit {
        timer?.invalidate()
        original.pause()
        remaster.pause()
    }
}
