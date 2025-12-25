# Story 5.7: Conflict Resolution Modal - Slot Assignment

**Story**: Epic 5, Story 5.7 - Slot Assignment & Progressive Publishing
**Component**: Conflict Detection & Resolution Modal
**User Role**: Organizer
**Related FR**: FR17 (Speaker Matching), AC9 (Conflict Warnings)

---

## Purpose

This wireframe documents the modal dialog that appears when an organizer attempts to assign a session timing that creates a conflict. The system detects three types of conflicts and provides resolution workflows.

**Context**: Triggered during drag-drop timing assignment on the dedicated slot assignment page.

---

## Conflict Types

### 1. Room Overlap (ERROR - Must Resolve)

**Trigger**: Assigning a session to a room that is already occupied during the proposed time slot.

**Severity**: 🔴 Error - Assignment blocked, must be resolved

**Example**: Trying to assign "Workshop 2" to Room A from 14:00-15:30 when "Panel Discussion" already occupies Room A from 14:00-16:00.

---

### 2. Speaker Double-Booked (ERROR - Must Resolve)

**Trigger**: Assigning a session to a speaker who is already assigned to another session with overlapping timing.

**Severity**: 🔴 Error - Assignment blocked, must be resolved

**Example**: Trying to assign Dr. Sarah Miller to "Workshop 2" at 14:00-15:30 when she's already assigned to "Keynote" at 13:00-14:30.

---

### 3. Speaker Preference Conflict (WARNING - Can Override)

**Trigger**: Assigning a session timing that conflicts with the speaker's stated time preferences or avoid times.

**Severity**: ⚠️ Warning - Assignment allowed with explicit override

**Example**: Assigning a speaker who prefers afternoon sessions to an early morning 08:00-09:30 time slot.

---

## Modal Structure

### Header (All Conflict Types)

```
┌─────────────────────────────────────────────────────────────────────────┐
│ [Icon] Timing Conflict Detected                                    [X] │
└─────────────────────────────────────────────────────────────────────────┘
```

**Icons by Severity**:
- 🔴 Error: Red circle with exclamation mark
- ⚠️ Warning: Yellow triangle with exclamation mark

---

## Scenario 1: Room Overlap Conflict

