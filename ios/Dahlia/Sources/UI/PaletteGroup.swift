import SwiftUI
@preconcurrency import MandalaCore

/// An own palette while it is being made or changed; `before` is what the layer had, for Cancel.
struct PaletteDraft {
    var palette: CustomPalette
    let isNew: Bool
    var selected: Int
    let beforeIndex: Int
    let beforeOwn: CustomPalette?
}

/// The palette of a layer (Android's `PaletteGroup`, ADR-0021): the offered built-in palettes, one that came
/// with the picture, the user's own ones with a plus to make one, and the editor for them in place, so the
/// picture above shows every change. The rules (fallback palette, saving, deleting) are the shared module's.
struct PaletteGroup<More: View>: View {
    /// "Palette" for a layer, "Colour" for the emblem.
    var title: String = Texts.palette
    let index: Int
    let own: CustomPalette?
    /// A built-in palette chosen (replaces an own one).
    let choose: (Int) -> Void
    /// An own palette chosen or changed.
    let useOwn: (CustomPalette) -> Void
    /// Index and own palette exactly as they were (Cancel).
    let restore: (Int, CustomPalette?) -> Void
    /// More controls after the palettes (the emblem's colour sliders), as Android's `more`.
    @ViewBuilder var more: () -> More

    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var editing: PaletteDraft?
    @State private var pictureTask: Task<Void, Never>?

    var body: some View {
        let editor = IosEditing.shared
        let palettes = editor.palettes()
        let shown = own.flatMap { $0.stops.isEmpty ? nil : $0 }
        let builtIn = palettes[max(0, min(index, palettes.count - 1))]
        let mine = model.myPalettes
        ControlGroup(title, summary: (editing?.palette ?? shown)?.name ?? builtIn.name) {
            if let draft = editing {
                PaletteEditor(draft: draft, onChange: { next in
                    if next.palette.stops != draft.palette.stops { useOwn(next.palette) }
                    editing = next
                }, onFromPicture: {
                    pictureTask?.cancel()
                    pictureTask = Task {
                        // Dropped when another layer is selected meanwhile (this view is then gone), as on Android.
                        guard let stops = await model.coloursFromPicture(), !Task.isCancelled, let d = editing else { return }
                        var next = d
                        next.palette = CustomPalette(id: d.palette.id, name: d.palette.name, stops: stops)
                        next.selected = 0
                        useOwn(next.palette)
                        editing = next
                    }
                }, onSave: {
                    let name = draft.palette.name.trimmingCharacters(in: .whitespacesAndNewlines)
                    model.saveMyPalette(CustomPalette(id: draft.palette.id, name: name.isEmpty ? Texts.paletteDefaultName(mine.count + 1) : name, stops: draft.palette.stops))
                    editing = nil
                }, onCancel: {
                    restore(draft.beforeIndex, draft.beforeOwn)
                    editing = nil
                }, onDelete: {
                    model.deleteMyPalette(draft.palette.id)
                    editing = nil
                })
            } else {
                FlowLayout {
                    ForEach(editor.paletteChoices(current: shown == nil ? Int32(index) : -1).map { Int($0.int32Value) }, id: \.self) { i in
                        Swatch(palette: palettes[i], selected: shown == nil && i == index) { choose(i) }
                    }
                }
                if let foreign = shown, !mine.contains(foreign) {
                    SubHeading(text: Texts.inThisPicture)
                    Swatch(palette: foreign.toColorPalette(), selected: true) {}
                    NameRow(name: foreign.name, action: Texts.paletteKeep) { model.keepPalette(foreign) }
                }
                SubHeading(text: Texts.myPalettes)
                FlowLayout {
                    ForEach(mine, id: \.id) { p in
                        Swatch(palette: p.toColorPalette(), selected: shown == p) { useOwn(p) }
                    }
                    NewPaletteButton {
                        let start = shown?.stops ?? editor.stopsOf(palette: builtIn)
                        let stops = start.map { CustomPaletteStop(id: EditorModel.newPaletteID(), position: $0.position, hue: $0.hue, saturation: $0.saturation, brightness: $0.brightness) }
                        let palette = CustomPalette(id: EditorModel.newPaletteID(), name: Texts.paletteDefaultName(mine.count + 1), stops: stops)
                        editing = PaletteDraft(palette: palette, isNew: true, selected: 0, beforeIndex: index, beforeOwn: own)
                        useOwn(palette)
                    }
                }
                if let current = shown, mine.contains(current) {
                    NameRow(name: current.name, action: Texts.edit) {
                        editing = PaletteDraft(palette: current, isNew: false, selected: 0, beforeIndex: index, beforeOwn: own)
                    }
                }
            }
            more()
        }
        .onDisappear { pictureTask?.cancel() }
    }
}

