# Feature Spec: Moderator Presentation Page

**Status:** Ready for implementation
**Origin:** Brainstorming session 2026-02-27 (Mary — Analyst)
**Epic:** New feature — Epic 10 candidate or standalone
**Route:** `/present/:eventCode`

---

## Story

As the **event moderator**,
I want to open a single fullscreen webpage on the projector laptop and navigate through the evening using my presentation remote,
so that I can guide the audience through the BATbern event without ever touching PowerPoint — and the content is always live from the platform.

---

## Background & Context

Every BATbern event, one organizer moderates the evening using a manually-maintained PowerPoint deck containing: BATbern branding, the Verein purpose, the organizer committee, the topic image, the full agenda, a break slide, upcoming events, and an Apéro closing. This deck requires manual updates before each event (speaker names, dates, topics, photos).

All this data already lives in the BATbern platform and is accessible via public APIs. This feature replaces the PowerPoint with an immersive, web-native experience driven by platform data.

**Key design decisions from brainstorming:**
- Single page, single URL — no separate control/display split
- Fullscreen; navigated by keyboard / presentation remote (← →, B, F)
- All data public — no authentication required
- Data loaded at page start + 60-second background poll (picks up Watch schedule cascades)
- Visual approach: topic image as persistent Ken Burns background; speaker info as animated flying cards; agenda as ambient sidebar
- Break = `B` key → branded animated overlay (not a black screen); press `B` again → return to current section
- Navigation break = sequential section in the flow, with an Agenda Recap section after the break

---

## Navigation Map

Sections are generated dynamically at page load from event data.

```
Static sections (always present, fixed order):
  1  Welcome
  2  About BATbern
  3  Organizer Committee
  4  Topic Reveal

Dynamic sections (derived from event sessions):
  5  Agenda Preview          ← full agenda centered; all sessions upcoming/neutral
  6..N  Speaker Sessions     ← one section per PRESENTATION/KEYNOTE/WORKSHOP session
     [Break]                 ← inserted if event has a session with sessionType=break or lunch
     [Agenda Recap]          ← inserted immediately after Break section
  N+1  Upcoming Events
  N+2  Apéro
```

**Section sequence for a typical afternoon event (e.g. BATbern57):**

| # | Section | Center Stage | Sidebar |
|---|---------|-------------|---------|
| 1 | Welcome | BATbern logo + hashtag + topic title + date + venue | Hidden |
| 2 | About BATbern | Verein purpose (admin text) + partner count staggered cards | Hidden |
| 3 | Committee | 4 organizer photo cards, staggered fly-in | Hidden |
| 4 | Topic Reveal | Topic title large + image comes forward | Hidden |
| 5 | **Agenda Preview** | Full agenda centered (all upcoming/neutral) | Hidden |
| 6 | Session: Swiss Post | Novoselec speaker card flies in ← **MAGIC: agenda morphs to sidebar** | Sidebar appears, Swiss Post lit |
| 7 | Session: SBB | Masen card | SBB lit |
| 8 | **Break** (nav) | Break animation (same visual as B-key overlay) | Pause lit |
| 9 | **Agenda Recap** | Full agenda re-centered; Swiss Post/SBB/Pause greyed; Mobiliar/PostFinance lit | Sidebar hidden (agenda is center stage) |
| 10 | Session: Mobiliar | Cavadini card ← **MAGIC: agenda morphs back to sidebar** | Mobiliar lit |
| 11 | Session: PostFinance | Halbeisen + Bouaaoud two-card layout | PostFinance lit |
| 12 | Upcoming Events | Next 3 BATbern event cards, staggered | Hidden |
| 13 | Apéro | BATbern spinner + closing visual | Hidden |

**Rules for dynamic section generation:**
- Include sessions with `sessionType` in `[keynote, presentation, workshop, panel_discussion]`
- Exclude `sessionType` in `[moderation, networking]` — these are structural (Begrüssung, Abschluss)
- `sessionType=break` or `sessionType=lunch` → generate a Break section + Agenda Recap section pair
- Sessions ordered by `scheduledStartTime` ascending
- If no break session exists in agenda → no Break / Agenda Recap sections generated

---

## Two Break Behaviors

