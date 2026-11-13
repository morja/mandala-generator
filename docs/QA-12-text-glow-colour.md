# Dahlia – Mandala Maker

Session: DM-24 A2 text and glow colour, build · 13 November 2026

## QA-12: Textfarbe und Leuchtfarbe (DM-24 A2) für Android und Core

Kurz: Der Text hat jetzt zwei Farben, „Text“ und „Leuchten“ (Glow). Die Leuchtfarbe steht anfangs auf „Wie der Text“, deshalb sehen alle gespeicherten Favoriten und Mac-Dateien genau wie vorher aus: 1 416 Render-Hashes vor und nach der Änderung sind identisch. Das Text-Tab zeigt die Farbgruppe wie im Mock: zwei Punkte, Schalter Text | Leuchten, Bildfarben plus Weiß, „Hervorheben“ (Stand out), „Weitere Farben“ mit Farbblatt und die eingeklappten Feinregler. Auf dem Emulator geprüft; auf dem Galaxy S23 nicht installiert, weil es nicht per adb verbunden war.

Bilder: `/Users/mathis/Work/Projects/apps/mandala-generator/docs/qa/shots/text-glow-2026-11-13/` (vom Git ignoriert).

### Befunde

1. **Jeder bisherige Favorit rendert Byte für Byte gleich.** Das Werkzeug `RenderHashes` aus SPIKE-4 (jetzt mit zusätzlichen Ordnern) hat alle 93 Fixtures, die drei Bench-Fälle, alle 21 Start-Favoriten und Mathis' Kabir-Datei in je 12 Varianten gerechnet (Vorschau 512, Export 1024, Pro 2048, Hochformat, Finish, drei Threads, drei Animationsbilder samt Videoebenen): 1 416 Hashes vorher (alter Core, Stand `109f7bf`) und nachher, `diff` leer. Grund: Bei „Wie der Text“ nimmt der Renderer für Schein und Lichtdurchgänge dasselbe Farb-Array wie für die Buchstaben. Zusätzlich prüft ein neuer Test, dass die Leuchtfarben-Felder bei „Wie der Text“ nichts ändern und dass eine eigene Leuchtfarbe nur dort färbt, wo der Schein hinreicht.

2. **Mac-Kompatibilität.** Neue Felder in `textLayer`: `glowSameAsText` (Standard `true`), `glowHue` (0), `glowSaturation` (0), `glowBrightness` (1). Mac-Dateien ohne die Felder lesen sich als „Wie der Text“ (Test). Der Mac kennt die Felder nicht, behält sie aber als Extras und schreibt sie zurück; er zeigt solche Mandalas mit einem Schein in der Textfarbe, wie der Mock angekündigt hat. ADR-0003 ist ergänzt, die Freigabe steht in `docs/APPROVALS.md`. Keine neuen Design-Tokens: Der Schleier hinter dem Blatt ist Materials `BottomSheetDefaults.ScrimColor`.

3. **Kabir: „Hervorheben“ macht den Text deutlich lesbar.** Die Regel fand hinter dem Text den Farbton 0,52 (türkis, Klarheit 0,33), nahm als Leuchten die Bildfarbe Orangerot (Farbton 0,05, nächste Bildfarbe zum Gegenton) und als Text fast Weiß mit Orangestich; Kontrast der Buchstaben 6,9 : 1. Leuchtstärke, Wolke und Schatten blieben. Vorher/Nachher mit dem echten Renderer: `kabir-before.png`, `kabir-stand-out.png`; auf dem Emulator `04-colour-group.png` und `05-stand-out.png`.

4. **Whitman (Start-Favorit 2): „Hervorheben“ wird eher schlechter.** Hinter dem Text liegt Violett (0,86); keine Bildfarbe liegt nahe am Gegenton, also grün mit Sättigung 0,75. Weil der Favorit mit Leuchten 3,0 arbeitet, wird der grüne Schein selbst so hell, dass die fast weißen Buchstaben darin weniger abstechen als vorher. Die Regel aus dem Mock misst den Kontrast nur gegen das Bild ohne Text, nicht gegen den eigenen Schein. Bilder: `starter-2-whitman-before.png`, `starter-2-whitman-stand-out.png`. Siehe F1.

5. **Live-Vorschau im Farbblatt.** Solange das Blatt offen ist, zeichnet jede Änderung sofort ein kleines Bild (256 px, ohne Supersampling, eigener Ebenen-Cache, sodass ein Farbzug nur den Text neu zeichnet); 150 ms nach dem Loslassen kommt wie immer die volle Vorschau. Auf dem Emulator sichtbar: während des Ziehens wird der Schein im Bild schon blasser (`07-sheet-dragging.png`). Kosten: ein 256er-Bild ist ein Sechzehntel der Pixel der Vorschau (1024 intern); die Ebenen kommen aus dem Cache. Gemessen auf dem S23 ist das nicht.

6. **Cancel, Done, Zurück.** „Abbrechen“ stellt beide Farben wieder her (geprüft: nach Ziehen und Abbrechen ist die orange Bildfarbe wieder markiert, `10-after-cancel.png`); die Zurück-Geste und ein Tipp auf den Schleier tun dasselbe; ein anderes Tab schließt das Blatt und behält die Farben.

