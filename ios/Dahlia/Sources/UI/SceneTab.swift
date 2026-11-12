import PhotosUI
import SwiftUI
import UniformTypeIdentifiers
@preconcurrency import MandalaCore

/// The Scene tab (Android's `SceneTab`): the effects first, since they are used far more than the
/// background, then the background as its own part. Holding the picture shows it without the effects.
struct SceneTab: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let base = model.params.baseLayer
        VStack(alignment: .leading, spacing: Spacing.sm) {
            EffectsControls()
            Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2).padding(.top, Spacing.md)
            SwitchRow(label: Texts.background, checked: base.isEnabled) {
                model.apply(IosEditing.shared.withBackgroundSwitched(params: model.params, on: $0))
            }
            if base.isEnabled { BackgroundControls(base: base) }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.sm)
        .padding(.bottom, Spacing.xl)
    }
}

// ── Background ──

/// Background behind all layers: one of the macOS types with the fitting sliders. A picture comes from
/// Photos (the system picker, which needs no permission) or from Files.
private struct BackgroundControls: View {
    let base: BaseLayerSettings
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var choosing = false
    @State private var fromPhotos = false
    @State private var fromFiles = false
    @State private var picked: PhotosPickerItem?

    var body: some View {
        let editing = IosEditing.shared
        VStack(alignment: .leading, spacing: Spacing.sm) {
            FlowLayout {
                ForEach(editing.backgroundChoices(current: base), id: \.self) { type in
                    Chip(label: Texts.background(type), selected: base.isEnabled && base.type == type) {
                        if type == .image && base.imageURL == nil { choosing = true } else { model.apply(editing.withBackgroundType(params: model.params, type: type)) }
                    }
                }
            }
            if editing.isOffered(type: base.type) { details(editing) }
        }
        .confirmationDialog(Texts.choosePicture, isPresented: $choosing) {
            Button(Texts.photos) { fromPhotos = true }
            Button(Texts.files) { fromFiles = true }
        }
        .photosPicker(isPresented: $fromPhotos, selection: $picked, matching: .images)
        .onChange(of: picked) { _, item in
            guard let item else { return }
            picked = nil
            Task { if let data = try? await item.loadTransferable(type: Data.self) { model.useBackgroundPicture(data) } }
        }
        .fileImporter(isPresented: $fromFiles, allowedContentTypes: [.image]) { result in
            guard case .success(let url) = result else { return }
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            if let data = try? Data(contentsOf: url) { model.useBackgroundPicture(data) } // read inside the access scope; the copy is made off the main thread
        }
    }

    @ViewBuilder
    private func details(_ editing: IosEditing) -> some View {
        switch base.type {
        case .auto_:
            Text(Texts.backgroundAutoNote).textRole(.note).foregroundStyle(t.textMuted)
        case .image:
            Button { choosing = true } label: {
                Text(Texts.choosePicture).textRole(.heading).foregroundStyle(t.text)
                    .padding(.horizontal, Spacing.lg).frame(minHeight: Sizes.icon * 2)
                    .overlay(RoundedRectangle(cornerRadius: Radius.sm).strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
            }
            .buttonStyle(.plain)
            slider(.pictureStrength)
            slider(.opacity)
        default:
            let twoColours = base.type != .color && base.type != .grain
            ColourGroup(title: twoColours ? Texts.colour1 : Texts.colour, hue: value(.hue), saturation: value(.saturation), brightness: value(.brightness),
                        defaults: (BackgroundSlider.hue.defaultValue, BackgroundSlider.saturation.defaultValue, BackgroundSlider.brightness.defaultValue),
                        brightnessRange: BackgroundSlider.brightness.min...BackgroundSlider.brightness.max) { h, s, b in
                set(.hue, h, .saturation, s, .brightness, b)
            }
            if twoColours {
                ColourGroup(title: Texts.colour2, hue: value(.hue2), saturation: value(.saturation2), brightness: value(.brightness2),
                            defaults: (BackgroundSlider.hue2.defaultValue, BackgroundSlider.saturation2.defaultValue, BackgroundSlider.brightness2.defaultValue),
                            brightnessRange: BackgroundSlider.brightness2.min...BackgroundSlider.brightness2.max) { h, s, b in
                    set(.hue2, h, .saturation2, s, .brightness2, b)
                }
            }
            switch base.type {
            case .gradient:
                SwitchRow(label: Texts.radial, checked: base.isRadial) { model.apply(editing.withBackgroundRadial(params: model.params, radial: $0)) }
                if !base.isRadial { slider(.angle) }
            case .sunburst:
                slider(.rays)
                slider(.sharpness)
            case .grain:
                slider(.grainAmount)
                SwitchRow(label: Texts.colouredGrain, checked: base.grainColored) { model.apply(editing.withColouredGrain(params: model.params, coloured: $0)) }
            default:
                EmptyView()
            }
            slider(.opacity)
        }
    }

    private func value(_ s: BackgroundSlider) -> Double { IosEditing.shared.value(params: model.params, slider: s) }

    private func slider(_ s: BackgroundSlider) -> some View {
        SliderRow(label: Texts.slider(s), value: value(s), range: s.min...s.max, defaultValue: s.defaultValue) {
            model.apply(IosEditing.shared.withValue(params: model.params, slider: s, value: $0))
        }
    }

    /// Hue, saturation and brightness of one colour in one change.
    private func set(_ a: BackgroundSlider, _ av: Double, _ b: BackgroundSlider, _ bv: Double, _ c: BackgroundSlider, _ cv: Double) {
        let editing = IosEditing.shared
        let one = editing.withValue(params: model.params, slider: a, value: av)
        let two = editing.withValue(params: one, slider: b, value: bv)
        model.apply(editing.withValue(params: two, slider: c, value: cv))
    }
}

// ── Effects ──

/// Scene effects over the mandala, by default also over the emblem (ADR-0018): their switch heads the tab,
/// the quick, reliable sliders follow in four groups, the rarer ones under "More".
private struct EffectsControls: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    /// Once shown, the old fade slider stays while this screen is open, so it does not vanish at 0 mid-drag.
    @State private var showOldFade = false

