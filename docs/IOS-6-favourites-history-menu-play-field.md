# Dahlia – Mandala Maker

Session: iOS WP-7 shell · 12 November 2026

## IOS-6: Favourites, history, menu, die and play field of the iPhone editor (PLAN-4 WP-7)

What WP-7 of [PLAN-4](PLAN-4-iphone-app.md) built on top of [IOS-4](IOS-4-layers-and-scene.md): favourites with selecting and Undo, Back and Forward, the menu with the comparison for a new mandala, opening and saving settings files, the global die, the play field, the folded picture and the last picture standing in at the start. Checks: `ios/scripts/check`, `ios/scripts/check-full` (parity included) and, through the commit hook, `android/scripts/check` are green.

### What was built

1. **Top bar** (`EditorView.swift`): Back and Forward on the left; play field, die ("Surprise me"), save, star and menu on the right, in Android's order. Seven buttons do not fit the system navigation bar on the smallest iPhone (it hid the star and the play field in an overflow), so the bar is Dahlia's own row, with the tokens' sizes.

2. **History** (`Shell.swift`, `EditorModel+Shell.swift`, shared `History` through `IosShell`): a state settles 700 ms after the last change and goes in (50 states), going back and then changing drops the states ahead, a change younger than the delay is kept before stepping, `history.json` in the Mac's format is kept across starts and read at the start. Same rules as `EditorViewModel`.

3. **Die and menu** (`MenuSheet.swift`): the top bar's die is `surprise` (new layers and background, three layers without Pro). The menu has the current mandala with its styles and layer count (plural per language), the Favourite tile, the three dice of "New mandala" (Variation, New layers, Surprise me) that show the new mandala beside the current one with "Try another" and "Use new" (nothing changes before that), the Dark/Light choice (kept, not following the system) and "More" with Open file, Save settings, Fast preview and Pictures in the lists (the two switches moved here from WP-5's ellipsis menu, which is gone).

4. **Favourites** (`Sheets.swift`): tap opens, long press selects, then taps select; "Selected: n" with Cancel and Delete, one message "Favourites deleted: n" with Undo that brings them back in their old places (favourites added meanwhile stay at the end). "Add current" inside the sheet is quiet, the menu's Favourite tile says "Added to favourites". The list is saved off the main thread, in turn.

5. **Files**: "Open file" through the Files picker takes a settings file or a favourites file of the Mac app (or of Android); settings replace the mandala, favourites are merged by id with the shared `Favorites.merge` and the message "n added (m already there)" with real plurals. "Save settings" writes the settings JSON through the Files exporter, named like the picture. Checked with a favourites file made from the starters (3 new, 2 known: 24 in the list afterwards, 21 + 3).

