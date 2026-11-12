# Dahlia – Mandala Maker

Session: iOS WP-12 tour, About, languages · 12 November 2026

## IOS-8: First-start tour, Dahlia page of the menu, About, rating and the ten-language pass of the iPhone editor (PLAN-4 WP-12)

What WP-12 of [PLAN-4](PLAN-4-iphone-app.md) built on top of [IOS-6](IOS-6-favourites-history-menu-play-field.md): the eight-card tour on the real controls, the Dahlia page of the menu (Guide, Tour, Rate, About), About with the iOS library list, the rating prompt through StoreKit, and a pass through all ten languages on the smallest supported iPhone. Decisions are F9 a (the same eight cards, rebuilt for the iOS controls, iOS wording where Android places are named).

### What was built

1. **Tour logic** (`Model/Tour.swift`, `Model/TourModel.swift`): the same eight steps as Android's `Tour.kt` in the same order (Play, Favourites, Save and share, Layers, Styles, Colours, Effects, The die), each with its rings, line flags, tab, scroll row and undimmed areas, plus `TourGate` (once on a new install, never for someone with a saved mandala, marked seen at once) and `TourLayout.cardTop`. Pure Swift without UI types; compiled into the unit-test bundle and covered by `Tests/TourTests.swift`, a port of Android's `TourTest` (11 tests, green).

2. **Tour overlay** (`UI/TourOverlay.swift`): the controls report their bounds with `.tourAnchor(...)` (anchor preference, only while a tour runs); the overlay dims everything except the picture, the step's targets and what the step leaves open, rings the targets in the accent colour, draws the line from a ring to the card, docks the card at the bottom (moved up past rings it would cover), and folds the picture smaller where the card would not fit (small phones, large text), as Android does. The card has title and "3 of 8", the sentences, the die places on the last step, Skip / Back / Next (Done on the last card); at large text Skip drops below the other two buttons instead of wrapping. VoiceOver announces each step; the escape gesture ends the tour.

3. **Tabs and scrolling** (`UI/ControlTabs.swift`): the tour switches to the step's tab itself, scrolls the row up under the tabs, then marks the step settled so the rings appear; when it ends, the tab and the scroll positions it found come back. A new step cancels the one before.

4. **Start**: once the first picture is drawn on a fresh install (and without a saved mandala), automatic; again from the Dahlia page ("Tour", which closes the menu first). After the automatic tour a message says where to find it again. The rating prompt and messages wait while the tour runs.

5. **Dahlia page of the menu** (`UI/MenuSheet.swift`): a row "Dahlia" under "More" opens a page with Guide (the website guide in German or English, the same link as Android), Tour, "Rate the app" and About. Pro and Widgets rows come with WP-9 and WP-10.

6. **About** (`UI/AboutView.swift`): icon, name, version and build, licence (copyright, free-software text, source link, GPL link, the section 7 permission in a group), privacy text and policy link, and "Open-source libraries": the Kotlin standard library with the Kotlin/Native runtime and kotlinx.serialization (both Apache 2.0, inside MandalaCore), with the full Apache text bundled as a resource. No Google libraries, no coroutines. SwiftUI, StoreKit and the other system frameworks belong to iOS and are covered by the section 7 permission (its text already names iOS).

7. **Rating** (`Model/ReviewPolicy.swift`, `Model/ReviewPrompt.swift`): the kit's rule from ADR-0006, ported: the first request after at least 7 days since the first opening and 10 different opening days; the second after 90 days and 30 opening days; every later one after 365 days and 30 opening days; at most once per build; the editor must be calm for 2 s (picture drawn, no error, no message, no sheet, no tour, nothing being drawn or moved, app active). The request is StoreKit's `AppStore.requestReview(in:)`. `Tests/TourTests.swift` has the port of the kit's policy tests (8 tests, green). The row "Rate the app" opens `https://apps.apple.com/app/id6833903967?action=write-review`. **That link leads nowhere until the app is released;** the id is the one from App Store Connect.

8. **Strings**: all texts come from Android's catalogue entries (`strings_tour.xml`, `strings_menu.xml`) in ten languages. One iOS-only text, `tour_save_body` (see "Differences"). `Strings/values*/strings_ios.xml`, catalogue regenerated with `ios/scripts/gen-strings`.

### Differences from Android and why

