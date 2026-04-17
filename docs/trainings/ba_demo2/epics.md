---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
status: 'complete'
completedAt: '2026-04-05'
inputDocuments:
  - docs/trainings/ba_demo2/prd.md
  - docs/trainings/ba_demo2/architecture.md
  - docs/trainings/ba_demo2/ux-design-specification.md
---

# BATbern Active Partner Meeting Agenda - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for BATbern Active Partner Meeting Agenda, decomposing the requirements from the PRD, UX Design, and Architecture into implementable stories.

## Requirements Inventory

### Functional Requirements

**FR1: Meeting Management**
FR1.1: Create partner meetings with date, time, and location
FR1.2: Select which active agenda item types are included (Greeting, Analytics, Audit, Budget, Topics, Hot Topics)
FR1.3: Add custom passive agenda items (title + description) for ad-hoc discussion
FR1.4: Reorder agenda items via drag-and-drop
FR1.5: Send ICS calendar invites to all partners when meeting is saved
FR1.6: Maintain meeting status lifecycle: DRAFT → SCHEDULED → IN_PROGRESS → COMPLETED
FR1.7: Configure auditor email address; auditor receives ICS invite only when Audit Sign-off item is on agenda

**FR2: RSVP & Attendance**
FR2.1: Partners confirm or decline meeting attendance via partner portal
FR2.2: Display RSVP dashboard showing confirmed / declined / pending counts
FR2.3: Toggle partner presence (present/absent) during meeting via Who Is Who gallery
FR2.4: Record stand-in delegates for absent partner companies (free-text name, delegated by which partner)
FR2.5: Display "New" badge for first-year partner companies in gallery

**FR3: Presenter Mode**
FR3.1: Full-screen Presenter Mode with dark theme optimized for projector (min 1280px width)
FR3.2: Agenda sidebar showing all items with status: upcoming, active, completed, skipped
FR3.3: Linear navigation via Next/Previous buttons and keyboard shortcuts (→/←/1-9)
FR3.4: Jump to any agenda item via sidebar (non-destructive)
FR3.5: Persistent note panel at bottom of every agenda item with auto-save
FR3.6: Auto-save all organizer actions within 2 seconds of input
FR3.7: Auto-save indicator ("Saved ✓") that fades after 2 seconds
FR3.8: Keyboard shortcuts: N (focus notes), V (start vote), Esc (exit with confirmation)

**FR4: Document Upload & Viewing**
FR4.1: Upload PDF and spreadsheet documents to agenda items
FR4.2: Display uploaded documents in embedded viewer within Presenter Mode
FR4.3: Permanently attach uploaded documents to meeting record
FR4.4: Support documents up to 20MB using existing S3 presigned URL pattern

**FR5: Voting**
FR5.1: Start a vote on any votable agenda item (audit sign-off, budget approval)
FR5.2: Display vote panel listing all present partners with approve/decline buttons
FR5.3: Cast votes on behalf of each present partner (one click per partner)
FR5.4: Cast votes on behalf of stand-in delegates with clear attribution
FR5.5: Allow vote changes before vote is completed (reversible)
FR5.6: Display vote results with visual banner upon completion (e.g., "Approved — 8/8")
FR5.7: Record vote results with per-partner attribution in meeting minutes

**FR6: Meeting Minutes**
FR6.1: Create PartnerMeetingMinutes entity in DRAFT status when Presenter Mode starts
FR6.2: Auto-populate minutes attendance section from Who Is Who presence data
FR6.3: Auto-populate minutes with vote results, uploaded documents, analytics snapshots, and organizer notes
FR6.4: Allow organizers to review and edit auto-assembled minutes after meeting ends
FR6.5: Allow organizers to add, edit, and remove action items during minutes review
FR6.6: Allow organizers to publish minutes (DRAFT → PUBLISHED)
FR6.7: Send email notifications with portal link to all partners upon minutes publication
FR6.8: Display published minutes in mobile-friendly format in partner portal

**FR7: Meeting Prep**
FR7.1: Display Meeting Prep Checklist showing readiness status per enabled agenda item
FR7.2: Display each checklist item with green/yellow/red status and actionable link
FR7.3: Soft-block "Start Presenter Mode" when critical items are missing

