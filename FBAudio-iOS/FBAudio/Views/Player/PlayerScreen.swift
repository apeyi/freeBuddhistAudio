import SwiftUI
import UIKit

/// Full-screen player (v1.0 design): coloured from the artwork (hourly colour for black-and-white
/// art, gold on remastered talks), "Playing from", artwork, title, speaker, the current track
/// scrolling sideways when long, scrubber, transport (back 15 / previous / play / next / forward
/// 30; one-track talks swap previous and next for speed and sleep), the talk tools row, and white
/// sheets for More, Playback speed, Sleep timer and Tracks. Swipe down to close. Mirrors Android.
struct PlayerScreen: View {
    @ObservedObject var player: AudioPlayer
    // Observed so the download tool re-renders when a download finishes while paused.
    @ObservedObject private var downloadManager = DownloadManager.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.hourlyAccent) private var hourly
    @State private var gradient: PlayerColors.Gradient?
    @State private var showMore = false
    @State private var showSpeed = false
    @State private var showSleep = false
    @State private var showTracks = false
    @State private var showReport = false
    // Local scrub state: while dragging, the scrubber follows the finger only.
    @State private var scrubFraction: Double?
    @State private var dragY: CGFloat = 0

    let onNavigateToDetail: (String) -> Void
    let onSpeakerClick: (String) -> Void
    var onSeriesClick: (String) -> Void = { _ in }
    var onTranscriptClick: (String, String) -> Void = { _, _ in }
    var onDonateClick: () -> Void = {}
    var onPlayingFromClick: (Int) -> Void = { _ in }

    private static let speeds: [Float] = [0.75, 1, 1.25, 1.5, 1.75, 2]
    private let white72 = Color.white.opacity(0.72)
    private let white88 = Color.white.opacity(0.88)

    var body: some View {
        if let talk = player.currentTalk {
            let g = currentGradient(talk)
            content(talk, gradient: g)
                .background(background(g).ignoresSafeArea())
                .offset(y: max(dragY, 0))
                .gesture(
                    DragGesture(minimumDistance: 20)
                        .onChanged { v in if v.translation.height > 0 { dragY = v.translation.height } }
                        .onEnded { v in
                            if v.translation.height > 140 { dismiss() } else { withAnimation(.spring()) { dragY = 0 } }
                        }
                )
                .task(id: talk.catNum) { await extractGradient(talk) }
                .fbaPageTheme(talk.hasRemaster ? .remastered : .hourly)
                .preferredColorScheme(.dark)   // light status bar on the coloured background
        } else {
            Color.clear.onAppear { dismiss() }
        }
    }

    // MARK: - Colour

    private func currentGradient(_ talk: Talk) -> PlayerColors.Gradient {
        if talk.hasRemaster { return PlayerColors.forGold() }
        return gradient ?? PlayerColors.forAccent(hourly)
    }

    private func background(_ g: PlayerColors.Gradient) -> some View {
        ZStack {
            LinearGradient(stops: [.init(color: Color(hex: g.top), location: 0),
                                   .init(color: Color(hex: g.mid), location: 0.45),
                                   .init(color: Color(hex: g.bottom), location: 1)],
                           startPoint: .top, endPoint: .bottom)
            Color(hex: 0x6B6661).opacity(0.15)
            LinearGradient(stops: [.init(color: .white.opacity(0.05), location: 0),
                                   .init(color: .black.opacity(0.1), location: 0.6),
                                   .init(color: .black.opacity(0.3), location: 1)],
                           startPoint: .top, endPoint: .bottom)
        }
    }

    /// Main colours of the artwork from a 24 x 24 thumbnail (see ArtworkPalette).
    private func extractGradient(_ talk: Talk) async {
        guard !talk.hasRemaster,
              let url = ArtworkRule.talkImage(bannerUrl: talk.bannerUrl, speakerImageUrl: talk.speakerImageUrl,
                                              listImageUrl: talk.imageUrl),
              let image = await ArtworkLoader.shared.image(for: url, aspect: 372.0 / 272.0) else { return }
        let pixels = Self.pixels(of: image, side: 24)
        gradient = PlayerColors.fromColors(ArtworkPalette.colors(pixels: pixels))
    }

    private static func pixels(of image: UIImage, side: Int) -> [UInt32] {
        guard let cg = image.cgImage else { return [] }
        var data = [UInt8](repeating: 0, count: side * side * 4)
        guard let ctx = CGContext(data: &data, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
                                  space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return [] }
        ctx.interpolationQuality = .medium
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: side, height: side))
        var out: [UInt32] = []
        out.reserveCapacity(side * side)
        var i = 0
        while i + 3 < data.count {
            if data[i + 3] >= 128 {
                let r = UInt32(data[i]) << 16
                let g = UInt32(data[i + 1]) << 8
                let b = UInt32(data[i + 2])
                out.append(r | g | b)
            }
            i += 4
        }
        return out
    }

    // MARK: - Content

    private func content(_ talk: Talk, gradient g: PlayerColors.Gradient) -> some View {
        let multiTrack = talk.tracks.count > 1
        return ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                TalkArtwork(talk: talk, aspect: 362.0 / 272.0)
                    .frame(maxWidth: .infinity).frame(height: 272)
                    .clipShape(RoundedRectangle(cornerRadius: FbaDimens.radiusArtwork))
                titleBlock(talk, multiTrack: multiTrack)
                scrubber
                transport(multiTrack: multiTrack, playColor: Color(hex: g.top))
                if player.isReconnecting && player.playbackError == nil {
                    Text("Reconnecting").font(.subheadline).foregroundStyle(white88).frame(maxWidth: .infinity)
                }
                if let err = player.playbackError {
                    VStack(spacing: 6) {
                        Text(err).font(.subheadline).foregroundStyle(.white).multilineTextAlignment(.center)
                        Button("Try again") { player.retry() }.foregroundStyle(.white)
                    }
                    .frame(maxWidth: .infinity)
                }
                toolsRow(talk)
            }
            .padding(.horizontal, FbaDimens.margin)
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
        .sheet(isPresented: $showMore) { moreSheet(talk, multiTrack: multiTrack) }
        .sheet(isPresented: $showSpeed) {
            ChoiceSheet(title: "Playback speed", subtitle: "Changes the pace, not the pitch.",
                        options: Self.speeds.map { ($0 == 1 ? "1× (normal)" : Self.speedLabel($0), $0) },
                        selected: player.playbackSpeed) { player.setPlaybackSpeed($0); showSpeed = false }
        }
        .sheet(isPresented: $showSleep) {
            ChoiceSheet(title: "Sleep timer", subtitle: "Pauses playback gently, with a short fade out.",
                        options: SleepOption.allCases.filter { multiTrack || $0 != .endOfTrack }.map { ($0.label, $0) },
                        selected: player.sleepOption) { player.setSleepTimer($0); showSleep = false }
        }
        .sheet(isPresented: $showTracks) { tracksSheet(talk) }
        .confirmationDialog("Report a problem", isPresented: $showReport, titleVisibility: .visible) {
            ForEach(ReportProblem.reasons, id: \.self) { reason in Button(reason) { report(reason, talk: talk) } }
            Button("Cancel", role: .cancel) {}
        } message: { Text("\(talk.title) · \(talk.speaker)") }
    }

    private var header: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "chevron.down").font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 40, height: 40).background(Color.white.opacity(0.18), in: Circle())
            }
            .accessibilityLabel("Close player")
            Spacer()
            if !player.playingFrom.isEmpty {
                Button { if let tab = player.playingFromTab { onPlayingFromClick(tab) } } label: {
                    VStack(spacing: 2) {
                        Text("PLAYING FROM").font(.caption2.weight(.semibold)).tracking(0.66).foregroundStyle(white72)
                        Text(player.playingFrom).font(.subheadline.weight(.medium)).foregroundStyle(.white)
                    }
                }
                .buttonStyle(.plain)
            }
            Spacer()
            Color.clear.frame(width: 40, height: 40)
        }
        .padding(.top, 6)
    }

    private func titleBlock(_ talk: Talk, multiTrack: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(talk.title).font(.title2.weight(.bold)).foregroundStyle(.white).lineLimit(3)
            if !talk.speaker.isEmpty {
                Button { onSpeakerClick(talk.speaker) } label: {
                    Text(talk.speaker).font(.headline.weight(.medium)).underline().foregroundStyle(.white)
                }
                .buttonStyle(.plain)
            }
            if talk.hasRemaster {
                HStack(spacing: 6) {
                    BundleImage(name: "dharmachakra_gold").frame(width: 18, height: 18)
                    Text("digitally remastered").font(.subheadline.weight(.medium)).underline().foregroundStyle(white88)
                }
                .padding(.top, 2)
            }
            if multiTrack, let track = talk.tracks[safe: player.currentTrackIndex] {
                MarqueeText(text: "Track \(player.currentTrackIndex + 1) of \(talk.tracks.count) · \(track.title.isEmpty ? "Track \(player.currentTrackIndex + 1)" : track.title)")
                    .font(.callout.weight(.medium))
                    .foregroundStyle(white88)
                    .padding(.top, 12)
            }
            if talk.hasRemaster {
                HStack(spacing: 12) {
                    Text("Original").font(.callout.weight(.medium)).foregroundStyle(player.useRemaster ? white72 : .white)
                    Toggle("Enhanced version", isOn: Binding(get: { player.useRemaster }, set: { player.setUseRemaster($0) }))
                        .labelsHidden().tint(Color.white.opacity(0.35)).disabled(player.versionLocked)
                    Text("Enhanced").font(.callout.weight(.medium)).foregroundStyle(player.useRemaster ? .white : white72)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 10)
                if player.versionLocked {
                    Text("Playing the downloaded version").font(.caption).foregroundStyle(white72).frame(maxWidth: .infinity)
                }
            }
        }
    }

    private var scrubber: some View {
        let duration = player.duration
        let live = duration > 0 ? (player.currentPosition / duration).safeFraction() : 0
        let fraction = scrubFraction ?? live
        let shown = scrubFraction.map { $0 * duration } ?? player.currentPosition
        return VStack(spacing: 8) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.3)).frame(height: 4)
                    Capsule().fill(.white).frame(width: geo.size.width * fraction, height: 4)
                    Circle().fill(.white).frame(width: 14, height: 14)
                        .offset(x: (geo.size.width - 14) * fraction)
                }
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0)
                    .onChanged { v in scrubFraction = min(max(v.location.x / geo.size.width, 0), 1) }
                    .onEnded { _ in
                        if let f = scrubFraction, duration > 0 { player.seekTo(f * duration) }
                        scrubFraction = nil
                    })
            }
            .frame(height: 24)
            .accessibilityElement()
            .accessibilityLabel("Playback position")
            .accessibilityValue(formatDuration(Int(shown)))
            .accessibilityAdjustableAction { dir in
                switch dir {
                case .increment: player.seekForward()
                case .decrement: player.seekBack()
                @unknown default: break
                }
            }
            HStack {
                Text(formatDuration(Int(shown)))
                Spacer()
                Text("−" + formatDuration(max(Int(duration - shown), 0)))
            }
            .font(.caption.weight(.medium)).foregroundStyle(white72)
        }
    }

    private func transport(multiTrack: Bool, playColor: Color) -> some View {
        let talk = player.currentTalk
        let hasPrevious = player.currentTrackIndex > 0
        let hasNext = player.currentTrackIndex < (talk?.tracks.count ?? 0) - 1
        return HStack {
            if multiTrack {
                SkipButton(seconds: 15, forward: false) { player.seekBack() }
                Spacer()
                Button { player.previousTrack() } label: { Image(systemName: "backward.end.fill").font(.title2) }
                    .foregroundStyle(hasPrevious ? .white : white72).disabled(!hasPrevious)
                    .accessibilityLabel("Previous track")
            } else {
                Button { showSpeed = true } label: { Text(Self.speedLabel(player.playbackSpeed)).font(.headline.weight(.bold)) }
                    .foregroundStyle(.white).accessibilityLabel("Playback speed \(Self.speedLabel(player.playbackSpeed))")
                Spacer()
                SkipButton(seconds: 15, forward: false) { player.seekBack() }
            }
            Spacer()
            Button { player.togglePlayPause() } label: {
                ZStack {
                    Circle().fill(.white).frame(width: 76, height: 76)
                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill").font(.system(size: 30)).foregroundStyle(playColor)
                    if player.isBuffering {
                        ProgressView().tint(.white).scaleEffect(1.6).offset(y: 52)
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(player.isPlaying ? "Pause" : "Play")
            Spacer()
            if multiTrack {
                Button { player.nextTrack() } label: { Image(systemName: "forward.end.fill").font(.title2) }
                    .foregroundStyle(hasNext ? .white : white72).disabled(!hasNext)
                    .accessibilityLabel("Next track")
                Spacer()
                SkipButton(seconds: 30, forward: true) { player.seekForward() }
            } else {
                SkipButton(seconds: 30, forward: true) { player.seekForward() }
                Spacer()
                Button { showSleep = true } label: { Image(systemName: player.sleepOption == .off ? "moon" : "moon.fill").font(.title2) }
                    .foregroundStyle(.white).accessibilityLabel(player.sleepOption == .off ? "Sleep timer" : "Sleep timer on")
            }
        }
        .padding(.horizontal, 6)
        .padding(.top, 10)
        .padding(.bottom, 18)
    }

    private func toolsRow(_ talk: Talk) -> some View {
        let entry = downloadManager.downloads[talk.catNum].flatMap { $0.transcriptOnly ? nil : $0 }
        return HStack(alignment: .top) {
            switch entry?.status {
            case .complete: WhiteTool(label: "Downloaded", systemImage: "checkmark.circle.fill") {}
            case .downloading, .pending: WhiteTool(label: "Downloading", systemImage: "arrow.down.circle") {
                downloadManager.cancelDownload(catNum: talk.catNum)
            }
            default: WhiteTool(label: "Download", systemImage: "arrow.down.to.line") { downloadManager.startDownload(talk: talk) }
            }
            if !talk.transcriptUrl.isEmpty {
                Spacer(minLength: 0)
                WhiteTool(label: "Transcript", systemImage: "doc.text") { onTranscriptClick(talk.transcriptUrl, talk.catNum) }
            }
            Spacer(minLength: 0)
            WhiteTool(label: "Donate", systemImage: "heart.fill", action: onDonateClick)   // white heart on the coloured player
            Spacer(minLength: 0)
            ShareLink(item: DetailScreen.talkURL(talk.catNum), subject: Text(talk.title),
                      message: Text("\(talk.title) by \(talk.speaker)")) {
                WhiteToolLabel(label: "Share", systemImage: "square.and.arrow.up")
            }
            .buttonStyle(.plain)
            Spacer(minLength: 0)
            WhiteTool(label: "More", systemImage: "ellipsis") { showMore = true }
        }
        .padding(.top, 4)
    }

    // MARK: - Sheets

    private func moreSheet(_ talk: Talk, multiTrack: Bool) -> some View {
        NavigationStack {
            List {
                if multiTrack {
                    Section {
                        sheetRow("Playback speed", value: Self.speedLabel(player.playbackSpeed)) { showMore = false; showSpeed = true }
                        sheetRow("Sleep timer", value: player.sleepOption.shortLabel) { showMore = false; showSleep = true }
                        sheetRow("Tracks", value: "\(talk.tracks.count)") { showMore = false; showTracks = true }
                    }
                }
                Section {
                    sheetRow("Go to talk page") { showMore = false; onNavigateToDetail(talk.catNum) }
                    if !talk.series.isEmpty {
                        sheetRow("Go to series") { showMore = false; onSeriesClick(talk.seriesHref.isEmpty ? talk.series : talk.seriesHref) }
                    }
                    if !talk.speaker.isEmpty { sheetRow("Go to speaker") { showMore = false; onSpeakerClick(talk.speaker) } }
                    sheetRow("Copy link") { UIPasteboard.general.string = DetailScreen.talkURL(talk.catNum).absoluteString; showMore = false }
                    sheetRow("Report a problem") { showMore = false; showReport = true }
                }
            }
            .navigationTitle(talk.title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showMore = false } } }
        }
        .presentationDetents([.medium, .large])
        .preferredColorScheme(.light)
    }

    private func sheetRow(_ label: String, value: String? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label).foregroundStyle(FbaColors.black)
                Spacer()
                if let value { Text(value).foregroundStyle(FbaColors.meta) }
            }
        }
    }

    private func tracksSheet(_ talk: Talk) -> some View {
        NavigationStack {
            ScrollViewReader { proxy in
                List {
                    ForEach(Array(talk.tracks.enumerated()), id: \.offset) { index, track in
                        Button { player.playTrackByIndex(index); showTracks = false } label: {
                            TrackSheetRow(index: index, track: track, current: player.currentTrackIndex,
                                          positionMs: Int64(player.currentPosition * 1000))
                        }
                        .id(index)
                    }
                }
                .listStyle(.plain)
                .onAppear { proxy.scrollTo(player.currentTrackIndex, anchor: .center) }
            }
            .navigationTitle("Tracks (\(talk.tracks.count))").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showTracks = false } } }
        }
        .presentationDetents([.medium, .large])   // opens at half height, drag up for all
        .preferredColorScheme(.light)
    }

    private func report(_ reason: String, talk: Talk) {
        let position = Int(PlaybackMath.cumulativePositionMs(tracks: talk.tracks, trackIndex: player.currentTrackIndex,
                                                             positionInTrackMs: Int64(player.currentPosition * 1000)) / 1000)
        let email = ReportProblem.email(
            reason: reason, talkTitle: talk.title, catNum: talk.catNum, positionSeconds: position,
            device: "\(UIDevice.current.model), iOS \(UIDevice.current.systemVersion)",
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "",
            signedIn: AuthRepository.shared.isLoggedIn)
        if let url = ReportProblem.mailtoURL(email), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        } else {
            UIPasteboard.general.string = email.to
        }
    }

    static func speedLabel(_ speed: Float) -> String {
        let s = speed == speed.rounded() ? String(Int(speed)) : String(format: "%g", speed)
        return "\(s)×"
    }
}

