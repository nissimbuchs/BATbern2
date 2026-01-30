# Story 5.7 - Responsive Design Wireframe

**Story**: BAT-11 - Slot Assignment & Progressive Publishing
**Priority**: Phase 3 (NICE TO HAVE - Polish)
**Related Stories**: Story 5.8 (Unified Event Page)

## Purpose

This wireframe documents responsive design breakpoints and layout adaptations for the slot assignment workflow across desktop, tablet, and mobile devices. Ensures optimal user experience at all screen sizes with appropriate layout changes, touch targets, and interaction patterns.

## Design Principles

1. **Mobile First**: Design for smallest screen first, enhance for larger screens
2. **Content Priority**: Most important content visible without scrolling on all devices
3. **Touch Optimized**: Larger touch targets and spacing on mobile/tablet
4. **Progressive Enhancement**: Add features as screen real estate increases
5. **Performance**: Optimize images, lazy load off-screen content
6. **Consistency**: Same visual language and patterns across all breakpoints

## Breakpoint Strategy

### Breakpoint Definitions

```css
/* Mobile First Approach */

/* Extra Small (Mobile Portrait) */
@media (min-width: 0px) {
  /* Base styles - mobile portrait */
  /* 320px - 599px */
}

/* Small (Mobile Landscape) */
@media (min-width: 600px) {
  /* Mobile landscape, small tablets */
  /* 600px - 767px */
}

/* Medium (Tablet Portrait) */
@media (min-width: 768px) {
  /* Tablet portrait, large phones landscape */
  /* 768px - 1023px */
}

/* Large (Tablet Landscape / Small Desktop) */
@media (min-width: 1024px) {
  /* Tablet landscape, small desktops */
  /* 1024px - 1439px */
}

/* Extra Large (Desktop) */
@media (min-width: 1440px) {
  /* Standard desktop */
  /* 1440px - 1919px */
}

/* Extra Extra Large (Large Desktop) */
@media (min-width: 1920px) {
  /* Large desktop, 1080p+ monitors */
  /* 1920px+ */
}
```

### Material-UI Grid System

```typescript
// Using Material-UI breakpoints
const theme = createTheme({
  breakpoints: {
    values: {
      xs: 0,      // Extra small devices (portrait phones)
      sm: 600,    // Small devices (landscape phones)
      md: 768,    // Medium devices (tablets)
      lg: 1024,   // Large devices (desktops)
      xl: 1440,   // Extra large devices
      xxl: 1920,  // Extra extra large devices
    },
  },
});

// Responsive grid usage
<Grid container spacing={2}>
  <Grid item xs={12} md={4} lg={3}>
    {/* Speaker Pool - Full width mobile, 1/3 tablet, 1/4 desktop */}
  </Grid>
  <Grid item xs={12} md={8} lg={6}>
    {/* Session Grid - Full width mobile, 2/3 tablet, 1/2 desktop */}
  </Grid>
  <Grid item xs={12} lg={3}>
    {/* Quick Actions - Full width mobile/tablet, 1/4 desktop */}
  </Grid>
</Grid>
```

## 1. Mobile Portrait (320px - 599px)

### Layout Structure

```
┌─────────────────────────────────────┐
│ ═══ Header ═══════════════════      │ ← Sticky header
│ < Back to Event                     │   48px height
├─────────────────────────────────────┤
│ Assign Session Timings              │ ← Page title
│ BATbern 2025                        │   Subtitle
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ Progress: 3 of 8 (37%)          │ │ ← Sticky progress bar
│ │ ███████░░░░░░░░░░░░░             │ │   Below header
│ └─────────────────────────────────┘ │
├─────────────────────────────────────┤
│                                     │
│ ┌─── Speakers (Collapsed) ────────┐│ ← Accordion/expandable
│ │ 🔶 5 unassigned                  ││   Tap to expand
│ │ [Tap to show speakers ▼]        ││
│ └─────────────────────────────────┘│
│                                     │
│ ┌─── Session Timeline ─────────────┐│ ← Primary content area
│ │                                  ││   Scrollable
│ │ [Today: June 10]  [Tomorrow >]   ││   Day selector tabs
│ │                                  ││
│ │ ┌─────────────────────────────┐ ││
│ │ │ 09:00 - 10:30               │ ││ ← Session card
│ │ │ Keynote Opening             │ ││   Full width
│ │ │ Main Hall (200 seats)       │ ││   Min height 120px
│ │ │ ✓ Assigned: Dr. Smith       │ ││
│ │ │ Match: 🟢 92%               │ ││
│ │ └─────────────────────────────┘ ││
│ │                                  ││
│ │ ┌─────────────────────────────┐ ││
│ │ │ 11:00 - 12:30               │ ││
│ │ │ Workshop 1                   │ ││
│ │ │ Room A (50 seats)           │ ││
│ │ │ ○ Not assigned              │ ││
│ │ │ [Tap to assign]             │ ││
│ │ └─────────────────────────────┘ ││
│ │                                  ││
│ │ [Load more sessions...]         ││
│ └─────────────────────────────────┘│
│                                     │
│                                     │
│ ═══════════════════════════════════ │ ← Sticky bottom bar
│ [🏠 Home] [👥 Speakers] [⚡ Actions]│   Navigation tabs
└─────────────────────────────────────┘   56px height
```

### Speakers Panel (Expanded State)

