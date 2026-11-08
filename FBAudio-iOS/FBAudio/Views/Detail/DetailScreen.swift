import SwiftUI
import UIKit

/// Talk page (v1.0 design, iOS Candidate Release): artwork, title block with speaker / length
/// and year / place / series links, the version switch on remastered talks, one primary action
/// (Play or Resume), the grey tool row, description, Tracks | Related talks (or Description |
/// Related talks for single-track talks) and Support FBA. Remastered talks are gold everywhere
/// on the page. Mirrors Android `DetailScreen`.
struct DetailScreen: View {
    let catNum: String
    let onPlay: (String) -> Void
    let onSpeakerClick: (String) -> Void
    let onSeriesClick: (String) -> Void
    let onTranscriptClick: (String, String) -> Void
    var onDonateClick: () -> Void = {}
    var onJoinClick: () -> Void = {}
    var onPlaceClick: (_ slug: String, _ name: String) -> Void = { _, _ in }
    var onYearClick: (Int) -> Void = { _ in }
    var onTalkClick: (String) -> Void = { _ in }
    var onDigitalLegacyClick: () -> Void = {}
    /// False when downloads are member-only and the user isn't a member.
    var canDownload: Bool = true

    @ObservedObject private var player = AudioPlayer.shared
    @ObservedObject private var downloadManager = DownloadManager.shared
    @State private var talk: Talk?
    @State private var related: [SearchResult]?
    @State private var isLoading = true
    @State private var error: String?
    @State private var showDeleteConfirm = false
    @State private var showMore = false
    @State private var showReport = false
    @State private var tab = 0
    @State private var useRemaster = true

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
            } else if let error {
                VStack(spacing: 16) {
                    Text(error).foregroundStyle(.secondary)
                    Button("Retry") { loadTalk() }
                }
            } else if let talk {
                content(talk)
                    .fbaPageTheme(talk.hasRemaster ? .remastered : .hourly)
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .task { loadTalk() }
        .task { related = await TalkRepository.shared.getRelatedTalks(catNum) }
        .alert("Remove download?", isPresented: $showDeleteConfirm) {
            Button("Remove", role: .destructive) { downloadManager.deleteDownload(catNum: catNum) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the offline files from your iPhone.")
        }
    }

    private func loadTalk() {
        isLoading = true
        error = nil
        useRemaster = AppSettings.shared.useRemaster(catNum)
        Task {
            // Logged in: fetch fresh so the page carries the account's saved position.
            if let result = await TalkRepository.shared.getTalkDetailForPlayback(catNum) {
                talk = result
            } else {
                error = "Could not load this talk."
            }
            isLoading = false
        }
    }

    // MARK: - State

    private var isActive: Bool { player.currentTalk?.catNum == catNum }
    private var isPlaying: Bool { isActive && player.isPlaying }

    /// Live position while this talk is in the player, else the saved point (the account
    /// checkpoint wins when signed in, as in AudioPlayer.playTalk).
    private func resumePoint(for talk: Talk) -> TalkProgress.Resume? {
        var track: Int
        var pos: Int64
        if isActive {
            track = player.currentTrackIndex
            pos = Int64(player.currentPosition * 1000)
        } else {
            track = PersistenceManager.shared.getLastTrackIndex(catNum)
            pos = PersistenceManager.shared.getLastPosition(catNum)
            if let cp = talk.checkpoint, let i = talk.tracks.firstIndex(where: { $0.trackId == cp.trackId }) {
                track = i
                pos = Int64(cp.timeSeconds) * 1000
            }
        }
        let total = PlaybackMath.totalDurationSeconds(talkDurationSeconds: talk.durationSeconds,
                                                      tracks: talk.tracks, playerDurationMs: 0)
        return TalkProgress.resume(tracks: talk.tracks, totalSeconds: total, savedTrack: track, savedPositionMs: pos)
    }

    // MARK: - Content

    private func content(_ talk: Talk) -> some View {
        let resume = resumePoint(for: talk)
        let multiTrack = talk.tracks.count > 1
        return ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                TalkArtwork(talk: talk, aspect: 362.0 / 240.0)
                    .frame(maxWidth: .infinity)
                    .frame(height: 240)
                    .clipShape(RoundedRectangle(cornerRadius: FbaDimens.radiusArtwork))

                TitleBlock(talk: talk, onSpeakerClick: onSpeakerClick, onYearClick: onYearClick,
                           onPlaceClick: onPlaceClick, onSeriesClick: onSeriesClick,
                           onDigitalLegacyClick: onDigitalLegacyClick)

                if talk.hasRemaster {
                    VersionSwitch(enhanced: $useRemaster,
                                  enabled: downloadManager.downloads[catNum]?.status != .complete) { value in
                        AppSettings.shared.setRemasterChoice(catNum, useRemaster: value)
                        if isActive { player.setUseRemaster(value) }
                    }
                }

                VStack(spacing: 10) {
                    FbaPrimaryButton(title: isPlaying ? "Pause" : (resume != nil ? "Resume" : "Play"),
                                     systemImage: isPlaying ? "pause.fill" : "play.fill") {
                        if isActive { player.togglePlayPause() } else { onPlay(catNum) }
                    }
                    if let resume, !isPlaying {
                        VStack(spacing: 2) {
                            Text(TalkProgress.resumeLabel(resume, trackCount: talk.tracks.count))
                                .font(.subheadline).foregroundStyle(FbaColors.meta)
                            Button("Play from the start") { player.playTalk(talk, fromTrackIndex: 0) }
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(FbaColors.nearBlack)
                                .underline()
                        }
                        .frame(maxWidth: .infinity)
                    }
                    toolRow(talk)
                }

                if multiTrack && !talk.description.isEmpty {
                    ExpandableText(text: talk.description)
                }

                FbaTextTabs(titles: multiTrack ? ["Tracks (\(talk.tracks.count))", "Related talks"]
                                               : ["Description", "Related talks"],
                            selected: $tab)
                if tab == 1 {
                    RelatedTalksList(related: related, onTalkClick: onTalkClick)
                } else if multiTrack {
                    trackList(talk, resume: resume)
                } else {
                    Text(talk.description.isEmpty ? "No description for this talk." : talk.description)
                        .font(.body).lineSpacing(4).foregroundStyle(FbaColors.black)
                }

                SupportFbaButton(action: onDonateClick).padding(.top, 4)
            }
            .padding(.horizontal, FbaDimens.margin)
            .padding(.bottom, 24)
        }
        .miniPlayerClearance()
        .confirmationDialog(talk.title, isPresented: $showMore, titleVisibility: .visible) {
            let saved = downloadManager.downloads[catNum]?.status == .complete
            if !talk.transcriptUrl.isEmpty && !saved {
                Button("Download transcript") {
                    if canDownload { downloadManager.startTranscriptDownload(talk: talk) } else { onJoinClick() }
                }
            }
            Button("Copy link") { UIPasteboard.general.string = Self.talkURL(catNum).absoluteString }
            if !talk.series.isEmpty {
                Button("Go to series") { onSeriesClick(talk.seriesHref.isEmpty ? talk.series : talk.seriesHref) }
            }
            if !talk.speaker.isEmpty { Button("Go to speaker") { onSpeakerClick(talk.speaker) } }
            Button("Report a problem") { showReport = true }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog("Report a problem", isPresented: $showReport, titleVisibility: .visible) {
            ForEach(ReportProblem.reasons, id: \.self) { reason in
                Button(reason) { report(reason, talk: talk) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("\(talk.title) · \(talk.speaker)")
        }
    }

    private func toolRow(_ talk: Talk) -> some View {
        HStack(alignment: .top) {
            downloadTool(talk)
            if !talk.transcriptUrl.isEmpty {
                Spacer(minLength: 0)
                ToolCircle("Transcript", systemImage: "doc.text") { onTranscriptClick(talk.transcriptUrl, catNum) }
            }
            Spacer(minLength: 0)
            DonateTool(action: onDonateClick)
            Spacer(minLength: 0)
            ShareLink(item: Self.talkURL(catNum), subject: Text(talk.title),
                      message: Text("\(talk.title) by \(talk.speaker)")) {
                VStack(spacing: 6) {
                    ToolIcon(systemImage: "square.and.arrow.up", tint: FbaColors.black)
                        .frame(width: FbaDimens.toolCircle, height: FbaDimens.toolCircle)
                        .background(FbaColors.circle, in: Circle())
                    Text("Share").font(.caption.weight(.medium)).foregroundStyle(Color(hex: 0x3D3D3D))
                }
            }
            .buttonStyle(.plain)
            Spacer(minLength: 0)
            ToolCircle("More", systemImage: "ellipsis", accessibilityText: "More options") { showMore = true }
        }
        .padding(.top, 4)
    }

    @ViewBuilder
    private func downloadTool(_ talk: Talk) -> some View {
        // A transcript-only download doesn't count as the audio being saved.
        let state = downloadManager.downloads[talk.catNum].flatMap { $0.transcriptOnly ? nil : $0 }
        switch state?.status {
        case .complete:
            DownloadedTool { showDeleteConfirm = true }
        case .downloading, .pending:
            let progress = state?.progress ?? 0
            ToolCircle(label: "\(progress)%", accessibilityText: "Downloading, \(progress) percent. Stop download",
                       action: { downloadManager.cancelDownload(catNum: catNum) }) {
                ProgressRing(fraction: Double(progress) / 100)
            }
        default:
            ToolCircle("Download", systemImage: "arrow.down.to.line",
                       accessibilityText: canDownload ? "Download" : "Download (for members)") {
                if canDownload { downloadManager.startDownload(talk: talk) } else { onJoinClick() }
            }
        }
    }

    private func trackList(_ talk: Talk, resume: TalkProgress.Resume?) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(talk.tracks.enumerated()), id: \.offset) { index, track in
                if index > 0 { FbaColors.divider.frame(height: 1) }
                TrackRow(index: index, track: track, state: TalkProgress.trackState(index, resume), resume: resume,
                         isCurrent: isActive && player.currentTrackIndex == index) {
                    if isActive {
                        player.playTrackByIndex(index)
                    } else {
                        // Start THIS talk at the tapped track (calling playTrackByIndex here
                        // would race the async load and play the wrong talk).
                        player.playTalk(talk, fromTrackIndex: index)
                    }
                }
            }
        }
    }

    private func report(_ reason: String, talk: Talk) {
        let position: Int? = isActive ? Int(PlaybackMath.cumulativePositionMs(
            tracks: talk.tracks, trackIndex: player.currentTrackIndex,
            positionInTrackMs: Int64(player.currentPosition * 1000)) / 1000) : nil
        let device = "\(UIDevice.current.model), iOS \(UIDevice.current.systemVersion)"
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let email = ReportProblem.email(reason: reason, talkTitle: talk.title, catNum: talk.catNum,
                                        positionSeconds: position, device: device, appVersion: version,
                                        signedIn: AuthRepository.shared.isLoggedIn)
        if let url = ReportProblem.mailtoURL(email), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        } else {
            // No mail app set up: copy the address so it can be pasted elsewhere.
            UIPasteboard.general.string = email.to
        }
    }

    static func talkURL(_ catNum: String) -> URL {
        var c = URLComponents(string: "https://www.freebuddhistaudio.com/audio/details")!
        c.queryItems = [URLQueryItem(name: "num", value: catNum)]
        return c.url!
    }
}

