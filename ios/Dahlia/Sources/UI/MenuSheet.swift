import SwiftUI
import UniformTypeIdentifiers
@preconcurrency import MandalaCore

/// The menu as a sheet from below (Android's `MenuSheet`, DM-7 B): the mandala on screen with what keeps it, three dice
/// for a new one shown beside the current mandala, the theme and "More" (files, the two switches). What belongs to
/// later packages (wallpapers, Pro, widgets, guide, tour, About, the line drawing, share) is not here yet.
struct MenuSheet: View {
    let close: @MainActor () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @AppStorage("lightTheme") private var lightTheme = false
    @State private var page = Page.main
    @State private var showOpen = false
    @State private var showExport = false

    private enum Page { case main, more }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if let candidate = model.shell.candidate {
                    Compare(candidate: candidate, close: close)
                } else if page == .more {
                    morePage
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
        .fileImporter(isPresented: $showOpen, allowedContentTypes: [.json, .plainText, .data]) { result in
            if case .success(let url) = result {
                model.openFile(url)
                close()
            }
        }
        .fileExporter(isPresented: $showExport, document: SettingsDocument(text: model.settingsFileText), contentType: .json,
                      defaultFilename: model.suggestedSettingsName) { result in
            if case .success = result { model.settingsSaved(true) } else { model.settingsSaved(false) }
            close()
        }
    }

    // ── Main page ──

    private var mainPage: some View {
        VStack(alignment: .leading, spacing: 0) {
            current
            Pill(icon: model.isFavorite ? "star.fill" : "star", label: Texts.menuFavourite) {
                model.addFavorite()
                close()
            }
            .padding(.top, Spacing.md)
            .disabled(!model.canAddFavorite)
            Text(Texts.menuNewMandala).textRole(.heading).foregroundStyle(t.text).padding(.top, Spacing.xl).padding(.bottom, Spacing.sm)
            HStack(spacing: Spacing.sm) {
                ForEach(Self.dice, id: \.kind) { die in DieTile(die: die) { model.tryNew(die.kind) } }
            }
            divider.padding(.top, Spacing.xl)
            MenuRow(icon: "moon", label: Texts.menuTheme, detail: nil, action: nil) { themeChoice }
            divider
            MenuRow(icon: "slider.horizontal.3", label: Texts.menuMore, detail: nil, action: { page = .more }) { chevron }
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
            MenuRow(icon: "folder", label: Texts.menuOpenFile, detail: Texts.menuOpenFileDetail, action: { showOpen = true }) { chevron }
            divider
            MenuRow(icon: "square.and.arrow.down", label: Texts.menuSaveSettings, detail: Texts.menuSaveSettingsDetail, action: { showExport = true }) { chevron }
            divider
            SwitchMenuRow(icon: "bolt", label: Texts.fastPreview, detail: Texts.fastPreviewDetail,
                          checked: model.fastPreview) { model.setFastPreview($0) }
            divider
            SwitchMenuRow(icon: "eye", label: Texts.chipPreviews, detail: Texts.chipPreviewsDetail,
                          checked: model.chipPreviews) { model.setChipPreviews($0) }
        }
    }

    // ── Parts ──

    private var divider: some View { Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2) }

    private var chevron: some View { Image(systemName: "chevron.right").foregroundStyle(t.textMuted) }

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
private struct Pill: View {
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
private struct MenuRow<Trailing: View>: View {
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
private struct SwitchMenuRow: View {
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

private struct BackRow: View {
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
private struct Thumb: View {
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
