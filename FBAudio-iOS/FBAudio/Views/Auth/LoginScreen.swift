import SwiftUI
import WebKit

/// Logs in with the FBA / Triratna account using the website's own login page.
/// The site redirects to sso.triratna.co and back; once it lands on
/// freebuddhistaudio.com with a complete session we copy the cookies into the
/// app and close.
struct LoginScreen: View {
    let onDone: () -> Void

    @State private var loading = true
    @State private var finishing = false
    @State private var failed = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if loading { ProgressView().progressViewStyle(.linear) }
                if failed {
                    Text("Couldn't complete the login. Please try again.")
                        .font(.caption).foregroundStyle(.red).padding()
                }
                ZStack {
                    LoginWebView(
                        onLoadingChanged: { loading = $0 },
                        onSessionCookies: { cookies in
                            guard !finishing else { return }
                            finishing = true
                            Task {
                                let ok = await AuthRepository.shared.completeLoginFromWebView(cookies)
                                if ok { onDone() } else { failed = true; finishing = false }
                            }
                        }
                    )
                    if finishing { ProgressView().controlSize(.large) }
                }
            }
            .navigationTitle("Log in to FBA")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: onDone) }
            }
        }
    }
}

private struct LoginWebView: UIViewRepresentable {
    let onLoadingChanged: (Bool) -> Void
    let onSessionCookies: ([String: String]) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        // Non-persistent store: start from a clean browser session so the SSO
        // form shows rather than silently reusing a previous account.
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.load(URLRequest(url: AuthRepository.loginURL))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        let parent: LoginWebView
        init(_ parent: LoginWebView) { self.parent = parent }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            parent.onLoadingChanged(true)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.onLoadingChanged(false)
            guard let url = webView.url, url.host == AuthRepository.host, !url.path.hasPrefix("/sso") else { return }
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
                var map: [String: String] = [:]
                for c in cookies where c.domain.hasSuffix("freebuddhistaudio.com") && AuthRepository.sessionCookies.contains(c.name) {
                    map[c.name] = c.value
                }
                if AuthRepository.isCompleteSession(map) {
                    self.parent.onSessionCookies(map)
                }
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            parent.onLoadingChanged(false)
        }
    }
}
