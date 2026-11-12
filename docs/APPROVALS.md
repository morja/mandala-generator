# Approvals

What needs approval: new packages, new design tokens, new folders in the root directory, deviations from an ADR. Agents add open requests here instead of building a workaround.

## Open

- **Core Image's random tile as a data file** · `CIRandomGenerator` turns out to be one 256 x 256 tile of 8-bit RGBA values repeated without end (measured 19 October 2026). Shipping that tile (256 KB, read once from Core Image on a Mac) would make the grain of the effects layer, the grain background and the heavy painted effect pixel-identical to the Mac; today they have the same strength but another pattern (Mac favourites with grain: 30–40 dB instead of about 45). Open point: the table is output of Apple's framework, so whether it may be shipped is your call · Alternative without approval: keep the own hash grain (same look, different pattern) · Recommendation: only if exact grain matters to you; visually the difference is not noticeable.
- **Bundled fonts for the text layer** · Android has none of the macOS fonts (Zapfino, Didot, Papyrus …), so they map to the closest system family. Open-licence look-alikes (for example from Google Fonts, OFL) could be shipped in the app · Alternative without approval: system families (current) · Recommendation: decide after looking at quotes on the phone.

iPhone app (PLAN-4 revision of 12 November 2026, section 5; IDs A1–A11 as in the plan):

