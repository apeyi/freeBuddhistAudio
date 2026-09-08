import Foundation

/// The URLSession that carries the FBA login. It has its own cookie storage, so
/// the session cookies never ride along on image or audio requests (which use
/// URLSession.shared), and — because the site issues a new `fba` cookie on EVERY
/// response — session-carrying requests are sent one at a time while logged in:
/// two parallel requests would share a stale cookie and the second gets bounced
/// to the SSO. Mirrors Android's SessionCookieStore.serializingInterceptor.
@MainActor
final class FbaSession {
    static let shared = FbaSession()

    let cookieStorage = HTTPCookieStorage()
    let session: URLSession

    /// Set by AuthRepository; when false, requests run in parallel as before.
    var isLoggedIn = false

    private var queue: Task<Void, Never>?

    private init() {
        let config = URLSessionConfiguration.default
        config.httpCookieStorage = cookieStorage
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
            "Referer": "https://www.freebuddhistaudio.com/",
        ]
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        session = URLSession(configuration: config)
    }

    /// Perform a request through the login session, serialized while logged in.
    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        guard isLoggedIn else { return try await session.data(for: request) }
        let previous = queue
        let task = Task<Void, Never> { _ = await previous?.value }
        queue = task
        await task.value
        defer { if queue == task { queue = nil } }
        return try await session.data(for: request)
    }

    func data(from url: URL) async throws -> (Data, URLResponse) {
        try await data(for: URLRequest(url: url))
    }
}
