# Story 10.8: Moderator Presentation Page

Status: ready-for-dev

## Story

As an **event moderator**,
I want to open a single fullscreen webpage on the projector laptop and navigate through the evening using my presentation remote,
so that I can guide the audience through the BATbern event without ever touching PowerPoint — and the content is always live from the platform.

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
9. Committee section shows: all active organizers with photo, name, company (from `GET /api/v1/public/organizers`)
10. Topic Reveal shows: topic name + topic image in a visually dominant layout
11. Agenda Preview shows all sessions in chronological order, all styled as upcoming/neutral
12. Each speaker session section shows: speaker photo, full name, company, session title; multi-speaker sessions show side-by-side cards
13. Structural sessions (`moderation`, `networking`) are excluded from speaker section generation and sidebar
14. Agenda Recap shows: pre-break sessions greyed with ✓, post-break sessions lit
15. Upcoming Events shows: next 3 future BATbern events with date and topic (placeholder if topic TBD)
16. Apéro shows: closing visual with BATbern `~` spinner animation

### Sidebar
17. Sidebar is hidden for sections 1–4 (Welcome, About, Committee, Topic Reveal), Upcoming Events, and Apéro
18. Sidebar is visible for sections 5–N (Agenda Preview through last speaker session + Break + Agenda Recap)
19. Sidebar highlight follows moderator navigation position (not wall clock)
20. Break slot appears in sidebar as `─── Pause ───` with its scheduled time
21. Structural sessions (`moderation`, `networking`) are NOT shown in sidebar
22. Sidebar session times update silently on 60-second poll if Watch cascade changed them

### Break Behaviour
23. Pressing `B` from any section shows the break overlay (full-screen, animated); section index unchanged
24. Pressing `B` again dismisses the break overlay; returns to exact same section
25. Break overlay shows: BATbern `~` spinner, animated coffee cup with steam, floating coffee beans, "Pause" text, "Weiter um HH:MM" derived from next post-break session start time
26. Navigating `→` from the last pre-break speaker section shows the Break section (sequential nav)
27. Navigating `→` from the Break section shows the Agenda Recap section
28. Navigating `→` from Agenda Recap shows the first post-break speaker section
29. B-key overlay and navigation Break section both render the same `BreakSlide` component

### Agenda ↔ Sidebar Animation
30. When navigating from Agenda Preview (§5) to the first speaker section, the agenda animates from center stage to left sidebar position using Framer Motion `layout` prop (FLIP)
31. When navigating from Agenda Recap to the first post-break speaker, the same FLIP animation occurs
32. Transition duration ≈350ms, spring physics (`stiffness: 100, damping: 22, mass: 1`)
33. Navigating backwards reverses the animation correctly

### Background
34. Topic image displayed as full-bleed background with Ken Burns zoom (scale `1.0 → 1.06`, 30s loop)
35. Dark overlay (`rgba(0,0,0,0.65)`) ensures text legibility in any lighting condition
36. If no topic image available, a default dark BATbern brand image is used as fallback
37. Background does NOT transition between sections — it persists throughout

### Real-time Resilience
38. If Watch operator cascades the schedule, sidebar times and break resume time update within 60 seconds without moderator action
39. If the API poll fails, the page continues functioning with the last-loaded data (graceful degradation)

### Display
40. Page renders correctly at 1920×1080 and 2560×1440
41. All text is legible at projector-viewing distances (minimum 5m); minimum font sizes: body text ≥ 1.5rem, headings ≥ 3rem, topic title ≥ 4.5rem, sidebar session text ≥ 0.75rem
42. No horizontal scrollbar at any supported resolution

### Error Handling
43. If the initial event data load fails (network error or service unavailable), the page shows a branded error screen with the event hashtag, a descriptive error message, and a "Retry" button that re-fetches all data sources; the page never shows a blank white screen on initial load failure

## Tasks / Subtasks

