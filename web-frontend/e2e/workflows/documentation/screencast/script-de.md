# BATbern Event-Workflow Schulungsvideo Skript (Deutsch)

## Einleitung (0:00 - 0:30)

Willkommen zur BATbern Event-Management-Plattform. In diesem umfassenden Tutorial durchlaufen wir den kompletten Event-Lebenszyklus, von der ersten Erstellung bis zur abschliessenden Archivierung eines Events. Sie sehen in diesem Video alle wichtigen Schritte, die ein Organisator durchführt, um ein Berner Architekten Treffen zu planen und durchzuführen.

---

## Phase A: Event-Einrichtung (0:30 - 10:00)

### Dashboard & Authentifizierung (0:30 - 1:00)

Wir beginnen am Event-Dashboard. Hier sehen Sie die Übersicht aller bevorstehenden und vergangenen Veranstaltungen. Die Authentifizierung erfolgt über AWS Cognito und ist bereits durchgeführt. Sie sehen oben rechts Ihren Benutzernamen und haben Zugriff auf alle Funktionen als Organisator.

### Neues Event erstellen (1:00 - 4:00)

Jetzt erstellen wir ein neues Event. Klicken Sie auf den Button "Neue Veranstaltung" oben rechts. Es öffnet sich ein modales Formular mit allen wichtigen Feldern.

Wir geben folgende Informationen ein:

**Event-Nummer**: Eine eindeutige Kennung für dieses Event. Das System verwendet intern das Format "BATbern" gefolgt von der Nummer. Dies dient der Identifikation in der Datenbank und in URLs.

**Titel**: Ein aussagekräftiger Name für die Veranstaltung, den die Teilnehmer auf der öffentlichen Website sehen werden.

**Beschreibung**: Eine kurze Erläuterung des Event-Themas. Diese Information hilft Interessenten bei der Anmeldung.

**Event-Typ**: Wählen Sie zwischen drei Formaten. "Abend" für Feierabend-Events, "Nachmittag" für Nachmittagsveranstaltungen, oder "Ganztag" für ganztägige Konferenzen. Dies beeinflusst die Zeitplanung und Slot-Verwaltung.

**Datum und Anmeldefrist**: Das Event-Datum legt fest, wann die Veranstaltung stattfindet. Die Anmeldefrist ist wichtig für die Teilnehmer-Verwaltung.

**Veranstaltungsort**: Name und Adresse des Veranstaltungsortes. Diese Informationen werden auf der öffentlichen Website angezeigt.

Wir klicken auf "Speichern" und das Event wird erstellt. Das System kehrt automatisch zum Dashboard zurück, wo Sie das neue Event in der Liste sehen können.

### Aufgaben an Organisatoren zuweisen (4:00 - 6:00)

Nach der Event-Erstellung navigieren wir zur Event-Detailseite. Hier können wir Aufgaben an Teammitglieder zuweisen. Klicken Sie auf "Bearbeiten" um das Event-Formular erneut zu öffnen.

Wechseln Sie zum Tab "Aufgaben". Hier sehen Sie eine vordefinierte Liste von Standard-Aufgaben, die bei jedem Event anfallen:

- **Venue Booking**: Reservation des Veranstaltungsortes
- **Partner Meeting**: Koordination mit Sponsoren und Partnern
- **Moderator Assignment**: Zuweisung eines Moderators für die Veranstaltung
- **Newsletter: Topic**: Newsletter-Versand zur Themen-Bekanntgabe
- **Newsletter: Speaker**: Newsletter-Versand zur Referenten-Bekanntgabe
- **Newsletter: Final**: Finaler Newsletter mit vollständigem Programm

Für jede Aufgabe wählen wir einen verantwortlichen Organisator aus dem Dropdown-Menü. Dies stellt sicher, dass alle wichtigen Tätigkeiten klar zugeordnet sind und nichts vergessen wird.

Nach der Zuweisung klicken wir auf "Speichern". Das System speichert alle Aufgaben und kehrt zur Event-Detailseite zurück.

Um die Aufgaben zu überprüfen, navigieren wir zur Aufgabenliste. Zunächst sehen Sie die Standard-Filterung "Meine Aufgaben", die nur Ihre eigenen Aufgaben anzeigt. Wir ändern den Filter auf "Alle Aufgaben", um alle Zuweisungen zu sehen. Dies gibt einen Überblick über die Verantwortlichkeiten im gesamten Team.

