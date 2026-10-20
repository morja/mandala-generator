# ADR-0010 · Export formats and framing: portrait images with the mandala placed by hand

Status: accepted, 20 October 2026 (Mathis' decisions; points 1 to 3 first, the rest as recommended in [PLAN-2](../PLAN-2-portrait-export.md)).

## Decision

1. **Portrait is free.** Every format can be saved at the free size; Pro (ADR-0007) adds higher resolution, not formats.
2. **Android only.** The macOS app does not get formats or framing. This is an exception to "Mac first" (`AGENTS.md`, ADR-0008): it is an export option of the phone app, not a motif.
3. **The format is chosen when saving.** It is not part of the mandala: not in the settings JSON, not in favourites or history (ADR-0003 is untouched). The app remembers the last format and framing as a convenience.
4. **Framing by hand.** After picking a format the user places the mandala in the frame: drag to move, pinch to zoom, double tap to reset. Zoomed in, only a section of the mandala is saved; zoomed out, it sits anywhere on the background.
5. **The canvas is extended, never cropped from a square.** The renderer draws a canvas of any width and height with the mandala at a given centre and size. Zooming in renders real detail at the output resolution, not enlarged pixels.
6. **What moves with the mandala, what stays with the canvas.** The mandala layers (and the emblem) move and scale with the framing, including everything that depends on their size: line widths, glow, wash, painted effect, brightness scale. Background and the effects layer (grain, stars, glitter, vignette, bloom, local contrast, relief) belong to the canvas.
7. **Text layer stays with the canvas.** A quote is placed in canvas coordinates, so it can sit in the free space below the mandala; it does not move or scale with the framing.
8. **Zoom range** from a quarter to four times "fits the width"; the mandala's centre always stays inside the frame.
9. **Sizes.** Free: 1024 px wide in every format, drawn at up to twice the size so the inside stays at about 4.2 megapixels (today's large export), then scaled down with an area filter. Pro: the largest size with at most 2048 px width and about 4.5 megapixels, without supersampling: 2048 × 2048 square, about 1900 × 2370 for 4:5, about 1440 × 3120 for a 9:19.5 phone screen.
10. **"Set as wallpaper"** in the framing step, through Android's `WallpaperManager` (home screen, lock screen or both), at the size of the chosen export.
11. **Square with the default framing is today's image.** Format square, mandala centred at the width of the canvas, must render pixel-identical to today, so `ParityTest` and its thresholds stay valid.

Formats offered: **Square**, **This phone's screen** (aspect ratio from the display, the format for wallpapers) and **4:5**. More can follow without a new decision.

## How it is built

- `RenderOptions` gets a canvas (`width`, `height`) and a framing (`centreX`, `centreY` as fractions of the canvas, `zoom` relative to "fits the width"). `outputSize` stays as the square shortcut. Every layer buffer covers at most the canvas plus its effect margin, also when zoomed in; a mandala larger than the canvas is never drawn in full and cropped afterwards.
- Background types that today take one `size` get width and height. Radial ones (radial gradient, sunburst, auto) stay round and centred on the canvas; their radius is measured on the shorter side, so a square stays as it is.
- Memory and sizes: see PLAN-2. The budget rule (ADR-0002, `MemoryBudgetTest`) applies to every format at every size the app offers; `MemoryBudgetTest` gets one case per format at its largest size, zoomed in as far as the app allows.

## Reasons

- A section cut from the square loses resolution (1152 × 2048 out of 2048 × 2048 for 9:16) and cuts the mandala at the sides. That is the opposite of what a wallpaper needs.
- Placing by hand covers both wishes with one gesture: a detail of the mandala filling the screen, or the whole mandala low on the lock screen below the clock, with the background continuing.
- Portrait for free: wallpapers are the main use on a phone, and shared wallpapers advertise the app (same reasoning as ADR-0007). Pro stays attractive through sharpness on high-resolution screens and for prints.
- Keeping the format out of the settings JSON keeps compatibility with the macOS app simple: a favourite looks the same on both, and the format is a decision about one saved image.

## Later

- Larger Pro sizes (2048 wide in portrait, 4096 square): first profile and lower the memory per pixel; banded rendering only if that is not enough (PLAN-2).
