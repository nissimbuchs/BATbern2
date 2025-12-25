# Story 5.7 (BAT-11): Dedicated Slot Assignment Page - Wireframe

**Story**: BAT-11 - Slot Assignment & Progressive Publishing
**Screen**: Session Timing Assignment
**Route**: `/organizer/events/:eventCode/slot-assignment`
**User Role**: Organizer
**Related FR**: FR17 (Speaker Matching), FR19 (Progressive Publishing)

---

## Overview

This is a **dedicated full-page workflow** for assigning session timings to placeholder sessions. Organizers use drag-and-drop to assign accepted speakers to specific time slots, with real-time preference matching and conflict detection.

**Key Difference from Story 3.1**: This is specifically for assigning **timings** to sessions (start_time, end_time, room), not initial speaker matching. Sessions already exist as placeholders when speakers accept invitations.

---

## Full-Page Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ Event Management > BATbern 2025 > Slot Assignment          [Back to Event]   [Help]         │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                               │
│  Assign Session Timings - BATbern 2025                              Status: In Planning      │
│                                                                                               │
│  ┌─── SPEAKERS ──────────────────┐  ┌─── SESSION TIMELINE ───────────────────────────────┐  │
│  │         🔶 5 Unassigned         │  │                                                    │  │
│  │                                 │  │  June 10, 2025                                     │  │
│  │  Progress: 3 of 8 (37%)        │  │                                                    │  │
│  │  ████████░░░░░░░░               │  │        Room A     Room B    Main Hall             │  │
│  │                                 │  │  ──────────────────────────────────────────────   │  │
│  │  [All] [●Assigned] [○Unassigned]│  │  08:00                                            │  │
│  │  ─────────────────────────────  │  │  08:30                                            │  │
│  │                                 │  │  09:00  ┌────────────┬───────────┬──────────────┐ │  │
│  │  🔶 Dr. Sarah Miller  ☀️ 🌙     │  │         │ Keynote:   │           │              │ │  │
│  │  AI in Architecture             │  │         │ Future of  │ [Empty]   │   [Empty]    │ │  │
│  │  Swiss Re                       │  │         │ Arch       │           │              │ │  │
│  │  [View Preferences]             │  │         │ ────────── │           │              │ │  │
│  │  ↓ Drag to assign →            │  │  09:30  │ John Chen  │           │              │ │  │
│  │  ─────────────────────────────  │  │         └────────────┴───────────┴──────────────┘ │  │
│  │                                 │  │  10:00  ┌────────────┬───────────┬──────────────┐ │  │
│  │  🔶 Prof. Anna Meyer   ☀️       │  │         │ Workshop 1:│           │              │ │  │
│  │  Sustainable Design             │  │         │ (No timing)│ [Empty]   │ Panel:       │ │  │
│  │  Credit Suisse                  │  │  10:30  │ Grayed out │           │ Industry     │ │  │
│  │  [View Preferences]             │  │         │ Dashed ◻   │           │ Trends       │ │  │
│  │  ↓ Drag to assign →            │  │         │            │           │ ────────     │ │  │
│  │  ─────────────────────────────  │  │  11:00  │  Drop here │           │ 3 speakers   │ │  │
│  │                                 │  │         └────────────┴───────────┴──────────────┘ │  │
│  │  🔶 Thomas Weber       🌙       │  │  11:30                                            │  │
│  │  DevOps Practices               │  │  12:00  LUNCH BREAK                               │  │
│  │  Swisscom                       │  │  13:00  ┌────────────┬───────────┬──────────────┐ │  │
│  │  [View Preferences]             │  │         │ Workshop 2:│ Workshop  │              │ │  │
│  │  ↓ Drag to assign →            │  │  13:30  │ (No timing)│ 3: Mobile │   [Empty]    │ │  │
│  │  ─────────────────────────────  │  │         │ Grayed out │ Dev       │              │ │  │
│  │                                 │  │         │ Dashed ◻   │ ─────     │              │ │  │
│  │  ✓ Peter Muller                │  │  14:00  │  Drop here │ M. Weber  │              │ │  │
│  │  Kubernetes Expert              │  │         └────────────┴───────────┴──────────────┘ │  │
│  │  Assigned: Jun 10, 14:00       │  │  14:30                                            │  │
│  │  [Edit Timing] [Details]        │  │  15:00  ┌────────────┬───────────┬──────────────┐ │  │
│  │  ─────────────────────────────  │  │         │ Closing:   │           │              │ │  │
│  │                                 │  │  15:30  │ (No timing)│ [Empty]   │   [Empty]    │ │  │
│  │  ✓ Marc Baum                   │  │         │ Grayed out │           │              │ │  │
│  │  Cloud Architecture             │  │         │ Dashed ◻   │           │              │ │  │
│  │  PostFinance                    │  │  16:00  │  Drop here │           │              │ │  │
│  │  Assigned: Jun 10, 16:30       │  │         └────────────┴───────────┴──────────────┘ │  │
│  │  [Edit Timing] [Details]        │  │  16:30                                            │  │
│  │                                 │  │                                                    │  │
│  │                                 │  │  ← Drag speakers from left to assign to slots →   │  │
│  │  [Auto-Assign All]             │  │                                                    │  │
│  └─────────────────────────────────┘  └────────────────────────────────────────────────────┘  │
│                                                                                               │
│  ┌─── QUICK ACTIONS ─────────────────────────────────────────────────────────────────────┐  │
│  │  Sessions: 8 total  •  3 assigned  •  5 pending                                        │  │
│  │  [Auto-Assign All] [Clear All Assignments] [Export Schedule PDF]                       │  │
│  └─────────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## State 2: Dragging Speaker (Preference Matching)

