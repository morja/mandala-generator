import CoreGraphics
import Foundation
import ImageIO
import Photos
import UniformTypeIdentifiers

/// Encoding pictures: the favourites' thumbnails like the macOS app stores them, and PNG for saving.
enum Pictures {
    private static let thumbnailSize = 128
    private static let thumbnailQuality = 0.85

    /// A 128 px JPEG in base64, as Android's `Thumbnails.encode` and the macOS app.
    static func thumbnail(_ image: CGImage) -> String {
        let side = thumbnailSize
        guard let ctx = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
        else { return "" }
        ctx.interpolationQuality = .high
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: side, height: side))
        guard let small = ctx.makeImage() else { return "" }
        return encode(small, type: .jpeg, quality: thumbnailQuality)?.base64EncodedString() ?? ""
    }

    /// Null when the data is missing or not an image.
    static func decodeThumbnail(_ base64: String) -> CGImage? {
        guard let data = Data(base64Encoded: base64, options: .ignoreUnknownCharacters),
              let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        return CGImageSourceCreateImageAtIndex(source, 0, nil)
    }

    static func png(_ image: CGImage) -> Data? { encode(image, type: .png, quality: nil) }

    private static func encode(_ image: CGImage, type: UTType, quality: Double?) -> Data? {
        let data = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(data, type.identifier as CFString, 1, nil) else { return nil }
        let options = quality.map { [kCGImageDestinationLossyCompressionQuality: $0] as CFDictionary }
        CGImageDestinationAddImage(dest, image, options)
        return CGImageDestinationFinalize(dest) ? data as Data : nil
    }
}

/// Adds pictures to the Photos library with add-only access (approval A10), without an own album:
/// creating an album makes iOS ask for full library access (checked in WP-1, IOS-1 finding).
enum PhotoLibrary {
    enum Failure: Error { case denied, failed(String) }

    /// Asks once; afterwards the system remembers the answer and only Settings can change it.
    static func mayAdd() async -> Bool {
        switch PHPhotoLibrary.authorizationStatus(for: .addOnly) {
        case .authorized, .limited: return true
        case .notDetermined: return await PHPhotoLibrary.requestAuthorization(for: .addOnly) == .authorized
        default: return false
        }
    }

    static func addPNG(_ data: Data, fileName: String) async throws {
        guard await mayAdd() else { throw Failure.denied }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                let options = PHAssetResourceCreationOptions()
                options.originalFilename = fileName
                PHAssetCreationRequest.forAsset().addResource(with: .photo, data: data, options: options)
            }
        } catch {
            throw Failure.failed(error.localizedDescription)
        }
    }
}