### Themenauswahl über Heat Map (6:00 - 7:30)

Zurück auf der Event-Detailseite beginnen wir mit der inhaltlichen Planung. Der erste Schritt ist die Themenauswahl. Klicken Sie auf "Thema auswählen".

Es öffnet sich eine Ansicht mit verschiedenen Themen-Kategorien. Die Standardansicht zeigt eine Liste, aber wir nutzen die Heat Map für eine bessere Übersicht.

Die Heat Map ist eine Zwei-Dimensionale Matrix, die Themen nach ihrer Popularität und Aktualität visualisiert. Helle Farben zeigen beliebte Themen, dunklere weniger häufig gewählte. Dies hilft bei der strategischen Themenplanung basierend auf den Interessen der Teilnehmer.

Wir wählen ein Thema aus der Heat Map, indem wir auf eine Zelle klicken. Das System markiert die Auswahl und zeigt Details an. Nach der Bestätigung speichert das System das gewählte Thema und öffnet automatisch die Referenten-Brainstorming-Ansicht.

### Referenten-Brainstorming (7:30 - 10:00)

Jetzt sammeln wir potenzielle Referenten für das gewählte Thema. In dieser Phase erstellen wir einen Pool von Kandidaten, die wir später kontaktieren werden.

Für jeden Referenten-Kandidaten geben wir ein:

- **Vorname**: Der Vorname des potenziellen Referenten
- **Firma**: Die Organisation, bei der die Person arbeitet
- **Expertise**: Das Fachgebiet oder die relevante Erfahrung
- **Zugewiesen an**: Welcher Organisator für die Kontaktaufnahme zuständig ist

Wir fügen vier Referenten-Kandidaten hinzu. Dies gibt uns ausreichend Optionen für die Kontaktaufnahme, falls nicht alle zusagen.

Nach dem Hinzufügen aller Kandidaten klicken wir auf "Weiter zur Kontaktierung". Das System wechselt automatisch zur Kanban-Ansicht, wo wir den Kontaktstatus verfolgen können.

---

## Phase B: Referenten-Kontaktierung (10:00 - 20:00)

### Kanban-Board Übersicht (10:00 - 11:00)

Wir befinden uns jetzt in der Referenten-Kontaktierungs-Phase. Das Kanban-Board zeigt vier Spalten, die den Workflow abbilden:

1. **INTERESTED**: Alle Kandidaten aus dem Brainstorming starten hier
2. **CONTACTED**: Referenten, die wir bereits kontaktiert haben
3. **READY**: Referenten, die grundsätzlich zugesagt haben
4. **ACCEPTED**: Referenten mit vollständiger Zusage und Inhaltseinreichung

Diese Visualisierung gibt einen schnellen Überblick über den Fortschritt der Referenten-Gewinnung.

### Referenten kontaktieren (11:00 - 13:00)

Jetzt kontaktieren wir systematisch alle Referenten-Kandidaten. Für jeden Kandidaten klicken wir auf die Karte und öffnen den Kontakt-Dialog.

Im Dialog geben wir ein:

- **Kontaktmethode**: E-Mail, Telefon, oder persönlich
- **Notizen**: Was wurde besprochen? Gibt es Rückmeldungen? Wann erfolgt Follow-up?

Diese Dokumentation ist wichtig für die Nachverfolgung und für andere Teammitglieder, die den Status einsehen müssen.

Nach dem Speichern aktualisiert das System automatisch den Status. Sie sehen jetzt, dass alle fünf Referenten kontaktiert wurden. Die Karten zeigen die Kontaktmethode und das Datum der letzten Kontaktaufnahme.

### Status-Übergänge im Kanban (13:00 - 15:00)

Nachdem wir positive Rückmeldungen erhalten haben, verschieben wir Referenten durch den Workflow. Wir nutzen Drag-and-Drop, um Karten zwischen den Spalten zu verschieben.

**Von CONTACTED zu READY**: Wir ziehen Referenten, die grundsätzlich zugesagt haben, in die READY-Spalte. Dies signalisiert, dass sie bereit sind, Inhalte einzureichen.

Das System öffnet nach jedem Drag-and-Drop einen Bestätigungs-Dialog. Hier können Sie optional einen Grund für die Statusänderung angeben. Dies ist nützlich für Audit-Trails und Dokumentation. Wir bestätigen ohne zusätzliche Notiz.

Wir verschieben vier Referenten in die READY-Spalte. Ein Referent bleibt in CONTACTED, da wir noch auf Rückmeldung warten.

