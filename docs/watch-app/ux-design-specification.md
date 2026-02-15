---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
inputDocuments:
  - docs/watch-app/prd-batbern-watch.md
  - _bmad-output/planning-artifacts/product-brief-BATbern-2026-02-14.md
---

# UX Design Specification — BATbern Watch

**Author:** Nissim
**Date:** 2026-02-15
**Version:** 2.0 (Dual-zone architecture — Public Companion + Organizer Command Center)

---

## Executive Summary

### Project Vision

BATbern Watch is a native Apple Watch companion app that transforms the Apple Watch into **two experiences on one wrist**:

**Public Event Companion (Left Zone):** Anyone with an Apple Watch can browse tonight's BATbern event — theme, schedule, session details, speaker bios and portraits — by scrolling with the Digital Crown. No login required.

**Organizer Command Center (Right Zone):** The 4 BATbern organizers swipe right to enter a paired, authenticated zone with live countdown control, haptic-driven time alerts, and team-synchronized session management.

The app uses a horizontal paging model: public zone on the left (default on launch), organizer zone on the right (swipe right). This dual-zone architecture makes the app both App Store-worthy (200+ attendees) and operationally powerful (4 organizer power users).

### Target Users

**Moderator (Power User):** The organizer on stage — presenting, managing Q&A, introducing speakers. Cannot use a phone. Needs countdown visibility, speaker info for introductions, and session advance — all from wrist glances. The Watch must be invisible until needed, then instantly useful.

**Floor Organizer (3 others):** Organizers on the floor — greeting attendees, managing logistics, talking to sponsors. The Watch is a background awareness tool that buzzes at the right moments. They need passive schedule awareness and synchronized state without checking in with the moderator.

**Attendee (200+ per event):** Architects who registered for tonight's event. They installed the app from the App Store and want to browse the schedule from their wrist — what's on now, what's next, who's speaking. No login, no accounts, pure browse. The public zone replaces pulling out a phone or hunting for a PDF program.

### Key Design Challenges

1. **Extreme screen real estate** — Apple Watch displays ~2-3 lines max. Speaker name + title + countdown + portrait must coexist on a 44mm screen. Every pixel must earn its place.

2. **Glanceability vs. actionability tension** — Most of the time the Watch is passive (complication showing countdown). At key moments it must accept input (tap "Done", confirm cascade). Switching between passive display and active input without fumbling is the core UX challenge.

