import SwiftUI

/// Result categories. Chips are shown for All plus every category that has results.
enum SearchFilter: String, CaseIterable {
    case all = "All", talks = "Talks", series = "Series", speakers = "Speakers", places = "Places", collections = "Collections"
}

struct SearchResults {
    var talks: [SearchResult] = []
    var series: [SearchResult] = []
    var speakers: [SearchResult] = []
    var places: [SearchResult] = []
    var collections: [SearchResult] = []

    var isEmpty: Bool { talks.isEmpty && series.isEmpty && speakers.isEmpty && places.isEmpty && collections.isEmpty }

    func of(_ filter: SearchFilter) -> [SearchResult] {
        switch filter {
        case .all: return []
        case .talks: return talks
        case .series: return series
        case .speakers: return speakers
        case .places: return places
        case .collections: return collections
        }
    }

    /// Categories with results, in display order.
    var available: [SearchFilter] {
        [.speakers, .places, .collections, .series, .talks].filter { !of($0).isEmpty }
    }
}

/// The design's search categories (Audio = talks and series).
enum SearchCategory: String, CaseIterable { case audio = "Audio", speakers = "Speakers", transcripts = "Transcripts", podcasts = "Podcasts" }

/// Search (v1.0 design): field, category chips (Audio | Speakers | Transcripts | Podcasts), the
/// language row with All languages pinned, then results grouped (Talks, Series, Speakers, ...)
/// with "See all n". Talk rows have the grey play circle. Transcript and podcast search aren't in
/// the app's API yet, so those chips offer the same search on freebuddhistaudio.com.
/// Mirrors Android `SearchScreen`.
struct SearchScreen: View {
    @Environment(\.pageColors) private var page
    @ObservedObject private var player = AudioPlayer.shared
    @State private var query = ""
    @State private var results = SearchResults()
    @State private var category: SearchCategory = .audio
    @State private var language: String?
    @State private var languages: [V2Catalogue.LanguageEntry] = []
    @State private var expanded: SearchFilter?
    @State private var isLoading = false
    @State private var error: String?
    @State private var hasSearched = false
    @State private var debounceTask: Task<Void, Never>?

    let onTalkClick: (String) -> Void
    /// Series, speaker, place and collection results, routed by the caller.
    var onItemClick: (SearchResult) -> Void = { _ in }