### Finale Zusagen (15:00 - 20:00)

Im nächsten Schritt verschieben wir Referenten von READY zu ACCEPTED. Dies erfolgt erst, nachdem konkrete Zusagen vorliegen und der Referent bereit ist, Präsentations-Inhalte einzureichen.

Wieder nutzen wir Drag-and-Drop. Jeder Übergang wird im System protokolliert mit Zeitstempel und Benutzer. Dies schafft Transparenz und ermöglicht Nachverfolgung.

Alle vier READY-Referenten werden zu ACCEPTED verschoben. Das Kanban-Board zeigt jetzt deutlich unseren Fortschritt: Vier Referenten sind bereit für die Inhaltseinreichung.

---

## Phase B.5: Inhaltseinreichung (20:00 - 25:00)

### Thema veröffentlichen (20:00 - 21:00)

Bevor Referenten ihre Inhalte einreichen können, müssen wir das Thema veröffentlichen. Dies macht das Thema auf der öffentlichen Website sichtbar und ermöglicht die Content-Submission.

Wir navigieren zum Tab "Veröffentlichung". Hier sehen Sie drei Publishing-Schritte:

1. **Thema veröffentlichen**: Macht das gewählte Thema öffentlich
2. **Referenten veröffentlichen**: Veröffentlicht die Referenten-Liste
3. **Agenda veröffentlichen**: Veröffentlicht das finale Programm mit Zeitslots

Wir klicken auf "Thema veröffentlichen". Das System aktiviert das Thema und zeigt einen Erfolgs-Indikator. Der Button wird ausgegraut, da dieser Schritt nur einmal durchgeführt werden kann.

### Präsentations-Inhalte einreichen (21:00 - 25:00)

Zurück im Referenten-Tab reichen wir nun für jeden Referenten die Präsentations-Inhalte ein. Klicken Sie auf eine Referenten-Karte in der ACCEPTED-Spalte.

Es öffnet sich ein Drawer auf der rechten Seite mit dem Inhalts-Formular. Wir sehen drei Hauptbereiche:

**Referenten-Zuordnung**: Zunächst ordnen wir den Brainstorming-Kandidaten einer echten Person aus der Speaker-Datenbank zu. Wir nutzen die Suchfunktion, um den richtigen Speaker zu finden. Das System bietet Auto-Complete an. Wählen Sie den passenden Treffer aus der Liste.

**Präsentations-Titel**: Ein prägnanter Titel für die Präsentation. Dieser erscheint auf der Website und in gedruckten Programmen.

**Abstract**: Eine Zusammenfassung des Präsentations-Inhalts. Dies hilft Teilnehmern bei der Entscheidung, welche Sessions sie besuchen möchten.

Nach dem Ausfüllen klicken wir auf "Inhalt einreichen". Das System speichert die Informationen und schliesst den Drawer. Die Referenten-Karte zeigt jetzt einen visuellen Indikator, dass Inhalte eingereicht wurden.

Wir wiederholen diesen Prozess für alle drei Referenten mit zugesagten Präsentationen. Das System zeigt kontinuierlich den Fortschritt an. Ein Progress-Dashboard am oberen Rand zeigt, wie viele Inhalte bereits eingereicht wurden.

---

## Phase C: Qualitätsprüfung (25:00 - 30:00)

### Referenten veröffentlichen (25:00 - 26:00)

Nach der Inhaltseinreichung folgt die Qualitätsprüfung. Zunächst müssen wir die Referenten veröffentlichen, um ihre Profile auf der öffentlichen Website sichtbar zu machen.

Wir navigieren wieder zum Tab "Veröffentlichung" und klicken auf "Referenten veröffentlichen". Das System aktiviert alle Referenten-Profile mit eingereichten Inhalten. Der Button wird ausgegraut nach erfolgreicher Veröffentlichung.

### Präsentations-Genehmigung (26:00 - 30:00)

Zurück im Referenten-Tab prüfen und genehmigen wir nun jede eingereichte Präsentation. Klicken Sie auf eine Präsentations-Karte.

Der Quality-Review-Drawer öffnet sich. Hier sehen Sie:

- **Vollständiger Titel und Abstract**: Zur inhaltlichen Prüfung
- **Referenten-Informationen**: Name, Firma, Expertise
- **Submission-Datum**: Wann wurden die Inhalte eingereicht

Als Organisator prüfen Sie:

