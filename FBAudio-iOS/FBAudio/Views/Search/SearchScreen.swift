import SwiftUI

/// All = talks and series; Audio = talks only. A Text tab follows when the server supports transcript search.
enum SearchMode {
    case all, audio
}

struct SearchScreen: View {
    @State private var query = ""
    @State private var results: [SearchResult] = []
    @State private var isLoading = false
    @State private var error: String?
    @State private var hasSearched = false
    @State private var searchMode: SearchMode = .all
    @State private var debounceTask: Task<Void, Never>?

    let onTalkClick: (String) -> Void
    var onSeriesClick: (String) -> Void = { _ in }

    private var series: [SearchResult] { searchMode == .all ? results.filter(\.isSeries) : [] }
    private var talks: [SearchResult] { results.filter { !$0.isSeries } }

    var body: some View {
        List {
            Section {
                TextField("Search talks and series", text: $query)
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

            // All | Audio
            Section {
                HStack(spacing: 8) {
                    modeChip("All", selected: searchMode == .all) { searchMode = .all }
                    modeChip("Audio", selected: searchMode == .audio) { searchMode = .audio }
                }
            }

            if isLoading {
                ProgressView().frame(maxWidth: .infinity)
            } else if let error {
                VStack(spacing: 8) {
                    Text(error).foregroundStyle(.secondary)
                    Button("Retry") { search() }
                }
            } else if hasSearched && talks.isEmpty && series.isEmpty {
                Text("No results found for \"\(query)\"").foregroundStyle(.secondary)
            } else {
                if !series.isEmpty {
                    sectionHeader("Series", series.count)
                    ForEach(series) { result in
                        ListItemCard(item: result, onClick: { onSeriesClick(result.path) })
                            .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                            .listRowSeparator(.hidden)
                    }
                }
                if !talks.isEmpty {
                    if !series.isEmpty { sectionHeader("Talks", talks.count) }
                    ForEach(talks) { result in
                        ListItemCard(item: result, onClick: { onTalkClick(result.catNum) })
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
                onSeriesClick("https://www.freebuddhistaudio.com/series/details?num=\(catNum)")
            } else {
                onTalkClick(catNum)
            }
            return
        }

        isLoading = true
        error = nil
        Task {
            do {
                if trimmed.lowercased().hasPrefix("sangharakshita") {
                    // "sangharakshita <words>" answers from the bundled catalogue (works offline)
                    let words = trimmed.split(separator: " ").dropFirst().map(String.init)
                    let all = SharedDataLoader.sangharakshitaTalks
                    results = words.isEmpty ? all : all.filter { r in
                        words.allSatisfy { r.title.localizedCaseInsensitiveContains($0) }
                    }
                } else {
                    // Series first, then talks, deduped (type-prefixed: separate namespaces).
                    async let seriesTask = (try? TalkRepository.shared.searchSeries(trimmed)) ?? []
                    async let audioTask = TalkRepository.shared.searchAudio(trimmed)
                    let seriesResults = await seriesTask
                    let audio = try await audioTask
                    var seen = Set<String>()
                    let merged = (seriesResults + audio).filter {
                        seen.insert("\($0.isSeries ? "s" : "a"):\($0.catNum)").inserted
                    }
                    results = await ContentRepository.shared.filterForLanguage(merged)
                }
                hasSearched = true
            } catch {
                self.error = friendlyError(error)
            }
            isLoading = false
        }
    }
}
