# Dahlia

Session: QA-6 Kaufablauf mit Lizenztester auf dem Tablet · 6. November 2026

## QA-6: Kaufablauf aus dem Kit mit Google Play und Lizenztester-Konto

Umfang: die „Checkliste für Mathis“ aus QA-5 (Punkte 1–14), also der Teil der Billing-Testmatrix (Skill `app-release`, Abschnitt 5, Teil 2), der Google Play und ein Lizenztester-Konto braucht. Gerät: Tablet Samsung SM-X230 (`RZGYC145BJH`), Android 16, Deutsch, dunkles Design, Querformat; Play-Konto koblin@gmail.com (Lizenztester über die Liste „Tearoom testers“), zweites Konto dojolava@gmail.com. Builds aus dem internen Track: 1.8.6 (Code 21, Commit `1cf0652`, Kit `5bcbe9b`) und für Punkt 13 1.8.7 (Code 22, Commit `faeec32`, Kit `3631def`, Kaufablauf unverändert gegenüber `5bcbe9b`, nur die Versionsnummer unlock 1.1.1). Kein Release-Tag, keine Produktion.

Bilder liegen in `docs/qa/QA-6/`.

Ergebnis in einem Satz: Der Kaufablauf des Kits funktioniert mit echtem Play in allen geprüften Fällen; die zwei Befunde betreffen Dahlia selbst (Danke-Meldung, Text beim Wiederherstellen), dazu eine Lücke in der Console-Einrichtung, die den Start um Stunden verzögert hat.

## Befunde

Die Nummern 1–14 entsprechen den Punkten der Checkliste in QA-5, ab 15 folgen Befunde außerhalb der Checkliste.

1. **Upload und Installation: bestanden, mit Verzögerung.** 1.8.6 (Code 21) um 13:03 in den internen Track geladen (`bin/release internal`, Notizen nennen Kit `5bcbe9b`, das `14812d1` enthält). Auf dem Tablet kam das Update erst um 18:55 an, siehe Befund 17. Installiert aus Play (`installerPackageName=com.android.vending`).

2. **Sheet mit Preis: bestanden.** Menü → Dahlia → Dahlia Pro: Knopf „Für 2,99 € kaufen“, kein Grund-Text, keine Meldung. Der Wortlaut ist „Für 2,99 € kaufen“, nicht „Pro für 2,99 € kaufen“ wie in der Checkliste. Bild `p2-sheet-price.png`.

3. **Kauf mit der Standard-Testkarte: bestanden bis auf die Danke-Meldung.** Play zeigt zuerst den Verzicht auf das Widerrufsrecht („Zustimmen“), dann das Kaufblatt mit Testkarte. Nach „Tippen & Kaufen“ schließt das Pro-Sheet von selbst, die Pro-Marke auf dem Mandala ist weg, die Menüzeile zeigt „Dahlia Pro · Aktiv, danke“. Die Snackbar „Danke! Pro ist freigeschaltet.“ erschien nicht, siehe Befund 15. Bestellung `GPA.3313-0970-1888-40487`, 19:43. Bild `p3-after-purchase-menu.png`.

4. **Neustart und Flugmodus: bestanden.** Nach `force-stop` und Neustart bleibt Pro; ebenso nach `force-stop`, Flugmodus an und Neustart. Bild `p4-offline-restart.png`.

5. **App-Daten löschen: bestanden.** `pm clear` auf dem Tablet, App öffnen: die Tour startet neu, danach zeigt die Menüzeile „Aktiv, danke“, ohne Kauf. Keine Pro-Marke beim Laden gesehen (das Standard-Mandala nutzt kein Pro). Bild `p5-after-clear-menu.png`.

6. **Wiederherstellen mit anderem Konto: Meldung richtig, ihr Rückweg falsch.** Dahlia deinstalliert, Play auf dojolava@gmail.com (ohne Kauf), aus Play installiert (Code 21). Installiert hat also dojolava. Sheet → „Kauf wiederherstellen“: „Google Play hat für dieses Google-Konto keinen Pro-Kauf gefunden. Wenn du Pro mit einem anderen Google-Konto gekauft hast, wechsle im Play Store zu diesem Konto und tippe noch einmal auf „Kauf wiederherstellen“.“ Dem Rat gefolgt: Play auf koblin gewechselt, zurück in Dahlia, wiederhergestellt, auch nach Kaltstart: Pro bleibt aus. Erst Deinstallieren und mit koblin neu installieren bringt Pro zurück. Siehe Befund 16. Bild `p6-restore-other-account.png`.

