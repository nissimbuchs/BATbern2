---
stepsCompleted: [1, 2, 3]
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

## Core User Experience

### Defining Experience

The BATbern Watch experience is built around a single loop: **glance → feel → tap**.

- **Glance:** Raise wrist, see countdown + speaker + what's next. No interaction needed.
- **Feel:** Haptic buzz tells you something changed (5 min, 2 min, time's up, gong). No looking needed.
- **Tap:** One tap to advance. One tap to cascade. That's it.

90% of usage is passive — the complication does the work. 10% is active — a single tap at session transitions. The app succeeds when you forget it's there until the exact moment you need it.

### Platform Strategy

- **Platform:** watchOS 10+ standalone app (no iPhone companion)
- **Primary input:** Touch (single tap), Digital Crown (scroll)
- **Primary output:** Visual (complication, always-on display) + Haptic (Taptic Engine)
- **No text input** — ever. No keyboards, no dictation, no typing.
- **Offline-first:** Full schedule cached locally. WiFi is for sync, not for function.
- **Context-aware display:** What the Watch shows depends on event state (during talk, between talks, during break)

### Effortless Interactions

| Interaction | Effort Level | How |
|---|---|---|
| Know time remaining | Zero — passive glance | Always-on complication |
| Know who's next | Zero — passive glance | Complication sub-display |
| Get timing warnings | Zero — body feels it | Haptic patterns |
| Advance to next session | One tap | "Done" button |
| Handle overrun | Two taps | "Done" → "Shift +N min" |
| See full schedule | Turn Digital Crown | Scrollable list |
| Connect to event | One-time setup | Login + select event |

### Critical Success Moments

1. **First haptic during a live talk** — The moderator feels the 5-min buzz while speaking and instinctively knows what it means without looking down. The haptic vocabulary works.

2. **First "Done" tap** — Marco taps Done, all watches update, he glances at the next speaker's name and portrait, and introduces them confidently. The system works end-to-end.

3. **First schedule cascade** — A speaker overruns, Marco shifts everything by 5 minutes with one tap, and the other 3 organizers' watches update silently. No verbal coordination needed.

4. **First gong reminder** — Sarah is chatting with a sponsor, her wrist buzzes, she excuses herself and rings the gong right on time. The Watch carried the cognitive load.

### Experience Principles

1. **Invisible until essential** — The Watch disappears during talks. No distractions, no notifications, no visual noise. It only demands attention at transition moments.

2. **Body over eyes** — Prefer haptic communication over visual. The moderator shouldn't need to look at the Watch to know the time state. The buzz IS the information.

3. **One tap, one action** — Every interaction completes in a single tap (normal case). The cascade flow is the only multi-step interaction, and even that is two taps.

4. **Synchronized by default** — Any action by any organizer propagates to all. There is no "out of sync" state during normal operation. The team shares one reality.

5. **Degrade gracefully, never fail** — If WiFi drops, the Watch still counts down. If one organizer doesn't have a Watch, the others still work. Nothing is all-or-nothing.
