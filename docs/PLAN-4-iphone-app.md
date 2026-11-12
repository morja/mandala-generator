# Dahlia – Mandala Maker

Session: PLAN-4 iPhone plan update · 12 November 2026

## PLAN-4: Dahlia for iPhone

First written 22 October 2026, revised 12 November 2026. Status: decided 12 November 2026 (Mathis, "Alles wie empfohlen"): every recommendation of section 6 is taken, approvals A1–A11 of section 5 are granted (A5 only if the device measurement confirms it, `docs/APPROVALS.md`), [ADR-0015](adr/0015-iphone-app.md) is accepted. Nothing of the app is built yet; `ios/SpikeBench` is the benchmark of SPIKE-3 and SPIKE-4.

**Revised 12 November 2026.** What changed against the October version:

- The goal is now the **full Android feature set** in the first version (Mathis, 12 November 2026: "die erste Version kann ruhig schon alles unterstützen"), lowest device about the iPhone 13. October's "no video export" and "up to five favourites for free" are gone: the moving picture is Pro on Android since 26 October (ADR-0010 point 10), favourites are unlimited since 22 October (ADR-0016).

- **Android first** replaces Mac first (`AGENTS.md`, 27 October 2026): the iPhone app follows today's Android app (1.8.8), not the Mac.

- The **speed question is answered** by [SPIKE-3](SPIKE-3-ios-kotlin-native-speed.md) and [SPIKE-4](SPIKE-4-renderer-allocations.md): the shared Kotlin core is fast enough, draws the same pixels as the JVM, needs the release framework and the worker-pool `Parallel`, and widgets cannot render in their extension. Mathis decided (11 and 12 November) to keep the shared core and not to tune speed further before the app exists. October's path A (Swift renderer from the Mac) is dropped and the path question removed.

- New since October and now in the plan: own palettes (ADR-0021), ten languages and quotes per language (ADR-0019), the home screen widget with settings per widget (ADR-0020 and its amendment), the onboarding tour, the play field, the moving picture, the shared Pro purchase code (ADR-0022, Android only), the rating prompt.

- The Apple Developer account exists (team 6735ZK5WKV); October's question 5 is now only the bundle id (F1). The minimum iOS is reconsidered against iOS 27 (F2).

- New sections: feature inventory, architecture choices, work packages, approvals, things still to verify.

### 1. What stays from October

- A mandala maker with no subscription, no ads, no tracking, sold with one Pro purchase (ADR-0016, 2.99 € on both platforms).

- Open source as in ADR-0008: GPL-3.0 with the section 7 permission, which already names the iOS system libraries and the App Store. StoreKit, WidgetKit, App Intents, AVFoundation, Core Text and ImageIO are system libraries, so nothing new is needed there.

- Native SwiftUI on top of the Kotlin `core` as an XCFramework (approved 11 November 2026, `docs/APPROVALS.md`); the Android UI is not shared. Not planned: iCloud sync, Apple Watch, the Mac app in the App Store (decided later).

### 2. Feature inventory

Every user-facing feature of the Android app as of 12 November 2026 (sources in `android/app/src/main/kotlin/com/mathis/mandala/app/`, strings in `res/values/strings_*.xml`, UX-1). "Free" and "Pro" follow ADR-0016; "same" means the same behaviour and the same core call, with iOS controls.

