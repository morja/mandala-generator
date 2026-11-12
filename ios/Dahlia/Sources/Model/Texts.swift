import Foundation
@preconcurrency import MandalaCore
import UIKit

// English texts of the first build. TEMPORARY (WP-4): taken from Android's strings_*.xml by hand;
// WP-4 generates the string catalogue for all ten languages from those files under the same keys.
enum Texts {
    static let layers = "Layers" // controls_tab_layers
    static let style = "Style" // controls_style
    static let palette = "Palette" // controls_palette
    static let shape = "Shape" // controls_shape
    static let lightAndColour = "Light and colour" // controls_light_and_colour
    static let favourites = "Favourites" // editor_favourites
    static let addCurrent = "Add current" // editor_favourites_add
    static let favouritesEmpty = "No favourites yet. Add the current mandala." // editor_favourites_empty, without "Open file" (WP-7)
    static let favouritesHint = "Tap to use." // editor_favourites_hint, without selecting (WP-7)
    static let favouriteAdded = "Added to favourites" // message_favourite_added
    static let saveImage = "Save image" // editor_save_image
    static let saveSquare = "Square · 1024 × 1024" // iOS only until the save sheet (WP-8)
    static let saveToPhotos = "Save to Photos" // iOS variant of action_save
    static let creatingImage = "Creating the image" // export_creating_image
    static let addingToPhotos = "Adding to Photos" // iOS variant of export_saving_to_pictures
    static let saveFailed = "Could not create the image." // message_image_failed without the cause
    static let photosDenied = "Dahlia may not add pictures to Photos. You can allow it in Settings." // iOS only
    static let openSettings = "Settings" // iOS only
    static let preview = "Mandala preview" // editor_preview
    static let close = "Close"

    static func savedToPhotos(_ size: Int) -> String { "Saved \(size) × \(size) to Photos" } // iOS variant of message_saved_to_pictures
    static func layerHide(_ n: Int) -> String { "Hide layer \(n)" } // controls_layer_hide
    static func layerShow(_ n: Int) -> String { "Show layer \(n)" } // controls_layer_show
    static func sliderReset(_ label: String) -> String { "Reset \(label)" } // controls_slider_reset

    static func slider(_ s: LayerSlider) -> String {
        switch s {
        case .symmetry: "Symmetry"
        case .scale: "Scale"
        case .complexity: "Complexity"
        case .spokes: "Spokes"
        case .density: "Density"
        case .ripple: "Ripple"
        case .glow: "Glow"
        case .colourDrift: "Colour drift"
        case .painted: "Painted"
        case .saturation: "Saturation"
        case .brightness: "Brightness"
        default: s.name
        }
    }

    /// Android's short style labels (strings_labels.xml), by the Kotlin enum name.
    static func style(_ s: MandalaStyle) -> String { styleLabels[s.name] ?? s.displayName }

    private static let styleLabels: [String: String] = [
        "SPIROGRAPH": "Spirograph",
        "ROSE_CURVES": "Rose Curves",
        "FLORAL": "Floral",
        "BUTTERFLY": "Butterfly",
        "EPITROCHOID": "Epitrochoid",
        "LISSAJOUS": "Lissajous",
        "HYPOCYCLOID": "Hypocycloid",
        "PHYLLOTAXIS": "Phyllotaxis",
        "FRACTAL": "Fractal",
        "SUPERFORMULA": "Superformula",
        "WAVE_INTERFERENCE": "Waves",
        "GEOMETRIC": "Geometric",
        "SACRED_GEOMETRY": "Sacred",
        "SUNBURST": "Sunburst",
        "STAR_BURST": "Starburst",
        "STRING_ART": "String Art",
        "SPIDER_WEB": "Spider Web",
        "WEAVE": "Weave",
        "RADIAL_MESH": "Radial Mesh",
        "MOIRE": "Moiré",
        "FLOW_FIELD": "Flow",
        "TENDRIL": "Tendril",
        "VORONOI": "Voronoi",
        "STRANGE_ATTRACTOR": "Attractor",
        "CONSTELLATION_WEB": "Constellation",
        "DEEP_NEBULA": "Nebula",
        "INTERFERENCE_BLOOM": "Interference",
        "HYPERBOLOID": "Hyperboloid",
        "TORUS": "Torus",
        "NAUTILUS": "Nautilus",
        "SPHERE_GRID": "Sphere Grid",
        "TORUS_KNOT": "Torus Knot",
        "TESSERACT": "Tesseract",
        "UNIVERSE": "Universe",
        "SYMBOLS": "Symbols",
        "MIXED": "Mixed",
        "PETAL_RINGS": "Petals",
        "LOOP_RINGS": "Loops",
    ]
}

enum Links {
    @MainActor static func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }
}
