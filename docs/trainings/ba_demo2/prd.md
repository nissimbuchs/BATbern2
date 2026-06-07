---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-02b-vision', 'step-02c-executive-summary', 'step-03-success', 'step-04-journeys', 'step-05-domain', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional', 'step-10-nonfunctional', 'step-11-polish', 'step-12-complete']
inputDocuments:
  - docs/trainings/ba_demo2/brainstorming-session-2026-04-04.md
  - docs/trainings/ba_demo2/ux-design-specification.md
  - _bmad-output/project-context.md
  - docs/prd-enhanced.md
documentCounts:
  briefs: 0
  research: 0
  brainstorming: 1
  projectDocs: 3
workflowType: 'prd'
workflow_completed: true
classification:
  projectType: web_app
  domain: event_management_community_governance
  complexity: medium
  projectContext: brownfield
---

# Product Requirements Document — BATbern Active Partner Meeting Agenda

**Author:** Nissim
**Date:** 2026-04-04

---

## Executive Summary

BATbern's bi-annual partner meetings currently run on verbal agreements, emailed PDFs, handwritten notes, and "someone takes minutes." Six agenda items — greeting, analytics review, audit sign-off, budget approval, topic discussion, and open topics — exist only as text in an ICS calendar invite. There is no governance trail for financial sign-offs, no structured record of decisions, and absent partners are completely excluded from the process.

This PRD defines the **Active Partner Meeting Agenda** — a system that transforms those 6 static agenda items into interactive, system-driven features within the existing BATbern platform. The organizer walks through the meeting in a Presenter Mode, and every action they take (toggling attendance, uploading a document, triggering a vote, typing a note) silently contributes to auto-assembling meeting minutes. The meeting documents itself as a byproduct of use.

The feature extends the meeting beyond the room: absent partners receive published minutes, participate in async fee acceptance, and provide feedback — ensuring no partner is ever out of the loop.

**Target users:** Organizer (meeting driver, sole input device), Present Partners (view projected screen, participate verbally), Absent Partners (async via portal), New Partners (first-year onboarding), Stand-Ins (non-system delegates represented by organizer).

**Platform:** Brownfield extension of BATbern (React 19 + Spring Boot microservices on AWS). The partner coordination service (Epic 8) and partner portal already exist. This feature adds the meeting lifecycle engine on top.

### What Makes This Special

1. **Minutes as byproduct** — The organizer never "creates minutes." Every interaction (toggling presence, casting a vote, uploading a PDF, typing a note) auto-populates a structured meeting record. After the meeting, the organizer reviews a near-complete document they barely need to edit. This is the core innovation.

2. **Proxied group voting** — Partners don't use devices during the meeting. The organizer casts approve/decline votes on behalf of each present partner, like a teller in parliament. One person, one device, full attribution trail.

3. **Configurable agenda engine** — Not a hardcoded sequence. The organizer toggles which active agenda item types are relevant for each meeting (annual meeting = all 6; mid-year check-in = analytics + topics only). Custom passive items can be added for ad-hoc discussion topics.

4. **Meeting extends beyond the room** — Async fee acceptance, feedback pulses, and published minutes ensure absent partners have full context and can still participate in decisions. The meeting's value isn't limited to the 90 minutes in the room.

5. **Action item carry-forward** — Action items created during a meeting carry forward to the next meeting automatically. Nothing falls through the cracks between bi-annual meetings. Items carried 2+ meetings get urgency indicators.

## Project Classification

| Attribute | Value |
|-----------|-------|
| **Project Type** | Web Application (SPA — React 19, browser-based) |
| **Domain** | Event Management / Community Governance |
| **Complexity** | Medium |
| **Project Context** | Brownfield — extending existing BATbern platform (Epics 1-8 complete) |
| **Primary Service** | Partner Coordination Service (existing) |
| **Frontend** | React 19 + TypeScript + MUI + Tailwind (dark theme for presenter mode) |
| **Backend** | Java 21 + Spring Boot 3 + PostgreSQL 15 |
| **Infrastructure** | AWS ECS Fargate + S3 + CDK |

