# Narration Script Mapping

This document maps the playful German narration script to specific locations in `screencast-event-workflow.spec.ts`.

## Narration Markers

### NARRATION_01 (Line 63)

```
[excited] Willkommen zur BATbern Event-Management-Plattform! [playful] Heute zeige ich Ihnen, wie man ein Event plant, ohne dabei den Verstand zu verlieren. [chuckling] Wir durchlaufen den kompletten Event-Lebenszyklus, von "Oh Gott, wir brauchen ein Event" bis zu "Endlich vorbei, ab ins Archiv damit!" [pause] Sie sehen in diesem Video alle wichtigen Schritte, die ein Organisator durchführt, um ein Berner Architekten Treffen zu planen, ohne dabei in Panik zu geraten.
```

**Location**: Before dashboard navigation

### NARRATION_02 (Line 92)

```
[cheerful] Wir beginnen am Event-Dashboard. [playful] Das ist sozusagen Ihre Kommandozentrale, von der aus Sie den Überblick über alle Events behalten, die Sie jemals organisiert haben oder noch organisieren werden. [pause] Die Authentifizierung über AWS Cognito ist bereits erledigt. [satisfied] Sie sehen oben rechts Ihren Benutzernamen, und damit haben Sie die Macht, alle Funktionen als Organisator zu nutzen. [dramatic] Mit großer Macht kommt große Verantwortung!
```

**Location**: At dashboard

### NARRATION_03 (Line 102)

```
[enthusiastic] Jetzt erstellen wir ein brandneues Event! [excited] Klicken Sie auf den Button "Neue Veranstaltung" oben rechts. [pause] Boom! Ein modales Formular erscheint. [playful] Keine Sorge, es sieht nach viel aus, aber wir füllen das gemeinsam aus.
```

**Location**: Before clicking create event

### NARRATION_04 (Line 114)

```
[professional] Event-Nummer. [casual] Eine eindeutige Kennung für dieses Event. Das System verwendet intern das Format "BATbern" gefolgt von der Nummer. [playful] Sozusagen die Geburtsurkunde Ihres Events. Dies dient der Identifikation in der Datenbank und in URLs.
```

**Location**: During form filling

### NARRATION_05 (Line 120)

```
[short pause] Titel. [cheerful] Hier kommt der Name, der Ihre Teilnehmer begeistern soll! [playful] "Langweiliges Architektur-Event Nummer 47" wäre zwar ehrlich, aber vielleicht nicht die beste Wahl. [chuckling] Wählen Sie etwas Aussagekräftiges, das die Leute auf der öffentlichen Website sehen werden.
```

### NARRATION_06 (Line 122)

```
[short pause] Beschreibung. [casual] Eine kurze Erläuterung des Event-Themas. [helpful] Diese Information hilft Interessenten zu verstehen, warum sie sich unbedingt anmelden sollten. [playful] Oder zumindest, worum es geht.
```

### NARRATION_07 (Line 124)

```
[pause] Event-Typ. [professional] Wählen Sie zwischen drei Formaten: [clear] "Abend" für Feierabend-Events, [cheerful] bei denen man nach der Arbeit noch ein bisschen netzwerken kann. "Nachmittag" für Nachmittagsveranstaltungen, [playful] perfekt für alle, die abends lieber auf dem Sofa sitzen. [pause] Oder "Ganztag" für ganztägige Konferenzen. [dramatic] Da brauchen Sie dann viel Kaffee! [short pause] Dies beeinflusst die Zeitplanung und Slot-Verwaltung.
```

### NARRATION_08 (Line 123 - Date)

```
[professional] Datum und Anmeldefrist. [clear] Das Event-Datum legt fest, wann die Veranstaltung stattfindet. [playful] Bitte wählen Sie ein Datum in der Zukunft, Zeitreisen unterstützen wir noch nicht. [chuckling] Die Anmeldefrist ist wichtig für die Teilnehmer-Verwaltung.
```

### NARRATION_09 (Line 125-126 - Venue)

