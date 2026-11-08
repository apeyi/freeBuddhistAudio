import SwiftUI
import UIKit

/// Connect (SPEC Home): six logos in light grey circles, spread full width.
private enum ConnectIcon {
    case paths([String]), mask(String)
}

private struct ConnectLink: Identifiable {
    let label: String
    let url: String
    let icon: ConnectIcon
    var id: String { label }
}

private let connectLinks: [ConnectLink] = [
    ConnectLink(label: "The FBA Podcast", url: "https://www.freebuddhistaudio.com/community/podcasts", icon: .paths(ConnectIconPaths.podcast)),
    ConnectLink(label: "The Buddhist Centre", url: "https://thebuddhistcentre.com/", icon: .mask("connect_tbco")),
    ConnectLink(label: "YouTube", url: "https://youtube.com/freebuddhistaudio1967", icon: .paths(ConnectIconPaths.youtube)),
    ConnectLink(label: "Facebook", url: "https://www.facebook.com/pages/Free-Buddhist-Audio/79854346331", icon: .mask("connect_facebook")),
    ConnectLink(label: "Instagram", url: "https://www.instagram.com/freebuddhistaudio/", icon: .paths(ConnectIconPaths.instagram)),
    ConnectLink(label: "SoundCloud", url: "https://soundcloud.com/freebuddhistaudio", icon: .mask("connect_soundcloud")),
]

/// Home (v1.0 design): header with "sign in", the featured carousel, the Sangharakshita and
/// Digital Legacy card, Collections tiles with generated artwork, Connect and Support FBA.
/// Mirrors Android `HomeScreen`.
struct HomeScreen: View {
    @Environment(\.pageColors) private var page
    @ObservedObject private var auth = AuthRepository.shared
    @State private var digitalLegacy: DigitalLegacy?
    @State private var featured: [Featured] = []
    @State private var featuredIndex = 0

