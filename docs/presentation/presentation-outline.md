# BATbern Vortrag — Präsentations-Outline
## „Enterprise Software alleine mit AI bauen — von der Idee zur Produktion"

**Datum:** 6. März 2026
**Speaker:** Nissim
**Dauer:** 35 Minuten Vortrag + 10 Minuten Q&A
**Sprache:** Deutsch
**Thema des Events:** Agentic Software Development

---

## Design-Direktiven für Slide-Erstellung

- **Stil:** Modern, minimalistisch, tech-affin — kein Corporate Bingo
- **Farbpalette:** Dunkel (dark mode), Akzentfarbe BATbern-Blau/Orange
- **Schrift:** Große Headlines, wenig Fliesstext — Stichwörter, keine Sätze
- **Zahlen:** Immer gross und prominent — Zahlen sind die Helden dieser Präsentation
- **Icons statt Bullets** wo möglich
- **Jede Slide hat maximal eine Kernbotschaft**

---

## BLOCK 1 — „Ich war genau wie ihr" *(4 Minuten, 4 Slides)*

### Kernbotschaft
Der Speaker war selbst ein Skeptiker. Das schafft Vertrauen bei einem skeptischen Publikum.

---

### Slide 1.1 — Einstieg: Die Frage
**Layout:** Vollbild, zwei Fragen, grosser Text, Pause eingebaut

**Inhalt:**
- Headline: *„Wer hat schon mit einem AI-Coding-Tool gearbeitet?"*
- Subline: *„Und wer war… enttäuscht?"*

**Speaker Notes:**
> Kurze Pause für Handheben lassen. Dann: „Ich auch. Vor zwei Jahren."

---

### Slide 1.2 — Copilot 2023: Die Ernüchterung
**Layout:** Zwei Spalten — Was funktionierte / Was nicht funktionierte

**Inhalt:**
- Headline: *„2023 — Team-Experiment mit GitHub Copilot"*
- Links ✓: Tests schreiben, Code erklären, Dokumentation
- Rechts ✗: Mehr als eine Datei, Architektur-Entscheide, Zusammenhänge verstehen
- Fazit-Box: *„Hilfreich. Aber kein Game Changer."*

**Speaker Notes:**
> „Ich habe meinen Teams den Auftrag gegeben: Evaluiert Copilot. Das Resultat war: ja, es hilft ein bisschen. Aber immer confined to one file. Immer reaktiv. Nie initiativ. Wir haben es wieder weggelegt."

---

### Slide 1.3 — Der Wendepunkt: August 2025
**Layout:** Timeline oder dramatischer Moment — ein Link, ein Datum

**Inhalt:**
- Headline: *„19. August 2025 — ein Link ändert alles"*
- Von: Andy (CTO, Mobiliar) — YouTube Link
- Text: *„An einem Wochenende baue ich eine komplette Applikation: RTFM — Regular Travel Expense Form Manager"*
- Quote: *„Ich konnte nicht aufhören."*

**Speaker Notes:**
> „Andy schickt mir einen Link. Ich schaue das Video an einem Freitagabend. Am Sonntag habe ich eine laufende Applikation. Nicht ein CRUD-Formular. Eine Applikation mit Authentifizierung, Business Logic, Tests, Deployment."

---

### Slide 1.4 — Die Entscheidung
**Layout:** Grosse Frage, dann Antwort

**Inhalt:**
- Headline: *„Was wenn ich das mit einem echten Enterprise-Projekt teste?"*
- Text: *„BATbern brauchte seit Jahren eine neue Plattform."*
- Arrow: September 2025 → Start
- Subline: *„Alleine. Mit AI. Mit Methode."*

---

## BLOCK 2 — „Was ist BMAD?" *(3 Minuten, 3 Slides)*

### Kernbotschaft
BMAD ist die Methode, die aus einem Experiment eine Disziplin macht. 90 Sekunden Erklärung — dann weiter.

---

### Slide 2.1 — BMAD: Boomerang Agile Method
**Layout:** Name + Tagline + kurze Definition

