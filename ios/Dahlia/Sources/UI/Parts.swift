import SwiftUI
@preconcurrency import MandalaCore

// Recurring parts of the editor, the iOS counterparts of android/app/.../ui/Parts.kt. Screens build from
// these and the tokens only.

/// A section that opens and closes: a framed surface with its title, a short summary while folded, and
/// a chevron (Android's `Group`). `changed` puts a dot beside the title when something inside differs from
/// its default, `trailing` shows something before the chevron (a colour dot), `inset` marks a child section.
struct ControlGroup<Content: View, Trailing: View>: View {
    let title: String
    var summary: String? = nil
    var changed = false
    var inset = false
    let trailing: Trailing
    @State private var open: Bool
    @ViewBuilder let content: () -> Content
    @Environment(\.tokens) private var t

    init(_ title: String, summary: String? = nil, initiallyOpen: Bool = false, changed: Bool = false, inset: Bool = false,
         @ViewBuilder trailing: () -> Trailing, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.summary = summary
        self.changed = changed
        self.inset = inset
        self.trailing = trailing()
        _open = State(initialValue: initiallyOpen)
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                open.toggle()
            } label: {
                HStack(spacing: Spacing.sm) {
                    // The title keeps its line when the summary is empty or long ("Zusätzliche Erlaubnis").
                    Text(title).textRole(.heading).foregroundStyle(t.text).layoutPriority(1)
                    if changed {
                        Circle().fill(t.accent).frame(width: Spacing.sm, height: Spacing.sm).accessibilityLabel(Texts.groupChanged)
                    }
                    Text(summary ?? "").textRole(.note).foregroundStyle(t.textMuted).lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    trailing
                    Image(systemName: open ? "chevron.up" : "chevron.down").textRole(.note).foregroundStyle(t.textMuted)
                }
                .padding(.horizontal, Spacing.md)
                .frame(minHeight: Sizes.icon * 2)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel([title, summary ?? "", changed ? Texts.groupChanged : ""].filter { !$0.isEmpty }.joined(separator: ", "))
            .accessibilityHint(open ? Texts.groupClose(title) : Texts.groupOpen(title))
            .accessibilityValue(open ? Texts.groupStateOpen : Texts.groupStateClosed)
            if open {
                Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2)
                VStack(alignment: .leading, spacing: Spacing.sm, content: content)
                    .padding(.horizontal, Spacing.lg)
                    .padding(.top, Spacing.sm)
                    .padding(.bottom, Spacing.md)
            }
        }
        .background(t.surface, in: RoundedRectangle(cornerRadius: Radius.sm))
        .overlay(RoundedRectangle(cornerRadius: Radius.sm).strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
        .padding(.leading, inset ? Spacing.sm : 0)
    }
}

extension ControlGroup where Trailing == EmptyView {
    init(_ title: String, summary: String? = nil, initiallyOpen: Bool = false, changed: Bool = false, inset: Bool = false,
         @ViewBuilder content: @escaping () -> Content) {
        self.init(title, summary: summary, initiallyOpen: initiallyOpen, changed: changed, inset: inset, trailing: { EmptyView() }, content: content)
    }
}

/// Whether any value differs from its default, pairs of (value, default) (Android's `differs`).
func differs(_ values: (Double, Double)...) -> Bool { values.contains { abs($0.0 - $0.1) > 1e-6 } }

