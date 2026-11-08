import SwiftUI

/// A curated collection (Start here, Themes) with where it opens.
struct ExploreCollection: Hashable { let title: String; let source: ContentSource }

/// Explore (v1.0 design): Continue listening (new listeners: Start here), New on FBA, Popular,
/// the Digital Legacy card, Latest series, Speakers, Themes, Talks in other languages, From the
/// archive, Places, Support FBA. Rows load on their own. Mirrors Android `ExploreScreen`.
struct ExploreScreen: View {
    let onItemClick: (SearchResult) -> Void
    let onTalkClick: (String) -> Void
    let onSourceClick: (ContentSource, String) -> Void
    let onMenuClick: ([String], String) -> Void
    let onContinueSeeAll: () -> Void
    let onDigitalLegacyClick: () -> Void
    let onDonateClick: () -> Void

    @State private var recent: [PersistenceManager.RecentlyListened] = []
    @State private var startHere: [ExploreCollection] = []
    @State private var newOnFba: [SearchResult] = []
    @State private var popular: [SearchResult] = []
    @State private var remastered: [SearchResult] = []
    @State private var latestSeries: [SearchResult] = []
    @State private var speakers: [SearchResult] = []
    @State private var places: [SearchResult] = []
    @State private var languages: [V2Catalogue.LanguageEntry] = []
    @State private var archiveYear: Int?
    @State private var archiveTalks: [SearchResult] = []
    @State private var archiveCount = 0
    @State private var rainbowStart = Int.random(in: 0..<10)
    @Environment(\.pageColors) private var page

    /// SPEC "Collection links": the site's main English themes, all curated Collections.
    static let themes: [ExploreCollection] = [
        ("Metta", "positive-emotion"), ("Bodhisattva Ideal", "bodhisattva-ideal"), ("Myth and Symbol", "myth-symbol"),
        ("Wisdom", "wisdom"), ("Going for Refuge", "going-for-refuge"), ("Death", "death-dying"),
        ("Friendship", "friendship-kalyana-mitrata"), ("Ethics", "ethics"), ("Tara", "tara"), ("Pure Land", "pureland"),
    ].map { ExploreCollection(title: $0.0, source: .namedCollection($0.1)) }

    static let languageOrder = ["hi", "de", "es", "fr", "no", "nl", "pl", "pt", "ru", "fi", "sv"]

