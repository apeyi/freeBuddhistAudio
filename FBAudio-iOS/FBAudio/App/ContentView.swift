import SwiftUI

let donateURL = URL(string: "https://www.freebuddhistaudio.com/donate/")!

struct ContentView: View {
    @EnvironmentObject var player: AudioPlayer
    @ObservedObject private var membership = MembershipRepository.shared
    /// Debug/CI hook: `-fbaTab N` launch argument opens on tab N (screenshot runs).
    @State private var selectedTab = UserDefaults.standard.integer(forKey: "fbaTab")
    @State private var showPlayer = false
    @State private var showLogin = false
    // One stack per tab: a talk opened from a list is pushed on top of that list, and
    // back returns to it at the same scroll position (v1.0 navigation rule).
    @State private var homePath = NavigationPath()
    @State private var explorePath = NavigationPath()
    @State private var searchPath = NavigationPath()
    @State private var myFbaPath = NavigationPath()

    /// Tabs (v1.0 design order): Home, Explore, Donate, Search, My FBA.
    enum Tab { static let home = 0, explore = 1, donate = 2, search = 3, myFba = 4 }

    static func tabLabel(_ tab: Int) -> String {
        switch tab {
        case Tab.explore: return "Explore"
        case Tab.search: return "Search"
        case Tab.myFba: return "My FBA"
        default: return "Home"
        }
    }

    /// Downloads are member-only when gating is on; non-members land on Join.
    private var canDownload: Bool { !FeatureFlags.membershipGating || membership.isMember }

    var body: some View {
        TabView(selection: tabSelection) {
            NavigationStack(path: $homePath) {
                HomeScreen(
                    onSangharakshitaByYearClick: { navigateToBrowse(.sangharakshitaByYear) },
                    onSangharakshitaSeriesClick: {
                        navigate(.list(.apiCollection("series_sangharakshita", title: "Series by Sangharakshita"), "Series by Sangharakshita"))
                    },
                    onDigitalLegacyClick: { navigate(.digitalLegacy) },
                    onCollectionsClick: { navigate(.collections) },
                    onSourceClick: { source, title in navigate(.list(source, title)) },
                    onMenuClick: { path, title in navigate(.menu(path, title)) },
                    onDonateClick: openDonate,
                    onLoginClick: { showLogin = true },
                    onAccountClick: { selectedTab = Tab.myFba },
                    onOpenUrl: { url in if let u = URL(string: url) { UIApplication.shared.open(u) } },
                    onTalkClick: { navigateToDetail($0) },
                    onSeriesClick: { navigate(.list(.seriesByCatNum($0), "")) }
                )
                .navigationDestination(for: Route.self) { route in routeView(route) }
            }
            .miniPlayerInset(player: player, isHidden: showPlayer) { showPlayer = true }
            .tabItem { Label("Home", systemImage: "house.fill") }
            .tag(Tab.home)

            NavigationStack(path: $explorePath) {
                ExploreScreen(
                    onItemClick: { openItem($0) },
                    onTalkClick: { navigateToDetail($0) },
                    onSourceClick: { source, title in navigate(.list(source, title)) },
                    onMenuClick: { path, title in navigate(.menu(path, title)) },
                    onContinueSeeAll: { selectedTab = Tab.myFba },
                    onDigitalLegacyClick: { navigate(.digitalLegacy) },
                    onDonateClick: openDonate
                )
                .navigationDestination(for: Route.self) { route in routeView(route) }
            }
            .miniPlayerInset(player: player, isHidden: showPlayer) { showPlayer = true }
            .tabItem { Label("Explore", systemImage: "square.grid.2x2.fill") }
            .tag(Tab.explore)

            // Donate opens the FBA donation page directly (see tabSelection); the tab
            // is never actually shown.
            Color.clear
                .tabItem { Label("Donate", systemImage: "heart.fill") }
                .tag(Tab.donate)

            NavigationStack(path: $searchPath) {
                SearchScreen(
                    onTalkClick: { navigateToDetail($0) },
                    onItemClick: { openItem($0) }
                )
                .navigationDestination(for: Route.self) { route in routeView(route) }
            }
            .miniPlayerInset(player: player, isHidden: showPlayer) { showPlayer = true }
            .tabItem { Label("Search", systemImage: "magnifyingglass") }
            .tag(Tab.search)

            NavigationStack(path: $myFbaPath) {
                MyFbaScreen(
                    onTalkClick: { navigateToDetail($0) },
                    onDonateClick: openDonate,
                    onLoginClick: { showLogin = true },
                    onDownloadsClick: { navigate(canDownload ? .downloads : .join) }
                )
                .navigationDestination(for: Route.self) { route in routeView(route) }
            }
            .miniPlayerInset(player: player, isHidden: showPlayer) { showPlayer = true }
            .tabItem { Label("My FBA", systemImage: "person.crop.circle.fill") }
            .tag(Tab.myFba)
        }
        .fullScreenCover(isPresented: $showPlayer) {
            PlayerScreen(
                player: player,
                onNavigateToDetail: { catNum in
                    showPlayer = false
                    navigateToDetail(catNum)
                },
                onSpeakerClick: { speaker in
                    showPlayer = false
                    navigateToBrowse(.speaker(speaker))
                },
                onSeriesClick: { href in
                    showPlayer = false
                    navigate(Route.seriesFromHref(href))
                },
                onTranscriptClick: { url, catNum in
                    showPlayer = false
                    navigate(.transcript(url, catNum))
                },
                onDonateClick: openDonate,
                onPlayingFromClick: { tab in
                    showPlayer = false
                    selectedTab = tab
                }
            )
        }
        .sheet(isPresented: $showLogin) {
            LoginScreen(onDone: { showLogin = false })
        }
        .onOpenURL { url in
            handleDeepLink(url)
        }
        // "Playing from" in the player: the section playback is started in.
        .onAppear { player.section = (Self.tabLabel(selectedTab), selectedTab) }
        .onChange(of: selectedTab) { tab in player.section = (Self.tabLabel(tab), tab) }
        .onAppear {
            // Debug/CI hook: `-fbaDeepLink <url>` opens a page at launch without the
            // "Open in FBAudio?" prompt that `simctl openurl` triggers.
            if let link = UserDefaults.standard.string(forKey: "fbaDeepLink"), let url = URL(string: link) {
                handleDeepLink(url)
            }
        }
        .alert("Delete download?", isPresented: $player.showDeleteDownloadPrompt) {
            Button("Delete", role: .destructive) { player.confirmDeleteAfterPlayback() }
            Button("Keep", role: .cancel) { player.dismissDeletePrompt() }
        } message: {
            Text("You've finished listening. Remove the offline files?")
        }
    }

