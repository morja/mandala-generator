import Foundation
import Observation
import SwiftUI

/// The running tour and what the controls report to it (Android's `EditorViewModel` tour part and `TourTargets`).
/// The controls open the step's tab and scroll its row into place, then mark the step settled; only then the
/// overlay draws its rings.
@MainActor
@Observable
final class TourModel {
    /// The running tour; nil while none runs.
    private(set) var state: TourState?
    /// The step whose tab and scroll position are in place.
    var settled: TourStep?
    /// Points the picture's side gives up while the tour runs, when the card does not fit below the picture and the
    /// marked row (small screens, large text). Only grows during a tour.
    var shrinkBy: Double = 0
    /// The picture's side at the size the user chose.
    var pictureFull: Double = 0

    @ObservationIgnored var onEnd: (@MainActor (Bool) -> Void)?
    @ObservationIgnored private let gate: TourGate
    /// Whether the tour is to start by itself on this new install, once the editor has drawn its first picture.
    @ObservationIgnored private var pending: Bool

    init(gate: TourGate = .standard(), hadSavedState: Bool = SettingsStore.load() != nil) {
        self.gate = gate
        pending = gate.startsByItself(hadSavedState: hadSavedState)
    }

    var running: Bool { state != nil }

    /// Starts the tour if this is a new install that has not seen it; called once the first picture is drawn.
    func startIfNew() {
        #if DEBUG
        if let n = ShotSetup.tourStep, let step = TourStep(rawValue: n - 1) {
            state = TourState(step: step)
            return
        }
        #endif
        guard pending else { return }
        pending = false
        state = .start(automatic: true)
    }

    /// Starts the tour again, from the menu.
    func start() { state = .start(automatic: false) }

    func next() {
        if let n = state?.next() { state = n } else { end() }
    }

    func back() { state = state?.back() }

    /// Done or Skip. `onEnd` hears whether it started by itself, so a message can say where to find it again.
    func end() {
        guard let ended = state else { return }
        state = nil
        settled = nil
        shrinkBy = 0
        onEnd?(ended.automatic)
    }
}

extension Timing {
    /// How often the tour looks whether the first picture is drawn.
    static let tourStartPoll = Duration.milliseconds(100)
    /// The scroll that brings a step's row under the tabs; the rings appear when it ends (QA-10 item 1).
    static let tourScroll = Animation.easeOut(duration: 0.25)
    /// The latest a step's rings appear, should the scroll animation's completion not be reported.
    static let tourSettle = Duration.milliseconds(350)
}