**Trigger**: User drags "Workshop 2" (Dr. Sarah Miller) to Room A, 14:00-15:30
**Conflict**: "Panel Discussion" already occupies Room A from 14:00-16:00

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 🔴 Timing Conflict Detected                                        [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Conflict Type: Room Overlap                                            │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ You are trying to assign:                                        │  │
│  │                                                                  │  │
│  │   Session:   "Workshop 2: Advanced Cloud Architecture"          │  │
│  │   Speaker:   Dr. Sarah Miller                                   │  │
│  │   Time:      14:00 - 15:30, June 10, 2025                       │  │
│  │   Room:      Conference Room A                                  │  │
│  │   Duration:  1.5 hours                                          │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ But this room is already occupied:                              │  │
│  │                                                                  │  │
│  │   Session:   "Panel Discussion: Industry Trends"                │  │
│  │   Speakers:  John Smith, Jane Doe, Robert Chen (3 panelists)   │  │
│  │   Time:      14:00 - 16:00, June 10, 2025                       │  │
│  │   Room:      Conference Room A                                  │  │
│  │   Duration:  2 hours                                            │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  Overlap Duration: 1.5 hours (14:00 - 15:30)                           │
│                                                                         │
│  ┌─── Visual Timeline ──────────────────────────────────────────────┐  │
│  │                                                                  │  │
│  │  Conference Room A - June 10, 2025                              │  │
│  │                                                                  │  │
│  │  13:00    14:00    15:00    16:00    17:00                      │  │
│  │  ──────────┬────────┬────────┬────────┬────────                 │  │
│  │            │        │        │        │                         │  │
│  │            ├────────┴────────┴────────┤                         │  │
│  │            │  Panel Discussion        │  ← Existing session     │  │
│  │            │  (14:00 - 16:00)        │                         │  │
│  │            └─────────────────────────┘                         │  │
│  │                                                                  │  │
│  │            ├────────┴────────┤                                  │  │
│  │            │  Workshop 2     │  ← Attempted assignment          │  │
│  │            │  (14:00 - 15:30)│                                  │  │
│  │            └─────────────────┘                                  │  │
│  │                                                                  │  │
│  │            └─────────────────┘                                  │  │
│  │            OVERLAP: 1.5 hours (shown in red)                    │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  How would you like to resolve this conflict?                          │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [1] Find Alternative Time Slot                                  │  │
│  │     View available slots in Room A or other rooms that match    │  │
│  │     Dr. Sarah Miller's preferences                              │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [2] Assign to Different Room                                    │  │
│  │     Keep timing (14:00-15:30) but assign to a different room    │  │
│  │     Available rooms: Room B, Main Hall                          │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [3] Reassign Panel Discussion                                   │  │
│  │     Move "Panel Discussion" to a different time or room         │  │
│  │     Warning: This affects 3 speakers                            │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [Cancel]                                                         │  │
│  │     Abort this assignment and return to slot assignment view    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Resolution Flow for Option 1 (Find Alternative Slot)**:

User clicks [1] Find Alternative Time Slot → Modal transitions to suggestion view:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 🔍 Alternative Time Slots for Workshop 2                          [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Showing available slots that match Dr. Sarah Miller's preferences:    │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ 🟢 Strong Match (85%)                                            │  │
│  │                                                                  │  │
│  │ June 10, 2025 | 11:00 - 12:30 | Conference Room B              │  │
│  │                                                                  │  │
│  │ ✓ Afternoon slot (preferred)                                    │  │
│  │ ✓ Room B has natural light (preferred)                          │  │
│  │ ✓ Projector and microphone available                            │  │
│  │ ✓ No conflicts with other sessions                              │  │
│  │                                                                  │  │
│  │ [Assign to This Slot]                                           │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ 🟢 Strong Match (82%)                                            │  │
│  │                                                                  │  │
│  │ June 10, 2025 | 16:00 - 17:30 | Conference Room A              │  │
│  │                                                                  │  │
│  │ ✓ Afternoon slot (preferred)                                    │  │
│  │ ✓ Same room as originally intended                              │  │
│  │ ✓ All A/V requirements met                                      │  │
│  │ ⚠️ Slightly later than typical (4pm start)                       │  │
│  │                                                                  │  │
│  │ [Assign to This Slot]                                           │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ 🟡 Acceptable Match (65%)                                        │  │
│  │                                                                  │  │
│  │ June 11, 2025 | 09:00 - 10:30 | Main Hall                      │  │
│  │                                                                  │  │
│  │ ✓ Large room with excellent A/V setup                           │  │
│  │ ⚠️ Morning slot (speaker prefers afternoon)                      │  │
│  │ ⚠️ Different day than originally planned                         │  │
│  │                                                                  │  │
│  │ [Assign to This Slot]                                           │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  Showing 3 of 8 available slots. [View All Slots]                      │
│                                                                         │
│  [← Back to Conflict Details]        [Cancel]                          │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

User clicks [Assign to This Slot] → Modal shows confirmation:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ✓ Conflict Resolved                                                [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Success! Workshop 2 has been assigned.                                │
│                                                                         │
│  Session:   "Workshop 2: Advanced Cloud Architecture"                  │
│  Speaker:   Dr. Sarah Miller                                           │
│  Time:      11:00 - 12:30, June 10, 2025                               │
│  Room:      Conference Room B                                          │
│  Match:     85% (Strong match with speaker preferences)                │
│                                                                         │
│  [Return to Slot Assignment]                                           │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Resolution Flow for Option 2 (Assign to Different Room)**:

User clicks [2] Assign to Different Room → Modal transitions to room selection:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 🏢 Select Alternative Room                                        [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Keep timing: 14:00 - 15:30, June 10, 2025                             │
│  Speaker: Dr. Sarah Miller                                             │
│                                                                         │
│  Available rooms during this time:                                     │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ ● Conference Room B                                             │  │
│  │                                                                  │  │
│  │   Capacity:  40 people                                          │  │
│  │   A/V Setup: ✓ Projector, ✓ Microphone, ✓ Clicker              │  │
│  │   Features:  Natural light, Standing desk available             │  │
│  │   Status:    Available (no conflicts)                           │  │
│  │                                                                  │  │
│  │   Match Score: 90% 🟢 (Excellent match for speaker setup)       │  │
│  │                                                                  │  │
│  │   [Assign to Room B]                                            │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ ○ Main Hall                                                     │  │
│  │                                                                  │  │
│  │   Capacity:  200 people                                         │  │
│  │   A/V Setup: ✓ Projector, ✓ Microphone, ✓ Recording            │  │
│  │   Features:  Stage, Theater seating                             │  │
│  │   Status:    Available (no conflicts)                           │  │
│  │                                                                  │  │
│  │   Match Score: 60% 🟡 (Large room, may be oversized)            │  │
│  │                                                                  │  │
│  │   [Assign to Main Hall]                                         │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  [← Back to Conflict Details]        [Cancel]                          │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Resolution Flow for Option 3 (Reassign Panel Discussion)**:

User clicks [3] Reassign Panel Discussion → Modal shows warning:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ⚠️ Reassign Existing Session?                                      [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  You are about to move an existing session to resolve this conflict.   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Session to Reassign:                                            │  │
│  │                                                                  │  │
│  │   "Panel Discussion: Industry Trends"                           │  │
│  │   Current: 14:00 - 16:00, Room A                                │  │
│  │   Speakers: John Smith, Jane Doe, Robert Chen (3 panelists)    │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ⚠️ Warning: This will affect multiple speakers                        │
│  You will need to assign a new timing for the panel discussion that    │
│  works for all 3 panelists.                                            │
│                                                                         │
│  Alternative suggested timings for Panel Discussion:                   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ June 10, 2025 | 16:30 - 18:30 | Conference Room A              │  │
│  │ Match: 75% (all speakers available, Room A still free)          │  │
│  │ [Move Panel to This Slot]                                       │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ June 11, 2025 | 10:00 - 12:00 | Main Hall                      │  │
│  │ Match: 65% (different day, all speakers available)              │  │
│  │ [Move Panel to This Slot]                                       │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  [View More Alternatives]                                              │
│                                                                         │
│  [← Back to Conflict Details]        [Cancel]                          │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Scenario 2: Speaker Double-Booked Conflict

**Trigger**: User drags "Workshop 2" to 14:00-15:30 time slot, assigning Dr. Sarah Miller
**Conflict**: Dr. Sarah Miller already assigned to "Keynote" at 13:00-14:30

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 🔴 Timing Conflict Detected                                        [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Conflict Type: Speaker Double-Booked                                  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ You are trying to assign Dr. Sarah Miller to:                   │  │
│  │                                                                  │  │
│  │   Session:   "Workshop 2: Advanced Cloud Architecture"          │  │
│  │   Time:      14:00 - 15:30, June 10, 2025                       │  │
│  │   Room:      Conference Room B                                  │  │
│  │   Duration:  1.5 hours                                          │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ But Dr. Sarah Miller is already assigned to:                    │  │
│  │                                                                  │  │
│  │   Session:   "Keynote: Future of Cloud Architecture"            │  │
│  │   Time:      13:00 - 14:30, June 10, 2025                       │  │
│  │   Room:      Main Hall                                          │  │
│  │   Duration:  1.5 hours                                          │  │
│  │   Status:    Already confirmed and published                    │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  Overlap Duration: 30 minutes (14:00 - 14:30)                          │
│                                                                         │
│  ┌─── Visual Timeline ──────────────────────────────────────────────┐  │
│  │                                                                  │  │
│  │  Dr. Sarah Miller's Schedule - June 10, 2025                    │  │
│  │                                                                  │  │
│  │  12:00    13:00    14:00    15:00    16:00                      │  │
│  │  ──────────┬────────┬────────┬────────┬────────                 │  │
│  │            │        │        │        │                         │  │
│  │            ├────────┴────┤                                      │  │
│  │            │  Keynote    │  ← Existing assignment               │  │
│  │            │  13:00-14:30│                                      │  │
│  │            └─────────────┘                                      │  │
│  │                                                                  │  │
│  │                     ├────────┴────┤                             │  │
│  │                     │  Workshop 2 │  ← Attempted assignment     │  │
│  │                     │  14:00-15:30│                             │  │
│  │                     └─────────────┘                             │  │
│  │                                                                  │  │
│  │                     └──┘                                        │  │
│  │                     OVERLAP: 30 min (shown in red)              │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ⚠️ Note: Keynote is already published (Phase 2). Moving it would      │
│     require unpublishing and notifying subscribers.                    │
│                                                                         │
│  How would you like to resolve this conflict?                          │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [1] Find Alternative Time Slot                                  │  │
│  │     View available slots for Workshop 2 that don't conflict     │  │
│  │     with Dr. Sarah Miller's schedule                            │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [2] Assign Different Speaker                                    │  │
│  │     Keep Workshop 2 timing (14:00-15:30) but assign a           │  │
│  │     different speaker from the available pool                   │  │
│  │     Available speakers: Prof. John Chen, Dr. Maria Lopez        │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [3] Reschedule Keynote (Not Recommended)                        │  │
│  │     Move Keynote to a different time                            │  │
│  │     ⚠️ Requires unpublishing Phase 2 and notifying subscribers   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [Cancel]                                                         │  │
│  │     Abort this assignment and return to slot assignment view    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Resolution Flow for Option 2 (Assign Different Speaker)**:

User clicks [2] Assign Different Speaker → Modal transitions to speaker selection:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 👤 Select Alternative Speaker                                     [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Session: "Workshop 2: Advanced Cloud Architecture"                    │
│  Keep timing: 14:00 - 15:30, June 10, 2025                             │
│  Room: Conference Room B                                               │
│                                                                         │
│  Available speakers (no conflicts during this time):                   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ ● Prof. John Chen                                               │  │
│  │                                                                  │  │
│  │   Topic Expertise:    Cloud Architecture, Microservices         │  │
│  │   Availability:       Available all day June 10                 │  │
│  │   Time Preference:    ☀️ Prefers afternoon (14:00-15:30 ideal)  │  │
│  │   A/V Requirements:   Projector, Microphone (met by Room B)     │  │
│  │   Current Sessions:   1 assigned (Morning workshop)             │  │
│  │                                                                  │  │
│  │   Match Score: 92% 🟢 (Excellent match for this workshop)       │  │
│  │                                                                  │  │
│  │   [Assign Prof. Chen to Workshop 2]                             │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ ○ Dr. Maria Lopez                                               │  │
│  │                                                                  │  │
│  │   Topic Expertise:    DevOps, Infrastructure                    │  │
│  │   Availability:       Available (no conflicts)                  │  │
│  │   Time Preference:    ⚪ Neutral about afternoon slots           │  │
│  │   A/V Requirements:   Projector, Whiteboard (Room B has both)   │  │
│  │   Current Sessions:   2 assigned (Morning + Evening)            │  │
│  │                                                                  │  │
│  │   Match Score: 75% 🟢 (Good match, slightly off-topic)          │  │
│  │                                                                  │  │
│  │   [Assign Dr. Lopez to Workshop 2]                              │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  Showing 2 of 3 available speakers. [View All Speakers]                │
│                                                                         │
│  [← Back to Conflict Details]        [Cancel]                          │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Scenario 3: Speaker Preference Conflict (Warning)

**Trigger**: User drags "Early Morning Workshop" to 08:00-09:30, assigning Dr. Sarah Miller
**Conflict**: Dr. Sarah Miller has marked "Morning before 09:00" as "Avoid" in preferences

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ⚠️ Preference Mismatch Warning                                     [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Conflict Type: Speaker Preference Conflict                            │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ You are assigning Dr. Sarah Miller to:                          │  │
│  │                                                                  │  │
│  │   Session:   "Early Morning Workshop: Getting Started"          │  │
│  │   Time:      08:00 - 09:30, June 10, 2025                       │  │
│  │   Room:      Conference Room A                                  │  │
│  │   Duration:  1.5 hours                                          │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Speaker preferences indicate:                                   │  │
│  │                                                                  │  │
│  │   Time Preferences:                                             │  │
│  │   ⊘ Morning (6:00 - 12:00)        Avoid (especially before 9am) │  │
│  │   ☀️ Afternoon (12:00 - 18:00)     Preferred                     │  │
│  │   ⊘ Evening (18:00 - 22:00)       Avoid                         │  │
│  │                                                                  │  │
│  │   Specific Avoid Time:                                          │  │
│  │   🔴 June 10, 08:00 - 09:00 (Morning commute)                   │  │
│  │                                                                  │  │
│  │   Additional Notes:                                             │  │
│  │   "I prefer afternoon sessions due to morning family            │  │
│  │    commitments. Early morning (before 9am) is very difficult."  │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  Match Score: 25% 🔴 (Poor match - conflicts with stated preferences)  │
│                                                                         │
│  ┌─── Preference Comparison ─────────────────────────────────────────┐ │
│  │                                                                    │ │
│  │  Criteria              Preference    Actual      Status           │ │
│  │  ─────────────────────────────────────────────────────────────    │ │
│  │  Time of Day           Afternoon     Morning     ✗ Mismatch       │ │
│  │  Specific Avoid Time   08:00-09:00   08:00-09:30 ✗ Conflicts     │ │
│  │  Room Setup            Natural light Available   ✓ Match          │ │
│  │  A/V Requirements      Projector     Available   ✓ Match          │ │
│  │                                                                    │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ⚠️ This is a warning, not an error. You can proceed if needed, but    │
│     the speaker may request a time change or decline the assignment.   │
│                                                                         │
│  How would you like to proceed?                                        │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [Find Better Matching Slot] (Recommended)                       │  │
│  │     View alternative time slots that better match                │  │
│  │     Dr. Sarah Miller's preferences (afternoon slots)            │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [Assign Different Speaker]                                       │  │
│  │     Keep early morning timing but assign a speaker who           │  │
│  │     prefers or is available for morning sessions                │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [Override & Assign Anyway]                                       │  │
│  │     Proceed with this assignment despite preference mismatch     │  │
│  │     ⚠️ You may need to contact the speaker to confirm            │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [Cancel]                                                         │  │
│  │     Abort this assignment and return to slot assignment view    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Unique Features for Preference Conflict (vs. Hard Conflicts)**:

1. **Warning Severity**: Yellow ⚠️ icon instead of red 🔴 error
2. **Override Option**: [Override & Assign Anyway] button available (not present in hard conflicts)
3. **Match Score Display**: Shows percentage match with color coding
4. **Preference Comparison Table**: Detailed breakdown of criteria match/mismatch
5. **Advisory Language**: "You can proceed if needed" vs. "Assignment blocked"

**Override Confirmation Flow**:

User clicks [Override & Assign Anyway] → Modal shows confirmation:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ⚠️ Confirm Override                                                 [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  You are about to override speaker preferences.                        │
│                                                                         │
│  This assignment has a match score of only 25% (Poor match).           │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Recommendation:                                                  │  │
│  │                                                                  │  │
│  │ We recommend contacting Dr. Sarah Miller before confirming      │  │
│  │ this timing to ensure she can accommodate the early morning     │  │
│  │ slot despite her stated preferences.                            │  │
│  │                                                                  │  │
│  │ Contact: sarah.miller@example.com                               │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ☑ I understand the speaker may request a time change                  │
│  ☑ I will contact the speaker to confirm availability                  │
│                                                                         │
│  [Proceed with Assignment]        [Cancel]                             │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

After clicking [Proceed with Assignment] → Success with warning flag:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ⚠️ Assignment Confirmed with Override                              [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Session assigned successfully, but marked for follow-up.              │
│                                                                         │
│  Session:   "Early Morning Workshop: Getting Started"                  │
│  Speaker:   Dr. Sarah Miller                                           │
│  Time:      08:00 - 09:30, June 10, 2025                               │
│  Room:      Conference Room A                                          │
│  Match:     25% (Poor match - override applied)                        │
│                                                                         │
│  ⚠️ Action Required:                                                    │
│  Contact Dr. Sarah Miller to confirm availability for this timing.     │
│                                                                         │
│  [Send Email to Speaker]    [Add to Follow-Up List]                    │
│                                                                         │
│  [Return to Slot Assignment]                                           │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Conflict Detection Logic

### Room Overlap Detection

**Trigger Conditions**:
1. Proposed session has `room = X` AND `start_time = T1` AND `end_time = T2`
2. Existing session in database has `room = X` AND time range overlaps with `[T1, T2)`

**SQL Query**:
```sql
SELECT * FROM sessions
WHERE room = :proposedRoom
  AND event_id = :eventId
  AND session_id != :proposedSessionId
  AND (
    (start_time < :proposedEndTime AND end_time > :proposedStartTime)
  )
```

**Conflict Severity**: 🔴 Error (blocking - must resolve)

---

### Speaker Double-Booking Detection

**Trigger Conditions**:
1. Proposed session assigns `speaker_id = S` AND `start_time = T1` AND `end_time = T2`
2. Existing session has same `speaker_id = S` AND time range overlaps with `[T1, T2)`

**SQL Query**:
```sql
SELECT s.* FROM sessions s
JOIN speaker_pool sp ON s.id = sp.session_id
WHERE sp.speaker_id = :proposedSpeakerId
  AND s.event_id = :eventId
  AND s.session_id != :proposedSessionId
  AND (
    (s.start_time < :proposedEndTime AND s.end_time > :proposedStartTime)
  )
```

**Conflict Severity**: 🔴 Error (blocking - must resolve)

---

### Speaker Preference Conflict Detection

**Trigger Conditions**:
1. Proposed session timing falls outside speaker's preferred time ranges
2. Proposed session overlaps with speaker's specific avoid times
3. Match score calculated based on preferences falls below threshold (e.g., <50%)

**Preference Match Algorithm**:
```typescript
interface PreferenceMatch {
  score: number;  // 0-100
  severity: 'strong' | 'acceptable' | 'poor';
  conflicts: string[];
}

function calculatePreferenceMatch(
  sessionTiming: { start: Date, end: Date, room: Room },
  preferences: SpeakerPreferences
): PreferenceMatch {
  let score = 100;
  const conflicts: string[] = [];

  // Time of day preference (30 points)
  if (isInPreferredTimeRange(sessionTiming, preferences.preferredTimes)) {
    // No penalty
  } else if (isInAvoidTimeRange(sessionTiming, preferences.avoidTimes)) {
    score -= 30;
    conflicts.push('Time of day conflicts with avoid preference');
  } else {
    score -= 15;  // Neutral time
  }

  // Specific avoid times (40 points)
  if (overlapsSpecificAvoidTime(sessionTiming, preferences.specificAvoidTimes)) {
    score -= 40;
    conflicts.push(`Conflicts with specific avoid time: ${formatAvoidTime(...)}`);
  }

  // Room setup preferences (15 points)
  if (!roomMeetsRequirements(sessionTiming.room, preferences.roomSetup)) {
    score -= 15;
    conflicts.push('Room setup does not meet preferences');
  }

  // A/V requirements (15 points)
  if (!roomHasAVSetup(sessionTiming.room, preferences.avRequirements)) {
    score -= 15;
    conflicts.push('Room lacks required A/V equipment');
  }

  // Determine severity
  let severity: 'strong' | 'acceptable' | 'poor';
  if (score >= 80) severity = 'strong';
  else if (score >= 50) severity = 'acceptable';
  else severity = 'poor';

  return { score, severity, conflicts };
}
```

**Conflict Severity**: ⚠️ Warning (non-blocking - can override)

**Warning Threshold**: Match score < 50% triggers preference conflict modal

---

## Modal Behavior

### Trigger Timing

**When Conflicts Are Detected**:
- **During Drag**: Real-time validation highlights potential conflicts (slot border color changes)
- **On Drop**: Modal appears immediately when drop action is attempted
- **Before Save**: Final validation before saving assignment to database

### Modal Dismiss Behavior

**Close Button [X]**:
- Same as [Cancel] button
- Does NOT save any changes
- Returns to slot assignment page with no assignment made

**[Cancel] Button**:
- Abort assignment
- Return to slot assignment page
- Dragged speaker returns to speaker pool sidebar
- No changes saved

**Background Click**:
- Modal does NOT close (prevent accidental dismissal)
- User must use [X] or [Cancel] to close

**Escape Key**:
- Same as [Cancel] button
- Keyboard shortcut for quick dismissal

### Success States

After successful resolution (any option):
1. Modal transitions to success confirmation (green checkmark)
2. Shows assignment details with match score
3. Auto-dismisses after 2 seconds OR user clicks [Return to Slot Assignment]
4. Toast notification appears in slot assignment page: "✓ Session assigned successfully"
5. Session grid updates to show new assignment
6. Speaker card moves to "Assigned" section in pool

---

## Accessibility

### Keyboard Navigation

**Tab Order**:
1. Close button [X]
2. Resolution option buttons ([1], [2], [3], etc.)
3. [Cancel] button (always last)

**Keyboard Shortcuts**:
- `Escape`: Close modal (same as [Cancel])
- `1`, `2`, `3`: Activate corresponding resolution option
- `Enter`: Confirm currently focused button
- `Tab`: Navigate between buttons
- `Shift+Tab`: Navigate backwards

### ARIA Attributes

```html
<div role="dialog" aria-labelledby="conflict-title" aria-modal="true">
  <h2 id="conflict-title">🔴 Timing Conflict Detected</h2>

  <!-- Conflict type announcement -->
  <div role="alert" aria-live="assertive">
    Conflict Type: Room Overlap. This assignment is blocked.
  </div>

  <!-- Resolution options -->
  <button aria-label="Option 1: Find alternative time slot that matches speaker preferences">
    [1] Find Alternative Time Slot
  </button>

  <!-- Close button -->
  <button aria-label="Close conflict resolution modal and cancel assignment">
    [X]
  </button>
</div>
```

### Screen Reader Announcements

**On Modal Open**:
```
"Timing conflict detected. Room overlap. The assignment is blocked and must be resolved. 3 resolution options available. Press 1, 2, or 3 to select an option, or press Escape to cancel."
```

**On Resolution Selection**:
```
"Finding alternative time slots. 3 slots found. Press Enter to assign to a slot, or Tab to review options."
```

**On Success**:
```
"Conflict resolved. Workshop 2 has been assigned to Conference Room B from 11:00 to 12:30. Match score: 85 percent, strong match. Returning to slot assignment page."
```

---

## Responsive Design

### Desktop (≥1200px)

- Modal width: 800px (fixed)
- Visual timeline: Full horizontal layout
- All resolution options visible simultaneously
- Two-column layout for alternative slot suggestions

### Tablet (768px - 1199px)

- Modal width: 90% viewport width (max 700px)
- Visual timeline: Condensed horizontal layout
- Resolution options: Single column, full width
- Alternative slots: Single column

### Mobile (<768px)

- Modal: Full-screen overlay
- Header: Sticky at top
- Visual timeline: Simplified vertical timeline
- Resolution options: Large touch-friendly buttons (min 44px height)
- Scrollable content area
- Sticky footer with [Cancel] button

---

## Edge Cases

### Multiple Simultaneous Conflicts

**Scenario**: Proposed assignment violates BOTH room overlap AND speaker preference

**Behavior**:
- Modal shows MOST SEVERE conflict first (room overlap)
- After resolving room conflict, if new assignment still has preference conflict → second modal appears
- Conflicts are chained (resolve one at a time)

**Example Flow**:
1. User drags speaker to conflicting room slot
2. Modal shows: "🔴 Room Overlap" (error - blocking)
3. User selects "Assign to Different Room" → chooses Room B
4. Room conflict resolved
5. Modal transitions to: "⚠️ Preference Mismatch" (warning - non-blocking)
6. User can now choose to override or find better slot

### Published Session Conflicts

**Scenario**: Conflict involves a session already published (Phase 2 or 3)

**Behavior**:
- Modal shows special warning badge: "⚠️ Published Session"
- Additional context: "This session is already published. Moving it requires unpublishing."
- Reassignment option labeled: "[Reschedule Keynote (Requires Unpublish)]"
- If user selects reassignment → additional confirmation modal:
  ```
  ⚠️ Unpublish Confirmation Required

  This action will:
  • Unpublish Phase 2 (Speakers) from the public site
  • Send notification to 247 active subscribers
  • Revert event to Phase 1 (Topic Only)

  Are you sure you want to proceed?

  [Yes, Unpublish & Reschedule]    [Cancel]
  ```

### Zero Alternative Slots

**Scenario**: User clicks [Find Alternative Time Slot] but no available slots exist that match preferences

**Behavior**:
```
┌─────────────────────────────────────────────────────────────────────────┐
│ 🔍 Alternative Time Slots                                          [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  No available time slots found.                                        │
│                                                                         │
│  We searched for slots matching Dr. Sarah Miller's preferences but    │
│  found no available options that:                                      │
│  • Are during afternoon (preferred time)                              │
│  • Have no room conflicts                                             │
│  • Have no speaker conflicts                                          │
│  • Meet A/V requirements                                              │
│                                                                         │
│  Suggestions:                                                          │
│  1. Expand search to include neutral time slots (morning/evening)     │
│  2. Assign a different speaker to this session                        │
│  3. Create a new time slot by extending event schedule                │
│                                                                         │
│  [Expand Search Criteria]    [Assign Different Speaker]               │
│                                                                         │
│  [← Back to Conflict Details]        [Cancel]                         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Testing Checklist

### Room Overlap Conflict

- [ ] Modal appears when assigning to occupied room
- [ ] Visual timeline shows correct overlap region
- [ ] Overlap duration calculated correctly
- [ ] [Find Alternative Slot] shows available rooms
- [ ] [Assign to Different Room] lists only available rooms
- [ ] [Reassign Other Session] shows affected speakers
- [ ] Success confirmation appears after resolution
- [ ] Session grid updates with new assignment

### Speaker Double-Booked Conflict

- [ ] Modal appears when speaker has overlapping session
- [ ] Visual timeline shows speaker's existing schedule
- [ ] Published session warning appears if applicable
- [ ] [Find Alternative Slot] filters by speaker availability
- [ ] [Assign Different Speaker] shows available speakers with match scores
- [ ] Success confirmation includes speaker name

### Speaker Preference Conflict

- [ ] Modal appears when match score < 50%
- [ ] Preference comparison table shows all criteria
- [ ] Match score displayed with color coding
- [ ] [Override & Assign Anyway] button present
- [ ] Override confirmation modal appears
- [ ] Checkboxes required before proceeding with override
- [ ] Success confirmation includes warning flag
- [ ] [Send Email to Speaker] action works

### Multiple Conflicts

- [ ] Conflicts resolved in sequence (error → warning)
- [ ] Second modal appears after first resolution
- [ ] No infinite loop if conflicts cannot be resolved

### Edge Cases

- [ ] Published session warning appears correctly
- [ ] Unpublish confirmation modal appears when rescheduling published session
- [ ] Zero alternatives handled gracefully
- [ ] Expand search criteria works
- [ ] Keyboard navigation works (1, 2, 3, Escape)
- [ ] Screen reader announcements fire

### Accessibility

- [ ] Modal has correct ARIA attributes
- [ ] Focus trapped within modal
- [ ] Escape key closes modal
- [ ] Tab order logical
- [ ] Screen reader announces conflict severity

---

## Related Wireframes

- **story-5.7-slot-assignment-page.md**: Main slot assignment interface where conflicts are triggered
- **story-5.7-speaker-preference-panel.md**: Detailed speaker preferences used for match scoring
- **story-5.7-navigation-integration.md**: Navigation flows to/from slot assignment page

---

## Change Log

| Date       | Version | Description                          | Author     |
|------------|---------|--------------------------------------|------------|
| 2025-12-25 | 1.0     | Initial conflict resolution wireframe | Sally (UX Expert) |
