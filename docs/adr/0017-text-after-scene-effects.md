# ADR-0017 · Text above the finished scene

Status: accepted, 25 October 2026 (Mathis).

## Decision

1. Render the background, mandala layers, graffiti, drawing and emblem first. Apply the complete Scene effects layer to that artwork, then composite the text last, before the final downscale.
2. Scene effects, including Smoother, grain, relief, colour changes, stars and glitter, do not process or cover the text. The text keeps its own colour, blend mode, opacity, cloud, shadow, glow and deliberate blur.
3. Keep text layout and framing rules from ADR-0010. Text may stay on the canvas or follow the mandala exactly as before. The export finish remains a final adjustment of the exported image.
4. Use the same order in macOS and Android, implemented on the Mac first. No JSON fields or defaults change, and there is no new inclusion switch or text-effect control in this change.

## Reason

Smoother made the letters unreadable when used strongly. Text usually needs to stay legible above a softened or textured scene. Any additional text effects or optional participation in Scene effects can be considered separately later, as Mathis requested.

## Consequences

Saved settings with text and active Scene effects intentionally render with clearer text when rendered again. Settings without text, or without Scene effects, keep their appearance. Existing text-specific effects still work.

Kotlin regression tests exercise the complete render pipeline, with and without supersampling, and verify compositing the text and its own effects onto the finished scene. Mac image comparisons verify that Smoother leaves text on black unchanged and that disabling Scene effects keeps the previous pixels. Non-text parity fixtures retain their references and thresholds from ADR-0004.

Implementation and before/after images: [TUNE-5](../TUNE-5-text-after-scene-effects.md).
