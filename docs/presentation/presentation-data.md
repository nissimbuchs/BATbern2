# BATbern Vortrag — Präsentations-Daten & Assets
## Kontext für PowerPoint-Erstellung

Dieses Dokument enthält alle konkreten Zahlen, Screenshots, Icons und Hintergrundinformationen
für die Erstellung der Slides. Gemeinsam mit `presentation-outline.md` lesen.

---

## 1. KONKRETE ZAHLEN (verifiziert aus dem Projekt)

### Arbeitsaufwand
| Kennzahl | Wert | Quelle |
|----------|------|--------|
| **Gesamtstunden (Claude-Sessions)** | **589 Stunden** | `apps/workhours/working-hours-report-20260103-180759.md` |
| **Gesamtstunden (Git-basiert)** | **241 Stunden** | `apps/workhours/working-hours-report-20260103-181039.md` |
| **Personentage (eigene Einschätzung)** | **~60 PT** | Nissims Selbsteinschätzung |
| **Personentage Schätzung Team-Variante** | **~300 PT** | Nissims Schätzung |
| **Effizienz-Faktor** | **~5x** | 300 / 60 PT |
| **Sessions total** | **476** | Claude Session Logs |
| **Ø Session-Länge** | **1.2 Stunden** | Analyse |
| **Anteil Claude-Zeit** | **75%** | 441.8h von 589h |
| **Anteil Git-Commit-Zeit** | **25%** | 147.3h von 589h |
| **Analysezeitraum** | **Okt 2025 – Jan 2026** | 14 Wochen |

**Peak-Wochen:**
- W48 (Nov 24-30): 89.1 Stunden
- W50 (Dez 8-14): 89.8 Stunden  ← Intensivste Woche
- W52 (Dez 22-28): 81.0 Stunden (Weihnachtsferien!)

### Code-Statistiken
| Kennzahl | Wert | Quelle |
|----------|------|--------|
| **Java Produktionscode** | **~63'000 Zeilen** | `find . -name "*.java"` |
| **Java Testcode** | **~65'000 Zeilen** | `find . -name "*Test*.java"` |
| **Code/Test Verhältnis** | **49% / 51%** | Fast exakt 50/50 |
| **Java Test-Dateien** | **224 Dateien** | Unit + Integration Tests |
| **TypeScript Test-Dateien** | **131 Dateien** | 50 spec + 81 test |
| **Gesamt Commits** | **~1'059** | `git log --oneline` |
| **Commits Projektphase** | **~1'016** | Sep 2025 – Feb 2026 |
| **Commits pro Monat (peak)** | **327** | Oktober 2025 |

### Plattform-Scope
| Kennzahl | Wert |
|----------|------|
| **Microservices** | **5** (API Gateway + 4 Domain Services) |
| **Epics abgeschlossen** | **5** (MVP complete) + Epic 6 auf Staging |
| **Event Workflow States** | **9** (CREATED → ARCHIVED) |
| **Speaker Workflow States** | **8** (identified → confirmed/declined) |
| **Environments** | **3** (Dev / Staging / Production) |
| **Test Coverage** | **85%+** |
| **API Endpoints** | **OpenAPI-definiert**, alle 5 Services |

### Historische Daten (migriert)
| Kennzahl | Wert | Quelle |
|----------|------|--------|
| **Events total** | **60** | `volume-metrics.json` |
| **Abend-Events (AbendBAT)** | **44** | volume-metrics.json |
| **Ganztags-Events (GanztagBAT)** | **14** | volume-metrics.json |
| **Sessions total** | **302** | volume-metrics.json |
| **Unique Speakers** | **269** | volume-metrics.json |
| **Speakers mit Bio** | **267 (99%)** | volume-metrics.json |
| **Speakers mit Foto** | **223 (83%)** | volume-metrics.json |
| **Unique Unternehmen** | **143** | volume-metrics.json |
| **Historische Teilnehmer** | **2'307** | README.md |
| **Datenqualität** | **0 kritische Fehler** | `data-quality-report.json` |

---

## 2. TIMELINE (für Timeline-Slide)

```
Aug 2025   YouTube Link von Andy (Mobiliar CTO) → Erkenntnis
Sep 2025   RTFM Weekend-Projekt → Entscheid: BATbern neu bauen
Okt 2025   Epic 1: Foundation & Infrastructure
           Epic 2: Entity CRUD & Domain Services
Nov 2025   Epic 3: Historical Data Migration (54+ Events)
Dez 2025   Epic 4: Public Website & Content Discovery
Jan 2026   Epic 5: Enhanced Organizer Workflows → MVP COMPLETE ✅
Feb 2026   Epic 6: Speaker Self-Service Portal → Staging ✅
Mrz 2026   BATbern Event #60 — DIESER VORTRAG
```