---

## Success Criteria

### User Success

| Criteria | Measurable Target |
|----------|------------------|
| Meeting execution | Full 6-item agenda completed in Presenter Mode without leaving the system |
| Minutes completeness | Auto-assembled minutes contain 90%+ of meeting content with minimal manual editing |
| Zero training needed | New organizer can run a meeting after 2-minute orientation or cold with prep checklist |
| Vote recording speed | Every vote captured with partner-level attribution in under 30 seconds |
| Post-meeting distribution | Minutes published and sent to all partners within 5 minutes of meeting end |
| Action item persistence | 100% of action items carry forward to next meeting automatically |
| Absent partner inclusion | Absent partners can review full minutes and participate in async decisions within 24 hours |

### Business Success

| Criteria | Measurable Target |
|----------|------------------|
| Governance trail | Every financial sign-off (audit, budget) has a traceable, dated digital record with per-partner attribution |
| Meeting prep time | Organizer prep reduced from ad-hoc manual setup to a system-guided checklist |
| Institutional memory | Year-over-year meeting archives with analytics snapshots, decision records, and action item history browsable |
| Partner satisfaction | Post-meeting feedback pulse scores trending upward over consecutive meetings |
| Fee acceptance rate | 100% of partners (present + absent) respond to fee acceptance within the deadline |

### Technical Success

| Criteria | Measurable Target |
|----------|------------------|
| Presenter Mode stability | Zero crashes or data loss during a live meeting session |
| Auto-save reliability | All organizer actions persisted within 2 seconds, no "did it save?" anxiety |
| Document upload | PDF/spreadsheet upload and embedded viewing functional with files up to 20MB |
| Minutes PDF generation | Exportable PDF with attendance, votes, documents, notes, and action items |
| Mobile minutes viewing | Published minutes readable on mobile (iPhone Safari, Android Chrome) for absent partners |

---

## User Journeys

### Journey 1: Organizer — Create & Prepare Meeting (Pre-Meeting)

**Persona:** Sarah, BATbern organizer. Runs bi-annual partner meetings. Tech-savvy but uses this system twice a year — needs it to be self-explanatory.

**Entry:** Partner Portal → Meetings → "Create New Meeting"

**Narrative:**
Sarah creates a new partner meeting, sets the date, time, and location. She selects which active agenda items are relevant — for the annual meeting, she toggles on all six. She adds a custom passive item: "Venue proposal for 2027." She drags items to reorder them. She configures the auditor's email address so they receive an automatic ICS invite (only if the audit sign-off item is enabled).

She saves the meeting in DRAFT status. The system sends ICS invites to all partners with deep-links to analytics and topic voting pages. Sarah monitors the Meeting Prep Dashboard: a checklist shows what's ready and what's not — audit PDF uploaded? Budget spreadsheet uploaded? RSVP count? Topic voting status? Open action items from last meeting?

When all critical items are green, the "Start Presenter Mode" button enables. Sarah is confident — the system has her back.

**Edge cases:**
- Audit PDF not uploaded → yellow warning, soft-blocks Start until uploaded or item removed
- Zero RSVPs → warning shown but doesn't block
- Previous meeting has open action items → surfaced automatically in prep dashboard

---

### Journey 2: Organizer — Run Meeting in Presenter Mode (Live)

**Entry:** "Start Presenter Mode" on meeting prep screen

**Narrative:**
Sarah clicks "Start Presenter Mode." The screen transitions to the dark theme — zinc-950 background, blue-400 accents, large typography optimized for the projector. The agenda sidebar appears on the left. The first item (Who Is Who gallery) loads in the main area. The system creates a PartnerMeetingMinutes entity in DRAFT status.

Sarah walks through each item:

1. **Greeting / Who Is Who** — A gallery grid of partner companies: logo, delegate name, photo. Sarah clicks each present partner to toggle their green "Present" badge. Absent partners are dimmed. For Company Y whose partner sent a delegate, Sarah clicks "Add Stand-in" and types the delegate's name. The attendance section of the minutes writes itself.

2. **Analytics Review** — The existing analytics dashboard embeds in the main area. Sarah clicks "Capture Snapshot" — the system freezes current analytics data as an immutable record attached to the minutes. Partners see the numbers on the projector.

3. **Audit Sign-off** — The uploaded audit PDF renders in an embedded viewer. Sarah clicks "Start Vote." A vote panel appears on the right with each present partner listed. She clicks approve/decline for each partner as they voice their decision. "Financial Report 2025 — Approved (8/8)" animates onto the screen. The PDF, vote result, and Sarah's notes are captured in the minutes.

4. **Budget Approval** — Same pattern: budget spreadsheet in the viewer, vote panel for directional approval of the budget and proposed new fee. After the meeting, the system will trigger async fee acceptance for all partners.

5. **Topic Discussion** — Topic voting results and blob game results display. Sarah adds discussion notes per topic.

6. **Hot Topics / Custom Items** — Free-form note area for unstructured discussion.

At every step: the note panel is visible at the bottom, the agenda sidebar shows progress, auto-save indicator confirms "Saved ✓." Sarah presses "Next Item →" to advance. After the last item, the button changes to "End Meeting."

Sarah clicks "End Meeting" → transition to Minutes Review screen.

**Edge cases:**
- Skip an item → marked as "Skipped" in minutes
- Go back → non-destructive, all data preserved
- Stand-in proxy voting → organizer casts votes on behalf, minutes log attribution chain
- Add action item → button available on every item, captures title, owner, optional deadline

---

### Journey 3: Organizer — Review & Publish Minutes (Post-Meeting)

**Entry:** Automatic transition from end of Presenter Mode

**Narrative:**
Sarah sees the auto-assembled minutes: a clean, document-style view with sections per agenda item. Attendance list with present/absent/stand-in. Analytics snapshot embedded. Audit PDF attached with vote result. Budget vote result with fee decision. Topic discussion notes. Action items with assigned owners and deadlines.

She edits a few sections — adds a clarification to the budget discussion, fixes a typo. She adds one more action item she forgot during the meeting. She clicks "Publish & Send." Confirmation: "Send minutes to all 12 partners?" She confirms.

Minutes status → PUBLISHED. All partners (present + absent) receive email with portal link. Fee acceptance requests trigger for all partners. Feedback pulse survey schedules for 24 hours later.

**Edge cases:**
- Save draft and publish later → "Save Draft" button available
- PDF too large for email → email contains portal link instead

---

### Journey 4: Absent Partner — Catch Up & Act (Async)

**Persona:** Marco, partner at an architecture firm. Couldn't attend the spring meeting due to a client deadline.

**Entry:** Email notification with link to published minutes

**Narrative:**
Marco receives an email: "BATbern Partner Meeting — Spring 2026 Minutes Published." He clicks the link, opens the partner portal on his phone. The minutes are displayed in a mobile-friendly format with collapsible sections.

He reads through everything: who attended, analytics highlights (snapshot attached), audit report approved unanimously (PDF downloadable), budget direction approved with new fee of CHF 2,400.

He receives a fee acceptance request: "Review and confirm the new partner fee: CHF 2,400 (up from CHF 2,200)." He clicks "Accept." Done.

24 hours later, he receives a 3-question feedback pulse: "Was the meeting productive? (1-5) / Topics we should have discussed? / Suggestions for next meeting?" He fills it in.

Marco is fully informed and has participated in all post-meeting decisions — despite never being in the room.

**Edge cases:**
- No action on fee acceptance → reminders at 7 and 14 days, then organizer notified
- Partner declines fee → organizer notified with partner's comment
- Action item assigned to absent partner → they see it in their portal

