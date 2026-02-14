---
stepsCompleted: [1, 2, 3, 4, 5, 6]
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

## Desired Emotional Response

### Primary Emotional Goals

**Invisible Support** — The Watch should feel like a trusted stage manager working silently behind the curtain. Organizers should never feel they're "operating an app." The technology recedes; confidence rises. The dominant feeling is: *"I don't have to think about timing — it's handled."*

**Team Unity** — Every organizer should feel they are part of one synchronized organism. No one is out of the loop. No one needs to ask "where are we?" The Watch creates a shared awareness that feels like telepathy between the 4 team members.

### Emotional Journey Mapping

| Event Phase | Desired Emotion | What Creates It |
|---|---|---|
| Pre-event setup | Calm confidence | Green dots — all watches connected, schedule loaded |
| During a talk (passive) | Relaxed awareness | Complication quietly counting down, no action needed |
| Haptic alert (5 min / 2 min) | Gentle nudge, not anxiety | Firm but not startling buzz — information, not alarm |
| Session transition | Effortless flow | One tap "Done," next speaker info appears instantly |
| Speaker overrun | Control, not panic | Clear overrun display + one-tap cascade = "I've got this" |
| Break gong reminder | Reliable support | Buzz arrives exactly when needed — "the Watch remembered so I didn't have to" |
| Post-event | Quiet pride | "We ran that smoothly — together" |

### Micro-Emotions

- **Confidence over Confusion** — At every glance, the Watch shows exactly one thing: what matters right now. No menus, no ambiguity.
- **Trust over Skepticism** — Haptics fire precisely on time. Cascade propagates instantly. The system earns trust through reliability.
- **Accomplishment over Frustration** — Every transition feels like a small win. "Done" tap → schedule advances → next speaker ready. Momentum builds.
- **Belonging over Isolation** — Synchronized state means no organizer is ever alone. Sarah on the floor knows exactly what Marco on stage knows.

### Design Implications

- **Invisible Support → Minimal UI**: No chrome, no navigation bars, no settings during events. The Watch face IS the app for 90% of usage.
- **Team Unity → Instant sync feedback**: When one organizer taps "Done," all watches update within seconds — reinforcing the feeling of shared control.
- **Confidence → Large, clear typography**: Countdown numbers must be readable in a split-second wrist glance. No squinting, no parsing.
- **Trust → Consistent haptic language**: Same buzz pattern = same meaning, every time. The body learns to trust the vocabulary.
- **No anxiety → Gentle escalation**: Haptic alerts inform, they don't startle. The overrun display is factual ("+2:00 over"), not alarming.

### Emotional Design Principles

1. **The Watch is a teammate, not a tool** — Design every interaction as if a calm, competent colleague is whispering updates in your ear.
2. **Silence is the default emotion** — The best emotional state is "I forgot the Watch was there." Only surface information at moments that matter.
3. **Shared knowing beats shared talking** — The emotional payoff of team unity comes from NOT needing to communicate verbally. The buzz says it all.
4. **Stress absorption, not stress creation** — Every feature must reduce cognitive load. If a feature could create anxiety (e.g., overrun warnings), design it to feel like support, not pressure.
5. **Pride through flow** — When an event runs smoothly, every organizer should feel they contributed to that flow — the Watch enabled it quietly.

## UX Pattern Analysis & Inspiration

### Inspiring Products Analysis

**Apple Workout App (watchOS)** — The gold standard for "glance, act, forget" Watch UX. During a run, the always-on display shows elapsed time, heart rate, and pace — no interaction needed. The Digital Crown locks to prevent accidental taps. The end-workout flow is a single swipe + tap. BATbern Watch should feel this natural during an event — passive display, minimal interaction, always legible.

**Apple Timer / Stopwatch** — Proves that a countdown is the most natural Watch complication. Large digits, always visible, haptic on completion. BATbern's session countdown should feel exactly this native — as if Apple built it into watchOS. The lesson: don't reinvent the countdown UI. Match the system aesthetic.

**Walkie-Talkie (watchOS)** — Apple's team communication app on Watch. One tap to talk, instant connection between two people. The key UX insight: team awareness on a Watch works when it's radically simple. BATbern Watch extends this concept — instead of voice, the "communication" is synchronized state. The same design principle applies: one action, instant team effect.

**Broadcast Production Countdown Monitors** — In TV studios, stage managers use large countdown displays visible from across the room. Red at 30 seconds, flashing at zero. BATbern Watch is the wrist-sized version of this production tool. The lesson: color-coded time states (green → yellow → red → overrun) are an established pattern that works under pressure.

### Transferable UX Patterns

**Navigation Patterns:**
- **Complication-first architecture** (from Workout) — The Watch face complication IS the primary interface. The app is secondary. 90% of usage never opens the full app.
- **Context-aware display** (from Workout) — What's shown depends on state. During a run = metrics. Paused = resume button. Applied to BATbern: during talk = countdown, between talks = next speaker, during break = gong timer.