| Feature | On Android today | Free / Pro | iPhone |
|---|---|---|---|
| Editor | Picture on top, controls below; two panes from 600 dp; picture full or small, pulled to any size, kept across restarts; a status line with progress | Free | Same. Two panes only if F3 says iPad |
| Tabs | Layers, Scene, Emblem, Text, Draw; groups that open and close, "Changed" marks, reset per slider | Free | Same |
| Layers | Add, duplicate, hide, reorder, delete, randomise; blending, opacity; up to three free, five with Pro | 3 free, 5 Pro | Same (`ProFeatures.FREE_LAYERS` in core) |
| Moving layers and emblem | "Move on image": drag, pinch, turn; snapping to centre lines with a guide and one haptic tick (ADR-0011) | Pro | Same; haptic through SwiftUI `sensoryFeedback` |
| Styles | All styles, each with a small picture rendered by core; switch "Pictures in the lists" | Free | Same |
| Palettes | 21 built-in palettes in rows of three by family | Free | Same |
| Own palettes | Made and edited in place in the Palette group, "From picture", carried inside the settings, `palettes.json` (ADR-0021) | Free | Same, with Dahlia's own hue, saturation and brightness rows, not the system colour picker (it opens a sheet over the picture and works in RGB) |
| Scene: background | Auto, picture, colour, gradient, sunburst, grain | Free | Same; the picture comes through the system photo picker, which needs no permission. Different: the copied picture is found by file name in the app's folder, since the absolute container path changes between installs |
| Scene: effects | Vignette, bloom, tone, sepia, fade, texture, clarity, stars, glitter, highlights, dimming, erasure, relief, chromatic, old fade, smoother; a die for a new look and one per card; hold the picture to see it without effects | Free | Same |
| Emblem | Motifs with variants and small pictures, style and frame, colour group with own palettes | Pro | Same |
| Text and quotes | Words card in the picture's font, collection with language chips (ADR-0019), own quotes, random quote, Keep, font, size and place, cloud, shadow; moving the text up and down on the picture | Pro | Different on iOS: the text masks come from Core Text instead of Android's text renderer, so pixels differ from Android (as Android already differs from the Mac). iOS ships many of the Mac's fonts (Zapfino, Didot, Papyrus, from memory, to verify), so it may come closer to the Mac than Android does. Quotes come from core unchanged |
| Drawing | Lines and spray, symmetry, brush size, softness, colours, undo, clear | Free | Same |
| Play field | Compass icon in the top bar; Landscape and Compass views, New landscape, Reset, Add to favourites | Free | Same (needs the shared editing logic, section 3.1) |
| The die | Menu "New mandala": Variation, New layers, Surprise me, shown beside the current mandala with "Try another" and "Use new"; dice in groups | Free; surprises stay within the free line without Pro | Same |
| Favourites | Star sheet, add current, 21 starters written on the first start, select several by long press and delete with one Undo; file compatible with the Mac | Free, unlimited | Same |
| Open and save files | "Open file" (settings or a Mac favourites file), "Save settings" as JSON | Free | Same through the system file picker and exporter; files stay interchangeable with Android and the Mac (ADR-0003) |
| Undo history | Back and forward through 50 states, kept across restarts in the Mac's `history.json` format; recent pictures come back without rendering | Free | Same |
| Last picture at start | The last preview stands in while the first render runs | Free | Same |
| Save sheet | Picture or Video tabs, format menu, size in the save button | Free at 1024 px | Same |
| Formats and framing | Square, This screen, 4:5 … 16:9; Whole and Fill free; placing by hand with drag, pinch, double tap (ADR-0010) | Whole/Fill free, by hand Pro | Same |
| Sizes | 1024 px on the shorter side free, 2048 Pro | 2048 Pro | Same |
| Finish | "Adjust": brightness, contrast, saturation; quote moves with the mandala | Pro | Same |
| Saving pictures | To `Pictures/Dahlia` through MediaStore | Free (Pro features need Pro) | Different on iOS: added to the Photos library with add-only access (one permission text), without an own album (an album needs full library access, from memory) |
| SVG line drawing | Square SVG, colour on black or dark on white | Pro | Different on iOS: Photos does not take SVG, so it goes to the share sheet or "Save to Files" |
| Sharing | Share from the menu through the system sheet | Free (Pro features need Pro) | Same with `ShareLink` |
| Set as wallpaper | From the menu (Fill) or the save sheet (as framed): home, lock or both through `WallpaperManager` | Free | Not possible on iOS (no wallpaper API). Replacement: save the "This screen" picture to Photos with a one-screen guide to the system's wallpaper editor; the Shortcuts action below for the automatic way (F5) |
| Wallpaper pair | Home and lock as a pair with previews, home darker | Pro | Not possible as one tap. Replacement: both pictures saved together; the Shortcuts action takes a "Screen" parameter (lock or home, home darker), so one shortcut sets both (F5) |
| Daily wallpaper | Every morning from the ticked favourites, set by a JobScheduler job between 4 and 8 | Pro | Not possible as an app job. Replacement: App Intent "Mandala of the day" that a Shortcuts personal automation passes to "Set Wallpaper" (F5); whether it runs without a confirmation is unverified (section 7). Different: the turn is a function of the date (section 3.6) |
| Live wallpaper | Mandala turning on the graphics chip, breathing, colour drift, palettes in turn, layers turning on their own | Pro | Not possible on iOS (no live wallpaper API). Replacement open (F6) |
| Moving picture | Video tab: layers turning, 5, 10, 15 or 30 s, 720 or 1080 px, H.264 MP4, progress with minutes left, cancel (ADR-0010 point 10) | Watch free, save and share Pro | Same, written with AVFoundation (section 3.7); saved to Photos as a video |
| Widgets | "Mandala of the day", square shows the picture, wide adds the words; per widget: daily turn or one favourite, light, dark or like the app; tap opens the app and offers the widget's mandala; listed on the Dahlia page (ADR-0020) | Free; daily turn with Pro | Different on iOS: WidgetKit with small, medium (wide, with words) and large; settings through the system's "Edit Widget"; pictures rendered ahead by the app into an App Group (section 3.5). The Pro line is a question, ADR-0016 says Pro, ADR-0020 says free (F7) |
| Tour | Eight cards docked at the bottom, ring and line to the real control, rest dimmed; once on a new install, again from "Tour" | Free | Same, rebuilt in SwiftUI with anchors on the real controls; card texts that name Android places change (F9) |
| Guide | Opens `tearoom.one/<de or en>/apps/dahlia/help` | Free | Same link; the pages need iOS sections for wallpaper, widgets and Shortcuts (F9) |
| Menu and Dahlia page | Favourite, Share, Wallpaper, New mandala, Open file, Save settings, Daily wallpaper page with ticked favourites, Theme, More (fast preview, pictures in lists, line drawing); Dahlia page: Guide, Tour, About, Pro, Rate, Widgets | Free | Same structure; "Wallpaper" and "Daily wallpaper" become one page with saving and the Shortcuts setup; the widget list comes from WidgetKit's current configurations |
| About | Version, licence, source, section 7, privacy, open-source libraries | Free | Same; the library list changes (no Google libraries; Kotlin stdlib and kotlinx.serialization stay) |
| Appearance | Dark by default, light by choice, kept; does not follow the system | Free | Same |
| Fast preview | Menu switch, on by default: glow blurred smaller in the editor only (ADR-0004) | Free | Same |
| Ten languages | English, German, Spanish, French, Portuguese (Brazil), Italian, Russian, Japanese, Korean, Chinese (Simplified) from the device (ADR-0019) | Free | Same, from one string catalogue generated from `strings.xml` (section 3.9); iOS also offers the per-app language in Settings |
| Pro mark and Pro sheet | Mark on the picture when it uses Pro; sheet lists what, "Buy", "Remove them", try freely | — | Same (`ProFeatures.of` in core) |
| Pro purchase and restore | Play Billing through tearoom-android-kit: price, pending, refunds, restore, offline cache, checked on every resume (ADR-0022) | — | Different on iOS: StoreKit 2 (section 3.4); Ask to Buy as "pending"; Family Sharing is a question (F4) |
| Rating prompt | Google's review dialog after 10 days of use over at least 7, once per version, while calm; a permanent row on the Dahlia page | — | Different on iOS: the system's review request with the same rule (the system shows it at most three times a year, from memory); the row opens the App Store's "Write a Review" page |
| First-start mandala | A fresh install opens on the starter the app icon was made from | — | Same (`starter-favorites.json` shared with Android) |
| Messages with Undo | Snackbars with "Undo" and "Open" | — | Same, as Dahlia's own message part (iOS has no snackbar) |
| Free and fast builds | `free` counts as not owning Pro, `fast` and `debug` as Pro; marked icons | — | Debug build with its own bundle id, name and marked icon; Pro switchable in a debug setting (global rule for local builds) |