---

### Journey 5: New Partner — First Meeting Experience

**Persona:** Lisa, new partner delegate. Her company joined BATbern this year.

**Narrative:**
Lisa logs into the partner portal for the first time. A guided onboarding tour walks her through: analytics, topic voting, partner directory, meeting agenda. She receives a "New Partner Context Package" — current fee, last year's analytics summary, organizer contacts, other partner companies.

At the meeting, her company appears in the Who Is Who gallery with a subtle purple "New" badge. Veterans notice and welcome her. She feels she belongs.

---

### Journey 6: Next Meeting — Action Item Carry-Forward (Lifecycle)

**Entry:** Organizer creates next partner meeting

**Narrative:**
Sarah creates the autumn meeting. The system surfaces 3 open action items from the spring meeting in the prep dashboard. She adds "Review Open Actions" as an agenda item. During the meeting, each item is reviewed: one marked done, one extended, one carried forward. Items carried from 2+ meetings get an urgency indicator.

Nothing falls through the cracks. The system remembers what people forget.

---

## Innovation Focus

### Novel UX Patterns

| Pattern | Type | Description |
|---------|------|-------------|
| **Minutes as byproduct** | Novel | Every organizer action silently contributes to auto-assembling meeting minutes. No explicit "save to minutes" action. |
| **Proxied group voting** | Novel | Organizer casts votes on behalf of each partner, fitting the single-device, projector-driven constraint. Like a parliamentary teller. |
| **Action item carry-forward** | Novel | System automatically surfaces open action items from previous meetings when creating the next meeting. |
| **Configurable active agenda** | Adapted | Agenda items are not hardcoded — organizer toggles which item types are relevant per meeting, reorders via drag-and-drop. |
| **Projector-first presenter mode** | Adapted (PowerPoint) | Click-to-advance linear flow through interactive agenda items. Each "slide" supports domain-specific interactions. |

### Validation Approach

The core innovation (minutes as byproduct) can be validated in the first real meeting:
- Does the auto-assembled document capture 90%+ of meeting content?
- Does the organizer spend less than 5 minutes editing before publishing?
- Do absent partners report feeling "fully informed" from the minutes alone?

---

## Project-Type Requirements (Web App)

### Browser & Device Support

| Context | Target | Minimum |
|---------|--------|---------|
| **Presenter Mode** | MacBook + projector (1920x1080) | 1280x720 |
| **Partner Portal (minutes, fee acceptance)** | Desktop Chrome, Firefox, Safari | iPhone Safari, Android Chrome |
| **Meeting Prep Dashboard** | Desktop Chrome, Firefox, Safari | — |

**Presenter Mode is desktop-only.** No mobile/tablet adaptation — it runs on the organizer's laptop projected to the room. Warning message if screen width < 1280px.

**Partner Portal is fully responsive.** Minutes viewing, fee acceptance, and feedback pulse must work on mobile. Partners check minutes on their phone from the email notification.

### SPA Architecture

- Single-page application within existing BATbern React 19 frontend
- New route group: `/partner/meetings/*` for meeting management
- Presenter Mode as a dedicated full-screen layout (`PresenterLayout`)
- Standard partner portal pages use existing `OrganizerLayout`

### Accessibility

- WCAG 2.1 AA compliance (matches existing BATbern standard)
- Presenter Mode: all text AAA (7:1+) on dark backgrounds, full keyboard navigation (→/←/1-9/N/V/Esc)
- Partner Portal: 44x44px touch targets, semantic HTML + ARIA, logical heading hierarchy
- No color-only information: badges use color + text label + icon

### Real-Time & Offline

- No offline support needed — meetings happen in venues with connectivity
- Auto-save every 2 seconds of inactivity (no explicit save action)
- No WebSocket requirement — standard HTTP with optimistic UI updates sufficient
- Pre-load all content before entering Presenter Mode (no loading spinners during meeting)

