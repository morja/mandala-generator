# ADR-0005 · Android app shell: one screen, ViewModel, gallery export

Status: proposed, 19 October 2026

## Decision

- **minSdk 29 (Android 10), targetSdk 36.** From Android 10 on, saving to the shared gallery through MediaStore needs no storage permission.
- **One activity, one Compose screen** for now: canvas, "New variation", "Save to gallery", the layer list and the controls of the selected layer (style, palette, blend mode, opacity, symmetry, scale, complexity, density, ripple, glow, colour drift, painted, saturation, brightness).
- **Layers** follow the macOS app: at most 5; a new layer gets the next style, a palette three further on, scale 0.65; duplicates get a new seed; "New variation" re-seeds every layer. The list shows the top layer first (it is drawn last); each row can hide the layer and move it up or down; add, duplicate and delete act on the selected layer.
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
- No favourites, history, or loading Mac settings files yet. The selected layer is not restored (the first one is selected after a restart).
- The release build is signed with the local debug key; no store signing.