- Ist der Titel präzise und aussagekräftig?
- Ist der Abstract vollständig und verständlich?
- Passt der Inhalt zum gewählten Thema?
- Sind Rechtschreibung und Grammatik korrekt?

Wenn alles in Ordnung ist, klicken wir auf "Genehmigen". Das System markiert die Präsentation als approved und schliesst den Drawer.

Wir genehmigen alle drei Präsentationen. Nach jeder Genehmigung aktualisiert sich die Karte visuell mit einem grünen Häkchen oder Status-Badge. Dies zeigt auf einen Blick, welche Präsentationen bereits die Qualitätsprüfung durchlaufen haben.

Das System ist jetzt bereit für die Slot-Zuweisung und Agenda-Erstellung.

---

## Phase D: Slot-Zuweisung und Veröffentlichung (30:00 - 38:00)

### Sessions-Ansicht (30:00 - 31:00)

Für die Slot-Zuweisung wechseln wir zur Sessions-Ansicht. Im Referenten-Tab finden Sie oben rechts einen Toggle-Button "Sessions-Ansicht".

Die Sessions-Ansicht zeigt alle genehmigten Präsentationen in einer Liste mit zusätzlichen Informationen:

- Titel der Präsentation
- Referent und Firma
- Geschätzte Dauer
- Status der Slot-Zuweisung

Diese Ansicht ist optimiert für das Programm-Management und die zeitliche Planung.

### Slot-Zuweisungs-Seite (31:00 - 32:00)

Klicken Sie auf "Slot-Zuweisungen verwalten". Es öffnet sich eine dedizierte Seite für die Zeitplanung.

Diese Seite zeigt zwei Bereiche:

**Linke Seite**: Die Event-Struktur mit allen verfügbaren Zeitslots. Diese Slots wurden automatisch basierend auf dem Event-Typ generiert. Für ein Abend-Event sehen Sie typischerweise 3-4 Slots zwischen 18:00 und 21:00 Uhr.

**Rechte Seite**: Eine Liste aller genehmigten Präsentationen, die noch keinem Slot zugewiesen sind. Hier können Sie auch die Auto-Assign-Funktion nutzen.

### Automatische Slot-Zuweisung (32:00 - 33:00)

Für eine schnelle initiale Planung nutzen wir die Auto-Assign-Funktion. Klicken Sie auf den Button "Automatisch zuweisen".

Das System öffnet einen Bestätigungs-Dialog, der erklärt, wie der Algorithmus funktioniert:

- Präsentationen werden basierend auf Dauer und Priorität zugewiesen
- Zeitslots werden optimal ausgefüllt
- Pausen werden berücksichtigt
- Referenten-Präferenzen werden beachtet, falls angegeben

Klicken Sie auf "Bestätigen". Das System führt die automatische Zuweisung durch. Sie sehen eine Animation oder einen Lade-Indikator.

Nach wenigen Sekunden aktualisiert sich die Ansicht. Alle Präsentationen sind jetzt Zeitslots zugeordnet. Die linke Seite zeigt die vollständige Agenda mit Zeitangaben und Referenten.

Sie können jederzeit manuell Anpassungen vornehmen, indem Sie Präsentationen per Drag-and-Drop zwischen Slots verschieben.

### Zurück zur Event-Seite (33:00 - 34:00)

Nach der Slot-Zuweisung klicken wir auf "Zurück zur Veranstaltung". Das System kehrt zur Event-Detailseite zurück.

Alle Tabs sind jetzt aktualisiert mit den neuesten Informationen. Die Agenda ist vollständig geplant und bereit zur Veröffentlichung.

### Agenda veröffentlichen (34:00 - 38:00)

Der finale Schritt ist die Agenda-Veröffentlichung. Navigieren Sie zum Tab "Veröffentlichung".

Sie sehen jetzt alle drei Publishing-Schritte:

1. ✅ Thema veröffentlichen (bereits durchgeführt)
2. ✅ Referenten veröffentlichen (bereits durchgeführt)
3. ⏳ Agenda veröffentlichen (noch ausstehend)

Klicken Sie auf "Agenda veröffentlichen". Dies ist der wichtigste Publishing-Schritt, da er das vollständige Event-Programm für die Öffentlichkeit freigibt.

Das System führt mehrere Aktionen durch:

- Generiert die öffentliche Agenda-Seite
- Aktualisiert die Event-Übersicht
- Sendet optional Benachrichtigungen an registrierte Teilnehmer
- Erstellt exportierbare Formate (PDF, iCal)