```
User taps "Tap to show speakers":

┌─────────────────────────────────────┐
│ ═══ Header ═══════════════════      │
│ < Back to Timeline                  │ ← Back button returns to timeline
├─────────────────────────────────────┤
│ Speakers (5 unassigned)             │
├─────────────────────────────────────┤
│ [All] [●Assigned] [○Unassigned]     │ ← Filter chips
├─────────────────────────────────────┤
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 🔶 Dr. Sarah Miller             │ │ ← Speaker card
│ │ AI in Architecture              │ │   Full width
│ │ ☀️ Prefers: Afternoon           │ │   Min height 100px
│ │                                 │ │
│ │ [Tap to assign]                 │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 🔶 Prof. John Chen              │ │
│ │ Sustainable Design              │ │
│ │ ☀️ Prefers: Morning             │ │
│ │                                 │ │
│ │ [Tap to assign]                 │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ... (more speakers)                 │
│                                     │
└─────────────────────────────────────┘
```

### Assignment Flow (Mobile)

```
Step 1: User taps [Tap to assign] on speaker card

┌─────────────────────────────────────┐
│ ═══ Header ═══════════════════      │
│ < Cancel Assignment                 │
├─────────────────────────────────────┤
│ ✓ Dr. Sarah Miller - SELECTED       │ ← Sticky context bar
│ Tap a time slot to assign           │   Purple background
├─────────────────────────────────────┤
│                                     │
│ ┌─── Available Slots ──────────────┐│
│ │                                  ││
│ │ ┌─────────────────────────────┐ ││
│ │ │ 14:00 - 15:30               │ ││ ← Highlight match score
│ │ │ Workshop 2                   │ ││   Match colors visible
│ │ │ Room B (50 seats)           │ ││
│ │ │ Match: 🟢 85% Strong        │ ││
│ │ │ ☀️ Afternoon (preferred)     │ ││
│ │ │                             │ ││
│ │ │ [Tap to assign here]        │ ││
│ │ └─────────────────────────────┘ ││
│ │                                  ││
│ │ ┌─────────────────────────────┐ ││
│ │ │ 16:00 - 17:30               │ ││
│ │ │ Evening Panel                │ ││
│ │ │ Main Hall (200 seats)       │ ││
│ │ │ Match: 🔴 35% Poor          │ ││
│ │ │ ⊘ Evening (avoid)            │ ││
│ │ │                             │ ││
│ │ │ [Assign anyway]             │ ││
│ │ └─────────────────────────────┘ ││
│ │                                  ││
│ └─────────────────────────────────┘│
└─────────────────────────────────────┘

Step 2: User taps [Tap to assign here]

┌─────────────────────────────────────┐
│ ✓ Assignment Confirmed!             │ ← Success toast
│ Dr. Miller → Workshop 2             │   Slides up from bottom
│ 14:00-15:30, Room B                 │   Auto-dismisses in 4s
│ Match: 85%                          │
│                                     │
│ [Undo] [Continue]                   │
└─────────────────────────────────────┘

After confirmation:
- Automatically returns to session timeline
- Progress bar updates to 4 of 8 (50%)
- If more unassigned speakers: "4 more to go!" banner
- If all assigned: Success celebration screen
```

### Mobile-Specific Features

```css
/* Mobile optimizations */
@media (max-width: 599px) {
  /* Larger touch targets */
  button,
  a,
  .speaker-card,
  .session-card {
    min-height: 48px; /* Increased from desktop 44px */
    padding: 12px 16px;
    font-size: 16px; /* Prevent zoom on input focus */
  }

  /* Full-width components */
  .speaker-card,
  .session-card,
  .modal-dialog {
    width: 100%;
    margin: 0;
  }

  /* Stack buttons vertically */
  .button-group {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .button-group button {
    width: 100%;
  }

  /* Sticky elements */
  .page-header {
    position: sticky;
    top: 0;
    z-index: 100;
    background: white;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  }

  .progress-bar-container {
    position: sticky;
    top: 48px; /* Below header */
    z-index: 99;
    background: white;
    padding: 12px 16px;
  }

  .bottom-nav {
    position: sticky;
    bottom: 0;
    z-index: 100;
    background: white;
    box-shadow: 0 -2px 4px rgba(0, 0, 0, 0.1);
  }

  /* Simplified session grid */
  .session-grid {
    /* Single column, no rooms */
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  /* Room selector instead of columns */
  .room-selector {
    display: block; /* Shown on mobile */
    margin-bottom: 16px;
  }

  /* Day navigation tabs */
  .day-selector {
    display: flex;
    overflow-x: auto;
    gap: 8px;
    padding: 8px 16px;
    background: #f5f5f5;
  }

  .day-tab {
    flex-shrink: 0;
    padding: 8px 16px;
    background: white;
    border-radius: 16px;
    white-space: nowrap;
  }

  .day-tab.active {
    background: #2196f3;
    color: white;
  }

  /* Bottom sheet modals */
  .modal-dialog {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    top: auto;
    max-height: 85vh;
    border-radius: 16px 16px 0 0;
    transform: translateY(100%);
    transition: transform 0.3s ease;
  }

  .modal-dialog.visible {
    transform: translateY(0);
  }

  /* Swipe handle */
  .modal-dialog::before {
    content: "";
    display: block;
    width: 40px;
    height: 4px;
    background: #bdbdbd;
    border-radius: 2px;
    margin: 12px auto;
  }

  /* Hide desktop-only features */
  .quick-actions-panel {
    display: none; /* Accessed via bottom nav */
  }

  .breadcrumb-nav {
    display: none; /* Back button instead */
  }

  /* Simplified typography */
  h1 {
    font-size: 24px;
    line-height: 32px;
  }

  h2 {
    font-size: 20px;
    line-height: 28px;
  }

  p,
  .body-text {
    font-size: 16px;
    line-height: 24px;
  }

  /* Reduce spacing */
  .container {
    padding: 12px 16px;
  }

  section {
    margin-bottom: 24px;
  }
}
```

### Bottom Navigation

