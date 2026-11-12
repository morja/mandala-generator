import SwiftUI
@preconcurrency import MandalaCore

/// The one screen: the picture on top, the controls below, the actions in the navigation bar in Android's
/// order (Back and Forward on the left; play field, die, save, favourites and the menu on the right; F12 a).
/// The picture folds the way Android's `FoldingCanvas` does, so the controls get room.
struct EditorView: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var showFavorites = false
    @State private var showSave = false
    @State private var showMenu = false
    @State private var header: ControlHeader?
    /// Moves the folding along with the change of size, but not with every change of the layout.
    private static let foldAnimation = Animation.easeInOut(duration: 0.25)

    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                let fullSide = min(geo.size.width - Spacing.lg * 2, geo.size.height * 0.5)
                let fraction = model.shell.previewFraction
                let folded = fraction < PreviewSize.full
                VStack(spacing: 0) {
                    // The picture stays in view; only the open tab scrolls, so every change is visible.
                    HStack(alignment: .top, spacing: Spacing.md) {
                        CanvasView(side: fullSide * fraction, folded: folded) { model.shell.setPreviewFraction(folded ? PreviewSize.full : PreviewSize.small) }
                        if fraction <= PreviewSize.headerBelow, let header {
                            VStack(alignment: .leading, spacing: Spacing.xs) {
                                Text(header.title).textRole(.body).foregroundStyle(t.text).lineLimit(1)
                                Text(header.detail).textRole(.note).foregroundStyle(t.textMuted).lineLimit(1)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: folded ? .leading : .center)
                    .padding(.horizontal, Spacing.lg)
                    .padding(.bottom, Spacing.xs)
                    .animation(Self.foldAnimation, value: fraction)
                    ControlTabs(onHeader: { header = $0 })
                }
            }
            .background(t.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .topBarLeading) {
                    Button { model.goBack() } label: { Image(systemName: "arrow.uturn.backward") }
                        .disabled(!model.shell.canGoBack)
                        .accessibilityLabel(Texts.back)
                    Button { model.goForward() } label: { Image(systemName: "arrow.uturn.forward") }
                        .disabled(!model.shell.canGoForward)
                        .accessibilityLabel(Texts.forward)
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { model.openPlay() } label: { Image(systemName: "safari") }
                        .tint(t.text)
                        .accessibilityLabel(Texts.playTitle)
                    Button { model.surprise() } label: { Image(systemName: "die.face.5") }
                        .tint(t.accent)
                        .accessibilityLabel(Texts.newKind(.everything))
                    Button { showSave = true } label: { Image(systemName: "square.and.arrow.down") }
                        .tint(t.accent)
                        .accessibilityLabel(Texts.saveImage)
                    Button { showFavorites = true } label: { Image(systemName: model.isFavorite ? "star.fill" : "star") }
                        .tint(t.text)
                        .accessibilityLabel(Texts.favourites)
                    Button { showMenu = true } label: { Image(systemName: "ellipsis") }
                        .tint(t.text)
                        .accessibilityLabel(Texts.more)
                }
            }
        }
        .overlay(alignment: .bottom) {
            if let message = model.message, !showFavorites, !showSave, !showMenu, model.shell.play == nil {
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
        .sheet(isPresented: $showMenu) {
            MenuSheet { showMenu = false }
        }
        .sheet(isPresented: Binding(get: { model.shell.play != nil }, set: { if !$0 { model.closePlay() } })) {
            PlaySheet()
        }
    }
}

/// The square picture on black with a thin bar while it is drawn (Android's `Canvas`).
struct CanvasView: View {
    let side: CGFloat
    /// The picture is folded small: a tap makes it large again.
    var folded = false
    /// Folds or unfolds the picture; the corner button and, while folded, a tap call it.
    var fold: (() -> Void)? = nil
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    /// A finger holds the picture: it shows without the scene effects, while they are on.
    @State private var holding = false
    @State private var holdTask: Task<Void, Never>?

    var body: some View {
        let comparing = holding && model.params.effectsLayer.isEnabled
        VStack(alignment: .leading, spacing: Spacing.xs) {
            ZStack(alignment: .bottom) {
                t.canvas
                if let image = comparing ? (model.plainPreview ?? model.preview) : model.preview {
                    Image(decorative: image, scale: 1).resizable().interpolation(.high).aspectRatio(1, contentMode: .fit)
                        .accessibilityLabel(Texts.preview)
                }
                if model.rendering || comparing && model.plainPreview == nil { BusyBar() }
            }
            .overlay(alignment: .bottomTrailing) {
                if let fold {
                    Button(action: fold) {
                        Image(systemName: folded ? "arrow.up.left.and.arrow.down.right" : "arrow.down.right.and.arrow.up.left")
                            .foregroundStyle(t.text)
                            .padding(Spacing.xs)
                            .background(t.surface, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .padding(Spacing.xs)
                    .accessibilityLabel(folded ? Texts.largerImage : Texts.smallerImage)
                    .accessibilityHint(folded ? Texts.showLarge : Texts.makeSmall)
                }
            }
            .contentShape(Rectangle())
            // The image is usually ready when the hold counts: it is drawn as soon as the finger goes down.
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard holdTask == nil, !holding else { return }
                    model.preparePlainPreview()
                    holdTask = Task {
                        try? await Task.sleep(for: .seconds(Timing.holdSeconds))
                        if !Task.isCancelled { holding = true }
                    }
                }
                .onEnded { value in
                    // A tap on the folded picture makes it large again (Android's `onTap`).
                    if folded, !holding, abs(value.translation.width) + abs(value.translation.height) < Spacing.sm { fold?() }
                    holdTask?.cancel()
                    holdTask = nil
                    holding = false
                })
            .frame(width: side, height: side)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            if let error = model.previewError {
                Text(error).textRole(.note).foregroundStyle(t.error)
            }
        }
    }
}
