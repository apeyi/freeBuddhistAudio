import SwiftUI

/// The spec's collections: fixed entry points, shown as the first tiles.
private struct HubTile: Identifiable {
    let title: String
    let slug: String
    let open: () -> Void
    var id: String { slug }
}

struct CollectionsScreen: View {
    let onCollectionClick: (MenuNode) -> Void
    let onSourceClick: (ContentSource, String) -> Void
    let onMenuClick: ([String], String) -> Void

    private var hub: [HubTile] {
        [
            HubTile(title: "Introductions", slug: "introductions") { onSourceClick(.apiCollection("introductions", title: "Introductions"), "Introductions") },
            HubTile(title: "Meditations", slug: "guided-meditations") { onSourceClick(.namedCollection("guided-meditations"), "Meditations") },
            HubTile(title: "Latest", slug: "latest") { onSourceClick(.apiCollection("latest", title: "Latest"), "Latest") },
            HubTile(title: "Themes", slug: "themes") { onMenuClick(["themes"], "Themes") },
            HubTile(title: "Series", slug: "all-series") { onSourceClick(.apiCollection("all_series", title: "Series"), "Series") },
            HubTile(title: "People", slug: "people") { onMenuClick(["people"], "People") },
            HubTile(title: "Places", slug: "places") { onMenuClick(["places"], "Places") },
        ]
    }

    @State private var tiles: [MenuNode] = []
    /// slug → cover image URL, filled in as collection pages load
    @State private var covers: [String: String] = [:]
    @State private var isLoading = true
    @State private var error: String?

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        // The hub tiles are static; only FBA's curated tiles need the network, so the
        // grid renders immediately and those fill in (or show a retry) below.
        ScrollView {
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(hub) { tile in
                    Button(action: tile.open) {
                        CollectionTile(title: tile.title, slug: tile.slug, imageUrl: "")
                    }
                    .buttonStyle(.plain)
                }
                Section {
                    ForEach(tiles) { node in
                        let slug = node.collectionSlug ?? node.label
                        Button(action: { onCollectionClick(node) }) {
                            CollectionTile(title: node.label, slug: slug, imageUrl: covers[slug] ?? "")
                        }
                        .buttonStyle(.plain)
                    }
                } header: {
                    Text("From Free Buddhist Audio")
                        .font(.subheadline).bold().foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 8)
                }
            }
            .padding(16)
            if isLoading {
                ProgressView().padding()
            } else if let error {
                VStack(spacing: 8) {
                    Text(error).foregroundStyle(.secondary)
                    Button("Retry") { Task { await load() } }
                }
                .padding()
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
