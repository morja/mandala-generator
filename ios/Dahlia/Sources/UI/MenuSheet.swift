import SwiftUI
import UniformTypeIdentifiers
@preconcurrency import MandalaCore

/// The menu as a sheet from below (Android's `MenuSheet`, DM-7 B): the mandala on screen with what keeps it, three dice
/// for a new one shown beside the current mandala, the theme and "More" (the line drawing, files, the two switches);
/// the Dahlia page with Pro (WP-9) and the home screen widgets (WP-10); the wallpaper and daily pages (WP-11) are in
/// WallpaperPages.swift.
struct MenuSheet: View {
    let close: @MainActor () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(TourModel.self) private var tour
    @Environment(\.tokens) private var t
    @AppStorage("lightTheme") private var lightTheme = false
    #if DEBUG
    @AppStorage(EditorModel.widgetMarkKey) private var widgetMark = true
    #endif
    @State var page = Self.initialPage
    @State private var showOpen = false
    @State private var showExport = false
    @State private var showAbout = Self.initialAbout

    enum Page { case main, more, dahlia, lineDrawing, pro, widgets, wallpaper, daily, dailyFavourites }

    /// The placed home screen widgets, read from WidgetKit whenever the Dahlia page or the widget list opens.
    @State private var widgetRows: [WidgetRow] = []

    private static var initialPage: Page {
        #if DEBUG
        switch ShotSetup.sheet {
        case "more": return .more
        case "dahlia", "about": return .dahlia
        case "propage": return .pro
        case "widgets": return .widgets
        case "wallpaper": return .wallpaper
        case "daily": return .daily
        case "dailyFavourites": return .dailyFavourites
        default: return .main
        }
        #else
        return .main
        #endif
    }

    private static var initialAbout: Bool {
        #if DEBUG
        return ShotSetup.sheet == "about"
        #else
        return false
        #endif
    }

