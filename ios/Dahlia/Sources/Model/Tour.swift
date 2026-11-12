import Foundation

// The first-start tour (Android's `Tour.kt`, DM-22, PLAN-4 F9 a): eight cards docked at the bottom, each pointing
// with a ring and a line at a real control while the rest of the screen is dimmed, except the picture. This file
// holds the steps and the rules only (no UI types), so the unit tests compile it on its own.

/// What the tour points at. The controls report where they are on screen.
enum TourTarget: Hashable {
    case picture, play, favourites, save, more, topDie, layersTab, layerRow, addLayer, sceneTab, styleRow, paletteRow, effectsRow, layerDie
}

/// The control tabs a step can need.
enum TourTab { case layers, scene }

/// A ring around a target; `line` also draws the line from the ring to the card.
struct TourMark: Equatable {
    let target: TourTarget
    var line = true
}

/// The steps in their order, with what each one marks and which tab and row it needs.
enum TourStep: Int, CaseIterable {
    case play, favourites, save, layers, styles, colours, effects, die

    /// The key stem of the step's texts: `tour_<stem>_title` and `tour_<stem>_body`.
    var stem: String {
        switch self {
        case .play: "play"
        case .favourites: "favourites"
        case .save: "save"
        case .layers: "layers"
        case .styles: "styles"
        case .colours: "colours"
        case .effects: "effects"
        case .die: "die"
        }
    }

    /// The rings. On the iPhone the menu has no Share row yet, so Save and share rings only the save arrow (IOS-8).
    var marks: [TourMark] {
        switch self {
        case .play: [TourMark(target: .play)]
        case .favourites: [TourMark(target: .favourites)]
        case .save: [TourMark(target: .save)]
        // The tab and Add get rings, the selected layer's row stays clear.
        case .layers: [TourMark(target: .layersTab, line: false), TourMark(target: .addLayer)]
        case .styles: [TourMark(target: .styleRow)]
        case .colours: [TourMark(target: .paletteRow)]
        case .effects: [TourMark(target: .effectsRow)]
        // The top die is far up; only the one in the Layers tab gets a line, so the line does not cross the picture.
        case .die: [TourMark(target: .topDie, line: false), TourMark(target: .layerDie)]
        }
    }

    /// The tab the step opens; nil keeps the tab that was open before the tour.
    var tab: TourTab? {
        switch self {
        case .play, .favourites, .save: nil
        case .layers, .styles, .colours, .die: .layers
        case .effects: .scene
        }
    }

    /// Scrolled up to just under the tabs.
    var scrollTo: TourTarget? {
        switch self {
        case .play, .favourites, .save: nil
        case .layers: .layerRow
        case .styles: .styleRow
        case .colours: .paletteRow
        case .effects: .effectsRow
        case .die: .layerDie
        }
    }

    /// Left undimmed, without a ring.
    var open: [TourTarget] {
        switch self {
        case .layers: [.layerRow]
        case .effects: [.sceneTab]
        default: []
        }
    }
}

/// Where a running tour is, and whether it started by itself on a new install (then it says where to find it again).
struct TourState: Equatable {
    var step: TourStep
    var automatic = false

    /// The step's number from 1, for "3 of 8".
    var number: Int { step.rawValue + 1 }
    var isFirst: Bool { step == TourStep.allCases.first }
    var isLast: Bool { step == TourStep.allCases.last }

    /// The next step, or nil after the last one (the tour ends).
    func next() -> TourState? {
        TourStep(rawValue: step.rawValue + 1).map { TourState(step: $0, automatic: automatic) }
    }

    /// The step before; the first step stays.
    func back() -> TourState {
        TourState(step: TourStep(rawValue: step.rawValue - 1) ?? step, automatic: automatic)
    }

    static var count: Int { TourStep.allCases.count }

    static func start(automatic: Bool) -> TourState { TourState(step: TourStep.allCases[0], automatic: automatic) }
}

/// Whether the tour starts by itself: once, on a new install. Someone who already has a saved mandala (an update
/// from an older version) never gets it by itself; both are marked as seen right away, so the tour does not come
/// back after a crash or on the next start. The menu can start it any time.
struct TourGate {
    let isSeen: () -> Bool
    let markSeen: () -> Void

    func startsByItself(hadSavedState: Bool) -> Bool {
        if isSeen() { return false }
        markSeen()
        return !hadSavedState
    }

    /// The flag in the app's defaults.
    static func standard(_ defaults: UserDefaults = .standard) -> TourGate {
        TourGate(isSeen: { defaults.bool(forKey: seenKey) }, markSeen: { defaults.set(true, forKey: seenKey) })
    }

    static let seenKey = "tourSeen"
}

/// Where the card goes, in points from the top of the overlay.
enum TourLayout {
    /// A ring's top and bottom edge.
    struct Span: Equatable {
        let top: Double
        let bottom: Double
    }

    /// The card's top edge: docked at `bottom`, so Next stays under the thumb, and moved up past every ring it
    /// would cover (keeping `gap` to it), but never above `top`.
    static func cardTop(rings: [Span], cardHeight: Double, top: Double, bottom: Double, gap: Double) -> Double {
        var y = bottom - cardHeight
        for r in rings.sorted(by: { $0.bottom > $1.bottom }) {
            let covers = y < r.bottom + gap && y + cardHeight > r.top - gap
            if covers { y = r.top - gap - cardHeight }
        }
        return max(top, y)
    }
}