### B-Key Break (overlay — available from any section at any time)
```
Current state: showing any section, any time during the event
[B pressed] → break overlay fades IN over current section (section index unchanged)
[B pressed again] → overlay fades OUT, returns to exact same section
```
- Does NOT change the section index
- Used for unexpected pauses, AV issues, anything requiring a moment

### Navigation Break (sequential section in the flow)
```
[→] from last pre-break session → Section 8: Break screen (same visual as B overlay)
[→] from Break section          → Section 9: Agenda Recap (center stage, done sessions greyed)
[→] from Agenda Recap           → Section 10: First post-break speaker card
```
- IS a section change (modifies section index)
- Used for the planned break: moderator announces break, advance three times to resume

---

## The Two "Agenda Takes Center Stage" Moments

| Section | Content | `completedSessions` prop |
|---------|---------|--------------------------|
| **Agenda Preview** (§5) | All sessions neutral/upcoming — "here is everything tonight" | `[]` |
| **Agenda Recap** (§9) | Pre-break sessions greyed, post-break sessions lit — "here is what remains" | `[...slugs of completed sessions]` |

Both render the same `AgendaFullView` component with different `completedSessions`.

---

## The "Agenda Morphs to Sidebar" Animation

This is the visual centrepiece of the experience. It occurs twice:
1. Navigating **from Agenda Preview → first speaker session** (§5 → §6)
2. Navigating **from Agenda Recap → first post-break speaker session** (§9 → §10)

**Implementation: Framer Motion `layout` prop**

The `AgendaView` component is never unmounted — it transitions between two CSS layout states:

```tsx
// State A: CENTER STAGE (sections 5 and 9)
// State B: SIDEBAR (sections 6-11)

<motion.div
  layout
  transition={{ type: "spring", stiffness: 100, damping: 22, mass: 1 }}
  className={agendaIsSidebar ? styles.agendaSidebar : styles.agendaCenterStage}
>
  <AgendaView sessions={sessions} completedSessions={completedSessions} />
</motion.div>
```

Framer calculates the FLIP animation automatically. While the agenda morphs left, the first speaker card enters from the right via `AnimatePresence`.

**CSS states:**

```css
.agendaCenterStage {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  width: 560px;
  font-size: 1.1rem;
}

.agendaSidebar {
  position: absolute;
  left: 2rem;
  top: 50%;
  transform: translateY(-50%);
  width: 200px;
  font-size: 0.75rem;
  opacity: 0.85;
}
```

---

## Component Architecture

```
web-frontend/src/
  pages/
    PresentationPage.tsx           ← route /present/:eventCode; owns section state

  components/presentation/
    usePresentationSections.ts     ← generates section list from event data
    usePresentationData.ts         ← load at start + 60s poll
    useKeyboardNavigation.ts       ← keyboard/remote event listener hook
    BlankOverlay.tsx               ← B-key break overlay (animated)
    SectionDots.tsx                ← subtle progress indicator (bottom center)
    TopicBackground.tsx            ← full-bleed image + Ken Burns + dark overlay
    AgendaView.tsx                 ← renders agenda list; works in both layout states
    SpeakerCard.tsx                ← speaker photo + name + company + title
    TwoSpeakerCard.tsx             ← side-by-side layout for multi-speaker sessions

    slides/
      WelcomeSlide.tsx             ← BATbern logo, hashtag, topic title, date, venue
      AboutSlide.tsx               ← Verein purpose text + partner count
      CommitteeSlide.tsx           ← organizer photo grid (from /api/v1/public/organizers)
      TopicRevealSlide.tsx         ← topic title + image emphasis
      AgendaPreviewSlide.tsx       ← wraps AgendaView in center-stage layout
      SessionSlide.tsx             ← wraps SpeakerCard or TwoSpeakerCard
      BreakSlide.tsx               ← coffee break animation (also used by BlankOverlay)
      AgendaRecapSlide.tsx         ← wraps AgendaView with completedSessions
      UpcomingEventsSlide.tsx      ← next 3 future events
      AperoSlide.tsx               ← closing visual + BATbern spinner
```

---

## Section Transition Animations

All content transitions use `AnimatePresence mode="wait"` with a directional spring:

