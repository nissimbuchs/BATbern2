# Story 5.7 - Accessibility Features Wireframe

**Story**: BAT-11 - Slot Assignment & Progressive Publishing
**Priority**: Phase 3 (NICE TO HAVE - Polish)
**Related Stories**: Story 5.8 (Unified Event Page)

## Purpose

This wireframe documents comprehensive accessibility features for the slot assignment workflow, ensuring WCAG 2.1 Level AA compliance and providing an excellent experience for users with disabilities. Covers keyboard navigation, screen reader support, ARIA attributes, focus management, and alternative interaction patterns.

## Design Principles

1. **Keyboard First**: All drag-drop operations have keyboard alternatives
2. **Clear Feedback**: Visual, auditory, and tactile feedback for all actions
3. **Progressive Enhancement**: Core functionality works without JavaScript
4. **Screen Reader Optimized**: Meaningful announcements and navigation landmarks
5. **Focus Management**: Always clear where focus is and how to move it
6. **Error Prevention**: Clear warnings before destructive actions

## WCAG 2.1 Level AA Compliance

### Success Criteria Addressed

- **1.3.1 Info and Relationships**: Semantic HTML, ARIA landmarks, labeled form controls
- **1.4.3 Contrast (Minimum)**: 4.5:1 text contrast, 3:1 UI component contrast
- **2.1.1 Keyboard**: All functionality available via keyboard
- **2.1.2 No Keyboard Trap**: User can navigate away from all elements
- **2.4.3 Focus Order**: Logical focus order throughout workflow
- **2.4.7 Focus Visible**: Clear focus indicators on all interactive elements
- **3.2.4 Consistent Identification**: Same icons/labels for same functions
- **4.1.3 Status Messages**: ARIA live regions for dynamic updates

## 1. Keyboard Navigation

### Overview

All slot assignment operations are fully accessible via keyboard, providing an alternative to mouse-based drag-and-drop.

### Global Keyboard Shortcuts

```
Slot Assignment Page Shortcuts:
┌────────────────────────────────────────────────────────────────────┐
│  Navigation:                                                        │
│  Tab              → Move focus to next interactive element         │
│  Shift+Tab        → Move focus to previous interactive element     │
│  Ctrl+Home        → Jump to page top (breadcrumb)                  │
│  Ctrl+End         → Jump to page bottom (success banner if shown)  │
│                                                                     │
│  Workflow:                                                          │
│  /                → Focus search/filter input                      │
│  Ctrl+A           → Open auto-assign modal                         │
│  Ctrl+S           → Save current assignments (if changes exist)    │
│  Esc              → Cancel current operation / close modal         │
│                                                                     │
│  Help:                                                              │
│  ?                → Show keyboard shortcuts help modal             │
│  Ctrl+?           → Show accessibility settings                    │
└────────────────────────────────────────────────────────────────────┘

Press [?] to show this help anytime.
```

### Speaker Pool Keyboard Navigation

**Focus Management:**

```
Speaker Pool Focus Order:
1. Filter buttons: [All] → [●Assigned] → [○Unassigned]
2. Speaker cards (top to bottom):
   - First speaker card (entire card is focusable)
   - Second speaker card
   - ...
   - Last speaker card

Speaker Card Focused State:
┌─────────────────────────────────────┐
│ 🔶 Dr. Sarah Miller                 │ ← 2px solid blue border (#2196f3)
│ Topic: AI in Architecture           │   Background: rgba(33, 150, 243, 0.08)
│ Time Preferences: ☀️ Afternoon      │   Box shadow: 0 0 0 3px rgba(33, 150, 243, 0.25)
│ Status: Accepted, no timing         │
│                                     │
│ Actions:                            │
│ [A] Assign  [P] Preferences         │ ← Shortcut keys shown in brackets
└─────────────────────────────────────┘

Keyboard Actions on Focused Speaker:
- Enter / Space   → Enter assign mode (alternative to drag)
- A               → Quick assign to best match slot
- P               → View preferences drawer
- ↓ / ↑           → Move focus to next/previous speaker
- Ctrl+C          → Copy speaker details to clipboard
```

**Assign Mode (Keyboard Alternative to Drag-Drop):**

```
Step 1: User focuses speaker card and presses Enter
┌─────────────────────────────────────┐
│ ✓ Dr. Sarah Miller - ASSIGN MODE    │ ← Purple border (#9c27b0)
│ Topic: AI in Architecture           │   "Selected for assignment" state
│                                     │
│ [Esc] Cancel  [Tab] Navigate slots  │
└─────────────────────────────────────┘

Screen Reader Announces:
"Assign mode activated for Dr. Sarah Miller. Press Tab to navigate
 available time slots. Press Enter on a slot to assign. Press Escape
 to cancel."

Step 2: User presses Tab to navigate session grid
Session Grid Focus Order:
- Focus moves to first available (unassigned) slot
- Tab moves to next slot (left-to-right, top-to-bottom)
- Shift+Tab moves to previous slot
- Arrow keys navigate grid (← ↑ → ↓)

Focused Slot (with speaker in assign mode):
┌─────────────────────────────────────┐
│ 14:00 - 15:30                       │ ← 3px solid purple border
│ Workshop 2: Advanced Topics         │   Match score shown prominently
│ Room B, Capacity: 50                │
│ Status: No timing assigned          │
│                                     │
│ Match Score: 🟢 85% (Strong Match)  │
│ • ✓ Afternoon slot (preferred)      │
│ • ✓ Projector available             │
│ • ⚠️ Large room (prefers intimate)  │
│                                     │
│ [Enter] Assign here  [Esc] Cancel   │
└─────────────────────────────────────┘

Screen Reader Announces (on focus):
"Workshop 2: Advanced Topics, 14:00 to 15:30, Room B. Currently unassigned.
 Match score: 85 percent, strong match. Matches afternoon preference,
 has projector. Room capacity may be larger than preferred.
 Press Enter to assign Dr. Sarah Miller to this slot."

Step 3: User presses Enter to confirm assignment
Visual Feedback:
- Slot border flashes green 3 times
- Speaker card moves to "Assigned" section with slide animation
- Progress bar updates with smooth fill animation
- Toast notification: "✓ Dr. Sarah Miller assigned to Workshop 2"

Screen Reader Announces:
"Assignment confirmed. Dr. Sarah Miller assigned to Workshop 2,
 14:00 to 15:30, Room B. Match score: 85 percent.
 Progress: 4 of 8 sessions assigned, 50 percent complete.
 Focus returned to speaker pool. 4 unassigned speakers remaining."

Focus Management After Assignment:
- Focus automatically moves to next unassigned speaker
- User can continue assigning without extra navigation
```

### Session Grid Keyboard Navigation (View Mode)

```
Grid Navigation Pattern:

Arrow Keys Navigation:
→ (Right Arrow)   → Move to next time slot (same row)
← (Left Arrow)    → Move to previous time slot (same row)
↓ (Down Arrow)    → Move to slot one row below (same column)
↑ (Up Arrow)      → Move to slot one row above (same column)

Tab / Shift+Tab:
- Tab moves focus to next interactive element (ignoring grid structure)
- Useful for jumping between grid sections quickly

Home / End:
- Home  → Jump to first slot in current row
- End   → Jump to last slot in current row

Page Up / Page Down:
- Page Up   → Scroll up one screen (keep focus)
- Page Down → Scroll down one screen (keep focus)

Focused Assigned Session:
┌─────────────────────────────────────┐
│ 14:00 - 15:30                       │ ← 2px solid blue border
│ Workshop 2: Advanced Topics         │   Focus halo
│ Room B                              │
│ Speaker: Dr. Sarah Miller           │
│ Match Score: 🟢 85%                 │
│                                     │
│ [Enter] Edit  [Del] Unassign  [I] Info │
└─────────────────────────────────────┘

Keyboard Actions on Focused Session:
- Enter       → Edit session timing (opens time picker)
- Delete/Backspace → Unassign speaker (with confirmation)
- I           → View full session info drawer
- P           → View speaker preferences
- Ctrl+C      → Copy session details to clipboard
```

### Modal Keyboard Navigation

**Conflict Resolution Modal:**

```
Focus Trap Active (cannot Tab outside modal):

Focus Order:
1. Modal title: "Timing Conflict Detected"
2. Conflict details (focusable for screen readers)
3. Resolution option buttons:
   - [Find Alternative Slot]
   - [Change Room]
   - [Reassign Panel]
4. [Cancel] button
5. [X] Close button (top-right)

Keyboard Shortcuts within Modal:
- Tab / Shift+Tab → Navigate buttons
- Enter           → Activate focused button
- Esc             → Close modal (same as Cancel)
- ↓ / ↑           → Navigate option buttons
- 1-4             → Quick select option by number

Screen Reader Announces (on modal open):
"Alert: Timing Conflict Detected. Dialog opened.
 You are trying to assign Workshop 2 to Room A at 14:00 to 15:30,
 but this room is already occupied by Panel Discussion at the same time.
 Please choose a resolution option. 4 options available."
```