```
┌─────────────────────────────────────┐
│ ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐ │ ← 4 tabs
│ │ 🏠  │  │ 👥  │  │ 📅  │  │ ⚡  │ │   Min height 56px
│ │Home │  │Speak│  │Time │  │Acts │ │   Each tab 25% width
│ └─────┘  └─────┘  └─────┘  └─────┘ │
└─────────────────────────────────────┘

Tabs:
1. Home: Return to event overview
2. Speakers: View/filter speaker pool
3. Timeline: View session timeline (default)
4. Actions: Quick actions (auto-assign, clear, export)

Active tab: Blue background (#2196f3)
Inactive tabs: Gray (#757575)
```

## 2. Mobile Landscape (600px - 767px)

### Layout Structure

```
┌───────────────────────────────────────────────────────────────┐
│ ═══ Header ════════════════════════════════                   │ ← Sticky
│ < Back to Event  |  Assign Session Timings - BATbern 2025     │   48px
├───────────────────────────────────────────────────────────────┤
│ Progress: 3 of 8 (37%)  ████████░░░░░░░░░░░░  🔶 5 Remaining  │ ← Sticky
├───────────────────────────────────────────────────────────────┤
│ ┌──── Speakers ────┐ ┌──── Session Timeline ────────────────┐ │
│ │ (Collapsible)    │ │                                       │ │
│ │                  │ │ [June 10] [June 11] [June 12]         │ │
│ │ [All] [Assigned] │ │                                       │ │
│ │                  │ │ Room: [All Rooms ▼]                   │ │
│ │ 🔶 Dr. Miller    │ │                                       │ │
│ │ [Assign]         │ │ ┌───────────────────────────────────┐ │ │
│ │                  │ │ │ 09:00 - 10:30                     │ │ │
│ │ 🔶 Prof. Chen    │ │ │ Keynote Opening                   │ │ │
│ │ [Assign]         │ │ │ Main Hall │ ✓ Dr. Smith │ 🟢 92% │ │ │
│ │                  │ │ └───────────────────────────────────┘ │ │
│ │ ... (scroll)     │ │                                       │ │
│ │                  │ │ ┌───────────────────────────────────┐ │ │
│ │                  │ │ │ 11:00 - 12:30                     │ │ │
│ │                  │ │ │ Workshop 1                         │ │ │
│ │                  │ │ │ Room A │ ○ Not assigned │ [Tap] │ │ │
│ │                  │ │ └───────────────────────────────────┘ │ │
│ │                  │ │                                       │ │
│ │ [Collapse <]     │ │ ... (more sessions, scroll)           │ │
│ └──────────────────┘ └───────────────────────────────────────┘ │
│ 30% width            70% width                                 │
│                                                                 │
│ ═══════════════════════════════════════════════════════════════│
│ [🏠] [👥 Speakers] [📅 Timeline] [⚡ Actions]                   │ ← Bottom nav
└───────────────────────────────────────────────────────────────┘
```

### Speaker Panel Collapsed State

```
When collapsed (user taps [Collapse <]):

┌───────────────────────────────────────────────────────────────┐
│ ═══ Header ════════════════════════════════════                │
├───────────────────────────────────────────────────────────────┤
│ Progress: 3 of 8 (37%)  ████████░░░░░░░░░░░░  🔶 5 Remaining  │
├───────────────────────────────────────────────────────────────┤
│ ┌┐ ┌────────── Session Timeline (Full Width) ────────────────┐│
│ ││ │                                                          ││
│ ││ │ [June 10] [June 11] [June 12]    Room: [All Rooms ▼]    ││
│ ┌┤ │                                                          ││
│ │  │ ┌──────────────────────┐ ┌──────────────────────┐       ││
│ >  │ │ 09:00 - 10:30        │ │ 11:00 - 12:30        │       ││
│ │  │ │ Keynote              │ │ Workshop 1           │       ││
│ │  │ │ ✓ Dr. Smith | 🟢 92% │ │ ○ Not assigned       │       ││
│ └┤ │ └──────────────────────┘ └──────────────────────┘       ││
│ ││ │                                                          ││
│ ││ │ ... (more sessions in 2-column grid)                    ││
│ └┘ └──────────────────────────────────────────────────────────┘│
│  ^                                                              │
│  └─ Expand speakers (vertical tab)                             │
└───────────────────────────────────────────────────────────────┘

Collapsed sidebar: 40px width (just enough for expand button)
Session grid: 2-column layout for better space utilization
```

## 3. Tablet Portrait (768px - 1023px)

### Layout Structure

