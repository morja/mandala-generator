# ADR-0015 · iPhone app on the shared Kotlin core

Status: proposed, 22 October 2026 ([PLAN-4](../PLAN-4-iphone-app.md)). Deviates from ADR-0002 (`core` no longer JVM only).

## Decision

1. **An iPhone app "Dahlia"** (ADR-0012) in the App Store, iPhone only, iOS 17 or later. No iPad layout; the app runs on the iPad as an iPhone app.
2. **Free with a one-time Pro purchase** (StoreKit 2, non-consumable, Family Sharing). What is free and what is Pro, and the price, follow ADR-0016, the same as on Android.
3. **No wallpaper API on iOS:** the daily wallpaper is an App Intent that the user's Shortcuts automation passes to "Set wallpaper"; widgets show today's mandala.
4. **Renderer:** the Kotlin `core` becomes a Kotlin Multiplatform module with JVM and iOS targets and is linked into the iPhone app as an XCFramework. `commonMain` holds no `java.*`; `Parallel` has one implementation per platform. The UI is native SwiftUI in a new root folder `ios/`; text through Core Text and images through ImageIO behind the existing `TextRenderer` and `ImageLoader` interfaces.
5. **Licence** unchanged (ADR-0008); the section 7 permission already covers iOS and the App Store.

## Reasons

- Everything a phone needs from the renderer (portrait and framing, strips and 8-bit intermediates within the memory budget, finish, SVG) exists only in the Kotlin `core`. The macOS renderer draws squares with float buffers and Core Image; porting it to a phone would repeat ADR-0002, ADR-0010 and ADR-0014 in Swift.
- `core` needs only small changes to build for iOS (PLAN-4, section 6), and one renderer for three platforms keeps the parity tests (ADR-0004) meaningful.
- SwiftUI rather than Compose Multiplatform: native look and sheets, smaller app, the Mac app's SwiftUI experience applies.

## Consequences

- `ArchitectureTest` checks `commonMain` for `java.*`; the JVM tool and the Android app keep using `core` unchanged.
- The Mac-first rule (ADR-0008, ADR-0009) stays: new motifs come to the Mac, then to `core`, then to both phone apps.
- A new check for `ios/` in the git hook (`xcodebuild` for the simulator) once the folder exists.
- Apple Developer Program membership (99 € a year) is Mathis'.

## Open

- Speed of Kotlin/Native in the pixel loops; measured in SPIKE-3 before anything else is built. Fallback: the Swift renderer from the macOS app (PLAN-4, path A).
- Whether "Set wallpaper" runs in a Shortcuts automation without a confirmation on the current iOS.
