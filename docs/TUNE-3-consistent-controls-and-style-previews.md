# TUNE-3 · Consistent controls and style previews

25 October 2026, Mathis' review of the Fast app after TUNE-2.

## Controls

All Android editor sections now use the same outlined container, typography, spacing and right-hand chevron. The header has a 48 dp minimum height using existing size tokens. Larger system text can grow it. Child sections use indentation, without a smaller header variant. Colour sections and Draw use this same component. Adjust in the save sheet keeps the surrounding sheet dividers instead of another frame.

Background starts closed on Mac and Android. Section state still survives tab changes within the editor. Effects puts its switch, random pattern and Reset in an action row inside the open section, keeping its closed header the same height as the others.

Style starts with **Variation · Colour & settings**, with the direct dice button that keeps the style and randomises its palette and look. Shape starts with **Seed · Arrangement**, whose dice changes only the seed. The existing editing functions and undo behaviour are unchanged. The layer-level die still randomises the selected layer including its style. No random-action submenu was added.

## Style previews

The 36 styles stay in their fixed alphabetical order in Android's inline picker. Each chip adds a small monochrome example beside its name, with the selected chip still highlighted. There are no category headings or additional selection screens. The Mac picker also shows these examples.

The Mac renderer generates 96 × 96 PNGs from fixed settings, Silver palette, no background or atmospheric effects, and seed 42. Strange Attractor uses its existing calmer range. A contrast lift makes thin linework legible at the 24 dp display size and keeps black black. These are illustrative examples, not previews of the user's current layer.

Both apps ship identical files. Android loads standard drawable resources, with no rendering while the picker opens. The complete artwork is under 512 KB per platform, with no new packages or design tokens. The Mac app bundle packaging includes its new resources.

Regenerate both sets from the Mac sources:

```sh
android/tools/reference-renderer/render-references.sh --style-previews
```

The Android resource mapping is exhaustive over the style enum. A new style requires its preview resource and mapping before the app compiles. The generator rejects empty images.

## Validation

- Mac `swift build` passes, including the resource bundle.
- Android `scripts/check` passes, including the architecture and editing tests.
- All 36 preview files exist at 96 × 96 and match byte for byte between both apps.
- Debug and Fast APKs build.
- Emulator button checks confirm Seed changes only the selected layer's seed; Variation changes that layer's look while keeping its style and the rest of the scene. Undo restores each test change.
- The first Scene visit shows Background closed. Fully visible Style, Palette, Blending and Shape headers measure 126 px at 420 dpi, corresponding to 48 dp.

Visual checks include the inline style selection and Draw in phone portrait, and the two-pane tablet layout. [Style picker, phone dark](images/tune-3/styles-phone-dark.png) and [Draw, phone dark](images/tune-3/draw-phone-dark.png), and [Style picker, phone light](images/tune-3/styles-phone-light.png).

The final Fast APK was installed and opened on Mathis' connected phone.

The light phone and tablet layouts were checked without clipped labels or horizontal scrolling. The save sheet's expanded Adjust section still leaves its frame and bottom actions visible. [Tablet, light](images/tune-3/styles-tablet-light.png) and [Save sheet](images/tune-3/save-adjust-phone-light.png).

The final dark tablet selection also fits without clipping: [Tablet, dark](images/tune-3/styles-tablet-dark.png).
