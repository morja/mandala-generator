# ADR-0003 · Settings JSON compatible with the macOS app

Status: proposed, 19 October 2026

## Decision

The Android app reads and writes the same settings JSON as the macOS app (`MandalaParameters`):

- same field names and defaults as the Swift `decodeSafe` calls;
- unknown enum values fall back to the default (style → `mixed`, blend → `screen`, background → `gradient`);
- unknown fields are ignored;
- 64-bit seeds are unsigned (`ULong`); values above `Long.MAX_VALUE` occur in real files;
- layers not ported yet (text, graffiti, drawing) are kept as raw JSON so a round trip loses nothing.

## Reasons

Favourites and settings can move between Mac and phone, and the parity tests (ADR-0004) can use files saved by the Mac app.

## Consequences

Changes to the Swift model must be mirrored in `Model.kt`. Custom palettes are not part of the file in the macOS app either (`paletteIndex` refers to built-in palettes first); syncing them is an open question.
