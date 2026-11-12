import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Background pictures, as Android's `BackgroundImages`: a picked photo is copied into the app's folder
/// (at most 2048 px a side, JPEG) and referenced by a `file:` URL, as the macOS app stores its image path.
/// The folder's absolute path changes between installs, so a stored URL is resolved by its file name.
enum BackgroundPictures {
    private static let maxSide = 2048
    private static let quality = 0.92

    static let folder: URL = {
        let url = AppFiles.folder.appendingPathComponent("backgrounds", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }()

    enum Failure: Error { case unreadable, notStored }

    /// Copies the picture in `data` and returns its `file:` URL. One picture at a time; a new name per pick
    /// so the settings change and the preview is drawn.
    static func store(_ data: Data) throws -> String {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  kCGImageSourceCreateThumbnailWithTransform: true,
                  kCGImageSourceThumbnailMaxPixelSize: maxSide,
              ] as CFDictionary)
        else { throw Failure.unreadable }
        let out = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(out, UTType.jpeg.identifier as CFString, 1, nil) else { throw Failure.notStored }
        CGImageDestinationAddImage(dest, image, [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
        guard CGImageDestinationFinalize(dest) else { throw Failure.notStored }
        let manager = FileManager.default
        for old in (try? manager.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)) ?? [] { try? manager.removeItem(at: old) }
        let file = folder.appendingPathComponent("background-\(Int(Date().timeIntervalSince1970 * 1000)).jpg")
        try (out as Data).write(to: file, options: .atomic)
        return file.absoluteString
    }

    /// The file a settings URL points to in this install, found by name; nil when it is not there.
    static func file(forURL url: String) -> URL? {
        let name = URL(string: url)?.lastPathComponent ?? (url as NSString).lastPathComponent
        guard !name.isEmpty else { return nil }
        let file = folder.appendingPathComponent(name)
        return FileManager.default.fileExists(atPath: file.path) ? file : nil
    }
}
