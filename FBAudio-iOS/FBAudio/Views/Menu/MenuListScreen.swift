import SwiftUI

/// Shows one section (or sub-section) of the website's curated menu: Themes, People, Places…
struct MenuListScreen: View {
    let path: [String]
    let title: String
    let onNodeClick: (MenuNode) -> Void

    @State private var nodes: [MenuNode] = []
    /// normalized browse path → image URL (People / Places only)
    @State private var images: [String: String] = [:]
    @State private var isLoading = true
    @State private var error: String?

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
            } else if let error {
                VStack(spacing: 8) {
                    Text(error).foregroundStyle(.secondary)
                    Button("Retry") { Task { await load() } }
                }
            } else if nodes.isEmpty {
                Text("Nothing here yet").foregroundStyle(.secondary)
            } else {
                List(nodes) { node in
                    // "all speakers" / "all our places" / "all themes": FBA's complete A–Z index,
                    // as opposed to the curated picks below — shown as a distinct list row.
                    let isIndex: Bool = {
                        if case .apiCollection = node.toSource(), !node.hasChildren { return true }
                        return false
                    }()
                    Button(action: { onNodeClick(node) }) {
                        HStack(spacing: 12) {
                            if isIndex {
                                Image(systemName: "list.bullet")
                                    .font(.title2).foregroundStyle(Color.saffronOrange)
                                    .frame(width: 48, height: 48)
                            } else {
                                CollectionTile(title: "", slug: node.collectionSlug ?? node.label,
                                               imageUrl: images[FBAScraper.normalizeBrowsePath(node.link)] ?? "", showTitle: false)
                                    .frame(width: 48, height: 48)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text(capitalizedFirst(node.label))
                                    .font(.body).foregroundStyle(.primary)
                                if isIndex {
                                    Text("Complete A–Z list").font(.caption).foregroundStyle(.secondary)
                                } else if node.hasChildren {
                                    Text("\(node.children.count) entries").font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(.secondary)
                        }
                        .padding(12)
                        .background(isIndex ? Color.clear : Color(.secondarySystemGroupedBackground))
                        .overlay(isIndex ? RoundedRectangle(cornerRadius: 12).stroke(Color(.separator)) : nil)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                    .listRowSeparator(.hidden)
                }
                .listStyle(.plain)
            }
        }
        .miniPlayerClearance()
        .navigationTitle(capitalizedFirst(title))
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func load() async {
        isLoading = true
        error = nil
        do {
            nodes = try await ContentRepository.shared.getNodeChildren(path)
                .filter { !($0.isPlaceholder && !$0.hasChildren) }
        } catch {
            self.error = friendlyError(error)
        }
        isLoading = false
        // People and Places entries get the images FBA shows in its own indexes
        let indexType: String? = switch path.first?.lowercased() {
            case "people": "speakers"
            case "places": "places"
            default: nil
        }
        if let indexType {
            images = await ContentRepository.shared.getIndexImages(indexType)
        }
    }
}