---

## 3. TECHNOLOGIE-STACK (für Tech-Stack Slide)

### Backend
- Java 21 LTS + Spring Boot 3.5+
- PostgreSQL 15 (Testcontainers für Tests)
- Caffeine Cache (in-memory)
- OpenAPI 3.1 (Contract-First)
- Domain-Driven Design (DDD)
- TDD: Red-Green-Refactor

### Frontend
- React 19 + TypeScript 5.3+
- Material-UI (MUI)
- Zustand + React Query
- Vite (Dev Server)
- OpenAPI-generierte Types

### Infrastructure
- AWS ECS Fargate (SPOT 70% + Standard 30%)
- AWS RDS PostgreSQL
- AWS S3 + CloudFront CDN
- AWS Cognito (Authentication)
- AWS SES (E-Mail)
- AWS CDK v2 (Infrastructure as Code)
- GitHub Actions (CI/CD)
- 3 AWS Accounts (Dev/Staging/Prod)

### Development Tools
- Claude Code (Anthropic) — primäres AI-Tool
- BMAD Method v6 (Open Source)
- Bruno (API Contract Tests)
- Playwright (E2E Tests)
- JaCoCo (Java Coverage)
- SonarQube (Code Quality)
- Dependabot (automatische Updates)

---

## 4. BMAD METHOD (für BMAD-Erklärungsslide)

**Full Name:** BMAD (Boomerang Agile Method for AI Development)
**GitHub:** https://github.com/bmad-method (Open Source, kostenlos)
**Version:** v6 (aktuell)
**Lizenz:** Open Source

### Die 5 Phasen mit Agents:
1. 📊 **Analyst (Mary)** → Discovery, Brainstorming, PRD
2. 🏗️ **Architect** → System-Architektur, Tech-Entscheide, ADRs
3. 🎨 **UX Designer** → Wireframes, User Journeys, Design-System
4. 📋 **PM + SM** → Epics, Stories, Sprint Planning
5. 💻 **Dev** → Implementation, TDD, Code Reviews
6. 🧪 **TEA (Test Engineer)** → Test-Architektur, Coverage, QA-Gates

### Spezielle Features:
- **Party Mode**: Mehrere Agents diskutieren gleichzeitig
- **Resume**: Context über Sessions erhalten (MEMORY.md)
- **Plans**: Strukturierter Plan vor jeder Implementation
- **Skills**: Wiederverwendbare Workflows als Slash-Commands
- **Workflow Status**: Jederzeit Status des Projekts abfragen

---

## 5. WATCH APP (für Special Goodie Slide)

**Name:** BATbern Watch — Apple Watch Companion App
**Platform:** watchOS, native SwiftUI
**Vorwissen:** Kein SwiftUI/watchOS Vorkenntnisse bei Start

### Features:
**Public Zone (ohne Login):**
- Aktuelles BATbern Event browsen
- Theme, Schedule, Session-Details
- Speaker-Bios und Fotos via Digital Crown
- Keine Anmeldung nötig — für alle 200 Teilnehmer

**Organizer Zone (authentifiziert, rechts wischen):**
- Live Countdown Control (haptic-driven)
- Session-Management (Start/Ende)
- Multi-Organizer synchronized (4 Uhren gleichzeitig)
- Moderator auf Bühne — kein Handy nötig

### Technische Besonderheit:
- **Dual-Zone Architecture**: Horizontal paging, zwei Welten
- **Multi-Organizer Sync**: Ein Tap → alle Uhren aktualisieren
- **Offline-First**: Läuft ohne Netzwerk (cached data)

---

## 6. SCREENSHOTS (für Slides)

Alle Screenshots liegen in `docs/presentation/screenshots/`:

