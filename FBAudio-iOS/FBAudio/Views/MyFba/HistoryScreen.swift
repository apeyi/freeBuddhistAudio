import SwiftUI

/// Continue listening (See all) and Listening history (SPEC "My FBA, Continue listening (See all)").
/// `continueOnly`: talks in progress with progress lines and Resume circles; swipe left marks a
/// talk finished. Otherwise every talk played, grouped by day. Mirrors Android `HistoryScreen`.
struct HistoryScreen: View {
    let continueOnly: Bool
    let onTalkClick: (String) -> Void
    var onFullHistory: () -> Void = {}

    @EnvironmentObject private var player: AudioPlayer
    @Environment(\.pageColors) private var page
    @State private var recent: [PersistenceManager.RecentlyListened] = []

    private var inProgress: [PersistenceManager.RecentlyListened] { MyFbaRules.continueListening(recent, now: Date()) }

    private var days: [(String, [PersistenceManager.RecentlyListened])] {
        let now = Date()
        var out: [(String, [PersistenceManager.RecentlyListened])] = []
        for r in recent.sorted(by: { $0.timestamp > $1.timestamp }) {
            let label = MyFbaRules.historyDayLabel(r.timestamp, now: now)
            if out.last?.0 == label { out[out.count - 1].1.append(r) } else { out.append((label, [r])) }
        }
        return out
    }

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(continueOnly ? "Continue listening" : "Listening history")
                        .font(.fbaPageTitle).foregroundStyle(FbaColors.black)
                    if let sub = subtitle { Text(sub).font(.subheadline).foregroundStyle(FbaColors.meta) }
                }
                .listRowSeparator(.hidden)
            }
            if continueOnly {
                Section {
                    ForEach(inProgress) { r in
                        VStack(spacing: 0) {
                            HistoryRow(entry: r, onTalkClick: onTalkClick, meta: MyFbaRules.continueMeta(r))
                            if r.totalDurationSeconds > 0 {
                                ProgressBar(fraction: MyFbaRules.progress(r), color: page.deep, height: 3).padding(.bottom, 8)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button { markFinished(r.catNum) } label: { Label("Finished", systemImage: "checkmark") }
                                .tint(Color(hex: Brand.fbaGreen.set.deep))
                        }
                    }
                } footer: {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Talks leave this list when you finish them, or after 90 days without a play. Swipe left on a talk to mark it as finished.")
                        Button("See full listening history", action: onFullHistory)
                            .font(.subheadline.weight(.medium)).foregroundStyle(page.deep)
                    }
                    .padding(.top, 8)
                }
            } else {
                ForEach(days, id: \.0) { day, entries in
                    Section {
                        ForEach(entries) { HistoryRow(entry: $0, onTalkClick: onTalkClick) }
                    } header: {
                        Text(day).font(.subheadline.weight(.bold)).foregroundStyle(FbaColors.meta).textCase(nil)
                    }
                }
            }
        }
        .listStyle(.plain)
        .miniPlayerClearance()
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { recent = PersistenceManager.shared.getRecentlyListened() }
    }

    private var subtitle: String? {
        if continueOnly { return inProgress.isEmpty ? "Nothing in progress" : MyFbaRules.inProgressLabel(inProgress.count) }
        return recent.isEmpty ? "Talks you play will appear here." : nil
    }

    private func markFinished(_ catNum: String) {
        PersistenceManager.shared.markFinished(catNum)
        recent = PersistenceManager.shared.getRecentlyListened()
    }
}