    // Donate opens the web page; tapping the already-selected tab pops it to its root.
    private var tabSelection: Binding<Int> {
        Binding(
            get: { selectedTab },
            set: { newTab in
                if newTab == Tab.donate {
                    openDonate()
                    return
                }
                if newTab == selectedTab {
                    switch newTab {
                    case Tab.explore: explorePath = NavigationPath()
                    case Tab.search: searchPath = NavigationPath()
                    case Tab.myFba: myFbaPath = NavigationPath()
                    default: homePath = NavigationPath()
                    }
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
        /// A list of talks/series from any source
        case list(ContentSource, String)
        /// A section of the website's curated menu ("themes", "people", "places", …)
        case menu([String], String)
        case collections
        case digitalLegacy
        case join
        case downloads

        /// Series links from talk pages / search are hrefs; resolve them to the series list.
        static func seriesFromHref(_ href: String) -> Route {
            let path = href.hasPrefix("https://www.freebuddhistaudio.com")
                ? String(href.dropFirst("https://www.freebuddhistaudio.com".count)) : href
            if path.hasPrefix("/series/details") { return .list(.series(path), "") }
            return .browse(.series(href))
        }
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

    /// Pushes onto the current tab's stack.
    private func navigate(_ route: Route) {
        switch selectedTab {
        case Tab.explore: explorePath.append(route)
        case Tab.search: searchPath.append(route)
        case Tab.myFba: myFbaPath.append(route)
        default: homePath.append(route)
        }
    }

    private func navigateToDetail(_ catNum: String) {
        navigate(.detail(catNum))
    }

    private func navigateToBrowse(_ mode: BrowseModeRoute) {
        navigate(.browse(mode))
    }

    /// Talks → detail; series → series list; speaker/place/year tiles → their listing.
    private func openItem(_ item: SearchResult) {
        if item.isSeries {
            navigate(Route.seriesFromHref(item.path))
        } else if item.isCollection, let range = item.path.range(of: "/collection/") {
            let slug = String(item.path[range.upperBound...].prefix { $0 != "/" && $0 != "?" })
            navigate(.list(.namedCollection(slug), item.title))
        } else if item.isBrowseLink {
            let path = item.path.hasPrefix("https://www.freebuddhistaudio.com")
                ? String(item.path.dropFirst("https://www.freebuddhistaudio.com".count)) : item.path
            navigate(.list(.browse(path), item.title))
        } else {
            navigateToDetail(item.catNum)
        }
    }

    /// Menu entries → the right screen for their link type.
    private func openMenuNode(_ node: MenuNode, parentPath: [String]) {
        if node.hasChildren {
            navigate(.menu(parentPath + [node.label], node.label))
        } else if node.isExternal {
            if let url = URL(string: node.link) { UIApplication.shared.open(url) }
        } else if let catNum = node.talkCatNum {
            navigateToDetail(catNum)
        } else if let source = node.toSource() {
            navigate(.list(source, node.label))
        } else if node.link.hasPrefix("http"), let url = URL(string: node.link) {
            // Genuine site pages (about, contact, podcasts…): open the website (parity with Android).
            UIApplication.shared.open(url)
        }
    }

    // MARK: - Deep links
    // fbaudio://talk/01, fbaudio://series/X04, fbaudio://speaker/Name,
    // and https://(www.)freebuddhistaudio.com/audio/details?num=…  (parity with Android)
    private func handleDeepLink(_ url: URL) {
        selectedTab = Tab.home
        if url.scheme == "fbaudio" {
            let id = url.pathComponents.count > 1 ? url.pathComponents[1] : nil
            guard let id else { return }
            switch url.host {
            case "talk": navigateToDetail(id)
            case "series": navigate(.list(.seriesByCatNum(id), ""))
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
            if let num { navigate(.list(.seriesByCatNum(num), "")) }
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
                        if let talk = await TalkRepository.shared.getTalkDetailForPlayback(catNum) {
                            player.playTalk(talk)
                        }
                    }
                },
                onSpeakerClick: { navigateToBrowse(.speaker($0)) },
                onSeriesClick: { navigate(Route.seriesFromHref($0)) },
                onTranscriptClick: { url, catNum in
                    navigate(.transcript(url, catNum))
                },
                onDonateClick: openDonate,
                onJoinClick: { navigate(.join) },
                onPlaceClick: { slug, name in
                    let q = slug.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? slug
                    navigate(.list(.browse("/browse?p=\(q)"), name))
                },
                onYearClick: { year in navigate(.list(.browse("/browse?y=\(year)"), "Talks from \(String(year))")) },
                onTalkClick: { navigateToDetail($0) },
                onDigitalLegacyClick: { navigate(.digitalLegacy) },
                canDownload: canDownload
            )
        case .browse(let mode):
            BrowseScreen(
                initialMode: mode.toBrowseMode,
                onTalkClick: { navigateToDetail($0) },
                onSeriesSelect: { navigate(Route.seriesFromHref($0)) }
            )
        case .transcript(let url, let catNum):
            TranscriptScreen(transcriptUrl: url, catNum: catNum)
        case .list(let source, let title):
            ListScreen(source: source, initialTitle: title, onItemClick: openItem, onDonateClick: openDonate,
                       onSpeakerClick: { navigateToBrowse(.speaker($0)) },
                       onYearClick: { year in navigate(.list(.browse("/browse?y=\(year)"), "Talks from \(String(year))")) })
        case .menu(let path, let title):
            MenuListScreen(path: path, title: title, onNodeClick: { openMenuNode($0, parentPath: path) })
        case .join:
            JoinScreen(onDonateClick: openDonate)
        case .downloads:
            DownloadsScreen(onTalkClick: { navigateToDetail($0) })
        case .collections:
            CollectionsScreen(
                onCollectionClick: { openMenuNode($0, parentPath: ["collections"]) },
                onSourceClick: { source, title in navigate(.list(source, title)) },
                onMenuClick: { path, title in navigate(.menu(path, title)) }
            )
        case .digitalLegacy:
            DigitalLegacyScreen(
                onSeriesClick: { path in navigate(.list(.series(path), "")) },
                onDonateClick: openDonate
            )
        }
    }

    private func openDonate() {
        UIApplication.shared.open(donateURL)
    }
}

private extension View {
    /// Places the mini player in a reserved strip at the bottom of a tab's
    /// content — above the tab bar, not floating over it — so SwiftUI insets the
    /// tab's scroll views by the player's real height.
    func miniPlayerInset(player: AudioPlayer, isHidden: Bool, onExpand: @escaping () -> Void) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) {
            if !isHidden {
                MiniPlayer(player: player, onExpand: onExpand)
            }
        }
    }
}
