---
stepsCompleted: [1, 2]
inputDocuments:
  - _bmad-output/planning-artifacts/prd.md
  - _bmad-output/planning-artifacts/product-brief-BATbern-2026-02-14.md
---

# UX Design Specification BATbern

**Author:** Nissim
**Date:** 2026-02-14

---

## Executive Summary

### Project Vision

BATbern Watch is a standalone watchOS companion app that transforms the Apple Watch into a real-time event operations tool for a 4-person organizer team. It replaces printed schedules and mental clock math with glanceable countdowns, haptic alerts, and one-tap event control — enabling moderators to run entire conferences from their wrist without touching a phone.

### Target Users

**Moderator (Power User):** The organizer on stage — presenting, managing Q&A, introducing speakers. Cannot use a phone. Needs countdown visibility, speaker info for introductions, and session advance — all from wrist glances. The Watch must be invisible until needed, then instantly useful.

**Floor Organizer (3 others):** Organizers on the floor — greeting attendees, managing logistics, talking to sponsors. The Watch is a background awareness tool that buzzes at the right moments. They need passive schedule awareness and synchronized state without checking in with the moderator.

### Key Design Challenges

1. **Extreme screen real estate** — Apple Watch displays ~2-3 lines max. Speaker name + title + countdown + portrait must coexist on a 44mm screen. Every pixel must earn its place.

2. **Glanceability vs. actionability tension** — Most of the time the Watch is passive (complication showing countdown). At key moments it must accept input (tap "Done", confirm cascade). Switching between passive display and active input without fumbling is the core UX challenge.

3. **Haptic vocabulary design** — 4+ distinct haptic patterns needed (5-min, 2-min, time's up, overrun, gong reminder). Must be distinguishable without looking at the screen — a non-visual "language of vibrations."

4. **Schedule cascade interaction** — The most complex Watch interaction: "Done" → overrun confirmation → choose shift increment → confirm. 3 steps on a tiny screen during a high-stress moment. Must be fast and error-proof.

### Design Opportunities

1. **Haptics as primary interface** — Communication through the body, not the eyes. A moderator mid-sentence feels a buzz and instinctively knows "5 minutes left" without breaking eye contact with the audience. Tactile UX paradigm.

2. **Always-on complication as ambient awareness** — The Watch face complication can be the most-used "screen" — always visible, never requires interaction. Replaces an entire app for 90% of usage time.

3. **Context-aware wrist-raise display** — During a talk = countdown. Between talks = next speaker brief. During break = gong countdown. The Watch shows exactly what the organizer needs based on event state.
