# ADR-0003 · Settings JSON compatible with the macOS app

Status: accepted, 6 November 2026 (Mathis, after REV-1; proposed 19 October 2026 and in production since Android 1.4)

## Decision

The Android app reads and writes the same settings JSON as the macOS app (`MandalaParameters`):

- same field names and defaults as the Swift `decodeSafe` calls;
- unknown enum values fall back to the default (style → `mixed`, blend → `screen`, background → `gradient`);
- unknown fields are kept and written back (since 6 November 2026, below); before that they were ignored;
- 64-bit seeds are unsigned (`ULong`); values above `Long.MAX_VALUE` occur in real files;
- layers not ported yet would be kept as raw JSON so a round trip loses nothing (text, graffiti and drawing were, until they were ported on 19 October 2026; all layers are typed now).

The favourites file uses the macOS `Favorite` format as well: a list of `{id, parameters, thumbnailData, name, createdAt}`, with the thumbnail as base64 JPEG and `createdAt` in seconds since 1 January 2001 (Swift's `Date` encoding).

## Reasons

Favourites and settings can move between Mac and phone, and the parity tests (ADR-0004) can use files saved by the Mac app.

## Consequences

Changes to the Swift model must be mirrored in `Model.kt`. Fields added after the port, with the default a missing or unknown value decodes to: `emblemLayer.glowMode` (`"shadow"`, the original glow; or `"light"`), added with TUNE-1 on 22 October 2026. TUNE-2 adds `effectsLayer.smoother` (25 October 2026), default and fallback `0.0`, for Gaussian softening. Custom palettes are not part of the file in the macOS app (`paletteIndex` refers to built-in palettes first). Android adds `customPalette` on layers and the emblem (ADR-0021, 26 October 2026), default and fallback none; the Mac ignores it and draws the built-in palette `paletteIndex` names. Android adds the glow colour of the text (DM-24 A2, Mathis 13 November 2026): `textLayer.glowSameAsText` (default and fallback `true`), `glowHue` (`0.0`), `glowSaturation` (`0.0`), `glowBrightness` (`1.0`). With `glowSameAsText` true the glow and its light passes take the text colour exactly as before (every fixture, bench case and starter favourite renders byte for byte the same, QA-12); false, they take the glow colour. The Mac does not know the fields yet: it keeps them as `textLayer` extras and writes them back, and draws the glow in the text colour.

Round trip without loss (6 November 2026, Mathis, F8 of the Cockpit review REV-2): both apps keep the
fields they do not know and write them back, so a file from a newer version or from the other
platform loses nothing on the way. The rule is the same on both sides (`ParamsJson` in `core`,
`ParamsJSON` on the Mac): unknown keys of the root object and of the six fixed layer objects
(`baseLayer`, `effectsLayer`, `graffitiLayer`, `drawingLayer`, `textLayer`, `emblemLayer`) travel
with the parameters (`extras`) and are merged back on save, a typed field always winning over a kept
one; unknown fields inside elements of the `layers` list are not kept, since that list is reordered
and shortened. Every written settings object carries `formatVersion` (1 today), which is not kept
from the file: an app always writes its own. Nothing warns yet when a file's version is higher;
`SettingsJson.formatVersion(text)` reads it for the day a warning is wanted.
