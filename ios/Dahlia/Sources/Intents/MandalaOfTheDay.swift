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
        guard await MainActor.run(body: { ProStore.shared.unlocked }) else { throw MandalaOfTheDayError.needsPro }

        // From now on the app renders today's and tomorrow's pair ahead whenever it runs.
        AppGroup.defaults?.set(Date(), forKey: DailyWallpaper.usedKey)
        guard let job = WallpaperSource.load().job(day: WidgetDay.number(of: Date())) else { throw MandalaOfTheDayError.noFavourite }

        var drawn = false
        if !WallpaperFiles.exists(job) {
            let ticket = RenderTicket()
            let done = await withTaskCancellationHandler {
                await WallpaperFiles.render(job, painter: .shared, ticket: ticket)
            } onCancel: {
                ticket.cancel()
            }
            await WidgetPainter.shared.collect()
            guard done else { throw MandalaOfTheDayError.failed }
            drawn = true
        }

        let millis = Int(Date().timeIntervalSince(started) * 1000)
        Engine.trace("wallpaper intent side=\(side.rawValue) \(job.width)x\(job.height) drawn=\(drawn) in \(millis) ms")
        #if DEBUG
        AppGroup.defaults?.set("\(side.rawValue) \(String(format: "%.1f", Double(millis) / 1000)) s, \(drawn ? "drawn now" : "rendered ahead") · " +
                               Date().formatted(date: .abbreviated, time: .shortened), forKey: DailyWallpaper.lastRunKey)
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