**Auto-Assign Modal:**

```
Multi-Step Modal Focus Management:

Step 1: Algorithm Selection
Focus Order:
1. Modal title
2. Radio button: Optimize for Speaker Preferences
3. Radio button: Optimize for Expertise Match
4. Radio button: Optimize for Balanced Schedule (default selected)
5. Checkbox: Respect hard constraints
6. Checkbox: Balance session distribution
7. [Preview Assignments] button
8. [Cancel] button

Keyboard Shortcuts:
- Space         → Toggle checkbox / select radio
- ↓ / ↑         → Navigate radio buttons
- Enter         → Activate [Preview] button (if focused)
- P             → Quick activate [Preview Assignments]
- Esc           → Close modal

Step 2: Preview Assignments
Focus Order:
1. Results summary (focusable region)
2. Side-by-side comparison table (scrollable)
3. Warning messages (if any)
4. [Accept All] button
5. [Customize Before Applying] button
6. [Cancel] button

Keyboard Shortcuts:
- ↓ / ↑         → Scroll comparison table
- A             → Quick activate [Accept All]
- C             → Quick activate [Customize]
- Esc           → Return to step 1

Screen Reader Announces (on step 2):
"Preview auto-assignments. 5 sessions assigned.
 Overall match score: 78 percent, good.
 Review assignments below.
 Press A to accept all, C to customize, or Escape to cancel."
```

## 2. ARIA Attributes

### Landmark Regions

```html
<!-- Page Structure with ARIA Landmarks -->
<div role="banner">
  <!-- Breadcrumb navigation -->
  <nav aria-label="Breadcrumb">
    <ol>
      <li><a href="/organizer/events">Event Management</a></li>
      <li><a href="/organizer/events/batbern-2025">BATbern 2025</a></li>
      <li aria-current="page">Slot Assignment</li>
    </ol>
  </nav>
</div>

<main role="main" aria-labelledby="page-title">
  <h1 id="page-title">Assign Session Timings - BATbern 2025</h1>

  <!-- Speaker Pool Sidebar -->
  <aside role="complementary" aria-labelledby="speaker-pool-title">
    <h2 id="speaker-pool-title">Speakers</h2>

    <!-- Progress indicator -->
    <div role="status" aria-live="polite" aria-atomic="true">
      <p>Progress: <span id="progress-text">3 of 8 sessions assigned (37%)</span></p>
      <div role="progressbar"
           aria-valuenow="37"
           aria-valuemin="0"
           aria-valuemax="100"
           aria-labelledby="progress-text">
        <div class="progress-fill" style="width: 37%"></div>
      </div>
    </div>

    <!-- Filter buttons -->
    <div role="group" aria-label="Filter speakers by assignment status">
      <button aria-pressed="true">All</button>
      <button aria-pressed="false">Assigned</button>
      <button aria-pressed="false">Unassigned</button>
    </div>

    <!-- Speaker cards -->
    <ul role="list" aria-label="Speaker list">
      <li>
        <div class="speaker-card"
             tabindex="0"
             role="button"
             aria-label="Dr. Sarah Miller, AI in Architecture, not assigned. Press Enter to assign."
             aria-describedby="speaker-1-details"
             draggable="true"
             aria-grabbed="false">

          <div id="speaker-1-details">
            <h3>Dr. Sarah Miller</h3>
            <p>Topic: AI in Architecture</p>
            <p>Time Preferences: Afternoon preferred</p>
            <p>Status: Accepted, no timing assigned</p>
          </div>

          <div class="card-actions">
            <button aria-label="Assign Dr. Sarah Miller to time slot">
              Assign
            </button>
            <button aria-label="View Dr. Sarah Miller's preferences">
              Preferences
            </button>
          </div>
        </div>
      </li>
      <!-- More speaker cards... -->
    </ul>
  </aside>

  <!-- Session Timeline Grid -->
  <section role="region" aria-labelledby="timeline-title">
    <h2 id="timeline-title">Session Timeline</h2>

    <!-- Timeline grid with ARIA grid pattern -->
    <div role="grid"
         aria-labelledby="timeline-title"
         aria-rowcount="13"
         aria-colcount="4">

      <!-- Column headers -->
      <div role="row" aria-rowindex="1">
        <div role="columnheader" aria-colindex="1">Time</div>
        <div role="columnheader" aria-colindex="2">Room A</div>
        <div role="columnheader" aria-colindex="3">Room B</div>
        <div role="columnheader" aria-colindex="4">Main Hall</div>
      </div>

      <!-- Time slot rows -->
      <div role="row" aria-rowindex="2">
        <div role="rowheader" aria-colindex="1">14:00 - 15:30</div>

        <!-- Session cell (assigned) -->
        <div role="gridcell"
             aria-colindex="2"
             tabindex="0"
             aria-label="Workshop 2: Advanced Topics, 14:00 to 15:30, Room A, assigned to Dr. Sarah Miller, match score 85 percent strong match"
             aria-describedby="session-2-details">

          <div id="session-2-details">
            <h4>Workshop 2: Advanced Topics</h4>
            <p>14:00 - 15:30, Room A</p>
            <p>Speaker: Dr. Sarah Miller</p>
            <p>Match Score: 85% (Strong Match)</p>
          </div>

          <div class="session-actions">
            <button aria-label="Edit Workshop 2 timing">Edit</button>
            <button aria-label="Unassign Dr. Sarah Miller from Workshop 2">
              Unassign
            </button>
          </div>
        </div>

        <!-- Empty slot (drop zone) -->
        <div role="gridcell"
             aria-colindex="3"
             tabindex="0"
             aria-label="Available slot, 14:00 to 15:30, Room B, no session assigned"
             aria-dropeffect="move"
             class="drop-zone">
          <p class="visually-hidden">Drop speaker here to assign to this time slot</p>
        </div>

        <!-- More cells... -->
      </div>
      <!-- More rows... -->
    </div>
  </section>

  <!-- Quick Actions Panel -->
  <aside role="complementary" aria-labelledby="actions-title">
    <h2 id="actions-title">Quick Actions</h2>

    <button aria-label="Auto-assign all unassigned sessions using optimization algorithm">
      Auto-Assign All
    </button>

    <button aria-label="Clear all current assignments. Confirmation required.">
      Clear All Assignments
    </button>

    <div role="status" aria-live="polite">
      <p>8 total sessions, 3 assigned, 5 pending</p>
    </div>
  </aside>
</main>

<!-- Success Banner (when all assigned) -->
<div role="status"
     aria-live="assertive"
     aria-atomic="true"
     class="success-banner">
  <h2>All session timings assigned!</h2>
  <p>8 of 8 sessions complete. Ready to proceed to publishing.</p>
  <a href="/organizer/events/batbern-2025?tab=publishing">
    View Publishing Options
  </a>
</div>

<!-- Footer -->
<footer role="contentinfo">
  <p>BATbern Event Management Platform</p>
</footer>
```

### Dynamic Content Announcements

**ARIA Live Regions for Real-Time Updates:**

```html
<!-- Progress Updates (polite - don't interrupt) -->
<div role="status" aria-live="polite" aria-atomic="true" class="visually-hidden">
  <p id="progress-announcement"></p>
</div>

JavaScript Update:
document.getElementById('progress-announcement').textContent =
  "Workshop 2 assigned to Dr. Sarah Miller at 14:00 to 15:30, Room B. " +
  "Match score: 85 percent, strong match. " +
  "Progress: 4 of 8 sessions assigned, 50 percent complete.";

<!-- Errors/Conflicts (assertive - interrupt) -->
<div role="alert" aria-live="assertive" aria-atomic="true" class="visually-hidden">
  <p id="conflict-announcement"></p>
</div>

JavaScript Update:
document.getElementById('conflict-announcement').textContent =
  "Timing conflict detected. Room A is already occupied at this time. " +
  "Please choose an alternative slot or resolve the conflict.";

<!-- Success Messages (polite) -->
<div role="status" aria-live="polite" aria-atomic="true" class="visually-hidden">
  <p id="success-announcement"></p>
</div>

JavaScript Update:
document.getElementById('success-announcement').textContent =
  "Success! All session timings assigned. " +
  "8 of 8 sessions complete with average match score of 82 percent.";
```

### Drag-and-Drop ARIA Pattern

