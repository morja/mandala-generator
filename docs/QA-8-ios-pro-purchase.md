# Dahlia – Mandala Maker

Session: iOS WP-9 Pro purchase · 12 November 2026

## QA-8: Pro-Kauf auf dem iPhone über StoreKit 2 (PLAN-4 WP-9)

Umfang: der einmalige Kauf „Dahlia Pro“ (`one.tearoom.mandala.pro`, Nicht-Verbrauchsartikel, 3,99 €, Familienfreigabe an) in der iPhone-App, gebaut in WP-9 ([IOS-9](IOS-9-pro-purchase.md)). Vorbild ist [QA-4](QA-4-pro-purchase.md) für Android. Die Texte stehen in `ios/Dahlia/Strings/values-de/strings_ios.xml` (App-Store-Fassungen) und in Androids `values-de`.

Die Fälle, die mit der lokalen StoreKit-Konfiguration (`ios/Dahlia/Config/Dahlia.storekit`) laufen, hat der Agent im Simulator mit StoreKitTest erledigt (Testziel `DahliaStoreTests`, läuft in `ios/scripts/check-full` mit). Was Apples Server braucht (Sandbox, echtes Gerät, Familie), steht als Schritt für Mathis darunter.

## Voraussetzungen für Mathis' Fälle

- Der Vertrag für kostenpflichtige Apps steht in App Store Connect auf „Aktiv“ (am 12. November 2026 noch „Processing“). Vorher liefert die Sandbox keinen Preis.

- Ein Sandbox-Tester ist in App Store Connect angelegt (Benutzer und Zugriff → Sandbox), mit einer Adresse, die kein echter Apple Account ist.

- Auf dem iPhone ist der Sandbox-Account angemeldet: Einstellungen → Entwickler → Sandbox-Apple-Account (fehlt der Punkt „Entwickler“, einmal einen Build aus Xcode auf das iPhone spielen).

- Ein Build, der mit dem Team signiert ist: Xcode mit angemeldetem Account und die Team-ID in `ios/Dahlia/Config/Signing.xcconfig` (siehe IOS-9), oder ein interner TestFlight-Build (F11 a). Ein Debug-Build zählt ohne `-proOwned NO` immer als Pro; für die Kauffälle den Release-Build oder TestFlight nehmen, oder den Debug-Build mit dem Startargument `-proOwned NO`.

## Testfälle

### 1. Kauf

Erledigt (Agent, StoreKitTest, 12. November 2026): Der Preis kommt aus der Konfiguration als „3,99 €“. „Für 3,99 € kaufen“ schaltet Pro frei, die Meldung „Danke! Pro ist freigeschaltet.“ erscheint, Pro steht im Zwischenspeicher (`pro-purchase.json` in Application Support) und das App-Group-Merkmal `proUnlocked` für das spätere Widget ist gesetzt. Ein zweiter Kauf ist gesperrt. Ein Kauf von einem anderen Gerät desselben Accounts kommt über `Transaction.updates` an, ohne dass die App etwas tun muss.

Schritte für Mathis (Sandbox, iPhone):

- App ohne Pro öffnen, eine Pro-Markierung antippen (zum Beispiel ein Emblem einschalten und auf „Pro“ oben links im Bild tippen).

- Im Pro-Fenster prüfen, dass der Preis „3,99 €“ lautet, dann „Für 3,99 € kaufen“ tippen und mit dem Sandbox-Account bestätigen.

Erwartung:

- Das Pro-Fenster schließt sich, unten steht „Danke! Pro ist freigeschaltet.“, die Pro-Markierung im Bild ist weg.

- Menü → Dahlia → „Dahlia Pro“ zeigt „Aktiv, danke“ und öffnet die Seite mit dem Dank.

Ergebnis: 

### 2. Wiederherstellen nach Neuinstallation

Erledigt (Agent, StoreKitTest): Ohne Zwischenspeicher (wie nach einer Neuinstallation) bringt StoreKits Berechtigung Pro beim Start zurück, ohne Tippen. „Kauf wiederherstellen“ fragt den App Store und zeigt „Pro ist auf diesem Gerät aktiv.“

Schritte für Mathis (Sandbox, nach Fall 1):

- Dahlia löschen und neu installieren (TestFlight oder Xcode), öffnen, ein paar Sekunden warten.

