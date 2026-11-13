import CoreGraphics
import Foundation
@preconcurrency import MandalaCore
import Observation

// State of the text's two colours (DM-24 A2, QA-12) and of typing in the Text tab (QA-10 item 4). The rules
// are the shared module's (`TextColours`, `TextColourSheet`, through `IosTextColours`); the operations are in
// EditorModel+TextColours.swift, as Android's text colour parts of `EditorViewModel`.

/// Immutable once made: the small picture without its text and its colours.
extension TextPictureSample: @retroactive @unchecked Sendable {}
extension Hsb: @retroactive @unchecked Sendable {}

@MainActor
@Observable
final class TextColourState {
    /// Which of the two colours the swatches, the sheet and "Fine tune" change.
    var target: TextColourTarget = .text
    /// The colour sheet while it is open, with the colours from when it opened (for Cancel).
    private(set) var sheet: TextColourSheet?
    /// The preview as it was when the sheet opened: the "Before" half of the sheet's comparison.
    private(set) var before: CGImage?
    /// The picture's colours plus white; just white until the picture was looked at.
    private(set) var swatches: [Hsb] = [IosTextColours.shared.white()]
    /// The field of the Text tab with the keyboard: the picture folds small while typing (QA-10 item 4).
    var typing: TextTabField?

    /// The picture without its text the swatches came from; "Stand out" looks at it too.
    @ObservationIgnored private(set) var sample: TextPictureSample?
    @ObservationIgnored var sampleTask: Task<Void, Never>?
    @ObservationIgnored var sampleTicket: RenderTicket?
    @ObservationIgnored var standOutTask: Task<Void, Never>?
    /// The sheet's live picture: one render at a time, and the newest settings next (see `renderLive`).
    @ObservationIgnored var liveRunning = false
    @ObservationIgnored var liveWanted = false
    @ObservationIgnored var liveTicket: RenderTicket?

    func open(_ sheet: TextColourSheet, before: CGImage?) {
        self.before = before
        self.sheet = sheet
    }

    func close() {
        sheet = nil
        before = nil
        liveWanted = false
        liveTicket?.cancel()
    }

    func keep(_ sample: TextPictureSample) {
        self.sample = sample
        swatches = sample.swatches
    }

    /// After a memory warning; the swatches stay until the picture is looked at again.
    func dropSample() { sample = nil }
}
