import AppKit
import SwiftUI

// MARK: - Slider that resets on double-click
//
// SwiftUI's Slider on macOS is an NSSlider that keeps the mouse events to itself, so a gesture
// attached in SwiftUI never sees a double-click on the knob or track. This wraps NSSlider
// directly and catches the second click, like Lightroom does.

struct ResettableSlider: NSViewRepresentable {
    @Binding var value: Double
    var range: ClosedRange<Double>
    var tint: Color
    /// Value a double-click returns to; nil turns the double-click off.
    var reset: Double?

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeNSView(context: Context) -> DoubleClickSlider {
        let slider = DoubleClickSlider(value: value, minValue: range.lowerBound, maxValue: range.upperBound,
                                       target: context.coordinator, action: #selector(Coordinator.changed(_:)))
        slider.isContinuous = true
        slider.setContentHuggingPriority(.defaultLow, for: .horizontal)
        return slider
    }

    func updateNSView(_ slider: DoubleClickSlider, context: Context) {
        context.coordinator.parent = self
        slider.minValue = range.lowerBound
        slider.maxValue = range.upperBound
        if slider.doubleValue != value { slider.doubleValue = value }
        slider.trackFillColor = NSColor(tint)
        switch context.environment.controlSize {
        case .mini:  slider.controlSize = .mini
        case .small: slider.controlSize = .small
        default:     slider.controlSize = .regular
        }
        let coordinator = context.coordinator
        slider.onDoubleClick = reset.map { r in { coordinator.parent.value = r } }
    }

    final class Coordinator: NSObject {
        var parent: ResettableSlider
        init(_ parent: ResettableSlider) { self.parent = parent }
        @objc func changed(_ sender: NSSlider) { parent.value = sender.doubleValue }
    }
}

final class DoubleClickSlider: NSSlider {
    var onDoubleClick: (() -> Void)?

    override func mouseDown(with event: NSEvent) {
        if event.clickCount == 2, let reset = onDoubleClick {
            reset()
            return
        }
        super.mouseDown(with: event)
    }
}