| Datei | Zeigt | Empfohlen für Slide |
|-------|-------|---------------------|
| `a-01-event-dashboard.png` | Organizer Dashboard — Event-Übersicht | Block 3 / Demo-Intro |
| `a-17-topic-heatmap.png` | Topic Heatmap mit Duplikat-Detection | Block 4 Demo |
| `a-21-all-speakers-added.png` | Speaker Brainstorming Pool | Block 4 Demo |
| `b-12-kanban-contacted-state.png` | Kanban Speaker Outreach (contacted) | Block 4 Demo |
| `c-04-quality-review-1-opened.png` | Quality Review Drawer | Block 4 Demo |
| `d-03-slot-assignment-page-loaded.png` | Slot Assignment (Drag & Drop) | Block 4 Demo |
| `d-09-agenda-published.png` | Agenda published — CDN live | Block 4 Demo |
| `e-07-event-archived-successfully.png` | Event archiviert — kompletter Lifecycle | Block 4 Demo |
| `watch-app-1.png` | Apple Watch Simulator — Screen 1 | Block 8 Special Goodie |
| `watch-app-2.png` | Apple Watch Simulator — Screen 2 | Block 8 Special Goodie |
| `watch-app-3.png` | Apple Watch Simulator — Screen 3 | Block 8 Special Goodie |

**Gesamt verfügbare Screenshots:** 101 Stück in `docs/user-guide/assets/screenshots/workflow/`
(Alle Workflow-Phasen A–E dokumentiert)

---

## 7. ICON SET (Lucide Icons)

