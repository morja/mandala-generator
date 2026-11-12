import AppIntents
import Foundation

// The settings of one placed widget, through the system's "Edit Widget" (ADR-0020 amended 5 November 2026, PLAN-4
// section 3.5): Mandala (the daily turn, or always the same favourite) and Appearance (like the app, light, dark).
// In the app too, so it can read the placed widgets' settings (`WidgetCenter` current configurations).

/// The daily turn or one favourite, from the widget index the app writes.
struct WidgetMandala: AppEntity {
    static let dailyID = "daily"

    let id: String
    let name: String

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "widget_mandala")
    static let defaultQuery = WidgetMandalaQuery()

    static let daily = WidgetMandala(id: dailyID, name: "")

    var displayRepresentation: DisplayRepresentation {
        id == Self.dailyID ? DisplayRepresentation(title: "widget_daily") : DisplayRepresentation(title: "\(name)")
    }

    /// A fixed favourite's id; nil for the daily turn.
    var fixedID: String? { id == Self.dailyID ? nil : id }
}

struct WidgetMandalaQuery: EntityQuery {
    /// The daily turn first, then the favourites in the app's order.
    private static func all() -> [WidgetMandala] {
        [.daily] + (WidgetFiles.readIndex()?.favourites ?? []).map { WidgetMandala(id: $0.id, name: $0.name) }
    }

    /// A deleted favourite is not found, so its widget falls back to the daily turn.
    func entities(for identifiers: [String]) async throws -> [WidgetMandala] {
        let all = Self.all()
        return identifiers.compactMap { id in all.first { $0.id == id } }
    }

    func suggestedEntities() async throws -> [WidgetMandala] { Self.all() }

    func defaultResult() async -> WidgetMandala? { .daily }
}

extension WidgetLook: AppEnum {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "widget_appearance")
    static let caseDisplayRepresentations: [WidgetLook: DisplayRepresentation] = [
        .app: DisplayRepresentation(title: "widget_look_app"),
        .light: DisplayRepresentation(title: "widget_look_light"),
        .dark: DisplayRepresentation(title: "widget_look_dark"),
    ]
}

struct MandalaWidgetIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "widget_label"
    static let description = IntentDescription("widget_description")

    @Parameter(title: "widget_mandala")
    var mandala: WidgetMandala?

    @Parameter(title: "widget_appearance", default: .app)
    var appearance: WidgetLook

    init() {}

    init(mandala: WidgetMandala?, appearance: WidgetLook) {
        self.mandala = mandala
        self.appearance = appearance
    }
}
