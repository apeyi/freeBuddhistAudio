import SwiftUI

/// My FBA (v1.0 design, SPEC "My FBA"): the listener's photo top right (opens the Account
/// sheet), the sign-in card when signed out, Continue listening, Library (Downloads, Listening
/// history), Recently listened, Help (Settings, Contact support) and Support FBA. A new
/// listener sees a dashed empty Continue listening card that points to Explore.
/// Mirrors Android `MyFbaScreen`.
struct MyFbaScreen: View {
    let onTalkClick: (String) -> Void
    let onDonateClick: () -> Void
    let onLoginClick: () -> Void
    var onDownloadsClick: () -> Void = {}
    var onContinueSeeAll: () -> Void = {}
    var onHistoryClick: () -> Void = {}
    var onSettingsClick: () -> Void = {}
    var onHelpClick: () -> Void = {}
    var onExploreClick: () -> Void = {}

    @EnvironmentObject private var player: AudioPlayer
    @ObservedObject private var auth = AuthRepository.shared
    @ObservedObject private var downloadManager = DownloadManager.shared
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.pageColors) private var page
    @Environment(\.openURL) private var openURL
    @State private var recent: [PersistenceManager.RecentlyListened] = []
    @State private var showAccount = false

    private var continueListening: [PersistenceManager.RecentlyListened] {
        MyFbaRules.continueListening(recent, now: Date())
    }

    private var completedDownloads: [DownloadManager.DownloadState] {
        downloadManager.downloads.values.filter { $0.status == .complete && !$0.transcriptOnly }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("My FBA").font(.largeTitle.weight(.bold)).foregroundStyle(FbaColors.black)
                    Spacer()
                    if auth.state.loggedIn {
                        Button { showAccount = true } label: { MyFbaAvatar(url: auth.state.avatarUrl, name: auth.state.username, size: 40) }
                            .buttonStyle(.plain).accessibilityLabel("Account")
                    }
                }
                .padding(.horizontal, FbaDimens.margin).padding(.top, 8).padding(.bottom, 4)

                if !auth.state.loggedIn && FeatureFlags.auth { signInCard }

                section("Continue listening", onSeeAll: continueListening.isEmpty ? nil : onContinueSeeAll) {
                    if continueListening.isEmpty {
                        emptyContinueCard
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(alignment: .top, spacing: 12) {
                                ForEach(continueListening.prefix(10)) { r in continueCard(r) }
                            }
                            .padding(.horizontal, FbaDimens.margin)
                        }
                    }
                }

                group("Library") {
                    MyFbaNavRow(title: "Downloads",
                                value: MyFbaRules.downloadsSummary(count: completedDownloads.count,
                                                                   bytes: completedDownloads.reduce(0) { $0 + $1.totalBytes }),
                                action: onDownloadsClick)
                    if !recent.isEmpty { MyFbaNavRow(title: "Listening history", action: onHistoryClick) }
                }

                if !recent.isEmpty {
                    section("Recently listened", onSeeAll: onHistoryClick) {
                        VStack(spacing: 0) {
                            ForEach(Array(recent.prefix(3).enumerated()), id: \.element.id) { i, r in
                                if i > 0 { Divider().overlay(FbaColors.divider) }
                                HistoryRow(entry: r, onTalkClick: onTalkClick)
                            }
                        }
                        .padding(.horizontal, FbaDimens.margin)
                    }
                }

                group("Help") {
                    MyFbaNavRow(title: "Settings", action: onSettingsClick)
                    MyFbaNavRow(title: "Contact support", action: onHelpClick)
                }

                SupportFbaButton(action: onDonateClick)
                    .padding(.horizontal, FbaDimens.margin).padding(.top, 24).padding(.bottom, 24)
            }
        }
        .background(Color.white)
        .miniPlayerClearance()
        .navigationBarHidden(true)
        .onAppear { refresh() }
        .onChange(of: scenePhase) { phase in if phase == .active { refresh() } }
        .onChange(of: auth.state.loggedIn) { _ in refresh() }
        .onChange(of: player.currentTalk?.catNum) { _ in refresh() }
        .sheet(isPresented: $showAccount) { accountSheet }
    }

    private func refresh() {
        recent = PersistenceManager.shared.getRecentlyListened()
        // Pull the account's web history each time the tab is opened while signed in.
        if auth.isLoggedIn {
            Task {
                await HistoryRepository.shared.syncFromServer()
                recent = PersistenceManager.shared.getRecentlyListened()
            }
        }
    }

    // MARK: - Sections

    private func section<Content: View>(_ title: String, onSeeAll: (() -> Void)?, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title).font(.fbaSectionTitle).foregroundStyle(FbaColors.black)
                Spacer()
                if let onSeeAll {
                    Button("See all", action: onSeeAll).font(.subheadline.weight(.medium)).foregroundStyle(page.deep)
                }
            }
            .padding(.horizontal, FbaDimens.margin)
            content()
        }
        .padding(.top, 20)
    }

    private func group<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(.fbaSectionTitle).foregroundStyle(FbaColors.black)
                .padding(.horizontal, FbaDimens.margin).padding(.bottom, 4)
            content()
        }
        .padding(.top, 20)
    }

    private func continueCard(_ r: PersistenceManager.RecentlyListened) -> some View {
        Button { onTalkClick(r.catNum) } label: {
            VStack(alignment: .leading, spacing: 6) {
                Artwork(url: r.imageUrl, aspect: 200.0 / 134.0).frame(width: 200, height: 134)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                if r.totalDurationSeconds > 0 {
                    ProgressBar(fraction: MyFbaRules.progress(r), color: page.deep).padding(.top, 2)
                }
                Text(r.title).font(.subheadline.weight(.medium)).foregroundStyle(FbaColors.black)
                    .lineLimit(2).multilineTextAlignment(.leading)
                if r.totalDurationSeconds > 0, let left = PlaybackMath.timeLeftLabel(MyFbaRules.remainingSeconds(r)) {
                    Text(left).font(.footnote).foregroundStyle(FbaColors.meta)
                }
            }
            .frame(width: 200, alignment: .leading)
        }
        .buttonStyle(.plain)
    }

    /// Signed out: the sign-in card in the page tint (Dharmachakra's wording, 8 Oct).
    private var signInCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Keep your place on every device").font(.title3.weight(.bold)).foregroundStyle(FbaColors.black)
            Text("Sign in to sync your FBA history and continue listening on any phone, tablet or at freebuddhistaudio.com.")
                .font(.subheadline).foregroundStyle(FbaColors.black)
            Text("Order members: sign in to hear Order talks and access Sangharakshita's seminar texts.")
                .font(.subheadline).foregroundStyle(FbaColors.black)
            FbaPrimaryButton(title: "Sign in", action: onLoginClick).padding(.top, 4)
            Button("New to FBA? Create an account") { openURL(Self.createAccountURL) }
                .font(.subheadline.weight(.medium)).foregroundStyle(page.deep)
                .frame(maxWidth: .infinity).padding(.top, 2)
        }
        .padding(18)
        .background(page.tint, in: RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, FbaDimens.margin).padding(.vertical, 8)
    }

    /// New listener: a dashed empty card pointing to Explore.
    private var emptyContinueCard: some View {
        VStack(spacing: 14) {
            Text("Nothing here yet. Talks you start will appear here, so you can pick up where you left off.")
                .font(.subheadline).foregroundStyle(FbaColors.meta)
            FbaPrimaryButton(title: "Explore talks", action: onExploreClick)
        }
        .padding(20)
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(FbaColors.grey500, style: StrokeStyle(lineWidth: 1.5, dash: [5, 4])))
        .padding(.horizontal, FbaDimens.margin)
    }

    /// Account sheet (tap your photo): photo, name, email, Order member, Manage account, Sign out.
    private var accountSheet: some View {
        VStack(spacing: 6) {
            MyFbaAvatar(url: auth.state.avatarUrl, name: auth.state.username, size: 72).padding(.top, 24)
            Text(auth.state.username.isEmpty ? "Your account" : auth.state.username)
                .font(.title3.weight(.bold)).foregroundStyle(FbaColors.black)
            if !auth.state.email.isEmpty {
                Text(auth.state.email).font(.subheadline).foregroundStyle(FbaColors.meta)
            }
            if auth.state.isOrderMember {
                // Always Order Only Blue, whatever the page colour.
                Text("Order member").font(.footnote.weight(.medium)).foregroundStyle(FbaColors.orderOnly.deep)
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .background(FbaColors.orderOnly.tint, in: RoundedRectangle(cornerRadius: 8))
            }
            VStack(spacing: 0) {
                Divider()
                Button { showAccount = false; openURL(Self.manageAccountURL) } label: {
                    Text("Manage account").foregroundStyle(FbaColors.black).frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 14)
                }
                Divider()
                Button { showAccount = false; Task { await auth.logout() } } label: {
                    Text("Sign out").foregroundStyle(FbaColors.destructive).frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 14)
                }
                Divider()
                Text("Signing out keeps your listening history and downloads on this iPhone.")
                    .font(.footnote).foregroundStyle(FbaColors.meta).frame(maxWidth: .infinity, alignment: .leading).padding(.top, 8)
            }
            .padding(.top, 8)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 24)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    /// Manage account (name, photo, password) lives on the Triratna sign-in site.
    static let manageAccountURL = URL(string: "https://thebuddhistcentre.com/user/password")!
    static let createAccountURL = URL(string: "https://thebuddhistcentre.com/user/register")!
}