    private var continueListening: [PersistenceManager.RecentlyListened] {
        recent.filter { r in
            r.positionMs >= TalkProgress.startedAfterMs &&
                (r.totalDurationSeconds <= 0 || Int(r.positionMs / 1000) < r.totalDurationSeconds - TalkProgress.finishedWithinSeconds)
        }.prefix(6).map { $0 }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                Text("Explore the archive").font(.largeTitle.weight(.bold)).foregroundStyle(FbaColors.black)
                    .padding(.horizontal, FbaDimens.margin).padding(.top, 8)
                if recent.isEmpty {
                    if !startHere.isEmpty {
                        row("Start here", subtitle: "A few good places to begin.") {
                            ForEach(Array(startHere.enumerated()), id: \.offset) { i, c in
                                Button { onSourceClick(c.source, c.title) } label: {
                                    VStack(alignment: .leading, spacing: 6) {
                                        TileArtwork(colors: TileArt.rainbowAt(i, start: rainbowStart), symbol: .lotus)
                                            .frame(width: 200, height: 134).clipShape(RoundedRectangle(cornerRadius: 12))
                                        cardText(c.title, "")
                                    }.frame(width: 200)
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                } else if !continueListening.isEmpty {
                    row("Continue listening", onSeeAll: onContinueSeeAll) {
                        ForEach(continueListening) { r in
                            Button { onTalkClick(r.catNum) } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    Artwork(url: r.imageUrl, aspect: 200.0 / 134.0).frame(width: 200, height: 134)
                                        .clipShape(RoundedRectangle(cornerRadius: 12))
                                    if r.totalDurationSeconds > 0 {
                                        ProgressView(value: min(Double(r.positionMs) / 1000 / Double(r.totalDurationSeconds), 1))
                                            .tint(page.deep)
                                    }
                                    cardText(r.title, PlaybackMath.timeLeftLabel(max(r.totalDurationSeconds - Int(r.positionMs / 1000), 0)) ?? r.speaker)
                                }.frame(width: 200)
                            }.buttonStyle(.plain)
                        }
                    }
                }
                if !newOnFba.isEmpty {
                    let open = { onSourceClick(.apiCollection("latest", title: "New on FBA"), "New on FBA") }
                    row("New on FBA", subtitle: "Single talks. Series are below.", onSeeAll: open) {
                        ForEach(newOnFba) { talkCard($0, size: 150) }
                        seeAllTile(150, action: open)
                    }
                }
                if !popular.isEmpty {
                    let open = { onSourceClick(.apiCollection("popular", title: "Popular"), "Popular") }
                    row("Popular", subtitle: "Most listened to on FBA.", onSeeAll: open) {
                        ForEach(popular) { talkCard($0, size: 150) }
                        seeAllTile(150, action: open)
                    }
                }
                if !remastered.isEmpty { digitalLegacyCard }
                if !latestSeries.isEmpty {
                    let open = { onSourceClick(.apiCollection("all_series", title: "Series"), "Series") }
                    row("Latest series", onSeeAll: open) {
                        ForEach(latestSeries) { talkCard($0, size: 108) }
                        seeAllTile(108, label: "All series", action: open)
                    }
                }
                if !speakers.isEmpty {
                    row("Speakers", onSeeAll: { onSourceClick(.apiCollection("speakers", title: "All speakers"), "All speakers") }) {
                        ForEach(speakers) { sp in
                            Button { onItemClick(sp) } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    // Rounded squares, never circles.
                                    Artwork(url: sp.imageUrl).frame(width: 108, height: 108).clipShape(RoundedRectangle(cornerRadius: 12))
                                    Text(sp.title).font(.subheadline).foregroundStyle(FbaColors.black).lineLimit(1)
                                }.frame(width: 108)
                            }.buttonStyle(.plain)
                        }
                    }
                }
                section("Themes", onSeeAll: { onMenuClick(["themes"], "Themes") }) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        LazyHGrid(rows: [GridItem(.fixed(100), spacing: 10), GridItem(.fixed(100))], spacing: 10) {
                            ForEach(Array(Self.themes.enumerated()), id: \.offset) { i, c in
                                Button { onSourceClick(c.source, c.title) } label: {
                                    TileArtwork(colors: TileArt.symbolSafeAt(i, start: rainbowStart), symbol: .wheel,
                                                wash: TileArt.washes[i % TileArt.washes.count], title: c.title)
                                        .frame(width: 140, height: 100).clipShape(RoundedRectangle(cornerRadius: 14))
                                }.buttonStyle(.plain).accessibilityLabel(c.title)
                            }
                        }
                        .padding(.horizontal, FbaDimens.margin)
                    }
                }
                if !languages.isEmpty {
                    section("Talks in other languages") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 10) {
                                ForEach(languages, id: \.code) { l in
                                    Button { onSourceClick(.browse("/browse?lang=\(l.code)"), l.nativeName) } label: {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(l.nativeName).font(.headline.weight(.bold)).foregroundStyle(FbaColors.black).lineLimit(1)
                                            Text(l.name).font(.footnote).foregroundStyle(FbaColors.meta).lineLimit(1)
                                        }
                                        .frame(width: 92, alignment: .leading)
                                        .padding(.horizontal, 14).padding(.vertical, 12)
                                        .background(FbaColors.circle, in: RoundedRectangle(cornerRadius: 12))
                                    }.buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, FbaDimens.margin)
                        }
                        Text("Choose which languages appear across the app in Settings, Talk languages.")
                            .font(.footnote).foregroundStyle(FbaColors.meta).padding(.horizontal, FbaDimens.margin).padding(.top, 8)
                    }
                }
                if let year = archiveYear { archive(year) }
                if !places.isEmpty {
                    row("Places", onSeeAll: { onSourceClick(.apiCollection("places", title: "All places"), "All places") }) {
                        ForEach(places) { p in
                            Button { onItemClick(p) } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    Artwork(url: p.imageUrl).frame(width: 132, height: 132).clipShape(RoundedRectangle(cornerRadius: 12))
                                    Text(p.title).font(.subheadline).foregroundStyle(FbaColors.black).lineLimit(1)
                                }.frame(width: 132)
                            }.buttonStyle(.plain)
                        }
                    }
                }
                SupportFbaButton(action: onDonateClick).padding(.horizontal, FbaDimens.margin)
            }
            .padding(.bottom, 24)
        }
        .miniPlayerClearance()
        .navigationBarHidden(true)
        .onAppear { recent = PersistenceManager.shared.getRecentlyListened() }
        .task { await load() }
    }

    // MARK: - Data

    private func load() async {
        let repo = ContentRepository.shared
        func talks(_ p: ListPage?) -> [SearchResult] { (p?.items ?? []).filter { !$0.isSeries && !$0.isCollection && !$0.isBrowseLink } }
        async let latest = try? repo.getPage(.apiCollection("latest", title: ""), page: 1)
        async let pop = try? repo.getPage(.apiCollection("popular", title: ""), page: 1)
        async let series = try? repo.getPage(.apiCollection("series_latest", title: ""), page: 1)
        async let legacy = try? repo.getPage(.seriesByCatNum("X16"), page: 1)
        async let sections = repo.getHomeSections()
        async let langs = repo.getLanguages()
        newOnFba = Array(talks(await latest).prefix(10))
        popular = Array(talks(await pop).prefix(10))
        latestSeries = Array(((await series)?.items ?? []).prefix(10))
        remastered = Array(((await legacy)?.items ?? []).prefix(6))
        let s = await sections
        speakers = Array((s["speakers"] ?? []).prefix(10))
        places = Array((s["places"] ?? []).prefix(10))
        languages = (await langs).filter { $0.code != "en" }.sorted {
            (Self.languageOrder.firstIndex(of: $0.code) ?? 99) < (Self.languageOrder.firstIndex(of: $1.code) ?? 99)
        }
        if let menu = try? await repo.getMenu() {
            let groups = SiteMenuParser.section(menu, "collections")?.children.dropFirst().prefix(6) ?? []
            startHere = groups.flatMap { $0.hasChildren ? $0.children : [$0] }
                .compactMap { n in n.collectionSlug.map { ExploreCollection(title: capitalizedFirst(n.label), source: .namedCollection($0)) } }
                .shuffled().prefix(6).map { $0 }
        }
        // From the archive: a different year each visit, with enough talks to show.
        let years = await repo.getIndexEntries("years").compactMap { Int($0.title.trimmingCharacters(in: .whitespaces)) }
            .filter { (1965...2019).contains($0) }.shuffled().prefix(4)
        for (i, year) in years.enumerated() {
            guard let p = try? await repo.getPage(.browse("/browse?y=\(year)"), page: 1) else { continue }
            if p.totalItems >= 8 || i == years.count - 1 {
                archiveYear = year; archiveTalks = Array(talks(p).prefix(4)); archiveCount = p.totalItems
                break
            }
        }
    }

    // MARK: - Pieces

    private func section<Content: View>(_ title: String, subtitle: String? = nil, seeAll: String = "See all",
                                        onSeeAll: (() -> Void)? = nil, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(title).font(.title3.weight(.bold)).foregroundStyle(FbaColors.black)
                    Spacer()
                    if let onSeeAll { Button(seeAll, action: onSeeAll).font(.subheadline.weight(.medium)).foregroundStyle(page.deep) }
                }
                if let subtitle { Text(subtitle).font(.footnote).foregroundStyle(FbaColors.meta) }
            }
            .padding(.horizontal, FbaDimens.margin)
            content()
        }
    }

    private func row<Content: View>(_ title: String, subtitle: String? = nil, onSeeAll: (() -> Void)? = nil,
                                    @ViewBuilder content: () -> Content) -> some View {
        section(title, subtitle: subtitle, onSeeAll: onSeeAll) {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) { content() }.padding(.horizontal, FbaDimens.margin)
            }
        }
    }

    private func talkCard(_ item: SearchResult, size: CGFloat) -> some View {
        Button { onItemClick(item) } label: {
            VStack(alignment: .leading, spacing: 6) {
                Artwork(url: item.imageUrl).frame(width: size, height: size).clipShape(RoundedRectangle(cornerRadius: 12))
                cardText(item.title, item.speaker)
            }.frame(width: size)
        }.buttonStyle(.plain)
    }

    private func cardText(_ title: String, _ sub: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.subheadline.weight(.medium)).foregroundStyle(FbaColors.black).lineLimit(2).multilineTextAlignment(.leading)
            if !sub.isEmpty { Text(sub).font(.footnote).foregroundStyle(FbaColors.meta).lineLimit(1) }
        }
    }

    private func seeAllTile(_ size: CGFloat, label: String = "See all", action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.subheadline.weight(.medium)).foregroundStyle(page.deep)
                .frame(width: size, height: size).background(FbaColors.circle, in: RoundedRectangle(cornerRadius: 12))
        }.buttonStyle(.plain)
    }

    private func archive(_ year: Int) -> some View {
        let open = { onSourceClick(.browse("/browse?y=\(year)"), "Talks from \(String(year))") }
        return section("From the archive: \(String(year))", subtitle: "A different year each visit.", seeAll: "All years",
                       onSeeAll: { onSourceClick(.apiCollection("years", title: "All years"), "All years") }) {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) {
                    Button(action: open) {
                        VStack(alignment: .leading, spacing: 6) {
                            ZStack {
                                TileArtwork(colors: TileArt.rainbowAt(0, start: rainbowStart), wash: TileArt.Wash(x: 1.05, y: 1.05))
                                Text(String(year)).font(.system(size: 56, weight: .black)).foregroundStyle(.white.opacity(0.32))
                            }
                            .frame(width: 150, height: 150).clipShape(RoundedRectangle(cornerRadius: 12))
                            cardText("All of \(String(year))", "\(archiveCount) talks")
                        }.frame(width: 150)
                    }.buttonStyle(.plain)
                    ForEach(archiveTalks) { talkCard($0, size: 150) }
                }
                .padding(.horizontal, FbaDimens.margin)
            }
            Button("All \(archiveCount) talks from \(String(year))", action: open)
                .font(.subheadline.weight(.medium)).foregroundStyle(page.deep).padding(.horizontal, FbaDimens.margin).padding(.top, 8)
        }
    }

    private var digitalLegacyCard: some View {
        let gold = FbaColors.gold
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                BundleImage(name: "dharmachakra_gold").frame(width: 20, height: 20)
                Text("The Digital Legacy").font(.title3.weight(.bold)).foregroundStyle(FbaColors.black)
                Spacer()
                Button("See all", action: onDigitalLegacyClick).font(.subheadline.weight(.medium)).foregroundStyle(gold.deep)
            }
            .padding([.horizontal, .top], 16)
            Text("Sangharakshita's talks, digitally remastered so they can be heard clearly.")
                .font(.subheadline).foregroundStyle(FbaColors.meta).padding(.horizontal, 16).padding(.vertical, 6)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(remastered) { t in
                        Button { onItemClick(t) } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                Artwork(url: t.imageUrl, aspect: 170.0 / 112.0).frame(width: 170, height: 112)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                cardText(t.title, [t.speaker.isEmpty ? nil : t.speaker, t.year > 0 ? String(t.year) : nil]
                                            .compactMap { $0 }.joined(separator: " · "))
                            }.frame(width: 170)
                        }.buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 8)
            }
            VStack(alignment: .leading, spacing: 10) {
                Text("Help keep Sangharakshita's voice clear").font(.title3.weight(.bold)).foregroundStyle(.white)
                Text("Each remastered talk takes a lot of time and care. It takes up to 10 hours to remaster a single lecture and get it to sound perfect! Your gift helps the Digital Legacy restore more of the archive for everyone to hear, always for free.")
                    .font(.subheadline).foregroundStyle(.white)
                HStack(spacing: 12) {
                    Button(action: onDonateClick) {
                        Text("Donate").font(.subheadline.weight(.bold)).foregroundStyle(gold.deep)
                            .padding(.horizontal, 20).padding(.vertical, 14).background(.white, in: Capsule())
                    }.buttonStyle(.plain)
                    Button("Learn more", action: onDigitalLegacyClick).font(.subheadline.weight(.medium)).foregroundStyle(.white)
                }
            }
            .padding(20).frame(maxWidth: .infinity, alignment: .leading).background(gold.base)
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(FbaColors.gold.base, lineWidth: 1.5))
        .padding(.horizontal, FbaDimens.margin)
    }
}
