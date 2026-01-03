# Wireframe Review Plan: BAT-11 Slot Assignment & Progressive Publishing

## Executive Summary

Reviewed two existing wireframes against Story BAT-11 requirements and Story 5.8 (Unified Event Page) architecture. **Critical Discovery:** Slot assignment should be a **dedicated separate page** due to workflow complexity, not integrated into existing tabs. Publishing features should enhance the existing Publishing tab.

**Existing Architecture (Story 5.8 - Already Implemented):**
- Unified Event Page at `/organizer/events/:eventCode` with 6 tabs
- **Speakers Tab** has 3 views: Kanban | Table | **Sessions**
  - Sessions view (`SpeakersSessionsTable`) shows simple session table for viewing/editing
  - URL pattern: `?tab=speakers&view=sessions`
- **Publishing Tab** (`EventPublishingTab`) already exists
  - URL pattern: `?tab=publishing`
- Story 5.8 status: **In QA Review - Implementation Complete**

**BAT-11 Architecture Decision:**
- **Separate Page:** `/organizer/events/:eventCode/slot-assignment` for intensive drag-drop timing workflow
- **Rationale:** Workflow too complex for tab integration (requires full screen, focused attention, strategic decision-making)
- **Navigation:** Links from Speakers tab and Publishing tab validation dashboard

**Wireframe Strategy:**
- ✅ **DO** create dedicated slot assignment page wireframe (separate from tabs)
- ✅ **DO** show navigation integration with Story 5.8 tabs
- ✅ **DO** enhance Publishing tab with session timing validation
- ❌ **DO NOT** try to cram complex workflow into existing tab views

**Wireframe Status:**
- ✅ **story-3.1-speaker-matching-interface.md** - Good UI patterns, correct architecture (standalone page)
- ⚠️ **story-2.3-basic-publishing-engine.md** - Good publishing controls but missing slot validation
- ❌ **Dedicated Slot Assignment Page** - MISSING (critical - main workflow page)
- ❌ **Publishing Tab Slot Validation** - MISSING (critical - validate timings before Agenda publish)
- ❌ **Conflict Resolution Modal** - MISSING (referenced in AC9 but not designed)
- ❌ **Speaker Preferences Panel** - MISSING (referenced in AC7/AC8 but not shown)
- ❌ **Navigation Integration** - MISSING (how to reach slot assignment from tabs)

## Story 5.8 Integration Context

**INTEGRATION STRATEGY:** BAT-11 adds a new dedicated page that integrates with Story 5.8's existing tab structure through navigation links.

**Existing Tab Structure (Story 5.8):**
1. **Overview Tab** - Event summary, status, quick actions
   - **BAT-11 Integration**: Add "Assign Session Timings" button when speakers accepted
2. **Speakers Tab** - Has 3 views: Kanban | Table | **Sessions**
   - Sessions view uses `SpeakersSessionsTable` - simple table for viewing/editing session details
   - Existing URL state: `?tab=speakers&view=sessions`
   - **BAT-11 Integration**: Add navigation button to slot assignment page
3. **Venue & Logistics Tab** - Venue details, schedule
4. **Team Tab** - Team assignments, outreach distribution
5. **Publishing Tab** - Uses `EventPublishingTab` component
   - Existing URL state: `?tab=publishing`
   - **BAT-11 Enhancement**: Add session timing validation dashboard item
   - **BAT-11 Integration**: [Assign Timings] button links to slot assignment page
6. **Settings Tab** - Notifications, danger zone

**New BAT-11 Page:**
- **Route:** `/organizer/events/:eventCode/slot-assignment`
- **Purpose:** Dedicated drag-drop timing assignment workflow
- **Navigation From:** Speakers tab, Publishing tab, Overview tab quick actions
- **Navigation Back:** Breadcrumb, "Back to Event" button

**Components Already Implemented (Reusable):**
- `SpeakersSessionsTable` - Can inspire session grid design for slot assignment page
- `EventPublishingTab` - Validation dashboard pattern to enhance
- Material-UI components for drag-drop, modals, drawers

## Wireframe Coverage Analysis

### Wireframe 1: story-3.1-speaker-matching-interface.md

**Architecture Assessment:**
- ✅ Shows standalone page (correct approach after UX analysis)
- ✅ URL pattern: `/organizer/events/:eventCode/slots` (can be updated to `/slot-assignment`)
- ⚠️ Missing navigation context (how to reach from Story 5.8 tabs)
- ⚠️ Missing breadcrumb navigation back to event page

**What It Covers Well:**
- ✅ Basic drag-and-drop slot assignment UI pattern
- ✅ Two-column layout (speakers list | time slots)
- ✅ Speaker cards with action buttons
- ✅ Time-slot grid organized by session type
- ✅ Empty slot indicators ("Drop here" prompts)
- ✅ Visual drag feedback (implied)

**Acceptance Criteria Covered:**
- AC5: Drag-and-drop UI ✅ (pattern shown, wrong context)
- AC6: Visual timeline showing slots ✅ (pattern shown)
- AC7: Display speaker preferences ❌ (mentioned but not shown)
- AC8: Track A/V needs ❌ (not visible in UI)
- AC9: Conflict warnings ❌ (prevention mentioned, no UI shown)