// MARK: - Pieces

/// Circular arrow with the seconds inside (back 15, forward 30).
private struct SkipButton: View {
    let seconds: Int
    let forward: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: forward ? "goforward.\(seconds)" : "gobackward.\(seconds)")
                .font(.system(size: 28)).foregroundStyle(.white)
        }
        .accessibilityLabel(forward ? "Forward \(seconds) seconds" : "Back \(seconds) seconds")
    }
}

private struct WhiteToolLabel: View {
    let label: String
    let systemImage: String
    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: systemImage).font(.system(size: 20)).foregroundStyle(.white)
                .frame(width: FbaDimens.toolCircle, height: FbaDimens.toolCircle)
                .background(Color.white.opacity(0.18), in: Circle())
            Text(label).font(.caption.weight(.medium)).foregroundStyle(.white)
        }
    }
}

private struct WhiteTool: View {
    let label: String
    let systemImage: String
    let action: () -> Void
    var body: some View {
        Button(action: action) { WhiteToolLabel(label: label, systemImage: systemImage) }
            .buttonStyle(.plain)
            .accessibilityLabel(label)
    }
}

/// A white sheet with a one-line explanation and ticked choices (speed, sleep).
private struct ChoiceSheet<T: Equatable>: View {
    let title: String
    let subtitle: String
    let options: [(String, T)]
    let selected: T
    let onSelect: (T) -> Void
    @Environment(\.pageColors) private var page

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(Array(options.enumerated()), id: \.offset) { _, option in
                        Button { onSelect(option.1) } label: {
                            HStack {
                                Text(option.0).foregroundStyle(option.1 == selected ? page.deep : FbaColors.black)
                                Spacer()
                                if option.1 == selected { Image(systemName: "checkmark").foregroundStyle(page.deep) }
                            }
                        }
                        .accessibilityAddTraits(option.1 == selected ? .isSelected : [])
                    }
                } header: { Text(subtitle).textCase(nil) }
            }
            .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium])
        .preferredColorScheme(.light)
    }
}

