# QA-4 · Pro-Kauf über die gemeinsame Kauf-Bibliothek

30. Oktober 2026. Umfang: der einmalige Pro-Kauf aus ADR-0022 (`one.tearoom:unlock`), getestet auf Mathis' Handy mit 1.8.1 (Code 16) aus dem internen Test-Track, mit seinem einen privaten Google-Konto und den Testzahlungsarten von Play. Am Ende steht ein echter Kauf mit Erstattung.

## Voraussetzungen

- Das Konto steht in der Play Console unter Einstellungen → Lizenztests. Deshalb bietet die App Testzahlungsarten an und es fließt kein Geld.

- Die App ist aus Google Play installiert, nicht per Sideload.

- Optional: ein zweites Gerät, auf dem dasselbe Konto angemeldet ist (nur für Fall 3).

## Schon erledigt

- Testkauf mit der Standard-Testkarte war erfolgreich, Pro wurde freigeschaltet (30. Oktober 2026, 1.8.0).

- Signaturprüfung: Nach dem Update auf 1.8.1 (Code 16, der erste Build mit dem Play-Lizenzschlüssel) hat die App Play online gefragt und Pro behalten, ohne dass irgendwo Pro-Markierungen auftauchten. Ein falscher Schlüssel hätte Pro entfernt (`UnlockPolicy` zählt nur geprüfte Käufe). Schlüssel bestätigt (30. Oktober 2026).

## Testfälle

Die Texte stammen aus `values-de/strings_app.xml` und `values-de/strings_editor.xml`. Das Pro-Fenster öffnet sich über jede Pro-Markierung im Editor.

Mit aktivem Pro ist das Pro-Fenster nicht erreichbar, und ein Testkauf lässt sich nicht zurücknehmen: Im Play Store kann man einen einmaligen Testkauf nicht stornieren, und die Play Console erstattet keine Testbestellungen. Deshalb kommen zuerst die Fälle 1 bis 3, die den vorhandenen Testkauf brauchen, dann in Fall 4 der echte Kauf mit Erstattung, und erst danach die Fälle 5 bis 8, die ein Konto ohne Kauf voraussetzen, zuletzt Fall 9 (ausstehende Zahlung), der wieder einen Testkauf hinterlässt. Das Konto kommt dafür nach der Erstattung wieder in die Lizenztests.

### 1. Wiederherstellen auf demselben Konto

Schritte:

- Mit dem Testkauf auf dem Konto die App-Daten löschen (oder deinstallieren und neu aus Play installieren).

- App öffnen. Kurz warten, bis Play geantwortet hat.

Erwartung:

- Pro ist wieder aktiv, ohne dass der Kauf neu ausgelöst wird (Play wird bei jedem Fortsetzen gefragt).

- Taucht kurz eine Pro-Markierung auf, im Pro-Fenster „Kauf wiederherstellen“ tippen: „Pro ist auf diesem Gerät aktiv.“

Ergebnis: 

### 2. Offline

Schritte:

- Mit Pro im Flugmodus die App beenden (Force-Stop) und kalt starten.

- Flugmodus ausschalten, App fortsetzen.

Erwartung:

- Pro bleibt aus dem Zwischenspeicher freigeschaltet, es erscheinen keine Pro-Markierungen. Ein nicht erreichbares Play entfernt Pro nie.

- Online bleibt Pro weiterhin aktiv.

Der Teil „Offline ohne Pro“ steht als Fall 8.

Ergebnis: 

### 3. Zweites Gerät, gleiches Konto (optional)

Schritte:

- Auf dem zweiten Gerät, angemeldet mit dem Konto, das Pro besitzt, die App aus Google Play installieren und öffnen.

Erwartung:

- Pro wird beim Fortsetzen erkannt, ohne „Kauf wiederherstellen“ zu tippen.

Ergebnis: 

### 4. Echter Kauf, Erstattung und Wiederherstellen ohne Kauf

Testkäufe erscheinen nicht in der Play Console unter Bestellverwaltung. Nur eine echte Bestellung lässt sich erstatten und prüft damit den Widerruf.

Schritte:

