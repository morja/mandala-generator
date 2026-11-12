import Foundation

// Texts of the widget extension, from the same generated catalogue as the app (Android's keys, ios/scripts/gen-strings);
// ios/scripts/check-sources fails when a key below is missing from it.
enum WidgetTexts {
    static let label = text("widget_label")
    static let description = text("widget_description")
    static let empty = text("widget_empty")

    private static func text(_ key: String) -> String { String(localized: String.LocalizationValue(key)) }
}
