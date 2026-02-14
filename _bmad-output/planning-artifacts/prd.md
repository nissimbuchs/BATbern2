---
stepsCompleted: [step-01-init, step-02-discovery, step-03-success, step-04-journeys, step-05-domain, step-06-innovation, step-07-project-type]
inputDocuments:
  - _bmad-output/planning-artifacts/product-brief-BATbern-2026-02-14.md
  - _bmad-output/analysis/brainstorming-session-2026-02-14.md
  - docs/brainstorming-session-results.md
documentCounts:
  briefs: 1
  research: 0
  brainstorming: 2
  projectDocs: multiple
workflowType: 'prd'
projectType: brownfield
classification:
  projectType: mobile_app
  domain: event_management
  complexity: medium
  projectContext: brownfield
---

# Product Requirements Document - BATbern

**Author:** Nissim
**Date:** 2026-02-14

## Success Criteria

### User Success

- The app feels supportive, not fiddly — large tap targets, glanceable information, no text walls
- Moderator never does mental clock math on stage again — countdown is always visible
- Rescheduling after an overrun takes one tap, not a huddle with co-organizers
- Break gong is never forgotten — haptics carry the cognitive load
- All 4 organizers see identical event state without verbal coordination

### Business Success

- All 4 organizers adopt the Watch app and use it at every BATbern event (spring + autumn)
- Paper schedules eliminated from the organizer workflow
- The app is an internal team tool — no external user expansion required for MVP success

### Technical Success

- Watch app connects directly to BATbern backend — no iPhone companion app required (standalone watchOS app)
- Real-time state sync across all 4 watches is reliable throughout a 3-hour event
- Battery consumption allows full event coverage without mid-event charging

### Measurable Outcomes

- 100% of organizer timing actions handled by Watch (zero phone pulls for schedule management)
- Schedule cascade completes in under 3 seconds across all watches
- Haptic alerts delivered within 1 second of scheduled time

## Product Scope

### MVP — Minimum Viable Product