```
[short pause] Veranstaltungsort. [casual] Name und Adresse des Veranstaltungsortes. [playful] Also nicht "bei mir im Keller", sondern ein richtiger Ort mit Adresse. [cheerful] Diese Informationen werden auf der öffentlichen Website angezeigt, damit die Leute auch wirklich hingehen können.
```

### NARRATION_10 (Line 151)

```
[satisfied] Wir klicken auf "Speichern" und das Event wird erstellt. [excited] Tada! [pause] Das System kehrt automatisch zum Dashboard zurück, und da ist es! Ihr brandneues Event in der Liste. [playful] Ihr Baby ist geboren!
```

**Location**: After event created

### NARRATION_11 (Line 155)

```
[pause] Nach der Event-Erstellung navigieren wir zur Event-Detailseite. [professional] Hier können wir Aufgaben an Teammitglieder zuweisen. [playful] Denn warum sollten Sie alles alleine machen, wenn Sie ein ganzes Team haben? [chuckling] Klicken Sie auf "Bearbeiten" um das Event-Formular erneut zu öffnen.
```

**Location**: Before task assignment

### NARRATION_12 (Line 169)

```
[clear] Wechseln Sie zum Tab "Aufgaben". [professional] Hier sehen Sie eine vordefinierte Liste von Standard-Aufgaben, die bei jedem Event anfallen. [playful] Das System kennt sich aus, es weiß, was alles zu tun ist. [pause] Für jede Aufgabe wählen wir einen verantwortlichen Organisator aus dem Dropdown-Menü. [helpful] Dies stellt sicher, dass alle wichtigen Tätigkeiten klar zugeordnet sind und nichts vergessen wird. [dramatic] Denn vergessene Aufgaben führen zu Chaos, und Chaos führt zu... naja, mehr Chaos.
```

### NARRATION_13 (Line 198)

```
[confident] Nach der Zuweisung klicken wir auf "Speichern". [satisfied] Das System speichert alle Aufgaben und kehrt zur Event-Detailseite zurück. [cheerful] Perfekt!
```

### NARRATION_14 (Line 211)

```
[pause] Um die Aufgaben zu überprüfen, navigieren wir zur Aufgabenliste. [informative] Zunächst sehen Sie die Standard-Filterung "Meine Aufgaben", die nur Ihre eigenen Aufgaben anzeigt. [playful] Aber wir sind neugierig und wollen wissen, was die anderen so machen. [chuckling] Wir ändern den Filter auf "Alle Aufgaben", um alle Zuweisungen zu sehen. Dies gibt einen Überblick über die Verantwortlichkeiten im gesamten Team. [whispers] Und wer vielleicht gerade nichts zu tun hat.
```

### NARRATION_15 (Line 229)

```
[excited] Zurück auf der Event-Detailseite beginnen wir mit der inhaltlichen Planung! [enthusiastic] Der erste Schritt ist die Themenauswahl. Klicken Sie auf "Thema auswählen".
```

**Location**: Before topic selection

### NARRATION_16 (Line 231)

```
[cheerful] Es öffnet sich eine Ansicht mit verschiedenen Themen-Kategorien. Die Standardansicht zeigt eine Liste, [excited] aber wir nutzen die Heat Map für eine bessere Übersicht. [playful] Denn wer liebt nicht eine gute Heat Map?
```

### NARRATION_17 (Line 235)

```
[curious] Die Heat Map ist eine Zwei-Dimensionale Matrix, die Themen nach ihrer Popularität und Aktualität visualisiert. [excited] Helle Farben zeigen beliebte Themen, [casual] sozusagen die Rockstars unter den Architektur-Themen. [pause] Dunklere Farben zeigen weniger häufig gewählte Themen. [playful] Die Außenseiter, die auch eine Chance verdienen. [helpful] Dies hilft bei der strategischen Themenplanung basierend auf den Interessen der Teilnehmer.
```

### NARRATION_18 (Line 239)

```
[instructional] Wir wählen ein Thema aus der Heat Map, indem wir auf eine Zelle klicken. [satisfied] Das System markiert die Auswahl und zeigt Details an. [excited] Nach der Bestätigung speichert das System das gewählte Thema und öffnet automatisch die Referenten-Brainstorming-Ansicht. [playful] Das System ist wie ein guter Assistent, es weiß immer, was als Nächstes kommt!
```