**FR8: Post-Meeting Workflows**
FR8.1: Trigger async fee acceptance requests to all partners after budget approval vote with fee decision
FR8.2: Allow partners to accept or decline new fee via partner portal with optional comment
FR8.3: Display fee acceptance tracker for organizers (accepted / pending / declined per partner)
FR8.4: Send fee acceptance reminders at 7 and 14 days for non-responsive partners, then notify organizer
FR8.5: Send feedback pulse survey (3 questions) to all partners 24 hours after minutes publication
FR8.6: Aggregate feedback pulse results and display to organizers with historical trends

**FR9: Action Items**
FR9.1: Create action items at any agenda item with title, description, assigned owner, optional deadline
FR9.2: Include action items as distinct section in meeting minutes
FR9.3: Surface open action items from previous meetings when creating a new meeting
FR9.4: Mark action items as done, extend deadline, or carry forward during review
FR9.5: Display urgency indicator on action items carried forward from 2+ meetings

**FR10: Analytics Snapshot**
FR10.1: Embed existing analytics dashboard within Analytics Review agenda item in Presenter Mode
FR10.2: Capture snapshot of current analytics data as immutable record attached to minutes
FR10.3: Store analytics snapshots as part of meeting record for year-over-year browsing

### NonFunctional Requirements

NFR1: Presenter Mode initial load < 3 seconds (all content pre-loaded before entering)
NFR2: Agenda item transition < 300ms (no visible loading between items)
NFR3: Auto-save latency < 2 seconds from last input
NFR4: Document viewer render < 5 seconds for PDFs up to 20MB
NFR5: Vote result display < 500ms after completing vote
NFR6: Minutes page load < 2 seconds on 4G mobile
NFR7: Existing AWS Cognito authentication; organizer role required for meeting management
NFR8: Partners can only view published minutes, respond to fee acceptance, and submit feedback for meetings they were invited to
NFR9: Document storage via S3 with presigned URLs; documents not publicly accessible
NFR10: Vote records immutable once completed; audit trail preserved
NFR11: Auto-save — every organizer action persisted server-side; no data loss on browser crash
NFR12: Presenter Mode exit — graceful exit with draft saved; resume capability
NFR13: Single organizer per meeting (no multi-user editing needed)
NFR14: WCAG 2.1 AAA contrast (7:1+) on dark backgrounds for Presenter Mode
NFR15: WCAG 2.1 AA (4.5:1 contrast); 44x44px touch targets for partner portal
NFR16: No color-only information — all badges, vote results, status indicators use color + text + icon
NFR17: Screen reader support — aria-live on vote results and auto-save; role="navigation" on sidebar
NFR18: Support up to 30 partners per meeting
NFR19: Unlimited meeting records with browsable archive
NFR20: No offline support needed

### Additional Requirements

**From Architecture:**
- Brownfield extension of Partner Coordination Service — no new microservice
- 11 new entities following ADR-003 (meaningful IDs), ADR-004 (no user field duplication), ADR-006 (OpenAPI contract-first)
- meetingCode as meaningful identifier (pattern: PM-{YYYY}-{Spring|Autumn})
- All new database tables prefixed with `partner_meeting_*`
- Minutes content stored as JSONB — structured sections for auto-assembly
- Query-based minutes assembly (not event-sourced)
- Dedicated PresenterLayout component (not modal/overlay)
- Zustand store for presenter navigation state
- useAutoSave hook with debounced TanStack Query mutation
- Spring @Scheduled for fee reminders and feedback pulse triggers
- No starter template — extends existing codebase directly

**From UX Design:**
- Projector-optimized dark theme: zinc-950 background, blue-400 accents, 56px H1 / 24px body
- Three-zone fixed layout: 280px sidebar + fluid main + 180px bottom notes
- Partner accent color: orange-400 for names and role badges
- Present/Approved: green-400 | Absent/Declined: red-400 | New partner: purple-400
- Extend existing BATbern design system (MUI + Tailwind)
- New presenterTheme variant for dark/large typography
- 8 new reusable components (AgendaSidebar, VotePanel, DocumentViewer, NotePanel, PartnerGallery, MeetingPrepChecklist, ActionItemCard, MinutesPreview)
- Animated vote result reveal for delight moment
- All interactions must feel like "walking through a presentation" — linear, predictable, no page loads

### FR Coverage Map

