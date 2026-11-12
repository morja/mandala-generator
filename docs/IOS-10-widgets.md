# Dahlia – Mandala Maker

Session: iOS WP-10 widgets · 12 November 2026

## IOS-10: Home screen widgets "Mandala of the day" (PLAN-4 WP-10)

What WP-10 of [PLAN-4](PLAN-4-iphone-app.md) built: the widget extension with small, medium and large sizes, settings per widget through Edit Widget, pictures rendered ahead by the app into the App Group, timelines that turn at 4:00 without the app, the tap that offers the widget's mandala, the widget list on the Dahlia page and the debug mark. Decisions F7 a (free, the daily turn with Pro) and F8 a (seven days ahead); approvals A2 and A10. The design is recorded as the iPhone amendment of [ADR-0020](adr/0020-home-screen-widget.md). Screenshots: `docs/qa/shots/ios-wp10-2026-11-12/` in the main checkout (ignored by Git).

### What was built

- **Shared code** `ios/Dahlia/Shared/` (in the app and the extension, plain Swift without MandalaCore): `AppGroup` (moved out of `ProStore.swift`; the identifier now comes from the Info.plist, `group.<bundle id>`), `WidgetData.swift` (the widget kind, `WidgetDay` with days from 4:00 numbered like Android's epoch day, the index `WidgetIndex`, the widget sizes the extension saw, `WidgetRules` as Android's `WidgetChoice`), `WidgetIntent.swift` (the configuration intent with Mandala and Appearance, the favourite list read from the index).

