---
stepsCompleted: [step-01-init, step-02-discovery, step-03-success, step-04-journeys, step-05-domain, step-06-innovation, step-07-project-type, step-08-scoping, step-09-functional, step-10-nonfunctional, step-11-polish, step-12-complete]
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

# Product Requirements Document - BATbern Apple Watch Companion App

**Author:** Nissim
**Date:** 2026-02-15
**Version:** 2.0 (Consolidated)
**Project:** BATbern — Berner Architekten Treffen
**Platform:** Native watchOS standalone app
**Status:** Ready for Architecture & Design

---

## Document History

- **v1.0** (2026-02-14): Initial PRD from brainstorming session (28 FRs, organizer-focused)
- **v2.0** (2026-02-15): Consolidated version merging dual-zone architecture, navigation details, data architecture, and repository structure

**Input Documents:**
- [Brainstorming Session Results](brainstorming-session.md) — Feature discovery and innovation exploration
- Previous PRD iterations from Claude.ai sessions (connection-issue fragmented capture)

---

## Executive Summary

BATbern Watch is a native Apple Watch companion app for the BATbern event management platform. It transforms the Apple Watch into **two experiences on one wrist**:

**Public Event Companion (Left Zone):**
Anyone with an Apple Watch can browse tonight's BATbern event — theme, schedule, session details, speaker bios and portraits — by scrolling with the Digital Crown. No login required.

**Organizer Command Center (Right Zone):**
The 4 BATbern organizers swipe right to enter a paired, authenticated zone with live countdown control, haptic-driven time alerts, and team-synchronized session management.

**Vision:** Replace printed schedules and mental clock math with a glanceable, haptic-driven command surface on the wrist — enabling moderators to run entire events without touching a phone, while giving all attendees instant access to the event program.

**Target Users:**
- **Primary:** 4 BATbern organizers (all generalists, one moderating per event)
- **Secondary:** ~200 attendees per event (public browsing)

**Core Differentiator:**
Unlike typical single-user Watch apps, BATbern Watch is a **multi-organizer synchronized system** where one tap on any wrist updates all watches simultaneously. The moderator on stage — who cannot use a phone — is the power user. Plus, the public zone makes it App Store-worthy with broad adoption potential.

**Project Context:**
Brownfield extension of the existing BATbern platform (MVP complete: 5 microservices, React frontend, AWS infrastructure, 9-state event workflow, speaker coordination).

---

## Navigation Architecture

The app uses a **horizontal paging model** with two zones separated by swipe gestures:

```
┌─────────────────────────────────────────────────┐
│                                                   │
│   ◄── SWIPE LEFT          SWIPE RIGHT ──►         │
│                                                   │
│  ┌─────────────┐         ┌──────────────┐         │
│  │  PUBLIC      │         │  ORGANIZER    │         │
│  │  EVENT VIEW  │  ◄───► │  COMMAND      │         │
│  │             │         │  CENTER       │         │
│  │  (no login)  │         │  (paired)     │         │
│  └─────────────┘         └──────────────┘         │
│                                                   │
│  Crown scroll:            Crown scroll:            │
│  Sessions ↕               Session detail ↕         │
│                                                   │
│  Tap title: Abstract       Tap: Session control    │
│  Tap speaker: Bio                                  │
│                                                   │
└─────────────────────────────────────────────────┘
```

### Public Zone (Left — Default on Launch)

**Screen 1: Event Hero**
- Event theme image as full-bleed background
- Event title overlay
- Bottom bar: date, time, venue name
- Data source: `themeImageUrl` from `GET /api/v1/events/current`

**Crown Scroll Down → Session Pages (one per session)**

Each session page is a vertically-split card:

```
┌──────────────────────┐
│  09:00 – 09:45       │  ← Time slot
│  ─────────────────── │
│                       │
│  Cloud Native         │  ← Title (tappable → abstract)
│  Security in 2026     │
│                       │
│  ─────────────────── │
│  ┌────┐  ┌────┐      │  ← Speaker area (tappable → bio)
│  │ 📷 │  │ 📷 │      │     Portrait + company logo grid
│  │John│  │Jane│      │
│  │ACME│  │Corp│      │
│  └────┘  └────┘      │
└──────────────────────┘
```

**Tap Interactions:**
- **Tap upper area (title):** Push to abstract detail screen showing `session.description`
- **Tap lower area (speakers):** Push to speaker detail screen
  - Single speaker: Full portrait, name, company, `speaker.bio`
  - Multiple speakers: Grid of portraits + company logos; tap individual speaker for bio

Sessions are ordered by `startTime`. Break/lunch sessions shown as simple cards without speaker areas.

### Organizer Zone (Right — Swipe Right from Any Public Screen)

**If not paired:** Shows pairing screen (6-character code display + instructions).

**If paired and within 1 hour before event start:** Shows speaker portrait overview — a scrollable grid of all speakers for tonight with portraits and arrival tracking. Organizer taps a speaker to confirm their arrival; a green ✓ badge appears on the portrait and syncs to all organizer watches instantly.

