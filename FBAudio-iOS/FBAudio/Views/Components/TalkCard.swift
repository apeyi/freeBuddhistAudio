import SwiftUI

struct TalkCard: View {
    let title: String
    let speaker: String
    let imageUrl: String
    let subtitle: String?
    let onClick: () -> Void
    var trailing: AnyView?

    init(title: String, speaker: String, imageUrl: String = "",
         subtitle: String? = nil, onClick: @escaping () -> Void,
         trailing: AnyView? = nil) {
        self.title = title
        self.speaker = speaker
        self.imageUrl = imageUrl
        self.subtitle = subtitle
        self.onClick = onClick
        self.trailing = trailing
    }

    var body: some View {
        HStack(spacing: 12) {
            // Main tappable area (artwork + text). Kept as a sibling of `trailing`
            // rather than wrapping it, so an interactive trailing control (e.g. a
            // delete button) isn't nested inside this Button — nested buttons
            // swallow the inner tap.
            Button(action: onClick) {
                HStack(spacing: 12) {
                    if !imageUrl.isEmpty {
                        AsyncImage(url: URL(string: imageUrl)) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Color.gray.opacity(0.3)
                        }
                        .frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .font(.body)
                            .lineLimit(2)
                            .foregroundStyle(.primary)
                        if !speaker.isEmpty {
                            Text(speaker)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        if let subtitle, !subtitle.isEmpty {
                            Text(subtitle)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if let trailing {
                trailing
            }
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
