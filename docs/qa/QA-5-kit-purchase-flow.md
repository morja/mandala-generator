# Dahlia

Session: Purchase flow in the kit (unlock 1.1.0) · 6. November 2026

## QA-5: Kaufablauf aus dem Kit (unlock 1.1.0, unlock-ui 1.1.0) auf dem Emulator

Umfang: der Teil der Billing-Testmatrix (Skill `app-release`, Abschnitt 5; Kit ADR-0002 und ADR-0004), der ohne Mathis und ohne Lizenztester-Konto geht. Dahlia ist seit dem 6. November 2026 die Referenz-App für den Kaufablauf des Kits (Entscheidung F14): das Sheet ist das `UnlockSheet` aus `one.tearoom:unlock-ui`, der Ablauf dahinter `AppUnlock` aus `one.tearoom:unlock`. Kit-Stand: `tearoom-android-kit` Commit `14812d1` (Tag `unlock-1.1.0` zeigt auf `4846f7d`, davor liegen zwei Korrekturen aus diesem Test, siehe Befunde 4 und 7).

Aufbau: Emulator `emulator-5556` (AVD Api34b, Android 14, Play Store ohne angemeldetes Konto, Gerätesprache Deutsch). Builds aus diesem Arbeitsstand: `app-free.apk` (zählt nicht als Pro) für den Kaufpfad, `app-fast.apk` (`pro_for_testing`) für den Besitzer-Zustand. Ohne Play-Konto antwortet Billing mit „nicht verfügbar“, deshalb sind Preis, Kauf und ausstehende Zahlung hier nicht prüfbar; genau diese Fälle stehen in der Checkliste unten.

Bilder liegen in `docs/qa/QA-5/`.

## Befunde

1. **Sheet und Zustände ohne Preis (free, Deutsch).** Menü → Dahlia → „Dahlia Pro“ öffnet das Kit-Sheet: Titel, Dahlias Einleitung (gedämpft), der Kaufen-Knopf bleibt an seinem Platz und ist ohne Preis deaktiviert („Pro kaufen“), darunter der Grund („Pro wird über Google Play verkauft und ist in dieser Installation gerade nicht verfügbar.“), dann „Erneut versuchen“ und „Kauf wiederherstellen“, je eine Zeile. Passt zu SG-1 (Knopf springt nicht, Nebentext eine Stufe kleiner und gedämpft). Bild `free-de-restored.png`.

2. **Wiederherstellen ohne Play.** „Kauf wiederherstellen“ liefert die Meldung „Google Play war nicht erreichbar, daher wurde nichts geprüft. Bitte versuch es später noch einmal.“ als eigene Zeile unter dem Grund; der Grund bleibt stehen. Nach Schließen und Wiederöffnen des Sheets ist die Meldung weg (das Sheet löscht sie beim Schließen durch den Nutzer), der Grund bleibt. Bild `free-de-restored.png`.

3. **Englisch.** Per-App-Sprache Englisch: Titel, Einleitung, „Buy Pro“, Grund, Meldung, „Try again“, „Restore purchase“ alle englisch, Satzbau wie im Deutschen. Bild `free-en-restored.png`.

4. **Schriftgröße 130 %.** Vor der Korrektur ragte „Kauf wiederherstellen“ unter den Bildschirmrand, das Sheet ließ sich nicht schieben (Bild `free-de-font130-before-scroll-fix.png`; das alte Dahlia-Sheet hatte denselben Aufbau). Behoben im Kit (`f45cb33`): der Inhalt des Sheets scrollt. Danach ist „Restore purchase“ bei 130 % nach einem Wisch erreichbar, Bild `free-en-font130-scrolled.png`.

5. **Dunkel und hell.** Dahlia zeichnet standardmäßig dunkel; das Sheet nimmt Farben, Schrift und Formen aus Dahlias `MaterialTheme` (Akzent für die Textknöpfe, gedämpfter Nebentext). Dahlias helles Design (Menü → Design) wurde in diesem Durchgang nicht aufgenommen.

