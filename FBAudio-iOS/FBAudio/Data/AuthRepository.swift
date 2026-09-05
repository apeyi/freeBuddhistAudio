import Foundation
import Combine

struct AuthState: Equatable {
    var loggedIn = false
    var username = ""
    var avatarUrl = ""
    var isOrderMember = false
    /// True while the stored session is being verified at startup.
    var checking = false
}

/// Login with the existing FBA (Triratna single sign-on) account. `SsoLogin`
/// runs the website's sign-on natively; the resulting session cookies are
/// stored and installed into the shared cookie storage so every website
/// request (scraper, downloads) carries them.
///
/// To be replaced by a token-based login when the FBA API provides one.
@MainActor
final class AuthRepository: ObservableObject {
    static let shared = AuthRepository()

    static let host = "www.freebuddhistaudio.com"
    static let loginURL = URL(string: "https://www.freebuddhistaudio.com/sso/?login=true&returnTo=https://www.freebuddhistaudio.com/")!
    static let logoutURL = URL(string: "https://www.freebuddhistaudio.com/user/logout")!
    private static let myDetailsURL = URL(string: "https://www.freebuddhistaudio.com/api/v1/my-details")!
    static let sessionCookies: Set<String> = ["PHPSESSID", "SimpleSAMLAuthToken", "fba"]

    @Published private(set) var state = AuthState()
    var isLoggedIn: Bool { state.loggedIn }

    private let defaults = UserDefaults.standard
    private let loggedInKey = "fba_session_logged_in"
    private let cookiesKey = "fba_session_cookies"
    private let scraper = FBAScraper()

    init() {
        let loggedIn = defaults.bool(forKey: loggedInKey)
        state = AuthState(loggedIn: loggedIn, checking: loggedIn)
        if loggedIn {
            installStoredCookies()
            Task { await refresh() }
        }
    }

    /// The login is complete once the SAML token and the site session are both present.
    static func isCompleteSession(_ cookies: [String: String]) -> Bool {
        cookies["SimpleSAMLAuthToken"] != nil && cookies["fba"] != nil && cookies["PHPSESSID"] != nil
    }

    /// Log in with the Triratna username (not the email) and password through the
    /// single sign-on, natively. Returns nil on success or a user-facing message.
    func login(username: String, password: String) async -> String? {
        let user = username.trimmingCharacters(in: .whitespaces)
        guard !user.isEmpty, !password.isEmpty else { return "Enter your username and password." }
        switch await SsoLogin().login(username: user, password: password) {
        case .success(let cookies):
            return await installSession(cookies) ? nil : "Couldn't complete the login. Please try again."
        case .invalidCredentials:
            return "Username or password not recognised. Use your Triratna username, not your email address."
        case .failure:
            return "Couldn't reach the login service. Check your connection and try again."
        }
    }

    /// Install a complete FBA session (from the sign-on), verify it and load the user header.
    func installSession(_ cookies: [String: String]) async -> Bool {
        guard Self.isCompleteSession(cookies) else { return false }
        let session = cookies.filter { Self.sessionCookies.contains($0.key) }
        defaults.set(session, forKey: cookiesKey)
        defaults.set(true, forKey: loggedInKey)
        installStoredCookies()
        state = AuthState(loggedIn: true, checking: true)
        return await refresh()
    }

    /// Re-check the stored session with the site and refresh the user header.
    /// A session the site no longer accepts logs the user out.
    @discardableResult
    func refresh() async -> Bool {
        guard defaults.bool(forKey: loggedInKey) else {
            state = AuthState()
            return false
        }
        state.checking = true
        do {
            let siteLoggedIn = try await fetchSiteLoggedIn()
            if !siteLoggedIn {
                await logout(clearRemote: false)
                return false
            }
            let user = try? await scraper.fetchLoggedInUser()
            captureRotatedCookies()
            state = AuthState(
                loggedIn: true,
                username: (user?["username"] as? String) ?? (user?["name"] as? String) ?? state.username,
                avatarUrl: (user?["profileImageUrl"] as? String) ?? "",
                isOrderMember: {
                    if let b = user?["isOrderMember"] as? Bool { return b }
                    if let n = user?["isOrderMember"] as? Int { return n != 0 }
                    return false
                }(),
                checking: false
            )
            return true
        } catch {
            // Network trouble: keep the session, stop showing the spinner.
            state.loggedIn = true
            state.checking = false
            return true
        }
    }

    /// Forget the session. `clearRemote` also ends the website session.
    func logout(clearRemote: Bool = true) async {
        if clearRemote, defaults.bool(forKey: loggedInKey) {
            _ = try? await URLSession.shared.data(from: Self.logoutURL)
        }
        defaults.removeObject(forKey: loggedInKey)
        defaults.removeObject(forKey: cookiesKey)
        removeSiteCookies()
        state = AuthState()
    }

    // MARK: - Cookies

    private func storedCookies() -> [String: String] {
        defaults.dictionary(forKey: cookiesKey) as? [String: String] ?? [:]
    }

    /// Put the saved session into the shared cookie storage used by URLSession.
    private func installStoredCookies() {
        for (name, value) in storedCookies() {
            var props: [HTTPCookiePropertyKey: Any] = [.domain: Self.host, .path: "/", .name: name, .value: value]
            if name == "fba" { props[.secure] = "TRUE" } // presence of the key marks the cookie secure
            if let cookie = HTTPCookie(properties: props) {
                HTTPCookieStorage.shared.setCookie(cookie)
            }
        }
    }

    /// The site rotates session ids; keep the stored copy in step with the cookie jar.
    private func captureRotatedCookies() {
        guard let cookies = HTTPCookieStorage.shared.cookies(for: URL(string: "https://\(Self.host)/")!) else { return }
        var stored = storedCookies()
        for c in cookies where Self.sessionCookies.contains(c.name) { stored[c.name] = c.value }
        defaults.set(stored, forKey: cookiesKey)
    }

    private func removeSiteCookies() {
        guard let cookies = HTTPCookieStorage.shared.cookies(for: URL(string: "https://\(Self.host)/")!) else { return }
        for c in cookies { HTTPCookieStorage.shared.deleteCookie(c) }
    }

    private func fetchSiteLoggedIn() async throws -> Bool {
        var request = URLRequest(url: Self.myDetailsURL)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, http.statusCode == 401 || http.statusCode == 403 { return false }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return false }
        return obj["loggedIn"] as? Bool ?? false
    }
}

/// Whether the user is a paying member (downloads, later transcript search).
/// Neither the store subscription nor an FBA-account entitlement exists yet;
/// this is the single seam both plug into later.
@MainActor
final class MembershipRepository: ObservableObject {
    static let shared = MembershipRepository()
    @Published var isMember = false
}
