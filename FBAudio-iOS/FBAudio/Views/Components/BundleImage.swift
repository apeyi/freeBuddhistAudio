import SwiftUI

/// Load an image from the fbaudio-shared/images/ bundle directory.
struct BundleImage: View {
    let name: String

    var body: some View {
        if let url = Bundle.main.url(forResource: name, withExtension: "jpg", subdirectory: "fbaudio-shared/images"),
           let data = try? Data(contentsOf: url),
           let uiImage = UIImage(data: data) {
            Image(uiImage: uiImage)
                .resizable()
        } else {
            Color.gray.opacity(0.3)
        }
    }
}
