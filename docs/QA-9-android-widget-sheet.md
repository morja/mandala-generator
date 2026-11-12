# Dahlia – Mandala Maker

Session: Android widget sheet · 12 November 2026

## QA-9: Android widget sheet, first run

The Android app now has the shared widget check `bin/widget-sheet` (Cockpit `docs/android-widget-sheet.md`). It draws the real `RemoteViews` of the "Mandala of the day" widget in both layouts (square: the picture alone; wide: the picture without words and the quote beside it) in every measured launcher box, plus the empty widget without a favourite. Nothing about the widget's look or behaviour was changed; the findings below are for Mathis to decide.

The default run (`bin/widget-sheet`) drew 812 cases on the emulator Api34: text sizes 100 and 145 %, German and Russian, light and dark look, six quote sets (quote of the day, short, typical, longest built-in, very long own quote, and the empty widget). Result: 128 cases with errors, 466 with warnings. The sheet is `docs/qa/widget-sheet/latest/index.html` (git-ignored, about 600 MB of pictures). `docs/qa/widget-sheet/reference/` is still empty: no picture is approved until Mathis has looked at the sheet.

Case numbers (#n) are the numbers in that sheet. Each box starts with the layout its launcher shows, then the other layout ("not shown in this box"), then the empty widget. Findings on a layout a box does not show are warnings, so the run fails only on what people see.

### Findings

1. **Which launcher gets which layout.** With `WIDE_RATIO` 1.3 (reported width over height): wide in Pixel 4×2 (#1, aspect 1.65), Nova 5×2 (#117, 1.80), Nova 4×2 (#175, 1.42), One UI S23 4×2 (#233, 1.77) and One UI Tab 4×2 (#291, 1.89); square in Nova 3×2 (#349, 1.04), Nova 2×2 (#407), Nova 5×3 (#465, 1.16), Nova 5×4 (#523), Pixel 2×2 (#581), Pixel 4×4 (#639), Tab 2×2 (#697) and Tab 4×4 (#755). The Cockpit table has no reported size for the Pixel Launcher, so the sheet uses the laid-out box there; with that, Pixel 4×2 is wide. The box `pixel-seen 4x2` (#59 to #116) repeats what Mathis saw on 12 November: options at 1.25, so the square picture fills the 368 × 223 box and is cropped top and bottom (#59 to #68). Nova 4×2 is just above the threshold (1.42) and is the tightest wide box.

2. **The author line silently loses words in almost every wide box.** `widget_author` has one line and no ellipsis, so a name that wraps shows only its first line: "— Emily" instead of "— Emily Dickinson" (#1, Pixel 4×2 at 100 %), "— Henry David" (#9), "— Rabindranath" (#25). At 100 % this happens in Pixel 4×2, Nova 5×2 and Nova 4×2 for most authors (#1, #9, #17, #25, #125, #141, #175 to #201); One UI S23 from short names upward (#241, #257); only the tablet is free at 100 % and loses names at 145 % (#303, #319). Nothing marks the cut, so it reads as a wrong author.

3. **At 145 % the quote loses lines and the author disappears in Nova 5×2 and 4×2.** Six lines of quote need 156 dp, the box has 136, so the sixth line is cut in half and the author line gets 0 dp: Nova 5×2 #121, #137, #145, #153 (own quote), Nova 4×2 #179, #195, #203, #211. In Nova 4×2 at 145 % the text column is only about 58 dp wide, so words break inside ("Clo / uds", "floati / ng", #203). This is Mathis' phone launcher.

4. **Long quotes end in an ellipsis after six lines** (warning, by design of `maxLines` 6): the longest built-in quote (Tagore, 124 characters) in every wide box at 145 % and in Pixel 4×2, Nova 5×2 and 4×2 already at 100 % (#25, #141, #199); the very long own quote everywhere (#33 to #40, #149, #207). Quote of the day and typical quotes at 145 % are shortened too in Pixel 4×2 (#5, #21) and Nova 4×2 (#179). Short quotes fit everywhere.

5. **The square layout cuts long words in the picture.** The square widget shows the favourite's words inside the mandala picture (the text layer at the favourite's own size), which the sheet's rules cannot see in a bitmap, so this comes from looking at the pictures: the very long own quote runs off the top and bottom in every square box (#357, #415, #473, #531, #589, #647, #705, #763); the longest built-in quote loses its author line at the bottom in Nova 3×2 (#355) and Nova 5×3 (#471) and its first line in `pixel-seen` 4×2 (#65). Portrait boxes (2×2, 4×4) show the longest built-in quote whole (#413, #645).

6. **The empty widget's text is cut at 145 %** in the narrow Nova boxes: Nova 2×2 German and Russian (#461, #463: 8 lines need 183 dp, 136 available) and Nova 3×2 Russian (#405).

7. **Debug mark.** It was missing on Android (the iPhone has one since IOS-10). It is now there in the local builds (debug, fast, free): the launcher icon's dot bottom right, set in one place after the picture is drawn (`WidgetMark` in `MandalaWidget.draw`), with a switch "Debug: mark on the widgets" at the end of the menu's Widgets page (local builds only, on by default). Release has no mark. Checked with `bin/widget-sheet mark=on`: `docs/qa/QA-9/widget-mark-pixel-4x2.png`. The sheet's own pictures are drawn without it.

8. **Quote languages.** The built-in collection has German and English only. A German phone gets both, so its longest built-in quote is Tagore's English one; a Russian phone gets the English quotes (ADR-0019 fallback), so "longest built-in in Russian" is the same Tagore quote. Cyrillic wrapping is covered by the own Russian quote (#35, #155).

### Proposed rule for long quotes (for Mathis to decide)

a. **Fit by measuring, then shorten (recommended).** Before choosing the layout, measure the quote with the real paint at the current font scale (rule 2 of the Cockpit widget page). The wide layout keeps a minimum text column of about 140 dp; below that it shows the square picture instead. The quote gets as many lines as fit above the author (not a fixed 6) and ends in an ellipsis; the author line gets an ellipsis too ("— Rabindranath T…") and is never dropped. Words never break inside: if one word does not fit the column, the widget uses the square layout. In the square picture, the text layer is scaled down until the whole quote and author fit, with a lower limit, and a quote that still does not fit is left out of the picture.

b. **Fixed limits.** Wide: quote at most 4 lines with ellipsis, author one line with ellipsis, the threshold raised to about 1.6 so Nova 4×2 becomes square. Simple, but a 1.42 box loses the words entirely and 145 % still needs checking per box.

c. **Words only when short.** Wide shows the quote only up to about 80 characters (else the picture alone, centred); the square picture leaves out text over that length. Clean look, but users who typed long own quotes see them nowhere on the home screen.

### Selbst entschieden

- The sheet activity lives in `android/app/src/localBuild/kotlin` (plus `localBuild/assets` and the activity in `localBuild/AndroidManifest.xml`), the source set the project already shares between debug, fast and free; `bin/widget-sheet` builds and installs the fast build (`INSTALL_BUILD_TYPE`).

- `MandalaWidget` got an `internal fun views(…)` (the same `picture` and `empty` code, with the layout, the words and the renderer passed in), `words` takes the languages and the day as parameters with the old defaults, and `render` and `WIDE_RATIO` became `internal`. The widget itself draws as before.

- The wide layout is wrapped in a `FrameLayout` so the mark can sit bottom right over picture and words; square and empty layouts got the hidden mark view. The release layouts carry the hidden view and an empty drawable; nothing visible changes.

- The mark switch sits on the menu's Widgets page, since Android has no debug settings page; strings are English only (`translatable="false"`), local builds only.

- Square cases are drawn once per text size and look (neither shows in an edge-to-edge picture). Findings on the layout a box does not show are warnings, not errors.

- The canvas is clipped to the widget's corner radius, because a bitmap canvas ignores `clipToOutline`, which the launcher applies in hardware.

- Fixed data: favourite "This is the House of Love…" (starter favourite with words, `widget-sheet-favourites.json`), day 12 November 2026, own long quotes in German, English and Russian written for the sheet (`widget-sheet.json`). Short, typical and longest come from the built-in collection at run time, so a new longer quote shows up in the sheet.

- Extra box `pixel-seen 4x2` with a derived reported width of 279 dp (aspect 1.25), to show what Mathis saw; the Pixel Launcher's real reported size is still unmeasured.

- Default run: text 100 and 145 %, de and ru, light and dark. Full run (`--full`): text 90 to 145 %, de, en, ru, ja, light and dark. The look "like the app" is left out of both runs, since it is the same as light or dark.

- ADR-0020 got an amendment for the Android mark and the sheet.
