# ADR-0015 · iPhone app on the shared Kotlin core

Status: accepted, 12 November 2026 (Mathis, after [SPIKE-3](../SPIKE-3-ios-kotlin-native-speed.md) and [SPIKE-4](../SPIKE-4-renderer-allocations.md), with every recommendation of [PLAN-4](../PLAN-4-iphone-app.md) F1–F11 and approvals A1–A11). Proposed 22 October 2026; revised 12 November 2026 against today's rules, changed points are marked "(12 November 2026)". Deviates from ADR-0002 (`core` no longer JVM only; approved 11 November 2026 in `docs/APPROVALS.md`).

## Decision

1. **An iPhone app "Dahlia"** (ADR-0012) in the App Store, iPhone only; on the iPad it runs as an iPhone app. *(12 November 2026)* The minimum iOS version is iOS 26 (PLAN-4 F2, Mathis 12 November 2026, instead of October's iOS 17). Bundle id `one.tearoom.mandala`, the same as the Android package (F1). The first version has the full feature set of the Android app (PLAN-4 section 2).
2. **Free with a one-time Pro purchase** (StoreKit 2, non-consumable `pro`, Family Sharing on, PLAN-4 F4). What is free and what is Pro, and the price, follow ADR-0016, the same as on Android.
3. **No wallpaper API on iOS:** the daily wallpaper is an App Intent that the user's Shortcuts automation passes to "Set wallpaper". *(12 November 2026)* The intent runs in the app's process and returns pictures rendered ahead. Widgets show the mandala of the day from pictures the app renders into an **App Group**; the widget extension never renders, since it may use only about 30 MB (SPIKE-3 finding 7). The daily turn is a function of the date, so app, widget and intent agree without a morning job.
4. **Renderer:** the Kotlin `core` is a Kotlin Multiplatform module with JVM and iOS targets, linked into the iPhone app as an XCFramework. `commonMain` holds no `java.*`. *(12 November 2026)* `Parallel` on iOS is a **fixed pool of Kotlin/Native workers** that live as long as the app, never Grand Central Dispatch (which let the footprint grow to 21 GB, SPIKE-3 finding 2). Only the **release (optimised) framework** is built into the app; the debug framework is 5 to 8 times slower. The UI is native SwiftUI in the root folder `ios/`; text through Core Text and images through ImageIO behind the existing `TextRenderer` and `ImageLoader` interfaces.
5. **Licence** unchanged (ADR-0008); the section 7 permission already covers the iOS system libraries and the App Store.
6. *(12 November 2026)* **Editing rules are shared, not ported:** the platform-free rules of the Android app (editing, the die, the play field, the history, the daily turn) move into a shared Kotlin Multiplatform module that both apps use, approved 12 November 2026 (PLAN-4 section 3.1, approval A7).

## Reasons

- Everything a phone needs from the renderer (portrait and framing, strips and 8-bit intermediates within the memory budget, finish, SVG, the moving picture's frames) exists only in the Kotlin `core`. The macOS renderer draws squares with float buffers and Core Image; porting it would repeat ADR-0002, ADR-0010 and ADR-0014 in Swift.
- *(12 November 2026)* SPIKE-3 measured it: in a release build the iPhone 15 renders a typical favourite's preview in 1.3 s and its 1024 px export in 5.2 s, 1.2 to 1.9 times faster than the Galaxy S23 runs the same core on Android, with a peak of 385 MB and the same pixels as the JVM. SPIKE-4 cut allocations by three quarters with unchanged pixels; the iPhone gained only about 3 %, so speed is not tuned further before the app exists (Mathis, 12 November 2026).
- One renderer for three platforms keeps the parity tests (ADR-0004) meaningful.
- SwiftUI rather than Compose Multiplatform: native look and sheets, smaller app.

## Consequences

- `ArchitectureTest` checks all core source sets for platform APIs and `commonMain` for `java.*`; the JVM tool and the Android app use `core` unchanged.
- *(12 November 2026)* **Android first** (`AGENTS.md`, 27 October 2026) replaces the Mac-first rule of October's draft: new features come to the Android app first, then to `core` where needed, then to the iPhone app; the Mac follows later.
- *(12 November 2026)* The editor renders off the main thread, keeps the last picture until the new one is ready and drops outdated renders; exports show progress and survive a short trip to the background.
- *(12 November 2026)* The git hook runs a check for `ios/` (simulator build and tests) and compiles core for the iOS simulator when `android/core/` changes; `ios/scripts/check-full` adds a parity test of the iOS renders against the JVM's.
- *(12 November 2026)* The `gcMutatorsCooperate` condition of SPIKE-3 is dropped: the Kotlin version in use ignores it (SPIKE-4). A fixed collector target is decided after a measurement on the iPhone (PLAN-4 approval A5).
- Apple Developer Program membership (99 € a year) is Mathis'; the account exists (team 6735ZK5WKV).

## Open

- *(12 November 2026)* The speed question is answered (SPIKE-3); the Swift renderer fallback is dropped.
- Whether "Set wallpaper" runs in a Shortcuts automation without a confirmation on the current iOS (PLAN-4 section 7).
- *(12 November 2026)* The questions F1 to F11 and approvals A1 to A11 of PLAN-4 are answered (Mathis, 12 November 2026, "Alles wie empfohlen"); A5 depends on the device measurement in WP-1.
