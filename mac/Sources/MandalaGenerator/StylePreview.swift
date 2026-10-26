import SwiftUI

/// The picker examples use the same seed and settings for every style and never render on opening.
struct StylePreview: View {
    let style: MandalaStyle

    private static let images: [MandalaStyle: NSImage] = {
        var result: [MandalaStyle: NSImage] = [:]
        for style in MandalaStyle.allCases {
            let filename = style.rawValue + ".png"
            var url = Bundle.main.url(forResource: filename, withExtension: nil, subdirectory: "StylePreviews")
            #if SWIFT_PACKAGE
            if url == nil {
                url = Bundle.module.url(forResource: filename, withExtension: nil, subdirectory: "StylePreviews")
            }
            #endif
            if let url, let image = NSImage(contentsOf: url) { result[style] = image }
        }
        return result
    }()

    var body: some View {
        Group {
            if let image = Self.images[style] {
                Image(nsImage: image).resizable().interpolation(.high)
            } else {
                Image(systemName: style.sfSymbol)
            }
        }
        .frame(width: 24, height: 24)
        .clipShape(RoundedRectangle(cornerRadius: 4))
        .accessibilityHidden(true)
    }
}
