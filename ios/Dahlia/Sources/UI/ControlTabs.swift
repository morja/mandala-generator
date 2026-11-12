import SwiftUI
@preconcurrency import MandalaCore

/// The tabs under the picture: a fixed row of equal text tabs with an amber line under the open one, as
/// Android's PrimaryTabRow. WP-1 has Layers; Scene, Emblem, Text and Draw join in WP-5 and WP-6.
struct ControlTabs: View {
    enum Tab: CaseIterable { case layers }

    @State private var tab = Tab.layers
    @Environment(\.tokens) private var t

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(Tab.allCases, id: \.self) { option in
                    Button {
                        tab = option
                    } label: {
                        Text(label(option)).textRole(.heading).lineLimit(1)
                            .foregroundStyle(option == tab ? t.accent : t.textMuted)
                            .padding(.vertical, Spacing.md)
                            .frame(maxWidth: .infinity)
                            .overlay(alignment: .bottom) {
                                if option == tab { Rectangle().fill(t.accent).frame(height: Sizes.progress) }
                            }
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(option == tab ? [.isSelected, .isHeader] : .isHeader)
                }
            }
            Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2)
            ScrollView {
                switch tab {
                case .layers: LayersTab()
                }
            }
            .scrollDismissesKeyboard(.immediately)
        }
    }

    private func label(_ tab: Tab) -> String {
        switch tab {
        case .layers: Texts.layers
        }
    }
}

/// The Layers tab: the layers from top to bottom, then the selected layer's style, palette and main
/// sliders (Android's `LayersTab` and `LayerControls`, without adding, moving, blending and positions yet).
struct LayersTab: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let layers = model.params.layers
        let layer = model.layer
        let editing = IosEditing.shared
        VStack(alignment: .leading, spacing: Spacing.sm) {
            VStack(spacing: Spacing.xs) {
                ForEach(Array(layers.indices.reversed()), id: \.self) { i in
                    LayerRow(number: i + 1, layer: layers[i], selected: i == model.selected,
                             onSelect: { model.select(i) }, onToggle: { model.setVisible(i, !layers[i].isEnabled) })
                }
            }
            .padding(.bottom, Spacing.xs)

            // Grouped per layer, so each layer keeps which of its groups are open.
            Group {
                ControlGroup(Texts.style, summary: Texts.style(layer.style)) {
                    FlowLayout {
                        ForEach(editing.stylesByName(), id: \.self) { style in
                            Chip(label: Texts.style(style), selected: layer.style == style) { model.setStyle(style) }
                        }
                    }
                }

                let palettes = editing.palettes()
                let builtIn = editing.usesBuiltInPalette(layer: layer)
                ControlGroup(Texts.palette, summary: editing.paletteOf(layer: layer).name) {
                    FlowLayout {
                        ForEach(editing.paletteChoices(current: builtIn ? layer.paletteIndex : -1).map { Int($0.int32Value) }, id: \.self) { i in
                            Swatch(palette: palettes[i], selected: builtIn && Int(layer.paletteIndex) == i) { model.setPalette(i) }
                        }
                    }
                }

                sliders(Texts.shape, group: .shape, open: true)
                sliders(Texts.lightAndColour, group: .light, open: false)
            }
            .id(model.selected)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.sm)
        .padding(.bottom, Spacing.xl)
    }

    private func sliders(_ title: String, group: SliderGroup, open: Bool) -> some View {
        let layer = model.layer
        let shown = IosEditing.shared.sliders(style: layer.style).filter { $0.group == group }
        return ControlGroup(title, initiallyOpen: open) {
            ForEach(shown, id: \.self) { s in
                SliderRow(label: Texts.slider(s), value: model.value(s), range: s.min...s.maxFor(style: layer.style),
                          step: s.stepped ? 1 : nil, defaultValue: s.defaultValue) { model.setValue(s, $0) }
            }
        }
    }
}

/// One layer: its palette, number and style; a tap selects it, the eye hides or shows it.
struct LayerRow: View {
    let number: Int
    let layer: StyleLayer
    let selected: Bool
    let onSelect: () -> Void
    let onToggle: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        HStack(spacing: Spacing.md) {
            PaletteDot(palette: IosEditing.shared.paletteOf(layer: layer))
                .frame(width: Sizes.swatchSmall, height: Sizes.swatchSmall)
            Text("\(number) · \(Texts.style(layer.style))").textRole(.body)
                .foregroundStyle(layer.isEnabled ? t.text : t.textMuted).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onToggle) {
                Image(systemName: layer.isEnabled ? "eye" : "eye.slash")
                    .foregroundStyle(layer.isEnabled ? t.text : t.textMuted)
                    .frame(width: Sizes.icon * 2, height: Sizes.icon * 2)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(layer.isEnabled ? Texts.layerHide(number) : Texts.layerShow(number))
        }
        .padding(.leading, Spacing.md)
        .background(selected ? t.accentSoft : t.surface, in: RoundedRectangle(cornerRadius: Radius.sm))
        .contentShape(Rectangle())
        .onTapGesture(perform: onSelect)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
