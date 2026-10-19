# ADR-0005 · Android app shell: one screen, ViewModel, gallery export

Status: proposed, 19 October 2026

## Decision

- **minSdk 29 (Android 10), targetSdk 36.** From Android 10 on, saving to the shared gallery through MediaStore needs no storage permission.
- **One activity, one Compose screen** for now: canvas, "Surprise", "Variation", "Save", the layer list and the controls of the selected layer (style, palette, blend mode, opacity, symmetry, scale, complexity, density, ripple, glow, colour drift, painted, saturation, brightness).
- **Layers** follow the macOS app: at most 5; a new layer gets the next style, a palette three further on, scale 0.65; duplicates get a new seed; "New variation" re-seeds every layer. The list shows the top layer first (it is drawn last); each row can hide the layer and move it up or down; add, duplicate and delete act on the selected layer.
- **Favourites** (star in the top bar): a bottom sheet with thumbnails; tap applies (with "Undo"), long press deletes, "Add current" stores the current settings with a 128 px JPEG thumbnail. Stored in `favorites.json` in the macOS app's format (ADR-0003), so the Mac file can be imported as is.
- **Files** (menu in the top bar): "Open file…" reads a settings file or the Mac favourites file (`~/Library/Application Support/MandalaGenerator/favorites.json`; favourites with a known id are skipped); "Save settings…" writes a settings file the Mac app can open; "Share image" renders 1024 px and opens the share sheet (FileProvider, cache folder).
- **Background** section above the layers: off, auto (dark glow and fibres in the bottom layer's palette), colour, gradient (radial or linear with angle), sunburst, grain or picture, with the fitting sliders. "Picture" opens the system photo picker (no storage permission); the photo is copied into the app's files, at most 2048 px a side, and referenced by a `file:` URL like on the Mac. A Mac file whose picture is not on the phone falls back to colour 1 with a warning.
- **Effects** section: a switch for the effects layer and, folded away by default, its sliders grouped as light, tone, texture, spots and sparkle; "New pattern" re-seeds spots, highlights, stars and glitter.
- **Text** section: a switch for the text layer; "Random quote" and "Choose quote" draw from the macOS app's 150 quotes; the text is editable (the author follows a known quote); sliders for font, size, width, height on the image, glow, cloud, shadow and colour are folded away by default. The core lays out and composites the text like the macOS app; the platform draws the glyphs (`TextRenderer`: Android `Canvas` in the app, Java2D in the command). Android has no Zapfino, Didot, Papyrus and so on, so the macOS font names map to the closest system family (serif, sans, script, mono, casual).
- **Drawing** section, as on the Mac: two tools, "Lines" (the drawing layer: glowing lines) and "Spray" (the graffiti layer: soft paint), each with its own stroke count, symmetry (1–16 rotated copies), colour for new strokes and folded sliders. "Draw on image" turns drags on the canvas into strokes; the stroke in progress is shown with its copies and symmetry guides, and the finished stroke appears in the next preview. "Undo" removes the last stroke of the tool, "Clear" removes all (with "Undo" in the message). A stroke switches its layer on; layers from Mac files that are switched off stay off until then.
- **Dice, as on the Mac:** "Surprise" is "Randomize All" (one to three new layers with a shared symmetry, and a new background, same ranges as `AppState.swift`); "Variation" re-seeds; under the layer list "Randomize layer" and "New layers" ("Randomize Layers"). Effects, text and drawing stay as they are.
- **History:** back and forward arrows in the top bar, like the macOS app. Every state that rests for 700 ms is recorded, at most 50; a change after going back drops the states ahead. Kept in memory only (the Mac app also writes it to `history.json`).
- **Warnings** under the canvas name what the Android renderer cannot draw yet (for example a layer in an unported style, or the effects layer).
- **Last state** is saved 400 ms after each change to `last-settings.json` in the app's private files (the macOS settings format, ADR-0003) and restored on start. A broken or empty file is ignored.
- **Layering:** `Editing.kt` holds pure editing rules (unit-tested on the JVM); `EditorViewModel` holds the settings as `MandalaParameters`, renders the preview and exports; `core` renders. The screen only shows state and forwards input.
- **Preview** at 512 px, rendered 150 ms after the last change on a background thread. A newer change cancels a running preview (`RenderOptions.isCancelled`).
- **Export** at 1024 px as PNG to `Pictures/Mandala`, file names in the scheme of the macOS app (`mandala-<style>-p<palette>-x<symmetry>-<hash>.png`).
- **Layout:** on phones the canvas and buttons stay in view and only the controls scroll; from 600 dp width canvas and controls sit side by side.
- `android:largeHeap` is set as a safety margin; the renderer is designed to stay under 256 MB at 1024 px (ADR-0002).

## Measured (emulator, 4 cores, Apple silicon host)

| Build | Preview 512 px | Export 1024 px incl. saving |
|---|---|---|
| release | 1.1–1.2 s | about 3–4 s |
| debug | 2.8 s | — |
| release, 3 layers (2 visible) | 1.9–2.5 s | — |

Debug builds are much slower on Android; judge speed with `assembleRelease`.

## Open

- Tried on a Galaxy S23 (Android 16) on 19 October 2026: speed feels fine (user feedback); no timings measured on the phone yet.
- The history is lost when the app is closed. The selected layer is not restored (the first one is selected after a restart).
- The release build is signed with the local debug key; no store signing.
