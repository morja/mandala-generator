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

/// The square picture on black with a thin bar while it is drawn (Android's `Canvas`).
struct CanvasView: View {
    let side: CGFloat
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            ZStack(alignment: .bottom) {
                t.canvas
                if let image = model.preview {
                    Image(decorative: image, scale: 1).resizable().interpolation(.high).aspectRatio(1, contentMode: .fit)
                        .accessibilityLabel(Texts.preview)
                }
                if model.rendering { BusyBar() }
            }
            .frame(width: side, height: side)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            if let error = model.previewError {
                Text(error).textRole(.note).foregroundStyle(t.error)
            }
        }
        .frame(maxWidth: .infinity)
    }
}