```
┌─────────────────────────────────────────────────────────────────────┐
│ ═══ Header ═══════════════════════════════════════════════          │
│ Event Management > BATbern 2025 > Slot Assignment  |  < Back to Event│
├─────────────────────────────────────────────────────────────────────┤
│ Assign Session Timings - BATbern 2025                              │
│ Progress: 3 of 8 sessions (37%)  ████████░░░░░░░░░░  🔶 5 Remaining│
├─────────────────────────────────────────────────────────────────────┤
│ ┌────── Speakers ──────┐ ┌────── Session Timeline ──────────────┐  │
│ │ 🔶 5 Remaining        │ │ Multi-Day View                        │  │
│ │ [All][●][○]           │ │ [< June 9] [June 10] [June 11 >]      │  │
│ │                       │ │                                       │  │
│ │ ┌───────────────────┐ │ │ ┌─────────────────────────────────┐ │  │
│ │ │ 🔶 Dr. Sarah Miller│ │ │ │         Room A    Room B   Main  │ │  │
│ │ │ AI in Arch        │ │ │ ├─────────────────────────────────┤ │  │
│ │ │ ☀️ Afternoon      │ │ │ │ 09:00   ┌───────┐ ┌───────┐     │ │  │
│ │ │ [Assign][Prefs]   │ │ │ │         │Keynote│ │       │     │ │  │
│ │ └───────────────────┘ │ │ │ 10:30   │Dr.S   │ │  ---  │     │ │  │
│ │                       │ │ │         │🟢 92% │ │       │     │ │  │
│ │ ┌───────────────────┐ │ │ │         └───────┘ └───────┘     │ │  │
│ │ │ 🔶 Prof. John Chen│ │ │ │ 11:00   ┌───────┐ ┌───────┐     │ │  │
│ │ │ Sustainable       │ │ │ │         │Work 1 │ │Work 2 │     │ │  │
│ │ │ ☀️ Morning        │ │ │ │ 12:30   │  ---  │ │  ---  │     │ │  │
│ │ │ [Assign][Prefs]   │ │ │ │         │       │ │       │     │ │  │
│ │ └───────────────────┘ │ │ │         └───────┘ └───────┘     │ │  │
│ │                       │ │ │ 14:00   ┌───────┐ ┌───────┐     │ │  │
│ │ ✓ Assigned (3):       │ │ │         │Panel  │ │       │     │ │  │
│ │ ┌───────────────────┐ │ │ │ 15:30   │Multi  │ │  ---  │     │ │  │
│ │ │ ✓ Dr. Alex Smith  │ │ │ │         │🟡 78% │ │       │     │ │  │
│ │ │ Keynote           │ │ │ │         └───────┘ └───────┘     │ │  │
│ │ │ 09:00-10:30       │ │ │ │ ... (scrollable)                 │ │  │
│ │ │ [Edit][Details]   │ │ │ └─────────────────────────────────┘ │  │
│ │ └───────────────────┘ │ │                                       │  │
│ │                       │ │ [Auto-Assign All] [Clear All]         │  │
│ │ ... (scroll)          │ └───────────────────────────────────────┘  │
│ └───────────────────────┘                                            │
│ 35% width                 65% width                                  │
└─────────────────────────────────────────────────────────────────────┘
```

### Session Grid (Tablet - Timeline View)

```
Timeline Grid with 3 rooms shown:

┌─────────────────────────────────────────────────────────────┐
│ June 10, 2025                                               │
│ ┌─────┬─────────────────┬─────────────────┬─────────────┐  │
│ │Time │ Room A (50)     │ Room B (50)     │ Main Hall   │  │
│ ├─────┼─────────────────┼─────────────────┼─────────────┤  │
│ │09:00│ ┌─────────────┐ │ ┌─────────────┐ │             │  │
│ │     │ │ Keynote     │ │ │  ---        │ │             │  │
│ │     │ │ Dr. Smith   │ │ │ Available   │ │             │  │
│ │10:30│ │ 🟢 92%      │ │ │             │ │             │  │
│ │     │ └─────────────┘ │ └─────────────┘ │             │  │
│ ├─────┼─────────────────┼─────────────────┼─────────────┤  │
│ │11:00│ ┌─────────────┐ │ ┌─────────────┐ │             │  │
│ │     │ │ Workshop 1  │ │ │ Workshop 2  │ │             │  │
│ │     │ │  ---        │ │ │  ---        │ │             │  │
│ │12:30│ │ Available   │ │ │ Available   │ │             │  │
│ │     │ └─────────────┘ │ └─────────────┘ │             │  │
│ ├─────┼─────────────────┼─────────────────┼─────────────┤  │
│ │14:00│ ┌─────────────┐ │                 │ ┌─────────┐ │  │
│ │     │ │ Panel Disc  │ │                 │ │ Closing │ │  │
│ │     │ │ Multi       │ │                 │ │  ---    │ │  │
│ │15:30│ │ 🟡 78%      │ │                 │ │         │ │  │
│ │     │ └─────────────┘ │                 │ └─────────┘ │  │
│ └─────┴─────────────────┴─────────────────┴─────────────┘  │
└─────────────────────────────────────────────────────────────┘

Features:
- 3 rooms visible simultaneously
- Time slots on left axis (09:00 - 20:00)
- Session blocks sized by duration
- Match scores visible on assigned sessions
- Empty slots have "Available" label
- Tap session to view details/edit
- Drag speaker from sidebar to drop zone
```

### Drag-and-Drop (Tablet)

```
Tablet supports both touch and mouse:

Touch Interaction:
1. Tap speaker → Enters assign mode (same as mobile)
2. Tap time slot → Assigns speaker
3. Visual feedback: Purple highlight, match score overlay

Mouse Interaction:
1. Drag speaker card from sidebar
2. Drop on time slot in grid
3. Visual feedback: Drop zone highlights, match score tooltip

Hybrid (Touchpad/Stylus):
- Supports both drag-drop and tap-tap workflows
- System automatically detects input method
- No preference setting needed
```

## 4. Tablet Landscape / Small Desktop (1024px - 1439px)

