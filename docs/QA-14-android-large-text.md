# Dahlia – Mandala Maker

Session: Android large text · 13 November 2026

## QA-14: Tab-Leiste und Ebenen-Aktionen bei großer Schrift

Kurz: Die Tab-Leiste des Editors und die Aktionszeile im Tab „Ebenen“ passen jetzt bei jeder Schriftgröße in die Breite, ohne seitliches Scrollen und ohne abgeschnittene oder umbrechende Wörter. Beide folgen derselben Regel wie das iPhone (IOS-5 Befund 8): Alle Beschriftungen einer Zeile haben dieselbe Schriftrolle; passt eine nicht, nehmen alle zusammen die nächstkleinere Rolle, und bei sehr großer Schrift werden sie gemeinsam kleiner. Nebenbei behoben: Die Ebenenzeile darüber verlor bei großer Schrift den Namen des Stils und den Prozentwert. Geprüft auf dem Emulator `Api34` mit 360 und 411 dp Breite, Schrift 1,0, 1,3 und 2,0 (die größte Stufe von Android 14), Deutsch, Russisch und Englisch, hell und dunkel; dazu Französisch, Spanisch, Portugiesisch, Italienisch, Japanisch, Koreanisch und Chinesisch bei 360 dp mit Schrift 1,3 und 2,0.

Bilder: `/Users/mathis/Work/Projects/apps/mandala-generator/docs/qa/shots/android-large-text-2026-11-13/` (`before-…` vom Stand vor der Änderung, `after-…` danach; Name: Breite, Sprache, Schrift, Thema).

### Vorher

1. **Tab-Leiste schneidet ohne Auslassungspunkte.** Bei 360 dp und Schrift 1,3 standen „Emble“ und „Zeichn“ in der Leiste (`before-360dp-de-font13-dark.png`); bei Schrift 2,0 auf Russisch „Сцен“, „Эмб“, „Текс“, „Рису“, und „Слои“ stieß an „Сцен“ (`before-360dp-ru-font20-dark.png`). Auch bei 411 dp und Schrift 2,0 war „Zeichnen“ gekürzt (`before-411dp-de-font20-dark.png`).

2. **„Löschen“ bricht um, der Würfel fällt aus dem Bild.** Bei 360 dp und Schrift 1,3 stand „Lösche / n“ in zwei Zeilen, und der Würfel war nicht mehr zu sehen (`before-360dp-de-font13-dark.png`). Bei Schrift 2,0 auf Russisch brach schon „Дублировать“ um, „Удалить“ und der Würfel fehlten (`before-360dp-ru-font20-dark.png`).

3. **Ebenenzeile verliert Wörter.** Die erste Zeile („1 · Hyperboloid“) zeigte bei großer Schrift nur „1 ·“, weil der Stilname in eine unsichtbare zweite Zeile umbrach; die zweite Zeile zeigte nur „Negativ“ oder „Экран ·“, der Prozentwert fehlte (`before-360dp-de-font13-dark.png`, `before-360dp-ru-font20-dark.png`).

### Nachher

1. **Tab-Leiste: eine Regel für alle fünf.** Jedes Tab hat ein Fünftel der Breite. Eine Beschriftung passt, wenn sie in ihr Fünftel geht und zu jedem Nachbarn mindestens 8 dp Abstand bleibt (`Spacing.xs` je Seite). Passt eine nicht, nehmen alle fünf zusammen die nächstkleinere Rolle (`titleSmall` 14 sp → `labelMedium` 12 sp). Passt auch das nicht, werden alle fünf gemeinsam in kleinen Schritten kleiner, höchstens bis zur Größe der kleinsten Rolle des Themas bei normaler Schrift (`labelSmall`, 11 sp); Auslassungspunkte bleiben nur als letzte Sicherung. Ergebnis:

   - Schrift 1,0: überall `titleSmall`, auch Deutsch und Russisch bei 360 dp (`after-360dp-de-font10-*.png`).

   - Schrift 1,3: Deutsch und Russisch nehmen bei 360 und 411 dp `labelMedium`, Englisch bleibt bei `titleSmall` (`after-360dp-de-font13-*.png`, `after-411dp-en-font13-*.png`).

   - Schrift 2,0: alle Beschriftungen ganz, auch „Zeichnen“ und „Эмблема“ bei 360 dp (`after-360dp-de-font20-*.png`, `after-360dp-ru-font20-*.png`).

   Die seitlichen Ränder der Leiste sind die der Bedienelemente darunter (16 dp); das war auf Android schon so, nur die Beschriftungen liefen über.

2. **Aktionszeile: der Würfel bleibt am Ende, die Beschriftungen werden zuerst kleiner.** „Hinzufügen“, „Duplizieren“ und „Löschen“ behalten ihre natürliche Breite und stehen links, der Würfel rechts. Passen die drei nicht neben den Würfel, nehmen sie zusammen die nächstkleinere Rolle (`labelLarge` → `labelMedium`) und werden bei sehr großer Schrift gemeinsam kleiner, nach derselben Regel wie die Tab-Leiste. Bei 360 dp und Schrift 1,3 steht „Löschen“ einzeilig, der Würfel ist da (`after-360dp-de-font13-*.png`); bei Schrift 2,0 auf Russisch ebenso (`after-360dp-ru-font20-*.png`). Bei Schrift 1,0 und genügend Platz sieht die Zeile aus wie vorher.

