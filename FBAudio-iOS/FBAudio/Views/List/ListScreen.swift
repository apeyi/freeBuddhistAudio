import SwiftUI

/// A paginated list of talks/series from any ContentSource, with an optional
/// header (image, blurb, Donate for series pages).
struct ListScreen: View {
    let source: ContentSource
    let initialTitle: String
    let onItemClick: (SearchResult) -> Void
    let onDonateClick: () -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var imageUrl = ""
    @State private var hasRemaster = false
    @State private var items: [SearchResult] = []
    @State private var totalItems = 0
    @State private var isLoading = true
    @State private var isLoadingMore = false
    @State private var hasMore = false
    @State private var error: String?
    @State private var lastPage: ListPage?

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
                List {
                    header
                    if items.isEmpty {
                        Text("No talks found").foregroundStyle(.secondary)
                            .listRowSeparator(.hidden)
                    }
                    if totalItems > 0 {
                        Text("\(totalItems) \(source.isSeries ? "talks" : "items")")
                            .font(.caption).foregroundStyle(.secondary)
                            .listRowSeparator(.hidden)
                            .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 0, trailing: 16))
                    }
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                        ListItemCard(item: item, onClick: { onItemClick(item) })
                            .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                            .listRowSeparator(.hidden)
                            .onAppear {
                                if index >= items.count - 6 { Task { await loadMore() } }
                            }
                    }
                    if isLoadingMore {
                        ProgressView().frame(maxWidth: .infinity)
                            .listRowSeparator(.hidden)
                    }
                }
                .listStyle(.plain)
            }
        }
        .miniPlayerClearance()
        .navigationTitle(capitalizedFirst(title.isEmpty ? initialTitle : title))
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    @ViewBuilder
    private var header: some View {
        if !imageUrl.isEmpty || !description.isEmpty || source.isSeries {
            VStack(alignment: .leading, spacing: 12) {
                if !imageUrl.isEmpty, let url = URL(string: imageUrl) {
                    AsyncImage(url: url) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.gray.opacity(0.2)
                    }
                    .frame(maxWidth: .infinity)
                    .aspectRatio(16.0/9.0, contentMode: .fit)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                if hasRemaster { RemasterBadge() }
                if !description.isEmpty {
                    Text(description).font(.body)
                }
                if source.isSeries {
                    Button(action: onDonateClick) { Text("Donate").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent)
                        .tint(.saffronOrange)
                }
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
            .listRowSeparator(.hidden)
        }
    }

    private func load() async {
        isLoading = true
        error = nil
        do {
            let page = try await ContentRepository.shared.getPage(source, page: 1)
            lastPage = page
            title = page.title.isEmpty ? initialTitle : page.title
            description = page.description
            imageUrl = page.imageUrl
            hasRemaster = page.hasRemaster
            items = page.items
            totalItems = page.totalItems
            hasMore = page.hasMore
        } catch {
            self.error = friendlyError(error)
        }
        isLoading = false
    }

    private func loadMore() async {
        guard hasMore, !isLoadingMore, let prev = lastPage else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        guard var page = try? await ContentRepository.shared.getPage(source, page: prev.page + 1, previous: prev) else { return }
        if page.apiUrl.isEmpty { page.apiUrl = prev.apiUrl }
        if page.apiQuery.isEmpty { page.apiQuery = prev.apiQuery }
        lastPage = page
        var seen = Set(items.map(\.id))
        items += page.items.filter { seen.insert($0.id).inserted }
        hasMore = page.hasMore && !page.items.isEmpty
    }
}

/// Talk, series or speaker/place tile in the shared list style.
struct ListItemCard: View {
    let item: SearchResult
    let onClick: () -> Void

    var body: some View {
        let subtitle = [item.year > 0 ? "\(item.year)" : nil, item.centre.isEmpty ? nil : item.centre]
            .compactMap { $0 }.joined(separator: " · ")
        TalkCard(
            title: item.title,
            speaker: item.isSeries ? "Series" + (item.speaker.isEmpty ? "" : " · \(item.speaker)") : item.speaker,
            imageUrl: item.imageUrl,
            subtitle: item.isBrowseLink || subtitle.isEmpty ? nil : subtitle,
            onClick: onClick
        )
    }
}
