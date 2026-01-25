# Story 5.6: Unified Event Page - Wireframe

**Story**: Epic 5, Story 5.6 - Event & Speaker Page Consolidation
**Screen**: Unified Event Page with Tab-Based Navigation
**User Role**: ORGANIZER
**Status**: ✅ **IMPLEMENTED IN MVP** (6 tabs, not all wireframed features)
**Related FR**: FR2 (Workflow Management), FR17 (Speaker Management), FR12 (Logistics), FR5 (Publishing)

**Actual Implementation**: 6 tabs (Overview, Speakers, Sessions, Tasks, Publishing, Settings) at `/organizer/events/:eventCode`

---

## Problem Statement

Current implementation has two separate pages (`EventDetail` and `EventDetailEdit`) that:
- Show similar read-only information despite naming
- Fragment the speaker management experience
- Confuse users about which page to use for editing
- Require navigation between pages to see full event picture

## Solution: Single Unified Event Page

Consolidate into one tabbed page at `/organizer/events/:eventCode` that provides:
- All event information in logical tab groupings
- Inline editing where appropriate
- Unified speaker management experience
- Clear information architecture

---

## Page Structure Overview

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Events                           BATbern #54: Spring Conference 2025              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  [Overview] [Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]           │
│  ─────────────────────────────────────────────────────────────────────────────────  │
│                                                                                      │
│  ┌────────────────────────────────────────────────────────────────────────────────┐ │
│  │                                                                                 │ │
│  │                         TAB CONTENT AREA                                        │ │
│  │                                                                                 │ │
│  └────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Tab 1: Overview (Default Tab)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Events                           BATbern #54: Spring Conference 2025              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  [Overview] [Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]           │
│  ═════════                                                                           │
│                                                                                      │
│  ┌─── STATUS BAR ──────────────────────────────────────────────────────────────────┐│
│  │ [SPEAKER_IDENTIFICATION ●]  9-State Workflow  ████████░░░░░░░░░  [View Details →]││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│  Note: Actual implementation shows event workflow state (9 states), not step count
│                                                                                      │
│  ┌─── THEME IMAGE ─────────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  ╔══════════════════════════════════════════════════════════════════════════╗   ││
│  │  ║                                                                          ║   ││
│  │  ║                    [ Theme Image Banner: 1200x400 ]                      ║   ││
│  │  ║                                                                          ║   ││
│  │  ╚══════════════════════════════════════════════════════════════════════════╝   ││
│  │                                                                    [Change Image]││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─────────────────────────────────────┬───────────────────────────────────────────┐│
│  │ EVENT DETAILS                       │ KEY METRICS                               ││
│  ├─────────────────────────────────────┼───────────────────────────────────────────┤│
│  │                                     │                                           ││
│  │  Title                          ✎  │  📅 Event Date                            ││
│  │  Spring Conference 2025             │     March 15, 2025 • 09:00-18:00          ││
│  │                                     │                                           ││
│  │  Description                    ✎  │  📍 Venue                                 ││
│  │  A comprehensive conference         │     Kursaal Bern                          ││
│  │  covering advanced microservices    │     Kornhausstrasse 3, 3013 Bern          ││
│  │  architecture patterns...           │                                           ││
│  │                                     │  👥 Capacity                              ││
│  │  Event Type                         │     87/150 registered (58%)               ││
│  │  [Full Day ▼]                       │     ████████████░░░░░░░░                  ││
│  │                                     │                                           ││
│  │  Topic                              │  🎤 Speakers                              ││
│  │  Cloud-Native Architecture          │     8/12 confirmed                        ││
│  │  [Change Topic]                     │     ████████████████░░░░                  ││
│  │                                     │                                           ││
│  │                                     │  📋 Materials                             ││
│  │                                     │     2 pending, 6 complete                 ││
│  │                                     │                                           ││
│  │  [Edit Details]                     │  💰 Budget: CHF 15,000                    ││
│  │                                     │                                           ││
│  └─────────────────────────────────────┴───────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── QUICK ACTIONS ───────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  [📧 Send Notification]  [👁 Preview Public Page]  [📅 View Timeline]           ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Tab 2: Speakers (Unified Speaker Management)