6. **Play field** (`PlaySheet.swift`, shared `PlayField`): Landscape and Compass tabs, picture, the field with the point (one finger, Android's 1.3 aspect), Compass with Light/Shape/Depth/Colour at its edges, hint, and the row Reset, New landscape (in the Compass "Start from here"), Effects (struck through and muted when off), star (adds the picture as played, fills, tapped again takes it out) and Done. Same sequences as Android: the point hops out of the middle for a new landscape, the effects switch keeps the effects layer's values, the sheet cannot be pulled away (only Done closes it), Back in the editor undoes the play.

7. **Folded picture**: the button in the picture's corner and a tap on the small picture switch between full and small (0.32 of full, kept across starts); small, the open tab's title and detail stand beside it (`ControlHeader`, "1 · Hyperboloid / Screen · 100 %", for Layers and Scene; the other tabs come with WP-6). The controls get the room.

8. **Last picture at the start**: `last-preview.png` with the settings it was drawn from; it stands in while the first render runs, only if the settings are still the same.

9. **Strings and tokens**: all texts through the catalogue (new file `ShellTexts.swift`, one iOS-only key `ios_select` in ten languages, the iOS variants of the favourites hint and empty text removed because Android's now apply); `check-sources` also checks plural keys and extensions of `Texts`. No new tokens.

### Differences from Android and why

1. **Left out, belong to later packages:** Share, Wallpaper, daily wallpaper, line drawing (WP-8/WP-11), Pro page, widgets, guide, tour, rate, About (WP-9/10/12). Nothing is shown dead; the menu's main page therefore has one Favourite tile instead of three.

2. **Pulling the controls down does not grow the picture** (Android's nested scroll); button and tap do. SwiftUI has no equivalent of the nested scroll connection that is worth the risk now.

3. **No tooltips on the icon buttons:** iOS has none by touch; every button has its accessibility label.

4. **Own top bar instead of the system navigation bar** (finding 1).

5. **Candidate renders share the engine's queue** with the preview; Android renders both in parallel. The 320 px picture is small; a throw can delay a running preview slightly.

6. **Messages** are Dahlia's own bar; in the favourites sheet it stands below the title as on Android, elsewhere at the bottom.

7. The star in the play field waits for the finished preview (the review found Android makes the thumbnail of the previous state there); this is a small fix over Android, which should get it too.

### What the review found

An Opus review checked the diff for behaviour against Android (history and die sequences, favourites merge and Undo, play field, candidate) and the preview pipeline: no deviation in history, dice, merge order, Undo order, play field sequences or the pipeline's latest-wins. Fixed after it:

1. Favourites were saved on the main thread on every change; now off it, in turn, newest last.

2. "Open file" decoded and merged on the main thread and refused files with a stray non-UTF-8 byte; now decoded and merged off it, lossy like Android's `decodeToString`.

3. History, favourites and last-picture saves could overtake each other; each is now one task chained after the previous one.

4. VoiceOver could not start selecting favourites; a "Select" action was added. The play field could be moved only up and down with VoiceOver; two actions (Colour, Shape) move it across and it reads its position.

5. The new-mandala picture ignored the fast-preview switch; it now uses the fast glow only when the switch is on. A picture that cannot be made keeps the old one instead of leaving "Use new" locked.

6. The star in the play field took the old preview as thumbnail; it now waits for the finished one. The stand-in picture at the start is only used when the settings are still the same. Cancelling the settings export no longer reports a failure.

Not done: the thumbnails of the favourites sheet are decoded again whenever the selection changes (cheap at 128 px, a cache can come with the grid's later work); candidate renders still share the engine queue (difference 5).

### Verified in the simulator

iPhone 17e (smallest, 390 pt) and iPhone 18 Pro Max, dark and light, English, German and Russian; screenshots in `docs/qa/shots/ios-wp7-2026-11-12/` of the main checkout. Checked: die twice, Back twice, Forward; menu, comparison, Use new; favourites sheet, long press, select two, delete, Undo (restores in place); play field Landscape and Compass with drag, Effects off, star; folded picture with header (Layers, Scene, Russian); the last mandala and the history come back after a restart (`history.json`, `last-preview.png` on disk); opening a favourites file merges as expected. Not driven by hand: the Files picker panels themselves (opened with a debug launch argument `-openFile <path>` that calls the same `openFile`; `-lightTheme` and `-AppleLanguages` for light and language), the settings exporter and Back/Forward on the 17e after a restart. Two sessions drove the same simulators; the WP-7 build is installed as `one.tearoom.mandala.debug.wp7` so it does not collide with WP-6's debug build.

The iPhone 15 was **not** reachable (state "unavailable", locked or not connected): no install on the device.

### What to try on the iPhone

1. Tap the die in the top bar a few times, then Back and Forward. Expect: new mandalas, then the ones you came from and went to.

2. Menu (three dots) → "New mandala" → one of the dice. Expect: the current picture and a new one side by side; "Try another" throws again, "Use new" replaces and closes the menu.

3. Star → press a favourite for a moment, tap two more, Delete, then Undo in the bar. Expect: the favourites come back where they were.

4. Menu → More → Open file: pick a `favorites.json` from the Mac app. Expect: a message "n added" and the favourites in the sheet. Save settings writes a file you can open again.

5. Compass icon → drag in the field, switch Compass, tap the sparkle (Effects off), the star, Done; then Back. Expect: the picture follows the finger, the star fills, Back undoes the play.

6. Picture's corner button. Expect: the picture becomes small with the tab's title beside it; the controls get the room; the button or a tap on the picture brings it back.

### Selbst entschieden

- Own top bar instead of the system navigation bar (seven buttons did not fit; the system bar hid two).

- The menu has no wallpaper, share, Pro, widgets, guide, tour, About and rate entries now; the Favourite tile alone fills the row.

- Play field sheet cannot be dismissed by swiping, only with Done.

- Folded picture size is a plain fraction (0.32) kept in `UserDefaults`; pulling the controls does not enlarge it.

- Menu closes after "Use new", "Favourite", opening or saving a file (Android closes before acting, too).

- "Select" is the one new iOS-only string; the iOS variants of the favourites hint and empty text were dropped because Android's text now holds (selecting and the Mac file exist).

- `-openFile <path>` debug launch argument (DEBUG builds only) to test file opening without driving the picker.

- The `EditorModel` fields `params`, `selected`, `preview`, `rendering`, `favorites` and a few helpers lost `private(set)` / `private` so the new extension file can use them; the new rules live in new files.
