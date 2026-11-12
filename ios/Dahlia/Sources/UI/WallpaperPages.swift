import SwiftUI
@preconcurrency import MandalaCore

// The menu's wallpaper pages (PLAN-4 WP-11, F5 a, ADR-0014 amended for iOS): iOS lets no app set the wallpaper, so the
// wallpaper page saves the screen-sized picture (with Pro the pair) to Photos and says how to set it; the daily page
// ticks the favourites that take turns and explains the Shortcuts automation that sets one every morning.

/// The ready-made shortcut on iCloud ("Dahlia wallpaper": Mandala of the day for both screens and Set Wallpaper Photo
/// twice). Mathis builds it once on his iPhone and shares it (IOS-11); until then the button stays hidden.
enum ReadyShortcut {
    static let link: URL? = nil
}

/// The current mandala filling this iPhone's screen, as one picture or (Pro) as the pair, saved to Photos.
struct WallpaperPage: View {
    @Binding var page: MenuSheet.Page
    let close: @MainActor () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let pro = model.proOwned
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: Texts.menuBack) { page = .main }
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
                // With Pro features but without Pro the Pro sheet opens on top of the menu; "Save without them" goes on.
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
        }
        .onAppear { model.setWallpaperOpen(true) }
        .onDisappear { model.setWallpaperOpen(false) }
        // A new mandala (Pro features removed) or Pro bought: the preview follows.
        .onChange(of: model.params) { model.refreshWallpaperPreview() }
        .onChange(of: model.proOwned) { model.refreshWallpaperPreview() }
    }

    private var chevron: some View { Image(systemName: "chevron.right").foregroundStyle(t.textMuted) }

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

/// The daily wallpaper: which favourites take turns, and the Shortcuts automation that sets one every morning (Pro).
struct DailyPage: View {
    @Binding var page: MenuSheet.Page
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @Environment(\.openURL) private var openURL

    var body: some View {
        let chosen = model.dailyChosen
        let count = model.favorites.filter { chosen?.contains($0.id) ?? true }.count
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: Texts.menuBack) { page = .main }
            Text(Texts.dailyWallpaper).textRole(.title).foregroundStyle(t.text).padding(.top, Spacing.sm)
            Text(Texts.dailyAbout).textRole(.body).foregroundStyle(t.textMuted).padding(.top, Spacing.xs)
                .fixedSize(horizontal: false, vertical: true)
            divider.padding(.top, Spacing.md)
            MenuRow(icon: "star", label: Texts.dailyFavourites,
                    detail: chosen == nil ? Texts.dailyAll : Texts.dailySome(count, model.favorites.count),
                    action: model.favorites.isEmpty ? nil : { page = .dailyFavourites }) { chevron }
            divider

            if model.proOwned {
                Text(Texts.dailyAutomationTitle).textRole(.heading).foregroundStyle(t.text).padding(.top, Spacing.xl)
                Text(Texts.dailyAutomationSteps).textRole(.body).foregroundStyle(t.textMuted).padding(.top, Spacing.xs)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: Spacing.sm) {
                    Button {
                        if let url = URL(string: "shortcuts://") { openURL(url) }
                    } label: {
                        Label(Texts.dailyOpenShortcuts, systemImage: "square.stack.3d.up").textRole(.heading).lineLimit(1)
                            .frame(maxWidth: .infinity).padding(.vertical, Spacing.xs)
                    }
                    .buttonStyle(.bordered)
                    .tint(t.accent)
                    if let link = ReadyShortcut.link {
                        Button { openURL(link) } label: {
                            Label(Texts.dailyReadyShortcut, systemImage: "plus").textRole(.heading).lineLimit(1).minimumScaleFactor(0.8)
                                .frame(maxWidth: .infinity).padding(.vertical, Spacing.xs)
                        }
                        .buttonStyle(.bordered)
                        .tint(t.accent)
                    }
                }
                .padding(.top, Spacing.md)
                if ReadyShortcut.link != nil {
                    Text(Texts.dailyReadyNote).textRole(.note).foregroundStyle(t.textMuted).padding(.top, Spacing.sm)
                        .fixedSize(horizontal: false, vertical: true)
                }
                #if DEBUG
                if let run = AppGroup.defaults?.string(forKey: DailyWallpaper.lastRunKey) {
                    Text(Texts.dailyLastRunDebug(run)).textRole(.note).foregroundStyle(t.textMuted).padding(.top, Spacing.sm)
                }
                #endif
            } else {
                MenuRow(icon: "star", label: Texts.proTitle, detail: Texts.dailyNeedsPro, action: { model.showPro() }) { chevron }
                divider
            }

            Text(Texts.dailyWidgetNote).textRole(.note).foregroundStyle(t.textMuted).padding(.top, Spacing.lg)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var divider: some View { Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2) }

    private var chevron: some View { Image(systemName: "chevron.right").foregroundStyle(t.textMuted) }
}

/// The favourites that take turns, ticked in a grid (Android's `DailyFavouritesPage`, UX-1 13); all are ticked at first.
struct DailyFavouritesPage: View {
    @Binding var page: MenuSheet.Page
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let chosen = model.dailyChosen
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: Texts.dailyWallpaper) { page = .daily }
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