**Critical Gaps:**
1. **No navigation integration** - doesn't show how to reach from Story 5.8 tabs
2. **No breadcrumb** - missing "Back to Event" navigation
3. **No SpeakerPreferencePanel** - AC7/AC8 requirements not visualized
4. **No ConflictDetectionAlert** - AC9 modal not designed
5. **No UnassignedSpeakersList counter** - AC12 real-time progress indicator missing
6. **Missing advanced features:**
   - No bulk auto-assignment (AC13)
   - No optimal order suggestion (AC10)
   - No preference match highlighting (AC11)
7. **No success state** - missing "All timings assigned! Proceed to Publishing →" banner

### Wireframe 2: story-2.3-basic-publishing-engine.md

**Architecture Assessment:**
- ✅ Publishing tab patterns are good
- ⚠️ Missing session timing validation
- ⚠️ Missing link to dedicated slot assignment page

**What It Covers Well:**
- ✅ Publishing timeline with phase milestones
- ✅ Content validation dashboard with hierarchical checks
- ✅ Publishing readiness progress bar
- ✅ Live preview pane with device toggle
- ✅ Publishing mode selection (Draft/Progressive/Complete)
- ✅ Version control with rollback
- ✅ Action buttons for fixing validation errors
- ✅ Color-coded status indicators (✓/⚠️/✗)

**Acceptance Criteria Covered:**
- AC14-AC17: Publishing phases ✅
- AC18: Manual publish/unpublish ✅
- AC19: Auto-publish scheduling ✅
- AC20: Preview mode ✅
- AC21: Validation before publish ✅
- AC22-AC23: Progressive disclosure ✅
- AC24: Notify subscribers ⚠️ (implied but no UI shown)
- AC25: CDN invalidation ⚠️ (happens but no status shown)
- AC26: Track versions ✅
- AC27: Rollback capability ✅

**Critical Gaps:**
1. **No session timing validation** - validation dashboard shows "Agenda Times" but not "Session Timings: X/Y assigned"
2. **No blocking validation** - doesn't prevent Phase 3 (Agenda) publish without complete timings
3. **No link to Sessions tab** - [Assign] button doesn't show target (`?tab=speakers&view=sessions&mode=assign`)
4. **Missing workflow context** - no indication that timing assignment is prerequisite
5. **Incomplete validation items:**
   - "Agenda Times" exists but doesn't mean "all sessions have timing"
   - No per-session visibility (which sessions lack start_time/end_time)
6. **Subscriber notification UI missing** (AC24)
7. **CDN invalidation status missing** (AC25)

## Critical Integration Gaps

### Gap 1: Dedicated Slot Assignment Page Incomplete
**Problem:** Existing wireframe (story-3.1) shows correct architecture (separate page) but missing critical features

**Impact:**
- No navigation integration with Story 5.8 tabs
- Missing preference panel, conflict resolution, success states
- No bulk auto-assignment feature shown
- Developers lack complete specification

**Required Updates:** Enhance `story-3.1-speaker-matching-interface.md` or create new comprehensive wireframe

**Must Add:**
- **Page Header:**
  - Breadcrumb: Event Management > BATbern 2025 > Slot Assignment
  - Page title: "Assign Session Timings"
  - [Back to Event] button linking to `/organizer/events/:eventCode`
- **Navigation Context:**
  - How users reach this page from Speakers tab, Publishing tab, Overview tab
  - Return navigation after completion
- **Speaker Pool Sidebar:**
  - Progress indicator: "5 of 8 sessions assigned (63%)"
  - Filter: [All] [Assigned] [Unassigned]
  - Unassigned badge: "🔶 3 remaining"
  - Speaker preference icons on cards
- **Session Grid Enhancements:**
  - Placeholder sessions clearly marked (grayed, "No timing" label)
  - Preference match highlights during drag (green/yellow/red)
  - Multi-day timeline view
- **Advanced Features:**
  - [Auto-Assign] button with algorithm modal
  - [View Preferences] per speaker
  - Preference match tooltips
- **Success State:**
  - Banner: "✓ All timings assigned! Proceed to Publishing →"
  - [Go to Publishing Tab] button

### Gap 2: Publishing Tab Slot Validation Missing
**Problem:** Publishing tab doesn't validate session timings before allowing Agenda phase publish

**Impact:**
- Could publish incomplete agenda (Phase 3) with placeholder sessions
- No blocking validation preventing premature publish
- Users don't know they need to assign timings first

**Required Update:** `story-2.3-basic-publishing-engine.md` (Section 2 - Validation Dashboard)

**Must Add:**
- New validation item: "Session Timings"
  - Status: "Ready (8/8 sessions assigned)" or "Incomplete (5/8 sessions)"
  - Sub-items showing which sessions lack timing
  - Action: [Assign Timings] button linking to `/organizer/events/:eventCode/slot-assignment`
- Blocking validation: Red error icon preventing Phase 3 publish
- Example row:
  ```
  Session Timings | ⚠️ Incomplete | 5/8 sessions assigned | [Assign Timings]
    ├─ Morning Session 1 | ✗ Not assigned | No start/end time | [Assign]
    ├─ Afternoon Session 2 | ✗ Not assigned | No start/end time | [Assign]
    └─ Evening Panel | ✗ Not assigned | No start/end time | [Assign]
  ```

### Gap 3: Navigation Integration Missing
**Problem:** No wireframes showing how to navigate TO slot assignment page and back to event page

