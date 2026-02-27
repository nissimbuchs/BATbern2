# Story 10.8a: Moderator Presentation Page — Functional

Status: ready-for-dev

## Story

As an **event moderator**,
I want to open a single fullscreen webpage on the projector laptop and navigate through the evening using my presentation remote,
so that I can guide the audience through the BATbern event without ever touching PowerPoint — and the content is always live from the platform.

> **Note**: This story delivers a fully functional, usable presenter page. Animations (FLIP agenda transition, Ken Burns background, section spring transitions, committee stagger, break/apéro visual effects) are deferred to Story 10.8b. No Framer Motion dependency in this story.

## Acceptance Criteria

### Navigation
1. Navigating to `/present/BATbern57` loads all event data and renders the Welcome section without authentication
2. `→` / `PageDown` / `Space` advances to the next section; `←` / `PageUp` goes back one section
3. At the last section (Apéro), `→` does nothing (no wrap-around)
4. At the first section (Welcome), `←` does nothing
5. `F` toggles browser fullscreen (`document.documentElement.requestFullscreen()`); page fills viewport with no browser chrome
6. `F11` also triggers fullscreen (native browser behavior)

### Section Content
7. Welcome section shows: BATbern logo, event hashtag (`#BATbernXX`), topic title, date, venue name
8. About section shows: admin-configurable Verein purpose text + partner count (from `GET /api/v1/public/settings/presentation`)
9. Committee section shows: all active organizers with photo, name, company (from `GET /api/v1/public/organizers`); cards render immediately without stagger animation (stagger added in Story 10.8b)
10. Topic Reveal shows: topic name + topic image in a visually dominant layout
11. Agenda Preview shows all sessions in chronological order, all styled as upcoming/neutral
12. Each speaker session section shows: speaker photo, full name, company, session title; multi-speaker sessions show side-by-side cards
13. Structural sessions (`moderation`, `networking`) are excluded from speaker section generation and sidebar
14. Agenda Recap shows: pre-break sessions greyed with ✓, post-break sessions lit
15. Upcoming Events shows: next 3 future BATbern events with date and topic (placeholder if topic TBD)
16. Apéro shows: closing visual with BATbern `~` text centered; spinner animation added in Story 10.8b

### Sidebar
17. Sidebar is hidden for sections 1–4 (Welcome, About, Committee, Topic Reveal), Upcoming Events, and Apéro
18. Sidebar is visible for sections 5–N (Agenda Preview through last speaker section + Break + Agenda Recap)
19. Sidebar highlight follows moderator navigation position (not wall clock)
20. Break slot appears in sidebar as `─── Pause ───` with its scheduled time
21. Structural sessions (`moderation`, `networking`) are NOT shown in sidebar
22. Sidebar session times update silently on 60-second poll if Watch cascade changed them

### Break Behaviour
23. Pressing `B` from any section shows the break overlay (full-screen); section index unchanged
24. Pressing `B` again dismisses the break overlay; returns to exact same section
25. Break overlay shows: "Pause" heading centered in BATbern blue + "Weiter um HH:MM" derived from next post-break session start time; coffee cup and bean animations added in Story 10.8b
26. Navigating `→` from the last pre-break speaker section shows the Break section (sequential nav)
27. Navigating `→` from the Break section shows the Agenda Recap section
28. Navigating `→` from Agenda Recap shows the first post-break speaker section
29. B-key overlay and navigation Break section both render the same `BreakSlide` component

### Agenda ↔ Sidebar Transition
30. When navigating from Agenda Preview (§5) to the first speaker section, the agenda switches from center-stage to sidebar position instantly via CSS class change; FLIP spring animation added in Story 10.8b
31. When navigating from Agenda Recap to the first post-break speaker, the same instant CSS class switch occurs
32. Backward navigation correctly reverses the layout (sidebar → center-stage)

### Background
33. Topic image displayed as full-bleed background (CSS `object-fit: cover`) with dark overlay `rgba(0,0,0,0.65)`; Ken Burns zoom animation added in Story 10.8b
34. Dark overlay (`rgba(0,0,0,0.65)`) ensures text legibility in any lighting condition
35. If no topic image available, a default dark BATbern brand image is used as fallback
36. Background does NOT transition between sections — it persists throughout