**If paired and within event window:** Shows the live countdown and session control interface.

### Navigation Summary

| Gesture | Action |
|---|---|
| **Launch** | Public zone → Event hero screen |
| **Crown scroll down** | Next session page |
| **Crown scroll up** | Previous session page |
| **Tap title area** | Push → Abstract detail |
| **Tap speaker area** | Push → Speaker bio (or multi-speaker grid → tap → individual bio) |
| **Swipe right** | Switch to organizer zone |
| **Swipe left** | Return to public zone |
| **Digital Crown press** | Return to current/active session (organizer zone) |

---

## Success Criteria

### User Success

- The app feels supportive, not fiddly — large tap targets, glanceable information, no text walls
- Moderator never does mental clock math on stage — countdown is always visible
- Rescheduling after an overrun takes one tap, not a huddle with co-organizers
- Break gong is never forgotten — haptics carry the cognitive load
- All 4 organizers see identical event state without verbal coordination
- Attendees can browse the schedule without pulling out their phone

### Business Success

- All 4 organizers adopt the Watch app at every BATbern event (spring + autumn)
- 30+ attendees install the app within first 2 events (public zone adoption)
- Paper schedules eliminated from the organizer workflow
- Internal team tool — no external user expansion required for MVP success

### Technical Success

- Watch connects directly to BATbern backend — no iPhone companion app required
- Real-time state sync across all 4 watches reliable throughout a 3-hour event
- Battery allows full event coverage without mid-event charging
- Public zone works offline with cached data

### Measurable Outcomes

- 100% of organizer timing actions handled by Watch (zero phone pulls)
- Schedule cascade completes in under 3 seconds across all watches
- Haptic alerts delivered within 1 second of scheduled time
- Sessions end within ±2 minutes of planned time (vs. ±5-10 min today)

---

## Product Scope

### MVP (Phase 1)

**MVP Approach:** Experience MVP — the minimum feature set that lets the moderator run an entire BATbern evening event from their wrist, while giving all attendees a useful schedule browser. If it works for one event, it's validated.

**Resource Requirements:** 1 iOS/watchOS developer + existing BATbern backend team. Backend changes are incremental (new endpoints on existing API Gateway).

**Must-Have Capabilities:**

| ID | Capability | Description | Audience |
|---|---|---|---|
| PUB-1 | Event browsing | Browse event theme, sessions, speakers, bios | Attendees |
| PUB-2 | Speaker portraits | View speaker photos and company logos | Attendees |
| PUB-3 | Session abstracts | Read talk descriptions via Crown scroll | Attendees |
| LIVE-1 | Always-on schedule complication | Current talk, speaker name, portrait, countdown | Organizers |
| LIVE-2 | Haptic cue system | Escalating alerts at 5min / 2min / time's up / overrun | Organizers |
| LIVE-5 | Live schedule cascade | One-tap reschedule when speaker overruns | Organizers |
| LIVE-6 | Break gong reminder | Haptic at configured time before break ends | Organizers |
| LIVE-8 | Session complete | Tap "Done" to advance schedule for all organizers | Organizers |
| SYNC-1 | Shared state | All watches display identical event state via backend sync | Organizers |
| PRE-1 | Speaker arrival tracking | Tap speaker portrait to confirm arrival; green ✓ syncs to all watches | Organizers |
| — | Standalone connectivity | Direct backend connection over WiFi, no iPhone required | Both |
| — | Offline resilience | Cached schedule; countdown/haptics work without WiFi | Both |

**Core User Journeys Supported:** Moderator happy path, Floor Organizer, Speaker Overrun edge case, Pre-Event Setup, Attendee browsing — all fully supported.

### Phase 2 (Growth)

- **LIVE-7:** Speaker time signal/flash (discreet signal to speaker)
- **SYNC-3:** Quick ping between organizers (silent wrist buzz)
- **LIVE-3:** Next-up speaker notification (auto-ping "You're on in 10 minutes")

### Phase 3 (Expansion)

- **LIVE-4:** Attendee count pulse (live check-in count on wrist)
- Speaker-facing Watch complication (countdown for the speaker themselves)
- Attendee-facing live schedule on Watch (already covered by Public Zone MVP)

---

## User Journeys

### Journey 1: Marco the Moderator — Evening Event (Happy Path)

Marco is one of the 4 BATbern organizers and tonight he's moderating. It's 17:45, fifteen minutes before doors open. He raises his wrist — the Watch face shows the BATbern complication: **"Event starts in 15 min | First: Anna Meier — Cloud-Native Pitfalls"**.

18:00 — Marco welcomes the room. He glances at his wrist: the countdown is running. **24:12 remaining**. He doesn't think about time. He listens to Anna's talk, watches the audience.

At **5 minutes remaining**, a firm haptic buzz hits his wrist. Only he feels it. He knows it's time to start thinking about the transition. At **2 minutes**, another buzz. He prepares his notes for the Q&A wrap-up. At **0:00**, the final buzz. He stands, thanks Anna, and opens a quick Q&A.