**Impact:**
- Users don't know how to access slot assignment workflow
- No visual spec for navigation entry points
- Unclear return path after completing assignments

**Required Wireframes:** Navigation integration across multiple touch points

**Entry Points to Slot Assignment Page:**

**From Speakers Tab:**
- Show: Call-to-action button/banner when all speakers accepted
- Button: [Assign Session Timings →]
- Location: Top of Speakers tab, below summary metrics
- Condition: Visible when speaker_pool has ≥1 accepted speaker

**From Overview Tab:**
- Show: Quick action card
- Card: "Slot Assignment" with status badge
- Status: "Not Started" / "In Progress (5/8)" / "Complete (8/8)"
- Action: [Assign Timings] button

**From Publishing Tab:**
- Show: Validation dashboard [Assign Timings] button (covered in Gap 2)
- Links to `/organizer/events/:eventCode/slot-assignment`

**Return Navigation from Slot Assignment Page:**
- Breadcrumb: Event Management > BATbern 2025 > **Slot Assignment**
- [Back to Event] button in header → returns to `/organizer/events/:eventCode`
- Success banner: "✓ All timings assigned! [View Publishing Options →]"
  - Links to `/organizer/events/:eventCode?tab=publishing`

### Gap 4: Conflict Resolution Modal Missing
**Problem:** AC9 requires conflict detection but no wireframe exists

**Impact:**
- No specification for conflict modal triggered during drag-drop
- No workflow for resolving room overlap or speaker double-booking
- Developers lack visual guidance

**Required Wireframe:** `story-5.7-conflict-resolution-modal.md`

**Must Include:**
- Modal triggered when: assigning timing creates conflict in Sessions view
- Conflict types:
  - `room_overlap`: Two sessions same room, overlapping times
  - `speaker_double_booked`: Speaker assigned to overlapping sessions
  - `speaker_unavailable`: Timing conflicts with speaker preferences
- Modal contents:
  - Conflict type badge (error/warning severity)
  - Affected sessions with names, current times
  - Visual timeline showing overlap
  - Conflicting speaker details
- Resolution options:
  - [Find Alternative Slot] - suggests available slots matching preferences
  - [Reassign Other Speaker] - move conflicting speaker to different slot
  - [Override Warning] - force assignment (for warnings, not errors)
  - [Cancel] - abort assignment
- Success confirmation after resolution
- **Scenarios to Show:**
  1. Room overlap conflict (error - must resolve)
  2. Speaker double-booked (error - must resolve)
  3. Speaker preference conflict (warning - can override)

### Gap 5: Speaker Preferences Panel Missing
**Problem:** AC7/AC8 require preference display but no wireframe exists

**Impact:**
- No visual specification for how preferences are shown during assignment
- No guidance on preference match indicators
- Unclear how A/V requirements integrate with UI

**Required Wireframe:** `story-5.7-speaker-preference-panel.md`

**Must Include:**
- Panel type: Drawer sliding from right (consistent with Story 5.8 speaker drawer pattern)
- Trigger: Hover over speaker in pool or click [View Preferences]
- Panel contents:
  - **Time Preferences:**
    - Morning preference: ☀️ Preferred / ⚪ Neutral / ⊘ Avoid
    - Afternoon preference: ☀️ Preferred / ⚪ Neutral / ⊘ Avoid
    - Evening preference: ☀️ Preferred / ⚪ Neutral / ⊘ Avoid
    - Specific avoid times: List of date-time ranges
  - **A/V Requirements:**
    - Microphone: ✓ Required
    - Projector: ✓ Required
    - Clicker/Remote: - Optional
    - Recording permission: ✓ Approved
  - **Room Setup:**
    - Standing desk: ✓ Preferred
    - Flip chart: ✓ Required
    - Whiteboard: - Not needed
    - Notes: "Prefers natural light, quiet room"
  - **Match Score:** When hovering over session slot, show percentage match
- **Visual Indicators on Session Grid:**
  - Green highlight: Strong match (80-100% score)
  - Yellow highlight: Acceptable match (50-79% score)
  - Red highlight: Poor match (<50% score) or conflicts
- **Interaction Flow:**
  1. User drags speaker from pool
  2. Session slots highlight with match colors
  3. User hovers over slot → tooltip shows score and reason
  4. User clicks [View Full Preferences] → drawer opens
  5. User assigns to preferred slot → confirmation with match score display

## Recommended Wireframes

### Priority 1: CRITICAL (Blocks Implementation)

#### 1.1 Dedicated Slot Assignment Page
**File:** Update `docs/wireframes/story-3.1-speaker-matching-interface.md` OR create `docs/wireframes/story-5.7-slot-assignment-page.md` (NEW)

**Purpose:** Comprehensive wireframe for dedicated slot assignment workflow page

**Route:** `/organizer/events/:eventCode/slot-assignment`

**Must Include:**

**Page Header:**
- Breadcrumb: Event Management > BATbern 2025 > Slot Assignment
- Page title: "Assign Session Timings - BATbern 2025"
- [Back to Event] button → `/organizer/events/:eventCode`
- Event status badge: "In Planning" / "Ready to Publish"

**Layout:** Three-column full-page:
- Left sidebar (300px): Speaker pool
- Center (flexible): Session timeline grid
- Right panel (collapsible): Quick actions