This is the key consolidated tab combining:
- SpeakerStatusDashboard (kanban lanes)
- SpeakersSessionsTable (session slots)
- SpeakerOutreachDashboard (outreach tracking)

### Speakers Tab - Kanban View (Default)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Events                           BATbern #54: Spring Conference 2025              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  [Overview] [Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]           │
│             ═════════                                                                │
│                                                                                      │
│  ┌─── SPEAKER SUMMARY BAR ─────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Progress: 8/12 confirmed  ████████████████░░░░  67%     Acceptance Rate: 72%   ││
│  │                                                                                  ││
│  │  ✓ Threshold Met (min 8 speakers)                        [+ Add Speakers]       ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── VIEW TOGGLE ─────────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  View: [●Kanban] [○Table] [○Sessions]           Filter: [All Statuses ▼]        ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── KANBAN LANES (Drag & Drop) ──────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  IDENTIFIED        CONTACTED         READY            ACCEPTED        DECLINED  ││
│  │  ════════════      ════════════      ════════════     ════════════    ═════════ ││
│  │  ┌──────────┐      ┌──────────┐      ┌──────────┐     ┌──────────┐    ┌───────┐ ││
│  │  │[3]       │      │[4]       │      │[2]       │     │[8]       │    │[1]    │ ││
│  │  ├──────────┤      ├──────────┤      ├──────────┤     ├──────────┤    ├───────┤ ││
│  │  │┌────────┐│      │┌────────┐│      │┌────────┐│     │┌────────┐│    │┌─────┐│ ││
│  │  ││ JM     ││      ││ SW     ││      ││ PL     ││     ││ SM     ││    ││ TK  ││ ││
│  │  ││ John   ││      ││ Sarah  ││      ││ Peter  ││     ││ Dr.    ││    ││ Tom ││ ││
│  │  ││ Miller ││      ││ Wilson ││      ││ Lang   ││     ││ Sarah  ││    ││ Kim ││ ││
│  │  ││ ─────  ││      ││ ─────  ││      ││ ─────  ││     ││ Miller ││    │└─────┘│ ││
│  │  ││Acme Inc││      ││SwissRe ││      ││Google  ││     ││────────││    │       │ ││
│  │  ││        ││      ││        ││      ││        ││     ││Accentur││    │       │ ││
│  │  ││ ⚠ 5d   ││      ││ ● 2d   ││      ││ ● 1d   ││     ││ Slot 1 ││    │       │ ││
│  │  │└────────┘│      │└────────┘│      │└────────┘│     │└────────┘│    │       │ ││
│  │  │          │      │          │      │          │     │          │    │       │ ││
│  │  │┌────────┐│      │┌────────┐│      │┌────────┐│     │┌────────┐│    │       │ ││
│  │  ││ AW     ││      ││ MH     ││      ││        ││     ││ JW     ││    │       │ ││
│  │  ││ Anna   ││      ││ Mike   ││      ││        ││     ││ Prof.  ││    │       │ ││
│  │  ││ Weber  ││      ││ Harris ││      ││        ││     ││ James  ││    │       │ ││
│  │  │└────────┘│      │└────────┘│      │          │     ││ Wilson ││    │       │ ││
│  │  │          │      │          │      │          │     │└────────┘│    │       │ ││
│  │  │          │      │┌────────┐│      │          │     │          │    │       │ ││
│  │  │          │      ││ LT     ││      │          │     │ ... +6   │    │       │ ││
│  │  │          │      ││ Lisa   ││      │          │     │          │    │       │ ││
│  │  │          │      ││ Thompson│      │          │     │          │    │       │ ││
│  │  │          │      │└────────┘│      │          │     │          │    │       │ ││
│  │  └──────────┘      └──────────┘      └──────────┘     └──────────┘    └───────┘ ││
│  │                                                                                  ││
│  │  💡 Drag speakers between lanes to update status. Click card for details.       ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Speakers Tab - Table View

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Events                           BATbern #54: Spring Conference 2025              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  [Overview] [Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]           │
│             ═════════                                                                │
│                                                                                      │
│  ┌─── SPEAKER SUMMARY BAR ─────────────────────────────────────────────────────────┐│
│  │  Progress: 8/12 confirmed  ████████████████░░░░  67%     [+ Add Speakers]       ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── VIEW TOGGLE ─────────────────────────────────────────────────────────────────┐│
│  │  View: [○Kanban] [●Table] [○Sessions]     Search: [________________] 🔍         ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── SPEAKER TABLE ───────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ ││
│  │  │ Speaker          │ Company    │ Status      │ Assigned  │ Days │ Actions  │ ││
│  │  ├──────────────────┼────────────┼─────────────┼───────────┼──────┼──────────┤ ││
│  │  │ Dr. Sarah Miller │ Accenture  │ [ACCEPTED●] │ Sally O.  │  -   │ [···]    │ ││
│  │  │ Prof. J. Wilson  │ Uni Bern   │ [ACCEPTED●] │ Mark T.   │  -   │ [···]    │ ││
│  │  │ Anna Schmidt     │ SwissRe    │ [ACCEPTED●] │ Sally O.  │  -   │ [···]    │ ││
│  │  │ Peter Lang       │ Google     │ [READY    ○]│ Anna W.   │  1d  │ [···]    │ ││
│  │  │ Sarah Wilson     │ SwissRe    │ [CONTACTED○]│ Mark T.   │  2d  │ [···]    │ ││
│  │  │ Mike Harris      │ Microsoft  │ [CONTACTED○]│ Sally O.  │  3d  │ [···]    │ ││
│  │  │ Lisa Thompson    │ Amazon     │ [CONTACTED○]│ Anna W.   │  4d  │ [···]    │ ││
│  │  │ John Miller      │ Acme Inc   │ [IDENTIFIED]│ Mark T.   │  5d⚠│ [···]    │ ││
│  │  │ Anna Weber       │ SAP        │ [IDENTIFIED]│ -         │  6d⚠│ [···]    │ ││
│  │  │ Tom Kim          │ Oracle     │ [DECLINED ●]│ Sally O.  │  -   │ [···]    │ ││
│  │  └────────────────────────────────────────────────────────────────────────────┘ ││
│  │                                                                                  ││
│  │  Showing 10 of 18 speakers                                  [1] [2] [Next →]    ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── BULK ACTIONS ────────────────────────────────────────────────────────────────┐│
│  │  ☐ Select All    [Mark as Contacted] [Assign Organizer] [Send Reminder]         ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Speakers Tab - Sessions View

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Events                           BATbern #54: Spring Conference 2025              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  [Overview] [Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]           │
│             ═════════                                                                │
│                                                                                      │
│  ┌─── SPEAKER SUMMARY BAR ─────────────────────────────────────────────────────────┐│
│  │  Sessions Filled: 8/12  ████████████████░░░░  67%        [Auto-Assign Speakers] ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── VIEW TOGGLE ─────────────────────────────────────────────────────────────────┐│
│  │  View: [○Kanban] [○Table] [●Sessions]                    [View Full Agenda]     ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── SESSION SLOTS ───────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  MORNING SESSIONS                                                                ││
│  │  ─────────────────────────────────────────────────────────────────────────────  ││
│  │                                                                                  ││
│  │  ┌─ Slot 1 ─────────────────────────────────────────────────────────────────┐   ││
│  │  │ 09:00-10:00 │ Dr. Sarah Miller (Accenture)                      ✓ [···]  │   ││
│  │  │             │ "Microservices: From Theory to Practice"                   │   ││
│  │  │             │ Materials: ✓ Complete                                      │   ││
│  │  └──────────────────────────────────────────────────────────────────────────┘   ││
│  │                                                                                  ││
│  │  ┌─ Slot 2 ─────────────────────────────────────────────────────────────────┐   ││
│  │  │ 10:15-11:15 │ Prof. James Wilson (University of Bern)           ✓ [···]  │   ││
│  │  │             │ "Cloud Security Architecture"                              │   ││
│  │  │             │ Materials: ⚠ Pending (Due: Mar 8)                          │   ││
│  │  └──────────────────────────────────────────────────────────────────────────┘   ││
│  │                                                                                  ││
│  │  ┌─ Slot 3 ─────────────────────────────────────────────────────────────────┐   ││
│  │  │ 11:30-12:30 │ Anna Schmidt (SwissRe)                            ✓ [···]  │   ││
│  │  │             │ "Kubernetes in Production"                                 │   ││
│  │  │             │ Materials: ⚠ Pending (Due: Mar 8)                          │   ││
│  │  └──────────────────────────────────────────────────────────────────────────┘   ││
│  │                                                                                  ││
│  │  ─── LUNCH BREAK: 12:30-14:00 ───                                               ││
│  │                                                                                  ││
│  │  AFTERNOON SESSIONS                                                              ││
│  │  ─────────────────────────────────────────────────────────────────────────────  ││
│  │                                                                                  ││
│  │  ┌─ Slot 4 ─────────────────────────────────────────────────────────────────┐   ││
│  │  │ 14:00-15:00 │ [EMPTY - Click to Assign]                         ○ [···]  │   ││
│  │  │             │                                                            │   ││
│  │  │             │ [Assign from Pool ▼]                                       │   ││
│  │  └──────────────────────────────────────────────────────────────────────────┘   ││
│  │                                                                                  ││
│  │  ... (Slots 5-12 continue)                                                       ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Speaker Details Drawer (Slide-out Panel)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                                                         ┌──────────┐│
│  [Main Content Area - Dimmed]                                           │ SPEAKER  ││
│                                                                         │ DETAILS  ││
│                                                                         │          ││
│                                                                         │ ╳ Close  ││
│                                                                         ├──────────┤│
│                                                                         │          ││
│                                                                         │  ┌────┐  ││
│                                                                         │  │ SM │  ││
│                                                                         │  └────┘  ││
│                                                                         │          ││
│                                                                         │ Dr. Sarah││
│                                                                         │ Miller   ││
│                                                                         │          ││
│                                                                         │ Accenture││
│                                                                         │          ││
│                                                                         │ ──────── ││
│                                                                         │          ││
│                                                                         │ Status   ││
│                                                                         │ [ACCEPTED││
│                                                                         │  ●]      ││
│                                                                         │          ││
│                                                                         │ Assigned ││
│                                                                         │ Slot 1   ││
│                                                                         │ 09:00-   ││
│                                                                         │ 10:00    ││
│                                                                         │          ││
│                                                                         │ ──────── ││
│                                                                         │          ││
│                                                                         │ OUTREACH ││
│                                                                         │ HISTORY  ││
│                                                                         │          ││
│                                                                         │ Mar 1    ││
│                                                                         │ 📧 Email ││
│                                                                         │ by Sally ││
│                                                                         │ "Initial ││
│                                                                         │ invite"  ││
│                                                                         │          ││
│                                                                         │ Mar 3    ││
│                                                                         │ 📞 Phone ││
│                                                                         │ by Sally ││
│                                                                         │ "Follow- ││
│                                                                         │ up call" ││
│                                                                         │          ││
│                                                                         │ Mar 5    ││
│                                                                         │ ✓ Accept ││
│                                                                         │          ││
│                                                                         │ ──────── ││
│                                                                         │          ││
│                                                                         │ [Mark    ││
│                                                                         │ Contacted]││
│                                                                         │          ││
│                                                                         │ [Change  ││
│                                                                         │  Status] ││
│                                                                         │          ││
│                                                                         │ [Remove  ││
│                                                                         │ from Pool││
│                                                                         │  ]       ││
│                                                                         │          ││
│                                                                         └──────────┘│
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Add Speakers Panel (Inline Collapsible)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                      │
│  [+ Add Speakers] clicked - Panel expands:                                           │
│                                                                                      │
│  ┌─── ADD SPEAKERS TO POOL ─────────────────────────────────────────────── [╳] ────┐│
│  │                                                                                  ││
│  │  ┌─── QUICK ADD ────────────────────────────────────────────────────────────┐   ││
│  │  │                                                                           │   ││
│  │  │  Speaker Name: [_______________________]  Company: [___________________]  │   ││
│  │  │                                                                           │   ││
│  │  │  Expertise: [________________________________]  Assign to: [Sally O. ▼]  │   ││
│  │  │                                                                           │   ││
│  │  │  Notes: [______________________________________________________________]  │   ││
│  │  │                                                                           │   ││
│  │  │  [Add to Pool]                                                            │   ││
│  │  │                                                                           │   ││
│  │  └───────────────────────────────────────────────────────────────────────────┘   ││
│  │                                                                                  ││
│  │  ─── OR ───                                                                      ││
│  │                                                                                  ││
│  │  [📥 Import from CSV]     [🔍 Search Existing Speakers]    [📋 From Topic Pool]  ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Tab 3: Venue & Logistics

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Events                           BATbern #54: Spring Conference 2025              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  [Overview] [Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]           │
│                        ══════════════════                                            │
│                                                                                      │
│  ┌─── VENUE ───────────────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Kursaal Bern                                              [Change Venue]       ││
│  │  Kornhausstrasse 3, 3013 Bern                                                    ││
│  │                                                                                  ││
│  │  ┌───────────────────────────────────────────────────────────────────────────┐  ││
│  │  │ Capacity    │ Parking      │ Accessibility │ Booking Status              │  ││
│  │  ├─────────────┼──────────────┼───────────────┼─────────────────────────────┤  ││
│  │  │ 200 persons │ ✓ Available  │ ✓ Wheelchair  │ ✓ Confirmed (KB-2025-03-001)│  ││
│  │  └───────────────────────────────────────────────────────────────────────────┘  ││
│  │                                                                                  ││
│  │  Contact: Anna Schmidt (anna.schmidt@kursaal-bern.ch, +41 31 339 55 00)        ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── CATERING ────────────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Provider: [Select Caterer ▼]                              [+ Add New Caterer]  ││
│  │                                                                                  ││
│  │  Menu Configuration: Not configured                        [Configure Menu]     ││
│  │                                                                                  ││
│  │  Dietary Requirements (from registrations):                                      ││
│  │  • 5 Vegetarian  • 2 Vegan  • 3 Gluten-free  • 1 Lactose-free                  ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── SCHEDULE ────────────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  08:00    Registration & Coffee                                                  ││
│  │  09:00    Sessions Begin (Slots 1-3)                                            ││
│  │  12:30    Lunch Break                                                            ││
│  │  14:00    Sessions Continue (Slots 4-6)                                         ││
│  │  16:00    Coffee Break                                                           ││
│  │  16:30    Sessions Continue (Slots 7-9)                                         ││
│  │  18:00    Networking Apéro                                                       ││
│  │  19:00    Event Ends                                                             ││
│  │                                                                                  ││
│  │  [Edit Schedule]                                                                 ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Tab 4: Team

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Events                           BATbern #54: Spring Conference 2025              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  [Overview] [Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]           │
│                                             ════                                     │
│                                                                                      │
│  ┌─── TEAM ASSIGNMENTS ────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  ┌─────────────────────────────────────────────────────────────────────────┐    ││
│  │  │ Role               │ Assigned To                │ Actions               │    ││
│  │  ├────────────────────┼────────────────────────────┼───────────────────────┤    ││
│  │  │ Lead Organizer     │ Sally Organizer            │ [Change]              │    ││
│  │  │ Co-Organizer       │ Mark Thompson              │ [Change] [Remove]     │    ││
│  │  │ Co-Organizer       │ Anna Weber                 │ [Change] [Remove]     │    ││
│  │  │ Moderator          │ [Not assigned ▼]           │ [Assign]              │    ││
│  │  │ Content Reviewer   │ Dr. Peter Lang             │ [Change]              │    ││
│  │  └─────────────────────────────────────────────────────────────────────────┘    ││
│  │                                                                                  ││
│  │  [+ Add Team Member]                                                             ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── SPEAKER OUTREACH ASSIGNMENTS ────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Organizer           │ Assigned Speakers  │ Contacted  │ Pending                 ││
│  │  ────────────────────┼────────────────────┼────────────┼─────────               ││
│  │  Sally Organizer     │ 8                  │ 6          │ 2                       ││
│  │  Mark Thompson       │ 5                  │ 3          │ 2                       ││
│  │  Anna Weber          │ 5                  │ 4          │ 1                       ││
│  │                                                                                  ││
│  │  [Reassign Speakers]                                                             ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Tab 5: Publishing

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Events                           BATbern #54: Spring Conference 2025              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  [Overview] [Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]           │
│                                                    ══════════                        │
│                                                                                      │
│  ┌─── PUBLISHING STATUS ───────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Strategy: Progressive Publishing                           [Configure]         ││
│  │  Current Phase: Speakers Published                                               ││
│  │                                                                                  ││
│  │  ──────────────────────────────────────────────────────────────────────────────  ││
│  │                                                                                  ││
│  │  TIMELINE                                                                        ││
│  │                                                                                  ││
│  │  ✓ Jan 5   │ Topic Published        │ Immediate after topic selection           ││
│  │  ✓ Feb 15  │ Speakers Published     │ 1 month before event                      ││
│  │  ○ Mar 1   │ Final Agenda           │ 2 weeks before event                      ││
│  │  ○ Mar 15  │ Event Day              │ Event takes place                         ││
│  │  ○ Mar 22  │ Post-Event Materials   │ 1 week after event                        ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── QUALITY CHECKPOINTS ─────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  ✓ Abstract length validation (max 1000 chars)                                  ││
│  │  ✓ Lessons learned requirement met                                              ││
│  │  ⚠ All materials submitted (2 pending)                                          ││
│  │  ○ Moderator review complete                                                     ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── ACTIONS ─────────────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  [👁 Preview Public Page]   [📤 Republish Event]   [📧 Notify Attendees]        ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Tab 6: Settings

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Events                           BATbern #54: Spring Conference 2025              │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  [Overview] [Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]           │
│                                                                  ════════            │
│                                                                                      │
│  ┌─── EVENT SETTINGS ──────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Event Number: 54                                                                ││
│  │  Event Code: BAT2025-54                                                          ││
│  │  Created: January 5, 2025 by Sally Organizer                                    ││
│  │  Last Modified: March 10, 2025                                                   ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── NOTIFICATIONS ───────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Active Automations: 8                                                           ││
│  │                                                                                  ││
│  │  ● Speaker deadline reminders (3 days before)                       [Edit]      ││
│  │  ● Registration confirmation emails                                 [Edit]      ││
│  │  ● Final agenda distribution - Mar 1, 2025                          [Edit]      ││
│  │  ● Event day check-in reminders - Mar 15, 2025                     [Edit]      ││
│  │                                                                                  ││
│  │  [Manage All Notifications]                                                      ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
│  ┌─── DANGER ZONE ─────────────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  ⚠️ These actions are irreversible                                              ││
│  │                                                                                  ││
│  │  [Cancel Event]    - Cancels event and notifies all registrants                ││
│  │                                                                                  ││
│  │  [Delete Event]    - Permanently removes event (only if no registrations)       ││
│  │                                                                                  ││
│  └─────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Mobile Responsive Design

