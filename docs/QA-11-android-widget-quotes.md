# Dahlia – Mandala Maker

Session: Android widget quotes · 12 November 2026

## QA-11: Widget quotes fit by measuring (rule a of QA-9)

On 12 November 2026 Mathis chose rule a of QA-9 for the home screen widget's words. It is built: the widget measures the quote with its real font before it draws, picks the layout from that, gives the quote as many lines as fit, keeps the author on one line with an ellipsis, never breaks inside a word, shrinks the words in the square picture until they fit (or leaves them out), and the empty widget's text shrinks at large font sizes. RemoteViews stay; no new package, no new colour, two new dimens.

The default run of `bin/widget-sheet` on the emulator Api34 drew the same 812 cases as QA-9, with the same case numbers: before 128 cases with errors and 466 with warnings, now 0 with errors and 516 with warnings. The full run (`bin/widget-sheet --full`: text 90 to 145 %, de, en, ru, ja, light and dark) drew 3640 cases: 0 with errors, 2538 with warnings, of the same kinds as below. The sheet is `docs/qa/widget-sheet/latest/index.html` (git-ignored; it holds the default run, so the numbers below match it). No reference is approved; Mathis looks first.

### How the widget measures

- `WidgetMeasure` (`android/app/src/main/kotlin/com/mathis/mandala/app/WidgetFit.kt`) runs in `MandalaWidget.picture`, so in `WidgetRenderJob` for every widget, and in the sheet with the same code. It builds `StaticLayout`s with the paint of the widget's text views: serif (italic for the author), `widget_quote_size` and `widget_author_size` read from the context's resources, so the system font scale is in them, font padding on, high quality line breaks, no hyphenation and fallback line spacing, as a `TextView` lays out. The layouts now spell out `breakStrategy` and `hyphenationFrequency`, so measuring and drawing cannot drift.

- The box comes from the widget's options from `AppWidgetManager` (portrait: smallest width, largest height). The picture in the wide layout is square and as high as the widget, so the room beside it is width minus height; the larger reported height is used there, so the column is never wider than estimated. The lines are budgeted for 92 % of the reported height, because Nova lays out about 8 % less than it reports (Cockpit table).