    let onSangharakshitaByYearClick: () -> Void
    let onSangharakshitaSeriesClick: () -> Void
    let onDigitalLegacyClick: () -> Void
    let onCollectionsClick: () -> Void
    let onSourceClick: (ContentSource, String) -> Void
    let onMenuClick: ([String], String) -> Void
    let onDonateClick: () -> Void
    let onLoginClick: () -> Void
    /// Signed in: the header shows the account name and opens My FBA.
    let onAccountClick: () -> Void
    let onOpenUrl: (String) -> Void
    var onTalkClick: (String) -> Void = { _ in }
    var onSeriesClick: (String) -> Void = { _ in }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                header
                if !featured.isEmpty { carousel }
                sangharakshitaCard
                collections
                connect
                SupportFbaButton(action: onDonateClick)
            }
            .padding(.horizontal, FbaDimens.margin)
            .padding(.bottom, 24)
        }
        .miniPlayerClearance()
        .navigationBarHidden(true)
        .task {
            async let legacy = ContentRepository.shared.getDigitalLegacy()
            async let posts = ContentRepository.shared.getFeatured()
            digitalLegacy = await legacy
            if featured.isEmpty { featured = await posts.shuffled() }   // random order, like the website
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: 12) {
            BundleImage(name: "fba_wordmark").scaledToFit().frame(height: 21)
            Text("free buddhist audio").font(.title3.weight(.medium)).foregroundStyle(FbaColors.black)
                .accessibilityLabel("Free Buddhist Audio")
            Spacer()
            if FeatureFlags.auth {
                Button(auth.state.loggedIn ? (auth.state.username.isEmpty ? "My account" : auth.state.username) : "sign in",
                       action: auth.state.loggedIn ? onAccountClick : onLoginClick)
                    .font(.headline.weight(.medium))
                    .foregroundStyle(page.deep)
                    .lineLimit(1)
            }
        }
        .padding(.top, 12)
    }

    // MARK: - Featured carousel

    /// 290 x 360 cards, about 80pt of the next one showing as the cue to swipe; grey dots.
    private var carousel: some View {
        VStack(spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(Array(featured.enumerated()), id: \.element.id) { index, post in
                        Button { open(post) } label: { featuredCard(post, index: index) }
                            .buttonStyle(.plain)
                            .id(index)
                            .onAppear { featuredIndex = index }
                    }
                }
                .padding(.vertical, 6)
                .padding(.horizontal, FbaDimens.margin)
            }
            .padding(.horizontal, -FbaDimens.margin)
            HStack(spacing: 8) {
                ForEach(featured.indices, id: \.self) { i in
                    Circle().fill(i == featuredIndex ? FbaColors.grey700 : FbaColors.grey500.opacity(0.5))
                        .frame(width: i == featuredIndex ? 9 : 7, height: i == featuredIndex ? 9 : 7)
                }
            }
            .frame(maxWidth: .infinity)
            .accessibilityHidden(true)
        }
    }

    private func featuredCard(_ post: Featured, index: Int) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Artwork(url: post.imageUrl, aspect: 290.0 / 200.0).frame(width: 290, height: 200)
            VStack(alignment: .leading, spacing: 4) {
                Text("FEATURED").font(.caption2.weight(.bold)).tracking(0.8).foregroundStyle(.white.opacity(0.85))
                Text(post.title).font(.title3.weight(.bold)).foregroundStyle(.white).lineLimit(4)
                    .multilineTextAlignment(.leading)
            }
            .padding(16)
            .frame(width: 290, height: 160, alignment: .topLeading)
        }
        .background(Color(hex: FeaturedLink.band(index)))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.15), radius: 6, y: 3)
    }

    private func open(_ post: Featured) {
        switch FeaturedLink.target(post.webUrl) {
        case .talk(let cat): onTalkClick(cat)
        case .series(let cat): onSeriesClick(cat)
        case .digitalLegacy: onDigitalLegacyClick()
        case .web(let url): onOpenUrl(url)
        }
    }

    // MARK: - Sangharakshita + Digital Legacy

    private var sangharakshitaCard: some View {
        let gold = FbaColors.gold
        return VStack(alignment: .leading, spacing: 0) {
            // Color.clear sets the size; the photo fills it as an overlay (a scaledToFill image as
            // the layout child would widen the card past the screen).
            Color.clear.frame(height: 240)
                .overlay { BundleImage(name: "sangharakshita").scaledToFill() }
                .clipped()
                .accessibilityLabel("Sangharakshita")
            VStack(alignment: .leading, spacing: 2) {
                Text("Sangharakshita").font(.title.weight(.bold)).foregroundStyle(FbaColors.black)
                Text("\(SharedDataLoader.sangharakshitaTalks.count) talks · \(SharedDataLoader.sangharakshitaSeries.count) series")
                    .font(.subheadline).foregroundStyle(FbaColors.meta)
            }
            .padding(16)
            FbaColors.divider.frame(height: 1)
            cardRow("Year", action: onSangharakshitaByYearClick)
            FbaColors.divider.frame(height: 1)
            cardRow("Series", action: onSangharakshitaSeriesClick)
            VStack(alignment: .leading, spacing: 10) {
                Text("The Digital Legacy").font(.title3.weight(.bold)).foregroundStyle(.white)
                Text(digitalLegacy?.description ?? "Since 1967, the team at Dharmachakra has been sharing Urgyen Sangharakshita's Dharma talks with the world.")
                    .font(.subheadline).foregroundStyle(.white).lineLimit(4)
                HStack(spacing: 12) {
                    Button(action: onDonateClick) {
                        Text("Support the Digital Legacy").font(.subheadline.weight(.bold)).foregroundStyle(gold.deep)
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .background(.white, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    Button("Learn more", action: onDigitalLegacyClick).font(.subheadline.weight(.medium)).foregroundStyle(.white)
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(gold.base)
        }
        .background(FbaColors.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(FbaColors.gold.base, lineWidth: 1.5))
    }

    private func cardRow(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label).font(.headline.weight(.regular)).foregroundStyle(FbaColors.black)
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(FbaColors.grey700)
            }
            .padding(.horizontal, 16).padding(.vertical, 15)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Collections

    private var collections: some View {
        let tiles = TileArt.homeTiles.filter { !$0.orderOnly || auth.state.isOrderMember }
        return VStack(alignment: .leading, spacing: 14) {
            Text("Collections").font(.title.weight(.bold)).foregroundStyle(FbaColors.black)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 14) {
                ForEach(tiles, id: \.title) { tile in
                    Button { openTile(tile) } label: {
                        TileArtwork(colors: tile.colors, symbol: tile.symbol, wash: tile.wash, title: tile.title,
                                    caption: tile.orderOnly ? "Order members only" : nil)
                            .aspectRatio(174.0 / 104.0, contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: FbaDimens.radiusTile))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(tile.title)
                }
            }
        }
    }

    private func openTile(_ tile: TileArt.HomeTile) {
        switch tile.title {
        case "Introducing Buddhism": onMenuClick(["collections", "introducing buddhism"], tile.title)
        case "Meditation & mindfulness": onMenuClick(["collections", "meditation & mindfulness"], tile.title)
        case "Latest talks & meditations": onSourceClick(.apiCollection("latest", title: tile.title), tile.title)
        case "Themes": onMenuClick(["themes"], tile.title)
        case "Series": onSourceClick(.apiCollection("all_series", title: tile.title), tile.title)
        case "People": onMenuClick(["people"], tile.title)
        case "Places": onMenuClick(["places"], tile.title)
        case "Order talks": onSourceClick(.apiCollection("order_talks", title: tile.title), tile.title)
        default: break
        }
    }

    // MARK: - Connect

    private var connect: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Connect").font(.title3.weight(.bold)).foregroundStyle(FbaColors.black)
            HStack {
                ForEach(connectLinks) { link in
                    Button { onOpenUrl(link.url) } label: {
                        Group {
                            switch link.icon {
                            case .paths(let p): SVGPathShape(paths: p, viewBox: CGSize(width: 24, height: 24)).fill(FbaColors.meta)
                            case .mask(let name): TemplateImage(name: name).foregroundStyle(FbaColors.meta)
                            }
                        }
                        .frame(width: 24, height: 24)
                        .frame(width: 48, height: 48)
                        .background(FbaColors.circle, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(link.label)
                    if link.id != connectLinks.last?.id { Spacer(minLength: 0) }
                }
            }
        }
    }
}

/// A shared bundle image drawn as a template (tinted with the foreground style).
struct TemplateImage: View {
    let name: String
    var body: some View {
        let url = Bundle.main.url(forResource: name, withExtension: "png", subdirectory: "fbaudio-shared/images")
            ?? Bundle.main.url(forResource: name, withExtension: "png")
        if let url, let data = try? Data(contentsOf: url), let image = UIImage(data: data, scale: 2) {
            Image(uiImage: image.withRenderingMode(.alwaysTemplate)).resizable().scaledToFit()
        } else {
            Color.clear
        }
    }
}