**Name:** Lucide Icons
**Version:** 0.575.0 (Feb 2026)
**Lizenz:** ISC License (equivalent to MIT — frei für alle Zwecke, keine Attribution nötig)
**Format:** SVG (skalierbar, recolorierbar in PowerPoint)
**Empfohlene Farbe für Dark Mode:** Weiss (#FFFFFF) oder Akzentfarbe

Ausgewählte Icons liegen in `docs/presentation/icons/selected/`:

| Icon-Datei | Verwendung |
|------------|------------|
| `brain.svg` | AI / Intelligenz |
| `bot.svg` | AI Agent |
| `cpu.svg` | Technologie |
| `code-2.svg` | Code / Entwicklung |
| `terminal.svg` | CLI / Claude Code |
| `git-branch.svg` | Git / Versionskontrolle |
| `layers.svg` | Architektur / Microservices |
| `workflow.svg` | BMAD Workflow / Event-Workflow |
| `zap.svg` | Geschwindigkeit / AI-Boost |
| `rocket.svg` | Launch / Deployment |
| `shield-check.svg` | Security / TDD |
| `test-tube-2.svg` | Tests / TDD |
| `users.svg` | Team / Stakeholder |
| `user-check.svg` | Speaker / Organizer |
| `clock.svg` | Timeline / Deadline |
| `calendar.svg` | Datum / Events |
| `bar-chart-3.svg` | Statistiken / Zahlen |
| `trending-up.svg` | Effizienz-Gain |
| `package.svg` | Microservice / Deployment |
| `server.svg` | Backend / AWS |
| `cloud.svg` | AWS / Cloud |
| `database.svg` | PostgreSQL / Daten |
| `refresh-cw.svg` | Iteration / Sprint |
| `check-circle-2.svg` | Abgeschlossen / Done |
| `alert-circle.svg` | Problem / Lesson Learned |
| `lightbulb.svg` | Idee / Insight |
| `book-open.svg` | Dokumentation / BMAD |
| `graduation-cap.svg` | Learning / Experience |
| `award.svg` | Qualität / Achievement |
| `watch.svg` | Apple Watch App |
| `smartphone.svg` | Mobile |
| `laptop.svg` | Development |
| `monitor.svg` | Frontend / Demo |
| `arrow-right.svg` | Progression / Flow |
| `chevron-right.svg` | Navigation |
| `key.svg` | Authentication / Cognito |
| `lock.svg` | Security |
| `file-code.svg` | OpenAPI / Spec |
| `file-text.svg` | Story / Dokumentation |
| `folder-open.svg` | Projekt / Struktur |
| `timer.svg` | Countdown / Session Timer |

---

## 8. DESIGN-SYSTEM FÜR SLIDES

### Farben (Dark Mode)
```
Hintergrund:    #0F1117  (fast schwarz)
Primärtext:     #FFFFFF  (weiss)
Sekundärtext:   #94A3B8  (grau)
Akzent 1:       #3B82F6  (blau — Technologie)
Akzent 2:       #10B981  (grün — Erfolg / Done)
Akzent 3:       #F59E0B  (amber — Warning / Lessons)
Akzent 4:       #EF4444  (rot — Problem)
Highlight:      #8B5CF6  (violett — AI / BMAD)
```

### Typografie-Empfehlungen
- **Headlines:** Inter Bold oder Segoe UI Bold — gross, dominant
- **Body:** Inter Regular
- **Code:** JetBrains Mono oder Fira Code

### Slide-Regeln
- Max. 5 Bullet-Points pro Slide
- Zahlen: Immer >60pt gross, prominent
- Screenshots: Mit abgerundeten Ecken, leichtem Schatten
- Icons: 32–48pt, weiss oder Akzentfarbe
- Eine Kernbotschaft pro Slide

---

## 9. WIRKUNGSSTÄRKSTE EINZELNE FACTS

Diese Aussagen allein auf einer Slide (Vollbild, grosser Text) sind am wirkungsvollsten:

1. **„60 Personentage. Eine Person."**
2. **„~300 Personentage geschätzt für ein Team."**
3. **„1'059 Commits. 589 Stunden. 14 Wochen."**
4. **„49% Produktionscode. 51% Tests."** ← Enterprise-Qualitätsstandard
5. **„75% meiner Entwicklungszeit: Claude-Sessions."**
6. **„Diese Plattform verwaltet das heutige Event."**
7. **„Ich hatte nie zuvor SwiftUI programmiert."** (für Watch App)

---

## 10. QUOTES FÜR SLIDES

Authentische Zitate die im Vortrag verwendet werden können:

> *„Ich dachte: AI braucht weniger Tests. Ich lag falsch."*

> *„AI schreibt Code der kompiliert — aber nicht immer das Richtige tut."*

> *„Der Kontext ist euer wertvollstes Asset — schützt ihn."*

> *„Garbage in, Garbage out — aber auf Enterprise-Niveau und in Sekunden."*

> *„Es ist kein Coding-Tool. Es ist ein Entwicklungspartner der nie schläft."*

> *„Ihr seid immer noch der Architekt. Ihr seid immer noch verantwortlich."*

> *„An einem Wochenende. Eine laufende Applikation. Ich konnte nicht aufhören."*

---

## 11. Q&A VORBEREITUNG (vollständig)

| Erwartete Frage | Kernpunkt |
|-----------------|-----------|
| „Wie steht es um Security?" | OWASP-aware, TDD deckt Lücken, manueller Security-Review bleibt |
| „Was wenn Claude falsch liegt?" | Tests fangen das auf — deswegen TDD wichtiger |
| „Skaliert das im Team?" | BMAD genau dafür — Agents auf Team-Mitglieder verteilen |
| „Welche Kosten?" | Claude Max: ~100 CHF/Monat, AWS Staging: ~50 CHF/Monat |
| „IP und Datenschutz?" | Code beim User. Anthropic-AGB. On-premise möglich. |
| „Braucht man Programmierkenntnisse?" | Ja — multipliziert Kapazität, ersetzt kein Fundament |
| „Was würdest du anders machen?" | Früher TDD, OpenAPI von Tag 1, kleinere MVP-Schritte |
| „Welches Modell hast du verwendet?" | Claude Sonnet / Opus (Anthropic). Claude Code als CLI. |
| „Kann man das ohne BMAD machen?" | Ja — aber BMAD gibt Struktur die verhindert dass AI ins Chaos führt |
| „Wann ist AI nicht geeignet?" | Legacy-Systeme ohne Dokumentation, stark regulierte Domains (initial) |

---

## 12. KONTEXT: WOFÜR IST DAS SYSTEM?

**BATbern = Berner Architekten Treffen**
- Community von IT-Architekten und Software Engineers in Bern
- Gegründet vor 20+ Jahren
- 60 Events bisher (BAT 1 bis BAT 60)
- ~200 Teilnehmer pro Event
- Bekannte Unternehmen: SBB, Mobiliar, PostFinance, Swisscom, BKW, ELCA, Zühlke, AWS...
- Der 60. Event (BAT 60) = Dieser Vortrag am 6.3.2026

**Die Plattform ersetzt:**
- Statische HTML-Website (seit 20+ Jahren)
- Manuelle E-Mail-Koordination mit Speakers
- Excel-Sheets für Task-Tracking
- Fehlende digitale Archivierung

**Was jetzt möglich ist:**
- Organizer erstellt Event → System führt durch 9-State Workflow
- Speaker bekommt Einladungslink → Self-Service Portal
- CDN publiziert automatisch 30 Tage vor Event (Speakers) + 14 Tage (Agenda)
- 20+ Jahre Content sofort durchsuchbar
- Event-Registrierung mit QR-Code-Bestätigung
