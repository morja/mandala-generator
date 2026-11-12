import CoreGraphics
import Foundation
@preconcurrency import MandalaCore
import Observation
import UIKit

// State of the save sheet (WP-8, ADR-0010): the save choice, the frame preview and a running export. The
// operations are in EditorModel+Save.swift, as Android's save sheet parts of `EditorViewModel`.

/// What an export is doing, shown at the top of the sheet (Android's `ExportProgress`); `share` is nil while
/// iOS works on the finished picture.
struct ExportProgress: Equatable {
    var label: String
    var share: Double?
}

/// The save sheet's frame: the picture in the chosen format and the framing it was drawn with, so a finger
/// can move and zoom the last one until the next is drawn (Android's `FramePreviewState`).
struct FramePreview {
    var image: CGImage?
    var framing = IosSave.shared.centred()
    var width = 0
    var height = 0
    var rendering = true
}

/// What the Pro sheet (WP-9) is opened for (Android's `ProRequest`): the Pro features the mandala uses and the
/// step to take again once they are removed, or a plain note (the video, placing by hand, Adjust).
struct ProRequest: Identifiable {
    let id = UUID()
    var features: [ProFeature] = []
    var action: String?
    var note: String?
    var then: (@MainActor () -> Void)?
}

/// What the frame was drawn from; a format or setting shown recently comes back at once.
struct FrameKey: Hashable {
    let params: MandalaParameters
    let choice: SaveChoice
    let aspect: Double
    let pro: Bool
}

@MainActor
@Observable
final class SaveState {
    /// Kept across starts as a convenience, not part of the mandala (Android's `SaveChoiceStore`).
    var choice: SaveChoice { didSet { if choice != oldValue { SaveChoiceStore.save(choice) } } }
    /// The screen's taller side over its shorter side, for "This screen".
    var screenAspect = SaveState.currentScreenAspect()
    var frame = FramePreview()
    /// The framing the sheet shows and pictures are drawn with: the preset for this mandala, or by hand with Pro.
    var shownFraming = IosSave.shared.centred()
    var export: ExportProgress?
    /// An export is running; the sheet's controls wait meanwhile.
    var busy = false
    var makingVideo = false
    /// The large size (2048 px, a 1080 video) chosen in the save button; Pro.
    var large = false
    var open = false
    /// Why the Pro sheet is open; nil while it is closed (Android's `proRequest`).
    var proRequest: ProRequest?

    @ObservationIgnored var frameTask: Task<Void, Never>?
    @ObservationIgnored var frameTicket: RenderTicket?
    @ObservationIgnored var exportTicket: RenderTicket?
    @ObservationIgnored var fillZooms: [FillKey: Double] = [:]
    @ObservationIgnored var fillOrder: [FillKey] = []
    @ObservationIgnored var recentFrames: [FrameKey: FramePreview] = [:]
    @ObservationIgnored var recentOrder: [FrameKey] = []
    /// The frame before its finish, so the finish sliders only finish it again.
    @ObservationIgnored var lastDrawn: (key: FrameKey, drawn: DrawnFrame)?

    struct FillKey: Hashable {
        let params: MandalaParameters
        let aspect: Double
    }

    init() { choice = SaveChoiceStore.load() }

    /// The device's screen: taller side over shorter side, whatever the orientation.
    static func currentScreenAspect() -> Double {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        guard let bounds = scene?.screen.nativeBounds, bounds.width > 0, bounds.height > 0 else { return 19.5 / 9 }
        return max(bounds.width, bounds.height) / min(bounds.width, bounds.height)
    }
}

/// The save choice in the app's preferences, with Android's keys and fallbacks (the codec is the shared module's).
enum SaveChoiceStore {
    private static let key = "saveChoice"

    static func load() -> SaveChoice {
        IosSave.shared.decodeChoice(saved: UserDefaults.standard.dictionary(forKey: key) as? [String: String] ?? [:])
    }

    static func save(_ choice: SaveChoice) {
        UserDefaults.standard.set(IosSave.shared.encodeChoice(choice: choice), forKey: key)
    }
}