### Mobile - Tabs become Bottom Navigation

```
┌─────────────────────────────┐
│ ← Events          BATbern 54│
├─────────────────────────────┤
│                             │
│  SPEAKER_OUTREACH ●         │
│  Step 8/16  50%             │
│  [Advance Workflow →]       │
│                             │
├─────────────────────────────┤
│                             │
│  ┌───────────────────────┐  │
│  │   Theme Image Banner  │  │
│  └───────────────────────┘  │
│                             │
│  EVENT DETAILS              │
│  ────────────────           │
│  Title                      │
│  Spring Conference 2025     │
│  [Edit]                     │
│                             │
│  Date: Mar 15, 2025         │
│  Venue: Kursaal Bern        │
│  Capacity: 87/150           │
│                             │
│  KEY METRICS                │
│  ────────────────           │
│  🎤 8/12 speakers           │
│  📋 2 materials pending     │
│                             │
├─────────────────────────────┤
│ [🏠] [👥] [📍] [👤] [📤] [⚙]│
│ Over  Spkr Venue Team Pub  Set│
└─────────────────────────────┘
```

### Mobile - Speakers Tab (Kanban becomes Vertical List)

```
┌─────────────────────────────┐
│ ← Events          BATbern 54│
├─────────────────────────────┤
│                             │
│  SPEAKERS                   │
│  8/12 confirmed (67%)       │
│  [+ Add]                    │
│                             │
│  [Kanban▼] Filter: [All▼]   │
│                             │
├─────────────────────────────┤
│                             │
│  ═══ ACCEPTED (8) ═══       │
│                             │
│  ┌───────────────────────┐  │
│  │ SM  Dr. Sarah Miller  │  │
│  │     Accenture         │  │
│  │     Slot 1 • 09:00    │  │
│  └───────────────────────┘  │
│                             │
│  ┌───────────────────────┐  │
│  │ JW  Prof. J. Wilson   │  │
│  │     Uni Bern          │  │
│  │     Slot 2 • 10:15    │  │
│  └───────────────────────┘  │
│                             │
│  ... more cards             │
│                             │
│  ═══ CONTACTED (4) ═══      │
│                             │
│  ┌───────────────────────┐  │
│  │ SW  Sarah Wilson  2d  │  │
│  │     SwissRe           │  │
│  └───────────────────────┘  │
│                             │
├─────────────────────────────┤
│ [🏠] [👥] [📍] [👤] [📤] [⚙]│
└─────────────────────────────┘
```

