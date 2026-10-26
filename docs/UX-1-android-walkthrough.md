# UX-1 · Android walkthrough: details, order and ideas

26 October 2026. A walk through the Android app (fast build, German, emulator, dark and light) after the menu pages and the per-format placement. Mathis asked for small visual faults, logical order of controls, and missing features worth having.

## Fixed in this round

- **F1 Save sheet:** the switch "Quote moves with the mandala" sat on the divider below it; the area above the divider now ends with a small margin. "Adjust" was indented 8 dp further than the switch below it; an unframed group now lines up with the rows around it.
- **F2 Draw tab:** "Draw on image", "Undo" and "Clear" wrapped onto two lines in German. Now one wide main button and two icon buttons (undo arrow, bin) with their names for screen readers; the row fits in every language.
- **F3 Menu:** the theme (dark/light) is back on the main page, above About; "More" keeps files and fast preview.

## Findings, not changed yet

Layout and consistency

1. **Numbers on sliders:** the editor shows 0.00 to 1.00, the save sheet shows 100 %. Proposal: percent everywhere, whole numbers for counts and angles.
2. **"Seed · Anordnung" in the Form group** is the only filled, accent-coloured row in the tabs, and "Seed" stays English. Proposal: a plain row "Arrangement" with the die, like the other rows.
3. **One layer:** the move up/down arrows show, greyed out. Proposal: hide them while there is only one layer.
4. **Draw tab:** "Symmetry" stands outside any group and flush left, while the same slider in Layers sits inside "Form". Proposal: put it into a group with the line settings, or give it the same inset.
5. **Text tab description** says the quote sits "under the mandala"; by default it sits on the mandala's centre. Proposal: "A quote or your own words in the picture, with the author if you like", in all languages.
6. **Text tab, top part:** a die (new look) beside the switch, then "Random quote" and "Choose quote", the two fields, "Save in my quotes" as a loose text button and "Show author" as a loose switch, and only then the groups. Proposal: one group "Words" (choose or random quote, text, author, show author, save) followed by Font, Size and position, Light, Colour.
7. **Random quote language:** in the German app a random quote came out in English (Gibran). Proposal: random quotes in the app language first, other languages only when chosen with the language chips (ADR-0019).
8. **Save sheet:** the "Wallpaper" button has grey text and reads as disabled next to "Save". Proposal: normal text colour.
9. **Switching a part on:** effects have their switch inside the open group ("Show effects"), emblem and text have a plain switch row at the top of the tab. Minor; one pattern would be calmer.
10. **Favourites:** deleting is a hidden long press, there is no rename or reorder, and no undo after deleting. Proposal: long press opens a small menu (Rename, Delete), and a message with "Undo" after deleting.
11. **Canvas height on a phone:** the preview takes a little over half the screen, the controls the rest; long tabs need a lot of scrolling. The shrink button exists. Proposal to try: remember whether the preview was shrunk.

## Ideas for later

12. **Animation export** (MOV/GIF on the Mac): a short rotating video or GIF from Android too, for stories and status.
13. **Daily wallpaper from a chosen set:** pick which favourites take part, instead of all of them in turn.
14. **Home screen widget** with today's mandala, opening the app.
15. **Own palettes:** save a palette from the colours of the current mandala, or build one from a few colours.
16. **Compare:** press and hold the preview to see it without scene effects, to judge how much they add.
17. **Text around the circle:** a quote set along a ring around the mandala, as a text layout next to the centred one.
18. **History strip:** the states the editor keeps for undo as small pictures to jump back to, like the dice comparison in the menu.

## Decisions (Mathis, 26 October 2026)

- 1 Numbers stay as decimals (less room than percent, and some sliders go below zero). 3 The greyed arrows stay with one layer. 4 Symmetry stays on its own in the Draw tab, as the most direct control there. 7 Random quotes stay as they are.
- Done: 2 the die rows ("Arrangement", "Variation") are plain rows with the die on the right, without "Seed"; 5 the text description says "in the picture"; 8 all outlined buttons use the normal text colour (Material's default was the muted one); 10 favourites can be selected by long press and deleted together, with one Undo (Undo for a single favourite existed already). No rename: the name shows almost nowhere.
- 9 Scene tab built as DM-12 (switch heads the tab, flat groups, background with its own switch). 6 Text tab built as DM-13 A: a "Words" card with the quote in the picture's font, tap to edit, and Collection, New quote, Keep below it.
- 11 The small preview is kept while the app runs; it grows back while drawing or moving, when the controls are pulled down at their top, and after a restart. Idea from Mathis: one more, middle size.
- 12 Built as DM-16 A: "Picture | Moving" in the save sheet, the layers turning against each other as an MP4 of 5, 10 or 15 s (Pro, ADR-0010 point 10).
- Ideas wanted: 12 animation export (Pro), 13 daily wallpaper from chosen favourites, 14 home screen widget (probably Pro), 15 own palettes (free, at the palette), 16 compare by holding the preview, 17 text around the circle, 18 history strip. Which of these are Pro is recorded as an amendment of ADR-0016 when each is built.