```html
<!-- Speaker Card (draggable) -->
<div class="speaker-card"
     draggable="true"
     tabindex="0"
     role="button"
     aria-grabbed="false"
     aria-label="Dr. Sarah Miller, press Enter to enter assign mode, or drag to drop on time slot"
     data-speaker-id="speaker-1">
  <!-- Card content -->
</div>

<script>
// Update aria-grabbed when dragging starts
speakerCard.addEventListener('dragstart', (e) => {
  e.currentTarget.setAttribute('aria-grabbed', 'true');

  // Announce to screen reader
  const announcement = `Dragging ${speakerName}. ` +
    `Navigate to a time slot and drop to assign. ` +
    `Available slots will be highlighted.`;

  announceToScreenReader(announcement, 'polite');

  // Update drop zones
  document.querySelectorAll('.drop-zone').forEach(zone => {
    zone.setAttribute('aria-dropeffect', 'move');
    zone.setAttribute('aria-label',
      `${zone.dataset.timeSlot}, ${zone.dataset.room}. ` +
      `Drop ${speakerName} here to assign. ` +
      `Match score: ${calculateMatchScore(speaker, zone)} percent`);
  });
});

// Update aria-grabbed when dragging ends
speakerCard.addEventListener('dragend', (e) => {
  e.currentTarget.setAttribute('aria-grabbed', 'false');

  // Reset drop zones
  document.querySelectorAll('.drop-zone').forEach(zone => {
    zone.setAttribute('aria-dropeffect', 'none');
  });
});

// Drop zone receives drop
dropZone.addEventListener('drop', (e) => {
  const speakerId = e.dataTransfer.getData('speaker-id');
  const speaker = getSpeakerById(speakerId);
  const matchScore = calculateMatchScore(speaker, dropZone);

  // Perform assignment
  assignSpeakerToSlot(speaker, dropZone);

  // Announce success
  const announcement =
    `${speaker.name} assigned to ${dropZone.dataset.sessionName}, ` +
    `${dropZone.dataset.timeSlot}, ${dropZone.dataset.room}. ` +
    `Match score: ${matchScore} percent. ` +
    `Progress: ${assignedCount} of ${totalCount} sessions assigned.`;

  announceToScreenReader(announcement, 'polite');
});
</script>
```

## 3. Screen Reader Support

### Descriptive Labels and Instructions

**Speaker Card Screen Reader Experience:**

```
Visual UI:
┌─────────────────────────────────────┐
│ 🔶 Dr. Sarah Miller                 │
│ Topic: AI in Architecture           │
│ Time Preferences: ☀️ Afternoon      │
│ Status: Accepted, no timing         │
│ [Assign] [Preferences]              │
└─────────────────────────────────────┘

Screen Reader Announces (on focus):
"Dr. Sarah Miller.
 Topic: AI in Architecture.
 Time preferences: Afternoon preferred.
 Status: Accepted, no timing assigned.
 Button. Press Enter to enter assign mode,
 or drag to drop on time slot.
 Press P to view preferences."

Alternative Interaction Hint:
"Tip: Press Enter to use keyboard assignment mode
 if drag-and-drop is difficult."
```

**Session Grid Cell Screen Reader Experience:**

```
Visual UI (Assigned Session):
┌─────────────────────────────────────┐
│ 14:00 - 15:30                       │
│ Workshop 2: Advanced Topics         │
│ Room B                              │
│ Speaker: Dr. Sarah Miller           │
│ Match Score: 🟢 85%                 │
└─────────────────────────────────────┘

Screen Reader Announces (on focus):
"Workshop 2: Advanced Topics.
 June 10, 2025, 14:00 to 15:30.
 Room B, capacity 50.
 Currently assigned to Dr. Sarah Miller.
 Match score: 85 percent, strong match.
 Matches: Afternoon slot preferred, projector available.
 Grid cell. Press Enter to edit timing,
 Delete to unassign speaker,
 or I for full session information."

Visual UI (Empty Slot):
┌─────────────────────────────────────┐
│ 16:00 - 17:30                       │
│ Main Hall                           │
│ [Drop here]                         │
└─────────────────────────────────────┘

Screen Reader Announces (on focus):
"Available time slot.
 June 10, 2025, 16:00 to 17:30.
 Main Hall, capacity 200.
 No session currently assigned.
 Drop zone. Press Enter when a speaker is in assign mode
 to assign them to this slot."

When Speaker is Being Dragged (dynamic update):
"Available time slot.
 June 10, 2025, 16:00 to 17:30.
 Main Hall.
 Drop Dr. Sarah Miller here to assign.
 Match score: 72 percent, acceptable match.
 Matches afternoon preference.
 Warning: Large room, speaker prefers smaller venue."
```

### Context-Sensitive Help

```html
<!-- Help Modal Triggered by "?" Key -->
<div role="dialog"
     aria-labelledby="help-title"
     aria-describedby="help-description">

  <h2 id="help-title">Keyboard Navigation Help</h2>

  <div id="help-description">
    <section>
      <h3>Assigning Sessions with Keyboard</h3>
      <ol>
        <li>
          <strong>Navigate to a speaker:</strong>
          Press Tab until you reach the speaker pool.
          Use Up/Down arrows to move between speakers.
        </li>
        <li>
          <strong>Enter assign mode:</strong>
          Press Enter or Space on a speaker card.
          You'll hear "Assign mode activated."
        </li>
        <li>
          <strong>Navigate to a time slot:</strong>
          Press Tab to move to the session grid.
          Use Arrow keys to navigate slots.
          Match scores are announced for each slot.
        </li>
        <li>
          <strong>Confirm assignment:</strong>
          Press Enter on your chosen slot.
          You'll hear confirmation and updated progress.
        </li>
        <li>
          <strong>Cancel:</strong>
          Press Escape anytime to cancel assign mode.
        </li>
      </ol>
    </section>

    <section>
      <h3>Screen Reader Tips</h3>
      <ul>
        <li>
          Use your screen reader's heading navigation
          (H key in NVDA/JAWS) to jump between sections.
        </li>
        <li>
          Use landmark navigation (D key) to jump to
          main regions: speaker pool, session grid, quick actions.
        </li>
        <li>
          Enable "Speak All" to hear full page summary
          including current progress and unassigned speakers.
        </li>
      </ul>
    </section>
  </div>

  <button aria-label="Close help dialog">Close</button>
</div>
```

### Progress Announcements Strategy

```javascript
// Progressive announcement strategy to avoid overwhelming users

// Only announce progress at milestones (not every assignment)
const MILESTONE_PERCENTAGES = [25, 50, 75, 100];

function handleAssignment(speaker, slot) {
  // Perform assignment
  const result = assignSpeakerToSlot(speaker, slot);

  // Immediate feedback (always announce)
  const immediateAnnouncement =
    `${speaker.name} assigned to ${slot.sessionName}. ` +
    `Match score: ${result.matchScore} percent.`;

  announceToScreenReader(immediateAnnouncement, 'polite');

  // Progress update (only at milestones)
  const progressPercent = (result.assignedCount / result.totalCount) * 100;
  const reachedMilestone = MILESTONE_PERCENTAGES.some(m =>
    progressPercent >= m && (progressPercent - 12.5) < m
  );

  if (reachedMilestone) {
    const milestoneAnnouncement =
      `Milestone: ${Math.round(progressPercent)} percent complete. ` +
      `${result.assignedCount} of ${result.totalCount} sessions assigned. ` +
      `${result.totalCount - result.assignedCount} remaining.`;

    // Delay slightly to avoid interrupting immediate feedback
    setTimeout(() => {
      announceToScreenReader(milestoneAnnouncement, 'polite');
    }, 1500);
  }

  // Success celebration (only at 100%)
  if (progressPercent === 100) {
    setTimeout(() => {
      const successAnnouncement =
        `Success! All session timings assigned! ` +
        `${result.totalCount} of ${result.totalCount} sessions complete. ` +
        `Average match score: ${result.averageMatchScore} percent. ` +
        `You can now proceed to publishing. ` +
        `Press Ctrl+End to navigate to publishing options.`;

      announceToScreenReader(successAnnouncement, 'assertive');
    }, 2000);
  }
}

// Helper function to announce to screen reader
function announceToScreenReader(message, priority = 'polite') {
  const liveRegion = document.getElementById(
    priority === 'assertive' ? 'alert-region' : 'status-region'
  );

  // Clear previous announcement
  liveRegion.textContent = '';

  // Trigger reflow to ensure announcement
  void liveRegion.offsetHeight;

  // Set new announcement
  liveRegion.textContent = message;
}
```

## 4. Focus Management

### Focus Indicators

**Visual Focus Styles:**

