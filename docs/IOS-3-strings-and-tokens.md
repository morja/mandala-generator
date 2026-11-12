# Dahlia – Mandala Maker

Session: iOS WP-4 strings and tokens · 12 November 2026

## IOS-3: String catalogue, design tokens and source rules for the iPhone app (PLAN-4 WP-4)

What WP-4 of [PLAN-4](PLAN-4-iphone-app.md) added: two generators (Android to iOS), the text roles of approval A9, and a check that fails on stale generated files and on literals in the screens. WP-1's hand-written `Texts.swift` strings and `Tokens.swift` are replaced.

### Findings

1. **Strings: how it works.** `ios/scripts/gen-strings` (plain Python 3, no package) reads every `android/app/src/main/res/values*/strings*.xml` of the ten languages (values, de, es, fr, it, pt → pt-BR, ru, ja, ko, zh-rCN → zh-Hans) and writes `ios/Dahlia/Resources/Localizable.xcstrings` (572 keys, Android's keys kept, source language en). `translatable="false"` entries are skipped. `ios/scripts/gen-strings --check` exits 1 when the committed catalogue differs from what the sources produce (compared as parsed JSON, so Xcode reformatting does not matter). The file layout follows Xcode's, so opening it in Xcode does not rewrite it.

2. **iOS-only texts.** `ios/Dahlia/Strings/values*/strings_ios.xml`, Android format, one per language, so the same translation round covers them. A key that exists in both places takes the iOS text in every language; a language the iOS file lacks falls back to the iOS English text, never to Android's (which names Pictures and Google Play). Ten keys today: five overrides (`editor_favourites_empty`, `editor_favourites_hint`, `export_saving_to_pictures`, `message_image_failed`, `message_saved_to_pictures`) and five new `ios_*` keys (save square, save to Photos, photos denied, open Settings, favourite label). The translations of these ten keys in nine languages are my drafts using Apple's own names (Fotos, Photos, Foto, 写真, 사진, 照片, Ajustes/Réglages/Impostazioni, …); they need a native read in the translation round.

3. **What did not convert one to one.** Placeholders: `%1$s` becomes `%1$@`, `%1$d` becomes `%1$lld` (the catalogue's integer type), `%%` stays `%%` in strings with other placeholders and becomes `%` otherwise. Android escapes (`\'`, `\n`, `\"`) are resolved. Plurals (7 keys, up to six forms for Russian) become `variations.plural`; no screen uses one yet, so they are converted but not run on a device. A text missing in a language is left out so iOS falls back to English; today none is missing. About ten Android keys name Android places (Google Play, Pictures, back button); they stay in the catalogue unused, and iOS screens must not use them, they need an iOS key when a screen needs the text. Nothing else failed to convert.

4. **Using the texts.** `Texts.swift` is now thin: it maps names to catalogue keys (`String(localized:)`, positional `String(format:)`), style labels use `style_` plus the Kotlin enum name in lower case. The check verifies every key in `Texts.swift` exists in the catalogue. The project got `developmentLanguage: en` and the ten `knownRegions` in `project.yml`.

5. **Tokens.** `ios/scripts/gen-tokens` reads `Tokens.kt` (Spacing, Radius, Sizes with their doc comments, dark and light colours; dp become points one to one) and writes `ios/Dahlia/Sources/Theme/Tokens.swift`; `--check` fails on a stale file. Two tokens that WP-1 did not copy now come along (`Spacing.controlsWidth`, `twoPaneMinWidth`; unused on the phone). The generated values equal WP-1's hand copy.

6. **Text roles (A9).** Six roles in the generator's template, on Dynamic Type styles: `display` (.title, about 28, unused so far), `title` (.title3 semibold, 20), `heading` (.subheadline semibold, 15), `body` (.subheadline, 15), `note` (.caption, 12), `value` (.caption with tabular digits, 12). Neighbouring sizes 28, 20, 15, 12 differ by at least 1.2; roles of equal size differ by weight or digits, as on Android. WP-1's four roles keep their look; `value` replaces `note` plus `.monospacedDigit()` on the slider numbers (same result).

7. **The check.** `ios/scripts/check-sources` (Python) fails on, in `ios/Dahlia/Sources` apart from the generated `Tokens.swift`: colour literals (`Color(`, `UIColor(`, named colours); `.font(` and font sizes; number literals in layout calls in `UI/` and `App/` (frame, padding, spacing, cornerRadius, lineWidth, offset, opacity, …); readable text literals in `Text`, `Button`, `Label`, `accessibilityLabel`; keys missing from the catalogue. Shares of a width (`* 0.34`, `/ 3`) and `0` are allowed. A line may carry `// token-exempt: <reason>`; one use, the mandala's own palette colour from core. I tried it with a seven-point padding and a colour literal: both fail. `ios/scripts/check` now runs `gen-strings --check`, `gen-tokens --check` and `check-sources` before it builds; `ios/Dahlia/scripts/generate` regenerates both files. The Git hook also runs the two staleness checks when Android texts or `Tokens.kt` are staged, so changing a string on Android without regenerating blocks the commit.

8. **Look unchanged.** English dark and light editor on the iPhone 18 Pro Max against WP-1's pictures: same size, only about 0.03 % of the bytes differ (the clock). Screens in German, Japanese and Russian on the iPhone 17e (390 pt, the smallest supported width) and the iPhone 18 Pro Max (440 pt), editor, favourites sheet, save sheet: `docs/qa/shots/ios-wp4-2026-11-12/` in the main checkout (18 pictures plus the two English ones). Light theme was checked in English only.

9. **Overflow at the smallest width.** None in the three languages on the three screens. The tightest is the favourites button: "Aktuelles hinzufügen" and "Добавить текущую" fit on one line at 390 pt with a few points to spare. Not looked at: the other eight languages, and Dynamic Type sizes above the default.

10. **Texts still English.** Palette names ("Prism") come from core in English; Android has no strings for them either (a later package, with the palette picker). The starter favourites' quotes are content. A found bug: an integer in a localised format printed "1.024" in German; fixed by formatting without the locale (`locale: nil`).

### Selbst entschieden

- Strings script in Python in `ios/scripts/` as the plan says; the iOS-only files live in `ios/Dahlia/Strings/values*/strings_ios.xml` (name and place mine).
- `Texts.swift` stays as a thin hand-written file of key names instead of a generated accessor for all 572 keys; the check catches wrong keys.
- Number literals `0` and shares after `*` or `/` are allowed in screens; the two hairlines (`height: 1`) became `Sizes.swatchRing / 2`, the same 1 pt Parts.swift already used. The fallback fill `.white` for an empty palette became `.clear` (never happens with core's palettes).
- Text roles: six as above, with `display` and `value` as the additions.
- A `// token-exempt: <reason>` escape for colours from the mandala itself.
- The Git hook runs the staleness checks for staged Android texts and `Tokens.kt` (small change to `.githooks/pre-commit`).
- Translations of the ten iOS keys written by me (finding 2).