- The result is a `WidePlan` (wide or not, the quote's line count, the quote text). It reaches the RemoteViews as the layout choice (wide or square layout), `setMaxLines` on the quote and the text that is set; a quote that does not fit is shortened by the widget itself after its last whole word, with an ellipsis. The author line has `maxLines` 1 and `ellipsize="end"` in the layout.

- The wide layout keeps its words only when: the box is wide (`WIDE_RATIO` as before), the room beside the picture is at least 140 dp (`widget_min_text_column`, padding included), at least two lines of the quote fit (a one-line quote needs one), and no word of the shown lines is wider than the column. Otherwise the widget shows the square picture, the same as a square box.

- Square picture: core's text layer draws the words, and core is unchanged. The widget's call (`MandalaWidget.fitted`) measures the text block the way core's `TextLayer.layout` does (same greedy wrap, line heights, gaps and paddings, with the same `AndroidTextRenderer` the picture is drawn with) in the picture's own size, and passes the renderer a smaller `fontSize` in steps of 0.9 down to 60 % of the favourite's own size until quote and author fit with 4 % margin; below that the text layer is switched off for the widget. The favourite itself is not changed.

- Empty widget: the same measuring with its text; its size goes down in steps of 0.9 to 10 dp (`widget_min_text_size`, the sheet's smallest size) until the text fits the box with whole words.

- The rules are plain functions with unit tests (`WidgetFitTest`): the column, the line budget, the wide decision, the shrink steps, the picture fit with a fake text renderer, words that must not break, and the cut after the last whole word.

### Findings, before and after

1. **The author line lost words without a mark** (QA-9 2, errors in almost every wide box, for example #1, #9, #25). After: the author keeps one line and ends in an ellipsis ("— Emily Dickins…" in #1, "— Kahlil …" where the room is small). The sheet now counts an author's ellipsis as `shortened` (a warning, by design of rule a), not as `cut`. 74 shown cases carry it, mostly at 145 % and in the narrow boxes.

2. **At 145 % the sixth line was cut in half and the author disappeared in Nova 5×2 and 4×2** (QA-9 3: #121, #137, #145, #153, #179, #195, #203, #211). After: Nova 5×2 at 145 % gives the quote the lines that fit (two to five depending on the author line: #121 two lines, #145 three, #153 four) and the author always shows; no line is cut. Nova 4×2 shows the square picture in every case now (see 3).

3. **Nova 4×2's column was 58 dp and broke words** ("Clo/uds", #203). After: its room beside the picture is 76 dp by the reported height, below 140, so the launcher shows the square picture in all of #175 to #232; the wide cases there are drawn as "not shown in this box" (warnings). Two more cases fall back to the square picture because a word would not fit the column at 145 %: Pixel 4×2 and One UI S23 4×2 with the Russian phone's quote of the day ("togetherness", #7 and #239).

4. **Long quotes stopped after six lines** (QA-9 4). After: as many lines as fit, ending after the last whole word with an ellipsis: Pixel 4×2 at 100 % shows eight lines of the longest built-in quote (#25) and nine of the own quote (#33), Nova 5×2 six (#141, #149), the tablet eleven (#323). 80 shown cases end shortened (warning, by design).

5. **The square picture's words ran out of the picture** (QA-9 5: #357, #415, #473, #531, #589, #647, #705, #763; #355, #471, #65). After: the words are measured in the picture's own size and drawn smaller where needed: the very long own quote fits whole in the picture in every square box (#357, #415, #473, #531, #589, #647, #705, #763), drawn smaller; no case in the sheet had to leave the words out, the longest built-in quote keeps its author line in Nova 3×2 (#355) and 5×3 (#471). The sheet's rules cannot see words inside the bitmap, so this comes from looking at the pictures. `pixel-seen` 4×2 (#59 to #68) is unchanged in kind: the picture is made for the reported 1.25 shape and the launcher crops it to 1.65; that is the unmeasured Pixel report from QA-9 1, not the words.

6. **The empty widget's text was cut at 145 %** (QA-9 6: #461, #463, #405). After: the text gets smaller until it fits with whole words (Nova 2×2 Russian at 145 %, #463, seven lines, "приложении" whole). No empty case has a finding.

7. **Warnings left on purpose.** Shown cases: only `shortened` (quote 80, author 74). On the layout a box does not show: `clipped`, `edge`, `uneven-h`, `uneven-v` and `shortened` of the wide layout drawn into boxes that now show the square picture (Nova 4×2, Nova 3×2, the portrait boxes and the five fallback cases); they only matter if the threshold changes.

### Selbst entschieden

- **140 dp counts the room beside the picture with its padding.** Read as the bare text width (padding off), only the tablet would keep the wide layout: Pixel 4×2 has 113 dp, Nova 5×2 113 dp by the reported height, One UI S23 132 dp. Counted with the padding (145, 145 and 164 dp) those stay wide and Nova 4×2 (76 dp) becomes square, which is what QA-9 aimed at. If Mathis meant the bare width, it is the one dimen `widget_min_text_column` (then about 172 dp).

- At least two quote lines beside the picture (one for a one-line quote); a single line of a longer quote reads as a fragment, so the square picture shows instead.

- The quote is shortened by the widget after its last whole word (with trailing commas and dashes dropped before the ellipsis), not by `TextView`'s ellipsis, which cut inside words ("feathers t…"). The author keeps `TextView`'s ellipsis inside the name ("— Emily Di…"), as rule a's example "— Rabindranath T…" shows. The sheet counts a quote ending in "…" as `shortened`.

- The lines are budgeted for 92 % of the reported height on every launcher, since the options do not tell Nova from the Pixel Launcher; on Pixel and One UI this can leave one line unused at the bottom.

- Square picture: shrink steps of 0.9, lower limit 60 % of the favourite's size, 4 % margin inside the picture; Chinese and Japanese text counts as breakable between characters for the "no break inside a word" rule.

- Empty widget: smallest text 10 dp (the sheet's `tiny` limit); where even that does not fit, as many lines as fit with an ellipsis (no box needs it today).

- The sheet decides per case whether the launcher shows a layout (the measured words decide, not the box alone); the square picture, drawn once for all text sizes, counts as shown when any text size of the run shows it.