// MARK: - Pieces

/// Title, underlined speaker, "90 min · 2026", the "digitally remastered" link, place and series.
private struct TitleBlock: View {
    let talk: Talk
    let onSpeakerClick: (String) -> Void
    let onYearClick: (Int) -> Void
    let onPlaceClick: (String, String) -> Void
    let onSeriesClick: (String) -> Void
    let onDigitalLegacyClick: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(talk.title)
                .font(.system(.title2, weight: .bold))          // ~24 in the frame; a text style so it scales
                .foregroundStyle(FbaColors.black)
                .fixedSize(horizontal: false, vertical: true)
            if !talk.speaker.isEmpty {
                Button { onSpeakerClick(talk.speaker) } label: {
                    Text(talk.speaker).font(.headline.weight(.medium)).underline()
                        .foregroundStyle(FbaColors.nearBlack)
                }
                .buttonStyle(.plain)
                .accessibilityHint("All talks by \(talk.speaker)")
            }
            lengthAndYear
            if talk.hasRemaster {
                Button(action: onDigitalLegacyClick) {
                    HStack(spacing: 6) {
                        BundleImage(name: "dharmachakra_gold").frame(width: 18, height: 18)
                        Text("digitally remastered").font(.subheadline.weight(.medium)).underline()
                            .foregroundStyle(FbaColors.nearBlack)
                    }
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }
            if !talk.placeName.isEmpty || !talk.series.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    if !talk.placeName.isEmpty {
                        Button { onPlaceClick(talk.placeSlug.isEmpty ? talk.placeName : talk.placeSlug, talk.placeName) } label: {
                            Text(talk.placeName).font(.subheadline).underline().foregroundStyle(FbaColors.meta)
                                .multilineTextAlignment(.leading)
                        }
                        .buttonStyle(.plain)
                    }
                    if !talk.series.isEmpty {
                        Button { onSeriesClick(talk.seriesHref.isEmpty ? talk.series : talk.seriesHref) } label: {
                            (Text("Part of the series ") + Text(talk.series).underline())
                                .font(.subheadline).foregroundStyle(FbaColors.meta)
                                .multilineTextAlignment(.leading)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.top, 2)
            }
        }
    }

    @ViewBuilder
    private var lengthAndYear: some View {
        let total = PlaybackMath.totalDurationSeconds(talkDurationSeconds: talk.durationSeconds,
                                                      tracks: talk.tracks, playerDurationMs: 0)
        let length = total > 0 ? TalkProgress.lengthLabel(total) : nil
        if length != nil || talk.year > 0 {
            HStack(spacing: 0) {
                if let length { Text(length) }
                if length != nil && talk.year > 0 { Text(" · ") }
                if talk.year > 0 {
                    // String(year), not "\(year)" in a LocalizedStringKey, which would print "2,026".
                    Button { onYearClick(talk.year) } label: { Text(String(talk.year)).underline() }
                        .buttonStyle(.plain)
                        .accessibilityHint("Talks from \(String(talk.year))")
                }
            }
            .font(.subheadline)
            .foregroundStyle(FbaColors.meta)
        }
    }
}

