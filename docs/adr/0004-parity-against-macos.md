# ADR-0004 · Parity tests against macOS reference images

Status: proposed, 19 October 2026

## Decision

`tools/reference-renderer/render-references.sh` compiles the macOS renderer from `../Mandala-Generator` into a command-line tool and renders `fixtures/reference/*.png` from `fixtures/settings/*.json`. `ParityTest` (full check) renders the same files in Kotlin and requires a minimum PSNR per fixture. Default threshold: 45 dB.

## Known deviations

| Area | Cause | Effect |
|---|---|---|
| Add blending above 1 | Core Image's handling of over-range values when converting its P3 working space back to the output space is not reproduced exactly. | `wave-two-layers`: about 36 dB, Kotlin slightly darker where Add overflows. Not visible side by side. |
| Overexposed dense areas | Same over-range handling as above, where many lines pile up (Hyperboloid centre). | `hyperboloid-tilted`: about 42 dB, slightly brighter in the white centre. Geometry identical. |
| Painted effect (`abstractLevel`) | The macOS code asks for a CITurbulence displacement first, but Core Image has no filter of that name, so that step never runs there; the port skips it too (found 19 October 2026, before that the port displaced with its own noise and reached 21–27 dB). Above 0.3 the grain uses CIRandomGenerator, whose pattern is not reproduced (see APPROVALS.md). | `painted-default`: about 46 dB. `painted-heavy`: about 28 dB, from the grain pattern only. Mac favourites without grain effects: 43–55 dB. |
| Layer buffer size | macOS always enlarges each layer buffer to the full radius. Kotlin does so only for rotated layers and otherwise adds a margin as wide as the effects reach (ADR-0002). | Light from lines far outside the frame (beyond three sigma of the stronger glow passes) is missing at the outermost pixels. Not measurable in the fixtures. |
| Effects: grain | CIRandomGenerator is Apple's own; amount and offset match. | `fx-grain`: about 36 dB, same look. |
| Effects: chromatic | Core Image's two additions leave alpha 3, which its final colour conversion takes into account. Not reproduced (no favourite uses chromatic). | `fx-chromatic`: about 43 dB, slightly darker. |
| Effects: relief border | The emboss alpha at the outermost pixel row. | `fx-relief`: 38 dB overall, 54 dB inside. |
| Strange Attractor | Chaotic iteration amplifies the last-bit differences between Apple's and Java's sin/cos. | `style-strange-attractor`: about 19 dB per pixel, same shape and density. |
| Constellation Web | A few random keep-or-skip decisions sit on a rounding edge. | `style-constellation-web`: about 37 dB, a handful of lines differ. |
| Deep Nebula | Very large thin ellipses (three times the layer radius) magnify last-bit differences. | `style-deep-nebula`: about 35 dB, same look. |
| Graffiti layer | Core Graphics strokes each spray path as one shape, but at some joins of long hand-drawn paths it paints an extra brush-sized disc on top. Which joins get one depends on the whole path (bisecting a real stroke moves or removes them), so it is not reproduced. | `hand-graffiti`: about 29 dB, `hand-both` about 33 dB; the Mac shows a few brighter round spots inside translucent strokes. Single strokes, crossings and blending match (probes within 1/255). No Mac favourite has graffiti switched on. |
| Auto background fibres | The macOS app splits the fibres over as many random streams as the Mac has cores (`activeProcessorCount`), so the same file gives other fibres on another Mac. The port uses 12, the count of the Mac the favourites come from. | `background-auto`: about 47 dB on that Mac. |
| Text layer | Glyphs come from the platform: Java2D in the command (same macOS fonts, other rasteriser and kerning), Android system families in the app. Layout, cloud, shadow, glow and blending follow the macOS code. | Not in the parity fixtures. Mac favourites with text: 17–21 dB overall, same line breaks and placement, visually near-identical with Java2D. |
| Lanczos and blur kernels | Own implementations (Gaussian ≤ σ 6, three box passes above). | ≤ 1/255 on average. |

## macOS behaviour reproduced on purpose

These look like accidents in the macOS app but define how its images look, so the port keeps them (measured with Core Image, see `EffectsLayer.kt` and `MeasuredCurves.kt`):

- **Relief turns the image light grey.** CIConvolution3X3 adds its bias of 0.5 to alpha too, so the emboss layer is half transparent and lifts everything towards white. No Mac favourite uses relief.
- **Clarity darkens.** Its high pass is the sum of two opaque images (alpha 2); the colour matrix then scales half the value and soft light blends with alpha 2.
- **Effects brightness is added after contrast** (CIColorControls order), not before.
- **CIVignette and CISepiaTone** have no documented formula; both are tabulated from measurements.
- **Symbols** picks its type with `% 20`, so the last three symbols of the macOS library (Eye of Horus, Star of Ishtar, Caduceus) never appear; they are not ported.

## Consequences

A new fixture is added with every ported style or filter. Lowering a threshold needs a row in the table above.
