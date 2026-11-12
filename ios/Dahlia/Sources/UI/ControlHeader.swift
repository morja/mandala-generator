import SwiftUI
@preconcurrency import MandalaCore

/// What the open tab is about, in two short lines (beside the folded picture; Android's `ControlHeader`).
struct ControlHeader: Equatable {
    let title: String
    let detail: String

    static func of(_ title: String, tab: ControlTabs.Tab, params: MandalaParameters, selected: Int) -> ControlHeader {
        switch tab {
        case .layers:
            guard params.layers.indices.contains(selected) else { return ControlHeader(title: title, detail: Texts.headerNoLayers) }
            let layer = params.layers[selected]
            return ControlHeader(title: "\(selected + 1) · \(Texts.style(layer.style))", detail: Texts.blendSummary(layer.blendMode, layer.opacity))
        case .scene:
            return ControlHeader(title: title, detail: params.baseLayer.isEnabled ? Texts.background(params.baseLayer.type) : Texts.headerNoBackground)
        default:
            // Emblem, Text and Draw come with WP-6.
            return ControlHeader(title: title, detail: "")
        }
    }
}
