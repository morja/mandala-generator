# ADR-0018 · Which layers the scene effects reach

Status: accepted, 26 October 2026 (Mathis). Android first; amends ADR-0017 point 4 and departs from ADR-0008 (Mac first) and ADR-0003 (same JSON fields) for these two fields.

## Decision

1. The effects layer gets two switches, "Emblem" and "Text", under "Applies to" at the end of the effects. JSON fields `affectsEmblem` (default `true`) and `affectsText` (default `false`) on `effectsLayer`.
2. Text sits above the emblem, so effects go in below the lowest layer left out: with the emblem left out they apply before the emblem, with both in after the text. Switching text on switches the emblem on; switching the emblem off switches text off.
3. No switches for single mandala layers: bloom, vignette and clarity work on the whole picture, and leaving out a middle layer would mean splitting and recompositing the stack.
4. In the Scene tab the effects come first and open; the background moves below them and stays closed.
5. Android only for now (Mathis, 26 October 2026: the Mac app is secondary). The Mac app ignores the two fields and renders with the defaults.

## Consequences

Saved settings without the fields keep their look, since the defaults are the order of ADR-0017. A favourite with a non-default choice looks different when opened on the Mac. Tests: `EffectsScopeTest` (core), `EditingTest` (switch coupling, reset keeps the choice).