```css
/* Default focus indicator (all interactive elements) */
*:focus {
  outline: 2px solid #2196f3; /* Blue */
  outline-offset: 2px;
  box-shadow: 0 0 0 4px rgba(33, 150, 243, 0.25); /* Focus halo */
}

/* Remove default outline, add custom */
*:focus:not(:focus-visible) {
  outline: none;
  box-shadow: none;
}

/* Keyboard focus only (not mouse clicks) */
*:focus-visible {
  outline: 2px solid #2196f3;
  outline-offset: 2px;
  box-shadow: 0 0 0 4px rgba(33, 150, 243, 0.25);
}

/* High contrast mode support */
@media (prefers-contrast: high) {
  *:focus-visible {
    outline: 3px solid currentColor;
    outline-offset: 3px;
  }
}

/* Speaker card focus */
.speaker-card:focus-visible {
  border: 2px solid #2196f3;
  background: rgba(33, 150, 243, 0.08);
  box-shadow: 0 0 0 3px rgba(33, 150, 243, 0.25);
}

/* Session grid cell focus */
.session-cell:focus-visible {
  border: 2px solid #2196f3;
  box-shadow: 0 0 0 3px rgba(33, 150, 243, 0.25),
              0 4px 8px rgba(0, 0, 0, 0.1);
  z-index: 10; /* Ensure focus ring not clipped */
}

/* Button focus */
button:focus-visible {
  outline: 2px solid #2196f3;
  outline-offset: 2px;
  box-shadow: 0 0 0 4px rgba(33, 150, 243, 0.25);
}

/* Link focus */
a:focus-visible {
  outline: 2px solid #2196f3;
  outline-offset: 2px;
  text-decoration: underline;
  text-decoration-thickness: 2px;
}

/* Special states */

/* Assign mode (speaker selected for assignment) */
.speaker-card[data-assign-mode="true"]:focus-visible {
  border: 3px solid #9c27b0; /* Purple */
  background: rgba(156, 39, 176, 0.12);
  box-shadow: 0 0 0 4px rgba(156, 39, 176, 0.3);
}

/* Drop zone active (speaker being dragged) */
.drop-zone[aria-dropeffect="move"]:focus-visible {
  border: 3px dashed #4caf50; /* Green */
  background: rgba(76, 175, 80, 0.08);
  box-shadow: 0 0 0 4px rgba(76, 175, 80, 0.25);
}

/* Error state */
.has-error:focus-visible {
  border: 2px solid #f44336; /* Red */
  box-shadow: 0 0 0 4px rgba(244, 67, 54, 0.25);
}
```

**Focus Visibility States:**

```
Speaker Card States:

1. Default (no focus):
┌─────────────────────────────────────┐
│ 🔶 Dr. Sarah Miller                 │ ← 1px solid #e0e0e0
│ Topic: AI in Architecture           │
│ [Assign] [Preferences]              │
└─────────────────────────────────────┘

2. Mouse hover (no focus):
┌─────────────────────────────────────┐
│ 🔶 Dr. Sarah Miller                 │ ← 1px solid #bdbdbd, slight elevation
│ Topic: AI in Architecture           │   Background: #fafafa
│ [Assign] [Preferences]              │
└─────────────────────────────────────┘

3. Keyboard focus:
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ ← 2px solid #2196f3
┃ 🔶 Dr. Sarah Miller                 ┃   Focus halo (4px rgba blue)
┃ Topic: AI in Architecture           ┃   Background: rgba(33, 150, 243, 0.08)
┃ [Assign] [Preferences]              ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

4. Assign mode active (keyboard focus):
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ ← 3px solid #9c27b0 (purple)
┃ ✓ Dr. Sarah Miller - ASSIGN MODE    ┃   Focus halo (4px rgba purple)
┃ Topic: AI in Architecture           ┃   Background: rgba(156, 39, 176, 0.12)
┃ [Esc] Cancel  [Tab] Navigate slots  ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

Session Grid Cell States:

1. Empty slot (no focus):
┌─────────────────────────────────────┐
│ 16:00 - 17:30                       │ ← 1px dashed #e0e0e0
│ Main Hall                           │   Background: #fafafa
│ [Drop here]                         │
└─────────────────────────────────────┘

2. Empty slot (keyboard focus, speaker in assign mode):
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ ← 3px dashed #4caf50 (green)
┃ 16:00 - 17:30                       ┃   Focus halo (4px rgba green)
┃ Main Hall                           ┃   Background: rgba(76, 175, 80, 0.08)
┃ Match: 🟢 85% Strong                ┃   Match score prominently shown
┃ [Enter] Assign here                 ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

3. Assigned session (keyboard focus):
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ ← 2px solid #2196f3
┃ 14:00 - 15:30                       ┃   Focus halo (3px rgba blue)
┃ Workshop 2: Advanced Topics         ┃   Background: white
┃ Speaker: Dr. Sarah Miller           ┃   Slight elevation
┃ Match: 🟢 85%                       ┃
┃ [Enter] Edit  [Del] Unassign        ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
```

### Focus Order Strategy

**Logical Reading Order:**

```
Page Focus Flow:

1. [Skip to main content] link (hidden, shown on focus)
2. Breadcrumb navigation:
   - Event Management link
   - BATbern 2025 link
   - Slot Assignment (current, not linked)
3. Page title: "Assign Session Timings"
4. [Back to Event] button
5. Speaker Pool Sidebar:
   - Progress indicator (status, not focusable)
   - Filter buttons: [All] [Assigned] [Unassigned]
   - Speaker card 1
   - Speaker card 2
   - ... (all speaker cards)
6. Session Timeline Grid:
   - Grid navigation instructions (focusable help text)
   - Session cell (row 1, col 1)
   - Session cell (row 1, col 2)
   - ... (all cells, left-to-right, top-to-bottom)
7. Quick Actions Panel:
   - [Auto-Assign All] button
   - [Clear All Assignments] button
   - [Download Schedule PDF] button
8. Success Banner (if shown):
   - [View Publishing Options] link
9. Footer links

Within Speaker Card:
- Card itself (entire card focusable)
- [Assign] button (when card focused, press A)
- [Preferences] button (when card focused, press P)

Within Session Cell:
- Cell itself (entire cell focusable)
- [Edit] button (when cell focused, press Enter)
- [Unassign] button (when cell focused, press Delete)
- [Info] button (when cell focused, press I)
```

### Focus Restoration After Actions

```javascript
// Focus management after modal closes

// Example: Conflict Resolution Modal
function openConflictModal(conflict) {
  // Save current focus
  const triggerElement = document.activeElement;

  // Open modal, trap focus
  const modal = showConflictModal(conflict);
  modal.setAttribute('data-trigger-element', getElementIdentifier(triggerElement));

  // Focus modal title
  modal.querySelector('[role="dialog"] h2').focus();
}

function closeConflictModal(resolution) {
  const modal = document.querySelector('[role="dialog"]');
  const triggerElementId = modal.getAttribute('data-trigger-element');

  // Close modal
  hideConflictModal();

  // Restore focus to trigger element
  const triggerElement = getElementByIdentifier(triggerElementId);
  if (triggerElement && isElementVisible(triggerElement)) {
    triggerElement.focus();
  } else {
    // Fallback: focus next logical element
    if (resolution === 'assigned') {
      // Focus was on speaker, now assigned
      // Move focus to next unassigned speaker
      const nextUnassigned = findNextUnassignedSpeaker();
      if (nextUnassigned) {
        nextUnassigned.focus();
      }
    } else {
      // Resolution cancelled, return to same speaker
      const speakerCard = document.querySelector(`[data-speaker-id="${conflict.speakerId}"]`);
      speakerCard?.focus();
    }
  }

  // Announce result to screen reader
  const announcement = generateAnnouncementForResolution(resolution);
  announceToScreenReader(announcement, 'polite');
}

// Example: Speaker Assignment
function assignSpeakerToSlot(speaker, slot) {
  // Perform assignment
  const result = performAssignment(speaker, slot);

  // Update UI
  updateSpeakerCard(speaker.id, { assigned: true });
  updateSessionCell(slot.id, { speakerId: speaker.id });
  updateProgressBar(result.assignedCount, result.totalCount);

  // Smart focus management
  if (result.assignedCount < result.totalCount) {
    // More speakers to assign
    // Move focus to next unassigned speaker
    const nextSpeaker = findNextUnassignedSpeaker(speaker.id);
    if (nextSpeaker) {
      setTimeout(() => {
        nextSpeaker.focus();
        announceToScreenReader(
          `Focus moved to ${nextSpeaker.dataset.speakerName}. ` +
          `${result.totalCount - result.assignedCount} speakers remaining.`,
          'polite'
        );
      }, 300); // Brief delay for UI update
    }
  } else {
    // All assigned!
    // Focus success banner
    setTimeout(() => {
      const successBanner = document.querySelector('.success-banner a');
      successBanner?.focus();
    }, 500); // Wait for celebration animation
  }
}

// Example: Auto-Assign Modal
function completeAutoAssign(assignments) {
  // Apply assignments
  assignments.forEach(assignment => {
    assignSpeakerToSlot(assignment.speaker, assignment.slot);
  });

  // Close modal
  const modal = document.querySelector('[role="dialog"]');
  hideAutoAssignModal();

  // Determine focus target
  if (assignments.length === totalSessions) {
    // All assigned, focus success banner
    setTimeout(() => {
      const successBanner = document.querySelector('.success-banner');
      successBanner?.focus();
      announceToScreenReader(
        'Auto-assignment complete. All sessions assigned. ' +
        'Focus moved to success banner. ' +
        'Press Enter to view publishing options.',
        'assertive'
      );
    }, 500);
  } else {
    // Some remain, focus first unassigned speaker
    setTimeout(() => {
      const firstUnassigned = findNextUnassignedSpeaker();
      firstUnassigned?.focus();
      announceToScreenReader(
        `Auto-assignment complete. ${assignments.length} sessions assigned. ` +
        `${totalSessions - assignments.length} sessions require manual assignment. ` +
        `Focus moved to first unassigned speaker.`,
        'polite'
      );
    }, 300);
  }
}
```

