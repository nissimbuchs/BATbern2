---
stepsCompleted: [1, 2, 3, 4]
inputDocuments: []
session_topic: 'Active partner meeting agenda - transforming 6 static agenda items into interactive system-supported features'
session_goals: 'Feature discovery for each agenda point, new functionality ideas, making partner meetings truly interactive'
selected_approach: 'ai-recommended'
techniques_used: ['morphological-analysis', 'role-playing', 'cross-pollination']
ideas_generated: 35
session_active: false
workflow_completed: true
context_file: '_bmad/bmm/data/project-context-template.md'
---

# Brainstorming Session Results

**Facilitator:** Nissim
**Date:** 2026-04-04

## Session Overview

**Topic:** Active Partner Meeting Agenda — transforming 6 static agenda items into interactive, system-supported features within the BATbern event management platform.

**Goals:**
- Make each of the 6 agenda items system-supported (not just text in an ICS invite)
- Identify new functionality that enhances each agenda point
- Explore how partner engagement before, during, and after the meeting can be improved

### Context Guidance

_Project context loaded from BATbern project template. Focus areas include user problems & pain points, feature ideas, technical approaches, UX patterns, business model, and success metrics. Results feed into Product Briefs, PRDs, or Technical Specs._

### Session Setup

**Current Agenda Items (Static):**
1. Greet all partners present (partners = users with role PARTNER)
2. Analytics on past years events, attendances, and attendee feedback
3. Show costs of past years + auditor's revision PDF + get sign-off for books
4. Show last year's costs + present year's budget + new fee per partner + get sign-off
5. Discuss current year's topics (topic voting + blob game for topic selection)
6. Discuss other current hot architecture topics partners are interested in

**Existing Platform Capabilities:**
- Partner roles & auth (Epic 8 complete)
- Attendance analytics + XLSX export
- Topic voting system
- ICS meeting invites
- Partner portal with login

## Technique Selection

**Approach:** AI-Recommended Techniques
**Analysis Context:** Active partner meeting agenda with focus on feature discovery and interactive engagement

**Recommended Techniques:**
- **Morphological Analysis:** Systematically map 6 agenda items × interaction dimensions to reveal every feature opportunity
- **Role Playing:** Walk through the agenda from Partner, Organizer, Auditor, and Absent Partner perspectives
- **Cross-Pollination:** Borrow proven patterns from conference platforms, workshop tools, and retrospective tools

## Technique Execution Results

### Phase 1: Morphological Analysis

**Matrix:** 6 agenda items × 5 interaction dimensions (PRE / LIVE / POST / ARTIFACT / NOTIFICATION)

#### A1 — Partner Greeting / Attendance

