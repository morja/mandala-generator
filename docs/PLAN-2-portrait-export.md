# PLAN-2 · Portrait export with framing by hand

20 October 2026. Status: approved by Mathis on 20 October 2026, all questions as recommended. Decision draft: [ADR-0010](adr/0010-export-formats-and-framing.md). Nothing is built yet.

Decided by Mathis: portrait is free, Android only, the format is chosen when saving, and the user places the mandala in the frame by dragging and pinching (a section when zoomed in, anywhere on the background when zoomed out).

## Memory measurement (done)

The renderer only draws squares today, so portrait sizes were measured as squares with the same number of pixels: the worst case of `MemoryBudgetTest` (five heavy layers, emblem, all heavy effects), no supersampling, 4 threads, 256 MB heap, on `main` of 20 October 2026. With framing every layer buffer is limited to the canvas plus margin, so a square of the same area is a fair stand-in for a zoomed-in portrait.

| Stands in for | Square | Megapixels | Result |
|---|---|---|---|
| today's large export, 2048 × 2048 | 2048 | 4.2 | renders |
| | 2200 | 4.8 | renders |
| | 2350 | 5.5 | renders |
| | 2500 | 6.3 | renders |
| 2048 × 3641 (9:16) | 2731 | 7.5 | out of memory |
| 2048 × 4437 (9:19.5, most phones) | 3014 | 9.1 | out of memory |
| 2048 × 4779 (9:21) | 3128 | 9.8 | out of memory |

The limit is about 6.5 megapixels. Today's 2048 export already uses about 220 of the 256 MB (peak readings by sampling are rough; pass or fail is the reliable figure). This is roughly 50 bytes per pixel at the peak, against 3 bytes for the finished `Image8`.

Consequences:

- **Pro at 2048 width does not fit in portrait** with today's pipeline.
- **The free export does not fit either as it is built today**: 1024 wide, drawn at twice the size and scaled down, is internally 2048 × 4437. The free portrait export therefore draws at a smaller factor (about 1.36 for 9:19.5, so the inside stays at about 4.2 megapixels like today's large export) and scales down with an area filter. That is slightly softer than the free square, still clean.
- Without further work, **the largest Pro portrait that keeps today's safety margin is about 1440 × 3120** (4.5 megapixels, no supersampling). That covers every phone screen up to 1440 px wide, which is practically all of them.

Ways to 2048 wide (and to 4096 square, open since ADR-0007):

- **Lower the memory per pixel.** First profile where the 50 bytes go (float buffers of the graffiti layer, `PixelBuffer`, blur and bloom intermediates). Possibly enough for 9 megapixels without changing how the picture is drawn.
- **Banded rendering.** Draw the canvas in horizontal strips with an overlap as wide as the widest effect (glow, bloom). Unlimited size, but every effect that reads its neighbours must be checked for seams; the bigger change.

## Steps

1. **Core: canvas and framing.** `RenderOptions` gets width, height, centre and zoom (ADR-0010); backgrounds take width and height; layer buffers are limited to the canvas plus margin; size-dependent values follow the mandala for layers and the canvas for background and effects; supersampling with any factor and an area filter for scaling down. Tests: every fixture in `android/fixtures/settings/` renders byte-identical through the old square path and through square with the default framing; `MemoryBudgetTest` gets one case per format at its largest size, zoomed in as far as the app allows; `ParityTest` unchanged.
2. **CLI:** width, height, centre and zoom as options, to check results on the desktop and for the docs.
3. **App: framing step.** "Save" opens it: format choice (Square, This phone's screen, 4:5), the frame with the picture, drag, pinch, double tap to reset, then "Save" and "Save large · Pro". While a finger moves, the last preview is moved and scaled on screen; when it lifts, the preview is drawn again for the new framing. The last format and framing are kept in shared preferences. Design per `design-baseline` and ADR-0006, only existing tokens (new ones need approval); phone portrait and tablet, light and dark, no horizontal scrolling.
4. **"Set as wallpaper"** from the framing step through Android's `WallpaperManager` (home screen, lock screen or both). Needs the permission `SET_WALLPAPER`, which Android grants without asking; no new package.
5. **Pro and docs:** labels with the real size per format, Pro sheet text, ADR-0007 "How it is built" updated, ADR-0010 accepted.

Steps 1 and 2 belong together; step 3 needs step 1. Banded rendering or the memory work, if wanted, comes after step 5 and only raises the Pro sizes.

## Decisions (20 October 2026, Mathis: "everything as recommended")

1. **Pro size in portrait:** about 1440 wide now (rule in ADR-0010, point 9), not waiting for 2048.
2. **Memory work later:** profile and lower the memory per pixel first; banded rendering only if that is not enough.
3. **Text layer:** stays with the canvas.
4. **"Set as wallpaper":** yes, step 4 is part of the work.
5. **Zoom range:** a quarter to four times the width, the mandala's centre inside the frame.
