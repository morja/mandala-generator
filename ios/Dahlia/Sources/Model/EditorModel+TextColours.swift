import CoreGraphics
import Foundation
@preconcurrency import MandalaCore

// The text's two colours (DM-24 A2, QA-12), as Android's text colour parts of `EditorViewModel`: the colour the
// switch shows, the swatches from a small picture without the text, "Stand out", and the colour sheet with its
// live picture. Every rule is the shared module's (`TextColours`, `TextColourSheet`, through `IosTextColours`).

extension EditorModel {
    private var colours: IosTextColours { IosTextColours.shared }

    /// The colour the switch shows: a "Fine tune" slider sets it as given.
    func setTextColour(_ colour: Hsb) { change(colours.withColour(params: params, target: textColours.target, colour: colour)) }

    /// A swatch: keeps a text brightness boost above 1 (`TextColours.withPickedColour`).
    func pickTextColour(_ colour: Hsb) { change(colours.withPicked(params: params, target: textColours.target, colour: colour)) }

    /// A point of the sheet's field, x to the right and y down, both 0–1.
    func setTextColourField(x: Double, y: Double) { change(colours.withFieldPoint(params: params, target: textColours.target, x: x, y: y)) }

    /// A hue from the sheet's strip.
    func setTextColourHue(_ hue: Double) { change(colours.withStripHue(params: params, target: textColours.target, hue: hue)) }

    func setGlowSameAsText(_ on: Bool) { change(colours.withSameAsText(params: params, on: on)) }

    // ── The colour sheet ──

    /// Opens the sheet, on one colour when a head dot was tapped.
    func openTextColourSheet(_ target: TextColourTarget? = nil) {
        if let target { textColours.target = target }
        textColours.open(colours.openSheet(params: params), before: preview)
    }

    /// Done keeps the colours; Cancel puts back both as they were when the sheet opened.
    func closeTextColourSheet(keep: Bool) {
        guard let sheet = textColours.sheet else { return }
        textColours.close()
        if !keep { change(colours.cancelled(params: params, sheet: sheet)) }
        Task { await engine.dropLiveCache() }
    }

    /// The sheet's small picture (Android's live preview, QA-12 finding 5): drawn at once while the sheet is open.
    /// One at a time with its own ticket, and when it is done, the newest settings next: a drag never cancels the
    /// picture it waits for, so it shows at the speed the phone draws, whatever the speed of the finger. The full
    /// preview follows 150 ms after the finger rests, as always; a live picture never covers a finished one.
    func renderLive() {
        guard textColours.sheet != nil else { return }
        textColours.liveWanted = true
        guard !textColours.liveRunning else { return }
        textColours.liveRunning = true
        Task {
            defer { textColours.liveRunning = false }
            while textColours.liveWanted, textColours.sheet != nil {
                textColours.liveWanted = false
                let p = params
                let ticket = RenderTicket()
                textColours.liveTicket = ticket
                guard let result = await engine.live(p, hot: hot, ticket: ticket), !ticket.isCancelled(), let image = result.cgImage else { continue }
                // Only while the full preview of the settings shown is not there yet.
                if textColours.sheet != nil, recent[params] == nil { preview = image }
            }
        }
    }

    // ── Swatches and "Stand out" ──

    /// Looks at the picture for the swatches while the colour group is open, once the picture rests; a change to
    /// the text alone keeps the colours found (Android's `prepareTextSwatches`).
    func prepareTextSwatches() {
        let key = colours.sampleKey(params: params)
        if let sample = textColours.sample, sample.key == key { return }
        textColours.sampleTicket?.cancel()
        textColours.sampleTask?.cancel()
        let ticket = RenderTicket()
        textColours.sampleTicket = ticket
        textColours.sampleTask = Task {
            try? await Task.sleep(for: TextColourTiming.sampleDelay)
            guard !Task.isCancelled else { return }
            // Not drawn (cancelled): the swatches stay as they were.
            _ = await samplePicture(params, ticket: ticket)
        }
    }

    /// The picture without its text, small, with its colours; the kept one while everything but the text is the same.
    private func samplePicture(_ p: MandalaParameters, ticket: RenderTicket) async -> TextPictureSample? {
        let key = colours.sampleKey(params: p)
        if let sample = textColours.sample, sample.key == key { return sample }
        guard let sample = await engine.textSample(p, ticket: ticket), !ticket.isCancelled() else { return nil }
        textColours.keep(sample)
        return sample
    }

    /// Glow in the complement of the picture behind the text, letters near white (DM-24); taps while it works are ignored.
    func standOut() {
        guard textColours.standOutTask == nil else { return }
        textColours.standOutTask = Task {
            defer { textColours.standOutTask = nil }
            let p = params
            guard let sample = await samplePicture(p, ticket: RenderTicket()) else { return }
            // Applied to the settings as they are now; the picture behind the text is the one looked at.
            if let next = colours.withStandOut(params: params, sample: sample) { change(next) }
        }
    }
}

/// How long the picture rests before the swatches look at it (Android's `SAMPLE_DELAY_MS`).
enum TextColourTiming {
    static let sampleDelay = Duration.milliseconds(600)
}
