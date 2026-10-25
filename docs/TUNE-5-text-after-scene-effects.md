# TUNE-5 · Clear text above Scene effects

25 October 2026, requested and approved by Mathis. Decision: [ADR-0017](adr/0017-text-after-scene-effects.md).

The Mac and Kotlin renderers now complete Scene effects before compositing text. Background, mandala, drawing and emblem still receive the same effects. Text is the final layer and keeps its own colour, cloud, shadow, glow, deliberate blur, opacity and blending. Final downscaling, export finish and text framing are unchanged. No new controls or JSON fields were added.

## Regression checks

Two new tests in `TextLayerTest` failed with the previous render order and pass with the new one:

- Text on black is pixel-identical with Scene Smoother enabled and disabled, with and without supersampling.
- Text with its own glow, cloud, shadow and blur matches compositing it onto an already finished scene containing Smoother, sepia, grain, relief, chromatic, bloom and stars. Render progress still rises to 1.

The existing text layout, author and missing-renderer tests also pass.

The Mac reference renderer confirms:

- Without Scene effects, text-only output is pixel-identical before and after the change.
- With the new order, enabling Smoother on the black scene leaves every text pixel unchanged. Before this change the difference measured 22.22 dB PSNR; afterwards it is zero.

These comparisons use each platform's own text rendering. Cross-platform glyph differences remain the documented limitation from ADR-0004; existing non-text parity references are retained.

## Visual check

The same settings, Smoother at 1, before and after the order change:

![Text processed by Smoother](images/tune-5/text-before-effects-change.png)

![Clear text over the softened scene](images/tune-5/text-after-effects-change.png)

Input: [text-over-smoother.json](images/tune-5/text-over-smoother.json). From the repository root, after building the reference renderer:

```sh
android/build/reference-renderer/mandala-ref docs/images/tune-5/text-over-smoother.json /tmp/text-after-effects.png
```

## Complete checks

- Mac `swift build` passes.
- All 58 core and 53 app unit tests pass, including the two new regressions and existing text-framing tests.
- All nine 256 MB memory-budget tests pass.
- All 93 Mac parity fixtures pass with their existing references and thresholds.
- The Android command also renders text on black pixel-identically with Smoother on or off, using its real font renderer. Its full scene example renders with a 256 MB heap limit.
- The Fast APK builds successfully.

[Android rendering of the same example](images/tune-5/android-text-after-effects-change.png).
