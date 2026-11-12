# Dahlia – Mandala Maker

Session: iOS WP-3 shared editing · 12 November 2026

## IOS-2: Shared editing rules, Android first (PLAN-4 WP-3)

What WP-3 of [PLAN-4](PLAN-4-iphone-app.md) moved out of the Android app into the shared Kotlin Multiplatform module `android/editing` (approval A7), where the seams are, how it was checked, and what Mathis should try on his phone.

### Findings

1. **What moved.** Into `android/editing/src/commonMain` (package `com.mathis.mandala.editing`), unchanged apart from the package: `Editing` (pickers, ranges, layers, effects, strokes, positions, save sizes and file names, framing), `MoveTarget`, `CentreSnap`, `DrawTool`, `Randomize` (the dice), `PlayField`, `History`, the save choice types `SaveChoice`, `Placement` and `Fit`, the favourites list rules `Favorites` (add, remove, merge, first mandala) and the daily turn as `DailyTurn` (`turn`, `toggled`, `displayName`, formerly inside `DailyWallpaper`). Two `Math.round` calls became Kotlin's `roundToLong`, which is the same function on the JVM and has the same rounding on iOS.

2. **What stayed in the app.** Everything with `android.*`, Compose, storage or state: `EditorViewModel`, `SaveChoiceStore` (shared preferences), `FavoritesStore`, `MyQuotes` and its store, `writeAtomically` (java.io), `DailyWallpaper` (JobScheduler, preferences, the 4–8 o'clock window with `java.time`), `WallpaperTarget`, `Wallpapers`, `screenSize`, and `PreviewSize` (how large the phone preview stands; a view layout value, now in `PreviewSize.kt`). `RecentImages` and `MyPalettes` also stay; their tests were split out of the moved test files.

3. **The seams.** (a) String resources: `SaveFormat` and `DrawTool` carried `@StringRes` labels. In the module they are plain enums; the app maps each entry to its resource in `EditingLabels.kt` (`SaveFormat.label`, `DrawTool.label`), an exhaustive `when`, so a new entry without a label does not compile. Call sites read `format.label` as before. (b) `Editing.framingFor` needs the save choice, so the data types `SaveChoice`, `Placement` and `Fit` moved with it while their store stayed. (c) The daily turn: the choice of favourite is shared, the timing is not (Android: the morning job; iPhone: by date, ADR-0015 point 3). (d) iOS: `IosEditing` (with `LayerSlider` and `SliderGroup`) moved from core's `iosMain` to editing's `iosMain` and now calls `Editing` for every rule instead of copying it; the slider ranges come from `Editing.Ranges`.

4. **Ways considered for the cut.** (a) Move whole objects and files as they are, the app maps the few labels: one place per rule, the app's call sites barely change; drawback: the app imports from a new package and the label mapping is a second list next to the enum. (b) Keep `Editing` in the app as a thin facade over the module: no import changes; drawback: every rule has two names and new rules land in the wrong place. (c) Move only what the iPhone app needs now (WP-1's few rules): smallest change; drawback: the die, the play field and the save sizes would follow piecemeal and the copy in `IosEditing.kt` would grow meanwhile. **Picked (a).** For the package, keeping `com.mathis.mandala.app` in the module would have saved the imports but put an "app" package into shared code, so it is `com.mathis.mandala.editing`.

5. **The framework.** The module builds the XCFramework the iPhone app links: still named `MandalaCore` with `export(project(":core"))`, so Swift's `import MandalaCore` and all names stay. `ios/Dahlia/scripts/build-core` now calls `:editing:assembleMandalaCoreReleaseXCFramework`; the path the Xcode project links (`ios/build/MandalaCore.xcframework`) is unchanged, so WP-2's test target needed nothing. Core keeps its own framework for SpikeBench. Swift changes: one comment, and `Timing.quickMinLayers` and `quickAfterMs` now read `Editing.QUICK_MIN_LAYERS` and `QUICK_AFTER_MS` (same values, 2 and 1000).

6. **Rules and checks.** `ArchitectureTest` gained `editingUsesNoPlatformApis` (no `android.`, `androidx.`, `java.awt`, `javax.`, `kotlinx.coroutines` in `commonMain` and `iosMain`) and `commonEditingUsesNoJava`; `editing` is an approved root folder of `android/`. `android/scripts/check` and the hook's task list (`bin/project.env`) run `:editing:test`. The A7 deviation is recorded in ADR-0005 (layering) and ADR-0015 (consequences); `android/AGENTS.md` names the module.