extension PaletteGroup where More == EmptyView {
    init(title: String = Texts.palette, index: Int, own: CustomPalette?, choose: @escaping (Int) -> Void,
         useOwn: @escaping (CustomPalette) -> Void, restore: @escaping (Int, CustomPalette?) -> Void) {
        self.init(title: title, index: index, own: own, choose: choose, useOwn: useOwn, restore: restore, more: { EmptyView() })
    }
}

/// The plus after the own palettes: makes a new one from the current colours.
private struct NewPaletteButton: View {
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            Image(systemName: "plus").foregroundStyle(t.accent)
                .frame(width: Sizes.swatch - Sizes.swatchRing * 4, height: Sizes.swatch - Sizes.swatchRing * 4)
                .overlay(Circle().strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
                .padding(Sizes.swatchRing * 2)
                .frame(width: Sizes.swatch, height: Sizes.swatch)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Texts.paletteNew)
    }
}

/// The chosen palette's name with one action on the right.
private struct NameRow: View {
    let name: String
    let action: String
    let onAction: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        HStack {
            Text(name).textRole(.body).foregroundStyle(t.text).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onAction) { Text(action).textRole(.heading).foregroundStyle(t.accent).frame(minHeight: Sizes.icon * 2) }
                .buttonStyle(.plain)
        }
    }
}

/// The editor in place: name, the palette as a bar with its stops as handles, hue, saturation and brightness of
/// the chosen stop, "From picture", add and remove colour, Save, Cancel, Delete.
private struct PaletteEditor: View {
    let draft: PaletteDraft
    let onChange: (PaletteDraft) -> Void
    let onFromPicture: () -> Void
    let onSave: () -> Void
    let onCancel: () -> Void
    let onDelete: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        let editor = IosEditing.shared
        let palette = draft.palette
        let stops = palette.stops
        let selected = max(0, min(draft.selected, stops.count - 1))
        let stop = stops[selected]
        let defaults = editor.defaultPaletteStops()[1]

        func withStops(_ next: [CustomPaletteStop], _ sel: Int? = nil) {
            var d = draft
            d.palette = CustomPalette(id: palette.id, name: palette.name, stops: next)
            d.selected = sel ?? selected
            onChange(d)
        }
        func withStop(_ change: (CustomPaletteStop) -> CustomPaletteStop) {
            var next = stops
            next[selected] = change(stop)
            withStops(next)
        }
        func copy(_ s: CustomPaletteStop, id: String? = nil, position: Double? = nil, hue: Double? = nil, saturation: Double? = nil, brightness: Double? = nil) -> CustomPaletteStop {
            CustomPaletteStop(id: id ?? s.id, position: position ?? s.position, hue: hue ?? s.hue, saturation: saturation ?? s.saturation, brightness: brightness ?? s.brightness)
        }

