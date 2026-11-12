import SwiftUI
@preconcurrency import MandalaCore

/// Favourites as a grid of thumbnails with "Add current" (Android's `FavoritesSheet`; selecting and
/// deleting several come with WP-7). A tap opens the favourite and closes the sheet.
struct FavoritesSheet: View {
    let close: @MainActor () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(Texts.favourites).textRole(.title).foregroundStyle(t.text)
                Spacer()
                Button(Texts.addCurrent) { model.addFavorite() }
                    .buttonStyle(.borderedProminent)
                    .tint(t.accent)
                    .foregroundStyle(t.onAccent)
                    .disabled(!model.canAddFavorite)
            }
            .padding(.top, Spacing.xl)
            if model.favorites.isEmpty {
                Text(Texts.favouritesEmpty).textRole(.body).foregroundStyle(t.textMuted).padding(.top, Spacing.md)
                Spacer()
            } else {
                Text(Texts.favouritesHint).textRole(.note).foregroundStyle(t.textMuted)
                    .padding(.top, Spacing.sm).padding(.bottom, Spacing.sm)
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: Sizes.favourite), spacing: Spacing.sm)], spacing: Spacing.sm) {
                        ForEach(model.favorites, id: \.id) { favorite in
                            FavoriteTile(favorite: favorite) {
                                model.applyFavorite(favorite)
                                close()
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

/// A favourite's thumbnail on black; its styles are read out.
struct FavoriteTile: View {
    let favorite: Favorite
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            ZStack {
                t.canvas
                if let thumb = Pictures.decodeThumbnail(favorite.thumbnailData) {
                    Image(decorative: thumb, scale: 1).resizable().scaledToFill()
                }
            }
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Favourite: " + favorite.parameters.layers.map { Texts.style($0.style) }.joined(separator: ", "))
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
                Text(Texts.saveSquare).textRole(.body).foregroundStyle(t.textMuted)
            }
            Spacer(minLength: 0)
            if let saving = model.saving {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    if let share = saving.share {
                        Text("\(Texts.creatingImage) · \(Int(share * 100)) %").textRole(.note).foregroundStyle(t.textMuted)
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
