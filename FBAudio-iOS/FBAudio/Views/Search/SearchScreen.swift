import SwiftUI

enum SearchMode {
    case all, bySpeaker
}

struct SearchScreen: View {
    @State private var query = ""
    @State private var keywordFilter = ""
    @State private var results: [SearchResult] = []
    @State private var isLoading = false
    @State private var error: String?
    @State private var hasSearched = false
    @State private var searchMode: SearchMode = .all
    @State private var debounceTask: Task<Void, Never>?

    let onTalkClick: (String) -> Void
    var onSeriesClick: (String) -> Void = { _ in }

    private var filteredResults: [SearchResult] {
        if searchMode == .bySpeaker && !keywordFilter.isEmpty {
            return results.filter { $0.title.localizedCaseInsensitiveContains(keywordFilter) }
        }
        return results
    }

    var body: some View {
        List {
            Section {
                TextField("Search or paste URL", text: $query)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit { search() }
                    .autocorrectionDisabled()
                    // Live search after 3+ chars, debounced (parity with Android).
                    // URL pastes still need an explicit submit — auto-navigating
                    // away mid-paste would be jarring.
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

            // Mode toggle
            Section {
                HStack(spacing: 8) {
                    modeChip("All", selected: searchMode == .all) { setMode(.all) }
                    modeChip("By speaker", selected: searchMode == .bySpeaker) { setMode(.bySpeaker) }
                }
            }

            // Keyword filter (speaker mode only)
            if searchMode == .bySpeaker && hasSearched && !results.isEmpty {
                Section {
                    TextField("Filter by keyword", text: $keywordFilter)
                        .textFieldStyle(.roundedBorder)
                }
                if !keywordFilter.isEmpty || !results.isEmpty {
                    let countText = keywordFilter.isEmpty
                        ? "\(results.count) talks"
                        : "\(filteredResults.count) of \(results.count) talks"
                    Text(countText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if isLoading {
                ProgressView().frame(maxWidth: .infinity)
            } else if let error {
                VStack(spacing: 8) {
                    Text(error).foregroundStyle(.secondary)
                    Button("Retry") { search() }
                }
            } else if hasSearched && filteredResults.isEmpty {
                Text(keywordFilter.isEmpty
                     ? "No results found for \"\(query)\""
                     : "No talks matching \"\(keywordFilter)\"")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(filteredResults) { result in
                    let isSeries = result.path.contains("/series/")
                    TalkCard(
                        title: result.title,
                        speaker: isSeries ? "Series · \(result.speaker)" : result.speaker,
                        imageUrl: result.imageUrl,
                        subtitle: result.year > 0 ? "\(result.year)" : nil,
                        onClick: {
                            if isSeries { onSeriesClick(result.path) }
                            else { onTalkClick(result.catNum) }
                        }
                    )
                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                    .listRowSeparator(.hidden)
                }
            }
        }
        .listStyle(.plain)
        .miniPlayerClearance()
        .navigationTitle("Search")
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

    private func setMode(_ mode: SearchMode) {
        guard mode != searchMode else { return }
        searchMode = mode
        // Old-mode results don't fit the new mode's UI (counts, keyword filter) —
        // clear rather than presenting them mislabelled.
        results = []
        hasSearched = false
        keywordFilter = ""
        error = nil
        search()
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
                onSeriesClick("https://www.freebuddhistaudio.com/series/details?num=\(catNum)")
            } else {
                onTalkClick(catNum)
            }
            return
        }

        isLoading = true
        error = nil
        keywordFilter = ""
        Task {
            do {
                if searchMode == .bySpeaker {
                    if trimmed.lowercased() == "sangharakshita" {
                        // Offline-capable: the bundled catalogue
                        results = SharedDataLoader.sangharakshitaTalks
                    } else {
                        let page = try await TalkRepository.shared.browseBySpeaker(trimmed)
                        results = page.items
                    }
                } else if trimmed.lowercased().hasPrefix("sangharakshita") {
                    // "sangharakshita <words>" answers from the bundled catalogue (works offline)
                    let words = trimmed.split(separator: " ").dropFirst().map(String.init)
                    let all = SharedDataLoader.sangharakshitaTalks
                    results = words.isEmpty ? all : all.filter { r in
                        words.allSatisfy { r.title.localizedCaseInsensitiveContains($0) }
                    }
                } else {
                    // Series first, then audio, deduped (parity with Android).
                    // Type-prefixed key: series and talk numbers are separate
                    // namespaces, a bare-catNum dedup could hide a talk.
                    async let seriesTask = (try? TalkRepository.shared.searchSeries(trimmed)) ?? []
                    async let audioTask = TalkRepository.shared.searchAudio(trimmed)
                    let series = await seriesTask
                    let audio = try await audioTask
                    var seen = Set<String>()
                    results = (series + audio).filter {
                        let type = $0.path.contains("/series/") ? "s" : "a"
                        return seen.insert("\(type):\($0.catNum)").inserted
                    }
                }
                hasSearched = true
            } catch {
                self.error = friendlyError(error)
            }
            isLoading = false
        }
    }
}