7. **Schmales Telefon, Deutsch, Schrift 1,3, hell und dunkel.** Bei 320 dp passt der Gruppenkopf („Farbe · Text ● · Leuchten ●“) in eine Zeile (`14-de-light-narrow-group.png`). Das Label über den Bildfarben brach als „Bildfarben für: Leuchten“ dreizeilig um; es heißt jetzt in allen Sprachen nur „Aus dem Bild“, weil der Schalter darüber schon die Farbe nennt (bei 320 dp noch zweizeilig). Das Blatt passt bei 320 dp und Schrift 1,3 nicht ganz in den Tab-Bereich: Feld, Farbtonstreifen und die Knöpfe erreicht man durch Scrollen im Blatt (`16-de-light-sheet.png`). Bei normaler Schrift auf 411 dp ist nur die Knopfzeile unter dem Rand.

8. **Absturz beim Start gefunden und behoben.** Der erste Emulator-Build stürzte beim Start ab: Der Vorschau-Collector im `init` las den Blattzustand, bevor er deklariert war. Behoben (Deklaration vor `init`), danach lief alles.

9. **Review durch einen Opus-Subagenten.** Keine blockierenden Fehler. Umgesetzt: eigener Ebenen-Cache für das Live-Bild (vorher wurde bei jedem Zug das ganze Bild neu gerechnet, auf schweren Bildern evtl. nie fertig); ein Text-Boost über 1 (Kabir hat Helligkeit 2,0, der Boost hellt auch den Schein auf) bleibt bei Bildfarben und „Hervorheben“ erhalten; Fehler beim Abtasten des Bildes werden abgefangen, doppelte Tipps auf „Hervorheben“ ignoriert; Farbtonstreifen springt am rechten Ende nicht mehr auf Rot links; Schleier mit Klick-Beschriftung, das Blatt meldet sich bei TalkBack nicht mehr als Knopf; neue Tests (Wrap-around des Farbkreises, nur Weiß verfügbar, Boost bleibt, Mac-JSON).

10. **Prüfungen.** `android/scripts/check` (Hook) und `ios/scripts/check` (Hook; das iPhone bindet Core und Editing) grün bei jedem Commit; `android/scripts/check-full` mit Paritätstest gegen die macOS-Referenzen und Release-Build grün (Stand `f79362c`).

### Offene Fragen

F1. „Hervorheben“ bei starkem Leuchten (über etwa 1,5): Soll der Knopf dort den Kontrast gegen den eigenen Schein prüfen und notfalls die Leuchtstärke senken, oder bleibt es bei der Regel aus dem Mock (Leuchtstärke unverändert)? Empfehlung: a) Leuchtstärke bei „Hervorheben“ auf höchstens 1,2 begrenzen, wenn sie darüber liegt; b) Regel lassen wie sie ist.

F2. Das Farbblatt passt auf einem schmalen Telefon mit großer Schrift nicht ganz in den Tab-Bereich. Reicht Scrollen im Blatt, oder soll das Feld dort niedriger werden (etwa 96 statt 120 dp)?

### Selbst entschieden

- Würfel (Zufallslook für den Text): setzt „Wie der Text“ wieder ein, damit nie eine alte eigene Leuchtfarbe zu einer neuen Zufalls-Textfarbe stehen bleibt; so würfelt Android wie bisher und wie der Mac. Eine Zufalls-Leuchtfarbe gibt es nicht.

- Live-Vorschau als kleines Bild (256 px) statt eines Masken-Caches im Renderer: der Cache wäre schneller, greift aber tief in Core und Speicher ein.

- Vorher/Nachher im Blatt zeigen den echten Ausschnitt der Vorschau um den Text (vor dem Öffnen und jetzt), nicht ein nachgebautes „Aa“ wie im Mock.

- Bildfarben und „Hervorheben“ schauen auf ein kleines Bild ohne Text (160 px), damit die Buchstaben selbst die Farben nicht verfälschen; der Textbereich ist wie im Mock die Breite des Textfelds und ±0,13 der Höhe um die Textmitte.

- Ein Text-Boost über 1 hellt weiter den ganzen Text samt Schein auf, auch bei eigener Leuchtfarbe (wie auf dem Mac); die Leuchthelligkeit selbst geht nur bis 1. Bildfarben und „Hervorheben“ behalten den Boost, Feld und Feinregler setzen die Helligkeit so, wie man sie zieht.

- Feinregler „Helligkeit“ geht für den Text bis 2 (gespeicherte Werte über 1), für das Leuchten bis 1. Doppeltipp setzt beim Leuchten auf die Textfarbe zurück.

- Deutsche Begriffe: „Leuchten“ wie der bestehende Regler, „Hervorheben“ für Stand out, „Weitere Farben“, „Feinregler“, „Wie der Text“; französisch, japanisch und koreanisch ebenso nach dem bestehenden Glow-Regler („Lueur“, „グロー“, „글로우“).

- Acht Bildfarben in einer Zeile wie im Mock; auf 360 dp ist jede Tippfläche etwa 37 dp breit und 48 dp hoch, unter der Material-Empfehlung von 48 dp Breite.

- `RenderHashes` nimmt zusätzliche Ordner; neues Werkzeug `TextGlowShots` (cli-Tests) rendert eine Datei vor und nach „Hervorheben“.