Q&A wraps. Marco taps **"Done"** on his Watch. The schedule advances. His wrist now shows: **"Next: Thomas Keller — Zero Trust Architecture | 5 min break"**. All 4 organizers' watches update simultaneously. He glances down, reads the speaker name and title, and introduces Thomas without touching a piece of paper.

The evening flows. Three talks, one break (the gong reminder buzzes his wrist at 15 minutes — he rings the bell). At 21:00, Marco taps "Done" on the final session. The event is complete.

**What Marco never did:** pull out his phone, do clock math, check a printed schedule, or ask a co-organizer "who's next?"

---

### Journey 2: Sarah the Floor Organizer — Supporting from the Room

Sarah is another organizer. Tonight Marco is moderating, so she's on the floor — greeting late arrivals, checking on the catering setup, talking to a sponsor.

She's deep in conversation with a partner representative when her wrist buzzes: **5 minutes remaining** on the current talk. She wraps the conversation naturally — "I should head back, the next speaker is up soon."

She walks to the side of the room. Her Watch shows the same countdown Marco sees: **4:32 remaining**. She spots Thomas Keller near the coffee — she walks over and quietly says "You're up next, Thomas."

When Marco taps "Done," Sarah's Watch advances too. She sees the break starting and the gong countdown begin. She doesn't need to check with Marco or look at a printout. She *knows* the state of the event at all times.

**What Sarah never did:** ask Marco "where are we in the schedule?", check her phone for the agenda, or lose track of time while talking to sponsors.

---

### Journey 3: Marco the Moderator — Speaker Overrun (Edge Case)

It's the second talk. The speaker is deep into a live demo that's captivating the audience. Marco's wrist buzzes at **0:00** — time's up. But the demo is landing perfectly. He decides to let it run.

At **+2 minutes**, another haptic pattern — slightly different, more urgent. The Watch shows **"+2:00 over"** in a distinct color. Marco makes a judgment call — two more minutes.

At **+4:00**, Marco wraps the Q&A. He taps **"Done"** and the Watch asks: **"Session ran +4 min over. Shift remaining schedule?"** He taps **"Shift +5 min"** (rounding up for buffer).

Instantly, all 4 watches update. The break that was 20 minutes is now 20 minutes starting 5 minutes later. The gong reminder recalculates. The final talk's start time shifts. One tap, everything cascades.

The audience notices nothing. The other 3 organizers don't need to be told — their watches already show the new timeline.

**What Marco never did:** whisper to a co-organizer "we're running late, push everything back 5 minutes", recalculate the schedule mentally, or panic about the domino effect.

---

### Journey 4: Pre-Event Setup (Organizer)

It's 17:00 on event day. Nissim opens the BATbern web app on his laptop and activates tonight's event for Watch sync. The event's agenda, speaker names, and timing are already in the system from weeks of preparation.

He raises his Apple Watch — it connects directly to the BATbern backend over the venue WiFi. The complication populates: tonight's event, first speaker, start time. He swipes right to organizer zone and checks — all 4 organizers show as connected. Green dots. They're ready.

No iPhone tethering. No Bluetooth pairing with a phone. The Watch talks directly to the backend. Simple.

---

### Journey 5: Ana the Attendee — Public Browsing (New Journey)

Ana is an architect who registered for tonight's BATbern event. She installed the Watch app from the App Store after seeing it mentioned in the registration confirmation email.

She arrives at the venue at 18:05 — the first talk is already underway. She finds a seat in the back. Instead of pulling out her phone to check what's on, she raises her wrist.

The Watch shows the event hero screen. She scrolls down with the Crown — **"18:00-18:45 | Cloud-Native Pitfalls"** — that's the current talk. She taps the title and reads the abstract: *"Microservices gone wrong..."* — interesting. She taps the speaker area and sees Anna Meier's portrait, company, and bio.

She scrolls down to see what's next: **"19:00-19:45 | Zero Trust Architecture"**. She makes a mental note. During the break, she doesn't need to check her phone — one Crown scroll on her wrist shows the schedule.

**What Ana never did:** pull out her phone during a talk, struggle to find the PDF program email, or ask someone "what's on next?"

---

### Journey Requirements Summary

| Journey | Capabilities Revealed |
|---|---|
| Marco (Happy Path) | Always-on complication, haptic cue system, session advance, speaker info display |
| Sarah (Floor) | Shared real-time state, passive schedule awareness, same haptic cues |
| Marco (Overrun) | Overrun detection, escalating haptics, schedule cascade with confirmation, auto-recalculation |
| Pre-Event Setup | Direct backend connection, multi-organizer presence detection, event activation |
| Ana (Attendee) | Public browsing, event hero, session abstracts, speaker bios, offline cache |

---

## Functional Requirements

### Schedule Display & Awareness

