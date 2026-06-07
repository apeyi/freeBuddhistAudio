import Foundation

extension BinaryFloatingPoint {
    /// Coerce into a valid 0...1 progress/slider fraction. NaN or infinite
    /// inputs become 0 — passing NaN to a SwiftUI Slider or ProgressView
    /// produces CoreGraphics errors and broken layout, so all such values
    /// funnel through here.
    func safeFraction() -> Self {
        if isNaN || isInfinite { return 0 }
        return Swift.min(Swift.max(self, 0), 1)
    }
}

func formatDuration(_ seconds: Int) -> String {
    let h = seconds / 3600
    let m = (seconds % 3600) / 60
    let s = seconds % 60
    if h > 0 {
        return String(format: "%d:%02d:%02d", h, m, s)
    }
    return String(format: "%d:%02d", m, s)
}

func formatFileSize(_ bytes: Int64) -> String {
    if bytes <= 0 { return "" }
    if bytes < 1024 { return "\(bytes) B" }
    if bytes < 1024 * 1024 { return String(format: "%.1f KB", Double(bytes) / 1024) }
    if bytes < 1024 * 1024 * 1024 { return String(format: "%.1f MB", Double(bytes) / (1024 * 1024)) }
    return String(format: "%.2f GB", Double(bytes) / (1024 * 1024 * 1024))
}

func friendlyError(_ error: Error) -> String {
    let desc = error.localizedDescription.lowercased()
    if desc.contains("timeout") || desc.contains("connect") || desc.contains("host") {
        return "Connection error. Check your internet."
    }
    return error.localizedDescription
}