**[A1-PRE #1]**: RSVP Attendance Dashboard
_Concept_: Partners confirm attendance via the portal ahead of the meeting. Organizers see a live RSVP dashboard showing confirmed / declined / pending, with a headcount forecast. Partners can also see who else is coming.
_Novelty_: Turns passive "who showed up" into proactive planning — organizers can follow up on non-responders.

**[A1-PRE #2]**: Partner Profile Cards
_Concept_: Each confirmed partner gets a visible profile card (photo, company, role, years as partner). Especially valuable when new partners join — everyone can see at a glance who's new.
_Novelty_: Removes the awkward "who are you?" moment. New partners feel welcomed.

**[A1-LIVE #3]**: Who Is Who — Partner Gallery View
_Concept_: Clicking the "Greeting" agenda item opens a gallery grid of all partner companies, each showing the company logo, partner delegate name, photo, and role — all from existing User entities. Present partners get a green "Present" badge; absent ones greyed. Organizer toggles presence.
_Novelty_: Zero ceremony — no check-in ritual, no roll call. One glance, everyone knows who's in the room.

**[CROSS-CUTTING #4]**: Auto-Generated Meeting Minutes
_Concept_: The system maintains a PartnerMeetingMinutes entity that builds itself as the organizer walks through the active agenda. Each agenda item automatically contributes its section — attendance, snapshots, decisions, action items. Organizer can add free-text notes at each step. Final minutes exportable as PDF.
_Novelty_: Eliminates "someone takes notes." The system already knows what was shown, who voted, what was decided.

**[A1-POST #5]**: Attendance Section in Minutes
_Concept_: The Who Is Who presence data automatically populates the "Attendees" section of the meeting minutes — present partners by name and company, absent partners noted.
_Novelty_: First section of minutes writes itself.

#### A2 — Analytics Review

**[A2-PRE #6]**: Analytics Deep-Link in Meeting Invite
_Concept_: The ICS meeting invite includes a direct link to the analytics page. Zero development cost — just smarter invite content.
_Novelty_: Turns the invite into a preparation tool.

**[A2-LIVE #7]**: Analytics Snapshot for Minutes
_Concept_: When the organizer presents the analytics agenda item, the system captures a snapshot of current analytics data — key metrics, graphs — and attaches it to the meeting minutes as an immutable historical record.
_Novelty_: The analytics page can evolve, but the minutes always reflect what was presented that day.

**[A2-POST #8]**: Historized Analytics Archive
_Concept_: Each partner meeting creates a dated analytics snapshot stored as part of the meeting record. Over years, builds a browsable archive.
_Novelty_: Routine presentations become institutional memory.

#### A3 — Financial Audit Sign-off

**[A3-LIVE #9]**: Audit Report Upload & Viewer
_Concept_: Organizer uploads the auditor's revision PDF to the agenda item. Clicking it opens an embedded PDF viewer. Document permanently attached to this meeting's record.
_Novelty_: No emailing PDFs around. The document lives in the system.

**[A3-LIVE #10]**: Simple Approve/Decline Vote
_Concept_: After reviewing the PDF, organizer triggers a vote. Each present partner: Approve / Decline. System tallies and logs: "Financial report 2025 approved — 8 of 8 present partners approved."
_Novelty_: Replaces a verbal nod with a traceable record. One click per partner.

**[A3-POST #11]**: Audit Section in Meeting Minutes
_Concept_: Minutes get: uploaded PDF as attachment, vote result, and any discussion notes. PDF historized permanently.
_Novelty_: Complete audit trail years later.

#### A4 — Budget & Fee Approval

**[A4-LIVE #12]**: Budget Spreadsheet Upload & Viewer
_Concept_: Same upload/viewer pattern as audit PDF — organizer uploads budget document, embedded viewer shows it during the meeting.
_Novelty_: Reuses the same component. Consistent UX.

**[A4-LIVE #13]**: Live Vote on Budget Direction
_Concept_: Simple approve/decline vote for "Do we agree with the budget direction and proposed new fee?" Captures room sentiment. Minutes record result.
_Novelty_: Softer vote — directional agreement, not binding commitment.

**[A4-POST #14]**: Async Fee Acceptance in Partner Portal
_Concept_: After the meeting, system sets the new partner fee and each partner gets a notification to review and confirm in their portal. Deadline reminder if no response. Partners who were absent can also participate.
_Novelty_: Meeting creates the decision, portal operationalizes it. 100% partner coverage.

**[A4-POST #15]**: Fee Acceptance Tracker
_Concept_: Organizer dashboard: which partners accepted, pending, declined. Feeds into invoicing pipeline.
_Novelty_: Closes the loop between meeting decision and operational execution.

#### A5 — Topic Discussion & Voting

**[A5-PRE #16]**: Topic Voting Deep-Link in Meeting Invite
_Concept_: ICS invite includes direct link to topic selection/voting page. Zero new development.
_Novelty_: Same pattern as A2. Smart invite content.

**[A5-POST #17]**: Topic Discussion Results in Minutes
_Concept_: Minutes capture which topics discussed, blob game results (winners, scores), decisions and action items per topic.
_Novelty_: Blob game results historized — see what the community cared about each year.

#### A6 — Hot Architecture Topics

**[A6 #18]**: Open Discussion — Future Enhancement
_Concept_: Left as free-form. May merge with A5 topic voting. Organizer adds free-text notes to minutes.
_Novelty_: Intentionally unstructured. Not everything needs a feature.

### Phase 2: Role Playing

#### Perspective: The New Partner

**[ROLE-NEW #19]**: New Partner Onboarding Tour
_Concept_: First-login guided tour: "Welcome to BATbern! Here's how the partner platform works." Step-by-step walkthrough of analytics, topic voting, partner directory, meeting agenda.
_Novelty_: Turns overwhelming first login into confident "I know what's going on."

**[ROLE-NEW #20]**: New Partner Context Package
_Concept_: System surfaces key context: current fee, last year's analytics, organizers, other partner companies. Auto-assembled "partner briefing pack."
_Novelty_: No organizer prepares a welcome packet manually.

**[ROLE-NEW #21]**: "New Partner" Badge in Who Is Who
_Concept_: New partners (first year) get a subtle "New" badge. Signals to veterans to be welcoming.
_Novelty_: Tiny UI touch, huge social impact.

#### Perspective: The Absent Partner

**[ROLE-ABSENT #22]**: Post-Meeting Minutes Access
_Concept_: All partners receive notification with link to published minutes. Absent partners review everything — snapshots, PDFs, votes, notes.
_Novelty_: No partner ever out of the loop.

**[ROLE-ABSENT #23]**: Async Fee Vote for Absent Partners
_Concept_: Absent partners still receive fee acceptance request post-meeting. Same workflow, without having been in the room.
_Novelty_: Decouples fee decision from physical presence.

**[ROLE-ABSENT #24]**: Stand-In Delegation Record
_Concept_: Organizer records a stand-in: "Company Y represented by [free-text name], delegated by [partner name]." No system login needed. Who Is Who shows "Represented by [name]" badge.
_Novelty_: Handles non-system-users attending. Governance trail preserved.

**[ROLE-ABSENT #25]**: Stand-In Vote Attribution
_Concept_: Organizer casts votes on behalf of stand-in's partner company. Minutes log: "Company Y — approved (represented by [stand-in], delegated by [partner])."
_Novelty_: Clear attribution chain for proxy voting.

#### Perspective: The Organizer

**[ROLE-ORG #26]**: Agenda Builder — Select Active Items
_Concept_: When creating a partner meeting, organizer toggles which active agenda item types are relevant. Items can be reordered via drag-and-drop. Unchecked items don't appear.
_Novelty_: Every meeting tailored. Annual meeting has all items; mid-year check-in might only have Analytics + Topics.

**[ROLE-ORG #27]**: Custom Passive Agenda Items
_Concept_: Organizer adds free-form items (title + description) that appear in the agenda as discussion placeholders. Examples: "Venue proposal for 2027", "New sponsor introduction."
_Novelty_: Agenda never limited to what the system knows about.

**[ROLE-ORG #28]**: Meeting Prep Checklist
_Concept_: Based on enabled items, system generates checklist: "Audit PDF uploaded? ✅ / Budget spreadsheet uploaded? ✅ / RSVP count: 8/12 / Topic voting closes in 2 days ⚠️"
_Novelty_: No mental tracking. System knows what each item needs.

**[ROLE-ORG #29]**: Agenda Controller / Presenter Mode
_Concept_: Organizer has controller view: agenda sidebar, current item highlighted, click to advance. Each item shows its specific interaction (viewer, vote, gallery) in the main area. Quick note button always visible.
_Novelty_: Meeting driven like a presentation, but each "slide" is interactive.

**[ROLE-ORG #30]**: Per-Item Note Taking
_Concept_: At any agenda item, organizer types notes that attach to that item in the minutes. Free-text, quick capture — discussion points, decisions, action items.
_Novelty_: Notes always contextualized under the right agenda heading.

#### Perspective: The Auditor

**[ROLE-AUDITOR #31]**: Auditor Email Configuration
_Concept_: Admin page has auditor email field. When partner meeting includes Audit Sign-off item, auditor automatically receives ICS invite. If audit item not on agenda, no invite. Auditor sends PDF to organizer for upload.
_Novelty_: Zero system overhead for external participant. One config field, conditional logic.

### Phase 3: Cross-Pollination

**[CROSS-POLL #32]**: Post-Meeting Feedback Pulse
_Concept_: After minutes published, each invited partner receives short survey (3-4 questions): "Was the meeting productive? (1-5) / Topics we should have discussed? / Suggestions for next meeting?" Results visible to organizers, aggregated over time.
_Novelty_: The meeting improves itself. Track satisfaction trends over years.

**[CROSS-POLL #33]**: Manual Minutes Publication & Distribution
_Concept_: Organizer reviews auto-assembled minutes, edits if needed, clicks "Publish & Send." All invited participants (partners, auditor, stand-ins) receive email with minutes PDF or portal link.
_Novelty_: Organizer stays in control — heavy lifting done by system.

**[CROSS-POLL #34]**: Action Items with Owner Assignment
_Concept_: At any agenda item, organizer adds action items: title, description, assigned owner (organizer or partner), optional deadline. Distinct section in minutes.
_Novelty_: Decisions don't evaporate. Every action has a name next to it.

**[CROSS-POLL #35]**: Action Item Carry-Forward
_Concept_: When creating next partner meeting, system surfaces open action items from previous meetings. Organizer adds "Review Open Actions" to agenda. Each reviewed — mark done, update, or carry forward.
_Novelty_: Nothing falls through the cracks. System remembers what humans forget.

## Idea Organization and Prioritization

### Thematic Organization

| Theme | Ideas | Description |
|-------|-------|-------------|
| 1. Configurable Meeting Agenda Engine | #26, #27, #28, #29, #30 | The backbone — agenda builder, presenter mode, notes |
| 2. Meeting Minutes as Living Document | #4, #5, #7, #8, #11, #33 | Auto-assembled, historized, manually published |
| 3. Partner Interaction Components | #1, #2, #3, #9, #10, #12, #13 | RSVP, gallery, voting, document viewer — reusable |
| 4. Post-Meeting Workflows | #14, #15, #23, #32, #34, #35 | Async fee acceptance, feedback, action items |
| 5. New Partner & Stand-In Experience | #19, #20, #21, #24, #25 | Onboarding tour, context package, delegation |
| 6. Smart Meeting Invite | #6, #16, #31 | Deep-links, conditional auditor invite |

### Prioritization Results

**P1 — Must-Have (Themes 1, 2, 3, 4):**
Core active agenda system — agenda builder, minutes engine, interaction components, post-meeting workflows. 28 ideas forming the complete meeting lifecycle.

**P2 — Quick Wins (Theme 6 + reusable components):**
Deep-links in ICS invite (#6, #16), auditor auto-invite (#31), partner profile cards (#2). Low effort, immediate value.

**P3 — Nice-to-Have (Theme 5):**
New partner onboarding tour (#19, #20, #21), stand-in delegation (#24, #25). Phase later when partner count grows.

### Suggested Build Order (P1)

```
LAYER 1 — Foundation
├── #26  Agenda Builder (core entity + UI)
├── #9   Document Upload & Viewer component
├── #10  Approve/Decline Vote component
└── #1   RSVP Dashboard

LAYER 2 — Meeting Flow
├── #3   Who Is Who Gallery + presence badges
├── #27  Custom Passive Agenda Items
├── #29  Presenter/Controller Mode
├── #30  Per-Item Note Taking
└── #4   Meeting Minutes entity (auto-assembly)

LAYER 3 — Agenda Item Interactions
├── #5   Attendance → minutes
├── #7   Analytics snapshot → minutes
├── #11  Audit PDF + vote → minutes
├── #12  Budget doc + vote → minutes
├── #28  Meeting Prep Checklist
└── #34  Action Items with Owners

LAYER 4 — Post-Meeting & Lifecycle
├── #33  Publish & Distribute minutes
├── #35  Action Item Carry-Forward
├── #32  Feedback Pulse
├── #14  Async Fee Acceptance
├── #15  Fee Acceptance Tracker
└── #23  Absent Partner Fee Vote
```

### Key Architectural Decisions

1. **PartnerMeeting entity** — agenda definition (active + passive items, ordered), linked documents, vote results, minutes
2. **Reusable Vote component** — approve/decline for audit, budget, and future use
3. **Reusable Document Viewer** — upload + embedded view for PDF and spreadsheet
4. **Meeting Minutes** — assembled progressively from agenda item artifacts, editable, manually published
5. **Action Item entity** — title, owner, deadline, status, linked to meeting + agenda item, carry-forward logic

## Session Summary and Insights

**Key Achievements:**
- 35 ideas generated across 3 complementary techniques
- Clear system architecture emerged organically from the brainstorming
- 4 reusable components identified (agenda builder, vote, document viewer, minutes engine)
- Natural 4-layer build sequence with clean dependency chain
- Cross-cutting meeting minutes pattern discovered as the backbone artifact

**Breakthrough Moments:**
- Meeting minutes as a living document that builds itself from agenda item interactions
- Async fee acceptance extending the meeting beyond the room (ties to invoicing feature)
- Configurable agenda with active + passive items — not a hardcoded sequence
- Stand-in delegation for non-system-users — real governance need

**Creative Facilitation Narrative:**
Nissim brought strong pragmatism throughout the session — consistently filtering out over-engineered ideas in favor of simple, useful solutions. The morphological analysis grid provided systematic coverage, role playing uncovered edge cases (new partners, absent partners, stand-ins), and cross-pollination from retrospective tools yielded the action items carry-forward pattern. The session naturally converged on a system where the meeting itself generates its documentation as a byproduct of use.

**Session Techniques:** Morphological Analysis → Role Playing → Cross-Pollination
**Total Ideas:** 35
**Prioritized as Must-Have:** 28 (Themes 1-4)
**Quick Wins:** 4 (Theme 6 + profile cards)
**Nice-to-Have:** 5 (Theme 5)
