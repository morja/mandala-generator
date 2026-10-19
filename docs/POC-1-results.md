# POC-1 · Renderer port to Kotlin: results

19 October 2026. Question: can the macOS renderer run in plain Kotlin with the same look, and is it fast and small enough for a phone?

## What was built

- `core`: settings model and JSON (compatible with the Mac files), the xorshift RNG (bit-identical), the 18 palettes, the additive pixel buffer with Wu lines and tone map, five styles (Spirograph, Rose Curves, Hypocycloid, Phyllotaxis, Wave Interference), the per-layer chain (glow, wash, painted effect, colour grade, rotation, opacity, blend modes), the solid background and the final Lanczos downscale. Core Image steps are rebuilt on the CPU, including its Display P3 working space and 8-bit hand-over between steps.
- `cli`: `mandala render <settings.json> <out.png>` and `mandala compare`.
- `tools/reference-renderer`: builds the macOS renderer as a command-line tool, for reference images.

Not ported yet: the other 33 styles, background types other than solid colour, the effects layer, text, graffiti and drawing layers. The renderer reports these as warnings.

## Look: matches

![Mac (left) and Kotlin (right) for two fixtures](images/poc-1-comparison.jpg)

| Fixture | PSNR | Mean error |
|---|---|---|
| spirograph-basic | 58.5 dB | 0.07 /255 |
| phyllotaxis-solid-background | 57.7 dB | 0.10 /255 |
| rose-ripple | 56.1 dB | 0.10 /255 |
| hypocycloid-rotated-wash | 47.3 dB | 0.33 /255 |
| wave-two-layers (Add blend) | 36.4 dB | 2.0 /255 |
| painted-default (abstract 0.3) | 27.2 dB | 2.9 /255 |
| painted-heavy (abstract 0.8) | 21.6 dB | 13.3 /255 |

Above about 40 dB the difference is invisible. The two lower groups have known causes (ADR-0004): Core Image's over-range handling with Add, and Apple's own turbulence noise. Side by side, the painted images have the same character.

## Speed: comparable to the Mac, often faster

MacBook (12 cores), JVM 21, single layer unless noted. "Kotlin, first port" is the 1:1 port; "after memory pass" is the current state (see below).

| Fixture | Size | macOS (Swift + Core Image) | Kotlin, first port (4 threads) | Kotlin after memory pass (4 threads) |
|---|---|---|---|---|
| spirograph-basic | 1024 | 1.0 s | 1.1 s | 0.7 s |
| spirograph-basic | 2048 | 2.4 s | 2.9 s | 1.4 s |
| wave-two-layers (2 layers) | 1024 | 2.8 s | 1.9 s | 1.3 s |
| wave-two-layers (2 layers) | 2048 | 6.7 s | 5.3 s | 2.5 s |
| painted-heavy | 1024 | 0.8 s | 1.3 s | 0.9 s |
| painted-heavy | 2048 | 2.0 s | 4.5 s | 2.2 s |

Phone CPUs are roughly 2–4× slower than this Mac. A 512 px preview of a typical layer should take well under 2 s on a phone; with a debounce and a progress indicator that is fine.

## Memory: solved for 1024 px

The first, 1:1 port needed far more memory than an Android app gets (typically 256–512 MB with `largeHeap`): a 512 px preview of two layers needed about 490 MB, 1024 px did not fit in 512 MB. The causes came from the macOS design: rendering at twice the output size, each layer buffer enlarged to the full radius even without rotation, one full float buffer per drawing thread, and new full float copies in every filter step.

Memory pass (same day), without changing the look:

1. **8-bit intermediates.** Images between two former Core Image steps are stored as bytes (`Image8`, 3 bytes per pixel instead of 12). Exact, since macOS quantises there too.
2. **Layer buffer enlarged only when rotated.** Other layers get a margin as wide as glow, wash and painting reach, so light from just outside the frame still bleeds in. Size-dependent effects keep using the enlarged width, so their scale does not change.
3. **No buffer per thread.** Threads draw into horizontal bands of one shared buffer. The result no longer depends on the thread count (it is bit-identical for 1 or 4 threads).
4. **Filters in place and streaming.** Blurs run in place, resampling streams row by row, noise is sampled on demand, combined effects write back into their input.

Scene of the memory test (two layers, full-size wave with glow and painted effect, spirograph with wash and heavy painted effect, Add blend):

| Output size | Heap needed now | Before |
|---|---|---|
| 512 px (preview) | ≤ 128 MB | about 490 MB |
| 1024 px | 128–192 MB | more than 512 MB |
| 2048 px | 640–768 MB | more than 3 GB with 12 threads |

`MemoryBudgetTest` renders this scene at 1024 px under a hard 256 MB heap limit and runs in the fast check. The parity results are unchanged (table above).

## Conclusion and next step

The port is feasible: the look matches, speed and memory fit a phone up to 1024 px. 2048 px exports still need about 700 MB. The remaining large items there are the float pixel buffer (12 bytes per pixel at twice the output size) and the two float images of the painted effect. Options: process the painted effect in bands, or move the pixel buffer into native memory (NDK). This can wait until the app runs; a 1024 px export may be enough for phone wallpapers at first.

Next: the Android app shell (Compose canvas with preview, layer list, export to the gallery), then the remaining styles.

## Found in the macOS app

`executeTasksParallel` in `MandalaRenderer.swift` splits the curves into `count / cores` chunks and never draws the remainder. Depending on the Mac, up to `cores − 1` curves per layer are missing, so the same settings can give slightly different images on different Macs. Fix: let the last chunk run to `tasks.count`.