**Inhalt:**
- Headline: *„BMAD — Boomerang Agile Method"*
- Definition: *„Eine strukturierte Methode für AI-gestützte Software-Entwicklung"*
- Kernidee: *„Spezialisierte AI-Agents mit Rollen, Personas, Methoden"*
- GitHub Link / QR Code: `github.com/bmad-method` (open source, kostenlos)

---

### Slide 2.2 — Die 5 Phasen
**Layout:** Horizontaler Flow mit Icons für jeden Agent

**Inhalt:**
- Phase 1 📊 Analyst → Discovery, Marktanalyse, PRD
- Phase 2 🏗️ Architect → Architektur, Tech Stack, Entscheide
- Phase 3 📋 PM + SM → Epics, Stories, Sprint Planning
- Phase 4 💻 Dev → Implementation, TDD, Code Review
- Phase 5 🧪 QA/TEA → Tests, Qualität, Abnahme

**Speaker Notes:**
> „Jeder Agent hat eine eigene Persönlichkeit, eigene Frameworks, eigene Checkpoints. Kein Chaos — Methode."

---

### Slide 2.3 — Warum BMAD?
**Layout:** 3 Icons mit kurzen Punkten

**Inhalt:**
- 🎯 Struktur für AI-Entwicklung (kein Prompt-Chaos)
- 🔄 Skaliert vom Weekend-Projekt bis Enterprise
- 🤝 Jeder Agent kennt seinen Scope — kein Context-Overflow

---

## BLOCK 3 — „Die Plattform" *(2 Minuten, 2 Slides)*

### Kernbotschaft
Scope setzen — damit das Publikum versteht, was „Enterprise" hier bedeutet.

---

### Slide 3.1 — BATbern Platform: Was wurde gebaut?
**Layout:** Stats-Slide mit grossen Zahlen

**Inhalt:**
- Headline: *„BATbern Event Management Platform"*
- 5 Microservices (Java 21, Spring Boot)
- 1 React Frontend (TypeScript, Material-UI)
- 9-State Event Workflow
- AWS Infrastructure (ECS Fargate, RDS, S3, CloudFront, Cognito)
- 3 Environments (Dev / Staging / Production)
- 54+ historische Events migriert
- Epic 1–5 abgeschlossen, Epic 6 auf Staging

---

### Slide 3.2 — Der Meta-Moment *(Impact Slide)*
**Layout:** Vollbild, grosser Text, hohe emotionale Wirkung

**Inhalt:**
- Headline: *„Diese Plattform verwaltet das heutige Event."*
- Subline: *„Meine Einladung als Speaker: über dieses System verschickt."*
- Subline: *„Das Event das ihr heute besucht: in diesem System organisiert."*
- Subline: *„Was ich euch jetzt zeige — läuft live auf AWS."*

**Speaker Notes:**
> Kurze Pause. Lassen Sie das wirken. Dann: „Zeigen wir es."

---

## BLOCK 4 — Live Demo *(10 Minuten)*

### Kernbotschaft
Sehen ist glauben. Live-Demo beweist die Hauptthese: echte Enterprise Software, produktiv, jetzt.

---

### Slide 4.0 — Demo-Ankündigung
**Layout:** Einfach. Aufmerksamkeit auf Bildschirm lenken.

**Inhalt:**
- Headline: *„Live Demo"*
- Subline: *„BATbern Event Management Platform"*

---

### Demo-Flow (kein Slide, Browser/App live):

```
00:00  Login als Organizer (30 Sek)
00:30  Organizer Dashboard — laufendes Event zeigen
01:00  Neues Event erstellen — Typ, Datum, Format
02:00  Topic Selection → Speaker hinzufügen → Status-Tracking
05:00  Progressive Publishing Engine (30/14 Tage Auto-Publish)
06:00  Archiv — 20+ Jahre Content, Volltextsuche
07:00  Optional: BMAD Story öffnen + Claude kurz zeigen (wie ein Agent arbeitet)
08:30  Zurück zu Slides
```

