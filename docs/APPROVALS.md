# Approvals

What needs approval: new packages, new design tokens, new folders in the root directory, deviations from an ADR. Agents add open requests here instead of building a workaround.

## Open

- **Core Image's random tile as a data file** · `CIRandomGenerator` turns out to be one 256 x 256 tile of 8-bit RGBA values repeated without end (measured 19 October 2026). Shipping that tile (256 KB, read once from Core Image on a Mac) would make the grain of the effects layer, the grain background and the heavy painted effect pixel-identical to the Mac; today they have the same strength but another pattern (Mac favourites with grain: 30–40 dB instead of about 45). Open point: the table is output of Apple's framework, so whether it may be shipped is your call · Alternative without approval: keep the own hash grain (same look, different pattern) · Recommendation: only if exact grain matters to you; visually the difference is not noticeable.
- **Bundled fonts for the text layer** · Android has none of the macOS fonts (Zapfino, Didot, Papyrus …), so they map to the closest system family. Open-licence look-alikes (for example from Google Fonts, OFL) could be shipped in the app · Alternative without approval: system families (current) · Recommendation: decide after looking at quotes on the phone.

Format: `- **What** · What for and why · Alternative without approval · Recommendation`

## Approved

13 November 2026 (Mathis, "passt, bauen" to the revised mock `docs/mocks/DM-24-text-colour.html`, variant A2): the text's two colours in the Text tab (Text and Glow dots, Text|Glow switch, picture swatches plus white, Stand out, More colours sheet with field, hue strip and before/after, folded Fine tune) and the new settings fields `textLayer.glowSameAsText`, `glowHue`, `glowSaturation`, `glowBrightness` (ADR-0003). No new design tokens: the sheet's scrim is Material's `BottomSheetDefaults.ScrimColor`, the caption over the before/after crops uses `canvas` and the dark `text` token. Built Android first (QA-12); the Mac follows later and meanwhile draws the glow in the text colour.

12 November 2026 (Mathis, "Alles wie empfohlen" to PLAN-4's questions and approvals; ADR-0015 accepted after SPIKE-3 and SPIKE-4): the iPhone app's approvals A1–A11 as PLAN-4 section 5 words them, with these values:

- **A1 Bundle id** `one.tearoom.mandala`, the same as the Android package (F1 a); debug build `one.tearoom.mandala.debug` with its own name and marked icon.

- **A2 App Group** `group.one.tearoom.mandala` for widget pictures, widget index, Pro flag and appearance.

- **A3 Minimum iOS** iOS 26 (F2 a); iPhone only, on the iPad as an iPhone app (F3 a).

- **A4 Pro product** non-consumable `pro`, 2.99 € (ADR-0016), Family Sharing on (F4 a). Created 12 November 2026 as `one.tearoom.mandala.pro` at 3.99 € (Mathis, ADR-0016 amended; `docs/store/STORE-2-app-store.md`).

- **A5 Collector setting** a fixed collector target of 64 MB set at app start through the facade, **only if WP-1's measurement on the iPhone 15 confirms** the simulator's gain; otherwise the default autotuned collector. Removing the ignored `gcMutatorsCooperate` from `android/core/build.gradle.kts` is approved either way. *Measured 12 November 2026 ([IOS-1](IOS-1-first-build.md) finding 5): not confirmed (64 MB 3–8 % slower on the iPhone 15, about 40 % less peak memory), so the app keeps the default collector; `gcMutatorsCooperate` removed.*

- **A6 XcodeGen** for `ios/`, the `.xcodeproj` not committed, the signing team in a local ignored xcconfig.

- **A7 Shared editing module** `android/editing` (Kotlin Multiplatform, JVM and iOS), built Android first in WP-3, with the deviation from `android/AGENTS.md`.

- **A8 MP4 written by the iOS app** through AVFoundation, the same exception as `VideoExport.kt`.

- **A9 iOS text roles** as six design tokens in `Tokens.swift`, mapped onto Dynamic Type; the roles are listed in WP-4 before use.

- **A10 Permissions and capabilities:** Photos add-only access with one usage text, App Groups, background app refresh for widget pictures.

- **A11 Deviations from ADRs:** ADR-0004 (iOS parity against the JVM), ADR-0010 point 10 and ADR-0014 (wallpaper routes replaced per F5 a and F6 c), ADR-0016 point 4 (iPhone widgets free, the daily turn Pro, F7 a; amended 12 November 2026), ADR-0020 gets an iOS counterpart, the daily turn by date on iOS; each recorded in an ADR when built. No Swift packages requested.

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
