import SwiftUI

private let benefits = [
    "Support us keeping over 7,000 Dharma talks available to all",
    "Support the development of this app and help us reach even more people",
    "Access to downloads — take any talk or series with you on retreat or on the road",
    "Access to transcript search, as it becomes available",
]

/// Membership page. Purchases go through the App Store's subscription system;
/// until FBA has set that up the buttons explain that subscriptions open at launch.
struct JoinScreen: View {
    let onDonateClick: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Join Free Buddhist Audio").font(.title2).bold()
                Text("Free Buddhist Audio is free for everyone, and always will be. Members help us keep it that way — and get a little extra in return.")
                    .font(.body)

                Text("Benefits of joining").font(.headline).padding(.top, 8)
                ForEach(benefits, id: \.self) { benefit in
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.saffronOrange)
                        Text(benefit).font(.body)
                    }
                }

                VStack(spacing: 8) {
                    Button(action: {}) { Text("Join monthly").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).disabled(true)
                    Button(action: {}) { Text("Join yearly").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).disabled(true)
                    Text("Subscriptions open at launch.").font(.caption).foregroundStyle(.secondary)
                }
                .padding(.top, 16)

                Text("Prefer to give once?").font(.headline).padding(.top, 16)
                Button(action: onDonateClick) { Text("Donate").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
            }
            .padding(16)
        }
        .tint(.saffronOrange)
        .miniPlayerClearance()
        .navigationTitle("Join")
    }
}