When a speaker is being dragged, session slots highlight with preference match colors:

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ Event Management > BATbern 2025 > Slot Assignment          [Back to Event]   [Help]         │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                               │
│  Assign Session Timings - BATbern 2025                              Status: In Planning      │
│                                                                                               │
│  ┌─── SPEAKERS ──────────────────┐  ┌─── SESSION TIMELINE ───────────────────────────────┐  │
│  │         🔶 5 Unassigned         │  │                                                    │  │
│  │                                 │  │  Dragging: Dr. Sarah Miller (prefers afternoons)   │  │
│  │  🔶 Dr. Sarah Miller  ☀️ 🌙    │  │                                                    │  │
│  │  ┌─────────────────────────┐   │  │        Room A     Room B    Main Hall             │  │
│  │  │ [DRAGGING...]          │   │  │  ──────────────────────────────────────────────   │  │
│  │  │ AI in Architecture     │   │  │  09:00  ┌────────────┬───────────┬──────────────┐ │  │
│  │  │                        │   │  │         │ 🔴 25%     │ 🔴 20%    │  🔴 15%      │ │  │
│  │  └─────────────────────────┘   │  │         │ Poor Match │ Poor      │  Poor        │ │  │
│  │                                 │  │  09:30  │ Morning    │           │              │ │  │
│  │  ─────────────────────────────  │  │         └────────────┴───────────┴──────────────┘ │  │
│  │                                 │  │  ...                                               │  │
│  │  🔶 Prof. Anna Meyer            │  │  13:00  ┌────────────┬───────────┬──────────────┐ │  │
│  │  Sustainable Design             │  │         │ 🟡 65%     │ 🟢 85%    │  🟢 90%      │ │  │
│  │  Credit Suisse                  │  │  13:30  │ Acceptable │ Strong    │  Strong      │ │  │
│  │  [View Preferences]             │  │         │ Afternoon  │ Match!    │  Match!      │ │  │
│  │  ↓ Drag to assign →            │  │  14:00  │            │ Natural   │  Projector   │ │  │
│  │                                 │  │         │            │ light ✓   │  Available ✓ │ │  │
│  │                                 │  │         └────────────┴───────────┴──────────────┘ │  │
│  │                                 │  │                                                    │  │
│  │                                 │  │  Hover tooltip: "85% Strong Match - Afternoon      │  │
│  │                                 │  │  slot (preferred), Room B has natural light,       │  │
│  │                                 │  │  projector available"                              │  │
│  └─────────────────────────────────┘  └────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────┘

Legend:
  🟢 Green (80-100%): Strong match - preferred time, good room setup, A/V available
  🟡 Yellow (50-79%): Acceptable match - some preferences met
  🔴 Red (<50%): Poor match - conflicts with preferences or avoid times
