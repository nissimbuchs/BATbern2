# BATbern Vortrag — Präsentations-Outline v2
## „Enterprise Software alleine mit AI bauen — von der Idee zur Produktion"

**Datum:** 6. März 2026
**Speaker:** Dr. Nissim J. Buchs
**Dauer:** 35 Minuten Vortrag + 10 Minuten Q&A
**Sprache:** Deutsch
**Thema des Events:** Agentic Software Development (BAT #60)

---

## Design-Direktiven für Slide-Erstellung

### Farben
```
Hintergrund:    #0F1117  (fast schwarz)
Primärtext:     #FFFFFF  (weiss)
Sekundärtext:   #94A3B8  (grau)
Akzent Blau:    #3B82F6  (Technologie, Code)
Akzent Grün:    #10B981  (Erfolg, Done, ✅)
Akzent Amber:   #F59E0B  (Lessons, Warnung)
Akzent Rot:     #EF4444  (Problem, ✗)
Akzent Violett: #8B5CF6  (AI, BMAD, Magic)
```

### Stil
- Modern, minimalistisch, tech-affin — kein Corporate Bingo
- **Zahlen:** Immer gross (>60pt) und prominent — Zahlen sind die Helden
- **Icons:** Aus `docs/presentation/icons/selected/*.svg` — weiss oder Akzentfarbe, 32–48pt
- **Screenshots:** Mit abgerundeten Ecken (8px), leichtem Schatten — aus `docs/presentation/screenshots/`
- **Jede Slide: maximal eine Kernbotschaft**
- Schrift: Inter oder Segoe UI Bold für Headlines

### Logo
BATbern Logo: `apps/projectdoc/src/assets/BATbern_color_logo.svg`

---

## BLOCK 1 — „Ich war genau wie ihr" *(4 Minuten, 4 Slides)*

### Kernbotschaft
Der Speaker war selbst Skeptiker — genau wie das Publikum. Das schafft sofortige Verbindung.

---

### Slide 1.1 — Einstieg: Die Frage
**Layout:** Vollbild, dunkler Hintergrund `#0F1117`, zwei Fragen zentriert, sehr grosser Text, viel Leerraum

**Inhalt:**
- Headline 72pt: *„Wer hat schon mit einem AI-Coding-Tool gearbeitet?"*
- Pause (Handheben)
- Subline 60pt, Farbe `#F59E0B`: *„Und wer war… enttäuscht?"*

**Icon:** Keines — die Stille wirkt

**Speaker Notes:**
> Handheben lassen, kurze Pause, dann: „Ich auch. Vor zwei Jahren."
> Diese zwei Sekunden Stille sind der stärkste Moment der ganzen Präsentation.

---

### Slide 1.2 — GitHub Copilot 2023: Die Ernüchterung
**Layout:** Zwei Spalten, Trennlinie in der Mitte, links Grün, rechts Rot

**Inhalt:**
- Headline: *„2023 — Team-Experiment mit GitHub Copilot"*
- **Links (✓ Grün `#10B981`):** Tests schreiben | Code erklären | Dokumentation generieren
- **Rechts (✗ Rot `#EF4444`):** Mehr als eine Datei | Architektur-Entscheide | Zusammenhänge verstehen | Refactoring über Boundaries
- Fazit-Box unten, `#94A3B8`: *„Hilfreich. Aber kein Game Changer."*

**Icon:** `code-2.svg` (Akzent)

**Speaker Notes:**
> „Ich habe meinen Teams den Auftrag gegeben: Evaluiert Copilot. Resultat: ja, es hilft ein bisschen.
> Immer confined to one file. Immer reaktiv. Nie initiativ. Wir haben es wieder weggelegt."

---

### Slide 1.3 — Der Wendepunkt: 19. August 2025
**Layout:** Dramatischer Moment — schwarzer Hintergrund, ein Datum gross, dann der Link, dann das Resultat

**Inhalt:**
- Datum oben, `#94A3B8`: *„19. August 2025, Freitagabend"*
- Gross: *„Ein Link. Von Andy."* (Andy = CTO, Die Mobiliar)
- Sub: *„YouTube. BMAD. Agentic Development."*
- Separator
- *„Wochenende. Eine Applikation gebaut: RTFM — Regular Travel Expense Form Manager"*
- Quote, Violett `#8B5CF6`: *„Ich konnte nicht aufhören."*

**Icon:** `zap.svg` (Violett — der Funke)

**Speaker Notes:**
> „Andy schickt mir einen Link Freitagabend. Am Sonntagabend läuft eine Applikation mit
> Authentifizierung, Business Logic, Tests, Deployment. Nicht ein CRUD-Formular. Eine Applikation.
> In zwei Tagen. Alleine."

---

### Slide 1.4 — Die Entscheidung
**Layout:** Grosse Frage → Antwort, minimalistisch

**Inhalt:**
- Headline: *„Was wenn ich das mit einem echten Enterprise-Projekt teste?"*
- Body: *„BATbern brauchte seit Jahren eine neue Plattform."*
- Arrow `→` gross: *„September 2025"*
- Subline, `#8B5CF6`: *„Alleine. Mit AI. Mit Methode."*

**Icon:** `rocket.svg` (Violett)

---

## BLOCK 2 — „Was ist BMAD?" *(3 Minuten, 3 Slides)*

### Kernbotschaft
BMAD verwandelt AI-Chaos in Methode. 90 Sekunden Erklärung — dann weiter.

---

### Slide 2.1 — BMAD: Die Methode
**Layout:** Name + Definition + GitHub-Link/QR

**Inhalt:**
- Headline 64pt: *„BMAD"*
- Subline: *„Boomerang Agile Method for AI-Driven Development"*
- Body: *„Spezialisierte AI-Agents mit Rollen, Personas, Frameworks — für Software-Entwicklung von Discovery bis Production"*
- Tag: *„Open Source · Kostenlos · github.com/bmad-method"*
- QR-Code: GitHub BMAD

**Icon:** `workflow.svg` (Violett)

**Speaker Notes:**
> „BMAD gibt dem AI-Pair-Programming die Struktur die es braucht.
> Ohne Methode: Prompt-Chaos. Mit BMAD: reproduzierbare Qualität."

---

### Slide 2.2 — Die Agents (Rollen)
**Layout:** Horizontaler Flow oder Grid, Icons für jeden Agent, Farbe Violett

**Inhalt:**
- Headline: *„6 spezialisierte Agents — jeder mit eigenem Scope"*
- 📊 **Analyst** → PRD, Discovery, Brainstorming
- 🏗️ **Architect** → Architektur, ADRs, Tech-Entscheide
- 🎨 **UX Designer** → Wireframes, User Journeys
- 📋 **PM + SM** → Epics, Stories, Sprint-Planung
- 💻 **Dev** → Implementation, TDD, Code Review
- 🧪 **TEA** → Test-Architektur, QA-Gates, Coverage

**Separator:** *„+ Party Mode: alle Agents gleichzeitig"*

**Icons:** `brain.svg` `layers.svg` `users.svg` `file-text.svg` `terminal.svg` `shield-check.svg`

**Speaker Notes:**
> „Jeder Agent hat Persönlichkeit, eigene Methoden, eigene Checkpoints.
> Kein Chaos — Methode. Der Analyst fragt andere Fragen als der Dev."

---

### Slide 2.3 — Warum BMAD? (Konkret)
**Layout:** 3 grosse Punkte mit Icon, horizontal

**Inhalt:**
- Headline: *„Was BMAD wirklich bringt"*
- `workflow.svg` **Struktur**: Kein Prompt-Chaos — jeder Agent kennt seinen Job
- `refresh-cw.svg` **Skalierung**: Vom Weekend-Projekt bis Enterprise-Platform
- `book-open.svg` **Kontext**: MEMORY.md + Plans → AI verliert den Faden nicht

---

## BLOCK 3 — „Die Plattform" *(2 Minuten, 2 Slides)*

### Kernbotschaft
Scope setzen — das Publikum muss verstehen was „Enterprise" hier konkret bedeutet.

---

### Slide 3.1 — BATbern Platform: Was wurde gebaut?
**Layout:** Stats-Grid, 3×3, grosse Zahlen prominent

**Screenshot-Hintergrund (abgedunkelt):** `screenshots/a-01-event-dashboard.png`

**Inhalt:**
- Headline: *„BATbern Event Management Platform"*

| Zahl | Beschreibung |
|------|-------------|
| **5** | Microservices (Java 21, Spring Boot 3.5) |
| **9** | Event-Workflow States (CREATED → ARCHIVED) |
| **3** | AWS Environments (Dev / Staging / Prod) |
| **60** | historische Events (20+ Jahre) |
| **269** | einzigartige Speakers migriert |
| **2'307** | historische Teilnehmer |
| **85%+** | Test Coverage |
| **5 Epics** | MVP abgeschlossen |
| **Epic 6** | Speaker Portal auf Staging |

**Icons:** `server.svg` `cloud.svg` `database.svg` `users.svg`

---

### Slide 3.2 — Der Meta-Moment *(Wirkungsstärkste Slide des Blocks)*
**Layout:** Vollbild, dunkler Hintergrund, Text zentriert, jede Zeile nacheinander erscheinen lassen (Animation)

**Inhalt:**
- Grosse Headline, Weiss: *„Diese Plattform verwaltet das heutige Event."*
- Zeile 1, `#94A3B8`: *„Die Einladung an mich als Speaker: über dieses System verschickt."*
- Zeile 2, `#94A3B8`: *„Das Event das ihr heute besucht: in diesem System organisiert."*
- Zeile 3, `#94A3B8`: *„Was ich euch jetzt zeige: läuft live auf AWS."*
- Letzter Satz, `#10B981`: *„BAT #60. Der 60. Event. In der Plattform die ich gebaut habe."*

**Speaker Notes:**
> Jede Zeile einzeln vorlesen, Pause dazwischen.
> „Zeigen wir es." → Browser öffnen.

---

## BLOCK 4 — Live Demo *(10 Minuten)*

### Kernbotschaft
Sehen ist glauben. Live beweist die These: echte Enterprise-Software, produktiv, jetzt.

---

### Slide 4.0 — Demo Ankündigung
**Layout:** Simpel, Fokus auf Browser

**Inhalt:**
- Headline: *„Live Demo"*
- Subline: *„BATbern Event Management Platform"*
- URL sichtbar: `staging.batbern.ch`

---

### Demo-Flow *(kein Slide — Browser live)*

```
MIN  AKTION                                          SCREENSHOT-REFERENZ
───────────────────────────────────────────────────────────────────────────
0:00  Login als Organizer                            —
0:30  Dashboard — laufendes BAT #60 Event zeigen     a-01-event-dashboard.png
1:00  Neues Test-Event erstellen (Typ, Datum)         a-02/a-04-event-creation
2:00  Topic Selection + Topic Heatmap zeigen          a-16/a-17-topic-heatmap.png
3:00  Speaker Brainstorming — Speaker hinzufügen      a-20/a-21-all-speakers-added.png
4:00  Outreach — Kanban Board Speaker Status          b-12-kanban-contacted-state.png
5:00  Content Submission + Quality Review             c-04-quality-review-1-opened.png
6:00  Slot Assignment (Drag & Drop)                  d-03-slot-assignment-page-loaded.png
7:00  Progressive Publishing — Agenda publizieren     d-09-agenda-published.png
7:30  Archiv — 20+ Jahre, Volltextsuche              —
8:30  Optional: BMAD Story + Claude kurz zeigen       —
9:30  Zurück zu Slides                               —
```

**Risiko-Management:**
- Lokale Umgebung (`make dev-native-up`) als Fallback — kein Internet-Dependency
- Tutorial-Video: `docs/user-guide/assets/videos/workflow/event-workflow-schulung-de.mp4` als Browser-Tab bereit (erstellt in 2h mit Claude)
- Video läuft während der Pause für Interessierte (Techniker fragen)

---

### Slide 4.1 — Architektur: Was dahinter steckt
**Layout:** Architektur-Diagramm, einfach und lesbar

**Inhalt:**
- Headline: *„5 unabhängig deploybare Microservices — Domain-Driven Design"*

```
[Browser / Apple Watch App]
         ↓
   [API Gateway :8000]          ← Auth, Rate Limiting, Routing
    /    |    |    \
   ↓     ↓    ↓     ↓
[Event] [Speaker] [Company/User] [Attendee]
   Mgmt   Coord      Mgmt         Experience
         ↓
  [AWS: ECS Fargate | RDS PostgreSQL | S3 + CloudFront | Cognito]
```

- Subline: *„Jeder Service: eigene Datenbank, eigenes Schema, eigenes Deployment"*

**Icons:** `layers.svg` `server.svg` `cloud.svg` `database.svg`

---

## BLOCK 5 — „Die Zahlen" *(4 Minuten, 5 Slides)*

### Kernbotschaft
Verifizierbarer Beweis. Keine Behauptungen — echte Zahlen aus dem Projekt.

---

### Slide 5.1 — Die Hauptzahl *(Wirkungsstärkste Slide der ganzen Präsentation)*
**Layout:** Vollbild, zwei Zahlen links/rechts, visuell stark kontrastiert

**Inhalt:**
- Links, Blau `#3B82F6`, 120pt: **60 PT**
- Rechts, Grau `#94A3B8`, 120pt: **~300 PT**
- Links unten: *„Ich. Alleine."*
- Rechts unten: *„Geschätzte Team-Variante"*
- Trennlinie mit: *„vs."*
- Unten zentriert, `#F59E0B`: *„Ist das 80% Effizienzgewinn? Nein. Es ist eine andere Art zu arbeiten."*

**Icon:** `trending-up.svg`

**Speaker Notes:**
> „Ich sage bewusst: keine 80%-Einsparung. Es ist eine andere Arbeitsweise.
> Andere Stärken, andere Schwächen. Aber diese Zahl ist real — ich habe jeden Tag gemessen."

---

### Slide 5.2 — Wie ich gearbeitet habe *(NEU in v2)*
**Layout:** Zahlen-Grid + Balkendiagramm Wochenverteilung

**Inhalt:**
- Headline: *„589 Stunden. 476 Sessions. 14 Wochen."*
- **75%** Claude-Session-Zeit (441.8h) | **25%** Git-Commit-Zeit (147.3h)
- Ø Session-Länge: **1.2 Stunden**
- Peak-Woche W50 (8.–14. Dez): **89.8 Stunden** ← Weihnachtsferien!
- Peak-Woche W48 (24.–30. Nov): **89.1 Stunden**
- Sub, `#94A3B8`: *„Die intensivsten Wochen: Weihnachtsferien. Freiwillig."*
- Mini-Balkendiagramm der 14 Wochen optional

**Icons:** `clock.svg` `bar-chart-3.svg`

**Speaker Notes:**
> „Das ist kein 9-to-5. Das ist ein Projekt das einen nicht loslässt.
> W52 = Weihnachtsferien: 81 Stunden. Freiwillig. Das ist der Unterschied."

---

### Slide 5.3 — Die Timeline
**Layout:** Horizontale Timeline mit Meilensteinen und Commit-Balken

**Inhalt:**
- Headline: *„Von Idee zu Production in 6 Monaten"*

```
Aug 2025   🔗 YouTube Link → RTFM Wochenende
Sep 2025   🚀 BATbern Start — Epic 1: Foundation
           3 Commits/Tag durchschnittlich
Okt 2025   ⚡ 327 Commits — intensivster Monat
           Epic 1+2: Gateway, Auth, CRUD, Frontend
Nov 2025   📦 188 Commits — Epic 3+4
           Data Migration (60 Events, 2'307 Teilnehmer)
           Public Website live
Dez 2025   🏗️ 148 Commits — Epic 5
           9-State Workflow, Speaker Coordination
Jan 2026   ✅ 159 Commits — MVP COMPLETE
           Auto-Publishing, Lifecycle Automation
Feb 2026   🎯 194 Commits — Epic 6 Staging
           Speaker Self-Service Portal
Mrz 2026   📢 DIESER VORTRAG — BAT #60
```

**Icon:** `calendar.svg` `git-branch.svg`

---

### Slide 5.4 — Code-Qualität: Die ehrliche Zahl
**Layout:** Zwei grosse Zahlen nebeneinander + Stats unten

**Inhalt:**
- Headline: *„AI-Code ist nicht schlechter Code — wenn man es richtig macht"*
- Links, Blau: **63'000** Zeilen — *„Produktionscode (Java)"*
- Rechts, Grün: **65'000** Zeilen — *„Testcode (Java)"*
- Subline `#10B981`: *„49% Produktion / 51% Tests — fast exakt 50/50"*
- Separator
- **224** Java Test-Dateien | **131** TypeScript Test-Dateien
- **~1'016** Commits in der Projektphase
- **0** kritische Datenmigrations-Fehler
- Quote: *„Kein Mock in production code. Testcontainers mit echter PostgreSQL."*

**Icons:** `test-tube-2.svg` `shield-check.svg` `check-circle-2.svg`

---

### Slide 5.5 — Was wurde gebaut (Checklist)
**Layout:** Achievement-Slide, Checkmarks, grüne Farbe

**Inhalt:**
- Headline: *„Das alles: 60 Personentage. Eine Person."*
- ✅ 5 Microservices — Java 21, Spring Boot 3.5, DDD
- ✅ React 19 Frontend — TypeScript, role-adaptive UI
- ✅ 9-State Event Workflow + per-Speaker Coordination
- ✅ AWS CDK Infrastructure — 3 Environments, Fargate
- ✅ CI/CD Pipeline — GitHub Actions, Dependabot
- ✅ 60 Events, 269 Speakers, 2'307 Teilnehmer migriert
- ✅ Projektdokumentation-Portal (project.batbern.ch)
- ✅ Tutorial-Video (2h erstellt mit Claude)
- ✅ Apple Watch App (SwiftUI, App Store)

**Screenshot (klein, rechts):** `screenshots/a-01-event-dashboard.png`
**Icons:** `check-circle-2.svg` `rocket.svg`

---

## BLOCK 6 — „Was ich falsch gemacht habe" *(7 Minuten, 6 Slides)*

### Kernbotschaft
Ehrlichkeit kauft Glaubwürdigkeit bei Skeptikern. Lessons learned = grösster Mehrwert.

---

### Slide 6.0 — Intro Lessons Learned
**Layout:** Einfach, direkt, Amber-Farbe signalisiert Ehrlichkeit

**Inhalt:**
- Headline, `#F59E0B`: *„Was ich falsch gemacht habe"*
- Subline: *„5 Lektionen — ehrlich und spezifisch"*

**Icon:** `graduation-cap.svg` (Amber)

**Speaker Notes:**
> „Das hier ist der Teil den ich am meisten vorbereitet habe.
> Weil hier liegt der echte Wert für euch — nicht in meinen Erfolgen."

---

### Slide 6.1 — Lesson 1: Context Management
**Layout:** Problem → Realität → Lösung, drei horizontale Zonen

**Inhalt:**
- Headline: *„Context Management ist alles"*
- 🔴 **Problem:** *„AI 'vergisst' — bei jedem neuen Session-Start: neuer Kontext"*
- 🟡 **Realität:** *„Mit 63'000 Zeilen Produktionscode: Context Management wird zur Hauptaufgabe"*
- 🟢 **Lösung:** *„MEMORY.md · Project Context Files · strukturierte Session-Übergaben · Resume-Flag"*
- Quote, `#8B5CF6`: *„Der Kontext ist euer wertvollstes Asset. Schützt ihn."*

**Icon:** `brain.svg` (Violett)

**Speaker Notes:**
> „BMAD löst das mit MEMORY.md und Project-Context. Trotzdem:
> Ab einer gewissen Codegrösse müsst ihr aktiv Context-Management betreiben.
> Das ist die unsichtbare Hauptaufgabe."

---

### Slide 6.2 — Lesson 2: TDD wird wichtiger, nicht unwichtiger
**Layout:** Erwartung links / Realität rechts — starker Kontrast

**Inhalt:**
- Headline: *„Ich dachte: AI braucht weniger Tests. Ich lag falsch."*
- **Links `#94A3B8` (Erwartung):**
  - *„AI schreibt Code schnell"*
  - *„→ Tests werden weniger wichtig"*
  - *„→ Ich spare Zeit"*
- **Rechts `#EF4444` (Realität):**
  - *„AI schreibt Code der kompiliert"*
  - *„aber nicht immer das Richtige tut"*
  - *„ohne Tests: stille Fehler in Sekunden"*
- Quote, `#10B981`: *„Red-Green-Refactor ist mit AI MEHR wichtig als ohne."*
- Fact: *„Resultat: 65'000 Zeilen Testcode. 51% des gesamten Java-Codes."*

**Icons:** `test-tube-2.svg` `alert-circle.svg`

---

### Slide 6.3 — Lesson 3: OpenAPI Contract-First
**Layout:** Architektur-Prinzip, visuell mit Vertrag in der Mitte

**Inhalt:**
- Headline: *„API-Vertrag zuerst — Backend und Frontend dagegen entwickeln"*
- Diagramm: [Backend] → OpenAPI Spec ← [Frontend] (Vertrag in der Mitte)
- Problem: *„AI produziert Code in Sekunden — ohne Vertrag: Integration-Chaos in Sekunden"*
- Lösung: *„OpenAPI als Single Source of Truth"*
- Enterprise-Box, `#3B82F6`: *„Für Teams mit separaten Frontend/Backend-Teams: doppelt kritisch"*
- Quote: *„Der Vertrag muss existieren bevor die erste Zeile Code geschrieben wird."*

**Icon:** `file-code.svg` (Blau)

**Speaker Notes:**
> „In eurem Enterprise habt ihr Frontend- und Backend-Teams.
> Das Problem existiert schon ohne AI. Mit AI wird es 10x schneller sichtbar."

---

### Slide 6.4 — Lesson 4: Stories als Detailspecs
**Layout:** Zwei Stories gegenübergestellt — schlechte vs. gute

**Inhalt:**
- Headline: *„Garbage in, Garbage out — aber auf Enterprise-Niveau und in Sekunden"*

- **Schlechte Story `#EF4444`:**
  ```
  Als Organizer möchte ich einen Speaker hinzufügen.
  ```
  *Resultat: AI implementiert etwas. Aber nicht das Richtige.*

- **Gute Story `#10B981`:**
  ```
  Screen: Speaker Brainstorming Modal (Wireframe-Referenz)
  API: POST /api/v1/events/{id}/speakers (OpenAPI Spec)
  Datenmodell: SpeakerEntity mit Status-Enum
  Acceptance Criteria: 5 spezifische Szenarien
  Edge Cases: Duplikat, Max-Speaker-Limit, Status-Validierung
  ```
  *Resultat: Exakt das was gemeint war. Beim ersten Mal.*

- Quote: *„Je mehr präziser Kontext, desto besser das Ergebnis."*

**Icon:** `file-text.svg` (Amber)

---

### Slide 6.5 — Lesson 5: Nicht zu viel zu früh planen
**Layout:** Contra-intuitiv aufgebaut, Erwartung wird umgekehrt

**Inhalt:**
- Headline: *„Meine Intuition: AI braucht mehr Planung. Falsch."*
- Intuition `#94A3B8`: *„Alles vorab spezifizieren → AI macht es perfekt"*
- Realität `#EF4444`: *„Zu viel Upfront-Planung = false confidence + Waste"*
- *„Der 16-Schritt Workflow war gut gemeint — aber zu starr"*
- Separator
- Besser `#10B981`:
  - *„Kleines MVP pro Domain zuerst (Healthcheck)"*
  - *„Iterieren — dann skalieren"*
  - *„Stories erst detaillieren wenn die Domain verstanden ist"*
- Quote: *„Start small. Prove it works. Then build the rest."*

**Icon:** `lightbulb.svg` (Amber)

---

## BLOCK 7 — „Was könnt ihr morgen tun?" *(3 Minuten, 3 Slides)*

### Kernbotschaft
Das Publikum verlässt den Raum mit einem konkreten nächsten Schritt.

---

### Slide 7.1 — Start Small (nicht Enterprise)
**Layout:** 3 Schritte klar nummeriert

**Inhalt:**
- Headline: *„Startet nicht mit eurem nächsten Enterprise-Projekt"*
- **Schritt 1** `laptop.svg`: *„Weekend-Projekt wählen — etwas das euch persönlich interessiert"*
- **Schritt 2** `bot.svg`: *„Claude Code installieren (Anthropic) — das Tool das ich verwende"*
- **Schritt 3** `workflow.svg`: *„BMAD installieren — 5 Minuten, Open Source, kostenlos"*
- QR-Code: BMAD GitHub (`github.com/bmad-method`)
- Kosten-Box: *„Claude Max: ~100 CHF/Monat. Weniger als ein Developer-Tag."*

---

### Slide 7.2 — Top 3 BMAD Tips
**Layout:** 3 grosse Icons, horizontal, jeder mit Titel + einem Satz

**Inhalt:**
- Headline: *„3 Dinge die alles verändern"*

- `refresh-cw.svg` **Resume**
  *„Context über Sessions erhalten — nie mehr von vorne anfangen"*

- `file-text.svg` **Plans**
  *„AI erst planen lassen, dann implementieren — nie direkt loscodieren"*

- `users.svg` **Party Mode**
  *„Mehrere Agents gleichzeitig — Analyst + Architect + Dev diskutieren euer Problem"*

---

### Slide 7.3 — Der Mindset-Shift
**Layout:** Vollbild, starke Statements aufgebaut, Pause nach jedem

**Inhalt:**
- Gross, Weiss: *„Es ist kein Coding-Tool."*
- Pause
- `#94A3B8`: *„Es ist ein Entwicklungspartner der nie schläft."*
- `#94A3B8`: *„Der nie vergisst."*
- `#94A3B8`: *„Der nie ungeduldig wird."*
- Separator
- Weiss: *„Ihr seid immer noch der Architekt."*
- Weiss: *„Ihr seid immer noch verantwortlich."*
- `#8B5CF6` gross: *„Aber ihr könnt alleine bauen, was früher ein Team brauchte."*

**Icon:** `award.svg` (Violett)

---

## BLOCK 8 — Special Goodie: Apple Watch App *(2 Minuten, 2 Slides)*

### Kernbotschaft
WOW-Abschluss. Zeigt: die Grenzen sind grösser als das Publikum dachte.

---

### Slide 8.1 — „Und dann konnte ich nicht aufhören…"
**Layout:** Dramatische Enthüllung + 3 Watch-Screenshots nebeneinander

**Inhalt:**
- Headline: *„Und dann konnte ich nicht aufhören."*
- *„Weil ich mal schauen wollte ob es geht…"*
- *„Apple Watch App. SwiftUI. watchOS."*
- **Fact-Box `#F59E0B`:** *„Ich hatte nie zuvor SwiftUI programmiert."*
- Separator
- *„Dual-Zone: Public Event Companion + Organizer Command Center"*
- *„Multi-Organizer Sync: ein Tap → alle 4 Uhren aktualisieren gleichzeitig"*
- *„Im App Store. Gratis. Für alle BATbern-Teilnehmer."*
- QR-Code: App Store

**Screenshots (3 nebeneinander):**
- `screenshots/watch-app-1.png`
- `screenshots/watch-app-2.png`
- `screenshots/watch-app-3.png`

**Icon:** `watch.svg` (Grün)

**Speaker Notes:**
> „Ich habe einfach angefangen. BMAD hat mir einen Analyst, einen Architect,
> einen UX Designer und einen Dev für watchOS gegeben.
> In zwei Wochen war die App fertig."
> [Watch am Handgelenk zeigen, App live starten]

---

### Slide 8.2 — Abschluss / Danke
**Layout:** Clean closing slide, BATbern Logo prominent

**Inhalt:**
- BATbern Logo oben
- Headline: *„Danke"*
- `#94A3B8`: *„BAT #60 — Berner Architekten Treffen · 6. März 2026"*
- Separator
- *„Alle Ressourcen:"*
- QR 1: BMAD GitHub (`github.com/bmad-method`)
- QR 2: BATbern Platform (`batbern.ch`)
- QR 3: Watch App (App Store)
- QR 4: Projektdoku (`project.batbern.ch`)
- LinkedIn / Kontakt: Dr. Nissim J. Buchs
- Gross, `#8B5CF6`: *„Fragen?"*

---

## Anhang: Q&A Vorbereitung *(10 Minuten)*

| Erwartete Frage | Kernpunkt der Antwort |
|-----------------|----------------------|
| „Wie steht es um Security?" | TDD + OWASP-aware AI + manueller Security-Review bleibt. Kein Security-Bypass. |
| „Was wenn Claude falsch liegt?" | Tests fangen das auf — 65'000 Zeilen Testcode sind dafür da. |
| „Kann man das in einem Team skalieren?" | BMAD genau dafür — Agents auf Team-Rollen verteilen. Party Mode für Team-Architektur. |
| „Welche Kosten entstehen?" | Claude Max: ~100 CHF/Monat. Claude Code CLI. AWS Staging: ~50 CHF/Monat. |
| „IP und Datenschutz?" | Code bleibt beim User. Anthropic-AGB prüfen. On-premise Modelle möglich. |
| „Braucht man Programmierkenntnisse?" | Ja — AI multipliziert Kapazität, ersetzt kein Fundament. |
| „Was würdest du anders machen?" | OpenAPI Tag 1, kleinere MVPs pro Domain, früher TDD einfordern. |
| „Welches Modell?" | Claude Sonnet / Opus (Anthropic). Claude Code als CLI-Tool. |
| „Ohne BMAD möglich?" | Ja — aber BMAD verhindert dass AI im Chaos endet. Besonders bei Enterprise-Grösse. |
| „Wann ist AI nicht geeignet?" | Legacy ohne Doku — aber: AI kann dabei helfen, die Doku erst zu erstellen. |

---

## Slide-Zählung Übersicht

| Block | Slides | Minuten | NEU in v2 |
|-------|--------|---------|-----------|
| 1 — Ich war wie ihr | 4 | 4 | Konkrete Daten im Speaker Notes |
| 2 — Was ist BMAD? | 3 | 3 | 6 Agents (statt 5), Party Mode |
| 3 — Die Plattform | 2 | 2 | Echte Zahlen: 60 Events, 269 Speakers, 2'307 Teilnehmer |
| 4 — Live Demo | 2 + live | 10 | Screenshot-Referenzen für jeden Demo-Schritt |
| 5 — Die Zahlen | **5** | 4 | **+1 Slide: Wie ich gearbeitet habe** (589h, 75% Claude, Peak-Wochen) |
| 6 — Lessons Learned | 6 | 7 | Exakte Zahlen, konkrete Story-Beispiele |
| 7 — Was tun? | 3 | 3 | Kostenzahl (100 CHF/Monat) |
| 8 — Watch App + Danke | 2 | 2 | 3 Screenshots, SwiftUI-Fakt, 4 QR-Codes |
| **Total** | **27 Slides** | **35 Min** | |

### Asset-Referenzen
- **Screenshots:** `docs/presentation/screenshots/` (11 Dateien)
- **Icons:** `docs/presentation/icons/selected/` (41 SVG-Dateien, Lucide v0.575.0, ISC-Lizenz)
- **Logo:** `apps/projectdoc/src/assets/BATbern_color_logo.svg`
- **Alle Slides zusammen mit** `presentation-data.md` lesen für vollständige Datenbasis
