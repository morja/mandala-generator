# Dahlia – Mandala Maker

Session: master chat · 12 November 2026 · listing texts and console answers: WP-13 store texts · 26 November 2026

## STORE-2: App Store Connect record

What exists in Apple's consoles for the iPhone app, and what is still Mathis' to do there. Team 6735ZK5WKV.

### Created on 12 November 2026 (in Chrome, with Mathis' OK)

| What | Value |
|---|---|
| App ID (Certificates, Identifiers & Profiles) | `one.tearoom.mandala`, explicit, description "Dahlia Mandala Maker", no extra capabilities yet (App Groups follow with WP-10) |
| App record | "Dahlia – Mandala Maker", iOS, primary language English (U.S.), SKU `dahlia-ios`, Apple ID 6833903967, all users have access |
| Version | iOS 1.0, "Prepare for Submission", nothing filled in |
| In-app purchase | "Dahlia Pro", non-consumable, product id `one.tearoom.mandala.pro`, Apple ID 6833904728, status "Prepare for Submission" |
| Family Sharing | on (F4 a; cannot be turned off again) |
| Price | 3.99 € with Germany as base country, Apple's comparable prices in the other 174 countries (US 3.99 $), may adjust automatically (ADR-0016, amended 12 November 2026) |
| Availability | all 175 countries and regions |
| Display name and description, English (U.S.) | Dahlia Pro · "More layers, emblems, text and 2048 px. Pay once." |
| Display name and description, German | Dahlia Pro · "Mehr Ebenen, Embleme, Text und 2048 px. Einmal zahlen." |

The product id is `one.tearoom.mandala.pro`, not `pro` as on Play: an Apple product id cannot be reused once taken, and a bare `pro` would be in the way of Mathis' other apps. The app maps the id per platform (WP-9).

Apple allows 55 characters for the description, so the Play text (STORE-1) was shortened.

### Done by Mathis on 12 November 2026

- Trader declaration for the EU, Paid Apps Agreement, bank account and tax form submitted. The agreement shows "Processing" until Apple has checked it.

### Open, Mathis' own steps

- Wait until the Paid Apps Agreement shows "Active" (usually one to three days).

- App Store Small Business Program (15 % instead of 30 %).

### TestFlight

- Test information entered on 12 November 2026: English beta description, feedback and contact email apps@tearoom.one, marketing and privacy policy links (the privacy page still describes the Android app; it must name the iPhone before the store release), review notes on how to reach the Pro sheet.

- First build 1.0 (1) uploaded on 12 November 2026 from `main` at c33adfc (Release archive, automatic signing, App Group on device and widget extension). Internal group "Team" (Mathis, automatic distribution) and external group "Freunde" created the same day; build 1.0 (1) submitted to Beta App Review for "Freunde" ("Waiting for Review"), without sign-in, with English "What to Test" notes. The External Testing section only appeared after the first internal group existed. Build 1.0 (1) passed Beta App Review by 13 November 2026 ("Approved", one invite, eleven sessions); a public link with a limit of 20 testers exists for "Freunde".

- Build 1.0 (2) uploaded on 13 November 2026 from `main` at 6c7bfa4 (text and glow colours, shadow sliders, cleanup IOS-13); the internal group "Team" gets it automatically, added to the external group "Freunde" the same evening and approved at once (no second review for the same version).

- Upload note: `xcodebuild -exportArchive` fails with "Copy failed" when Homebrew's `rsync` comes first on the PATH; run it with `/usr/bin` first.

- Build 1.0 (3) uploaded on 28 November 2026 from `main` at be16ee6 (iPhone sizes like Android IOS-14, iPad full screen IOS-15, daily wallpaper as a tip, "Remove Pro parts", privacy manifest in app and widget). Internal group "Team" got it automatically; added to "Freunde" with English "What to Test" notes, and Beta App Review passed at once ("Testing"). The friend tester had installed 1.0 (2) on an iPhone SE (3rd generation). Upload needed a fresh Xcode sign-in.

