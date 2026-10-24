import SwiftUI

@main
struct FBAudioApp: App {
    @StateObject private var player = AudioPlayer.shared

    init() {
        ApiConfig.install()   // base URL + X-FBA-Client / X-FBA-App-Version headers
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(player)
                .tint(.saffronOrange)
        }
    }
}
