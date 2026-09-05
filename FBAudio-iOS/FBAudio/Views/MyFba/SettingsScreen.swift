import SwiftUI

/// App settings, reached from the gear on My FBA.
struct SettingsScreen: View {
    @ObservedObject private var settings = AppSettings.shared

    var body: some View {
        Form {
            Toggle(isOn: $settings.englishOnly) {
                VStack(alignment: .leading) {
                    Text("English only")
                    Text(settings.englishOnly ? "Talks in other languages are hidden" : "Showing all languages")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .onChange(of: settings.englishOnly) { _ in ContentRepository.shared.invalidateMemo() }
            Toggle(isOn: $settings.preferRemastered) {
                VStack(alignment: .leading) {
                    Text("Prefer remastered audio")
                    Text("Play the remastered version when a talk has one")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .tint(.saffronOrange)
        .miniPlayerClearance()
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
    }
}