- **FR1:** Organizer can view the current session's speaker name, talk title, and remaining time on the Watch face complication
- **FR2:** Organizer can view the next upcoming session (speaker name and talk title) at a glance
- **FR3:** Organizer can scroll through the full remaining event schedule using the Digital Crown
- **FR4:** Organizer can view a speaker's portrait photo alongside their session information
- **FR5:** Organizer can see the current event state on the always-on display without raising their wrist

### Session Lifecycle Management

- **FR6:** Organizer can mark the current session as complete to advance the schedule to the next item
- **FR7:** System detects when a session runs past its allocated time and displays overrun duration
- **FR8:** Organizer can initiate a schedule cascade when a session overruns, shifting all remaining items by a chosen increment
- **FR9:** System automatically recalculates all downstream session times and break durations after a cascade
- **FR10:** Organizer can view whether the event is on time, ahead, or behind schedule

### Time Alerting & Haptics

- **FR11:** Organizer receives a haptic alert when 5 minutes remain in the current session
- **FR12:** Organizer receives a distinct haptic alert when 2 minutes remain in the current session
- **FR13:** Organizer receives a distinct haptic alert when the session time reaches zero
- **FR14:** Organizer receives escalating haptic alerts at intervals when a session runs over time
- **FR15:** Organizer receives a haptic alert at a configured time before a break ends (gong reminder)
- **FR16:** All connected organizers receive haptic alerts simultaneously for the same timing events

### Team Synchronization

- **FR17:** All connected organizer watches display identical event state in real time
- **FR18:** When one organizer advances the schedule, all other watches update within 3 seconds
- **FR19:** When one organizer triggers a schedule cascade, all other watches reflect the new times within 3 seconds
- **FR20:** Organizer can see how many fellow organizers are currently connected to the event

### Event Setup & Connection

- **FR21:** Organizer can authenticate with the BATbern platform directly from the Watch via pairing code
- **FR22:** Organizer can select and join an active event from the Watch
- **FR23:** System syncs the full event schedule (sessions, speakers, times, portraits) to the Watch on join
- **FR24:** Watch connects directly to the BATbern backend over WiFi without requiring an iPhone

### Pre-Event Coordination

- **FR36:** Organizer can view all speakers with portraits in a pre-event overview (available within 1 hour before event start)
- **FR37:** Organizer can tap a speaker's portrait to confirm their arrival, marking them with a visible green ✓ badge
- **FR38:** Speaker arrival confirmations sync to all connected organizer watches within 3 seconds
- **FR39:** Pre-event overview shows arrival count ("3 of 5 arrived") updated in real time across all watches

### Offline Resilience

- **FR25:** System caches the complete event schedule locally after initial sync
- **FR26:** Countdown timer and haptic alerts continue functioning when WiFi connectivity is lost
- **FR27:** Actions taken offline (session advance, cascade) queue locally and sync when connectivity is restored
- **FR28:** System indicates connectivity status to the organizer (connected / offline)

### Public Zone (Attendee Features)

- **FR29:** Attendee can view the current event's theme image, title, date, time, and venue on launch (no login)
- **FR30:** Attendee can browse all sessions via Digital Crown scrolling
- **FR31:** Attendee can tap on a session title to view the full abstract
- **FR32:** Attendee can tap on a speaker area to view speaker bio, portrait, and company info
- **FR33:** Multi-speaker sessions display a grid of speaker portraits with company logos
- **FR34:** Public zone respects progressive publishing phases (TOPIC / SPEAKERS / AGENDA)
- **FR35:** Public zone caches event data and works offline with "Last updated" indicator

---

## Non-Functional Requirements

### Performance

- **NFR1:** Complication updates within 1 second of state change
- **NFR2:** Haptic alerts fire within 1 second of scheduled time
- **NFR3:** Schedule cascade propagates to all connected watches within 3 seconds
- **NFR4:** Event schedule initial sync completes within 5 seconds on venue WiFi
- **NFR5:** App launch to usable state within 3 seconds
- **NFR6:** Crown scroll between sessions: <100ms per page transition
- **NFR7:** Public zone: App launch to hero screen <2 seconds (cached), <4 seconds (cold)

### Reliability

- **NFR8:** App must not crash during a live event (3-hour continuous session)
- **NFR9:** Haptic alerts must fire even if the app moves to background
- **NFR10:** Offline mode activates seamlessly on WiFi loss — no user action required
- **NFR11:** Queued offline actions must not be lost on app restart
- **NFR12:** System must handle conflicting actions from multiple organizers gracefully
- **NFR13:** Zero missed haptic alerts per event

### Security

- **NFR14:** Organizer authentication via pairing code flow (no password entry on Watch)
- **NFR15:** Pairing tokens stored in Keychain (not UserDefaults)
- **NFR16:** Short-lived JWTs (1 hour) for organizer API calls with auto-refresh
- **NFR17:** Only authenticated organizers assigned to an event can join that event's Watch session
- **NFR18:** Communication with backend encrypted via TLS
- **NFR19:** Max 2 watches per organizer account
- **NFR20:** Pairing codes expire after 24 hours if unused

### Battery & Resources