/// Label on the left, slider in the middle, value on the right: one line per setting. A double tap on
/// the line, the slider itself included (QA-10 item 7), puts the setting back to its default, also offered
/// as an accessibility action.
struct SliderRow: View {
    let label: String
    let value: Double
    let range: ClosedRange<Double>
    var step: Double? = nil
    let defaultValue: Double
    /// How the value reads on the right ("45°"); two decimals, or whole steps, otherwise.
    var display: ((Double) -> String)? = nil
    let onChange: (Double) -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        // A saved value outside the range only puts the knob at the end; the value stays until moved.
        let binding = Binding(get: { min(max(value, range.lowerBound), range.upperBound) }, set: { onChange($0) })
        GeometryReader { geo in
            HStack(spacing: 0) {
                // A long word ("Насыщенность") shrinks a little rather than run into the slider.
                Text(label).textRole(.body).foregroundStyle(t.text).lineLimit(1).minimumScaleFactor(0.8)
                    .frame(width: geo.size.width * 0.34, alignment: .leading)
                ResettableSlider(value: binding.wrappedValue, range: range, step: step, label: label,
                                 valueText: display?(value) ?? (step == nil ? String(format: "%.2f", value) : String(Int(value.rounded()))),
                                 accent: t.accent, onChange: onChange, onReset: { onChange(defaultValue) })
                    .frame(width: geo.size.width * 0.52)
                Text(display?(value) ?? (step == nil ? String(format: "%.2f", value) : String(Int(value.rounded()))))
                    .textRole(.value).foregroundStyle(t.textMuted).lineLimit(1)
                    .frame(width: geo.size.width * 0.14, alignment: .trailing)
            }
            .frame(maxHeight: .infinity)
        }
        .frame(minHeight: Sizes.icon * 2)
        .contentShape(Rectangle())
        .simultaneousGesture(TapGesture(count: 2).onEnded { onChange(defaultValue) })
        .accessibilityAction(named: Text(Texts.sliderReset(label))) { onChange(defaultValue) }
    }
}

/// The system slider (UISlider) with a double tap of its own: SwiftUI's `Slider` keeps every touch on its track
/// and knob, so a double tap on the row never reached the slider line (QA-10 item 7). The tap recogniser lets the
/// slider see all touches at once (no delay on a drag) and gives up as soon as a finger moves; scrolling past
/// the slider stays the scroll view's, as with the system slider.
private struct ResettableSlider: UIViewRepresentable {
    let value: Double
    let range: ClosedRange<Double>
    let step: Double?
    let label: String
    /// What VoiceOver reads as the value: the same text as on the right of the row.
    let valueText: String
    let accent: Color
    let onChange: (Double) -> Void
    let onReset: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UISlider {
        let slider = UISlider()
        slider.isContinuous = true
        slider.addTarget(context.coordinator, action: #selector(Coordinator.moved(_:)), for: .valueChanged)
        // Once the finger lifts, the knob goes to the value kept (a whole step, a value the model limited).
        slider.addTarget(context.coordinator, action: #selector(Coordinator.released(_:)), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.doubleTapped))
        tap.numberOfTapsRequired = 2
        tap.cancelsTouchesInView = false
        tap.delaysTouchesEnded = false
        slider.addGestureRecognizer(tap)
        slider.setContentHuggingPriority(.defaultLow, for: .horizontal)
        slider.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return slider
    }

    func updateUIView(_ slider: UISlider, context: Context) {
        context.coordinator.parent = self
        slider.minimumValue = Float(range.lowerBound)
        slider.maximumValue = Float(range.upperBound)
        // Not while a finger moves the knob: the value coming back would only shake it.
        if !slider.isTracking { slider.value = Float(value) }
        slider.minimumTrackTintColor = UIColor(accent) // token-exempt: the accent token handed to UIKit
        slider.accessibilityLabel = label
        slider.accessibilityValue = valueText
        // The row's reset, offered on the slider itself, where VoiceOver lands.
        let reset = onReset
        slider.accessibilityCustomActions = [UIAccessibilityCustomAction(name: Texts.sliderReset(label)) { _ in
            reset()
            return true
        }]
    }

    @MainActor
    final class Coordinator: NSObject {
        var parent: ResettableSlider?

        @objc func moved(_ slider: UISlider) {
            guard let parent else { return }
            let range = parent.range
            var v = Double(slider.value)
            // Whole steps from the lower end, never past the ends.
            if let step = parent.step, step > 0 { v = range.lowerBound + ((v - range.lowerBound) / step).rounded() * step }
            parent.onChange(min(max(v, range.lowerBound), range.upperBound))
        }

        @objc func released(_ slider: UISlider) {
            // After SwiftUI has handed over the value from the last move.
            Task { @MainActor [weak self, weak slider] in
                await Task.yield()
                guard let value = self?.parent?.value, let slider, !slider.isTracking else { return }
                slider.value = Float(value)
            }
        }

        @objc func doubleTapped() { parent?.onReset() }
    }
}

/// A choice among several, like Android's FilterChip: amber ring and label when selected.
struct Chip: View {
    let label: String
    let selected: Bool
    var picture: UIImage? = nil
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                if let picture {
                    Image(uiImage: picture).resizable().aspectRatio(contentMode: .fill)
                        .frame(width: Sizes.swatchSmall, height: Sizes.swatchSmall)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                        .accessibilityHidden(true)
                }
                Text(label).textRole(.body).lineLimit(1)
            }
                .foregroundStyle(selected ? t.accent : t.text)
                .padding(.horizontal, Spacing.md)
                .frame(minHeight: Sizes.icon + Spacing.md)
                .background(selected ? t.accentSoft : .clear, in: RoundedRectangle(cornerRadius: Radius.sm))
                .overlay(RoundedRectangle(cornerRadius: Radius.sm)
                    .strokeBorder(selected ? t.accent : t.outline, lineWidth: selected ? Sizes.swatchRing : Sizes.swatchRing / 2))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// A palette as a round swatch of its colours; an amber ring marks the selected one.
struct Swatch: View {
    let palette: ColorPalette
    let selected: Bool
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            PaletteDot(palette: palette)
                .padding(Sizes.swatchRing * 2)
                .frame(width: Sizes.swatch, height: Sizes.swatch)
                .overlay(Circle().strokeBorder(selected ? t.accent : .clear, lineWidth: Sizes.swatchRing))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(palette.name)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// The palette's colours around a circle (Android's `paletteBrush`), in Display P3 like core's palettes.
struct PaletteDot: View {
    let palette: ColorPalette

    var body: some View {
        let stops = palette.stops.map { stop in
            Gradient.Stop(color: Color(.displayP3, red: stop.color.r, green: stop.color.g, blue: stop.color.b), location: stop.position) // token-exempt: the mandala's own palette from core
        }
        Circle().fill(stops.count < 2 ? AnyShapeStyle(stops.first?.color ?? .clear) : AnyShapeStyle(AngularGradient(stops: stops, center: .center)))
    }
}

/// Lays out its children in rows, breaking to the next row when one does not fit (Android's FlowRow).
struct FlowLayout: Layout {
    var spacing: CGFloat = Spacing.sm

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: rows.last.map { $0.y + $0.height } ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.items {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: bounds.minY + row.y + (row.height - size.height) / 2), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
        }
    }

    private struct Row { var items: [Int] = []; var width: CGFloat = 0; var height: CGFloat = 0; var y: CGFloat = 0 }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var row = Row()
        for (index, view) in subviews.enumerated() {
            let size = view.sizeThatFits(.unspecified)
            if !row.items.isEmpty && row.width + spacing + size.width > width {
                rows.append(row)
                row = Row(y: row.y + row.height + spacing)
            }
            row.width += (row.items.isEmpty ? 0 : spacing) + size.width
            row.height = max(row.height, size.height)
            row.items.append(index)
        }
        if !row.items.isEmpty { rows.append(row) }
        return rows
    }
}