Nothing in the Android app has no answer at all on iOS: three features become replacements (set as wallpaper, daily wallpaper, live wallpaper), three are open questions (F5, F6, F7), everything else is the same or differs only in the system route.

### 3. Architecture on iOS

```
SwiftUI app (ios/Dahlia)          Widget extension            App Intents
  editor, sheets, StoreKit 2        reads pictures only         "Mandala of the day"
        │                                 │                         │ (runs in the app's process)
        ▼                                 ▼                         ▼
  Engine (Swift actor) ──── App Group container: pictures, widget index, Pro flag
        │
        ▼
  MandalaCore.xcframework (Kotlin core, release)  ◄── Core Text TextRenderer, ImageIO ImageLoader (Swift)
        │
  Parallel: fixed Kotlin/Native worker pool (iosMain)
```

#### 3.1 Bridge and the editing rules

- **Facade.** Ways: (a) grow `IosBridge` into a small Kotlin facade in `iosMain` with plain types (settings as JSON or Kotlin objects, RGBA as `NSData`, closures for progress and cancel); drawback: every new core call needs a line there. (b) Call core's generated Objective-C headers directly; drawback: Swift sees Kotlin default arguments, boxed numbers and sealed classes awkwardly, and core's API becomes the iOS API. **Pick (a):** one place for the iOS contract, which a test can cover.

- **The editing rules are shared code, not ported.** Today `Editing.kt`, `Randomize.kt`, `PlayField.kt` and `History.kt` live in the Android app, but they import nothing from Android (only one `@StringRes` in `Editing.kt`); the daily turn in `DailyWallpaper.kt` and the merge in `Favorites.kt` are plain logic too. Ways: (a) move them into a new Kotlin Multiplatform module next to `core` (for example `android/editing`), which the Android app uses unchanged and the iOS app links in the same XCFramework; drawback: a new folder in `android/` and a deviation from `android/AGENTS.md` ("editing rules in `Editing.kt`"), both need approval. (b) Put them into `core`; drawback: blurs core's role as the renderer (ADR-0002). (c) Port them to Swift; drawback: two copies that drift, the die and the play field would behave differently on the two phones, every Android fix has to be repeated. **Pick (a)**, built Android first (WP-3), so the Android app proves the move before iOS uses it.

- **The settings model in Swift.** Ways: (a) Swift keeps the core's `MandalaParameters` and changes it only through the shared editing functions; drawback: Kotlin objects in SwiftUI state need small wrappers. (b) Swift structs mirroring the JSON; drawback: a second model of every field that must follow ADR-0003. **Pick (a).**

#### 3.2 Threads, the preview pipeline and export

- **One engine actor** owns all render calls. Kotlin calls block, so the actor runs them on its own thread, never on the main thread; inside a render the worker-pool `Parallel` uses all cores (SPIKE-3 finding 2; Grand Central Dispatch is ruled out).

- **Preview: latest wins.** Ways: (a) every change raises a generation number; the running render checks it through core's `isCancelled` and throws `RenderCancelledException`; the last finished picture stays on screen; the layer cache (`LayerCache`) and recent pictures are reused as on Android; drawback: a cancelled render's work is lost. (b) Debounce only; drawback: on a heavy favourite (1–3.5 s, SPIKE-3) every outdated render still runs to the end. **Pick (a)**, as Android does with `collectLatest`.

- **Export with progress.** Core reports progress (`RenderProgress`); the sheet shows it as Android does. A picture takes up to about 18 s at 2048 on an iPhone 15 and about 22 s on an iPhone 13 (estimate). Ways for leaving the app during export: (a) a background task assertion (`beginBackgroundTask`), enough for a picture (about 30 s, from memory), plus iOS 26's continued processing task with a system progress for the video; drawback: the second API is new and unverified (section 7). (b) Cancel on leaving and start again on return; drawback: a lost video after minutes. **Pick (a)**, falling back to (b) for the video if the continued task does not work as hoped.

- **Throttling under sustained load.** The phone throttles (SPIKE-4: worst case 16 → 21 s over three rounds). The app watches the thermal state: from "serious" on, the editor preview uses the fast glow and fewer threads; exports keep their pixels and only take longer. Measured on the device in WP-6 before it is tuned.

- **Collector.** `gcMutatorsCooperate` is ignored by the Kotlin version in use (SPIKE-4) and goes from `android/core/build.gradle.kts`. A fixed collector target of 64 MB made the worst case 16–23 % faster in the simulator (SPIKE-4 finding 7); WP-1 measures it on the iPhone 15 and it is set at app start through the facade only if the device confirms it (approval A5).

#### 3.3 Storage, text and pictures

- **Files.** Ways: (a) the app's own files in its Application Support folder, the App Group container only for what the widget and the intent need (pictures, a small widget index with favourite names and ids, the Pro flag, the appearance); drawback: the app writes the index whenever favourites change. (b) Everything in the App Group container; drawback: the extension can reach (and lock) the user's whole data. **Pick (a).**

- **Formats as on Android:** `settings.json`, `history.json`, `favorites.json`, `palettes.json` and the save choice, written atomically, in the Mac's formats (ADR-0003), so "Open file" and "Save settings" stay interchangeable with Android and the Mac.