7. **Ausstehende Zahlung, App offen: bestanden.** Langsame Testkarte „Genehmigung nach einigen Minuten“: das Sheet zeigt „Der Kauf steht noch aus. Pro wird freigeschaltet, sobald er bezahlt ist.“, der Kaufen-Knopf ist deaktiviert (Bild `p7-pending.png`). Nach etwa einer Minute (19:00:56 → 19:01:54) schloss das Sheet von selbst, Pro war frei (Bild `p7-approved-open.png`). Ob die Danke-Meldung kurz erschien, ist offen (Abfrage alle 15 Sekunden; vermutlich dieselbe Ursache wie in Befund 15). Bestellung `GPA.3315-3991-6700-35372`.

8. **Abbruch: bestanden.** Play-Dialog mit Zurück geschlossen: keine Meldung, der Knopf ist wieder aktiv. Bild `p8-after-cancel.png`.

9. **Erstattung: bestanden.** Bestellung aus Punkt 7 in der Console erstattet mit „Remove entitlement“, App lag im Hintergrund. Beim nächsten Öffnen war Pro weg, auch im Flugmodus: Play hatte die Erstattung schon vorher in seinen lokalen Kaufspeicher übernommen und antwortete offline daraus, das Kit bekam also eine echte Play-Antwort. Dass der Cache ohne Play-Antwort bleibt, zeigt Punkt 4. Bilder `p9-offline-still-pro.png` (Name täuscht: Pro ist dort schon weg), `p9-online-free.png`.

10. **Acknowledge: bestanden, ohne drei Tage zu warten.** Für Lizenztester erstattet Play unbestätigte Käufe nicht nach drei Tagen, sondern nach drei bis fünf Minuten (Google nennt 3 Minuten; siehe Punkt 12, wo das passierte). Bestellung `GPA.3313-0970-1888-40487` (19:43:19) stand um 19:56 weiter auf „Processed“: die App hat bestätigt. Der Drei-Tage-Blick in der Console ist damit überflüssig.

11. **Abgelehnte Zahlung: bestanden.** Testkarte „immer abgelehnt“: Play meldet „Fehler · Declined by always denied test instrument“, danach zeigt das Sheet „Der Kauf ist nicht zustande gekommen.“ (Checkliste: „…wurde nicht abgeschlossen.“), Pro bleibt aus, der Knopf ist aktiv. Bild `p11-declined-sheet.png`.

12. **Ausstehende Zahlung bei geschlossener App: nicht prüfbar, Play hat den Kauf erstattet.** Langsame Testkarte, App sofort mit `force-stop` beendet (19:05). Play buchte um 19:06:00 ab, meldete der App beim Öffnen um 19:08, beim Fortsetzen, beim Wiederherstellen und noch 30 Minuten lang aber weiter „ausstehend“ (Bild `p12-after-3min.png`), und erstattete die Bestellung um 19:11:01 selbst, weil sie unbestätigt war. Danach meldete Play gar keinen Kauf mehr (Wiederherstellen: „keinen Pro-Kauf gefunden“, Bild `p12-coldstart-restore.png`; ein neuer Kaufversuch öffnete Plays Kaufblatt). Das Kit fragt bei jedem Fortsetzen `queryPurchasesAsync` ab und zeigt richtig, was Play liefert; die Lücke liegt in Plays Kaufspeicher auf dem Gerät, der den Wechsel von ausstehend zu bezahlt bei geschlossener App nicht mitbekam. Bekanntes Verhalten der langsamen Testkarte (RevenueCat-Community: „slow test card payments are refunded due to lack of acknowledgment“). In der Produktion hat die App drei Tage Zeit, die Lücke wäre dort mit dem nächsten Abgleich von Play geschlossen. Bestellung `GPA.3305-1018-7848-75767`. Der zweite Fall (Ablehnung während der geschlossenen App) wurde nicht mehr geprüft: er hängt an derselben Synchronisierung und hätte nur dasselbe gezeigt.

13. **Update über eine Installation mit Kauf: bestanden (1.8.6 → 1.8.7); 1.8.5 → 1.8.6 nur im Code geprüft.** Installation 1.8.6 von koblin mit gekauftem Pro, 1.8.7 (Code 22) um 19:58 hochgeladen, Play bot das Update um 20:10 an, installiert um 20:10:55. Erster Start im Flugmodus: „Aktiv, danke“, also aus dem Cache (Bild `p13-update-offline.png`); online beim Fortsetzen und nach Kaltstart unverändert (Bild `p13-update-online.png`). Eine Pro-Marke war nicht zu erwarten, weil das offene Mandala kein Pro nutzt; die Prüfung stützt sich auf die Menüzeile. Nicht geprüft ist das Update, das echte Käufer bekommen: von 1.8.5 (alter Billing-Code, Production) auf die Kit-Version. Der erste Versuch am Mittag scheiterte an Befund 17, und seit dem Beitritt zum internen Test liefert Play dem Konto keine 1.8.5 mehr. Im Code nachgesehen: 1.8.5 nutzt schon `OneTimeUnlock` aus dem Kit mit demselben Cache (`SharedPreferences` „pro“, Schlüssel „owned“, Tag `android-v1.8.5`, `Pro.kt`); 1.8.6 liest denselben Schlüssel. Der Übergang ändert am Cache nichts, das Risiko ist klein.

