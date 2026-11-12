import SwiftUI
@preconcurrency import MandalaCore

/// Favourites as a grid of thumbnails (Android's `FavoritesSheet`): a tap opens the favourite, a long press starts
/// selecting; then taps add or remove favourites, and the heading offers deleting them all at once (with Undo).
struct FavoritesSheet: View {
    let close: @MainActor () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var selected = Set<String>()

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
        .background(t.background)
        .presentationDetents([.medium, .large])
        .presentationBackground(t.background)
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

/// Saving the square picture at 1024 px to Photos. TEMPORARY (WP-8): the save sheet with formats,
/// framing, sizes and the video grows from this one.
struct SaveSheet: View {
    let close: @MainActor () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(Texts.saveImage).textRole(.title).foregroundStyle(t.text).padding(.top, Spacing.xl)
            HStack(alignment: .center, spacing: Spacing.lg) {
                ZStack {
                    t.canvas
                    if let image = model.preview { Image(decorative: image, scale: 1).resizable().scaledToFit() }
                }
                .frame(width: Sizes.favourite * 1.5, height: Sizes.favourite * 1.5)
                .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                Text(Texts.saveSquare(1024)).textRole(.body).foregroundStyle(t.textMuted)
            }
            Spacer(minLength: 0)
            if let saving = model.saving {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    if let share = saving.share {
                        Text("\(Texts.creatingImage) · \(Texts.percent(Int(share * 100)))").textRole(.note).foregroundStyle(t.textMuted)
                        ProgressView(value: share).tint(t.accent)
                    } else {
                        Text(Texts.addingToPhotos).textRole(.note).foregroundStyle(t.textMuted)
                        ProgressView().tint(t.accent).frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            Button {
                model.saveToPhotos(onDone: close)
            } label: {
                Text(Texts.saveToPhotos).textRole(.heading).frame(maxWidth: .infinity).padding(.vertical, Spacing.xs)
            }
            .buttonStyle(.borderedProminent)
            .tint(t.accent)
            .foregroundStyle(t.onAccent)
            .disabled(model.saving != nil)
            .padding(.bottom, Spacing.lg)
        }
        .padding(.horizontal, Spacing.lg)
        .background(t.background)
        .presentationDetents([.medium])
        .presentationBackground(t.background)
    }
}
