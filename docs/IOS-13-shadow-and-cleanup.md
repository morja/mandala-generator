# Dahlia – Mandala Maker

Session: IOS-13 iPhone shadow sliders and cleanup · 13 November 2026

## IOS-13: Schattenregler, Tour, Dankesmeldung, Wallpaper-Knopf, Herunterziehen, verlässliche Kauf-Tests, Videotempo

Kurz: Das iPhone hat jetzt die drei Schattenregler aus QA-13, eine Tour ohne feste Wartezeiten, die Dankesmeldung nach dem Kauf erst nach dem Pro-Blatt, den Knopf „Wallpaper“ neben Speichern, das Wachsen des Bildes beim Herunterziehen und Kauf-Tests, die zehnmal hintereinander grün laufen. Beim Videoexport ist gemessen, wohin die Zeit geht: fast nur ins Rendern im gemeinsamen Kern. Geprüft im Simulator auf dem iPhone SE (3. Generation, kleinstes unterstütztes Gerät) und dem iPhone 18 Pro Max, dunkel und hell, Englisch und Deutsch.

Bilder: `/Users/mathis/Work/Projects/apps/mandala-generator/docs/qa/shots/ios-cleanup-2026-11-13/`

### Befunde

1. **Schattenregler im Tab Text.** Winkel, Abstand und Weichheit stehen unter „Schatten“ und erscheinen nur, solange der Schatten über 0 steht, vor der Schattenfarbe. Die Regeln kommen aus `TextShadow` in `android/editing` über neue Funktionen in `IosEditingParts` (Bereiche, Standard 0,125, Richtung als Swift-Optional, `withShadowDirection/Distance/Softness`). Wie auf Android merkt sich der Winkelregler die letzte Richtung, solange der Abstand 0 ist, und der Punkt „geändert“ der Gruppe „Licht“ zählt die drei Werte nur bei sichtbarem Schatten. Die Texte gab es schon (QA-13). Im Simulator: Rumi-Favorit (Versatz 0,72 / −0,33) zeigt Winkel 0,07 und Abstand 0,79, wie gerechnet; Winkel auf 0,52 legt den Schatten links neben die Buchstaben (`01-se-dark-en-shadow-sliders.png`, `02-se-dark-en-shadow-angle-left.png`, `12-max-dark-de-shadow-sliders.png`).

2. **Tour ohne Verzögerung (QA-10 Punkt 1).** Die festen Wartezeiten (150 ms für den Tab, 450 ms fürs Scrollen) sind weg. Alle Tabs leben ständig im Hintergrund, also beginnt das Scrollen sofort; die Karte wechselt sofort, die Ringe erscheinen, sobald die Liste ruht, wie Android, das den Schritt nach `animateScrollTo` setzt. Ein Schritt ohne Scrollen bekommt seine Ringe sofort, als Rückfall spätestens nach 350 ms. Die Fixes aus IOS-8 bleiben (Karte hält ihren Platz, Meldungen warten während der Tour). Geprüft Schritte 3 bis 8 und Fertig auf beiden Geräten (`04` bis `06`, `13` bis `15`).

3. **Einmaliger Hänger in der Tour, nicht reproduzierbar.** Beim ersten Durchgang auf dem iPhone SE hing die App beim Wechsel von Schritt 5 zu 6 in einer Layout-Schleife des Scrollens (Hauptthread 100 %, Stack in `hang-sample-tour-step5to6.txt`, Bild `06-se-dark-en-tour5-hang.png`). In fünf weiteren Versuchen, auch mit schnellen Doppeltaps, und im Durchgang auf dem Pro Max kam er nicht wieder. Das Review sah einen möglichen Weg: Die Ringe kamen manchmal, während die Liste noch scrollte, und das Verkleinern des Bildes für die Karte veränderte dann eine Liste in Bewegung. Behoben: Die Ringe warten jetzt, bis die Liste ruht, und die Scroll-Versätze werden nicht mehr als beobachteter Zustand bei jedem Frame geschrieben. Ob das der Hänger war, lässt sich nicht beweisen; auf dem iPhone bitte darauf achten.

4. **Dankesmeldung nach dem Kauf (QA-10 Punkt 5).** Die Meldung wartet, bis Dahlias Pro-Blatt wirklich weg ist (`onDismiss`), und jede Meldung zählt ihre 4 Sekunden nur, solange Dahlia aktiv ist; die Zeit hinter Apples Kaufblatt zählt nicht. Im Simulator mit einem simulierten Kauf (neuer Debug-Schalter `-shotBuy YES` mit `-shotSheet pro`): bei 6,0 s noch das Blatt ohne Meldung dahinter, ab etwa 6,5 s die Meldung, bei 11,0 s weg (`thanks-t6.0.png`, `thanks-t10.0.png`, `thanks-t11.0.png`). **Apples eigenes Kaufblatt ließ sich nicht zeigen:** Die StoreKit-Konfiguration greift nur beim Start aus Xcode, ein Start über `simctl` fragt stattdessen nach einer Apple-Anmeldung (ich habe den Dialog abgebrochen und nichts eingegeben). Der echte Ablauf ist ein Prüfschritt auf dem iPhone 15.