6. **Besitzer-Zustand (fast) und Neustart.** Mit `pro_for_testing` zeigt die Menüzeile „Dahlia Pro · Aktiv, danke“; nach `force-stop` und Neustart unverändert. Die Zeile öffnet Dahlias eigene Pro-Infoseite („Danke, dass du Pro gekauft hast.“), nicht das Kauf-Sheet; das Sheet schließt sich bei Pro von selbst (unverändertes Verhalten aus ADR-0022). Bild `fast-menu-owned.png`.

7. **Befund im Kit, beim Querprüfen in Lazy Moves gefunden.** Der Debug-Schalter (`UnlockConfig.debugUnlocked`) wurde nur beim Start und nach einer Play-Antwort gelesen; ohne Play-Antwort blieb `unlocked` im Zustand stehen, und das Kit-Panel zeigte in Lazy Moves' Angebotskarte die Dankeszeile. Behoben im Kit (`14812d1`): jeder `refresh`, `restore` und `buy` liest den Schalter neu; Test `AppUnlockTest`. Für Dahlia ohne Folgen (`pro_for_testing` ändert sich zur Laufzeit nicht).

8. **Nicht geprüft** (braucht Play-Konto oder echten Kauf): Preis und „Pro für 2,99 € kaufen“, Kauf, Danke-Meldung nach dem Kauf (schließendes Sheet plus Snackbar), ausstehende Zahlung, Abbruch, Erstattung, Wiederherstellen mit anderem Konto, Signaturprüfung mit dem Lizenzschlüssel (QA-4 bestätigte sie mit 1.8.1).

## Checkliste für Mathis (Lizenztester-Konto, Build aus dem internen Test-Track)

Voraussetzungen wie in QA-4: Konto in der Play Console unter Lizenztests, App aus Google Play installiert, Pro vorher in der Bestellverwaltung erstattet („Remove entitlement“), damit das Konto ohne Kauf beginnt. Jeder Punkt gilt für dieses Sheet; die anderen Apps laufen über denselben Kit-Ablauf und brauchen danach nur ihren `bin/check` und einen Blick auf ihre Oberfläche.

1. Internen Test-Build hochladen (`bin/release internal`), Kit-Commit `14812d1` in den Release-Notizen vermerken, auf dem Handy aus Play installieren.

2. Sheet öffnen (Menü → Dahlia → Dahlia Pro, oder eine Pro-Markierung im Editor): Preis steht im Knopf („Pro für 2,99 € kaufen“), kein Grund-Text, keine Meldung.

3. Kauf mit der Standard-Testkarte: Play-Sheet öffnet sich, nach dem Kauf schließt das Pro-Sheet von selbst, die Snackbar „Danke! Pro ist freigeschaltet.“ erscheint, Pro-Markierungen sind weg.

4. App beenden und neu starten, dann Flugmodus an und erneut starten: Pro bleibt (Cache).

5. App-Daten löschen, App öffnen, kurz warten: Pro kommt ohne Kauf zurück. Falls kurz eine Pro-Markierung erscheint, „Kauf wiederherstellen“: „Pro ist auf diesem Gerät aktiv.“

6. Im Play Store auf ein zweites Google-Konto ohne Kauf wechseln, App-Daten löschen, Sheet → „Kauf wiederherstellen“: der Text nennt das andere Konto und den Weg zurück.

7. Ausstehende Zahlung (Testkarte „slow“): das Sheet zeigt „Die Zahlung ist noch offen…“, der Kaufen-Knopf bleibt deaktiviert, nach der Bestätigung durch Play wird Pro frei.

8. Kauf abbrechen (Play-Sheet mit Zurück schließen): keine Meldung, Knopf wieder aktiv.

9. Erstattung in der Play Console („Remove entitlement“), App in den Vordergrund holen: Pro verschwindet beim nächsten Fortsetzen; offline bleibt es bis zur nächsten Play-Antwort.

10. Ergebnis je Punkt hier unter „Ergebnis:“ eintragen oder in einem QA-6 fortschreiben.
