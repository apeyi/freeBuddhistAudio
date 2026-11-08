import SwiftUI
import UIKit

/// A list of talks, series or index entries (v1.0 design): a grey label (PLACE, YEAR, SPEAKER,
/// COLLECTION), the title and talk count, then compact rows whose grey play circle plays the
/// talk in the Now Playing bar. Series get their own page, built like a talk page.
/// Mirrors Android `ListScreen`.
struct ListScreen: View {
    let source: ContentSource
    let initialTitle: String
    let onItemClick: (SearchResult) -> Void
    let onDonateClick: () -> Void
    var onSpeakerClick: (String) -> Void = { _ in }
    var onYearClick: (Int) -> Void = { _ in }

    @ObservedObject private var player = AudioPlayer.shared
    @State private var page: ListPage?
    @State private var items: [SearchResult] = []
    @State private var isLoading = true
    @State private var isLoadingMore = false
    @State private var hasMore = false
    @State private var error: String?
    @State private var saved: [String: SeriesProgress.Saved] = [:]
    @State private var showMore = false

    private var title: String { capitalizedFirst((page?.title.isEmpty == false ? page?.title : nil) ?? initialTitle) }

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
            } else if let error {
                VStack(spacing: 8) {
                    Text(error).foregroundStyle(.secondary)
                    Button("Retry") { Task { await load() } }
                }
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        if source.isSeries { seriesHeader } else { listHeader }
                        if items.isEmpty {
                            Text("No talks found").foregroundStyle(FbaColors.meta).padding(.vertical, 40)
                        }
                        ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                            if index > 0 { FbaColors.divider.frame(height: 1) }
                            row(index, item)
                                .onAppear { if index >= items.count - 6 { Task { await loadMore() } } }
                        }
                        if isLoadingMore { ProgressView().frame(maxWidth: .infinity).padding() }
                        SupportFbaButton(action: onDonateClick).padding(.top, 20)
                    }
                    .padding(.horizontal, FbaDimens.margin)
                    .padding(.bottom, 24)
                }
                .fbaPageTheme(source.isSeries && page?.hasRemaster == true ? .remastered : .hourly)
            }
        }
        .miniPlayerClearance()
        .navigationTitle(source.isSeries ? title : "")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .onAppear(perform: loadSaved)
        .onChange(of: player.isPlaying) { _ in loadSaved() }
        .confirmationDialog(title, isPresented: $showMore) {
            Button("Copy link") { UIPasteboard.general.string = seriesURL.absoluteString }
            if let speaker = page?.speaker, !speaker.isEmpty { Button("Go to speaker") { onSpeakerClick(speaker) } }
            Button("Cancel", role: .cancel) {}
        }
    }

    // MARK: - Rows

    @ViewBuilder
    private func row(_ index: Int, _ item: SearchResult) -> some View {
        let isTalk = !item.isSeries && !item.isCollection && !item.isBrowseLink
        let current = isTalk && player.currentTalk?.catNum == item.catNum
        if source.isSeries {
            let s = saved[item.catNum]
            let state = SeriesProgress.state(s)
            let oneSpeaker = Set(items.map(\.speaker)).count <= 1
            let parts: [String] = {
                var p = ["Talk \(index + 1)"]
                if !oneSpeaker && !item.speaker.isEmpty { p.append(item.speaker) }
                switch state {
                case .listened: p.append("Listened")
                case .inProgress:
                    if let s, let left = PlaybackMath.timeLeftLabel(max(s.totalSeconds - Int(s.positionMs / 1000), 0)) { p.append(left) }
                case .notStarted: break
                }
                return p
            }()
            CompactTalkRow(title: item.title, meta: parts.joined(separator: " · "), imageUrl: item.imageUrl,
                           onClick: { onItemClick(item) }, onPlay: { play(item) },
                           isCurrent: current, isPlaying: player.isPlaying, listened: state == .listened,
                           thumbSize: CGSize(width: 84, height: 56))
        } else {
            CompactTalkRow(title: capitalizedFirst(item.title), meta: item.metaLine, imageUrl: item.imageUrl,
                           onClick: { onItemClick(item) }, onPlay: isTalk ? { play(item) } : nil,
                           isCurrent: current, isPlaying: player.isPlaying)
        }
    }

    /// The circle plays in the Now Playing bar; on the playing row it pauses / resumes.
    private func play(_ item: SearchResult) {
        if player.currentTalk?.catNum == item.catNum { player.togglePlayPause(); return }
        Task {
            if let talk = await TalkRepository.shared.getTalkDetailForPlayback(item.catNum) { player.playTalk(talk) }
        }
    }

    // MARK: - Headers

    private var label: String {
        switch source {
        case .series: return "SERIES"
        case .namedCollection: return "COLLECTION"
        case .browse(let path):
            if path.contains("p=") { return "PLACE" }
            if path.contains("y=") { return "YEAR" }
            if path.contains("s=") { return "SPEAKER" }
            if path.contains("th=") || path.contains("genre=") { return "THEME" }
            if path.contains("lang=") { return "LANGUAGE" }
            return ""
        case .apiCollection: return ""
        }
    }

    private var listHeader: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !label.isEmpty {
                Text(label).font(.footnote.weight(.semibold)).tracking(0.8).foregroundStyle(FbaColors.meta)
            }
            Text(title).font(.title.weight(.bold)).foregroundStyle(FbaColors.black)
            if let d = page?.description, !d.isEmpty {
                Text(d).font(.subheadline).foregroundStyle(FbaColors.black).lineLimit(4).padding(.top, 2)
            }
            if let total = page?.totalItems, total > 0 {
                Text("\(total) \(total == 1 ? "talk" : "talks")").font(.subheadline).foregroundStyle(FbaColors.meta)
            }
        }
        .padding(.top, 4)
        .padding(.bottom, 10)
    }

    private var seriesHeader: some View {
        let resume = SeriesProgress.resume(members: items.map(\.catNum), saved: saved)
        return VStack(alignment: .leading, spacing: 18) {
            Artwork(url: (page?.bannerUrl.isEmpty == false ? page?.bannerUrl : nil) ?? page?.imageUrl, aspect: 362.0 / 240.0)
                .frame(maxWidth: .infinity).frame(height: 240)
                .clipShape(RoundedRectangle(cornerRadius: FbaDimens.radiusArtwork))
            VStack(alignment: .leading, spacing: 6) {
                Text("SERIES").font(.footnote.weight(.semibold)).tracking(0.8).foregroundStyle(FbaColors.meta)
                Text(title).font(.title2.weight(.bold)).foregroundStyle(FbaColors.black)
                if let speaker = page?.speaker, !speaker.isEmpty {
                    Button { onSpeakerClick(speaker) } label: {
                        Text(speaker).font(.headline.weight(.medium)).underline().foregroundStyle(FbaColors.nearBlack)
                    }
                    .buttonStyle(.plain)
                }
                HStack(spacing: 0) {
                    Text("\(items.count) talks")
                    if let year = page?.year, year > 0 {
                        Text(" · ")
                        Button { onYearClick(year) } label: { Text(String(year)).underline() }.buttonStyle(.plain)
                    }
                }
                .font(.subheadline).foregroundStyle(FbaColors.meta)
            }
            VStack(spacing: 10) {
                if let resume, items.indices.contains(resume.index) {
                    FbaPrimaryButton(title: "Resume Talk \(resume.index + 1)", systemImage: "play.fill") { play(items[resume.index]) }
                    VStack(spacing: 2) {
                        Text([items[resume.index].title, resume.remainingSeconds.flatMap(PlaybackMath.timeLeftLabel)]
                                .compactMap { $0 }.joined(separator: " · "))
                            .font(.subheadline).foregroundStyle(FbaColors.meta).lineLimit(1)
                        Button("Play from the start") { if let first = items.first { play(first) } }
                            .font(.subheadline.weight(.medium)).foregroundStyle(FbaColors.nearBlack).underline()
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    FbaPrimaryButton(title: "Play all", systemImage: "play.fill") { if let first = items.first { play(first) } }
                }
                HStack(alignment: .top) {
                    ToolCircle("Download", systemImage: "arrow.down.to.line", accessibilityText: "Download all talks") { downloadAll() }
                    Spacer(minLength: 0)
                    SeriesDonateTool(action: onDonateClick)
                    Spacer(minLength: 0)
                    ShareLink(item: seriesURL, subject: Text(title)) {
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
            if let d = page?.description, !d.isEmpty {
                Text(d).font(.body).lineSpacing(4).foregroundStyle(FbaColors.black).lineLimit(6)
            }
            Text("Talks (\(items.count))").font(.title3.weight(.bold)).foregroundStyle(FbaColors.black)
        }
        .padding(.bottom, 8)
    }

    private var seriesURL: URL {
        var c = URLComponents(string: "https://www.freebuddhistaudio.com/series/details")!
        c.queryItems = [URLQueryItem(name: "num", value: page?.catNum ?? "")]
        return c.url!
    }

    private func downloadAll() {
        let talks = items.filter { !$0.isSeries && !$0.isCollection && !$0.isBrowseLink }
        Task {
            for item in talks where DownloadManager.shared.downloads[item.catNum] == nil {
                if let talk = await TalkRepository.shared.getTalkDetail(item.catNum) {
                    DownloadManager.shared.startDownload(talk: talk)
                }
            }
        }
    }

    // MARK: - Data

    private func loadSaved() {
        var map: [String: SeriesProgress.Saved] = [:]
        for r in PersistenceManager.shared.getRecentlyListened() {
            map[r.catNum] = .init(positionMs: r.positionMs, totalSeconds: r.totalDurationSeconds, listenedAt: r.timestamp)
        }
        saved = map
    }

    private func load() async {
        isLoading = true
        error = nil
        do {
            let p = try await ContentRepository.shared.getPage(source, page: 1)
            page = p
            items = p.items
            hasMore = p.hasMore
        } catch {
            self.error = friendlyError(error)
        }
        isLoading = false
    }

    private func loadMore() async {
        guard hasMore, !isLoadingMore, let prev = page else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        guard var next = try? await ContentRepository.shared.getPage(source, page: prev.page + 1, previous: prev) else { return }
        if next.apiUrl.isEmpty { next.apiUrl = prev.apiUrl }
        if next.apiQuery.isEmpty { next.apiQuery = prev.apiQuery }
        // Keep the header fields from the first page.
        next.title = prev.title; next.description = prev.description; next.totalItems = prev.totalItems
        page = next
        var seen = Set(items.map(\.id))
        items += next.items.filter { seen.insert($0.id).inserted }
        hasMore = next.hasMore && !next.items.isEmpty
    }
}

private struct SeriesDonateTool: View {
    let action: () -> Void
    @Environment(\.pageColors) private var page
    var body: some View { ToolCircle("Donate", systemImage: "heart.fill", tint: page.deep, action: action) }
}
