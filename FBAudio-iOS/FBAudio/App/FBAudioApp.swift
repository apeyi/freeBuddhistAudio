import SwiftUI

@main
struct FBAudioApp: App {
    @StateObject private var player = AudioPlayer.shared
    @StateObject private var accentClock = AccentClock()

    init() {
        ApiConfig.install()   // base URL + X-FBA-Client / X-FBA-App-Version headers
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(player)
                .fbaHourlyTheme(accentClock.accent)
        }
    }
}