- Das Konto wird in der Play Console unter Einstellungen → Lizenztests aus der Liste entfernt (macht der Agent auf Mathis' Wort). Einige Zeit warten. Zeigt Play danach noch die Testkarte, den Cache der Play-Store-App leeren.

- App-Daten löschen, Pro-Fenster öffnen, „Für … kaufen“ tippen und mit dem Konto echt bezahlen.

- Prüfen, dass Pro aktiv ist („Danke! Pro ist freigeschaltet.“) und die Pro-Markierungen weg sind.

- In der Play Console unter Bestellverwaltung die Bestellung erstatten (macht der Agent auf Mathis' Wort).

- App neu starten (kalt starten oder fortsetzen), mit Internet.

- Im Pro-Fenster „Kauf wiederherstellen“ tippen.

- Danach das Konto wieder in die Lizenztests aufnehmen (Agent auf Mathis' Wort), damit die Testzahlungsarten für die Fälle 5 bis 9 zurück sind.

Erwartung:

- Nach der Erstattung und dem nächsten Start mit Internet ist Pro weg und die Pro-Markierungen sind wieder da. Play muss geantwortet haben; offline behält der Zwischenspeicher Pro.

- „Kauf wiederherstellen“ zeigt: „Google Play hat für dieses Google-Konto keinen Pro-Kauf gefunden. Wenn du Pro mit einem anderen Google-Konto gekauft hast, wechsle im Play Store zu diesem Konto und tippe noch einmal auf „Kauf wiederherstellen“.“

- Kein Pro.

Ergebnis: 

### 5. Abgebrochener Kauf

Schritte:

- Ohne Pro „Für … kaufen“ tippen und den Play-Dialog verlassen (Zurück-Taste oder neben den Dialog tippen).

Erwartung:

- Kein Pro, kein Absturz, das Fenster ist wieder bedienbar und „Für … kaufen“ ist aktiv.

- Die Bibliothek ignoriert einen Abbruch absichtlich, es erscheint keine Meldung. „Google Play konnte den Kauf nicht öffnen. Bitte versuch es noch einmal.“ erscheint nur, wenn Play den Dialog gar nicht öffnen kann.

Ergebnis: 

### 6. Abgelehnte Zahlung

Schritte:

- Ohne Pro „Für … kaufen“ tippen, die Testkarte „always declines“ wählen und bestätigen.

Erwartung:

- Play zeigt seinen eigenen Ablehnungshinweis; schließt der Dialog mit einem Fehler, zeigt die App: „Der Kauf ist nicht zustande gekommen.“

- Kein Pro.

Ergebnis: 

### 7. Pro-Fenster auf Deutsch und Russisch bei 360 dp

Schritte:

- Das Handy auf eine Breite von 360 dp stellen (Einstellungen → Anzeige → Anzeigegröße, oder ein schmales Handy nehmen), die App-Sprache auf Deutsch und auf Russisch stellen.

- Das Pro-Fenster ohne Pro öffnen, auch mit einem Preisproblem oder dem Text „Ausstehend“, wenn das leicht erreichbar ist.

Erwartung:

- Kein abgeschnittener oder überlappender Text, kein horizontales Scrollen, „Für … kaufen“, „Erneut versuchen“ und „Kauf wiederherstellen“ sind lesbar. Ein kurzer Blick genügt.

Ergebnis: 

### 8. Offline ohne Pro

Schritte:

- Ohne Pro (Konto ohne Kauf) Flugmodus einschalten, App kalt starten und das Pro-Fenster öffnen.

- „Kauf wiederherstellen“ tippen.

- Flugmodus ausschalten und „Erneut versuchen“ tippen.

Erwartung:

- Das Fenster erklärt den fehlenden Preis: „Google Play ist gerade nicht erreichbar, daher gibt es noch keinen Preis. Prüf die Verbindung und versuch es noch einmal.“ mit der Schaltfläche „Erneut versuchen“.

- „Kauf wiederherstellen“ zeigt offline: „Google Play war nicht erreichbar, daher wurde nichts geprüft. Bitte versuch es später noch einmal.“

- Online zeigt „Erneut versuchen“ den Preis.

Ergebnis: 

### 9. Ausstehende Zahlung

Schritte:

- Sicherstellen, dass das Konto keinen Kauf hat (nach Fall 4) und wieder in den Lizenztests steht.

- Pro-Fenster öffnen, „Für … kaufen“ tippen, die Testkarte mit verzögerter Genehmigung („slow approval“) wählen und bestätigen.

- Das Fenster ansehen, dann noch einmal kaufen versuchen.

- Einige Minuten warten, bis Play die Zahlung genehmigt, dann die App schließen und wieder öffnen.

Erwartung:

- Das Fenster zeigt: „Der Kauf steht noch aus. Pro wird freigeschaltet, sobald er bezahlt ist.“

- Ein zweiter Kauf ist gesperrt, solange die Zahlung aussteht.

- Nach der Genehmigung und dem Wiederöffnen ist Pro freigeschaltet („Danke! Pro ist freigeschaltet.“) und die Pro-Markierungen sind weg.

Danach hat das Konto wieder einen Testkauf. Deshalb kommt dieser Fall zuletzt.

Ergebnis: 

## Offene Punkte

- Alle Fälle 1 bis 9 sind noch offen; sie laufen auf 1.8.1 (Code 16) aus dem internen Test-Track.

- Nicht geprüft wird die Wiederherstellung auf einem anderen Google-Konto (kein zweites Konto vorhanden). Nur der Text „Kauf nicht gefunden“ wird in Fall 4 mit demselben Konto ohne Kauf gesehen.

- Unklar ist, ob Play beim echten Kauf in Fall 4 den vorhandenen Testkauf als „bereits gekauft“ meldet. Das zeigt sich in Fall 4.
