# Story 5.7: Bulk Auto-Assignment Feature - Slot Assignment

**Story**: Epic 5, Story 5.7 - Slot Assignment & Progressive Publishing
**Component**: Bulk Auto-Assignment Wizard
**User Role**: Organizer
**Related FR**: FR17 (Speaker Matching), AC13 (Bulk Auto-Assignment)

---

## Purpose

This wireframe documents the bulk auto-assignment feature that allows organizers to automatically assign multiple unassigned sessions at once using an intelligent matching algorithm. The feature reduces manual work while ensuring high-quality speaker-slot matches.

**Context**: Triggered from the dedicated slot assignment page when organizer has multiple unassigned sessions.

---

## Entry Points

### Entry Point 1: Slot Assignment Page - Quick Actions Panel

**Location**: Right sidebar on slot assignment page

```
┌─────────────────────────────────────────────────────────────────┐
│  Slot Assignment - BATbern 2025                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [Speaker Pool]  [Session Timeline Grid]  [Quick Actions]      │
│                                                                 │
│  ┌─── Quick Actions ────────────────────────────────────────┐  │
│  │                                                           │  │
│  │  Session Progress: 3 of 8 assigned (37%)                 │  │
│  │  🔶 5 sessions awaiting timing                            │  │
│  │                                                           │  │
│  │  ┌─────────────────────────────────────────────────────┐ │  │
│  │  │ [Auto-Assign All]                                   │ │  │
│  │  │                                                       │ │  │
│  │  │ Let the system automatically assign remaining       │ │  │
│  │  │ sessions based on speaker preferences and           │ │  │
│  │  │ availability.                                       │ │  │
│  │  │                                                       │ │  │
│  │  │ • Analyzes 5 unassigned sessions                    │ │  │
│  │  │ • Matches with 5 available speakers                 │ │  │
│  │  │ • Optimizes for preference scores                   │ │  │
│  │  │                                                       │ │  │
│  │  └─────────────────────────────────────────────────────┘ │  │
│  │                                                           │  │
│  │  [Clear All Assignments]                                 │  │
│  │                                                           │  │
│  │  Export Options:                                         │  │
│  │  [Download Schedule PDF]                                 │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

User Action: Click [Auto-Assign All]
Result: Opens Algorithm Selection Modal (Step 1)
```

**Button States**:
- **Enabled**: When ≥2 unassigned sessions AND ≥2 available speakers
- **Disabled**: When <2 unassigned sessions OR <2 available speakers
- **Tooltip (Disabled)**: "Auto-assignment requires at least 2 unassigned sessions and 2 available speakers"

---

### Entry Point 2: Slot Assignment Page - Empty State Banner

**Trigger**: When organizer first lands on slot assignment page with 0 assignments

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Slot Assignment - BATbern 2025                                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─── Getting Started ────────────────────────────────────────────────┐│
│  │                                                                     ││
│  │  🎯 No sessions assigned yet                                        ││
│  │                                                                     ││
│  │  You have 8 accepted speakers awaiting session timing assignment.  ││
│  │                                                                     ││
│  │  Choose your approach:                                             ││
│  │                                                                     ││
│  │  ┌───────────────────────────┐  ┌───────────────────────────────┐ ││
│  │  │ [Let AI Auto-Assign]      │  │ [Assign Manually]             │ ││
│  │  │                           │  │                               │ ││
│  │  │ Automatically assign all  │  │ Drag and drop speakers to     │ ││
│  │  │ sessions using intelligent│  │ session slots for full control│ ││
│  │  │ matching algorithm        │  │ over timing assignments       │ ││
│  │  │                           │  │                               │ ││
│  │  │ ⚡ Fast (30 seconds)      │  │ 🎨 Flexible (custom control)  │ ││
│  │  │ 📊 High match scores      │  │ 👁️ Visual preference feedback│ ││
│  │  │                           │  │                               │ ││
│  │  └───────────────────────────┘  └───────────────────────────────┘ ││
│  │                                                                     ││
│  │  Tip: You can also use a hybrid approach - auto-assign most        ││
│  │       sessions, then manually adjust any that need special handling││
│  │                                                                     ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘

User Action: Click [Let AI Auto-Assign]
Result: Opens Algorithm Selection Modal (Step 1)
```

---

## Step 1: Algorithm Selection Modal

**Trigger**: User clicks [Auto-Assign All] button

**Purpose**: Allow organizer to choose optimization strategy and configure constraints

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 🤖 Auto-Assign Session Timings                                     [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Configure how the system should assign sessions to time slots.        │
│                                                                         │
│  ┌─── Optimization Strategy ──────────────────────────────────────────┐│
│  │                                                                     ││
│  │  Choose what to prioritize when assigning sessions:                ││
│  │                                                                     ││
│  │  ○ Optimize for Speaker Preferences                                ││
│  │    Prioritize matching speakers to their preferred time slots      ││
│  │                                                                     ││
│  │    Best for: Maximizing speaker satisfaction and availability      ││
│  │    Algorithm: Time preference matching (weights: Time 40%, Room 20%)││
│  │    Expected avg match score: ~85%                                  ││
│  │                                                                     ││
│  │  ─────────────────────────────────────────────────────────────     ││
│  │                                                                     ││
│  │  ○ Optimize for Expertise Match                                    ││
│  │    Prioritize matching speaker expertise to session topics         ││
│  │                                                                     ││
│  │    Best for: Content quality and topic relevance                   ││
│  │    Algorithm: Topic similarity scoring (NLP-based)                 ││
│  │    Expected avg match score: ~78%                                  ││
│  │                                                                     ││
│  │  ─────────────────────────────────────────────────────────────     ││
│  │                                                                     ││
│  │  ● Optimize for Balanced Schedule (Recommended)                    ││
│  │    Balance speaker preferences, expertise, and time distribution   ││
│  │                                                                     ││
│  │    Best for: Overall event quality and attendee experience         ││
│  │    Algorithm: Multi-criteria optimization (Time 30%, Expertise 25%,││
│  │                Room 15%, Distribution 30%)                          ││
│  │    Expected avg match score: ~82%                                  ││
│  │                                                                     ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                                                         │
│  ┌─── Advanced Options ───────────────────────────────────────────────┐│
│  │                                                                     ││
│  │  [Show Advanced Options ▼]                                         ││
│  │                                                                     ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                                                         │
│  ┌─── Sessions to Assign ─────────────────────────────────────────────┐│
│  │                                                                     ││
│  │  Unassigned sessions: 5                                            ││
│  │  Available speakers: 5                                             ││
│  │                                                                     ││
│  │  ☑ Workshop 1: Advanced Cloud Architecture (Dr. Sarah Miller)      ││
│  │  ☑ Panel Discussion: Industry Trends (Multiple speakers)           ││
│  │  ☑ Afternoon Workshop: Practical DevOps (Dr. Maria Lopez)          ││
│  │  ☑ Evening Session: Q&A and Networking (Robert Williams)           ││
│  │  ☑ Lightning Talks: Community Showcase (Multiple speakers)         ││
│  │                                                                     ││
│  │  [Deselect All]  [Select All]                                      ││
│  │                                                                     ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                                                         │
│  Estimated processing time: ~15 seconds                                │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [Preview Assignments]                           [Cancel]          │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Advanced Options Expanded**:

```
│  ┌─── Advanced Options ───────────────────────────────────────────────┐│
│  │                                                                     ││
│  │  [Hide Advanced Options ▲]                                         ││
│  │                                                                     ││
│  │  Constraints:                                                      ││
│  │  ☑ Respect hard constraints (avoid times, speaker conflicts)       ││
│  │     Never violate speaker-specified avoid times                    ││
│  │                                                                     ││
│  │  ☑ Balance session distribution across days                        ││
│  │     Avoid clustering all sessions on one day                       ││
│  │                                                                     ││
│  │  ☐ Prioritize keynotes in prime time slots                         ││
│  │     Assign keynote sessions to morning/early afternoon slots       ││
│  │                                                                     ││
│  │  ─────────────────────────────────────────────────────────────     ││
│  │                                                                     ││
│  │  Room Preferences:                                                 ││
│  │  ○ Use all available rooms                                         ││
│  │  ● Prefer specific rooms for certain session types                 ││
│  │    • Keynotes → Main Hall                                          ││
│  │    • Workshops → Conference Rooms A/B                              ││
│  │    • Panels → Main Hall or Conference Room A                       ││
│  │                                                                     ││
│  │  ─────────────────────────────────────────────────────────────     ││
│  │                                                                     ││
│  │  Conflict Resolution:                                              ││
│  │  ● Stop and ask (recommended - show conflicts for review)          ││
│  │  ○ Auto-resolve (find alternatives, may reduce match scores)       ││
│  │                                                                     ││
│  └─────────────────────────────────────────────────────────────────────┘│
```

**Validation**:
- At least 1 session must be selected
- Selected optimization strategy required
- If "Respect hard constraints" unchecked → warning confirmation

**Button States**:
- **[Preview Assignments]**: Primary CTA, enabled when ≥1 session selected
- **[Cancel]**: Secondary, always enabled

---

## Step 2: Processing Indicator

**Display**: After clicking [Preview Assignments], modal transitions to processing state

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 🤖 Auto-Assign Session Timings                                     [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                                                                 │   │
│  │  ⏳ Analyzing assignments...                                    │   │
│  │                                                                 │   │
│  │  ████████████████████████████░░░░░░  78%                        │   │
│  │                                                                 │   │
│  │  Status: Evaluating speaker preferences for Workshop 2          │   │
│  │                                                                 │   │
│  │  ┌───────────────────────────────────────────────────────────┐ │   │
│  │  │ ✓ Analyzed 5 sessions                                     │ │   │
│  │  │ ✓ Calculated preference scores for 5 speakers             │ │   │
│  │  │ ✓ Evaluated 120 possible slot combinations                │ │   │
│  │  │ ⏳ Optimizing assignments (Hungarian algorithm)            │ │   │
│  │  │ ⏳ Validating for conflicts...                             │ │   │
│  │  └───────────────────────────────────────────────────────────┘ │   │
│  │                                                                 │   │
│  │  Estimated time remaining: 5 seconds                           │   │
│  │                                                                 │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  [Cancel Processing]                                                   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Processing Steps**:
1. ✓ Analyzed sessions (2s)
2. ✓ Calculated preference scores (3s)
3. ✓ Evaluated slot combinations (5s)
4. ⏳ Optimizing assignments (3s)
5. ⏳ Validating for conflicts (2s)

**Total**: ~15 seconds

---

## Step 3: Preview Assignments

**Display**: After processing completes, modal transitions to preview state

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 🤖 Preview Auto-Assignments                                        [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Algorithm: Balanced Schedule                                          │
│  Sessions Analyzed: 5                                                  │
│  Assignments Generated: 5                                              │
│  Overall Match Score: 82% 🟢 (Good)                                     │
│                                                                         │
│  ┌─── Side-by-Side Comparison ────────────────────────────────────────┐│
│  │                                                                     ││
│  │  Current State                    →    Proposed Assignments        ││
│  │  ───────────────────────────────────────────────────────────────   ││
│  │                                                                     ││
│  │  Workshop 1                       →    Workshop 1                  ││
│  │  Dr. Sarah Miller                      Dr. Sarah Miller             ││
│  │  No timing assigned                    June 10, 14:00-15:30        ││
│  │                                        Conference Room B            ││
│  │                                        Match: 92% 🟢 (Strong)       ││
│  │                                        [View Details]               ││
│  │                                                                     ││
│  │  ─────────────────────────────────────────────────────────────     ││
│  │                                                                     ││
│  │  Panel Discussion                 →    Panel Discussion            ││
│  │  John Smith, Jane Doe, R. Chen         John Smith, Jane Doe, R.C.  ││
│  │  No timing assigned                    June 10, 16:00-17:30        ││
│  │                                        Main Hall                    ││
│  │                                        Match: 78% 🟢 (Good)         ││
│  │                                        [View Details]               ││
│  │                                                                     ││
│  │  ─────────────────────────────────────────────────────────────     ││
│  │                                                                     ││
│  │  Afternoon Workshop               →    Afternoon Workshop          ││
│  │  Dr. Maria Lopez                       Dr. Maria Lopez              ││
│  │  No timing assigned                    June 11, 13:00-14:30        ││
│  │                                        Conference Room A            ││
│  │                                        Match: 85% 🟢 (Strong)       ││
│  │                                        [View Details]               ││
│  │                                                                     ││
│  │  ─────────────────────────────────────────────────────────────     ││
│  │                                                                     ││
│  │  Evening Session                  →    Evening Session             ││
│  │  Robert Williams                       Robert Williams              ││
│  │  No timing assigned                    June 11, 18:00-19:30        ││
│  │                                        Conference Room B            ││
│  │                                        Match: 65% 🟡 (Acceptable)   ││
│  │                                        ⚠️ Speaker prefers afternoon ││
│  │                                        [View Details] [Adjust]      ││
│  │                                                                     ││
│  │  ─────────────────────────────────────────────────────────────     ││
│  │                                                                     ││
│  │  Lightning Talks                  →    Lightning Talks             ││
│  │  Multiple speakers                     Multiple speakers            ││
│  │  No timing assigned                    June 12, 10:00-11:30        ││
│  │                                        Main Hall                    ││
│  │                                        Match: 88% 🟢 (Strong)       ││
│  │                                        [View Details]               ││
│  │                                                                     ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                                                         │
│  ┌─── Detected Issues ────────────────────────────────────────────────┐│
│  │                                                                     ││
│  │  ⚠️ 1 Warning:                                                      ││
│  │                                                                     ││
│  │  • Evening Session: Low match score (65%)                          ││
│  │    Speaker Robert Williams prefers afternoon slots but was         ││
│  │    assigned to 18:00-19:30 (evening). This was the best available ││
│  │    slot that avoids conflicts.                                     ││
│  │                                                                     ││
│  │    Recommendation: Contact speaker to confirm availability OR      ││
│  │    manually adjust after accepting assignments.                    ││
│  │                                                                     ││
│  │  ℹ️ 0 Conflicts detected - all assignments are valid               ││
│  │                                                                     ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                                                         │
│  ┌─── Summary Statistics ─────────────────────────────────────────────┐│
│  │                                                                     ││
│  │  Total sessions assigned:         5 of 5 (100%)                    ││
│  │  Average match score:             82% (Good overall quality)       ││
│  │  Strong matches (≥80%):           4 sessions                       ││
│  │  Acceptable matches (50-79%):     1 session                        ││
│  │  Poor matches (<50%):             0 sessions                       ││
│  │                                                                     ││
│  │  Time distribution:                                                ││
│  │  • June 10: 2 sessions (Workshop 1, Panel)                         ││
│  │  • June 11: 2 sessions (Afternoon Workshop, Evening Session)       ││
│  │  • June 12: 1 session (Lightning Talks)                            ││
│  │                                                                     ││
│  │  Room utilization:                                                 ││
│  │  • Main Hall: 2 sessions                                           ││
│  │  • Conference Room A: 1 session                                    ││
│  │  • Conference Room B: 2 sessions                                   ││
│  │                                                                     ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                                                         │
│  What would you like to do?                                            │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [Accept All Assignments]    [Customize Before Applying]  [Cancel]│  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**[View Details] Button** (expands inline):

```
│  │  Workshop 1                       →    Workshop 1                  ││
│  │  Dr. Sarah Miller                      Dr. Sarah Miller             ││
│  │  No timing assigned                    June 10, 14:00-15:30        ││
│  │                                        Conference Room B            ││
│  │                                        Match: 92% 🟢 (Strong)       ││
│  │                                        [Hide Details ▲]             ││
│  │                                                                     ││
│  │  ┌───────────────────────────────────────────────────────────────┐││
│  │  │ Match Score Breakdown:                                        │││
│  │  │                                                               │││
│  │  │ ✓ Time of Day:      Afternoon (preferred)        +30 points  │││
│  │  │ ✓ Avoid Times:      No conflicts                 +40 points  │││
│  │  │ ✓ Room Features:    Natural light, quiet room    +12 points  │││
│  │  │ ✓ A/V Setup:        Projector + mic available    +10 points  │││
│  │  │                                                               │││
│  │  │ Total: 92/100                                                 │││
│  │  │                                                               │││
│  │  │ Why this slot was chosen:                                     │││
│  │  │ • Matches speaker's preferred time (afternoon)                │││
│  │  │ • No conflicts with avoid times (June 10, 08:00-09:00)        │││
│  │  │ • Room B has natural light (speaker preference)               │││
│  │  │ • All required A/V equipment available                        │││
│  │  │ • No room or speaker conflicts                                │││
│  │  │                                                               │││
│  │  │ Alternative slots considered:                                 │││
│  │  │ • June 10, 11:00-12:30, Room A (score: 85%)                  │││
│  │  │ • June 11, 14:00-15:30, Main Hall (score: 78%)               │││
│  │  │                                                               │││
│  │  └───────────────────────────────────────────────────────────────┘││
│  │                                                                     ││
```

**[Adjust] Button** (for sessions with warnings):

Click [Adjust] → Opens mini slot picker:

```
│  ┌─── Adjust Evening Session ──────────────────────────────────────┐  │
│  │                                                                  │  │
│  │  Current Assignment:                                            │  │
│  │  June 11, 18:00-19:30, Conference Room B                        │  │
│  │  Match: 65% 🟡 (Speaker prefers afternoon)                      │  │
│  │                                                                  │  │
│  │  Alternative Slots:                                             │  │
│  │                                                                  │  │
│  │  ○ June 11, 14:00-15:30, Conference Room A                      │  │
│  │     Match: 82% 🟢 (Better match, afternoon slot)                │  │
│  │     ⚠️ Requires moving "Afternoon Workshop" to different slot   │  │
│  │                                                                  │  │
│  │  ○ June 12, 13:00-14:30, Main Hall                              │  │
│  │     Match: 75% 🟢 (Afternoon, different day)                    │  │
│  │     ✓ No conflicts, slot available                              │  │
│  │                                                                  │  │
│  │  ● Keep current assignment (65% match)                          │  │
│  │                                                                  │  │
│  │  [Apply Change]    [Cancel]                                     │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
```

---

## Step 4A: Accept All Assignments (Success Flow)

**Trigger**: User clicks [Accept All Assignments]

**Confirmation**:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ✓ Assignments Applied                                              [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                                                                 │   │
│  │  🎉 Success! All session timings have been assigned.           │   │
│  │                                                                 │   │
│  │  ████████████████████████████████████ 100%                     │   │
│  │                                                                 │   │
│  │  ✓ 5 sessions assigned                                         │   │
│  │  ✓ Average match score: 82%                                    │   │
│  │  ✓ 0 conflicts detected                                        │   │
│  │  ✓ All assignments saved                                       │   │
│  │                                                                 │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─── Next Steps ──────────────────────────────────────────────────┐   │
│  │                                                                 │   │
│  │  What would you like to do now?                                │   │
│  │                                                                 │   │
│  │  • Review the full agenda timeline                             │   │
│  │  • Manually adjust any sessions if needed                      │   │
│  │  • Proceed to Publishing to make schedule public               │   │
│  │                                                                 │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [View Timeline]   [Make Adjustments]   [Go to Publishing]       │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Auto-dismiss**: Modal auto-closes after 3 seconds OR user clicks button

**On Close**: Returns to slot assignment page with updated timeline grid showing all assignments

**Toast Notification** (appears in slot assignment page):
```
✓ Auto-assignment complete: 5 sessions assigned with 82% avg match score
```

---

## Step 4B: Customize Before Applying (Hybrid Flow)

**Trigger**: User clicks [Customize Before Applying]

**Result**: Modal closes, returns to slot assignment page with:
- **Proposed assignments** shown as semi-transparent overlays on timeline grid
- **Speaker cards** remain in pool (not moved yet)
- **Accept/Reject buttons** on each proposed assignment

```
┌───────────────────────────────────────────────────────────────────────────┐
│  Slot Assignment - BATbern 2025                                           │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  ⚠️ Review Proposed Assignments (5 sessions)                              │
│  Accept or reject each suggestion, or make manual adjustments.           │
│  [Accept All]  [Reject All]                                              │
│                                                                           │
│  ┌─── Session Timeline ────────────────────────────────────────────────┐ │
│  │                                                                      │ │
│  │  June 10, 2025                                                       │ │
│  │  ┌──────────────────────────────────────────────────────────────┐   │ │
│  │  │       Room A           Room B           Main Hall            │   │ │
│  │  │ 14:00 ┌──────────────┐ ┌───────────────────┐                 │   │ │
│  │  │       │              │ │ PROPOSED:         │                 │   │ │
│  │  │       │              │ │ Workshop 1        │                 │   │ │
│  │  │ 15:30 │              │ │ Dr. S. Miller     │                 │   │ │
│  │  │       │              │ │ Match: 92% 🟢     │                 │   │ │
│  │  │       │              │ │ [✓Accept][✗Reject]│                 │   │ │
│  │  │       └──────────────┘ └───────────────────┘                 │   │ │
│  │  │                                                               │   │ │
│  │  │ 16:00 ┌──────────────┐                      ┌──────────────┐ │   │ │
│  │  │       │              │                      │ PROPOSED:    │ │   │ │
│  │  │       │              │                      │ Panel        │ │   │ │
│  │  │ 17:30 │              │                      │ Multiple     │ │   │ │
│  │  │       │              │                      │ Match: 78% 🟢│ │   │ │
│  │  │       │              │                      │ [✓][✗]       │ │   │ │
│  │  │       └──────────────┘                      └──────────────┘ │   │ │
│  │  └──────────────────────────────────────────────────────────────┘   │ │
│  │                                                                      │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘

User Actions:
1. Click [✓Accept] on individual proposals → Assignment becomes permanent (overlay becomes solid)
2. Click [✗Reject] on individual proposals → Overlay disappears, speaker returns to pool
3. Drag speaker from pool to different slot → Overrides proposal
4. Click [Accept All] → All proposals become permanent
5. Click [Reject All] → All proposals removed, return to manual assignment
```

**Proposed Assignment Visual**:
- **Border**: Dashed 2px (indicates temporary/proposed state)
- **Background**: Semi-transparent with match score color (green 🟢 / yellow 🟡)
- **Badge**: "PROPOSED" label in top-left corner
- **Buttons**: Inline [✓Accept] and [✗Reject] buttons

**After All Accepted/Rejected**:
- Banner disappears
- Timeline grid shows final state
- Success toast: "All proposed assignments reviewed. X accepted, Y rejected."

---

## Algorithm Details (Technical Documentation)

### Balanced Schedule Algorithm

**Objective**: Maximize overall match quality while balancing multiple criteria

**Input**:
- `sessions[]`: Array of unassigned sessions with speaker assignments
- `speakers[]`: Array of speakers with preferences
- `slots[]`: Array of available time slots with room assignments

**Output**:
- `assignments[]`: Array of { sessionId, slotId, matchScore }

**Algorithm**: Hungarian Algorithm (Munkres Assignment Algorithm)

```typescript
interface Assignment {
  sessionId: string;
  slotId: string;
  matchScore: number;
}

