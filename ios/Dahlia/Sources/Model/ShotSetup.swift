#if DEBUG
import Foundation
@preconcurrency import MandalaCore

/// Debug only: puts the editor into a state for screenshots in the simulator, where tapping through is
/// slow (IOS-5). Launch arguments: `-shotFavourite <id prefix>` opens a starter or favourite,
/// `-shotEmblem YES` switches the emblem on, `-shotMove layer|emblem|text` and `-shotDraw lines|spray`
/// start moving or drawing; `-startTab layers|scene|emblem|text|draw` (read by `ControlTabs`) opens a tab.
/// The save sheet (WP-8): `-shotSave YES` opens it, `-shotFormat <SaveFormat name>` (SQUARE, SCREEN, FOUR_FIVE …),
/// `-shotVideo YES` the Video tab, `-shotFill YES` Fill, `-shotAdjust YES` with Adjust open (read by `SaveSheet`).
/// `-exportProbe image|image2048|video|video1080` saves once without a tap (`-probeSeconds 30`), for the memory and
/// colour measurements in IOS-7; Photos access is granted beforehand with `xcrun simctl privacy … grant photos-add`.
@MainActor
enum ShotSetup {
    static func runIfAsked(_ model: EditorModel) {
        let d = UserDefaults.standard
        if let prefix = d.string(forKey: "shotFavourite")?.uppercased(),
           let favourite = model.favorites.first(where: { $0.id.hasPrefix(prefix) }) {
            model.applyFavorite(favourite)
        }
        if d.bool(forKey: "shotEmblem") { model.apply(IosEditingParts.shared.emblemSwitched(params: model.params, on: true)) }
        switch d.string(forKey: "shotMove") {
        case "layer": model.setMoveTarget(.layer)
        case "emblem": model.setMoveTarget(.emblem)
        case "text": model.setMoveTarget(.text)
        default: break
        }
        switch d.string(forKey: "shotDraw") {
        case "lines": model.setDrawTool(.lines)
        case "spray": model.setDrawTool(.spray)
        default: break
        }
    }

    /// The save sheet's state for screenshots and the export probe.
    static func saveSetup(_ model: EditorModel) {
        let d = UserDefaults.standard
        if let name = d.string(forKey: "shotFormat"), let f = IosSave.shared.formats().first(where: { $0.name == name }) { model.setSaveFormat(f) }
        if d.object(forKey: "shotVideo") != nil { model.setMoving(d.bool(forKey: "shotVideo")) }
        if d.bool(forKey: "shotFill") { model.setFill(true) }
        if d.integer(forKey: "probeSeconds") > 0 { model.setVideoSeconds(d.integer(forKey: "probeSeconds")) }
    }

    static var openSave: Bool { UserDefaults.standard.bool(forKey: "shotSave") }
    static var openAdjust: Bool { UserDefaults.standard.bool(forKey: "shotAdjust") }

    /// Saves once as asked and logs the time and the peak footprint (the export itself traces them).
    static func exportProbeIfAsked(_ model: EditorModel) async {
        guard let kind = UserDefaults.standard.string(forKey: "exportProbe") else { return }
        saveSetup(model)
        try? await Task.sleep(for: .seconds(2))
        Engine.trace("exportProbe \(kind) start footprint=\(Footprint.current() / 1_048_576)MB")
        switch kind {
        case "image": model.saveImage(large: false)
        case "image2048": model.saveImage(large: true)
        case "video": model.saveVideo(large: false, share: false)
        case "video1080": model.saveVideo(large: true, share: false)
        default: return
        }
        try? await Task.sleep(for: .milliseconds(200))
        while model.save.busy { try? await Task.sleep(for: .milliseconds(100)) }
        Engine.trace("exportProbe \(kind) done message=\(model.message?.text ?? "-")")
    }

    /// The tab to open at start.
    static var startTab: String? { UserDefaults.standard.string(forKey: "startTab") }
}
#endif