/// A thin bar sliding along the bottom of the picture while it is drawn (Android's indeterminate bar).
struct BusyBar: View {
    @Environment(\.tokens) private var t
    @State private var phase = false

    var body: some View {
        GeometryReader { geo in
            Rectangle().fill(t.accent)
                .frame(width: geo.size.width / 3)
                .offset(x: phase ? geo.size.width : -geo.size.width / 3)
        }
        .frame(height: Sizes.progress)
        .clipped()
        .onAppear {
            withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) { phase = true }
        }
        .accessibilityHidden(true)
    }
}

/// A short message at the bottom, inverted like Android's snackbar, with an optional action.
struct MessageBar: View {
    let message: Message
    let dismiss: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        HStack(spacing: Spacing.md) {
            Text(message.text).textRole(.body).foregroundStyle(t.background)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let label = message.actionLabel, let action = message.action {
                Button(label) {
                    action()
                    dismiss()
                }
                .textRole(.heading)
                .foregroundStyle(t.inverseAccent)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.md)
        .background(t.text, in: RoundedRectangle(cornerRadius: Radius.sm))
        .padding(.horizontal, Spacing.lg)
        .task(id: message.id) {
            try? await Task.sleep(for: .seconds(message.action == nil ? 4 : 6))
            dismiss()
        }
    }
}


