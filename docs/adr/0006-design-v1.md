# ADR-0006 · Design v1: warm neutrals around the mandala, one amber accent

Status: proposed, 19 October 2026

## Decision

The mandala brings all the colour. The interface around it stays calm:

- Dark by default (decided by Mathis on 20 October 2026, variant DM-3 in QA-1): a near-black warm background (#0A0908) with calmer controls, so the mandala is the brightest thing on screen. The light theme (warm off-white) is a choice in the menu ("Light theme") and is kept across starts; the system setting no longer decides. Status and navigation bar icons follow the app's theme.
- One soft amber accent for selection and the main action (the save icon in the top bar).
- The canvas is always black, because the renderer draws light on black.
- System sans serif (Material 3 type scale), Material 3 components mapped onto the tokens.
- Palettes are shown as round sweep-gradient swatches of their own colours.
- Controls in tabs and foldable groups with one-line sliders (20 October 2026), so the phone screen shows the canvas and one area at a time. Amended 25 October 2026 after Mathis' review (TUNE-3): one outlined section style across the editor, equal 48 dp minimum header height, chevron at the right; random actions in labelled rows inside Style and Shape; Background initially closed. Style choices stay inline and alphabetically ordered, with fixed renderer-made miniatures instead of category headings. Existing tokens only.
- On phones the canvas takes up to half the screen height (was 42 %, DM-3).
- On phones a button in the image's bottom right corner (where the thumb reaches it) folds it into a small square at the top left, with the selected layer named beside it, so the controls get the room; the button, a tap on the small image, or pulling the controls down once they are at the top brings it back (the image grows with the pull; past a third it opens), and the size stays as chosen while the controls scroll (DM-4 variant B, chosen by Mathis on 21 October 2026 after trying variant A on the phone). Folding only scales the shown preview and never renders (a smaller preview while folded was tried and dropped on 21 October 2026: switching sizes restarted renders while scrolling). Drawing and moving keep the image large.
- Previews rendered recently (30, about 1 MB each) are kept by their settings, so stepping through the history, undoing or switching something back shows the image at once; a favourite shows its thumbnail until its preview is ready; the save sheet keeps its last six previews by format and framing.
- The menu (⋮) is a sheet from below (DM-7 B, chosen by Mathis on 22 October 2026): the mandala on screen with Favourite, Share and Wallpaper; "New mandala" with three dice; then Open file, Save settings, Fast preview, Theme (Dark or Light) and About. A die shows its new mandala next to the current one in the same sheet, with Try another and Use new; nothing is replaced before Use new. The die in the top bar stays the quick way and replaces at once (the back arrow undoes it).
- Everything random has a die: one dot for a small change (Variation, a single layer, the emblem), three for new layers, five for everything (the top bar). The menu icons are outlined in the accent over a faint fill of the same colour.
- The save sheet (DM-11 A, chosen by Mathis on 22 October 2026): format as one segmented row (Square, This screen, Ratio with a list); the frame with Whole or Fill at its bottom right beside a tall frame (This screen, 9:16, 2:3), below the others; brightness, contrast and saturation folded into "Adjust"; the line drawing as a quiet button beside the title. Size, Save and Wallpaper at the bottom. Nothing scrolls inside the sheet (a drag at the top of a scrolled list closed it too easily, Mathis on the phone): the frame takes the height the other parts leave.
- Messages disappear on their own (about 10 s with Undo, a few seconds without); while the favourites sheet is open they show at its top.
- All values in `app/src/main/kotlin/com/mathis/mandala/app/ui/theme/Tokens.kt`.

## Reasons

Follows the `design-baseline` skill: the project's own imagery carries the character; no neon, glow, glass or purple gradients in the interface itself.

## Rejected

- Folding the image while the controls scroll (DM-4 variant A, tried on the phone on 21 October 2026): every slider further down forced the small image, with no way to keep it large.
- A drag handle under the image (DM-4 variant C): any size, but harder to hit and easy to confuse with scrolling.

- Following the system's light or dark setting (until 20 October 2026, DM-1): the warm grey dark theme and the light theme let the mandala glow less.

- A dropdown menu with icons and a sheet of tiles (DM-5 A and B), a sheet that explains each die in a list (DM-6 A, too tall, it needs scrolling), and dice in the menu that replace at once (DM-7 A).

- A save sheet with tabs for image, wallpaper and line drawing (DM-11 B): tidier, but one more step for the common "just save"; Whole and Fill on the image itself (first DM-11 draft) covered the mandala.

- One long scrolling page with all sections and "Show sliders" buttons (v1, 19 October 2026): too cramped and confusing on a phone.