3. **Ebenenzeile: Name gekürzt, Prozent ganz.** Die erste Zeile endet jetzt mit Auslassungspunkten („1 · Hyperbol…“). In der zweiten bleibt der Prozentwert ganz, nur der Name des Mischmodus wird gekürzt („Negativ multip… · 100 %“), im Format der jeweiligen Sprache (wie auf dem iPhone, IOS-5 Befund 8). Bei 360 dp und Schrift 2,0 bleibt vom Namen nur ein Buchstabe („N… · 100 %“), auf Japanisch und Koreanisch nur „…“, weil die drei Symbole der Zeile den Platz brauchen; siehe F1.

4. **Weitere Sprachen.** Französisch, Spanisch, Portugiesisch, Italienisch, Japanisch, Koreanisch und Chinesisch bei 360 dp, Schrift 1,3 und 2,0, hell: Tab-Leiste und Aktionszeile passen ganz (`after-360dp-fr-…` bis `after-360dp-zh-CN-…`). Japanisch hat mit „エンブレム“ und „テキスト“ die breitesten Beschriftungen; seine Tabs stehen schon bei Schrift 1,3 an der Untergrenze (11 sp) und wirken deutlich kleiner als in den anderen Sprachen (`after-360dp-ja-font13-light.png`), sind aber ganz und lesbar.

5. **Hell und dunkel** verhalten sich gleich; es wurden keine Farben geändert. Deutsch, Russisch und Englisch sind in beiden Themen bei 360 und 411 dp mit Schrift 1,0, 1,3 und 2,0 aufgenommen.

### Umsetzung

- `android/app/src/main/kotlin/com/mathis/mandala/app/ui/FittingText.kt`: neuer gemeinsamer Baustein `fittingStyle(labels, roles, space, minimum, fits)`. Er misst die Beschriftungen mit Compose' `TextMeasurer` bei der aktuellen Schriftgröße und gibt die erste passende Rolle zurück, sonst die letzte, gemeinsam verkleinert. Die Regel, was „passt“ heißt, gibt die Zeile selbst mit (Tab-Leiste: Fünftel und Abstand zum Nachbarn; Aktionszeile: Summe der Knopfbreiten neben dem Würfel).

- `Controls.kt`: Tab-Leiste und Aktionszeile nutzen den Baustein; die Trennlinie unter der Leiste ist jetzt eine eigene `HorizontalDivider` (vorher die eingebaute der `PrimaryTabRow`, gleich aussehend). Die Ebenenzeile hat Auslassungspunkte und trennt den Prozentwert ab.

- Keine neuen Tokens, keine neuen Pakete; nur vorhandene Schriftrollen und Abstände.

### Prüfungen

- `android/scripts/check` (Pre-commit-Hook) und `android/scripts/check-full` (mit Paritätstest und Release-Build): grün.

- Der geteilte Emulator ist wieder ausgeschaltet; Dichte, Schriftgröße, Dunkelmodus und App-Sprache sind zurückgesetzt. Das Thema der App selbst steht dort noch auf „Hell“.

### Selbst entschieden

- Kleinere Rolle der Tab-Leiste ist `labelMedium` (12 sp, gleiche Strichstärke wie `titleSmall`), nicht `bodySmall`, damit die Tabs als Tabs lesbar bleiben.

- Untergrenze beim Verkleinern ist die kleinste Rolle des Themas bei normaler Schrift (`labelSmall`, 11 sp), statt eines festen Faktors wie auf dem iPhone (0,6). Mit 0,6 passten „Zeichnen“ und „Эмблема“ bei 360 dp und Schrift 2,0 nicht; die Beschriftungen bleiben so nie kleiner als der kleinste Text sonst in der App.

- „Passt“ in der Tab-Leiste heißt: im eigenen Fünftel und mindestens 8 dp zum Nachbarn, statt eines festen Innenabstands je Tab. Mit festem Innenabstand hätten Deutsch, Russisch, Französisch und Portugiesisch schon bei Schrift 1,0 auf 360 dp die kleinere Rolle bekommen, obwohl nichts zusammenstößt.

- Aktionszeile: Würfel am Ende und natürliche Breiten statt gleich breiter Knöpfe, damit die Zeile bei normaler Schrift unverändert bleibt.

- Die Ebenenzeile (Befund 3) mitbehoben, weil sie in denselben Aufnahmen kaputt war und das iPhone die Regel schon hat.

### Offene Fragen

F1. In der Ebenenzeile bleibt bei 360 dp und Schrift 2,0 vom Mischmodus nur ein Buchstabe („N… · 100 %“), auf Japanisch und Koreanisch nur „… · 100 %“. Vorschläge: a) so lassen, die Gruppe „Mischmodus“ darunter zeigt den vollen Namen (Empfehlung); b) bei sehr großer Schrift den Prozentwert weglassen und den Namen zeigen; c) die Zeile bei sehr großer Schrift zweizeilig erlauben.