---

## Scope & Phasing

### MVP Strategy

**Philosophy:** Experience MVP — the minimum that delivers the "meeting documents itself" magic moment. The organizer must be able to run a full meeting in Presenter Mode and produce publishable minutes without leaving the system.

### Phase 1 — MVP (Must-Have)

**Layer 1: Foundation**
- PartnerMeeting entity with configurable agenda (active + passive items, ordered)
- Agenda Builder UI (toggle item types, reorder via drag-and-drop, add custom items)
- RSVP dashboard (partners confirm/decline attendance pre-meeting)
- Document upload & embedded viewer component (PDF, reuses existing S3 presigned URL pattern)
- Approve/decline vote component (reusable across audit, budget, and future use)

**Layer 2: Meeting Flow**
- Presenter Mode layout (dark theme, three-zone: sidebar + main + notes)
- Who Is Who partner gallery with presence toggle and "New" badge
- Per-item note taking (always-visible, auto-save)
- Meeting minutes entity (auto-assembly from agenda item interactions)
- Linear agenda navigation (next/previous, sidebar jump, keyboard shortcuts)

**Layer 3: Agenda Item Interactions**
- Attendance → auto-populates minutes attendance section
- Analytics snapshot → captured and attached to minutes
- Audit PDF + vote result → recorded in minutes with document attachment
- Budget doc + vote result → recorded in minutes, triggers async fee acceptance
- Meeting Prep Checklist (dynamic based on enabled items)
- Action items with owner assignment and deadline

**Layer 4: Post-Meeting & Lifecycle**
- Minutes review & editing screen
- Publish & distribute minutes (email to all partners with portal link)
- Async fee acceptance widget in partner portal
- Fee acceptance tracker (organizer dashboard)
- Action item carry-forward to next meeting
- Stand-in delegation record and proxy vote attribution

### Phase 2 — Quick Wins

- Deep-links in ICS meeting invite (analytics page, topic voting page)
- Auditor email configuration (conditional ICS invite when audit item is on agenda)
- Partner profile cards (photo, company, role, years as partner)
- Post-meeting feedback pulse (3-question survey, sent 24h after minutes published)

### Phase 3 — Nice-to-Have (Future)

- New partner onboarding tour (guided first-login walkthrough)
- New partner context package (auto-assembled briefing pack)
- Historized analytics archive (browsable year-over-year snapshots)
- Minutes PDF export (formatted downloadable PDF)

### Explicitly Out of Scope

- Multi-device partner interaction during meetings (partners participate verbally, organizer is sole operator)
- Real-time WebSocket features (not needed for bi-annual meetings with single operator)
- Video conferencing or screen sharing integration
- Partner self-scheduling of meetings
- Automated agenda item content (e.g., AI-generated analytics summaries)

---

## Functional Requirements

### FR1: Meeting Management

**FR1.1** The system shall allow organizers to create partner meetings with date, time, and location.

**FR1.2** The system shall allow organizers to select which active agenda item types are included in a meeting from the following types: Greeting/Attendance, Analytics Review, Audit Sign-off, Budget Approval, Topic Discussion, Hot Topics.

**FR1.3** The system shall allow organizers to add custom passive agenda items (title + description) for ad-hoc discussion topics.

**FR1.4** The system shall allow organizers to reorder agenda items via drag-and-drop.

**FR1.5** The system shall send ICS calendar invites to all partners when a meeting is saved.

**FR1.6** The system shall maintain meeting status lifecycle: DRAFT → SCHEDULED → IN_PROGRESS → COMPLETED.

**FR1.7** The system shall allow organizers to configure an auditor email address; the auditor receives an ICS invite only when the Audit Sign-off item is on the agenda.

### FR2: RSVP & Attendance

**FR2.1** The system shall allow partners to confirm or decline meeting attendance via the partner portal.

