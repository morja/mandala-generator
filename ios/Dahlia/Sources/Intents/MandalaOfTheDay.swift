import AppIntents
import Foundation
@preconcurrency import MandalaCore
import UniformTypeIdentifiers

// "Mandala of the day" for Shortcuts (PLAN-4 WP-11, F5 a, Pro): returns today's wallpaper picture for the lock or the
// home screen, so a personal automation ("every day at 7:00, Run Immediately: Mandala of the day, Set Wallpaper") gives
// the iPhone a new wallpaper each morning, as Android's daily wallpaper does. It runs in the app's process without
// opening the app; the picture is usually rendered ahead by the app (WidgetSync), otherwise it is drawn here.

/// The action's "Screen" parameter.
enum WallpaperScreen: String, AppEnum {
    case lock, home

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "ios_intent_screen"
    static let caseDisplayRepresentations: [WallpaperScreen: DisplayRepresentation] = [
        .lock: "pair_lock_screen",
        .home: "pair_home_screen",
    ]

    var side: WallpaperSide { self == .lock ? .lock : .home }
}

/// Why the action gave no picture, in the words Shortcuts shows.
enum MandalaOfTheDayError: Error, CustomLocalizedStringResourceConvertible {
    case needsPro, noFavourite, failed

    var localizedStringResource: LocalizedStringResource {
        switch self {
        case .needsPro: "ios_intent_needs_pro"
        case .noFavourite: "message_daily_needs_favourite"
        case .failed: "message_image_failed"
        }
    }
}

struct MandalaOfTheDayIntent: AppIntent {
    static let title: LocalizedStringResource = "ios_intent_title"
    static let description = IntentDescription("ios_intent_description")
    /// Runs in the background; Shortcuts' "Set Wallpaper" takes the picture.
    static let openAppWhenRun = false

    @Parameter(title: "ios_intent_screen", default: .lock)
    var screen: WallpaperScreen

    init() {}

    init(screen: WallpaperScreen) { self.screen = screen }

    func perform() async throws -> some IntentResult & ReturnsValue<IntentFile> {
        let url = try await Self.picture(screen.side)
        return .result(value: IntentFile(fileURL: url, filename: url.lastPathComponent, type: .png))
    }

    /// Today's picture for `side`: the one rendered ahead, or drawn now when it is missing (the mandala, the choice of
    /// favourites, the finish or the day changed since the app last ran). Pro only.
    static func picture(_ side: WallpaperSide) async throws -> URL {
        let started = Date()
        #if DEBUG
        let peak = Footprint.watch()
        defer { peak.cancel() }
        #endif
        // The cached purchase first; without one (a new install, a new iPhone before the app was opened) StoreKit is asked.
        if !(await MainActor.run(body: { ProStore.shared.unlocked })) {
            await ProStore.shared.check()
            guard await MainActor.run(body: { ProStore.shared.unlocked }) else { throw MandalaOfTheDayError.needsPro }
        }

        // From now on the app renders today's and tomorrow's pair ahead whenever it runs.
        AppGroup.defaults?.set(Date(), forKey: DailyWallpaper.usedKey)
        guard let job = WallpaperSource.load().job(day: WidgetDay.number(of: Date())) else { throw MandalaOfTheDayError.noFavourite }

        // Rendered ahead, or drawn now; waiting behind the app drawing the same pair finds it done.
        let ticket = RenderTicket()
        let result = await withTaskCancellationHandler {
            await WallpaperFiles.ensure(job, painter: .shared, ticket: ticket)
        } onCancel: {
            ticket.cancel()
        }
        guard result.ready else { throw MandalaOfTheDayError.failed }
        let drawn = result.drawn

        let millis = Int(Date().timeIntervalSince(started) * 1000)
        Engine.trace("wallpaper intent side=\(side.rawValue) \(job.width)x\(job.height) drawn=\(drawn) in \(millis) ms")
        #if DEBUG
        peak.cancel()
        let megabytes = await peak.value
        Engine.trace("wallpaper intent peakFootprint=\(megabytes)MB")
        AppGroup.defaults?.set("\(side.rawValue) \(String(format: "%.1f", Double(millis) / 1000)) s, \(drawn ? "drawn now" : "rendered ahead"), " +
                               "peak \(megabytes) MB · " + Date().formatted(date: .abbreviated, time: .shortened), forKey: DailyWallpaper.lastRunKey)
        #endif
        return WallpaperFiles.url(job, side)
    }
}

/// The action shows in the Shortcuts app and in Spotlight without any setup.
struct DahliaShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: MandalaOfTheDayIntent(),
            phrases: [
                "\(.applicationName) mandala of the day",
                "Mandala of the day from \(.applicationName)",
            ],
            shortTitle: "ios_intent_title",
            systemImageName: "photo.on.rectangle"
        )
    }
}

#if DEBUG
/// Debug launch argument for IOS-11: `-wallpaperProbe lock|home` removes the pictures rendered ahead and runs the action's
/// code as Shortcuts would, logging its time and peak footprint (`-wallpaperProbeDelay 5` waits, e.g. to press Home first).
enum WallpaperProbe {
    static func runIfAsked() async {
        guard let name = UserDefaults.standard.string(forKey: "wallpaperProbe"), let side = WallpaperSide(rawValue: name) else { return }
        let delay = max(1, UserDefaults.standard.double(forKey: "wallpaperProbeDelay"))
        try? await Task.sleep(for: .seconds(delay))
        WallpaperFiles.keepOnly([], graceSeconds: 0)
        Engine.trace("wallpaperProbe \(name) start footprint=\(Footprint.current() / 1_048_576)MB")
        do {
            let url = try await MandalaOfTheDayIntent.picture(side)
            let bytes = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int) ?? 0
            Engine.trace("wallpaperProbe \(name) done file=\(url.lastPathComponent) \(bytes / 1024) KB")
        } catch {
            Engine.trace("wallpaperProbe \(name) failed: \(error)")
        }
    }
}
#endif