```

---

## State 3: Success State (All Timings Assigned)

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│ Event Management > BATbern 2025 > Slot Assignment          [Back to Event]   [Help]         │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                               │
│  ┌───────────────────────────────────────────────────────────────────────────────────────┐  │
│  │  ✓ All Session Timings Assigned!                                                      │  │
│  │                                                                                         │  │
│  │  8 of 8 sessions complete. Ready to proceed to publishing.                            │  │
│  │                                                                                         │  │
│  │  [View Publishing Options →]                                                           │  │
│  └───────────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                               │
│  Assign Session Timings - BATbern 2025                              Status: Ready           │
│                                                                                               │
│  ┌─── SPEAKERS ──────────────────┐  ┌─── SESSION TIMELINE ───────────────────────────────┐  │
│  │         ✓ All Assigned          │  │                                                    │  │
│  │                                 │  │  June 10, 2025 - Full Schedule                     │  │
│  │  Progress: 8 of 8 (100%)       │  │                                                    │  │
│  │  ████████████████████          │  │        Room A     Room B    Main Hall             │  │
│  │                                 │  │  ──────────────────────────────────────────────   │  │
│  │  [All] [●Assigned]              │  │  09:00  ┌────────────┬───────────┬──────────────┐ │  │
│  │  ─────────────────────────────  │  │         │ Keynote    │ Workshop  │              │ │  │
│  │                                 │  │         │ John Chen  │ A. Meyer  │   Panel      │ │  │
│  │  ✓ Dr. Sarah Miller            │  │  09:30  │ ────────   │ ────────  │   ──────     │ │  │
│  │  AI in Architecture             │  │         │ 09:00-10:30│ 09:00-10:30│ 09:00-11:00 │ │  │
│  │  Assigned: Jun 10, 13:30       │  │         └────────────┴───────────┴──────────────┘ │  │
│  │  Match: 85%  [Edit] [Details]   │  │  10:00                                            │  │
│  │  ─────────────────────────────  │  │  10:30  ┌────────────┬───────────┬──────────────┐ │  │
│  │                                 │  │         │ Workshop 1 │           │              │ │  │
│  │  ✓ Prof. Anna Meyer            │  │  11:00  │ S. Miller  │ Workshop  │   Closing    │ │  │
│  │  Sustainable Design             │  │         │ ────────   │ 4         │   ────────   │ │  │
│  │  Assigned: Jun 10, 09:00       │  │  11:30  │ 11:00-12:00│ T. Weber  │   M. Baum    │ │  │
│  │  Match: 90%  [Edit] [Details]   │  │         │            │ ────────  │   11:00-12:00│ │  │
│  │  ─────────────────────────────  │  │  12:00  └────────────┴───────────┴──────────────┘ │  │
│  │                                 │  │                                                    │  │
│  │  [8 more assigned speakers...] │  │  ... (full schedule with all 8 sessions assigned) │  │
│  │                                 │  │                                                    │  │
│  │  [Export Schedule PDF]         │  │  Average match score: 78% (Good)                  │  │
│  │  [Clear All] [Auto-Optimize]    │  │                                                    │  │
│  └─────────────────────────────────┘  └────────────────────────────────────────────────────┘  │
│                                                                                               │
│  ┌─── NEXT STEPS ─────────────────────────────────────────────────────────────────────────┐  │
│  │  ✓ Session timings complete                                                            │  │
│  │  Next: Proceed to Publishing tab to publish the agenda                                 │  │
│  │  [Go to Publishing Tab →]                                                              │  │
│  └─────────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

### Speaker Pool Sidebar (Left)
- **Progress Indicator**: Visual progress bar showing X/Y sessions assigned
- **Unassigned Badge**: Orange "🔶 N Unassigned" count
- **Filter Buttons**: [All] [Assigned] [Unassigned] to show/hide speakers
- **Speaker Cards**:
  - **Unassigned**: Orange border, preference icons (☀️ morning, 🌙 evening)
  - **Assigned**: Green checkmark, shows assigned time and match score
  - **Draggable**: Visual grab handle for drag-and-drop
  - **[View Preferences]**: Opens speaker preferences drawer
- **Actions**: [Auto-Assign All] for bulk algorithmic assignment

### Session Timeline Grid (Center)
- **Multi-Day View**: Tabs for different days (if multi-day event)
- **Time Slots**: Hourly rows from 08:00 to 20:00
- **Room Columns**: Separate column per room (Room A, Room B, Main Hall)
- **Session Cells**:
  - **Placeholder (No Timing)**: Gray background, dashed border, "Drop here" prompt
  - **Assigned**: Speaker name, session title, time range, colored border
  - **Empty**: Available drop zone for new sessions
- **Preference Highlights** (during drag):
  - 🟢 **Green (80-100%)**: Strong match - preferred time, good room
  - 🟡 **Yellow (50-79%)**: Acceptable match - some preferences met
  - 🔴 **Red (<50%)**: Poor match - conflicts with preferences
- **Hover Tooltips**: Show detailed match score breakdown

### Quick Actions Panel (Bottom)
- **Session Summary**: "8 total • 3 assigned • 5 pending"
- **[Auto-Assign All]**: Opens algorithm selection modal
- **[Clear All Assignments]**: Confirmation dialog, removes all timings
- **[Export Schedule PDF]**: Downloads printable schedule

### Navigation
- **Breadcrumb**: Event Management > BATbern 2025 > Slot Assignment
- **[Back to Event]**: Returns to `/organizer/events/:eventCode`
- **Success State**: [View Publishing Options →] links to `?tab=publishing`

---

## Functional Requirements Met

- **AC5**: Drag-and-drop UI for timing assignment ✅
- **AC6**: Visual timeline showing all slots ✅
- **AC7**: Display speaker preferences (icons on cards, drawer on click) ✅
- **AC8**: Track A/V needs (shown in preferences drawer) ✅
- **AC9**: Conflict warnings (modal triggered on conflict) ✅ (see story-5.7-conflict-resolution-modal.md)
- **AC10**: Optimal order suggestion (via Auto-Assign feature) ✅ (see story-5.7-bulk-auto-assignment.md)
- **AC11**: Preference match highlighting (green/yellow/red during drag) ✅
- **AC12**: Unassigned speakers list with real-time updates ✅

---

## User Interactions

### Primary Workflow
1. **View Unassigned Speakers**: Browse speaker pool sidebar filtered to unassigned
2. **Drag Speaker Card**: Click and drag speaker from pool
3. **See Preference Matches**: Session grid highlights with match scores
4. **Hover for Details**: Tooltip shows why match is strong/weak
5. **Drop on Slot**: Assign speaker to specific time/room
6. **Handle Conflicts**: If conflict detected, modal appears (see conflict wireframe)
7. **Track Progress**: Progress bar updates showing N/M complete
8. **Complete Assignment**: When all slots filled, success banner appears
9. **Proceed to Publishing**: Click [View Publishing Options] to publish agenda

### Advanced Features
- **View Preferences**: Click [View Preferences] to open drawer (see preferences wireframe)
- **Auto-Assign**: Click [Auto-Assign All] to use algorithm (see bulk-assign wireframe)
- **Edit Timing**: Click [Edit Timing] on assigned speaker to change time/room
- **Filter Speakers**: Toggle [All]/[Assigned]/[Unassigned] to focus view
- **Export Schedule**: Click [Export Schedule PDF] to download printable agenda

---

## Technical Notes

### Drag-and-Drop Implementation
- **Library**: Use `react-beautiful-dnd` or `@dnd-kit` for accessible drag-drop
- **Visual Feedback**:
  - Ghost image of speaker card during drag
  - Drop zones highlighted with preference colors
  - Smooth animations on drop
- **Optimistic Updates**: Immediate UI update before API confirmation
- **Rollback on Error**: If API fails, revert to previous state with error toast

### Preference Matching Algorithm
- **Match Scoring**:
  - Time preference: ±40 points (morning/afternoon/evening match)
  - Avoid times: -50 points if conflicts
  - Room setup: ±20 points (natural light, flip chart, etc.)
  - A/V requirements: ±15 points (projector, mic, etc.)
  - Duration preference: ±10 points (1h vs 1.5h session)
- **Color Mapping**:
  - 80-100%: Green (strong match)
  - 50-79%: Yellow (acceptable)
  - <50%: Red (poor match)

### Real-Time Updates
- **WebSocket Connection**: Listen for timing changes from other organizers
- **Conflict Detection**: Check on drop:
  - Room overlap (same room, overlapping time)
  - Speaker double-booking (same speaker, overlapping sessions)
  - Speaker unavailability (conflicts with avoid times)
- **Lock Mechanism**: Prevent concurrent edits to same session

### API Integration
- **GET /api/v1/events/:eventCode/sessions**: Load all sessions with timing status
- **GET /api/v1/events/:eventCode/speaker-pool**: Load accepted speakers with preferences
- **PUT /api/v1/sessions/:sessionId/timing**: Assign timing to session
  - Payload: `{ startTime, endTime, room }`
  - Returns: Updated session with timing
- **POST /api/v1/sessions/timing/validate**: Check for conflicts before assignment
  - Payload: `{ sessionId, startTime, endTime, room }`
  - Returns: `{ valid: boolean, conflicts: [...] }`

---

## Navigation Map

### Entry Points (How to Reach This Page)

**From Speakers Tab** (`/organizer/events/:eventCode?tab=speakers`):
- **Button**: [Assign Session Timings →]
- **Location**: Top of Speakers tab, below summary metrics
- **Condition**: Visible when ≥1 accepted speaker exists
- **Context**: Opens slot assignment page with all accepted speakers loaded

**From Overview Tab** (`/organizer/events/:eventCode?tab=overview`):
- **Card**: "Slot Assignment" quick action card
- **Status Badge**: "Not Started" / "In Progress (5/8)" / "Complete (8/8)"
- **Button**: [Assign Timings]
- **Context**: Opens slot assignment page

**From Publishing Tab** (`/organizer/events/:eventCode?tab=publishing`):
- **Validation Item**: "Session Timings" validation row
- **Status**: "Incomplete (5/8 sessions)"
- **Button**: [Assign Timings] or [Assign] on specific session
- **Context**: Opens slot assignment page, optionally focused on specific session

### Exit Points (Navigation From This Page)

**Success Completion**:
- **Button**: [View Publishing Options →]
- **Target**: `/organizer/events/:eventCode?tab=publishing`
- **Context**: All timings assigned, proceed to publish agenda

**Return to Event**:
- **Button**: [Back to Event] (top-right)
- **Target**: `/organizer/events/:eventCode`
- **Context**: Returns to event page, preserves timing assignments

**Breadcrumb Navigation**:
- **Event Management**: Returns to `/organizer/events` (event list)
- **BATbern 2025**: Returns to `/organizer/events/:eventCode` (event details)
- **Slot Assignment**: Current page (not clickable)

---

## Accessibility

- **Keyboard Navigation**:
  - Tab through speaker cards
  - Arrow keys to navigate session grid
  - Enter to select speaker, Arrow keys + Enter to drop
  - Escape to cancel drag operation
- **ARIA Labels**:
  - `aria-grabbed="true"` when speaker selected
  - `aria-dropeffect="move"` on valid drop zones
  - `aria-live="polite"` for progress updates
  - `aria-describedby` for preference match tooltips
- **Screen Reader Announcements**:
  - "Grabbed Dr. Sarah Miller" on drag start
  - "Dropped on Workshop 2, 13:30-15:00, Room A. Match: 85% Strong" on drop
  - "Conflict detected: Room overlap" if conflict occurs
  - "Progress: 4 of 8 sessions assigned" after assignment

---

## Mobile/Tablet Adaptation

**Tablet (768px - 1024px)**:
- Collapsible speaker sidebar (swipe from left edge)
- Session grid scrolls horizontally
- Two-column grid (combine rooms)

**Mobile (<768px)**:
- Bottom sheet for speaker pool (swipe up)
- Single-column timeline (stack rooms vertically)
- No drag-drop - use [Assign] button → picker modal
- Simplified match scoring (just icon: 🟢/🟡/🔴)

---

## Enhanced Progress Tracking (AC12)

### Progress Indicator States

**Speaker Pool Header - Initial State (0% Complete)**:
```
┌─── SPEAKERS ──────────────────┐
│      🔶 8 Unassigned           │
│                                │
│  Progress: 0 of 8 (0%)         │
│  ░░░░░░░░░░░░░░░░░░░░          │
│                                │
│  [All] [○Assigned] [●Unassigned]│
│  ──────────────────────────────│
```

**Speaker Pool Header - In Progress (37% Complete)**:
```
┌─── SPEAKERS ──────────────────┐
│      🔶 5 Remaining            │
│                                │
│  Progress: 3 of 8 (37%)        │
│  ███████░░░░░░░░░░░░░          │
│                                │
│  [All] [●Assigned] [○Unassigned]│
│  ──────────────────────────────│
```

**Speaker Pool Header - Nearly Complete (87% Complete)**:
```
┌─── SPEAKERS ──────────────────┐
│      🔶 1 Remaining            │
│                                │
│  Progress: 7 of 8 (87%)        │
│  ██████████████████░░          │
│                                │
│  [All] [●Assigned] [○Unassigned]│
│  ──────────────────────────────│
│                                │
│  ⚠️ Almost done!               │
│  Just 1 more session to assign │
│  ──────────────────────────────│
```

**Speaker Pool Header - Complete (100%)**:
```
┌─── SPEAKERS ──────────────────┐
│      ✓ All Assigned            │
│                                │
│  Progress: 8 of 8 (100%)       │
│  ████████████████████          │
│                                │
│  [●All] [Assigned] [Unassigned] │
│  ──────────────────────────────│
│                                │
│  🎉 Congratulations!           │
│  All sessions have timings     │
│  ──────────────────────────────│
```

---

### Speaker Card Visual Distinction

**Unassigned Speaker Card** (Orange Border, Draggable):
```
┌──────────────────────────────────────┐
│ 🔶 Dr. Sarah Miller  ☀️ 🌙           │  ← Orange badge
│ ┌──────────────────────────────────┐ │
│ │                                  │ │
│ │ AI in Modern Architecture        │ │
│ │ Swiss Re                         │ │
│ │                                  │ │
│ │ Preferences:                     │ │
│ │ • Time: Afternoon preferred      │ │
│ │ • A/V: Projector + Mic required  │ │
│ │                                  │ │
│ │ [View Full Preferences →]        │ │
│ │                                  │ │
│ └──────────────────────────────────┘ │
│                                      │
│ Status: Awaiting timing assignment   │
│                                      │
│ ↓ Drag to assign to session →       │
└──────────────────────────────────────┘