### NARRATION_19 (Line 252)

```
[enthusiastic] Jetzt sammeln wir potenzielle Referenten für das gewählte Thema! [professional] In dieser Phase erstellen wir einen Pool von Kandidaten, die wir später kontaktieren werden. [pause] Wir fügen vier Referenten-Kandidaten hinzu. [strategic] Dies gibt uns ausreichend Optionen für die Kontaktaufnahme, falls nicht alle zusagen. [playful] Denn Referenten sind wie Katzen, manchmal sagen sie einfach nein, ohne Grund.
```

**Location**: Speaker brainstorming

### NARRATION_20 (Line 269)

```
[confident] Nach dem Hinzufügen aller Kandidaten klicken wir auf "Weiter zur Kontaktierung". [satisfied] Das System wechselt automatisch zur Kanban-Ansicht, wo wir den Kontaktstatus verfolgen können. [excited] Kanban! Das klingt wichtig und organisiert!
```

### NARRATION_21 (Line 280)

```
[professional] Wir befinden uns jetzt in der Referenten-Kontaktierungs-Phase. [informative] Das Kanban-Board zeigt mehrere Spalten, die den Workflow abbilden. [playful] Von "Wer ist das?" über "Angefragt" bis "Hurra, zugesagt!" [helpful] Diese Visualisierung gibt einen schnellen Überblick über den Fortschritt der Referenten-Gewinnung.
```

**Location**: Phase B start

### NARRATION_22 (Line 294)

```
[methodical] Jetzt kontaktieren wir systematisch alle Referenten-Kandidaten. [instructional] Für jeden Kandidaten klicken wir auf die Karte und öffnen den Kontakt-Dialog. [professional] Diese Dokumentation ist wichtig für die Nachverfolgung und für andere Teammitglieder, die den Status einsehen müssen. [playful] Sonst fragt nächste Woche jemand: "Haben wir den schon kontaktiert?" Und niemand weiß es. [chuckling] Chaos vermieden!
```

**Location**: Contact speakers

### NARRATION_23 (Line 308)

```
[positive] Nachdem wir positive Rückmeldungen erhalten haben, [excited] verschieben wir Referenten durch den Workflow! [playful] Das ist wie Tetris, nur mit Menschen. [instructional] Wir nutzen Drag-and-Drop, um Karten zwischen den Spalten zu verschieben. [clear] Wir verschieben vier Referenten in die "READY"-Spalte.
```

**Location**: Drag to READY

### NARRATION_24 (Line 355)

```
[confident] Im nächsten Schritt verschieben wir Referenten von "READY" zu "ACCEPTED". [satisfied] Alle vier READY-Referenten werden zu ACCEPTED verschoben. [excited] Das läuft wie geschmiert!
```

**Location**: Drag to ACCEPTED

### NARRATION_25 (Line 407)

```
[important] Bevor Referenten ihre Inhalte einreichen können, müssen wir das Thema veröffentlichen. [instructional] Wir navigieren zum Tab "Veröffentlichung" und klicken auf "Thema veröffentlichen". [informative] Im unteren Bereich ist ein Preview des Events auf der öffentlichen Seite ersichtlich. [satisfied] Schön, oder?
```

**Location**: Publish topic

### NARRATION_26 (Line 422)

```
[professional] Zurück im Referenten-Tab reichen wir nun für jeden Referenten die Präsentations-Inhalte ein. [casual] Titel, Abstract, die üblichen Verdächtigen. [methodical] Wir wiederholen diesen Prozess für alle drei Referenten mit zugesagten Präsentationen. [playful] Copy, paste, repeat. [chuckling] Nein, Spaß, jeder Referent hat natürlich einzigartige Inhalte!
```

**Location**: Submit content

### NARRATION_27 (Line 481)

```
[pause] Nach der Inhaltseinreichung folgt die Qualitätsprüfung. [professional] Da wir jetzt die Inhalte haben, können wir die Referenten veröffentlichen. [instructional] Wir klicken auf "Referenten veröffentlichen". [excited] Und jetzt kommt's! [satisfied] Nun sind auf der öffentlichen Webseite nicht nur das Thema, sondern auch die zugesagten Referenten mit ihrem Thema ersichtlich. [cheerful] Die Welt kann es sehen!
```

