# ADR-0004 · Parity tests against macOS reference images

Status: proposed, 19 October 2026

## Decision

`tools/reference-renderer/render-references.sh` compiles the macOS renderer from `../Mandala-Generator` into a command-line tool and renders `fixtures/reference/*.png` from `fixtures/settings/*.json`. `ParityTest` (full check) renders the same files in Kotlin and requires a minimum PSNR per fixture. Default threshold: 45 dB.

## Known deviations

| Area | Cause | Effect |
|---|---|---|
| Curve chunking | The macOS renderer splits curves into equal chunks per CPU core and drops the remainder, so its output depends on the Mac's core count. Kotlin draws every curve; `RenderOptions.macCoreCountForChunking` reproduces the macOS behaviour for comparisons (references: 12 cores). | Without the option, Kotlin shows a few curves more than the Mac. |
| Add blending above 1 | Core Image's handling of over-range values when converting its P3 working space back to the output space is not reproduced exactly. | `wave-two-layers`: about 36 dB, Kotlin slightly darker where Add overflows. Not visible side by side. |
| Painted effect (`abstractLevel`) | CITurbulence and CIRandomGenerator are Apple's own; Kotlin uses its own value noise and hash grain. | Same character, different displacement pattern: 21–27 dB. |
| Lanczos and blur kernels | Own implementations (Gaussian ≤ σ 6, three box passes above). | ≤ 1/255 on average. |

## Consequences

A new fixture is added with every ported style or filter. Lowering a threshold needs a row in the table above.