function balancedScheduleAlgorithm(
  sessions: Session[],
  speakers: Speaker[],
  slots: Slot[]
): Assignment[] {
  // Step 1: Build cost matrix
  const costMatrix: number[][] = [];

  for (const session of sessions) {
    const rowCosts: number[] = [];
    for (const slot of slots) {
      const speaker = speakers.find(s => s.id === session.speakerId);
      const matchScore = calculateMatchScore(slot, speaker.preferences);

      // Convert match score (0-100) to cost (higher match = lower cost)
      const cost = 100 - matchScore;
      rowCosts.push(cost);
    }
    costMatrix.push(rowCosts);
  }

  // Step 2: Apply Hungarian algorithm to minimize total cost
  const hungarianResult = hungarianAlgorithm(costMatrix);

  // Step 3: Convert result to assignments
  const assignments: Assignment[] = [];
  for (let i = 0; i < hungarianResult.length; i++) {
    const sessionIndex = i;
    const slotIndex = hungarianResult[i];

    assignments.push({
      sessionId: sessions[sessionIndex].id,
      slotId: slots[slotIndex].id,
      matchScore: 100 - costMatrix[sessionIndex][slotIndex]
    });
  }

  // Step 4: Validate for hard conflicts
  const validated = validateAssignments(assignments);

  return validated;
}
```

**Complexity**: O(n³) where n = number of sessions

**Expected Runtime**:
- 5 sessions: ~5 seconds
- 10 sessions: ~15 seconds
- 20 sessions: ~45 seconds

### Speaker Preference Algorithm

**Weights**:
- Time preference: 40%
- Avoid times: 40%
- Room features: 10%
- A/V setup: 10%

(Prioritizes speaker availability and time preferences)

### Expertise Match Algorithm

**Uses**: Natural Language Processing (NLP) for topic similarity

```typescript
function expertiseMatchScore(sessionTopic: string, speakerExpertise: string[]): number {
  // Use TF-IDF + cosine similarity
  const sessionVector = tfidf(sessionTopic);
  const expertiseVectors = speakerExpertise.map(e => tfidf(e));

  const similarities = expertiseVectors.map(v => cosineSimilarity(sessionVector, v));
  const maxSimilarity = Math.max(...similarities);

  return maxSimilarity * 100;  // Convert to 0-100 scale
}
```

**Weights**:
- Topic similarity: 60%
- Speaker credentials: 20%
- Past speaking experience: 20%

---

## Error Handling

### Insufficient Slots

**Scenario**: More sessions than available slots

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ⚠️ Insufficient Available Slots                                    [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Unable to auto-assign all sessions.                                   │
│                                                                         │
│  Sessions to assign: 8                                                 │
│  Available slots: 5                                                    │
│  Shortfall: 3 slots                                                    │
│                                                                         │
│  Recommendations:                                                      │
│  1. Add more time slots to the event schedule                         │
│  2. Assign only some sessions (select which to prioritize)            │
│  3. Increase session duration to fit more in fewer slots              │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [Add Time Slots]   [Assign Partial]   [Cancel]                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Unavoidable Conflicts

**Scenario**: Algorithm cannot find conflict-free assignments

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ⚠️ Conflicts Detected                                              [X] │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  The algorithm found optimal assignments but detected 2 conflicts:     │
│                                                                         │
│  1. Workshop 2: All available slots conflict with speaker's avoid times│
│     Dr. Sarah Miller is unavailable June 10-11 (traveling)             │
│                                                                         │
│  2. Evening Session: No room available during preferred time range     │
│     All rooms occupied 18:00-20:00                                     │
│                                                                         │
│  What would you like to do?                                            │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [Assign Conflict-Free Only]  [Show Best Effort]  [Cancel]       │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**[Show Best Effort]**: Shows assignments with warnings, allows manual override

---

## Accessibility

### Screen Reader Support

**Algorithm Selection**:
```
"Auto-assign session timings dialog. Choose optimization strategy.
Currently selected: Balanced Schedule.
3 options available. Press Down arrow to explore options."
```

**Preview Assignments**:
```
"Preview auto-assignments. 5 sessions assigned with average match score 82 percent.
1 warning detected. Press Tab to review each assignment."
```

**ARIA Attributes**:
```html
<div role="dialog" aria-labelledby="auto-assign-title" aria-modal="true">
  <h2 id="auto-assign-title">Auto-Assign Session Timings</h2>

  <fieldset aria-labelledby="strategy-legend">
    <legend id="strategy-legend">Optimization Strategy</legend>
    <input type="radio" id="balanced" name="strategy" aria-describedby="balanced-desc" />
    <label for="balanced">Optimize for Balanced Schedule</label>
    <p id="balanced-desc">Balance speaker preferences, expertise, and time distribution</p>
  </fieldset>
