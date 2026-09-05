import Foundation
import SwiftSoup

/// Performs the FBA website's Triratna single sign-on with a username and
/// password, without showing the (unstyled) web form. Mirrors Android's SsoLogin:
///
///  1. GET the FBA login URL → redirects to the SSO login page (sso.triratna.co)
///  2. POST username + password to that form
///  3. The SSO answers with an auto-submitting form carrying the SAML response →
///     POST it back to FBA, which sets the session cookies
///
/// Runs on an ephemeral session with its own cookie storage, so the app's shared
/// cookies are only touched once the whole flow has succeeded.
actor SsoLogin {

    enum Result {
        /// The FBA session cookies (PHPSESSID, SimpleSAMLAuthToken, fba).
        case success([String: String])
        case invalidCredentials
        case failure(String)
    }

    private let session: URLSession

    init() {
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        config.httpAdditionalHeaders = ["User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"]
        session = URLSession(configuration: config)
    }

    func login(username: String, password: String) async -> Result {
        do {
            // 1. Land on the SSO login form
            let (formUrl, formHtml) = try await get(AuthRepository.loginURL)
            let formDoc = try SwiftSoup.parse(formHtml, formUrl.absoluteString)
            guard let form = try formDoc.select("form:has(input[name=username])").first() else {
                return .failure("Login form not found")
            }

            // 2. Submit credentials
            var fields = try hiddenFields(form)
            fields["username"] = username
            fields["password"] = password
            var (url, html) = try await post(absAction(form, pageUrl: formUrl), fields)

            // 3. Relay the SAML response back to FBA (at most a couple of hops)
            var hops = 0
            while hops < 3 {
                let doc = try SwiftSoup.parse(html, url.absoluteString)
                guard let saml = try doc.select("form:has(input[name=SAMLResponse])").first() else { break }
                (url, html) = try await post(absAction(saml, pageUrl: url), try hiddenFields(saml))
                hops += 1
            }

            let cookies = sessionCookies()
            if AuthRepository.isCompleteSession(cookies) { return .success(cookies) }
            if html.range(of: "not recognised", options: .caseInsensitive) != nil
                || (try? SwiftSoup.parse(html).select("form:has(input[name=username])").first()) != nil {
                return .invalidCredentials
            }
            return .failure("Sign-on did not complete")
        } catch {
            return .failure(error.localizedDescription)
        }
    }

    private func get(_ url: URL) async throws -> (URL, String) {
        let (data, response) = try await session.data(from: url)
        try check(response)
        return (response.url ?? url, String(decoding: data, as: UTF8.self))
    }

    private func post(_ url: URL, _ fields: [String: String]) async throws -> (URL, String) {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = fields.map { "\(formEncode($0.key))=\(formEncode($0.value))" }
            .joined(separator: "&").data(using: .utf8)
        let (data, response) = try await session.data(for: request)
        try check(response)
        return (response.url ?? url, String(decoding: data, as: UTF8.self))
    }

    private func check(_ response: URLResponse) throws {
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw URLError(.badServerResponse)
        }
    }

    private func formEncode(_ s: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._*")
        return (s.addingPercentEncoding(withAllowedCharacters: allowed) ?? s).replacingOccurrences(of: "%20", with: "+")
    }

    private func hiddenFields(_ form: Element) throws -> [String: String] {
        var out: [String: String] = [:]
        for input in try form.select("input[name]") where (try input.attr("type")) != "submit" {
            out[try input.attr("name")] = try input.attr("value")
        }
        return out
    }

    /// Empty / query-only actions post back to the current page.
    private func absAction(_ form: Element, pageUrl: URL) throws -> URL {
        let action = try form.attr("action")
        if action.isEmpty || action.hasPrefix("?") {
            var comps = URLComponents(url: pageUrl, resolvingAgainstBaseURL: false)!
            comps.percentEncodedQuery = action.isEmpty ? comps.percentEncodedQuery : String(action.dropFirst())
            return comps.url ?? pageUrl
        }
        return URL(string: action, relativeTo: pageUrl)?.absoluteURL ?? pageUrl
    }

    private func sessionCookies() -> [String: String] {
        var out: [String: String] = [:]
        for c in session.configuration.httpCookieStorage?.cookies ?? []
            where c.domain.hasSuffix("freebuddhistaudio.com") && AuthRepository.sessionCookies.contains(c.name) {
            out[c.name] = c.value
        }
        return out
    }
}