- **Text through Core Text:** a Swift class implements core's `TextRenderer`; it is called from worker threads, so it builds its own framesetter and bitmap context per call. Fonts map from the Mac names through one table.

- **Pictures through ImageIO:** a Swift `ImageLoader` decodes the background picture at the requested size with ImageIO's thumbnail call. The picked photo is copied like `BackgroundImages.kt` does (at most 2048 px), the `file:` URL in the settings is resolved by file name inside the app's picture folder.

#### 3.4 Pro with StoreKit 2

- Ways: (a) StoreKit 2 directly in the app, one non-consumable product, `Transaction.updates` and current entitlements, restore through `AppStore.sync`; drawback: Lazy Moves will need the same code later. (b) A shared Swift package like tearoom-android-kit; drawback: a package (approval) before a second iOS app exists. (c) RevenueCat or similar; drawback: their backend and data, against "no tracking". **Pick (a)**; extract a package when Lazy Moves' iPhone app needs it.

- Behaviour mirrors ADR-0022: price shown with reasons when missing, pending purchases (Ask to Buy) shown and blocking a second purchase, a refund or revocation removes Pro, an unreachable store never does. The app keeps its own Pro flag in the App Group for the widget and the intent.

- Local tests with a StoreKit configuration file in Xcode; sandbox tests with a sandbox account before review.

#### 3.5 Widgets

