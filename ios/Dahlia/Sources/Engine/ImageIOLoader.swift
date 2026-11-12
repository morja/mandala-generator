import CoreGraphics
import Foundation
import ImageIO
@preconcurrency import MandalaCore

/// Core's `ImageLoader` on ImageIO (PLAN-4 section 3.3): decodes the background picture at the requested
/// size with the thumbnail call, so a big photo is never held at full size, and stretches it to the square
/// core asks for, as Android's `AndroidImageLoader`. It keeps the last picture, since every preview asks
/// again. Called from core's render threads, so the cache sits behind a lock.
final class ImageIOLoader: NSObject, ImageLoader, @unchecked Sendable {
    private let lock = NSLock()
    private var cached: (url: String, size: Int32, image: Image8)?

    func load(url: String, size: Int32) -> Image8? {
        lock.lock()
        defer { lock.unlock() }
        if let cached, cached.url == url, cached.size == size { return cached.image }
        // The settings carry a `file:` URL from some install; the picture is found by file name in the app's folder.
        guard let path = BackgroundPictures.file(forURL: url),
              let source = CGImageSourceCreateWithURL(path as CFURL, nil) else { return nil }
        // Both sides must reach `size` before the stretch to the square (as Android's sampling): the thumbnail
        // call limits the longer side, so ask for more when the picture is not square.
        let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
        let w = (props?[kCGImagePropertyPixelWidth] as? Int) ?? Int(size), h = (props?[kCGImagePropertyPixelHeight] as? Int) ?? Int(size)
        let longer = Int(size) * max(w, h) / max(1, min(w, h))
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: min(longer, w > 0 && h > 0 ? max(w, h) : longer),
        ]
        guard let decoded = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        let side = Int(size)
        var pixels = Data(count: side * side * 4)
        let drawn = pixels.withUnsafeMutableBytes { raw -> Bool in
            guard let ctx = CGContext(data: raw.baseAddress, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
                                      space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
            else { return false }
            ctx.interpolationQuality = .high
            ctx.draw(decoded, in: CGRect(x: 0, y: 0, width: side, height: side))
            return true
        }
        guard drawn else { return nil }
        let image = IosBridge.shared.image8(rgba: pixels, size: size)
        cached = (url, size, image)
        return image
    }
}