    @State private var lineDrawing: SvgDocument?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if let candidate = model.shell.candidate {
                    Compare(candidate: candidate, close: close)
                } else if page == .more {
                    morePage
                } else if page == .lineDrawing {
                    lineDrawingPage
                } else if page == .dahlia {
                    dahliaPage
                } else if page == .pro {
                    proPage
                } else if page == .widgets {
                    widgetsPage
                } else if page == .wallpaper {
                    WallpaperPage(page: $page, close: close)
                } else if page == .daily {
                    DailyPage(page: $page)
                } else if page == .dailyFavourites {
                    DailyFavouritesPage(page: $page)
                } else {
                    mainPage
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.xl)
            .padding(.bottom, Spacing.xl)
        }
        .background(t.background)
        .presentationDetents([.large])
        .presentationBackground(t.background)
        // The comparison belongs to the open menu only.
        .onDisappear { model.closeCandidate() }
        // A Pro request from here (the line drawing, Share, "Dahlia Pro") shows on top of the menu.
        .proSheet()
        // A purchase closes the menu too, so the thanks shows in the editor (Android, QA-6 finding 15).
        .onChange(of: model.proOwned) { _, owned in if owned && page != .pro { close() } }
        #if DEBUG
        .task { await ShotSetup.proOverIfAsked("menu", model) }
        #endif
        .sheet(isPresented: $showAbout) { AboutView { showAbout = false } }
        .task(id: page) { if page == .dahlia || page == .widgets { widgetRows = await model.widgetRows() } }
        .fileImporter(isPresented: $showOpen, allowedContentTypes: [.json, .plainText, .data]) { result in
            if case .success(let url) = result {
                model.openFile(url)
                close()
            }
        }
        .fileExporter(isPresented: $showExport, document: SettingsDocument(text: model.settingsFileText), contentType: .json,
                      defaultFilename: model.suggestedSettingsName) { result in
            switch result {
            case .success: model.settingsSaved(true)
            case .failure(let error):
                // Leaving the panel without saving is not a failure.
                if (error as? CocoaError)?.code != .userCancelled { model.settingsSaved(false) }
            }
            close()
        }
    }

    // ── Main page ──

    private var mainPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            current
            HStack(spacing: Spacing.sm) {
                Pill(icon: model.isFavorite ? "star.fill" : "star", label: Texts.menuFavourite) {
                    model.addFavorite()
                    close()
                }
                .disabled(!model.canAddFavorite)
                // The screen-sized picture or the pair to Photos, with how to set it in iOS (WP-11).
                Pill(icon: "iphone", label: Texts.menuWallpaper) { page = .wallpaper }
                    .disabled(model.save.busy)
                // The square picture through the share sheet, drawn after the menu closed (Android's KeepRow).
                Pill(icon: "square.and.arrow.up", label: Texts.share) {
                    // With Pro features but without Pro the Pro sheet opens on top of the menu; "Share without them" goes on.
                    let share: @MainActor () -> Void = {
                        close()
                        model.shareImage()
                    }
                    if model.mayKeep(Texts.share, then: share) { share() }
                }
                .disabled(model.save.busy)
            }
            .padding(.top, Spacing.md)
            Text(Texts.menuNewMandala).textRole(.heading).foregroundStyle(t.text).padding(.top, Spacing.xl).padding(.bottom, Spacing.sm)
            HStack(spacing: Spacing.sm) {
                ForEach(Self.dice, id: \.kind) { die in DieTile(die: die) { model.tryNew(die.kind) } }
            }
            divider.padding(.top, Spacing.xl)
            // A setting that keeps running, so a row of its own with its state (Android's main page).
            MenuRow(icon: "calendar", label: Texts.dailyWallpaper,
                    detail: model.proOwned ? model.dailyToday.map { Texts.dailyToday(WidgetSync.name($0)) } : Texts.dailyPro,
                    action: { page = .daily }) { chevron }
            divider
            MenuRow(icon: "moon", label: Texts.menuTheme, detail: nil, action: nil) { themeChoice }
            divider
            MenuRow(icon: "slider.horizontal.3", label: Texts.menuMore, detail: nil, action: { page = .more }) { chevron }
            divider
            MenuRow(icon: "info.circle", label: Texts.menuDahlia, detail: Texts.menuDahliaDetail, action: { page = .dahlia }) { chevron }
        }
    }

    private var current: some View {
        let layers = model.params.layers.filter(\.isEnabled)
        return HStack(spacing: Spacing.md) {
            Thumb(image: model.preview).frame(width: Sizes.favourite / 2, height: Sizes.favourite / 2)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(layers.isEmpty ? Texts.menuNoLayers : layers.map { Texts.style($0.style) }.joined(separator: ", "))
                    .textRole(.heading).foregroundStyle(t.text).lineLimit(1)
                Text(Texts.layersSummary(layers.count, emblem: model.params.emblemLayer.isEnabled)).textRole(.note).foregroundStyle(t.textMuted)
            }
            Spacer(minLength: 0)
        }
    }

    // ── More page: files, the two switches ──

    private var morePage: some View {
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: Texts.menuBack) { page = .main }
            Text(Texts.menuMore).textRole(.title).foregroundStyle(t.text).padding(.top, Spacing.sm)
            divider.padding(.top, Spacing.sm)
            MenuRow(icon: "scribble", label: model.proOwned ? Texts.lineDrawing : Texts.lineDrawingPro, detail: nil, action: { page = .lineDrawing }) { chevron }
            divider
            MenuRow(icon: "folder", label: Texts.menuOpenFile, detail: Texts.menuOpenFileDetail, action: { showOpen = true }) { chevron }
            divider
            MenuRow(icon: "square.and.arrow.down", label: Texts.menuSaveSettings, detail: Texts.menuSaveSettingsDetail, action: { showExport = true }) { chevron }
            divider
            SwitchMenuRow(icon: "bolt", label: Texts.fastPreview, detail: Texts.fastPreviewDetail,
                          checked: model.fastPreview) { model.setFastPreview($0) }
            divider
            SwitchMenuRow(icon: "eye", label: Texts.chipPreviews, detail: Texts.chipPreviewsDetail,
                          checked: model.chipPreviews) { model.setChipPreviews($0) }
            #if DEBUG
            // Debug builds only: the dot on widget pictures (global rule), off for screenshots.
            divider
            SwitchMenuRow(icon: "circle.bottomrighthalf.filled", label: Texts.widgetMarkDebug, detail: Texts.widgetMarkDebugDetail,
                          checked: widgetMark) { on in
                widgetMark = on
                model.setWidgetMark(on)
            }
            #endif
        }
    }

    // ── Line drawing page (ADR-0013, Pro): an SVG to Files; Photos does not take SVG ──

    private var lineDrawingPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: Texts.menuBack) { page = .more }
            Text(model.proOwned ? Texts.lineDrawing : Texts.lineDrawingPro).textRole(.title).foregroundStyle(t.text).padding(.top, Spacing.sm)
            let about = Texts.lineDrawingDetail + (IosSave.shared.hasPointLayers(params: model.params) ? " " + Texts.lineDrawingPoints : "")
            Text(about).textRole(.body).foregroundStyle(t.textMuted).padding(.top, Spacing.xs)
            divider.padding(.top, Spacing.md)
            ForEach(IosSave.shared.lineDrawingLooks(), id: \.self) { look in
                MenuRow(icon: "doc.badge.arrow.up", label: look == .lines ? Texts.lineDrawingLines : Texts.lineDrawingColour, detail: nil,
                        action: model.save.busy ? nil : {
                            Task {
                                // Without Pro the Pro sheet opens on top of the menu.
                                if let made = await model.lineDrawing(look) { lineDrawing = SvgDocument(text: made.text, name: made.name) }
                            }
                        }) { chevron }
                divider
            }
        }
        // On this page, not beside "Save settings": two file exporters on one view show only one.
        .fileExporter(isPresented: Binding(get: { lineDrawing != nil }, set: { if !$0 { lineDrawing = nil } }), document: lineDrawing,
                      contentType: .svg, defaultFilename: lineDrawing?.name) { result in
            switch result {
            case .success: model.lineDrawingSaved(true)
            case .failure(let error):
                if (error as? CocoaError)?.code != .userCancelled { model.lineDrawingSaved(false, reason: error.localizedDescription) }
            }
            lineDrawing = nil
            close()
        }
    }

    // ── Dahlia page: Pro, widgets, guide, tour, rating, About ──

    private var dahliaPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: Texts.menuBack) { page = .main }
            Text(Texts.menuDahlia).textRole(.title).foregroundStyle(t.text).padding(.top, Spacing.sm)
            divider.padding(.top, Spacing.sm)
            // With Pro a page with what it includes, otherwise the Pro sheet (Android's DahliaPage).
            MenuRow(icon: "star", label: Texts.proTitle, detail: model.proOwned ? Texts.menuProActive : Texts.menuProDetail, action: {
                if model.proOwned { page = .pro } else { model.showPro() }
            }) { chevron }
            divider
            // Each widget's own mandala and colours (ADR-0020); on iOS changed through the system's Edit Widget.
            MenuRow(icon: "square.grid.2x2", label: Texts.menuWidgets,
                    detail: widgetRows.isEmpty ? Texts.menuWidgetsNone : Texts.widgetsCount(widgetRows.count),
                    action: { page = .widgets }) { chevron }
            divider
            MenuRow(icon: "questionmark.circle", label: Texts.menuGuide, detail: nil, action: { Links.openGuide() }) { chevron }
            divider
            // The short tour of the editor that a new install starts with.
            MenuRow(icon: "map", label: Texts.menuTour, detail: nil, action: {
                close()
                tour.start()
            }) { chevron }
            divider
            // The permanent way to rate: the App Store review page, never an own pre-question.
            MenuRow(icon: "heart", label: Texts.menuRate, detail: nil, action: { ReviewPrompt.openReviewPage() }) { chevron }
            divider
            MenuRow(icon: "info.circle", label: Texts.menuAbout, detail: nil, action: { showAbout = true }) { chevron }
        }
    }

    // ── Widgets page: the placed widgets by size with what each shows (Android's WidgetsPage). iOS opens a widget's
    //    settings only from the home screen, so the rows do not open anything. ──

    private var widgetsPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: Texts.menuDahlia) { page = .dahlia }
            Text(Texts.menuWidgets).textRole(.title).foregroundStyle(t.text).padding(.top, Spacing.sm)
            Text(Texts.menuWidgetsAbout).textRole(.body).foregroundStyle(t.textMuted).padding(.top, Spacing.xs)
            divider.padding(.top, Spacing.md)
            ForEach(widgetRows) { row in
                HStack(spacing: Spacing.md) {
                    Thumb(image: row.thumbnail).frame(width: Sizes.favourite / 2, height: Sizes.favourite / 2)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(row.title).textRole(.body).foregroundStyle(t.text)
                        Text(row.detail).textRole(.note).foregroundStyle(t.textMuted).lineLimit(2)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.vertical, Spacing.md)
                divider
            }
            if widgetRows.isEmpty {
                Text(Texts.menuWidgetsEmpty).textRole(.note).foregroundStyle(t.textMuted).padding(.top, Spacing.md)
            }
        }
    }

    // ── Pro page: with Pro, the thanks and what it includes, in the sentences the Pro sheet sells it with ──

    private var proPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: Texts.menuDahlia) { page = .dahlia }
            Text(Texts.proTitle).textRole(.title).foregroundStyle(t.text).padding(.top, Spacing.sm)
            Text(Texts.menuProThanks).textRole(.body).foregroundStyle(t.textMuted).padding(.top, Spacing.xs)
            divider.padding(.top, Spacing.md)
            ForEach([("slider.horizontal.3", Texts.proAddsMaking), ("square.and.arrow.down", Texts.proAddsSaving),
                     ("photo", Texts.proAddsWallpapers)], id: \.0) { icon, text in
                HStack(alignment: .top, spacing: Spacing.md) {
                    Image(systemName: icon).foregroundStyle(t.textMuted).frame(width: Sizes.icon)
                    Text(text).textRole(.body).foregroundStyle(t.text).fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
                .padding(.vertical, Spacing.md)
                divider
            }
            Text(Texts.menuProOnce).textRole(.note).foregroundStyle(t.textMuted).padding(.top, Spacing.md)
        }
    }

    // ── Parts ──

    var divider: some View { Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2) }

    var chevron: some View { Image(systemName: "chevron.right").foregroundStyle(t.textMuted) }

    /// Dark or light, as two halves of one rounded control (not following the system, as on Android).
    private var themeChoice: some View {
        HStack(spacing: 0) {
            ForEach([false, true], id: \.self) { light in
                let on = lightTheme == light
                Button { lightTheme = light } label: {
                    Text(light ? Texts.menuThemeLight : Texts.menuThemeDark).textRole(.heading)
                        .foregroundStyle(on ? t.text : t.textMuted)
                        .padding(.horizontal, Spacing.md).padding(.vertical, Spacing.xs)
                        .background(on ? t.surface : t.surfaceRaised, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(Spacing.xs)
        .background(t.surfaceRaised, in: Capsule())
    }

    struct Die { let kind: NewKind; let icon: String; let steps: Int }

    static let dice = [
        Die(kind: .variation, icon: "die.face.1", steps: 1),
        Die(kind: .layers, icon: "die.face.3", steps: 2),
        Die(kind: .everything, icon: "die.face.5", steps: 3),
    ]
    /// How many steps of change the dice show (Android: one, two or three of three).
    static let stepCount = 3
}

/// A die of the menu: icon, name, what changes, and how much (one, two or three of three steps).
private struct DieTile: View {
    let die: MenuSheet.Die
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            VStack(spacing: Spacing.xs) {
                Image(systemName: die.icon).foregroundStyle(t.accent)
                Text(Texts.newKind(die.kind)).textRole(.heading).foregroundStyle(t.text).lineLimit(1).minimumScaleFactor(0.7)
                Text(Texts.newKindDetail(die.kind)).textRole(.note).foregroundStyle(t.textMuted).lineLimit(1).minimumScaleFactor(0.7)
                HStack(spacing: Spacing.xs) {
                    ForEach(0..<MenuSheet.stepCount, id: \.self) { i in
                        RoundedRectangle(cornerRadius: Radius.sm).fill(i < die.steps ? t.accent : t.outline)
                            .frame(width: Spacing.md, height: Sizes.progress)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.xs)
            .padding(.vertical, Spacing.md)
            .background(t.surfaceRaised, in: RoundedRectangle(cornerRadius: Radius.md))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Texts.newKind(die.kind))
        .accessibilityHint(Texts.newKindDetail(die.kind))
    }
}

/// A raised tile with an icon above its label (Android's menu pills).
struct Pill: View {
    let icon: String
    let label: String
    let action: () -> Void
    @Environment(\.tokens) private var t
    @Environment(\.isEnabled) private var enabled

    var body: some View {
        Button(action: action) {
            VStack(spacing: Spacing.xs) {
                Image(systemName: icon).foregroundStyle(enabled ? t.accent : t.textMuted)
                Text(label).textRole(.note).foregroundStyle(enabled ? t.text : t.textMuted)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(t.surfaceRaised, in: RoundedRectangle(cornerRadius: Radius.md))
        }
        .buttonStyle(.plain)
    }
}

/// A menu line: icon, title, a muted line below it and something at the end; the whole line is tappable when it has an action.
struct MenuRow<Trailing: View>: View {
    let icon: String
    let label: String
    let detail: String?
    let action: (() -> Void)?
    @ViewBuilder var trailing: Trailing
    @Environment(\.tokens) private var t

    var body: some View {
        let row = HStack(spacing: Spacing.md) {
            Image(systemName: icon).foregroundStyle(t.textMuted).frame(width: Sizes.icon)
            VStack(alignment: .leading, spacing: 0) {
                Text(label).textRole(.body).foregroundStyle(t.text)
                if let detail { Text(detail).textRole(.note).foregroundStyle(t.textMuted).multilineTextAlignment(.leading) }
            }
            Spacer(minLength: 0)
            trailing
        }
        .padding(.vertical, Spacing.md)
        .contentShape(Rectangle())
        if let action {
            Button(action: action) { row }.buttonStyle(.plain)
        } else {
            row
        }
    }
}

/// A menu line with an icon, a title, a line below it and a switch; the whole line switches.
struct SwitchMenuRow: View {
    let icon: String
    let label: String
    let detail: String
    let checked: Bool
    let onChange: (Bool) -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: icon).foregroundStyle(t.textMuted).frame(width: Sizes.icon)
            VStack(alignment: .leading, spacing: 0) {
                Text(label).textRole(.body).foregroundStyle(t.text)
                Text(detail).textRole(.note).foregroundStyle(t.textMuted).multilineTextAlignment(.leading)
            }
            Spacer(minLength: 0)
            Toggle("", isOn: Binding(get: { checked }, set: { onChange($0) })).labelsHidden().tint(t.accent)
                .accessibilityLabel(label)
        }
        .padding(.vertical, Spacing.md)
        .contentShape(Rectangle())
        .onTapGesture { onChange(!checked) }
    }
}

struct BackRow: View {
    let label: String
    let action: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.xs) {
                Image(systemName: "chevron.left")
                Text(label).textRole(.body)
            }
            .foregroundStyle(t.textMuted)
            .padding(.vertical, Spacing.xs)
        }
        .buttonStyle(.plain)
    }
}

