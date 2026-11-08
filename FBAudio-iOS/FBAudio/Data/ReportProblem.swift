import Foundation

/// "Report a problem" (SPEC: Report a problem sheet): the reasons offered and the email that
/// goes to support, with the talk, its number, the playback position and the device filled in.
/// Pure so the wording is unit-tested; mirrors Android `ReportProblem`.
enum ReportProblem {
    static let supportEmail = "support@dharmachakra.com"

    static let reasons = [
        "It won't play, or it stops",
        "Sound quality",
        "Wrong title, speaker or details",
        "Problem with the transcript",
        "Something else",
    ]

    struct Email: Equatable {
        let to: String
        let subject: String
        let body: String
    }

    static func email(reason: String, talkTitle: String, catNum: String, positionSeconds: Int?,
                      device: String, appVersion: String, signedIn: Bool) -> Email {
        var details = ["Talk: \(talkTitle) (\(catNum))"]
        if let p = positionSeconds, p > 0 { details.append("Playback position: \(formatClock(p))") }
        details.append("App version: \(appVersion)")
        details.append("Device: \(device)")
        details.append("Signed in: \(signedIn ? "yes" : "no")")
        return Email(to: supportEmail, subject: "\(reason): \(talkTitle) (\(catNum))",
                     body: "\n\n\n" + details.joined(separator: "\n"))
    }

    /// A mailto: URL for `email` (Mail or the default mail app).
    static func mailtoURL(_ email: Email) -> URL? {
        var c = URLComponents()
        c.scheme = "mailto"
        c.path = email.to
        c.queryItems = [URLQueryItem(name: "subject", value: email.subject),
                        URLQueryItem(name: "body", value: email.body)]
        return c.url
    }

    /// 662 → "11:02", 3725 → "1:02:05".
    static func formatClock(_ seconds: Int) -> String {
        let h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }
}
