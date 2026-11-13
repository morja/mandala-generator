import CoreText
import SwiftUI
@preconcurrency import MandalaCore

/// The Text tab (Android's `TextTab`): the switch with die and Reset, the words in the picture's font with
/// the quote collection and a new quote, then font, size and place (with moving the text up and down on the
/// picture), light and colour. Text is Pro to keep; moving it is free, like the height slider.
struct TextTab: View {
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var choosing = false

    var body: some View {
        let parts = IosEditingParts.shared
        let text = model.params.textLayer
        VStack(alignment: .leading, spacing: Spacing.sm) {
            SwitchHeader(label: model.proOwned || text.isEnabled ? Texts.textShow : Texts.withPro(Texts.textShow), checked: text.isEnabled,
                         dieLabel: Texts.randomTextLook,
                         onChange: { model.setTextShown($0) },
                         onDie: { model.randomizeText() },
                         onReset: { model.apply(parts.textReset(params: model.params)) })
            if !text.isEnabled {
                Text(Texts.textOffNote).textRole(.note).foregroundStyle(t.textMuted)
            } else {
                if !model.proOwned { ProNote(text: Texts.proNoteText) }
                Words(text: text, onChoose: { choosing = true })
                TextControls(text: text)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.sm)
        .padding(.bottom, Spacing.xl)
        .sheet(isPresented: $choosing) {
            QuoteSheet { choosing = false }
        }
    }
}

/// The words (DM-13 B): both fields always open, in the picture's font; Collection and New quote in the
/// card's head; keeping the quote is a bookmark in the text field, filled once kept, tapped again to let it go.
private struct Words: View {
    let text: TextLayerSettings
    let onChoose: () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    /// The field with the keyboard (QA-10 item 4): the picture folds small while typing, the field scrolls into
    /// view above the keyboard, and Done on the bar above the keyboard puts it away.
    @FocusState private var focus: TextTabField?

    var body: some View {
        ControlGroup(Texts.words, initiallyOpen: true, trailing: { heading }) {
            wordsField
            authorField
            SwitchRow(label: Texts.showAuthor, checked: text.showAuthor) {
                model.apply(IosEditingParts.shared.withShowAuthor(params: model.params, show: $0))
            }
        }
        .onChange(of: focus) { _, field in model.textColours.typing = field }
        // Another tab, a sheet or the editor leaving: the keyboard goes with the field's focus.
        .onChange(of: model.textColours.typing) { _, field in if field == nil { focus = nil } }
        // Text switched off while typing: this view goes, and its focus change would never be seen.
        .onDisappear {
            focus = nil
            model.textColours.typing = nil
        }
        #if DEBUG
        .task {
            // Screenshots of typing (`-shotTyping words|author`): the keyboard on that field once the editor shows.
            try? await Task.sleep(for: .seconds(2))
            switch ShotSetup.typing {
            case "words": focus = .words
            case "author": focus = .author
            default: break
            }
        }
        #endif
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(Texts.done) { focus = nil }.tint(t.accent)
            }
        }
    }

    /// Collection and New quote in the card's head.
    private var heading: some View {
        HStack(spacing: 0) {
            Button(action: onChoose) { Image(systemName: "text.quote").foregroundStyle(t.accent).frame(width: Sizes.icon * 2, height: Sizes.icon * 2) }
                .buttonStyle(.plain)
                .accessibilityLabel(Texts.collection)
            Button { model.randomQuote() } label: { Image(systemName: "shuffle").foregroundStyle(t.accent).frame(width: Sizes.icon * 2, height: Sizes.icon * 2) }
                .buttonStyle(.plain)
                .accessibilityLabel(Texts.newQuote)
        }
    }

    private var wordsField: some View {
        let kept = model.wordsKept
        let blank = text.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let binding = Binding<String>(get: { text.text }, set: { model.apply(IosEditingParts.shared.withWords(params: model.params, text: $0)) })
        return FieldBox(label: Texts.textField) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                TextField(Texts.textField, text: binding, axis: .vertical)
                    .focused($focus, equals: .words)
                    .lineLimit(1...6)
                    .font(WordsFont.font(text.fontName, italic: false, role: .title3)) // token-exempt: the words in the picture's own font
                    .foregroundStyle(t.text)
                Button {
                    if kept { model.letGoOfWords() } else { model.keepWords() }
                } label: {
                    Image(systemName: kept ? "bookmark.fill" : "bookmark").foregroundStyle(blank ? t.textMuted : t.accent)
                        .frame(width: Sizes.icon * 2, height: Sizes.icon * 2)
                }
                .buttonStyle(.plain)
                .disabled(blank)
                .accessibilityLabel(kept ? Texts.kept : Texts.keep)
            }
        }
        .id(TextTabField.words)
    }

    private var authorField: some View {
        let binding = Binding<String>(get: { text.customAuthor }, set: { model.apply(IosEditingParts.shared.withAuthor(params: model.params, author: $0)) })
        return FieldBox(label: Texts.author) {
            TextField(Texts.author, text: binding)
                .focused($focus, equals: .author)
                .submitLabel(.done)
                .font(WordsFont.font(text.fontName, italic: true, role: .body)) // token-exempt: the author in the picture's own font
                .foregroundStyle(t.text)
                .frame(minHeight: Sizes.icon * 2)
        }
        .id(TextTabField.author)
    }
}

