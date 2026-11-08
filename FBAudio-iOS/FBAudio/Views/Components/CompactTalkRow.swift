import SwiftUI

/// The design's compact row (Figma TalkRow): a 56 rounded thumbnail (talk artwork rule), the title
/// on up to two lines and a grey "Speaker · year" line. Talk rows end in a black play symbol in a
/// light grey circle: the circle plays in the Now Playing bar, the rest of the row opens the page.
/// The playing row shows a pause symbol and its title and "Playing" line in the page colour.
/// `onPlay` nil = no circle (series, speakers, places). Mirrors Android `CompactTalkRow`.
struct CompactTalkRow: View {
    let title: String
    let meta: String
    let imageUrl: String?
    let onClick: () -> Void
    var onPlay: (() -> Void)? = nil
    var isCurrent = false
    var isPlaying = false
    var listened = false
    var thumbSize = CGSize(width: 56, height: 56)
    @Environment(\.pageColors) private var page

    var body: some View {
        HStack(spacing: 14) {
            Button(action: onClick) {
                HStack(spacing: 14) {
                    Artwork(url: imageUrl, aspect: Double(thumbSize.width / thumbSize.height))
                        .frame(width: thumbSize.width, height: thumbSize.height)
                        .clipShape(RoundedRectangle(cornerRadius: FbaDimens.radiusThumb))
                        .overlay(alignment: .topTrailing) {
                            if listened {
                                Image(systemName: "checkmark").font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                                    .frame(width: 18, height: 18).background(page.deep, in: Circle())
                                    .overlay(Circle().stroke(.white, lineWidth: 2))
                                    .padding(4)
                                    .accessibilityLabel("Listened")
                            }
                        }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .font(.callout.weight(.medium))
                            .foregroundStyle(isCurrent ? page.deep : FbaColors.black)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                        let line = isCurrent ? ["Playing", meta].filter { !$0.isEmpty }.joined(separator: " · ") : meta
                        if !line.isEmpty {
                            Text(line).font(.footnote).foregroundStyle(isCurrent ? page.deep : FbaColors.meta).lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if let onPlay {
                Button(action: onPlay) {
                    Image(systemName: isCurrent && isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 14))
                        .foregroundStyle(Color(hex: 0x141414))
                        .frame(width: FbaDimens.playCircle, height: FbaDimens.playCircle)
                        .background(Color(hex: 0xF0F0F0), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(isCurrent && isPlaying ? "Pause \(title)" : "Play \(title)")
            }
        }
        .padding(.vertical, 10)
    }
}

extension SearchResult {
    /// The grey line under a list item's title: "Speaker · year", or "Series · Speaker".
    var metaLine: String {
        let year = self.year > 0 ? String(self.year) : nil
        if isSeries { return ["Series", speaker.isEmpty ? nil : speaker, year].compactMap { $0 }.joined(separator: " · ") }
        if isCollection { return "Collection" }
        if isBrowseLink { return "" }
        return [speaker.isEmpty ? nil : speaker, year].compactMap { $0 }.joined(separator: " · ")
    }
}
