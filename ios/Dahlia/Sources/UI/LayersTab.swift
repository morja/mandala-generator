import SwiftUI
@preconcurrency import MandalaCore

/// The small example pictures beside the styles (Android's `StylePreviews`): Android's files, copied by
/// `ios/scripts/gen-style-previews`, named `style_preview_<style>`.
enum StylePictures {
    @MainActor private static var cache: [String: UIImage] = [:]

    @MainActor static func image(for style: MandalaStyle) -> UIImage? {
        let name = "style_preview_" + style.name.lowercased()
        if let known = cache[name] { return known }
        guard let url = Bundle.main.url(forResource: name, withExtension: "png"), let image = UIImage(contentsOfFile: url.path) else { return nil }
        cache[name] = image
        return image
    }
}

/// The Layers tab (Android's `LayersTab` and `LayerControls`): the layers from top to bottom, the actions on
/// the selected one, then its groups: style, palette, blending, shape, position and light and colour.
struct LayersTab: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let layers = model.params.layers
        let layer = model.layer
        let canAdd = layers.count < Int(IosEditing.shared.MAX_LAYERS)
        VStack(alignment: .leading, spacing: Spacing.sm) {
            VStack(spacing: Spacing.xs) {
                ForEach(Array(layers.indices.reversed()), id: \.self) { i in
                    LayerRow(number: i + 1, layer: layers[i], selected: i == model.selected,
                             canMoveUp: i < layers.count - 1, canMoveDown: i > 0,
                             onSelect: { model.select(i) }, onToggle: { model.setVisible(i, !layers[i].isEnabled) },
                             onMoveUp: { model.moveLayer(i, by: 1) }, onMoveDown: { model.moveLayer(i, by: -1) })
                }
            }
            // Without Pro a fourth visible layer can be tried; keeping the mandala then needs Pro (ADR-0016).
            if !model.proOwned && layers.filter(\.isEnabled).count > Int(ProFeatures.shared.FREE_LAYERS) {
                ProNote(text: Texts.proNoteLayers)
            }
            // Actions on the selected (highlighted) layer; its settings follow below.
            HStack(spacing: 0) {
                ActionButton(label: Texts.layersAdd, enabled: canAdd, action: model.addLayer)
                ActionButton(label: Texts.layersDuplicate, enabled: canAdd, action: model.duplicateSelected)
                ActionButton(label: Texts.delete, enabled: layers.count > 1, action: model.removeSelected)
                Spacer(minLength: 0)
                Button(action: model.randomizeSelected) {
                    Image(systemName: "dice").foregroundStyle(t.accent).frame(width: Sizes.icon * 2, height: Sizes.icon * 2).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Texts.layerRandomize(model.selected + 1))
            }
            // Open groups stay open across layers, as on Android; an open palette editor belongs to its layer.
            LayerControls(layer: layer)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.sm)
        .padding(.bottom, Spacing.xl)
    }
}