private struct TrackSheetRow: View {
    let index: Int
    let track: Track
    let current: Int
    let positionMs: Int64
    @Environment(\.pageColors) private var page

    var body: some View {
        HStack(spacing: 12) {
            if index < current {
                Image(systemName: "checkmark").font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 18, height: 18).background(page.deep, in: Circle())
            } else {
                Text("\(index + 1)").font(.subheadline.weight(.semibold)).foregroundStyle(FbaColors.grey500)
            }
            VStack(alignment: .leading, spacing: 8) {
                Text(track.title.isEmpty ? "Track \(index + 1)" : track.title)
                    .font(.body.weight(index == current ? .semibold : .regular))
                    .foregroundStyle(index == current ? page.deep : FbaColors.black)
                if index == current && track.durationSeconds > 0 {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(FbaColors.divider)
                            Capsule().fill(page.deep).frame(width: geo.size.width * TalkProgress.trackFraction(track, positionMs: positionMs))
                        }
                    }
                    .frame(height: 4)
                }
            }
            Spacer()
            Text(trailing).font(.subheadline).foregroundStyle(FbaColors.meta)
        }
        .padding(.vertical, 6)
    }

    private var trailing: String {
        if index < current { return "Listened" }
        if index == current { return PlaybackMath.timeLeftLabel(TalkProgress.trackRemainingSeconds(track, positionMs: positionMs)) ?? "" }
        return track.durationSeconds > 0 ? formatDuration(track.durationSeconds) : ""
    }
}

/// One line that scrolls slowly sideways when it doesn't fit, pausing at the start each time
/// (as Apple does with long titles); it never truncates.
private struct MarqueeText: View {
    let text: String
    @State private var textWidth: CGFloat = 0
    @State private var offset: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            let overflow = textWidth - geo.size.width
            Text(text)
                .fixedSize()
                .background(GeometryReader { t in Color.clear.onAppear { textWidth = t.size.width } })
                .offset(x: overflow > 0 ? offset : 0)
                .task(id: "\(text)|\(geo.size.width)|\(textWidth)") {
                    guard overflow > 0 else { offset = 0; return }
                    while !Task.isCancelled {
                        offset = 0
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        withAnimation(.linear(duration: Double(overflow) / 30)) { offset = -overflow }
                        try? await Task.sleep(nanoseconds: UInt64(Double(overflow) / 30 * 1_000_000_000) + 1_500_000_000)
                    }
                }
        }
        .frame(height: 22)
        .clipped()
        .accessibilityLabel(text)
    }
}
