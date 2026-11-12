import SwiftUI
@preconcurrency import MandalaCore

/// The play field (UX-1 19, Android's `PlaySheet`): the picture, and below it a field where one finger changes many
/// settings at once. "Landscape" lets every setting follow a wave of its own, "Compass" changes light, shape, depth or
/// colour by direction. Done closes it and keeps the mandala as played; Back in the editor undoes it.
struct PlaySheet: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let state = model.shell.play ?? PlayState()
        VStack(spacing: Spacing.md) {
            // The two views alone at the top; every action sits in the row at the bottom.
            HStack(spacing: 0) {
                ForEach(PlayMode.allCases, id: \.self) { mode in
                    Button { model.setPlayMode(mode) } label: {
                        Text(mode == .landscape ? Texts.playMap : Texts.playCompass).textRole(.heading).lineLimit(1)
                            .foregroundStyle(mode == state.mode ? t.accent : t.textMuted)
                            .padding(.vertical, Spacing.md)
                            .frame(maxWidth: .infinity)
                            .overlay(alignment: .bottom) {
                                if mode == state.mode { Rectangle().fill(t.accent).frame(height: Sizes.progress) }
                            }
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(mode == state.mode ? .isSelected : [])
                }
            }
            GeometryReader { geo in
                let side = min(geo.size.width, geo.size.height)
                ZStack {
                    t.canvas
                    if let image = model.preview {
                        Image(decorative: image, scale: 1).resizable().interpolation(.high).aspectRatio(1, contentMode: .fit)
                            .accessibilityLabel(Texts.preview)
                    }
                    if model.rendering { BusyBar().frame(maxHeight: .infinity, alignment: .bottom) }
                }
                .frame(width: side, height: side)
                .clipShape(RoundedRectangle(cornerRadius: Radius.md))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            PlayPad(state: state)
            Text(Texts.playHint).textRole(.note).foregroundStyle(t.textMuted).multilineTextAlignment(.center).frame(maxWidth: .infinity)
            actions(state)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.md)
        .padding(.bottom, Spacing.lg)
        .background(t.background)
        .presentationDetents([.large])
        .presentationBackground(t.background)
        // The field takes its own touches; only Done closes the sheet (Android: a swipe outside the field).
        .interactiveDismissDisabled()
    }

    /// Reset, a new landscape (in the compass: this point as the new middle), the effects and the favourite as icons;
    /// Done is the one filled button, where the thumb goes.
    private func actions(_ state: PlayState) -> some View {
        HStack(spacing: Spacing.sm) {
            iconButton("arrow.counterclockwise", label: Texts.reset, tint: t.accent) { model.resetPlay() }
            if state.mode == .landscape {
                iconButton("die.face.1", label: Texts.playNewMap, tint: t.accent) { model.playAgain() }
            } else {
                iconButton("scope", label: Texts.playFromHere, tint: t.accent) { model.playAgain() }
            }
            // The scene effects on or off: a dark scene can hide the play. Off it is struck through and muted.
            Button { model.setPlayEffects(!state.effects) } label: {
                Image(systemName: "sparkles")
                    .foregroundStyle(state.effects ? t.accent : t.textMuted)
                    .overlay { if !state.effects { Image(systemName: "line.diagonal").foregroundStyle(t.textMuted) } }
                    .frame(width: Sizes.swatch, height: Sizes.swatch)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Texts.playEffects)
            .accessibilityValue(state.effects ? Texts.on : Texts.off)
            .accessibilityAddTraits(.isToggle)
            iconButton(state.keptID == nil ? "star" : "star.fill", label: state.keptID == nil ? Texts.playKeep : Texts.favouriteDelete, tint: t.accent) {
                model.playKeep()
            }
            Button { model.closePlay() } label: {
                Text(Texts.playDone).textRole(.heading).lineLimit(1)
                    .frame(maxWidth: .infinity).padding(.vertical, Spacing.xs)
            }
            .buttonStyle(.borderedProminent)
            .tint(t.accent)
            .foregroundStyle(t.onAccent)
        }
    }

    private func iconButton(_ symbol: String, label: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).foregroundStyle(tint).frame(width: Sizes.swatch, height: Sizes.swatch)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// The field: one finger places the point and drags it.
private struct PlayPad: View {
    let state: PlayState
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    /// Wider than tall, so picture and field both fit on a phone (Android's `PAD_ASPECT`).
    private static let padAspect = 1.3

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: Radius.md).fill(t.surface)
                RoundedRectangle(cornerRadius: Radius.md).strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2)
                if state.mode == .compass {
                    label(Texts.playLight).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    label(Texts.playShape).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                    label(Texts.playDepth).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    label(Texts.playColour).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                }
                // The finger's point.
                Circle().fill(t.accentSoft)
                    .overlay(Circle().strokeBorder(t.accent, lineWidth: Sizes.swatchRing))
                    .frame(width: Sizes.icon * 1.5, height: Sizes.icon * 1.5)
                    .position(x: geo.size.width * state.x, y: geo.size.height * state.y)
                    .allowsHitTesting(false)
            }
            .contentShape(Rectangle())
            .highPriorityGesture(
                DragGesture(minimumDistance: 0).onChanged { value in
                    model.playTouch(x: value.location.x / max(1, geo.size.width), y: value.location.y / max(1, geo.size.height))
                }
            )
        }
        .aspectRatio(Self.padAspect, contentMode: .fit)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Texts.playFieldDescription)
        .accessibilityAdjustableAction { direction in
            // VoiceOver: swipe up or down moves the point up or down.
            model.playTouch(x: state.x, y: state.y + (direction == .increment ? -Self.step : Self.step))
        }
    }

    private static let step = 0.1

    private func label(_ text: String) -> some View {
        Text(text).textRole(.note).foregroundStyle(t.textMuted).padding(Spacing.sm)
    }
}
