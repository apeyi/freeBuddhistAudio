import SwiftUI

/// A collection tile: FBA's cover image when there is one, otherwise stable
/// auto-generated gradient artwork with the title set large (see CollectionArtwork).
struct CollectionTile: View {
    let title: String
    let slug: String
    let imageUrl: String
    var showTitle = true

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            if !imageUrl.isEmpty, let url = URL(string: imageUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    generated
                }
                LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .center, endPoint: .bottom)
            } else {
                generated
            }
            if showTitle {
                Text(capitalizedFirst(title))
                    .font(.headline).bold()
                    .foregroundStyle(.white)
                    .lineLimit(3)
                    .padding(12)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private var generated: some View {
        LinearGradient(
            colors: [
                Color(hue: CollectionArtwork.hue(slug) / 360, saturation: 0.55, brightness: 0.65),
                Color(hue: CollectionArtwork.secondHue(slug) / 360, saturation: 0.6, brightness: 0.45),
            ],
            startPoint: .topLeading, endPoint: .bottomTrailing
        )
    }
}

/// "Remastered" marker used on talks and series with digitally remastered audio.
struct RemasterBadge: View {
    var body: some View {
        Text("REMASTERED")
            .font(.caption2).bold()
            .foregroundStyle(Color(red: 43/255, green: 33/255, blue: 23/255))
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Color(red: 219/255, green: 175/255, blue: 85/255))
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}
