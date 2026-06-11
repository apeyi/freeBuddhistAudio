import SwiftUI

struct ContentView: View {
    @EnvironmentObject var player: AudioPlayer
    @State private var selectedTab = 0
    @State private var showPlayer = false
    @State private var navigationPath = NavigationPath()

    var body: some View {
        TabView(selection: tabSelection) {
            NavigationStack(path: $navigationPath) {
                HomeScreen(
                    onTalkClick: { navigateToDetail($0) },
                    onSangharakshitaByYearClick: { navigateToBrowse(.sangharakshitaByYear) },
                    onSangharakshitaSeriesClick: { navigateToBrowse(.sangharakshitaSeries) },
                    onDonateClick: { openDonateUrl() }
                )
                .navigationTitle("Free Buddhist Audio")
                .navigationDestination(for: Route.self) { route in
                    routeView(route)
                }
            }
            .miniPlayerInset(player: player, isHidden: showPlayer) { showPlayer = true }
            .tabItem {
                Label("Home", systemImage: "house")
            }
            .tag(0)

            NavigationStack {
                SearchScreen(
                    onTalkClick: { catNum in
                        // navigationPath is plain state — appending works even while
                        // tab 0 is offscreen, no delay needed (the old asyncAfter was
                        // uncancellable and raced with further taps).
                        selectedTab = 0
                        navigateToDetail(catNum)
                    },
                    onSeriesClick: { seriesUrl in
                        selectedTab = 0
                        navigateToBrowse(.series(seriesUrl))
                    }
                )
            }
            .miniPlayerInset(player: player, isHidden: showPlayer) { showPlayer = true }
            .tabItem {
                Label("Search", systemImage: "magnifyingglass")
            }
            .tag(1)

            NavigationStack {
                DownloadsScreen(onTalkClick: { catNum in
                    selectedTab = 0
                    navigateToDetail(catNum)
                })
            }
            .miniPlayerInset(player: player, isHidden: showPlayer) { showPlayer = true }
            .tabItem {
                Label("Downloads", systemImage: "arrow.down.circle")
            }
            .tag(2)
        }
        .fullScreenCover(isPresented: $showPlayer) {
            PlayerScreen(
                player: player,
                onNavigateToDetail: { catNum in
                    showPlayer = false
                    // The push goes onto the Home tab's stack — switch to it, or the
                    // navigation lands invisibly behind the Search/Downloads tab.
                    selectedTab = 0
                    navigateToDetail(catNum)
                },
                onSpeakerClick: { speaker in
                    showPlayer = false
                    selectedTab = 0
                    navigateToBrowse(.speaker(speaker))
                }
            )
        }
        .onOpenURL { url in
            handleDeepLink(url)
        }
        .alert("Delete download?", isPresented: $player.showDeleteDownloadPrompt) {
            Button("Delete", role: .destructive) { player.confirmDeleteAfterPlayback() }
            Button("Keep", role: .cancel) { player.dismissDeletePrompt() }
        } message: {
            Text("You've finished listening. Remove the offline files?")
        }
    }

    // Tapping the already-selected Home tab pops to root
    private var tabSelection: Binding<Int> {
        Binding(
            get: { selectedTab },
            set: { newTab in
                if newTab == selectedTab && newTab == 0 {
                    navigationPath = NavigationPath()
                }
                selectedTab = newTab
            }
        )
    }

    // MARK: - Navigation

    enum Route: Hashable {
        case detail(String)
        case browse(BrowseModeRoute)
        case transcript(String, String)
    }

    enum BrowseModeRoute: Hashable {
        case sangharakshitaByYear
        case sangharakshitaSeries
        case speaker(String)
        case series(String)

        var toBrowseMode: BrowseScreen.BrowseMode {
            switch self {
            case .sangharakshitaByYear: return .sangharakshitaByYear
            case .sangharakshitaSeries: return .sangharakshitaSeries
            case .speaker(let name): return .speaker(name)
            case .series(let url): return .series(url)
            }
        }
    }

    private func navigateToDetail(_ catNum: String) {
        navigationPath.append(Route.detail(catNum))
    }

    private func navigateToBrowse(_ mode: BrowseModeRoute) {
        navigationPath.append(Route.browse(mode))
    }

    // MARK: - Deep links
    // fbaudio://talk/01, fbaudio://series/X04, fbaudio://speaker/Name,
    // and https://(www.)freebuddhistaudio.com/audio/details?num=…  (parity with Android)
    private func handleDeepLink(_ url: URL) {
        selectedTab = 0
        if url.scheme == "fbaudio" {
            let id = url.pathComponents.count > 1 ? url.pathComponents[1] : nil
            guard let id else { return }
            switch url.host {
            case "talk": navigateToDetail(id)
            case "series":
                navigateToBrowse(.series("https://www.freebuddhistaudio.com/series/details?num=\(id)"))
            case "speaker": navigateToBrowse(.speaker(id))
            default: break
            }
            return
        }

        guard let host = url.host,
              host == "www.freebuddhistaudio.com" || host == "freebuddhistaudio.com",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return }
        let num = components.queryItems?.first(where: { $0.name == "num" })?.value
        let speaker = components.queryItems?.first(where: { $0.name == "s" })?.value
        switch true {
        case url.path.hasPrefix("/audio/details"):
            if let num { navigateToDetail(num) }
        case url.path.hasPrefix("/series/details"):
            if let num {
                navigateToBrowse(.series("https://www.freebuddhistaudio.com/series/details?num=\(num)"))
            }
        case url.path.hasPrefix("/browse"):
            if let speaker { navigateToBrowse(.speaker(speaker)) }
        default:
            break
        }
    }

    @ViewBuilder
    private func routeView(_ route: Route) -> some View {
        switch route {
        case .detail(let catNum):
            DetailScreen(
                catNum: catNum,
                onPlay: { catNum in
                    Task {
                        if let talk = await TalkRepository.shared.getTalkDetail(catNum) {
                            player.playTalk(talk)
                        }
                    }
                },
                onSpeakerClick: { navigateToBrowse(.speaker($0)) },
                onSeriesClick: { navigateToBrowse(.series($0)) },
                onTranscriptClick: { url, catNum in
                    navigationPath.append(Route.transcript(url, catNum))
                }
            )
        case .browse(let mode):
            BrowseScreen(
                initialMode: mode.toBrowseMode,
                onTalkClick: { navigateToDetail($0) }
            )
        case .transcript(let url, let catNum):
            TranscriptScreen(transcriptUrl: url, catNum: catNum)
        }
    }

    private func openDonateUrl() {
        if let url = URL(string: "https://www.freebuddhistaudio.com/donate/") {
            UIApplication.shared.open(url)
        }
    }
}

private extension View {
    /// Places the mini player in a reserved strip at the bottom of a tab's
    /// content — above the tab bar, not floating over it — so SwiftUI insets the
    /// tab's scroll views by the player's real height. Content (e.g. talk
    /// chapters) is never hidden behind it, with no hardcoded height guess.
    func miniPlayerInset(player: AudioPlayer, isHidden: Bool, onExpand: @escaping () -> Void) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) {
            if !isHidden {
                MiniPlayer(player: player, onExpand: onExpand)
            }
        }
    }
}
