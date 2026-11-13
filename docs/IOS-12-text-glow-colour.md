# Dahlia – Mandala Maker

Session: iPhone text and glow colour (DM-24 A2), QA-10 items 4 and 7 · 13 November 2026

## IOS-12: Textfarbe und Leuchtfarbe auf dem iPhone, Tastatur im Text-Tab, Doppeltipp auf Reglern

Kurz: Das Text-Tab des iPhones hat jetzt die Farbgruppe aus DM-24 A2 wie Android (QA-12): zwei Punkte im Kopf, Schalter Text | Leuchten, Bildfarben plus Weiß, „Hervorheben“, das Blatt „Weitere Farben“ mit Feld, Farbtonstreifen, Vorher/Nachher und kleinem Live-Bild beim Ziehen, und die eingeklappten Feinregler. Alle Regeln kommen aus dem geteilten `TextColours` über die neue Fassade `IosTextColours`; Swift rechnet keine Farbe selbst. Dazu zwei Punkte aus QA-10: Tippen im Text-Tab (Punkt 4) und Doppeltipp auf Reglern (Punkt 7). Geprüft im Simulator auf dem iPhone SE (3. Generation, kleinstes unterstütztes Gerät) und dem iPhone 18 Pro Max, dunkel und hell, Englisch und Deutsch, mit Mathis' Kabir-Datei und Start-Favorit 2.

Bilder: `/Users/mathis/Work/Projects/apps/mandala-generator/docs/qa/shots/ios-text-glow-2026-11-13/` (vom Git ignoriert).

### Befunde

1. **Gleiche Regeln wie Android, nicht nachgebaut.** `android/editing/src/iosMain/.../IosTextColours.kt` reicht `TextColours` und `TextColourSheet` an Swift weiter (Farbe setzen, Bildfarbe mit Boost, Feldpunkt, Farbtonstreifen, „Wie der Text“, Abbrechen, Bereiche und Standardwerte der Feinregler) und macht die zwei kleinen Renders, die Android im `EditorViewModel` macht: das Bild ohne Text (160 px) für Bildfarben und „Hervorheben“ und das Live-Bild (256 px, eigener Ebenen-Cache). Wie auf Android schauen die Bildfarben erst 600 ms nach dem Ruhen des Bildes und nur bei offener Gruppe hin; eine Änderung nur am Text behält sie; doppelte Tipps auf „Hervorheben“ werden ignoriert. Android-Code ist nicht geändert.

2. **Start-Favorit 2 (Whitman) und Kabir wie auf Android.** „Hervorheben“ gibt bei Whitman grünes Leuchten und fast weiße Buchstaben, mit der seit heute geltenden Grenze 1,2 für die Leuchtstärke bleibt der Text lesbar (`02-se-dark-en-starter2-stand-out.png`). Bei Kabir orangerotes Leuchten und fast weiße Buchstaben mit Orangestich, wie in QA-12 (`08b-se-dark-en-kabir-stand-out-large.png`, hell und deutsch `23-se-light-de-kabir-standOut.png`). Die Bildfarben bei Kabir: Orangerot, Grün, Türkis, Cyan, zwei Blau, Violett und Weiß (`07-se-dark-en-kabir-group.png`).

3. **Live-Bild beim Ziehen: billig und ohne Stau.** Solange das Blatt offen ist, zeichnet jede Änderung sofort ein 256-px-Bild; die volle Vorschau kommt 150 ms nach dem Loslassen. Anders als Android bricht ein neuer Fingerschritt das laufende Live-Bild nicht ab: Es läuft immer genau eines, danach das neueste. So erscheint das Bild im Takt, in dem das Gerät zeichnet, auch wenn der Finger schneller ist; ein Live-Bild überdeckt nie eine fertige Vorschau. Im Simulator (Mac-CPU) über 802 Live-Bilder bei Whitman: Median 7 ms, 95 % unter 21 ms, höchstens 70 ms. Auf dem iPhone 15 nicht gemessen. Bilder: `04-se-dark-en-starter2-sheet-dragging.png` (während), `04b-…-after-drag.png` (danach), `33-promax-light-de-kabir-sheet-dragging.png`.

4. **Abbrechen, Fertig, Wischen, anderer Tab.** „Abbrechen“, ein Tipp auf den Schleier über dem Blatt und ein Wisch nach unten am Blattkopf stellen beide Farben wieder her; „Fertig“ behält sie; ein anderer Tab schließt das Blatt und behält sie (wie Android). Nach Abbrechen ist der Stern wieder gefüllt, der gespeicherte Favorit ist also unverändert (`06-se-dark-en-starter2-after-cancel.png`, `13-se-dark-en-after-swipe-down-cancel.png`).