/// Original | switch | Enhanced (remastered talks only); the chosen side in gold-deep.
private struct VersionSwitch: View {
    @Binding var enhanced: Bool
    let enabled: Bool
    let onChange: (Bool) -> Void
    @Environment(\.pageColors) private var page

    var body: some View {
        HStack(spacing: 14) {
            Text("Original").font(.headline.weight(.medium))
                .foregroundStyle(enhanced ? FbaColors.grey500 : page.deep)
            Toggle("Enhanced version", isOn: Binding(get: { enhanced }, set: { enhanced = $0; onChange($0) }))
                .labelsHidden()
                .tint(FbaColors.gold.base)
                .disabled(!enabled)
            Text("Enhanced").font(.headline.weight(.medium))
                .foregroundStyle(enhanced ? page.deep : FbaColors.grey500)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
    }
}

private struct DonateTool: View {
    let action: () -> Void
    @Environment(\.pageColors) private var page
    var body: some View {
        ToolCircle("Donate", systemImage: "heart.fill", tint: page.deep, action: action)
    }
}

private struct DownloadedTool: View {
    let action: () -> Void
    @Environment(\.pageColors) private var page
    var body: some View {
        ToolCircle("Downloaded", systemImage: "checkmark.circle.fill", tint: page.deep,
                   accessibilityText: "Downloaded. Remove download", action: action)
    }
}

private struct ProgressRing: View {
    let fraction: Double
    @Environment(\.pageColors) private var page
    var body: some View {
        ZStack {
            Circle().stroke(FbaColors.divider, lineWidth: 3)
            Circle().trim(from: 0, to: fraction).stroke(page.deep, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: 26, height: 26)
    }
}

/// Track row: number (or a tick when listened), title, length (or "Listened" / time left).
/// The track in progress has its title in the page colour with a thin progress bar.
private struct TrackRow: View {
    let index: Int
    let track: Track
    let state: TalkProgress.TrackState
    let resume: TalkProgress.Resume?
    let isCurrent: Bool
    let onTap: () -> Void
    @Environment(\.pageColors) private var page

