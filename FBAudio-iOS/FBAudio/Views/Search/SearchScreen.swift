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

struct SearchScreen: View {
    @State private var query = ""
    @State private var results = SearchResults()
    @State private var filter: SearchFilter = .all
    @State private var isLoading = false
    @State private var error: String?
    @State private var hasSearched = false
    @State private var debounceTask: Task<Void, Never>?

    let onTalkClick: (String) -> Void
    /// Series, speaker, place and collection results — routed by the caller.
    var onItemClick: (SearchResult) -> Void = { _ in }

    /// Chips to show: All + categories with results (none when there's only one category).
    private var chips: [SearchFilter] { results.available.count > 1 ? [.all] + results.available : [] }
    private var effectiveFilter: SearchFilter { filter == .all || results.available.contains(filter) ? filter : .all }

    var body: some View {
        List {
            Section {
                TextField("Search talks, series, speakers, places", text: $query)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit { search() }
                    .autocorrectionDisabled()
                    // Live search after 3+ chars, debounced (parity with Android).
                    // URL pastes still need an explicit submit.
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
            }

            // Category chips: All + every category that has results
            if !chips.isEmpty && !isLoading {
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(chips, id: \.self) { f in
                                modeChip(f.rawValue, selected: effectiveFilter == f) { filter = f }
                            }
                        }
                    }
                }
            }

            if isLoading {
                ProgressView().frame(maxWidth: .infinity)
            } else if let error {
                VStack(spacing: 8) {
                    Text(error).foregroundStyle(.secondary)
                    Button("Retry") { search() }
                }
            } else if hasSearched && results.isEmpty {
                Text("No results found for \"\(query)\"").foregroundStyle(.secondary)
            } else {
                let sections = effectiveFilter == .all ? results.available : [effectiveFilter]
                let showHeaders = sections.count > 1
                ForEach(sections, id: \.self) { section in
                    let items = results.of(section)
                    if showHeaders { sectionHeader(section.rawValue, items.count) }
                    ForEach(items) { result in
                        ListItemCard(item: result, onClick: { open(result) })
                            .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                            .listRowSeparator(.hidden)
                    }
                }
            }
        }
        .listStyle(.plain)
        .miniPlayerClearance()
        .navigationTitle("Search")
    }

    private func open(_ result: SearchResult) {
        if result.isTalk { onTalkClick(result.catNum) } else { onItemClick(result) }
    }

    private func sectionHeader(_ title: String, _ count: Int) -> some View {
        Text("\(title) (\(count))")
            .font(.subheadline).bold()
            .foregroundStyle(.secondary)
            .listRowSeparator(.hidden)
    }

    private func modeChip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.caption)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(selected ? Color.saffronOrange : Color(.systemGray5))
                .foregroundStyle(selected ? .white : .primary)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
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
                    let audio = try await TalkRepository.shared.searchAudio(trimmed)
                    let seriesResults = (try? await TalkRepository.shared.searchSeries(trimmed)) ?? []
                    var seen = Set<String>()
                    let merged = await ContentRepository.shared.filterForLanguage((seriesResults + audio).filter {
                        seen.insert("\($0.isSeries ? "s" : "a"):\($0.catNum)").inserted
                    })
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
