import Foundation
@preconcurrency import MandalaCore

/// The app's own files in Application Support (PLAN-4 section 3.3), with Android's names and the macOS
/// app's formats (ADR-0003), so "Open file" and "Save settings" stay interchangeable later.
enum AppFiles {
    static let folder: URL = {
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }()

    static let settings = folder.appendingPathComponent("last-settings.json")
    static let favorites = folder.appendingPathComponent("favorites.json")
    static let palettes = folder.appendingPathComponent("palettes.json")

    /// Writes to a temporary file and moves it over the old one, so a crash never leaves half a file.
    static func write(_ text: String, to url: URL) throws {
        try Data(text.utf8).write(to: url, options: .atomic)
    }
}

/// The last settings across restarts, as Android's `SettingsStore`.
enum SettingsStore {
    static func load() -> MandalaParameters? {
        guard let text = try? String(contentsOf: AppFiles.settings, encoding: .utf8) else { return nil }
        return IosBridge.shared.decodeOrNull(json: text)
    }

    static func save(_ params: MandalaParameters) {
        try? AppFiles.write(IosBridge.shared.encode(params: params), to: AppFiles.settings)
    }
}

/// The favourites in the macOS app's format, as Android's `FavoritesStore`: on the very first start
/// (no file yet) the starters are written, so they come only once and can be deleted like any other.
enum FavoritesStore {
    static func loadOrSeed() -> [Favorite] {
        if FileManager.default.fileExists(atPath: AppFiles.favorites.path) {
            guard let text = try? String(contentsOf: AppFiles.favorites, encoding: .utf8) else { return [] }
            return IosBridge.shared.decodeFavorites(json: text)
        }
        let starters = Starters.all
        save(starters)
        return starters
    }

    static func save(_ favorites: [Favorite]) {
        try? AppFiles.write(IosBridge.shared.encodeFavorites(favorites: favorites), to: AppFiles.favorites)
    }
}

/// The user's own palettes in `palettes.json`, in the macOS app's format (ADR-0021), as Android's `MyPalettesStore`.
enum PalettesStore {
    static func load() -> [CustomPalette] {
        guard let text = try? String(contentsOf: AppFiles.palettes, encoding: .utf8) else { return [] }
        return IosEditing.shared.decodePalettes(json: text)
    }

    static func save(_ palettes: [CustomPalette]) {
        try? AppFiles.write(IosEditing.shared.encodePalettes(palettes: palettes), to: AppFiles.palettes)
    }
}

/// The 21 starter favourites shared with Android (`android/app/src/main/assets/starter-favorites.json`).
enum Starters {
    static let all: [Favorite] = {
        guard let url = Bundle.main.url(forResource: "starter-favorites", withExtension: "json"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return IosBridge.shared.decodeFavorites(json: text)
    }()

    /// The starter the app icon was made from; a fresh install opens on it.
    static var firstMandala: MandalaParameters? {
        all.first { $0.id == IosBridge.shared.FIRST_MANDALA_ID }?.parameters
    }
}
