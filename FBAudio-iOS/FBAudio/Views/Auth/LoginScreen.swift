import SwiftUI

/// Native login with the FBA / Triratna account (the single sign-on runs behind the scenes).
struct LoginScreen: View {
    let onDone: () -> Void

    @State private var username = ""
    @State private var password = ""
    @State private var showPassword = false
    @State private var isLoading = false
    @State private var error: String?
    @FocusState private var focused: Field?

    private enum Field { case username, password }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Your FBA account").font(.title2).bold()
                    Text("Free Buddhist Audio uses the Triratna login — the same username and password as The Buddhist Centre Online.")
                        .font(.subheadline).foregroundStyle(.secondary)

                    TextField("Username", text: $username)
                        .textContentType(.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.next)
                        .focused($focused, equals: .username)
                        .onSubmit { focused = .password }
                        .textFieldStyle(.roundedBorder)
                        .padding(.top, 12)

                    HStack {
                        Group {
                            if showPassword {
                                TextField("Password", text: $password)
                            } else {
                                SecureField("Password", text: $password)
                            }
                        }
                        .textContentType(.password)
                        .submitLabel(.go)
                        .focused($focused, equals: .password)
                        .onSubmit(submit)
                        Button(action: { showPassword.toggle() }) {
                            Image(systemName: showPassword ? "eye.slash" : "eye").foregroundStyle(.secondary)
                        }
                    }
                    .textFieldStyle(.roundedBorder)

                    if let error {
                        Text(error).font(.caption).foregroundStyle(.red)
                    }

                    Button(action: submit) {
                        Group {
                            if isLoading { ProgressView().tint(.white) } else { Text("Log in") }
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isLoading)
                    .padding(.top, 8)

                    HStack {
                        Link("Forgot your details?", destination: URL(string: "https://thebuddhistcentre.com/user/password")!)
                        Spacer()
                        Link("Create an account", destination: URL(string: "https://thebuddhistcentre.com/register")!)
                    }
                    .font(.footnote)
                    .padding(.top, 8)
                }
                .padding(24)
            }
            .tint(.saffronOrange)
            .navigationTitle("Log in")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: onDone) }
            }
            .onAppear { focused = .username }
        }
    }

    private func submit() {
        guard !isLoading else { return }
        isLoading = true
        error = nil
        Task {
            let result = await AuthRepository.shared.login(username: username, password: password)
            isLoading = false
            if let result { error = result } else { onDone() }
        }
    }
}
