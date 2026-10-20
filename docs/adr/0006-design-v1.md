# ADR-0006 · Design v1: warm neutrals around the mandala, one amber accent

Status: proposed, 19 October 2026

## Decision

The mandala brings all the colour. The interface around it stays calm:

- Warm neutral backgrounds (dark: near-black brown-grey, light: warm off-white), following the system's light or dark setting.
- One soft amber accent for selection and the main action (the save icon in the top bar).
- The canvas is always black, because the renderer draws light on black.
- System sans serif (Material 3 type scale), Material 3 components mapped onto the tokens.
- Palettes are shown as round sweep-gradient swatches of their own colours.
- Controls in tabs and foldable groups with one-line sliders (20 October 2026), so the phone screen shows the canvas and one area at a time.
- All values in `app/src/main/kotlin/com/mathis/mandala/app/ui/theme/Tokens.kt`.

## Reasons

Follows the `design-baseline` skill: the project's own imagery carries the character; no neon, glow, glass or purple gradients in the interface itself.

## Rejected

- One long scrolling page with all sections and "Show sliders" buttons (v1, 19 October 2026): too cramped and confusing on a phone.