/// A square picture on black (the current mandala or a new one).
struct Thumb: View {
    let image: CGImage?
    @Environment(\.tokens) private var t

    var body: some View {
        ZStack {
            t.canvas
            if let image { Image(decorative: image, scale: 1).resizable().scaledToFill() }
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
    }
}

/// Second step: the current mandala and the new one side by side; nothing changes before "Use new".
private struct Compare: View {
    let candidate: Candidate
    let close: @MainActor () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            BackRow(label: Texts.menuBack) { model.closeCandidate() }
            HStack(spacing: Spacing.sm) {
                captioned(Texts.menuNow) { Thumb(image: model.preview) }
                Image(systemName: "arrow.right").foregroundStyle(t.textMuted)
                captioned(Texts.newKind(candidate.kind)) {
                    Thumb(image: candidate.image).overlay(alignment: .bottom) {
                        if candidate.rendering { BusyBar() }
                    }
                }
            }
            .padding(.top, Spacing.sm)
            HStack(spacing: Spacing.sm) {
                ForEach(MenuSheet.dice, id: \.kind) { die in
                    Button { model.tryNew(die.kind) } label: {
                        Text(Texts.newKind(die.kind)).textRole(.heading).lineLimit(1).minimumScaleFactor(0.7)
                            .foregroundStyle(die.kind == candidate.kind ? t.onAccent : t.text)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, Spacing.sm)
                            .background(die.kind == candidate.kind ? t.accent : t.surfaceRaised, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, Spacing.md)
            HStack(spacing: Spacing.sm) {
                Button { model.tryNew(candidate.kind) } label: {
                    Label(Texts.menuTryAnother, systemImage: "die.face.3").textRole(.heading).frame(maxWidth: .infinity).padding(.vertical, Spacing.xs)
                }
                .buttonStyle(.bordered)
                .tint(t.accent)
                Button {
                    model.useCandidate()
                    close()
                } label: {
                    Label(Texts.menuUseNew, systemImage: "checkmark").textRole(.heading).frame(maxWidth: .infinity).padding(.vertical, Spacing.xs)
                }
                .buttonStyle(.borderedProminent)
                .tint(t.accent)
                .foregroundStyle(t.onAccent)
                .disabled(candidate.image == nil || candidate.rendering)
            }
        }
    }

    private func captioned<Content: View>(_ caption: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: Spacing.xs) {
            content()
            Text(caption).textRole(.note).foregroundStyle(t.textMuted)
        }
        .frame(maxWidth: .infinity)
    }
}

/// The settings file "Save settings" writes (the macOS app's format).
struct SettingsDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.json]
    let text: String

    init(text: String) { self.text = text }

    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

/// The line drawing "Save to Files" writes (ADR-0013).
struct SvgDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.svg]
    let text: String
    var name = ""

    init(text: String, name: String) {
        self.text = text
        self.name = name
    }

    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
