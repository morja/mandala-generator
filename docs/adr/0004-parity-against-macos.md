# ADR-0004 · Parity tests against macOS reference images

Status: proposed, 19 October 2026

## Decision

`tools/reference-renderer/render-references.sh` compiles the macOS renderer from `../Mandala-Generator` into a command-line tool and renders `fixtures/reference/*.png` from `fixtures/settings/*.json`. `ParityTest` (full check) renders the same files in Kotlin and requires a minimum PSNR per fixture. Default threshold: 45 dB.

## Known deviations

| Area | Cause | Effect |
|---|---|---|
| Add blending above 1 | Core Image's handling of over-range values when converting its P3 working space back to the output space is not reproduced exactly. | `wave-two-layers`: about 36 dB, Kotlin slightly darker where Add overflows. Not visible side by side. |
| Overexposed dense areas | Same over-range handling as above, where many lines pile up (Hyperboloid centre). | `hyperboloid-tilted`: about 42 dB, slightly brighter in the white centre. Geometry identical. |
| Painted effect (`abstractLevel`) | CITurbulence and CIRandomGenerator are Apple's own; Kotlin uses its own value noise and hash grain. | Same character, different displacement pattern: 21–27 dB. Matters in practice: the macOS app gives every new layer 0.3, so most Mac favourites are affected (a 3-layer example: 22 dB with the effect, 45 dB without). Candidate for a closer reproduction. |
| Layer buffer size | macOS always enlarges each layer buffer to the full radius. Kotlin does so only for rotated layers and otherwise adds a margin as wide as the effects reach (ADR-0002). | Light from lines far outside the frame (beyond three sigma of the stronger glow passes) is missing at the outermost pixels. Not measurable in the fixtures. |
| Lanczos and blur kernels | Own implementations (Gaussian ≤ σ 6, three box passes above). | ≤ 1/255 on average. |

## Consequences

A new fixture is added with every ported style or filter. Lowering a threshold needs a row in the table above.