/// The two fields of the Text tab, as focus and as places to scroll to while typing (QA-10 item 4).
enum TextTabField: Hashable {
    case words, author
}

/// A framed field with its label above, like Android's outlined text field.
private struct FieldBox<Content: View>: View {
    let label: String
    @ViewBuilder let content: () -> Content
    @Environment(\.tokens) private var t

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label).textRole(.note).foregroundStyle(t.textMuted)
            content()
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.sm)
                .overlay(RoundedRectangle(cornerRadius: Radius.sm).strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
        }
        .accessibilityElement(children: .contain)
    }
}

/// The picture's font for the words fields, at the size of a Dynamic Type style, so large text works.
enum WordsFont {
    static func font(_ name: String, italic: Bool, role: UIFont.TextStyle) -> Font { // token-exempt: the picture's own font for the words fields
        let size = UIFont.preferredFont(forTextStyle: role).pointSize // token-exempt: Dynamic Type size for the picture's font
        let font = TextFont(name: name, sizePx: Float(size), italic: italic, trackingPx: 0)
        let base = CTFontCreateWithName(FontMap.installedName(for: font) as CFString, size, nil)
        let face = italic ? (CTFontCreateCopyWithSymbolicTraits(base, 0, nil, .traitItalic, .traitItalic) ?? base) : base
        return Font(face) // token-exempt: the picture's own font
    }
}

private struct TextControls: View {
    let text: TextLayerSettings
    @Environment(EditorModel.self) private var model

    var body: some View {
        let parts = IosEditingParts.shared
        let d = parts.defaultText()
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ControlGroup(Texts.font, summary: parts.fontDisplayName(name: text.fontName), changed: text.fontName != d.fontName) {
                FlowLayout {
                    ForEach(parts.fontNames(), id: \.self) { name in
                        Chip(label: parts.fontDisplayName(name: name), selected: text.fontName == name) { model.apply(parts.withFont(params: model.params, name: name)) }
                    }
                }
            }
            ControlGroup(Texts.sizeAndPlace, changed: differs((text.fontSize, d.fontSize), (text.fontWidth, d.fontWidth), (text.tracking, d.tracking), (text.offsetY, d.offsetY))) {
                slider(.size)
                slider(.width)
                slider(.tracking)
                slider(.height)
                // Moving by hand is free, like the height slider; the text only goes up and down (UX-1 20).
                HStack {
                    Spacer(minLength: 0)
                    MoveButton(target: .text, enabled: text.isEnabled)
                }
            }
            ControlGroup(Texts.light, changed: differs((text.glow, d.glow), (text.cloudOpacity, d.cloudOpacity), (text.cloudScale, d.cloudScale), (text.shadowOpacity, d.shadowOpacity))) {
                slider(.glow)
                slider(.cloud)
                slider(.cloudSize)
                // The colours only where the cloud or the shadow shows; the default of both is black.
                if text.cloudOpacity > 0 {
                    colour(Texts.cloudColour, .cloudHue, .cloudSaturation, .cloudBrightness, inset: true)
                }
                slider(.shadow)
                if text.shadowOpacity > 0 {
                    colour(Texts.shadowColour, .shadowHue, .shadowSaturation, .shadowBrightness, inset: true)
                }
            }
            TextColourGroup()
        }
    }

    private func value(_ s: TextSlider) -> Double { IosEditingParts.shared.textValue(params: model.params, slider: s) }

    private func slider(_ s: TextSlider) -> some View {
        SliderRow(label: Texts.slider(s), value: value(s), range: s.min...s.max, defaultValue: s.defaultValue) {
            model.apply(IosEditingParts.shared.textWithValue(params: model.params, slider: s, value: $0))
        }
    }

    private func colour(_ title: String, _ h: TextSlider, _ s: TextSlider, _ b: TextSlider, inset: Bool) -> some View {
        ColourGroup(title: title, hue: value(h), saturation: value(s), brightness: value(b),
                    defaults: (h.defaultValue, s.defaultValue, b.defaultValue), inset: inset, brightnessRange: b.min...b.max) { hv, sv, bv in
            let parts = IosEditingParts.shared
            let one = parts.textWithValue(params: model.params, slider: h, value: hv)
            let two = parts.textWithValue(params: one, slider: s, value: sv)
            model.apply(parts.textWithValue(params: two, slider: b, value: bv))
        }
    }
}

