# Dahlia – Mandala Maker

Session: QA-13 Android colour sheet and text shadow · 13 November 2026

## QA-13: Farbblatt wie auf dem iPhone, Richtung und Abstand des Textschattens

Kurz: Android übernimmt die zwei Verbesserungen aus IOS-12 (Mathis' Antwort „Ja, übernehmen“ auf IOS-12 F2): Das Farbblatt schließt sich, sobald ein anderes Mandala kommt, und „Abbrechen“ und „Fertig“ stehen im Kopf des Blatts. Dazu hat der Textschatten unter „Schatten“ drei neue Regler, Winkel, Abstand und Weichheit (Vorschlag a). Sie schreiben in die Felder des Mac (`shadowOffsetX`, `shadowOffsetY`, `shadowBlur`), die Regeln stehen im geteilten Modul `android/editing` (`TextShadow`), damit das iPhone sie später übernehmen kann. Geprüft auf dem Emulator mit 360 dp Breite, Deutsch, Schrift 1,0 und 1,3; Fast-Build auf dem Galaxy S23 installiert.

Bilder: `/Users/mathis/Work/Projects/apps/mandala-generator/docs/qa/shots/android-sheet-shadow-2026-11-13/`

### Befunde

1. **Farbblatt schließt bei einem anderen Mandala.** Würfel oben, Zurück, Vor, ein Favorit, eine Datei, das neue Mandala aus dem Menü, „Neue Ebenen“ und die Spielfläche schließen das Blatt und behalten die Farben (`EditorViewModel.anotherMandala()`, wie `replace`, `navigate` und `surprise` auf dem iPhone). „Abbrechen“ kann damit keine alten Farben mehr auf ein neues Mandala setzen (IOS-12 Befund 9). Auf dem Emulator: Rumi-Favorit (Torus, Stern) geöffnet, Farbblatt auf, Textfarbe auf Grün, Würfel: Blatt zu, das neue Mandala trägt das Grün (`05-sheet-colour-changed.png`, `06-after-die-sheet-closed.png`); Zurück bringt den Favoriten mit dem Grün wieder, weil die Änderung in der Geschichte steht (`07-back-colour-kept.png`). Ebenso mit einem Favoriten bei offenem Blatt (`20-favourite-while-sheet-open-closed.png`).

2. **„Abbrechen“ und „Fertig“ im Kopf.** Griff, Titel, „Abbrechen“ und „Fertig“ bilden einen festen Kopf; der Rest scrollt darunter, wenn das Blatt höher ist als der Platz unter dem Bild. So liegen die Knöpfe nie unter dem Rand (`04-sheet-head-360-de.png`). Bei Schrift 1,3 wurde „Textfarbe“ zuerst zu „Textfa…“ gekürzt; „Fertig“ hat jetzt das schmalere Innenpolster von Materials Text-Knopf (12 statt 24 dp je Seite), damit passt der Titel ganz (`08-sheet-head-360-de-font130.png`). Kein neues Token, Scrim und Bildunterschrift bleiben Materials Werte (IOS-12 F1 wie besprochen).

3. **Schattenregler: Winkel, Abstand, Weichheit.** Sie stehen direkt unter „Schatten“ und erscheinen nur, solange der Schatten über 0 steht, wie die Schattenfarbe (`10-shadow-sliders-favourite.png`). Winkel ist wie der Reliefwinkel im Tab Szene ein Bruchteil einer Drehung von 0 bis 1, im Uhrzeigersinn auf dem Bildschirm: 0 rechts, 0,25 unten, 0,5 links, 0,75 oben. Der Standard des Mac (0,3 / −0,3) ist 0,125, rechts unten, derselbe Wert wie der Standard des Reliefwinkels. Abstand 0 bis 2, Weichheit 0 bis 1 (die Bereiche der Mac-Regler). Auf dem Emulator dreht sich der Schatten um den Text: 0,17 rechts unten, 0,43 links, 0,70 oben (`11-angle-0.17-down-right.png`, `12-angle-0.43-left.png`, `13-angle-0.70-up.png`). Abstand 0 legt den Schatten unter die Buchstaben (`14-distance-0.png`); danach bestimmt der Winkelregler, wohin er beim Herausziehen wandert (`15-distance-out-up.png`). Weichheit 0,91 (`16-softness-high.png`). Bei Schrift 1,3 passen die Beschriftungen (`19-shadow-sliders-de-font130.png`).

4. **Mac-Bedeutung der Felder geprüft; der Kommentar im Mac-Code stimmt nicht.** Der Mac rechnet den Versatz als Feld × Schriftgröße × 0,75 und zeichnet mit AppKit (y nach oben): Ein positives `shadowOffsetY` schiebt den Schatten nach oben. Core macht dasselbe (`TextLayer.kt` übergibt `-offY` als Verschiebung nach unten). Bereich und Richtung sind also gleich; nur der Kommentar in `mac/Sources/MandalaGenerator/MandalaParameters.swift` („-1 up … 1 down“) ist verkehrt herum. Die neuen Regler behalten die Bedeutung des Mac; den Kommentar habe ich nicht angefasst (Mac-Code, siehe F1).

5. **Jeder Favorit sieht gleich aus, bis ein Regler bewegt wird.** Die Regler schreiben nur, wenn sie bewegt werden; eine Mac-Datei behält ihre genauen Werte. Ein schräger Versatz über 2 (bis 2,83 aus den Mac-Reglern ±2) bleibt, der Knopf steht dann am Ende. Dass auch ein Berühren ohne Bewegen nichts ändert, zeigt das Werkzeug `RenderHashes`: Die 13 Start-Favoriten mit Schatten wurden einmal unverändert und einmal mit Winkel, Abstand und Weichheit durch die Regeln auf ihre eigenen Werte gesetzt gerechnet (neues Hilfswerkzeug `TextShadowRoundTrip` in den cli-Tests), je 12 Varianten (Vorschau 512, Export 1024, Pro 2048, Hochformat, Finish, drei Threads, drei Animationsbilder samt Videoebenen): 156 von 156 Hashes gleich, obwohl die Felder in der letzten Stelle abweichen (zum Beispiel 0,3 → 0,30000000000000004). Core und Renderer sind unverändert (`git diff main -- android/core` leer), alle 93 Fixtures und 21 Start-Favoriten rendern damit wie vorher; keine Fixture hat einen Schatten. Auf dem Emulator ist der frisch geöffnete Rumi-Favorit vor und nach dem Aufklappen der Regler pixelgleich (`17-favourite-opened-star.png`, `18-favourite-sliders-untouched-star.png`, 0 abweichende Pixel).

6. **Tests.** `TextShadowTest` (commonTest, sechs Fälle): Standard ist 0,125, Richtungen im Uhrzeigersinn mit Y nach oben, Drehen behält den Abstand, Ziehen behält die Richtung, Abstand 0 nimmt die Richtung des Reglers, ein Versatz über 2 bleibt beim Drehen, Weichheit ändert nur `shadowBlur`.

7. **Prüfungen.** `android/scripts/check` und `ios/scripts/check` grün (Hook bei jedem Commit, iOS wegen `android/editing`), `android/scripts/check-full` grün (Parität gegen die macOS-Referenzen, Release-Build). Fast-Build auf dem Galaxy S23 installiert.

8. **Nebenbei gesehen, nicht geändert:** Bei Schrift 1,3 auf 360 dp kürzt die Tab-Leiste „Emblem“ und „Zeichnen“ zu „Emble“ und „Zeichn“, ohne Auslassungspunkte (`08-sheet-head-360-de-font130.png`). Das war schon vorher so und gehört nicht zu dieser Arbeit.

### Offene Fragen

F1. Soll der falsche Kommentar zu `shadowOffsetY` im Mac-Code („-1 up … 1 down“, richtig wäre „-1 down … 1 up“) bei der nächsten Mac-Arbeit berichtigt werden? Empfehlung: a) ja, eine Zeile beim nächsten Mac-Port; b) so lassen.