        return VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(draft.isNew ? Texts.paletteNew : Texts.paletteEdit).textRole(.heading).foregroundStyle(t.text)
            TextField(Texts.paletteName, text: Binding(get: { palette.name }, set: { name in
                var d = draft
                d.palette = CustomPalette(id: palette.id, name: name, stops: stops)
                onChange(d)
            }))
            .textRole(.body).foregroundStyle(t.text)
            .padding(Spacing.md)
            .background(t.surfaceRaised, in: RoundedRectangle(cornerRadius: Radius.sm))
            .overlay(RoundedRectangle(cornerRadius: Radius.sm).strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
            PaletteBar(palette: palette.toColorPalette())
                .frame(height: Sizes.swatch)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                .padding(.top, Spacing.xs)
            StopHandles(stops: stops, selected: selected, onSelect: { var d = draft; d.selected = $0; onChange(d) }) { i, position in
                var next = stops
                next[i] = copy(next[i], position: position)
                withStops(next, i)
            }
            SliderRow(label: Texts.hue, value: stop.hue, range: 0...1, defaultValue: defaults.hue) { v in withStop { copy($0, hue: v) } }
            SliderRow(label: Texts.saturation, value: stop.saturation, range: 0...1, defaultValue: defaults.saturation) { v in withStop { copy($0, saturation: v) } }
            SliderRow(label: Texts.brightness, value: stop.brightness, range: 0...1, defaultValue: defaults.brightness) { v in withStop { copy($0, brightness: v) } }
            HStack(spacing: Spacing.sm) {
                ActionTile(icon: "photo", label: Texts.paletteFromPicture, enabled: true, action: onFromPicture)
                ActionTile(icon: "plus", label: Texts.paletteAddColour, enabled: stops.count < Int(IosEditing.shared.MAX_STOPS)) {
                    withStops(editor.addedStop(stops: stops, selected: Int32(selected), newId: EditorModel.newPaletteID()), stops.count)
                }
                ActionTile(icon: "trash", label: Texts.paletteRemoveColour, enabled: stops.count > Int(IosEditing.shared.MIN_STOPS)) {
                    var next = stops
                    next.remove(at: selected)
                    withStops(next, max(0, selected - 1))
                }
            }
            .padding(.top, Spacing.sm)
            Text(Texts.paletteHint).textRole(.note).foregroundStyle(t.textMuted).padding(.top, Spacing.sm)
            HStack(spacing: Spacing.sm) {
                if !draft.isNew {
                    Button(action: onDelete) { Text(Texts.delete).textRole(.heading).foregroundStyle(t.error).frame(minHeight: Sizes.icon * 2) }
                        .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
                Button(action: onCancel) { Text(Texts.cancel).textRole(.heading).foregroundStyle(t.accent).frame(minHeight: Sizes.icon * 2) }
                    .buttonStyle(.plain)
                Button(action: onSave) { Text(Texts.paletteSave).textRole(.heading).padding(.horizontal, Spacing.sm) }
                    .buttonStyle(.borderedProminent).tint(t.accent).foregroundStyle(t.onAccent)
            }
            .padding(.top, Spacing.sm)
        }
    }
}

/// The palette from left to right, at its stops' positions (the editor's bar), in Display P3 like core's palettes.
private struct PaletteBar: View {
    let palette: ColorPalette

    var body: some View {
        let stops = palette.stops.map { stop in
            Gradient.Stop(color: Color(.displayP3, red: stop.color.r, green: stop.color.g, blue: stop.color.b), location: stop.position) // token-exempt: the mandala's own palette from core
        }
        Rectangle().fill(stops.count < 2 ? AnyShapeStyle(stops.first?.color ?? .clear) : AnyShapeStyle(LinearGradient(stops: stops, startPoint: .leading, endPoint: .trailing)))
    }
}

/// The stops as dots under the bar, at their positions: a tap chooses one, a sideways drag moves it.
private struct StopHandles: View {
    let stops: [CustomPaletteStop]
    let selected: Int
    let onSelect: (Int) -> Void
    let onMove: (Int, Double) -> Void
    @Environment(\.tokens) private var t
    @State private var dragStart: Double?

    var body: some View {
        GeometryReader { geo in
            let track = max(geo.size.width - Sizes.swatchSmall, 1)
            ForEach(Array(stops.enumerated()), id: \.offset) { i, s in
                Circle().fill(hsbColour(s.hue, s.saturation, s.brightness))
                    .padding(Sizes.swatchRing)
                    .overlay(Circle().strokeBorder(i == selected ? t.accent : t.outline, lineWidth: Sizes.swatchRing))
                    .frame(width: Sizes.swatchSmall, height: Sizes.swatchSmall)
                    .contentShape(Circle())
                    .offset(x: track * s.position)
                    .gesture(DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            if dragStart == nil {
                                dragStart = s.position
                                onSelect(i)
                            }
                            if v.translation.width != 0, let start = dragStart {
                                onMove(i, min(max(start + v.translation.width / track, 0), 1))
                            }
                        }
                        .onEnded { _ in dragStart = nil })
                    .accessibilityLabel(Texts.paletteColour(i + 1))
                    .accessibilityAddTraits(i == selected ? [.isSelected, .isButton] : .isButton)
                    .accessibilityAction { onSelect(i) }
            }
        }
        .frame(height: Sizes.swatch)
    }
}