### Layout Structure (3-Column)

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ ═══ Header ════════════════════════════════════════════════════════           │
│ Event Management > BATbern 2025 > Slot Assignment      [Back to Event]        │
├───────────────────────────────────────────────────────────────────────────────┤
│ Assign Session Timings - BATbern 2025                                        │
│ Progress: 3 of 8 sessions (37%)  ████████░░░░░░░░░░░░  🔶 5 Remaining       │
├───────────────────────────────────────────────────────────────────────────────┤
│ ┌─── Speakers ───┐ ┌────────── Timeline ─────────────┐ ┌── Quick Actions ──┐│
│ │ 🔶 5 Remaining  │ │ Multi-Day, Multi-Room View      │ │                   ││
│ │ [All][●][○]     │ │                                 │ │ Session Summary   ││
│ │                 │ │ [< Jun 9][Jun 10][Jun 11][12 >] │ │ Total: 8          ││
│ │ 🔶 Unassigned:  │ │                                 │ │ Assigned: 3       ││
│ │ ┌─────────────┐ │ │ ┌─────────────────────────────┐ │ │ Pending: 5        ││
│ │ │Dr. S. Miller│ │ │ │    RmA  RmB  Hall  Auditorium││ │                   ││
│ │ │AI in Arch   │ │ │ ├─────────────────────────────┤│ │ [Auto-Assign All] ││
│ │ │☀️ Afternoon │ │ │ │09:00 ▓▓▓  ---  ---   ---    ││ │ [Clear All]       ││
│ │ │🎤📽️         │ │ │ │     ▓▓▓                     ││ │ [Download PDF]    ││
│ │ │[Assign]     │ │ │ │10:30▓▓▓                     ││ │                   ││
│ │ └─────────────┘ │ │ │11:00 ▓▓▓  ▓▓▓  ---   ---    ││ │ Match Quality     ││
│ │                 │ │ │     ▓▓▓  ▓▓▓               ││ │ Avg: 85% (High)   ││
│ │ ┌─────────────┐ │ │ │12:30▓▓▓  ▓▓▓               ││ │ Range: 72%-98%    ││
│ │ │Prof. J. Chen│ │ │ │14:00 ███  ---  ---   ---    ││ │                   ││
│ │ │Sustainable  │ │ │ │     ███                     ││ │ Conflicts: 0      ││
│ │ │☀️ Morning   │ │ │ │15:30███                     ││ │ Warnings: 1       ││
│ │ │📽️          │ │ │ │16:00 ---  ---  ▓▓▓   ---    ││ │                   ││
│ │ │[Assign]     │ │ │ │     ---  ---  ▓▓▓   ---    ││ │ [View All Issues] ││
│ │ └─────────────┘ │ │ │17:30---  ---  ▓▓▓   ---    ││ │                   ││
│ │                 │ │ │ ... (scroll for more times)  ││ │ Export Options    ││
│ │ ... (scroll)    │ │ └─────────────────────────────┘│ │ [CSV] [PDF] [iCal]││
│ │                 │ │                                 │ │                   ││
│ │ ✓ Assigned (3): │ │ Legend:                         │ └───────────────────┘│
│ │ ┌─────────────┐ │ │ ▓▓▓ Assigned (green)            │                      │
│ │ │✓ Dr. Smith  │ │ │ ███ Assigned (yellow/orange)    │                      │
│ │ │Keynote      │ │ │ --- Available (white/gray)      │                      │
│ │ │09:00-10:30  │ │ │                                 │                      │
│ │ │[Edit][Info] │ │ │ [Zoom In] [Zoom Out] [Fit All]  │                      │
│ │ └─────────────┘ │ └─────────────────────────────────┘                      │
│ │                 │                                                            │
│ └─────────────────┘                                                            │
│ 25% width          55% width                         20% width                 │
└───────────────────────────────────────────────────────────────────────────────┘

Color Key:
▓▓▓ = Green (strong match 80-100%)
███ = Yellow/Orange (acceptable match 50-79%)
░░░ = Red (poor match <50%) - shown on hover/conflict
--- = Available/empty slot
```

### Hover States and Tooltips

```
When mouse hovers over assigned session:

┌─────────────────────────────────────┐
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                   │ ← Session block
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                   │   Highlights
└─────────────────────────────────────┘
         ↓
    ┌─────────────────────────────────────┐
    │ Workshop 2: Advanced Topics         │ ← Tooltip
    │ 14:00 - 15:30 (1.5 hours)           │   Appears on hover
    │ Room B, Capacity: 50                │
    │                                     │
    │ Speaker: Dr. Sarah Miller           │
    │ Match Score: 🟢 85% (Strong)        │
    │                                     │
    │ Matches:                            │
    │ ✓ Afternoon slot (preferred)        │
    │ ✓ Projector available               │
    │ ✓ Room size appropriate             │
    │                                     │
    │ Click to edit | Right-click options │
    └─────────────────────────────────────┘

When dragging speaker over available slot:

┌─────────────────────────────────────┐
│ --- Available ---                   │ ← Drop zone
│ [Drop Dr. Miller here]              │   Highlights during drag
│ Match: 🟢 85% Strong                │   Shows match score
└─────────────────────────────────────┘
```

### Context Menu (Right-Click)

```
Right-click on assigned session:

┌─────────────────────────────────────┐
│ Edit Timing...                      │
│ View Session Details                │
│ View Speaker Preferences            │
│ ───────────────────────────────────│
│ Unassign Speaker                    │
│ Swap with Another Session...        │
│ ───────────────────────────────────│
│ Copy Session Link                   │
│ Add to Calendar                     │
└─────────────────────────────────────┘

Right-click on available slot:

