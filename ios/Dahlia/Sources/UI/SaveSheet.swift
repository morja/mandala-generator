import SwiftUI
@preconcurrency import MandalaCore

/// Saving in a format (ADR-0010, Android's `SaveSheet`, laid out as DM-18 A3): Picture | Video as tabs with the format
/// menu beside them in place of a title; the mandala in its frame (placed by hand with Pro) with Whole and Fill beside
/// or below it; one line on placing by hand; "Adjust" with the finish and whether the quote follows; the length for a
/// video; the size in the save button. Nothing scrolls: the frame takes the height the other parts leave. The
/// wallpaper button beside Save comes with WP-11; the line drawing has its own page in the menu, as on Android.
struct SaveSheet: View {
    let close: @MainActor () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var adjustOpen = false

    private var save: SaveState { model.save }

    var body: some View {
        let choice = save.choice
        let pro = model.proOwned
        let busy = save.busy
        let aspect = model.aspect(of: choice.format)
        VStack(spacing: 0) {
            VStack(spacing: Spacing.sm) {
                HStack(spacing: Spacing.sm) {
                    modeTabs(choice.moving, enabled: !busy)
                    formatMenu(choice.format, enabled: !busy)
                }
                FrameWithFit(aspect: aspect, showFit: aspect != 1) {
                    SaveFrame(aspect: aspect, enabled: pro && !busy)
                } fit: { vertical in
                    FitToggle(preset: preset(choice, pro: pro), vertical: vertical, enabled: !busy) { model.setFill($0) }
                }
                frameLine(pro: pro, preset: preset(choice, pro: pro), busy: busy)
                VStack(spacing: 0) {
                    // One line across the sheet: the picture above, what is set and saved below it.
                    Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2).padding(.horizontal, -Spacing.lg)
                    adjust(choice, pro: pro, busy: busy)
                }
                if choice.moving { lengthRow(Int(choice.videoSeconds), enabled: !busy) }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.md)
            .padding(.bottom, Spacing.sm)
            buttons(choice, pro: pro, busy: busy)
                .padding(.horizontal, Spacing.lg)
                .padding(.top, Spacing.md)
                .padding(.bottom, Spacing.lg)
        }
        .overlay(alignment: .top) {
            // Above the frame, the same place as messages (Android's ExportNote).
            if let export = save.export {
                ExportNote(progress: export)
            } else if let message = model.message {
                MessageBar(message: message) { model.message = nil }.padding(.top, Spacing.md)
            }
        }
        .background(t.background)
        .presentationDetents([.large])
        .presentationBackground(t.background)
        // The sheet stays while an export runs, so its Cancel and progress stay in reach.
        .interactiveDismissDisabled(busy)
        .onAppear {
            model.setSaveOpen(true)
            #if DEBUG
            if ShotSetup.openAdjust { adjustOpen = true }
            #endif
        }
        .onDisappear { model.setSaveOpen(false) }
    }

    /// The preset the frame is on; none once a Pro user has moved or zoomed by hand.
    private func preset(_ choice: SaveChoice, pro: Bool) -> Bool? {
        switch choice.fit {
        case .fill: true
        case .hand: pro ? nil : false
        default: false
        }
    }

    // ── Picture or Video, and the format ──

    private func modeTabs(_ moving: Bool, enabled: Bool) -> some View {
        EqualWidthRow {
            ForEach([false, true], id: \.self) { video in
                Button { model.setMoving(video) } label: {
                    Text(video ? Texts.moving : Texts.still).textRole(.heading).lineLimit(1)
                        .foregroundStyle(moving == video ? t.accent : t.textMuted)
                        .padding(.vertical, Spacing.md)
                        .frame(maxWidth: .infinity)
                        .overlay(alignment: .bottom) {
                            if moving == video { Rectangle().fill(t.accent).frame(height: Sizes.progress) }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!enabled)
                .accessibilityAddTraits(moving == video ? .isSelected : [])
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// Square, This screen and the fixed ratios as one menu. Titled "Format", not the chosen one (the frame shows it,
    /// the menu ticks it), so it keeps its width.
    private func formatMenu(_ format: Editing.SaveFormat, enabled: Bool) -> some View {
        Menu {
            ForEach(IosSave.shared.formats(), id: \.self) { f in
                Button { model.setSaveFormat(f) } label: {
                    if f == format { Label(Texts.formatMenuLabel(f), systemImage: "checkmark") } else { Text(Texts.formatMenuLabel(f)) }
                }
            }
        } label: {
            HStack(spacing: Spacing.xs) {
                Text(Texts.formatTitle).textRole(.heading).lineLimit(1)
                Image(systemName: "chevron.down").textRole(.note)
            }
            .foregroundStyle(enabled ? t.accent : t.textMuted)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: Sizes.icon * 2)
            .overlay(Capsule().strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
        }
        .disabled(!enabled)
        .accessibilityValue(Texts.formatLabel(format))
    }

    // ── The line under the frame ──

    /// How to place by hand, or, once placed, that it is and the way back; without Pro, that it comes with Pro.
    /// Two small lines high, so the frame keeps its size when one replaces the other; it gives its room to the
    /// frame while Adjust is open.
    @ViewBuilder
    private func frameLine(pro: Bool, preset: Bool?, busy: Bool) -> some View {
        if !pro {
            HStack(spacing: Spacing.sm) {
                Text(Texts.framePro).textRole(.note).foregroundStyle(t.textMuted).frame(maxWidth: .infinity, alignment: .leading)
                Button(Texts.aboutPro) { model.requestPro(ProRequest(note: Texts.framePro)) }
                    .textRole(.heading).foregroundStyle(t.accent)
            }
        } else if !adjustOpen {
            Group {
                if preset == nil {
                    HStack(spacing: Spacing.sm) {
                        Text(Texts.placedByHand).textRole(.note).foregroundStyle(t.textMuted)
                        Button(Texts.reset) { model.setFill(false) }
                            .textRole(.heading).foregroundStyle(t.accent).disabled(busy)
                    }
                } else {
                    Text(Texts.frameHint).textRole(.note).foregroundStyle(t.textMuted).multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity, minHeight: Spacing.xl + Spacing.sm)
        }
    }

    // ── Adjust: the finish and the quote (ADR-0014, Pro) ──

    private func adjust(_ choice: SaveChoice, pro: Bool, busy: Bool) -> some View {
        let finish = choice.finish
        let quote = model.params.textLayer.isEnabled && !model.params.textLayer.text.isEmpty
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: Spacing.sm) {
                Button { adjustOpen.toggle() } label: {
                    HStack(spacing: Spacing.sm) {
                        Text(Texts.adjust).textRole(.heading).foregroundStyle(t.text)
                        Text(adjustSummary(finish, pro: pro, quoteMoves: quote && choice.textFollowsMandala))
                            .textRole(.note).foregroundStyle(t.textMuted).lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                // Reset in the heading, only when something is changed; it saves the frame a line.
                if pro && !finish.isIdentity {
                    Button(Texts.reset) { model.setFinish(IosSave.shared.identityFinish()) }
                        .textRole(.heading).foregroundStyle(t.accent).disabled(busy)
                }
                Button { adjustOpen.toggle() } label: {
                    Image(systemName: adjustOpen ? "chevron.up" : "chevron.down").textRole(.note).foregroundStyle(t.textMuted)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(adjustOpen ? Texts.groupClose(Texts.adjust) : Texts.groupOpen(Texts.adjust))
            }
            .frame(minHeight: Sizes.icon * 2)
            if adjustOpen {
                if pro {
                    let r = IosSave.shared.finishRanges().map(\.doubleValue)
                    SliderRow(label: Texts.brightness, value: finish.brightness, range: r[0]...r[1], defaultValue: 1, display: percent) {
                        if !busy { model.setFinish(IosSave.shared.makeFinish(brightness: $0, contrast: finish.contrast, saturation: finish.saturation)) }
                    }
                    SliderRow(label: Texts.contrast, value: finish.contrast, range: r[2]...r[3], defaultValue: 1, display: percent) {
                        if !busy { model.setFinish(IosSave.shared.makeFinish(brightness: finish.brightness, contrast: $0, saturation: finish.saturation)) }
                    }
                    SliderRow(label: Texts.saturation, value: finish.saturation, range: r[4]...r[5], defaultValue: 1, display: percent) {
                        if !busy { model.setFinish(IosSave.shared.makeFinish(brightness: finish.brightness, contrast: finish.contrast, saturation: $0)) }
                    }
                    // The quote's place is part of Adjust (Pro, ADR-0016).
                    if quote { SwitchRow(label: Texts.quoteFollows, checked: choice.textFollowsMandala) { model.setTextFollows($0) } }
                } else {
                    HStack(spacing: Spacing.sm) {
                        Text(quote ? Texts.adjustProQuote : Texts.adjustPro).textRole(.note).foregroundStyle(t.textMuted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Button(Texts.aboutPro) { model.requestPro(ProRequest(note: quote ? Texts.adjustProQuote : Texts.adjustPro)) }
                            .textRole(.heading).foregroundStyle(t.accent)
                    }
                    .padding(.bottom, Spacing.sm)
                }
            }
        }
    }

    private func adjustSummary(_ finish: Finish, pro: Bool, quoteMoves: Bool) -> String {
        if !pro { return Texts.adjustWithPro }
        var parts: [String] = []
        if !finish.isIdentity { parts.append(Texts.adjustSummary(percent(finish.brightness), percent(finish.contrast), percent(finish.saturation))) }
        if quoteMoves { parts.append(Texts.adjustQuoteMoves) }
        return parts.isEmpty ? Texts.adjustUnchanged : parts.joined(separator: ", ")
    }

    private func percent(_ v: Double) -> String { "\(Int((v * 100).rounded())) %" }

    // ── The video's length ──

    private func lengthRow(_ seconds: Int, enabled: Bool) -> some View {
        HStack(spacing: Spacing.md) {
            Text(Texts.length).textRole(.body).foregroundStyle(t.text)
            Spacer(minLength: 0)
            Picker(Texts.length, selection: Binding(get: { seconds }, set: { model.setVideoSeconds($0) })) {
                ForEach(IosSave.shared.videoSeconds().map(\.intValue), id: \.self) { s in
                    Text(Texts.videoSeconds(s)).tag(s)
                }
            }
            .pickerStyle(.segmented)
            .fixedSize()
            .disabled(!enabled)
        }
        .frame(minHeight: Sizes.icon * 2)
    }

    // ── Save, Share, Cancel ──

    @ViewBuilder
    private func buttons(_ choice: SaveChoice, pro: Bool, busy: Bool) -> some View {
        let screen = save.screenAspect
        let small = choice.moving ? IosSave.shared.videoSize(format: choice.format, screenAspect: screen, large: false)
            : IosSave.shared.exportSize(format: choice.format, screenAspect: screen, large: false)
        let big = choice.moving ? IosSave.shared.videoSize(format: choice.format, screenAspect: screen, large: true)
            : IosSave.shared.exportSize(format: choice.format, screenAspect: screen, large: true)
        let sizes = [Texts.saveSize(Int(small.width), Int(small.height)),
                     pro ? Texts.saveSize(Int(big.width), Int(big.height)) : Texts.saveSizePro(Int(big.width), Int(big.height))]
        let large = save.large && pro
        let pick: (Int) -> Void = { i in
            if i == 0 { save.large = false } else if pro { save.large = true } else { model.requestPro(ProRequest(note: choice.moving ? Texts.saveVideoPro : nil)) }
        }
        WeightedRow(spacing: Spacing.sm) {
            if choice.moving {
                // A video goes mostly straight to a story or status, so Share stands beside Save.
                // Cancel only while frames are made; once the file is complete it goes to Photos or the share sheet.
                if save.makingVideo && save.export?.share != nil {
                    OutlinedButton(label: Texts.saveCancel, enabled: true) { model.cancelExport() }
                } else {
                    OutlinedButton(label: Texts.shareAction, enabled: !busy) { model.saveVideo(large: large, share: true) }
                }
                // Without Pro the mark goes into the size line, where it has room.
                SaveButton(label: Texts.save, sizes: sizes, chosen: large ? 1 : 0, enabled: !busy, markPro: !pro,
                           onSave: { model.saveVideo(large: large, share: false) }, onSize: pick)
            } else {
                SaveButton(label: Texts.save, sizes: sizes, chosen: large ? 1 : 0, enabled: !busy, markPro: false,
                           onSave: { model.saveImage(large: large) }, onSize: pick)
            }
        }
    }
}

/// Two buttons side by side, the second (Save with its size line and arrow) wider, as Android's weights 1 : 1.4;
/// a single child takes the whole width.
private struct WeightedRow: Layout {
    var spacing: CGFloat
    private static var secondWeight: CGFloat { 1.4 }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let height = zip(subviews, widths(width, subviews.count)).map { $0.sizeThatFits(ProposedViewSize(width: $1, height: nil)).height }.max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        for (view, w) in zip(subviews, widths(bounds.width, subviews.count)) {
            view.place(at: CGPoint(x: x, y: bounds.minY), proposal: ProposedViewSize(width: w, height: bounds.height))
            x += w + spacing
        }
    }

    private func widths(_ total: CGFloat, _ count: Int) -> [CGFloat] {
        guard count > 1 else { return [total] }
        let room = total - spacing
        let first = room / (1 + Self.secondWeight)
        return [first, room - first]
    }
}

/// The frame, as large as the room allows and centred in it. Tall frames (This screen, 9:16, 2:3) have Whole and
/// Fill as two icons beside their lower right corner; the others have them in a row below (Android's `FrameWithFit`).
private struct FrameWithFit<Frame: View, Fit: View>: View {
    let aspect: Double
    let showFit: Bool
    @ViewBuilder let frame: () -> Frame
    @ViewBuilder let fit: (Bool) -> Fit

    /// From this height over width on, Whole and Fill sit beside the frame instead of below it.
    private static var sideFitAspect: Double { 1.5 }

    var body: some View {
        GeometryReader { geo in
            let side = showFit && aspect >= Self.sideFitAspect
            let fitRoom = Sizes.icon * 2 + Spacing.sm
            if side {
                let w = min(geo.size.width - 2 * fitRoom, geo.size.height / aspect)
                let h = w * aspect
                ZStack(alignment: .topLeading) {
                    frame().frame(width: w, height: h)
                        .position(x: geo.size.width / 2, y: geo.size.height / 2)
                    fit(true)
                        .position(x: geo.size.width / 2 + w / 2 + fitRoom / 2, y: geo.size.height / 2 + h / 2 - Sizes.icon * 2 - Spacing.sm / 2)
                }
            } else if !showFit {
                let w = min(geo.size.width, geo.size.height / aspect)
                frame().frame(width: w, height: w * aspect).position(x: geo.size.width / 2, y: geo.size.height / 2)
            } else {
                let below = Sizes.icon * 2 + Spacing.md
                let w = min(geo.size.width, (geo.size.height - below) / aspect)
                let h = w * aspect
                VStack(spacing: Spacing.md) {
                    frame().frame(width: w, height: h)
                    fit(false)
                }
                .frame(width: geo.size.width, height: geo.size.height)
            }
        }
    }
}

/// Whole or Fill as two round icon buttons, stacked beside a tall frame or in a row below the others.
private struct FitToggle: View {
    let preset: Bool?
    let vertical: Bool
    let enabled: Bool
    let onFill: (Bool) -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        let layout = vertical ? AnyLayout(VStackLayout(spacing: Spacing.sm)) : AnyLayout(HStackLayout(spacing: Spacing.sm))
        layout {
            ForEach([false, true], id: \.self) { fill in
                let on = preset == fill
                Button { onFill(fill) } label: {
                    Image(systemName: fill ? "arrow.up.left.and.arrow.down.right" : "arrow.down.right.and.arrow.up.left")
                        .foregroundStyle(on ? t.accent : t.text)
                        .frame(width: Sizes.icon * 2, height: Sizes.icon * 2)
                        .background(on ? t.accentSoft : t.background, in: Circle())
                        .overlay(Circle().strokeBorder(on ? t.accentSoft : t.outline, lineWidth: Sizes.swatchRing / 2))
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .disabled(!enabled)
                .accessibilityLabel(fill ? Texts.fitFill : Texts.fitWhole)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }
}

/// The picture in the chosen format. While a finger moves, the last frame is moved and scaled to the new framing;
/// the next frame replaces it when it is drawn (Android's `Frame`). Drag to move, pinch to zoom, double tap to centre.
private struct SaveFrame: View {
    let aspect: Double
    let enabled: Bool
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var lastDrag = CGSize.zero
    @State private var lastZoom = 1.0
    /// How much the frame dims while an export runs (Android: the canvas at 60 %).
    private static let dimmed = 0.6

    var body: some View {
        let save = model.save
        let framing = save.shownFraming
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                t.canvas
                let frame = save.frame
                // Only a frame of this format; the next one follows within moments of a change.
                if let image = frame.image, frame.width > 0, abs(Double(frame.height) / Double(frame.width) - aspect) < 0.01 {
                    let drawn = frame.framing
                    let s = framing.zoom / drawn.zoom
                    Image(decorative: image, scale: 1).resizable()
                        .frame(width: geo.size.width, height: geo.size.height)
                        .scaleEffect(s, anchor: .topLeading)
                        .offset(x: (framing.centreX - drawn.centreX * s) * geo.size.width, y: (framing.centreY - drawn.centreY * s) * geo.size.height)
                        .accessibilityLabel(Texts.previewDescription)
                }
                if save.busy {
                    // What the export does shows at the top of the sheet; the frame only dims.
                    t.canvas.opacity(Self.dimmed)
                } else if frame.rendering {
                    BusyBar().frame(maxHeight: .infinity, alignment: .bottom)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            .contentShape(Rectangle())
            .gesture(drag(geo.size).simultaneously(with: pinch(geo.size)), isEnabled: enabled)
            // Beside the drag, so a double tap is not taken for the start of a move.
            .simultaneousGesture(TapGesture(count: 2).onEnded { model.setFill(false) }, isEnabled: enabled)
        }
    }

    private func drag(_ size: CGSize) -> some Gesture {
        // The default distance: a touch alone does not end Whole or Fill, only a move does (as on Android).
        DragGesture()
            .onChanged { value in
                let dx = (value.translation.width - lastDrag.width) / size.width
                let dy = (value.translation.height - lastDrag.height) / size.height
                lastDrag = value.translation
                model.setFraming(IosSave.shared.moved(framing: model.save.shownFraming, dx: dx, dy: dy))
            }
            .onEnded { _ in lastDrag = .zero }
    }

    private func pinch(_ size: CGSize) -> some Gesture {
        MagnifyGesture()
            .onChanged { value in
                let factor = value.magnification / lastZoom
                lastZoom = value.magnification
                guard factor != 1 else { return }
                let px = value.startLocation.x / size.width, py = value.startLocation.y / size.height
                model.setFraming(IosSave.shared.zoomedAround(framing: model.save.shownFraming, px: px, py: py, factor: factor))
            }
            .onEnded { _ in lastZoom = 1 }
    }
}

/// An outlined capsule action (Android's OutlinedButton) beside the save button.
private struct OutlinedButton: View {
    let label: String
    let enabled: Bool
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            Text(label).textRole(.heading).lineLimit(1)
                .foregroundStyle(enabled ? t.accent : t.textMuted)
                .padding(.horizontal, Spacing.md)
                .frame(maxWidth: .infinity, minHeight: Sizes.icon * 2 + Spacing.md)
                .overlay(Capsule().strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Save with the size in a second line, and an arrow beside it that picks the size (Android's `SaveButton`).
private struct SaveButton: View {
    let label: String
    let sizes: [String]
    let chosen: Int
    let enabled: Bool
    /// Without Pro on the video tab, the size line says it comes with Pro.
    let markPro: Bool
    let onSave: () -> Void
    let onSize: (Int) -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        HStack(spacing: Spacing.xs) {
            Button(action: onSave) {
                VStack(spacing: 0) {
                    Text(label).textRole(.heading).lineLimit(1)
                    Text(markPro ? Texts.withPro(sizes[chosen]) : sizes[chosen]).textRole(.note).lineLimit(1)
                }
                .foregroundStyle(enabled ? t.onAccent : t.textMuted)
                .padding(.horizontal, Spacing.md)
                .frame(maxWidth: .infinity, minHeight: Sizes.icon * 2 + Spacing.md)
                .background(enabled ? t.accent : t.surfaceRaised, in: UnevenRoundedRectangle(topLeadingRadius: Sizes.icon * 2, bottomLeadingRadius: Sizes.icon * 2))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!enabled)
            Menu {
                ForEach(sizes.indices, id: \.self) { i in
                    Button { onSize(i) } label: {
                        if i == chosen { Label(sizes[i], systemImage: "checkmark") } else { Text(sizes[i]) }
                    }
                }
            } label: {
                Image(systemName: "chevron.down")
                    .foregroundStyle(enabled ? t.onAccent : t.textMuted)
                    .frame(width: Sizes.icon + Spacing.lg, height: Sizes.icon * 2 + Spacing.md)
                    .background(enabled ? t.accent : t.surfaceRaised, in: UnevenRoundedRectangle(bottomTrailingRadius: Sizes.icon * 2, topTrailingRadius: Sizes.icon * 2))
            }
            .disabled(!enabled)
            .accessibilityLabel(Texts.chooseSize)
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// What an export does, with its share in percent and a bar (Android's `ExportNote`), inverted like a message.
struct ExportNote: View {
    let progress: ExportProgress
    @Environment(\.tokens) private var t

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                Text(Texts.progressStep(progress.label)).textRole(.body).foregroundStyle(t.background)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let share = progress.share { Text(Texts.percent(Int(share * 100))).textRole(.value).foregroundStyle(t.background) }
            }
            if let share = progress.share {
                ProgressView(value: share).tint(t.inverseAccent)
            } else {
                ProgressView().progressViewStyle(.linear).tint(t.inverseAccent)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.md)
        .background(t.text, in: RoundedRectangle(cornerRadius: Radius.sm))
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.md)
        .accessibilityElement(children: .combine)
    }
}