**Speaker Pool Sidebar:**
- Header: "Speakers" with count badge "🔶 5 Unassigned"
- Progress bar: "3 of 8 assigned (37%)"
- Filters: [All] [●Assigned] [○Unassigned]
- Speaker cards:
  - Draggable with visual grab handle
  - Show: Name, topic, preference icons (☀️ morning, 🌙 evening)
  - Status: Checkmark for assigned, orange border for unassigned
  - [View Preferences] button per card

**Session Timeline Grid:**
- Multi-day view with date headers
- Time slots: 08:00 - 20:00 (hourly rows)
- Columns: Room A, Room B, Main Hall
- Session cells:
  - **Placeholder sessions:** Gray background, dashed border, "No timing assigned"
  - **Assigned sessions:** Show speaker name, start-end time, room, colored border
  - **Empty drop zones:** "Drop speaker here" prompt on hover
- Preference match colors during drag:
  - 🟢 Green: Strong match (80-100%)
  - 🟡 Yellow: Acceptable (50-79%)
  - 🔴 Red: Poor match (<50%) or conflict

**Quick Actions Panel:**
- [Auto-Assign All] button → opens algorithm modal
- [Clear All Assignments] button (confirmation required)
- Session summary: "8 total sessions, 3 assigned, 5 pending"
- Export options: [Download Schedule PDF]

**Success State:**
- Full-width green banner: "✓ All session timings assigned!"
- Sub-text: "8 of 8 sessions complete. Ready to proceed to publishing."
- [View Publishing Options →] button → `/organizer/events/:eventCode?tab=publishing`

**Screens to Show:**
1. Initial state: 5/8 sessions awaiting timing, speaker pool populated
2. Dragging speaker: Preference match highlights visible on timeline
3. Conflict detected: Modal triggered (reference story-5.7-conflict-resolution-modal.md)
4. Success state: All sessions assigned, success banner visible with navigation

#### 1.2 Publishing Tab Slot Validation
**File:** Update `docs/wireframes/story-2.3-basic-publishing-engine.md` (Section 2)

**Purpose:** Add session timing validation to Publishing tab

**Must Add to Validation Dashboard:**

**New Validation Item:**
```
Session Timings | Status | Details | Action
──────────────────────────────────────────────────────
✓ Ready          | 8/8 sessions have assigned timings | - | -
  ├─ Keynote     | ✓ Assigned | 09:00 - 10:30, Main Hall | -
  ├─ Workshop 1  | ✓ Assigned | 11:00 - 12:30, Room A | -
  └─ ... (6 more) | ✓ Assigned | ... | -

OR (incomplete state):

⚠️ Incomplete     | 5/8 sessions assigned | 3 sessions lack timing | [Assign Timings]
  ├─ Keynote     | ✓ Assigned | 09:00 - 10:30, Main Hall | -
  ├─ Workshop 1  | ✓ Assigned | 11:00 - 12:30, Room A | -
  ├─ Workshop 2  | ✗ Not assigned | No start/end time | [Assign]
  ├─ Panel       | ✗ Not assigned | No start/end time | [Assign]
  └─ Closing     | ✗ Not assigned | No start/end time | [Assign]
```

**Blocking Validation Logic:**
- Phase 1 (Topic): No session timing requirement
- Phase 2 (Speakers): No session timing requirement
- **Phase 3 (Agenda): BLOCKED if any session lacks start_time/end_time**
  - Error message: "Cannot publish Agenda phase. 3 sessions require timing assignment."
  - [Publish Agenda] button disabled (grayed out)
  - [Assign Timings] button links to `/organizer/events/:eventCode/slot-assignment`

**Visual Changes:**
- Add "Session Timings" row above "Agenda Times" in validation dashboard
- Use ⚠️ icon if incomplete, ✓ if all assigned
- Show expandable sub-items listing all sessions
- [Assign Timings] button appears when clicking incomplete row

#### 1.3 Conflict Resolution Modal
**File:** `docs/wireframes/story-5.7-conflict-resolution-modal.md` (NEW)

**Purpose:** Show how conflicts are detected and resolved during timing assignment

**Trigger Conditions:**
1. **Room Overlap:** Assigning session to room already occupied at that time
2. **Speaker Double-Booked:** Assigning speaker to session overlapping with another assignment
3. **Speaker Unavailable:** Assigning timing that conflicts with speaker preferences (warning only)

**Modal Structure:**

**Header:**
```
⚠️ Timing Conflict Detected
[X] Close
```

**Body (Room Overlap Example):**
```
Conflict Type: Room Overlap

You are trying to assign:
  Session: "Workshop 2: Advanced Topics"
  Time: 14:00 - 15:30, June 10, 2025
  Room: Conference Room A
  Speaker: Dr. Sarah Miller

But this room is already occupied:
  Session: "Panel Discussion: Industry Trends"
  Time: 14:00 - 16:00, June 10, 2025
  Room: Conference Room A
  Speaker: John Smith, Jane Doe, Robert Chen

Overlap: 14:00 - 15:30 (1.5 hours)

Visual Timeline:
13:00 ────────────────────────────────────── 17:00
       [  Panel Discussion (Room A)  ]
              [  Workshop 2 (Room A)  ] ← CONFLICT

Resolution Options:
┌───────────────────────────────────────────────────┐
│ [Find Alternative Slot]                           │
│ Suggest available time slots in Room A or other  │
│ rooms that match speaker preferences              │
├───────────────────────────────────────────────────┤
│ [Change Room]                                     │
│ Keep timing 14:00-15:30, assign to different room│
├───────────────────────────────────────────────────┤
│ [Reassign Panel]                                  │
│ Move Panel Discussion to different time/room     │
├───────────────────────────────────────────────────┤
│ [Cancel]                                          │
│ Abort assignment, return to assignment view      │
└───────────────────────────────────────────────────┘
```