- **NFR21:** Full 3-hour event operation on a single charge (Watch battery > 30% remaining at event end)
- **NFR22:** Battery impact during event: <15% for 4-hour event (organizer mode with active sync)
- **NFR23:** Network polling frequency adapts to battery level (reduce frequency below 20% battery)
- **NFR24:** Cached data storage under 50MB per event (schedule + portraits)

### Compatibility & Accessibility

- **NFR25:** watchOS 11+ (Apple Watch Series 8, SE 2nd gen, Ultra, and later)
- **NFR26:** No iPhone app required (standalone watchOS app)
- **NFR27:** Direct WiFi support required (Series 6+ have this capability)
- **NFR28:** VoiceOver support for all screens
- **NFR29:** Dynamic Type support for text scaling
- **NFR30:** High contrast mode for countdown colors
- **NFR31:** Haptic-only alerts (no audio in default mode)

### Localization

- **NFR32:** German (primary), English, French
- **NFR33:** Matches existing web frontend i18n keys where applicable

---

## Innovation & Novel Patterns

### Detected Innovation Areas

1. **Watch as Team Coordination Tool** — Most Apple Watch apps are single-user personal tools. BATbern Watch is a multi-person real-time operations tool where 4 watches act as one synchronized system. Haptics serve as a silent team communication channel invisible to the audience.

2. **Standalone Event Control Surface** — Unlike typical Watch apps that act as phone companions, BATbern Watch connects directly to the backend with no iPhone dependency. The Watch becomes an independent operations terminal for a moderator who cannot touch any other device while on stage.

3. **The "Event Conductor" Pattern** — The Watch is not passive (just displaying info). It is an active control surface — tap "Done" to advance the event state, tap to cascade the entire schedule. One wrist drives the event forward for all stakeholders simultaneously.

4. **Dual-Zone Architecture** — Public + Organizer zones on one wrist. This unlocks App Store distribution (200 users) while protecting organizer-only features. Separated by a simple swipe gesture, not separate apps.

### Validation Approach

- Pilot at one BATbern evening event with all 4 organizers wearing Apple Watches
- Measure: did any organizer pull out their phone for schedule management during the event?
- Measure: did the moderator successfully use Watch-only for all transitions and introductions?
- Measure: did 10+ attendees use the public zone during the event?
- Post-event debrief: "would you go back to paper / phone?"

---

## watchOS Platform Requirements

### Platform & Distribution

- **Target:** Apple Watch Series 6+ (watchOS 10+) — needed for always-on display and reliable standalone networking
- **Framework:** SwiftUI with watchOS app lifecycle
- **Distribution:** App Store (public + organizers download same app)
- **Authentication:**
  - Public zone: No login required
  - Organizer zone: Pair once via code during setup; session persists for event duration

### Device Features

| Feature | Usage |
|---|---|
| **Taptic Engine** | Haptic cue system — escalating patterns for 5min/2min/time's up/overrun |
| **Complications** | Always-on display showing current talk, speaker, countdown |
| **Digital Crown** | Scroll through upcoming schedule items (both zones) |
| **Always-On Display** | Persistent countdown visible without wrist raise (organizer zone) |
| **Speaker Portraits** | Display speaker photo alongside name and talk title for face recognition |
| **WiFi Connectivity** | Direct backend connection without iPhone tethering |

### Offline Mode

- Cache full event schedule (speakers, times, agenda, portrait images) on initial sync
- If WiFi drops: local countdown and haptics continue uninterrupted (organizer zone)
- If WiFi drops: cached event data remains browsable (public zone)
- Real-time team sync degrades gracefully — resumes on reconnect
- Schedule cascade actions queue locally and sync when connectivity returns

### Sync Strategy (Organizer Zone)

- WebSocket connection to BATbern backend for real-time state sync between watches
- Fallback to polling if WebSocket unavailable
- Push notifications (APNs) as backup channel for critical state changes (session advance, schedule cascade)

### UX Constraints

- Maximum 2-3 lines of text visible at any time — design for glanceability
- Large tap targets only; no text input on Watch; no tiny buttons
- New REST/WebSocket endpoints on existing BATbern API Gateway for Watch-specific event state

---

## Data Architecture

### Public Data (No Auth Required)

All data sourced from existing public API endpoints:

| Data | Endpoint | Key Fields |
|---|---|---|
| Current event | `GET /api/v1/events/current?expand=sessions,speakers` | eventCode, title, date, themeImageUrl, venueName, typicalStartTime/EndTime, currentPublishedPhase |
| Event sessions | Included in expand | sessionSlug, title, description, sessionType, startTime, endTime, speakers[] |
| Speaker info | Included in session speakers[] | firstName, lastName, company, profilePictureUrl, bio, speakerRole |
| Company logos | `GET /api/v1/companies/{companyName}?expand=logo` | logoUrl |

**No new backend endpoints needed for public zone.**

---

