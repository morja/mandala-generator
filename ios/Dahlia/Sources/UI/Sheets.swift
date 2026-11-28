import SwiftUI
@preconcurrency import MandalaCore

/// Favourites as a grid of thumbnails (Android's `FavoritesSheet`): a tap opens the favourite, a long press starts
/// selecting; then taps add or remove favourites, and the heading offers deleting them all at once (with Undo).
struct FavoritesSheet: View {
    let close: @MainActor () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var selected = Set<String>()
    /// On the iPad the half-height detent would pin the sheet to the bottom of the window; there it is a form sheet in
    /// the middle. (Inside a form sheet the size class reads compact, so the device decides.)
    private static let pad = UIDevice.current.userInterfaceIdiom == .pad

    var body: some View {
        // A favourite deleted elsewhere (or brought back by Undo's opposite) drops out of the selection.
        let selection = selected.filter { id in model.favorites.contains { $0.id == id } }
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: Spacing.sm) {
                if selection.isEmpty {
                    Text(Texts.favourites).textRole(.title).foregroundStyle(t.text)
                    Spacer()
                    Button(Texts.addCurrent) { model.addFavoriteQuietly() }
                        .buttonStyle(.borderedProminent)
                        .tint(t.accent)
                        .foregroundStyle(t.onAccent)
                        .disabled(!model.canAddFavorite)
                } else {
                    Text(Texts.favouritesSelected(selection.count)).textRole(.title).foregroundStyle(t.text)
                    Spacer()
                    Button(Texts.favouritesCancel) { selected = [] }
                        .foregroundStyle(t.accent)
                    Button(Texts.favouriteDelete) {
                        model.removeFavorites(selection)
                        selected = []
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(t.accent)
                    .foregroundStyle(t.onAccent)
                }
            }
            .padding(.top, Spacing.xl)
            // Below the title, so a message does not cover "Add current"; it takes no room while empty.
            if let message = model.message {
                MessageBar(message: message) { model.message = nil }
                    .padding(.horizontal, -Spacing.lg)
                    .padding(.top, Spacing.sm)
            }
            if model.favorites.isEmpty {
                Text(Texts.favouritesEmpty).textRole(.body).foregroundStyle(t.textMuted).padding(.top, Spacing.md)
                Spacer()
            } else {
                Text(Texts.favouritesHint).textRole(.note).foregroundStyle(t.textMuted)
                    .padding(.top, Spacing.sm).padding(.bottom, Spacing.sm)
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: Sizes.favourite), spacing: Spacing.sm)], spacing: Spacing.sm) {
                        ForEach(model.favorites, id: \.id) { favorite in
                            FavoriteTile(favorite: favorite, selected: selection.contains(favorite.id), selecting: !selection.isEmpty) {
                                model.applyFavorite(favorite)
                                close()
                            } toggle: {
                                if selection.contains(favorite.id) { selected = selection.subtracting([favorite.id]) } else { selected = selection.union([favorite.id]) }
                            }
                        }
                    }
                    .padding(.bottom, Spacing.xl)
                }
            }
        }
        .padding(.horizontal, Spacing.lg)
        .presentationDetents(Self.pad ? [.large] : [.medium, .large])
        .sheetSurface(fitted: true)
    }
}

/// A favourite's thumbnail on black; its styles are read out. A tap uses it, or, while selecting, selects it;
/// a long press selects it.
struct FavoriteTile: View {
    let favorite: Favorite
    var selected = false
    var selecting = false
    let apply: () -> Void
    let toggle: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        ZStack {
            t.canvas
            if let thumb = Pictures.decodeThumbnail(favorite.thumbnailData) {
                Image(decorative: thumb, scale: 1).resizable().scaledToFill()
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
        .overlay {
            if selected { RoundedRectangle(cornerRadius: Radius.sm).strokeBorder(t.accent, lineWidth: Sizes.swatchRing) }
        }
        .overlay(alignment: .topTrailing) {
            if selected {
                Image(systemName: "checkmark")
                    .foregroundStyle(t.onAccent)
                    .padding(Spacing.xs)
                    .background(t.accent, in: Circle())
                    .padding(Spacing.xs)
            }
        }
        .contentShape(RoundedRectangle(cornerRadius: Radius.sm))
        .onTapGesture { if selecting { toggle() } else { apply() } }
        .onLongPressGesture { toggle() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Texts.favouriteLabel(favorite.parameters.layers.map { Texts.style($0.style) }.joined(separator: ", ")))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
        .accessibilityAction(named: Texts.select) { toggle() }
    }
}

/// The look of every sheet, as Android's ModalBottomSheet: the surface colour, a shade lighter than the editor, and a
/// visible grabber (IOS-14).
/// On the iPad a sheet is a form sheet in the middle of the window; `fitted` makes it as tall as its content there
/// (on the iPhone the detents decide).
private struct SheetSurface: ViewModifier {
    var fitted = false
    @Environment(\.tokens) private var t

    func body(content: Content) -> some View {
        content
            .background(t.surface)
            .presentationBackground(t.surface)
            .presentationDragIndicator(.visible)
            .presentationSizing(.form.fitted(horizontal: false, vertical: fitted))
    }
}

extension View {
    func sheetSurface(fitted: Bool = false) -> some View { modifier(SheetSurface(fitted: fitted)) }
}

/// A sheet as tall as its content, so the picture stays visible above it (Android's menu and Pro sheets); it scrolls
/// when the content is taller than the screen. The content brings its own insets.
struct FittedSheet<Content: View>: View {
    @ViewBuilder var content: Content
    @State private var height: CGFloat = 0
    @State private var bottomInset: CGFloat = 0

    private static var startAtEnd: Bool {
        #if DEBUG
        return UserDefaults.standard.bool(forKey: "shotScrollEnd")
        #else
        return false
        #endif
    }

    var body: some View {
        ScrollView {
            content.onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 }
        }
        .onGeometryChange(for: CGFloat.self) { $0.safeAreaInsets.bottom } action: { bottomInset = $0 }
        .scrollBounceBehavior(.basedOnSize)
        // Debug builds: `-shotScrollEnd YES` starts at the bottom, e.g. for a screenshot of the wallpaper page's tip.
        .defaultScrollAnchor(Self.startAtEnd ? .bottom : nil)
        // The sheet floats above the home indicator and adds its inset below the detent, so the content's own bottom
        // padding is room enough.
        .presentationDetents(height > 0 ? [.height(max(height - bottomInset, height / 2))] : [.medium])
        .sheetSurface(fitted: true)
    }
}
