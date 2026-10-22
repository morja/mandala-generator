# SPIKE-2 · SVG export

22 October 2026. Question from Mathis: could the app export SVG, as another mandala app does? Tried on a Mac with the Android renderer in `android/core` and the JVM command (`mandala svg`). The prototype is on the local branch `spike/svg-export`. Samples and a side-by-side page: [`images/spike-2/compare.html`](images/spike-2/compare.html).

## Short answer

- **Line drawings as SVG: yes, and cheaply.** Every style except Strange Attractor first collects curves and line segments; those are written as SVG paths. The geometry matches the PNG exactly. Writing takes under 0.2 s; files are 15 KB to 3 MB, often smaller than the PNG, and stay sharp at any size.
- **The look of the PNG: no.** The glow of the mandalas comes from pixel steps: thousands of hairlines adding up their light, a tone map, glow, wash, the painted effect, colour grading, backgrounds and the effects layer. SVG can only imitate some of it (line opacity added with `plus-lighter`, a Gaussian blur under each layer). The coloured SVG comes close in shape and colour but is flatter and less luminous, most visibly in dense centres.
- **Recommendation:** if at all, offer it as "Save as line drawing (SVG)", honestly named, with two looks: coloured lines on black and dark lines on white. Not as a second format of the same image.

## What was built

- `VectorExport.kt` in `core`: per layer the same seed, symmetry, radius and style calls as the renderer (`collectStyle`, now `internal`), then paths. Consecutive segments of one colour (32 steps per channel), opacity (64 steps) and width are joined into one path; coordinates have one decimal.
- Two looks. **Colour:** black or the solid background colour, lines in palette colours, opacity from the line weight, layers added with `mix-blend-mode: plus-lighter`, a blur copy under each layer for glow. **Lines:** white, one dark colour, layers multiplied; for plotters, cutters and colouring pages.
- Opacity needs about 4 times (colour) or 3 times (lines) the light the pixel renderer gives a single hairline, because SVG has no tone map and glow. Found by eye on the samples; these are the defaults of `mandala svg`.
- Layer rotation, offset and opacity are kept. Left out, and listed by the command: Strange Attractor points, glow (except the blur), wash, painted, colour grading, background types other than a solid colour, emblem, text, hand layers and effects layer.

## Measured (1024 units, nine settings files)

| Settings | Segments | SVG colour | SVG lines | PNG 1024 px |
|---|---|---|---|---|
| App icon (Spirograph mesh) | 30,672 | 847 KB | 810 KB | 2459 KB |
| Spirograph | 90,000 | 1153 KB | 1022 KB | 911 KB |
| Rose curves with ripple | 60,000 | 805 KB | 681 KB | 732 KB |
| Floral | 4,235 | 85 KB | 79 KB | 561 KB |
| Sacred geometry | 4,362 | 249 KB | 93 KB | 704 KB |
| String art | 663 | 49 KB | 15 KB | 890 KB |
| Mixed (three styles) | 40,215 | 2971 KB | 614 KB | 1126 KB |
| Starter favourite lotus with quote | 7,515 | 150 KB | 137 KB | 1581 KB |
| Strange Attractor | 0 | – | – | 2525 KB |

Mixed is large in colour because its colour changes along every short curve, which splits the paths. Five layers of the densest styles would give roughly 5 to 15 MB, still usable, but slow to open in some editors.

## What it would take

1. Emblem as paths: its geometry is already vector (`EmblemGeometry`); line and outline modes map directly, beads and filled need shapes.
2. Strange Attractor: either left out with a note, or points as tiny circles (large files).
3. Simplifying paths (fewer points on smooth curves) would cut size by perhaps half; worth it for plotters.
4. App: a save option next to the formats, the file through Android's document picker or share sheet. Free or Pro, and Android only or both apps, is a decision for an ADR (ADR-0007, ADR-0008). A line drawing fits Pro ("for prints").
5. Tests: SVG output is deterministic, so a fixture test can compare files or path counts.

Estimate for 1 to 4 on Android: about one to two days, mostly the emblem and the app's save flow.