**Risiko-Management:**
- Lokale Umgebung als Fallback bereit haben (kein Internet-Dependency)
- Tutorial-Video (docs/user-guide/assets/videos/workflow/event-workflow-schulung-de.mp4) als Backup im Browser-Tab bereit
- Video läuft während der Pause für Interessierte

---

### Slide 4.1 — Architektur-Überblick (nach Demo)
**Layout:** Einfaches C4/Service-Diagramm

**Inhalt:**
- Headline: *„5 unabhängig deploybare Microservices"*
- Diagramm: API Gateway → [Event Management | Speaker Coordination | Company/User | Partner | Attendee]
- Subline: *„Domain-Driven Design — jede Domain ein eigenes bounded context"*

---

## BLOCK 5 — „Die Zahlen" *(4 Minuten, 4 Slides)*

### Kernbotschaft
Konkrete Evidenz. Zahlen die schockieren — mit ehrlichen Kommentaren.

---

### Slide 5.1 — Die Hauptzahl *(Wirkungsstärkste Slide der ganzen Präsentation)*
**Layout:** Vollbild, zwei grosse Zahlen, visuelle Gegenüberstellung

**Inhalt:**
- Links gross: **60 PT** — *„Ich. Alleine."*
- Rechts gross: **~300 PT** — *„Geschätzte Team-Variante"*
- Unten: *„Ist das 80% Effizienzgewinn? Nein. Es ist eine andere Art zu arbeiten."*

**Speaker Notes:**
> „Ich sage bewusst: das ist keine 80%-Einsparung. Es ist eine andere Arbeitsweise. Andere Stärken, andere Schwächen. Aber diese Zahl ist real."

---

### Slide 5.2 — Die Timeline
**Layout:** Horizontale Timeline

**Inhalt:**
- Aug 2025: YouTube Link → RTFM Wochenende
- Sep 2025: BATbern Start
- Okt–Nov 2025: Epics 1–2 (Foundation, Entity CRUD)
- Dez 2025: Epics 3–4 (Migration, Public Website)
- Jan 2026: Epic 5 complete (MVP) ✅
- Feb 2026: Epic 6 Staging ✅
- Mrz 2026: Dieser Vortrag 🎯

---

### Slide 5.3 — Code-Qualität
**Layout:** Stats-Grid

**Inhalt:**
- Headline: *„AI-Code ist nicht schlechter Code — wenn man es richtig macht"*
- Code / Test Ratio: ~50% / 50%
- Test Coverage: 85%+
- SonarQube: [Quality Gate Status]
- TDD: Red-Green-Refactor für alle Features
- *„Kein Mock in production code"*

---

### Slide 5.4 — Was wurde gebaut (Zusammenfassung)
**Layout:** Checklist / Achievement-Slide

**Inhalt:**
- ✅ 5 Microservices (Java 21 + Spring Boot)
- ✅ React 19 Frontend mit role-adaptive UI
- ✅ 9-State Event Workflow + Speaker Coordination
- ✅ AWS CDK Infrastructure (3 Environments)
- ✅ CI/CD Pipeline (GitHub Actions)
- ✅ 54+ historische Events migriert
- ✅ Apple Watch App (Bonus)
- *„Das alles: 60 Personentage. Eine Person."*

---

## BLOCK 6 — „Was ich falsch gemacht habe" *(7 Minuten, 6 Slides)*

### Kernbotschaft
Ehrlichkeit kauft Glaubwürdigkeit. Lessons learned sind der grösste Mehrwert für das Publikum.

---

### Slide 6.0 — Intro Lessons Learned
**Layout:** Einfach, direkt

**Inhalt:**
- Headline: *„Was ich falsch gemacht habe"*
- Subline: *„5 Lektionen — ehrlich und spezifisch"*

**Speaker Notes:**
> „Das hier ist der Teil den ich am meisten vorbereitet habe. Weil hier liegt der echte Wert für euch."

---

### Slide 6.1 — Lesson 1: Context Management
**Layout:** Problem → Realität → Lösung