---

## URL Structure

| Route | Tab | Description |
|-------|-----|-------------|
| `/organizer/events/:eventCode` | Overview | Default tab |
| `/organizer/events/:eventCode?tab=speakers` | Speakers | Speaker management |
| `/organizer/events/:eventCode?tab=speakers&view=kanban` | Speakers | Kanban view |
| `/organizer/events/:eventCode?tab=speakers&view=table` | Speakers | Table view |
| `/organizer/events/:eventCode?tab=speakers&view=sessions` | Speakers | Sessions view |
| `/organizer/events/:eventCode?tab=venue` | Venue | Venue & logistics |
| `/organizer/events/:eventCode?tab=team` | Team | Team assignments |
| `/organizer/events/:eventCode?tab=publishing` | Publishing | Publishing config |
| `/organizer/events/:eventCode?tab=settings` | Settings | Event settings |

---

## Deprecated Routes (Redirects)

| Old Route | Redirects To |
|-----------|--------------|
| `/organizer/events/:eventCode/edit` | `/organizer/events/:eventCode` |
| `/organizer/events/:eventCode/speakers/outreach` | `/organizer/events/:eventCode?tab=speakers` |

---

## Key Interactive Elements

### Speaker Card (Kanban)
- **Drag**: Move between status lanes
- **Click**: Open speaker details drawer
- **Badge**: Days since assignment (warning if >7 days)

