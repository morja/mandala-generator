import CoreGraphics
import Foundation
@preconcurrency import MandalaCore
import UIKit

// WP-10: the home screen widgets from the editor's side. Redrawn as on Android (ADR-0020, amended 5 November 2026)
// when what they show may change: the first start (starter favourites), favourites added, changed or deleted, the
// daily choice, the app's theme, Pro, the quote languages, and every return to the app (a new day). `WidgetSync`
// writes only what changed and reloads the timelines only then.

/// A row of the menu's widget list: its size, what it shows now, and that favourite's thumbnail.
struct WidgetRow: Identifiable {
    let id: Int
    let title: String
    let detail: String
    let thumbnail: CGImage?
}

extension EditorModel {
    static let widgetMarkKey = "widgetMark"

    /// Debug builds mark widget pictures with a small dot (global rule; on by default, the switch on the More page or
    /// `-widgetMark NO` turns it off for screenshots). Release builds never mark them.
    var widgetMark: Bool {
        #if DEBUG
        // A launch argument arrives as text ("NO"), so `bool(forKey:)` reads it, not a cast.
        UserDefaults.standard.object(forKey: Self.widgetMarkKey) == nil ? true : UserDefaults.standard.bool(forKey: Self.widgetMarkKey)
        #else
        false
        #endif
    }

    func setWidgetMark(_ on: Bool) {
        UserDefaults.standard.set(on, forKey: Self.widgetMarkKey)
        widgetsChanged()
    }

    var widgetInputs: WidgetInputs {
        let scale = (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.screen.scale ?? 3
        let chosen = DailyChoiceStore.load()
        // The wallpapers ahead only with Pro and once an automation runs the Shortcuts action (WP-11).
        let wallpaper = proOwned && DailyWallpaper.actionUsed ? WallpaperSource(favourites: favorites, chosen: chosen, choice: save.choice) : nil
        return WidgetInputs(favourites: favorites, chosen: chosen, pro: proOwned,
                            appLight: UserDefaults.standard.bool(forKey: "lightTheme"), quoteLanguages: quoteLanguages,
                            mark: widgetMark, scale: max(2, scale), wallpaper: wallpaper)
    }

    /// At launch, after the first preview had its turn.
    func widgetsStart() {
        #if DEBUG
        WidgetDebugSettings.applyLaunchArguments()
        #endif
        guard Self.widgetsRun else { return }
        widgets.request(widgetInputs, delay: .seconds(2))
    }

    func widgetsChanged() {
        // Every return to the app: the action has no window to ask for the screen (WP-11).
        WallpaperFiles.rememberScreen()
        guard Self.widgetsRun else { return }
        widgets.request(widgetInputs)
    }

    /// Not while the app only hosts the StoreKit tests: rendering widget pictures beside them made two timing-sensitive
    /// tests flaky (IOS-10).
    private static var widgetsRun: Bool { ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] == nil }

    /// The background app refresh (approval A10): renders the next day and returns when done or out of time.
    func refreshWidgetsInBackground() async { await widgets.run(widgetInputs) }

    // ── The tap on a widget ──

    /// A tap opens the app as it was; only when the widget shows another mandala, a message offers it
    /// (Android's `offerWidgetFavourite`).
    func openedFromWidget(_ url: URL) {
        guard url.host == "widget",
              let id = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "favourite" })?.value,
              let favourite = favorites.first(where: { $0.id == id }), favourite.parameters != params else { return }
        message = Message(text: Texts.widgetOffer, actionLabel: Texts.widgetOpen) { [weak self] in
            guard let self, let current = favorites.first(where: { $0.id == id }) else { return }
            applyFavorite(current)
        }
    }

    // ── The menu's list ──

    /// The placed widgets with what each shows now: a fixed favourite by its name, the daily turn as "name · Daily turn".
    func widgetRows() async -> [WidgetRow] {
        let placed = await WidgetSync.placed()
        let inputs = widgetInputs
        let today = WidgetSync.turn(inputs, day: WidgetDay.number(of: Date()))
        return placed.map { widget in
            let fixed = widget.fixed.flatMap { id in favorites.first { $0.id == id } }
            let shown = fixed ?? today
            let detail = shown.map { fixed != nil ? WidgetSync.name($0) : Texts.widgetDaily(WidgetSync.name($0)) } ?? Texts.widgetEmpty
            return WidgetRow(id: widget.id, title: Texts.widgetTitle(widget.shape), detail: detail,
                             thumbnail: shown.flatMap { Pictures.decodeThumbnail($0.thumbnailData) })
        }
    }
}

#if DEBUG
/// Debug launch arguments for the widget checks in IOS-10: `-widgetDayMinutes 2` makes a day of the turn two minutes
/// long (app and extension), so the turn can be watched without waiting for 4:00; `-widgetResetFootprint YES` clears
/// the extension's recorded peak memory.
enum WidgetDebugSettings {
    static func applyLaunchArguments() {
        let d = UserDefaults.standard
        let minutes = d.double(forKey: "widgetDayMinutes")
        if minutes > 0 {
            AppGroup.defaults?.set(minutes * 60, forKey: WidgetDay.debugLengthKey)
        } else {
            AppGroup.defaults?.removeObject(forKey: WidgetDay.debugLengthKey)
        }
        if d.bool(forKey: "widgetResetFootprint") {
            AppGroup.defaults?.removeObject(forKey: "debugWidgetFootprintKB")
            AppGroup.defaults?.removeObject(forKey: "debugWidgetFootprintKB.moment")
        }
    }
}
#endif