### Pairing & Auth Data (New Endpoints Required)

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/api/v1/watch/pair` | POST | None (code validates) | Exchange pairing code for pairing token |
| `/api/v1/watch/authenticate` | POST | Pairing token | Exchange pairing token for access JWT |
| `/api/v1/users/{username}/watch-pairing` | POST | Organizer JWT | Register pairing code from web frontend |
| `/api/v1/users/{username}/watch-pairing` | DELETE | Organizer JWT | Unpair watch |
| `/api/v1/users/{username}/watch-pairing` | GET | Organizer JWT | Check pairing status |

---

### Live Session Data (New WebSocket Required)

| Channel | Auth | Purpose |
|---|---|---|
| `wss://{backend}/ws/events/{eventCode}/live` | Access JWT | Real-time session state sync |

**Message types:**
- `SESSION_STARTED { sessionSlug, startedBy, startTime }`
- `SESSION_EXTENDED { sessionSlug, extendedBy, newEndTime, minutesAdded }`
- `SESSION_ENDED { sessionSlug, endedBy, endTime }`
- `SESSION_SKIPPED { sessionSlug, skippedBy }`
- `SPEAKER_ARRIVED { speakerUsername, confirmedBy, timestamp }`
- `HEARTBEAT { timestamp }`

Reconnection with exponential backoff on disconnect.

---

### Local Storage (SwiftData)

```swift
@Model class CachedEvent {
    var eventCode: String
    var title: String
    var date: Date
    var themeImageUrl: String?
    var venueName: String
    var startTime: String  // typicalStartTime
    var endTime: String    // typicalEndTime
    var currentPublishedPhase: String?  // TOPIC, SPEAKERS, AGENDA
    var sessions: [CachedSession]
    var lastSynced: Date
}

@Model class CachedSession {
    var sessionSlug: String
    var title: String
    var abstract: String?  // session.description
    var sessionType: String  // keynote, presentation, workshop, panel_discussion, networking, break, lunch
    var startTime: Date?
    var endTime: Date?
    var speakers: [CachedSpeaker]
    var state: SessionState  // scheduled, active, completed, skipped (organizer zone only)
    var actualStartTime: Date?  // organizer zone only
    var overrunMinutes: Int?    // organizer zone only
}

@Model class CachedSpeaker {
    var username: String
    var firstName: String
    var lastName: String
    var company: String?
    var companyLogoUrl: String?
    var profilePictureUrl: String?
    var bio: String?
    var speakerRole: String  // keynote_speaker, panelist, moderator
    var arrived: Bool        // organizer zone: confirmed present at venue
    var arrivedConfirmedBy: String?  // username of organizer who confirmed
    var arrivedAt: Date?     // timestamp of arrival confirmation
}

@Model class PairingInfo {
    var pairingToken: String  // Stored in Keychain, referenced here
    var organizerUsername: String
    var organizerFirstName: String
    var pairedAt: Date
}

enum SessionState: String, Codable {
    case scheduled
    case active
    case completed
    case skipped
}
```

---

## Epic Breakdown

### Epic 1: Public Event Companion

**Goal:** Anyone with an Apple Watch can browse tonight's BATbern event — theme, schedule, session details, speaker bios and portraits — by scrolling with the Digital Crown. No login required.

**Scope:** FR29-FR35

**Backend work:** None — uses existing public endpoints.

**Key deliverables:**
- Xcode project setup (watchOS 11, Swift, SwiftUI)
- Event data fetching and SwiftData caching
- Hero screen with theme image background
- Session page carousel (Crown scrollable)
- Abstract detail view (tap title)
- Speaker bio detail view (tap speakers)
- Multi-speaker grid layout
- Progressive publishing support (TOPIC / SPEAKERS / AGENDA)
- Offline cache with stale indicator

**App Store:** Yes — this epic alone is a shippable product. Submit for TestFlight after Epic 1.

---

### Epic 2: Watch Pairing & Organizer Access

**Goal:** Organizer pairs their Watch once via a simple code, then swipes right to enter the organizer zone — automatically authenticated, no passwords ever.

**Scope:** FR21-FR24 (pairing), FR36-FR39 (pre-event coordination)

**Backend work:**
- Pairing code endpoints (company-user-management-service)
- Watch authentication token exchange
- Speaker arrival tracking (WebSocket message + state)
- Web frontend profile extension ("Watch Pairing" section)

**Key deliverables:**
- Horizontal paging navigation (public ↔ organizer zones)
- Pairing code generation (6-character) and display on Watch
- Web frontend "Watch Pairing" UI in organizer profile
- Backend pairing code → token exchange
- Auto-authentication on swipe-right (token in Keychain)
- Pre-event speaker portrait overview (<1 hour before event)
- Speaker arrival tracking (tap portrait → confirm → green ✓ badge syncs to all watches)
- Arrival counter ("3 of 5 arrived") with real-time sync
- Keychain storage for pairing token

---

### Epic 3: Live Countdown & Haptic Awareness

**Goal:** During the event, the organizer's wrist counts down, shows speaker info, and fires distinct haptic alerts at every critical moment.

**Scope:** FR1-FR5, FR11-FR16

