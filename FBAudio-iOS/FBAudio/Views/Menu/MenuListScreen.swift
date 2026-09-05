import SwiftUI

/// Shows one section (or sub-section) of the website's curated menu: Themes, People, Places…
struct MenuListScreen: View {
    let path: [String]
    let title: String
    let onNodeClick: (MenuNode) -> Void

    @State private var nodes: [MenuNode] = []
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
                    Button(action: { onNodeClick(node) }) {
                        HStack(spacing: 12) {
                            CollectionTile(title: "", slug: node.collectionSlug ?? node.label, imageUrl: "", showTitle: false)
                                .frame(width: 48, height: 48)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(capitalizedFirst(node.label))
                                    .font(.body).foregroundStyle(.primary)
                                if node.hasChildren {
                                    Text("\(node.children.count) entries").font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(.secondary)
                        }
                        .padding(12)
                        .background(Color(.secondarySystemGroupedBackground))
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
    }
}
