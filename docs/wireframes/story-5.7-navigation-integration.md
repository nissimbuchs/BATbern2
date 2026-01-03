# Story 5.7: Navigation Integration - Slot Assignment Page

**Story**: Epic 5, Story 5.7 - Slot Assignment & Progressive Publishing
**Component**: Navigation Integration
**User Role**: Organizer
**Related Stories**: Story 5.8 (Unified Event Page), Story 2.3 (Publishing Engine)

---

## Purpose

This wireframe documents how organizers navigate TO the dedicated slot assignment page from various entry points in the unified event page (Story 5.8), and how they navigate back after completing timing assignments.

**Architecture**: Slot assignment is a **dedicated separate page** at `/organizer/events/:eventCode/slot-assignment`, NOT a tab within the unified event page.

---

## Navigation Entry Points

### Entry Point 1: Speakers Tab

**Location**: `/organizer/events/:eventCode?tab=speakers`

**Trigger**: When event has accepted speakers awaiting timing assignment

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back              BATbern 2025 Spring Conference                                  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  [Overview] [●Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]         │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  ┌─── SPEAKERS TAB ────────────────────────────────────────────────────────────┐   │
│  │                                                                              │   │
│  │  ⚠️ Action Required: Session Timing Assignment                              │   │
│  │  ┌──────────────────────────────────────────────────────────────────────┐  │   │
│  │  │                                                                      │  │   │
│  │  │  📅 Next Step: Assign Session Timings                               │  │   │
│  │  │                                                                      │  │   │
│  │  │  You have 5 accepted speakers awaiting session timing assignment.   │  │   │
│  │  │  Assign timings to publish the event agenda (Phase 3).             │  │   │
│  │  │                                                                      │  │   │
│  │  │  Progress: 3 of 8 sessions assigned (37%)                           │  │   │
│  │  │  🔶 5 sessions need timing                                           │  │   │
│  │  │                                                                      │  │   │
│  │  │  [Assign Session Timings →]                                         │  │   │
│  │  │                                                                      │  │   │
│  │  └──────────────────────────────────────────────────────────────────────┘  │   │
│  │                                                                              │   │
│  │  Speaker Management Views: [Kanban] [Table] [●Sessions]                     │   │
│  │                                                                              │   │
│  │  Sessions Summary:                                                           │   │
│  │  ┌──────────────────────────────────────────────────────────────────────┐  │   │
│  │  │ Session                    │ Speaker          │ Timing       │ Status │  │   │
│  │  │──────────────────────────────────────────────────────────────────────│  │   │
│  │  │ Keynote                    │ Dr. Sarah Miller │ 09:00-10:30  │ ✓      │  │   │
│  │  │ Workshop 1: Advanced       │ Prof. John Chen  │ 11:00-12:30  │ ✓      │  │   │
│  │  │ Workshop 2: Practical      │ Dr. Maria Lopez  │ 14:00-15:30  │ ✓      │  │   │
│  │  │ Panel Discussion           │ (Not assigned)   │ Not set      │ ⚠️     │  │   │
│  │  │ Afternoon Workshop         │ (Not assigned)   │ Not set      │ ⚠️     │  │   │
│  │  │ Evening Session            │ (Not assigned)   │ Not set      │ ⚠️     │  │   │
│  │  │ Closing Keynote            │ Robert Williams  │ (No timing)  │ ⚠️     │  │   │
│  │  │ Lightning Talks            │ Multiple         │ (No timing)  │ ⚠️     │  │   │
│  │  └──────────────────────────────────────────────────────────────────────┘  │   │
│  │                                                                              │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘

User Action: Click [Assign Session Timings →]
Result: Navigate to /organizer/events/:eventCode/slot-assignment
```

**Visibility Conditions**:
- Banner appears when: `speaker_pool` has ≥1 speaker with `status = 'ACCEPTED'` AND session has `start_time = null`
- Banner disappears when: All sessions have assigned timings (`start_time ≠ null`)
- Progress bar updates in real-time as timings are assigned

---

### Entry Point 2: Overview Tab

**Location**: `/organizer/events/:eventCode?tab=overview`

**Trigger**: Available as quick action when event is in planning phase

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back              BATbern 2025 Spring Conference                                  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  [●Overview] [Speakers] [Venue & Logistics] [Team] [Publishing] [Settings]         │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  ┌─── OVERVIEW TAB ────────────────────────────────────────────────────────────┐   │
│  │                                                                              │   │
│  │  Event Status: In Planning                            Phase 2: Speakers ✓   │   │
│  │                                                                              │   │
│  │  ┌─── Quick Actions ─────────────────────────────────────────────────────┐  │   │
│  │  │                                                                        │  │   │
│  │  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │  │   │
│  │  │  │ 📧 Speaker       │  │ 📅 Slot          │  │ 📢 Publishing    │   │  │   │
│  │  │  │    Outreach      │  │    Assignment    │  │    Controls      │   │  │   │
│  │  │  │                  │  │                  │  │                  │   │  │   │
│  │  │  │ Status: Active   │  │ Status: 🔶 In    │  │ Status: Pending  │   │  │   │
│  │  │  │                  │  │         Progress │  │                  │   │  │   │
│  │  │  │ 8 speakers       │  │                  │  │ Blocked until    │   │  │   │
│  │  │  │ contacted        │  │ 3 of 8 sessions  │  │ timings assigned │   │  │   │
│  │  │  │                  │  │ assigned (37%)   │  │                  │   │  │   │
│  │  │  │                  │  │                  │  │                  │   │  │   │
│  │  │  │ [Manage]         │  │ [Assign Timings] │  │ [View Status]    │   │  │   │
│  │  │  └──────────────────┘  └──────────────────┘  └──────────────────┘   │  │   │
│  │  │                                                                        │  │   │
│  │  └────────────────────────────────────────────────────────────────────────┘  │   │
│  │                                                                              │   │
│  │  ┌─── Workflow Progress ──────────────────────────────────────────────────┐  │   │
│  │  │                                                                        │  │   │
│  │  │  Phase 1: Topic Selection        ✓ Complete                           │  │   │
│  │  │  Phase 2: Speaker Coordination   ✓ Complete                           │  │   │
│  │  │  Phase 3: Agenda Planning        ⏳ In Progress (awaiting timings)    │  │   │
│  │  │  Phase 4: Publishing             ⏳ Pending                            │  │   │
│  │  │                                                                        │  │   │
│  │  └────────────────────────────────────────────────────────────────────────┘  │   │
│  │                                                                              │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘

User Action: Click [Assign Timings] button on Slot Assignment card
Result: Navigate to /organizer/events/:eventCode/slot-assignment
```

**Card Status Badges**:
- **Not Started**: Gray badge, "0 of X sessions assigned"
- **In Progress**: Orange badge (🔶), "X of Y sessions assigned (Z%)"
- **Complete**: Green badge (✓), "All sessions assigned"

**Button States**:
- Not Started: [Assign Timings]
- In Progress: [Continue Assignment]
- Complete: [Review Assignments]

---

### Entry Point 3: Publishing Tab

**Location**: `/organizer/events/:eventCode?tab=publishing`