**Body (Speaker Double-Booked Example):**
```
Conflict Type: Speaker Double-Booked

You are trying to assign Dr. Sarah Miller to:
  Session: "Workshop 2: Advanced Topics"
  Time: 14:00 - 15:30, June 10, 2025

But Dr. Sarah Miller is already assigned to:
  Session: "Keynote: Future of Architecture"
  Time: 13:00 - 14:30, June 10, 2025

Overlap: 14:00 - 14:30 (30 minutes)

Resolution Options:
[Find Alternative Slot] [Reassign Other Session] [Cancel]
```

**Body (Speaker Preference Conflict - Warning):**
```
⚠️ Warning: Preference Mismatch

You are assigning Dr. Sarah Miller to:
  Session: "Early Morning Workshop"
  Time: 08:00 - 09:30, June 10, 2025

Speaker preferences indicate:
  - Prefers: Afternoon sessions
  - Avoid: Morning sessions before 09:00

Match Score: 25% (Poor match)

This is a warning, not an error. You can proceed if needed.

[Override & Assign Anyway] [Find Better Slot] [Cancel]
```

**Footer:**
- Severity indicator: 🔴 Error (must resolve) or ⚠️ Warning (can override)
- Help text: "Need help? View slot assignment guide"

**Success State After Resolution:**
- Modal closes
- Toast notification: "✓ Conflict resolved. Timing assigned successfully."
- Session grid updates with new assignment

**Scenarios to Wireframe:**
1. Room overlap conflict (full modal)
2. Speaker double-booked (full modal)
3. Preference conflict warning (simplified modal)

#### 1.4 Speaker Preference Panel
**File:** `docs/wireframes/story-5.7-speaker-preference-panel.md` (NEW)

**Purpose:** Show how speaker preferences are displayed during timing assignment

**Panel Type:** Drawer sliding from right (400px width)

**Trigger:**
- User hovers over speaker card in pool → tooltip with summary
- User clicks [View Preferences] → full drawer opens

**Drawer Header:**
```
Dr. Sarah Miller - Preferences
                                                      [X] Close
```

**Drawer Body:**

**Section 1: Time Preferences**
```
Time of Day Preferences:
┌─────────────────────────────────────────┐
│ ☀️ Morning (6:00 - 12:00)      ⚪ Neutral │
│ ☀️ Afternoon (12:00 - 18:00)   ✓ Preferred │
│ ⊘ Evening (18:00 - 22:00)     ✗ Avoid    │
└─────────────────────────────────────────┘

Specific Avoid Times:
  • June 10, 08:00 - 09:00 (Morning commute)
  • June 11, 17:00 - 19:00 (Prior commitment)
```

**Section 2: A/V Requirements**
```
Audio/Visual Setup:
  ✓ Microphone required
  ✓ Projector/Screen required
  ✓ Clicker/Remote required
  - Whiteboard (not needed)
  ✓ Recording permission approved
```

**Section 3: Room Setup**
```
Room Preferences:
  ✓ Standing desk preferred
  ✓ Natural light preferred
  - Flip chart (not needed)
  ✓ Quiet room (minimal background noise)

Notes:
  "Prefers interactive setup with audience seating
   in semi-circle. Needs HDMI adapter for MacBook."
```

**Section 4: Match Score (Dynamic)**
```
Current Slot Match:
When hovering over session slot "Workshop 2 (14:00-15:30)":

Match Score: 85% ✓ Strong Match

  ✓ Time: Afternoon slot (preferred)
  ✓ Room: Conference Room A has natural light
  ✓ A/V: Projector and microphone available
  ⚠️ Duration: 1.5 hours (speaker prefers 1 hour)

Overall: Excellent match for this speaker
```

**Drawer Footer:**
```
[Assign to Current Slot] [Find Best Match] [Close]
```

**Visual Indicators on Session Grid (when dragging speaker):**
```
Session Grid (while dragging Dr. Sarah Miller):

09:00 - 10:30 | Workshop 1    | Room A | 🔴 Poor Match (25%)
              └─ Morning slot (speaker avoids early morning)

14:00 - 15:30 | Workshop 2    | Room A | 🟢 Strong Match (85%)
              └─ Afternoon slot (preferred), good room setup

18:30 - 20:00 | Evening Panel | Room B | 🔴 Poor Match (10%)
              └─ Evening slot (speaker avoids), conflicts with avoid time
```

**Interaction Flow:**
1. User hovers over speaker → tooltip shows summary (time preferences, match score)
2. User starts dragging speaker → session slots highlight with colors
3. User hovers over slot → tooltip expands showing detailed match score
4. User clicks [View Full Preferences] → drawer opens from right
5. User reviews all preferences in drawer
6. User drags to slot → assignment confirmed with match score display