- **The extension never renders** (about 30 MB, SPIKE-3 finding 7). Ways: (a) the app renders the pictures ahead into the App Group, the widget only reads and downsamples them; drawback: what the app has not rendered yet, the widget cannot show (it falls back to the favourite's thumbnail, as Android does after a failed render). (b) The extension draws a small picture through core; drawback: does not fit in 30 MB. (c) The widget shows only the stored thumbnails; drawback: blurry at large size. **Pick (a).**

- **Families:** small and large show the picture (square, so one picture serves both), medium shows the picture without its text beside the words, as Android's wide widget. StandBy shows the small widget on its own (from memory). Lock screen widgets are left out: they are drawn tinted in one colour, which does not suit a mandala.

- **Settings per widget** through an App Intent configuration ("Edit Widget"): Mandala (daily turn or a favourite, the list read from the widget index) and Appearance (like the app, light, dark), as the Android amendment of ADR-0020.

- **"Daily turn" without a morning job.** Android advances a counter in its morning job. iOS has no job that runs at a set time, so the turn becomes a function of the date: the n-th ticked favourite on day n, counted from a fixed day, with the day starting at 4:00 as Android's window. App, widget and intent then agree without talking to each other. Whenever the app runs (opened, or in the background when iOS grants it a refresh), it renders the pictures of the coming days into the App Group and hands WidgetKit a timeline with one entry per day at 4:00. How many days ahead is F8.

- **Tap:** opens the app with the widget's favourite; the app offers it with "Open" as on Android.

#### 3.6 The Shortcuts route for the wallpaper

- An App Intent "Mandala of the day" in the app target (not in an extension, so it runs in the app's process with the app's memory, launched in the background). Parameter "Screen": lock or home (home darker, as the pair). It returns the picture of today's turn as an image file at the screen's pixel size, with Fill and the finish, as the daily wallpaper on Android.

- It returns the picture rendered ahead (section 3.5) and renders only when none is there, because the time an intent may run in the background is limited (about 30 s reported, unverified).

- Offered as an App Shortcut, so it appears in Shortcuts without setup. The automation ("every day at 6, get Mandala of the day, set wallpaper") is the user's step; the app's wallpaper page explains it with a ready shortcut from an iCloud link if F5 says so.

- Pro per ADR-0016 point 4; without Pro the intent answers with a short message that it comes with Pro.

#### 3.7 The moving picture through AVFoundation

- Core keeps drawing the frames (`LayerAnimation`, `LayerCache`) and converting them to I420 (`VideoFrames`). Ways: (a) the app repacks core's I420 into the two-plane layout that iOS's encoder takes, tags BT.709 limited range and writes H.264 MP4 with `AVAssetWriter`; drawback: a small repacking loop in Swift. (b) Hand RGBA frames to the encoder and let it convert; drawback: a different colour conversion from Android's. **Pick (a)**: same colours on both phones. Same lengths, sizes and limits as ADR-0010 point 10.

- Like `VideoExport.kt` on Android, the encoder and the file live in the app, the only file format the iOS app writes itself (approval A8).

#### 3.8 Live wallpaper

No iOS route for a moving wallpaper an app can set. The nearest is a Live Photo (a still picture with a short video), which iOS 17 and later can play on the lock screen when the phone wakes (from memory, unverified). October's decision was "no live photo"; F6 asks again because core now draws turning frames.

#### 3.9 Strings in ten languages

- Ways: (a) a script generates the iOS string catalogue (`Localizable.xcstrings`) from all `res/values*/strings_*.xml`, keeping Android's keys; `%1$s` becomes `%1$@`, plurals become catalogue plural variations; iOS-only texts (App Store instead of Google Play, Shortcuts, Photos) live in one Android-format file under `ios/`, so they go through the same script and the same translation round; the check fails when the catalogue is out of date; drawback: a translator edits Android files for iOS-only texts. (b) Keep a second catalogue by hand; drawback: ten languages maintained twice. (c) A neutral source file generating both platforms; drawback: changes the Android workflow for no Android gain. **Pick (a).** The script is plain Python in `ios/scripts/`, no package.

- Texts that name Android places ("Pictures", "Google Play", "home screen widget by long press") get iOS variants in the iOS file under the same key, which the script prefers.

#### 3.10 Design tokens

- Ways: (a) generate `Tokens.swift` from `Tokens.kt` (colours, spacing, radii, sizes; dp become points one to one), checked like the strings; drawback: a small parser for a simple file. (b) Copy by hand with a test comparing both; drawback: a test that needs the same parser anyway. (c) One neutral source generating both; drawback: touches the Android build. **Pick (a).**

- Text roles: Android takes Material's type scale; iOS takes six roles mapped onto Dynamic Type styles (SF Pro, so large system text works), defined once in `Tokens.swift` (design-baseline: five to seven fixed roles, neighbours at least 1.2 apart). These are new tokens and need approval (A9).

- Screens use only the tokens; a check in `ios/scripts/check` rejects colour and number literals in screen files, as `ArchitectureTest` does on Android. Navigation, sheets, pickers and menus are the iOS ones (system sheets with detents), so the app looks like an iOS app while the surfaces and the amber accent stay Dahlia's.

#### 3.11 Build and checks

- **Framework:** only the release XCFramework (SPIKE-3: debug is 5–8 times slower). A pre-build script phase runs the Gradle task (as `ios/SpikeBench/build-core.sh` does); Gradle skips it when core is unchanged. The output stays in `ios/build/`, ignored by Git. Swift code builds Debug or Release as usual.

- **Project file:** ways: (a) XcodeGen from `ios/project.yml`, the `.xcodeproj` not committed, signing team in a local ignored xcconfig with a committed example (as Lazy Moves IOS-2); drawback: XcodeGen must be installed (it is, and SpikeBench uses it). (b) A committed Xcode project with synchronised folders; drawback: targets, App Group entitlements and settings live in a file agents edit badly and that conflicts in merges. **Pick (a)** (approval A6, a tool, not a package). SpikeBench keeps its committed project until it is removed.

- **Builds:** Debug as `<bundle id>.debug` with its own name, a marked icon and marked widget pictures, a debug switch for the widget mark and for Pro; Release with the final id.

- **Git hook:** staged files under `ios/` run `ios/scripts/check` (generate the project, build and run the unit tests on a simulator, strings and tokens up to date, token literals). Staged files under `android/core/` additionally compile core for the iOS simulator (compile only, no framework link), so a `java.*` slip shows at once.

- **Parity on iOS:** `ios/scripts/check-full` builds the release framework and runs an XCTest that renders every fixture of `android/fixtures/settings/` at 512 and the three SPIKE-3 cases at 1024 in the simulator, compared with the JVM renders of the cli (pixel hashes as `RenderHashes.kt`, PSNR ≥ 90 dB where floats round differently). The reference for iOS is the JVM, not the Mac (ADR-0004 stays the Android contract against the Mac). Text fixtures get their own threshold, since Core Text draws different masks.

### 4. Build order

Work packages for one session each, in this order. "Mathis" lists what only he can do. Sizes are rough session counts.

**WP-1 · First build on the iPhone 15** (Opus; 2–3 sessions)

- Contains: `ios/Dahlia` with XcodeGen, Debug build on the iPhone 15; the facade (render with options, progress, cancel, layer cache; RGBA to `CGImage`); the engine actor with the latest-wins preview; the editor with the picture and the Layers tab (style, palette, the main sliders); the 21 starters and the first-start mandala; favourites sheet (open, add); settings kept across restarts; saving the square picture at 1024 to Photos. Measures the fixed collector target on the device and the editor's feel on a heavy favourite. Removes `gcMutatorsCooperate`.

- Depends on: F1 (bundle id) and F2 (minimum iOS); approvals A1, A6.

- Done: Mathis opens the app on his iPhone 15, changes a starter's style and palette, keeps it as a favourite, saves it to Photos, and it is still there after a restart. A short report with the collector measurement.

- Mathis: answer F1 and F2; trust the developer profile on the phone once; try it.

**WP-2 · iOS checks and parity** (Sonnet; 1 session)

- Contains: `ios/scripts/check`, `check-full`, the hook lines, the parity test against the JVM, a note in `AGENTS.md`.

- Depends on: WP-1.

- Done: the hook runs for `ios/` and `android/core/`; `check-full` green with the parity numbers in a short QA note.

**WP-3 · Shared editing logic, Android first** (Opus; 1–2 sessions)

- Contains: the editing rules, the die, the play field, the history, the daily turn and the favourites merge move into the shared module (approval A7); `@StringRes` becomes a key the app maps; Android tests move with them; the Android app behaves the same.

- Depends on: A7. Independent of WP-2.

- Done: Android `check-full` green, a fast build tried on the phone (die, play field, undo), the module in the XCFramework.

**WP-4 · Strings and tokens** (Sonnet; 1 session)

- Contains: the string catalogue script with the iOS-only file, `Tokens.swift` generator, text roles, the literal check.

- Depends on: WP-1; A9 for the text roles.

- Done: the app shows German, Japanese and Russian on the simulator from the generated catalogue; the check fails on a stale catalogue.

**WP-5 · Editor: layers, scene, palettes** (Sonnet with an Opus review; 2 sessions)

- Contains: all of the Layers and Scene tabs, own palettes with the in-place editor, background picture, effects with the hold-to-compare, pictures in the lists, the fast preview switch.

- Depends on: WP-3, WP-4.

- Done: every control of the two tabs works and matches Android on the same favourite; screenshots light and dark, German.

**WP-6 · Editor: emblem, text, draw, moving on the picture** (Opus; 2 sessions)

- Contains: Emblem and Text tabs with the Core Text renderer and the quote collection with language chips, Draw tab, moving layers, emblem and text on the picture with snapping, the thermal-state handling.

- Depends on: WP-5.

- Done: a mandala with an emblem and a quote matches its Android render apart from text masks; moving and snapping feel right on the phone.

- Mathis: try moving and pinching on the device.

**WP-7 · Editor: favourites, history, menu, die, play field** (Sonnet; 1–2 sessions)

- Contains: favourites with multi-select and Undo, back and forward, the menu with "New mandala" and its comparison, open and save files, play field, messages with Undo, appearance, the last-picture stand-in.

- Depends on: WP-3, WP-5.

- Done: a Mac favourites file opens; die, play field and history work as on Android.

**WP-8 · Save sheet and export** (Opus; 2 sessions)

- Contains: formats, Whole and Fill, framing by hand, finish, sizes, Pro mark and sheet logic (without the purchase), export in the background with progress, Photos, share, SVG to Files, the moving picture with AVFoundation.

- Depends on: WP-6.

- Done: every format saved on the phone; a 30 s video at 1080 plays in Photos and in a messenger with the same colours as Android's.

- Mathis: allow Photos access once; look at a video on the phone.

**WP-9 · Pro purchase** (Opus; 1–2 sessions)

- Contains: StoreKit 2, StoreKit configuration file, Pro sheet with price, restore, pending, refunds, the App Group flag.

- Depends on: WP-8; F4; the app record and the in-app purchase in App Store Connect.

- Done: purchase, restore, Ask to Buy and refund tested locally with the configuration file and in the sandbox.

- Mathis: accept the Paid Apps agreement, enter tax and banking details, consider the App Store Small Business Program (15 % instead of 30 %), create the app record and the product `pro` in App Store Connect (an agent can prepare the texts), create a sandbox tester.

**WP-10 · Widgets** (Opus; 2 sessions)

- Contains: App Group, widget extension, configuration intent, pictures rendered ahead, timelines, tap, the widget list on the Dahlia page, marked debug pictures.

- Depends on: WP-9 (Pro line in the widget), F7, F8; approvals A2, A10.

- Done: three widgets with different settings on the phone show the right pictures, turn at 4:00 without opening the app (checked over two mornings), never stay empty while favourites exist.

- Mathis: place widgets on his home screen and look at them over two days.

**WP-11 · Wallpaper and the Shortcuts action** (Opus; 1–2 sessions)

- Contains: the wallpaper page (save for wallpaper, guide), the "Mandala of the day" intent with the "Screen" parameter, the App Shortcut, the device check of the automation; the Live Photo if F6 says so.

- Depends on: WP-10 (pictures ahead), F5, F6.

- Done: on the iPhone 15 an automation set at a fixed time changes lock and home screen without a confirmation, or the plan says what the user has to tap.

- Mathis: build the shortcut once on his phone and share its iCloud link if F5 (a) is chosen.

**WP-12 · Tour, guide, About, rating, languages** (Sonnet; 1–2 sessions)

- Contains: tour with anchors, About with the iOS library list, the rating rule, the guide link and the iOS sections for the website, a pass through all ten languages (long German and Russian texts at the smallest iPhone width).

- Depends on: WP-7, F9.

- Done: tour on a fresh install, About complete, a `ui-check` style pass in the simulator.

**WP-13 · App Store** (Opus for review notes, Sonnet for texts; 2 sessions)

- Contains: privacy label ("Data Not Collected"), export compliance (no encryption), age rating, listing texts in the languages of F10, captioned screenshots (6.9" iPhone, and iPad only if F3 says so), App Review notes (how to reach Pro features, the Shortcuts action), TestFlight per F11, the iOS routine written into the `app-release` skill, ADR for the iOS widget and wallpaper routes.

- Depends on: everything above.

- Done: a build in review.

- Mathis: approve screenshots and texts, submit for review, answer App Review.

About **19 to 25 sessions**, a little more than October's 3–4 weeks, because the first version now includes the moving picture, widgets with settings, own palettes, the play field, the tour and ten languages.

### 5. Approvals needed

Approved: core as a Kotlin Multiplatform module and the root folder `ios/` (11 November 2026); A1–A11 below (12 November 2026, Mathis, with the values of section 6; A5 only if WP-1's device measurement confirms it), recorded under "Approved" in `docs/APPROVALS.md`.

- **A1 Bundle id** · final, cannot change after the first upload; debug build `<id>.debug` · see F1.

- **A2 App Group** `group.<bundle id>` (for example `group.one.tearoom.mandala`) · shared container for widget pictures, widget index, Pro flag · alternative: none, widgets cannot work without one.

- **A3 Minimum iOS version** · see F2.

- **A4 Pro product in App Store Connect** · non-consumable `pro` (same id as on Play), 2.99 € (ADR-0016), Family Sharing per F4. *Created 12 November 2026:* product id `one.tearoom.mandala.pro` (not `pro`), 3.99 € (ADR-0016 as amended), Family Sharing on; see [STORE-2](store/STORE-2-app-store.md).

- **A5 Collector setting** · a fixed collector target (64 MB) set at app start, only if WP-1 confirms on the iPhone 15 what the simulator showed (16–23 % faster in the worst case); removing the ignored `gcMutatorsCooperate` from `android/core/build.gradle.kts` · alternative: the default autotuned collector.

- **A6 XcodeGen** as the project generator for `ios/` (a build tool, not a Swift package), `.xcodeproj` not committed · alternative: a committed Xcode project.

- **A7 Shared editing module** `android/editing` (Kotlin Multiplatform, JVM and iOS), new folder in `android/` and a deviation from `android/AGENTS.md` (editing rules move out of the app) · alternative: port the rules to Swift.

- **A8 MP4 written by the iOS app** through AVFoundation, the same exception as `VideoExport.kt` on Android (ADR-0010 point 10).

- **A9 iOS text roles** as new design tokens in `Tokens.swift`, mapped onto Dynamic Type · alternative: system text styles without own roles.

- **A10 Permissions and capabilities:** Photos add-only access (usage text), App Groups, background app refresh for rendering widget pictures ahead · alternative: widgets update only when the app is opened.

- **A11 Deviations from ADRs**, each recorded when built: ADR-0004 (iOS parity against the JVM, not the Mac); ADR-0010 point 10 and ADR-0014 (no "Set as wallpaper", daily and live wallpaper on iOS; replaced per F5 and F6); ADR-0016 point 4 if F7 follows Android's widget line; ADR-0020 (Android only) gets an iOS counterpart; the daily turn by date instead of a counter on iOS.

- **Swift packages:** none requested. StoreKit, WidgetKit, App Intents, AVFoundation, PhotoKit, Core Text and ImageIO are system frameworks; a package that turns out to be needed comes here first.

### 6. Questions for Mathis

The app's name is decided (ADR-0012, "Dahlia – Mandala Maker", 22 characters, fits the App Store's 30); the price is decided (ADR-0016, 2.99 €); the free and Pro line is decided (ADR-0016). Each question below carries Mathis' answer of 12 November 2026 at its end; the options stay as they were asked.

**F1 Bundle id.** (a) `one.tearoom.mandala`, the same as the Android package; the `app-release` skill asks for the same naming on both platforms; drawback: the id says "mandala", not "dahlia" (nobody sees it). (b) `one.tearoom.dahlia` as October proposed and the spike used; drawback: a second id for the same app across the tooling. (c) Something new. Recommendation: (a). **Decided 12 November 2026 (Mathis): (a) `one.tearoom.mandala`.**

**F2 Minimum iOS.** iOS 26 and 27 run on the iPhone 11 and later and the iPhone SE (2nd and 3rd generation) (MacRumors, 14 September 2026); iOS 18 also on the XS and XR. In June 2026 Apple counted iOS 26 on 79 % of all active iPhones and 86 % of those from the last four years. (a) iOS 26: one design generation (Liquid Glass) and two system versions to test, devices from the iPhone 11 on; leaves out about 10–15 % of active iPhones today (estimate: those on iOS 18 or older), fewer by the release. (b) iOS 18 as Lazy Moves proposes: reaches about 5 % more (estimate), but two design generations and an iOS version Mathis has no phone for, and the XS and XR are the slowest phones (the worst case about twice the iPhone 15's time, estimate). (c) iOS 27: newest APIs only; leaves out perhaps a third today (estimate). Recommendation: (a). **Decided 12 November 2026 (Mathis): (a) iOS 26.**

**F3 iPad.** (a) iPhone only; on the iPad it runs as an iPhone app (October's decision); drawback: small window on the iPad. (b) Universal with two panes like the Android tablet layout; drawback: iPad screenshots in every listing language, window sizes and multitasking to test. (c) iPhone first, iPad in a later version. Recommendation: (a), with (c) as the path if iPad users ask. **Decided 12 November 2026 (Mathis): (a) iPhone only.**

**F4 Family Sharing for Pro.** (a) On: a purchase covers up to six family members; drawback: fewer sales from families, and once on it cannot be switched off (from memory). (b) Off: drawback: families buy several times, a common complaint in reviews; Android has no equivalent either way. Recommendation: (a), as October planned. **Decided 12 November 2026 (Mathis): (a) Family Sharing on.**

**F5 Wallpaper on iOS** (set as wallpaper, pair, daily). (a) Save the screen-sized picture (or both pictures of the pair) to Photos with a short guide, plus the Pro Shortcuts action "Mandala of the day" and a ready shortcut from an iCloud link on the wallpaper page; drawback: the user still has to add the automation once, and Mathis keeps the shared shortcut up to date. (b) The same without a ready shortcut, only a step-by-step guide; drawback: more people give up. (c) Only saving to Photos, no Shortcuts action; drawback: no daily wallpaper on iOS, a Pro feature less than on Android. Recommendation: (a), if the device check (section 7) shows the automation runs without a confirmation; otherwise (a) with the guide saying what to tap. **Decided 12 November 2026 (Mathis): (a) Photos with a guide, the Shortcuts action and a ready shortcut.**

**F6 What replaces the live wallpaper.** (a) Nothing in the first version; the moving picture is the moving output; drawback: Pro on iOS has one wallpaper feature less. (b) "Live Photo" as a third choice in the save sheet's Video tab, Pro, made from the turning frames, for the lock screen; drawback: only a few seconds of motion when the phone wakes, and whether it looks good is unverified. (c) (a) now, (b) after a device check in WP-11. Recommendation: (c). **Decided 12 November 2026 (Mathis): (c) nothing now, the Live Photo after a device check in WP-11.**

**F7 Widgets: free or Pro.** ADR-0016 point 4 makes the iPhone widgets Pro; ADR-0020 (later, Android only) made Android's widget free, with the daily turn as Pro. (a) As on Android: free, staying on the first ticked favourite or one fixed favourite; the daily turn with Pro; drawback: amends ADR-0016 point 4. (b) Pro as ADR-0016 says; drawback: the two apps differ, against ADR-0016 point 1, and the widget no longer advertises the app on free phones. Recommendation: (a). **Decided 12 November 2026 (Mathis): (a) widgets free as on Android, the daily turn Pro (ADR-0016 point 4 amended).**

**F8 How far ahead widget pictures are made.** The extension cannot render, so it shows what the app made beforehand. (a) Seven days ahead whenever the app runs, plus background refreshes when iOS grants them; after seven days without the app the widget stays on the last day's picture; drawback: about 5–15 MB of pictures. (b) Thirty days; drawback: more rendering after each change of favourites (a few minutes in the background). (c) Only today; drawback: the daily turn stops whenever the app is not opened. Recommendation: (a). **Decided 12 November 2026 (Mathis): (a) seven days ahead.**

**F9 Tour and guide.** (a) The same eight cards, rebuilt for the iOS controls, with iOS wording where Android places are named; the website guide gets iOS sections for wallpaper, widgets and Shortcuts; drawback: guide pages to write in German and English. (b) The tour only, the guide stays as it is; drawback: wallpaper and widget pages describe Android. (c) No tour in the first version. Recommendation: (a). **Decided 12 November 2026 (Mathis): (a) the same eight cards with iOS wording, iOS sections in the guide.**

**F10 App Store listing languages.** (a) All ten app languages from the start, from the Play listing texts adapted to iOS; drawback: Japanese, Korean and Chinese are machine quality (ADR-0019), and ten sets of screenshot captions. (b) English and German first, the rest with the next version; drawback: a German-only listing for a ten-language app hides it in other stores. (c) English only. Recommendation: (a), with the same caveat as on Play. **Decided 12 November 2026 (Mathis): (a) all ten languages.**

**F11 TestFlight.** (a) Internal TestFlight for Mathis' own phone from WP-9 on (builds from App Store Connect, the purchase in the sandbox as in the store), no external testers; drawback: an upload per test build. (b) External TestFlight with a public link before launch; drawback: Apple's beta review and support for testers. (c) No TestFlight, Xcode builds until review; drawback: the store path (signing, purchase, widgets from an archived build) is first seen in review. Recommendation: (a). **Decided 12 November 2026 (Mathis): (a) internal TestFlight.**

**F12 Editor actions on the iPhone** (raised by WP-1, [IOS-1](IOS-1-first-build.md)). (a) In the navigation bar, in Android's order. **Decided 12 November 2026 (Mathis): (a).**

**F13 Control tabs.** (a) A fixed row of five equal text tabs without sideways scrolling. **Decided 12 November 2026 (Mathis): (a).**

**F14 Save.** (a) Save opens a small sheet that grows into the full save sheet in WP-8. **Decided 12 November 2026 (Mathis): (a).**

### 7. Still to verify

Checked on 12 November 2026 from Apple's pages or reports of them: the iOS 27 device list (iPhone 11 and later, SE 2nd and 3rd generation; MacRumors and others reporting Apple's list); iOS 26 shares of June 2026 (Apple's App Store page as reported by 9to5Mac and MacRumors); the widget extension's memory limit of about 30 MB (Apple engineers on the developer forums, not in the documentation). Everything else below is from memory or forum reports and is checked where named.

- **"Set Wallpaper" in a personal automation without a confirmation** on iOS 26 and 27: forum reports conflict, nothing official found. Check in WP-11 on the iPhone 15: a time-of-day automation with "Run Immediately", the intent's picture, lock and home separately, locked and unlocked phone, and how the lock screen's depth effect treats a mandala.

- **Background time and memory of an App Intent** (about 30 s reported, no memory figure found): measure the intent rendering the worst case at screen size in the background in WP-11; the design avoids depending on it by returning pictures rendered ahead.

- **Widget memory** (30 MB, forums): load a 1024 px picture with ImageIO downsampling in the extension and watch the footprint in WP-10.

- **Widget refresh budget** (Apple's page did not load; from memory a few dozen reloads a day, reloads from the running app not counted): the design needs none during the day; check that a timeline entry at 4:00 shows without a reload over two mornings.

- **Reading widget configurations from the app** (`WidgetCenter` current configurations with their intents, from memory iOS 17 and later): check in WP-10, needed for the Dahlia page's widget list and for rendering fixed favourites ahead.

- **StoreKit 2:** entitlements available offline, Ask to Buy as pending, refund through `revocationDate`, Family Sharing irreversible once on, a restore button required by App Review (guideline 3.1.1, from memory). Check with the StoreKit configuration file and a sandbox tester in WP-9, and in the current App Review Guidelines.

- **Continued processing task for long exports** (new in iOS 26, from memory): check in WP-8 whether a 30 s video export continues with the app in the background; else the export stays in the foreground with Cancel.

- **Photos add-only access cannot create an album** (from memory): checked in WP-1 ([IOS-1](IOS-1-first-build.md) finding 8): creating an album makes iOS ask for full library access, so no album.

- **Mac fonts on iOS** (Zapfino, Didot, Papyrus …, from memory): list the installed families in the simulator in WP-6.

- **Live Photo on the lock screen** (iOS 17 and later plays it on wake, from memory): check in WP-11 if F6 is (b) or (c).

- **Collector target on the device** (SPIKE-4 measured only the simulator): measured in WP-1 (IOS-1 finding 5): 64 MB is 3–8 % slower on the iPhone 15 with about 40 % less peak memory; the default stays.

- **`gcMutatorsCooperate` ignored** (warning in the SPIKE-4 build): confirmed with Kotlin 2.4.20 and removed in WP-1 (IOS-1 finding 7).

- **The container path changes between installs** (from memory): the background picture is resolved by file name anyway (section 3.3).

- **iPhone 13 and iPhone 11 speed** (SPIKE-3 estimated 1.2 times the iPhone 15 for the 13; the 11 is not estimated): measure on any older iPhone or ask a tester before release.

### 8. Not planned

iCloud sync, Apple Watch, lock screen widgets, Control Center controls, the Mac app in the App Store and one purchase across Mac and iPhone (decided later). New features during the iPhone work go to Android first (`AGENTS.md`); the iPhone app follows from the Android state it was built against, and later Android features queue for iOS.

### 9. Decided in this revision

- The inventory is taken from the Android sources and strings of 12 November 2026 (1.8.8), not from the Play listing.

- Work packages are called WP-1 … WP-13 so they do not collide with the questions (F) and approvals (A).

- The facade, the shared editing module, the latest-wins preview, the date-based daily turn, the App Group only for widget data, StoreKit 2 without a package, XcodeGen without a committed project, the generated strings and tokens, and parity against the JVM are this plan's picks; each is marked as a pick in section 3 and the ones that need approval are in section 5.

- Lock screen widgets are left out (one-colour tinting does not suit a mandala); saving to Photos without an own album; the iOS app keeps Android's dark-by-default appearance that does not follow the system.

- October's sections 5 to 9 and questions 1 to 6 are folded into this version; questions 2 and 3 were already replaced by PLAN-5, question 1 (path) by Mathis' decision after SPIKE-3, question 6 (order) by section 4.