</div>
```

---

## Testing Checklist

- [ ] [Auto-Assign All] button enabled when ≥2 unassigned sessions
- [ ] [Auto-Assign All] button disabled when <2 unassigned sessions
- [ ] Algorithm selection modal opens correctly
- [ ] All 3 optimization strategies selectable
- [ ] Advanced options expand/collapse correctly
- [ ] Session checkboxes work (select/deselect)
- [ ] Processing indicator shows progress accurately
- [ ] Preview displays all proposed assignments
- [ ] Match scores calculated correctly for each assignment
- [ ] [View Details] expands inline details
- [ ] [Adjust] opens alternative slot picker
- [ ] Warnings displayed for low match scores (<65%)
- [ ] [Accept All Assignments] applies all assignments
- [ ] [Customize Before Applying] shows proposed overlays
- [ ] Individual [✓Accept] / [✗Reject] buttons work
- [ ] Success modal shows correct statistics
- [ ] Toast notification appears after completion
- [ ] Timeline grid updates with assignments
- [ ] Error handling for insufficient slots
- [ ] Error handling for unavoidable conflicts
- [ ] Keyboard navigation works (Tab, Enter, Arrow keys)
- [ ] Screen reader announcements correct

---

## Related Wireframes

- **story-5.7-slot-assignment-page.md**: Main interface where [Auto-Assign All] button appears
- **story-5.7-speaker-preference-panel.md**: Preferences used by matching algorithm
- **story-5.7-conflict-resolution-modal.md**: Conflict detection for manual overrides

---

## Change Log

| Date       | Version | Description                           | Author     |
|------------|---------|---------------------------------------|------------|
| 2025-12-25 | 1.0     | Initial bulk auto-assignment wireframe | Sally (UX Expert) |
