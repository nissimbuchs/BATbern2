# Story 5.7: Speaker Preferences Panel - Slot Assignment

**Story**: Epic 5, Story 5.7 - Slot Assignment & Progressive Publishing
**Component**: Speaker Preference Display & Match Scoring
**User Role**: Organizer
**Related FR**: FR17 (Speaker Matching), AC7 (Display Preferences), AC8 (Track A/V Needs), AC11 (Preference Match Highlighting)

---

## Purpose

This wireframe documents how speaker preferences are displayed during session timing assignment, including time preferences, A/V requirements, room setup needs, and real-time preference match scoring as organizers assign timings.

**Context**: Used within the dedicated slot assignment page during drag-drop timing assignment workflow.

---

## Preference Data Model

### Speaker Preferences Schema

```typescript
interface SpeakerPreferences {
  // Time preferences
  timeOfDay: {
    morning: 'preferred' | 'neutral' | 'avoid';    // 6:00 - 12:00
    afternoon: 'preferred' | 'neutral' | 'avoid';  // 12:00 - 18:00
    evening: 'preferred' | 'neutral' | 'avoid';    // 18:00 - 22:00
  };

  // Specific avoid times
  specificAvoidTimes: Array<{
    start: Date;
    end: Date;
    reason: string;  // e.g., "Morning commute", "Prior commitment"
  }>;

  // A/V requirements
  avRequirements: {
    microphone: 'required' | 'optional' | 'not_needed';
    projector: 'required' | 'optional' | 'not_needed';
    clickerRemote: 'required' | 'optional' | 'not_needed';
    whiteboard: 'required' | 'optional' | 'not_needed';
    recordingPermission: 'approved' | 'denied' | 'pending';
  };

  // Room setup preferences
  roomSetup: {
    standingDesk: 'preferred' | 'neutral' | 'not_needed';
    naturalLight: 'preferred' | 'neutral' | 'not_needed';
    flipChart: 'preferred' | 'neutral' | 'not_needed';
    quietRoom: 'preferred' | 'neutral' | 'not_needed';
    notes: string;  // Free-form text
  };

  // Session preferences
  sessionPreferences: {
    preferredDuration: number;  // In minutes (60, 90, 120, etc.)
    maxSessionsPerDay: number;
  };
}
```

---

## UI Component Types

### 1. Tooltip (Hover Summary)

**Trigger**: Mouse hover over speaker card in pool sidebar

**Purpose**: Quick preview of key preferences without opening full drawer

```
┌─────────────────────────────────────────────────────────────────────┐
│  Speaker Pool                                                       │
│  ┌────────────────────────────────────┐                            │
│  │ 🔶 Dr. Sarah Miller  ☀️ 🌙         │ ← Mouse hover here         │
│  │ AI in Architecture                  │                            │
│  │ [View Preferences]                  │                            │
│  └────────────────────────────────────┘                            │
│       │                                                             │
│       └──────────────────────────────────────────────────┐          │
│                                                          ↓          │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ Dr. Sarah Miller - Quick Preferences                          │ │
│  │                                                               │ │
│  │ Time:     ☀️ Prefers afternoon                                 │ │
│  │           ⊘ Avoid morning (before 09:00)                      │ │
│  │ A/V:      Microphone, Projector required                     │ │
│  │ Room:     Natural light, Quiet room preferred                │ │
│  │                                                               │ │
│  │ Click [View Preferences] for full details →                  │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Tooltip Appearance**:
- **Position**: Below speaker card, aligned left
- **Delay**: Appears after 500ms hover
- **Dismiss**: Disappears on mouse leave or click
- **Mobile**: Tap speaker card to toggle tooltip

---

### 2. Preference Match Highlights (During Drag)

**Trigger**: User starts dragging speaker card from pool

**Purpose**: Real-time visual feedback showing which session slots are good/poor matches

```
┌───────────────────────────────────────────────────────────────────────────┐
│  DRAGGING: Dr. Sarah Miller (prefers afternoon, avoid morning <09:00)    │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  Session Timeline Grid - June 10, 2025                                   │
│                                                                           │
│        Room A              Room B              Main Hall                 │
│  08:00 ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐      │
│        │ Workshop 1      │ │                 │ │                 │      │
│        │ (No timing)     │ │  [Empty Slot]   │ │  [Empty Slot]   │      │
│  09:30 │                 │ │                 │ │                 │      │
│        │ 🔴 Poor Match   │ │ 🔴 Poor Match   │ │ 🔴 Poor Match   │      │
│        │ 15% score       │ │ 20% score       │ │ 18% score       │      │
│        └─────────────────┘ └─────────────────┘ └─────────────────┘      │
│                                                                           │
│  11:00 ┌─────────────────┐ ┌─────────────────┐                          │
│        │ Panel Session   │ │  [Empty Slot]   │                          │
│  12:30 │ (Assigned)      │ │                 │                          │
│        │                 │ │ 🟡 Acceptable   │                          │
│        │                 │ │ 65% score       │                          │
│        └─────────────────┘ └─────────────────┘                          │
│                                                                           │
│  14:00 ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐      │
│        │  [Empty Slot]   │ │  [Empty Slot]   │ │  [Empty Slot]   │      │
│  15:30 │                 │ │                 │ │                 │      │
│        │ 🟢 Strong Match │ │ 🟢 Strong Match │ │ 🟢 Strong Match │      │
│        │ 85% score       │ │ 92% score       │ │ 88% score       │      │
│        └─────────────────┘ └─────────────────┘ └─────────────────┘      │
│                                                                           │
│  18:30 ┌─────────────────┐                                               │
│        │  [Empty Slot]   │                                               │
│  20:00 │                 │                                               │
│        │ 🔴 Poor Match   │                                               │
│        │ 12% score       │                                               │
│        └─────────────────┘                                               │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

