import CoreGraphics
import Foundation
import ImageIO
@preconcurrency import MandalaCore
import Observation

// State of the editor's shell (WP-7): history, the dice, the play field and the picture's size.
// The settings themselves stay in `EditorModel`; its extension in EditorModel+Shell.swift works on both.

/// The two views of the play field (Android's `PlayField.Mode`).
enum PlayMode: Int, CaseIterable {
    case landscape, compass
}

/// The play field while it is open (Android's `PlayState`): the view, where the finger is (0…1 across and down),
/// whether the scene effects are on, and the favourite made by the star, if any.
struct PlayState: Equatable {
    var mode = PlayMode.landscape
    var x = 0.5
    var y = 0.5
    var effects = true
    var keptID: String?
}

/// A new mandala the menu shows next to the current one; nothing is replaced until it is used (Android's `Candidate`).
struct Candidate {
    let kind: NewKind
    let params: MandalaParameters
    var image: CGImage?
    var rendering = true
}

/// How large the picture stands above the controls, as a fraction of its full side (Android's `PreviewSize`).
enum PreviewSize {
    static let full = 1.0
    static let small = 0.32
    /// Below this the picture is too small for the status line (the tab's title stands beside it).
    static let headerBelow = 0.6
}

@MainActor
@Observable
final class ShellState {
    private(set) var canGoBack = false
    private(set) var canGoForward = false
    var play: PlayState?
    var candidate: Candidate?
    var previewFraction: Double

    @ObservationIgnored let history: IosHistory
    @ObservationIgnored var historyTask: Task<Void, Never>?
    @ObservationIgnored var candidateTask: Task<Void, Never>?
    @ObservationIgnored var candidateTicket: RenderTicket?
    /// The mandala when the play field opened: its middle, and what Reset goes back to.
    @ObservationIgnored var playBase: MandalaParameters?
    @ObservationIgnored var playLand: PlayLandscape?
    @ObservationIgnored var previewSaveTask: Task<Void, Never>?

    static let previewFractionKey = "previewFraction"

    init() {
        history = IosShell.shared.makeHistory(saved: HistoryStore.load())
        let saved = UserDefaults.standard.object(forKey: Self.previewFractionKey) as? Double ?? PreviewSize.full
        previewFraction = min(PreviewSize.full, max(PreviewSize.small, saved))
        refreshHistoryState()
    }

    func refreshHistoryState() {
        canGoBack = history.canGoBack
        canGoForward = history.canGoForward
    }

    func setPreviewFraction(_ f: Double) {
        previewFraction = f
        UserDefaults.standard.set(f, forKey: Self.previewFractionKey)
    }
}

/// The history across restarts in `history.json`, in the macOS app's format (Android's `HistoryStore`).
enum HistoryStore {
    static let file = AppFiles.folder.appendingPathComponent("history.json")

    static func load() -> [MandalaParameters] {
        guard let text = try? String(contentsOf: file, encoding: .utf8) else { return [] }
        return IosShell.shared.decodeHistory(json: text)
    }

    static func save(_ entries: [MandalaParameters]) {
        try? AppFiles.write(IosShell.shared.encodeHistory(list: entries), to: file)
    }
}

/// The last finished preview and the settings it belongs to, so the picture stands in at the next start
/// while the first render runs (Android's `PreviewStore`).
enum PreviewStore {
    private static let image = AppFiles.folder.appendingPathComponent("last-preview.png")
    private static let settings = AppFiles.folder.appendingPathComponent("last-preview.json")

    static func save(_ params: MandalaParameters, _ picture: CGImage) {
        guard let png = Pictures.png(picture) else { return }
        // The settings go last: a half-written pair never matches.
        try? FileManager.default.removeItem(at: settings)
        try? png.write(to: image, options: .atomic)
        try? AppFiles.write(IosBridge.shared.encode(params: params), to: settings)
    }

    /// The picture, only when it was drawn from exactly these settings.
    static func load(for params: MandalaParameters) -> CGImage? {
        guard let text = try? String(contentsOf: settings, encoding: .utf8),
              let saved = IosBridge.shared.decodeOrNull(json: text), saved == params,
              let data = try? Data(contentsOf: image), let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        return CGImageSourceCreateImageAtIndex(source, 0, nil)
    }
}

extension IosHistory: @retroactive @unchecked Sendable {}
extension PlayLandscape: @retroactive @unchecked Sendable {}