┌─────────────────────────────────────┐
│ Assign Speaker...                   │
│ Block Time Slot                     │
│ View Room Details                   │
│ ───────────────────────────────────│
│ Copy Time Slot Link                 │
└─────────────────────────────────────┘
```

## 5. Desktop (1440px - 1919px)

### Layout Structure (Optimized 3-Column)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ ═══ Header ══════════════════════════════════════════════════════════════               │
│ Event Management > BATbern 2025 > Slot Assignment              [Back to Event]          │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ Assign Session Timings - BATbern 2025                                                  │
│ Progress: 3 of 8 sessions (37%)  ████████░░░░░░░░░░░░░░░  🔶 5 Remaining  Avg: 85% 🟢 │
├──────────────────┬──────────────────────────────────────────────────┬────────────────────┤
│ SPEAKERS         │ SESSION TIMELINE                                 │ QUICK ACTIONS      │
│ 🔶 5 Remaining   │                                                  │                    │
│                  │ Week View: [< June 9-13] [June 14-18] [19-23 >] │ Summary            │
│ [Search...]      │                                                  │ ┌────────────────┐│
│ [All][●][○]      │ ┌────────────────────────────────────────────┐  │ │Total:       8  ││
│                  │ │        Room A   Room B   Main    Aud    Lab │  │ │Assigned:    3  ││
│ 🔶 UNASSIGNED:   │ ├────────────────────────────────────────────┤  │ │Pending:     5  ││
│ ┌──────────────┐ │ │Mon     ▓▓▓     ---     ---     ---    --- │  │ │                ││
│ │Dr. S. Miller │ │ │09:00   ▓▓▓     ░░░     ---     ---    --- │  │ │Match Avg: 85%  ││
│ │AI in Arch    │ │ │        ▓▓▓     ░░░     ---     ---    --- │  │ │Conflicts:  0   ││
│ │☀️ Afternoon  │ │ │10:30   ---     ░░░     ---     ---    --- │  │ └────────────────┘│
│ │🎤📽️🔊       │ │ │11:00   ███     ███     ---     ---    --- │  │                    │
│ │Match: 85%    │ │ │        ███     ███     ---     ---    --- │  │ [Auto-Assign All]  │
│ │[Assign][Pref]│ │ │12:30   ███     ███     ---     ---    --- │  │ [Clear All]        │
│ └──────────────┘ │ │14:00   ---     ---     ▓▓▓     ---    --- │  │ [Download PDF]     │
│                  │ │        ---     ---     ▓▓▓     ---    --- │  │                    │
│ ┌──────────────┐ │ │15:30   ---     ---     ▓▓▓     ---    --- │  │ Issues             │
│ │Prof. J. Chen │ │ │16:00   ---     ---     ---     ▓▓▓    --- │  │ ┌────────────────┐│
│ │Sustainable   │ │ │        ---     ---     ---     ▓▓▓    --- │  │ │⚠️ 1 Warning    ││
│ │☀️ Morning    │ │ │17:30   ---     ---     ---     ▓▓▓    --- │  │ │                ││
│ │📽️🔊         │ │ │18:00   ---     ---     ---     ---    --- │  │ │Workshop 3:     ││
│ │Match: 92%    │ │ │ ... (scroll down for more times/days)      │  │ │Room too large  ││
│ │[Assign][Pref]│ │ └────────────────────────────────────────────┘  │ │[Review]        ││
│ └──────────────┘ │                                                  │ └────────────────┘│
│                  │ Zoom: [+] [-] [Fit]  View: [Day][Week][Month]    │                    │
│ ... (3 more)     │                                                  │ Export             │
│                  │ Legend: ▓▓▓ Strong  ███ Good  ░░░ Poor  --- Open │ [CSV] [PDF] [iCal] │
│                  │                                                  │ [Print Schedule]   │
│ ✓ ASSIGNED (3):  │ [Show Conflicts] [Show Preferences] [Show Stats]│                    │
│ ┌──────────────┐ │                                                  │ Help               │
│ │✓ Dr. A. Smith│ │                                                  │ [Keyboard Shortcuts│
│ │Keynote       │ │                                                  │ [User Guide]       │
│ │Mon 09:00     │ │                                                  │ [Video Tutorial]   │
│ │RmA │ 🟢 92%  │ │                                                  │                    │
│ │[Edit][Details│ │                                                  │                    │
│ └──────────────┘ │                                                  │                    │
│ ... (2 more)     │                                                  │                    │
│                  │                                                  │                    │
└──────────────────┴──────────────────────────────────────────────────┴────────────────────┘
22% width          58% width                                         20% width
```

### Advanced Features (Desktop Only)

**Week/Month View:**

```
Month View (1440px+ only):

┌─────────────────────────────────────────────────────────────────┐
│ June 2025                                              [< >]     │
├─────────────────────────────────────────────────────────────────┤
│ Mon    Tue    Wed    Thu    Fri    Sat    Sun                   │
├───────┬───────┬───────┬───────┬───────┬───────┬───────┬         │
│   1   │   2   │   3   │   4   │   5   │   6   │   7   │         │
│       │       │       │       │       │       │       │         │
├───────┼───────┼───────┼───────┼───────┼───────┼───────┤         │
│   8   │   9   │ ■ 10  │ ■ 11  │ ■ 12  │  13   │  14   │         │
│       │       │ 5evt  │ 6evt  │ 4evt  │       │       │         │
│       │       │ 3asgn │ 4asgn │ 2asgn │       │       │         │
├───────┼───────┼───────┼───────┼───────┼───────┼───────┤         │
│  15   │  16   │  17   │  18   │  19   │  20   │  21   │         │
│       │       │       │       │       │       │       │         │
└───────┴───────┴───────┴───────┴───────┴───────┴───────┘         │
                                                                   │
■ = Has events                                                     │
Click day to zoom to day view                                      │
Hover shows summary: "5 events, 3 assigned, 2 pending"            │
└───────────────────────────────────────────────────────────────────┘
```

**Keyboard Shortcuts Panel:**