**Color Coding**:
- **🟢 Green Border (Strong Match)**: 80-100% score
  - Slot background: Light green tint (#e8f5e9)
  - Border: 2px solid green (#4caf50)
  - Shadow: Green glow effect

- **🟡 Yellow Border (Acceptable Match)**: 50-79% score
  - Slot background: Light yellow tint (#fff9c4)
  - Border: 2px solid yellow (#ffc107)
  - Shadow: Yellow glow effect

- **🔴 Red Border (Poor Match)**: <50% score
  - Slot background: Light red tint (#ffebee)
  - Border: 2px solid red (#f44336)
  - Shadow: Red glow effect

**Match Score Display**:
- Position: Bottom of slot, centered
- Font: Small, bold, colored to match border
- Format: "85% score" or "Strong Match"

---

### 3. Slot Hover Tooltip (Match Details)

**Trigger**: While dragging speaker, mouse hovers over a specific session slot

**Purpose**: Show detailed breakdown of why slot is a good/poor match

```
┌───────────────────────────────────────────────────────────────────────────┐
│  Session Timeline Grid - June 10, 2025                                   │
│                                                                           │
│  14:00 ┌─────────────────┐ ┌─────────────────┐                          │
│        │  [Empty Slot]   │ │  [Empty Slot]   │ ← Mouse hover here       │
│  15:30 │                 │ │                 │                          │
│        │ 🟢 Strong Match │ │ 🟢 Strong Match │                          │
│        │ 85% score       │ │ 92% score       │                          │
│        └─────────────────┘ └─────────────────┘                          │
│                                   │                                       │
│        ┌──────────────────────────┴──────────────────────────────────┐   │
│        │ Match Details: Room B, 14:00-15:30                          │   │
│        │                                                              │   │
│        │ Overall Score: 92% 🟢 Strong Match                           │   │
│        │                                                              │   │
│        │ ✓ Time: Afternoon slot (preferred)           +30 points     │   │
│        │ ✓ Timing: No conflict with avoid times       +40 points     │   │
│        │ ✓ Room: Natural light available              +10 points     │   │
│        │ ✓ A/V: Projector & microphone ready          +12 points     │   │
│        │ ⚠️ Duration: 1.5h (speaker prefers 1h)        -5 points      │   │
│        │                                                              │   │
│        │ Recommendation: Excellent match - Assign here                │   │
│        │                                                              │   │
│        │ Drop to assign Dr. Sarah Miller to this slot →              │   │
│        └──────────────────────────────────────────────────────────────┘   │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

**Tooltip Features**:
- **Position**: Above or beside slot (auto-adjust to fit screen)
- **Scoring Breakdown**: Line items with +/- points
- **Checkmarks**: ✓ for matched criteria, ⚠️ for minor issues, ✗ for conflicts
- **Recommendation**: AI-generated suggestion ("Excellent match", "Acceptable", "Not recommended")
- **Call-to-Action**: "Drop to assign" instruction

---

### 4. Full Preferences Drawer

**Trigger**: User clicks [View Preferences] button on speaker card OR clicks speaker name

**Purpose**: Comprehensive view of all speaker preferences, used for detailed review before assignment

**Panel Type**: Material-UI Drawer sliding from right (400px width on desktop)

```
┌───────────────────────────────────────────────────────────────────────────┐
│  Session Timeline Grid                                     │  DRAWER  │  │
│                                                             │          │  │
│  ┌──────────────────────────────────────────────────────┐  │          │  │
│  │ Speaker cards and timeline grid...                   │  │          │  │
│  │                                                       │  │          │  │
│  └──────────────────────────────────────────────────────┘  │          │  │
│                                                             │          │  │
│                                                             │          │  │
└─────────────────────────────────────────────────────────────┴──────────┴──┘
                                                              │          │
                                                              │  Drawer  │
                                                              │  slides  │
                                                              │  from    │
                                                              │  right   │
                                                              │          │
                                                              └──────────┘
```

**Full Drawer Layout**:

```
┌─────────────────────────────────────────────────────────┐
│ Dr. Sarah Miller - Preferences                     [X] │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ ┌─── Time Preferences ────────────────────────────────┐ │
│ │                                                      │ │
│ │ Time of Day:                                        │ │
│ │                                                      │ │
│ │ ☀️ Afternoon (12:00 - 18:00)        ✓ Preferred     │ │
│ │    Best availability, highest energy levels         │ │
│ │                                                      │ │
│ │ ⚪ Morning (6:00 - 12:00)            ⚪ Neutral      │ │
│ │    Available after 09:00                            │ │
│ │                                                      │ │
│ │ ⊘ Evening (18:00 - 22:00)          ✗ Avoid         │ │
│ │    Family commitments, low energy                   │ │
│ │                                                      │ │
│ │ ─────────────────────────────────────────────────   │ │
│ │                                                      │ │
│ │ Specific Avoid Times:                               │ │
│ │                                                      │ │
│ │ 🔴 June 10, 08:00 - 09:00                           │ │
│ │    Reason: Morning commute from Zurich              │ │
│ │                                                      │ │
│ │ 🔴 June 11, 17:00 - 19:00                           │ │
│ │    Reason: Prior commitment (family event)          │ │
│ │                                                      │ │
│ │ 🔴 June 12, All day                                 │ │
│ │    Reason: Unavailable (traveling)                  │ │
│ │                                                      │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─── A/V Requirements ────────────────────────────────┐ │
│ │                                                      │ │
│ │ Audio/Visual Setup:                                 │ │
│ │                                                      │ │
│ │ ✓ Microphone            Required                    │ │
│ │   Needs wireless lavalier or headset mic            │ │
│ │                                                      │ │
│ │ ✓ Projector/Screen      Required                    │ │
│ │   HDMI connection, 1080p minimum                    │ │
│ │                                                      │ │
│ │ ✓ Clicker/Remote        Required                    │ │
│ │   For presentation slides                           │ │
│ │                                                      │ │
│ │ - Whiteboard            Not needed                  │ │
│ │                                                      │ │
│ │ ✓ Recording Permission  Approved                    │ │
│ │   Video recording allowed, requires attribution     │ │
│ │                                                      │ │
│ │ ─────────────────────────────────────────────────   │ │
│ │                                                      │ │
│ │ Technical Notes:                                    │ │
│ │ "Bringing MacBook Pro (2023) with USB-C ports.     │ │
│ │  Will need HDMI adapter. Prefer confidence         │ │
│ │  monitor for presenter notes."                      │ │
│ │                                                      │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─── Room Setup ──────────────────────────────────────┐ │
│ │                                                      │ │
│ │ Room Preferences:                                   │ │
│ │                                                      │ │
│ │ ✓ Standing Desk         Preferred                   │ │
│ │   Better for energy, audience engagement            │ │
│ │                                                      │ │
│ │ ✓ Natural Light         Preferred                   │ │
│ │   Helps with mood and presentation quality          │ │
│ │                                                      │ │
│ │ - Flip Chart            Not needed                  │ │
│ │                                                      │ │
│ │ ✓ Quiet Room            Preferred                   │ │
│ │   Minimal background noise for recording            │ │
│ │                                                      │ │
│ │ ─────────────────────────────────────────────────   │ │
│ │                                                      │ │
│ │ Seating Setup:                                      │ │
│ │ "Prefer interactive workshop setup with audience   │ │
│ │  seating in semi-circle or U-shape. Classroom-     │ │
│ │  style seating also acceptable. Avoid theater      │ │
│ │  seating if possible as it limits interaction."    │ │
│ │                                                      │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─── Session Preferences ─────────────────────────────┐ │
│ │                                                      │ │
│ │ Preferred Session Duration:  60-90 minutes          │ │
│ │   (Can extend to 120 min if needed)                 │ │
│ │                                                      │ │
│ │ Maximum Sessions per Day:    2 sessions             │ │
│ │   (Need breaks to maintain energy)                  │ │
│ │                                                      │ │
│ │ Presentation Style:          Interactive workshop   │ │
│ │   (Q&A, live demos, hands-on exercises)             │ │
│ │                                                      │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─── Match Score (Dynamic) ───────────────────────────┐ │
│ │                                                      │ │
│ │ 📊 Current Slot Match Analysis:                     │ │
│ │                                                      │ │
│ │ [No slot selected]                                  │ │
│ │                                                      │ │
│ │ Hover over a session slot or drag this speaker     │ │
│ │ to see preference match score.                      │ │
│ │                                                      │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌────────────────────────────────────────────────────┐  │
│ │ [Assign to Best Match]    [Close]                  │  │
│ └────────────────────────────────────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**Drawer Sections**:

1. **Time Preferences**:
   - Icons: ☀️ (preferred), ⚪ (neutral), ⊘ (avoid)
   - Color coding: Green for preferred, gray for neutral, red for avoid
   - Specific avoid times listed with date ranges and reasons
   - Visual emphasis on hard constraints (specific avoid times)

2. **A/V Requirements**:
   - Checklist format with status indicators
   - ✓ = Required, - = Not needed
   - Recording permission status (approved/denied/pending)
   - Technical notes field for additional context

3. **Room Setup**:
   - Preference items with preferred/neutral/not needed status
   - Free-form notes for seating arrangement preferences
   - Helps organizers match room capabilities to needs

4. **Session Preferences**:
   - Duration preferences (impacts session slot sizing)
   - Max sessions per day (prevents over-scheduling speaker)
   - Presentation style (informational)

5. **Match Score (Dynamic)**:
   - Updates in real-time when user hovers over slots
   - Shows which slot is currently being evaluated
   - Displays score breakdown

**Dynamic Match Score Section (When Hovering Over Slot)**:

```
│ ┌─── Match Score (Dynamic) ───────────────────────────┐ │
│ │                                                      │ │
│ │ 📊 Evaluating: Room B, June 10, 14:00-15:30         │ │
│ │                                                      │ │
│ │ Overall Match: 92% 🟢 Strong Match                   │ │
│ │                                                      │ │
│ │ ✓ Time of Day:           Afternoon (preferred)      │ │
│ │   Score: +30 points                                 │ │
│ │                                                      │ │
│ │ ✓ Avoid Times:           No conflicts               │ │
│ │   Score: +40 points                                 │ │
│ │                                                      │ │
│ │ ✓ Room Features:         Natural light available    │ │
│ │   Score: +10 points                                 │ │
│ │                                                      │ │
│ │ ✓ A/V Setup:             Projector, mic available   │ │
│ │   Score: +12 points                                 │ │
│ │                                                      │ │
│ │ ⚠️ Duration:              1.5h (prefer 1-1.5h)       │ │
│ │   Score: 0 points (neutral)                         │ │
│ │                                                      │ │
│ │ Total: 92/100                                       │ │
│ │                                                      │ │
│ │ Recommendation: Excellent match! This slot aligns   │ │
│ │ with all major preferences. Assign with confidence. │ │
│ │                                                      │ │
│ │ [Assign to This Slot →]                             │ │
│ │                                                      │ │
│ └──────────────────────────────────────────────────────┘ │
```

**[Assign to Best Match] Button**:
- When clicked: Automatically suggests slot with highest match score
- Opens confirmation modal showing suggested slot
- User can accept or decline suggestion

---

## Interaction Flow

### Flow 1: Quick Preview (Tooltip)

```
1. User hovers over speaker card in pool
   ↓
2. Tooltip appears after 500ms delay
   - Shows: Time preference icons, A/V summary, room needs
   ↓
3. User moves mouse away OR clicks [View Preferences]
   ↓
4. Tooltip disappears (if mouse away) OR Drawer opens (if clicked)
```

**Use Case**: Organizer wants quick check before dragging speaker

---

### Flow 2: Drag-and-Drop with Match Highlighting

```
1. User clicks and holds on speaker card
   ↓
2. Drag gesture begins
   ↓
3. Session grid updates:
   - All empty slots show match score overlays (green/yellow/red)
   - Occupied slots remain grayed out
   ↓
4. User hovers over specific slot while dragging
   ↓
5. Detailed match tooltip appears above slot
   - Shows: Breakdown of score, recommendation
   ↓
6. User drops speaker onto slot
   ↓
7. Match score displayed in assignment confirmation
   OR
   Conflict modal appears (if poor match <50% or hard conflict)
```

**Use Case**: Organizer is actively assigning timing and wants visual guidance

---

### Flow 3: Full Preferences Review

```
1. User clicks speaker card or [View Preferences] button
   ↓
2. Drawer slides in from right (400ms animation)
   ↓
3. Drawer displays all preference sections (scrollable)
   ↓
4. User reviews preferences
   ↓
5. User hovers over session slot in background
   ↓
6. Match Score section in drawer updates dynamically
   - Shows score for hovered slot
   ↓
7. User clicks [Assign to This Slot →] in drawer
   OR
   User clicks [Assign to Best Match]
   ↓
8. Assignment confirmation appears
   ↓
9. Drawer closes automatically
```

**Use Case**: Organizer wants detailed understanding of speaker needs before assignment

---

## Match Scoring Algorithm

### Scoring Breakdown

```typescript
interface MatchScore {
  total: number;          // 0-100
  severity: 'strong' | 'acceptable' | 'poor';
  breakdown: {
    timeOfDay: number;    // 0-30 points
    avoidTimes: number;   // 0-40 points
    roomFeatures: number; // 0-15 points
    avSetup: number;      // 0-15 points
  };
  recommendation: string;
}

function calculateMatchScore(
  slot: SessionSlot,
  preferences: SpeakerPreferences
): MatchScore {
  let timeOfDay = 0;
  let avoidTimes = 0;
  let roomFeatures = 0;
  let avSetup = 0;

  // 1. Time of Day (0-30 points)
  const slotTimeOfDay = getTimeOfDay(slot.startTime);  // 'morning' | 'afternoon' | 'evening'
  if (preferences.timeOfDay[slotTimeOfDay] === 'preferred') {
    timeOfDay = 30;
  } else if (preferences.timeOfDay[slotTimeOfDay] === 'neutral') {
    timeOfDay = 15;
  } else {
    timeOfDay = 0;  // 'avoid'
  }

  // 2. Avoid Times (0-40 points)
  const conflictsAvoidTime = preferences.specificAvoidTimes.some(avoid =>
    overlaps(slot.startTime, slot.endTime, avoid.start, avoid.end)
  );
  if (!conflictsAvoidTime) {
    avoidTimes = 40;  // No conflicts
  } else {
    avoidTimes = 0;   // Hard conflict with specific avoid time
  }

  // 3. Room Features (0-15 points)
  let roomScore = 0;
  if (preferences.roomSetup.naturalLight === 'preferred' && slot.room.hasNaturalLight) {
    roomScore += 5;
  }
  if (preferences.roomSetup.quietRoom === 'preferred' && slot.room.isQuiet) {
    roomScore += 5;
  }
  if (preferences.roomSetup.standingDesk === 'preferred' && slot.room.hasStandingDesk) {
    roomScore += 5;
  }
  roomFeatures = Math.min(roomScore, 15);  // Cap at 15

  // 4. A/V Setup (0-15 points)
  let avScore = 0;
  if (preferences.avRequirements.microphone === 'required' && slot.room.hasMicrophone) {
    avScore += 5;
  } else if (preferences.avRequirements.microphone === 'required' && !slot.room.hasMicrophone) {
    avScore -= 10;  // Penalty for missing required equipment
  }

  if (preferences.avRequirements.projector === 'required' && slot.room.hasProjector) {
    avScore += 5;
  } else if (preferences.avRequirements.projector === 'required' && !slot.room.hasProjector) {
    avScore -= 10;
  }

  if (preferences.avRequirements.clickerRemote === 'required' && slot.room.hasClicker) {
    avScore += 5;
  }
  avSetup = Math.max(0, Math.min(avScore, 15));  // Cap at 15, floor at 0

  // Total score
  const total = timeOfDay + avoidTimes + roomFeatures + avSetup;

  // Severity
  let severity: 'strong' | 'acceptable' | 'poor';
  if (total >= 80) severity = 'strong';
  else if (total >= 50) severity = 'acceptable';
  else severity = 'poor';

  // Recommendation
  let recommendation: string;
  if (total >= 90) {
    recommendation = 'Excellent match! This slot aligns with all major preferences. Assign with confidence.';
  } else if (total >= 80) {
    recommendation = 'Strong match. Most preferences are met. Good choice for assignment.';
  } else if (total >= 65) {
    recommendation = 'Acceptable match. Some preferences not met, but workable.';
  } else if (total >= 50) {
    recommendation = 'Below average match. Consider alternative slots if available.';
  } else {
    recommendation = 'Poor match. Assignment not recommended without speaker confirmation.';
  }

  return {
    total,
    severity,
    breakdown: { timeOfDay, avoidTimes, roomFeatures, avSetup },
    recommendation
  };
}
```

### Scoring Criteria Weights

| Criterion          | Max Points | Rationale                                      |
|--------------------|------------|------------------------------------------------|
| Avoid Times        | 40         | Hard constraint - highest weight               |
| Time of Day        | 30         | Strong preference - second highest             |
| Room Features      | 15         | Nice-to-have - moderate weight                 |
| A/V Setup          | 15         | Required equipment - moderate weight           |
| **Total**          | **100**    |                                                |

**Threshold Levels**:
- **Strong Match (80-100)**: 🟢 Green - Highly recommended
- **Acceptable Match (50-79)**: 🟡 Yellow - Workable, some compromises
- **Poor Match (<50)**: 🔴 Red - Not recommended, triggers warning

---

## Visual Design

### Color Palette

**Match Score Colors**:
- Strong Match (80-100): `#4caf50` (Material Green 500)
- Acceptable Match (50-79): `#ffc107` (Material Amber 500)
- Poor Match (<50): `#f44336` (Material Red 500)

**Preference Icons**:
- Preferred: ☀️ (sun emoji) + `#4caf50` background
- Neutral: ⚪ (white circle) + `#9e9e9e` background
- Avoid: ⊘ (prohibited sign) + `#f44336` background

**Drawer Background**:
- Header: `#f5f5f5` (Material Grey 100)
- Body: `#ffffff` (White)
- Section dividers: `#e0e0e0` (Material Grey 300)

---

## Accessibility

### Screen Reader Support

**Drawer Opening Announcement**:
```
"Speaker preferences drawer opened for Dr. Sarah Miller.
Showing time preferences, A/V requirements, and room setup.
Press Escape to close."
```

**Match Score Announcement (During Drag)**:
```
"Dragging Dr. Sarah Miller. Hovering over Room B, 14:00-15:30.
Match score: 92 percent, strong match.
Drop to assign to this slot."
```

**ARIA Attributes**:
```html
<!-- Drawer -->
<div role="complementary" aria-labelledby="drawer-title">
  <h2 id="drawer-title">Dr. Sarah Miller - Preferences</h2>

  <!-- Time preferences section -->
  <section aria-labelledby="time-prefs-heading">
    <h3 id="time-prefs-heading">Time Preferences</h3>

    <div role="list" aria-label="Time of day preferences">
      <div role="listitem">
        <span aria-label="Afternoon preferred">
          ☀️ Afternoon (12:00 - 18:00) ✓ Preferred
        </span>
      </div>
    </div>
  </section>

  <!-- Match score section -->
  <section aria-labelledby="match-score-heading" aria-live="polite">
    <h3 id="match-score-heading">Match Score</h3>
    <div role="status">
      Overall Match: 92% Strong Match
    </div>
  </section>
</div>

<!-- Session slot with match indicator -->
<div
  role="button"
  tabindex="0"
  aria-label="Session slot: Room B, 14:00 to 15:30. Match score 92 percent, strong match. Press Enter to assign."
  class="session-slot match-strong"
>
  <!-- Slot content -->
</div>
```

### Keyboard Navigation

**Drawer Controls**:
- `Escape`: Close drawer
- `Tab`: Navigate between preference sections
- `Shift+Tab`: Navigate backwards
- `Enter`: Activate [Assign to Best Match] or [Assign to This Slot] buttons

**During Drag (Keyboard Alternative)**:
- `Arrow Keys`: Navigate between session slots
- `Space`: Select slot to view match score
- `Enter`: Assign speaker to selected slot
- `Escape`: Cancel drag operation

---

## Responsive Design

### Desktop (≥1200px)

- **Drawer Width**: 400px fixed
- **Tooltip Width**: 320px max
- **Match Score Overlay**: Appears at bottom of slot (full text visible)

### Tablet (768px - 1199px)

- **Drawer Width**: 350px fixed
- **Tooltip Width**: 280px max
- **Match Score Overlay**: Abbreviated (e.g., "85%" only, hide "Strong Match" label)

### Mobile (<768px)

- **Drawer**: Full-screen modal (not side drawer)
- **Tooltip**: Bottom sheet (anchored to bottom of screen)
- **Match Score**: Badge icon only during drag (number visible on tap)
- **Drawer Sections**: Collapsible accordions to save vertical space

**Mobile Drawer Header**:
```
┌─────────────────────────────────────────┐
│ ← Dr. Sarah Miller - Preferences        │
├─────────────────────────────────────────┤
│ ▼ Time Preferences                      │
│ ▶ A/V Requirements                      │
│ ▶ Room Setup                            │
│ ▶ Session Preferences                   │
│ ▶ Match Score                           │
│                                         │
│ [Assign to Best Match]    [Close]      │
└─────────────────────────────────────────┘
```

---

## Edge Cases

### Missing Preferences Data

**Scenario**: Speaker has not filled out preferences form

**Display**:
```
┌─────────────────────────────────────────────────────────┐
│ Dr. John Chen - Preferences                        [X] │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ ⚠️ No preferences on file                               │
│                                                         │
│ This speaker has not provided time preferences,        │
│ A/V requirements, or room setup details.               │
│                                                         │
│ All session slots will show neutral match (50% score). │
│                                                         │
│ Actions:                                               │
│ • Assign to any available slot                         │
│ • Contact speaker for preference collection            │
│                                                         │
│ [Send Preference Request Email]    [Close]             │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**Match Score Behavior**:
- All slots show 🟡 Yellow (Acceptable - 50% default score)
- Tooltip message: "No preferences on file. Neutral match."

---

### Conflicting Preferences

**Scenario**: Speaker marked all time slots as "Avoid" (invalid state)

**Validation on Preference Save**:
```
⚠️ Invalid Preference Configuration

You have marked all time slots (Morning, Afternoon, Evening) as "Avoid."
Please mark at least one time slot as "Preferred" or "Neutral."

[Edit Preferences]
```

**Fallback if Invalid Data in Database**:
- System overrides "Avoid" on all slots to "Neutral"
- Admin notification sent to fix data inconsistency

---

### Zero Match Slots

**Scenario**: All available slots conflict with speaker's avoid times

**Display in Drawer**:
```
│ ┌─── Match Score (Dynamic) ───────────────────────────┐ │
│ │                                                      │ │
│ │ ⚠️ No Available Slots Match Preferences              │ │
│ │                                                      │ │
│ │ All current empty slots conflict with this          │ │
│ │ speaker's avoid times or preferences.               │ │
│ │                                                      │ │
│ │ Recommendations:                                    │ │
│ │ 1. Contact speaker to check flexibility            │ │
│ │ 2. Create new time slot outside current schedule   │ │
│ │ 3. Reassign a different speaker to this topic      │ │
│ │                                                      │ │
│ │ [Contact Speaker]    [View All Slots Anyway]        │ │
│ │                                                      │ │
│ └──────────────────────────────────────────────────────┘ │
```

**Grid Behavior**:
- All slots show 🔴 Red (Poor Match <30%)
- Tooltip includes: "Conflicts with avoid times. Contact speaker before assigning."

---

## Implementation Notes

### Data Fetching

**API Endpoints**:
```typescript
// Fetch speaker preferences
GET /api/v1/speakers/{speakerId}/preferences
Response: SpeakerPreferences

// Calculate match score for slot
POST /api/v1/speakers/{speakerId}/match-score
Body: { sessionSlot: { startTime, endTime, room } }
Response: { score: number, breakdown: {...}, recommendation: string }
```

**React Query Hook**:
```typescript
const useSpeakerPreferences = (speakerId: string) => {
  return useQuery({
    queryKey: ['speaker-preferences', speakerId],
    queryFn: () => fetchSpeakerPreferences(speakerId),
    staleTime: 5 * 60 * 1000,  // 5 minutes
  });
};

const useMatchScore = (speakerId: string, slot: SessionSlot) => {
  return useQuery({
    queryKey: ['match-score', speakerId, slot.id],
    queryFn: () => calculateMatchScore(speakerId, slot),
    enabled: !!slot,  // Only fetch when slot is hovered
  });
};
```

### Real-Time Updates

**WebSocket Event** (when preferences updated by speaker):
```json
{
  "type": "SPEAKER_PREFERENCES_UPDATED",
  "data": {
    "speakerId": "uuid",
    "updatedAt": "2025-06-10T14:30:00Z"
  }
}
```

**React Update Handler**:
```typescript
useEffect(() => {
  const handlePreferenceUpdate = (event: WebSocketEvent) => {
    if (event.type === 'SPEAKER_PREFERENCES_UPDATED') {
      queryClient.invalidateQueries(['speaker-preferences', event.data.speakerId]);
      queryClient.invalidateQueries(['match-score', event.data.speakerId]);
    }
  };

  websocket.on('message', handlePreferenceUpdate);
  return () => websocket.off('message', handlePreferenceUpdate);
}, []);
```

---

## Testing Checklist

### Tooltip Display

- [ ] Tooltip appears after 500ms hover on speaker card
- [ ] Tooltip shows time preferences summary
- [ ] Tooltip shows A/V requirements summary
- [ ] Tooltip disappears on mouse leave
- [ ] Tooltip has correct positioning (doesn't overflow screen)

### Match Highlighting During Drag

- [ ] Dragging speaker applies match score overlays to all empty slots
- [ ] Green border (80-100%) appears for strong matches
- [ ] Yellow border (50-79%) appears for acceptable matches
- [ ] Red border (<50%) appears for poor matches
- [ ] Match score percentage displayed at bottom of each slot
- [ ] Occupied slots remain grayed out (no match overlay)

### Slot Hover Tooltip

- [ ] Detailed match tooltip appears when hovering over slot during drag
- [ ] Tooltip shows score breakdown with +/- points
- [ ] Tooltip includes recommendation text
- [ ] Tooltip position adjusts to fit screen
- [ ] Tooltip includes "Drop to assign" instruction

### Full Preferences Drawer

- [ ] Drawer slides in from right (400ms animation)
- [ ] Close button [X] works
- [ ] All preference sections render correctly
- [ ] Time preferences show icons (☀️, ⚪, ⊘)
- [ ] Specific avoid times listed with date ranges
- [ ] A/V requirements show required/optional status
- [ ] Room setup preferences displayed
- [ ] Match score section updates when hovering over slots
- [ ] [Assign to Best Match] button suggests highest-scoring slot
- [ ] [Assign to This Slot] button works in match score section
- [ ] Drawer closes after successful assignment

### Match Scoring Algorithm

- [ ] Time of day score correct (30 points for preferred)
- [ ] Avoid times score correct (40 points if no conflict, 0 if conflict)
- [ ] Room features score correct (up to 15 points)
- [ ] A/V setup score correct (up to 15 points)
- [ ] Total score calculated correctly (0-100)
- [ ] Severity classification correct (strong/acceptable/poor)
- [ ] Recommendation text appropriate for score

### Edge Cases

- [ ] Missing preferences shows warning message
- [ ] All slots show neutral 50% when no preferences
- [ ] Zero match slots displays recommendation banner
- [ ] Conflicting preferences validated on save
- [ ] Real-time preference updates trigger re-calculation

### Accessibility

- [ ] Drawer has correct ARIA attributes
- [ ] Screen reader announces drawer opening
- [ ] Match score updates announced (aria-live)
- [ ] Keyboard navigation works (Escape to close, Tab between sections)
- [ ] Focus management correct (returns to trigger button after close)

### Responsive Design

- [ ] Desktop: 400px drawer width
- [ ] Tablet: 350px drawer width
- [ ] Mobile: Full-screen modal (not side drawer)
- [ ] Mobile: Accordion sections collapsible
- [ ] Tooltip positioning works on all screen sizes

---

## Related Wireframes

- **story-5.7-slot-assignment-page.md**: Main slot assignment interface where preferences are used
- **story-5.7-conflict-resolution-modal.md**: Conflict modal triggered when match score <50%
- **story-5.7-navigation-integration.md**: Navigation flows to/from slot assignment page

---

## Change Log

| Date       | Version | Description                                | Author     |
|------------|---------|--------------------------------------------|-----------
| 2025-12-25 | 1.0     | Initial speaker preferences panel wireframe | Sally (UX Expert) |