5. **„Wallpaper“ neben Speichern.** Im Tab Bild steht links neben Speichern „Wallpaper“ („Hintergrund“ auf Deutsch, Androids Text `save_wallpaper`); im Tab Video bleibt dort wie auf Android „Teilen“. Der Knopf öffnet die Wallpaper-Seite aus IOS-11 als Blatt über dem Speicherblatt; „Zurück“ heißt dort „Bild speichern“, Speichern schließt die Seite, Fortschritt und Meldung erscheinen im Speicherblatt; ein Pro-Kauf von dieser Seite zeigt seinen Dank auf ihr. Androids Knopf öffnet ein Menü mit Sperrbildschirm, Startbildschirm, beide, Paar und Live; das iPhone kann kein Hintergrundbild setzen, deshalb die Seite (`08`, `10`, `11`). Zum Bildausschnitt siehe F1.

6. **Herunterziehen lässt das Bild wachsen.** Zieht man die Liste eines Tabs über ihren Anfang hinaus, wächst das Bild mit; beim Loslassen bleibt es stehen oder rastet nahe an klein oder voll ein (Androids `PreviewSize.snapped`, Abstand 0,1). Nur ein Ziehen zählt, kein Schwung; beim Verschieben, Zeichnen, Tippen und in der Tour nicht. Umgesetzt mit SwiftUIs Scroll-Geometrie und Scroll-Phase, ohne eigene Gesten. Unterschied zu Android: iOS liefert nicht den Fingerweg, sondern den Überzug des Gummibands; ein Faktor 4,5 bringt das Bild bei mittleren Zügen etwa auf Fingerweg (SE: 80 pt Finger, etwa 100 pt Bild; Pro Max: 150 pt Finger, etwa 130 pt Bild), bei sehr langen Zügen bleibt es etwas zurück. In mittleren Größen steht das Bild links, voll in der Mitte (Android schiebt es dazwischen gleitend) (`03`, `16`).

7. **Kauf-Tests verlässlich.** Drei Ursachen gefunden:
   - Wird direkt nach einem Kauf erstattet, verlor StoreKitTest die Erstattung manchmal ganz (keine widerrufene Transaktion, kein Update, 30 s Warten). Testsitzung und StoreKit laufen über getrennte Verbindungen; eine Abfrage über StoreKit vor jeder Aktion der Testsitzung ordnet sie.
   - Tests lasen die Meldung, während der Store noch auf `finish()` wartete, oder die Startprüfung des Stores schaltete Pro still frei, bevor das Update kam. Die Tests warten jetzt auf den Start und auf die Meldung; der Rennen-Test nimmt seine Transaktion direkt aus dem Kaufergebnis.
   - Der eigene Listener der Gastgeber-App nahm Transaktionen weg; er startet nicht mehr, solange die App nur Tests beherbergt.

   Gewartet wird über Observation statt fester Pausen, jeder Store hört nach seinem Test auf. Nachweis: zehn Läufe von `DahliaStoreTests` hintereinander grün (je 27 Tests), danach nach den Review-Korrekturen noch einmal zehn ab kaltem Simulator grün.

8. **Videotempo gemessen.** Neue Aufschlüsselung im Debug-Log (`video timing per frame ms`). Simulator, 1080 × 1920, 5 s (120 Bilder), Rumi-Favorit, je zweimal:

   | Variante | gesamt je Bild | Rendern | Umrechnung + Kopie | Warten auf Encoder |
   |---|---|---|---|---|
   | vorher (alte Reihenfolge) | 305 / 298 ms | 301 / 295 ms | 1,6 ms | 2 ms |
   | nachher (Überlappung) | 301 / 298 ms | 298 / 294 ms | 1,6 ms | 0,1 ms |
   | 720 × 1282 (zum Vergleich) | 208 ms | 205 ms | 0,7 ms | 2 ms |

   Die Ebenen-Bilder werden schon wiederverwendet (LayerCache wie auf Android), Puffer kommen aus dem Pool des Writers. Das Rendern je Bild sind vor allem die Weichzeichner der Szene-Effekte, das Drehen der gemerkten Ebenen und Farbraum-Umrechnungen im Kern. Geändert: Das nächste Bild wird gerendert, während der Encoder noch arbeitet. Auf einem ruhigen Mac bringt das nichts Messbares; bei der ersten Messung, als der Mac ausgelastet war, wartete der (im Simulator softwarebasierte) Encoder 119 ms je Bild, das ist jetzt verdeckt. Das Bild ist unverändert. Ein spürbarer Gewinn ginge nur über den Kern (siehe F2) oder über andere Standards; 720 ist schon die Standardgröße, 1080 die Pro-Wahl.