```
Desktop supports extensive keyboard shortcuts:

┌─────────────────────────────────────────────────────────────────┐
│ Keyboard Shortcuts                                        [X]    │
├─────────────────────────────────────────────────────────────────┤
│ Navigation:                                                      │
│   Tab                Move to next element                        │
│   Shift+Tab          Move to previous element                    │
│   Arrow keys         Navigate grid                               │
│   Ctrl+Home          Jump to top                                 │
│   Ctrl+End           Jump to bottom                              │
│                                                                  │
│ Workflow:                                                        │
│   Enter              Activate/assign                             │
│   Escape             Cancel                                      │
│   Ctrl+A             Auto-assign all                             │
│   Ctrl+S             Save changes                                │
│   Ctrl+Z             Undo last action                            │
│   Ctrl+Y             Redo action                                 │
│                                                                  │
│ View:                                                            │
│   1                  Day view                                    │
│   2                  Week view                                   │
│   3                  Month view                                  │
│   +                  Zoom in                                     │
│   -                  Zoom out                                    │
│   0                  Reset zoom                                  │
│                                                                  │
│ Search:                                                          │
│   /                  Focus search                                │
│   Ctrl+F             Find speaker/session                        │
│                                                                  │
│ Help:                                                            │
│   ?                  Show this help                              │
└─────────────────────────────────────────────────────────────────┘
```

## 6. Large Desktop (1920px+)

### Layout Structure (4-Column Optional)

```
Ultra-wide displays (1920px+) can show additional panel:

┌──────────┬──────────────────────────────┬───────────┬──────────────┐
│ Speakers │ Session Timeline             │ Actions   │ Insights     │
│ (20%)    │ (50%)                        │ (15%)     │ (15%)        │
│          │                              │           │              │
│          │ 5-6 rooms visible            │           │ Analytics:   │
│          │ simultaneously               │           │ - Coverage   │
│          │                              │           │ - Quality    │
│          │ More vertical space          │           │ - Conflicts  │
│          │ = Less scrolling             │           │ - Trends     │
│          │                              │           │              │
│          │ Enhanced visual              │           │ AI Suggest:  │
│          │ match indicators             │           │ - Best slots │
│          │                              │           │ - Optimize   │
└──────────┴──────────────────────────────┴───────────┴──────────────┘

Insights Panel (1920px+ only):
- Real-time match quality analytics
- Session distribution charts
- Speaker preference satisfaction scores
- AI-powered assignment suggestions
- Conflict prediction
- Schedule optimization recommendations
```

## Responsive Images and Media

### Image Optimization

```html
<!-- Speaker profile images - responsive -->
<picture>
  <source
    media="(min-width: 1440px)"
    srcset="/images/speakers/sarah-miller-large.webp 1x,
            /images/speakers/sarah-miller-large@2x.webp 2x"
    type="image/webp">
  <source
    media="(min-width: 768px)"
    srcset="/images/speakers/sarah-miller-medium.webp 1x,
            /images/speakers/sarah-miller-medium@2x.webp 2x"
    type="image/webp">
  <source
    srcset="/images/speakers/sarah-miller-small.webp 1x,
            /images/speakers/sarah-miller-small@2x.webp 2x"
    type="image/webp">
  <img
    src="/images/speakers/sarah-miller-medium.jpg"
    alt="Dr. Sarah Miller, AI in Architecture specialist"
    loading="lazy"
    width="80"
    height="80">
</picture>

Sizes:
- Small (mobile): 60x60px
- Medium (tablet): 80x80px
- Large (desktop): 100x100px
- @2x retina versions for all sizes
```

### Icon Scaling

```css
/* Responsive icon sizes */
.icon {
  width: 20px;
  height: 20px;
}

@media (min-width: 768px) {
  .icon {
    width: 24px;
    height: 24px;
  }
}

@media (min-width: 1440px) {
  .icon {
    width: 28px;
    height: 28px;
  }
}

/* Preference icons in speaker cards */
.preference-icon {
  font-size: 18px; /* Mobile */
}

@media (min-width: 768px) {
  .preference-icon {
    font-size: 20px; /* Tablet */
  }
}

@media (min-width: 1024px) {
  .preference-icon {
    font-size: 24px; /* Desktop */
  }
}
```

## Performance Optimizations

### Lazy Loading

```typescript
// Lazy load session grid rows as user scrolls
import { useVirtualizer } from '@tanstack/react-virtual';

export const SessionGrid: React.FC<{ sessions: Session[] }> = ({ sessions }) => {
  const parentRef = useRef<HTMLDivElement>(null);

  const rowVirtualizer = useVirtualizer({
    count: sessions.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 80, // Estimated row height
    overscan: 5, // Render 5 extra rows above/below visible area
  });

  return (
    <div ref={parentRef} className="session-grid-container">
      <div
        style={{
          height: `${rowVirtualizer.getTotalSize()}px`,
          width: '100%',
          position: 'relative',
        }}
      >
        {rowVirtualizer.getVirtualItems().map((virtualRow) => (
          <SessionRow
            key={virtualRow.key}
            session={sessions[virtualRow.index]}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              height: `${virtualRow.size}px`,
              transform: `translateY(${virtualRow.start}px)`,
            }}
          />
        ))}
      </div>
    </div>
  );
};
```

### Code Splitting

```typescript
// Lazy load modals and heavy components
import { lazy, Suspense } from 'react';

const AutoAssignModal = lazy(() => import('@/components/AutoAssignModal'));
const ConflictModal = lazy(() => import('@/components/ConflictModal'));
const PreferencesDrawer = lazy(() => import('@/components/PreferencesDrawer'));

// Usage with suspense fallback
<Suspense fallback={<ModalSkeleton />}>
  {showAutoAssign && <AutoAssignModal />}
</Suspense>
```

### Responsive Bundle Loading

```typescript
// Load device-specific code
const isMobile = window.matchMedia('(max-width: 767px)').matches;

if (isMobile) {
  // Load touch-optimized components
  import('@/components/mobile/TouchDragDrop');
} else {
  // Load mouse-optimized components
  import('@/components/desktop/MouseDragDrop');
}
```

## Testing Across Devices

### Manual Testing Checklist