Border: 2px solid #ff9800 (Orange)
Background: #fff3e0 (Light orange tint)
Cursor: grab (draggable indicator)
```

**Assigned Speaker Card** (Green Border, Checkmark):
```
┌──────────────────────────────────────┐
│ ✓ Peter Muller                       │  ← Green checkmark
│ ┌──────────────────────────────────┐ │
│ │                                  │ │
│ │ Kubernetes Expert                │ │
│ │ PostFinance                      │ │
│ │                                  │ │
│ │ Assigned:                        │ │
│ │ • June 10, 14:00-15:30          │ │
│ │ • Conference Room B             │ │
│ │ • Match: 92% 🟢 (Strong)        │ │
│ │                                  │ │
│ │ [Edit Timing] [View Details]     │ │
│ │                                  │ │
│ └──────────────────────────────────┘ │
│                                      │
│ Status: ✓ Timing assigned            │
│                                      │
│ Assigned 5 minutes ago               │
└──────────────────────────────────────┘

Border: 2px solid #4caf50 (Green)
Background: #e8f5e9 (Light green tint)
Cursor: default (not draggable)
```

---

### Filter States

**Filter: [All]** - Shows both assigned and unassigned speakers

```
┌─── SPEAKERS ──────────────────┐
│      🔶 5 Remaining            │
│  Progress: 3 of 8 (37%)        │
│  [●All] [Assigned] [Unassigned] │
│  ──────────────────────────────│
│  Showing: 8 speakers           │
│  ──────────────────────────────│
│                                │
│  ✓ Peter Muller                │  ← Assigned (green)
│  Kubernetes Expert             │
│  Assigned: Jun 10, 14:00       │
│  ──────────────────────────────│
│                                │
│  🔶 Dr. Sarah Miller  ☀️ 🌙    │  ← Unassigned (orange)
│  AI in Architecture            │
│  ↓ Drag to assign →           │
│  ──────────────────────────────│
│                                │
│  ✓ Marc Baum                   │  ← Assigned (green)
│  Cloud Architecture            │
│  Assigned: Jun 10, 16:30       │
│  ──────────────────────────────│
│                                │
│  🔶 Prof. Anna Meyer   ☀️      │  ← Unassigned (orange)
│  Sustainable Design            │
│  ↓ Drag to assign →           │
│  ──────────────────────────────│
│                                │
│  ... (4 more speakers)         │
│                                │
└─────────────────────────────────┘
```

**Filter: [Assigned]** - Shows only assigned speakers

```
┌─── SPEAKERS ──────────────────┐
│      ✓ 3 Assigned              │
│  Progress: 3 of 8 (37%)        │
│  [All] [●Assigned] [Unassigned] │
│  ──────────────────────────────│
│  Showing: 3 of 8 speakers      │
│  ──────────────────────────────│
│                                │
│  ✓ Peter Muller                │
│  Kubernetes Expert             │
│  Assigned: Jun 10, 14:00       │
│  Room B • Match: 92% 🟢        │
│  [Edit Timing] [Details]        │
│  ──────────────────────────────│
│                                │
│  ✓ Marc Baum                   │
│  Cloud Architecture            │
│  Assigned: Jun 10, 16:30       │
│  Main Hall • Match: 85% 🟢     │
│  [Edit Timing] [Details]        │
│  ──────────────────────────────│
│                                │
│  ✓ John Chen                   │
│  DevOps Practices              │
│  Assigned: Jun 11, 09:00       │
│  Room A • Match: 78% 🟢        │
│  [Edit Timing] [Details]        │
│  ──────────────────────────────│
│                                │
└─────────────────────────────────┘
```

**Filter: [Unassigned]** - Shows only speakers awaiting timing

```
┌─── SPEAKERS ──────────────────┐
│      🔶 5 Remaining            │
│  Progress: 3 of 8 (37%)        │
│  [All] [Assigned] [●Unassigned] │
│  ──────────────────────────────│
│  Showing: 5 of 8 speakers      │
│  ──────────────────────────────│
│                                │
│  🔶 Dr. Sarah Miller  ☀️ 🌙    │
│  AI in Architecture            │
│  Swiss Re                      │
│  [View Preferences]            │
│  ↓ Drag to assign →           │
│  ──────────────────────────────│
│                                │
│  🔶 Prof. Anna Meyer   ☀️      │
│  Sustainable Design            │
│  Credit Suisse                 │
│  [View Preferences]            │
│  ↓ Drag to assign →           │
│  ──────────────────────────────│
│                                │
│  🔶 Thomas Weber       🌙      │
│  DevOps Practices              │
│  Swisscom                      │
│  [View Preferences]            │
│  ↓ Drag to assign →           │
│  ──────────────────────────────│
│                                │
│  ... (2 more unassigned)       │
│                                │
│  [Auto-Assign All]             │
│  Let AI assign these 5 speakers│
│                                │
└─────────────────────────────────┘
```

---

### Real-Time Progress Updates

**On Assignment (Live Update)**:

```
User Action: Drags Dr. Sarah Miller to Workshop 2 slot (14:00-15:30, Room B)
System Response: Immediate visual updates across UI

