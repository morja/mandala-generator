import CryptoKit
import Foundation
@preconcurrency import MandalaCore
import UIKit

// The wallpaper on the iPhone (PLAN-4 WP-11, F5 a, ADR-0014 amended for iOS): iOS lets no app set the wallpaper, so
// the app saves the screen-sized picture (or the pair: lock screen with the finish, home screen darker) to Photos, and
// the Pro Shortcuts action "Mandala of the day" hands today's picture to Shortcuts' own "Set Wallpaper". The daily turn
// is a function of the date, the same rule the widgets follow (`DailyTurn`, days from 4:00, `WidgetDay`).

/// Whose turn it is today, for the widgets and the Shortcuts action alike.
enum DailyWallpaper {
    /// The favourite of day `number`: with Pro the ticked favourites in turn (all when none or every ticked one is
    /// deleted), without Pro always the first of them (F7 a). The order and the choice are the shared `DailyTurn`'s.
    static func favourite(_ favourites: [Favorite], chosen: Set<String>?, pro: Bool, day number: Int) -> Favorite? {
        DailyTurn.shared.turn(favorites: favourites, chosen: chosen, next: Int32(pro ? number : 0))?.first as? Favorite
    }

    /// The App Group key of the moment the Shortcuts action last ran; from then on the app renders the pictures ahead.
    static let usedKey = "wallpaperIntentUsed"

    /// The Shortcuts action ran at least once on this phone (an automation is set up), so pictures are rendered ahead.
    static var actionUsed: Bool { AppGroup.defaults?.object(forKey: usedKey) != nil }

    #if DEBUG
    /// Debug builds: the last run of the action ("2.4 s, drawn now"), shown on the daily page for the device check.
    static let lastRunKey = "debugWallpaperIntentLastRun"
    #endif
}

/// One wallpaper as drawn: the mandala, the save sheet's finish, the home screen's brightness and the screen's pixels.
struct WallpaperJob: Sendable {
    let params: MandalaParameters
    let finish: Finish
    let homeBrightness: Double
    let width: Int
    let height: Int

    /// Named after what it shows, so an unchanged mandala is never drawn twice and a changed one never shown stale.
    var key: String {
        let f = finish
        let text = IosBridge.shared.encode(params: params) + "|\(f.brightness),\(f.contrast),\(f.saturation)|\(homeBrightness)|\(width)x\(height)"
        return SHA256.hash(data: Data(text.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
    }
}

/// Which screen a picture is for (the Shortcuts action's "Screen" parameter).
enum WallpaperSide: String, Sendable, CaseIterable {
    case lock, home
}

/// The wallpaper pictures rendered ahead, in the App Group's `Wallpapers` folder (the app's caches without one).
enum WallpaperFiles {
    static var folder: URL {
        let base = AppGroup.container ?? FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let url = base.appendingPathComponent("Wallpapers", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static func url(_ job: WallpaperJob, _ side: WallpaperSide) -> URL {
        folder.appendingPathComponent("\(job.key)-\(side.rawValue).png")
    }

    static func exists(_ job: WallpaperJob) -> Bool {
        WallpaperSide.allCases.allSatisfy { FileManager.default.fileExists(atPath: url(job, $0).path) }
    }

    // ── The screen ──

    private static let pixelsKey = "wallpaperPixels"

    /// This iPhone's screen in pixels, portrait, as the app last saw it; the action has no window to ask. Until then
    /// the 6.1" iPhone's (iPhone 15, 1179 × 2556).
    static var pixels: (width: Int, height: Int) {
        if let pair = AppGroup.defaults?.array(forKey: pixelsKey) as? [Int], pair.count == 2, pair[0] > 0, pair[1] > 0 {
            return (pair[0], pair[1])
        }
        return (1179, 2556)
    }

    /// Remembers the screen's pixels from the app's window, for the action.
    @MainActor static func rememberScreen() {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        guard let bounds = scene?.screen.nativeBounds, bounds.width > 0, bounds.height > 0 else { return }
        let pair = [Int(min(bounds.width, bounds.height)), Int(max(bounds.width, bounds.height))]
        if AppGroup.defaults?.array(forKey: pixelsKey) as? [Int] != pair { AppGroup.defaults?.set(pair, forKey: pixelsKey) }
    }

    // ── Drawing ──

    /// Makes sure both pictures of `job` exist: draws and writes them on the painter's queue unless they are there
    /// already, checked again on that queue, so an action waiting behind the app's render ahead never draws the same pair
    /// twice. Returns whether they exist and whether they were drawn now; false when cancelled or failed.
    static func ensure(_ job: WallpaperJob, painter: WidgetPainter, ticket: RenderTicket) async -> (ready: Bool, drawn: Bool) {
        await painter.ensureWallpaper(job, ticket: ticket)
    }

    /// Writes the pair's pictures, the home screen first (`exists` asks for both).
    static func write(lock: Data, home: Data, for job: WallpaperJob) -> Bool {
        do {
            try home.write(to: url(job, .home), options: .atomic)
            try lock.write(to: url(job, .lock), options: .atomic)
            return true
        } catch {
            Engine.trace("wallpaper not written: \(error.localizedDescription)")
            return false
        }
    }

    /// Removes the pictures of every other job (yesterday's, a changed mandala's), except those written in the last
    /// ten minutes: an action that just returned one (at 3:59, or from favourites not yet saved) may still be handing it
    /// to "Set Wallpaper Photo".
    static func keepOnly(_ jobs: [WallpaperJob], graceSeconds: TimeInterval = 600) {
        let keep = Set(jobs.flatMap { job in WallpaperSide.allCases.map { url(job, $0).lastPathComponent } })
        let names = (try? FileManager.default.contentsOfDirectory(atPath: folder.path)) ?? []
        for name in names where !keep.contains(name) {
            let file = folder.appendingPathComponent(name)
            let written = (try? FileManager.default.attributesOfItem(atPath: file.path)[.modificationDate] as? Date) ?? .distantPast
            if Date().timeIntervalSince(written) >= graceSeconds { try? FileManager.default.removeItem(at: file) }
        }
    }
}

/// What the Shortcuts action needs to know besides the date, read in the app's process.
struct WallpaperSource: Sendable {
    let favourites: [Favorite]
    let chosen: Set<String>?
    let choice: SaveChoice

    /// From the app's files and preferences, as the editor would load them.
    static func load() -> WallpaperSource {
        WallpaperSource(favourites: FavoritesStore.loadOrSeed(), chosen: DailyChoiceStore.load(), choice: SaveChoiceStore.load())
    }

    /// The job for day `number`; nil without favourites.
    func job(day number: Int) -> WallpaperJob? {
        guard let favourite = DailyWallpaper.favourite(favourites, chosen: chosen, pro: true, day: number) else { return nil }
        let size = WallpaperFiles.pixels
        return WallpaperJob(params: favourite.parameters, finish: choice.finish, homeBrightness: choice.homeBrightness,
                            width: size.width, height: size.height)
    }
}