```tsx
// Navigating forward (→)
initial:  { x: 80, opacity: 0 }
animate:  { x: 0,  opacity: 1 }
exit:     { x: -80, opacity: 0 }

// Navigating backward (←)
initial:  { x: -80, opacity: 0 }
animate:  { x: 0,   opacity: 1 }
exit:     { x: 80,  opacity: 0 }

transition: { type: "spring", stiffness: 120, damping: 20 }
// Total duration ≈ 350ms
```

**Within-section stagger animations (Committee, About, UpcomingEvents):**
```tsx
// Each card/item
initial:  { y: 30, opacity: 0 }
animate:  { y: 0,  opacity: 1, transition: { delay: index * 0.12 } }
```

---

## Topic Background

```tsx
// TopicBackground.tsx
// topic.imageUrl from event data, falls back to a default dark abstract image

<div className={styles.backgroundWrapper}>
  <motion.img
    src={topic.imageUrl}
    className={styles.backgroundImage}
    animate={{ scale: [1.0, 1.06, 1.0] }}
    transition={{ duration: 30, repeat: Infinity, ease: "easeInOut" }}
  />
  <div className={styles.darkOverlay} /> {/* bg: rgba(0,0,0,0.65) */}
</div>
```

The background image persists across all sections — it is never part of the transition. Only the content layer transitions.

---

## Break Animation (BreakSlide / BlankOverlay)

Used both as a full section (§8, nav break) and as the B-key overlay.

Visual elements:
- Background: same topic image with heavier overlay (rgba(0,0,0,0.85)) + subtle warm amber tint
- **BATbern `~` spinner**: the wave element from the BATbern logo, CSS `@keyframes` rotation, 3s loop
- **Animated coffee cup**: CSS steam lines rising + fading, 2s loop
- **Floating coffee beans**: 8–12 `<span>` elements, staggered upward float + gentle sway (reference to BATbern57 PPT slide 6)
- **"Pause"** text: large, centered, BATbern blue
- **Resume time**: `"Weiter um HH:MM"` — derived from `scheduledStartTime` of the first post-break session

```tsx
// Derive resume time from sessions
const firstPostBreakSession = sessions.find(s =>
  s.scheduledStartTime > breakSession.scheduledEndTime &&
  ['keynote','presentation','workshop','panel_discussion'].includes(s.sessionType)
);
const resumeTime = firstPostBreakSession
  ? format(new Date(firstPostBreakSession.scheduledStartTime), 'HH:mm')
  : null;
```

**B-key overlay (BlankOverlay.tsx):** wraps `BreakSlide` in a fixed full-screen overlay:
```tsx
<AnimatePresence>
  {isBlankActive && (
    <motion.div
      className={styles.overlay}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.3 }}
    >
      <BreakSlide resumeTime={resumeTime} />
    </motion.div>
  )}
</AnimatePresence>
```

---

## Keyboard / Remote Control Mapping

| Key(s) | Action |
|--------|--------|
| `ArrowRight`, `PageDown`, `Space` | Advance to next section |
| `ArrowLeft`, `PageUp` | Go back one section |
| `b`, `B` | Toggle break overlay (does NOT change section index) |
| `f`, `F11` | Toggle browser fullscreen (`document.documentElement.requestFullscreen()`) |
| `Escape` | If blank active → un-blank; else → exit fullscreen |

Implemented via `useKeyboardNavigation.ts`:
```ts
useEffect(() => {
  const handler = (e: KeyboardEvent) => {
    if (['INPUT','TEXTAREA'].includes((e.target as Element)?.tagName)) return;
    switch (e.key) {
      case 'ArrowRight': case 'PageDown': case ' ': goNext(); break;
      case 'ArrowLeft':  case 'PageUp':             goPrev(); break;
      case 'b': case 'B':                           toggleBlank(); break;
      case 'f':                                     toggleFullscreen(); break;
      case 'Escape':   if (isBlankActive) toggleBlank(); break;
    }
  };
  window.addEventListener('keydown', handler);
  return () => window.removeEventListener('keydown', handler);
}, [goNext, goPrev, toggleBlank, isBlankActive]);
```

---

## Sidebar (AgendaView in sidebar state)