**Responsive Behavior:**
- Desktop: 400px drawer from right
- Tablet: Full-width bottom sheet
- Mobile: Full-screen modal

### Priority 2: IMPORTANT (Enhances UX)

#### 2.1 Bulk Auto-Assignment Feature
**File:** `docs/wireframes/story-5.7-bulk-auto-assignment.md` (NEW)

**Purpose:** Show auto-assignment feature (AC13)

**Trigger:** User clicks [Auto-Assign] button in Sessions view

**Modal Structure:**

**Step 1: Algorithm Selection**
```
Auto-Assign Sessions

Choose optimization strategy:

⚪ Optimize for Speaker Preferences
   Prioritize matching speakers to preferred time slots
   Best for: Maximizing speaker satisfaction

⚪ Optimize for Expertise Match
   Prioritize matching speaker expertise to session topics
   Best for: Content quality and relevance

● Optimize for Balanced Schedule (Recommended)
   Balance between preferences, expertise, and time distribution
   Best for: Overall event quality

Advanced Options: [Show ▼]
  ☑ Respect hard constraints (avoid times, conflicts)
  ☑ Balance session distribution across days
  ☐ Prioritize keynotes in prime slots

[Preview Assignments] [Cancel]
```

**Step 2: Preview Assignments**
```
Preview Auto-Assignments

Algorithm: Balanced Schedule
Assignments Generated: 5 sessions
Overall Match Score: 78% (Good)

Side-by-Side Comparison:

Current State          →    Proposed Assignments
──────────────────────────────────────────────────────
Workshop 1             →    Workshop 1
  No timing assigned        09:00-10:30, Room A
                            Dr. Sarah Miller (Match: 85%)

Workshop 2             →    Workshop 2
  No timing assigned        14:00-15:30, Room A
                            Prof. John Chen (Match: 72%)

Panel Discussion       →    Panel Discussion
  No timing assigned        16:00-17:30, Main Hall
                            Multiple speakers (Match: 65%)

⚠️ Detected Issues:
  • Workshop 3: Low match score (45%) - Manual review recommended
  • Evening Session: No suitable speaker found - Manual assignment needed

[Accept All] [Customize Before Applying] [Cancel]
```

**Step 3: Confirmation**
```
✓ Auto-Assignment Complete

Successfully assigned: 5 sessions
Average match score: 78%
Manual review needed: 2 sessions

[View Assignments] [Assign Remaining Manually]
```

#### 2.2 Subscriber Notification Controls
**File:** Update `docs/wireframes/story-2.3-basic-publishing-engine.md` (Section 3)

**Purpose:** Show subscriber notification UI (AC24)

**Add to Publishing Controls Section:**

**Before Publishing:**
```
Publishing Options:

Mode: ● Progressive  ⚪ Complete  ⚪ Draft

☑ Notify subscribers when publishing
    Will send email to 247 active subscribers
    [Preview Newsletter]

Auto-Publish Schedule:
  Phase 1 (Topic): Immediately
  Phase 2 (Speakers): June 1, 2025 at 10:00
  Phase 3 (Agenda): June 8, 2025 at 10:00

[Publish Now] [Schedule Publish]
```

**After Publishing (Success State):**
```
✓ Published Successfully

Phase 2 (Speakers) is now live.

Newsletter sent to 247 subscribers at 14:35
Delivery status: 245 delivered, 2 pending

[View Sent Emails] [View Live Page]
```

**Newsletter Preview Modal:**
```
Newsletter Preview

Subject: New Speakers Announced for BATbern 2025

Preview:
┌───────────────────────────────────────┐
│ BATbern 2025 Update                   │
│                                       │
│ We're excited to announce the speaker │
│ lineup for BATbern 2025!              │
│                                       │
│ Featured Speakers:                    │
│ • Dr. Sarah Miller - AI in Arch       │
│ • Prof. John Chen - Sustainability    │
│ • ... (5 more)                        │
│                                       │
│ [View Full Program]                   │
└───────────────────────────────────────┘

[Send Test Email] [Close]
```

#### 2.3 Unassigned Speakers List Enhancement
**File:** Update `docs/wireframes/story-3.1-speaker-matching-interface.md` (Section 2)

**Purpose:** Show real-time progress of timing assignment (AC12)

**Add to Speaker Pool Sidebar:**

**Header with Badge:**
```
┌─────────────────────────────────────┐
│ Assign Timings         🔶 5 Remaining │
│                                     │
│ Filters: [All] [●Assigned] [Unassigned] │
│                                     │
│ Progress: ████████░░ 3 of 8 (37%)  │
└─────────────────────────────────────┘
```

**Speaker Cards with Visual Distinction:**
```
Unassigned Speakers (orange border):
┌─────────────────────────────────┐
│ 🔶 Dr. Sarah Miller             │
│ Topic: AI in Architecture       │
│ Status: Accepted, no timing     │
│ [View Preferences] [Assign]     │
└─────────────────────────────────┘

Assigned Speakers (checkmark):
┌─────────────────────────────────┐
│ ✓ Prof. John Chen               │
│ Topic: Sustainable Design       │
│ Assigned: June 10, 14:00-15:30  │
│ [Edit Timing] [Details]         │
└─────────────────────────────────┘
```