**Trigger**: Validation dashboard shows incomplete session timings

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back              Publishing Control Center - BATbern 2025                        │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  [Overview] [Speakers] [Venue & Logistics] [Team] [●Publishing] [Settings]         │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  ┌─── PUBLISHING TAB ──────────────────────────────────────────────────────────┐   │
│  │                                                                              │   │
│  │  ┌─── Content Validation Dashboard ───────────────────────────────────────┐ │   │
│  │  │                                                                          │ │   │
│  │  │  Publishing Readiness: 73%  ████████████████████░░░░░░░                 │ │   │
│  │  │                                                                          │ │   │
│  │  │  ┌─── Required Items ────────────────────────────────────────────────┐  │ │   │
│  │  │  │                                                                    │  │ │   │
│  │  │  │  Component              Status    Validation            Action    │  │ │   │
│  │  │  │  ────────────────────────────────────────────────────────────────  │  │ │   │
│  │  │  │  ✓ Event Title          Ready     Passed all checks     [Edit]    │  │ │   │
│  │  │  │  ✓ Date & Venue         Ready     Venue confirmed       [View]    │  │ │   │
│  │  │  │  ✓ Topic Description    Ready     Within 500 chars      [Edit]    │  │ │   │
│  │  │  │  ✓ Speaker List         Ready     8/8 confirmed         [Manage]  │  │ │   │
│  │  │  │  ✓ Abstracts            Ready     8/8 validated         [Review]  │  │ │   │
│  │  │  │  ✓ Speaker Photos       Ready     8/8 uploaded          [Upload]  │  │ │   │
│  │  │  │                                                                    │  │ │   │
│  │  │  │  ⚠️ Session Timings     Partial   5/8 sessions assigned [Assign]  │  │ │   │
│  │  │  │    └─ Keynote           ✓ Ready   09:00-10:30, Room A  -          │  │ │   │
│  │  │  │    └─ Workshop 1        ✓ Ready   11:00-12:30, Room A  -          │  │ │   │
│  │  │  │    └─ Workshop 2        ✓ Ready   14:00-15:30, Room B  -          │  │ │   │
│  │  │  │    └─ Panel             ✗ Missing No start/end time     [Assign]  │  │ │   │
│  │  │  │    └─ Afternoon WS      ✗ Missing No start/end time     [Assign]  │  │ │   │
│  │  │  │    └─ Evening Session   ✗ Missing No start/end time     [Assign]  │  │ │   │
│  │  │  │    └─ Closing Keynote   ⚠️ Partial Speaker assigned,    [Assign]  │  │ │   │
│  │  │  │                                   no timing                        │  │ │   │
│  │  │  │    └─ Lightning Talks   ⚠️ Partial Speakers assigned,   [Assign]  │  │ │   │
│  │  │  │                                   no timing                        │  │ │   │
│  │  │  │                                                                    │  │ │   │
│  │  │  │  ⚠️ Agenda Times        Blocked   Cannot publish Phase 3 [Assign]  │  │ │   │
│  │  │  │                                   until all timings set            │  │ │   │
│  │  │  │                                                                    │  │ │   │
│  │  │  │  ✓ Registration Link    Ready     Tested & working      [Test]    │  │ │   │
│  │  │  │                                                                    │  │ │   │
│  │  │  └────────────────────────────────────────────────────────────────────┘  │ │   │
│  │  │                                                                          │ │   │
│  │  │  ⚠️ Warning: Phase 3 (Agenda) publish is blocked                       │ │   │
│  │  │     3 sessions require timing assignment before Agenda can be published │ │   │
│  │  │                                                                          │ │   │
│  │  └──────────────────────────────────────────────────────────────────────────┘ │   │
│  │                                                                              │   │
│  │  ┌─── Publishing Controls ────────────────────────────────────────────────┐ │   │
│  │  │                                                                          │ │   │
│  │  │  Current Phase: Phase 2 (Speakers) - Published                         │ │   │
│  │  │  Next Phase: Phase 3 (Agenda) - Ready when timings complete            │ │   │
│  │  │                                                                          │ │   │
│  │  │  [Publish Phase 1] ✓     [Publish Phase 2] ✓     [Publish Phase 3] 🔒  │ │   │
│  │  │   (Topic)               (Speakers)              (Blocked)               │ │   │
│  │  │                                                                          │ │   │
│  │  └──────────────────────────────────────────────────────────────────────────┘ │   │
│  │                                                                              │   │
│  └──────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘

User Actions:
1. Click [Assign] button on "Session Timings" row → Navigate to /organizer/events/:eventCode/slot-assignment
2. Click [Assign] button on specific sub-item → Navigate with session focus parameter
   Example: /organizer/events/:eventCode/slot-assignment?session=panel-discussion

Result: Opens dedicated slot assignment page
```

**Blocking Validation Logic**:
- Phase 3 (Agenda) [Publish] button is disabled (grayed out with lock icon 🔒) when ANY session has `start_time = null OR end_time = null OR room = null`
- Error message displayed: "Cannot publish Agenda phase. X sessions require timing assignment."
- [Assign] button appears on validation row, linking to slot assignment page

---

## Return Navigation

### Navigation Header (on Slot Assignment Page)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ Event Management > BATbern 2025 > Slot Assignment          [Back to Event]   [Help] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  Assign Session Timings - BATbern 2025 Spring Conference                           │
│                                                                                      │
│  [Full slot assignment interface here - see story-5.7-slot-assignment-page.md]     │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘

User Actions:
1. Click breadcrumb "BATbern 2025" → Navigate to /organizer/events/:eventCode (Overview tab)
2. Click [Back to Event] button → Navigate to /organizer/events/:eventCode (last active tab)
```

