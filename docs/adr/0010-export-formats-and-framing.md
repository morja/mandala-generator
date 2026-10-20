# ADR-0010 · Export formats and framing: portrait images with the mandala placed by hand

Status: accepted, 20 October 2026 (Mathis' decisions; points 1 to 3 first, the rest as recommended in [PLAN-2](../PLAN-2-portrait-export.md); framing as a Pro feature, strips and 2048 wide portrait decided after [SPIKE-1](../SPIKE-1-banded-rendering.md)).

## Decision

1. **Portrait is free.** Every format can be saved at the free size, with the mandala centred. Pro (ADR-0007) adds higher resolution and framing by hand (point 4), not formats.
2. **Android only.** The macOS app does not get formats or framing. This is an exception to "Mac first" (`AGENTS.md`, ADR-0008): it is an export option of the phone app, not a motif.
3. **The format is chosen when saving.** It is not part of the mandala: not in the settings JSON, not in favourites or history (ADR-0003 is untouched). The app remembers the last format and framing as a convenience.
4. **Framing by hand, a Pro feature.** After picking a format the user places the mandala in the frame: drag to move, pinch to zoom, double tap to reset. Zoomed in, only a section of the mandala is saved; zoomed out, it sits anywhere on the background. Without Pro the default framing is used (mandala centred, fitting the width); the framing controls are visible with a Pro mark and open the Pro sheet, like "Save large" today.
5. **The canvas is extended, never cropped from a square.** The renderer draws a canvas of any width and height with the mandala at a given centre and size. Zooming in renders real detail at the output resolution, not enlarged pixels.
6. **What moves with the mandala, what stays with the canvas.** The mandala layers (and the emblem) move and scale with the framing, including everything that depends on their size: line widths, glow, wash, painted effect, brightness scale. Background and the effects layer (grain, stars, glitter, vignette, bloom, local contrast, relief) belong to the canvas.
7. **Text layer: a choice in the framing step.** "Text stays in place" (default): the quote is placed in canvas coordinates, so it can sit in the free space below the mandala. "Text moves with the mandala": it moves and scales with the framing. Like the format, the choice belongs to the saved image, not to the settings.
8. **Zoom range** (Pro) from a quarter to four times "fits the width"; the mandala's centre always stays inside the frame.
9. **Sizes.** Free: 1024 px wide in every format, drawn at twice the size and scaled down, as today. Pro: 2048 px wide in every format, without supersampling, so it is the free export's internal image, as today for the square (ADR-0007): 2048 × 2048, 2048 × 2560 for 4:5, about 2048 × 4437 for a 9:19.5 phone screen.
10. **"Set as wallpaper"** in the framing step, through Android's `WallpaperManager` (home screen, lock screen or both), at the size of the chosen export.
11. **Square with the default framing is today's image.** Format square, mandala centred at the width of the canvas, must render pixel-identical to today, so `ParityTest` and its thresholds stay valid.
12. **Every step that works on a full-size image does it in strips** and keeps only the 8-bit result ([SPIKE-1](../SPIKE-1-banded-rendering.md)): layer lines, Strange Attractor points, graffiti, drawing layer, effects layer, stars and glitter; glow and wash write into the layer image instead of a copy. The image stays byte-identical and the render time the same; the one exception is the graffiti blur, whose running sums start at the strip and may differ by one step of 255 in single pixels. This is what makes 2048 × 4437 fit into 256 MB (ADR-0002); without it neither the free nor the Pro portrait export fits.

Formats offered: **Square**, **This phone's screen** (aspect ratio from the display, the format for wallpapers) and **4:5**. More can follow without a new decision.

## How it is built

- `RenderOptions` gets a canvas (`width`, `height`) and a framing (`centreX`, `centreY` as fractions of the canvas, `zoom` relative to "fits the width"). `outputSize` stays as the square shortcut. Every layer buffer covers at most the canvas plus its effect margin, also when zoomed in; a mandala larger than the canvas is never drawn in full and cropped afterwards.
- Background types that today take one `size` get width and height. Radial ones (radial gradient, sunburst, auto) stay round and centred on the canvas; their radius is measured on the shorter side, so a square stays as it is.
- Memory and sizes: see PLAN-2. The budget rule (ADR-0002, `MemoryBudgetTest`) applies to every format at every size the app offers; `MemoryBudgetTest` gets one case per format at its largest size, zoomed in as far as the app allows.

## Reasons

- A section cut from the square loses resolution (1152 × 2048 out of 2048 × 2048 for 9:16) and cuts the mandala at the sides. That is the opposite of what a wallpaper needs.
- Placing by hand covers both wishes with one gesture: a detail of the mandala filling the screen, or the whole mandala low on the lock screen below the clock, with the background continuing.
- Portrait for free: wallpapers are the main use on a phone, and shared wallpapers advertise the app (same reasoning as ADR-0007). Framing by hand and the sharper image are a stronger reason to buy Pro than resolution alone.
- Keeping the format out of the settings JSON keeps compatibility with the macOS app simple: a favourite looks the same on both, and the format is a decision about one saved image.

## Later

- 4096 square: not needed for now (Mathis, 20 October 2026). It would need strips through the whole pipeline, about 2 to 3 times the render time (SPIKE-1).