**Location**: Publish speakers

### NARRATION_28 (Line 492)

```
[professional] Zurück im Referenten-Tab prüfen und genehmigen wir nun jede eingereichte Präsentation. [playful] Wir spielen jetzt Qualitätskontrolle. [pause] Sieht gut aus, sieht gut aus, das auch. [confident] Wir genehmigen alle drei Präsentationen. [satisfied] Grünes Licht für alle!
```

**Location**: Approve presentations

### NARRATION_29 (Line 524)

```
[instructional] Für die Slot-Zuweisung wechseln wir zur Sessions-Ansicht. Klicken Sie auf "Slot-Zuweisungen verwalten". [pause] Jetzt wird's zeitlich!
```

**Location**: Sessions view

### NARRATION_30 (Line 541)

```
[enthusiastic] Für eine schnelle initiale Planung nutzen wir die Auto-Assign-Funktion. [excited] Das System führt die automatische Zuweisung durch. [playful] Magie! Das System übernimmt die Arbeit. [informative] Durch Drag-und-Drop ist hier auch eine manuelle Zuweisung möglich. [casual] Falls Sie dem Computer nicht vertrauen oder einfach gerne Dinge herumschieben.
```

**Location**: Auto-assign

### NARRATION_31 (Line 560)

```
[calm] Nach der Slot-Zuweisung klicken wir auf "Zurück zur Veranstaltung".
```

### NARRATION_32 (Line 576)

```
[dramatic] Der finale Schritt ist die Agenda-Veröffentlichung! [enthusiastic] Das große Finale! [instructional] Klicken Sie auf "Agenda veröffentlichen". [triumphant] Und... [excited] Der Event ist jetzt vollständig geplant und öffentlich inklusive detaillierter Agenda zugänglich! [cheerful] Konfetti! Feuerwerk! Okay, vielleicht nur in Gedanken, aber trotzdem! [satisfied] Wir haben es geschafft!
```

**Location**: Publish agenda

### NARRATION_33 (Line 601)

```
[pause] Nach der Durchführung des Events archivieren wir es für die Historie. [professional] Wechseln Sie zum Tab "Übersicht" und klicken Sie auf "Bearbeiten". [playful] Zeit, das Event in Rente zu schicken.
```

**Location**: Archival start

### NARRATION_34 (Line 619)

```
[clear] Im Formular wählen Sie "ARCHIVIERT" als Status. [casual] Das Event war toll, aber jetzt ist es Geschichte.
```

### NARRATION_35 (Line 639)

```
[informative] Für Test-Zwecke können Sie die Workflow-Validierung überschreiben. [instructional] Aktivieren Sie die Checkbox "Workflow-Validierung überschreiben" und klicken Sie auf "Speichern". [playful] Das ist sozusagen der Notausgang, falls mal was nicht nach Plan läuft.
```

### NARRATION_36 (Line 669)

```
[triumphant] Damit ist der vollständige Event-Workflow abgeschlossen! [excited] Von der ersten Idee bis zum Archiv, wir haben die ganze Reise gemeinsam gemacht! [satisfied] Sie sind jetzt ein Event-Management-Profi! [cheerful] Vielen Dank für Ihre Aufmerksamkeit! [playful] Und denken Sie daran: Events planen macht Spaß, [chuckling] zumindest mit der richtigen Software! [laughing] Tschüss!
```

**Location**: End of test

---

## Implementation Instructions

To add these narrations to the test file:

1. Add each narration as a multi-line comment above the corresponding `logNarration()` call
2. Use the format:

```typescript
/*
 * NARRATION_XX: [emotion] Text here...
 */
logNarration('NARRATION_XX:XX', 'Short description');
```

3. The full playful narration text helps ElevenLabs or other TTS services apply proper emotional inflections

## Notes

- Emotion markers like `[excited]`, `[playful]`, `[chuckling]` help TTS services apply proper tone
- Pauses like `[pause]` and `[short pause]` give narration natural rhythm
- ElevenLabs supports these emotion markers directly in the text