| FR | Epic | Description |
|----|------|-------------|
| FR1.1–FR1.7 | 8.1 | Meeting CRUD, agenda config, ICS invites, status lifecycle |
| FR2.1–FR2.2 | 8.1 | RSVP portal + dashboard |
| FR2.3–FR2.5 | 8.2 | Live attendance toggle, stand-ins, "New" badge |
| FR3.1–FR3.8 | 8.2 | Presenter Mode layout, navigation, auto-save |
| FR4.1–FR4.4 | 8.2 | Document upload & embedded viewer |
| FR5.1–FR5.7 | 8.2 | Voting with proxied attribution |
| FR6.1–FR6.8 | 8.3 | Minutes auto-assembly, review, publish, distribution |
| FR7.1–FR7.3 | 8.1 | Meeting prep checklist |
| FR8.1–FR8.6 | 8.4 | Fee acceptance, feedback pulse, tracking |
| FR9.1 | 8.2 | Create action items during meeting |
| FR9.2, FR9.4, FR9.5 | 8.3 | Action items in minutes, review/update, urgency |
| FR9.3, FR9.5 | 8.1 | Surface carried-forward items in prep dashboard |
| FR10.1–FR10.3 | 8.2 | Analytics embedding + snapshot capture |

## Epic List

### Epic 8.1: Meeting Creation & Agenda Configuration
Organizers can create partner meetings with a configurable agenda, send invitations, track RSVPs, and see a prep dashboard with carried-forward action items — the complete pre-meeting workflow.
**FRs covered:** FR1.1–FR1.7, FR2.1, FR2.2, FR7.1–FR7.3, FR9.3, FR9.5

### Epic 8.2: Presenter Mode & Live Meeting
Organizers run the meeting in a projector-optimized presenter mode: attendance, documents, analytics, voting, notes, and action item creation — all in one linear flow. Everything that happens in the room, captured as it happens.
**FRs covered:** FR2.3–FR2.5, FR3.1–FR3.8, FR4.1–FR4.4, FR5.1–FR5.7, FR9.1, FR10.1–FR10.3

### Epic 8.3: Meeting Minutes & Publication
Auto-assembled minutes with review, editing, action item management, and publication to all partners. The meeting documents itself — organizer reviews, publishes, everyone gets the record.
**FRs covered:** FR6.1–FR6.8, FR9.2, FR9.4, FR9.5

### Epic 8.4: Post-Meeting Workflows & Partner Engagement
Fee acceptance, feedback surveys, automated reminders, and organizer tracking dashboards. The meeting extends beyond the room — absent partners participate, fees get confirmed, feedback improves future meetings.
**FRs covered:** FR8.1–FR8.6

---

## Epic 8.1: Meeting Creation & Agenda Configuration

Organizers can create partner meetings with a configurable agenda, send invitations, track RSVPs, and see a prep dashboard with carried-forward action items — the complete pre-meeting workflow.

### Story 8.1.1: Create Partner Meeting with Basic Details

As an **organizer**,
I want to create a new partner meeting with date, time, and location,
So that I can begin configuring the next bi-annual partner meeting.

**Acceptance Criteria:**

**Given** I am logged in as an organizer on the meetings page
**When** I click "Create New Meeting" and fill in date, time, and location
**Then** a new PartnerMeeting entity is created in DRAFT status with a generated meetingCode (PM-{YYYY}-{Spring|Autumn})
**And** I am redirected to the meeting detail/prep page

**Given** a meeting already exists with the same meetingCode pattern for that period
**When** I try to create another meeting for the same period
**Then** the system prevents duplicate meeting codes and shows an appropriate error

### Story 8.1.2: Agenda Builder with Active & Passive Items

As an **organizer**,
I want to select which active agenda item types to include and add custom passive items,
So that I can tailor the meeting agenda to what's relevant this time.

**Acceptance Criteria:**

**Given** I am on the meeting detail page for a DRAFT meeting
**When** I toggle active agenda item types (Greeting, Analytics, Audit, Budget, Topics, Hot Topics)
**Then** only selected types appear in the agenda list
**And** unselected types are excluded from the meeting

**Given** the agenda has items
**When** I drag items to reorder them
**Then** the sort order updates and persists

