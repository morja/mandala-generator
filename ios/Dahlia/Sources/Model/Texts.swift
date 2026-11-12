import Foundation
@preconcurrency import MandalaCore
import UIKit

// Texts of the app. The keys are Android's (strings*.xml) or those of ios/Dahlia/Strings; the catalogue
// Localizable.xcstrings is generated from both by ios/scripts/gen-strings, and ios/scripts/check-sources
// fails when a key below is missing from it. No English text belongs in the screens.
enum Texts {
    static let layers = text("controls_tab_layers")
    static let style = text("controls_style")
    static let palette = text("controls_palette")
    static let shape = text("controls_shape")
    static let lightAndColour = text("controls_light_and_colour")
    static let favourites = text("editor_favourites")
    static let addCurrent = text("editor_favourites_add")
    static let favouritesEmpty = text("editor_favourites_empty") // iOS variant: no "Open file"
    static let favouritesHint = text("editor_favourites_hint") // iOS variant: no selecting yet
    static let favouriteAdded = text("message_favourite_added")
    static let saveImage = text("editor_save_image")
    static let saveToPhotos = text("ios_save_to_photos")
    static let creatingImage = text("export_creating_image")
    static let addingToPhotos = text("export_saving_to_pictures") // iOS variant: Photos
    static let saveFailed = text("message_image_failed") // iOS variant: without the cause
    static let photosDenied = text("ios_photos_denied")
    static let openSettings = text("ios_open_settings")
    static let preview = text("editor_preview")
    static let close = text("controls_close")

    static func saveSquare(_ size: Int) -> String { format("ios_save_square", size, size) }
    static func savedToPhotos(_ size: Int) -> String { format("message_saved_to_pictures", size, size) } // iOS variant: Photos
    static func layerHide(_ n: Int) -> String { format("controls_layer_hide", n) }
    static func layerShow(_ n: Int) -> String { format("controls_layer_show", n) }
    static func sliderReset(_ label: String) -> String { format("controls_slider_reset", label) }
    static func favouriteLabel(_ styles: String) -> String { format("ios_favourite_label", styles) }
    static func percent(_ value: Int) -> String { format("editor_percent", value) }

    static func slider(_ s: LayerSlider) -> String {
        switch s {
        case .symmetry: text("controls_symmetry")
        case .scale: text("controls_scale")
        case .complexity: text("controls_complexity")
        case .spokes: text("controls_spokes")
        case .density: text("controls_density")
        case .ripple: text("controls_ripple")
        case .glow: text("controls_glow")
        case .colourDrift: text("controls_colour_drift")
        case .painted: text("controls_painted")
        case .saturation: text("controls_saturation")
        case .brightness: text("controls_brightness")
        default: s.name
        }
    }

    /// Android's short style labels (strings_labels.xml): the key is `style_` plus the Kotlin enum name in lower case.
    static func style(_ s: MandalaStyle) -> String {
        let key = "style_" + s.name.lowercased()
        let label = text(key)
        return label == key ? s.displayName : label
    }

    private static func text(_ key: String) -> String {
        String(localized: String.LocalizationValue(key))
    }

    /// A catalogue text with Android's positional placeholders (`%1$lld`, `%1$@`).
    private static func format(_ key: String, _ args: CVarArg...) -> String {
        String(format: text(key), locale: nil, arguments: args)
    }
}

enum Links {
    @MainActor static func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }
}
