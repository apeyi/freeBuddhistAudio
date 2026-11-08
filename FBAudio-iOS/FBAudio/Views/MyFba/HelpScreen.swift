import SwiftUI

/// Help and support (SPEC "My FBA, Help and support"): common questions that open in place,
/// Email us, Report a problem with a talk (asks which talk first), and the site's help pages.
/// Mirrors Android `HelpScreen`.
struct HelpScreen: View {
    static let helpPagesURL = URL(string: "https://www.freebuddhistaudio.com/page/help-and-info")!

    /// Draft copy for Dharmachakra to edit.
    static let questions: [(String, String)] = [
        ("How do I keep my place on another device?",
         "Sign in on My FBA. Your listening history and the place you reached in each talk then follow you to any phone, tablet or freebuddhistaudio.com."),
        ("Can I listen without a connection?",
         "Yes. Tap Download on a talk's page and it's saved to this iPhone. You'll find it in My FBA, Downloads. To save mobile data, turn on Download over Wi-Fi only in Settings."),
        ("How do I hear Order talks and seminar transcripts?",
         "Order talks and Sangharakshita's seminar transcripts are for Order members. Sign in with your Triratna account and they appear in the app."),
        ("A talk won't play. What can I try?",
         "Check your connection and try again. Where a talk has a Remastered and an Original version, try the other one, or download the talk. If it still won't play, please report the problem below and we'll look into it."),
    ]

    private struct TalkChoice: Identifiable, Hashable {
        let title: String, speaker: String, catNum: String
        var id: String { catNum }
    }

    @EnvironmentObject private var player: AudioPlayer
    @Environment(\.openURL) private var openURL
    @State private var pickTalk = false
    @State private var reporting: TalkChoice?
    @State private var copied = false

    private var version: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "" }
    private var device: String { "\(UIDevice.current.model), iOS \(UIDevice.current.systemVersion)" }

    /// Which talk? The one playing first, then recent ones.
    private var choices: [TalkChoice] {
        var out: [TalkChoice] = []
        if let t = player.currentTalk { out.append(TalkChoice(title: t.title, speaker: t.speaker, catNum: t.catNum)) }
        for r in PersistenceManager.shared.getRecentlyListened() where !out.contains(where: { $0.catNum == r.catNum }) {
            out.append(TalkChoice(title: r.title, speaker: r.speaker, catNum: r.catNum))
            if out.count >= 8 { break }
        }
        return out
    }

    var body: some View {
        List {
            Section("Common questions") {
                ForEach(Self.questions, id: \.0) { q, a in
                    DisclosureGroup {
                        Text(a).font(.subheadline).foregroundStyle(FbaColors.black).padding(.vertical, 4)
                    } label: {
                        Text(q).foregroundStyle(FbaColors.black)
                    }
                }
            }
            Section {
                Button { emailUs() } label: {
                    LabeledContent("Email us", value: ReportProblem.supportEmail)
                }
                .foregroundStyle(FbaColors.black)
                if copied {
                    Text("No email app is set up, so the address has been copied.").font(.footnote).foregroundStyle(FbaColors.meta)
                }
                Button("Report a problem with a talk") { pickTalk = true }.foregroundStyle(FbaColors.black)
                Button("Help pages on freebuddhistaudio.com") { openURL(Self.helpPagesURL) }.foregroundStyle(FbaColors.black)
            } header: {
                Text("Get in touch")
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Emails include the app version and your device, so we can help sooner.")
                    Text("Version \(version)")
                }
            }
        }
        .listStyle(.insetGrouped)
        .miniPlayerClearance()
        .navigationTitle("Help and support")
        .navigationBarTitleDisplayMode(.large)
        .confirmationDialog("Which talk?", isPresented: $pickTalk, titleVisibility: .visible) {
            ForEach(choices) { c in Button(c.title) { reporting = c } }
            Button("Cancel", role: .cancel) {}
        } message: {
            if choices.isEmpty { Text("Play the talk first, then report the problem from its page or here.") }
        }
        .confirmationDialog("Report a problem", isPresented: Binding(get: { reporting != nil }, set: { if !$0 { reporting = nil } }),
                            titleVisibility: .visible, presenting: reporting) { c in
            ForEach(ReportProblem.reasons, id: \.self) { reason in Button(reason) { report(reason, c) } }
            Button("Cancel", role: .cancel) {}
        } message: { c in
            Text([c.title, c.speaker].filter { !$0.isEmpty }.joined(separator: " · "))
        }
    }

    private func emailUs() {
        let email = ReportProblem.supportRequest(device: device, appVersion: version, signedIn: AuthRepository.shared.isLoggedIn)
        send(email)
    }

    private func report(_ reason: String, _ c: TalkChoice) {
        var position: Int? = nil
        if let talk = player.currentTalk, talk.catNum == c.catNum {
            position = Int(PlaybackMath.cumulativePositionMs(tracks: talk.tracks, trackIndex: player.currentTrackIndex,
                                                             positionInTrackMs: Int64(player.currentPosition * 1000)) / 1000)
        }
        let email = ReportProblem.email(reason: reason, talkTitle: c.title, catNum: c.catNum, positionSeconds: position,
                                        device: device, appVersion: version, signedIn: AuthRepository.shared.isLoggedIn)
        send(email)
    }

    private func send(_ email: ReportProblem.Email) {
        if let url = ReportProblem.mailtoURL(email), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        } else {
            // No mail app set up: copy the address so it can be pasted elsewhere.
            UIPasteboard.general.string = email.to
            copied = true
        }
    }
}