/// A text action on the selected layer.
private struct ActionButton: View {
    let label: String
    let enabled: Bool
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            Text(label).textRole(.heading).lineLimit(1).minimumScaleFactor(0.75)
                .foregroundStyle(enabled ? t.accent : t.textMuted)
                .padding(.horizontal, Spacing.md)
                .frame(minHeight: Sizes.icon * 2)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// One layer: its palette, number, style and blending; a tap selects it, the eye hides or shows it, the
/// arrows move it up or down the stack.
struct LayerRow: View {
    let number: Int
    let layer: StyleLayer
    let selected: Bool
    let canMoveUp: Bool
    let canMoveDown: Bool
    let onSelect: () -> Void
    let onToggle: () -> Void
    let onMoveUp: () -> Void
    let onMoveDown: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        HStack(spacing: 0) {
            PaletteDot(palette: IosEditing.shared.paletteOf(layer: layer))
                .frame(width: Sizes.swatchSmall, height: Sizes.swatchSmall)
                .padding(.trailing, Spacing.md)
            VStack(alignment: .leading, spacing: 0) {
                Text("\(number) · \(Texts.style(layer.style))").textRole(.body)
                    .foregroundStyle(layer.isEnabled ? t.text : t.textMuted).lineLimit(1)
                Text(Texts.blendSummary(layer.blendMode, layer.opacity)).textRole(.note).foregroundStyle(t.textMuted).lineLimit(1)
            }
            .padding(.vertical, Spacing.xs)
            .frame(maxWidth: .infinity, alignment: .leading)
            RowIcon(system: layer.isEnabled ? "eye" : "eye.slash", label: layer.isEnabled ? Texts.layerHide(number) : Texts.layerShow(number),
                    muted: !layer.isEnabled, enabled: true, action: onToggle)
            RowIcon(system: "arrow.up", label: Texts.layerMoveUp(number), muted: false, enabled: canMoveUp, action: onMoveUp)
            RowIcon(system: "arrow.down", label: Texts.layerMoveDown(number), muted: false, enabled: canMoveDown, action: onMoveDown)
        }
        .padding(.leading, Spacing.md)
        .background(selected ? t.accentSoft : t.surface, in: RoundedRectangle(cornerRadius: Radius.sm))
        .contentShape(Rectangle())
        .onTapGesture(perform: onSelect)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct RowIcon: View {
    let system: String
    let label: String
    let muted: Bool
    let enabled: Bool
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            Image(systemName: system)
                .foregroundStyle(muted || !enabled ? t.textMuted : t.text)
                .frame(width: Sizes.icon * 2, height: Sizes.icon * 2)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

/// The selected layer's groups, in Android's order.
private struct LayerControls: View {
    let layer: StyleLayer
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let editing = IosEditing.shared
        let sel = Int32(model.selected)
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ControlGroup(Texts.style, summary: Texts.style(layer.style)) {
                RandomActionRow(label: Texts.variation, detail: Texts.variationDetail, description: Texts.variationDescription, action: model.varySelected)
                FlowLayout {
                    ForEach(editing.stylesByName(), id: \.self) { style in
                        Chip(label: Texts.style(style), selected: layer.style == style,
                             picture: model.chipPreviews ? StylePictures.image(for: style) : nil) { model.setStyle(style) }
                    }
                }
            }

            PaletteGroup(index: Int(layer.paletteIndex), own: layer.customPalette,
                         choose: { model.setPalette($0) },
                         useOwn: { model.apply(editing.withOwnPalette(params: model.params, layer: sel, own: $0)) },
                         restore: { model.apply(editing.withLayerPalette(params: model.params, layer: sel, index: Int32($0), own: $1)) })
                .id(model.selected)

            ControlGroup(Texts.blending, summary: Texts.blendSummary(layer.blendMode, layer.opacity)) {
                FlowLayout {
                    ForEach(editing.blendModes(), id: \.self) { mode in
                        Chip(label: Texts.blend(mode), selected: layer.blendMode == mode) { model.setBlend(mode) }
                    }
                }
                slider(.opacity, style: layer.style)
            }

            ControlGroup(Texts.shape, initiallyOpen: true) {
                RandomActionRow(label: Texts.seedDetail, description: Texts.seedDescription, action: model.reseedSelected)
                ForEach(shown(.shape), id: \.self) { slider($0, style: layer.style) }
            }

            positionGroup

            ControlGroup(Texts.lightAndColour) {
                ForEach(shown(.light), id: \.self) { slider($0, style: layer.style) }
            }
        }
    }

    private func shown(_ group: SliderGroup) -> [LayerSlider] {
        IosEditing.shared.sliders(style: layer.style).filter { $0.group == group }
    }

    private func slider(_ s: LayerSlider, style: MandalaStyle) -> some View {
        SliderRow(label: Texts.slider(s), value: model.value(s), range: s.min...s.maxFor(style: style),
                  step: s.stepped ? 1 : nil, defaultValue: s.defaultValue) { model.setValue(s, $0) }
    }

    /// Where the layer sits (ADR-0011): across and up and down from the centre, and "Centre". Pro; without it a
    /// note says so. Moving it by hand on the picture comes with WP-6.
    private var positionGroup: some View {
        let centred = layer.offsetX == 0 && layer.offsetY == 0
        let moved = Texts.positionMoved
        return ControlGroup(Texts.position, summary: centred ? Texts.positionCentred : (model.proOwned ? moved : Texts.withPro(moved))) {
            if !model.proOwned { ProNote(text: Texts.proNotePosition) }
            if !layer.isEnabled { Text(Texts.positionHiddenNote).textRole(.note).foregroundStyle(t.textMuted) }
            ForEach(shown(.position), id: \.self) { slider($0, style: layer.style) }
            Button {
                model.apply(IosEditing.shared.withValue(params: IosEditing.shared.withValue(params: model.params, layer: Int32(model.selected), slider: .offsetX, value: 0),
                                                        layer: Int32(model.selected), slider: .offsetY, value: 0))
            } label: {
                Text(Texts.centre).textRole(.heading).foregroundStyle(centred ? t.textMuted : t.accent).frame(minHeight: Sizes.icon * 2)
            }
            .buttonStyle(.plain)
            .disabled(centred)
        }
    }
}
