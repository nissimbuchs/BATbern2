# Phase E: Publishing (Steps 11-12)

> Progressively publish agenda and handle last-minute changes

<div class="workflow-phase phase-e">
<strong>Phase E: Publishing</strong><br>
Status: <span class="feature-status planned">Planned</span><br>
Duration: 2-4 weeks<br>
State Transitions: SLOTS_ASSIGNED → PUBLISHED → FINALIZED
</div>

## Overview

Phase E makes the event public by progressively releasing information, then locks the final agenda while handling any last-minute speaker dropouts.

**Key Deliverable**: Public agenda with confirmed speakers and finalized schedule

## Step 11: Progressive Publishing

<span class="feature-status planned">Planned</span>

### Purpose

Gradually release event information to build anticipation while allowing flexibility for minor adjustments.

### Acceptance Criteria

- ✅ Event landing page published with basic info
- ✅ Topic list published (without speaker names initially)
- ✅ Speaker profiles published
- ✅ Complete agenda published with schedule
- ✅ Event state = **PUBLISHED**

### Publishing Strategy

**Progressive Release Timeline**:

| When | What Published | Why |
|------|----------------|-----|
| **4 weeks before** | Event date, venue, registration | Open registration early |
| **3 weeks before** | Topic list | Build interest, no speaker commitment yet |
| **2 weeks before** | Speaker names and bios | Confirm speakers before announcing |
| **1 week before** | Complete schedule | Final agenda with time slots |

**Benefits**:
- Speakers have time to withdraw before public announcement
- Organizers can adjust topics if needed
- Marketing can build gradually
- Reduces last-minute changes to published agenda

### How to Complete

<div class="step" data-step="1">

**Stage 1: Event Basics** (4 weeks before)

Publish core event information:

```
[Publish Event Basics]

Public Information:
✅ Event name: "BATbern 2025"
✅ Date: March 15, 2025
✅ Time: 9:00 - 18:00
✅ Venue: Kursaal Bern
✅ Event type: Full-Day Conference
✅ Registration link: Active

Hidden:
⏸️ Topics (not yet published)
⏸️ Speakers (not yet published)
⏸️ Schedule (not yet published)

[Publish to Website]
```

Event landing page goes live at: `https://www.batbern.ch/events/batbern-2025`
</div>

<div class="step" data-step="2">

**Stage 2: Topics** (3 weeks before)

Publish selected topics:

```
[Publish Topics]

Topics for BATbern 2025:
✅ Sustainable Building Materials
✅ Digital Transformation in Architecture
✅ Urban Planning Innovations
✅ Heritage Adaptive Reuse
... (8 more)

Speakers: "Coming soon - Stay tuned!"

[Publish Topics]
```

Topics appear on event page, no speaker names yet.
</div>

<div class="step" data-step="3">

**Stage 3: Speakers** (2 weeks before)

Publish speaker profiles:

```
[Publish Speaker Profiles]

Confirm all speakers before publishing:
✅ Hans Müller - Last confirmation: 2 days ago
✅ Anna Schmidt - Last confirmation: 1 day ago
✅ Peter Weber - Last confirmation: 3 days ago
... (9 more)

⚠️ All speakers must confirm availability within 7 days

[Publish All Speakers]
```

Speaker directory goes live with photos, bios, and session titles.
</div>

<div class="step" data-step="4">

**Stage 4: Complete Agenda** (1 week before)

Publish full schedule:

```
[Publish Complete Agenda]

Full Schedule:
────────────────────────────────────────────────
09:00-09:45
Track A: Hans Müller - Sustainable Materials
Track B: Anna Schmidt - Digital Transformation

10:00-10:45
Track A: Peter Weber - Urban Planning
Track B: Martin Fischer - Heritage Reuse

[Continue for all sessions...]

[Publish Full Agenda]
```

Complete schedule with time slots appears on event page.

Event state: SLOTS_ASSIGNED → **PUBLISHED**
</div>

### Website Preview

Public event page shows:

```
════════════════════════════════════════════════
BATbern 2025
Full-Day Conference | March 15, 2025 | Kursaal Bern
════════════════════════════════════════════════

[Register Now] - 45 days until event

────────────────────────────────────────────────
About This Event

Join 300+ architects for a full day of insights on...

Topics:
• Sustainable Building Materials
• Digital Transformation
• Urban Planning Innovations
• Heritage Adaptive Reuse
... and 8 more

────────────────────────────────────────────────
Speakers (12)

[Photo] Hans Müller
        Müller Architekten AG
        "Innovations in Sustainable Building Materials"
        [View Profile]

[Photo] Anna Schmidt
        Schmidt & Partner
        "Digital Transformation in Architecture"
        [View Profile]

... (10 more speakers)

────────────────────────────────────────────────
Schedule

09:00-09:45  Opening Sessions
[Track A] Hans Müller - Sustainable Materials
[Track B] Anna Schmidt - Digital Transformation

10:00-10:45  Morning Sessions
[Track A] Peter Weber - Urban Planning
[Track B] Martin Fischer - Heritage Reuse

[View Full Schedule]

────────────────────────────────────────────────
[Register Now]
````
</div>

### Publishing Notifications

<span class="feature-status planned">Planned</span>

Notify stakeholders at each stage:

**Registration Opened** (Stage 1):
- Email to all users: "Registration now open!"
- Social media announcement
- Partner notification

**Topics Announced** (Stage 2):
- Email to registered attendees
- Social media teaser
- Blog post

**Speakers Revealed** (Stage 3):
- Email to all with speaker highlights
- Social media speaker spotlights (1 per day)
- Press release

**Full Agenda Published** (Stage 4):
- Final email with complete schedule
- Downloadable PDF agenda
- Mobile app update (if available)

## Step 12: Finalization with Dropout Handling

<span class="feature-status planned">Planned</span>

### Purpose

Lock the final agenda while managing any last-minute speaker withdrawals.

### Acceptance Criteria

- ✅ All speakers reconfirmed within 1 week of event
- ✅ Dropout procedures executed (if needed)
- ✅ Agenda finalized and locked
- ✅ Event state = **FINALIZED**

### Reconfirmation Process

<div class="step" data-step="1">

**Send Reconfirmation Request** (1 week before)

Email all speakers:

```
To: hans.mueller@example.com
Subject: Final Confirmation - BATbern 2025 (1 week away!)

Hi Hans,

BATbern 2025 is one week away! Please confirm you're ready:

Your Session:
- Date: March 15, 2025
- Time: 09:00-09:45 (Track A)
- Topic: "Innovations in Sustainable Building Materials"
- Duration: 45 minutes (30 min talk + 15 min Q&A)

