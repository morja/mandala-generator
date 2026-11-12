# Dahlia – Mandala Maker

Session: iOS WP-9 Pro purchase · 12 November 2026

## IOS-9: Pro purchase through StoreKit 2, the Pro sheet and the App Group flag (PLAN-4 WP-9)

What WP-9 of [PLAN-4](PLAN-4-iphone-app.md) built on the Pro seam of [IOS-7](IOS-7-save-sheet-and-export.md) finding 9: the one purchase "Dahlia Pro" (`one.tearoom.mandala.pro`, non-consumable, 3.99 €, Family Sharing on, [STORE-2](store/STORE-2-app-store.md)) through StoreKit 2, as PLAN-4 section 3.4 picked it (a: StoreKit directly, no package), behaving as tearoom-android-kit's `unlock` does on Android (ADR-0022). The test list is [QA-8](QA-8-ios-pro-purchase.md). Screenshots: `docs/qa/shots/ios-wp9-2026-11-12/` in the main checkout (ignored by Git).

### What was built

1. **`ProStore`** (`Sources/Model/ProStore.swift`), the counterpart of the kit's `AppUnlock`: started in `DahliaApp.init`, before any screen, so `Transaction.updates` is heard from launch on; then it finishes `Transaction.unfinished`, reads the entitlement (`Transaction.currentEntitlements(for:)` and `Transaction.latest(for:)`) and loads the price (`Product.displayPrice`). Each return to the app reads the entitlement again (Android asks Play on every resume). Buy (`Product.purchase()`), Restore (`AppStore.sync()`), pending purchases, price problems and purchase feedback map onto the kit's states and Dahlia's texts.

2. **`ProPolicy`** (`Sources/Model/ProPolicy.swift`), the pure rules, like the kit's `UnlockPolicy`: only a verified transaction of this product unlocks (family shares count like an own purchase); a verified revoked transaction removes Pro (a refund, or a family share taken back) unless another purchase still counts; a restore that reached the App Store without a purchase removes it; anything else (offline, StoreKit's cache not filled) keeps the cached value. A check that started before the latest grant cannot take it back (finding 2). Unit-tested without StoreKit.

3. **The offline cache** is a small file in Application Support (`pro-purchase.json`), not UserDefaults: launch arguments can set any UserDefaults key, and they must not unlock a release build. It is written only from verified transactions or StoreKit's entitlement.

4. **The seam is filled.** `EditorModel.proOwned` is now `proStore.unlocked`: a verified purchase, or in debug builds the test Pro of IOS-7 (`-proOwned NO` still shows the free app). Release builds compile the test Pro out (`#if DEBUG`), so they unlock only through a verified purchase. `requestPro(ProRequest)` opens the real sheet; a request without features lists the Pro features the mandala uses, as Android's `showPro` and its video request do.