3. **Haptic vocabulary design** — 4+ distinct haptic patterns needed (5-min, 2-min, time's up, overrun, gong reminder). Must be distinguishable without looking at the screen — a non-visual "language of vibrations."

4. **Schedule cascade interaction** — The most complex Watch interaction: "Done" → overrun confirmation → choose shift increment → confirm. 3 steps on a tiny screen during a high-stress moment. Must be fast and error-proof.

5. **Dual-zone coexistence** — Two fundamentally different experiences (passive browse vs. active control) must coexist on the same device, separated by a simple swipe. The public zone must feel complete on its own — not a teaser for the organizer zone. The organizer zone must feel protected — no accidental exposure.

6. **Passwordless authentication** — Organizers must pair their Watch without typing a password on a tiny screen. The pairing code flow (generate on web → enter on Watch) must feel natural and forgettable after the one-time setup.

### Design Opportunities

1. **Haptics as primary interface** — Communication through the body, not the eyes. A moderator mid-sentence feels a buzz and instinctively knows "5 minutes left" without breaking eye contact with the audience. Tactile UX paradigm.

2. **Always-on complication as ambient awareness** — The Watch face complication can be the most-used "screen" — always visible, never requires interaction. Replaces an entire app for 90% of usage time.

3. **Context-aware wrist-raise display** — During a talk = countdown. Between talks = next speaker brief. During break = gong countdown. The Watch shows exactly what the organizer needs based on event state.

4. **Dual-zone as adoption multiplier** — The public zone makes the app useful to 200+ attendees per event, justifying App Store distribution. The organizer zone rides along as a power-user layer. The broad audience feature enables distribution of the niche team tool.

## Core User Experience

### Defining Experience

The BATbern Watch experience is built around two complementary loops:

**Organizer Loop — glance → feel → tap:**
- **Glance:** Raise wrist, see countdown + speaker + what's next. No interaction needed.
- **Feel:** Haptic buzz tells you something changed (5 min, 2 min, time's up, gong). No looking needed.
- **Tap:** One tap to advance. One tap to cascade. That's it.

90% of organizer usage is passive — the complication does the work. 10% is active — a single tap at session transitions.

**Attendee Loop — scroll → tap → read:**
- **Scroll:** Turn Digital Crown to browse sessions. Each session is a full card.
- **Tap:** Tap a title for the abstract, tap a speaker for their bio and portrait.
- **Read:** Quick detail view, then back to browsing.

100% of attendee usage is passive browsing — no actions, no login, no state changes.

The app succeeds when organizers forget it's there until the exact moment they need it, and attendees find it faster than pulling out their phone.

### Platform Strategy

- **Platform:** watchOS 10+ standalone app (no iPhone companion)
- **Primary input:** Touch (single tap), Digital Crown (scroll)
- **Primary output:** Visual (complication, always-on display) + Haptic (Taptic Engine)
- **No text input** — ever. No keyboards, no dictation, no typing.
- **Offline-first:** Full schedule cached locally. WiFi is for sync, not for function.
- **Context-aware display:** What the Watch shows depends on event state (during talk, between talks, during break)

### Effortless Interactions

| Interaction | Effort Level | How | Audience |
|---|---|---|---|
| Know time remaining | Zero — passive glance | Always-on complication | Organizer |
| Know who's next | Zero — passive glance | Complication sub-display | Organizer |
| Get timing warnings | Zero — body feels it | Haptic patterns | Organizer |
| Advance to next session | One tap | "Done" button | Organizer |
| Handle overrun | Two taps | "Done" → "Shift +N min" | Organizer |
| See full schedule | Turn Digital Crown | Scrollable list | Both |
| Pair Watch | One-time setup | Pairing code from web profile | Organizer |
| Browse tonight's event | Zero — just launch | Event Hero → Crown scroll | Attendee |
| Read talk abstract | One tap | Tap session title | Attendee |
| View speaker bio | One tap | Tap speaker portrait | Attendee |
| Switch to organizer zone | One swipe | Swipe right | Organizer |

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

6. **Open by default** — The public zone requires zero setup. No login, no account, no configuration. Launch the app, browse the event. The organizer experience is a swipe deeper — discovered, not demanded.

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

## Defining Core Interaction

### The One-Sentence Experience

**"Your wrist buzzes, you glance down, you tap Done — and your whole team moves forward together."**

This is BATbern Watch's "swipe right" moment. It combines three things no other tool gives a conference moderator: body-level awareness (haptic), instant context (glance), and team-wide control (tap). If we nail this loop, the app succeeds.

### User Mental Model

**Current mental model (without Watch):**
- Moderator checks wall clock → does mental math ("started at 18:05, talk is 25 min, so... 18:30") → checks clock again → whispers to co-organizer → checks printed schedule for next speaker name
- Floor organizer asks moderator "where are we?" or checks phone for schedule PDF
- Break timing relies on someone remembering to check the clock

**Target mental model (with Watch):**
- The Watch knows. I don't need to think about time — it will tell me.
- When my wrist buzzes, I know what it means without looking.
- When I tap Done, everyone knows. No coordination needed.
- The schedule is alive on my wrist — not a static printout.

**Key mental model shift:** From "I manage the schedule" to "the schedule manages itself — I just confirm transitions."

### Success Criteria

| Criteria | Measure |
|---|---|
| Haptic recognition | Organizer identifies alert type (5min/2min/done) without looking within first event |
| Glance speed | Wrist raise to "I know the state" in under 2 seconds |
| Tap confidence | "Done" tap with zero hesitation — no "did it work?" doubt |
| Team sync trust | Organizers stop verbally confirming schedule state with each other |
| Cognitive offload | Moderator reports "I didn't think about timing" after event |

### Novel vs. Established Patterns

**Established patterns we adopt:**
- Countdown timer (universal, no learning curve)
- Haptic notifications (every Watch user knows these)
- Tap to confirm (fundamental Watch interaction)
- Complication as ambient display (standard watchOS pattern)

**Novel combination that makes BATbern unique:**
- **Multi-user synchronized state from a single tap** — No existing Watch app treats 4 watches as one coordinated system. The "Done" tap is novel because its effect is team-wide, not personal.
- **Haptic vocabulary for event operations** — Using distinct haptic patterns as a non-visual communication channel for event timing is new. Users must learn 4 patterns, but each maps to one clear meaning.
- **Schedule cascade on wrist** — Dynamically rescheduling an entire event from a Watch is novel. The interaction must feel as simple as setting an alarm, despite the complexity behind it.

### Experience Mechanics

**1. Initiation — The Haptic Buzz:**
- System fires haptic at programmed time (5 min, 2 min, 0:00)
- Moderator feels it on wrist — no visual needed
- Distinct pattern tells them which alert without looking

**2. Interaction — The Glance:**
- Wrist raise activates always-on display
- Screen shows: countdown (large), speaker name (medium), "Done" button (if at/past zero)
- During normal time: passive display only, no action needed
- At session end: "Done" button appears prominently

**3. Feedback — The Tap Response:**
- Tap "Done" → immediate haptic confirmation (`.success` pattern)
- Screen transitions to next session info (speaker name, portrait, title)
- If session ran over: cascade prompt appears ("Shift remaining +N min?")
- All other watches update within 3 seconds — visible via sync indicator

**4. Completion — The Transition:**
- New countdown begins automatically for next session
- Moderator glances at next speaker name + portrait for introduction
- System returns to passive mode — no further interaction needed
- The loop resets: wait for next haptic → glance → tap

## Visual Design Foundation

### Color System

**Brand Adaptation for watchOS:** BATbern's web platform uses `#2C5F7C` (primary blue) with a Swiss precision aesthetic. On watchOS, we map the brand identity to Apple's system color semantics while preserving BATbern's blue DNA.

**watchOS Color Mapping:**

| Purpose | Color | Source | Usage |
|---|---|---|---|
| Brand accent | BATbern Blue (`#2C5F7C`) | Brand guidelines | App icon, complication tint, header accents |
| On track | System `.green` | watchOS semantic | Countdown running normally, connected status |
| Warning (5 min) | System `.yellow` | watchOS semantic | 5-minute warning state |
| Urgent (2 min) | System `.orange` | watchOS semantic | 2-minute warning state |
| Time's up / Overrun | System `.red` | watchOS semantic | Zero/overrun countdown display |
| Text primary | System `.white` | watchOS default | Countdown digits, speaker names |
| Text secondary | System `.gray` | watchOS default | Talk titles, secondary info |
| Background | System black | watchOS default | OLED-native, battery-efficient |

**Always-On Display Colors:** watchOS automatically dims colors for always-on state. Use system colors to ensure automatic dimming works correctly — custom hex colors may not dim properly and burn OLED.

**Color State Transitions:**
- Normal (>5 min): BATbern Blue accent + white countdown
- Warning (5-2 min): Yellow accent tint on countdown
- Urgent (<2 min): Orange accent tint
- Time's up (0:00): Red countdown digits
- Overrun (+N:NN): Red digits + red background tint

### Typography System

**watchOS Font Strategy:** Use Apple's system fonts exclusively — SF Pro and SF Mono are designed for Watch legibility at small sizes and automatically support Dynamic Type.

| Element | Font | Size | Weight | Usage |
|---|---|---|---|---|
| Countdown digits | SF Mono | ~40pt | Bold | Primary countdown display — must dominate the screen |
| Speaker name | SF Pro Rounded | ~16pt | Semibold | Current/next speaker identification |
| Talk title | SF Pro | ~13pt | Regular | Secondary context, truncated to 1 line |
| Status label | SF Pro | ~11pt | Medium | "On Track," "5 min left," "+2:00 over" |
| Complication text | SF Pro Compact | System | Medium | Watch face complication content |
| Button label | SF Pro | ~16pt | Semibold | "Done," "Shift +5 min" |

**Typography Principles:**
- Countdown is always the largest element on screen — no exceptions
- Speaker names truncate with ellipsis rather than wrapping
- Talk titles are optional — show only when space permits
- No paragraph text anywhere — this is a Watch, not a reading device

### Spacing & Layout Foundation

**watchOS Layout Constraints:**
- Screen: 44mm Watch = 184 × 224 points usable area
- Safe areas: ~8pt inset on all sides (more on rounded corners)
- Effective content area: ~168 × 208 points

**Layout Zones (Vertical Stack):**

```
┌──────────────────────┐
│  Status bar (8pt)    │  ← Connection status, event state
│──────────────────────│
│                      │
│   24:32              │  ← Countdown (dominant, ~80pt tall)
│                      │
│──────────────────────│
│  Anna Meier          │  ← Speaker name (~20pt)
│  Cloud-Native Pitf…  │  ← Talk title, truncated (~16pt)
│──────────────────────│
│  [ Done ]            │  ← Action button (only when relevant)
└──────────────────────┘
```

**Spacing Scale (watchOS-adapted):**
- 2pt: Minimum spacing (label-to-label)
- 4pt: Tight spacing (icon-to-text)
- 8pt: Standard spacing (between content blocks)
- 12pt: Section spacing (between layout zones)
- 16pt: Maximum spacing (rare, hero elements only)

**Complication Layouts:**
- `AccessoryCircular`: Circular progress ring + countdown minutes
- `AccessoryRectangular`: Speaker name + countdown + progress bar
- `AccessoryCorner`: Countdown digits only (minimal)

### Accessibility Considerations

- **Dynamic Type**: Support all watchOS text size settings — countdown digits scale proportionally
- **VoiceOver**: All elements labeled (countdown reads "24 minutes 32 seconds remaining for Anna Meier's talk")
- **Reduce Motion**: Disable any color transition animations if system preference set
- **Bold Text**: Respect system bold text preference — all weights shift up one level
- **Contrast**: System colors guarantee WCAG AA on OLED black background
- **Haptic alternatives**: Visual flash accompanies every haptic alert for users who may not feel vibrations

### Brand Assets for Watch

**Source logo:** `web-frontend/public/BATbern_color_logo.svg` — Full horizontal logo with three components:
1. **Symbol mark** — Two curved arrows forming a cycle (compact, recognizable at small sizes)
2. **Wordmark** — "BATbern" text
3. **Subtitle** — "TECHNOLOGIE-AUSTAUSCH ZWISCHEN IT EXPERTEN" (too small for Watch, never used)

**Watch usage — Symbol mark only:**

The symbol mark (cycle arrows) is the only logo element used on the Watch. It is extracted from the `featureKey="symbol1"` group in the SVG source — two `<path>` elements forming the interlocking cycle arrows.

| Placement | Size | Color | Context |
|---|---|---|---|
| P1 Event Hero | ~20pt width | BATbern Blue (`#2C5F7C`) | Above event number, establishes brand on launch screen |
| Empty states (no event) | ~32pt width | BATbern Blue (`#2C5F7C`) | Centered with "BATbern" wordmark below (~14pt) |

**Color decision:** Use `#2C5F7C` (design system BATbern Blue) — not the original logo blue (`#2d8acd`) — for consistency with all Watch UI accents (progress ring, buttons, complication tints). The design system color is slightly darker, which reads better alongside the watchOS system color palette on OLED.

**Where NOT to use the logo:**
- **Session Cards (P2)** — Every pixel needed for session content
- **Abstract Detail (P3)** — Reading space is precious
- **Speaker Bio (P4/P6)** — Portrait + bio dominate
- **Multi-Speaker Grid (P5)** — Grid needs all available space
- **Organizer zone screens** — Operational focus, no branding needed

**Implementation note:** For the SwiftUI implementation, extract the symbol mark paths into a standalone `batbern-symbol-mark.svg` asset for the Watch app asset catalog. The paths can be rendered via SwiftUI `Shape` or as an image asset.

---

## Navigation Architecture & Sitemap

### Dual-Zone Model

The app uses a **horizontal paging** model with two zones separated by swipe gestures:

```
◄── SWIPE LEFT                         SWIPE RIGHT ──►

┌──────────────────┐                ┌──────────────────┐
│   PUBLIC ZONE    │                │  ORGANIZER ZONE  │
│ (Left — Default) │   ◄────────►  │ (Right — Paired) │
│                  │                │                  │
│  No login needed │                │  Paired via code │
│  Browse schedule │                │  Live countdown  │
│  Crown scroll    │                │  Session control │
└──────────────────┘                └──────────────────┘
```

- **App launch** always opens the Public Zone (Event Hero screen)
- **Swipe right** from any public screen enters the Organizer Zone
- **Swipe left** from any organizer screen returns to the Public Zone
- The organizer zone entry screen depends on pairing state and event timing

### Complete Screen Catalog

#### Public Zone Screens

| ID | Screen | Entry | Content |
|---|---|---|---|
| **P1** | Event Hero | App launch (default) | Theme image background, event title, date/time/venue |
| **P2** | Session Card | Crown scroll from P1 | Time slot, title (tappable), speaker area (tappable) |
| **P3** | Abstract Detail | Tap title on P2 | Session title, full description text (Crown scrollable) |
| **P4** | Speaker Bio | Tap speaker on P2 (single) | Large portrait, name, company, bio |
| **P5** | Multi-Speaker Grid | Tap speaker area on P2 (multi) | Grid of portraits + company logos |
| **P6** | Individual Speaker Bio | Tap portrait on P5 | Same layout as P4 |

#### Organizer Zone Screens

| ID | Screen | Entry | Content |
|---|---|---|---|
| **O1** | Pairing Screen | Swipe right (not paired) | 6-digit code display + instructions |
| **O2** | Speaker Portrait Overview | Swipe right (paired, <1h before event) | Grid of speaker portraits with arrival tracking (tap to confirm arrived) |
| **O3** | Live Countdown | Swipe right (paired, event window) | Progress ring, countdown, speaker card, next preview |
| **O4** | Cascade Prompt | Tap "Done" when overrun | Shift options (+5/+10/absorb), Apply button |
| **O5** | Break + Gong View | Auto-transition during break | Break countdown, gong timer, next speaker preview |
| **O6** | Transition View | Auto-transition between talks | Next speaker portrait, name, talk title, duration |
| **O7** | Session Timeline | Crown scroll in organizer zone | Scrollable list of all sessions with status |

#### Watch Face Complications

| ID | Type | Content |
|---|---|---|
| **C1** | Circular (`AccessoryCircular`) | Progress ring + countdown minutes |
| **C2** | Rectangular (`AccessoryRectangular`) | Speaker name + countdown + progress bar |
| **C3** | Corner (`AccessoryCorner`) | Countdown digits only |

### Navigation Flow Diagram

```
                   ┌──────────────────────────────────────────────────┐
                   │                  WATCH FACE                      │
                   │  [C1: ○24]  [C2: Meier · 24:32 ━━━]  [C3: 24]  │
                   └────────────────────┬─────────────────────────────┘
                                        │ tap complication
                                        ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    PUBLIC ZONE (Left — Default on Launch)            │
│                                                                      │
│  ┌────────────┐  Crown    ┌────────────┐  Crown    ┌────────────┐  │
│  │     P1     │  scroll   │     P2     │  scroll   │     P2     │  │
│  │ Event Hero │ ────────► │ Session    │ ────────► │ Session    │  │
│  │            │ ◄──────── │  Card #1   │ ◄──────── │  Card #2   │  │
│  └────────────┘           └──────┬─────┘           └────────────┘  │
│                              tap │ title                            │
│                                  ▼                                  │
│                          ┌──────────────┐                           │
│                          │      P3      │                           │
│                          │   Abstract   │                           │
│                          │    Detail    │                           │
│                          └──────────────┘                           │
│                                                                      │
│                              tap │ speakers                         │
│                                  ▼                                  │
│                    ┌────────────────────────────┐                   │
│                    │  P4 (1 speaker)            │                   │
│                    │  Speaker Bio               │                   │
│                    ├────────────────────────────┤                   │
│                    │  P5 (2+ speakers)          │                   │
│                    │  Multi-Speaker Grid        │──tap──► P6 (bio) │
│                    └────────────────────────────┘                   │
│                                                                      │
└──────────────────────┬──────────────────────┬────────────────────────┘
                       │ ◄── SWIPE LEFT       │ SWIPE RIGHT ──►
┌──────────────────────┴──────────────────────┴────────────────────────┐
│                    ORGANIZER ZONE (Right — Paired)                   │
│                                                                      │
│  Entry screen depends on state:                                      │
│                                                                      │
│  ┌─────────────┐   ┌─────────────┐   ┌──────────────────────┐      │
│  │ Not paired? │   │ Paired,     │   │ Paired & event       │      │
│  │      ▼      │   │ <1h before? │   │ started?             │      │
│  │  ┌───────┐  │   │      ▼      │   │       ▼              │      │
│  │  │  O1   │  │   │  ┌───────┐  │   │  ┌──────────────┐   │      │
│  │  │Pairing│  │   │  │  O2   │  │   │  │     O3       │   │      │
│  │  │Screen │  │   │  │Speakr │  │   │  │ Live         │   │      │
│  │  └───────┘  │   │  │Arrival│  │   │  │ Countdown    │   │      │
│  └─────────────┘   │  └───────┘  │   │  │ (5 states)   │   │      │
│                     └─────────────┘   │  └───┬──────┬───┘   │      │
│                                       │ Done │  Crown│       │      │
│                                       │(over)│ scroll│       │      │
│                                       │      ▼      ▼       │      │
│                                       │  ┌──────┐ ┌──────┐  │      │
│                                       │  │  O4  │ │  O7  │  │      │
│                                       │  │Cascad│ │Timeln│  │      │
│                                       │  └──────┘ └──────┘  │      │
│                                       │                      │      │
│                                       │  Auto-transitions:   │      │
│                                       │  ┌──────┐  ┌──────┐ │      │
│                                       │  │  O5  │  │  O6  │ │      │
│                                       │  │Break │  │Trans.│ │      │
│                                       │  └──────┘  └──────┘ │      │
│                                       └──────────────────────┘      │
└──────────────────────────────────────────────────────────────────────┘
```

### Navigation Rules

| Gesture | From | To | Condition |
|---|---|---|---|
| App launch | Watch face | P1 (Event Hero) | Always |
| Tap complication | Watch face | O3 (Live Countdown) | Paired & event active |
| Crown scroll ↓ | P1 (Event Hero) | P2 (First session) | Sessions exist |
| Crown scroll ↓/↑ | P2 (Session Card) | P2 (Adjacent session) | More sessions in direction |
| Tap title area | P2 (Session Card) | P3 (Abstract Detail) | AGENDA phase published |
| Tap speaker area | P2 (Session Card) | P4 or P5 | 1 speaker → P4, 2+ speakers → P5 |
| Tap portrait | P5 (Multi-Speaker Grid) | P6 (Individual Bio) | Always |
| Back (swipe/button) | P3, P4, P5, P6 | P2 (Previous session card) | Always |
| Swipe right | Any public screen | O1, O2, or O3 | State-dependent (see below) |
| Swipe left | Any organizer screen | Last viewed public screen | Always |
| Digital Crown press | Any organizer screen | O3 (Active session) | During event |
| Tap "Done" (on time) | O3 (Time's up) | O6 (Transition) | Session ended on time |
| Tap "Done" (overrun) | O3 (Overrun) | O4 (Cascade Prompt) | Session ran over |
| Tap "Apply" | O4 (Cascade) | O6 (Transition) | After shift selection |
| Auto-transition | O3 / O6 | O5 (Break + Gong) | Break period starts |
| Auto-transition | O5 (Break) | O3 (Countdown) | Next session starts |

### Organizer Zone State Machine

| Condition | Entry Screen | Next Transition |
|---|---|---|
| Watch not paired | O1 (Pairing Screen) | → O2 after successful pairing |
| Paired, no current event | "No active event" message | → O2/O3 when event becomes available |
| Paired, >1h before event | Event preview (title, date, start time) | → O2 when <1h before event |
| Paired, <1h before event | O2 (Speaker Portrait Overview + arrival tracking) | → O3 when event starts |
| Paired, event started | O3 (Live Countdown) | → O4/O5/O6/O7 during event |

---

## Public Zone UX Design

### Design Philosophy

The public zone is a **browse-only experience**. No accounts, no login, no state changes. Think of it as a digital event program on the wrist — always available, always current. It must feel complete and useful on its own, not a teaser for the organizer zone.

**Core Interaction Model:**
- **Crown scroll** = browse sessions (vertical paging, one session per page)
- **Tap title** = read abstract (push detail view)
- **Tap speaker** = see bio + portrait (push detail view)
- **Back** = return to session list

### Event Hero Screen (P1)

The launch screen sets the mood. Full-bleed theme image background (dimmed for text readability). The BATbern **symbol mark** (cycle arrows) sits above the event number to establish brand identity — compact, recognizable, and tinted in BATbern Blue (`#2C5F7C`). Event title centered below. Compact info bar at bottom.

```
┌──────────────────────┐
│░░░░░░░░░░░░░░░░░░░░░░│
│░░░░░  [⟳ symbol]  ░░░│  ← BATbern cycle arrows (~20pt, BATbern Blue)
│░░░ BATBERN #42 ░░░░░░│  ← Event number (small, secondary)
│░░░░░░░░░░░░░░░░░░░░░░│
│░░░ Cloud Native ░░░░░│  ← Event title (large, centered)
│░░░ Evening    ░░░░░░░│
│░░░░░░░░░░░░░░░░░░░░░░│
│──────────────────────│
│ 15 Feb · 18:00 · Bern│  ← Info bar
│ ▼ Scroll for program │  ← Scroll affordance
└──────────────────────┘
```

**Symbol mark placement:** The BATbern cycle arrows symbol (extracted from the full logo — see [Brand Assets](#brand-assets-for-watch)) renders at ~20pt width, centered above the event number. It uses BATbern Blue (`#2C5F7C`) to match the design system accent color. On the OLED black background with the dimmed theme image, the symbol provides instant brand recognition without competing with the event title below.

**Data source:** `GET /api/v1/events/current?expand=sessions,speakers`

**Edge cases:**
- No current event → Show BATbern symbol mark (~32pt) + "BATbern" wordmark (~14pt, BATbern Blue) centered on screen, with "No upcoming BATbern event" message below (see layout below)
- Event published at TOPIC level → Show title only, sessions hidden
- Network unavailable → Show cached event with "Last updated [time]" indicator

**Empty state layout (no current event):**

```
┌──────────────────────┐
│                       │
│      [⟳ symbol]      │  ← Symbol mark (~32pt, BATbern Blue)
│       BATbern        │  ← Wordmark (~14pt, BATbern Blue)
│                       │
│  No upcoming event   │  ← Secondary text (system gray)
│                       │
└──────────────────────┘
```

### Session Card Pages (P2)

Each session occupies a full Watch page. Vertical paging via Digital Crown — one session per Crown "click."

```
┌──────────────────────┐
│  18:00 – 18:45       │  ← Time slot (secondary color)
│  ─────────────────── │
│                       │
│  Cloud Native         │  ← Title (blue tint, tappable → P3)
│  Security in 2026     │
│                       │
│  ─────────────────── │
│  ┌────┐  ┌────┐      │  ← Speaker area (tappable → P4/P5)
│  │ 📷 │  │ 📷 │      │     Portrait + name + company
│  │Anna│  │Tom │      │
│  │ACME│  │Corp│      │
│  └────┘  └────┘      │
└──────────────────────┘
```

**Session type variations:**
- **Presentation / Keynote / Workshop / Panel:** Full card with speaker area
- **Break / Networking / Lunch:** Simple card — title + time only, no speaker area

```
┌──────────────────────┐
│  19:00 – 19:20       │
│                       │
│         ☕            │
│    Coffee Break       │
│                       │
└──────────────────────┘
```

**Progressive publishing support:**
- `TOPIC` phase: Title only, no speakers shown
- `SPEAKERS` phase: Title + speakers, abstract dimmed/hidden
- `AGENDA` phase: Full detail — title, speakers, abstract available

### Abstract Detail (P3)

Push view from tapping session title. Crown-scrollable for longer abstracts.

```
┌──────────────────────┐
│  ◄ Back              │
│                       │
│  Cloud Native         │  ← Title
│  Security in 2026     │
│  ─────────────────── │
│  Microservices gone   │  ← Abstract text (Crown scrollable)
│  wrong: lessons from  │
│  3 years of produc... │
│                       │
│  18:00 – 18:45       │  ← Time slot
└──────────────────────┘
```

### Speaker Bio (P4 / P6)

Push view with speaker details. Crown-scrollable for longer bios.

```
┌──────────────────────┐
│  ◄ Back              │
│                       │
│      ┌──────┐        │  ← Large portrait
│      │  📷  │        │
│      └──────┘        │
│  Anna Meier          │  ← Name
│  ACME Corp  [logo]   │  ← Company + logo
│  ─────────────────── │
│  Senior architect     │  ← Bio (Crown scrollable)
│  specializing in...   │
└──────────────────────┘
```

### Multi-Speaker Grid (P5)

When a session has 2+ speakers, tapping the speaker area shows a grid of portrait thumbnails. Each portrait is tappable → pushes to P6 (individual bio).

```
┌──────────────────────┐
│  ◄ Back              │
│  Speakers (3)        │
│                       │
│  ┌────┐  ┌────┐      │  ← Portrait grid (tappable → P6)
│  │ 📷 │  │ 📷 │      │
│  │John│  │Jane│      │
│  │ACME│  │Corp│      │
│  └────┘  └────┘      │
│       ┌────┐         │
│       │ 📷 │         │
│       │Bob │         │
│       │Inc │         │
│       └────┘         │
└──────────────────────┘
```

### Offline & Stale Data

The public zone caches all event data locally (SwiftData). When offline:
- All screens remain browsable with cached data
- A subtle "Last updated [time]" indicator appears in the status bar
- The app retries sync every 5 minutes in background
- No functionality is lost — only real-time freshness degrades

---

## Pairing & Authentication UX

### Design Philosophy

Authentication happens **once** and is **invisible** after that. The pairing code flow avoids all password entry on the Watch — no tiny keyboard, no dictation, no complexity. Generate code on web → enter on Watch → paired permanently (until explicitly unpaired from web profile).

### Pairing Flow

**Step 1: Web Frontend (Laptop/Phone)**

The organizer opens their BATbern profile → clicks "Pair Apple Watch" → a 6-digit numeric code appears with a 24-hour expiry countdown.

**Step 2: Watch**

The organizer swipes right on the Watch → pairing screen appears (since unpaired) → enters 6-digit code using Crown-scroll digit picker → taps "Pair."

```
┌──────────────────────┐
│                       │
│  Pair Your Watch     │
│                       │
│  ┌──────────────┐    │
│  │  4 8 2 7 1 5 │    │  ← 6-digit numeric code
│  └──────────────┘    │
│                       │
│  Enter the code from │
│  your BATbern profile│
│                       │
│  [ Pair ]            │
└──────────────────────┘
```

**Step 3: Confirmation**

Success → haptic `.success` pattern → "Paired as [Name]" message → organizer zone loads immediately. Token stored in Keychain. Swiping right from now on always shows the organizer zone directly.

**Unpairing:** Only from the web profile ("Unpair Watch" button). Not available on the Watch itself — prevents accidental unpairing during events.

### Post-Pairing Behavior

| State | Organizer Zone Shows |
|---|---|
| Not paired | O1: Pairing Screen |
| Paired, no current event | BATbern symbol mark + wordmark + "No active event" (same layout as public zone empty state) |
| Paired, >1h before event | Event preview (title, date, start time) |
| Paired, <1h before event | O2: Speaker Portrait Overview with arrival tracking |
| Paired, event started | O3: Live Countdown |

### Speaker Portrait Overview (O2)

Shown within 1 hour before event start. Combines face recognition AND arrival tracking — a scrollable grid of tonight's speakers with portraits, names, and arrival status. Any organizer can tap a speaker to confirm they've arrived, and the green ✓ badge syncs to all watches.

**Arrival Tracking Interaction:**
- Each speaker portrait shows their face + name
- **Tap a speaker** → confirmation prompt: "Has [Name] arrived?"
- **Tap "Arrived ✓"** → green ✓ badge appears on portrait, syncs to all organizer watches via WebSocket
- **Arrival counter** at top: "2 of 4 arrived" — updates in real time across all watches
- Already-arrived speakers show green ✓ badge; tap again shows "Confirmed by [organizer name]"

```
┌──────────────────────┐
│  Tonight's Speakers  │
│  2 of 4 arrived      │  ← Arrival counter (synced)
│                       │
│  ┌────┐  ┌────┐      │
│  │ 📷 │  │ 📷 │      │
│  │Anna│  │Tom │      │
│  │ ✓  │  │    │      │  ← Green ✓ = confirmed arrived
│  └────┘  └────┘      │
│  ┌────┐  ┌────┐      │
│  │ 📷 │  │ 📷 │      │
│  │Lisa│  │Marc│      │
│  │ ✓  │  │    │      │
│  └────┘  └────┘      │
│                       │
│  Event starts at 18:00│
└──────────────────────┘
```

**Arrival Confirmation (tap a speaker):**

```
┌──────────────────────┐
│                       │
│      ┌──────┐        │
│      │  📷  │        │
│      └──────┘        │
│  Anna Meier          │
│  ACME Corp           │
│                       │
│  Has this speaker    │
│  arrived?            │
│                       │
│  [ Arrived ✓ ]       │  ← Green confirmation
│  [ Not yet   ]       │  ← Dismiss
└──────────────────────┘
```

**Transition:** Automatically switches to O3 (Live Countdown) when the event starts (`typicalStartTime` reached).