Please confirm by clicking:
[Yes, I'm Ready!] [I Need to Withdraw]

If confirming, please also confirm:
☐ Presentation slides ready (send by March 13)
☐ Technical requirements submitted
☐ Travel/accommodation arranged

See you next week!
Anna Schmidt
```
</div>

<div class="step" data-step="2">

**Track Confirmations**

Monitor response status:

```
Final Confirmation Status
────────────────────────────────────────
Deadline: March 8, 2025 (7 days before event)

Confirmed: 10 / 12 (83%) ✅
Pending: 2 / 12 (17%) ⚠️
  - Peter Weber (last contact: 3 days ago)
  - Lisa Meier (last contact: 5 days ago)

Dropout: 0

[Send Reminder to Pending]
```
</div>

<div class="step" data-step="3">

**Handle Dropouts** (if occur)

If speaker withdraws after publication:

```
⚠️ Dropout Alert

Martin Fischer withdrew (March 8, 2025)
Session: Heritage Adaptive Reuse (11:00-11:45 Track B)
Reason: "Family emergency"

Impact: 1 session affected, 1 week before event

Options:
1. [Promote Waitlist Speaker]
   Sophie Keller - Similar topic available

2. [Reassign Existing Speaker]
   Hans Müller could present second session

3. [Cancel Session]
   Reduce to 11 sessions, adjust schedule

4. [Merge Sessions]
   Combine with similar topic, extend duration

[Choose Option]
```
</div>

<div class="step" data-step="4">

**Option 1: Promote Waitlist Speaker**

Contact waitlist speaker immediately:

```
To: sophie.keller@example.com
Subject: URGENT: Speaker Slot Available - BATbern 2025

Hi Sophie,

A speaker slot has opened for BATbern 2025 (March 15).

Session Details:
- Topic: Heritage Adaptive Reuse
- Time: 11:00-11:45 (Track B)
- Notice: 6 days (expedited preparation)

Your waitlisted presentation "Circular Economy in Construction"
fits well. Are you available?

Please respond within 24 hours.

Urgently,
Anna Schmidt
```

If accepted:
- Update agenda immediately
- Notify registered attendees of change
- Update printed materials if not yet produced
</div>

<div class="step" data-step="5">

**Option 2/3/4: Adjust Schedule**

If no replacement available:

**Cancel Session**:
- Update online agenda
- Send attendee notification
- Adjust printed programs
- Offer partial refund if significant change

**Reassign or Merge**:
- Adjust affected speaker's content
- Update agenda and notify attendees
- Ensure quality maintained
</div>

<div class="step" data-step="6">

**Finalize Agenda**

Once all speakers confirmed and dropouts resolved:

```
[Finalize Agenda]

Final Status:
────────────────────────────────────────
Total Sessions: 12
Confirmed Speakers: 12 ✅
Dropouts Resolved: 1 (Sophie Keller replacement) ✅
Last Change: March 8, 2025

Lock agenda? After locking:
- No further changes allowed
- Printed materials can be produced
- Final attendee notification sent

[Lock Agenda] [Cancel]
```

Click **Lock Agenda**

Event state: PUBLISHED → **FINALIZED**

Phase E complete! ✅ Ready for Phase F (Execution)
</div>

### Dropout Communication

**To Registered Attendees**:
```
Subject: Schedule Update - BATbern 2025

Hi [Attendee],

A schedule update for BATbern 2025:

Changed Session:
- Was: Martin Fischer - "Heritage Adaptive Reuse"
- Now: Sophie Keller - "Circular Economy in Construction"
- Time: 11:00-11:45 Track B (unchanged)

All other sessions remain as published.

See updated schedule: [link]

Thank you for your understanding!
BATbern Team
```

**To Dropout Speaker**:
```
Subject: Thank You - BATbern 2025

Hi Martin,

Thank you for letting us know about your situation.
We completely understand and hope everything is okay.

We'd love to have you present at a future BATbern event.
I'll reach out in a few months about BATbern 2026.

Best wishes,
Anna
```

## Phase E Completion

### Success Criteria

- ✅ Event fully published in progressive stages
- ✅ All speakers reconfirmed
- ✅ Dropouts resolved (if any)
- ✅ Agenda finalized and locked
- ✅ Event state = **FINALIZED**

### What Happens Next

**Phase F: Communication** begins:
- Newsletter distribution
- Moderator assignments
- Final logistics (catering, etc.)
- Post-event archival preparation

See [Phase F: Communication →](phase-f-communication.md) to continue.

## Troubleshooting Phase E

### "Speaker drops out after finalization"

**Problem**: Speaker withdraws with <3 days notice.

**Solution**:
- No time for replacement
- Options: Cancel session or extend another session
- Communicate prominently to attendees
- Offer apology and alternative value (extra networking, longer Q&A)

### "Multiple speakers need schedule changes"

**Problem**: 2+ speakers request time slot changes after publishing.

**Solution**:
- Evaluate impact (minor vs. major changes)
- Allow changes only if <2 weeks before event
- After 2 weeks, changes only for emergencies
- Communicate any changes immediately

### "Printed materials already produced with old agenda"

**Problem**: Dropout occurs after programs printed.

**Solution**:
- Insert errata sheet in programs
- Display corrected schedule on venue screens
- Staff announce change at event opening
- Update digital agenda prominently

## Related Topics

- [Phase D: Assignment →](phase-d-assignment.md) - Previous phase
- [Phase F: Communication →](phase-f-communication.md) - Next phase
- [Event Management →](../entity-management/events.md) - Event publishing

## API Reference

```
POST /api/events/{id}/workflow/step-11      Complete Step 11 (Publishing)
POST /api/events/{id}/workflow/step-12      Complete Step 12 (Finalization)
POST /api/events/{id}/publish               Publish event stages
PUT  /api/events/{id}/finalize              Lock final agenda
POST /api/speakers/{id}/reconfirm           Send reconfirmation request
```

See [API Documentation](../../api/) for complete specifications.