- **A1 Bundle id of the iPhone app** · Final, cannot change after the first upload; the debug build gets `<id>.debug` with its own name and marked icon · Alternative without approval: none, WP-1 needs it · Recommendation: `one.tearoom.mandala`, the same as the Android package (PLAN-4 F1).
- **A2 App Group** `group.<bundle id>` (e.g. `group.one.tearoom.mandala`) · Shared container for the widget pictures, the widget index, the Pro flag and the appearance; the widget extension cannot render (SPIKE-3 finding 7) · Alternative without approval: none; no widgets without it · Recommendation: approve with A1.
- **A3 Minimum iOS version** · iOS 26 runs on the iPhone 11 and later and the SE 2/3; one design generation and two system versions to test; leaves out about 10–15 % of active iPhones today (estimate) · Alternative: iOS 18 (about 5 % more reach, estimate; two design generations, XS/XR the slowest phones) · Recommendation: iOS 26 (PLAN-4 F2).
- **A4 Pro product in App Store Connect** · Non-consumable `pro` (same id as on Play), 2.99 € (ADR-0016), Family Sharing per PLAN-4 F4 · Alternative without approval: none · Recommendation: approve as described with Family Sharing on.
- **A5 Collector setting of the iPhone app** · A fixed Kotlin/Native collector target (64 MB) set at app start through the iOS facade, only if WP-1 confirms on the iPhone 15 what the simulator showed (worst case 16–23 % faster, SPIKE-4 finding 7); remove the ignored `gcMutatorsCooperate` from `android/core/build.gradle.kts` · Alternative without approval: the default autotuned collector · Recommendation: measure, then set it if the device confirms.
- **A6 XcodeGen for `ios/`** · The project is generated from `ios/project.yml` (already installed, used by SpikeBench and planned for Lazy Moves); the `.xcodeproj` is not committed, the signing team sits in a local ignored xcconfig · Alternative without approval: a committed Xcode project · Recommendation: XcodeGen (a build tool, not a Swift package).
- **A7 Shared editing module `android/editing`** · Kotlin Multiplatform (JVM and iOS) for the platform-free rules now in the Android app (`Editing.kt`, `Randomize.kt`, `PlayField.kt`, `History.kt`, the daily turn, the favourites merge), so the die, the play field and the history behave the same on both phones; new folder in `android/` and a deviation from `android/AGENTS.md` ("editing rules in `Editing.kt`") · Alternative without approval: port the rules to Swift and keep two copies in step · Recommendation: approve; built Android first (PLAN-4 WP-3).
- **A8 MP4 written by the iOS app** · The moving picture through AVFoundation (`AVAssetWriter`, H.264, BT.709, core's I420 frames repacked), the same exception as `VideoExport.kt` on Android (ADR-0010 point 10, `android/AGENTS.md`) · Alternative without approval: no moving picture on iOS · Recommendation: approve.
- **A9 iOS text roles as design tokens** · Six text roles in `Tokens.swift`, mapped onto Dynamic Type styles (SF Pro); colours, spacing and radii are generated from `Tokens.kt` and need no approval · Alternative without approval: system text styles used directly in screens · Recommendation: approve, roles listed in WP-4 before use.
- **A10 iOS permissions and capabilities** · Photos add-only access (one usage text, for saving pictures and videos), App Groups, background app refresh to render widget pictures ahead · Alternative without approval: widgets update only when the app is opened · Recommendation: approve.
- **A11 Deviations from ADRs for the iPhone app** · ADR-0004: iOS parity is tested against the JVM renders, not the Mac; ADR-0010 point 10 and ADR-0014: no "Set as wallpaper", daily or live wallpaper on iOS, replaced per PLAN-4 F5 and F6; ADR-0016 point 4: iPhone widgets free with the daily turn as Pro, if F7 follows Android (ADR-0020); the daily turn by date instead of a counter on iOS · Alternative without approval: none that keeps the plan · Recommendation: approve together with the answers to F5–F7; each is recorded in an ADR when built.
- **Swift packages for the iPhone app** · None requested; StoreKit, WidgetKit, App Intents, AVFoundation, PhotoKit, Core Text and ImageIO are system frameworks · Alternative: — · Recommendation: nothing to approve now; a package that turns out to be needed comes here first.

Format: `- **What** · What for and why · Alternative without approval · Recommendation`

## Approved

11 November 2026 (Mathis, for the iPhone app, PLAN-4 and ADR-0015): `core` as a Kotlin Multiplatform module with JVM and iOS targets (Kotlin Multiplatform Gradle plugin 2.4.20, deviation from ADR-0002) and the new root folder `ios/` for the SwiftUI app; no new Swift packages. ADR-0015 itself stays proposed until the speed spike (SPIKE-3).

6 November 2026 (Mathis, after the review REV-1 in the Cockpit): the packages of the proof of concept (Kotlin Gradle plugin 2.4.20, kotlinx-serialization-json 1.11.0, kotlin-test with JUnit 5), ADR-0003 and ADR-0004, ADR-0005 and ADR-0006; all in production since Android 1.4.

6 November 2026 (resolved as recommended): the public build with tearoom-android-kit (ADR-0022); the kit is on GitHub (github.com/morja/tearoom-android-kit) under Apache-2.0, so the GPL repository builds from a fresh clone again.

26 October 2026 (Mathis, DM-16 "as recommended"): animation export on Android as a Pro "Moving" mode in the save sheet, layers turning against each other with kept layer images, MP4 only for now; the app writes MP4 itself with `MediaCodec` and `MediaMuxer` (an exception to "the app holds no file-format logic"), no new package. ADR-0010 point 10, ADR-0016 point 4.

22 October 2026 (Mathis, PLAN-5 question by question): ADR-0016, one line between free and Pro for Android and iPhone (two layers, effects free, emblems, text and positions Pro, five favourites, try freely and keep with Pro), 2.99 € on both; Android 1.4 is the first public version.

22 October 2026 (Mathis, PLAN-3): ADR-0014, more for Pro (finish, wallpaper pair, daily and live wallpaper); the permission `RECEIVE_BOOT_COMPLETED` and a `WallpaperService` with `BIND_WALLPAPER` in the manifest.

22 October 2026 (Mathis, after seeing a hard edge on his lock screen): "Show whole" and "Fill" free in every format, wallpapers from the menu filled, Fill zoom measured per mandala (ADR-0010, points 4 and 10); layer buffers as large as each style reaches (ADR-0010).

22 October 2026 (Mathis, after trying them on the Mac): palettes Silver, Earth and Rose Quartz appended to the built-in palettes in both apps (indices 18–20) and Cosmic offered again, 15 in all, in rows of three by family (TUNE-1). Custom Mac palettes move three places on; accepted, since the Mac app has had three release downloads.

21 October 2026 (Mathis, after QA-2): the app's preview may blur its glow at a smaller width than the macOS renderer, as the switch "Fast preview" in the menu; saved images keep the full glow (ADR-0004).

20 October 2026 (Mathis, RES-2 "everything as recommended"): layer and emblem positions in the settings, Mac first, editing them is Pro (ADR-0011).

20 October 2026 (Mathis, after the first portrait renders): centred backgrounds (auto, radial gradient, sunburst) follow the mandala's centre, the auto background also its size (ADR-0010, point 6).

20 October 2026 (Mathis, after SPIKE-1): strips in every render step that works on a full-size image; Pro 2048 px wide in every format; framing by hand only with Pro; no 4096 square for now (ADR-0010, PLAN-2).

20 October 2026 (Mathis, PLAN-2 "everything as recommended"): ADR-0010, portrait export with framing; the Android permission `SET_WALLPAPER` for "Set as wallpaper".

20 October 2026 (Mathis approved PLAN-1): one public repository for both apps (ADR-0009); root folders `mac`, `android`, `docs`, `.github`, `.githooks`; ADR-0004's path to the Mac renderer becomes `../mac`.

20 October 2026 (Mathis decided on a Pro unlock, ADR-0007): Google Play Billing Library for the one-time purchase.

19 October 2026 (Mathis asked for the Compose app shell): root folder `app`; Android Gradle plugin 9.4.1, Kotlin Compose plugin 2.4.20, Compose BOM 2026.09.00 (ui, material3), activity-compose 1.13.0, core-ktx 1.19.1, lifecycle-runtime-compose and lifecycle-viewmodel-compose 2.11.0, JUnit 4.13.2 for app unit tests.

19 October 2026: root folders `core`, `cli`, `docs`, `fixtures`, `scripts`, `tools`, `gradle`, `.githooks` (project setup).
