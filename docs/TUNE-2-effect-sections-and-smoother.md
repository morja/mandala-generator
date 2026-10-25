# TUNE-2 · Effect sections and Smoother

25 October 2026, requested by Mathis from Android screenshots.

## Sections

With Light open, Tone, Texture, Sparkle and More looked like controls belonging to Bloom. The nested groups shared one background and had no visible boundary at the end of Light.

Each Android effect section now has its own outlined container, with the same left alignment in open and closed states. The chevron sits at the right of the heading. An open section has a divider above its controls, and the next section starts outside its border. Headings keep a 48 dp minimum tap height, using existing tokens. Mac section labels also get a divider. No packages or design tokens were added.

## Smoother

Texture contains Clarity, Grain and Smoother on both platforms. Smoother is a Gaussian blur from 0 to 1, with radius `12 × strength × shorter image side / 1600` in rendering pixels. Edge pixels repeat outside the image, so softening a solid colour does not darken its edges. It runs after Clarity and before Grain, keeping deliberate grain visible. It softens the same scene as the other effects, before the separate Stars and Glitter overlays.

The JSON field is `effectsLayer.smoother`, default 0 in both models. Old settings keep their appearance; reset returns it to 0. The Mac render fingerprint includes the new value. Android computes the blur in overlapping strips, including the extra rows read by Clarity, Smoother and Relief.

## Validation

- Mac `swift build` passes.
- Android `scripts/check` passes, including architecture rules and app unit tests.
- New tests cover JSON round trips, disabled smoothing, fine detail across strip boundaries, and constant colours at the image edges.
- All nine memory-budget tests pass under a 256 MB heap with Smoother at 1 in the existing heavy-effects setup, including the large portrait and landscape exports.
- All 93 Mac parity fixtures pass. New references rendered from the Mac: `fx-smoother` measures 52.09 dB; `fx-smoother-combined`, with Bloom, Clarity and Relief, measures 51.47 dB. Both keep the default 45 dB threshold.
- Debug and Fast APKs build. The Fast app is installed on Mathis' phone for review.

The phone layout was visually checked with Light open and the other groups closed, then with Texture open, in dark and light themes. The two-pane tablet layout was also checked in both themes without clipped labels or horizontal scrolling. Screenshots: [Light](images/tune-2/phone-light-section-dark.png), [Texture, dark](images/tune-2/phone-texture-dark.png), [Texture, light](images/tune-2/phone-texture-light.png), [Tablet, light](images/tune-2/tablet-light.png), [Tablet, dark](images/tune-2/tablet-dark.png).