- Build 1.0 (4) uploaded on 28 November 2026 with the team API key (no Xcode sign-in), adds "Start fresh". Added to "Freunde" through the App Store Connect API with English test notes and submitted to Beta App Review ("Waiting for Review"); "Team" got it automatically.

### Open, with the packages

- Screenshots (WP-13, a later step); review screenshot of the product (plan below). Listing texts, privacy label, export compliance, age rating and review notes are prepared below and wait for Mathis to enter them. The first in-app purchase is submitted together with the first app version.

- App Group `group.one.tearoom.mandala` and the capability on the App ID (WP-10).

## Listing and App Store Connect answers for version 1.0 (prepared 26 November 2026)

Prepared for PLAN-4 WP-13, text part only. Nothing of this is entered in App Store Connect yet; Mathis enters it (or gives an agent the OK to do it in Chrome). Screenshots follow in a separate step.

### Listing texts

In `store/ios/<locale>/`, one folder per app language (PLAN-4 F10 a), named with App Store Connect's locale codes: `en-US` (primary), `de-DE`, `es-ES`, `es-MX`, `fr-FR`, `it`, `pt-BR`, `ru`, `ja`, `ko`, `zh-Hans`. `es-MX` is a copy of `es-ES` (checked for Spain-only words: none), so Mexico and Latin America get Spanish as on Play's es-419. Files in fastlane deliver's layout: `name.txt`, `subtitle.txt`, `promotional_text.txt`, `description.txt`, `keywords.txt`, `release_notes.txt`. `store/ios/check-limits` checks every file against Apple's limits (name and subtitle 30, promotional text 170, keywords 100, description and What's New 4000 characters; keywords also in UTF-8 bytes, as a warning).

- Based on the Play texts in `store/android/<locale>/`, changed to what the iPhone app does: no live wallpaper and no "set as wallpaper" (iOS lets no app set it; pictures go to Photos), no daily wallpaper (Mathis, 26 November 2026: on the iPhone it is only a tip in the app, the Shortcuts action stays Pro but is not advertised), Pro wallpapers are home and lock screen as a matching pair, the home screen widget "Mandala of the day" in three sizes (free, changing every morning with Pro), Family Sharing named in the Pro paragraph, 21 example favourites (the number in `starter-favorites.json`; the Play texts still say 23).

- No mention of Android or Google Play anywhere: App Review guideline 2.3.10 rejects metadata that names other mobile platforms. The favourites sentence names only the iPhone and the Mac version. The app's own texts were checked for the same (`Localizable.xcstrings`: none).

- The German text uses "du" like the app and STORE-1; the current Play German text (`store/android/de-DE`) uses "Sie".

- The name stays "Dahlia – Mandala Maker" in English and German; the other languages take their Play title. The subtitle never repeats words of the name, and the keywords never repeat words of name or subtitle (App Store search indexes all three together).

- **What's New** (`release_notes.txt`): App Store Connect shows no "What's New" field for an app's first version. The text is there for TestFlight's "What to Test" and as the start of 1.0.1's notes.

- Promotional text can be changed at any time without a new version; the rest only with a version.

### Decided on 26 November 2026 (Mathis, relayed by the master chat)

- **Privacy manifest:** the app has no `PrivacyInfo.xcprivacy`, but uses APIs Apple asks a reason for (UserDefaults, a file's modification date and size). Mathis agrees to add the manifest with build 3 where needed; it is built with build 3, not in this text step.

- **es-MX:** added as a copy of the Spanish text.

- **French subtitle** "Géométrie sacrée et fonds" (30 characters; "fonds d'écran" would be 33): accepted.

- **Ready shortcut through an iCloud link:** dropped. Mathis decided later the same day that the iPhone app does not offer the daily wallpaper as a feature: it becomes a text tip in the app, the Shortcuts action "Mandala of the day" stays (Pro) but is not advertised. The listing texts in all locales were changed accordingly (no new wallpaper every morning, no Shortcuts automation); the review notes mention the action only as optional.

### Other fields of the version page

| Field | Value |
|---|---|
| Support URL | https://www.tearoom.one/en/apps/dahlia/help |
| Marketing URL | https://www.tearoom.one/en/apps/dahlia |
| Privacy policy URL | https://www.tearoom.one/en/apps/privacy/dahlia (needs the iPhone wording below before release) |
| Copyright | 2026 Mathis Koblin |
| Primary category | Graphics & Design (as `LSApplicationCategoryType` in `project.yml`) |
| Secondary category | Lifestyle (proposal) |
| Price | Free; the in-app purchase "Dahlia Pro" is added to the version under "In-App Purchases and Subscriptions" so it goes to review with it |
| Sign-in required | No |
| Content rights | Does not contain third-party content: quotes are public domain (ADR-0007), the fonts are iOS system fonts, the Kotlin core is Apache-2.0 licensed and listed in About |

### App Privacy (privacy label)

Answer: **"No, we do not collect data from this app"**, which shows "Data Not Collected". Nothing under Tracking.

Checked in the code on 26 November 2026 (`ios/Dahlia/Sources`, `Shared`, `Widget`, and the Kotlin modules `android/core` and `android/editing` that make the framework):

- No network code: no `URLSession`, `URLRequest`, Network framework, sockets or web views; the Kotlin modules have no HTTP client.

- No third-party SDKs: no Swift packages (PLAN-4 section 5), no analytics, crash reporting, ads, attribution or `AdSupport`/App Tracking Transparency.

- The purchase goes through StoreKit 2. Apple handles payment and Apple Account; the app reads only the signed transaction on the device and stores whether Pro is unlocked (`pro-purchase.json` in Application Support, the App Group flag). Apple does not count data that Apple itself processes for a purchase as collected by the developer.

- The rating request is `AppStore.requestReview`; the "Rate" row opens the App Store page. Links (guide, privacy, source, licences) open in Safari. None of this sends data to Tearoom.

- Data stays on the device: settings, history, favourites, own quotes and background pictures, the widget pictures in the App Group. iOS may include them in the user's iCloud or computer backup; that is Apple's processing, not collection by the app.

### Encryption (export compliance)

`ITSAppUsesNonExemptEncryption` is `NO` in the app's Info.plist (`project.yml`), so App Store Connect does not ask per build; builds 1 and 2 went through without the question. If the version page asks: "None of the algorithms mentioned above". The app has no network code and uses no encryption of its own; only iOS's (StoreKit, data protection), which is exempt. No documents and no French declaration are needed.

### Age rating questionnaire

Every answer is "None" or "No", which gives **4+**:

- Features: parental controls No; age assurance No; unrestricted web access No (links open Safari, there is no browser in the app); user-generated content No (text and quotes the user types stay on the device and are not shared through the app); messaging and chat No; advertising No.

- Mature themes: profanity or crude humour None; horror or fear themes None; alcohol, tobacco or drug use or references None.

- Medical or wellness: medical or treatment information None; health or wellness topics No (meditation appears only as a keyword and in quotes, the app gives no health guidance).

- Sexuality or nudity: None. Violence (cartoon, realistic, prolonged graphic or sadistic, weapons): None.

- Chance-based activities: simulated gambling None; contests None; gambling No; loot boxes No.

- Made for Kids: No (not in the Kids category).

The symbols (Om, Dharma wheel, Sri Yantra …) and the spiritual quotes are not a questionnaire item.

### App Review notes (paste into "Notes", English)

> Dahlia makes mandala pictures on the device. There is no account, no sign-in and no network use, so no demo account is needed.
>
> Pro (in-app purchase "Dahlia Pro", one.tearoom.mandala.pro, non-consumable, Family Sharing on)
> - Purchase sheet: tap the ⋯ button in the navigation bar, then Dahlia › Dahlia Pro. It shows what Pro adds, "Buy for …" and "Restore purchase".
> - The sheet also opens from every Pro feature: a free user can try more layers, emblems and text in the editor, and the "Pro" mark on the picture opens the sheet; saving or sharing such a mandala offers Pro or saving without those features.
> - Pro features after buying in the sandbox: up to five layers (Layers tab, +), the Emblem and Text tabs, saving at 2048 px and as a video (the save arrow in the navigation bar, then Picture or Video), the SVG line drawing (⋯ › More › Line drawing) and the wallpaper pair (⋯ › Wallpaper › Save both to Photos).
>
> Optional: Shortcuts action (Pro)
> The app provides the App Intent "Mandala of the day" (parameter Screen: Lock screen or Home screen). It appears in the Shortcuts app under Dahlia without setup and returns today's picture from the user's favourites, for example for "Set Wallpaper Photo". It needs no setup to review the app; without Pro it answers "Mandala of the day comes with Dahlia Pro".
>
> Widget
> Long-press the home screen › Edit › Add Widget › Dahlia › "Mandala of the day" (small, medium, large). The widget is free and shows a favourite; with Pro it changes every morning at 4:00.
>
> Saving to Photos asks for add-only access (NSPhotoLibraryAddUsageDescription). Contact: apps@tearoom.one

The contact name, phone and email under "App Review Information" are Mathis' to enter.

### In-app purchase: review notes and screenshot

Review notes for the product "Dahlia Pro" (English):

> Unlocks up to five layers, emblems, text and quotes, moving layers, saving at 2048 px, as a video and as an SVG line drawing, and the wallpaper pair (home and lock screen). The purchase sheet opens from ⋯ › Dahlia › Dahlia Pro or from any Pro feature; "Restore purchase" is in the same sheet.

Review screenshot plan (taken with the store screenshots, not now):

- What: the Pro sheet as a free user sees it, with "Buy for 3,99 €" or "Buy for $3.99" and "Restore purchase" visible, English UI, over a mandala that uses an emblem so the "This mandala uses" list shows.

- How: iPhone 17 Pro Max simulator (6.9", 1320 × 2868; Apple asks for at least 640 × 920), Release-like build without the debug mark, launched from Xcode with `Config/Dahlia.storekit` so the real price shows; or the debug hooks `-shotSheet pro -shotPrice "$3.99"` with the debug mark switched off. Free state: `-proOwned NO`, and reset the StoreKit test transactions first (IOS-11 finding 7: a cached test purchase keeps Pro on).

- Where: App Store Connect › Dahlia Pro › Review Information › Screenshot. Only App Review sees it.

## Privacy page for tearoom.one: proposal

Approved by Mathis on 26 November 2026 and written into the tearoom.one repository the same day (dated 26 November 2026); goes online with the site's content deploy. It extends the current page (`content/7_apps/7_privacy/5_dahlia/app-document.{en,de}.txt` in the tearoom.one repo, updated 20 October 2026) to the iPhone app. Sections not listed stay as they are (Controller, Legal bases, Your rights, Support, Changes).

### English

**Opening line:** Updated [date of the iPhone release]. This policy covers the app Dahlia – Mandala Maker by Tearoom for Android (package one.tearoom.mandala) and for iPhone (bundle ID one.tearoom.mandala). It does not cover other Tearoom apps.

**In short:** Your mandalas stay on your phone. There is no app account, the app itself makes no internet connections, and it contains no advertising or analytics service. Data leaves the device only when you save or share an image, through the optional Pro purchase on Google Play or the App Store, and through your phone's own backup.

**What the app stores on your device:** The app keeps its data in its private storage: the current settings, the history of your recent mandalas, your favourites with small preview images, quotes you write yourself and copies of pictures you choose as a background. It also remembers whether Pro is unlocked. When you share an image, a temporary copy is kept in the app's cache. On the iPhone the app also keeps the pictures for its home screen widget and, if you use the Shortcuts action, today's and tomorrow's wallpaper pictures in a storage area shared only with its own widget.

**Images and files:** Saving writes the image to your photo gallery (Android: Pictures; iPhone: Photos, with access to add pictures only, which you grant when you first save). Sharing hands the image to the app you choose; that app applies its own terms. When you open or save a settings file, or choose a background picture, you pick the file in the system's file or photo picker, and the app reads or writes only that file. On the iPhone, the Shortcuts action "Mandala of the day" hands today's picture to the Shortcuts app, which sets it as wallpaper on your device.

**Backup:** Android may include the app's private data (settings, history, favourites, your quotes and background pictures) in your device backup to your Google account and in transfers to a new phone; Google processes this backup under its terms, and you can turn it off in Android's settings. On the iPhone, iOS may include the same data in your iCloud backup or a backup on your computer; Apple processes iCloud backups under its terms, and you can turn them off in the iPhone's settings. See [Google's privacy policy](https://policies.google.com/privacy) and [Apple's privacy policy](https://www.apple.com/legal/privacy/).

**Pro purchase:** Dahlia Pro is an optional one-time purchase through Google Play on Android and through the App Store on the iPhone. Google or Apple processes payment, your account and the receipt under its terms. The app receives the purchase state from the store, verifies it on the device and stores locally only whether Pro is unlocked. Tearoom runs no server for this and receives no images or settings. On Android, Google's Play Billing Library included for this sends technical diagnostics about the purchase flow (for example error codes and version details) to Google; these contain no images or settings. On the iPhone the purchase uses Apple's StoreKit, part of iOS; no extra library is included. With Family Sharing, members of your family group can use Pro on the iPhone.

**Permissions:** On Android the app asks for no permissions at runtime. Saving to the gallery uses Android's media store, which needs no permission on Android 10 and later. The Play Billing Library uses Google Play's billing permission to handle the purchase. On the iPhone the app asks once for permission to add pictures to Photos; it cannot see or read your photo library. It may refresh its widget pictures in the background, which uses no personal data.

**Deletion:** You can delete favourites and clear the history in the app. On Android, clearing the app's storage in the settings or uninstalling the app removes all its private data; on the iPhone, deleting the app removes it, including its widget pictures. Images you saved to your gallery or Photos or shared with other apps are not deleted.

### German

**Einleitung:** Stand: [Datum der iPhone-Veröffentlichung]. Diese Erklärung gilt für die App Dahlia – Mandala Maker von Tearoom für Android (Paket one.tearoom.mandala) und für das iPhone (Bundle-ID one.tearoom.mandala). Sie gilt nicht für andere Apps von Tearoom.

**Kurz gesagt:** Deine Mandalas bleiben auf deinem Handy. Es gibt kein App-Konto, die App selbst baut keine Internetverbindungen auf und enthält keine Werbe- oder Analysedienste. Daten verlassen das Gerät nur, wenn du ein Bild speicherst oder teilst, über den optionalen Kauf von Pro bei Google Play oder im App Store und über die Datensicherung deines Handys.

**Was die App auf deinem Gerät speichert:** Die App legt ihre Daten in ihrem privaten Speicher ab: die aktuellen Einstellungen, den Verlauf deiner letzten Mandalas, deine Favoriten mit kleinen Vorschaubildern, selbst geschriebene Zitate und Kopien von Bildern, die du als Hintergrund wählst. Außerdem merkt sie sich, ob Pro freigeschaltet ist. Wenn du ein Bild teilst, liegt vorübergehend eine Kopie im Cache der App. Auf dem iPhone legt die App außerdem die Bilder für ihr Widget und, wenn du den Kurzbefehl nutzt, die Hintergrundbilder für heute und morgen in einem Speicherbereich ab, den nur sie und ihr Widget nutzen.

**Bilder und Dateien:** Beim Speichern legt die App das Bild in deiner Galerie ab (Android: Bilder; iPhone: Fotos, nur mit dem Recht, Bilder hinzuzufügen, das du beim ersten Speichern erteilst). Beim Teilen gibt sie das Bild an die App weiter, die du auswählst; für diese gelten deren eigene Bedingungen. Wenn du eine Einstellungsdatei öffnest oder speicherst oder ein Hintergrundbild wählst, suchst du die Datei in der Datei- oder Fotoauswahl des Systems aus, und die App liest oder schreibt nur diese Datei. Auf dem iPhone gibt die Aktion „Mandala des Tages“ das heutige Bild an die App Kurzbefehle weiter, die es auf deinem Gerät als Hintergrundbild setzt.

**Datensicherung:** Android kann die privaten Daten der App (Einstellungen, Verlauf, Favoriten, deine Zitate und Hintergrundbilder) in die Sicherung deines Geräts in deinem Google-Konto und in die Übertragung auf ein neues Handy aufnehmen; Google verarbeitet diese Sicherung nach seinen Bedingungen, und du kannst sie in den Android-Einstellungen ausschalten. Auf dem iPhone kann iOS dieselben Daten in dein iCloud-Backup oder ein Backup auf deinem Computer aufnehmen; Apple verarbeitet iCloud-Backups nach seinen Bedingungen, und du kannst sie in den Einstellungen des iPhone ausschalten. Siehe [Datenschutzerklärung von Google](https://policies.google.com/privacy) und [Datenschutzrichtlinie von Apple](https://www.apple.com/de/legal/privacy/).

**Kauf von Pro:** Dahlia Pro ist ein optionaler einmaliger Kauf, auf Android über Google Play, auf dem iPhone über den App Store. Google bzw. Apple verarbeitet Zahlung, dein Konto und den Kaufbeleg nach seinen Bedingungen. Die App erhält vom Store den Kaufstatus, prüft ihn auf dem Gerät und speichert lokal nur, ob Pro freigeschaltet ist. Tearoom betreibt dafür keinen Server und erhält keine Bilder oder Einstellungen. Auf Android sendet die dafür eingebundene Play Billing Library von Google technische Diagnosedaten zum Kaufablauf (zum Beispiel Fehlercodes und Versionsangaben) an Google; sie enthalten keine Bilder oder Einstellungen. Auf dem iPhone läuft der Kauf über StoreKit von Apple, das Teil von iOS ist; eine zusätzliche Bibliothek ist nicht eingebunden. Mit der Familienfreigabe können Mitglieder deiner Familiengruppe Pro auf dem iPhone nutzen.

**Berechtigungen:** Auf Android fragt die App zur Laufzeit keine Berechtigungen ab. Das Speichern in der Galerie nutzt den Medienspeicher von Android, der ab Android 10 keine Berechtigung braucht. Die Play Billing Library nutzt die Abrechnungsberechtigung von Google Play, um den Kauf abzuwickeln. Auf dem iPhone fragt die App einmal, ob sie Bilder zu Fotos hinzufügen darf; deine Fotomediathek kann sie damit weder sehen noch lesen. Sie kann ihre Widget-Bilder im Hintergrund erneuern; dabei werden keine personenbezogenen Daten verwendet.

**Löschung:** Favoriten kannst du in der App löschen und den Verlauf leeren. Auf Android entfernt das Löschen des App-Speichers in den Einstellungen oder das Deinstallieren alle privaten Daten der App; auf dem iPhone entfernt das Löschen der App sie, samt der Widget-Bilder. Bilder, die du in der Galerie oder in Fotos gespeichert oder mit anderen Apps geteilt hast, werden dabei nicht gelöscht.

### Also to align when the page changes

- The app's About page links `…/apps/privacy/mandala-generator` (`ios/Dahlia/Sources/UI/AboutView.swift`), which the site redirects to `…/privacy/dahlia`; it works, a direct link would save the redirect.

- The TestFlight test information already points at the privacy URL; nothing to change there.
