# SPIKE-1 · Rendering in strips: memory and time

20 October 2026. Question from Mathis (PLAN-2): is rendering in strips worth it, and what does it cost in time and memory? Measured on the Android renderer in `android/core` on a Mac (JVM, 4 threads). The prototype is on the local branch `spike/banded-rendering` and is not meant for merging.

## Short answer

- **Strips per step, yes.** Every step that works on a full-size image does it in strips and keeps only the 8-bit result. The picture stays **byte-identical** and the time stays **the same**. Memory then grows by only a few bytes per pixel. This is enough for **2048 × 4437 in portrait (Pro) within 256 MB**, with room to spare.
- **Strips through the whole pipeline, not now.** Then no full-size intermediate image exists at all. But glow, wash, bloom and the emblem's glow need the whole image (at a 1600 px reference size), so every layer would have to be drawn twice and the effects layer would need another pass. Estimated **2 to 3 times the render time** (not measured). That would only be worth it for 4096 square.

## How it was measured

The worst case of `MemoryBudgetTest`: five heavy layers, emblem, graffiti and drawing, all heavy effects. Rendered without supersampling. For each case the smallest Java heap at which the render still finishes was searched in 8 MB steps (`-Xmx`, minimum heap below). Times are the mean of three runs at 2 GB heap. Portrait sizes stand in as squares with the same number of pixels, as in PLAN-2: 3014² ≈ 2048 × 4437.

## Where the memory goes today

| Case (minimum heap) | 2048² | 3014² |
|---|---|---|
| Everything | 182 MB | 348 MB |
| Mandala layers only (no effects layer, emblem, hand layers) | 182 MB | 348 MB |
| One layer only | 182 MB | – |

The peak is in **drawing one large layer**. The lines are summed in a float buffer of 12 bytes per pixel. That buffer is larger than the image by the reach of glow, wash and painting (about 2588² at 2048, about 3800² at 3014), and while it is converted to 8-bit both are held together. That alone is around 100 MB at 2048 and around 240 MB at 3014.

Not the problem, because it is already solved:
- **Glow, wash, bloom** are computed at a 1600 px reference width. They need about 70 MB at any output size.
- **The painted effect** is already computed in strips with an overlap (`LayerEffects.painted`).

## Prototype: drawing layers in strips

The lines of a layer are collected once as before. Then they are drawn strip by strip into a small float buffer and each strip goes straight into the 8-bit image. Every pixel receives its lines in the same order as before, so the result is exactly the same.

| | 2048² | 3014² |
|---|---|---|
| Image | byte-identical (MD5, 1 and 4 threads) | byte-identical |
| Time, layers only | 4.2 s → 4.2 s | 7.1 s → 6.9 s |
| Time, everything | 6.0 s → 6.6 s (one run each) | 9.6 s → 9.5 s |
| Minimum heap, layers only | 182 → 122 MB | 348 → 182 MB |
| Minimum heap, everything | 182 → 170 MB | 348 → 253 MB |

The strip height (256, 512 or 1024 rows) makes no measurable difference in time or memory.

After this change the peak at 3014² moves:

| Case, layers in strips (minimum heap at 3014²) | |
|---|---|
| Everything | 253 MB |
| Without effects layer | 253 MB (peak in the hand layers) |
| Without hand layers | 235 MB (peak in effects layer or emblem) |
| Mandala layers only | 182 MB |
| Layers only, at 1024² / 2048² | 105 / 122 MB |

About 100 MB do not depend on size: the glow passes at reference size, the collected lines, the JVM. On top of that there are about 6 bytes per pixel of the layer area: the layer image and a second copy of it after glow.

## What else would need strips for 2048 × 4437

| Step | Today per pixel | In strips | Note |
|---|---|---|---|
| Layer lines | 12 B float + 3 B | done in the prototype | |
| Strange Attractor (also in Mixed) | draws points straight into the full buffer | collect points first, like lines | prototype skips these two styles |
| Glow and wash: combining with the layer | new 8-bit image (3 B) | write into the same image | the image is converted row by row in place anyway |
| Graffiti | 12 B colour + 4 B alpha | strokes clipped per strip | peak after the prototype |
| Drawing layer | 12 B float | like layer lines | |
| Effects layer (relief, local contrast, chromatic) | up to 3 float copies (36 B) | strips with overlap, like the painted effect | bloom stays at reference size |
| Stars and glitter | 12 B float | like layer lines | |
| Emblem and text masks | 1–8 B | strips or leave as is | small |

**Estimate for 2048 × 4437 with all of these:** about 150 to 180 MB, so it fits into 256 MB. The time stays the same as without strips. Render time grows with the number of pixels: on this Mac about 6 s for 2048² and about 10 s for 2048 × 4437 in the worst case. Phones are about 2 to 4 times slower (POC-1), so roughly **20 to 40 s for the heaviest Pro portrait**, and well under that for typical favourites.

**4096 square** (16.8 megapixels): even with strips in every step, one 8-bit layer image (about 5176², 80 MB), the composite (50 MB) and the fixed part (about 100 MB) add up to about 230 MB or more. That is too tight. 4096 would need strips through the whole pipeline (see above, 2 to 3 times the time) or a smaller margin around the layers.

## Recommendation

1. **Build strips per step** as part of the 2048 Pro portrait work, as its own step after PLAN-2 steps 1 to 5. Every change can be checked against a byte-identical image (MD5 of all fixtures before and after), so the look stays the same, as with the painted effect.
2. **`MemoryBudgetTest`** then gets the case 2048 × 4437 zoomed in, and the Pro size in portrait goes from about 1440 to 2048 wide.
3. **No strips through the whole pipeline**, unless 4096 square becomes a goal. Then decide again with this report.
