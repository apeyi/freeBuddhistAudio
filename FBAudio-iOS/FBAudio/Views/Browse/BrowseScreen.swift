import SwiftUI

struct BrowseScreen: View {
    let initialMode: BrowseMode?
    let onTalkClick: (String) -> Void

    enum BrowseMode {
        case sangharakshitaByYear
        case sangharakshitaSeries
        case speaker(String)
        case series(String)
    }

    @Environment(\.dismiss) private var dismiss
    @State private var categories: [BrowseCategory] = []
    @State private var selectedCategory: BrowseCategory?
    @State private var talks: [SearchResult] = []
    @State private var isLoadingCategories = false
    @State private var isLoadingTalks = false
    @State private var error: String?

    // Decade/year filtering
    @State private var selectedDecade: Int?
    @State private var selectedYear: Int?

    var body: some View {
        Group {
            if isLoadingCategories {
                ProgressView()
            } else if selectedCategory != nil {
                talksView
            } else {
                categoriesView
            }
        }
        .miniPlayerClearance()
        .navigationTitle(selectedCategory?.name ?? "Browse")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(canGoBackInternally)
        .toolbar {
            if canGoBackInternally {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: goBack) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Back")
                        }
                    }
                }
            }
        }
        .task { await loadInitial() }
    }

    private var canGoBackInternally: Bool {
        switch initialMode {
        case .sangharakshitaSeries:
            return selectedCategory?.id.hasPrefix("sang_series_") == true
        default:
            return false
        }
    }

    private func goBack() {
        // Sangharakshita series: if viewing a series' talks, go back to series list
        if let cat = selectedCategory, cat.id.hasPrefix("sang_series_") {
            categories = SharedDataLoader.sangharakshitaSeriesAsCategories()
            selectedCategory = nil
            talks = []
            return
        }

        // Nothing left to go back to internally — pop nav
        dismiss()
    }

    // MARK: - Categories View

    private var categoriesView: some View {
        List(categories) { category in
            Button(action: { selectCategory(category) }) {
                VStack(alignment: .leading) {
                    Text(category.name)
                    Text(category.type.rawValue.capitalized)
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    // MARK: - Talks View

    private var talksView: some View {
        List {
            // Decade chips
            if !availableDecades.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        chipButton("All", selected: selectedDecade == nil) { selectedDecade = nil; selectedYear = nil }
                        ForEach(availableDecades, id: \.self) { decade in
                            chipButton("\(decade)s", selected: selectedDecade == decade) {
                                selectedDecade = decade; selectedYear = nil
                            }
                        }
                    }
                }
                .listRowSeparator(.hidden)
            }

            // Year chips within decade
            if let decade = selectedDecade {
                let years = filteredTalks(allTalks: talks, decade: decade, year: nil)
                    .compactMap { $0.year > 0 ? $0.year : nil }
                let uniqueYears = Array(Set(years)).sorted()
                if uniqueYears.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            chipButton("All \(decade)s", selected: selectedYear == nil) { selectedYear = nil }
                            ForEach(uniqueYears, id: \.self) { year in
                                chipButton("\(year)", selected: selectedYear == year) { selectedYear = year }
                            }
                        }
                    }
                    .listRowSeparator(.hidden)
                }
            }

            if isLoadingTalks {
                ProgressView().frame(maxWidth: .infinity)
            } else {
                ForEach(displayedTalks) { result in
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
    }

    private var availableDecades: [Int] {
        let years = Set(talks.compactMap { $0.year > 0 ? $0.year : nil })
        guard years.count > 10 else { return [] }
        return Array(Set(years.map { ($0 / 10) * 10 })).sorted()
    }

    private var displayedTalks: [SearchResult] {
        filteredTalks(allTalks: talks, decade: selectedDecade, year: selectedYear)
    }

    private func filteredTalks(allTalks: [SearchResult], decade: Int?, year: Int?) -> [SearchResult] {
        var result = allTalks
        if let decade {
            result = result.filter { $0.year >= decade && $0.year < decade + 10 }
        }
        if let year {
            result = result.filter { $0.year == year }
        }
        return result
    }

    private func chipButton(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
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

    // MARK: - Loading

    private func loadInitial() async {
        switch initialMode {
        case .sangharakshitaByYear:
            talks = SharedDataLoader.sangharakshitaTalks
            selectedCategory = BrowseCategory(id: "sang", name: "Sangharakshita", type: .sangharakshita, browseUrl: "")
        case .sangharakshitaSeries:
            categories = SharedDataLoader.sangharakshitaSeriesAsCategories()
            return
        case .speaker(let name):
            await loadSpeaker(name)
        case .series(let urlOrName):
            selectedCategory = BrowseCategory(id: "series", name: "Series", type: .series, browseUrl: urlOrName)
            await loadBrowseUrl(urlOrName)
        case nil:
            categories = TalkRepository.shared.getBrowseCategories()
        }
    }

    private func selectCategory(_ category: BrowseCategory) {
        selectedDecade = nil
        selectedYear = nil

        switch category.type {
        case .sangharakshita:
            selectedCategory = category
            talks = SharedDataLoader.sangharakshitaTalks
        default:
            selectedCategory = category
            Task { await loadBrowseUrl(category.browseUrl) }
        }
    }

    private func loadBrowseUrl(_ url: String) async {
        isLoadingTalks = true
        do {
            let page = try await TalkRepository.shared.getTalksByBrowseUrl(url)
            talks = page.items
            if !page.title.isEmpty {
                selectedCategory = BrowseCategory(id: selectedCategory?.id ?? "browse", name: page.title,
                                                   type: selectedCategory?.type ?? .series, browseUrl: url)
            }
        } catch {
            self.error = friendlyError(error)
        }
        isLoadingTalks = false
    }

    private func loadSpeaker(_ name: String) async {
        isLoadingTalks = true
        selectedCategory = BrowseCategory(id: name, name: name, type: .theme, browseUrl: "")
        do {
            let page = try await TalkRepository.shared.browseBySpeaker(name)
            talks = page.items
        } catch {
            self.error = friendlyError(error)
        }
        isLoadingTalks = false
    }
}
