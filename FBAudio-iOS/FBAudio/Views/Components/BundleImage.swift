import SwiftUI

/// Load an image from the fbaudio-shared/images/ bundle directory.
struct BundleImage: View {
    let name: String

    var body: some View {
        if let uiImage = loadImage() {
            Image(uiImage: uiImage)
                .resizable()
        } else {
            Color.gray.opacity(0.3)
        }
    }

    private func loadImage() -> UIImage? {
        // Try nested subdirectory
        if let url = Bundle.main.url(forResource: name, withExtension: "jpg", subdirectory: "fbaudio-shared/images"),
           let data = try? Data(contentsOf: url),
           let image = UIImage(data: data) {
            return image
        }
        // Try flat subdirectory (in case bundled differently)
        if let url = Bundle.main.url(forResource: name, withExtension: "jpg", subdirectory: "fbaudio-shared"),
           let data = try? Data(contentsOf: url),
           let image = UIImage(data: data) {
            return image
        }
        // Try searching the whole bundle
        if let url = Bundle.main.url(forResource: name, withExtension: "jpg"),
           let data = try? Data(contentsOf: url),
           let image = UIImage(data: data) {
            return image
        }
        // Try direct path in bundle
        if let bundlePath = Bundle.main.resourcePath {
            let paths = [
                "\(bundlePath)/fbaudio-shared/images/\(name).jpg",
                "\(bundlePath)/SharedData/images/\(name).jpg",
                "\(bundlePath)/images/\(name).jpg",
                "\(bundlePath)/\(name).jpg",
            ]
            for path in paths {
                if let image = UIImage(contentsOfFile: path) {
                    return image
                }
            }
        }
        print("BundleImage: Could not find \(name).jpg in bundle")
        return nil
    }
}