### Focus Trap in Modals

```javascript
// Focus trap implementation for modals

class FocusTrap {
  constructor(modalElement) {
    this.modal = modalElement;
    this.focusableElements = null;
    this.firstFocusable = null;
    this.lastFocusable = null;
  }

  activate() {
    // Get all focusable elements within modal
    this.focusableElements = this.modal.querySelectorAll(
      'a[href], button:not([disabled]), textarea:not([disabled]), ' +
      'input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'
    );

    this.firstFocusable = this.focusableElements[0];
    this.lastFocusable = this.focusableElements[this.focusableElements.length - 1];

    // Trap focus
    this.modal.addEventListener('keydown', this.handleKeyDown);

    // Focus first element
    this.firstFocusable.focus();
  }

  deactivate() {
    this.modal.removeEventListener('keydown', this.handleKeyDown);
  }

  handleKeyDown = (e) => {
    if (e.key === 'Tab') {
      if (e.shiftKey) {
        // Shift+Tab
        if (document.activeElement === this.firstFocusable) {
          e.preventDefault();
          this.lastFocusable.focus();
        }
      } else {
        // Tab
        if (document.activeElement === this.lastFocusable) {
          e.preventDefault();
          this.firstFocusable.focus();
        }
      }
    } else if (e.key === 'Escape') {
      // Close modal
      this.closeModal();
    }
  };

  closeModal() {
    // Implementation specific to modal type
    this.deactivate();
    // ... close modal logic
  }
}

// Usage
function showConflictModal(conflict) {
  const modal = document.getElementById('conflict-modal');
  modal.classList.add('visible');

  const focusTrap = new FocusTrap(modal);
  focusTrap.activate();

  // Store for later cleanup
  modal._focusTrap = focusTrap;

  return modal;
}

function hideConflictModal() {
  const modal = document.getElementById('conflict-modal');
  modal._focusTrap?.deactivate();
  modal.classList.remove('visible');
}
```

## 5. Color Contrast

### Text Contrast Ratios

**WCAG AA Requirements:**
- Normal text (< 18pt): 4.5:1 minimum
- Large text (≥ 18pt): 3:1 minimum
- UI components: 3:1 minimum

**Color Palette with Verified Ratios:**

```
Background: #FFFFFF (white)

Text Colors:
- Primary text: #212121 (near black) → 16.1:1 ✓
- Secondary text: #757575 (gray) → 4.6:1 ✓
- Disabled text: #9E9E9E (light gray) → 2.9:1 ⚠️ (large text only)

Interactive Elements:
- Primary blue: #1976D2 on white → 4.6:1 ✓
- Success green: #388E3C on white → 4.5:1 ✓
- Warning orange: #F57C00 on white → 3.4:1 ⚠️ (use with icon/pattern)
- Error red: #D32F2F on white → 4.5:1 ✓

Status Badges:
- Unassigned orange: #FF9800 text on #FFF3E0 bg → 5.2:1 ✓
- Assigned green: #2E7D32 text on #E8F5E9 bg → 4.8:1 ✓
- In Progress purple: #6A1B9A text on #F3E5F5 bg → 5.1:1 ✓

Match Score Indicators:
- Strong match: #2E7D32 (dark green) on white → 6.4:1 ✓
- Acceptable match: #F57C00 (orange) on white → 3.4:1 ⚠️
  → Paired with icon: 🟡 for redundancy
- Poor match: #C62828 (dark red) on white → 5.3:1 ✓

Focus Indicators:
- Focus outline: #2196F3 (blue) on white → 3.2:1 ✓
- Focus halo: rgba(33, 150, 243, 0.25) → decorative, not relied upon
```

**High Contrast Mode Adaptations:**

```css
/* Detect high contrast mode preference */
@media (prefers-contrast: high) {
  /* Increase all contrast ratios */
  body {
    --text-primary: #000000; /* Pure black */
    --text-secondary: #424242; /* Darker gray, 11:1 */
    --border-color: #000000;
  }

  /* Thicker borders */
  .speaker-card,
  .session-cell {
    border-width: 2px;
  }

  /* No subtle backgrounds */
  .speaker-card:hover,
  .session-cell:hover {
    background: transparent;
    border-color: #000000;
  }

  /* Focus indicators extra visible */
  *:focus-visible {
    outline-width: 3px;
    outline-color: currentColor;
  }

  /* Match scores with patterns, not just color */
  .match-strong::before {
    content: "✓✓ "; /* Double checkmark */
  }

  .match-acceptable::before {
    content: "✓ "; /* Single checkmark */
  }

  .match-poor::before {
    content: "⚠ "; /* Warning */
  }
}
```

### Non-Color Indicators

**Always provide non-color cues:**

```
Match Score Indicators (multi-sensory):

🟢 85% Strong Match
│
├─ Color: Green (#2E7D32)
├─ Icon: 🟢 or ✓✓
├─ Text: "Strong Match"
└─ Pattern: Solid border

🟡 65% Acceptable Match
│
├─ Color: Orange (#F57C00)
├─ Icon: 🟡 or ✓
├─ Text: "Acceptable Match"
└─ Pattern: Dashed border

🔴 30% Poor Match
│
├─ Color: Red (#C62828)
├─ Icon: 🔴 or ⚠
├─ Text: "Poor Match"
└─ Pattern: Dotted border

Status Indicators (multi-sensory):

Unassigned Speaker:
├─ Color: Orange border (#FF9800)
├─ Icon: 🔶 or ○
├─ Text: "Not assigned"
├─ Pattern: Dashed border
└─ Badge: "🔶 5 Remaining"

Assigned Speaker:
├─ Color: Green border (#4CAF50)
├─ Icon: ✓ or ●
├─ Text: "Assigned to [Session]"
├─ Pattern: Solid border
└─ Timestamp: "Assigned 2 mins ago"
```

## 6. Alternative Interaction Patterns

### Reduced Motion

**Respect `prefers-reduced-motion`:**

```css
/* Default: Smooth animations */
.speaker-card {
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.speaker-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
}

.progress-bar-fill {
  transition: width 0.5s ease-out;
}

.confetti-animation {
  animation: confetti-fall 3s ease-out;
}

/* Reduced motion: Instant transitions or minimal motion */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }

  .speaker-card:hover {
    transform: none; /* No lift effect */
    box-shadow: 0 0 0 2px #2196f3; /* Solid border instead */
  }

  .progress-bar-fill {
    transition: none; /* Instant fill */
  }

  .confetti-animation {
    animation: none; /* No confetti */
  }

  /* Replace animations with simple visual changes */
  .success-banner {
    /* No slide-in, just appear */
    animation: none;
  }

  /* Fade is acceptable (minimal motion) */
  .modal-overlay {
    animation: fade-in 0.15s ease;
  }

  @keyframes fade-in {
    from { opacity: 0; }
    to { opacity: 1; }
  }
}
```

**Alternative Success Feedback (No Confetti):**

```
Standard Success State:
┌─────────────────────────────────────────────────────────────────────┐
│  🎉 🎊 SUCCESS! All Session Timings Assigned! 🎊 🎉                 │
│  [Confetti animation plays for 3 seconds]                           │
│  8 of 8 sessions complete (100%)                                    │
│  Average match score: 82%                                           │
│  [View Publishing Options →]                                        │
└─────────────────────────────────────────────────────────────────────┘

Reduced Motion Success State:
┌─────────────────────────────────────────────────────────────────────┐
│  ✓✓✓ SUCCESS! All Session Timings Assigned! ✓✓✓                    │
│  [Solid green background, no animation]                             │
│  8 of 8 sessions complete (100%)                                    │
│  Average match score: 82%                                           │
│  [View Publishing Options →]  ← Highlight with pulsing border      │
└─────────────────────────────────────────────────────────────────────┘

@media (prefers-reduced-motion: reduce) {
  .success-banner::before {
    content: "✓✓✓ "; /* Triple checkmark instead of emoji */
  }

  .success-banner::after {
    content: " ✓✓✓";
  }

  .success-banner .cta-button {
    /* Pulsing border instead of confetti */
    animation: pulse-border 1.5s ease-in-out 3;
  }

  @keyframes pulse-border {
    0%, 100% { box-shadow: 0 0 0 0 rgba(76, 175, 80, 0.7); }
    50% { box-shadow: 0 0 0 6px rgba(76, 175, 80, 0); }
  }
}
```

### Touch Target Sizing

**Minimum 44x44px touch targets (WCAG 2.5.5):**