- **The extension** `ios/Dahlia/Widget/` (target `DahliaWidget`, `one.tearoom.mandala[.debug].widget`): `AppIntentConfiguration` for small, medium and large, margins off so the picture reaches the edges, medium with the picture at the left and the words beside it in the tokens' surface and text colours of the widget's appearance. One entry for today and one at 4:00 for each later day in the index; after the last one it stays and asks again the next day. Pictures are decoded with ImageIO at the size shown. Empty text only when there is no favourite; before the app ever ran the card alone (Android's loading layout).

- **Rendering ahead** `Sources/Model/WidgetSync.swift`, `EditorModel+Widgets.swift`: at launch, at every return to the app, on every change Android redraws for (favourites, the daily choice, theme, Pro, quote languages) and in a background app refresh (`BGTaskSchedulerPermittedIdentifiers` `<bundle id>.widgets`, `UIBackgroundModes fetch`, SwiftUI `.backgroundTask(.appRefresh)`), scheduled from the next 4:00 while widgets are placed. The square picture of all seven days always; the placed widgets' shapes for their fixed favourite or their days; every favourite's thumbnail as the fallback. Its own utility queue, so the editor's previews go first. The index is written atomically and only when it changed, then `reloadTimelines`.

- **Tap**: `dahlia://widget?favourite=<id>` (`dahlia-debug` in debug builds, a URL type of the app) opens the app as it was; `openedFromWidget` offers the mandala with "Open the mandala from the widget?" and Open only when it differs from the editor's.

- **Dahlia page**: a "Widgets" row (none, or "n on the home screen") opening the list of placed widgets with size, thumbnail and "name" or "name · Daily turn"; iOS texts for how to edit and add widgets in all ten languages (`menu_widgets_about`, `menu_widgets_empty` in `strings_ios.xml`).

- **Debug mark**: the icon's ring and dot, drawn by the extension bottom right in debug builds; a debug switch "Mark widget pictures" on the More page and `-widgetMark NO`. Release builds never mark.

- **Daily choice store** `DailyChoiceStore` (the ticked favourites, nil for all); the page that ticks them comes with WP-11, until then the turn goes through all favourites.

- **Checks**: `ios/scripts/check-sources` covers `Shared` and `Widget` too (tokens, texts, catalogue keys); `DahliaTests` has `WidgetRulesTests` (4:00, summer time, the debug day, the last day staying, fixed favourite and fallback, looks, words and picture fallbacks).

- **Debug hooks**: `-widgetDayMinutes 2` makes a day two minutes long in app and extension, `-widgetResetFootprint YES` clears the extension's recorded peak, `-shotSheet widgets` opens the widget list.

### Findings

1. **`WidgetCenter.currentConfigurations()` gives the placed widgets with their settings** (family and `widgetConfigurationIntent(of:)`) on iOS 27.2, so the Dahlia page lists them and the app knows which fixed favourites and shapes to render (PLAN-4 section 7, checked).

2. **The timeline turns without the app.** With two-minute debug days and the app terminated, the small and the medium widget changed favourite and quote of the day at the next entry (`debug-turn-two-minute-day-1.png`, `-2.png`). Whether a 4:00 entry shows on a phone after hours of sleep is the morning check below.

3. **Memory: the first version peaked at 27–29 MB**, close to the extension's 30 MB. Measured with `footprint` on the extension in the simulator: 14 MB without any picture, 28 MB with pictures. The thumbnail call kept the full decode and a scaled copy at the same time, even though the app writes each picture at the size shown. Now a picture whose size already fits is decoded once as it is: **14 MB steady, 15 MB peak** with four widgets (small, two medium, large at 1049 × 1095 px). Debug build in the simulator; the device is still to measure.

4. **Background app refresh cannot be tried in the simulator** ("BGTaskScheduler is not available on this platform"). Registration and scheduling are in place; whether iOS grants refreshes is for the device.

5. **The StoreKit tests leave a purchase behind.** `DahliaStoreTests` run inside the debug app and write `pro-purchase.json`; afterwards the debug app counts as bought even with `-proOwned NO`, so the free widget looked like Pro until the file was removed. For free-app checks in a simulator that ran `check-full`: delete the app or that file first.

6. **Edit Widget lists favourites by name only**, so unnamed starters with the same styles appear twice ("Floral"). Android's settings page shows thumbnails; an App Intent list cannot. Naming favourites (a later feature) would help.

7. **`-widgetMark NO` did not turn the mark off** at first: a launch argument arrives as text, and the cast to Bool failed; it now reads it with `bool(forKey:)`.

8. **The simulator was shared with another session.** While I placed widgets, a Lazy Moves session installed and launched its debug app on the same iPhone 17 simulator; some of my taps landed in Lazy Moves (its map, its settings, a "sample diary loaded" message that I confirmed with OK). Nothing of Dahlia depends on it, but that session may have seen my taps too, and the Mac was not used by this build alone.

9. **The debug build has its own App Group now** (`group.one.tearoom.mandala.debug`), so debug and store app never show each other's widget pictures. IOS-9's Xcode steps change accordingly (below).

10. **The StoreKit tests are flaky on this Mac today, also on `main`.** In three `check-full` runs two or three of the 27 store tests failed (the purchase from elsewhere, the race, once the refund while running: StoreKitTest's purchase or refund did not arrive in time, the whole bundle then takes 11 s instead of 1 s); run alone they passed in some runs and failed in others. The WP-9 build of `main` (2f91000), built separately and run back to back on the same simulator, failed one of them too while the WP-10 build passed. The simulator was busy with the other session (finding 8). The widget work stays off while the app hosts tests (`XCTestConfigurationFilePath`), so it cannot be the cause; the tests themselves are unchanged. Worth a run on a quiet simulator, and a look at whether `SKTestSession` needs a wait after `clearTransactions`.

### Verified in the simulator (iPhone 17, iOS 27.2, debug build)

- Four widgets with different settings: small, daily turn; medium, daily turn, like the app (dark, and light after switching the app to light); medium, fixed favourite "Sacred", light; large, fixed favourite "Starburst, Spirograph". Each showed its favourite, the large one in its own shape after the app ran, the medium ones with the quote of the day (`de-dark-home-page2-…`, `de-dark-home-page3-…`, `en-quotes-app-light-wide-like-the-app.png`).

- A simulated next day turns the daily widgets (debug Pro), finding 2.

- The free app (`-proOwned NO`, stale purchase removed): all seven days on the first favourite, the widgets on it (`de-dark-free-first-favourite.png`).

- Deleting the fixed favourite in the app: the medium widget falls back to the daily turn (`de-dark-fixed-deleted-falls-back-to-daily.png`).

- Tap on a widget from a cold start: the editor unchanged, the offer at the bottom (`de-dark-tap-offer.png`).

- Widget list on the Dahlia page in German dark and English light (`de-dark-menu-widgets.png`, `en-light-menu-widgets.png`); the mark off with `-widgetMark NO` (`de-dark-mark-off-for-screenshots.png`).

- Extension memory, finding 3.

- `ios/scripts/check` green; in `ios/scripts/check-full` the parity test is unchanged (96 fixtures, 74 identical, worst 82.9 dB; text cases as before), `DahliaTests` 28 of 28 including the new widget rule tests, the unsigned Release build with the extension (`one.tearoom.mandala.widget`, `group.one.tearoom.mandala`) builds; `DahliaStoreTests` flaky as in finding 10.

### To check on the iPhone over two mornings

- Place three widgets: small with the daily turn, medium with a fixed favourite and Appearance "Light", large with the daily turn; open the app once and leave it.

- The next morning after 4:00, without opening the app: the small and the large widget show the next favourite, the medium one the same favourite with a new quote of the day. The second morning the same again (the background refresh or the seven days ahead carry it).

- Then open the Dahlia page: the three widgets are listed with the right sizes and favourites. Tap a widget: the editor stays, the offer appears; Open replaces the mandala.

- Switch the app to light: the widgets "like the app" turn light; delete the medium widget's favourite: it falls back to the daily turn.

- With the debug build, the dot sits bottom right; the More page switch removes it.

- Memory: in Xcode, Debug Navigator on the widget extension process while the widgets reload; it should stay near 15 MB.

- Tinted home screen (Customize → Tinted): see whether the mandala stays readable; nothing is set for it yet.

### Steps for Mathis in Xcode (App Group on the device)

- Sign in to Xcode with the developer team (Xcode → Settings → Accounts) and put the team id into `ios/Dahlia/Config/Signing.xcconfig`, as in IOS-9.

- Open the generated project (`ios/Dahlia/scripts/generate`, then `open ios/Dahlia/Dahlia.xcodeproj`). For the target **Dahlia** and the target **DahliaWidget**: Signing & Capabilities → + Capability → App Groups. In the Debug configuration add `group.one.tearoom.mandala.debug`, in Release `group.one.tearoom.mandala` (the IOS-9 step named only the second; the debug build now has its own group).

- Build once for the iPhone with Debug and once Product → Archive with Release, so automatic signing registers the four App IDs (`one.tearoom.mandala`, `.widget`, `.debug`, `.debug.widget`) with their groups. Xcode writes this into the generated project, which is not kept; what counts is the portal.

- Tell the next session to remove the `[sdk=iphonesimulator*]` condition from `CODE_SIGN_ENTITLEMENTS` of both targets in `ios/Dahlia/project.yml`; then device builds carry the group and the widget works on the phone.

### Selbst entschieden

- Der Debug-Build hat eine eigene App Group (`group.one.tearoom.mandala.debug`) und ein eigenes URL-Schema (`dahlia-debug`), damit Debug- und Store-App auf einem Telefon nie dieselben Widget-Bilder zeigen; das passt zu A2 (`group.<bundle id>`), ändert aber die Xcode-Schritte aus IOS-9.

- Die Bilder hängen nicht vom Aussehen ab: Hell und Dunkel ändern wie auf Android nur die Fläche und die Schrift des breiten Widgets; das Bild füllt das Widget bis zum Rand.

- Das quadratische Bild der sieben Tage wird immer gerechnet, auch ohne platziertes Widget, damit ein neu platziertes Widget sofort scharf ist; das große Bild und das Bild ohne Worte nur für platzierte Widgets.

- Von jedem Favoriten liegt das Vorschaubild in der App Group, damit ein neu gewählter fester Favorit sofort etwas zeigt, bis die App läuft.

- Der Tagwechsel für das Zitat des Tages folgt ebenfalls 4:00, wie die Bilder.

- Auf der Dahlia-Seite öffnen die Widget-Zeilen nichts, weil iOS die Einstellungen eines Widgets nur vom Home-Bildschirm aus öffnet; der Text sagt, wie es dort geht.

- Die Zeile „Widgets“ steht wie auf Android direkt unter Pro.

- Das Zitat im breiten Widget steht in der Textrolle `body` statt in Androids Serifenschrift, weil Schriften nur über die Textrollen laufen.

- Kleine Widgets, die vor dem ersten App-Start platziert werden, zeigen nur die Fläche (Androids Ladezustand), nicht den Leer-Text, weil es Favoriten gibt, die App sie nur noch nicht geschrieben hat.

- Der Hintergrund-Refresh wird nur geplant, solange Widgets platziert sind.

- Die Seite zum Ankreuzen der Tagesfavoriten baue ich nicht hier, sondern WP-11; bis dahin gehen alle Favoriten reihum.

- Debug-Texte (Schalter für die Marke) nur auf Englisch.