5. **Blatt auf großem und kleinem Telefon.** Mit großem Bild passt das Blatt weder auf dem SE noch auf dem Pro Max ganz unter das Bild; der Rest scrollt im Blatt (wie Android bei 411 dp). Damit „Abbrechen“ und „Fertig“ trotzdem immer sichtbar sind, stehen sie im Kopf des Blatts neben dem Titel statt unten (`31-promax-dark-en-starter2-sheet.png`, `24-se-dark-de-kabir-sheet-head.png`). Mit kleinem Bild passt das ganze Blatt auch auf dem SE (`03b-se-dark-en-starter2-sheet-folded.png`).

6. **Gefunden und behoben beim Durchsehen:** Die Beschriftungen im Gruppenkopf brachen auf dem SE zu „T…“ und „Gl…“ ab, jetzt behalten sie ihre Wörter und der leere Platz daneben weicht (`01-se-dark-en-starter2-group.png`); der Schalter Text | Leuchten wurde im Blatt in die Höhe gezogen, wenn Platz übrig war, jetzt hat er seine eigene Höhe.

7. **QA-10 Punkt 4, Tastatur im Text-Tab.** Solange ein Feld die Tastatur hat, klappt das Bild klein und kommt danach in der gewählten Größe zurück; das Feld rückt nach dem Einklappen oben ins Tab; über der Tastatur steht „Fertig“ (beim Autor auch die Eingabetaste); Scrollen, ein anderer Tab oder ein Blatt nehmen die Tastatur weg. Im Simulator mit Fingertipp auf „Fertig“ geprüft (`09-se-dark-en-typing-words.png`, `09b-se-dark-en-typing-author.png`, `34-promax-light-de-typing-words.png`). QA-10 ist nachgetragen.

8. **QA-10 Punkt 7, Doppeltipp auf Reglern.** `SliderRow` nutzt jetzt den System-Regler (UISlider) mit eigenem Doppeltipp-Erkenner, der dem Regler alle Berührungen lässt. Im Simulator mit Fingergesten: Doppeltipp auf die Reglerlinie setzt zurück (Farbton 0,73 auf 0, `10-…before-double-tap.png`, `11-…after-double-tap.png`), Ziehen am Knopf geht weiter, ein senkrechter Wisch, der auf einem Regler beginnt, scrollt die Liste und lässt den Wert. Gilt für alle Regler der App. QA-10 ist nachgetragen.

9. **Review durch einen Opus-Subagenten.** Zehn Punkte, keiner blockierend; umgesetzt:
   - Wurde der Text ausgeschaltet, während die Tastatur offen war, blieb das Bild klein; ebenso, wenn man beim Tippen das Farbblatt öffnete (die Tastatur blieb über dem Blatt). Beides behoben.
   - Würfel, Zurück, Vor, ein Favorit oder eine Datei bei offenem Blatt: „Abbrechen“ hätte danach die alten Textfarben auf das neue Mandala gesetzt. Jetzt schließt jeder Wechsel des ganzen Mandalas das Blatt und behält die Farben. Android hat dieselbe Lücke (die obere Leiste bleibt dort ebenfalls bedienbar), siehe F2.
   - Stufen-Regler (Symmetrie, ganze Zahlen) blieben nach dem Loslassen zwischen zwei Stufen stehen; jetzt rasten sie ein, die Stufen zählen ab dem unteren Ende und bleiben im Bereich (`14-se-dark-en-step-slider-snaps.png`). VoiceOver liest den Wert wie rechts in der Zeile und bietet das Zurücksetzen am Regler selbst an (mit VoiceOver nicht geprüft).
   - Kein Live-Bild für einen Zustand, der gerade schon gezeigt wurde; ein verspätetes Bild ohne Text überschreibt nicht mehr die Bildfarben eines neueren Bildes.
   - Änderungspunkt der Gruppe, Standardwerte und Helligkeitsbereich der Feinregler waren in Android und in der neuen Fassade je einmal geschrieben; sie sind jetzt geteilte Regeln in `TextColours` (mit Test), Android nutzt sie auch.
   - Die Prozentwerte, die VoiceOver am Farbfeld liest, kommen aus dem Formatter der Sprache.
   Nicht umgesetzt: Ein Doppeltipp auf den Regler kann das Zurücksetzen zweimal auslösen (Regler und Zeile); das ändert nichts, weil der zweite Aufruf denselben Wert setzt. Die beiden Deckkraft-Werte für Schleier (0,32) und Bildunterschrift (0,6) sind feste Konstanten wie auf Android, kein Token, siehe F1.