**FR2.2** The system shall display an RSVP dashboard showing confirmed / declined / pending counts for organizers.

**FR2.3** The system shall allow organizers to toggle partner presence (present/absent) during the meeting via the Who Is Who gallery.

**FR2.4** The system shall allow organizers to record stand-in delegates for absent partner companies (free-text name, delegated by which partner).

**FR2.5** The system shall display a "New" badge for first-year partner companies in the gallery.

### FR3: Presenter Mode

**FR3.1** The system shall provide a full-screen Presenter Mode with dark theme optimized for projector display (min 1280px width).

**FR3.2** The system shall display an agenda sidebar showing all items with their status: upcoming, active, completed, or skipped.

**FR3.3** The system shall allow linear navigation through agenda items via Next/Previous buttons and keyboard shortcuts (→/←/1-9).

**FR3.4** The system shall allow the organizer to jump to any agenda item via the sidebar (non-destructive).

**FR3.5** The system shall display a persistent note panel at the bottom of every agenda item, with auto-save.

**FR3.6** The system shall auto-save all organizer actions within 2 seconds of input.

**FR3.7** The system shall display an auto-save indicator ("Saved ✓") that fades after 2 seconds.

**FR3.8** The system shall support keyboard shortcuts: N (focus notes), V (start vote), Esc (exit with confirmation).

### FR4: Document Upload & Viewing

**FR4.1** The system shall allow organizers to upload PDF and spreadsheet documents to agenda items (audit report, budget document).

**FR4.2** The system shall display uploaded documents in an embedded viewer within the agenda item during Presenter Mode.

**FR4.3** The system shall permanently attach uploaded documents to the meeting record.

**FR4.4** The system shall support documents up to 20MB using the existing S3 presigned URL upload pattern.

### FR5: Voting

**FR5.1** The system shall allow organizers to start a vote on any votable agenda item (audit sign-off, budget approval).

**FR5.2** The system shall display a vote panel listing all present partners with approve/decline buttons.

**FR5.3** The system shall allow organizers to cast votes on behalf of each present partner (one click per partner).

**FR5.4** The system shall allow organizers to cast votes on behalf of stand-in delegates with clear attribution (company, stand-in name, delegating partner).

**FR5.5** The system shall allow vote changes before the vote is completed (reversible).

**FR5.6** The system shall display vote results with a visual banner upon completion (e.g., "Approved — 8/8").

**FR5.7** The system shall record vote results with per-partner attribution in the meeting minutes.

### FR6: Meeting Minutes

**FR6.1** The system shall create a PartnerMeetingMinutes entity in DRAFT status when Presenter Mode starts.

**FR6.2** The system shall auto-populate the minutes attendance section from the Who Is Who presence data (present, absent, stand-ins).

**FR6.3** The system shall auto-populate the minutes with vote results, uploaded documents, analytics snapshots, and organizer notes for each agenda item.

**FR6.4** The system shall allow organizers to review and edit the auto-assembled minutes after the meeting ends.

**FR6.5** The system shall allow organizers to add, edit, and remove action items during minutes review.

**FR6.6** The system shall allow organizers to publish minutes, changing status from DRAFT to PUBLISHED.

**FR6.7** The system shall send email notifications with a portal link to all invited partners (present + absent) upon minutes publication.

**FR6.8** The system shall display published minutes in a mobile-friendly format in the partner portal.

### FR7: Meeting Prep

**FR7.1** The system shall display a Meeting Prep Checklist showing readiness status for each enabled agenda item (document uploaded, RSVP count, topic voting status, open action items from previous meetings).

**FR7.2** The system shall display each checklist item with green/yellow/red status and an actionable link.

**FR7.3** The system shall soft-block "Start Presenter Mode" when critical items are missing (e.g., audit PDF not uploaded for an audit sign-off item).

### FR8: Post-Meeting Workflows

**FR8.1** The system shall trigger async fee acceptance requests to all partners after a budget approval vote that includes a fee decision.