- Falls doch eine Pro-Markierung zu sehen ist: Pro-Fenster öffnen und „Kauf wiederherstellen“ tippen.

Erwartung:

- Pro ist ohne neuen Kauf wieder da, meist schon beim Start.

- „Kauf wiederherstellen“ zeigt „Pro ist auf diesem Gerät aktiv.“

Ergebnis: 

### 3. Offline mit Pro

Erledigt (Agent, StoreKitTest mit simuliertem Netzfehler): Mit Pro im Zwischenspeicher und ohne Antwort von StoreKit bleibt Pro. Der Preis fehlt mit „Der App Store ist gerade nicht erreichbar …“, und „Kauf wiederherstellen“ sagt „Der App Store war nicht erreichbar, daher wurde nichts geprüft.“; Pro bleibt trotzdem.

Schritte für Mathis (iPhone, mit Pro):

- Flugmodus an, Dahlia aus dem App-Umschalter wegwischen und neu starten.

- Ein Emblem einschalten und speichern.

- Flugmodus aus, Dahlia wieder nach vorne holen.

Erwartung:

- Pro bleibt die ganze Zeit freigeschaltet, keine Pro-Markierung, Speichern geht.

Ergebnis: 

### 4. Offline ohne Pro

Erledigt (Agent, StoreKitTest mit simuliertem Netzfehler): kein Pro, kein Preis, der Kaufknopf ist ausgegraut („Pro kaufen“), darunter „Der App Store ist gerade nicht erreichbar, daher gibt es noch keinen Preis. Prüf die Verbindung und versuch es noch einmal.“ und „Erneut versuchen“. Wieder online lädt „Erneut versuchen“ den Preis.

Schritte für Mathis (iPhone, ohne Pro, zum Beispiel mit einem zweiten Sandbox-Tester):

- Flugmodus an, Dahlia kalt starten, eine Pro-Markierung antippen.

- Flugmodus aus, „Erneut versuchen“ tippen.

Erwartung:

- Offline: kein Pro, der Hinweis wie oben, kein Absturz.

- Online: Der Preis „3,99 €“ erscheint, der Knopf wird aktiv.

Ergebnis: 

### 5. Abgebrochener Kauf

Erledigt (Agent, StoreKitTest mit simuliertem Abbruch): kein Pro, keine Meldung, der Kaufknopf ist danach wieder aktiv.

Schritte für Mathis (Sandbox):

- Ohne Pro „Für 3,99 € kaufen“ tippen und das Kaufblatt des App Stores mit dem Schließen-Knopf verlassen.

Erwartung:

- Kein Pro, keine Meldung, das Pro-Fenster bleibt bedienbar und „Für 3,99 € kaufen“ ist aktiv.

Ergebnis: 

### 6. Ausstehender Kauf (Kaufen anfragen)

Erledigt (Agent, StoreKitTest mit „Ask to Buy“): Der Kauf steht aus, im Fenster steht „Der Kauf wartet auf Zustimmung oder Bezahlung. Pro wird freigeschaltet, sobald er durch ist.“, der Kaufknopf ist gesperrt. Nach der Zustimmung kommt der Kauf über `Transaction.updates`, Pro ist frei und die Meldung „Danke! Pro ist freigeschaltet.“ erscheint.

Nicht als bestanden gewertet: die Ablehnung. StoreKitTest in Xcode 27.2 führt einen abgelehnten Kauf danach als gültige Berechtigung (IOS-9, Befund 3). Laut Apple schickt eine echte Ablehnung keine Transaktion; das prüft Fall 6b.

Schritte für Mathis (6b, Xcode mit der lokalen Konfiguration, kein Account nötig):

- Das Xcode-Projekt erzeugen und öffnen: im Repo `ios/Dahlia/scripts/generate`, dann `open ios/Dahlia/Dahlia.xcodeproj`.

- In Xcode das Schema „Dahlia“ wählen, Product → Scheme → Edit Scheme… → Run → Arguments: `-proOwned NO` hinzufügen. Die StoreKit-Konfiguration ist dort schon eingetragen.

- In Xcode die Datei `Config/Dahlia.storekit` öffnen und Editor → Enable Ask to Buy wählen. Dann im iPhone-17-Simulator starten und Debug → StoreKit → Manage Transactions… öffnen.