- **Save and share**: Android's text says the arrow saves "or sets it as wallpaper" and that Share is in the menu behind the three dots. On the iPhone there is neither a wallpaper route (WP-11) nor a Share row (WP-8) yet. The card says "The arrow saves your picture to Photos." (iOS-only `tour_save_body`, ten languages, using each language's existing word for Photos from `ios_save_to_photos`), and only the save arrow gets a ring, not the three dots.
- **Layer row and effects row** scroll to the top of their tab (offset 0), where they are anyway, instead of to an element id.
- **No wide layout**: the iPhone app is portrait and phone-only (ADR-0015).
- **Rating**: build number instead of `versionCode`, StoreKit instead of Play In-App Review; no "forced prompt" for test builds (the system dialog shows only in TestFlight and App Store builds).
- **Dahlia page line**: "Guide, Tour, About", made of the words of the rows; Android's "Guide, About, Pro" needs Pro.

### Language pass (all ten languages, iPhone 17e, 390 pt, dark)

Screens: the five tabs (Layers, Scene, Emblem, Text, Draw), favourites sheet, save sheet, menu, More page, Dahlia page, About, and all eight tour cards, in English, German, Spanish, French, Italian, Portuguese (Brazil), Russian, Japanese, Korean and Chinese (Simplified). English and German additionally in light and on the iPhone 18 Pro Max (dark and light). Screenshots: `/Users/mathis/Work/Projects/apps/mandala-generator/docs/qa/shots/ios-wp12-2026-11-12/` (named `<language>-<dark|light>-<17e|max>-<screen>.png`; `tour1` to `tour8`, `tab-*`, `sheet-*`).

Findings, by language (numbers are for this report):

1. **German, About**: "Zusätzliche Erlaubnis" and the summary "GPL Abschnitt 7" did not fit one line; the title wrapped. Fixed by layout: the group's title keeps its line (`layoutPriority`), and the summary moved into the group as its first line.

2. **Russian, About**: same problem with "Дополнительное разрешение". Same fix.

3. **Russian, Draw tab**: the slider label "Насыщенность" ran into the slider. Fixed in `SliderRow`: a label may shrink to 80 % before it touches the slider (all slider rows; nothing changes where the label fits).

4. **Russian, About**: the heading "Библиотеки с открытым исходным кодом" wraps and "кодом" stands alone on the second line. Left: a section title in the title role, Android's text; shortening it is Mathis' call.

5. **Japanese, tour step 8**: the five die places wrap to two lines with "プレイ" alone on the second. Left: same wrapping as Android's flow row; shorter spacing would fit them if wanted.

6. **German, Layers tab**: the layer row's blend name "Negativ multiplizier…" is cut (the percentage stays whole). Existing behaviour from WP-5, not changed.

7. **French, menu**: the die tile "Nouveaux calques" shrinks to fit (existing 70 % scale of WP-7). Readable.

8. **All languages, tour**: the card texts fit in every language, including German and Russian, with Skip, Back and Next on one line at the default text size. The line from a ring to the card crosses the tab labels (as on Android); no label is hidden by a ring.

9. **Spanish, Italian, Portuguese, Korean, Chinese, Japanese, French**: no truncation, overlap or bad wrapping on any other screen listed above.

No Android text was shortened. Not looked at: Dynamic Type sizes above the default, landscape (the app is portrait only), the iPhone 18 Pro Max in the other eight languages, and About in light and on the large phone (those shots were taken before the About fix; the light and large-phone tours, tabs and Dahlia page were checked).

### Verification

- Smallest phone iPhone 17e (390 pt) and iPhone 18 Pro Max (440 pt), dark and light; the tour was clicked through by hand once in English (all eight cards, Done, the message, the restored tab) and driven by debug launch arguments for the language pass: `-shotTourStep <1–8>` starts the tour at a step, `-shotSheet menu|more|dahlia|about|favourites|save` opens a sheet, `-tourSeen YES` keeps the automatic tour away (all debug builds only, in `ShotSetup.swift`).
- Tour card, rings and lines at their places in all eight steps; the picture stays undimmed; the card clears the home indicator.
- Unit tests: `TourTests` (11) and `ReviewPolicyTests` (8) green. `ios/scripts/check` green. `ios/scripts/check-full` green on the merged source (parity test against the JVM renders, including the text cases, and the unsigned Release build; run after merging `main` in).
- Not tested: the real review dialog (the system shows it only in TestFlight and App Store builds; `-reviewForce YES` / `-reviewReset YES` exist in debug builds), the review-page link (no listing yet).

### What the tour cannot point at yet, and what to revisit after WP-8 to WP-11