```css
/* All interactive elements */
button,
a,
input,
select,
[role="button"],
[role="link"] {
  min-width: 44px;
  min-height: 44px;
  padding: 8px 16px; /* Ensure content doesn't make it smaller */
}

/* Exception: Inline text links can be smaller if sufficient spacing */
p a,
li a {
  min-width: auto;
  min-height: auto;
  padding: 4px 2px;
  margin: 0 4px; /* Spacing from surrounding text */
}

/* Speaker card: Entire card is touch target */
.speaker-card {
  min-height: 120px; /* Well above 44px */
  padding: 16px;
  cursor: pointer;
  touch-action: manipulation; /* Disable double-tap zoom */
}

/* Session grid cells */
.session-cell {
  min-height: 80px; /* Well above 44px */
  min-width: 150px;
  padding: 12px;
}

/* Button groups: Ensure spacing between targets */
.button-group button {
  margin-right: 12px; /* 12px spacing prevents accidental taps */
}

.button-group button:last-child {
  margin-right: 0;
}

/* Small icon buttons: Add padding to reach 44x44 */
.icon-button {
  width: 24px; /* Icon size */
  height: 24px;
  padding: 10px; /* Total: 44x44 */
}

/* Filter buttons (small text, need padding) */
.filter-button {
  font-size: 14px;
  padding: 12px 20px; /* Reaches 44px height */
  margin: 0 4px; /* Spacing from adjacent buttons */
}
```

**Touch Target Visualization (Dev Mode):**

```css
/* Debug mode: Show touch target areas */
[data-debug-touch-targets="true"] button::after,
[data-debug-touch-targets="true"] a::after,
[data-debug-touch-targets="true"] [role="button"]::after {
  content: "";
  position: absolute;
  inset: 0;
  border: 1px dashed red;
  pointer-events: none;
  z-index: 9999;
}

/* Warning for undersized targets */
[data-debug-touch-targets="true"] button:where(:not([style*="min-height: 44px"]))::before {
  content: "⚠️ <44px";
  position: absolute;
  top: -20px;
  left: 0;
  background: yellow;
  color: black;
  font-size: 10px;
  padding: 2px 4px;
  z-index: 9999;
}
```

### Voice Control Support

**Voice command labels:**

```html
<!-- Explicit labels for voice control -->
<button aria-label="Auto-assign all sessions" data-voice-command="auto assign all">
  Auto-Assign All
</button>

<button aria-label="View speaker preferences for Dr. Sarah Miller"
        data-voice-command="preferences Sarah Miller">
  Preferences
</button>

<!-- Grid cells with speakable names -->
<div role="gridcell"
     aria-label="Workshop 2, 14:00 to 15:30, Room A"
     data-voice-command="workshop two">
  <h4>Workshop 2: Advanced Topics</h4>
  <!-- ... -->
</div>

<!-- Links with clear targets -->
<a href="/organizer/events/batbern-2025?tab=publishing"
   aria-label="Proceed to publishing options"
   data-voice-command="go to publishing">
  View Publishing Options →
</a>
```

**Voice command hints (shown on request):**

```
Voice Control Help Modal:

Available Commands:

Navigation:
- "Show speakers" → Focus speaker pool
- "Show timeline" → Focus session grid
- "Show actions" → Focus quick actions panel
- "Go back" → Navigate to event page

Assigning Sessions:
- "Assign [Speaker Name]" → Enter assign mode for speaker
- "Assign to [Session Name]" → Assign active speaker to session
- "Cancel assignment" → Exit assign mode

Quick Actions:
- "Auto assign all" → Open auto-assign modal
- "Clear assignments" → Clear all current assignments
- "Download schedule" → Download PDF schedule

View Information:
- "Show preferences [Speaker Name]" → Open preferences drawer
- "Show session info [Session Name]" → Open session details
- "Show help" → Open help modal

Publishing:
- "Go to publishing" → Navigate to publishing tab
- "View progress" → Announce current progress
```

## 7. Error States and Recovery

### Accessible Error Messages

**Error announcement strategy:**

```html
<!-- Error container (live region) -->
<div role="alert"
     aria-live="assertive"
     aria-atomic="true"
     class="error-message"
     id="assignment-error">
  <!-- Error injected here -->
</div>

<!-- Example: Conflict error -->
<div role="alert" class="error-message visible">
  <div class="error-icon" aria-hidden="true">⚠️</div>

  <div class="error-content">
    <h3 id="error-title">Timing Conflict Detected</h3>

    <p id="error-description">
      Room A is already occupied at 14:00-15:30 by Panel Discussion.
      Please choose an alternative slot or resolve the conflict.
    </p>

    <div class="error-actions">
      <button aria-describedby="error-description">
        Find Alternative Slot
      </button>
      <button aria-describedby="error-description">
        Change Room
      </button>
      <button aria-describedby="error-description">
        Cancel Assignment
      </button>
    </div>
  </div>

  <button class="error-dismiss"
          aria-label="Dismiss error message">
    ✕
  </button>
</div>

<style>
.error-message {
  background: #FFEBEE; /* Light red */
  border-left: 4px solid #D32F2F; /* Dark red */
  padding: 16px;
  margin: 16px 0;
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.error-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.error-content {
  flex-grow: 1;
}

.error-content h3 {
  color: #C62828; /* Dark red, 5.3:1 on light bg */
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
}

.error-content p {
  color: #212121; /* Near black, 16:1 */
  margin: 0 0 12px 0;
}

.error-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.error-actions button {
  background: #D32F2F;
  color: white;
  border: none;
  padding: 8px 16px;
  min-height: 44px;
  border-radius: 4px;
  cursor: pointer;
}

.error-actions button:hover {
  background: #B71C1C;
}

.error-actions button:focus-visible {
  outline: 2px solid #212121;
  outline-offset: 2px;
}

.error-dismiss {
  background: transparent;
  border: none;
  font-size: 20px;
  color: #757575;
  cursor: pointer;
  padding: 4px 8px;
  min-width: 44px;
  min-height: 44px;
}
</style>

<script>
// Show error with announcement
function showError(errorType, errorData) {
  const errorContainer = document.getElementById('assignment-error');

  // Build error message
  const errorMessage = buildErrorMessage(errorType, errorData);

  // Inject into DOM
  errorContainer.innerHTML = errorMessage.html;
  errorContainer.classList.add('visible');

  // Focus first action button
  const firstAction = errorContainer.querySelector('.error-actions button');
  setTimeout(() => firstAction?.focus(), 100);

  // Screen reader announcement (automatic via role="alert")
  // Additional explicit announcement for emphasis
  announceToScreenReader(
    `Error: ${errorMessage.title}. ${errorMessage.description}`,
    'assertive'
  );
}
</script>
```

**Inline validation feedback:**

```html
<!-- Form field with validation -->
<div class="form-field" data-error="false">
  <label for="session-duration">
    Session Duration (minutes)
    <span aria-label="required">*</span>
  </label>

  <input type="number"
         id="session-duration"
         name="duration"
         min="30"
         max="240"
         required
         aria-required="true"
         aria-invalid="false"
         aria-describedby="duration-hint duration-error">

  <p id="duration-hint" class="field-hint">
    Enter duration between 30-240 minutes
  </p>

  <p id="duration-error"
     class="field-error"
     role="alert"
     aria-live="assertive"
     hidden>
    <!-- Error message injected here -->
  </p>
</div>

<style>
.form-field[data-error="true"] input {
  border-color: #D32F2F; /* Red border */
  border-width: 2px;
}

.field-error {
  color: #C62828; /* Dark red */
  font-size: 14px;
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.field-error::before {
  content: "⚠️";
  flex-shrink: 0;
}
</style>

<script>
// Client-side validation
function validateSessionDuration(input) {
  const value = parseInt(input.value);
  const errorElement = document.getElementById('duration-error');
  const fieldContainer = input.closest('.form-field');

  // Clear previous error
  errorElement.hidden = true;
  errorElement.textContent = '';
  input.setAttribute('aria-invalid', 'false');
  fieldContainer.setAttribute('data-error', 'false');

  // Validate
  if (isNaN(value) || value < 30 || value > 240) {
    const errorMessage =
      isNaN(value)
        ? 'Please enter a valid number'
        : value < 30
          ? 'Duration must be at least 30 minutes'
          : 'Duration cannot exceed 240 minutes';

    // Show error
    errorElement.textContent = errorMessage;
    errorElement.hidden = false;
    input.setAttribute('aria-invalid', 'true');
    fieldContainer.setAttribute('data-error', 'true');

    // Focus input
    input.focus();

    return false;
  }

  return true;
}
</script>
```

### Recovery Guidance

**Clear paths to resolution:**

```
Error State: Conflict Detected
┌─────────────────────────────────────────────────────────────────────┐
│  ⚠️ Timing Conflict Detected                                        │
│                                                                     │
│  Problem:                                                           │
│  Room A is already occupied at 14:00-15:30 by Panel Discussion.    │
│                                                                     │
│  What you can do:                                                   │
│  1. [Find Alternative Slot]                                         │
│     System will suggest available slots matching speaker prefs     │
│     (Recommended if flexible on timing)                             │
│                                                                     │
│  2. [Change Room]                                                   │
│     Keep timing 14:00-15:30, assign to different room              │
│     (Recommended if timing is important)                            │
│                                                                     │
│  3. [Reassign Panel Discussion]                                     │
│     Move Panel to different time/room to free this slot            │
│     (Use if this slot is critical for Workshop 2)                  │
│                                                                     │
│  4. [Cancel]                                                        │
│     Abort this assignment, try a different approach                │
│                                                                     │
│  Need help deciding? [View Conflict Guide]                          │
└─────────────────────────────────────────────────────────────────────┘

Keyboard Shortcuts:
- 1-4: Select option by number
- Esc: Cancel (same as option 4)
- ?: View conflict resolution guide
```

