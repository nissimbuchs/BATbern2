# Phase D: Assignment (Steps 9-10)

> Handle overflow and assign speakers to time slots

<div class="workflow-phase phase-d">
<strong>Phase D: Assignment</strong><br>
Status: <span class="feature-status planned">Planned</span><br>
Duration: 1 week<br>
State Transitions: QUALITY_REVIEWED → SLOTS_ASSIGNED
</div>

## Overview

Phase D resolves any speaker overflow situations and assigns approved speakers to specific time slots, creating the event schedule.

**Key Deliverable**: Complete event schedule with no conflicts

## Step 9: Overflow Management with Voting

<span class="feature-status planned">Planned</span>

### Purpose

When more speakers are approved than available slots, use voting to select the best presentations.

### When Overflow Occurs

**Example**:
```
Event: Full-Day Conference
Available Slots: 12
Approved Speakers: 14
Overflow: 2 speakers
```

**Scenarios**:
- More interest than expected
- All backup candidates also approved
- Additional speakers volunteered late

### Acceptance Criteria

- ✅ Overflow situation identified (approved speakers > slots)
- ✅ Voting conducted among organizers
- ✅ Top-ranked speakers selected to fill slots
- ✅ Overflow speakers notified (added to waitlist)

### How to Complete

<div class="step" data-step="1">

**Identify Overflow**

System detects overflow automatically:

```
Overflow Detection - BATbern 2025
────────────────────────────────────────
Available Slots: 12
Approved Speakers: 14
Overflow: 2 speakers ⚠️

Action Required: Vote to select top 12 speakers

[Start Voting Process]
```
</div>

<div class="step" data-step="2">

**Initiate Voting**

Organizers vote on all approved speakers:

```
Speaker Selection Voting
────────────────────────────────────────
Instructions: Rank speakers 1-5 based on:
- Content quality
- Topic relevance
- Audience appeal
- Speaker reputation

Speakers:
1. Hans Müller - Sustainable Materials
   Quality: 4.5/5 | Topic: High Demand
   [Rate: ●●●●●] 5

2. Anna Schmidt - Digital Transformation
   Quality: 4.2/5 | Topic: Medium Demand
   [Rate: ●●●●○] 4

... (14 total)

[Submit Votes]
```
</div>

<div class="step" data-step="3">

**Calculate Results**

System aggregates votes:

```
Voting Results
────────────────────────────────────────
Voters: 3 organizers

Top 12 Selected:
1. Hans Müller (avg 4.8)          ✅ SELECTED
2. Peter Weber (avg 4.7)           ✅ SELECTED
3. Anna Schmidt (avg 4.5)          ✅ SELECTED
...
12. Martin Fischer (avg 4.0)       ✅ SELECTED

──────────────────────────────────────
Overflow (Waitlist):
13. Sophie Keller (avg 3.9)        ⏸️ WAITLIST
14. Lisa Meier (avg 3.8)           ⏸️ WAITLIST

[Confirm Selection] [Adjust Manually]
```
</div>

<div class="step" data-step="4">

**Notify Overflow Speakers**

Send notification to waitlisted speakers:

```
To: sophie.keller@example.com
Subject: BATbern 2025 - Waitlist Status

Hi Sophie,

Thank you for your excellent submission for BATbern 2025!

Due to high speaker interest, we've placed your presentation
"Circular Economy in Construction" on our waitlist.

If a confirmed speaker withdraws, we'll contact you immediately
to offer the slot (deadline: 2 weeks before event).

We'd love to have you present at a future BATbern event.

Best regards,
Anna Schmidt
```
</div>

<div class="step" data-step="5">

**Complete Overflow Management**

Click **Finalize Speaker Selection**

Proceed to Step 10 (Slot Assignment)
</div>

### Voting Criteria

**Content Quality** (from Phase C review):
- Use quality scores as baseline
- Consider reviewer notes

**Topic Balance**:
- Ensure coverage across all categories
- Avoid too many similar presentations

**Speaker Diversity**:
- Mix of companies (not 5 speakers from same firm)
- Range of experience levels
- Geographic diversity

**Practical Considerations**:
- Speaker availability constraints
- Special requirements (equipment, time preferences)
- Previous BATbern participation

## Step 10: Drag-and-Drop Slot Assignment

<span class="feature-status planned">Planned</span>

### Purpose

Assign selected speakers to specific event time slots, creating the final schedule.

### Acceptance Criteria

- ✅ All selected speakers assigned to slots
- ✅ No scheduling conflicts detected
- ✅ Speaker availability constraints respected
- ✅ Parallel tracks balanced (if applicable)

### Scheduling Interface

```
BATbern 2025 Schedule Builder - March 15, 2025
════════════════════════════════════════════════

Track A                  | Track B
────────────────────────────────────────────────
09:00-09:45             | 09:00-09:45
[Drop speaker here]      | [Drop speaker here]

10:00-10:45             | 10:00-10:45
[Hans Müller]           | [Drop speaker here]
Sustainable Materials    |

11:00-11:45             | 11:00-11:45
[Drop speaker here]      | [Anna Schmidt]
                        | Digital Transform

12:00-13:00  LUNCH BREAK
────────────────────────────────────────────────
13:00-13:45             | 13:00-13:45
[Peter Weber]           | [Drop speaker here]
Urban Planning           |

Available Speakers (6):
──────────────────────────────────────
[Drag to slot]
Martin Fischer | Lisa Meier | Thomas Berg
Sophie Weber | Maria Klein | David Roth
```

### How to Complete