**Mobile (320px - 599px):**
- [ ] All content readable without horizontal scroll
- [ ] Touch targets minimum 48x48px
- [ ] Bottom navigation accessible with thumb
- [ ] Speaker pool expandable/collapsible
- [ ] Assignment flow works with tap-tap
- [ ] Modals use bottom sheets
- [ ] Success banner sticky at bottom

**Tablet Portrait (768px - 1023px):**
- [ ] Two-column layout (speakers | timeline)
- [ ] Drag-drop works with touch
- [ ] Timeline shows 3 rooms
- [ ] Speaker cards show full details
- [ ] Progress bar sticky at top

**Tablet Landscape (1024px+):**
- [ ] Three-column layout visible
- [ ] Timeline shows 4-5 rooms
- [ ] Quick actions panel functional
- [ ] Drag-drop works smoothly
- [ ] Tooltips appear on hover

**Desktop (1440px+):**
- [ ] All columns optimally sized
- [ ] Week/month view available
- [ ] Keyboard shortcuts work
- [ ] Context menus functional
- [ ] Analytics visible (if 1920px+)

### Automated Responsive Tests

```typescript
// Playwright responsive tests
import { test, expect } from '@playwright/test';

const viewports = [
  { name: 'Mobile Portrait', width: 375, height: 667 },
  { name: 'Mobile Landscape', width: 667, height: 375 },
  { name: 'Tablet Portrait', width: 768, height: 1024 },
  { name: 'Tablet Landscape', width: 1024, height: 768 },
  { name: 'Desktop', width: 1440, height: 900 },
  { name: 'Large Desktop', width: 1920, height: 1080 },
];

for (const viewport of viewports) {
  test(`should render correctly on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    // Take screenshot
    await page.screenshot({
      path: `screenshots/${viewport.name.replace(' ', '-')}.png`,
      fullPage: true,
    });

    // Verify layout
    if (viewport.width < 600) {
      // Mobile: single column
      await expect(page.locator('.speaker-pool')).toBeHidden();
      await expect(page.locator('.bottom-nav')).toBeVisible();
    } else if (viewport.width < 1024) {
      // Tablet: two columns
      await expect(page.locator('.speaker-pool')).toBeVisible();
      await expect(page.locator('.quick-actions-panel')).toBeHidden();
    } else {
      // Desktop: three columns
      await expect(page.locator('.speaker-pool')).toBeVisible();
      await expect(page.locator('.quick-actions-panel')).toBeVisible();
    }
  });
}
```

## Implementation Notes

### CSS Grid Layout

```css
/* Mobile first, single column */
.page-layout {
  display: grid;
  grid-template-columns: 1fr;
  grid-template-areas:
    "header"
    "progress"
    "timeline"
    "nav";
  gap: 0;
}

/* Tablet: two columns */
@media (min-width: 768px) {
  .page-layout {
    grid-template-columns: 35% 65%;
    grid-template-areas:
      "header header"
      "progress progress"
      "speakers timeline"
      "speakers timeline";
    gap: 16px;
  }
}

/* Desktop: three columns */
@media (min-width: 1024px) {
  .page-layout {
    grid-template-columns: 22% 58% 20%;
    grid-template-areas:
      "header header header"
      "progress progress progress"
      "speakers timeline actions"
      "speakers timeline actions";
    gap: 24px;
  }
}

/* Large desktop: four columns (optional) */
@media (min-width: 1920px) {
  .page-layout {
    grid-template-columns: 20% 50% 15% 15%;
    grid-template-areas:
      "header header header header"
      "progress progress progress progress"
      "speakers timeline actions insights"
      "speakers timeline actions insights";
    gap: 24px;
  }
}
```

### Material-UI Responsive Props

```typescript
// Use Material-UI responsive props for clean code
<Box
  sx={{
    padding: {
      xs: 1, // 8px mobile
      sm: 2, // 16px tablet
      md: 3, // 24px desktop
    },
    fontSize: {
      xs: '14px',
      sm: '16px',
      md: '16px',
    },
  }}
>
  Content
</Box>

// Grid layout with responsive breakpoints
<Grid container spacing={{ xs: 1, sm: 2, md: 3 }}>
  <Grid item xs={12} md={4} lg={3}>
    {/* Speaker Pool */}
  </Grid>
  <Grid item xs={12} md={8} lg={6}>
    {/* Session Grid */}
  </Grid>
  <Grid item xs={12} lg={3}>
    {/* Quick Actions - hidden on mobile/tablet */}
  </Grid>
</Grid>
```

## Summary

This wireframe documents comprehensive responsive design strategies for the slot assignment workflow across all device sizes:

**Key Responsive Features:**
1. **Mobile-first approach** with progressive enhancement
2. **6 breakpoints** covering all device sizes (320px - 1920px+)
3. **Adaptive layouts**: 1-column mobile → 2-column tablet → 3-column desktop → 4-column ultra-wide
4. **Touch-optimized** interactions on mobile/tablet (48px targets, bottom sheets)
5. **Performance** optimizations (lazy loading, code splitting, responsive images)
6. **Consistent UX** across all devices with appropriate adaptations

**Layout Strategy:**
- Mobile: Single column, bottom nav, expandable panels
- Tablet Portrait: Two columns (speakers | timeline)
- Tablet Landscape: Three columns (speakers | timeline | actions)
- Desktop: Optimized three columns with advanced features
- Large Desktop: Optional four columns with analytics

**Testing:**
- Manual checklist for each breakpoint
- Automated Playwright tests across 6 viewports
- Screenshot regression testing

---

**Next Steps:**
1. Review responsive wireframe with UX team
2. Test on real devices (not just browser emulation)
3. Implement CSS Grid and Material-UI responsive components
4. Verify touch interactions on actual tablets
5. Performance test on low-end mobile devices
