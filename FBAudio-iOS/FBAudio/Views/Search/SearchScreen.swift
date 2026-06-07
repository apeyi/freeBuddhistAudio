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

    let onTalkClick: (String) -> Void

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
            }

            // Mode toggle
            Section {
                HStack(spacing: 8) {
                    modeChip("All", selected: searchMode == .all) { searchMode = .all; search() }
                    modeChip("By speaker", selected: searchMode == .bySpeaker) { searchMode = .bySpeaker; search() }
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
                    TalkCard(
                        title: result.title,
                        speaker: result.speaker,
                        imageUrl: result.imageUrl,
                        subtitle: result.year > 0 ? "\(result.year)" : nil,
                        onClick: { onTalkClick(result.catNum) }
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

    private func search() {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        isLoading = true
        error = nil
        keywordFilter = ""
        Task {
            do {
                if searchMode == .bySpeaker {
                    let page = try await TalkRepository.shared.browseBySpeaker(query)
                    results = page.items
                } else {
                    results = try await TalkRepository.shared.searchAudio(query)
                }
                hasSearched = true
            } catch {
                self.error = friendlyError(error)
            }
            isLoading = false
        }
    }
}
