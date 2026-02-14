---
stepsCompleted: [step-01-init, step-02-discovery, step-03-success]
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