- **WP-8 (save sheet, export)**: the save card ("The arrow saves your picture to Photos.") must say what the full sheet can do (sizes, sharing); if the menu gets a Share row, ring it again (`TourStep.save.marks`, `TourTests`) and decide whether the iOS-only `tour_save_body` can go back to Android's text.
- **WP-9 (Pro)**: the Dahlia page gets Pro (and its detail line "Guide, About, Pro" as on Android, instead of the composed "Guide, Tour, About"); the layers card names "3 layers in the free app" and stays true; no Pro card is planned.
- **WP-10 (widgets)**: no step points at widgets (Android's tour has none either); the Dahlia page gets its Widgets row; the guide needs the iOS section below.
- **WP-11 (wallpaper)**: Android's save card mentions wallpaper; decide whether the iPhone's does once the route exists.
- **Release**: the App Store review link works only after release; every upload needs a higher build number (`CURRENT_PROJECT_VERSION` is "1" in `project.yml`), because the rating prompt asks at most once per build number. Put it in the release checklist.

### Sections the website guide would need for iOS

The guide is a page on tearoom.one in another repository and was not touched. For the iPhone it needs, in German and English:

- Saving to Photos (the permission "add only", what to do if it was denied: Settings, Dahlia, Photos).
- Wallpaper on the iPhone (the wallpaper page, WP-11), and the "Mandala of the day" shortcut and its automation (WP-11).
- Widgets on the home screen and the lock screen: adding, choosing a mandala, the long-press settings (WP-10).
- Opening and saving settings files through the Files app.
- The language of the app from Settings, Dahlia, Language.
- Where to find the tour, the guide and About (Dahlia page of the menu).

### Review

An Opus agent compared the tour logic and the rating port with Android and the kit (`Tour.kt`, `TourOverlay.kt`, `Controls.kt`, `TourTest.kt`, `ReviewPolicy.kt`, `AppReview.kt`). Step table, gate and policy matched. Its findings and what was done:

1. **Card position not held between steps** (the card jumped to the bottom while a step settled): fixed, the settled position is kept.
2. **Step tasks not cancelled** (a stale task could overwrite the restored scroll position after Done, and fast Next/Back could scroll the wrong tab): fixed, the tab logic runs as `.task(id: step)` and checks for cancellation after each wait.
3. **A message already on screen covered the card**: fixed, messages wait while the tour runs.
4. **Safe-area insets read zero** inside the overlay (card sat on the home indicator, seen in the first screenshots): fixed, the insets come from an outer reader.
5. **Rows land flush under the tabs** (Android leaves a gap): not changed; the ring is inset 4 pt and a row's ring touches the tab line. Cheap to add a gap if wanted.
6. **Round rings stroked half outside the hole**: fixed.
7. **VoiceOver announcement and escape gesture**: added.
8. **`-reviewForce` uses up a real record** (debug builds only): not changed.
9. **Day number could shift with the time zone**: fixed, days are counted in a UTC calendar from the local date, as the kit's `toEpochDay`.
10. **Once per build depends on the build number rising**: not changed in code, noted for the release checklist (above).
11. **A cold start could be missed by the opening count**: fixed, counted on the scene phase.
12. **A tour started while drawing or moving ends that tool** (Android does the same): parity, left.

### Selbst entschieden

- The save card text and ring (see "Differences"), including my own translations of the short iOS-only sentence into nine languages from the words already in the catalogue; please have them read once.
- Ring inset 4 pt around rows and tabs (Android 2 dp), because the Scene tab's switch row has no inner padding and the ring touched the text.
- The tour's picture-fold, card placement and scrim values follow Android's formulas; the scrim colour is the canvas token with Android's two opacities.
- About: the app icon comes from a new image set `AboutArt` (the release icon at 256 px); the Apache text is bundled from Android's asset file through `project.yml`, no new package.
- Small edits in shared files: `ControlGroup` title priority (`Parts.swift`), `SliderRow` label shrink (`Parts.swift`), message hidden while the tour runs (`EditorView.swift`), one row in `MenuSheet.swift`. Strings were regenerated with the script.
- Debug-only launch arguments for the tour and sheets, in `ShotSetup.swift`.
- The language pass was limited to dark on the smallest phone for all ten languages; light and the large phone for English and German only, to keep the memory use of the simulators down (the Mac ran out of memory once while two sessions built in parallel).
- I created two temporary simulators (iPhone 17e and 18 Pro Max, named `wp12-…`) so other sessions' simulators stayed untouched, and delete them at the end.
