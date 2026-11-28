# Dahlia – Mandala Maker

Session: IOS-14 iPhone as roomy as Android · 28 November 2026

## IOS-14: Größen, Kopfleiste, Menü und Blätter wie in der Android-App

Kurz: Das iPhone hat jetzt dieselben Größen wie die Android-App auf einem gleich breiten Handy. Gemessen wurde in den Bildschirmfotos, mit beiden Bildschirmen auf dieselbe Breite gebracht: Das Galaxy S23 und das iPhone 15 sind etwa gleich breit (64,5 und 65,1 mm), ein iPhone-Punkt ist also etwas kleiner als ein Android-dp. Texte, Symbole, Zeilen und Kacheln liegen jetzt innerhalb von etwa 3 % der Android-App. Mathis hat die Größen in drei Runden geprüft und am 28. November freigegeben.

Bilder: `/Users/mathis/Work/Projects/apps/mandala-generator/docs/qa/shots/compare-2026-11-26/after/` (Android, iPhone vorher, iPhone nachher; `main-menu-3.png`, `dahlia-submenu-3.png`, `editor-3.png`, `top-bar-4.png`, dazu `iphone17e-de.png` und `iphone17e-ru.png`)

### Befunde

1. **Schriftrollen.** `body` 15 pt (Android bodyMedium, Editor und Seitentexte), `heading` 16 pt halbfett (titleSmall, labelLarge: Gruppentitel, Tabs, Knöpfe, Würfeltitel), `note` 13 pt, `title` 22 pt. Die Titel der Menüzeilen nehmen `body` zwei Dynamic-Type-Stufen größer (19 pt, Android bodyLarge), die Beschriftungen der oberen Menükacheln `heading` zwei Stufen kleiner (14 pt). Alles folgt weiter der Systemschriftgröße. Geändert im Generator `ios/scripts/gen-tokens`, beschrieben in IOS-3.

2. **Kopfleiste.** 64 pt hoch mit 48-pt-Knöpfen wie Androids TopAppBar. Jedes Symbol ist einzeln auf die Größe von Androids Symbol gebracht, weil SF Symbols untereinander verschieden groß zeichnen. Der Würfel liegt auf einem weichen Feld in der Akzentfarbe wie unter Android, der Stern ist gefüllt, Speichern ist Androids Material-Download-Pfeil als Vektorgrafik (Apache 2.0, in README und auf der About-Seite beider Apps genannt).

3. **Menü.** Das Menü ist so hoch wie sein Inhalt, liegt auf der Fläche `surface` mit sichtbarem Griff, und das Bild bleibt darüber sichtbar. Kacheln in Androids Reihenfolge (Favorit, Teilen, Hintergrund), Symbole in gleich großen Feldern, sodass die Texte an einer Kante stehen. Zeilen und Kacheln sind so hoch wie unter Android.

4. **Andere Blätter.** Favoriten, Speichern, Spielfeld und Pro liegen auf derselben Fläche mit Griff; Speichern und Spielfeld bleiben groß, weil ihr Bild die restliche Höhe füllt. Die Hintergrund-Seite über dem Speichern-Blatt passt sich wie das Menü dem Inhalt an.

5. **Messung, Abweichung von Android** (Runde 2 → freigegeben): Zeilentitel −6 % → +2 %, Kachelbeschriftung +6 % → +2 %, Würfeltitel +7 % → +1 %, Stern oben +17 % → +2 %, Speichern +24 % → −1 %, Kompass +8 % → +1 %, Würfel oben −2 % → +3 %, Menüsymbol +9 % → +4 %, obere Kacheln −5 % → −1 %, Würfelkacheln −9 % → −3 %, einzeilige Zeile −5 % → −2 %, zweizeilige Zeile −11 % → −3 %. Editor: Gruppentitel, Tabs und Knöpfe +4 %, Ebenentitel −2 %.

6. **Deutsch und Russisch am iPhone 17e (390 pt).** Nichts läuft über. Beim Prüfen behoben: „Zurücksetzen“ brach um (jetzt bricht die Beschriftung daneben um), „Дублировать“ und „Рисовать на изображении“ passten nicht (kleinerer Innenabstand, der breite Knopf darf um bis zu 15 % schrumpfen). Die Tab-Beschriftungen werden auf Deutsch eine Stufe kleiner, damit „Zeichnen“ passt.

7. **Noch abweichend.** Die grauen Unterzeilen im Menü sind 5 % kleiner als unter Android; die nächste Stufe wäre 10 % größer. Fließtext zu Hinweis steht jetzt 1,15 statt der 1,2 aus der Gestaltungsregel (Android: 1,17).

### Selbst entschieden

- Gemessen bei gleicher Bildschirmbreite statt in Punkten und dp, weil beide Handys gleich breit sind.

- Kleine Größenunterschiede zwischen SF Symbols gleiche ich mit Dynamic-Type-Stufen je Symbol aus (`typeSizeShifted`), nicht mit Skalierung, damit die Symbole scharf bleiben und mit der Systemschrift wachsen.

- Zeilenabstände als Bruchteile der vorhandenen Tokens (`Spacing.md + Spacing.xs / 4`), keine neuen Tokens.

- Die drei Punkte für das Menü bleiben waagerecht wie unter iOS üblich.

- Der Stern oben ist immer gefüllt wie unter Android; ob das Bild ein Favorit ist, zeigt die Kachel im Menü.