    var body: some View {
        let highlighted = isCurrent || state == .inProgress
        Button(action: onTap) {
            HStack(spacing: 12) {
                if state == .listened {
                    Image(systemName: "checkmark")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(FbaColors.white)
                        .frame(width: 18, height: 18)
                        .background(page.deep, in: Circle())
                        .accessibilityLabel("Listened")
                } else {
                    Text("\(index + 1)").font(.subheadline.weight(.semibold)).foregroundStyle(FbaColors.grey500)
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text(track.title.isEmpty ? "Track \(index + 1)" : track.title)
                        .font(.body.weight(highlighted ? .semibold : .regular))
                        .foregroundStyle(highlighted ? page.deep : FbaColors.black)
                        .multilineTextAlignment(.leading)
                    if state == .inProgress, let resume {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(FbaColors.divider)
                                Capsule().fill(page.deep)
                                    .frame(width: geo.size.width * TalkProgress.trackFraction(track, positionMs: resume.positionMs))
                            }
                        }
                        .frame(height: 4)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text(trailing).font(.subheadline).foregroundStyle(FbaColors.meta)
            }
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var trailing: String {
        switch state {
        case .listened: return "Listened"
        case .inProgress:
            if let resume, let left = PlaybackMath.timeLeftLabel(TalkProgress.trackRemainingSeconds(track, positionMs: resume.positionMs)) {
                return left
            }
            return track.durationSeconds > 0 ? formatDuration(track.durationSeconds) : ""
        case .notStarted:
            return track.durationSeconds > 0 ? formatDuration(track.durationSeconds) : ""
        }
    }
}

/// Related talks: 84 x 56 rounded image, title, "Speaker · year".
private struct RelatedTalksList: View {
    let related: [SearchResult]?
    let onTalkClick: (String) -> Void

    var body: some View {
        if let related {
            if related.isEmpty {
                Text("No related talks yet.").font(.subheadline).foregroundStyle(FbaColors.meta)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(related.enumerated()), id: \.offset) { index, item in
                        if index > 0 { FbaColors.divider.frame(height: 1) }
                        Button { onTalkClick(item.catNum) } label: {
                            HStack(spacing: 12) {
                                Artwork(url: item.imageUrl, aspect: 84.0 / 56.0)
                                    .frame(width: 84, height: 56)
                                    .clipShape(RoundedRectangle(cornerRadius: FbaDimens.radiusThumb))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.title).font(.subheadline.weight(.medium)).foregroundStyle(FbaColors.black)
                                        .lineLimit(2).multilineTextAlignment(.leading)
                                    let meta = [item.speaker.isEmpty ? nil : item.speaker,
                                                item.year > 0 ? String(item.year) : nil].compactMap { $0 }
                                    if !meta.isEmpty {
                                        Text(meta.joined(separator: " · ")).font(.footnote).foregroundStyle(FbaColors.meta)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        } else {
            ProgressView().frame(maxWidth: .infinity).padding(24)
        }
    }
}

/// Description: up to four lines with "Read more" when it runs longer; "Show less" collapses it.
private struct ExpandableText: View {
    let text: String
    @State private var expanded = false
    @State private var fullHeight: CGFloat = 0
    @State private var clampedHeight: CGFloat = 0
    @Environment(\.pageColors) private var page

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(text)
                .font(.body).lineSpacing(4).foregroundStyle(FbaColors.black)
                .lineLimit(expanded ? nil : 4)
                .background(GeometryReader { g in Color.clear.onAppear { clampedHeight = g.size.height } })
                .background(
                    // Unclamped copy, measured off-screen, to know whether "Read more" is needed.
                    Text(text).font(.body).lineSpacing(4).fixedSize(horizontal: false, vertical: true)
                        .background(GeometryReader { g in Color.clear.onAppear { fullHeight = g.size.height } })
                        .hidden()
                )
            if expanded || fullHeight > clampedHeight + 1 {
                Button(expanded ? "Show less" : "Read more") { withAnimation { expanded.toggle() } }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(page.deep)
            }
        }
    }
}
