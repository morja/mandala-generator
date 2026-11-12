import CoreGraphics
import Foundation

// What the app and the widget extension share about "Mandala of the day" (ADR-0020 with its iPhone amendment,
// PLAN-4 section 3.5). The extension never renders (about 30 MB): the app writes the pictures and this index into
// the App Group, and the extension only reads them. Plain Swift, no MandalaCore, so the extension stays small.

/// The widget's kind, for WidgetKit's reloads and the app's list of placed widgets.
enum WidgetKind {
    static let mandala = "MandalaOfTheDay"
}

/// How the widget is named and drawn: square small, wide with the words, square large (Android's `WidgetShape`).
enum WidgetShape: String, Codable, CaseIterable, Sendable {
    case small, wide, large
}

/// The widget's colours (Android's `WidgetLook`): like the app's own light or dark setting, or always one of them.
enum WidgetLook: String, Codable, CaseIterable, Sendable {
    case app, light, dark
}

/// The days of the daily turn. iOS has no job at a set time, so the turn is a function of the date (PLAN-4 section 3.5):
/// a day starts at 4:00 local time, as Android's morning window, and is numbered like Android's `LocalDate.toEpochDay`.
enum WidgetDay {
    static let startHour = 4

    /// The App Group key of a debug build's day length in seconds, so the turn can be watched in minutes.
    static let debugLengthKey = "debugDaySeconds"

    /// A debug build's short day, nil for real days (always nil in release builds).
    static var debugLength: TimeInterval? {
        #if DEBUG
        let seconds = AppGroup.defaults?.double(forKey: debugLengthKey) ?? 0
        return seconds > 0 ? seconds : nil
        #else
        return nil
        #endif
    }

    private static let utc: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC") ?? .current
        return c
    }()

    /// The number of the day `date` belongs to: before 4:00 it is still the day before.
    static func number(of date: Date, calendar: Calendar = .current, length: TimeInterval? = debugLength) -> Int {
        if let length { return Int((date.timeIntervalSince1970 / length).rounded(.down)) }
        let shifted = calendar.date(byAdding: .hour, value: -startHour, to: date) ?? date
        let parts = calendar.dateComponents([.year, .month, .day], from: shifted)
        let midnight = utc.date(from: parts) ?? shifted
        return Int((midnight.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    /// When day `number` starts: 4:00 local time on that date.
    static func start(of number: Int, calendar: Calendar = .current, length: TimeInterval? = debugLength) -> Date {
        if let length { return Date(timeIntervalSince1970: Double(number) * length) }
        var parts = utc.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: Double(number) * 86_400))
        parts.hour = startHour
        return calendar.date(from: parts) ?? Date(timeIntervalSince1970: Double(number) * 86_400)
    }
}

/// The index the app writes and the extension reads: the favourites with their pictures and words, the next days of
/// the daily turn, and the app's appearance. File names are relative to `WidgetFiles.folder`.
struct WidgetIndex: Codable, Equatable, Sendable {
    struct Words: Codable, Equatable, Sendable {
        var text: String
        var author: String
    }

    struct Favourite: Codable, Equatable, Sendable {
        var id: String
        /// As the menu names it: its own name, or its styles.
        var name: String
        /// Its own words, shown beside the picture in the wide widget.
        var words: Words?
        /// The stored 128 px thumbnail, until a picture is drawn.
        var thumb: String?
        /// Square, for the small widget and the large one until its own picture exists.
        var square: String?
        /// Square without the words, for the wide widget (the square picture when it has no words).
        var plain: String?
        /// In the large widget's shape.
        var large: String?
    }

    struct Day: Codable, Equatable, Sendable {
        var number: Int
        var start: Date
        /// The daily turn's favourite; nil when there is no favourite.
        var favourite: String?
        /// The quote of the day, for a favourite without words of its own.
        var quote: Words?
    }

    var favourites: [Favourite]
    var days: [Day]
    /// The app's own setting, for widgets "like the app".
    var appLight: Bool
    /// Debug builds mark widget pictures with a small dot (global rule); off for screenshots.
    var mark: Bool

    /// Every file the index points to.
    var files: Set<String> {
        Set(favourites.flatMap { [$0.thumb, $0.square, $0.plain, $0.large].compactMap { $0 } })
    }
}

/// The widget folder in the App Group.
enum WidgetFiles {
    static var folder: URL? { AppGroup.container?.appendingPathComponent("Widgets", isDirectory: true) }

    static var index: URL? { folder?.appendingPathComponent("index.json") }

    static func url(_ name: String) -> URL? { folder?.appendingPathComponent(name) }

    static func readIndex() -> WidgetIndex? {
        guard let url = index, let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(WidgetIndex.self, from: data)
    }
}

/// The widget's size in points per shape, as the extension last saw it (`context.displaySize`); the app renders
/// the pictures at that size. Until a widget was shown, the size of a 6.1" iPhone.
enum WidgetSizes {
    private static func key(_ shape: WidgetShape) -> String { "widgetSize." + shape.rawValue }

    static func fallback(_ shape: WidgetShape) -> CGSize {
        switch shape {
        case .small: CGSize(width: 158, height: 158)
        case .wide: CGSize(width: 338, height: 158)
        case .large: CGSize(width: 338, height: 354)
        }
    }

    static func load(_ shape: WidgetShape) -> CGSize {
        guard let pair = AppGroup.defaults?.array(forKey: key(shape)) as? [Double], pair.count == 2, pair[0] > 0, pair[1] > 0 else {
            return fallback(shape)
        }
        return CGSize(width: pair[0], height: pair[1])
    }

    static func save(_ shape: WidgetShape, _ size: CGSize) {
        guard size.width > 0, size.height > 0, load(shape) != size else { return }
        AppGroup.defaults?.set([Double(size.width), Double(size.height)], forKey: key(shape))
    }
}

/// The rules for one widget (Android's `WidgetChoice`), shared by the extension and the app's widget list.
enum WidgetRules {
    /// The day of the turn at `date`: the last one that has started; after the last one, it stays (F8 a).
    static func day(in index: WidgetIndex, at date: Date) -> WidgetIndex.Day? {
        index.days.last { $0.start <= date } ?? index.days.first
    }

    /// The fixed favourite while it exists; otherwise (daily turn, or the fixed one deleted) the day's.
    static func favourite(in index: WidgetIndex, fixed: String?, day: WidgetIndex.Day?) -> WidgetIndex.Favourite? {
        if let fixed, let found = index.favourites.first(where: { $0.id == fixed }) { return found }
        guard let id = day?.favourite else { return nil }
        return index.favourites.first { $0.id == id }
    }

    /// Whether the widget is light; "like the app" follows the app's own setting, not the phone's.
    static func isLight(_ look: WidgetLook, appLight: Bool) -> Bool {
        switch look {
        case .app: appLight
        case .light: true
        case .dark: false
        }
    }

    /// The favourite's own words, otherwise the quote of the day.
    static func words(_ favourite: WidgetIndex.Favourite, day: WidgetIndex.Day?) -> WidgetIndex.Words? {
        favourite.words ?? day?.quote
    }

    /// The picture for a shape, falling back to another size and last to the thumbnail (Android after a failed render).
    static func picture(_ favourite: WidgetIndex.Favourite, shape: WidgetShape) -> String? {
        switch shape {
        case .small: favourite.square ?? favourite.large ?? favourite.thumb
        case .wide: favourite.plain ?? favourite.square ?? favourite.large ?? favourite.thumb
        case .large: favourite.large ?? favourite.square ?? favourite.thumb
        }
    }
}
