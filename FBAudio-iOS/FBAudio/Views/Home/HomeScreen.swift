import SwiftUI

/// External "Connect" links shown as a row of chips on Home.
private struct ConnectLink: Identifiable {
    let label: String
    let url: String
    let icon: String
    var id: String { label }
}

private let connectLinks: [ConnectLink] = [
    ConnectLink(label: "FBA podcast", url: "https://www.freebuddhistaudio.com/community/podcasts", icon: "antenna.radiowaves.left.and.right"),
    ConnectLink(label: "Dharmabytes", url: "https://www.freebuddhistaudio.com/community/podcasts", icon: "headphones"),
    ConnectLink(label: "YouTube", url: "https://youtube.com/freebuddhistaudio1967", icon: "play.rectangle"),
    ConnectLink(label: "Facebook", url: "https://www.facebook.com/pages/Free-Buddhist-Audio/79854346331", icon: "globe"),
    ConnectLink(label: "Instagram", url: "https://www.instagram.com/freebuddhistaudio/", icon: "camera"),
    ConnectLink(label: "SoundCloud", url: "https://soundcloud.com/freebuddhistaudio", icon: "waveform"),
    ConnectLink(label: "The Buddhist Centre", url: "https://thebuddhistcentre.com/", icon: "network"),
]

struct HomeScreen: View {
    @ObservedObject private var auth = AuthRepository.shared
    @State private var digitalLegacy: DigitalLegacy?

    let onSangharakshitaByYearClick: () -> Void
    let onSangharakshitaSeriesClick: () -> Void
    let onDigitalLegacyClick: () -> Void
    let onCollectionsClick: () -> Void
    let onSourceClick: (ContentSource, String) -> Void
    let onMenuClick: ([String], String) -> Void
    let onDonateClick: () -> Void
    let onLoginClick: () -> Void
    /// Logged in: the header shows the account name and opens My FBA.
    let onAccountClick: () -> Void
    let onOpenUrl: (String) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                header
                sangharakshitaSection
                digitalLegacyCard
                collectionsCard
                Button(action: onDonateClick) {
                    Text("Support FBA").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.saffronOrange)
                .padding(.horizontal, 16)
                .padding(.top, 8)
                connectRow
            }
            .padding(.bottom, 24)
        }
        .miniPlayerClearance()
        .navigationBarTitleDisplayMode(.inline)
        .task {
            // Warm the menu cache so Collections/Themes/People/Places open instantly.
            _ = try? await ContentRepository.shared.getMenu()
            digitalLegacy = await ContentRepository.shared.getDigitalLegacy()
        }
    }

    // MARK: - Header: logo + name + log in/out

    private var header: some View {
        HStack(spacing: 12) {
            BundleImage(name: "fba_wordmark")
                .aspectRatio(contentMode: .fit)
                .frame(height: 32)
            Text("Free Buddhist Audio").font(.headline)
            Spacer()
            if FeatureFlags.auth {
                Button(auth.state.loggedIn ? (auth.state.username.isEmpty ? "My account" : auth.state.username) : "Log in",
                       action: { if auth.state.loggedIn { onAccountClick() } else { onLoginClick() } })
                    .font(.subheadline)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    // MARK: - Sangharakshita

    private var sangharakshitaSection: some View {
        VStack(spacing: 0) {
            BundleImage(name: "sangharakshita")
                .aspectRatio(contentMode: .fill)
                .frame(maxWidth: .infinity)
                .aspectRatio(16.0/9.0, contentMode: .fit)
                .clipped()

            VStack(alignment: .leading, spacing: 4) {
                Text("Sangharakshita").font(.title2).bold()
                Text("\(SharedDataLoader.sangharakshitaTalks.count) talks · \(SharedDataLoader.sangharakshitaSeries.count) series")
                    .font(.caption).foregroundStyle(.secondary)
                Divider().padding(.vertical, 8)
                linkRow("Year", action: onSangharakshitaByYearClick)
                Divider().padding(.vertical, 4)
                linkRow("Series", action: onSangharakshitaSeriesClick)
            }
            .padding(16)
        }
        .background(Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 16)
    }

    private func linkRow(_ title: String, subtitle: String? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading) {
                    Text(title).font(.body).foregroundStyle(.primary)
                    if let subtitle {
                        Text(subtitle).font(.caption).foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.secondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Digital Legacy

    private var digitalLegacyCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("The Digital Legacy")
                .font(.headline)
                .foregroundStyle(Color(red: 219/255, green: 175/255, blue: 85/255))
            Text(digitalLegacy?.description
                 ?? "Digitally remastered talks — hear the Dharma renewed for future generations.")
                .font(.caption)
                .foregroundStyle(Color(red: 237/255, green: 224/255, blue: 216/255))
                .lineLimit(4)
            HStack {
                Button(action: onDonateClick) { Text("Support the Digital Legacy") }
                    .buttonStyle(.borderedProminent)
                    .tint(.saffronOrange)
                Spacer()
                Button("Learn more", action: onDigitalLegacyClick)
                    .foregroundStyle(Color(red: 219/255, green: 175/255, blue: 85/255))
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(red: 43/255, green: 33/255, blue: 23/255))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 16)
        .onTapGesture(perform: onDigitalLegacyClick)
    }

    // MARK: - Collections

    private var collectionsCard: some View {
        Button(action: onCollectionsClick) {
            HStack(spacing: 12) {
                Image(systemName: "square.grid.2x2.fill").font(.title).foregroundStyle(.white)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Collections").font(.headline).foregroundStyle(.white)
                    Text("Introductions · Meditations · Latest · Themes · Series · People · Places")
                        .font(.caption).foregroundStyle(.white.opacity(0.9)).lineLimit(2)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.white)
            }
            .padding(16)
            .background(LinearGradient(colors: [.saffronOrange, .deepSaffron], startPoint: .leading, endPoint: .trailing))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
    }

    // MARK: - Connect

    private var connectRow: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Connect").font(.subheadline).bold().padding(.horizontal, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(connectLinks) { link in
                        Button(action: { onOpenUrl(link.url) }) {
                            Label(link.label, systemImage: link.icon)
                                .font(.caption)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(Color(.secondarySystemGroupedBackground))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
        .padding(.top, 12)
    }
}