/// A labelled on/off row; the whole row switches (Android's `SwitchRow`).
struct SwitchRow: View {
    let label: String
    let checked: Bool
    let onChange: (Bool) -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        HStack {
            Text(label).textRole(.body).foregroundStyle(t.text)
                .frame(maxWidth: .infinity, alignment: .leading)
            Toggle("", isOn: Binding(get: { checked }, set: { onChange($0) })).labelsHidden().tint(t.accent)
                .accessibilityLabel(label)
        }
        .frame(minHeight: Sizes.icon * 2)
        .contentShape(Rectangle())
        .onTapGesture { onChange(!checked) }
    }
}

/// A direct random action with a visible explanation of what changes: label, muted detail and a die on the
/// right where a slider ends (Android's `RandomActionRow`).
struct RandomActionRow: View {
    let label: String
    var detail: String? = nil
    let description: String
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                Text(label).textRole(.body).foregroundStyle(t.text)
                Text(detail ?? "").textRole(.note).foregroundStyle(t.textMuted).lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "dice").foregroundStyle(t.accent)
            }
            .frame(minHeight: Sizes.icon * 2)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityHint(description)
    }
}

/// A muted line saying what comes with Pro. The Pro sheet and its "About Pro" button come with the purchase (WP-9).
struct ProNote: View {
    let text: String
    @Environment(\.tokens) private var t

    var body: some View {
        Text(text).textRole(.note).foregroundStyle(t.textMuted).padding(.top, Spacing.sm)
    }
}

/// A large action with its icon above the label, several side by side (Android's `ActionTile`).
struct ActionTile: View {
    let icon: String
    let label: String
    let enabled: Bool
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            VStack(spacing: Spacing.xs) {
                Image(systemName: icon).foregroundStyle(enabled ? t.accent : t.textMuted)
                Text(label).textRole(.note).foregroundStyle(enabled ? t.text : t.textMuted).lineLimit(1).minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(t.surfaceRaised, in: RoundedRectangle(cornerRadius: Radius.md))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// A small muted heading with a line after it, inside a group (Android's `SubHeading`).
struct SubHeading: View {
    let text: String
    @Environment(\.tokens) private var t

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Text(text).textRole(.note).foregroundStyle(t.textMuted)
            Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2)
        }
        .padding(.top, Spacing.md)
    }
}

/// A colour from hue, saturation and brightness in 0…1, as the own palettes and the background store it.
func hsbColour(_ h: Double, _ s: Double, _ b: Double) -> Color {
    Color(hue: min(max(h, 0), 1), saturation: min(max(s, 0), 1), brightness: min(max(b, 0), 1)) // token-exempt: the user's own colour
}

/// Hue, saturation and brightness in one section, with the colour as its summary dot (Android's `ColourGroup`).
struct ColourGroup: View {
    let title: String
    let hue: Double
    let saturation: Double
    let brightness: Double
    /// Defaults of hue, saturation and brightness, for the double-tap reset.
    let defaults: (Double, Double, Double)
    var inset = false
    var brightnessRange: ClosedRange<Double> = 0...1
    var initiallyOpen = false
    let onChange: (Double, Double, Double) -> Void

    var body: some View {
        ControlGroup(title, initiallyOpen: initiallyOpen, changed: differs((hue, defaults.0), (saturation, defaults.1), (brightness, defaults.2)), inset: inset,
                     trailing: { ColourDot(colour: hsbColour(hue, saturation, brightness)) }) {
            SliderRow(label: Texts.hue, value: hue, range: 0...1, defaultValue: defaults.0) { onChange($0, saturation, brightness) }
            SliderRow(label: Texts.saturation, value: saturation, range: 0...1, defaultValue: defaults.1) { onChange(hue, $0, brightness) }
            SliderRow(label: Texts.brightness, value: brightness, range: brightnessRange, defaultValue: defaults.2) { onChange(hue, saturation, $0) }
        }
    }
}

struct ColourDot: View {
    let colour: Color

    var body: some View {
        Circle().fill(colour).frame(width: Sizes.swatchSmall, height: Sizes.swatchSmall).accessibilityHidden(true)
    }
}