Visible during sections 6 through N (speaker sessions + break + recap). Hidden elsewhere.

Content: only `PRESENTATION`, `KEYNOTE`, `WORKSHOP`, `PANEL_DISCUSSION` sessions + break entry.
Excludes `MODERATION`, `NETWORKING` sessions (structural: Begrüssung/Abschluss).

```
  ✓  Swiss Post — Novoselec        ← completed (grey)
  ▶  SBB — Masen                   ← current (white + left accent bar, BATbern blue)
     ─── Pause ────────────────
     Mobiliar — Cavadini            ← upcoming (white/60% opacity)
     PostFinance — Halbeisen et al.
```

Sidebar highlight follows **moderator navigation position** (Option B) — not wall clock.
Sidebar session times are updated from the 60-second poll (picks up Watch cascade shifts silently).

---

## Data Sources

All data is **public — no authentication required**.

| Data | Endpoint | Fields used |
|------|----------|-------------|
| Event detail | `GET /api/v1/events/{eventCode}?include=topics,venue,sessions` | `title`, `eventCode`, `date`, `venueName`, `venueAddress`, `topic.name`, `topic.imageUrl`, `topic.description`, `sessions[]` |
| Session speakers | Included in sessions expand | `firstName`, `lastName`, `company`, `profilePictureUrl`, `bio`, `speakerRole`, `sessionType`, `scheduledStartTime`, `scheduledEndTime` |
| Organizer committee | `GET /api/v1/public/organizers` | `firstName`, `lastName`, `email`, `bio`, `profilePictureUrl`, `companyId` |
| Upcoming events | `GET /api/v1/events?status=AGENDA_PUBLISHED,CREATED,TOPIC_SELECTION&limit=3` (future dates only) | `eventCode`, `title`, `date`, `topic.name` |
| About BATbern text | New: `GET /api/v1/public/settings/presentation` | `aboutText`, `partnerCount` (or derive from partner list) |

**60-second poll:** only re-fetches sessions for the current event (`/events/{eventCode}?include=sessions`) to pick up Watch-driven schedule cascades. Does not re-render the current section visually unless data has changed.

---

## New Backend Work

### Minimal: One New Public Settings Endpoint

All data is available except the admin-configurable "About BATbern" text.

**New endpoint** in `company-user-management-service` (or `event-management-service`):
```
GET  /api/v1/public/settings/presentation
→ { "aboutText": "BATbern ist eine Plattform...", "partnerCount": 9 }

PUT  /api/v1/settings/presentation          (requires organizer auth)
→ body: { "aboutText": "...", "partnerCount": 9 }
```

**Storage:** single-row table `presentation_settings` (or add columns to existing settings table if one exists). If none exists: `CREATE TABLE presentation_settings (id SERIAL PRIMARY KEY, about_text TEXT, partner_count INT)` with a single row.

**Admin UI:** add a "Presentation Settings" card to the existing organizer admin settings page with a `<TextField multiline>` for `aboutText` and a number field for `partnerCount`.

If scope must be minimal for v1: hardcode `aboutText` in the frontend as a constant — it hasn't changed in 20 years. Ship the admin-configurable version in v2.

---

## Acceptance Criteria

### Navigation
1. Navigating to `/present/BATbern57` loads all event data and renders the Welcome section
2. `→` / `PageDown` / `Space` advances to the next section; `←` / `PageUp` goes back
3. At the last section (Apéro), `→` does nothing (no wrap-around)
4. At the first section (Welcome), `←` does nothing
5. `F` toggles fullscreen; page fills the viewport with no browser chrome visible
6. Pressing `F11` also toggles fullscreen (native browser behaviour)