### View Toggle
- **Kanban**: Status-based lanes with drag-drop
- **Table**: Full list with sorting/filtering/bulk actions
- **Sessions**: Slot-based view with assignment dropdowns

### Quick Actions
- **Mark Contacted**: Opens modal to log contact attempt
- **Change Status**: Dropdown to manually change status
- **Assign to Slot**: Dropdown to assign accepted speaker to session

---

## State Management

### URL State (Query Params)
- `tab`: Current active tab
- `view`: Current view mode (kanban/table/sessions)
- `filter`: Active status filter
- `speaker`: Selected speaker ID (opens drawer)

### Local State
- Expanded/collapsed add speakers panel
- Drag-and-drop active item
- Selected speakers for bulk actions
- Modal/drawer open states

---

## Migration Path

### Phase 1: Create New Unified Component
1. Create `EventPage.tsx` with tab structure
2. Import existing components (SpeakerStatusDashboard, etc.)
3. Add view toggle for speakers tab

### Phase 2: Update Routing
1. Update `/organizer/events/:eventCode` to use `EventPage`
2. Add redirect from `/organizer/events/:eventCode/edit`
3. Add redirect from speaker outreach route

### Phase 3: Deprecate Old Pages
1. Remove `EventDetail.tsx`
2. Remove `EventDetailEdit.tsx`
3. Remove `SpeakerOutreachPage.tsx`

---

## Change Log

| Date       | Version | Description                            | Author     |
|------------|---------|----------------------------------------|------------|
| 2025-12-21 | 1.0     | Initial wireframe for unified event page | Claude (UX Analysis) |

---

## Review Notes

### Benefits of This Approach
- Single page reduces cognitive load
- Tab state persisted in URL (shareable, bookmarkable)
- Unified speaker experience (kanban + table + sessions in one place)
- Eliminates confusion between "detail" and "edit" pages
- Mobile-friendly with bottom navigation

### Trade-offs
- More complex single component
- Tab switching may feel less "focused" than dedicated pages
- Requires careful state management for tab persistence

### Open Questions
1. Should we keep separate session detail modal or expand inline?
2. Should bulk actions in table view persist across tab switches?
3. Do we need a "compact" vs "detailed" toggle for speaker cards?
