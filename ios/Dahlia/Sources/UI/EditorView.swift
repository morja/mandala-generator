import SwiftUI
@preconcurrency import MandalaCore

/// The one screen: the picture on top, the controls below, the actions in the navigation bar
/// (Android's `EditorScreen`, phone layout). WP-1 has the Layers tab, favourites and saving.
struct EditorView: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var showFavorites = false
    @State private var showSave = false

    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                VStack(spacing: 0) {
                    // The picture stays in view; only the open tab scrolls, so every change is visible.
                    CanvasView(side: min(geo.size.width - Spacing.lg * 2, geo.size.height * 0.5))
                        .padding(.horizontal, Spacing.lg)
                        .padding(.bottom, Spacing.xs)
                    ControlTabs()
                }
            }
            .background(t.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                // Android's order on the right: save, favourites (die, play field and menu follow in later packages).
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button {
                        showSave = true
                    } label: {
                        Image(systemName: "square.and.arrow.down")
                    }
                    .tint(t.accent)
                    .accessibilityLabel(Texts.saveImage)
                    Button {
                        showFavorites = true
                    } label: {
                        Image(systemName: model.isFavorite ? "star.fill" : "star")
                    }
                    .tint(t.text)
                    .accessibilityLabel(Texts.favourites)
                    // Until the menu comes (WP-7): the two switches Android keeps on its "More" page.
                    Menu {
                        Toggle(Texts.fastPreview, isOn: Binding(get: { model.fastPreview }, set: { model.setFastPreview($0) }))
                        Toggle(Texts.chipPreviews, isOn: Binding(get: { model.chipPreviews }, set: { model.setChipPreviews($0) }))
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                    .tint(t.text)
                    .accessibilityLabel(Texts.menu)
                }
            }
        }
        .overlay(alignment: .bottom) {
            if let message = model.message, !showFavorites, !showSave {
                MessageBar(message: message) { model.message = nil }
                    .padding(.bottom, Spacing.lg)
            }
        }
        .sheet(isPresented: $showFavorites) {
            FavoritesSheet { showFavorites = false }
        }
        .sheet(isPresented: $showSave) {
            SaveSheet { showSave = false }
        }
    }
}

/// The square picture on black with a thin bar while it is drawn (Android's `Canvas`). While moving or
/// drawing by hand an overlay takes the fingers; otherwise holding the picture shows it without the effects.
struct CanvasView: View {
    let side: CGFloat
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    /// A finger holds the picture: it shows without the scene effects, while they are on.
    @State private var holding = false
    @State private var holdTask: Task<Void, Never>?

    var body: some View {
        let mark = model.moveMark
        let drawing = model.drawTool
        let holdable = mark == nil && drawing == nil
        let comparing = holding && holdable && model.params.effectsLayer.isEnabled
        VStack(alignment: .leading, spacing: Spacing.xs) {
            ZStack(alignment: .bottom) {
                t.canvas
                if let image = comparing ? (model.plainPreview ?? model.preview) : model.preview {
                    Image(decorative: image, scale: 1).resizable().interpolation(.high).aspectRatio(1, contentMode: .fit)
                        .accessibilityLabel(Texts.preview)
                }
                if model.rendering || comparing && model.plainPreview == nil { BusyBar() }
            }
            .contentShape(Rectangle())
            // The image is usually ready when the hold counts: it is drawn as soon as the finger goes down.
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard holdable, holdTask == nil, !holding else { return }
                    model.preparePlainPreview()
                    holdTask = Task {
                        try? await Task.sleep(for: .seconds(Timing.holdSeconds))
                        if !Task.isCancelled { holding = true }
                    }
                }
                .onEnded { _ in
                    holdTask?.cancel()
                    holdTask = nil
                    holding = false
                }, isEnabled: holdable)
            .overlay {
                if let mark {
                    MoveOverlay(mark: mark) { dx, dy, zoom, turn in model.moveBy(dx: dx, dy: dy, zoom: zoom, turnDegrees: turn) }
                } else if let drawing {
                    drawingOverlay(drawing)
                }
            }
            .overlay(alignment: .topLeading) {
                // The mandala uses Pro features the user does not own (ADR-0016); the Pro sheet comes with WP-9.
                if model.missingPro { CanvasLabel(text: Texts.proMark, accent: true).accessibilityLabel(Texts.proMarkDescription) }
            }
            .overlay(alignment: .topTrailing) {
                if comparing { CanvasLabel(text: Texts.withoutEffects, accent: false) }
            }
            .frame(width: side, height: side)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            if let status = status(mark: mark, drawing: drawing) {
                Text(status).textRole(.note).foregroundStyle(model.previewError != nil ? t.error : t.textMuted)
            }
        }
        .frame(maxWidth: .infinity)
        .onChange(of: holdable) { _, can in
            if !can {
                holdTask?.cancel()
                holdTask = nil
                holding = false
            }
        }
    }

    /// The line under the picture: an error, else what the fingers do while moving or drawing.
    private func status(mark: MoveMark?, drawing: DrawTool?) -> String? {
        if let error = model.previewError { return error }
        if drawing != nil { return Texts.drawHint }
        guard let mark else { return nil }
        switch mark.target {
        case .emblem: return Texts.moveEmblemHint
        case .text: return Texts.moveTextHint
        default: return Texts.moveLayerHint(Int(mark.layer?.int32Value ?? 1))
        }
    }

    /// The active tool's look, as Android's `drawing`: the macOS overlay's line width, the spray's brush and paint.
    @ViewBuilder
    private func drawingOverlay(_ tool: DrawTool) -> some View {
        let p = model.params
        if tool == .lines {
            let d = p.drawingLayer
            // Same width as the macOS overlay: strokeWeight * 1.5 % of the picture.
            DrawingOverlay(symmetry: Int(d.symmetry), colour: hsbColour(d.currentHue, d.currentSaturation, d.currentBrightness),
                           width: d.strokeWeight * Self.lineWidthShare) { model.addStroke(xs: $0, ys: $1) }
        } else {
            let g = p.graffitiLayer
            DrawingOverlay(symmetry: Int(g.symmetry), colour: hsbColour(g.currentHue, g.currentSaturation, g.currentBrightness).opacity(g.currentOpacity),
                           width: g.currentBrushSize) { model.addStroke(xs: $0, ys: $1) }
        }
    }

    /// The macOS drawing overlay's line width per unit of stroke weight, as a share of the picture.
    private static let lineWidthShare = 0.015
}

/// A small rounded label on the picture ("Pro", "Without effects").
private struct CanvasLabel: View {
    let text: String
    let accent: Bool
    @Environment(\.tokens) private var t

    var body: some View {
        Text(text).textRole(.heading).foregroundStyle(accent ? t.accent : t.textMuted)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.xs)
            .background(t.surface, in: Capsule())
            .padding(Spacing.sm)
    }
}