**Undo/Redo Support:**

```
Assignment History (accessible via Ctrl+Z / Ctrl+Y):

Recent Actions:
┌─────────────────────────────────────────────────────────────────────┐
│  Last 5 actions (can undo):                                         │
│                                                                     │
│  5. [UNDO] Assigned Prof. Chen to Panel Discussion (2 mins ago)    │
│  4. [UNDO] Assigned Dr. Miller to Workshop 2 (5 mins ago)          │
│  3. [UNDO] Unassigned Jane Doe from Keynote (8 mins ago)           │
│  2. [UNDO] Auto-assigned 3 sessions (10 mins ago)                  │
│  1. Cleared all assignments (15 mins ago) - Cannot undo            │
│                                                                     │
│  [Undo Last Action] (Ctrl+Z)                                        │
│  [Redo] (Ctrl+Y) - Currently no actions to redo                    │
│  [Clear History]                                                    │
└─────────────────────────────────────────────────────────────────────┘

Undo Announcement (screen reader):
"Action undone. Dr. Miller unassigned from Workshop 2.
 Session now available. Progress: 3 of 8 sessions assigned.
 Press Ctrl+Y to redo if this was a mistake."

Implementation:
- Undo stack: Last 10 actions
- Redo stack: Cleared when new action performed
- Undo not available: Initial state, published assignments
- Auto-save: Every 30 seconds (persists undo history)
```

## 8. Responsive Accessibility

### Mobile Adaptations

**Touch-first interaction patterns:**

```
Desktop: Drag-and-drop with mouse
Mobile: Tap to select, tap to assign

Mobile Speaker Assignment Flow:
┌─────────────────────────────────────┐
│  Step 1: Tap speaker card           │
├─────────────────────────────────────┤
│  🔶 Dr. Sarah Miller                │ ← Tap activates assign mode
│  Topic: AI in Architecture          │
│  ☀️ Prefers: Afternoon              │
│                                     │
│  [Tap to assign] ← 44px height      │
└─────────────────────────────────────┘

After tap:
┌─────────────────────────────────────┐
│  ✓ Dr. Sarah Miller - SELECTED      │ ← Purple background
│  Tap a time slot below to assign    │
│                                     │
│  [✕ Cancel]                         │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  Step 2: Scroll to session grid     │
├─────────────────────────────────────┤
│  Time slots (tap to assign):        │
│                                     │
│  ┌─────────────────────────────────┐│
│  │ 14:00-15:30 | Room A             ││ ← Each cell 80px height (touch)
│  │ Workshop 2                       ││
│  │ Match: 🟢 85% Strong             ││
│  │                                  ││
│  │ [Tap to assign here]             ││
│  └─────────────────────────────────┘│
│                                     │
│  ┌─────────────────────────────────┐│
│  │ 16:00-17:30 | Main Hall          ││
│  │ Evening Panel                    ││
│  │ Match: 🟡 65% Acceptable         ││
│  │                                  ││
│  │ [Tap to assign here]             ││
│  └─────────────────────────────────┘│
└─────────────────────────────────────┘

After tap:
┌─────────────────────────────────────┐
│  ✓ Assignment Confirmed              │ ← Success toast
│  Dr. Miller → Workshop 2             │
│  14:00-15:30, Room A                 │
│  Match: 85%                          │
│                                     │
│  [Undo] [Continue]                  │
└─────────────────────────────────────┘
```

**Mobile-specific accessibility features:**

```css
/* Larger touch targets on mobile */
@media (max-width: 768px) {
  button,
  a,
  .speaker-card,
  .session-cell {
    min-height: 48px; /* Larger than desktop 44px */
    padding: 12px 16px;
  }

  /* Increased spacing to prevent accidental taps */
  .button-group button {
    margin-bottom: 12px; /* Stack vertically */
    width: 100%;
  }

  /* Bottom sheet instead of drawer */
  .speaker-preferences-panel {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    max-height: 80vh;
    border-radius: 16px 16px 0 0;
  }

  /* Swipe-to-dismiss hint */
  .speaker-preferences-panel::before {
    content: "";
    display: block;
    width: 40px;
    height: 4px;
    background: #BDBDBD;
    border-radius: 2px;
    margin: 12px auto;
  }

  /* Modal overlays full screen */
  .modal-dialog {
    width: 100vw;
    height: 100vh;
    max-width: none;
    margin: 0;
    border-radius: 0;
  }

  /* Sticky success banner */
  .success-banner {
    position: sticky;
    bottom: 0;
    z-index: 100;
  }
}
```

### Screen Size Adaptations

**Breakpoint-specific focus order:**

```javascript
// Adjust focus order based on screen size
function updateFocusOrder() {
  const isDesktop = window.matchMedia('(min-width: 1024px)').matches;
  const isTablet = window.matchMedia('(min-width: 768px) and (max-width: 1023px)').matches;
  const isMobile = window.matchMedia('(max-width: 767px)').matches;

  if (isMobile) {
    // Mobile: Single column, linear flow
    // Focus order: Header → Speakers → Timeline → Actions → Success
    setSpeakerPoolTabIndex(1000);
    setTimelineTabIndex(2000);
    setActionsTabIndex(3000);
  } else if (isTablet) {
    // Tablet: Two columns (speakers left, timeline/actions right)
    // Focus order: Header → Speakers → Timeline → Actions → Success
    setSpeakerPoolTabIndex(1000);
    setTimelineTabIndex(2000);
    setActionsTabIndex(3000);
  } else {
    // Desktop: Three columns (speakers left, timeline center, actions right)
    // Focus order: Header → Speakers → Timeline → Actions → Success
    setSpeakerPoolTabIndex(1000);
    setTimelineTabIndex(2000);
    setActionsTabIndex(3000);
  }
}

// Re-calculate on resize
window.addEventListener('resize', debounce(updateFocusOrder, 250));
```

## 9. Testing Recommendations

### Manual Testing Checklist

**Keyboard Navigation:**
- [ ] Can complete entire workflow using only keyboard
- [ ] Tab order is logical and predictable
- [ ] Focus indicators visible on all interactive elements
- [ ] Escape key closes modals and cancels operations
- [ ] Arrow keys navigate grid in expected directions
- [ ] Keyboard shortcuts work as documented

**Screen Reader:**
- [ ] All images have alt text or aria-labels
- [ ] Form fields have associated labels
- [ ] Buttons have descriptive labels
- [ ] Status messages announced via live regions
- [ ] Error messages announced immediately
- [ ] Dynamic content changes announced
- [ ] Page structure navigable by landmarks
- [ ] Heading hierarchy logical (no skipped levels)

**Color and Contrast:**
- [ ] All text meets 4.5:1 contrast (normal) or 3:1 (large)
- [ ] UI components meet 3:1 contrast
- [ ] Information not conveyed by color alone
- [ ] High contrast mode works correctly
- [ ] Focus indicators visible in all color modes

**Touch and Motor:**
- [ ] All touch targets at least 44x44px (48px on mobile)
- [ ] Sufficient spacing between interactive elements
- [ ] No fine motor control required (e.g., precise dragging)
- [ ] Alternative interaction methods available (tap vs drag)
- [ ] Accidental activation prevented (confirmation for destructive actions)

**Reduced Motion:**
- [ ] Essential animations simplified or removed
- [ ] Confetti/celebration animations disabled
- [ ] Transitions instant or minimal
- [ ] Progress still clear without animations

**Voice Control:**
- [ ] All interactive elements have voice-speakable labels
- [ ] Voice commands recognized correctly
- [ ] Ambiguous elements have unique labels

### Automated Testing

**axe-core integration:**

```javascript
// Automated accessibility tests using axe-core

import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Slot Assignment Accessibility', () => {
  test('should not have automatically detectable accessibility issues', async ({ page }) => {
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('speaker pool should be accessible', async ({ page }) => {
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .include('.speaker-pool')
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('session grid should be accessible', async ({ page }) => {
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .include('[role="grid"]')
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('modals should be accessible', async ({ page }) => {
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    // Open auto-assign modal
    await page.click('button:has-text("Auto-Assign All")');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .include('[role="dialog"]')
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });
});
```

**Keyboard navigation tests:**

