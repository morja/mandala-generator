import SwiftUI
@preconcurrency import MandalaCore

// The text's two colours (DM-24 A2, Mathis 13 November 2026; Android's TextColourParts.kt): the Colour group of
// the Text tab and the colour sheet with its field, hue strip and before/after.

extension Hsb {
    /// The colour as shown in a dot; a text boost above 1 shows at full brightness.
    var shown: Color { hsbColour(hue, saturation, min(brightness, 1)) }
}

/// The Colour group: two dots in its head (Text, Glow) that open the sheet on their side, the Text | Glow
/// switch, the picture's colours plus white with "Stand out", "More colours" and the folded "Fine tune"
/// sliders for the colour the switch shows.
struct TextColourGroup: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let colours = IosTextColours.shared
        let p = model.params
        let state = model.textColours
        let text = colours.text(params: p)
        let glow = colours.glow(params: p)
        let edited = colours.of(params: p, target: state.target)
        ControlGroup(Texts.colour, initiallyOpen: Self.shotOpen, changed: colours.changed(params: p), trailing: {
            HStack(spacing: Spacing.md) {
                HeadDot(label: Texts.textColourText, description: Texts.textColourOpenText, colour: text.shown) { model.openTextColourSheet(.text) }
                HeadDot(label: Texts.textColourGlow, description: Texts.textColourOpenGlow, colour: glow.shown) { model.openTextColourSheet(.glow) }
            }
            // The two labels keep their words; the empty summary beside the title gives way.
            .fixedSize()
        }) {
            TargetSwitch(target: state.target, text: text, glow: glow) { state.target = $0 }
                // The picture's colours are looked at only while the group is open, once the picture rests.
                .task(id: colours.sampleKey(params: p)) { model.prepareTextSwatches() }
            HStack(spacing: Spacing.sm) {
                // The switch above says which colour the swatches set; the label stays one short line.
                Text(Texts.textColourFromPicture).textRole(.note).foregroundStyle(t.textMuted).lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button { model.standOut() } label: {
                    HStack(spacing: Spacing.sm) {
                        Image(systemName: "circle.righthalf.filled")
                        Text(Texts.standOut).textRole(.heading).lineLimit(1)
                    }
                    .foregroundStyle(t.accent)
                    .padding(.horizontal, Spacing.md)
                    .frame(minHeight: Sizes.icon + Spacing.md)
                    .overlay(Capsule().strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Texts.standOutDescription)
            }
            .padding(.top, Spacing.xs)
            SwatchRow(swatches: state.swatches, edited: edited) { model.pickTextColour($0) }
            HStack {
                Spacer(minLength: 0)
                Button { model.openTextColourSheet() } label: {
                    Text(Texts.moreColours).textRole(.heading).foregroundStyle(t.accent).frame(minHeight: Sizes.icon * 2)
                }
                .buttonStyle(.plain)
            }
            FineTune(target: state.target, edited: edited)
        }
        .id(Self.scrollID)
    }
}

extension TextColourGroup {
    /// Where the Text tab scrolls for the colour screenshots.
    static let scrollID = "textColourGroup"

    /// Debug screenshots (`-shotTextColour`) open the group; the app opens it folded.
    static var shotOpen: Bool {
        #if DEBUG
        ShotSetup.textColour != nil
        #else
        false
        #endif
    }
}

/// A label with a colour dot in the group's head; a tap opens the sheet on that colour.
private struct HeadDot: View {
    let label: String
    let description: String
    let colour: Color
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.xs) {
                Text(label).textRole(.note).foregroundStyle(t.textMuted).lineLimit(1)
                Circle().fill(colour).frame(width: Sizes.swatchSmall, height: Sizes.swatchSmall)
                    .overlay(Circle().strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
            }
            .frame(minHeight: Sizes.icon * 2)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(description)
    }
}

/// The Text | Glow switch, each side with its colour; the same in the group and in the sheet (Android's
/// segmented buttons).
private struct TargetSwitch: View {
    let target: TextColourTarget
    let text: Hsb
    let glow: Hsb
    let onTarget: (TextColourTarget) -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        HStack(spacing: 0) {
            side(.text, Texts.textColourText, text)
            Rectangle().fill(t.outline).frame(width: Sizes.swatchRing / 2)
            side(.glow, Texts.textColourGlow, glow)
        }
        .frame(minHeight: Sizes.icon * 2)
        // Its own height only: in the sheet the room left over must not stretch it.
        .fixedSize(horizontal: false, vertical: true)
        .clipShape(Capsule())
        .overlay(Capsule().strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
    }