7. **Test results.** The 68 tests of the moved test files: 60 moved with the code (now `kotlin.test` in `commonTest`, `StyleControlsTest` in `jvmTest` because it renders), 8 stayed in the app (stores, assets, recent images, own palette list, the morning window). All 60 pass on the JVM, and the 58 common ones also pass on the iOS simulator (`:editing:iosSimulatorArm64Test`), so the dice, the play field and the history give the same results on both phones. `android/scripts/check`: green. `android/scripts/check-full` (parity against the macOS references and the release build): green. The iPhone app builds for the simulator (also through the hook's `ios/scripts/check`).

8. **iPhone app, WP-1 behaviour.** On a fresh simulator (iPhone 17, iOS 27.2, deleted afterwards): opens on the starter favourite (Hyperboloid, Prism); the style list is in name order and switching to Floral redraws; the palette list shows the 15 offered palettes plus the hidden Prism at the end while it is in use, and Prism leaves the list once Gold is picked; hiding a layer works; "Add current" puts the new favourite first in `favorites.json`; "Save to Photos" writes a 1024 × 1024 PNG to the photo library; the last settings come back after a restart.

9. **Android: the fast build.** Installed on the Galaxy S23 with `adb -s … install -r` (data kept, now 1.8.8 fast), but the phone was locked, so it could not be driven; the phone check is Mathis'. The same APK on the emulator (MandalaApi34, existing data kept): it opened on the saved mandala with the saved favourites; the die threw new layers (Nautilus, Torus Knot); Back restored the four layers and Forward the throw; the play field changed the mandala on a drag, "New landscape" changed it again with the point kept off the middle, Compass worked, and its star put the result first in `favorites.json`; tapping a favourite applied it; the save sheet's format list showed every label ("Square", "This screen", "4:5 · portrait" … "16:9 · landscape", mapped by `EditingLabels.kt`); "Save" wrote `mandala-hype-p5-x3-102c2de6-1024x2219.png` to the gallery; two forced morning runs (`cmd jobscheduler run -f … 2014`) moved the turn from 3 to 4 to 5 onto exactly the favourites the rule predicts, and both home screen widgets showed the new favourite.

10. **Left for later.** The hook runs `ios/scripts/check` for `ios/` and `android/core/` but not yet for `android/editing/`, so a change only in `editing` does not rebuild the iPhone app before a commit (WP-2's hook line; one pattern to add). The iPhone app still inserts favourites itself (`EditorModel.addFavorite`); it can switch to `Favorites.add` when WP-5 builds the favourites screen.

### What Mathis should try on his phone

- Open "Dahlia Fast" (the app with the mark) on the S23: it opens on the mandala you left, your favourites are all there. Expect: nothing looks or behaves differently from 1.8.8.

- Tap the die twice, then the back arrow twice and the forward arrow once. Expect: new mandalas, back to where you started, then the first throw again.

- Open the play field (compass at the top), drag the point, tap "New landscape", switch to Compass and drag, tap the star, then "Done". Expect: the picture follows the finger, and the star puts that mandala first in the favourites (star at the top).

- Save a picture in "This screen" and look at the format list on the way. Expect: every format has its usual name, the picture lands in the gallery as before.

- Tomorrow morning: the home screen widget shows the next favourite (with Pro), as it did before.

### Selbst entschieden

- Package `com.mathis.mandala.editing` instead of keeping `com.mathis.mandala.app` in the module (finding 4).

- The labels are mapped by enum entry in the app (`EditingLabels.kt`) rather than by a string key in the module: the entry name is the key, and the compiler checks that every entry has a label.

- `SaveChoice`, `Placement` and `Fit` moved along, because `Editing.framingFor` needs them; `SaveChoiceStore` stayed.

- `PreviewSize` stayed in the app as a layout value; `MyQuotes` stayed too (not part of WP-3's list, plain enough to move later).

- The editing tests are `commonTest` with `kotlin.test` instead of JUnit 4, so they also run on iOS; `kotlin-test` was already approved (6 November 2026), no new package.

- The framework keeps the name `MandalaCore` and comes from `:editing`; core's own framework stays for SpikeBench.

- `bin/project.env` (the hook's Android task list) gets `:editing:test`; the hook file itself is untouched.
