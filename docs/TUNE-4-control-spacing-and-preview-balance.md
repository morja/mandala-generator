# TUNE-4 · Control spacing, selection and preview balance

25 October 2026, Mathis' phone review after TUNE-3.

## Refinements

- Framed sections now have 16 dp horizontal content padding, up from 8 dp. Headers keep 8 dp, so content is visibly subordinate. Adjust in the save sheet keeps its existing spacing.
- The selected style has an Amber outline, Amber label and the existing soft Amber fill. Its outline is 2 dp; unselected choices keep a quiet 1 dp outline, using existing tokens.
- Variation and Seed are full-width tonal buttons, including their explanation and die. Their entire row is clickable. The 48 dp minimum height is unchanged, and their button appearance separates them from section headings.
- All active dice use the accent, including Text, Emblem, the selected layer and the top-bar Surprise me. These previously inherited the ordinary icon colour while Effects explicitly used the accent. Menu dice and the tonal action buttons already used the accent.

The slider rows and Material sliders have not changed height. TUNE-3 changed section headers to a common 48 dp minimum, added the two random-action rows and section spacing. This refinement adds horizontal room only.

## Preview balance

The same fixed examples are regenerated on the Mac and copied to Android. Each image uses its foreground's 90th-percentile brightness as a reference. Dense images get a lower brightness target than sparse linework, and a gentle curve lifts faint lines. The output is capped at 210/255, with black unchanged. This replaces the common square-root lift that left large bright forms much stronger than thin dark examples.

All 36 images are nonempty, 96 × 96, and identical between platforms. Measured peak brightness ranges from 131 to 210/255. This affects picker artwork only.

Regenerate with `android/tools/reference-renderer/render-references.sh --style-previews`.

## Arrangement review

The five editing areas still describe useful scopes: individual layers, background and the whole scene, emblem, text, and drawing tools. The renderer applies Effects after the emblem and text, to the assembled image. Layer light and colour settings act on one selected layer. Effects therefore remains one switchable block with its own groups in Scene. No sections were moved or regrouped during this refinement, as Mathis requested.

## Validation

- Mac build passes with regenerated resources.
- All 36 preview pairs match; every image is nonempty and stays below full white.
- Android checks and Debug/Fast builds pass for the final changes, including all 56 core and 53 app unit tests.
- Phone checks use the S23's actual 1080 × 2340 display and 480 dpi, a 360 dp wide layout.
- Selected-style outline, action buttons and slider padding were visually checked, along with the Amber Text and Emblem dice. The temporary emblem toggle and style selection were undone.

Screenshots: [Style selection](images/tune-4/styles-phone-dark.png), [Slider padding](images/tune-4/sliders-phone-dark.png), [Text dice](images/tune-4/text-dice-dark.png), [Emblem dice](images/tune-4/emblem-dice-dark.png).

The final Fast APK was installed and opened on Mathis' S23. Light phone selection was also checked: [Phone, light](images/tune-4/styles-phone-light.png).

The tablet layout was checked in both themes, including the selected-style border and the balanced preview grid. [Tablet, light](images/tune-4/styles-tablet-light.png), [Tablet, dark](images/tune-4/styles-tablet-dark.png). The built Mac resource bundle also contains all 36 updated files.