**FR8.2** The system shall allow partners to accept or decline the new fee via the partner portal, with an optional comment.

**FR8.3** The system shall display a fee acceptance tracker for organizers showing accepted / pending / declined status per partner.

**FR8.4** The system shall send fee acceptance reminders at 7 and 14 days for non-responsive partners, then notify the organizer.

**FR8.5** The system shall send a feedback pulse survey (3 questions) to all invited partners 24 hours after minutes publication.

**FR8.6** The system shall aggregate feedback pulse results and display them to organizers with historical trends.

### FR9: Action Items

**FR9.1** The system shall allow organizers to create action items at any agenda item during Presenter Mode, with title, description, assigned owner, and optional deadline.

**FR9.2** The system shall include action items as a distinct section in the meeting minutes.

**FR9.3** The system shall surface open action items from previous meetings when creating a new meeting.

**FR9.4** The system shall allow organizers to mark action items as done, extend their deadline, or carry them forward during the "Review Open Actions" agenda item.

**FR9.5** The system shall display an urgency indicator on action items carried forward from 2+ meetings.

### FR10: Analytics Snapshot

**FR10.1** The system shall embed the existing analytics dashboard within the Analytics Review agenda item in Presenter Mode.

**FR10.2** The system shall allow organizers to capture a snapshot of current analytics data, creating an immutable record attached to the meeting minutes.

**FR10.3** The system shall store analytics snapshots as part of the meeting record for year-over-year browsing.

---

## Non-Functional Requirements

### Performance

| Requirement | Target |
|-------------|--------|
| Presenter Mode initial load | < 3 seconds (all content pre-loaded before entering) |
| Agenda item transition | < 300ms (no visible loading between items) |
| Auto-save latency | < 2 seconds from last input |
| Document viewer render | < 5 seconds for PDFs up to 20MB |
| Vote result display | < 500ms after completing vote |
| Minutes page load (partner portal) | < 2 seconds on 4G mobile |

### Security

| Requirement | Description |
|-------------|-------------|
| Authentication | Existing AWS Cognito integration; organizer role required for meeting management |
| Authorization | Only organizers can create/edit meetings, start Presenter Mode, cast votes, and publish minutes |
| Partner access | Partners can only view published minutes, respond to fee acceptance, and submit feedback for meetings they were invited to |
| Document storage | S3 with presigned URLs (existing pattern), documents not publicly accessible |
| Vote integrity | Vote records are immutable once completed; audit trail preserved |

### Reliability

| Requirement | Target |
|-------------|--------|
| Auto-save | Every organizer action persisted server-side; no data loss on browser crash |
| Draft minutes | Persist indefinitely until published or deleted |
| Presenter Mode exit | Graceful exit with draft saved; resume capability |
| Concurrent access | Single organizer per meeting (no multi-user editing needed) |

### Accessibility

| Requirement | Standard |
|-------------|----------|
| Presenter Mode | WCAG 2.1 AAA contrast (7:1+) on dark backgrounds; full keyboard operation |
| Partner Portal | WCAG 2.1 AA (4.5:1 contrast); 44x44px touch targets; semantic HTML + ARIA |
| No color-only info | All badges, vote results, and status indicators use color + text + icon |
| Screen reader | `aria-live="polite"` on vote results and auto-save; `role="navigation"` on sidebar |

### Scalability

| Requirement | Description |
|-------------|-------------|
| Meeting size | Support up to 30 partners per meeting (current max ~12, but allow growth) |
| Meeting history | Unlimited meeting records with browsable archive |
| Document storage | S3-backed, no practical limit on meeting document count |
| Concurrent meetings | Not needed — only one meeting runs at a time |

---

## Design System & Visual Direction

### Presenter Mode (Projector-Optimized Dark Theme)