F2. Die Tab-Leiste bei Schrift 1,3 auf 360 dp (Befund 8): als eigenes kleines Paket ansehen? Empfehlung: a) ja, zusammen mit dem nächsten Durchgang durch die Oberfläche; b) nein.

### Selbst entschieden

- Winkel wie der Reliefwinkel als 0 bis 1 mit zwei Nachkommastellen, nicht in Grad, damit beide Winkelregler der App gleich lesen; Richtung im Uhrzeigersinn auf dem Bildschirm wie beim Relief.

- Beschriftungen „Winkel“ und „Weichheit“ aus den vorhandenen Texten (Verlauf, Sprühdose) wiederverwendet; neu ist nur „Abstand“ (`controls_shadow_distance`) in allen zehn Sprachen. Kurze Wörter statt „Schattenwinkel“, weil sie direkt unter „Schatten“ stehen und lange Wörter bei 1,3 gekürzt würden.

- Die drei Regler stehen direkt in der Gruppe „Licht“ unter „Schatten“, vor der Schattenfarbe, statt in einer eigenen aufklappbaren Gruppe.

- Abstand 0 hat keine Richtung; der Winkelregler merkt sich dann die zuletzt gewählte, damit der Schatten beim Herausziehen dorthin geht.

- Der Punkt „geändert“ der Gruppe „Licht“ zählt Winkel, Abstand und Weichheit nur, solange ein Schatten sichtbar ist (der Würfel des Textstils ändert die Weichheit auch ohne Schatten).

- Auch „Neue Ebenen“ und das Öffnen der Spielfläche schließen das Farbblatt, weil sie das ganze Bild ändern.

- „Fertig“ mit dem Innenpolster des Text-Knopfs (Befund 2), statt den Titel zu verkleinern.

- Den Pixel-Nachweis vorher/nachher nicht noch einmal mit dem alten Stand gerechnet: Core ist unverändert, der einzige Weg der Regler zu den Pixeln sind die drei Felder, und genau der ist mit den 13 Schatten-Favoriten gemessen.