10. **Prüfungen.** `ios/scripts/check` und `android/scripts/check` (Hook) grün bei jedem Commit. `android/scripts/check-full` grün (Parität gegen die macOS-Referenzen, Release-Build). `ios/scripts/check-full`: Parität unverändert (96 Fixtures, 74 identisch, schlechtester Wert 82,9 dB; Textfälle wie bisher), `DahliaTests` 28 von 28, unsignierter Release-Build gebaut. Die Kauf-Tests `DahliaStoreTests` schlugen in vier Läufen wechselnd fehl (je ein bis zwei von 27, mal Rückerstattung, mal Kauf von anderswo, mal das Rennen); das ist das bekannte Wackeln aus IOS-10 Befund 10, auch auf `main`, diese Arbeit berührt `ProStore` nicht. Debug-Build auf dem iPhone 15 installiert; gestartet hat er nicht, weil das Telefon gesperrt war.

### Offene Fragen

F1. Der Schleier über dem Tab-Bereich (32 % Schwarz) und das Feld hinter den Bildunterschriften (60 %) sind feste Werte im Code, wie auf Android, wo sie aus Material kommen. Sollen sie neue Design-Tokens werden (braucht deine Freigabe), oder bleiben sie so? Empfehlung: a) so lassen, weil es Androids Standardwerte sind; b) zwei Tokens `scrim` und `captionShade` für beide Apps.

F2. Android hat dieselbe Lücke wie in Befund 9 (Würfel, Zurück oder ein Favorit bei offenem Farbblatt, danach „Abbrechen“) und die Knopfzeile unten, die bei 411 dp unter dem Rand liegt. Soll Android beides vom iPhone übernehmen (Blatt schließt bei neuem Mandala, „Abbrechen“ und „Fertig“ im Kopf)? Empfehlung: a) ja, als kleines Folgepaket; b) nein, Android bleibt wie es ist.

### Selbst entschieden

- „Abbrechen“ und „Fertig“ im Kopf des Farbblatts statt unten wie auf Android, damit sie bei großem Bild nicht unter dem Rand liegen (Befund 5). Android könnte das übernehmen; dort liegt die Knopfzeile bei 411 dp ebenfalls unter dem Rand.

- Das Farbblatt liegt wie auf Android über dem Tab-Bereich, nicht als iOS-Blatt über dem ganzen Bildschirm, damit das Bild voll sichtbar bleibt. Der Wisch nach unten am Kopf ersetzt Androids Zurück-Geste.

- Der Schleier über dem Rest des Tab-Bereichs ist 32 % des Canvas-Schwarz, derselbe Wert wie Androids `BottomSheetDefaults.ScrimColor`; kein neuer Token.

- Live-Bild ohne Abbruch durch den nächsten Fingerschritt (Befund 3), statt Androids „der neueste bricht ab“.

- Doppeltipp über einen UISlider mit eigenem Erkenner statt eines selbstgezeichneten Reglers: Bedienung, Scrollen und VoiceOver bleiben die des Systems.

- Symbol für „Hervorheben“: das SF-Symbol `circle.righthalf.filled`, das Androids halb gefülltem Kreis entspricht.

- Änderungspunkt, Standardwerte und Helligkeitsbereich der Feinregler als geteilte Regeln in `TextColours`; Androids Farbgruppe nutzt sie jetzt auch (gleiches Verhalten, Android-Prüfungen grün).

- Debug-Startargumente `-shotTextColour group|glow|fineTune|standOut|sheet|drag|cancel` und `-shotTyping words|author` für Bildschirmfotos ohne Tippen; eine Zeitmesszeile `live render=…ms` im Protokoll.

### Für Mathis auf dem iPhone 15

Die Debug-App (Symbol mit Marke) ist installiert.

- Text-Tab, Gruppe „Farbe“ öffnen, „Hervorheben“ tippen, dann „Weitere Farben“: im Feld ziehen und darauf achten, ob das Bild beim Ziehen flüssig mitgeht; danach „Abbrechen“ und prüfen, dass die alten Farben zurück sind.

- In ein Textfeld tippen: Das Bild wird klein, das Feld steht oben, „Fertig“ über der Tastatur schließt sie und das Bild wird wieder groß.

- Doppeltipp direkt auf einen Regler (Linie oder Knopf) setzt ihn zurück; senkrecht über Regler wischen scrollt, ohne Werte zu ändern.
