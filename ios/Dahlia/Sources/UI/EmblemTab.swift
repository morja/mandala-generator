import SwiftUI
@preconcurrency import MandalaCore

/// The small pictures beside the emblem motifs (Android's `EmblemPreviews`): Android's files, copied by
/// `ios/scripts/gen-style-previews`, named `emblem_preview_<motif>`.
enum EmblemPictures {
    @MainActor private static var cache: [String: UIImage] = [:]

    @MainActor static func image(for motif: EmblemMotif) -> UIImage? {
        let name = "emblem_preview_" + motif.name.lowercased()
        if let known = cache[name] { return known }
        guard let url = Bundle.main.url(forResource: name, withExtension: "png"), let image = UIImage(contentsOfFile: url.path) else { return nil }
        cache[name] = image
        return image
    }
}

/// The Emblem tab (Android's `EmblemTab`): the switch with die and Reset, then motif, style and frame,
/// shape, position with "Move on image", colour and blending. The emblem is Pro to keep.
struct EmblemTab: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let parts = IosEditingParts.shared
        let em = model.params.emblemLayer
        VStack(alignment: .leading, spacing: Spacing.sm) {
            // Marked only while off: switched on, the note below says it, and the row needs the room.
            SwitchHeader(label: model.proOwned || em.isEnabled ? Texts.emblemShow : Texts.withPro(Texts.emblemShow), checked: em.isEnabled,
                         dieLabel: Texts.randomEmblem,
                         onChange: { model.apply(parts.emblemSwitched(params: model.params, on: $0)) },
                         onDie: { model.apply(parts.emblemRandomized(params: model.params)) },
                         onReset: { model.apply(parts.emblemReset(params: model.params)) })
            if !em.isEnabled {
                Text(Texts.emblemOffNote).textRole(.note).foregroundStyle(t.textMuted)
            } else {
                if !model.proOwned { ProNote(text: Texts.proNoteEmblem) }
                EmblemControls(em: em)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.sm)
        .padding(.bottom, Spacing.xl)
    }
}

private struct EmblemControls: View {
    let em: EmblemLayerSettings
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let parts = IosEditingParts.shared
        let d = parts.defaultEmblem()
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ControlGroup(Texts.motif, summary: Texts.motif(em.motif), initiallyOpen: true) {
                // The motifs with a small picture each, like the styles.
                FlowLayout {
                    ForEach(parts.emblemMotifs(), id: \.self) { motif in
                        Chip(label: Texts.motif(motif), selected: em.motif == motif,
                             picture: model.chipPreviews ? EmblemPictures.image(for: motif) : nil) {
                            model.apply(parts.emblemMotif(params: model.params, motif: motif))
                        }
                    }
                }
                if em.motif == .flowerOfLife {
                    ChipLine(caption: Texts.variant) {
                        ForEach(parts.flowerVariants(), id: \.self) { v in
                            Chip(label: Texts.flowerVariant(v), selected: em.flowerVariant == v) { model.apply(parts.emblemFlowerVariant(params: model.params, variant: v)) }
                        }
                    }
                }
                if em.motif == .lotus {
                    ChipLine(caption: Texts.petals) {
                        ForEach(parts.lotusPetals().map { Int($0.int32Value) }, id: \.self) { n in
                            Chip(label: String(n), selected: Int(em.petals) == n) { model.apply(parts.emblemPetals(params: model.params, petals: Int32(n))) }
                        }
                    }
                }
                if em.motif.isOrganic { slider(.organic) }
                if em.motif == .treeOfLife {
                    HStack {
                        Text(Texts.emblemShapeSeed(String(em.seed))).textRole(.body).foregroundStyle(t.text)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Button { model.apply(parts.emblemNewShape(params: model.params)) } label: {
                            Text(Texts.newShape).textRole(.heading).foregroundStyle(t.accent).frame(minHeight: Sizes.icon * 2)
                        }
                        .buttonStyle(.plain)
                    }
                    slider(.branches)
                    SwitchRow(label: Texts.roots, checked: em.roots) { model.apply(parts.emblemRoots(params: model.params, roots: $0)) }
                }
            }

            ControlGroup(Texts.styleAndFrame, summary: "\(Texts.rendering(em.rendering)) · \(Texts.frame(em.frame))") {
                ChipLine(caption: Texts.style) {
                    ForEach(parts.emblemRenderings(), id: \.self) { r in
                        Chip(label: Texts.rendering(r), selected: em.rendering == r) { model.apply(parts.emblemRendering(params: model.params, rendering: r)) }
                    }
                }
                ChipLine(caption: Texts.frame) {
                    ForEach(parts.emblemFrames(), id: \.self) { f in
                        Chip(label: Texts.frame(f), selected: em.frame == f) { model.apply(parts.emblemFrame(params: model.params, frame: f)) }
                    }
                }
            }

            ControlGroup(Texts.shape, changed: differs((em.size, d.size), (em.weight, d.weight), (em.rotation, d.rotation))) {
                slider(.size)
                slider(.weight)
                slider(.rotate, degrees: true)
            }

            PositionGroup(x: em.offsetX, y: em.offsetY, target: .emblem) { x, y in
                model.apply(parts.emblemPosition(params: model.params, x: x, y: y))
            }

            PaletteGroup(title: Texts.colour, index: Int(em.paletteIndex), own: em.customPalette,
                         choose: { model.apply(parts.emblemPalette(params: model.params, index: Int32($0))) },
                         useOwn: { model.apply(parts.emblemOwnPalette(params: model.params, own: $0)) },
                         restore: { model.apply(parts.emblemPaletteRestored(params: model.params, index: Int32($0), own: $1)) }) {
                slider(.colourPosition)
                slider(.spread)
                slider(.glow)
                ChipLine(caption: Texts.glow) {
                    ForEach(parts.emblemGlowModes(), id: \.self) { m in
                        Chip(label: Texts.glowMode(m), selected: em.glowMode == m) { model.apply(parts.emblemGlowMode(params: model.params, mode: m)) }
                    }
                }
            }
            .id("emblem")

            ControlGroup(Texts.blending, summary: Texts.blendSummary(em.blendMode, em.opacity),
                         changed: em.blendMode != d.blendMode || differs((em.opacity, d.opacity))) {
                FlowLayout {
                    ForEach(IosEditing.shared.blendModes(), id: \.self) { mode in
                        Chip(label: Texts.blend(mode), selected: em.blendMode == mode) { model.apply(parts.emblemBlend(params: model.params, mode: mode)) }
                    }
                }
                slider(.opacity)
            }
        }
    }

    private func slider(_ s: EmblemSlider, degrees: Bool = false) -> some View {
        SliderRow(label: Texts.slider(s), value: IosEditingParts.shared.emblemValue(params: model.params, slider: s), range: s.min...s.max,
                  defaultValue: s.defaultValue, display: degrees ? { "\(Int($0.rounded()))°" } : nil) {
            model.apply(IosEditingParts.shared.emblemWithValue(params: model.params, slider: s, value: $0))
        }
    }
}
