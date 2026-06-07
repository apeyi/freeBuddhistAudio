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
        // Try PNG first (transparent assets), then JPG, across the known locations
        for ext in ["png", "jpg"] {
            if let url = Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "fbaudio-shared/images"),
               let data = try? Data(contentsOf: url),
               let image = UIImage(data: data) {
                return image
            }
            if let url = Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "fbaudio-shared"),
               let data = try? Data(contentsOf: url),
               let image = UIImage(data: data) {
                return image
            }
            if let url = Bundle.main.url(forResource: name, withExtension: ext),
               let data = try? Data(contentsOf: url),
               let image = UIImage(data: data) {
                return image
            }
        }
        // Try direct paths in the bundle
        if let bundlePath = Bundle.main.resourcePath {
            for ext in ["png", "jpg"] {
                let paths = [
                    "\(bundlePath)/fbaudio-shared/images/\(name).\(ext)",
                    "\(bundlePath)/SharedData/images/\(name).\(ext)",
                    "\(bundlePath)/images/\(name).\(ext)",
                    "\(bundlePath)/\(name).\(ext)",
                ]
                for path in paths {
                    if let image = UIImage(contentsOfFile: path) {
                        return image
                    }
                }
            }
        }
        print("BundleImage: Could not find \(name).(png|jpg) in bundle")
        return nil
    }
}