/// The head of the Emblem and Text tabs (and of the effects): the label, which switches too, the die and
/// Reset while it is on, and the switch (Android's row in `EmblemTab` and `TextTab`).
struct SwitchHeader: View {
    let label: String
    let checked: Bool
    let dieLabel: String
    let onChange: (Bool) -> Void
    let onDie: () -> Void
    let onReset: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        HStack(spacing: 0) {
            Text(label).textRole(.body).foregroundStyle(t.text)
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(minHeight: Sizes.icon * 2)
                .contentShape(Rectangle())
                .onTapGesture { onChange(!checked) }
                .accessibilityHidden(true)
            if checked {
                Button(action: onDie) {
                    Image(systemName: "dice").foregroundStyle(t.accent).frame(width: Sizes.icon * 2, height: Sizes.icon * 2).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(dieLabel)
                Button(action: onReset) {
                    Text(Texts.reset).textRole(.heading).foregroundStyle(t.accent).frame(minHeight: Sizes.icon * 2)
                }
                .buttonStyle(.plain)
                .padding(.horizontal, Spacing.sm)
            }
            Toggle("", isOn: Binding(get: { checked }, set: { onChange($0) }))
                .labelsHidden()
                .tint(t.accent)
                .accessibilityLabel(label)
        }
        .frame(minHeight: Sizes.icon * 2)
    }
}

/// A small caption above a wrapping row of chips (Android's `ChipLine`).
struct ChipLine<Chips: View>: View {
    let caption: String
    @ViewBuilder let chips: () -> Chips
    @Environment(\.tokens) private var t

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(caption).textRole(.note).foregroundStyle(t.textMuted).padding(.top, Spacing.xs)
            FlowLayout { chips() }
        }
    }
}

/// A filled action (Android's `Button`), or the tonal "Done" while its mode runs (`FilledTonalButton`).
struct ModeButton: View {
    let label: String
    let active: Bool
    var enabled = true
    var wide = false
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            Text(label).textRole(.heading).lineLimit(1)
                .foregroundStyle(active ? t.accent : (enabled ? t.onAccent : t.textMuted))
                .padding(.horizontal, Spacing.lg)
                .frame(maxWidth: wide ? .infinity : nil, minHeight: Sizes.icon * 2)
                .background(active ? t.accentSoft : (enabled ? t.accent : t.surfaceRaised), in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// An outlined round icon action (Android's `OutlinedIconButton`).
struct OutlinedIconButton: View {
    let system: String
    let label: String
    let enabled: Bool
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            Image(systemName: system).foregroundStyle(enabled ? t.text : t.textMuted)
                .frame(width: Sizes.icon * 2, height: Sizes.icon * 2)
                .overlay(Circle().strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

/// Where a layer or the emblem sits (ADR-0011): across and up and down from the centre, "Centre", and
/// "Move on image", which turns drags on the picture into moves. Pro; without it a note says so
/// (Android's `PositionGroup`).
struct PositionGroup: View {
    let x: Double
    let y: Double
    let target: MoveTarget
    var hidden = false
    let onChange: (Double, Double) -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let centred = x == 0 && y == 0
        let moved = Texts.positionMoved
        let limit = Editing.shared.MAX_OFFSET
        ControlGroup(Texts.position, summary: centred ? Texts.positionCentred : (model.proOwned ? moved : Texts.withPro(moved))) {
            if !model.proOwned { ProNote(text: Texts.proNotePosition) }
            if hidden { Text(Texts.positionHiddenNote).textRole(.note).foregroundStyle(t.textMuted) }
            SliderRow(label: Texts.across, value: x, range: -limit...limit, defaultValue: 0) { onChange($0, y) }
            SliderRow(label: Texts.upAndDown, value: y, range: -limit...limit, defaultValue: 0) { onChange(x, $0) }
            HStack(spacing: 0) {
                Button { onChange(0, 0) } label: {
                    Text(Texts.centre).textRole(.heading).foregroundStyle(centred ? t.textMuted : t.accent).frame(minHeight: Sizes.icon * 2)
                }
                .buttonStyle(.plain)
                .disabled(centred)
                Spacer(minLength: Spacing.sm)
                MoveButton(target: target, enabled: !hidden)
            }
        }
    }
}

/// "Move on image", and "Done" while drags on the picture move `target` (Android's `MoveButton`).
struct MoveButton: View {
    let target: MoveTarget
    var enabled = true
    @Environment(EditorModel.self) private var model

    var body: some View {
        let active = model.moveTarget == target
        ModeButton(label: active ? Texts.done : Texts.moveOnImage, active: active, enabled: active || enabled) {
            model.setMoveTarget(active ? nil : target)
        }
    }
}