    private static let overviewRows = 3

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                Text("Search").font(.largeTitle.weight(.bold)).foregroundStyle(FbaColors.black)
                    .padding(.horizontal, FbaDimens.margin).padding(.top, 8).padding(.bottom, 14)
                searchField
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(SearchCategory.allCases, id: \.self) { c in
                            chip(c.rawValue, selected: category == c) { category = c; expanded = nil }
                        }
                    }
                    .padding(.horizontal, FbaDimens.margin).padding(.vertical, 12)
                }
                if category == .audio && !languages.isEmpty { languageRow }
                content
            }
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .miniPlayerClearance()
        .navigationBarHidden(true)
        .task {
            if languages.isEmpty {
                let order = ["en", "hi", "de", "es", "fr", "nl", "no", "pl", "pt", "ru", "fi", "sv"]
                languages = await ContentRepository.shared.getLanguages().sorted {
                    (order.firstIndex(of: $0.code) ?? 99) < (order.firstIndex(of: $1.code) ?? 99)
                }
            }
        }
    }

    // MARK: - Pieces

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(FbaColors.grey700)
            TextField("Search talks, series, speakers", text: $query)
                .submitLabel(.search)
                .onSubmit { search() }
                .autocorrectionDisabled()
                // Live search after 3+ chars, debounced (parity with Android).
                .onChange(of: query) { newValue in
                    debounceTask?.cancel()
                    let trimmed = newValue.trimmingCharacters(in: .whitespaces)
                    guard trimmed.count >= 3, !trimmed.contains("num=") else { return }
                    debounceTask = Task {
                        try? await Task.sleep(nanoseconds: 500_000_000)
                        guard !Task.isCancelled else { return }
                        search()
                    }
                }
            if !query.isEmpty {
                Button { query = ""; results = SearchResults(); hasSearched = false } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(FbaColors.grey500)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .background(FbaColors.circle, in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, FbaDimens.margin)
    }

    /// All languages stays pinned on the left; the rest scroll sideways.
    private var languageRow: some View {
        HStack(spacing: 8) {
            chip("All languages", selected: language == nil) { setLanguage(nil) }
            FbaColors.divider.frame(width: 1, height: 20)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(languages, id: \.code) { l in chip(l.nativeName, selected: language == l.code) { setLanguage(l.code) } }
                }
                .padding(.trailing, FbaDimens.margin)
            }
        }
        .padding(.leading, FbaDimens.margin)
        .padding(.bottom, 8)
    }

    private func chip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.subheadline.weight(.medium))
                .padding(.horizontal, 14).padding(.vertical, 7)
                .foregroundStyle(selected ? page.deep : FbaColors.black)
                .background(selected ? page.tint : FbaColors.circle, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    @ViewBuilder
    private var content: some View {
        switch category {
        case .transcripts, .podcasts:
            webSearchCard(transcripts: category == .transcripts)
        default:
            if isLoading && results.isEmpty {
                ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
            } else if let error, results.isEmpty {
                VStack(spacing: 8) {
                    Text(error).foregroundStyle(.secondary)
                    Button("Retry") { search() }
                }.frame(maxWidth: .infinity).padding(.top, 40)
            } else if hasSearched && !isLoading && results.isEmpty {
                Text(query.isEmpty ? "No talks in this language yet" : "No results for \"\(query)\"")
                    .foregroundStyle(FbaColors.meta).frame(maxWidth: .infinity).padding(.top, 40)
            } else {
                let sections: [SearchFilter] = (expanded.map { [$0] }
                    ?? (category == .speakers ? [.speakers] : [.talks, .series, .speakers, .places, .collections]))
                    .filter { !results.of($0).isEmpty }
                let overview = expanded == nil && sections.count > 1
                if query.isEmpty, let code = language, !sections.isEmpty {
                    Text("Talks in \(languages.first { $0.code == code }?.nativeName ?? "")")
                        .font(.title3.weight(.bold)).padding(.horizontal, FbaDimens.margin).padding(.vertical, 8)
                }
                ForEach(sections, id: \.self) { section in
                    let all = results.of(section)
                    if overview || expanded != nil {
                        HStack {
                            Text(section.rawValue).font(.title3.weight(.bold)).foregroundStyle(FbaColors.black)
                            Spacer()
                            if expanded != nil {
                                Button("All results") { expanded = nil }.foregroundStyle(page.deep)
                            } else if all.count > Self.overviewRows {
                                Button("See all \(all.count)") { expanded = section }.foregroundStyle(page.deep)
                            }
                        }
                        .font(.subheadline.weight(.medium))
                        .padding(.horizontal, FbaDimens.margin).padding(.top, 14).padding(.bottom, 4)
                    }
                    ForEach(overview ? Array(all.prefix(Self.overviewRows)) : all) { r in
                        VStack(spacing: 0) {
                            CompactTalkRow(title: capitalizedFirst(r.title), meta: r.metaLine, imageUrl: r.imageUrl,
                                           onClick: { open(r) },
                                           onPlay: r.isTalk ? { play(r) } : nil,
                                           isCurrent: r.isTalk && player.currentTalk?.catNum == r.catNum,
                                           isPlaying: player.isPlaying)
                            FbaColors.divider.frame(height: 1)
                        }
                        .padding(.horizontal, FbaDimens.margin)
                    }
                }
            }
        }
    }

    private func webSearchCard(transcripts: Bool) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(transcripts
                 ? "Searching inside transcripts is coming to the app. For now, you can search them on the website."
                 : "Podcast search is coming to the app. For now, you can search the podcasts on the website.")
                .font(.subheadline).foregroundStyle(FbaColors.black)
            if query.trimmingCharacters(in: .whitespaces).isEmpty {
                Text("Type what you're looking for above.").font(.footnote).foregroundStyle(FbaColors.meta)
            } else {
                FbaPrimaryButton(title: "Search on freebuddhistaudio.com") {
                    var c = URLComponents(string: "https://www.freebuddhistaudio.com/search")!
                    c.queryItems = [URLQueryItem(name: "s", value: "0"), URLQueryItem(name: "r", value: "10"),
                                    URLQueryItem(name: "b", value: "p"), URLQueryItem(name: "q", value: query.trimmingCharacters(in: .whitespaces)),
                                    URLQueryItem(name: "t", value: transcripts ? "text" : "podcast")]
                    if let url = c.url { UIApplication.shared.open(url) }
                }
            }
        }
        .padding(18)
        .background(page.tint, in: RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, FbaDimens.margin).padding(.vertical, 8)
    }

    // MARK: - Actions

    private func open(_ result: SearchResult) {
        if result.isTalk { onTalkClick(result.catNum) } else { onItemClick(result) }
    }

    private func play(_ r: SearchResult) {
        if player.currentTalk?.catNum == r.catNum { player.togglePlayPause(); return }
        Task { if let talk = await TalkRepository.shared.getTalkDetailForPlayback(r.catNum) { player.playTalk(talk) } }
    }

    /// Choose a talk language; with no search term it lists that language's talks.
    private func setLanguage(_ code: String?) {
        language = code
        expanded = nil
        if query.trimmingCharacters(in: .whitespaces).count >= 3 { search(); return }
        guard let code else { results = SearchResults(); hasSearched = false; return }
        isLoading = true
        Task {
            if let p = try? await ContentRepository.shared.getPage(.browse("/browse?lang=\(code)"), page: 1) {
                results = SearchResults(talks: p.items)
            }
            hasSearched = true
            isLoading = false
        }
    }

    /// Detect a pasted FBA URL ("…details?num=X"). Returns (catNum, isSeries).
    private func extractCatNumFromUrl(_ text: String) -> (String, Bool)? {
        guard text.contains("num=") else { return nil }
        let after = text.components(separatedBy: "num=").last ?? ""
        let catNum = after.components(separatedBy: "&").first?
            .components(separatedBy: " ").first?
            .trimmingCharacters(in: .whitespaces) ?? ""
        guard !catNum.isEmpty else { return nil }
        return (catNum, text.contains("/series/"))
    }

    private func search() {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }

        // URL paste: navigate straight to the talk or series
        if let (catNum, isSeries) = extractCatNumFromUrl(trimmed) {
            if isSeries {
                onItemClick(SearchResult(catNum: catNum, path: "https://www.freebuddhistaudio.com/series/details?num=\(catNum)"))
            } else {
                onTalkClick(catNum)
            }
            return
        }

        isLoading = true
        error = nil
        let lang = language
        Task {
            do {
                var found = SearchResults()
                if trimmed.lowercased().hasPrefix("sangharakshita") {
                    // "sangharakshita <words>" answers from the bundled catalogue (works offline)
                    let words = trimmed.split(separator: " ").dropFirst().map(String.init)
                    let all = SharedDataLoader.sangharakshitaTalks
                    found.talks = words.isEmpty ? all : all.filter { r in
                        words.allSatisfy { r.title.localizedCaseInsensitiveContains($0) }
                    }
                } else {
                    // Sequential, not parallel: while logged in the site rotates the session
                    // cookie per response, so concurrent calls would invalidate each other.
                    let audio = try await TalkRepository.shared.searchAudio(trimmed, language: lang)
                    let seriesResults = (try? await TalkRepository.shared.searchSeries(trimmed)) ?? []
                    var seen = Set<String>()
                    let unique = (seriesResults + audio).filter { seen.insert("\($0.isSeries ? "s" : "a"):\($0.catNum)").inserted }
                    // A language chosen in the row overrides the English-only setting.
                    let merged = lang != nil ? unique : await ContentRepository.shared.filterForLanguage(unique)
                    found.talks = merged.filter { !$0.isSeries }
                    found.series = merged.filter(\.isSeries)
                }
                // Speakers / places / collections come from FBA's indexes and curated menu
                // (the site's search only returns talks and series).
                let names = await ContentRepository.shared.matchNames(trimmed)
                found.speakers = names.speakers
                found.places = names.places
                found.collections = names.collections
                results = found
                hasSearched = true
            } catch {
                self.error = friendlyError(error)
            }
            isLoading = false
        }
    }
}