/// A recently listened talk as a compact row: "Speaker · Listened" once finished. The circle
/// plays in the Now Playing bar; the row opens the talk.
struct HistoryRow: View {
    let entry: PersistenceManager.RecentlyListened
    let onTalkClick: (String) -> Void
    var meta: String? = nil
    @EnvironmentObject private var player: AudioPlayer

    var body: some View {
        let finished = MyFbaRules.isFinished(entry)
        let line = meta ?? [entry.speaker, finished ? "Listened" : ""].filter { !$0.isEmpty }.joined(separator: " · ")
        CompactTalkRow(title: entry.title, meta: line, imageUrl: entry.imageUrl,
                       onClick: { onTalkClick(entry.catNum) },
                       onPlay: { play() },
                       isCurrent: player.activeCatNum == entry.catNum,
                       isPlaying: player.isPlaying,
                       listened: meta == nil && finished)
    }

    private func play() {
        if player.currentTalk?.catNum == entry.catNum { player.togglePlayPause(); return }
        Task { if let talk = await TalkRepository.shared.getTalkDetailForPlayback(entry.catNum) { player.playTalk(talk) } }
    }
}

/// The listener's photo in a circle, or their initial in a page-tint circle.
struct MyFbaAvatar: View {
    let url: String
    let name: String
    let size: CGFloat
    @Environment(\.pageColors) private var page