- **Background:** zinc-950 (#09090B) with zinc-900 (#18181B) card surfaces
- **Primary text:** zinc-100 (#F4F4F5) — 15.8:1 AAA contrast
- **Accent:** blue-400 (#60A5FA) — 7.8:1 AA+ contrast
- **Partner accent:** orange-400 (#FB923C) for partner names and role badges
- **Present/Approved:** green-400 (#4ADE80) | **Absent/Declined:** red-400 (#F87171)
- **New partner badge:** purple-400 (#C084FC)
- **Typography:** Inter at projector scale (~1.5x standard): H1 56px, H2 44px, Body 24px, Min 18px
- **Layout:** Three-zone fixed layout — 280px sidebar + main content + 180px bottom note panel

### Key Components

| Component | Purpose |
|-----------|---------|
| AgendaSidebar | Vertical navigation with upcoming/active/completed/skipped states |
| VotePanel | Per-partner approve/decline with result banner animation |
| DocumentViewer | Embedded PDF/spreadsheet with dark chrome |
| NotePanel | Always-visible auto-save textarea attached to current agenda item |
| PartnerGallery | Grid of partner cards with presence toggle and badges |
| MeetingPrepChecklist | Green/yellow/red readiness items with action links |
| ActionItemCard | Title, owner, deadline, status, carry-forward indicator |
| MinutesDocument | Auto-assembled meeting record, editable before publishing |
| AgendaBuilder | Toggle switches + drag-and-drop reorder + custom item creation |
| FeeAcceptanceWidget | Accept/decline with amount, change delta, optional comment |
| FeedbackPulse | 3-question post-meeting survey |

---

## Constraints & Dependencies

### Platform Dependencies

- **Partner Coordination Service** (Epic 8) — existing service handles partner entities, roles, and portal authentication
- **Company/User Management Service** — provides partner company data, logos, user profiles
- **S3 presigned URL upload** — existing file upload pattern for document storage
- **AWS Cognito** — existing authentication for organizer and partner roles
- **Email service** (SES) — existing infrastructure for meeting invites and minutes distribution

### Technical Constraints

- **Single-operator model** — Presenter Mode runs on one device only; no multi-user real-time editing
- **No WebSocket requirement** — standard HTTP sufficient for single-operator meeting flow
- **Existing dark theme** — Presenter Mode extends the existing zinc-950 + blue-400 public website dark theme
- **PostgreSQL** — all meeting, minutes, vote, and action item data stored in the partner coordination service database
- **Flyway migrations** — all schema changes follow existing migration patterns

### Business Constraints

- **Bi-annual usage** — UI must be self-explanatory with zero learning curve; organizer and partners use this twice a year
- **Projector-first** — primary consumption is via projector in a meeting room; all Presenter Mode screens must be legible at 5 meters
- **Swiss context** — partner meetings are a governance function; financial sign-off records may be referenced by auditors in future years

---

## Glossary

| Term | Definition |
|------|------------|
| **Active Agenda Item** | A system-supported agenda item type with specific interaction (gallery, vote, document viewer). Toggleable per meeting. |
| **Passive Agenda Item** | A custom, free-form discussion placeholder added by the organizer (title + description + notes only). |
| **Presenter Mode** | Full-screen dark-themed interface for running the meeting from the organizer's laptop, projected to the room. |
| **Stand-In** | A non-system delegate attending on behalf of a partner company. Recorded by organizer with free-text name, no system login needed. |
| **Proxied Vote** | A vote cast by the organizer on behalf of a present partner or stand-in delegate. Full attribution recorded. |
| **Fee Acceptance** | Post-meeting workflow where each partner reviews and confirms the new annual fee decided during the budget approval agenda item. |
| **Feedback Pulse** | 3-question post-meeting survey sent 24 hours after minutes publication. |
| **Carry-Forward** | Action items from a previous meeting that are automatically surfaced when creating the next meeting. |
| **Analytics Snapshot** | An immutable capture of analytics data at the time of the meeting, attached to the minutes as a historical record. |