- In Dahlia ein Emblem einschalten, „Pro“ antippen, „Für 3,99 € kaufen“.

- In „Manage Transactions“ den wartenden Kauf ablehnen („Decline“), Dahlia einmal in den Hintergrund und zurück holen.

Erwartung:

- Nach dem Kauf: der Hinweis „Der Kauf wartet auf Zustimmung …“, kein Pro.

- Nach der Ablehnung: kein Pro. Wird Pro trotzdem frei, ist es derselbe Fehler von StoreKitTest wie oben; dann bitte notieren, der Agent prüft es in TestFlight.

Ergebnis: 

### 7. Erstattung

Erledigt (Agent, StoreKitTest): Eine Erstattung, während die App läuft, entfernt Pro über `Transaction.updates`. Eine Erstattung, während die App zu ist, entfernt das zwischengespeicherte Pro beim nächsten Start, sobald StoreKit sie kennt. Bis dahin bleibt der Zwischenspeicher, wie bei Android.

Schritte für Mathis (Xcode-Simulator wie in 6b, nach einem Kauf dort):

- Debug → StoreKit → Manage Transactions…, den Kauf „Dahlia Pro“ auswählen und erstatten („Refund Purchases“).

- Dahlia ansehen; dann Dahlia beenden und neu starten.

Erwartung:

- Pro ist weg, die Pro-Markierung kommt zurück, nach dem Neustart weiterhin kein Pro.

Später, mit der fertigen App (wie QA-4 Fall 4): echter Kauf, Erstattung über reportaproblem.apple.com, nach Apples Bestätigung App öffnen; Pro muss verschwinden.

Ergebnis: 

### 8. Familienfreigabe

Erledigt (Agent, Regeltests ohne StoreKit): Ein von einem Familienmitglied geteilter Kauf zählt wie ein eigener; nimmt Apple die Freigabe zurück (Transaktion mit `revocationDate`), ist Pro weg, außer es gibt zusätzlich einen eigenen Kauf. StoreKitTest kann keine Familie nachstellen.

Schritte für Mathis (zwei iPhones in derselben Familie, Kaufteilung an; in der Sandbox nur, wenn App Store Connect für Sandbox-Tester eine Familie anbietet, sonst nach dem Start im App Store):

- Auf iPhone A Pro kaufen (Fall 1).

- Auf iPhone B (anderes Familienmitglied) Dahlia installieren und öffnen; falls nötig „Kauf wiederherstellen“.

- Auf iPhone A unter Einstellungen → Name → Familie → Kaufteilung die Freigabe ausschalten, auf iPhone B Dahlia neu öffnen.

Erwartung:

- iPhone B hat Pro ohne eigenen Kauf.

- Nach dem Ausschalten verliert iPhone B Pro (Apple meldet das, sobald es die Änderung verteilt hat; das kann dauern).

Ergebnis: 

### 9. Pro-Fenster auf Deutsch und Russisch in der kleinsten Breite

Erledigt (Agent, Simulator iPhone SE 3. Generation, 375 pt, dunkel): Das Pro-Fenster mit Emblem-Hinweis, Preis, „Entfernen“ / „Убрать их“ und „Kauf wiederherstellen“ / „Восстановить покупку“ passt ohne Abschneiden, auch mit dem Hinweis „App Store bietet Pro gerade nicht an“ und „Erneut versuchen“ (Russisch). Über dem Speichern-Fenster und über dem Menü öffnet es sich oben drauf. Bilder: `docs/qa/shots/ios-wp9-2026-11-12/` im Hauptordner (von Git ignoriert): `pro-sheet-de-se.png`, `pro-sheet-ru-se.png`, `pro-sheet-ru-se-no-price.png`, `pro-over-save-de-se.png`, `pro-over-menu-en-se.png`, `menu-pro-page-de-se.png`, `menu-dahlia-free-ru-se.png`.

Schritte für Mathis (iPhone, ohne Pro):

- iPhone-Sprache auf Deutsch, Pro-Fenster über die Pro-Markierung öffnen; dann dasselbe auf Russisch (Einstellungen → Apps → Dahlia → Sprache).

Erwartung:

- Nichts ist abgeschnitten, der Preis steht im Knopf in Euro, alle Knöpfe sind erreichbar.

Ergebnis: 
