import Foundation
import Combine

struct AuthState: Equatable {
    var loggedIn = false
    var username = ""
    var email = ""
    var avatarUrl = ""
    var isOrderMember = false
    /// True while the stored session is being verified at startup.
    var checking = false
}

/// Login with the existing FBA (Triratna single sign-on) account. `SsoLogin`
/// runs the website's sign-on natively; the resulting session cookies are
/// stored and installed into FbaSession's private cookie storage so website
/// requests (scraper, history) carry them — and nothing else does.
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
    private let usernameKey = "fba_session_username"
    private let scraper = FBAScraper()

    init() {
        let loggedIn = defaults.bool(forKey: loggedInKey)
        state = AuthState(loggedIn: loggedIn, checking: loggedIn)
        if loggedIn {
            installStoredCookies()
            FbaSession.shared.isLoggedIn = true
            Task { await refresh() }
        }
    }

    /// The login is complete once the SAML token and the site session are both present.
    nonisolated static func isCompleteSession(_ cookies: [String: String]) -> Bool {
        cookies["SimpleSAMLAuthToken"] != nil && cookies["fba"] != nil && cookies["PHPSESSID"] != nil
    }

    /// Log in with the Triratna username (not the email) and password through the
    /// single sign-on, natively. Returns nil on success or a user-facing message.
    func login(username: String, password: String) async -> String? {
        let user = username.trimmingCharacters(in: .whitespaces)
        guard !user.isEmpty, !password.isEmpty else { return "Enter your username and password." }
        switch await SsoLogin().login(username: user, password: password) {
        case .success(let cookies):
            defaults.set(user, forKey: usernameKey)
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
        FbaSession.shared.isLoggedIn = true
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
            let details = try await fetchMyDetails()
            let siteLoggedIn = details?["loggedIn"] as? Bool ?? false
            if !siteLoggedIn {
                await logout(clearRemote: false)
                return false
            }
            let user = try? await scraper.fetchLoggedInUser()
            captureRotatedCookies()
            // The site's user object and my-details use inconsistent field names
            // (and sometimes carry none), so try the likely ones and fall back to
            // the username the person actually typed at login.
            let display = firstString(user, "displayName", "name", "fullName", "username", "firstName", "screenName")
                ?? firstString(details, "displayName", "name", "fullName", "username")
                ?? storedUsername()
                ?? (state.username.isEmpty ? nil : state.username)
                ?? ""
            let email = firstString(user, "email", "emailAddress")
                ?? firstString(details, "email", "emailAddress") ?? ""
            state = AuthState(
                loggedIn: true,
                username: display,
                email: email,
                avatarUrl: firstString(user, "profileImageUrl", "avatar", "image", "photo") ?? "",
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
            _ = try? await FbaSession.shared.data(from: Self.logoutURL)
        }
        defaults.removeObject(forKey: loggedInKey)
        defaults.removeObject(forKey: cookiesKey)
        defaults.removeObject(forKey: usernameKey)
        removeSiteCookies()
        FbaSession.shared.isLoggedIn = false
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
                FbaSession.shared.cookieStorage.setCookie(cookie)
            }
        }
    }

    /// The site rotates session ids; keep the stored copy in step with the cookie jar.
    private func captureRotatedCookies() {
        guard let cookies = FbaSession.shared.cookieStorage.cookies(for: URL(string: "https://\(Self.host)/")!) else { return }
        var stored = storedCookies()
        for c in cookies where Self.sessionCookies.contains(c.name) { stored[c.name] = c.value }
        defaults.set(stored, forKey: cookiesKey)
    }

    private func removeSiteCookies() {
        guard let cookies = FbaSession.shared.cookieStorage.cookies(for: URL(string: "https://\(Self.host)/")!) else { return }
        for c in cookies { FbaSession.shared.cookieStorage.deleteCookie(c) }
    }

    private func fetchMyDetails() async throws -> [String: Any]? {
        var request = URLRequest(url: Self.myDetailsURL)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await FbaSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, http.statusCode == 401 || http.statusCode == 403 { return ["loggedIn": false] }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    private func storedUsername() -> String? {
        let s = defaults.string(forKey: usernameKey)
        return (s?.isEmpty == false) ? s : nil
    }

    /// First of `keys` present as a non-empty string on the (optional) object.
    private func firstString(_ dict: [String: Any]?, _ keys: String...) -> String? {
        guard let dict else { return nil }
        for k in keys { if let v = dict[k] as? String, !v.isEmpty { return v } }
        return nil
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