1. Speaker Card Updates:
   🔶 Dr. Sarah Miller  →  ✓ Dr. Sarah Miller
   Border: Orange → Green
   Background: Light orange → Light green

2. Progress Bar Updates:
   3 of 8 (37%) →  4 of 8 (50%)
   ███████░░░░░░░░░░░░░  →  ██████████░░░░░░░░░░

3. Header Badge Updates:
   🔶 5 Remaining  →  🔶 4 Remaining

4. Session Grid Updates:
   Workshop 2 slot: "No timing" → "Dr. S. Miller • 14:00-15:30"
   Border: Dashed gray → Solid blue

5. Toast Notification:
   ✓ Workshop 2 assigned to Dr. Sarah Miller (Match: 85%)

6. Screen Reader Announcement:
   "Workshop 2 assigned to Dr. Sarah Miller at 14:00 to 15:30, Room B.
    Match score: 85 percent, strong match.
    Progress: 4 of 8 sessions assigned, 50 percent complete."
```

**Animation Timing**:
- Progress bar fill: 300ms ease-in-out
- Badge count update: 200ms fade transition
- Speaker card border color: 400ms transition
- Toast appears: Slide up 300ms, auto-dismiss after 3s

---

### Success State with Celebration

**Full Success Banner** (appears when 100% complete):

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  🎉 🎊 SUCCESS! All Session Timings Assigned! 🎊 🎉                             │
│                                                                                 │
│  ┌────────────────────────────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  Congratulations! You've successfully assigned timings to all 8        │    │
│  │  sessions for BATbern 2025.                                            │    │
│  │                                                                         │    │
│  │  ┌─── Summary Statistics ──────────────────────────────────────────┐   │    │
│  │  │                                                                 │   │    │
│  │  │  Total Sessions:         8 of 8 (100% complete) ✓              │   │    │
│  │  │  Average Match Score:    82% (High quality assignments)         │   │    │
│  │  │  Strong Matches (≥80%):  6 sessions                             │   │    │
│  │  │  Good Matches (50-79%):  2 sessions                             │   │    │
│  │  │  Conflicts Detected:     0 (All assignments valid)              │   │    │
│  │  │                                                                 │   │    │
│  │  │  Time to Complete:       12 minutes                             │   │    │
│  │  │                                                                 │   │    │
│  │  └─────────────────────────────────────────────────────────────────┘   │    │
│  │                                                                         │    │
│  │  ┌─── What's Next? ────────────────────────────────────────────────┐   │    │
│  │  │                                                                 │   │    │
│  │  │  ✓ Review the full agenda timeline below                       │   │    │
│  │  │  ✓ Make any final adjustments if needed                        │   │    │
│  │  │  ✓ Proceed to Publishing to make schedule public               │   │    │
│  │  │                                                                 │   │    │
│  │  │  Once published, attendees can view the full event schedule    │   │    │
│  │  │  and register for sessions.                                    │   │    │
│  │  │                                                                 │   │    │
│  │  └─────────────────────────────────────────────────────────────────┘   │    │
│  │                                                                         │    │
│  │  ┌────────────────────────────────────────────────────────────────┐    │    │
│  │  │ [View Publishing Options →]   [Review Timeline]   [Close]     │    │    │
│  │  └────────────────────────────────────────────────────────────────┘    │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  Confetti animation plays for 3 seconds 🎊                                     │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**Celebration Features**:
- **Confetti Animation**: CSS particles falling from top for 3 seconds
- **Success Sound**: Optional audio cue (can be disabled in settings)
- **Banner Color**: Gradient background (green to blue)
- **Auto-Scroll**: Page scrolls to show success banner at top
- **Persistent**: Banner remains visible until user clicks [Close]

**Primary CTA**:
- **[View Publishing Options →]**: Most prominent button
- Links to `/organizer/events/:eventCode?tab=publishing`
- Keyboard shortcut: Enter key activates this button

**Secondary Actions**:
- **[Review Timeline]**: Scrolls to session grid below
- **[Close]**: Dismisses banner (can be reopened via notice icon)

---

### Progress Milestones

**Milestone Notifications** (appear at key progress points):

**25% Complete (2 of 8 sessions)**:
```
ℹ️ Good progress! You've assigned 2 sessions. 6 more to go.
   [Continue] [Auto-Assign Remaining]
