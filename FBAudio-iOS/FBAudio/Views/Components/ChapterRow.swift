import SwiftUI

/// A single chapter row showing title, optional duration, and highlighting if current.
/// Used by both DetailScreen's inline chapter list and PlayerScreen's chapter sheet.
struct ChapterRow: View {
    let index: Int
    let track: Track
    let isCurrent: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                Text(track.title.isEmpty ? "Chapter \(index + 1)" : track.title)
                    .fontWeight(isCurrent ? .bold : .regular)
                    .foregroundStyle(isCurrent ? Color.saffronOrange : .primary)
                Spacer()
                if track.durationSeconds > 0 {
                    Text(formatDuration(track.durationSeconds))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            // Make the whole row (including the spacer gap) tappable, not just the text
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