    var body: some View {
        let editing = IosEditing.shared
        let fx = model.params.effectsLayer
        VStack(alignment: .leading, spacing: Spacing.sm) {
            // The label switches too; the die and reset beside it keep their own taps (as on Android).
            HStack(spacing: 0) {
                Text(Texts.showEffects).textRole(.body).foregroundStyle(t.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(minHeight: Sizes.icon * 2)
                    .contentShape(Rectangle())
                    .onTapGesture { model.apply(editing.withEffectsEnabled(params: model.params, on: !fx.isEnabled)) }
                if fx.isEnabled {
                    Button { model.apply(editing.randomizedEffects(params: model.params)) } label: {
                        Image(systemName: "dice").foregroundStyle(t.accent).frame(width: Sizes.icon * 2, height: Sizes.icon * 2).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Texts.randomEffects)
                    Button { model.apply(editing.resetEffects(params: model.params)) } label: {
                        Text(Texts.reset).textRole(.heading).foregroundStyle(t.accent).frame(minHeight: Sizes.icon * 2)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, Spacing.sm)
                }
                Toggle("", isOn: Binding(get: { fx.isEnabled }, set: { model.apply(editing.withEffectsEnabled(params: model.params, on: $0)) }))
                    .labelsHidden()
                    .tint(t.accent)
                    .accessibilityLabel(Texts.showEffects)
            }
            .frame(minHeight: Sizes.icon * 2)
            .tourAnchor(.effectsRow)
            if !fx.isEnabled {
                Text(Texts.effectsOffNote).textRole(.note).foregroundStyle(t.textMuted)
            } else {
                groups(fx)
            }
        }
        .onAppear { if fx.fade > 0 { showOldFade = true } }
        .onChange(of: fx.fade) { _, v in if v > 0 { showOldFade = true } }
    }

    @ViewBuilder
    private func groups(_ fx: EffectsLayerSettings) -> some View {
        let editing = IosEditing.shared
        let d = { (s: EffectSlider) in s.defaultValue }
        let v = { (s: EffectSlider) in editing.value(params: model.params, slider_: s) }
        ControlGroup(Texts.glowAndLight, initiallyOpen: true,
                     changed: differs((v(.brightness), d(.brightness)), (v(.contrast), d(.contrast)), (v(.vignette), d(.vignette)), (v(.bloom), d(.bloom)))) {
            sliders(.brightness, .contrast, .vignette, .bloom)
        }
        ControlGroup(Texts.tone, changed: differs((v(.sepia), d(.sepia)), (v(.fade), d(.fade)))) {
            sliders(.sepia, .fade)
        }
        ControlGroup(Texts.texture, changed: differs((v(.clarity), d(.clarity)), (v(.grain), d(.grain)), (v(.smoother), d(.smoother)))) {
            sliders(.clarity, .grain, .smoother)
        }
        ControlGroup(Texts.sparkle, changed: differs((v(.stars), d(.stars)), (v(.glitter), d(.glitter)))) {
            RandomActionRow(label: Texts.seedDetail, detail: Texts.sparkleArrangementDetail, description: Texts.sparkleArrangementDescription) {
                model.apply(editing.doNewSparklePattern(params: model.params))
            }
            sliders(.stars, .glitter)
        }
        ControlGroup(Texts.more, changed: differs((v(.highlights), d(.highlights)), (v(.dimming), d(.dimming)), (v(.erasure), d(.erasure)),
                                                   (v(.relief), d(.relief)), (v(.chromatic), d(.chromatic)), (v(.oldFade), d(.oldFade)))) {
            RandomActionRow(label: Texts.seedDetail, detail: Texts.lightArrangementDetail, description: Texts.lightArrangementDescription) {
                model.apply(editing.doNewLightPattern(params: model.params))
            }
            sliders(.highlights, .dimming, .erasure, .relief)
            if fx.relief > 0 { sliders(.reliefAngle) }
            sliders(.chromatic)
            if showOldFade { sliders(.oldFade) }
        }
        // Rarely changed, so last and closed; the summary says what the effects reach.
        let fresh = editing.defaultEffects()
        let reach = [Texts.reachMandala] + (fx.affectsEmblem ? [Texts.tabEmblem] : []) + (fx.affectsText ? [Texts.tabText] : [])
        ControlGroup(Texts.appliesTo, summary: reach.joined(separator: ", "),
                     changed: fx.affectsEmblem != fresh.affectsEmblem || fx.affectsText != fresh.affectsText) {
            SwitchRow(label: Texts.tabEmblem, checked: fx.affectsEmblem) { model.apply(editing.withEffectsOnEmblem(params: model.params, on: $0)) }
            SwitchRow(label: Texts.tabText, checked: fx.affectsText) { model.apply(editing.withEffectsOnText(params: model.params, on: $0)) }
        }
    }

    @ViewBuilder
    private func sliders(_ list: EffectSlider...) -> some View {
        ForEach(list, id: \.self) { s in
            SliderRow(label: Texts.slider(s), value: IosEditing.shared.value(params: model.params, slider_: s), range: s.min...s.max, defaultValue: s.defaultValue) {
                model.apply(IosEditing.shared.withValue(params: model.params, slider: s, value_: $0))
            }
        }
    }
}