9. **Review (Opus).** Sechs Punkte, fünf behoben: ein Ziehen konnte bei Tabwechsel während des Ziehens „hängen bleiben“ (jetzt endet es immer mit dem Finger und bei jedem Tabwechsel); Ringe vor ruhender Liste (Befund 3); fehlende Meldung auf der Wallpaper-Seite; der Start eines Test-Stores lief in den nächsten Test; ein Test ohne Zeitgrenze. Nicht geändert: der Faktor statt des Fingerwegs beim Ziehen (Befund 6), weil eine zusätzliche Ziehgeste mit dem Scrollen der Liste kollidiert.

10. **Prüfungen.** Hook bei jedem Commit grün (`android/scripts/check` wegen `android/editing`, `ios/scripts/check`). `ios/scripts/check-full` grün: Parität unverändert (96 Fixtures, 74 identisch, schlechtester Wert 82,9 dB; Textfälle wie bisher), `DahliaTests` und `DahliaStoreTests` grün, unsignierter Release-Build gebaut. `android/scripts/check-full` grün (Parität gegen die macOS-Referenzen, Release-Build). Debug-Build auf dem iPhone 15 installiert; gestartet hat er nicht, weil das Telefon gesperrt war. Im Worktree fehlte die Signier-Datei (`ios/Dahlia/Config/Signing.xcconfig` hatte nur den Platzhalter); ich habe die aus dem Haupt-Checkout kopiert, sie ist von Git ausgenommen.

### Für Mathis auf dem iPhone 15

- Text-Tab, ein Favorit mit Schatten (zum Beispiel Rumi „Embrace the Soul“): Gruppe Licht öffnen, Winkel, Abstand, Weichheit bewegen; Doppeltipp setzt zurück.
- Menü › Dahlia › Tour: zügig durchtippen; erscheinen Karte und Ringe ohne merkliche Pause? Bleibt die App bei schnellem Tippen flüssig (Befund 3)?
- Mit dem nächsten TestFlight-Build Pro kaufen (Sandbox): Kommt „Thanks! Pro is unlocked.“ nach Apples „You're all set“ und bleibt etwa 4 Sekunden?
- Speicherblatt › Bild › Wallpaper: Seite öffnet sich über dem Blatt, „Save both to Photos“ speichert, die Meldung erscheint im Speicherblatt.
- In einem Tab mit kleinem Bild die Liste am Anfang nach unten ziehen: wächst das Bild angenehm mit dem Finger?

### Offene Fragen

F1. Bildausschnitt der Wallpaper-Seite aus dem Speicherblatt: Android setzt aus dem Speicherblatt ein einzelnes Hintergrundbild mit dem dort gewählten Ausschnitt (Ganz oder Füllen), das Paar aber immer mit Füllen. Die iPhone-Seite zeigt immer Füllen (mit Pro das Paar, ohne Pro ein Bild). a) so lassen, die Seite zeigt ja, was gespeichert wird (Empfehlung); b) ohne Pro das eine Bild mit dem Ausschnitt des Speicherblatts speichern.

F2. Videotempo: Der einzige große Hebel ist das Rendern im gemeinsamen Kern (Weichzeichner der Szene-Effekte und Drehen der Ebenen je Bild), das beide Plattformen beträfe. a) ein eigenes Kern-Paket „schnellere Effekte bei gleichem Bild“ mit Paritätstest, erst messen, wie viel das Weichzeichnen auf dem Gerät ausmacht (Empfehlung); b) so lassen. Standards (720, Länge) habe ich nicht angefasst.

### Selbst entschieden

- Der Knopf „Wallpaper“ öffnet die vorhandene Seite statt eines Menüs wie auf Android, und stellt das Format des Speicherblatts nicht auf Bildschirm um (Android tut das, damit der Rahmen das Hintergrundbild zeigt; die Seite zeigt es selbst).

- „Zurück“ auf der Wallpaper-Seite heißt aus dem Speicherblatt „Bild speichern“ (vorhandener Text), nicht „Menü“.

- Herunterziehen mit Faktor 4,5 auf den Gummiband-Überzug, im Simulator so abgestimmt, dass das Bild bei mittleren Zügen etwa dem Finger folgt.

- Rückfallzeit der Tour 350 ms statt 450 ms, Scroll-Animation 0,25 s.

- Meldungen zählen ihre Zeit nur bei aktiver App; das gilt für alle Meldungen, nicht nur für den Dank.

- Debug-Schalter `-shotBuy` für den simulierten Kauf; die Video-Aufschlüsselung bleibt im Debug-Log.

- Die Überlappung von Rendern und Kodieren bleibt, obwohl sie auf ruhigem Mac nichts misst, weil sie bei Last die Wartezeit verdeckt und das Bild gleich bleibt.
