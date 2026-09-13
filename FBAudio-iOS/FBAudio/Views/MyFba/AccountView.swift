import SwiftUI

/// Full account details, pushed from the account card on My FBA. Shows the name
/// and email in full and links out to the Triratna account page to change the
/// password — the login is an external single sign-on, so the app can't change
/// the password itself.
struct AccountView: View {
    @ObservedObject private var auth = AuthRepository.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    private let passwordURL = URL(string: "https://thebuddhistcentre.com/user/password")!

    var body: some View {
        List {
            Section {
                VStack(spacing: 8) {
                    if !auth.state.avatarUrl.isEmpty, let url = URL(string: auth.state.avatarUrl) {
                        AsyncImage(url: url) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: { Color.gray.opacity(0.2) }
                        .frame(width: 88, height: 88)
                        .clipShape(Circle())
                    } else {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.system(size: 80))
                            .foregroundStyle(.secondary)
                    }
                    Text(auth.state.username.isEmpty ? "Logged in" : auth.state.username)
                        .font(.title2).multilineTextAlignment(.center)
                    if !auth.state.email.isEmpty {
                        Text(auth.state.email)
                            .font(.subheadline).foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    Text(auth.state.isOrderMember ? "Order member" : "FBA account")
                        .font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            }

            Section {
                Button { openURL(passwordURL) } label: {
                    HStack {
                        Label("Change password", systemImage: "lock")
                        Spacer()
                        Image(systemName: "arrow.up.right.square").foregroundStyle(.secondary)
                    }
                }
            } footer: {
                Text("Opens your Triratna account on thebuddhistcentre.com.")
            }

            Section {
                Button(role: .destructive) {
                    Task { await auth.logout(); dismiss() }
                } label: {
                    Label("Log out", systemImage: "rectangle.portrait.and.arrow.right")
                }
            }
        }
        .tint(.saffronOrange)
        .navigationTitle("Account")
        .navigationBarTitleDisplayMode(.inline)
    }
}