**Given** I want an ad-hoc discussion topic
**When** I click "Add Custom Item" and enter a title and description
**Then** a passive agenda item is added to the list

### Story 8.1.3: Auditor Email Configuration & ICS Invites

As an **organizer**,
I want to configure the auditor's email and send ICS invites to all partners when the meeting is scheduled,
So that everyone has the meeting on their calendar and the auditor is invited only when relevant.

**Acceptance Criteria:**

**Given** the Audit Sign-off agenda item is enabled
**When** I enter an auditor email address
**Then** the auditor email is saved to the meeting

**Given** the meeting is in DRAFT status with a complete agenda
**When** I click "Schedule Meeting" (transition DRAFT → SCHEDULED)
**Then** ICS calendar invites are sent to all active partners
**And** if audit item is enabled and auditor email is set, the auditor receives an ICS invite
**And** the meeting status changes to SCHEDULED

**Given** the audit item is NOT on the agenda
**When** the meeting is scheduled
**Then** no invite is sent to the auditor email

### Story 8.1.4: Partner RSVP & Dashboard

As a **partner**,
I want to confirm or decline my attendance via the portal,
So that the organizer knows who to expect.

As an **organizer**,
I want to see an RSVP dashboard with confirmed/declined/pending counts,
So that I can follow up with non-responders.

**Acceptance Criteria:**

**Given** I am a partner and a meeting is SCHEDULED
**When** I visit the partner portal
**Then** I see the meeting with options to confirm or decline attendance

**Given** a partner confirms or declines
**When** the organizer views the meeting detail page
**Then** the RSVP dashboard shows updated counts (confirmed / declined / pending)

### Story 8.1.5: Meeting Prep Checklist & Action Item Carry-Forward

As an **organizer**,
I want to see a readiness checklist based on my enabled agenda items and any open action items from previous meetings,
So that I know exactly what's ready and what still needs attention before starting the meeting.

**Acceptance Criteria:**

**Given** I am on the meeting detail page for a SCHEDULED meeting
**When** I view the prep checklist
**Then** I see a green/yellow/red status for each enabled item:
- Audit PDF uploaded? (if audit item enabled)
- Budget document uploaded? (if budget item enabled)
- RSVP count
- Topic voting status (if topics item enabled)
- Open action items from previous meetings

**Given** critical items are missing (e.g., audit PDF not uploaded when audit item is enabled)
**When** I click "Start Presenter Mode"
**Then** the system shows a warning and soft-blocks entry (can override with confirmation)

**Given** open action items exist from a previous COMPLETED meeting
**When** a new meeting is created
**Then** those items are surfaced in the prep dashboard with urgency indicators (2+ carry-forwards highlighted)

---

## Epic 8.2: Presenter Mode & Live Meeting

Organizers run the meeting in a projector-optimized presenter mode: attendance, documents, analytics, voting, notes, and action item creation — all in one linear flow. Everything that happens in the room, captured as it happens.

### Story 8.2.1: Presenter Mode Layout & Navigation Shell

As an **organizer**,
I want to enter a full-screen presenter mode with a dark theme, agenda sidebar, and linear navigation,
So that I can project and drive the meeting from my laptop.

**Acceptance Criteria:**

**Given** I am on a SCHEDULED meeting's detail page
**When** I click "Start Presenter Mode"
**Then** the screen transitions to the PresenterLayout: zinc-950 dark theme, 280px agenda sidebar on the left, fluid main area, 180px note panel at the bottom
**And** the meeting status transitions to IN_PROGRESS
**And** presenterSessionStartedBy is set to my username

**Given** I am in Presenter Mode
**When** I click Next/Previous or use →/← keyboard shortcuts
**Then** the active agenda item advances/retreats and the sidebar updates (upcoming/active/completed states)

**Given** I am in Presenter Mode
**When** I press a number key (1-9)
**Then** the corresponding agenda item activates (non-destructive sidebar jump)

**Given** I am in Presenter Mode
**When** I press Esc
**Then** a confirmation dialog appears: "Exit Presenter Mode? Your progress is saved."
**And** if confirmed, I return to meeting detail (meeting stays IN_PROGRESS for re-entry)

**Given** the meeting is already IN_PROGRESS with presenterSessionStartedBy set
**When** another organizer tries to start Presenter Mode
**Then** they see "Meeting is being presented by [name]"

