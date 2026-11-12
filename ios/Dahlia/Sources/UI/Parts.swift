import SwiftUI
@preconcurrency import MandalaCore

// Recurring parts of the editor, the iOS counterparts of android/app/.../ui/Parts.kt. Screens build from
// these and the tokens only.

/// A section that opens and closes: a framed surface with its title, a short summary while folded, and
/// a chevron (Android's `Group`).
struct ControlGroup<Content: View>: View {
    let title: String
    var summary: String? = nil
    @State private var open: Bool
    @ViewBuilder let content: () -> Content
    @Environment(\.tokens) private var t

    init(_ title: String, summary: String? = nil, initiallyOpen: Bool = false, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.summary = summary
        _open = State(initialValue: initiallyOpen)
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                open.toggle()
            } label: {
                HStack(spacing: Spacing.sm) {
                    Text(title).textRole(.heading).foregroundStyle(t.text)
                    Text(summary ?? "").textRole(.note).foregroundStyle(t.textMuted).lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: open ? "chevron.up" : "chevron.down").textRole(.note).foregroundStyle(t.textMuted)
                }
                .padding(.horizontal, Spacing.md)
                .frame(minHeight: Sizes.icon * 2)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(open ? "open" : "closed")
            if open {
                Rectangle().fill(t.outline).frame(height: 1)
                VStack(alignment: .leading, spacing: Spacing.sm, content: content)
                    .padding(.horizontal, Spacing.lg)
                    .padding(.top, Spacing.sm)
                    .padding(.bottom, Spacing.md)
            }
        }
        .background(t.surface, in: RoundedRectangle(cornerRadius: Radius.sm))
        .overlay(RoundedRectangle(cornerRadius: Radius.sm).strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
    }
}

/// Label on the left, slider in the middle, value on the right: one line per setting. A double tap on
/// the line puts the setting back to its default, also offered as an accessibility action.
struct SliderRow: View {
    let label: String
    let value: Double
    let range: ClosedRange<Double>
    var step: Double? = nil
    let defaultValue: Double
    let onChange: (Double) -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        // A saved value outside the range only puts the knob at the end; the value stays until moved.
        let binding = Binding(get: { min(max(value, range.lowerBound), range.upperBound) }, set: { onChange($0) })
        GeometryReader { geo in
            HStack(spacing: 0) {
                Text(label).textRole(.body).foregroundStyle(t.text).lineLimit(1)
                    .frame(width: geo.size.width * 0.34, alignment: .leading)
                Group {
                    if let step { Slider(value: binding, in: range, step: step) } else { Slider(value: binding, in: range) }
                }
                .tint(t.accent)
                .frame(width: geo.size.width * 0.52)
                Text(step == nil ? String(format: "%.2f", value) : String(Int(value.rounded())))
                    .textRole(.note).foregroundStyle(t.textMuted).monospacedDigit().lineLimit(1)
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

/// A choice among several, like Android's FilterChip: amber ring and label when selected.
struct Chip: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            Text(label).textRole(.body).lineLimit(1)
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
            Gradient.Stop(color: Color(.displayP3, red: stop.color.r, green: stop.color.g, blue: stop.color.b), location: stop.position)
        }
        Circle().fill(stops.count < 2 ? AnyShapeStyle(stops.first?.color ?? .white) : AnyShapeStyle(AngularGradient(stops: stops, center: .center)))
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