14. **Ergebnisse festgehalten:** dieser Bericht.

15. **Dahlia: die Danke-Meldung fehlt, wenn Pro über das Menü gekauft wird** [mittel]. `EditorScreen.kt:208` blendet jede Snackbar aus, solange `showMenu` wahr ist. Der Weg Menü → Dahlia → Dahlia Pro öffnet das Pro-Sheet über dem Menü; nach dem Kauf schließt nur das Pro-Sheet, das Menü bleibt offen, und die Meldung „Danke! Pro ist freigeschaltet.“ wird sofort wieder ausgeblendet. Reproduktion: freie Installation, Menü → Dahlia → Dahlia Pro → kaufen. Über die Pro-Marke im Editor (ohne Menü) sollte sie erscheinen; nicht geprüft, weil das Konto danach Pro besaß. Mögliche Abhilfe: das Menü schließen, wenn es das Pro-Sheet öffnet, oder die Danke-Meldung von der Regel ausnehmen.

16. **Dahlia: der Rückweg in der Meldung „kein Kauf gefunden“ funktioniert nicht** [mittel]. `pro_restore_not_found` (zehn Sprachen) rät, im Play Store das Konto zu wechseln und noch einmal wiederherzustellen. Play ordnet Käufe aber dem Konto zu, das die App installiert hat; der Wechsel im Store ändert daran nichts (Punkt 6). Vorschlag für den Text: „…Wenn du Pro mit einem anderen Google-Konto gekauft hast, deinstalliere Dahlia, wechsle im Play Store zu diesem Konto und installiere Dahlia dort neu.“ Andere Apps mit eigenem `notFound`-Text sollten denselben Rat prüfen.

17. **Console: das Testkonto stand nicht unter Dahlias internen Testern** [hoch für jeden Testlauf, behoben]. koblin@gmail.com war kontoweit Lizenztester (Liste „Tearoom testers“), Dahlias interner Track hatte aber nur „License testers (auto-generated)“ (gazania.ip@gmail.com) angehakt; Play lieferte deshalb 1.8.5 aus der Produktion. Ich habe „Tearoom testers“ um 13:50 angehakt. Danach musste koblin die Einladung annehmen; die erste Annahme um 14:00 lief versehentlich in Brave, dem Standardbrowser des Tablets, wo dojolava angemeldet ist (der Agent hatte den Link ohne Browserangabe geöffnet). Erst die Annahme in Chrome als koblin um 18:54 wirkte: das Update stand eine Minute später bereit. Lazy Moves, Weatherfold und Feldbuch hatten die Liste schon. Für künftige Läufe: vor dem Testlauf in der Console nachsehen, dass „Tearoom testers“ beim internen Track angehakt ist, und auf dem Gerät den Opt-in-Link in dem Browser öffnen, in dem das Play-Konto angemeldet ist; die Opt-in-Seite muss „You're a tester“ zeigen. Wer schon Tester ist, bekommt neue interne Versionen in Minuten (Lazy Moves Code 44: höchstens 40 Minuten).

## Behoben nach dem Lauf

- Befund 15: `EditorScreen.kt` schließt nach einem Kauf auch das Menü, wenn das Pro-Sheet offen war; die Danke-Meldung erscheint dann im Editor. Auf dem Gerät noch nicht geprüft (braucht einen neuen internen Build und einen neuen Kauf über das Menü).

- Befund 16: `pro_restore_not_found` in allen zehn Sprachen neu: Play ordnet den Kauf dem installierenden Konto zu; mit dem anderen Konto deinstallieren, im Play Store wechseln, neu installieren; der Hinweis, dass Favoriten dabei verloren gehen, wenn Android sie nicht aus der Sicherung zurückholt. Lazy Moves hatte denselben Rat und hat ihn in `84a85c1` korrigiert.

## Zustand nach dem Lauf

- Flugmodus aus, Gerät auf dem Startbildschirm.

- Dahlia 1.8.7 (Code 22) aus dem internen Track auf dem Tablet, installiert von koblin@gmail.com, Pro gekauft (`GPA.3313-0970-1888-40487`, nicht erstatten).
- Erstattet: `GPA.3389-6941-8135-01035` (Vorbereitung), `GPA.3315-3991-6700-35372` (Punkt 9), `GPA.3305-1018-7848-75767` (von Play selbst, Punkt 12).
- Play Store auf dem Tablet: Daten am 6. November gelöscht (Mathis' OK), Konto koblin@gmail.com aktiv, dojolava@gmail.com angemeldet.
- Interner Track: 1.8.7 (Code 22); Produktion unverändert 1.8.5 (Code 20). Kein Tag gesetzt.
