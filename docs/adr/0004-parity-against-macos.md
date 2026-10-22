# ADR-0004 · Parity tests against macOS reference images

Status: proposed, 19 October 2026

## Decision

`tools/reference-renderer/render-references.sh` compiles the macOS renderer from `../mac` (path changed with ADR-0009) into a command-line tool and renders `fixtures/reference/*.png` from `fixtures/settings/*.json`. `ParityTest` (full check) renders the same files in Kotlin and requires a minimum PSNR per fixture. Default threshold: 45 dB.

## Known deviations

| Area | Cause | Effect |
|---|---|---|
| Add blending above 1 | Core Image's handling of over-range values when converting its P3 working space back to the output space is not reproduced exactly. | `wave-two-layers`: about 36 dB, Kotlin slightly darker where Add overflows. Not visible side by side. |
| Overexposed dense areas | Same over-range handling as above, where many lines pile up (Hyperboloid centre). | `hyperboloid-tilted`: about 42 dB, slightly brighter in the white centre. Geometry identical. |
| Painted effect (`abstractLevel`) | The macOS code asks for a CITurbulence displacement first, but Core Image has no filter of that name, so that step never runs there; the port skips it too (found 19 October 2026, before that the port displaced with its own noise and reached 21–27 dB). Above 0.3 the grain uses CIRandomGenerator, whose pattern is not reproduced (see APPROVALS.md). | `painted-default`: about 46 dB. `painted-heavy`: about 28 dB, from the grain pattern only. Mac favourites without grain effects: 43–55 dB. |
| Layer buffer size | macOS always enlarges each layer buffer to the full radius. Kotlin does so only for rotated layers and otherwise adds a margin as wide as the effects reach (ADR-0002). | Light from lines far outside the frame (beyond three sigma of the stronger glow passes) is missing at the outermost pixels. Not measurable in the fixtures. |
| Effects: grain | CIRandomGenerator is Apple's own; amount, offset and grain size match (measured: mean change 5.97/255 on the Mac, 5.96/255 in Kotlin for `fx-grain`). | `fx-grain`: about 28 dB, two different noise patterns of the same strength. Until TUNE-1 the Mac added transparent noise, i.e. nothing, and the fixture measured Kotlin's grain against none (36 dB). |
| Effects: bloom near the border | Core Image's resampling and blur see transparent pixels outside the canvas, so its bloom fades a little more towards the outermost rows and columns. | `fx-bloom`: 48 dB before, about 43 dB since the bloom gain was doubled (TUNE-1); differences only within a few pixels of the border. |
| Effects: chromatic | Core Image's two additions leave alpha 3, which its final colour conversion takes into account. Not reproduced (no favourite uses chromatic). | `fx-chromatic`: about 43 dB, slightly darker. |
| Strange Attractor | Chaotic iteration amplifies the last-bit differences between Apple's and Java's sin/cos. | `style-strange-attractor`: about 19 dB per pixel, same shape and density. |
| Constellation Web | A few random keep-or-skip decisions sit on a rounding edge. | `style-constellation-web`: about 37 dB, a handful of lines differ. |
| Deep Nebula | Very large thin ellipses (three times the layer radius) magnify last-bit differences. | `style-deep-nebula`: about 35 dB, same look. |
| Graffiti layer | Core Graphics strokes each spray path as one shape, but at some joins of long hand-drawn paths it paints an extra brush-sized disc on top. Which joins get one depends on the whole path (bisecting a real stroke moves or removes them), so it is not reproduced. | `hand-graffiti`: about 29 dB, `hand-both` about 33 dB; the Mac shows a few brighter round spots inside translucent strokes. Single strokes, crossings and blending match (probes within 1/255). No Mac favourite has graffiti switched on. |
| Auto background fibres | The macOS app splits the fibres over as many random streams as the Mac has cores (`activeProcessorCount`), so the same file gives other fibres on another Mac. The port uses 12, the count of the Mac the favourites come from. | `background-auto`: about 47 dB on that Mac. |
| Text layer | Glyphs come from the platform: Java2D in the command (same macOS fonts, other rasteriser and kerning), Android system families in the app. Layout, cloud, shadow, glow and blending follow the macOS code. | Not in the parity fixtures. Mac favourites with text: 17–21 dB overall, same line breaks and placement, visually near-identical with Java2D. |
| Emblem layer | Core Graphics antialiases the mask with its own area coverage; the port uses the distance to each round-capped segment, disc and ring and 16 sub-rows per pixel for even-odd fills. The overlay is computed from the mask on the fly in P3 working values, so the 8-bit round trips of the macOS overlay (after colouring and after glow) are left out. | `emblem-*` (16 fixtures, every motif, rendering, frame and blend mode): 50–63 dB, default threshold. Same shapes (the tree uses the same random sequence); edge pixels differ by a few levels. |
| Lanczos and blur kernels | Own implementations (Gaussian ≤ σ 6, three box passes above). | ≤ 1/255 on average. |
| Fast preview (app only) | The app's preview blurs glow, wash and the other soft light at 800 px instead of the macOS 1600 px (`RenderOptions.glowReferenceWidth`), when "Fast preview" is on (the default; Mathis' decision of 21 October 2026, as a switch). Saved, shared and wallpaper images and all parity renders keep 1600 px. | Starter favourites at preview size: at least 52.8 dB against the full glow, mean difference 0.3/255; the preview takes about 40 % less time. |

