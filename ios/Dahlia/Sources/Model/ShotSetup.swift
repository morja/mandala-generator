#if DEBUG
import Foundation
@preconcurrency import MandalaCore

/// Debug only: puts the editor into a state for screenshots in the simulator, where tapping through is
/// slow (IOS-5). Launch arguments: `-shotFavourite <id prefix>` opens a starter or favourite,
/// `-shotEmblem YES` switches the emblem on, `-shotMove layer|emblem|text` and `-shotDraw lines|spray`
/// start moving or drawing; `-startTab layers|scene|emblem|text|draw` (read by `ControlTabs`) opens a tab.
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

    /// The tab to open at start.
    static var startTab: String? { UserDefaults.standard.string(forKey: "startTab") }
}
#endif
