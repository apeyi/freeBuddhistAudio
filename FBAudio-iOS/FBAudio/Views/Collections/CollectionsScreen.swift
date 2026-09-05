import SwiftUI

struct CollectionsScreen: View {
    let onCollectionClick: (MenuNode) -> Void

    @State private var tiles: [MenuNode] = []
    /// slug → cover image URL, filled in as collection pages load
    @State private var covers: [String: String] = [:]
    @State private var isLoading = true
    @State private var error: String?

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

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
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(tiles) { node in
                            let slug = node.collectionSlug ?? node.label
                            Button(action: { onCollectionClick(node) }) {
                                CollectionTile(title: node.label, slug: slug, imageUrl: covers[slug] ?? "")
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                }
            }
        }
        .miniPlayerClearance()
        .navigationTitle("Collections")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func load() async {
        isLoading = true
        error = nil
        do {
            tiles = try await ContentRepository.shared.getCollectionTiles()
            isLoading = false
            // Cover images live on each collection's page; fetch in the background
            // (cached) — tiles show generated artwork meanwhile.
            await withTaskGroup(of: (String, String)?.self) { group in
                for slug in tiles.compactMap(\.collectionSlug) {
                    group.addTask {
                        guard let page = try? await ContentRepository.shared.getPage(.namedCollection(slug), page: 1),
                              !page.imageUrl.isEmpty else { return nil }
                        return (slug, page.imageUrl)
                    }
                }
                for await result in group {
                    if let result { covers[result.0] = result.1 }
                }
            }
        } catch {
            self.error = friendlyError(error)
            isLoading = false
        }
    }
}
