# PLAN-4 · Dahlia for iPhone

22 October 2026. Status: draft, for Mathis to decide on the questions at the end. Decision draft: [ADR-0015](adr/0015-iphone-app.md). Nothing is built yet.

Thought through as a new app, not as a copy of the Android app (Mathis, 22 October 2026). Decided in the conversation the same day: free with a one-time Pro purchase, up to five favourites for free, Pro may differ from Android, no live photo, no iCloud sync, no video export, no iPad layout. The macOS app in the App Store (with one purchase for Mac and iPhone) is left for later.

## 1. What the app is

A mandala maker for the iPhone, with no subscription, no ads and no tracking. The main promise: your own mandala, and a new one on the lock screen every day. App Store competitors are mostly subscription wallpaper apps and colouring books; a real generator with a one-time price stands out among them.

## 2. Open source

Stays as in ADR-0008: GPL-3.0 with the section 7 permission, which already names iOS and the App Store. Mathis, as the only author, ships his own App Store build. Others may build the app from source but would have to publish a copy under GPL, which the App Store's terms make impractical, so a free clone in the store is unlikely. Contributions from others would need their consent to the section 7 permission; not an issue while Mathis is the only contributor.

## 3. Business model

Free app with a **one-time Pro purchase** (StoreKit 2, non-consumable, with Family Sharing). No subscription, no ads.

- Price: about **4.99 €** (App Store prices tend to be higher than Google Play; one purchase for both platforms does not exist anyway). See question 2.
- No watermark, saving is never locked.

## 4. Free and Pro

The line runs **through the editor**, not through saving: people who only want a nice wallpaper get one for free and pass the app on; people who design pay.

| Free | Pro |
|---|---|
| Surprise mandalas and all built-in starters | Full editor: adding and removing layers, every style, emblems, effects layer, text layer, layer positions |
| Palettes, background, the main sliders of each existing layer, new variation | Save large (2048 px wide) |
| Save and share at screen size, every format, "Show whole" and "Fill" | Finish (brightness, contrast, saturation) for a dark home screen |
| Up to **5 favourites** | Unlimited favourites |
| Opening and saving Mac and Android files (ADR-0003) | "Mandala of the day" as a Shortcuts action |
| | Widgets: home screen, lock screen, StandBy |

A Mac file with five layers opens and renders in the free app; its layers can be changed with the free sliders. Only adding, removing or restyling layers is Pro, so no file is ever locked. See question 3 for the exact free sliders.

## 5. Daily wallpaper without a wallpaper API

iOS does not let an app set the wallpaper or run a live wallpaper. Replacement:

1. **App Intent "Mandala of the day"** (Pro): renders today's mandala (favourites in turn, like PLAN-3) at screen size, with the finish, and returns it as an image.
2. A Shortcuts **personal automation** (for example every morning at 6) passes it to the system action "Set wallpaper". The app shows a short guide with a ready shortcut link; setting it up once is the user's step.
3. **Widgets** show today's mandala on the home screen, lock screen and in StandBy (iPhone on its side while charging).

To be checked in the spike (section 7): whether "Set wallpaper" runs in an automation without a confirmation on the current iOS, and how the lock screen handles the depth effect with a mandala.

## 6. Technology: shared Kotlin core, SwiftUI on top

Two ways were considered. The recommendation changed after looking at both renderers on 22 October 2026.

**A. Swift renderer from the macOS app.** The obvious way at first: the Mac app is Swift and SwiftUI. But the Mac renderer draws only squares, keeps intermediates as float buffers (12 bytes per pixel), uses Core Image and supersamples at 4096 px. Everything a phone needs was solved in the Kotlin `core` only: portrait formats and framing (ADR-0010), strips and 8-bit intermediates within a memory budget (ADR-0002, SPIKE-1), the finish (ADR-0014), SVG export (ADR-0013). Path A means porting all of that to Swift a second time and keeping two phone renderers in step.

**B. The Kotlin `core` as a Kotlin Multiplatform module (recommended).** `core` is already nearly platform-free (checked 22 October 2026): only `Parallel.kt` uses `java.util.concurrent`; otherwise `System.arraycopy`, `Math.*` and `System.nanoTime` in about 35 places, all with Kotlin stdlib equivalents (`copyInto`, `kotlin.math`, `TimeSource`). Text and image loading already sit behind interfaces (`TextRenderer`, `ImageLoader`). `core` would gain an iOS target that builds an XCFramework; the iPhone UI is SwiftUI and calls it, with Core Text behind `TextRenderer` and ImageIO behind `ImageLoader`.

- Portrait, framing, finish, memory budget and parity tests come for free, and every later fix reaches Android, iPhone and the JVM tool at once.
- `Parallel` gets a small iOS variant (Grand Central Dispatch through `dispatch_apply`, or Kotlin/Native workers) behind `expect`/`actual`.
- Risk: speed of Kotlin/Native in the pixel loops. To be measured first (section 7).
- The UI stays native SwiftUI, not Compose Multiplatform: smaller app, iOS look, no second UI framework to learn. The Android UI is not shared.

## 7. Order

1. **Spike (2–3 days):** `core` with an iOS target, render the `MemoryBudgetTest` worst case and three favourites on an iPhone, compare time with the Android phone. Check the Shortcuts automation from section 5. Report as SPIKE-3. If Kotlin/Native is more than about twice as slow as Android on the same mandala, stop and decide between path A and tuning.
2. **Core multiplatform (2–3 days):** the replacements from section 6, `ArchitectureTest` extended (no `java.*` in `commonMain`), Android and JVM tool unchanged and green.
3. **iPhone app (1–1.5 weeks):** new root folder `ios/` with the Xcode project. One screen like the Android editor: mandala on top, panels below; save sheet with formats, framing and finish; favourites; Pro sheet. Design by the skill `design-baseline`, adapted to iOS (SF fonts, system sheets); light and dark.
4. **Pro (3–4 days):** StoreKit 2 purchase and restore, App Intent, widgets.
5. **App Store (2–4 days):** signing, privacy label ("no data collected"), screenshots, listing in English and German, review.

About **3 to 4 weeks** in all. Path A would be about 4 to 5 weeks, mostly for porting portrait, strips and the finish to Swift.

## 8. Not planned

Live photo, iCloud sync, video export, an iPad layout (the iPhone app runs on the iPad as it is), Apple Watch. The macOS app in the App Store is decided later.

## 9. Needs approval

- A deviation from ADR-0002: `core` becomes a Kotlin Multiplatform module with JVM and iOS targets (Kotlin Multiplatform Gradle plugin, same Kotlin version 2.4.20).
- New root folder `ios/` in the repository.
- No new Swift packages: SwiftUI, StoreKit 2, App Intents, WidgetKit, Core Text and ImageIO are system frameworks.

## Questions

1. **Path:** shared Kotlin core (B) or Swift renderer from the Mac app (A)? Recommendation: B, after the spike confirms the speed.
2. **Price:** 4.99 € or the same 2.99 € as on Android? Recommendation: 4.99 €; iPhone users are used to it, and the free part is generous.
3. **Free sliders:** which controls stay free? Recommendation: palette, background, and per layer symmetry, size, rotation and the style's first two parameters; everything else in the editor is Pro.
4. **Minimum iOS:** Recommendation: iOS 17 (current App Intents, StoreKit 2, interactive widgets, StandBy); it runs on every iPhone from the XS and XR on.
5. **Apple Developer account:** does one exist (99 € a year), under which name and with which bundle id? Recommendation: `one.tearoom.dahlia`, like the Android id scheme.
6. **Order:** spike first, then the rest as in section 7? Recommendation: yes.