```javascript
test.describe('Keyboard Navigation', () => {
  test('should navigate speaker pool with keyboard', async ({ page }) => {
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    // Tab to first speaker
    await page.keyboard.press('Tab'); // Skip link
    await page.keyboard.press('Tab'); // Breadcrumb
    await page.keyboard.press('Tab'); // Back button
    await page.keyboard.press('Tab'); // Filter: All
    await page.keyboard.press('Tab'); // Filter: Assigned
    await page.keyboard.press('Tab'); // Filter: Unassigned
    await page.keyboard.press('Tab'); // First speaker

    const focusedElement = await page.evaluate(() => document.activeElement.className);
    expect(focusedElement).toContain('speaker-card');

    // Navigate with arrow keys
    await page.keyboard.press('ArrowDown');
    const secondSpeaker = await page.evaluate(() => document.activeElement.textContent);
    expect(secondSpeaker).toContain('Prof.'); // Second speaker
  });

  test('should complete assignment with keyboard', async ({ page }) => {
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    // Navigate to first unassigned speaker
    const speaker = await page.locator('.speaker-card[data-assigned="false"]').first();
    await speaker.focus();

    // Enter assign mode
    await page.keyboard.press('Enter');

    // Verify assign mode
    const assignMode = await speaker.getAttribute('data-assign-mode');
    expect(assignMode).toBe('true');

    // Navigate to session grid
    await page.keyboard.press('Tab');

    // Should focus first available slot
    const focusedSlot = await page.evaluate(() => document.activeElement.className);
    expect(focusedSlot).toContain('session-cell');

    // Confirm assignment
    await page.keyboard.press('Enter');

    // Verify assignment
    const announcement = await page.locator('[role="status"]').textContent();
    expect(announcement).toContain('assigned');
  });

  test('should escape from assign mode', async ({ page }) => {
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    const speaker = await page.locator('.speaker-card').first();
    await speaker.focus();
    await page.keyboard.press('Enter');

    // Verify assign mode active
    let assignMode = await speaker.getAttribute('data-assign-mode');
    expect(assignMode).toBe('true');

    // Press Escape
    await page.keyboard.press('Escape');

    // Verify assign mode cancelled
    assignMode = await speaker.getAttribute('data-assign-mode');
    expect(assignMode).toBe('false');
  });
});
```

**Screen reader tests:**

```javascript
test.describe('Screen Reader Support', () => {
  test('should announce progress updates', async ({ page }) => {
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    // Perform assignment
    await performAssignment(page, 'Dr. Sarah Miller', 'Workshop 2');

    // Check live region announcement
    const announcement = await page.locator('[role="status"][aria-live="polite"]').textContent();
    expect(announcement).toMatch(/assigned to Workshop 2/);
    expect(announcement).toMatch(/Progress: \d+ of \d+ sessions/);
  });

  test('should announce errors assertively', async ({ page }) => {
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    // Trigger conflict
    await performConflictingAssignment(page);

    // Check alert announcement
    const alert = await page.locator('[role="alert"]').textContent();
    expect(alert).toMatch(/Conflict/);
    expect(alert).toMatch(/Room.*already occupied/);
  });

  test('should provide descriptive labels', async ({ page }) => {
    await page.goto('/organizer/events/batbern-2025/slot-assignment');

    // Check speaker card label
    const speakerLabel = await page.locator('.speaker-card').first().getAttribute('aria-label');
    expect(speakerLabel).toMatch(/Dr\.|Prof\./); // Speaker name
    expect(speakerLabel).toMatch(/not assigned|assigned to/); // Status

    // Check session cell label
    const sessionLabel = await page.locator('[role="gridcell"]').first().getAttribute('aria-label');
    expect(sessionLabel).toMatch(/\d{2}:\d{2}/); // Time
    expect(sessionLabel).toMatch(/Room/); // Room name
  });
});
```

## Technical Implementation Notes

### React Component Accessibility Pattern

```typescript
// SlotAssignmentPage.tsx - Accessibility implementation

import React, { useRef, useEffect, useState } from 'react';
import { useAnnouncer } from '@/hooks/useAnnouncer';
import { useKeyboardNavigation } from '@/hooks/useKeyboardNavigation';

export const SlotAssignmentPage: React.FC = () => {
  const announcerRef = useRef<HTMLDivElement>(null);
  const { announce } = useAnnouncer(announcerRef);
  const [assignMode, setAssignMode] = useState<string | null>(null);

  // Keyboard navigation hook
  useKeyboardNavigation({
    onEscape: () => {
      if (assignMode) {
        cancelAssignMode();
        announce('Assignment cancelled', 'polite');
      }
    },
    onCtrlA: () => openAutoAssignModal(),
    onSlash: () => focusSearchInput(),
  });

  // Focus management after assignment
  const handleAssignment = async (speakerId: string, slotId: string) => {
    const result = await assignSpeakerToSlot(speakerId, slotId);

    // Announce result
    announce(
      `${result.speakerName} assigned to ${result.sessionName}. ` +
      `Match score: ${result.matchScore} percent. ` +
      `Progress: ${result.assignedCount} of ${result.totalCount} sessions assigned.`,
      'polite'
    );

    // Focus next unassigned speaker
    if (result.assignedCount < result.totalCount) {
      const nextSpeaker = findNextUnassignedSpeaker(speakerId);
      nextSpeaker?.focus();
    } else {
      // All assigned, focus success banner
      const successBanner = document.querySelector('.success-banner a');
      (successBanner as HTMLElement)?.focus();
    }
  };

  return (
    <main role="main" aria-labelledby="page-title">
      <h1 id="page-title">Assign Session Timings - BATbern 2025</h1>

      {/* Announcer live regions */}
      <div className="sr-only">
        <div role="status" aria-live="polite" aria-atomic="true" ref={announcerRef} />
        <div role="alert" aria-live="assertive" aria-atomic="true" id="alert-region" />
      </div>

      {/* Skip link */}
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>

      {/* Speaker pool */}
      <aside role="complementary" aria-labelledby="speaker-pool-title">
        <h2 id="speaker-pool-title">Speakers</h2>
        <SpeakerPool
          onAssignModeStart={(speakerId) => setAssignMode(speakerId)}
          onAssignment={handleAssignment}
        />
      </aside>

      {/* Session grid */}
      <section id="main-content" role="region" aria-labelledby="timeline-title">
        <h2 id="timeline-title">Session Timeline</h2>
        <SessionGrid
          assignMode={assignMode}
          onAssignment={handleAssignment}
        />
      </section>

      {/* Success banner */}
      {allAssigned && (
        <div role="status" aria-live="assertive" className="success-banner">
          <h2>All session timings assigned!</h2>
          <p>8 of 8 sessions complete. Ready to proceed to publishing.</p>
          <a href="/organizer/events/batbern-2025?tab=publishing">
            View Publishing Options
          </a>
        </div>
      )}
    </main>
  );
};

// Keyboard navigation hook
function useKeyboardNavigation(handlers: {
  onEscape?: () => void;
  onCtrlA?: () => void;
  onSlash?: () => void;
}) {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && handlers.onEscape) {
        handlers.onEscape();
      } else if (e.key === 'a' && e.ctrlKey && handlers.onCtrlA) {
        e.preventDefault();
        handlers.onCtrlA();
      } else if (e.key === '/' && handlers.onSlash) {
        e.preventDefault();
        handlers.onSlash();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [handlers]);
}

// Announcer hook
function useAnnouncer(ref: React.RefObject<HTMLDivElement>) {
  const announce = (message: string, priority: 'polite' | 'assertive' = 'polite') => {
    const region = priority === 'assertive'
      ? document.getElementById('alert-region')
      : ref.current;

    if (region) {
      region.textContent = '';
      void region.offsetHeight; // Trigger reflow
      region.textContent = message;
    }
  };

  return { announce };
}
```

## Summary

This wireframe documents comprehensive accessibility features for the slot assignment workflow, ensuring WCAG 2.1 Level AA compliance and excellent user experience for people with disabilities:

**Key Features:**
1. **Full keyboard navigation** with alternatives to drag-drop
2. **Comprehensive ARIA attributes** for screen reader support
3. **Focus management** with clear visual indicators and logical flow
4. **Color contrast** meeting WCAG AA standards (4.5:1 text, 3:1 UI)
5. **Reduced motion** support with animations disabled/simplified
6. **Touch-friendly** with 44x44px minimum touch targets (48px mobile)
7. **Error recovery** with clear messages and resolution guidance
8. **Responsive** adaptations for mobile, tablet, desktop

**Testing Strategy:**
- Automated tests with axe-core (no violations)
- Manual testing checklist covering all WCAG criteria
- Keyboard navigation E2E tests
- Screen reader announcement tests
- Voice control support

**Implementation Priority:**
- All features marked as critical for Phase 3 (Polish)
- WCAG 2.1 Level AA compliance is **non-negotiable**
- Must pass automated accessibility scans before production deployment

---

**Next Steps:**
1. Review wireframe with accessibility specialist
2. Conduct user testing with people using assistive technologies
3. Implement accessible components following this specification
4. Run automated accessibility tests in CI/CD pipeline
5. Perform manual accessibility audit before production release