## macOS behaviour reproduced on purpose

These look like accidents in the macOS app but define how its images look, so the port keeps them (measured with Core Image, see `EffectsLayer.kt` and `MeasuredCurves.kt`):

- **Clarity darkens.** Its high pass is the sum of two opaque images (alpha 2); the colour matrix then scales half the value and soft light blends with alpha 2.
- **Effects brightness is added after contrast** (CIColorControls order), not before.
- **CIVignette and CISepiaTone** have no documented formula; both are tabulated from measurements.
- **Symbols** picks its type with `% 20`, so the last three symbols of the macOS library (Eye of Horus, Star of Ishtar, Caduceus) never appear; they are not ported.

## Fixed in both renderers (TUNE-1, 22 October 2026)

Accidents that made a slider useless were fixed in the macOS renderer first and ported; the references were re-rendered (details in `docs/TUNE-1-palettes-and-sliders.md`, section 10).

- **Relief** no longer turns the image grey: the emboss convolves colour only (CIConvolutionRGB3X3, alpha stays 1) and repeats the edge pixels. `fx-relief`: about 53 dB, default threshold.
- **Chromatic** repeats the edge pixels where red and blue are shifted in from outside, so no stripe remains at the left and right edges. `fx-chromatic`: about 43 dB as before.
- **Vignette** uses a fixed CIVignette radius of 1 (the measured table's strongest column) and intensity 3.75 × the value instead of 2.5 ×. `fx-vignette` 58 dB, `fx-brightness-contrast` 56 dB.
- **Grain** works on the Mac: the noise had alpha 0, so Core Image added nothing there (only Kotlin showed grain). It now has alpha 1, the addition's alpha is reset to 1, the amplitude is twice the former (0.56 × the value, offset −0.28 ×), and above 1024 px the noise cells grow with the image (bilinear), so large exports keep the preview's grain. `fx-grain`: about 28 dB, threshold 26 (see the table).
- **Bloom** has twice the former gain (passes 1.1 and 0.6 × the value instead of 0.55 and 0.3), so it shows on dark images. `fx-bloom`: about 43 dB, threshold 42 (see the table).
- **Sunburst background** rounds its number of rays (`patternScale × 14 + 3`) to a whole number, so the rays meet where the angle wraps round instead of leaving a seam on the left horizontal axis. New fixture `background-sunburst-default-scale` (scale 0.3, 7 rays): about 59 dB.

## Consequences

A new fixture is added with every ported style or filter. Lowering a threshold needs a row in the table above.