**Breadcrumb Navigation**:
- **Event Management**: Links to `/organizer/events` (event list)
- **BATbern 2025**: Links to `/organizer/events/:eventCode` (returns to Overview tab)
- **Slot Assignment**: Current page (no link)

**[Back to Event] Button Behavior**:
- If user came from Speakers tab: Returns to `/organizer/events/:eventCode?tab=speakers`
- If user came from Overview tab: Returns to `/organizer/events/:eventCode?tab=overview`
- If user came from Publishing tab: Returns to `/organizer/events/:eventCode?tab=publishing`
- Default (direct access): Returns to `/organizer/events/:eventCode` (Overview tab)
- Preserves last active tab using browser session storage

---

### Success State Navigation

**Trigger**: When all sessions have assigned timings (100% complete)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ Event Management > BATbern 2025 > Slot Assignment          [Back to Event]   [Help] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  ┌─── SUCCESS ──────────────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  ✓ All Session Timings Assigned!                                             │  │
│  │                                                                               │  │
│  │  🎉 Congratulations! All 8 sessions now have assigned timings.              │  │
│  │                                                                               │  │
│  │  ┌────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │                                                                        │  │  │
│  │  │  Sessions Assigned:       8 of 8 (100%)                               │  │  │
│  │  │  Average Match Score:     82% (Strong matches)                        │  │  │
│  │  │  Conflicts Resolved:      All clear                                   │  │  │
│  │  │                                                                        │  │  │
│  │  │  Next Step:               Proceed to Publishing                       │  │  │
│  │  │                                                                        │  │  │
│  │  └────────────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                               │  │
│  │  What's Next?                                                                 │  │
│  │  • Review the full agenda in the Publishing tab                              │  │
│  │  • Validate all content requirements are met                                 │  │
│  │  • Publish Phase 3 (Agenda) to make the schedule public                      │  │
│  │                                                                               │  │
│  │  [View Publishing Options →]        [Review Assignments]    [Back to Event]  │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                      │
│  ┌─── SESSION TIMELINE (Read-Only Summary) ────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  June 10, 2025                                                                │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐     │  │
│  │  │        Room A           Room B          Main Hall                   │     │  │
│  │  │ 09:00  ┌──────────────┐ ┌─────────────┐ ┌──────────────────────┐  │     │  │
│  │  │        │ ✓ Keynote    │ │             │ │                      │  │     │  │
│  │  │ 10:30  │ S. Miller    │ │             │ │                      │  │     │  │
│  │  │        └──────────────┘ └─────────────┘ └──────────────────────┘  │     │  │
│  │  │ 11:00  ┌──────────────┐ ┌─────────────┐                          │     │  │
│  │  │        │ ✓ Workshop 1 │ │ ✓ Workshop 2│                          │     │  │
│  │  │ 12:30  │ J. Chen      │ │ M. Lopez    │                          │     │  │
│  │  │        └──────────────┘ └─────────────┘                          │     │  │
│  │  │        [All sessions assigned - see full timeline in Publishing]  │     │  │
│  │  └─────────────────────────────────────────────────────────────────────┘     │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘

User Actions:
1. Click [View Publishing Options →] → Navigate to /organizer/events/:eventCode?tab=publishing
2. Click [Review Assignments] → Scroll to session timeline on current page
3. Click [Back to Event] → Navigate to last active tab (session storage)
```

**Success State Features**:
- **Celebration Banner**: Green background with checkmark icon
- **Summary Statistics**: Sessions assigned, average match score, conflicts status
- **Next Steps Guidance**: Clear call-to-action for next workflow phase
- **Primary CTA**: [View Publishing Options →] - Most prominent button, links to Publishing tab
- **Secondary Actions**: Review assignments (stay on page), return to event (breadcrumb)
- **Read-Only Timeline**: Collapsed view of assigned sessions, full details in Publishing tab

---

## Navigation Flow Diagram

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                         Unified Event Page                                    │
│                    /organizer/events/:eventCode                               │
│                                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   Overview   │  │   Speakers   │  │     Team     │  │  Publishing  │    │
│  │     Tab      │  │     Tab      │  │     Tab      │  │     Tab      │    │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘  └──────┬───────┘    │
│         │                 │                                    │            │
│         │                 │                                    │            │
│         │  [Assign        │  [Assign Session                  │  [Assign   │
│         │   Timings]      │   Timings →]                      │   Timings] │
│         │  (Quick Action) │  (Banner)                         │  (Validation)
│         │                 │                                    │            │
│         └─────────────────┴────────────────────────────────────┘            │
│                             │                                               │
└─────────────────────────────┼───────────────────────────────────────────────┘
                              │
                              ▼
                ┌─────────────────────────────────┐
                │   Slot Assignment Page          │
                │   (Dedicated Full Page)         │
                │                                 │
                │   /organizer/events/:eventCode/ │
                │   slot-assignment               │
                │                                 │
                │   Features:                     │
                │   • Speaker pool sidebar        │
                │   • Session timeline grid       │
                │   • Drag-drop interface         │
                │   • Preference matching         │
                │   • Conflict detection          │
                │                                 │
                └─────────────┬───────────────────┘
                              │
                ┌─────────────┴─────────────┐
                │                           │
                ▼                           ▼
    ┌───────────────────────┐   ┌─────────────────────────┐
    │  Return Navigation    │   │  Success State          │
    │                       │   │                         │
    │  [Back to Event]      │   │  ✓ All timings assigned │
    │  Breadcrumb           │   │                         │
    │                       │   │  [View Publishing       │
    │  → Last active tab    │   │   Options →]            │
    │    (session storage)  │   │                         │
    └───────────────────────┘   └─────────────┬───────────┘
                                              │
                                              ▼
                              ┌───────────────────────────┐
                              │   Publishing Tab          │
                              │                           │
                              │   • Validation complete   │
                              │   • Phase 3 unlocked      │
                              │   • Ready to publish      │
                              │                           │
                              │   [Publish Agenda]        │
                              └───────────────────────────┘
```

---

## URL State Management

### Entry Point URLs

**From Speakers Tab**:
```
Current: /organizer/events/batbern-2025?tab=speakers
Action: Click [Assign Session Timings →]
Target: /organizer/events/batbern-2025/slot-assignment
Session Storage: { lastActiveTab: 'speakers' }
```

**From Overview Tab**:
```
Current: /organizer/events/batbern-2025?tab=overview
Action: Click [Assign Timings] on Slot Assignment card
Target: /organizer/events/batbern-2025/slot-assignment
Session Storage: { lastActiveTab: 'overview' }
```

**From Publishing Tab**:
```
Current: /organizer/events/batbern-2025?tab=publishing
Action: Click [Assign] on Session Timings validation row
Target: /organizer/events/batbern-2025/slot-assignment
Session Storage: { lastActiveTab: 'publishing' }
```

**Direct Access** (bookmark, external link):
```
Direct: /organizer/events/batbern-2025/slot-assignment
Session Storage: { lastActiveTab: null } → defaults to 'overview'
```

### Optional Session Focus

**From Publishing Tab Sub-Item**:
```
Current: /organizer/events/batbern-2025?tab=publishing
Action: Click [Assign] on specific session (e.g., "Panel Discussion")
Target: /organizer/events/batbern-2025/slot-assignment?session=panel-discussion
Behavior: Auto-scroll to Panel Discussion session in timeline grid, highlight with border
```

### Return Navigation URLs

**[Back to Event] Button**:
```
From Slot Assignment: /organizer/events/batbern-2025/slot-assignment
Action: Click [Back to Event]
Target: /organizer/events/batbern-2025?tab={lastActiveTab}
```

**Breadcrumb "BATbern 2025"**:
```
From Slot Assignment: /organizer/events/batbern-2025/slot-assignment
Action: Click "BATbern 2025" breadcrumb
Target: /organizer/events/batbern-2025 (defaults to Overview tab)
```