**Backend work:** Session timing state endpoint (or derive from existing event data).

**Key deliverables:**
- Active session countdown (MM:SS) with progress ring
- Color-coded urgency transitions (Green > Yellow > Orange > Red)
- 8 distinct haptic patterns for timing milestones (session start, halfway, 10min, 5min, 2min, 1min, time's up, overtime pulse)
- Overtime tracking with delay impact display
- Next session preview below active session
- Always-on display support (dimmed countdown)
- Session timeline view (Crown scroll through all sessions)
- Watch face complication with real-time updates

---

### Epic 4: Session Control & Team Sync

**Goal:** One tap advances the event for ALL organizer watches simultaneously.

**Scope:** FR6-FR10, FR17-FR20, FR27

**Backend work:**
- WebSocket endpoint for real-time session state (`wss://.../ws/events/{eventCode}/live`)
- Session state machine (start, extend, end, skip)
- Multi-device conflict resolution (first action wins)

**Key deliverables:**
- Session start/extend/end/skip controls
- Schedule cascade UI ("Shift +5 min" / "+10 min")
- Downstream recalculation (breaks, final session times)
- WebSocket real-time sync across all organizer watches (<3 seconds)
- Conflict resolution with "Started by [name]" attribution
- Quick actions from haptic notifications (extend, view, end)
- Presence indicator (how many organizers connected)

---

### Epic 5: Offline Resilience

**Goal:** The Watch never fails, even when WiFi drops.

**Scope:** FR25-FR28, FR35 (public zone caching)

**Backend work:** None (resilience is client-side).

**Key deliverables:**
- Pre-event full schedule sync to SwiftData
- Local countdown timer (no network dependency)
- Local haptic schedule computation (wall-clock based)
- Connectivity monitoring and status indicator
- Sync recovery with server reconciliation
- Action queue for offline session control operations (session advance, cascade)
- Direct WiFi configuration (no iPhone tethering)
- Public zone offline browsing with "Last updated" timestamp

---

## Risk Mitigation

| Risk | Impact | Mitigation |
|---|---|---|
| Venue WiFi drops | Watches lose sync | Local cache keeps countdown/haptics running; sync resumes on reconnect |
| Not all organizers have Apple Watch | Partial team coverage | System works for any subset; non-Watch organizers use phone fallback |
| watchOS background limits | Haptics may not fire in background | Use local notifications + extended runtime session during events |
| Battery drain from WebSocket | Watch dies mid-event | Minimize active networking; poll every 30s instead of persistent socket if battery low |
| Small screen UX | Features don't fit Watch form factor | Strict 2-3 line maximum; test on real hardware before every feature ships |
| Adoption friction (organizers) | Organizers don't use it | Pilot at one event; post-event debrief to validate value |
| Adoption friction (attendees) | Public zone unused | Promote in registration emails; QR code posters at venue |

---

## Repository Structure & Artifact Separation

The Watch app lives alongside the main BATbern platform in the same monorepo but is **completely independent** of the Gradle/Java/TypeScript build system.

### Code Location

```
apps/BATbern-watch/           # Xcode project (Swift, SwiftUI, watchOS 11)
  BATbern-watch Watch App/    # Main Watch app target
  BATbern-watch Watch AppTests/        # Unit tests
  BATbern-watch Watch AppUITests/      # UI tests
  BATbern-watch.xcodeproj/    # Xcode project file
  CLAUDE.md                   # Watch app development guide
  README.md                   # Watch app overview
```

The `apps/` folder already contains legacy/auxiliary projects (`BATspa-old`, `BATbern-comming-soon`, etc.). The Watch app fits naturally here.

### Documentation Location

All Watch planning artifacts are in `docs/watch-app/`, separate from the platform's `docs/stories/` and `docs/architecture/`:

```
docs/watch-app/               # All Watch docs
  prd-batbern-watch-consolidated.md   # This PRD (authoritative)
  architecture.md             # Architecture decisions
  ux-design-specification.md  # UX design spec
  ux-design-directions.html   # Visual design mockups
  product-brief.md            # Initial product brief
  brainstorming-session.md    # Feature discovery session
  epics.md                    # Epic breakdown
  stories/                    # Watch stories (W-prefixed)
    W1.1-xcode-project-setup.md
    W1.2-event-hero-screen.md
    W2.1-pairing-code-flow.md
    ...
```

### Story Naming Convention

Watch stories use a **W prefix** to avoid collision with platform story numbers:

| Convention | Example | Meaning |
|---|---|---|
| `W{epic}.{story}` | `W1.2` | Watch Epic 1, Story 2 |
| Platform | `6.3` | Platform Epic 6, Story 3 |

This ensures `grep`, file listings, and CI workflows never confuse Watch and platform stories.

### Cross-Cutting Backend Work

Some Watch epics require changes to existing platform services:

| Watch Epic | Platform Service Affected | What Changes |
|---|---|---|
| W2 (Pairing) | `company-user-management-service` | New pairing endpoints, user profile extension |
| W2 (Pairing) | `web-frontend` | "Watch Pairing" section in organizer profile |
| W4 (Team Sync) | `event-management-service` | WebSocket endpoint, session state machine |

These backend changes are **implemented in the platform codebase** (existing services) but **tracked in Watch stories**. Each Watch story that requires backend work will note which service and files are affected.

### Build System Independence

| Concern | Platform | Watch App |
|---|---|---|
| Build tool | Gradle + npm | Xcode |
| Language | Java 21 + TypeScript | Swift |
| `make build` | Builds platform | Does NOT touch Watch |
| `make test` | Tests platform | Does NOT touch Watch |
| CI/CD | GitHub Actions → ECS | Separate workflow → TestFlight |
| Deploy | AWS CDK | App Store Connect |

---

## Open Questions

| # | Question | Status |
|---|---|---|
| 1 | Should the app be free on App Store or bundled with BATbern registration? | **Leaning: Free** — maximize adoption |
| 2 | Do we need a privacy policy page for App Store submission? | **Yes** — required by Apple, minimal since no PII collected in public mode |
| 3 | Should speaker portraits be pre-cached at a specific resolution for Watch? | To discuss — current CloudFront URLs serve full-size images |
| 4 | WebSocket infrastructure: add to existing API Gateway or separate lightweight service? | To discuss in architecture phase |
| 5 | Should the pairing code be numeric-only (easier to read on small screen) or alphanumeric? | **Leaning: Numeric 6-digit** for readability |
| 6 | Should we implement complication timeline updates or real-time push? | To discuss — timeline more battery-efficient |

---

## Dependencies

| Dependency | Status | Notes |
|---|---|---|
| Public event API endpoints | ✅ Available | All needed endpoints exist and are production-ready |
| Speaker/session data in API | ✅ Available | Full data including bios, portraits, abstracts |
| Company logo endpoint | ✅ Available | `GET /api/v1/companies/{name}?expand=logo` |
| CloudFront CDN for images | ✅ Available | Theme images, speaker photos, company logos |
| Organizer profile page | ✅ Available | Extend with "Watch Pairing" section |
| WebSocket infrastructure | ❌ New | Required for Epic 4 (team sync) |
| Pairing code backend | ❌ New | Required for Epic 2 |
| Apple Developer account | ❓ Needed | For App Store / TestFlight distribution |
| Xcode + watchOS SDK | ❓ Needed | Development environment for native Watch app |

---

## Out of Scope (v1)

- iPhone companion app (watchOS standalone only)
- Attendee voting or feedback from Watch
- Live Q&A display on Watch
- Speaker-facing Watch features (speakers don't get special Watch access in MVP)
- Push notifications for event reminders (rely on calendar/web notifications)
- Partner/sponsor display on Watch
- Registration from Watch (use phone/web)
- Multiple events on same day (BATbern runs one event at a time)
- Attendee login/personalization in public zone (public = truly public, no accounts)

---

## Appendix: API Endpoint Reference

### Existing Public Endpoints Used

```
GET  /api/v1/events/current?expand=sessions,speakers    → EventDetail with sessions & speakers
GET  /api/v1/events/{eventCode}?expand=sessions,speakers → EventDetail by code
GET  /api/v1/events/{eventCode}/sessions                 → Session list
GET  /api/v1/events/{eventCode}/sessions/{slug}          → Session detail
GET  /api/v1/companies/{companyName}?expand=logo         → Company with logo URL
GET  /api/v1/config                                      → Frontend config (API URLs, feature flags)
```

### New Endpoints Required

```
POST   /api/v1/watch/pair              → Exchange pairing code for pairing token
POST   /api/v1/watch/authenticate      → Exchange pairing token for access JWT
POST   /api/v1/users/{username}/watch-pairing   → Register pairing code (web frontend)
GET    /api/v1/users/{username}/watch-pairing   → Get pairing status
DELETE /api/v1/users/{username}/watch-pairing   → Unpair watch
WSS    /ws/events/{eventCode}/live              → Real-time session state channel
```

### Data Structures (from existing API)

**EventDetail** (subset relevant to Watch):
- `eventCode`, `title`, `date`, `themeImageUrl`, `venueName`, `venueAddress`
- `typicalStartTime`, `typicalEndTime`, `currentPublishedPhase`
- `sessions[]` (when expanded)

**Session** (subset relevant to Watch):
- `sessionSlug`, `title`, `description` (abstract), `sessionType`
- `startTime`, `endTime`, `room`
- `speakers[]` (when expanded)

**SessionSpeaker** (subset relevant to Watch):
- `firstName`, `lastName`, `company`, `profilePictureUrl`, `bio`, `speakerRole`

---

**END OF DOCUMENT**

---

**Next Steps:**
1. Review and approve this consolidated PRD
2. Proceed to Architecture phase (see `architecture.md`)
3. Proceed to UX Design phase (see `ux-design-specification.md`)
4. Proceed to Epic breakdown and story creation (see `epics.md`)