/// The quote list (Android's `QuoteDialog`, ADR-0019): language chips when there is more than one collection,
/// the user's own quotes with a remove button, then the built-in public-domain collections switched on.
private struct QuoteSheet: View {
    let onClose: () -> Void
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        let parts = IosEditingParts.shared
        let all = parts.quoteCollections()
        let shown = parts.quoteCollectionsFor(languages: model.quoteLanguages)
        let mine = model.myQuotes
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    if all.count > 1 {
                        FlowLayout {
                            ForEach(all, id: \.language) { c in
                                Chip(label: c.name, selected: model.quoteLanguages.contains(c.language)) { model.toggleQuoteLanguage(c.language) }
                            }
                        }
                        .padding(.bottom, Spacing.sm)
                    }
                    if !mine.isEmpty {
                        Text(Texts.yourQuotes).textRole(.heading).foregroundStyle(t.text).padding(.vertical, Spacing.xs)
                        ForEach(Array(mine.enumerated()), id: \.offset) { _, q in
                            QuoteRow(quote: q, onPick: pick) {
                                Button { model.removeMyQuote(q) } label: {
                                    Image(systemName: "xmark").foregroundStyle(t.textMuted).frame(width: Sizes.icon * 2, height: Sizes.icon * 2)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(Texts.removeQuote)
                            }
                        }
                    }
                    ForEach(Array(shown.enumerated()), id: \.offset) { i, c in
                        if !mine.isEmpty || shown.count > 1 {
                            Text(shown.count > 1 ? c.name : Texts.quotesCollection).textRole(.heading).foregroundStyle(t.text)
                                .padding(.top, i > 0 || !mine.isEmpty ? Spacing.md : 0)
                                .padding(.bottom, Spacing.xs)
                        }
                        ForEach(Array(c.quotes.enumerated()), id: \.offset) { _, q in
                            QuoteRow(quote: q, onPick: pick) { EmptyView() }
                        }
                    }
                    Text(Texts.quotesPublicDomain).textRole(.note).foregroundStyle(t.textMuted).padding(.top, Spacing.md)
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.bottom, Spacing.xl)
            }
            .background(t.background)
            .navigationTitle(Texts.quotes)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(Texts.close, action: onClose).tint(t.accent)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func pick(_ q: Quote) {
        model.useQuote(q)
        onClose()
    }
}

private struct QuoteRow<Trailing: View>: View {
    let quote: Quote
    let onPick: (Quote) -> Void
    @ViewBuilder let trailing: () -> Trailing
    @Environment(\.tokens) private var t

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Button { onPick(quote) } label: {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(quote.text).textRole(.body).foregroundStyle(t.text).multilineTextAlignment(.leading)
                    if !quote.author.isEmpty { Text(quote.author).textRole(.note).foregroundStyle(t.textMuted) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, Spacing.sm)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            trailing()
        }
    }
}
