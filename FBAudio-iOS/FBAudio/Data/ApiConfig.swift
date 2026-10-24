import Foundation

/// Configures the generated v2 API client: base URL and the two headers every
/// request carries (the iOS equivalent of Android's ApiHeadersInterceptor).
/// Call `install()` once at launch. The client key identifies the app and is
/// not a secret (per the API owners).
enum ApiConfig {
    static let clientKey = "a1a959868eb323ea3fbee6927e6bb157db718aaa"
    static let baseURL = "https://www.freebuddhistaudio.com/api/v2"

    static func install() {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        FBAApiAPI.basePath = baseURL
        FBAApiAPI.customHeaders = [
            "X-FBA-Client": clientKey,
            "X-FBA-App-Version": version,
        ]
    }
}