5. **The Pro sheet** (`Sources/UI/ProSheet.swift`), laid out as Android's `UnlockSheet` with Dahlia's `ProIntro`: title, the features the mandala uses or the note, what Pro adds; "Buy for 3,99 €" (the label keeps its place while the price loads, "Loading price…", or is missing, "Buy Pro"); "… without them" or "Remove them"; pending, why there is no price, the last feedback; "Try again" and "Restore purchase" one per line. As tall as its content, scrolling when taller. A purchase or restore closes it and the thanks shows as a message; inside the sheet the feedback stays there (Android's routing). It opens on top of whatever asked for it: the editor (the "Pro" mark on the picture is now a button, as on Android), the save sheet, the menu.

6. **Menu** (Android's Dahlia page): "Dahlia Pro" first, "Five layers, emblems, text, wallpapers" without Pro, "Active, thanks" with it, opening the page with the thanks and the three sentences of what Pro includes. Share from the menu opens the Pro sheet on top of the menu for a mandala with Pro features ("Share without them" goes on); the line drawing without Pro keeps the menu open under the sheet.

7. **App Group flag.** `AppGroup` (in `ProStore.swift`) is the one seam for `group.one.tearoom.mandala` (approval A2): `proUnlocked` is written whenever Pro changes and at start, for the widget's daily turn (WP-10, F7 a). The entitlement is in `Config/Dahlia.entitlements` and applied to simulator builds only (`CODE_SIGN_ENTITLEMENTS[sdk=iphonesimulator*]` in `project.yml`): a device build would need the group on the App IDs, which needs a signed-in Xcode (steps below). Without the entitlement `AppGroup.defaults` is nil and nothing is written; checked in the simulator that the group container exists and holds `proUnlocked`.

8. **Local StoreKit configuration** `Config/Dahlia.storekit`: the product with App Store Connect's Apple IDs, 3.99 € in the German storefront, Family Sharing on, English and German display name and description as in STORE-2. The scheme's Run action uses it, so running from Xcode buys against it (Debug, StoreKit, Manage Transactions refunds and approves).

9. **Tests** `DahliaStoreTests` (new target, hosted in the app because StoreKit needs an app around it; the purchase code is compiled in): 15 StoreKitTest cases against the configuration (price, purchase with cache and flag, reinstall, restore, purchase from elsewhere through the updates, cancel, Ask to Buy approved, bad signature, refund while running and at the next start, offline with and without Pro, restore without purchase, the race, debug Pro) and 12 rule cases. They run in `ios/scripts/check-full` with the parity test.

10. **Texts.** The keys are Android's (`Pro.kt`, the kit's sheet, the menu); eight that name Google Play got App Store wording in all ten languages in `ios/Dahlia/Strings/values*/strings_ios.xml`: `pro_pending` (approval or payment), `pro_product_unavailable`, `pro_play_unavailable`, `pro_restore_not_found` (Apple Account, Settings, Media & Purchases), `pro_restore_unreachable`, `pro_purchase_not_started`, `editor_pro_unavailable` (purchases turned off, Screen Time), `editor_pro_adds_wallpapers` (pair and daily through Shortcuts; no live wallpaper, F5 a and F6 c).

11. **Debug hooks** (`ShotSetup`): `-shotSheet pro` opens the Pro sheet after the mandala is set up, `-shotPrice "3,99 €"` shows a price for layout screenshots (a launch outside Xcode has no StoreKit configuration), `-shotProOver save|menu` opens it on top of that sheet, `-shotSheet propage` the menu's Pro page.

### Findings

1. **The race between app start and `Transaction.updates`.** Both run on the main actor, but each awaits StoreKit, so a check that started before a purchase arrived (an approved Ask to Buy, a purchase on another device) could finish after it and remove it with its older, empty answer. Grants are counted; a check applies a removal only if no grant arrived while it ran (`ProPolicy.mayRemove`, tested).

2. **StoreKit's entitlement list lags behind a revocation.** In the refund tests the update with `revocationDate` arrived while `currentEntitlements` still listed the same purchase, and right after a refund (app closed) neither the list nor `latest(for:)` knew it for a moment. The update's revoked transaction is now passed into the check and wins over a stale entitlement of the same purchase (`originalID`); at the next start the cache stays until StoreKit reports the refund, then Pro goes. That is the rule ADR-0022 sets: only a store answer removes Pro.

3. **StoreKitTest in Xcode 27.2 lists a declined Ask to Buy as an entitlement.** After `declineAskToBuyTransaction` the test transaction's state is "failed", yet `currentEntitlements` and `latest(for:)` return it as a valid, unrevoked transaction. Apple's documentation says a declined request produces no transaction. The app does not work around it (it would mean distrusting verified entitlements); the decline is QA-8 case 6b for Xcode's transaction manager and later TestFlight.

4. **`SKTestSession.buyProduct` records the purchase but may throw "unknown"** in this Xcode; the update arrives anyway. The tests ignore that call's error and check the update.

5. **Setting the session's storefront or locale in code made the product disappear**; the storefront and locale are taken from the configuration file instead.

6. **Pending is kept for the running app only.** StoreKit offers no query for a waiting Ask to Buy and sends nothing when a parent declines, so a stored "pending" could block buying forever. After a restart the button works again; a second request is StoreKit's to handle.

### Review of the diff (verification, revocation, offline cache, the race, release builds)

- Verification: every path that grants (`handle`, `buy`, `check`) goes through `ProPolicy`, which needs `.verified`; an unverified purchase posts "The purchase did not go through" and never touches the cache (tested with a simulated invalid signature). Unverified transactions are left unfinished, as Apple's sample does.

- Revocation: an update with `revocationDate` is finished and checked with the revoked transaction itself (finding 2); `latest(for:)` covers refunds while the app was closed; family shares taken back arrive the same way.

- Offline cache: only removed by a revoked verified transaction or a restore that reached the App Store; a failed price load keeps a known price; a failed restore says so and keeps Pro.

- Race: finding 1; `start()` creates the update listener before anything else, and `DahliaApp.init` calls it before the first scene.

- Release builds: the test Pro is `#if DEBUG`; the screenshot price setter and the shot hooks are `#if DEBUG`; the cache is a file, not a UserDefaults key a launch argument could set; the App Group flag is written, never read for unlocking. The unsigned Release build of `check-full` builds.

### Verified

- `ios/scripts/check` and `ios/scripts/check-full` green: parity against the JVM unchanged, `DahliaStoreTests` 27 of 27, unsigned Release build.

- Simulator iPhone SE (3rd generation, 375 pt, dark), debug build with `-proOwned NO`: the Pro sheet in German and Russian with a price, Russian without a price (product unavailable, "Try again"), on top of the save sheet and of the menu, the menu's Dahlia page without Pro and the Pro page with it. App Group container present, `proUnlocked` false with `-proOwned NO` and true with the test Pro.

### What remains for the sandbox and the device (QA-8)

- Purchase, restore after reinstall, offline with and without Pro, cancel and the sheet in German and Russian on the iPhone with a sandbox tester, once the Paid Apps Agreement is active (QA-8 cases 1 to 5 and 9).

- Ask to Buy declined and a refund in Xcode's transaction manager (QA-8 cases 6b and 7); a real refund after launch.

- Family Sharing with two family iPhones (QA-8 case 8).

- The App Group on the phone, after the steps below.

### Steps for Mathis in Xcode

- Sign in again: Xcode → Settings → Accounts → the Apple Account of the developer team.

- Copy `ios/Dahlia/Config/Signing.example.xcconfig` to `ios/Dahlia/Config/Signing.xcconfig` (it exists with a placeholder) and put the team id in `DEVELOPMENT_TEAM`.

- App Group on both App IDs: in Xcode open the generated project (`ios/Dahlia/scripts/generate`, then `open ios/Dahlia/Dahlia.xcodeproj`), target Dahlia → Signing & Capabilities → + Capability → App Groups → add `group.one.tearoom.mandala`; then build once for the iPhone (Debug) and once Product → Archive (Release), so automatic signing registers the group for `one.tearoom.mandala.debug` and `one.tearoom.mandala`. Xcode writes this into the generated project, which is not kept; what counts is that the App IDs now carry the group. Then tell the next session to drop the `[sdk=iphonesimulator*]` condition in `project.yml`, so device builds carry the entitlement too.

- For QA-8: a sandbox tester in App Store Connect (Users and Access → Sandbox) once the Paid Apps Agreement shows "Active".

### Selbst entschieden

- Die Testliste heißt QA-8 statt QA-7: `docs/QA-7-ios-checks-and-parity.md` (WP-2) trägt die Nummer schon.

- Der Zwischenspeicher liegt in einer Datei statt in UserDefaults, damit kein Startargument einen Release-Build freischalten kann.

- Ein ausstehender Kauf gilt nur bis zum nächsten Start (Befund 6).

- Die App Group gilt vorerst nur für Simulator-Builds, damit die Debug-Builds auf dem iPhone 15 ohne Profil mit App Group weiter laufen; das Merkmal schreibt die App schon jetzt.

- Acht App-Store-Texte in allen zehn Sprachen, darunter „Hintergründe: … jeden Tag ein neuer über Kurzbefehle“ statt Androids Live-Hintergrund (F5 a, F6 c); WP-11 muss das liefern.

- Teilen aus dem Menü und die Strichzeichnung öffnen das Pro-Fenster über dem Menü, statt das Menü erst zu schließen.

- Eigenes Testziel `DahliaStoreTests`, das in der App läuft; die Paritätstests bleiben ohne App.