**Interaction Patterns:**
- **Single-action completion** (from Timer) — Tap to stop. No confirmation dialog. For BATbern: tap "Done" to advance. The system trusts the user.
- **Haptic vocabulary** (from Workout) — Workout uses distinct haptic patterns for start, lap, goal reached. BATbern extends this: 5-min nudge, 2-min warning, time's up, overrun pulse.
- **Digital Crown for scrolling** (from watchOS system) — Natural scroll through upcoming schedule items. No swipe gestures competing with tap targets.

**Visual Patterns:**
- **Large monospace countdown digits** (from Timer/Stopwatch) — Time remaining must be the dominant visual element. SF Mono or system monospace, filling available width.
- **Color-coded urgency** (from broadcast monitors) — Subtle color shifts as time decreases. Not jarring — ambient awareness through color temperature.
- **Circular progress** (from Workout rings) — A circular complication gauge showing session progress feels native to watchOS and communicates "how far along" at a glance.

### Anti-Patterns to Avoid

- **List-heavy navigation** — Many Watch apps try to be phone apps on a tiny screen. Scrolling through menus kills glanceability. BATbern must avoid any multi-level navigation during events.
- **Confirmation dialogs for common actions** — "Are you sure you want to mark as done?" adds friction at the worst moment. Reserve confirmations only for cascade (which changes the schedule for everyone).
- **Text-heavy displays** — Any screen showing more than 3 lines of text has failed the Watch UX test. Speaker names may need truncation. Talk titles should be secondary to the countdown.
- **Aggressive haptics** — Over-buzzing trains users to ignore alerts (notification fatigue). Each haptic pattern must be earned — only fire at genuinely meaningful moments.
- **Custom UI controls** — Non-standard buttons, sliders, or gestures feel foreign on watchOS. Use system controls exclusively. The app should feel like it belongs on the Watch, not bolted on.

### Design Inspiration Strategy

**Adopt directly:**
- Apple Timer's countdown display aesthetic — large digits, always-on, system fonts
- Workout's complication-first architecture — the Watch face does 90% of the work
- Walkie-Talkie's one-tap team action model — tap once, everyone is updated

**Adapt for BATbern:**
- Workout's context-aware display — customize for event states (talk, break, transition, overrun)
- Broadcast countdown color coding — translate to watchOS-native color palette (green/yellow/red)
- Circular progress complication — show session elapsed as a ring gauge within the complication

**Avoid entirely:**
- Phone-style navigation (tab bars, hamburger menus, deep hierarchies)
- Custom gesture systems (stick to tap and Digital Crown)
- Information density from larger screens — ruthlessly prioritize what earns screen space

## Design System Foundation

### Design System Choice

**Apple Human Interface Guidelines (watchOS) + Native SwiftUI Components** — The only design system that makes sense for a standalone watchOS app. SwiftUI provides all the building blocks: `NavigationStack`, system buttons, `Gauge` for circular progress, `TimelineView` for countdowns, and WidgetKit for complications. No third-party component libraries needed or recommended.

### Rationale for Selection

- **Platform mandate** — watchOS apps that don't follow HIG feel alien and get rejected from App Store review. Apple's design system IS the design system.
- **Complication-first architecture** — WidgetKit complications are the primary interface. These are 100% Apple-native — no design system choice to make.
- **Haptic patterns** — `WKInterfaceDevice` haptic types are predefined by Apple. The vocabulary is: `.notification`, `.directionUp`, `.directionDown`, `.success`, `.failure`, `.retry`, `.start`, `.stop`, `.click`. We compose from these.
- **Accessibility built in** — VoiceOver, Dynamic Type, and reduced motion come free with native SwiftUI controls.
- **Performance** — Native components render at 60fps on Watch hardware. Third-party frameworks add overhead on a constrained device.

### Implementation Approach

- **SwiftUI declarative views** for all screens (session view, schedule list, cascade confirmation)
- **WidgetKit** for Watch face complications (countdown, next speaker, event status)
- **SF Symbols** for all iconography (system-consistent, scalable, accessible)
- **SF Pro / SF Mono** system fonts — SF Mono for countdown digits, SF Pro for labels and names
- **System colors with semantic meaning** — `.green` (on track), `.yellow` (warning), `.red` (overrun), mapped to watchOS dynamic color system for always-on display dimming

### Customization Strategy

**What we customize within HIG:**
- **Color palette** — Map BATbern's event states to system colors (on-time = green accent, warning = yellow, overrun = red)
- **Haptic sequences** — Combine Apple's base haptic types into distinguishable multi-pulse patterns (e.g., 5-min = single firm tap, 2-min = double tap, time's up = triple tap, overrun = rhythmic pulse)
- **Complication layout** — Use WidgetKit's `AccessoryRectangular` and `AccessoryCircular` families with custom content (speaker name + countdown + progress ring)
- **Typography scale** — Large countdown digits (SF Mono, ~40pt), medium speaker name (SF Pro, ~16pt), small secondary info (SF Pro, ~12pt)

**What we do NOT customize:**
- Navigation patterns (use system `NavigationStack`)
- Button styles (use system `.borderedProminent`)
- Scroll behavior (use system Digital Crown integration)
- Alert presentation (use system `.alert` modifier)