### Phase 1: Backend — OpenAPI spec first (ADR-006)
- [ ] Write `GET/PUT /api/v1/public/settings/presentation` endpoints in `docs/api/events.openapi.yml` (AC: #8)
  - [ ] `GET /api/v1/public/settings/presentation` → `PresentationSettingsResponse { aboutText, partnerCount }`
  - [ ] `PUT /api/v1/settings/presentation` → organizer-only; body: `PresentationSettingsRequest`
  - [ ] Regenerate frontend TypeScript types: `cd web-frontend && npm run generate:api-types`
- [ ] Write `PresentationSettingsControllerIntegrationTest` (TDD — RED phase) (AC: all backend ACs)
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
  - [ ] `api-gateway` `SecurityConfig.java` (line ~220 where `/api/v1/public/organizers` is permitted)
  - [ ] `company-user-management-service` `SecurityConfig.java` (same pattern; both `isAuthenticated`-chain and other chains)
- [ ] Add explicit CUMS routing rule in `DomainRouter.java` for `PUT /api/v1/settings/presentation` — this path is NOT under `/api/v1/public/` so it does not match the existing `startsWith("/api/v1/public")` → CUMS rule; add `startsWith("/api/v1/settings")` → CUMS before the catch-all rule (near line 120):
  ```java
  if (cleanPath.startsWith("/api/v1/settings")) {
      return companyUserManagementServiceUrl;
  }
  ```

### Phase 4: Frontend — Install Framer Motion
- [ ] `cd web-frontend && npm install framer-motion`
  - [ ] **CRITICAL**: Framer Motion is NOT currently installed. This is a new dependency.
  - [ ] Verify it does not conflict with existing MUI / Emotion stack
  - [ ] Add to `package.json` and commit

### Phase 5: Frontend — Core hooks and utilities
- [ ] `web-frontend/src/services/presentationService.ts` — API calls (AC: all data ACs)
  - [ ] `getPresentationData(eventCode)` — calls `GET /api/v1/events/{eventCode}?include=topics,venue,sessions`
  - [ ] `getPublicOrganizers()` — calls `GET /api/v1/public/organizers`
  - [ ] `getUpcomingEvents()` — calls `GET /api/v1/events?status=AGENDA_PUBLISHED,CREATED,TOPIC_SELECTION&limit=3`
  - [ ] `getPresentationSettings()` — calls `GET /api/v1/public/settings/presentation`
  - [ ] `updatePresentationSettings(data)` — calls `PUT /api/v1/settings/presentation`
- [ ] `usePresentationData.ts` — initial load of all 4 data sources + 60-second poll for sessions only (AC: #38, #39)
  - [ ] Use `useQuery` (React Query) for initial load; `useInterval` for the 60s sessions-only poll
  - [ ] Poll only re-fetches `GET /api/v1/events/{eventCode}?include=sessions` (lightweight)
  - [ ] Graceful degradation: per-source error handling; page never crashes on single API failure
- [ ] `usePresentationSections.ts` — derives section list from event data (AC: #1, #13, #26–28)
  - [ ] Filter sessions: include `['keynote','presentation','workshop','panel_discussion']`; exclude `['moderation','networking']`
  - [ ] Insert `Break` section + `AgendaRecap` section after the break speaker if a `break`/`lunch` session exists
  - [ ] Return typed `PresentationSection[]` array with section type and associated data
- [ ] `useKeyboardNavigation.ts` — keyboard/remote event listener (AC: #2–6, #23–24)
  - [ ] `ArrowRight`/`PageDown`/`Space` → `goNext()`; `ArrowLeft`/`PageUp` → `goPrev()`
  - [ ] `b`/`B` → `toggleBlank()` (does NOT change section index)
  - [ ] `f` → `toggleFullscreen()` via `document.documentElement.requestFullscreen()`
  - [ ] `Escape`: if blank active → `toggleBlank()`; otherwise exit fullscreen
  - [ ] Guard: skip if `e.target.tagName` is `INPUT` or `TEXTAREA`

### Phase 6: Frontend — Shared display components
- [ ] `TopicBackground.tsx` — full-bleed Ken Burns background (AC: #34–37)
  - [ ] `motion.img` with `animate={{ scale: [1.0, 1.06, 1.0] }}`, `transition: { duration: 30, repeat: Infinity }`
  - [ ] Dark overlay `rgba(0,0,0,0.65)` above the image
  - [ ] Fallback to `/images/batbern-default-bg.jpg` if no topic image
- [ ] `AgendaView.tsx` — agenda list; works in both center-stage and sidebar layout states (AC: #11, #14, #17–22)
  - [ ] Props: `sessions`, `completedSessions: string[]`, `currentSessionSlug?: string`
  - [ ] Render sessions filtered to `['keynote','presentation','workshop','panel_discussion', 'break']`
  - [ ] Break entry rendered as `─── Pause ───`
  - [ ] Completed sessions: grey + `✓`; current: white + left accent bar (BATbern blue); upcoming: white/60% opacity
- [ ] `SpeakerCard.tsx` — single speaker photo + name + company + session title (AC: #12)
- [ ] `TwoSpeakerCard.tsx` — side-by-side layout for multi-speaker sessions (AC: #12)
- [ ] `SectionDots.tsx` — subtle progress indicator (bottom center), dot per section
- [ ] `BlankOverlay.tsx` — B-key overlay wrapping `BreakSlide` (AC: #23–24, #29)
  - [ ] Uses `AnimatePresence` + `motion.div` fade in/out (0.3s)
  - [ ] Fixed full-screen, z-index above everything else

### Phase 7: Frontend — Slide components
- [ ] `slides/WelcomeSlide.tsx` — BATbern logo, `#BATbernXX` hashtag, topic title, date, venue (AC: #7)
- [ ] `slides/AboutSlide.tsx` — `aboutText` from settings + `partnerCount` cards staggered (AC: #8)
- [ ] `slides/CommitteeSlide.tsx` — organizer photo grid, staggered fly-in animation (AC: #9)
  - [ ] Stagger: `initial: { y: 30, opacity: 0 }`, `animate: { y: 0, opacity: 1, transition: { delay: index * 0.12 } }`
- [ ] `slides/TopicRevealSlide.tsx` — topic title large + image emphasis (AC: #10)
- [ ] `slides/AgendaPreviewSlide.tsx` — wraps `AgendaView` in center-stage layout; `completedSessions=[]` (AC: #11)
- [ ] `slides/SessionSlide.tsx` — wraps `SpeakerCard` or `TwoSpeakerCard` based on speaker count (AC: #12)
- [ ] `slides/BreakSlide.tsx` — coffee break animation (AC: #25, #29)
  - [ ] BATbern `~` spinner (CSS `@keyframes` rotation, 3s loop)
  - [ ] Animated coffee cup: CSS steam lines rising + fading, 2s loop
  - [ ] Floating coffee beans: 8–12 `<span>` elements, staggered upward float + gentle sway
  - [ ] "Pause" text large centered in BATbern blue
  - [ ] "Weiter um HH:MM" derived from `firstPostBreakSession.scheduledStartTime`
  - [ ] Background: topic image + heavier overlay `rgba(0,0,0,0.85)` + subtle amber tint
- [ ] `slides/AgendaRecapSlide.tsx` — `AgendaView` with `completedSessions` set to pre-break slugs (AC: #14)
- [ ] `slides/UpcomingEventsSlide.tsx` — next 3 future events, staggered cards (AC: #15)
- [ ] `slides/AperoSlide.tsx` — closing visual + BATbern `~` spinner (AC: #16)

### Phase 8: Frontend — PresentationPage and routing
- [ ] `web-frontend/src/pages/PresentationPage.tsx` — route owner, section state, layout orchestration (all ACs)
  - [ ] Section state: `currentIndex`, `isBlankActive`, `direction` (forward/back for transition)
  - [ ] Section transition: `AnimatePresence mode="wait"` directional spring
    - Forward: `initial: { x: 80, opacity: 0 }`, `exit: { x: -80, opacity: 0 }`
    - Backward: `initial: { x: -80, opacity: 0 }`, `exit: { x: 80, opacity: 0 }`
    - `transition: { type: "spring", stiffness: 120, damping: 20 }` ≈ 350ms
  - [ ] Agenda `motion.div layout` wrapper: center-stage state (§5, §Recap) vs sidebar state (§6–N)
    - `className` switches between `agendaCenterStage` and `agendaSidebar` CSS modules
    - `transition: { type: "spring", stiffness: 100, damping: 22, mass: 1 }`
  - [ ] Full-screen on mount (optional; moderator triggers via `F`)
  - [ ] No sidebar/nav — pure fullscreen canvas
- [ ] Add route in `web-frontend/src/App.tsx`:
  ```tsx
  <Route path="/present/:eventCode" element={<PresentationPage />} />
  ```
  — place with other public routes (no auth guard); lazy-import

### Phase 9: Frontend — Admin UI for presentation settings
- [ ] Add "Presentation Settings" card to existing organizer admin settings page
  - [ ] `<TextField multiline>` for `aboutText`
  - [ ] `<TextField type="number">` for `partnerCount`
  - [ ] Save button → `PUT /api/v1/settings/presentation` (organizer auth)
  - [ ] i18n keys: `admin.presentationSettings.*`

### Phase 10: i18n + tests + display QA
- [ ] Add i18n keys to `public/locales/en/common.json` and `de/common.json`:
  - [ ] `navigation.presentationMode` (or equivalent label if surfaced in UI)
  - [ ] `presentation.errorTitle`, `presentation.errorMessage`, `presentation.retryButton` (AC: #43)
- [ ] Write unit tests:
  - [ ] `usePresentationSections.test.ts` — section list generation for typical event with and without break
  - [ ] `useKeyboardNavigation.test.ts` — keyboard handler logic
  - [ ] `PresentationPage.test.tsx` — smoke test: renders welcome section, keyboard nav advances; renders error screen when all API calls fail
- [ ] Write Playwright E2E test (`web-frontend/e2e/presentation.spec.ts`):
  - [ ] Navigate to `/present/BATbernXX` (use a known fixture event)
  - [ ] Assert Welcome section renders (BATbern logo visible, no auth wall)
  - [ ] Press `ArrowRight` 4 times; assert sidebar appears (section 5+)
  - [ ] Assert no horizontal scrollbar at 1920×1080 viewport
- [ ] Manual QA checklist at both target resolutions:
  - [ ] Render at 1920×1080: no horizontal scrollbar, all sections navigate correctly (AC: #40, #42)
  - [ ] Render at 2560×1440: same check (AC: #40, #42)
  - [ ] Font legibility at projector distance: body ≥ 1.5rem, headings ≥ 3rem, topic title ≥ 4.5rem, sidebar ≥ 0.75rem (AC: #41)
  - [ ] Verify Ken Burns animation plays smoothly at both resolutions (AC: #34)
- [ ] Type-check: `cd web-frontend && npm run type-check` passes with zero errors

## Dev Notes

### Critical Architecture Points

#### Framer Motion — NOT yet installed
```bash
cd web-frontend && npm install framer-motion
```
Framer Motion is the animation library required by the spec (layout FLIP, AnimatePresence, directional springs). It is **not present** in `package.json`. Install it before writing any component. It works fine alongside MUI + Emotion — they do not conflict.

[Source: web-frontend/package.json — no framer-motion entry]

#### Backend routing — where does settings/presentation go?
- `GET /api/v1/public/settings/presentation` → already routes to `company-user-management-service` via DomainRouter rule at line 99: `cleanPath.startsWith("/api/v1/public")` → CUMS
- `PUT /api/v1/settings/presentation` is NOT under `/api/v1/public/` → check DomainRouter for a catch-all CUMS rule. If the path `/api/v1/settings/...` has no explicit rule, it may fall through incorrectly. Verify — may need an explicit CUMS routing rule for `cleanPath.startsWith("/api/v1/settings")`.

[Source: api-gateway/src/main/java/ch/batbern/gateway/routing/DomainRouter.java:99]

#### Flyway migration versions
- **company-user-management-service**: Latest is `V9__Remove_denormalized_attendee_data.sql` → use **`V10__add_presentation_settings.sql`**
- **event-management-service**: Latest is `V71__remove_topic_usage_count_column.sql` → next would be V72 (not needed for this story)

[Source: ls services/company-user-management-service/src/main/resources/db/migration/]

#### Security config — both files need updating
`GET /api/v1/public/settings/presentation` must be permitted in **two places**:
1. `api-gateway/src/.../config/SecurityConfig.java` — add near line 220 where `/api/v1/public/organizers` is listed
2. `company-user-management-service/src/.../config/SecurityConfig.java` — same, near line 77/115/160 where the pattern appears three times (all three security filter chains)

Pattern to follow:
```java
.requestMatchers(HttpMethod.GET, "/api/v1/public/settings/presentation").permitAll()
```

[Source: api-gateway SecurityConfig.java:220, company-user-management SecurityConfig.java:77,115,160]

#### OpenAPI contract-first (ADR-006)
Add `presentationSettings` endpoints to `docs/api/events.openapi.yml` BEFORE any implementation. After writing the spec, regenerate types:
```bash
cd web-frontend && npm run generate:api-types
```

#### Public Organizers API — already exists
`GET /api/v1/public/organizers` → `PublicOrganizerController` in company-user-management-service — already implemented, already permitAll. Returns `PublicOrganizerResponse` (firstName, lastName, bio, profilePictureUrl, companyId). Use as-is.

[Source: services/company-user-management-service/.../controller/PublicOrganizerController.java]

#### App.tsx — public route placement
Add the new route at line ~277 with the other public/speaker-portal routes. It must NOT be inside the `ProtectedRoute` wrapper:
```tsx
<Route path="/present/:eventCode" element={<PresentationPage />} />
```
Use `React.lazy()` import pattern consistent with other public pages.

[Source: web-frontend/src/App.tsx:238–280]

### Section Flow Algorithm

```
usePresentationSections(eventData):

staticSections = [Welcome, About, Committee, TopicReveal]

speakerSessions = sessions
  .filter(s => ['keynote','presentation','workshop','panel_discussion'].includes(s.sessionType))
  .sort((a, b) => a.scheduledStartTime - b.scheduledStartTime)

breakSession = sessions.find(s => ['break','lunch'].includes(s.sessionType))

dynamicSections = [AgendaPreview]

if (breakSession) {
  preBreakSessions = speakerSessions.filter(s => s.scheduledEndTime <= breakSession.scheduledStartTime)
  postBreakSessions = speakerSessions.filter(s => s.scheduledStartTime >= breakSession.scheduledEndTime)

  dynamicSections = [
    AgendaPreview,
    ...preBreakSessions.map(s => SessionSection(s)),
    BreakSection(breakSession),
    AgendaRecapSection({ completedSessions: preBreakSessions }),
    ...postBreakSessions.map(s => SessionSection(s))
  ]
} else {
  dynamicSections = [AgendaPreview, ...speakerSessions.map(s => SessionSection(s))]
}

return [...staticSections, ...dynamicSessions, UpcomingEvents, Apero]
```

### Agenda FLIP Animation — implementation pattern

```tsx
// In PresentationPage.tsx
const agendaIsSidebar = section.type === 'session' || section.type === 'break' || section.type === 'recap';

<motion.div
  layout
  transition={{ type: "spring", stiffness: 100, damping: 22, mass: 1 }}
  className={agendaIsSidebar ? styles.agendaSidebar : styles.agendaCenterStage}
>
  <AgendaView ... />
</motion.div>
```

`AgendaView` MUST NOT be unmounted between sections — the FLIP animation only works because the DOM element persists. Conditional rendering (`{showAgenda && <AgendaView />}`) will break the animation.

### Break Resume Time Derivation

```tsx
const firstPostBreakSession = sessions.find(s =>
  s.scheduledStartTime > breakSession.scheduledEndTime &&
  ['keynote','presentation','workshop','panel_discussion'].includes(s.sessionType)
);
const resumeTime = firstPostBreakSession
  ? format(new Date(firstPostBreakSession.scheduledStartTime), 'HH:mm')
  : null;
```

`date-fns` is already installed. Use `format` from `'date-fns'`.

### CSS Module structure for agendaCenterStage / agendaSidebar

```css
/* PresentationPage.module.css */
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

Note: When both transforms are in the `style` attribute (not CSS class), Framer Motion can't FLIP correctly. Keep them in CSS classes and let Framer capture the layout geometry before/after class change.

### 60-second Poll — do not useEffect + setInterval directly

Use React Query's `refetchInterval`:
```tsx
const { data: sessionsRefresh } = useQuery({
  queryKey: ['presentation-sessions', eventCode],
  queryFn: () => presentationService.getSessions(eventCode),
  refetchInterval: 60_000,
  refetchIntervalInBackground: true,
});
```

Or use the custom `useInterval` hook if one exists in the codebase. Do NOT use `setInterval` inside `useEffect` directly (memory leak risk on unmount).

### Project Structure Notes

**Alignment with unified project structure:**

Frontend components follow the `web-frontend/src/components/{domain}/` pattern. Presentation is a new domain:
```
web-frontend/src/
  pages/
    PresentationPage.tsx                ← lazy-imported in App.tsx
  components/
    presentation/                       ← new domain folder
      usePresentationData.ts
      usePresentationSections.ts
      useKeyboardNavigation.ts
      BlankOverlay.tsx
      SectionDots.tsx
      TopicBackground.tsx
      AgendaView.tsx
      SpeakerCard.tsx
      TwoSpeakerCard.tsx
      slides/
        WelcomeSlide.tsx
        AboutSlide.tsx
        CommitteeSlide.tsx
        TopicRevealSlide.tsx
        AgendaPreviewSlide.tsx
        SessionSlide.tsx
        BreakSlide.tsx
        AgendaRecapSlide.tsx
        UpcomingEventsSlide.tsx
        AperoSlide.tsx
  services/
    presentationService.ts              ← follows service layer pattern (no direct fetch)
```

Backend: company-user-management-service follows the layered architecture:
- `controller/` → `PresentationSettingsController.java`
- `service/` → `PresentationSettingsService.java`
- `repository/` → `PresentationSettingsRepository.java`
- `domain/` → `PresentationSettings.java`
- `dto/` → `PresentationSettingsResponse.java`, `PresentationSettingsRequest.java`

**No conflicts detected** with existing code. No files are being modified except:
- `web-frontend/src/App.tsx` — add one route
- `api-gateway/src/.../SecurityConfig.java` — add one permitAll line
- `company-user-management-service/.../SecurityConfig.java` — add permitAll in all three filter chains
- `docs/api/events.openapi.yml` — add 2 endpoints

### References

- Epic 10 story spec: [Source: docs/prd/epic-10-additional-stories.md#story-108-moderator-presentation-page]
- Full feature spec with 43 ACs: [Source: _bmad-output/implementation-artifacts/10-8-moderator-presentation-page.md]
- DomainRouter routing rules: [Source: api-gateway/src/main/java/ch/batbern/gateway/routing/DomainRouter.java:99]
- API Gateway security config: [Source: api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java:220]
- Company-user-management security config: [Source: company-user-management-service/src/main/java/ch/batbern/companyuser/config/SecurityConfig.java:77]
- Public Organizer pattern to follow: [Source: company-user-management-service/.../controller/PublicOrganizerController.java]
- Flyway V10 target for CUMS: [Source: services/company-user-management-service/src/main/resources/db/migration/ — latest is V9]
- App.tsx public route placement: [Source: web-frontend/src/App.tsx:277 — /unsubscribe as most recent public route]
- ADR-006 contract-first: [Source: docs/architecture/coding-standards.md or CLAUDE.md — OpenAPI spec written FIRST]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