### Story 8.2.2: Who Is Who — Attendance & Stand-In Tracking

As an **organizer**,
I want to toggle partner presence, record stand-in delegates, and see "New" badges during the Greeting agenda item,
So that attendance is captured accurately with zero ceremony.

**Acceptance Criteria:**

**Given** the Greeting/Attendance agenda item is active in Presenter Mode
**When** I see the partner gallery grid
**Then** each partner company shows: logo, delegate name, photo (enriched via Company/User service)
**And** pre-meeting RSVP status is reflected as initial state

**Given** the gallery is displayed
**When** I click a partner card
**Then** their presence toggles (present → absent or absent → present) with a green "Present" badge or dimmed state
**And** the change auto-saves within 2 seconds

**Given** a partner company sent a delegate instead
**When** I click "Add Stand-in" on that company's card and enter a free-text name
**Then** the card shows "Represented by [name]" with a stand-in badge
**And** the delegating partner's name is recorded

**Given** a partner company is in their first year
**When** the gallery renders
**Then** a purple "New" badge appears on their card

### Story 8.2.3: Per-Item Note Taking with Auto-Save

As an **organizer**,
I want a persistent note panel at the bottom of every agenda item that auto-saves,
So that I can capture discussion points without worrying about losing them.

**Acceptance Criteria:**

**Given** I am on any agenda item in Presenter Mode
**When** I type in the note panel
**Then** the content auto-saves within 2 seconds of the last keystroke (debounced PATCH)
**And** a "Saved ✓" indicator appears and fades after 2 seconds

**Given** I press N on the keyboard
**When** I am in Presenter Mode
**Then** the note panel text area receives focus

**Given** I navigate to a different agenda item and back
**When** I return to an item
**Then** my previous notes are preserved

**Given** the browser crashes during the meeting
**When** I re-enter Presenter Mode
**Then** all previously auto-saved notes are intact

### Story 8.2.4: Document Upload & Embedded Viewer

As an **organizer**,
I want to upload PDFs and spreadsheets to agenda items and view them inline during the meeting,
So that audit reports and budget documents are presented and permanently attached to the record.

**Acceptance Criteria:**

**Given** I am on the meeting detail page (pre-meeting or during)
**When** I upload a PDF or spreadsheet (up to 20MB) to an agenda item
**Then** the file is uploaded via S3 presigned URL and attached to the agenda item permanently

**Given** a document is attached to the active agenda item in Presenter Mode
**When** the item renders
**Then** the document displays in an embedded viewer (browser-native PDF rendering)
**And** the viewer has dark chrome matching the presenter theme

**Given** the embedded viewer fails to render
**When** the fallback activates
**Then** a download link is shown instead

### Story 8.2.5: Proxied Voting with Attribution

As an **organizer**,
I want to conduct approve/decline votes on behalf of each present partner during audit and budget items,
So that formal decisions are recorded with per-partner attribution and a traceable governance trail.

**Acceptance Criteria:**

**Given** I am on a votable agenda item (Audit Sign-off or Budget Approval) in Presenter Mode
**When** I press V or click "Start Vote"
**Then** a vote panel appears listing all present partners with approve/decline buttons

**Given** the vote panel is open
**When** I click approve or decline for a partner
**Then** their vote is recorded with attribution: companyName, decision, voterType (PARTNER)

**Given** a stand-in is attending for a company
**When** I cast their vote
**Then** the record shows: companyName, decision, voterType (STAND_IN), standInName, delegatedBy

**Given** the vote is still in progress
**When** I change a partner's vote
**Then** the vote updates (reversible before completion)

**Given** all present partners have voted
**When** I click "Complete Vote"
**Then** a result banner animates: "{Document Title} — Approved (8/8)" or "Declined (3/8)"
**And** vote records become immutable
**And** the result auto-saves to the meeting record

### Story 8.2.6: Analytics Snapshot Capture

As an **organizer**,
I want to view the existing analytics dashboard during the Analytics Review item and capture an immutable snapshot,
So that what was presented is historized as part of the meeting record.

**Acceptance Criteria:**

**Given** the Analytics Review agenda item is active in Presenter Mode
**When** the item renders
**Then** the existing analytics dashboard embeds in the main area