Nach erfolgreicher Veröffentlichung zeigt das System einen Erfolgs-Indikator. Der Button "Agenda veröffentlichen" wird ausgegraut.

Das Event ist jetzt vollständig geplant und öffentlich zugänglich. Teilnehmer können sich registrieren und das komplette Programm einsehen.

---

## Phase E: Archivierung (38:00 - 43:00)

### Event-Status ändern (38:00 - 39:00)

Nach der Durchführung des Events archivieren wir es für die Historie. Dies hält die aktive Event-Liste übersichtlich und markiert abgeschlossene Veranstaltungen.

Wechseln Sie zum Tab "Übersicht" auf der Event-Detailseite. Hier sehen Sie alle grundlegenden Event-Informationen.

Klicken Sie auf "Bearbeiten". Das Event-Formular öffnet sich erneut, diesmal für Status-Änderungen.

### Status auf ARCHIVIERT setzen (39:00 - 40:00)

Im Formular finden Sie das Feld "Status". Aktuell steht es auf "PUBLISHED" oder "COMPLETED". Klicken Sie auf das Dropdown-Menü.

Sie sehen verschiedene Status-Optionen:

- **DRAFT**: Event in Planung
- **PUBLISHED**: Event öffentlich sichtbar
- **COMPLETED**: Event durchgeführt
- **ARCHIVED**: Event archiviert

Wählen Sie "ARCHIVIERT". Das System markiert diese Auswahl im Formular.

### Workflow-Validierung (40:00 - 41:00)

Wenn Sie jetzt auf "Speichern" klicken, erscheint eine Validierungs-Meldung. Das System prüft automatisch, ob alle erforderlichen Workflow-Schritte abgeschlossen sind:

- Wurden alle Präsentationen genehmigt?
- Ist die Agenda vollständig?
- Sind alle Publishing-Schritte durchgeführt?

Falls Schritte fehlen, verhindert das System die Archivierung und zeigt einen Fehler an. Dies schützt vor versehentlicher Archivierung unvollständiger Events.

In unserem Fall zeigt das System möglicherweise eine Warnung, weil bestimmte Post-Event-Schritte noch ausstehen könnten.

### Validierung überschreiben (41:00 - 42:00)

Für Test-Zwecke oder in speziellen Situationen können Sie die Workflow-Validierung überschreiben. Aktivieren Sie die Checkbox "Workflow-Validierung überschreiben".

Diese Option sollte nur bewusst genutzt werden. Ein Kommentar-Feld erscheint, wo Sie den Grund für das Override dokumentieren können.

Klicken Sie erneut auf "Speichern". Diesmal akzeptiert das System die Archivierung trotz der Warnung.

### Archivierung bestätigen (42:00 - 43:00)

Das Modal schliesst sich. Sie kehren zur Event-Übersicht zurück.

Oben sehen Sie jetzt ein deutliches Badge "ARCHIVIERT" neben dem Event-Titel. Dies zeigt auf einen Blick, dass dieses Event abgeschlossen ist.

Archivierte Events:

- Erscheinen weiterhin in der Event-Liste mit Filter "Archivierte anzeigen"
- Können für Reports und Statistiken genutzt werden
- Sind nicht mehr editierbar (ohne Admin-Rechte)
- Dienen als historische Referenz für zukünftige Event-Planungen

---

## Abschluss (43:00)

Damit ist der vollständige Event-Workflow abgeschlossen. Sie haben gesehen:

- **Phase A**: Event-Erstellung, Task-Management, Themenauswahl, Referenten-Brainstorming
- **Phase B**: Referenten-Kontaktierung und Kanban-Workflow
- **Phase B.5**: Inhaltseinreichung für Präsentationen
- **Phase C**: Qualitätsprüfung und Genehmigung
- **Phase D**: Slot-Zuweisung und Agenda-Veröffentlichung
- **Phase E**: Event-Archivierung

Die BATbern-Plattform unterstützt Sie durchgängig mit automatisierten Workflows, klaren Status-Übergängen, und umfassender Dokumentation. Dies stellt sicher, dass kein wichtiger Schritt vergessen wird und alle Teammitglieder jederzeit den aktuellen Stand einsehen können.

Vielen Dank für Ihre Aufmerksamkeit. Bei Fragen zur Plattform wenden Sie sich bitte an den Support oder konsultieren Sie die ausführliche Dokumentation.
