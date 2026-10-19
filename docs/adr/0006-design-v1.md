# ADR-0006 · Design v1: warm neutrals around the mandala, one amber accent

Status: proposed, 19 October 2026

## Decision

The mandala brings all the colour. The interface around it stays calm:

- Warm neutral backgrounds (dark: near-black brown-grey, light: warm off-white), following the system's light or dark setting.
- One soft amber accent for selection and the main action ("Save to gallery").
- The canvas is always black, because the renderer draws light on black.
- System sans serif (Material 3 type scale), Material 3 components mapped onto the tokens.
- Palettes are shown as round sweep-gradient swatches of their own colours.
- All values in `app/src/main/kotlin/com/mathis/mandala/app/ui/theme/Tokens.kt`.

## Reasons

Follows the `design-baseline` skill: the project's own imagery carries the character; no neon, glow, glass or purple gradients in the interface itself.

## Rejected

Nothing yet.