**Success State [View Publishing Options →]**:
```
From Slot Assignment: /organizer/events/batbern-2025/slot-assignment
Action: Click [View Publishing Options →]
Target: /organizer/events/batbern-2025?tab=publishing
Session Storage: Updated { lastActiveTab: 'publishing' }
```

---

## Implementation Notes

### Session Storage Schema

```typescript
interface SlotAssignmentNavigation {
  lastActiveTab: 'overview' | 'speakers' | 'venue' | 'team' | 'publishing' | 'settings' | null;
  focusSession?: string;  // Session identifier for auto-scroll
  returnUrl?: string;     // Full return URL with query params
}

// Storage key
const NAV_STORAGE_KEY = 'batbern:slot-assignment-nav';
```

### Navigation State Management

**On Entry to Slot Assignment Page**:
```typescript
// Store current location before navigating
const storeNavigationContext = (currentTab: string) => {
  const navContext: SlotAssignmentNavigation = {
    lastActiveTab: currentTab,
    returnUrl: window.location.href
  };
  sessionStorage.setItem(NAV_STORAGE_KEY, JSON.stringify(navContext));

  // Navigate to slot assignment
  navigate(`/organizer/events/${eventCode}/slot-assignment`);
};
```

**On Return from Slot Assignment Page**:
```typescript
// Retrieve navigation context
const getNavigationContext = (): SlotAssignmentNavigation => {
  const stored = sessionStorage.getItem(NAV_STORAGE_KEY);
  return stored ? JSON.parse(stored) : { lastActiveTab: 'overview' };
};

// Navigate back
const handleBackToEvent = () => {
  const { lastActiveTab } = getNavigationContext();
  navigate(`/organizer/events/${eventCode}?tab=${lastActiveTab || 'overview'}`);
  sessionStorage.removeItem(NAV_STORAGE_KEY);  // Clean up
};
```

**Success State Navigation**:
```typescript
// Navigate to Publishing tab after completion
const handleViewPublishingOptions = () => {
  navigate(`/organizer/events/${eventCode}?tab=publishing`);
  sessionStorage.removeItem(NAV_STORAGE_KEY);  // Clean up

  // Optional: Show toast notification
  toast.success('All session timings assigned! Ready to publish Agenda phase.');
};
```

### Auto-Scroll to Focused Session

```typescript
// If session parameter present in URL
const { session: focusSessionId } = useSearchParams();

useEffect(() => {
  if (focusSessionId) {
    const sessionElement = document.getElementById(`session-${focusSessionId}`);
    if (sessionElement) {
      sessionElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
      sessionElement.classList.add('highlight-pulse');  // Add highlight animation

      // Remove highlight after 2 seconds
      setTimeout(() => {
        sessionElement.classList.remove('highlight-pulse');
      }, 2000);
    }
  }
}, [focusSessionId]);
```

### Accessibility Considerations

**Keyboard Navigation**:
- `Tab` to navigate between buttons
- `Enter` or `Space` to activate buttons
- `Escape` to trigger [Back to Event] (breadcrumb equivalent)
- Focus management: Return focus to originating button when navigating back

**Screen Reader Announcements**:
```html
<!-- On entry to slot assignment page -->
<div role="alert" aria-live="polite">
  Navigated to Slot Assignment page. 5 of 8 sessions awaiting timing assignment.
</div>

<!-- On success state -->
<div role="alert" aria-live="polite">
  Success! All 8 session timings have been assigned. You can now proceed to publishing.
</div>

<!-- On return navigation -->
<div role="alert" aria-live="polite">
  Returned to {Tab Name} tab. Your timing assignments have been saved.
</div>
```

**ARIA Labels**:
```html
<button aria-label="Assign session timings to all unassigned sessions">
  Assign Session Timings →
</button>

<button aria-label="Return to BATbern 2025 event page">
  Back to Event
</button>

<button aria-label="View publishing options in Publishing tab">
  View Publishing Options
</button>
```

---

## Responsive Design

### Desktop (≥1200px)

- Full navigation header with breadcrumb visible
- [Back to Event] button always visible in top-right
- Success banner spans full width with all three buttons horizontal

