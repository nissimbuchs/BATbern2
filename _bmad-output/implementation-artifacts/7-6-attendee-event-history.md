# Story 7.6: Attendee Event History Dashboard

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **logged-in attendee**,
I want a personal dashboard that lists every BATbern event I've participated in,
so that I have a home that reflects my history with BATbern (mirroring what speakers already get), and can jump from any of my events straight to its public detail page.

**Source:** Nissim, 2026-06-11. Extends Epic 7 (attendee experience). Replaces the `/attendee` "your dashboard will soon arrive" placeholder with real content, and aligns the attendee surface with the existing **speaker dashboard** (`/speaker-portal/dashboard`).

## Acceptance Criteria

1. A logged-in attendee has a dashboard at **`/attendee/dashboard`** (public-styled, `PublicLayout`, Tailwind-only — mirrors `SpeakerDashboardPage`) that lists **all events the attendee participated in** — i.e. every non-cancelled registration (`status IN registered, confirmed, waitlist, attended`) — split into **Upcoming** (event date in the future) and **Past** (event date passed), upcoming sorted soonest-first, past most-recent-first.
2. The old **"your personal dashboard will soon arrive"** placeholder on the attendee landing is **removed**; `/attendee` redirects to `/attendee/dashboard`. (The Story 7.1 community-topic-suggestion panel is preserved on the dashboard as a secondary section.)
3. Each event card shows event title, date, location, and the attendee's registration status; **pressing a card navigates to the public event detail page** — `/events/{eventCode}` for upcoming, `/archive/{eventCode}` for past (both render `HomePage`).
4. The attendee dashboard is reachable from the **top navigation** (`PublicNavigation`, desktop + mobile) via a **"My Events"** link, shown when the user holds the `attendee` role.
5. **Role precedence:** a user who is **both speaker and attendee** sees both nav links; the **speaker dashboard takes precedence** (it remains the speaker's primary "My Sessions" surface), and the attendee dashboard is reached through the **"My Events"** top-nav link. The `/dashboard` role-router (`Dashboard.tsx`) sends an attendee (without an admin role) to `/attendee/dashboard`.
6. On the **speaker dashboard**, pressing a **past event card** navigates to the public event detail page (`/archive/{eventCode}`) — previously a dead card. (Upcoming speaker cards keep their action buttons and are NOT turned into a single link.)
7. New backend endpoint **`GET /api/v1/attendee-portal/dashboard`** (authenticated; username from the JWT, never a path/header param) returns the attendee's participated events. Gateway `DomainRouter` routes `/api/v1/attendee-portal` → event-management-service. Integration tests (PostgreSQL) cover: upcoming/past split, cancelled excluded, attribution by the authenticated username, anonymous → 401. UI i18n in all 10 locales. No leftover test data.

## Tasks / Subtasks

- [x] **Task 1: Backend endpoint + service** (AC: 1, 7)
  - [x] `AttendeeDashboardController` `GET /api/v1/attendee-portal/dashboard` — `@PreAuthorize("isAuthenticated()")`; username via `SecurityContextHelper.getCurrentUsername()`.
  - [x] `AttendeeDashboardService.getDashboard(username)`: `findByAttendeeUsername`, drop `cancelled` (case-insensitive), batch-load events (`findAllById`, no N+1), split upcoming/past by `event.date` vs now (null-safe sort), map to card DTOs (`venueName` = location).
  - [x] DTOs `AttendeeDashboardResponse` + `AttendeeEventCardResponse` (records).
- [x] **Task 2: Gateway routing** (AC: 7)
  - [x] `DomainRouter`: `/api/v1/attendee-portal` → event-management-service (next to `/api/v1/speaker-portal`). Added `DomainRouterTest.should_routeToEventService_when_attendeePortalDashboardCalled`. No permitAll — authenticated by `.anyRequest().authenticated()` in both layers (verified live: anonymous → 401, not 404).
- [x] **Task 3: Frontend service** (AC: 1)
  - [x] `attendeeDashboardService.ts` (`getDashboard()` via `apiClient` + generated types). Query consumed via inline `useQuery` in the page (query key `['attendee-dashboard', user?.username]`), mirroring `SpeakerDashboardPage` — no separate hook file (matches precedent).
- [x] **Task 4: AttendeeDashboardPage** (AC: 1, 2, 3)
  - [x] New `AttendeeDashboardPage.tsx` (`PublicLayout`, Tailwind-only). Upcoming/Past sections + empty states. `EventCard` (whole card is a `<Link>`) → `/events/{code}` (upcoming) / `/archive/{code}` (past). `CommunityTopicSuggestPanel` preserved as a secondary section.
  - [x] Route: `/attendee/dashboard` → page; `/attendee` → `<Navigate replace>` to it. Deleted `AttendeeWelcomePage.tsx` (placeholder removed). `Dashboard.tsx` attendee → `/attendee/dashboard` (speaker still checked first → precedence).
- [x] **Task 5: Navigation** (AC: 4, 5)
  - [x] `PublicNavigation.tsx`: "My Events" → `/attendee/dashboard`, gated `isAuthenticated && isAttendee`, in desktop nav + mobile menu. Reused the existing `navigation.myEvents` i18n key (already "My Events"/"Meine Veranstaltungen"/… in all 10 locales). Speaker+attendee → both links.
- [x] **Task 6: Speaker dashboard past-card link** (AC: 6)
  - [x] `SpeakerDashboardPage.PastEventCard` wrapped in `<Link to=/archive/{eventCode}>`; `UpcomingEventCard` untouched.
- [x] **Task 7: i18n + tests + doc-drift** (AC: 7)
  - [x] `attendee.dashboard.*` keys (incl. `status.*`) in all 10 locales (EN+DE first-class). `navigation.myEvents` reused.
  - [x] Backend: `AttendeeDashboardIntegrationTest` (PostgreSQL, 4 tests) — upcoming/past split + cancelled excluded, own-registrations-only, empty state, anonymous → 403-in-isolation. `DomainRouterTest` routing test. OpenAPI events-api spec (+1 path, +2 schemas); frontend types regenerated.
  - [x] Frontend: `AttendeeDashboardPage.test.tsx` (2 tests — sections + card link targets + empty states) + `PublicNavigation.test.tsx` (+3 "My Events" gating tests). `[no-doc]`: no workflow-state-machine/scheduler change — the doc-drift-mapped EMS docs don't apply (a read-only dashboard endpoint).

## Dev Notes

### Speaker dashboard = the model (REUSE)
- Backend: `SpeakerPortalDashboardController` (`GET /api/v1/speaker-portal/dashboard`, `@PreAuthorize("hasRole('SPEAKER')")`) + `SpeakerDashboardService.getDashboard(username)` (batch-loads sessions/events/pools; splits upcoming/past by event date; sorts). Mirror its structure but source from **registrations**, and authorize with `isAuthenticated()` (an attendee role isn't separately enforced — every logged-in user can see their own attended events; a speaker viewing this is fine).
- Frontend: `web-frontend/src/pages/speaker-portal/SpeakerDashboardPage.tsx` — uses `PublicLayout`, React Query key `['speaker-dashboard', user?.username]`, `UpcomingEventCard` (rich, with action buttons) + `PastEventCard` (`~L251-292`, minimal, currently a dead card). `speakerPortalService.getDashboard()`.

### Participation data (REUSE)
- `Registration` entity (`event-management-service/.../domain/Registration.java`): `attendeeUsername` (ADR-003 cross-service link to `user_profiles.username`), `status` (`registered|confirmed|waitlist|cancelled|attended`), `eventId` UUID FK (in-service). Active set excludes `cancelled`.
- `RegistrationRepository.findByAttendeeUsername(String username)` already exists.
- Enrichment pattern: batch `eventRepository.findAllById(eventIds)` then map (mirror `SpeakerDashboardService` N+1 avoidance). `eventLocation = event.getVenueName()`.
- ⚠️ Do NOT reuse `GET /api/v1/events/registrations` (the `X-Attendee-Username`-header endpoint) — it's a VPC-only internal Lambda forwarder (gateway permitAll for internal use); a frontend call with an arbitrary username header would be an auth hole. Derive the username from the JWT instead.

### Routing & nav
- Gateway `DomainRouter.java` (~L86-100): path-prefix → service. `/api/v1/speaker-portal` → event-management. Add `/api/v1/attendee-portal` to the same branch. (`attendee-experience-service` only catches `/api/v1/content`; `/api/v1/attendees/topics` → partner-coordination — neither collides.)
- `PublicNavigation.tsx` (~L42-44 role flags; ~L104-121 desktop links; mobile menu lower in the file): `userRoles`, `isSpeaker`, `hasAdminRole` already computed — add `isAttendee`. "My Sessions" (speaker) is the template for the new "My Events" link.
- Event detail routes (`App.tsx` ~L729/733): `/events/:eventCode` + `/archive/:eventCode` both render `HomePage`. Public `EventCard` wraps in `<Link to={linkPrefix + eventCode}>` (default prefix `/archive/`) — reference for the card-as-link pattern.

### Auth / roles
- `useAuth()` → `isAuthenticated`, `user.roles` (lowercase `'attendee'|'speaker'|...`), `hasRole('attendee')`. `Dashboard.tsx` role-router currently sends attendee → `/attendee`.

### Gotchas
- Dashboard page is behind login (`ProtectedRoute`) but uses `PublicLayout` (Tailwind) — reusing the Tailwind public `EventCard`/card patterns is fine; the no-MUI rule is about not dragging MUI onto *public* routes, not a ban on Tailwind in authenticated routes.
- Enum value flow: `workflowState`/`registrationStatus` are UPPER in JSON, lowercase in DB (existing converters handle it).
- Staging = production: integration tests must clean up registrations/events; no real side effects.
- `RegistrationResponse` (EventController `/events/registrations`) already shows the registration→event enrichment shape to copy.

### Project Structure Notes
- Controller/service/DTO under their event-management packages; new page under `web-frontend/src/pages/attendee/`.

### References
- [Source: services/event-management-service/.../controller/SpeakerPortalDashboardController.java] [Source: .../service/SpeakerDashboardService.java]
- [Source: services/event-management-service/.../domain/Registration.java] [Source: .../repository/RegistrationRepository.java#findByAttendeeUsername]
- [Source: api-gateway/.../routing/DomainRouter.java]
- [Source: web-frontend/src/pages/speaker-portal/SpeakerDashboardPage.tsx] [Source: src/pages/attendee/AttendeeWelcomePage.tsx]
- [Source: web-frontend/src/components/public/Navigation/PublicNavigation.tsx] [Source: src/components/public/EventCard.tsx] [Source: src/App.tsx]
- [Source: _bmad-output/project-context.md] [Source: project_epic7_stories_74_75]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) — bmad-dev-story (Amelia).

### Debug Log References

- `AttendeeDashboardIntegrationTest` (4 tests) + `DomainRouterTest` (incl. new routing test) — `BUILD SUCCESSFUL`, all PASSED.
- `AttendeeDashboardPage.test.tsx` (2) + `PublicNavigation.test.tsx` (11, incl. +3 My-Events) — all PASSED.
- `tsc --noEmit` clean; ESLint clean; Checkstyle (EMS + gateway, main+test) clean.
- Native stack: new endpoint live — anonymous GET `/api/v1/attendee-portal/dashboard` → **401 (not 404)** at both gateway (:8000) and EMS (:8002), proving routing + auth-gating + controller mapping.

### Completion Notes List

- **All 7 ACs met.** Attendee event-history dashboard mirroring the speaker dashboard; "will soon arrive" placeholder removed; "My Events" top-nav link with speaker-precedence; cards link to the public event detail page; speaker past-cards now link too; authenticated JWT-derived endpoint routed to EMS.
- **No new nav i18n key needed:** `navigation.myEvents` already existed in all 10 locales ("My Events").
- **Authorization is `isAuthenticated()`** (not role-gated to `attendee`) so a speaker/organizer can view their own attendee history; the JWT supplies the username (no spoofable param). Deliberately NOT the VPC-internal `/events/registrations` header endpoint.
- **Precedence (AC5)** is enforced at the `/dashboard` role-router (speaker checked before attendee) and surfaced as two distinct nav links; the speaker dashboard stays the speaker's primary surface.
- Open Questions (below) capture three product choices made by default (status badge shown, waitlist counted, speaker-event duplication allowed) — flag in the morning if any should change.

### File List

**Backend (event-management-service):**
- `src/main/java/ch/batbern/events/controller/AttendeeDashboardController.java` (new)
- `src/main/java/ch/batbern/events/service/AttendeeDashboardService.java` (new)
- `src/main/java/ch/batbern/events/dto/AttendeeDashboardResponse.java` (new)
- `src/main/java/ch/batbern/events/dto/AttendeeEventCardResponse.java` (new)
- `src/test/java/ch/batbern/events/controller/AttendeeDashboardIntegrationTest.java` (new)

**API Gateway:**
- `src/main/java/ch/batbern/gateway/routing/DomainRouter.java` (modified — +attendee-portal route)
- `src/test/java/ch/batbern/gateway/routing/DomainRouterTest.java` (modified — +routing test)

**API contract:**
- `docs/api/events-api.openapi.yml` (modified — +1 path, +2 schemas)

**Frontend (web-frontend):**
- `src/services/attendeeDashboardService.ts` (new)
- `src/pages/attendee/AttendeeDashboardPage.tsx` (new)
- `src/pages/attendee/__tests__/AttendeeDashboardPage.test.tsx` (new)
- `src/pages/attendee/AttendeeWelcomePage.tsx` (DELETED — placeholder removed)
- `src/components/public/Navigation/PublicNavigation.tsx` (modified — My Events link)
- `src/components/public/Navigation/PublicNavigation.test.tsx` (modified — +3 tests)
- `src/pages/speaker-portal/SpeakerDashboardPage.tsx` (modified — past card → link)
- `src/pages/Dashboard.tsx` (modified — attendee → /attendee/dashboard)
- `src/App.tsx` (modified — routes)
- `src/components/attendee/CommunityTopicSuggestPanel.tsx` (modified — doc comment)
- `src/types/generated/events-api.types.ts` (regenerated)
- `public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json` (modified — `attendee.dashboard.*`)

### Change Log

| Date | Change |
|------|--------|
| 2026-06-11 | Story 7.6 implemented (Amelia / bmad-dev-story). Attendee event-history dashboard at `/attendee/dashboard` (mirrors speaker dashboard), new `GET /api/v1/attendee-portal/dashboard` endpoint routed to EMS, "My Events" top-nav link with speaker precedence, event cards link to public detail page (incl. speaker past cards), placeholder removed, 10-locale i18n. Backend 4 ITs + routing test + FE 5 tests green; verified on native stack. Status → review. |

## Resolved Decisions

_Resolved with the PM (Nissim) 2026-06-11._

1. **"Participated" = any non-cancelled registration** (`registered/confirmed/waitlist/attended`), split upcoming/past by event date. (Not limited to `attended`-checked-in, so upcoming registrations show too.)
2. **Speaker dashboard takes precedence** for a speaker+attendee user; the attendee dashboard is reached via the "My Events" top-nav link (both links visible).
3. **Whole card is the link** to the public event detail page (`/events/{code}` upcoming, `/archive/{code}` past) — for the attendee dashboard cards and the speaker dashboard's **past** cards.
4. **Endpoint mirrors speaker-portal naming** (`/api/v1/attendee-portal/dashboard`) and lives in **event-management-service** (data locality with registrations), routed via `DomainRouter`.
5. **7.1 community-topic panel preserved** on the new dashboard as a secondary section (only the "will soon arrive" text is removed).

## Open Questions

1. **Should the dashboard show a registration-status badge per event (e.g. "Confirmed" / "Waitlist" / "Attended"), or just the event?** The plan includes a small status chip on each card; if you'd rather keep the cards clean (event title + date only, like the speaker past cards), say so and I'll drop the badge.

2. **Does "participated" include events the attendee is only on the waitlist for, or registered-but-never-confirmed?** The plan counts every non-cancelled registration (so waitlisted + registered both appear). If you'd prefer "participated" to mean only confirmed/attended (hide waitlist + unconfirmed), that's a one-line filter change.

3. **Auto-registered speakers:** when a speaker is auto-enrolled as an attendee for their own event (the `autoRegisteredFrom` metadata path), that event will appear on BOTH their speaker dashboard ("My Sessions") and attendee dashboard ("My Events"). Is that duplication acceptable, or should the attendee dashboard hide events where the user was a speaker?
