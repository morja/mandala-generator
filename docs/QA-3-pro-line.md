# QA-3 · The Pro line of ADR-0016 on the emulator

22 October 2026, Android 1.4.0 (code 8), branch `feature/pro-line`. Release build (R8, signed with the debug key, so `pro_for_testing` is off and the app runs without Pro) on the Android emulator (Pixel-sized, 1080 × 2340, dark theme), with fresh app data. Google Play is not reachable there, so "Buy Pro" is disabled; the purchase itself was not tested. Not yet checked on Mathis' phone.

## Checks

`scripts/check-full` passed: unit and rule tests (new: `ProFeaturesTest`, favourites room, two-layer throws), memory test, parity test, release build.

| # | What | Result |
|---|---|---|
| 1 | Add a third layer without Pro | Works; "Add · Pro" and "Duplicate · Pro" from two visible layers on, a note under the list, the Pro mark on the mandala ([screenshot](images/qa-3/qa3.png)) |
| 2 | Save a mandala with three layers | The Pro sheet opens over the save sheet and names "More than two layers" ([screenshot](images/qa-3/qa4.png)) |
| 3 | "Save without them" | Layer 3 is hidden (not deleted), the image is saved at 1024 × 2219, the Pro mark goes away |
| 4 | A sixth own favourite | The first five are added; the sixth opens the Pro sheet with the favourites note; the button reads "Add current · Pro" ([screenshot](images/qa-3/qa6.png)) |
| 5 | Surprise die, eight throws | One or two layers each time |
| 6 | Emblem without Pro | "Show emblem · Pro", the emblem can be tried, note and Pro mark show ([screenshot](images/qa-3/qa7.png)) |

## Not checked

- Light theme and the two-pane tablet layout: no new colours or sizes, only existing tokens and components.
- A Pro build (the fast build counts as Pro): with Pro, `missingPro` is empty, so no marks or sheets show; covered by code, not by a run.
- Sharing, setting the wallpaper and text without Pro: same path as saving (`mayKeep`).

## Notes

- A surprise mandala keeps an emblem or text the user switched on, since the dice only change layers and background; it then shows the Pro mark like any other mandala with them.
- The Pro sheet's button says "… without them" also when one feature is listed.