### Tablet (768px - 1199px)

- Breadcrumb collapses to "... > Slot Assignment"
- [Back to Event] button remains visible
- Success banner buttons stack vertically (2 columns)

### Mobile (<768px)

- Breadcrumb hidden, replaced with [← Back] icon button
- Success banner buttons stack vertically (1 column)
- Primary CTA [View Publishing Options →] full-width, prominent

---

## Edge Cases

### Concurrent Editing

**Scenario**: User A is on slot assignment page. User B (different organizer) also assigns timings simultaneously.

**Behavior**:
- Real-time sync via WebSocket or polling
- Progress bar updates live
- If User B completes all assignments while User A still working:
  - User A sees success banner appear dynamically
  - Toast notification: "Another organizer just completed the timing assignments!"
  - Session grid updates to show all assigned timings

### Navigation During Unsaved Changes

**Scenario**: User has dragged a speaker to a slot but not confirmed/saved. Clicks [Back to Event].

**Behavior**:
- Confirmation modal appears:
  ```
  ⚠️ Unsaved Changes

  You have 1 pending timing assignment that has not been saved.

  What would you like to do?

  [Save & Return]  [Discard Changes]  [Cancel]
  ```
- [Save & Return]: Saves pending assignment, then navigates back
- [Discard Changes]: Reverts assignment, navigates back
- [Cancel]: Stays on slot assignment page

### Deep Linking

**Scenario**: User receives a direct link to slot assignment page via email or bookmark.

**URL**: `https://batbern.ch/organizer/events/batbern-2025/slot-assignment`

**Behavior**:
- Page loads normally (no authentication redirect issues)
- Session storage `lastActiveTab` is `null`
- [Back to Event] button defaults to Overview tab
- No focus session highlight (no `?session=` parameter)

---

## Testing Checklist

### Navigation Entry Points

- [ ] Speakers tab banner appears when sessions need timing
- [ ] Speakers tab banner disappears when all sessions assigned
- [ ] Overview tab Slot Assignment card shows correct status badge
- [ ] Overview tab [Assign Timings] button navigates correctly
- [ ] Publishing tab validation row shows session timing status
- [ ] Publishing tab [Assign] button on main row navigates to slot assignment
- [ ] Publishing tab [Assign] button on sub-item navigates with session focus
- [ ] Phase 3 [Publish] button is disabled when timings incomplete

### Return Navigation

- [ ] Breadcrumb "BATbern 2025" returns to Overview tab
- [ ] [Back to Event] button returns to last active tab (Speakers)
- [ ] [Back to Event] button returns to last active tab (Overview)
- [ ] [Back to Event] button returns to last active tab (Publishing)
- [ ] [Back to Event] defaults to Overview tab when session storage empty
- [ ] Session storage is cleaned up after navigation

### Success State

- [ ] Success banner appears when 100% sessions assigned
- [ ] Success banner shows correct statistics (8/8, match score, etc.)
- [ ] [View Publishing Options →] navigates to Publishing tab
- [ ] [Review Assignments] stays on page, scrolls to timeline
- [ ] [Back to Event] button still works in success state
- [ ] Session timeline shows read-only summary of assignments

### URL State

- [ ] Session focus parameter highlights correct session
- [ ] Auto-scroll works when session focus parameter present
- [ ] Highlight animation plays and removes after 2 seconds
- [ ] Session storage persists across page refresh (before navigation)

### Edge Cases

- [ ] Concurrent editing updates progress bar in real-time
- [ ] Unsaved changes confirmation modal appears
- [ ] Deep linking works without session storage
- [ ] Keyboard navigation works (Tab, Enter, Escape)
- [ ] Screen reader announcements fire correctly

---

## Related Wireframes

- **story-5.7-slot-assignment-page.md**: Main slot assignment interface
- **story-2.3-basic-publishing-engine.md**: Publishing tab with session timing validation
- **story-5.8-unified-event-page.md**: Unified event page with tab navigation (Story 5.8)

---

## Change Log

| Date       | Version | Description                     | Author     |
|------------|---------|----------------------------------|-----------
| 2025-12-25 | 1.0     | Initial navigation integration wireframe | Sally (UX Expert) |