<div class="step" data-step="1">

**Open Schedule Builder**

Navigate to Step 10 scheduling interface.

Timeline shows all event slots.
</div>

<div class="step" data-step="2">

**Drag Speakers to Slots**

Click and drag speaker cards to time slots:

```
[Hans Müller - Sustainable Materials]
   ↓ (dragging)
[09:00-09:45 Track A] ← (drop target highlighted)
```

Speaker assigned to slot, card removes from "Available" list.
</div>

<div class="step" data-step="3">

**Resolve Conflicts**

System detects conflicts:

```
⚠️ Conflict Detected

Martin Fischer is assigned to:
- 13:00-13:45 Track A (Urban Planning Workshop)
- 13:00-13:45 Track B (Panel Discussion)

A speaker cannot be in two slots simultaneously.

[Remove from Track B] [Remove from Track A]
```
</div>

<div class="step" data-step="4">

**Respect Availability Constraints**

System warns of availability issues:

```
⚠️ Availability Warning

Anna Schmidt marked "Not available after 15:00"

You're assigning her to 16:00-16:45 Track A.

[Assign Anyway] [Choose Different Slot]
```
</div>

<div class="step" data-step="5">

**Balance Parallel Tracks**

For multi-track events, balance topics:

```
Track Balance Analysis
────────────────────────────────────────
Track A: 4 Technology, 2 Sustainability
Track B: 1 Technology, 5 Sustainability

⚠️ Imbalance detected. Consider redistributing
topics for better attendee choice.

[Auto-Balance] [Ignore] [Manual Adjust]
```
</div>

<div class="step" data-step="6">

**Add Breaks and Special Sessions**

Insert non-speaker slots:

```
[+ Add Special Slot]

Type:
[▼ Coffee Break]
   Coffee Break
   Lunch Break
   Networking Reception
   Opening Remarks
   Closing Remarks

Time:
[⏰ 10:45] to [⏰ 11:00]

[Add]
```
</div>

<div class="step" data-step="7">

**Review Complete Schedule**

View final schedule in list format:

```
Final Schedule - BATbern 2025
════════════════════════════════════════════════

09:00-09:45
Track A: Hans Müller - Sustainable Materials
Track B: Anna Schmidt - Digital Transformation

10:00-10:45
Track A: Peter Weber - Urban Planning
Track B: Martin Fischer - Heritage Reuse

10:45-11:00  ☕ Coffee Break

11:00-11:45
Track A: Lisa Meier - Materials Innovation
Track B: Sophie Weber - Smart Buildings

[Continue viewing...]

Total Sessions: 12
Total Duration: 8 hours
Conflicts: 0 ✅

[Export Schedule] [Publish to Phase E]
```
</div>

<div class="step" data-step="8">

**Complete Slot Assignment**

Click **Finalize Schedule**

Event state: QUALITY_REVIEWED → **SLOTS_ASSIGNED**

Phase D complete! ✅ Ready for Phase E (Publishing)
</div>

### Scheduling Best Practices

**Prime Time Slots** (first sessions after breaks):
- Assign popular/high-demand topics
- Use experienced speakers (strong stage presence)

**Parallel Track Strategy**:
- Don't schedule similar topics simultaneously
- Balance technical vs. practical across tracks
- Consider attendee personas (beginners vs. experts)

**Speaker Preferences**:
- Ask speakers for time preferences during outreach
- Avoid early morning for international speakers (jet lag)
- Respect "cannot present after lunch" requests (energy levels)

**Buffer Time**:
- Include 15-minute breaks between sessions
- Allows speaker transitions and attendee movement
- Prevents cascading delays

## Phase D Completion

### Success Criteria

- ✅ Overflow resolved (if applicable)
- ✅ All selected speakers assigned to slots
- ✅ Zero scheduling conflicts
- ✅ Event state = **SLOTS_ASSIGNED**

### What Happens Next

**Phase E: Publishing** begins:
- Progressive agenda publication
- Dropout handling procedures
- Public event page goes live

See [Phase E: Publishing →](phase-e-publishing.md) to continue.

## Troubleshooting Phase D

### "Cannot resolve overflow - tied votes"

**Problem**: Multiple speakers have identical vote averages.

**Solution**:
- Use quality scores (Phase C) as tiebreaker
- Senior organizer makes final call
- Consider expanding slots by 1 (13 instead of 12)

### "Speaker availability conflicts with all slots"

**Problem**: Speaker cannot attend any available time slot.

**Solution**:
- Mark as DROPOUT (unavoidable conflict)
- Promote waitlist speaker
- Adjust schedule if speaker is critical

### "Parallel tracks extremely imbalanced"

**Problem**: One track much more popular than other.

**Solution**:
- Use auto-balance feature
- Manually redistribute topics
- Consider single-track format if balance impossible

## Related Topics

- [Phase C: Quality →](phase-c-quality.md) - Previous phase
- [Phase E: Publishing →](phase-e-publishing.md) - Next phase
- [Event Management →](../entity-management/events.md) - Event sessions

## API Reference

```
POST /api/events/{id}/workflow/step-9        Complete Step 9 (Overflow Mgmt)
POST /api/events/{id}/workflow/step-10       Complete Step 10 (Slot Assignment)
POST /api/events/{id}/voting                 Submit speaker votes
PUT  /api/events/{id}/sessions/{sid}/assign  Assign speaker to slot
GET  /api/events/{id}/schedule/conflicts     Check scheduling conflicts
```

See [API Documentation](../../api/) for complete specifications.