**Given** the analytics dashboard is visible
**When** I click "Capture Snapshot"
**Then** the current analytics data is frozen as an immutable JSONB record attached to this meeting
**And** a confirmation message appears: "Analytics snapshot captured ✓"

### Story 8.2.7: Action Item Creation During Meeting

As an **organizer**,
I want to create action items at any agenda point with an assigned owner and deadline,
So that decisions and follow-ups are captured in context as they arise.

**Acceptance Criteria:**

**Given** I am on any agenda item in Presenter Mode
**When** I click "Add Action Item" (or use a quick-add button)
**Then** a form appears with: title, description (optional), assigned owner (dropdown of partners + organizer usernames), optional deadline

**Given** I fill in the action item form and submit
**When** the item is saved
**Then** it is linked to the current meeting and agenda item
**And** it appears in the action items list for this meeting

### Story 8.2.8: End Meeting & Transition to Minutes

As an **organizer**,
I want to end the meeting from Presenter Mode and transition to the minutes review,
So that the live phase concludes cleanly and I can review what was captured.

**Acceptance Criteria:**

**Given** I am on the last agenda item in Presenter Mode
**When** I click "End Meeting"
**Then** a confirmation dialog appears: "End meeting and proceed to minutes review?"

**Given** I confirm ending the meeting
**When** the transition executes
**Then** the meeting status changes to COMPLETED
**And** presenterSession fields are cleared
**And** I am navigated to the Minutes Review page

**Given** I skipped some agenda items during the meeting
**When** the meeting ends
**Then** skipped items are marked as "Skipped" in the meeting record

---

## Epic 8.3: Meeting Minutes & Publication

Auto-assembled minutes with review, editing, action item management, and publication to all partners. The meeting documents itself — organizer reviews, publishes, everyone gets the record.

### Story 8.3.1: Minutes Auto-Assembly from Meeting Data

As an **organizer**,
I want the system to auto-assemble meeting minutes from all the data captured during the meeting,
So that I see a near-complete document without manually compiling anything.

**Acceptance Criteria:**

**Given** the meeting status is COMPLETED and I navigate to the Minutes Review page
**When** the page loads
**Then** the system assembles minutes by querying all meeting entities into a structured JSONB document containing:
- Attendance section (present, absent, stand-ins — from MeetingAttendance)
- A section per agenda item (sorted by sortOrder) with item-specific content:
  - Vote results and per-partner records (for votable items)
  - Uploaded documents as attachments
  - Analytics snapshot data (for analytics item)
  - Organizer notes
  - Custom/passive agenda items with their title, description, and organizer notes
- Action items section (all items created during the meeting)
**And** a MeetingMinutes entity is created in DRAFT status

**Given** the meeting had custom passive agenda items (e.g., "Venue proposal for 2027")
**When** the minutes are assembled
**Then** each custom item appears as its own section with the item title as the heading and the organizer's notes as the content

**Given** the minutes have been assembled
**When** I view the document
**Then** it renders as a clean, structured document with section headers per agenda item

### Story 8.3.2: Minutes Review, Editing & Action Item Management

As an **organizer**,
I want to review and edit the auto-assembled minutes and manage action items before publishing,
So that I can add clarifications, fix typos, and ensure action items are complete.

**Acceptance Criteria:**

**Given** I am on the Minutes Review page with DRAFT minutes
**When** I click on any section
**Then** I can edit the text content (notes, additional context)

**Given** I am reviewing minutes
**When** I view the action items section
**Then** I see all action items from this meeting with title, owner, deadline, status
**And** I can add new action items I forgot during the meeting
**And** I can edit or remove existing action items

**Given** action items were carried forward from previous meetings
**When** they appear in the action items section
**Then** each shows its carry-forward count and urgency indicator (highlighted if 2+ carry-overs)
**And** I can mark them as done, extend deadline, or carry forward

**Given** I make edits to the minutes
**When** I save
**Then** the JSONB content updates and persists in DRAFT status

### Story 8.3.3: Publish Minutes & Distribute to Partners

As an **organizer**,
I want to publish the minutes and send them to all partners,
So that everyone (present and absent) has the complete meeting record.

**Acceptance Criteria:**

**Given** I am on the Minutes Review page with finalized DRAFT minutes
**When** I click "Publish & Send"
**Then** a confirmation dialog appears: "Send minutes to all [N] partners?"

