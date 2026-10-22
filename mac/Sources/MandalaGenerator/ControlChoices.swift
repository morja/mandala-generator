import Foundation

// MARK: - What the controls offer (TUNE-1)
//
// Same rules as the Android app's `Editing` (android/app/.../Editing.kt); keep both in step.
// Saved settings keep every value: these only decide what the pickers, sliders and dice offer.

enum ControlChoices {
    /// Built-in palettes offered in the pickers, the dice and "add layer", by index into
    /// `ColorPalettes.all`: colourful first, then once round the colour wheel. The others (Prism,
    /// Candy, Nebula, Ocean, Synthwave, Dragon, Cosmic) look too much like one of these; they keep
    /// rendering in saved settings and are shown only while a layer uses them.
    static let offeredPalettes = [0, 3, 13, 4, 20, 16, 19, 17, 7, 9, 14, 6, 1, 11, 18]

    /// Palette of a new layer above one with palette `last`: three steps on in the offered order
    /// (was `(last + 3) % 18`). Above a hidden or custom palette the offered order starts again.
    static func nextPalette(after last: Int?) -> Int {
        guard let last else { return offeredPalettes[0] }
        let at = offeredPalettes.firstIndex(of: last) ?? 0
        return offeredPalettes[(at + 3) % offeredPalettes.count]
    }

    /// Styles whose drawing reads the density setting; the others ignore it. Found in the renderer
    /// and checked on Android by rendering every style with low and high density (StyleControlsTest).
    static let densityStyles: Set<MandalaStyle> = Set(MandalaStyle.allCases).subtracting([
        .spirograph, .roseCurves, .butterfly, .epitrochoid, .lissajous, .hypocycloid, .geometric, .sunburst, .stringArt,
    ])

    /// Styles whose curves the ripple setting bends; the line and point styles drawn directly ignore it.
    static let rippleStyles: Set<MandalaStyle> = Set(MandalaStyle.allCases).subtracting([
        .sacredGeometry, .sunburst, .starBurst, .stringArt, .strangeAttractor, .constellationWeb, .deepNebula,
        .interferenceBloom, .hyperboloid, .torus, .nautilus, .universe, .symbols,
    ])

    /// Shared symmetry of new layers from the dice; 1 and 2 look lopsided for most styles.
    static let diceSymmetry = 3...8
    /// Complexity and density of the second layer and above, relative to the first.
    static let upperLayerCalm = 0.8

    /// Colours a background starts with when it is switched on, brighter than the decoder defaults
    /// (0.18 and 0.04), which are so dark that Color, Gradient and Sunburst look alike.
    static let backgroundStartBrightness = 0.35
    static let backgroundStartBrightness2 = 0.08
    /// Brighter colour backgrounds drown the mandala, whose lines are added with Screen.
    static let backgroundBrightness: ClosedRange<Double> = 0...0.6

    /// Background types offered: Grain only while it is the current type (it vanishes in large exports).
    static func backgroundTypes(current: BaseLayerType) -> [BaseLayerType] {
        BaseLayerType.allCases.filter { $0 != .grain || current == .grain }
    }
}

extension MandalaStyle {
    var usesDensity: Bool { ControlChoices.densityStyles.contains(self) }
    var usesRipple: Bool { ControlChoices.rippleStyles.contains(self) }
}

extension BaseLayerSettings {
    /// Starts brighter while the colours are still the decoder defaults; colours the user set stay.
    mutating func brightenIfUntouched() {
        let defaults = BaseLayerSettings()
        guard brightness == defaults.brightness, brightness2 == defaults.brightness2 else { return }
        brightness = ControlChoices.backgroundStartBrightness
        brightness2 = ControlChoices.backgroundStartBrightness2
    }
}

extension AppState {
    /// Offered built-in palettes, then the custom ones (indices after the built-ins).
    var offeredPaletteIndices: [Int] {
        ControlChoices.offeredPalettes + Array(ColorPalettes.all.count ..< allPalettes.count)
    }

    /// The palettes a picker shows: the offered ones, plus `current` if it is a hidden built-in one.
    func paletteChoices(including current: Int) -> [Int] {
        let offered = offeredPaletteIndices
        if ColorPalettes.all.indices.contains(current) && !offered.contains(current) {
            return ControlChoices.offeredPalettes + [current] + Array(ColorPalettes.all.count ..< allPalettes.count)
        }
        return offered
    }
}