**Success State (all assigned):**
```
┌─────────────────────────────────────┐
│ ✓ All Timings Assigned!             │
│                                     │
│ 8 of 8 sessions (100%)              │
│                                     │
│ 🎉 Celebration animation here       │
│                                     │
│ Next step: Proceed to Publishing    │
│ [Go to Publishing Tab →]            │
└─────────────────────────────────────┘
```

#### 2.4 CDN Invalidation Status Display
**File:** Update `docs/wireframes/story-2.3-basic-publishing-engine.md` (Section 4)

**Purpose:** Show CDN cache invalidation feedback (AC25)

**Add to Publishing Progress:**

**During Publishing:**
```
Publishing in Progress...

┌─────────────────────────────────────┐
│ 1. ✓ Content saved                  │
│ 2. ✓ Version v3 created             │
│ 3. ⏳ CDN cache invalidating...      │
│    CloudFront ID: E2X7XYZ123        │
│    Estimated time: 30-60 seconds    │
│ 4. ⏳ Preparing notifications...     │
└─────────────────────────────────────┘
```

**Success State:**
```
✓ Published Successfully

Content is now live at: batbern.ch/events/2025

Performance:
  ✓ Cache invalidation completed in 42s
  ✓ Newsletter sent to 247 subscribers
  ✓ Version v3 deployed

[View Live Page] [View Analytics]
```

**Error State (CDN failure):**
```
⚠️ Published with Warnings

Content saved successfully, but:

  ⚠️ CDN cache invalidation failed
     CloudFront error: Throttling limit reached

     Impact: Content may be cached for up to 24h.
     New visitors will see updated content immediately.
     Returning visitors may see cached version.

  ✓ Newsletter sent successfully

[Retry Invalidation] [View Details] [Continue]
```

**Version History with CDN Status:**
```
Version History:

v3 | Published 2 mins ago  | ✓ CDN cleared    | [Rollback]
v2 | Published 1 day ago   | ⚠️ CDN partial   | [Rollback]
v1 | Published 3 days ago  | ✓ CDN cleared    | [Rollback]
```

### Priority 3: NICE TO HAVE (Polish)

#### 3.1 Accessibility Features
- Keyboard navigation flows for drag-drop (arrow keys + Enter)
- ARIA labels for all interactive elements
- Focus states clearly visible
- Screen reader announcements for status changes

#### 3.2 Responsive Design Breakpoints
- Desktop: Full three-column layout
- Tablet: Collapsible sidebar, two-column main area
- Mobile: Single column, bottom sheet for speaker pool

#### 3.3 Celebration Animations
- Confetti when last session assigned
- Progress bar fill animation
- Success badge pulse effect

## Updated Wireframe Structure

```
docs/wireframes/
├── story-3.1-speaker-matching-interface.md (UPDATE - Priority 1, CRITICAL)
│   ├── Status: Shows correct architecture (standalone page) but incomplete
│   ├── Action: Enhance with navigation, preferences, conflicts, success states
│   └── OR create new story-5.7-slot-assignment-page.md with complete specification
│
├── story-2.3-basic-publishing-engine.md (UPDATE - Priority 1, CRITICAL)
│   ├── Add: Session timing validation item
│   ├── Add: [Assign Timings] button linking to /slot-assignment
│   ├── Add: Subscriber notification UI
│   └── Add: CDN invalidation status
│
├── story-5.7-navigation-integration.md (NEW - Priority 1, CRITICAL)
│   ├── Entry points from Speakers, Overview, Publishing tabs
│   └── Return navigation and success state flows
│
├── story-5.7-conflict-resolution-modal.md (NEW - Priority 1, CRITICAL)
│   └── Conflict detection and resolution flows
│
├── story-5.7-speaker-preference-panel.md (NEW - Priority 1, CRITICAL)
│   └── Preference display and match indicators
│
├── story-5.7-bulk-auto-assignment.md (NEW - Priority 2)
│   └── Auto-assignment algorithm and preview
│
└── story-5.7-responsive-design.md (NEW - Priority 3)
    └── Mobile and tablet adaptations for slot assignment page
```

## Acceptance Criteria Coverage

### Fully Covered by Existing Wireframes ✅
- AC14-AC17: Publishing phases (story-2.3)
- AC18: Manual publish buttons (story-2.3)
- AC19: Auto-publish scheduling (story-2.3)
- AC20: Preview mode (story-2.3)
- AC21: Content validation (story-2.3)
- AC26: Version tracking (story-2.3)
- AC27: Rollback capability (story-2.3)

### Covered by New Priority 1 Wireframes ✅
- AC5: Drag-and-drop UI (story-3.1 or story-5.7-slot-assignment-page)
- AC6: Visual timeline (story-3.1 or story-5.7-slot-assignment-page)
- AC7: Speaker preferences (story-5.7-speaker-preference-panel)
- AC8: A/V needs (story-5.7-speaker-preference-panel)
- AC9: Conflict warnings (story-5.7-conflict-resolution-modal)
- AC11: Preference match highlighting (story-5.7-speaker-preference-panel + slot assignment page)
- AC12: Unassigned list (story-3.1 or story-5.7-slot-assignment-page with enhancements)

### Covered by New Priority 2 Wireframes ✅
- AC13: Bulk auto-assignment (story-5.7-bulk-auto-assignment)
- AC24: Notify subscribers (update to story-2.3)
- AC25: CDN invalidation (update to story-2.3)