**Inhalt:**
- Headline: *„Context Management ist alles"*
- Problem: *„AI 'vergisst' — bei jedem neuen Gespräch verliert man Kontext"*
- Realität: *„Mit wachsender Codebasis wird Context Management zur Hauptaufgabe"*
- Lösung: *„MEMORY.md, Project Context Files, strukturierte Session-Übergaben"*
- Quote: *„Der Kontext ist euer wertvollstes Asset — schützt ihn."*

---

### Slide 6.2 — Lesson 2: TDD wird wichtiger, nicht unwichtiger
**Layout:** Erwartung vs. Realität (zwei Spalten)

**Inhalt:**
- Headline: *„Ich dachte: AI braucht weniger Tests. Ich lag falsch."*
- Erwartung: *„AI schreibt Code schnell → Tests werden weniger wichtig"*
- Realität: *„AI schreibt Code der kompiliert — aber nicht immer das Richtige tut"*
- Lösung: *„Red-Green-Refactor ist mit AI MEHR wichtig als ohne"*
- Quote: *„Ohne Tests optimiert AI für das was es sieht, nicht für das was gemeint ist."*

---

### Slide 6.3 — Lesson 3: OpenAPI Contract-First
**Layout:** Architektur-Prinzip, Enterprise-relevant

**Inhalt:**
- Headline: *„API-Vertrag zuerst — Backend und Frontend dagegen entwickeln"*
- Problem: *„AI produziert Code in Sekunden — ohne Vertrag: Integration-Chaos in Sekunden"*
- Lösung: *„OpenAPI als Single Source of Truth — beide Seiten entwickeln gegen denselben Vertrag"*
- Enterprise-Relevanz: *„Für Teams mit separaten Frontend/Backend-Teams: absolut kritisch"*
- Quote: *„Der Vertrag muss zuerst da sein. Immer."*

---

### Slide 6.4 — Lesson 4: Stories als Detailspecs
**Layout:** Schlechte vs. Gute Story (Vergleich)

**Inhalt:**
- Headline: *„Garbage in, Garbage out — aber auf Enterprise-Niveau"*
- Schlechte Story: *„Als Organizer möchte ich einen Speaker hinzufügen"*
- Gute Story enthält: Screen-Design, API-Contract, Datenmodell, Acceptance Criteria, Edge Cases
- Quote: *„Je mehr präziser Kontext, desto besser das Ergebnis. AI ist kein Gedankenleser."*

---

### Slide 6.5 — Lesson 5: Nicht zu viel zu früh planen
**Layout:** Contra-intuitiv aufgebaut

**Inhalt:**
- Headline: *„Meine Intuition: AI braucht mehr Planung. Falsch."*
- Intuition: *„Alles vorab spezifizieren → bessere Resultate"*
- Realität: *„Zu viel Upfront-Planung erzeugt false confidence und Waste"*
- Besser: *„Kleines MVP pro Domain → iterieren → dann skalieren"*
- Quote: *„Der 16-Schritt-Workflow war gut gemeint — aber zu starr für die Realität."*

---

## BLOCK 7 — „Was könnt ihr morgen tun?" *(3 Minuten, 3 Slides)*

### Kernbotschaft
Konkrete nächste Schritte. Das Publikum verlässt den Raum mit einem Plan.

---

### Slide 7.1 — Start Small
**Layout:** Klarer Call-to-Action

**Inhalt:**
- Headline: *„Startet nicht mit eurem nächsten Enterprise-Projekt"*
- Schritt 1: *„Nehmt ein Weekend-Projekt — etwas das euch interessiert"*
- Schritt 2: *„BMAD installieren: 5 Minuten, Open Source, kostenlos"*
- Schritt 3: *„Claude Code (Anthropic) — das Tool das ich verwende"*
- QR Code: BMAD GitHub

---

### Slide 7.2 — Top 3 Tips
**Layout:** 3 grosse Icons mit Titel und einem Satz