### Section Content
7. Welcome section shows: BATbern logo, event hashtag (#BATbernXX), topic title, date, venue name
8. About section shows: admin-configurable Verein purpose text + partner count
9. Committee section shows: all active organizers with photo, name, company (from `/api/v1/public/organizers`)
10. Topic Reveal shows: topic name + topic image (if available) in a visually dominant layout
11. Agenda Preview shows all sessions in chronological order, all styled as upcoming
12. Each speaker session section shows: speaker photo, full name, company, session title. Multi-speaker sessions show side-by-side cards
13. Structural sessions (`moderation`, `networking`) are excluded from speaker section generation and sidebar
14. Agenda Recap shows: pre-break sessions greyed with ✓, post-break sessions lit
15. Upcoming Events shows: next 3 future BATbern events with date and topic (or placeholder if topic TBD)
16. Apéro shows: closing visual with BATbern spinner animation

### Sidebar
17. Sidebar is hidden for sections 1–4, Upcoming Events, Apéro
18. Sidebar is visible for sections 5–N (Agenda Preview through last speaker session)
19. Sidebar highlight follows moderator's navigation position (not wall clock)
20. Break slot appears in sidebar as `─── Pause ───` with its scheduled time
21. Structural sessions are NOT shown in sidebar
22. Sidebar session times update silently on 60-second poll if Watch cascade changed them

### Break Behaviour
23. Pressing `B` from any section shows the break overlay (full-screen, animated)
24. Pressing `B` again dismisses the break overlay and returns to the same section
25. Break overlay shows: BATbern `~` spinner animation, animated coffee cup with steam, floating coffee beans, "Pause" text, "Weiter um HH:MM" derived from next post-break session start time
26. Navigating `→` from the last pre-break speaker section shows the Break section (§8)
27. Navigating `→` from the Break section shows the Agenda Recap section (§9)
28. Navigating `→` from Agenda Recap shows the first post-break speaker section (§10)
29. Break overlay (`B` key) and navigation Break section share the same visual component

### Agenda ↔ Sidebar Animation
30. When navigating from Agenda Preview (§5) to the first speaker section (§6), the agenda view animates from center stage to the left sidebar position using Framer Motion `layout`
31. When navigating from Agenda Recap (§9) to the first post-break speaker (§10), the same animation occurs
32. The transition duration is ≈350ms, spring physics, not jarring
33. Navigating backwards reverses the animation correctly

### Background
34. The topic's image is displayed as a full-bleed background with a Ken Burns subtle slow zoom (scale 1.0 → 1.06, 30s loop)
35. A dark overlay (rgba(0,0,0,0.65)) ensures all text is legible in any lighting condition
36. If no topic image is available, a default dark BATbern brand image is used
37. The background does NOT transition between sections — it persists

### Real-time Resilience
38. If the Watch operator cascades the schedule (shifts session times), the sidebar times and break resume time update within 60 seconds without any moderator action
39. If the API poll fails, the page continues to function with the last-loaded data

### Accessibility & Display
40. The page renders correctly at 1920×1080 (standard projector resolution) and 2560×1440
41. All text remains legible at projector-viewing distances (minimum 5m)
42. No horizontal scrollbar at any supported resolution

---

## Non-Functional Requirements

| # | Requirement |
|---|-------------|
| NFR1 | Initial page load (all data fetched): < 2 seconds on venue WiFi |
| NFR2 | Section transition animation: ≤ 350ms, no dropped frames on a standard laptop |
| NFR3 | Background poll interval: 60 seconds; does not block UI thread |
| NFR4 | Works in Chrome, Safari, Firefox (modern versions) |
| NFR5 | No console errors during normal navigation flow |
| NFR6 | Page is usable if one of the three API calls fails (graceful degradation per section) |

---

## Out of Scope (v1)

- Attendee-facing companion view (phone mode)
- QR code / materials section (presentations only available post-event)
- Real-time WebSocket sync (60s polling is sufficient)
- Presenter notes panel
- Timer/countdown overlay
- Authentication or access control of any kind
- PDF/print export of the presentation
- Custom section reordering by organizer
- Animated speaker introductions triggered by the Watch (future: Watch could auto-advance presentation)

---

## File Output Location

```
web-frontend/src/pages/PresentationPage.tsx
web-frontend/src/components/presentation/   (all components above)
```

New backend (optional v1 / required v2):
```
services/company-user-management-service/
  controller/PresentationSettingsController.java
  service/PresentationSettingsService.java
  dto/PresentationSettingsResponse.java
  repository/PresentationSettingsRepository.java
  domain/PresentationSettings.java
  resources/db/migration/V{next}__add_presentation_settings.sql
```

---

**END OF SPEC**