    private func side(_ value: TextColourTarget, _ label: String, _ colour: Hsb) -> some View {
        let selected = target == value
        return Button { onTarget(value) } label: {
            HStack(spacing: Spacing.sm) {
                Circle().fill(colour.shown).frame(width: Spacing.lg, height: Spacing.lg)
                    .overlay(Circle().strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
                Text(label).textRole(.heading).foregroundStyle(selected ? t.accent : t.text).lineLimit(1)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(selected ? t.accentSoft : .clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// The picture's colours plus white in one row; the swatches shrink on a narrow phone.
private struct SwatchRow: View {
    let swatches: [Hsb]
    let edited: Hsb
    let onPick: (Hsb) -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        let slots = Int(IosTextColours.shared.pictureSwatchCount()) + 1
        let whiteSwatch = IosTextColours.shared.white()
        GeometryReader { geo in
            let size = min(Sizes.swatch, (geo.size.width - Spacing.xs * CGFloat(slots - 1)) / CGFloat(slots))
            HStack(spacing: Spacing.xs) {
                ForEach(Array(swatches.enumerated()), id: \.offset) { i, c in
                    let chosen = IosTextColours.shared.matches(a: c, b: edited)
                    Button { onPick(c) } label: {
                        Circle().fill(c.shown)
                            .overlay(Circle().strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
                            .padding(chosen ? Sizes.swatchRing * 2 : 0)
                            .overlay(Circle().strokeBorder(chosen ? t.accent : .clear, lineWidth: Sizes.swatchRing))
                            .frame(width: size, height: size)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(c == whiteSwatch ? Texts.whiteColour : Texts.pictureColour(i + 1))
                    .accessibilityValue(chosen ? Texts.chosen : "")
                    .accessibilityAddTraits(chosen ? .isSelected : [])
                }
                // Room for the colours still to come, so white keeps its place while the picture is looked at.
                ForEach(0..<max(0, slots - swatches.count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity) }
            }
        }
        .frame(height: Sizes.icon * 2)
    }
}

/// "Fine tune", folded: hue, saturation and brightness of the colour the switch shows.
private struct FineTune: View {
    let target: TextColourTarget
    let edited: Hsb
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var open = Self.shotOpen

    /// Debug screenshots (`-shotTextColour fineTune`) open it; the app opens it folded.
    private static var shotOpen: Bool {
        #if DEBUG
        ShotSetup.textColour == "fineTune"
        #else
        false
        #endif
    }

    var body: some View {
        let colours = IosTextColours.shared
        Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2)
        Button { open.toggle() } label: {
            HStack {
                Text(Texts.fineTune).textRole(.body).foregroundStyle(t.text).frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: open ? "chevron.up" : "chevron.down").textRole(.note).foregroundStyle(t.textMuted)
            }
            .frame(minHeight: Sizes.icon * 2)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityValue(open ? Texts.groupStateOpen : Texts.groupStateClosed)
        if open {
            // The text keeps its boosted range (up to 2, as saved files may have it); the glow goes to 1. The glow's
            // defaults are the text colour: a double tap brings it back to the letters' colour.
            let d = colours.fineTuneDefaults(params: model.params, target: target)
            let e = edited
            SliderRow(label: Texts.hue, value: e.hue, range: 0...1, defaultValue: d.hue) {
                model.setTextColour(Hsb(hue: $0, saturation: e.saturation, brightness: e.brightness))
            }
            SliderRow(label: Texts.saturation, value: e.saturation, range: 0...1, defaultValue: d.saturation) {
                model.setTextColour(Hsb(hue: e.hue, saturation: $0, brightness: e.brightness))
            }
            SliderRow(label: Texts.brightness, value: e.brightness, range: 0...colours.brightnessMax(target: target), defaultValue: d.brightness) {
                model.setTextColour(Hsb(hue: e.hue, saturation: e.saturation, brightness: $0))
            }
        }
    }
}

// ── The colour sheet ──

/// The colour sheet over the tab area only, so the picture above stays in full view (Android's
/// `TextColourSheetOverlay`): the switch, "Same as text" for the glow, before and after (the same crop of the
/// picture behind the text, as it was when the sheet opened and as it is now), the saturation-brightness field
/// and the hue strip. Dragging changes the picture at once (a small render while the finger moves). Cancel, a tap
/// on the shade above and a swipe down on the sheet's head put both colours back; Done keeps them.
struct TextColourSheetView: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    /// Material's scrim behind a bottom sheet, 32 % of the canvas black, as Android's `BottomSheetDefaults.ScrimColor`.
    private static let scrim = 0.32
    /// How far the head is pulled down before the sheet cancels.
    private static let pullToCancel = Spacing.xl * 2

    var body: some View {
        let colours = IosTextColours.shared
        let p = model.params
        let state = model.textColours
        let edited = colours.of(params: p, target: state.target)
        let area = colours.textArea(params: p).map { $0.doubleValue }
        ZStack(alignment: .bottom) {
            t.canvas.opacity(Self.scrim)
                .contentShape(Rectangle())
                .onTapGesture { model.closeTextColourSheet(keep: false) }
                .accessibilityLabel(Texts.cancel)
                .accessibilityAddTraits(.isButton)
            VStack(spacing: 0) {
                head
                // The whole sheet when it fits under the picture; otherwise it scrolls (a small phone, large text).
                ViewThatFits(in: .vertical) {
                    content(edited: edited, area: area)
                    ScrollView { content(edited: edited, area: area) }
                        .scrollBounceBehavior(.basedOnSize)
                }
            }
            .background(t.surfaceRaised, in: UnevenRoundedRectangle(topLeadingRadius: Radius.md, topTrailingRadius: Radius.md))
            .accessibilityAddTraits(.isModal)
        }
    }

    private func content(edited: Hsb, area: [Double]) -> some View {
        let colours = IosTextColours.shared
        let p = model.params
        let state = model.textColours
        return VStack(alignment: .leading, spacing: 0) {
            TargetSwitch(target: state.target, text: colours.text(params: p), glow: colours.glow(params: p)) { state.target = $0 }
            // One height on both sides, so the sheet does not jump when the switch changes.
            Group {
                if state.target == .glow {
                    SwitchRow(label: Texts.sameAsText, checked: p.textLayer.glowSameAsText) { model.setGlowSameAsText($0) }
                } else {
                    Text(Texts.theLetters).textRole(.note).foregroundStyle(t.textMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .frame(minHeight: Sizes.icon * 2)
            HStack(spacing: Spacing.xs) {
                CompareHalf(image: state.before, area: area, caption: Texts.before)
                CompareHalf(image: model.preview, area: area, caption: Texts.after)
            }
            ColourField(colour: edited) { model.setTextColourField(x: $0, y: $1) }
                .padding(.top, Spacing.md)
            HueStrip(hue: edited.hue) { model.setTextColourHue($0) }
                .padding(.top, Spacing.md)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.bottom, Spacing.lg)
    }

    /// The handle and the title; a swipe down here cancels, like a sheet pulled away.
    private var head: some View {
        VStack(alignment: .leading, spacing: 0) {
            Capsule().fill(t.outline).frame(width: Spacing.xl + Spacing.sm, height: Spacing.xs)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.sm)
            // Cancel and Done in the head, so they stay in view when the rest of the sheet scrolls under a large picture.
            HStack(spacing: Spacing.sm) {
                Text(Texts.textColourSheetTitle).textRole(.title).foregroundStyle(t.text).lineLimit(1).minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityAddTraits(.isHeader)
                Button { model.closeTextColourSheet(keep: false) } label: {
                    Text(Texts.cancel).textRole(.heading).foregroundStyle(t.accent).lineLimit(1)
                        .padding(.horizontal, Spacing.sm).frame(minHeight: Sizes.icon * 2)
                }
                .buttonStyle(.plain)
                ModeButton(label: Texts.done, active: false) { model.closeTextColourSheet(keep: true) }
                    .fixedSize()
            }
            .padding(.bottom, Spacing.sm)
        }
        .padding(.horizontal, Spacing.lg)
        .contentShape(Rectangle())
        .gesture(DragGesture().onEnded { value in
            if value.translation.height > Self.pullToCancel { model.closeTextColourSheet(keep: false) }
        })
    }
}

/// One half of the before/after: the picture's crop around the text, with a caption.
private struct CompareHalf: View {
    let image: CGImage?
    let area: [Double]
    let caption: String
    @Environment(\.tokens) private var t

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottomLeading) {
                t.canvas
                if let image, area.count == 4, let crop = Self.crop(image, area: area, aspect: geo.size.width / max(1, geo.size.height)) {
                    Image(decorative: crop, scale: 1).resizable().interpolation(.high)
                }
                Text(caption).textRole(.note).foregroundStyle(Tokens.dark.text).lineLimit(1)
                    .padding(.horizontal, Spacing.xs)
                    .background(t.canvas.opacity(TextColourSheetView.captionShade), in: RoundedRectangle(cornerRadius: Spacing.xs))
                    .padding(Spacing.xs)
            }
        }
        .frame(height: Sizes.icon * 2 + Spacing.sm)
        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
        .accessibilityElement(children: .combine)
    }

    /// The text area's centre, and a crop of the half's shape around it, as wide as the text box (as on Android).
    static func crop(_ image: CGImage, area: [Double], aspect: Double) -> CGImage? {
        let iw = Double(image.width), ih = Double(image.height)
        let cx = (area[0] + area[2]) / 2 * iw
        let cy = (area[1] + area[3]) / 2 * ih
        var sw = max(1, (area[2] - area[0]) * iw)
        var sh = sw / max(aspect, 0.01)
        if sh > ih { sh = ih; sw = sh * aspect }
        sw = min(sw, iw)
        let sx = min(max(cx - sw / 2, 0), iw - sw)
        let sy = min(max(cy - sh / 2, 0), ih - sh)
        let rect = CGRect(x: sx.rounded(), y: sy.rounded(), width: sw.rounded(), height: sh.rounded())
        return rect.isEmpty ? nil : image.cropping(to: rect)
    }
}

extension TextColourSheetView {
    /// The shade behind a caption on the picture, as Android's 60 % of the canvas.
    static let captionShade = 0.6
}

/// Saturation to the right, brightness up, in the colour's hue; a drag or a tap sets both.
private struct ColourField: View {
    let colour: Hsb
    let onPoint: (Double, Double) -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        GeometryReader { geo in
            let w = max(1, geo.size.width), h = max(1, geo.size.height)
            let x = min(max(colour.saturation, 0), 1) * w
            let y = (1 - min(max(colour.brightness, 0), 1)) * h
            ZStack(alignment: .topLeading) {
                hsbColour(colour.hue, 1, 1)
                LinearGradient(colors: [Tokens.light.surface, Tokens.light.surface.opacity(0)], startPoint: .leading, endPoint: .trailing)
                LinearGradient(colors: [t.canvas.opacity(0), t.canvas], startPoint: .top, endPoint: .bottom)
                Marker(fill: colour.shown)
                    .position(x: x, y: y)
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { v in
                onPoint(v.location.x / w, v.location.y / h)
            })
        }
        .frame(height: Spacing.xl * 5)
        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
        .accessibilityElement()
        .accessibilityLabel(Texts.colourField)
        .accessibilityValue("\(Int((colour.saturation * 100).rounded())) %, \(Int((min(colour.brightness, 1) * 100).rounded())) %")
        .accessibilityAdjustableAction { direction in
            // Brightness up and down, as the field's vertical axis.
            let step = direction == .increment ? 0.05 : -0.05
            onPoint(colour.saturation, 1 - min(max(min(colour.brightness, 1) + step, 0), 1))
        }
    }
}

/// The hues side by side; a drag or a tap sets the hue.
private struct HueStrip: View {
    let hue: Double
    let onHue: (Double) -> Void

    var body: some View {
        GeometryReader { geo in
            let r = geo.size.height / 2
            let span = max(1, geo.size.width - 2 * r)
            ZStack(alignment: .topLeading) {
                LinearGradient(colors: (0...6).map { hsbColour(Double($0) / 6, 1, 1) }, startPoint: .leading, endPoint: .trailing)
                Marker(fill: hsbColour(hue, 1, 1))
                    .position(x: r + min(max(hue, 0), 1) * span, y: r)
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { v in
                onHue(min(max((v.location.x - r) / span, 0), 1))
            })
        }
        .frame(height: Sizes.icon)
        .clipShape(Capsule())
        .accessibilityElement()
        .accessibilityLabel(Texts.hue)
        .accessibilityValue("\(Int((hue * 360).rounded()))°")
        .accessibilityAdjustableAction { direction in
            onHue(min(max(hue + (direction == .increment ? 0.02 : -0.02), 0), 1))
        }
    }
}

/// The round marker on the field and the strip: the colour, a light ring and a thin dark edge, readable on any hue.
private struct Marker: View {
    let fill: Color
    @Environment(\.tokens) private var t

    var body: some View {
        Circle().fill(fill)
            .overlay(Circle().strokeBorder(Tokens.light.surface, lineWidth: Sizes.swatchRing))
            .overlay(Circle().strokeBorder(t.canvas.opacity(TextColourSheetView.captionShade), lineWidth: Sizes.swatchRing / 2).padding(-Sizes.swatchRing / 2))
            .frame(width: Sizes.icon - Spacing.xs, height: Sizes.icon - Spacing.xs)
            .accessibilityHidden(true)
    }
}