**Inhalt:**
- Headline: *„3 Dinge die alles verändern"*
- 🔄 **Resume**: Context über Sitzungen hinweg erhalten — nie von vorne anfangen
- 📋 **Plans**: Immer mit einem Plan arbeiten bevor implementiert wird — AI braucht Struktur
- 🎉 **Party Mode**: Mehrere Agents gleichzeitig für Brainstorming und Architektur-Entscheide

---

### Slide 7.3 — Der Mindset-Shift
**Layout:** Vollbild, starke Aussagen

**Inhalt:**
- Headline: *„Es ist kein Coding-Tool."*
- *„Es ist ein Entwicklungspartner der nie schläft."*
- *„Der nie vergisst."*
- *„Der nie ungeduldig wird."*
- Separator
- *„Ihr seid immer noch der Architekt. Ihr seid immer noch verantwortlich."*
- *„Aber ihr könnt alleine bauen, was früher ein Team brauchte."*

---

## BLOCK 8 — Special Goodie: Apple Watch App *(2 Minuten, 2 Slides)*

### Kernbotschaft
WOW-Moment als Abschluss. Zeigt dass die Möglichkeiten grösser sind als das Publikum dachte.

---

### Slide 8.1 — „Und dann konnte ich nicht aufhören…"
**Layout:** Dramatische Enthüllung

**Inhalt:**
- Headline: *„Und dann konnte ich nicht aufhören."*
- *„Weil ich mal schauen wollte ob es geht…"*
- *„Apple Watch App — SwiftUI — BATbern Companion"*
- *„Im App Store. Gratis. Für alle BATbern-Teilnehmer."*
- Screenshot/Mockup der Watch App
- QR Code zum App Store

**Speaker Notes:**
> „Ich habe nie zuvor SwiftUI oder watchOS entwickelt. Ich habe einfach angefangen."

---

### Slide 8.2 — Abschluss / Danke
**Layout:** Clean closing slide

**Inhalt:**
- Headline: *„Danke"*
- *„Der Code dieser Plattform: Open Source auf GitHub"*
- QR Codes (3): BMAD GitHub | BATbern Platform | Watch App
- LinkedIn / Kontakt
- *„Fragen?"*

---

## Anhang: Q&A Vorbereitung *(10 Minuten)*

Erwartete Fragen von skeptischem Enterprise-Publikum:

| Frage | Kernpunkt der Antwort |
|-------|----------------------|
| „Wie steht es um Security?" | Kein Security-Bypass möglich — AI kennt OWASP, TDD deckt Lücken auf, manueller Security-Review bleibt Pflicht |
| „Was passiert wenn Claude falsch liegt?" | Tests fangen das auf. Deswegen ist TDD wichtiger, nicht unwichtiger. |
| „Kann man das in einem Team skalieren?" | BMAD ist genau dafür gebaut — Agents können auf Team-Mitglieder verteilt werden |
| „Welche Kosten entstehen?" | Claude Pro: ~20 CHF/Monat. Claude Max: ~100 CHF/Monat. AWS Staging: ~50 CHF/Monat. |
| „IP und Datenschutz?" | Code bleibt beim User. Anthropic-AGB beachten. Für sensitive Domains: on-premise Modelle möglich. |
| „Braucht man Programmierkenntnisse?" | Ja — AI macht aus einem Nicht-Entwickler keinen Entwickler. Aber es multipliziert die Kapazität eines Entwicklers erheblich. |
| „Was würdest du anders machen?" | Früher mit Tests anfangen, OpenAPI von Tag 1, kleinere Schritte im Upfront-Planning |

---

## Slide-Zählung Übersicht

| Block | Slides | Minuten |
|-------|--------|---------|
| 1 — Ich war wie ihr | 4 | 4 |
| 2 — Was ist BMAD? | 3 | 3 |
| 3 — Die Plattform | 2 | 2 |
| 4 — Live Demo | 2 + live | 10 |
| 5 — Die Zahlen | 4 | 4 |
| 6 — Lessons Learned | 6 | 7 |
| 7 — Was tun? | 3 | 3 |
| 8 — Watch App + Danke | 2 | 2 |
| **Total** | **26 Slides** | **35 Min** |