**Given** I confirm publication
**When** the publish executes
**Then** the minutes status changes from DRAFT to PUBLISHED (immutable after this)
**And** an email notification with a portal link is sent to all invited partners (present + absent)

**Given** minutes are PUBLISHED
**When** I try to edit them
**Then** editing is disabled — published minutes are the permanent record

### Story 8.3.4: Partner Minutes Viewing (Mobile-Friendly)

As a **partner**,
I want to view published meeting minutes in a mobile-friendly format in the partner portal,
So that I can review everything that was discussed and decided, even from my phone.

**Acceptance Criteria:**

**Given** I am a partner who was invited to a meeting
**When** I click the portal link from the minutes notification email
**Then** I see the published minutes with collapsible sections per agenda item

**Given** I am viewing minutes on a mobile device (iPhone Safari, Android Chrome)
**When** the page renders
**Then** the layout is responsive with readable text, touch-friendly collapsible sections, and downloadable document attachments

**Given** I am a partner who was NOT invited to this meeting
**When** I try to access the minutes
**Then** I receive a 403 — access denied

---

## Epic 8.4: Post-Meeting Workflows & Partner Engagement

Fee acceptance, feedback surveys, automated reminders, and organizer tracking dashboards. The meeting extends beyond the room — absent partners participate, fees get confirmed, feedback improves future meetings.

### Story 8.4.1: Async Fee Acceptance for Partners

As a **partner**,
I want to review and accept or decline the new annual fee via the portal,
So that I can participate in the fee decision even if I wasn't in the room.

As an **organizer**,
I want fee acceptance requests to be triggered automatically after a budget vote with a fee decision,
So that I don't have to manually chase each partner.

**Acceptance Criteria:**

**Given** a budget approval vote was completed that includes a fee decision
**When** the meeting minutes are published
**Then** the system creates a FeeAcceptance record (PENDING) for every invited partner with the fee amount

**Given** I am a partner with a pending fee acceptance
**When** I visit the portal or click the email link
**Then** I see the fee acceptance widget showing: new fee amount, change from previous fee, and accept/decline buttons with an optional comment field

**Given** I click "Accept" or "Decline"
**When** the response is submitted
**Then** my status updates (ACCEPTED or DECLINED with timestamp and optional comment)

**Given** no budget approval vote occurred in this meeting
**When** minutes are published
**Then** no fee acceptance requests are triggered

### Story 8.4.2: Fee Acceptance Tracking & Reminders

As an **organizer**,
I want to see who has responded to the fee acceptance and have the system send automatic reminders,
So that I can ensure 100% partner response without manual follow-up.

**Acceptance Criteria:**

**Given** fee acceptance requests were sent
**When** I view the meeting detail or a fee acceptance tracker page
**Then** I see per-partner status: accepted / pending / declined, with timestamps and any comments

**Given** a partner has not responded after 7 days
**When** the daily scheduled task runs
**Then** a reminder email is sent to that partner

**Given** a partner has not responded after 14 days
**When** the daily scheduled task runs
**Then** a second reminder email is sent to the partner
**And** the organizer is notified that this partner has not responded

**Given** a partner declines the fee
**When** the organizer views the tracker
**Then** the declined status is visible with the partner's comment

### Story 8.4.3: Feedback Pulse Survey & Results

As a **partner**,
I want to receive a short post-meeting survey and share my feedback,
So that future meetings improve based on participant input.

As an **organizer**,
I want to see aggregated feedback with historical trends,
So that I can measure whether meetings are getting better.

**Acceptance Criteria:**

**Given** meeting minutes were published
**When** 24 hours have passed
**Then** the daily scheduled task sends a feedback pulse email to all invited partners with a portal link

**Given** I am a partner and click the feedback link
**When** the form loads
**Then** I see 3 questions: "Was the meeting productive? (1-5)" / "Topics we should have discussed?" (free text) / "Suggestions for next meeting?" (free text)

**Given** I submit the feedback
**When** the response is saved
**Then** my FeedbackPulse record is stored with my responses and submission timestamp

**Given** feedback responses have been collected
**When** the organizer views the feedback results page
**Then** they see aggregated scores (average productivity rating) and individual text responses
**And** historical trends across previous meetings (if prior feedback data exists)
