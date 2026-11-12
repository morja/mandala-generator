import SwiftUI
@preconcurrency import MandalaCore

/// The Draw tab (Android's `DrawTab`): glowing lines (drawing layer) or soft spray paint (graffiti layer),
/// each repeated around the centre. "Draw on image" turns drags on the picture into strokes.
struct DrawTab: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    /// The tool the controls show; it becomes the active one with "Draw on image".
    @State private var tool = DrawTool.lines

    var body: some View {
        let parts = IosEditingParts.shared
        let count = Int(parts.strokeCount(params: model.params, tool: tool))
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: Spacing.sm) {
                ForEach(parts.drawTools(), id: \.self) { option in
                    Chip(label: Texts.drawTool(option, count: Int(parts.strokeCount(params: model.params, tool: option))), selected: tool == option) {
                        tool = option
                        if model.drawTool != nil { model.setDrawTool(option) }
                    }
                }
            }
            // One wide action and two small icon buttons, so the row fits in every language without wrapping.
            HStack(spacing: Spacing.sm) {
                let active = model.drawTool != nil
                ModeButton(label: active ? Texts.done : Texts.drawOnImage, active: active, wide: true) {
                    model.setDrawTool(active ? nil : tool)
                }
                OutlinedIconButton(system: "arrow.uturn.backward", label: Texts.undo, enabled: count > 0) {
                    model.apply(parts.withoutLastStroke(params: model.params, tool: tool))
                }
                OutlinedIconButton(system: "trash", label: Texts.clear, enabled: count > 0) {
                    model.apply(parts.withoutStrokes(params: model.params, tool: tool))
                }
            }
            .padding(.bottom, Spacing.sm)
            if count > 0 {
                SwitchRow(label: tool == .lines ? Texts.drawShowLines : Texts.drawShowSpray, checked: parts.drawShown(params: model.params, tool: tool)) {
                    model.apply(parts.drawWithShown(params: model.params, tool: tool, on: $0))
                }
            }
            if tool == .lines { lines } else { spray }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.sm)
        .padding(.bottom, Spacing.xl)
    }

    @ViewBuilder
    private var lines: some View {
        slider(.lineSymmetry)
        colour(Texts.lineColour, .lineHue, .lineSaturation, .lineBrightness)
        ControlGroup(Texts.lineAndLight, changed: blendChanged || changed(.lineWeight, .lineGlow, .lineOpacity)) {
            slider(.lineWeight)
            slider(.lineGlow)
            slider(.lineOpacity)
            blendChips
        }
    }

    @ViewBuilder
    private var spray: some View {
        slider(.spraySymmetry)
        slider(.brushSize)
        slider(.paint)
        colour(Texts.sprayColour, .sprayHue, .spraySaturation, .sprayBrightness)
        ControlGroup(Texts.softnessAndBlending, changed: blendChanged || changed(.softness, .sprayOpacity)) {
            slider(.softness)
            slider(.sprayOpacity)
            blendChips
        }
    }

    private var blendChanged: Bool {
        IosEditingParts.shared.drawBlend(params: model.params, tool: tool) != IosEditingParts.shared.drawDefaultBlend(tool: tool)
    }

    private var blendChips: some View {
        let parts = IosEditingParts.shared
        let current = parts.drawBlend(params: model.params, tool: tool)
        return FlowLayout {
            ForEach(IosEditing.shared.blendModes(), id: \.self) { mode in
                Chip(label: Texts.blend(mode), selected: current == mode) { model.apply(parts.drawWithBlend(params: model.params, tool: tool, mode: mode)) }
            }
        }
    }

    private func value(_ s: DrawSlider) -> Double { IosEditingParts.shared.drawValue(params: model.params, slider: s) }

    private func changed(_ list: DrawSlider...) -> Bool { list.contains { abs(value($0) - $0.defaultValue) > 1e-6 } }

    private func slider(_ s: DrawSlider) -> some View {
        // A saved symmetry above the slider's end shows at the end, as on Android.
        SliderRow(label: Texts.slider(s), value: value(s), range: s.min...s.max, step: s.stepped ? 1 : nil, defaultValue: s.defaultValue) {
            model.apply(IosEditingParts.shared.drawWithValue(params: model.params, slider: s, value: $0))
        }
    }

    private func colour(_ title: String, _ h: DrawSlider, _ s: DrawSlider, _ b: DrawSlider) -> some View {
        ColourGroup(title: title, hue: value(h), saturation: value(s), brightness: value(b),
                    defaults: (h.defaultValue, s.defaultValue, b.defaultValue), initiallyOpen: true) { hv, sv, bv in
            let parts = IosEditingParts.shared
            let one = parts.drawWithValue(params: model.params, slider: h, value: hv)
            let two = parts.drawWithValue(params: one, slider: s, value: sv)
            model.apply(parts.drawWithValue(params: two, slider: b, value: bv))
        }
    }
}