    var body: some View {
        Group {
            if !url.isEmpty, let u = URL(string: url) {
                AsyncImage(url: u) { image in image.resizable().aspectRatio(contentMode: .fill) }
                    placeholder: { page.tint }
            } else {
                ZStack {
                    page.tint
                    Text(name.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? "?")
                        .font(.system(size: size * 0.42, weight: .bold)).foregroundStyle(page.deep)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}

/// A grouped-list row: title, optional value, chevron.
struct MyFbaNavRow: View {
    let title: String
    var value: String? = nil
    var titleColor: Color = FbaColors.black
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 0) {
                HStack {
                    Text(title).foregroundStyle(titleColor)
                    Spacer()
                    if let value { Text(value).font(.subheadline).foregroundStyle(FbaColors.meta) }
                    Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(FbaColors.grey500)
                }
                .padding(.horizontal, FbaDimens.margin).padding(.vertical, 14)
                Divider().overlay(FbaColors.divider).padding(.leading, FbaDimens.margin)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// A thin rounded progress line in the page colour.
struct ProgressBar: View {
    let fraction: Double
    let color: Color
    var height: CGFloat = 4

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(FbaColors.divider)
                Capsule().fill(color).frame(width: geo.size.width * CGFloat(fraction.isFinite ? min(max(fraction, 0), 1) : 0))
            }
        }
        .frame(height: height)
    }
}
