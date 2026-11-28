# Dahlia – Mandala Maker

Session: IOS-15 iPad full screen · 28 November 2026

## IOS-15: Dahlia im Vollbild auf dem iPad

Kurz: Die iPhone-App läuft jetzt auch auf dem iPad im ganzen Fenster, mit Androids Tablet-Aufteilung: ab 600 Punkten Fensterbreite das Bild links und die Bedienung in einer Spalte rechts. Darunter (schmales Fenster in Split View oder Slide Over) bleibt die iPhone-Ansicht. Geprüft im Simulator auf dem iPad Pro 13 Zoll, hoch und quer, Englisch und Deutsch, dunkel; auf dem iPhone 15 nachgeprüft, dass sich dort nichts ändert. Mathis hat die Ansicht am 28. November freigegeben, das Hochformat bleibt wie unter Android (F1 a).

Bilder: `/Users/mathis/Work/Projects/apps/mandala-generator/docs/qa/shots/ipad-2026-11-28/`

### Befunde

1. **Universelle App.** `TARGETED_DEVICE_FAMILY` „1,2“ in `ios/Dahlia/project.yml`; das App-Symbol ist schon eine einzige 1024er-Datei für alle Geräte, das Widget folgt der gemeinsamen Einstellung.

2. **Zwei Spalten wie Android.** Ab `Spacing.twoPaneMinWidth` (600) teilt `EditorView` das Fenster wie Androids `EditorScreen`: das Bild links, so groß wie Breite und Höhe es erlauben (mit Platz für die Statuszeile darunter), die Tabs rechts in `Spacing.controlsWidth` (360), 24 Punkte Abstand, 16 Punkte Rand. Das Bild faltet dort nicht. Im Hochformat (1032 Punkte breit) steht das Bild mit 616 Punkten oben links, darunter bleibt Platz frei, wie unter Android.

3. **Tour.** Auf breiten Fenstern ist die Karte so breit wie die Bedienspalte und steht über ihr, wie unter Android; Linien von einem Ring zur Karte erscheinen nur, wo sie die Karte treffen.

4. **Blätter.** Auf dem iPad sind alle Blätter Formblätter in der Fenstermitte. Menü, Pro, Favoriten und die Hintergrund-Seite sind so hoch wie ihr Inhalt, Speichern und Spielfeld so hoch wie bisher. Auf dem iPhone bleibt alles wie in IOS-14.

5. **Mehrere Apps nebeneinander.** Alle vier Ausrichtungen, kein `UIRequiresFullScreen`: Dahlia läuft in Split View, Slide Over und Stage Manager, die Aufteilung folgt der Fensterbreite. Das ist der einfachste Weg, den Apple annimmt; `UIRequiresFullScreen` ist seit iPadOS 26 veraltet und wird für Apps mit dessen SDK nicht mehr beachtet.

6. **Nicht geprüft.** Ein echtes iPad, Split View mit Größenwechsel im laufenden Betrieb, Hell, weitere Sprachen außer Englisch und Deutsch.

### Selbst entschieden

- Auf dem iPad öffnen die Favoriten in voller Höhe statt halb: Halb hoch hing das Blatt unten am Fensterrand statt in der Mitte. Entschieden wird nach dem Gerät, weil die Größenklasse in einem Formblatt immer „kompakt“ meldet.

- Wechselt ein Fenster über die 600-Punkte-Grenze, baut sich die Ansicht neu auf; der offene Tab springt dabei auf „Ebenen“ zurück.

- Keine Überschrift „Dahlia“ in der Kopfleiste breiter Fenster (Android zeigt sie dort); die Leiste bleibt auf beiden Geräten gleich.