```

**50% Complete (4 of 8 sessions)**:
```
🎯 Halfway there! 4 sessions assigned, 4 remaining.
   Average match score so far: 85% (excellent!)
   [Continue] [Auto-Assign Remaining]
```

**75% Complete (6 of 8 sessions)**:
```
🔥 Almost done! Only 2 sessions left to assign.
   You're making great progress.
   [Continue] [Auto-Assign Remaining]
```

**100% Complete (8 of 8 sessions)**:
```
🎉 Success! All session timings assigned.
   [View Publishing Options →]
```

**Toast Position**: Bottom-right corner, auto-dismiss after 5 seconds

---

### Accessibility for Progress Tracking

**ARIA Live Regions**:
```html
<!-- Progress indicator -->
<div aria-live="polite" aria-atomic="true">
  Progress: 4 of 8 sessions assigned (50% complete)
</div>

<!-- Milestone notification -->
<div role="status" aria-live="polite">
  Halfway there! 4 sessions assigned, 4 remaining.
  Average match score: 85 percent.
</div>

<!-- Success banner -->
<div role="alert" aria-live="assertive">
  Success! All 8 session timings have been assigned.
  Ready to proceed to publishing.
</div>
```

**Screen Reader Announcements on Assignment**:
```
"Workshop 2 assigned to Dr. Sarah Miller.
Match score: 85 percent, strong match.
Progress: 4 of 8 sessions assigned, 50 percent complete.
4 sessions remaining."
```

**Keyboard Shortcuts**:
- `Ctrl + P`: View progress summary
- `Ctrl + F`: Toggle filter (All/Assigned/Unassigned)
- `Ctrl + A`: Auto-assign all remaining sessions

---

## Related Wireframes

- **story-5.7-navigation-integration.md**: Shows entry points from Speakers/Overview/Publishing tabs
- **story-5.7-conflict-resolution-modal.md**: Modal UI when conflict detected during assignment
- **story-5.7-speaker-preference-panel.md**: Drawer showing speaker preferences, A/V needs, match details
- **story-5.7-bulk-auto-assignment.md**: Auto-assignment algorithm modal and preview
- **story-2.3-basic-publishing-engine.md**: Publishing tab with session timing validation (updated)

---

## Version History

| Date       | Version | Changes                          | Author         |
|------------|---------|----------------------------------|----------------|
| 2025-12-25 | 1.0     | Initial wireframe creation       | Sally (UX Expert) |
