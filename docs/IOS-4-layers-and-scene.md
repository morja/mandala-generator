# Dahlia – Mandala Maker

Session: iOS WP-5 layers and scene · 12 November 2026

## IOS-4: Layers and Scene tabs of the iPhone editor (PLAN-4 WP-5)

What WP-5 of [PLAN-4](PLAN-4-iphone-app.md) built: the complete Layers tab and Scene tab as on Android, own palettes with the in-place editor, background pictures, scene effects with hold-to-compare, pictures in the style list, the fast preview switch and the fixed row of five tabs. Checks: `ios/scripts/check`, `ios/scripts/check-full` (parity test included) and `android/scripts/check` are green.

### What was built

1. **Layers tab** (`ios/Dahlia/Sources/UI/LayersTab.swift`): layer list from top to bottom with palette dot, "n · Style", blending line, hide/show, move up and down; Add, Duplicate, Delete and the layer die; groups Style (with "Variation" die and the style list with Android's small pictures), Palette, Blending (modes and opacity), Shape (with the arrangement die, symmetry, scale, complexity, spokes, density and ripple where the style uses them), Position (across, up and down, Centre) and Light and colour. Ranges, defaults, conditions and the order are Android's and come from the shared module; double tap resets a slider.

2. **Own palettes** (`PaletteGroup.swift`, ADR-0021): built-in palettes, "In this picture" with Keep, "My palettes", a plus that starts a new one, the editor in place (name, bar with draggable stop handles, hue, saturation, brightness, From picture, add and remove colour, Save, Cancel restores what the layer had, Delete). `palettes.json` in the Mac's format.

3. **Scene tab** (`SceneTab.swift`): effects first (switch, die, Reset; groups Light, Tone, Texture, Sparkle with its arrangement die, More with relief angle and "Old fade" only where used, Applies to), then the background: Auto, Picture, Colour, Gradient, Sunburst, Grain (only while current) with Android's sliders and switches. Hold the picture to see it without effects.

4. **Background picture**: `ImageIOLoader` implements core's `ImageLoader` on ImageIO (thumbnail call, stretched to the square, last picture cached). The picture comes from Photos (system picker, no permission) or Files; it is copied (at most 2048 px, JPEG) into the app's folder and found again by file name.

5. **Tabs**: five equal text tabs without sideways scrolling. Layers and Scene work; Emblem, Text and Draw show dimmed and cannot be opened until WP-6. Both built tabs stay alive, so each keeps its scroll position and open groups.

6. **Fast preview and "pictures in the lists"**: two switches in a small menu (ellipsis) at the end of the navigation bar, until WP-7's menu. Both are kept across starts. Switching the fast preview drops the kept previews, as on Android.

7. **Shared code**: new rules for Swift in `IosEditing` (layer operations, dice, blend, own palettes, background, effects, with `LayerSlider`, `EffectSlider`, `BackgroundSlider`); `MyPalettes` (list rules), the palette editor's "add colour" and the background switch rule moved from the Android app into `Editing`, and Android calls them from there (behaviour unchanged, its test moved with them). `IosBridge` takes an `ImageLoader`.

8. **Strings and tokens**: all texts through the catalogue; two new iOS-only texts ("Photos", "Files") in all ten languages. The style pictures are Android's files, copied by `ios/scripts/gen-style-previews` (checked by `ios/scripts/check`). No new tokens.

### Differences from Android and why

1. **No "Move on image" and no Pro sheet.** Moving on the picture is WP-6, the purchase WP-9. The Position group has its sliders and the Pro note; the "About Pro" link is missing. Pro is not owned for now (`EditorModel.proOwned`), so the marks show and nothing is unlocked.

2. **Fast preview and picture switches sit in an ellipsis menu** instead of Android's menu page (WP-7).

3. **Picture choice**: a dialog "Photos / Files" instead of Android's single picker.

4. **Controls are small when the picture is full size.** Android folds the picture; the iPhone editor keeps it at half the screen height (WP-1 layout). Not changed here.

5. **Emblem, Text, Draw tabs are dimmed placeholders.**

6. "Old fade" stays visible for the rest of the session once shown (both tabs stay alive); Android hides it again after leaving the tab.

### What the review found

An Opus review checked the diff against Android and the preview pipeline. No stale-render or main-thread problem was found. Fixed:

1. Holding the picture ended immediately (the press callback reports release when the hold counts); now a drag gesture tracks press and release. Checked in the simulator: held picture loses the vignette and bloom.

2. The effects die and Reset were unreachable for VoiceOver inside the switch label; the label now toggles on its own and the buttons stand beside the switch.

3. Switch rows toggled only on the switch; now the whole row.

4. "From picture" could apply its colours to a layer that was no longer selected; the task is cancelled when the palette group goes away.

5. Non-square photos were softer than on Android; the thumbnail size now keeps both sides at the requested size.

6. Group states reset when another layer was selected; now only the palette editor is per layer.

7. Smaller: dimmed unbuilt tabs, group header read-out with summary, stop handles as buttons, plain preview retried after a failed render, palette file written off the main thread, readable picture error, byte-wise picture copy in Kotlin replaced by one copy. Not done: the "Old fade" reset (difference 6 above) and a multi-entry cache in the picture loader.

### What to try on the iPhone

The debug build is installed on the iPhone 15 but was not started: the phone was locked. Open "Dahlia Debug" yourself.

1. Layers tab: tap the style group, pick a style (small pictures), tap "Add", move the new layer with the arrows, hide it with the eye, delete it.

2. Open Palette, tap the plus under "My palettes", drag a colour dot and change hue; Cancel puts the old palette back, Save keeps it; Delete in the editor removes it.

3. Scene tab: switch effects on, drag Vignette; press and hold the picture, it shows without effects until you lift your finger.

4. Scene tab, Background switch, then Picture: pick a photo from Photos or Files; the picture appears behind the mandala.

### Selbst entschieden

- Five tabs with three dimmed, unopenable ones instead of two tabs (keeps the final layout).
- Fast preview and picture switches in an ellipsis menu until WP-7.
- Pro not owned yet, so marks show without unlocking (no debug switch yet).
- Photos or Files chosen by a dialog.
- Style pictures copied from Android's files by a script instead of rendered on the phone.
- Hold time 0.4 s as Android's long press.
- Simulators used: iPhone 17e (smallest, 390 pt) and iPhone 18 Pro Max (largest). Screenshots: light, dark, English, German, Russian, in `docs/qa/shots/ios-wp5-2026-11-12/` of the main checkout. Renders are drawn by the same core as Android's; no separate image comparison with the Android emulator was made beyond the parity test in `check-full`.