### Real-time Resilience
37. If Watch operator cascades the schedule, sidebar times and break resume time update within 60 seconds without moderator action
38. If the API poll fails, the page continues functioning with the last-loaded data (graceful degradation)

### Display
39. Page renders correctly at 1920×1080 and 2560×1440
40. All text is legible at projector-viewing distances (minimum 5m); minimum font sizes: body text ≥ 1.5rem, headings ≥ 3rem, topic title ≥ 4.5rem, sidebar session text ≥ 0.75rem
41. No horizontal scrollbar at any supported resolution

### Error Handling
42. If the initial event data load fails (network error or service unavailable), the page shows a branded error screen with the event hashtag, a descriptive error message, and a "Retry" button that re-fetches all data sources; the page never shows a blank white screen on initial load failure

## Tasks / Subtasks

### Phase 1: Backend — OpenAPI spec first (ADR-006)
- [ ] Write `GET/PUT /api/v1/public/settings/presentation` endpoints in `docs/api/events.openapi.yml` (AC: #8)
  - [ ] `GET /api/v1/public/settings/presentation` → `PresentationSettingsResponse { aboutText, partnerCount }`
  - [ ] `PUT /api/v1/settings/presentation` → organizer-only; body: `PresentationSettingsRequest`
  - [ ] Regenerate frontend TypeScript types: `cd web-frontend && npm run generate:api-types`
- [ ] Write `PresentationSettingsControllerIntegrationTest` (TDD — RED phase)
  - [ ] Test: GET returns 200 + default values when no row exists
  - [ ] Test: PUT saves new values; GET returns updated values
  - [ ] Test: GET is public (no auth); PUT requires ORGANIZER role

### Phase 2: Backend — Flyway migration + domain layer
- [ ] Write `V10__add_presentation_settings.sql` in company-user-management-service (AC: #8)
  - [ ] Single-row table: `presentation_settings (id SERIAL PRIMARY KEY, about_text TEXT NOT NULL, partner_count INT NOT NULL)`
  - [ ] Insert default row: `INSERT INTO presentation_settings (about_text, partner_count) VALUES ('BATbern ist eine unabhängige Plattform...', 9)`
- [ ] Implement `PresentationSettings.java` domain entity (JPA, `@Table("presentation_settings")`)
- [ ] Implement `PresentationSettingsRepository.java` (Spring Data JPA; `findFirst()` or id=1 pattern)
- [ ] Implement `PresentationSettingsService.java` (GET: load + fallback to defaults; PUT: upsert)
- [ ] Implement `PresentationSettingsController.java` (implements generated `PresentationSettingsApi`; organizer auth on PUT)
- [ ] Add `PresentationSettingsResponse.java` DTO + `PresentationSettingsRequest.java` DTO

### Phase 3: Backend — Security + routing
- [ ] Add `permitAll` for `GET /api/v1/public/settings/presentation` in:
  - [ ] `api-gateway` `SecurityConfig.java` (near line 220 where `/api/v1/public/organizers` is permitted)
  - [ ] `company-user-management-service` `SecurityConfig.java` (all three security filter chains, near lines 77/115/160)
- [ ] Add explicit CUMS routing rule in `DomainRouter.java` for `PUT /api/v1/settings/presentation`:
  ```java
  if (cleanPath.startsWith("/api/v1/settings")) {
      return companyUserManagementServiceUrl;
  }
  ```

### Phase 4: Frontend — Core hooks and utilities
- [ ] `web-frontend/src/services/presentationService.ts` — API calls (all data ACs)
  - [ ] `getPresentationData(eventCode)` — `GET /api/v1/events/{eventCode}?include=topics,venue,sessions`
  - [ ] `getPublicOrganizers()` — `GET /api/v1/public/organizers`
  - [ ] `getUpcomingEvents()` — `GET /api/v1/events?status=AGENDA_PUBLISHED,CREATED,TOPIC_SELECTION&limit=3`
  - [ ] `getPresentationSettings()` — `GET /api/v1/public/settings/presentation`
  - [ ] `updatePresentationSettings(data)` — `PUT /api/v1/settings/presentation`
- [ ] `usePresentationData.ts` — initial load of all 4 sources + 60s poll for sessions only (ACs: #37, #38, #42)
  - [ ] Use `useQuery` for initial load; `refetchInterval: 60_000` for sessions-only refresh
  - [ ] Per-source error handling; on total initial failure → surface error state for AC #42
- [ ] `usePresentationSections.ts` — derives section array from event data (ACs: #1, #13, #26–28)
  - [ ] Filter: include `['keynote','presentation','workshop','panel_discussion']`; exclude `['moderation','networking']`
  - [ ] Insert `Break` + `AgendaRecap` sections after last pre-break speaker if break/lunch session exists
  - [ ] Return typed `PresentationSection[]`
- [ ] `useKeyboardNavigation.ts` — keyboard/remote handler (ACs: #2–6, #23–24)
  - [ ] `ArrowRight`/`PageDown`/`Space` → `goNext()`; `ArrowLeft`/`PageUp` → `goPrev()`
  - [ ] `b`/`B` → `toggleBlank()` (does NOT change section index)
  - [ ] `f` → `document.documentElement.requestFullscreen()`
  - [ ] `Escape`: if blank active → `toggleBlank()`; else exit fullscreen
  - [ ] Guard: skip if `e.target.tagName` is `INPUT` or `TEXTAREA`

### Phase 5: Frontend — Shared display components
- [ ] `TopicBackground.tsx` — full-bleed static background (ACs: #33–36)
  - [ ] `<img>` with CSS `object-fit: cover`, `position: absolute`, `inset: 0`, `width: 100%`, `height: 100%`
  - [ ] Dark overlay `<div>` with `rgba(0,0,0,0.65)` absolutely positioned above image
  - [ ] Fallback to `/images/batbern-default-bg.jpg` if no topic image
  - [ ] **No animation in this story** — Ken Burns zoom added in Story 10.8b
- [ ] `AgendaView.tsx` — agenda list; CSS class drives center-stage vs sidebar layout (ACs: #11, #14, #17–22)
  - [ ] Props: `sessions`, `completedSessions: string[]`, `currentSessionSlug?: string`, `layout: 'center' | 'sidebar'`
  - [ ] CSS class switches between `styles.center` and `styles.sidebar` — instant, no animation
  - [ ] Break entry: `─── Pause ───`; completed: grey+✓; current: BATbern blue accent; upcoming: 60% opacity
- [ ] `SpeakerCard.tsx` — single speaker (AC: #12)
- [ ] `TwoSpeakerCard.tsx` — side-by-side multi-speaker (AC: #12)
- [ ] `SectionDots.tsx` — subtle dot-per-section progress indicator (bottom center)
- [ ] `BlankOverlay.tsx` — B-key overlay wrapping `BreakSlide` (ACs: #23–24, #29)
  - [ ] Simple conditional render with CSS opacity transition (no Framer Motion AnimatePresence)
  - [ ] Fixed full-screen, z-index above everything

### Phase 6: Frontend — Slide components
- [ ] `slides/WelcomeSlide.tsx` — logo, `#BATbernXX`, topic title, date, venue (AC: #7)
- [ ] `slides/AboutSlide.tsx` — `aboutText` + `partnerCount` (AC: #8)
- [ ] `slides/CommitteeSlide.tsx` — organizer card grid, immediate render (AC: #9)
  - [ ] **No stagger animation** — stagger added in Story 10.8b
- [ ] `slides/TopicRevealSlide.tsx` — topic title + image emphasis (AC: #10)
- [ ] `slides/AgendaPreviewSlide.tsx` — `AgendaView` in `layout='center'` (AC: #11)
- [ ] `slides/SessionSlide.tsx` — `SpeakerCard` or `TwoSpeakerCard` (AC: #12)
- [ ] `slides/BreakSlide.tsx` — simplified break visual (AC: #25)
  - [ ] "Pause" heading, large, centered, BATbern blue
  - [ ] "Weiter um HH:MM" — derived from `firstPostBreakSession.scheduledStartTime` via `date-fns format()`
  - [ ] Background: topic image + heavier overlay `rgba(0,0,0,0.85)`
  - [ ] **No coffee cup, steam, or bean animations** — added in Story 10.8b
- [ ] `slides/AgendaRecapSlide.tsx` — `AgendaView` with `completedSessions` = pre-break slugs (AC: #14)
- [ ] `slides/UpcomingEventsSlide.tsx` — next 3 events as cards (AC: #15)
- [ ] `slides/AperoSlide.tsx` — closing visual with BATbern `~` text (AC: #16)
  - [ ] **No spinner animation** — added in Story 10.8b

### Phase 7: Frontend — PresentationPage and routing
- [ ] `web-frontend/src/pages/PresentationPage.tsx` — route owner, section state, layout orchestration (all ACs)
  - [ ] State: `currentIndex`, `isBlankActive`, `direction` (forward/back for future 10.8b transitions)
  - [ ] Section switch: instant — just render `sections[currentIndex]` with no transition wrapper
  - [ ] AgendaView visibility: `agendaLayout = (section.type === 'session' || section.type === 'break' || section.type === 'recap') ? 'sidebar' : 'center'` — CSS class switches instantly
  - [ ] Error state: if `usePresentationData` returns error on initial load → render branded error screen with retry (AC: #42)
  - [ ] No sidebar/nav — pure fullscreen canvas
- [ ] Add public route in `web-frontend/src/App.tsx`:
  ```tsx
  <Route path="/present/:eventCode" element={<PresentationPage />} />
  ```
  — lazy-imported, NOT inside ProtectedRoute (near line ~277 with other public routes)

### Phase 8: Frontend — Admin UI for presentation settings
- [ ] Add "Presentation Settings" card to organizer admin settings page
  - [ ] `<TextField multiline>` for `aboutText`
  - [ ] `<TextField type="number">` for `partnerCount`
  - [ ] Save button → `PUT /api/v1/settings/presentation`
  - [ ] i18n keys: `admin.presentationSettings.*`

### Phase 9: i18n + tests
- [ ] Add i18n keys to `public/locales/en/common.json` and `de/common.json`:
  - [ ] `navigation.presentationMode`
  - [ ] `presentation.errorTitle`, `presentation.errorMessage`, `presentation.retryButton`
- [ ] Write unit tests:
  - [ ] `usePresentationSections.test.ts` — section list generation with and without break
  - [ ] `useKeyboardNavigation.test.ts` — keyboard handler logic, boundary guards
  - [ ] `PresentationPage.test.tsx` — smoke: renders Welcome section; keyboard nav advances; renders error screen on all-API failure
- [ ] Write Playwright E2E test (`web-frontend/e2e/presentation.spec.ts`):
  - [ ] Navigate to `/present/BATbernXX`; assert Welcome renders without auth wall
  - [ ] Press `ArrowRight` 4 times; assert sidebar appears
  - [ ] Assert no horizontal scrollbar at 1920×1080 viewport
- [ ] Manual QA checklist:
  - [ ] Render at 1920×1080 and 2560×1440 — no horizontal scrollbar (ACs: #39, #41)
  - [ ] Font legibility: body ≥ 1.5rem, headings ≥ 3rem, topic title ≥ 4.5rem, sidebar ≥ 0.75rem (AC: #40)
- [ ] Type-check: `cd web-frontend && npm run type-check` passes with zero errors

## Definition of Done

- [ ] OpenAPI spec for `GET/PUT /api/v1/public/settings/presentation` committed before implementation (ADR-006)
- [ ] TDD: `PresentationSettingsControllerIntegrationTest` written first
- [ ] Route `/present/BATbernXX` loads without authentication and renders Welcome section
- [ ] All 42 ACs pass
- [ ] Break flow works: `→` navigates pre-break speakers → Break → AgendaRecap → post-break speakers
- [ ] B-key overlay shows/hides BreakSlide without changing section index
- [ ] 60-second poll updates sidebar times without user action
- [ ] Error screen renders on initial load failure; Retry re-fetches data
- [ ] Renders correctly at 1920×1080 and 2560×1440; no horizontal scrollbar
- [ ] No console errors during normal navigation flow
- [ ] Type-check passes, no TypeScript errors; Checkstyle passes

## Dev Notes

### No Framer Motion in this story

Framer Motion is **not installed** and must **not** be added in Story 10.8a. All animation work is deferred to Story 10.8b. Specifically:

- `TopicBackground`: static `<img>` only
- `AgendaView` layout switch: CSS class change, instant
- Section transitions: no transition — swap render immediately
- `CommitteeSlide`: static card grid, no stagger
- `BlankOverlay`: CSS `opacity` or `visibility` toggle
- `BreakSlide`: text only, no coffee cup/steam/beans
- `AperoSlide`: static `~` text, no spinner

Use `direction` state in `PresentationPage` now to track forward/back — Story 10.8b will use it for directional spring transitions.

### AgendaView layout switching (no FLIP)

```tsx
// PresentationPage.tsx
const agendaLayout = ['session', 'break', 'recap'].includes(section.type) ? 'sidebar' : 'center';

// AgendaView switches CSS class instantly:
<AgendaView layout={agendaLayout} ... />
```

```css
/* AgendaView.module.css */
.center { width: 560px; margin: 0 auto; font-size: 1.1rem; }
.sidebar { width: 200px; position: absolute; left: 2rem; top: 50%; transform: translateY(-50%); font-size: 0.75rem; opacity: 0.85; }
```

Story 10.8b will replace the instant class switch with a Framer Motion `motion.div layout` FLIP.

### Break Resume Time

```tsx
const firstPostBreakSession = sessions.find(s =>
  s.scheduledStartTime > breakSession.scheduledEndTime &&
  ['keynote','presentation','workshop','panel_discussion'].includes(s.sessionType)
);
const resumeTime = firstPostBreakSession
  ? format(new Date(firstPostBreakSession.scheduledStartTime), 'HH:mm')
  : null;
```

`date-fns` is already installed.

### 60-second Poll

```tsx
const { data: sessionsRefresh } = useQuery({
  queryKey: ['presentation-sessions', eventCode],
  queryFn: () => presentationService.getSessions(eventCode),
  refetchInterval: 60_000,
  refetchIntervalInBackground: true,
});
```

Do NOT use `setInterval` inside `useEffect` — memory leak on unmount.

### Flyway migration version

- company-user-management-service: latest is `V9__Remove_denormalized_attendee_data.sql` → use **`V10__add_presentation_settings.sql`**

### Security config pattern

```java
.requestMatchers(HttpMethod.GET, "/api/v1/public/settings/presentation").permitAll()
```

Add in all three CUMS filter chains (lines ~77, ~115, ~160) and in api-gateway SecurityConfig (~line 220).

### App.tsx route placement

Add at line ~277 with other public/speaker-portal routes. Use `React.lazy()`:
```tsx
const PresentationPage = React.lazy(() => import('./pages/PresentationPage'));
// ...
<Route path="/present/:eventCode" element={<PresentationPage />} />
```

### References

- Epic 10 PRD: [Source: docs/prd/epic-10-additional-stories.md#story-108a]
- DomainRouter: [Source: api-gateway/src/main/java/ch/batbern/gateway/routing/DomainRouter.java:99]
- API Gateway security config: [Source: api-gateway/src/.../config/SecurityConfig.java:220]
- CUMS security config: [Source: company-user-management-service/src/.../config/SecurityConfig.java:77]
- Public Organizer pattern: [Source: company-user-management-service/.../controller/PublicOrganizerController.java]
- App.tsx public route placement: [Source: web-frontend/src/App.tsx:277]
- Story 10.8b (animations): [Source: _bmad-output/implementation-artifacts/10-8b-moderator-presentation-page-animations.md]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
