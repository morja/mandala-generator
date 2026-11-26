import SwiftUI
@preconcurrency import MandalaCore

// The menu's wallpaper pages (PLAN-4 WP-11, F5 a, ADR-0014 amended for iOS): iOS lets no app set the wallpaper, so the
// wallpaper page saves the screen-sized picture (with Pro the pair) to Photos and says how to set it, with a folded tip
// for a Shortcuts automation that sets one every morning. Since 26 November 2026 the iPhone offers no daily wallpaper of
// its own (IOS-11); the picker of the favourites that take turns belongs to the widgets page.

/// The current mandala filling this iPhone's screen, as one picture or (Pro) as the pair, saved to Photos.
struct WallpaperPage: View {
    @Binding var page: MenuSheet.Page
    let close: @MainActor () -> Void
    /// Where Back leads: the menu, or the save sheet when the page opened from its Wallpaper button.
    var backLabel = Texts.menuBack
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    /// The Shortcuts tip, folded at first (debug builds: `-shotTipOpen YES` opens it for screenshots).
    @State private var tipOpen = Self.tipOpenAtStart

    private static var tipOpenAtStart: Bool {
        #if DEBUG
        return UserDefaults.standard.bool(forKey: "shotTipOpen")
        #else
        return false
        #endif
    }

    var body: some View {
        let pro = model.proOwned
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: backLabel) { page = .main }
            Text(Texts.menuWallpaper).textRole(.title).foregroundStyle(t.text).padding(.top, Spacing.sm)
            Text(Texts.wallpaperAbout).textRole(.body).foregroundStyle(t.textMuted).padding(.top, Spacing.xs)
                .fixedSize(horizontal: false, vertical: true)

            // The lock and the home screen side by side as they will look; without Pro the one picture.
            HStack(alignment: .top, spacing: Spacing.lg) {
                screen(Texts.pairLock, model.wallpaper.lock)
                if pro {
                    screen(Texts.pairHome, model.wallpaper.home)
                } else {
                    Color.clear.frame(maxWidth: .infinity, maxHeight: 0)
                }
            }
            .padding(.top, Spacing.lg)

            if pro {
                let range = IosSave.shared.homeBrightnessRange().map(\.doubleValue)
                SliderRow(label: Texts.pairHome, value: model.save.choice.homeBrightness, range: range[0]...range[1], defaultValue: range[2],
                          display: { Texts.percent(Int(($0 * 100).rounded())) }) { model.setHomeBrightness($0) }
                    .padding(.top, Spacing.md)
                Text(Texts.pairLockNote).textRole(.note).foregroundStyle(t.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                MenuRow(icon: "star", label: Texts.pairPro, detail: nil, action: { model.showPro() }) { chevron }
                    .padding(.top, Spacing.sm)
            }

            Button {
                // With Pro features but without Pro the Pro sheet opens on top of the menu; "Save without Pro parts" goes on.
                let go: @MainActor () -> Void = {
                    close()
                    model.saveWallpaper()
                }
                if model.mayKeep(Texts.actionSave, then: go) { go() }
            } label: {
                Text(pro ? Texts.saveBoth : Texts.saveToPhotos).textRole(.heading).lineLimit(1)
                    .frame(maxWidth: .infinity).padding(.vertical, Spacing.xs)
            }
            .buttonStyle(.borderedProminent)
            .tint(t.accent)
            .foregroundStyle(t.onAccent)
            .disabled(model.save.busy)
            .padding(.top, Spacing.lg)

            Text(Texts.setTitle).textRole(.heading).foregroundStyle(t.text).padding(.top, Spacing.xl)
            Text(pro ? Texts.setPair : Texts.setOne).textRole(.body).foregroundStyle(t.textMuted).padding(.top, Spacing.xs)
                .fixedSize(horizontal: false, vertical: true)

            // A tip, not a feature: iOS lets only the Shortcuts app change the wallpaper on its own (26 November 2026).
            divider.padding(.top, Spacing.xl)
            MenuRow(icon: "lightbulb", label: Texts.tipTitle, detail: nil, action: { tipOpen.toggle() }) {
                Image(systemName: "chevron.down").foregroundStyle(t.textMuted).rotationEffect(.degrees(tipOpen ? 180 : 0))
            }
            .accessibilityAddTraits(tipOpen ? .isSelected : [])
            if tipOpen {
                Text(Texts.tipSteps).textRole(.body).foregroundStyle(t.textMuted).padding(.bottom, Spacing.md)
                    .fixedSize(horizontal: false, vertical: true)
            }
            divider
        }
        .onAppear { model.setWallpaperOpen(true) }
        .onDisappear { model.setWallpaperOpen(false) }
        // A new mandala (Pro features removed) or Pro bought: the preview follows.
        .onChange(of: model.params) { model.refreshWallpaperPreview() }
        .onChange(of: model.proOwned) { model.refreshWallpaperPreview() }
    }

    private var chevron: some View { Image(systemName: "chevron.right").foregroundStyle(t.textMuted) }

    private var divider: some View { Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2) }

    /// One phone screen in this iPhone's shape, its name below.
    private func screen(_ label: String, _ image: CGImage?) -> some View {
        VStack(spacing: Spacing.sm) {
            ZStack {
                t.canvas
                if let image { Image(decorative: image, scale: 1).resizable().scaledToFill() }
            }
            .aspectRatio(1 / SaveState.currentScreenAspect(), contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: Radius.md))
            .overlay(alignment: .bottom) { if model.wallpaper.rendering && image == nil { BusyBar() } }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Texts.pairPreview(label))
            Text(label).textRole(.note).foregroundStyle(t.textMuted)
        }
        .frame(maxWidth: .infinity)
    }
}

/// The favourites that take turns, ticked in a grid (Android's `DailyFavouritesPage`, UX-1 13); all are ticked at first.
struct DailyFavouritesPage: View {
    @Binding var page: MenuSheet.Page
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let chosen = model.dailyShown
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: Texts.menuWidgets) { page = .widgets }
            Text(Texts.dailyFavourites).textRole(.title).foregroundStyle(t.text).padding(.top, Spacing.sm)
            Text(Texts.dailyPickAbout).textRole(.body).foregroundStyle(t.textMuted).padding(.top, Spacing.xs)
                .fixedSize(horizontal: false, vertical: true)
            Button(Texts.dailyChooseAll) { model.chooseAllDaily() }
                .textRole(.heading)
                .tint(t.accent)
                .disabled(chosen == nil)
                .padding(.top, Spacing.sm)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: Sizes.favourite), spacing: Spacing.sm)], spacing: Spacing.sm) {
                ForEach(model.favorites, id: \.id) { favourite in
                    FavoriteTile(favorite: favourite, selected: chosen?.contains(favourite.id) ?? true, selecting: true,
                                 apply: {}, toggle: { model.toggleDaily(favourite.id) })
                }
            }
            .padding(.top, Spacing.md)
        }
    }
}
