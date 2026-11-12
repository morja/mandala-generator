#if DEBUG
import Foundation
@preconcurrency import MandalaCore

/// Debug only: measures how the editor follows a slider on the phone, where an agent cannot drag
/// (IOS-1). Launch with `-dragProbe YES`: it opens the heaviest starter (most visible layers), waits for
/// its preview, then moves Scale like a finger three times (a step every 50 ms for one second) and logs
/// when the picture caught up after each drag ("DAHLIA preview … shownAfterChange=…").
@MainActor
enum DragProbe {
    static func runIfAsked(_ model: EditorModel) async {
        guard UserDefaults.standard.bool(forKey: "dragProbe"),
              let heavy = model.favorites.max(by: { visible($0) < visible($1) }) else { return }
        Engine.trace("dragProbe favourite layers=\(visible(heavy)) styles=\(heavy.parameters.layers.map { $0.style.name })")
        model.applyFavorite(heavy)
        await waitForPreview(model)
        let start = model.value(.scale)
        for round in 0..<3 {
            Engine.trace("dragProbe drag \(round + 1)")
            for step in 1...20 {
                // Each drag ends somewhere else, so no picture comes from the recent ones.
                model.setValue(.scale, start * (1 - (0.01 + 0.003 * Double(round)) * Double(step)))
                try? await Task.sleep(for: .milliseconds(50))
            }
            await waitForPreview(model)
            model.setValue(.scale, start)
            await waitForPreview(model)
        }
        Engine.trace("dragProbe done")
    }

    private static func visible(_ f: Favorite) -> Int { f.parameters.layers.filter(\.isEnabled).count }

    private static func waitForPreview(_ model: EditorModel) async {
        try? await Task.sleep(for: .milliseconds(200))
        while model.rendering { try? await Task.sleep(for: .milliseconds(20)) }
    }
}
#endif