- **LIVE-1:** Always-on schedule complication (current talk, speaker name, countdown)
- **LIVE-2:** Haptic cue system (5min / 2min / time's up — all watches buzz simultaneously)
- **LIVE-5:** Live schedule cascade (one-tap reschedule when speaker overruns)
- **LIVE-6:** Break gong reminder (haptic at 5 min before break ends)
- **LIVE-8:** Session complete (tap "Done" to advance schedule for all organizers)
- **SYNC-1:** Shared state across all 4 watches via backend sync
- **Standalone:** Direct backend connection — no iPhone companion app dependency

### Growth Features (Post-MVP)

- **PRE-1:** Speaker arrival tracking (shared across organizers)
- **LIVE-7:** Speaker time signal/flash (discreet signal to speaker)
- **SYNC-3:** Quick ping between organizers (silent wrist buzz)
- **LIVE-3:** Next-up speaker notification (auto-ping "You're on in 10 minutes")

### Vision (Future)

- **LIVE-4:** Attendee count pulse (live check-in count on wrist)
- Speaker-facing Watch complication (countdown for the speaker themselves)
- Attendee-facing live schedule on Watch

## User Journeys

### Journey 1: Marco the Moderator — Evening Event (Happy Path)

Marco is one of the 4 BATbern organizers and tonight he's moderating. It's 17:45, fifteen minutes before doors open. He raises his wrist — the Watch face shows the BATbern complication: **"Event starts in 15 min | First: Anna Meier — Cloud-Native Pitfalls"**.

18:00 — Marco welcomes the room. He glances at his wrist: the countdown is running. **24:12 remaining**. He doesn't think about time. He listens to Anna's talk, watches the audience.

At **5 minutes remaining**, a firm haptic buzz hits his wrist. Only he feels it. He knows it's time to start thinking about the transition. At **2 minutes**, another buzz. He prepares his notes for the Q&A wrap-up. At **0:00**, the final buzz. He stands, thanks Anna, and opens a quick Q&A.

Q&A wraps. Marco taps **"Done"** on his Watch. The schedule advances. His wrist now shows: **"Next: Thomas Keller — Zero Trust Architecture | 5 min break"**. All 4 organizers' watches update simultaneously. He glances down, reads the speaker name and title, and introduces Thomas without touching a piece of paper.

The evening flows. Three talks, one break (the gong reminder buzzes his wrist at 15 minutes — he rings the bell). At 21:00, Marco taps "Done" on the final session. The event is complete.

**What Marco never did:** pull out his phone, do clock math, check a printed schedule, or ask a co-organizer "who's next?"

### Journey 2: Sarah the Floor Organizer — Supporting from the Room

Sarah is another organizer. Tonight Marco is moderating, so she's on the floor — greeting late arrivals, checking on the catering setup, talking to a sponsor.

She's deep in conversation with a partner representative when her wrist buzzes: **5 minutes remaining** on the current talk. She wraps the conversation naturally — "I should head back, the next speaker is up soon."

She walks to the side of the room. Her Watch shows the same countdown Marco sees: **4:32 remaining**. She spots Thomas Keller near the coffee — she walks over and quietly says "You're up next, Thomas."

When Marco taps "Done," Sarah's Watch advances too. She sees the break starting and the gong countdown begin. She doesn't need to check with Marco or look at a printout. She *knows* the state of the event at all times.

**What Sarah never did:** ask Marco "where are we in the schedule?", check her phone for the agenda, or lose track of time while talking to sponsors.

### Journey 3: Marco the Moderator — Speaker Overrun (Edge Case)

It's the second talk. The speaker is deep into a live demo that's captivating the audience. Marco's wrist buzzes at **0:00** — time's up. But the demo is landing perfectly. He decides to let it run.

At **+2 minutes**, another haptic pattern — slightly different, more urgent. The Watch shows **"+2:00 over"** in a distinct color. Marco makes a judgment call — two more minutes.

At **+4:00**, Marco wraps the Q&A. He taps **"Done"** and the Watch asks: **"Session ran +4 min over. Shift remaining schedule?"** He taps **"Shift +5 min"** (rounding up for buffer).

Instantly, all 4 watches update. The break that was 20 minutes is now 20 minutes starting 5 minutes later. The gong reminder recalculates. The final talk's start time shifts. One tap, everything cascades.

The audience notices nothing. The other 3 organizers don't need to be told — their watches already show the new timeline.

**What Marco never did:** whisper to a co-organizer "we're running late, push everything back 5 minutes", recalculate the schedule mentally, or panic about the domino effect.

### Journey 4: Pre-Event Setup

It's 17:00 on event day. Nissim opens the BATbern web app on his laptop and activates tonight's event for Watch sync. The event's agenda, speaker names, and timing are already in the system from weeks of preparation.

He raises his Apple Watch — it connects directly to the BATbern backend over the venue WiFi. The complication populates: tonight's event, first speaker, start time. He checks — all 4 organizers show as connected. Green dots. They're ready.

No iPhone tethering. No Bluetooth pairing with a phone. The Watch talks directly to the backend. Simple.

### Journey Requirements Summary

| Journey | Capabilities Revealed |
|---|---|
| Marco (Happy Path) | Always-on complication, haptic cue system, session advance, speaker info display |
| Sarah (Floor) | Shared real-time state, passive schedule awareness, same haptic cues |
| Marco (Overrun) | Overrun detection, escalating haptics, schedule cascade with confirmation, auto-recalculation |
| Pre-Event Setup | Direct backend connection, multi-organizer presence detection, event activation |

## Innovation & Novel Patterns

### Detected Innovation Areas

1. **Watch as Team Coordination Tool** — Most Apple Watch apps are single-user personal tools. BATbern Watch is a multi-person real-time operations tool where 4 watches act as one synchronized system. Haptics serve as a silent team communication channel invisible to the audience.

2. **Standalone Event Control Surface** — Unlike typical Watch apps that act as phone companions, BATbern Watch connects directly to the backend with no iPhone dependency. The Watch becomes an independent operations terminal for a moderator who cannot touch any other device while on stage.

3. **The "Event Conductor" Pattern** — The Watch is not passive (just displaying info). It is an active control surface — tap "Done" to advance the event state, tap to cascade the entire schedule. One wrist drives the event forward for all stakeholders simultaneously.

### Validation Approach

- Pilot at one BATbern evening event with all 4 organizers wearing Apple Watches
- Measure: did any organizer pull out their phone for schedule management during the event?
- Measure: did the moderator successfully use Watch-only for all transitions and introductions?
- Post-event debrief: "would you go back to paper?"

### Risk Mitigation

- **WiFi failure at venue:** Watch app should cache the full event schedule locally on launch — if connectivity drops, countdown and haptics still work (only real-time sync between watches degrades)
- **Adoption friction:** If one organizer doesn't have an Apple Watch, the system must still work for the remaining 3 — graceful degradation, not all-or-nothing

## watchOS App Specific Requirements

### Project-Type Overview

Native Apple Watch (watchOS) standalone app built with SwiftUI. Targets watchOS 10+ for latest complication and connectivity APIs. No iPhone companion app required — direct backend communication over WiFi. Distributed via App Store.

### Platform Requirements

- **Target:** Apple Watch Series 6+ (watchOS 10+) — needed for always-on display and reliable standalone networking
- **Framework:** SwiftUI with watchOS app lifecycle
- **Distribution:** App Store
- **Authentication:** Organizer logs in once during setup; session persists for event duration

### Device Features

| Feature | Usage |
|---|---|
| **Taptic Engine** | Haptic cue system — escalating patterns for 5min/2min/time's up/overrun |
| **Complications** | Always-on display showing current talk, speaker, countdown |
| **Digital Crown** | Scroll through upcoming schedule items |
| **Always-On Display** | Persistent countdown visible without wrist raise |
| **Speaker Portraits** | Display speaker photo alongside name and talk title for face recognition |

### Offline Mode

- Cache full event schedule (speakers, times, agenda, portrait images) on initial sync
- If WiFi drops: local countdown and haptics continue uninterrupted
- Real-time team sync degrades gracefully — resumes on reconnect
- Schedule cascade actions queue locally and sync when connectivity returns

### Push & Sync Strategy

- WebSocket connection to BATbern backend for real-time state sync between watches
- Fallback to polling if WebSocket unavailable
- Push notifications (APNs) as backup channel for critical state changes (session advance, schedule cascade)

### Implementation Considerations

- **Battery:** Minimize active networking; use complication updates via WidgetKit timeline
- **Screen size:** Maximum 2-3 lines of text visible at any time — design for glanceability
- **Input:** Large tap targets only; no text input on Watch; no tiny buttons
- **Backend integration:** New REST/WebSocket endpoints on existing BATbern API Gateway for Watch-specific event state