### Partially Covered ⚠️
- AC22-AC23: Progressive disclosure (mode selection shown, needs phase-specific preview examples)
- AC29: Preview content based on mode (preview pane shown, needs mode-specific content examples)

### Not Yet Covered ❌
- AC10: Optimal order suggestion (could be part of bulk auto-assignment)
- AC1-AC4: Backend timing assignment APIs (technical, not UI wireframes)

## Implementation Recommendations

### Phase 1: Core Wireframes (Week 1)
1. Update `story-3.1-speaker-matching-interface.md` OR create `story-5.7-slot-assignment-page.md` (dedicated page)
2. Update `story-2.3-basic-publishing-engine.md` with session timing validation
3. Create `story-5.7-navigation-integration.md` (entry/exit points for slot assignment page)
4. Create `story-5.7-conflict-resolution-modal.md`
5. Create `story-5.7-speaker-preference-panel.md`

**Rationale:** These are critical blocking wireframes. Without these, developers cannot implement BAT-11 as a standalone page with proper navigation integration.

### Phase 2: Enhanced UX (Week 2)
1. Create `story-5.7-bulk-auto-assignment.md`
2. Update `story-2.3` with subscriber notification UI
3. Update `story-2.3` with CDN status display
4. Update `story-3.1` with unassigned count enhancements

**Rationale:** Enhances user experience but core functionality can work without these.

### Phase 3: Polish (Week 3)
1. Add responsive design wireframe
2. Add accessibility annotations
3. Add animation specifications

**Rationale:** Nice-to-have improvements for production quality.

## Success Criteria

**Wireframes Complete When:**
- ✅ All AC1-AC29 have visual representation
- ✅ Story 5.8 tab integration clearly shown
- ✅ Navigation flow within tabs specified
- ✅ Conflict resolution workflow designed
- ✅ Speaker preference UI detailed
- ✅ Session timing validation in Publishing tab
- ✅ All wireframes use consistent patterns and terminology

## Design Principles

**1. Progressive Disclosure:**
- Don't overwhelm with all features at once
- Show complexity only when needed (preferences drawer on demand)

**2. Clear Context:**
- Always show which tab user is in
- URL state reflects current view
- Breadcrumbs or tab highlights show location

**3. Immediate Feedback:**
- Visual confirmation of all actions
- Real-time progress indicators
- Success/error states clearly visible

**4. Error Prevention:**
- Validate before allowing problematic actions
- Show warnings for preference mismatches
- Block publish if prerequisites not met

**5. Consistency:**
- Same color coding: ✓ green, ⚠️ yellow, ✗ red
- Same terminology: sessions (not slots), timing (not assignment)
- Same interaction patterns: drawers from right, modals centered
- Same button styles: primary actions on right

## Notes for Implementation

**Story 5.8 Component Reuse:**
- `SpeakersSessionsTable` - UI patterns can inspire session grid design for new page
- `EventPublishingTab` - Add session timing validation (Gap 2)
- Material-UI components - Reuse drawer pattern for speaker preferences
- Drag-drop patterns - Apply similar DnD UX as existing speaker status lanes

**Routing:**
```typescript
// New route for BAT-11
/organizer/events/:eventCode/slot-assignment

// Integrates with existing Story 5.8 routes
/organizer/events/:eventCode              // Event page with tabs
/organizer/events/:eventCode?tab=speakers // Speakers tab
/organizer/events/:eventCode?tab=publishing // Publishing tab
```

**Component Architecture:**
- New page component: `SlotAssignmentPage.tsx`
- Standalone route (not nested in tabs)
- Full-screen layout for focused workflow
- Navigation links back to event page tabs

**Navigation Integration:**
- Entry points: Speakers tab, Overview tab, Publishing tab validation
- Return navigation: Breadcrumb, [Back to Event] button, success banner link
- No URL state complexity - separate page simplifies state management

**Testing Considerations:**
- E2E tests for drag-drop on dedicated page
- Navigation tests: reach page from multiple entry points, return navigation
- Conflict detection modal tests
- Validation blocking tests (prevent Phase 3 publish without timings)
- Integration tests: verify session timing updates persist
- Preference matching algorithm tests

## Open Questions

None.

**Architecture Decision Resolved:**
- **Question:** Should slot assignment be integrated into Sessions view (tab) or be a separate page?
- **Decision:** Separate page at `/organizer/events/:eventCode/slot-assignment`
- **Rationale:** Workflow complexity, cognitive load, focused task context, screen real estate requirements
- **User Confirmation:** Approved by product owner

## Sign-Off

**UX Expert (Sally):** Wireframe review plan complete with architecture decision.

**Key Deliverables:**
1. Dedicated slot assignment page wireframe (update story-3.1 or create new)
2. Publishing tab session timing validation (update story-2.3)
3. Navigation integration wireframes (new story-5.7-navigation-integration.md)
4. Conflict resolution modal (new story-5.7-conflict-resolution-modal.md)
5. Speaker preferences panel (new story-5.7-speaker-preference-panel.md)

**Next Steps:**
1. UX designer creates/updates wireframes based on this plan
2. Present wireframes to development team for implementation estimation
3. Update Story BAT-11 to reflect separate page architecture
4. Add route definition to web-frontend routing configuration
