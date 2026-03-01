# Story 10.10: Registration Status Indicator for Logged-in Users

Status: done

## Story

As a **logged-in attendee**,
I want to see my registration status for the current event directly on the public homepage and event cards,
so that I never accidentally try to register twice and always know at a glance whether I am registered, confirmed, on the waitlist, or cancelled.

## Acceptance Criteria

1. **AC1 — New read-only API endpoint**: `GET /api/v1/events/{eventCode}/my-registration` returns the authenticated user's registration status for that event. Response includes `registrationCode`, `eventCode`, `status` (uppercase: `REGISTERED`, `CONFIRMED`, `WAITLIST`, `CANCELLED`), and `registrationDate`. Returns `404` when the user has no registration for that event. Returns `401` for unauthenticated requests.

2. **AC2 — Homepage status banner**: When an authenticated user visits the public homepage and the displayed "current event" is in state `AGENDA_PUBLISHED`, `AGENDA_FINALIZED`, or `EVENT_LIVE`, a status banner appears below the hero section:
   - `CONFIRMED` → green banner with check icon and text from i18n key `registrationStatusBanner.confirmed`
   - `REGISTERED` → amber banner with clock icon and text from i18n key `registrationStatusBanner.registered`
   - `WAITLIST` → blue banner with hourglass icon and text from i18n key `registrationStatusBanner.waitlist`
   - `CANCELLED` → grey banner and text from i18n key `registrationStatusBanner.cancelled`
   - No banner when status is `404` (not registered) or user is not authenticated.

3. **AC3 — Banner "Manage Registration" link**: The status banner includes a link labelled from i18n key `registrationStatusBanner.manageLink` that navigates to `/register/{eventCode}`. For `CANCELLED` status the link text is `registrationStatusBanner.registerAgain`.

4. **AC4 — Loading skeleton**: While the `GET my-registration` call is in-flight (max ~500ms on staging), a placeholder skeleton element (same width/height as the banner) is shown below the hero section. There must be no cumulative layout shift after the banner resolves.

5. **AC5 — Event cards status chip**: The `EventCard` component accepts an optional prop `myRegistrationStatus?: string`. When provided, a small badge chip is rendered in the top-right corner of the card image (or top-right of the card header if no image):
   - `CONFIRMED` → green chip, text "Confirmed" (`eventCard.statusChip.confirmed`)
   - `REGISTERED` → amber chip, text "Registered" (`eventCard.statusChip.registered`)
   - `WAITLIST` → blue chip, text "Waitlist" (`eventCard.statusChip.waitlist`)
   - `CANCELLED` → grey chip, text "Cancelled" (`eventCard.statusChip.cancelled`)
   - Parent components pass this prop only for events in the past 12 months (to avoid N+1 on full archive).

6. **AC6 — Registration Wizard guard**: When `RegistrationWizard` mounts for an event, it calls `GET my-registration` for the current user. If the response is non-404 (any status), the wizard shows a status guard screen instead of step 1 of the form:
   - Guard shows current status, `registrationDate`, and an appropriate action button: for `REGISTERED`/`CONFIRMED`/`WAITLIST` → "Done, go back"; for `CANCELLED` → "Register again" (proceeds to step 1 of the wizard; the backend `RegistrationService.createRegistration()` is updated in T4.6 to allow a new registration when existing status is `cancelled`).
   - This replaces the confusing `IllegalStateException` error-on-submit that users currently experience.
   - **Note**: The current backend (line 101-113 of `RegistrationService.java`) throws `IllegalStateException` for both `confirmed` AND `cancelled` statuses — T4.6 fixes this.

7. **AC7 — Cache invalidation**: The `my-registration` endpoint response is cached in TanStack Query with `staleTime: 5 * 60 * 1000` (5 minutes). The query is invalidated when a new event registration is successfully created (the `useEventRegistration` mutation success callback must call `queryClient.invalidateQueries(['my-registration', eventCode])`).

8. **AC8 — Anonymous users**: For unauthenticated users, no API call is made (`useMyRegistration` returns `undefined` immediately when `isAuthenticated === false`). No banner, no chip, no guard screen.

9. **AC9 — TDD compliance**: `RegistrationStatusIntegrationTest` is written first (RED phase) covering: `200 REGISTERED`, `200 CONFIRMED`, `200 WAITLIST`, `200 CANCELLED`, `404 not-registered`, `401 unauthenticated`. `useMyRegistration.test.ts` unit tests cover all states. All tests pass before the story is marked done.

10. **AC10 — i18n**: All visible strings use i18n keys (no hardcoded English). Keys added to `en/registration.json` and `de/registration.json` for all banner and chip texts. `npm run type-check` passes; `npm run lint` passes.

## Tasks / Subtasks

### Backend

- [x] **T1 — OpenAPI spec first (ADR-006)** (AC: #1)
  - [x] T1.1 — Add `GET /api/v1/events/{eventCode}/my-registration` to `docs/api/events.openapi.yml`
  - [x] T1.2 — Define new schema `MyRegistrationResponse` with fields: `registrationCode: string`, `eventCode: string`, `status: string (enum: REGISTERED, CONFIRMED, WAITLIST, CANCELLED)`, `registrationDate: string (date-time)`
  - [x] T1.3 — Specify security: bearer JWT required; 401 when missing; 404 when no registration found
  - [x] T1.4 — Regenerate backend DTOs: `./gradlew :services:event-management-service:openApiGenerateEvents`
  - [x] T1.5 — Regenerate frontend types: `cd web-frontend && npm run generate:api-types:events`

- [x] **T2 — Repository query** (AC: #1)
  - [x] T2.1 — Add `findByEventCodeAndAttendeeUsername` to `RegistrationRepository.java`:
    ```java
    @Query("SELECT r FROM Registration r JOIN Event e ON r.eventId = e.id WHERE e.eventCode = :eventCode AND r.attendeeUsername = :username")
    Optional<Registration> findByEventCodeAndAttendeeUsername(@Param("eventCode") String eventCode, @Param("username") String username);
    ```
  - [x] T2.2 — Add index hint comment: existing `idx_registrations_event_id` + `idx_registrations_attendee_username` cover this query

- [x] **T3 — Service method** (AC: #1)
  - [x] T3.1 — Add `getMyRegistration(String eventCode, String authenticatedUsername)` to `RegistrationService.java`
  - [x] T3.2 — Returns `Optional<MyRegistrationResponse>` (generated DTO): builds from `Registration` entity, uppercases `status`, formats `registrationDate` as ISO-8601
  - [x] T3.3 — Log: `DEBUG "Getting registration for event: {} and user: {}"`

- [x] **T4 — Controller endpoint** (AC: #1)
  - [x] T4.1 — Add `getMyRegistration` method to `EventController.java` (or extract to new `RegistrationController.java` if it exceeds 2500 lines — check current length first)
  - [x] T4.2 — `@GetMapping("/{eventCode}/my-registration")`
  - [x] T4.3 — Extract authenticated username from `SecurityContextHolder.getContext().getAuthentication().getName()`
  - [x] T4.4 — Return `200` with `MyRegistrationResponse` when found; return `404` with standard `ErrorResponse` when not found
  - [x] T4.5 — No `@PreAuthorize` needed — SecurityConfig already requires authentication for `GET /api/v1/events/**` non-public endpoints; confirm and add public path exception if needed

- [x] **T4.6 — Allow re-registration for CANCELLED users** (AC: #6)
  - [x] T4.6.1 — In `RegistrationService.createRegistration()` (line ~101), update the existing-registration check to distinguish `cancelled` from `confirmed`:
    ```java
    if ("registered".equalsIgnoreCase(registration.getStatus())) {
        // Return existing pending registration (resend confirmation email)
        return registration;
    } else if ("cancelled".equalsIgnoreCase(registration.getStatus())) {
        // Allow re-registration: delete the cancelled record and fall through to create a new one
        registrationRepository.delete(registration);
        log.info("Deleted cancelled registration for event: {} by user: {}, allowing re-registration", eventCode, username);
        // Fall through to create a new registration below
    } else {
        // confirmed/waitlist — reject duplicate
        throw new IllegalStateException("User " + username + " is already registered for event " + eventCode);
    }
    ```
  - [x] T4.6.2 — Add integration test to `RegistrationStatusIntegrationTest` (T5): `POST /registrations` for a user whose existing registration is `CANCELLED` → `201` Created (new registration created successfully)
  - [x] T4.6.3 — Add Checkstyle-compliant Javadoc to the updated block explaining the re-registration logic

- [x] **T5 — Integration tests (TDD — write FIRST)** (AC: #9)
  - [x] T5.1 — Create `RegistrationStatusIntegrationTest.java` extending `AbstractIntegrationTest` (PostgreSQL via Testcontainers)
  - [x] T5.2 — Test: authenticated user with `CONFIRMED` registration → `200` + correct status
  - [x] T5.3 — Test: authenticated user with `REGISTERED` registration → `200` + correct status
  - [x] T5.4 — Test: authenticated user with `WAITLIST` registration → `200` + correct status
  - [x] T5.5 — Test: authenticated user with `CANCELLED` registration → `200` + correct status
  - [x] T5.6 — Test: authenticated user with no registration for event → `404`
  - [x] T5.7 — Test: unauthenticated request → `401`
  - [x] T5.8 — Run tests: `./gradlew :services:event-management-service:test --tests RegistrationStatusIntegrationTest 2>&1 | tee /tmp/test-10-10-backend.log`

### Frontend

- [x] **T6 — `useMyRegistration` hook** (AC: #1, #7, #8)
  - [x] T6.1 — Create `web-frontend/src/hooks/useMyRegistration.ts`
  - [x] T6.2 — Signature: `useMyRegistration(eventCode: string | undefined)`
  - [x] T6.3 — Uses `useAuth()` (existing hook); if `!isAuthenticated` OR `!eventCode` → return `{ data: undefined, isLoading: false }` immediately — NO API call
  - [x] T6.4 — Uses `useQuery` from `@tanstack/react-query` with key `['my-registration', eventCode]` and `staleTime: 5 * 60 * 1000`
  - [x] T6.5 — On `404` response: treat as "not registered" (return `{ data: null, isLoading: false }`) — do NOT throw error
  - [x] T6.6 — Returns `{ data: MyRegistrationResponse | null | undefined, isLoading: boolean }`
  - [x] T6.7 — Write `useMyRegistration.test.ts`: mock API, test authenticated flow (registered/not-registered/confirmed), test unauthenticated (no call made)
  - [x] T6.8 — Wire cache invalidation in `RegistrationWizard.tsx` (AC: #7)
    - The registration creation is a direct `await eventApiClient.createRegistration(...)` call at line ~149 (not a React Query `useMutation`)
    - Add `const queryClient = useQueryClient()` import and hook call at the top of the wizard component
    - In the success block (immediately after `setRegistrationSuccess(true)` at line ~163), add:
      ```typescript
      queryClient.invalidateQueries({ queryKey: ['my-registration', eventCode] });
      ```
    - This ensures the status banner and wizard guard reflect the newly created registration immediately

- [x] **T7 — `registrationService` extension** (AC: #1)
  - [x] T7.1 — Add `getMyRegistration(eventCode: string): Promise<MyRegistrationResponse>` to `web-frontend/src/services/registrationService.ts` (create file if it doesn't exist, following pattern of other service files)
  - [x] T7.2 — Calls `GET /api/v1/events/{eventCode}/my-registration`; throws on non-404 errors; returns `null` on `404`

- [x] **T8 — `RegistrationStatusBanner` component** (AC: #2, #3, #4)
  - [x] T8.1 — Create `web-frontend/src/components/public/RegistrationStatusBanner.tsx`
  - [x] T8.2 — Props: `status: 'REGISTERED' | 'CONFIRMED' | 'WAITLIST' | 'CANCELLED' | null | undefined`, `eventCode: string`, `isLoading: boolean`
  - [x] T8.3 — If `isLoading === true`: render a skeleton `div` with same height as the banner (MUI `Skeleton` component, variant="rectangular", height="56px")
  - [x] T8.4 — If `status === null || status === undefined`: render nothing (return `null`)
  - [x] T8.5 — Otherwise render MUI `Alert` component:
    - `CONFIRMED` → `severity="success"` + `CheckCircle` icon
    - `REGISTERED` → `severity="warning"` + `HourglassTop` icon
    - `WAITLIST` → `severity="info"` + `Queue` icon
    - `CANCELLED` → `severity="default"` / grey + `Cancel` icon
  - [x] T8.6 — Banner includes action link: for `CANCELLED` → i18n `registrationStatusBanner.registerAgain` → links to `/register/{eventCode}`; for all others → i18n `registrationStatusBanner.manageLink` → links to `/register/{eventCode}`
  - [x] T8.7 — Add `data-testid="registration-status-banner"` to the Alert wrapper
  - [x] T8.8 — Write `RegistrationStatusBanner.test.tsx`: test skeleton shown during loading, null returned when no status, each status variant renders correct severity and link

- [x] **T9 — Integrate banner into `HomePage.tsx`** (AC: #2, #4)
  - [x] T9.1 — Import and call `useMyRegistration(currentEvent?.eventCode)` where `currentEvent` is the existing "current event" loaded for the hero section
  - [x] T9.2 — Place `<RegistrationStatusBanner>` immediately below the hero section (before the countdown timer if present, otherwise after the register button CTA area)
  - [x] T9.3 — Only show banner when `currentEvent.status` is one of `AGENDA_PUBLISHED`, `AGENDA_FINALIZED`, `EVENT_LIVE` (do not show for past/archived events on homepage)

- [x] **T10 — Integrate status chip into `EventCard.tsx`** (AC: #5)
  - [x] T10.1 — Add optional prop `myRegistrationStatus?: string` to `EventCardProps` interface
  - [x] T10.2 — When `myRegistrationStatus` is provided, render a `Badge` chip in the top-right of the card header (inside the `flex items-start justify-between` div, third flex child)
  - [x] T10.3 — Chip colour mapping (use Tailwind classes):
    - `CONFIRMED` → `bg-green-400/20 text-green-400 border-green-400/30`
    - `REGISTERED` → `bg-amber-400/20 text-amber-400 border-amber-400/30`
    - `WAITLIST` → `bg-blue-400/20 text-blue-400 border-blue-400/30`
    - `CANCELLED` → `bg-zinc-600/20 text-zinc-400 border-zinc-600/30`
  - [x] T10.4 — Add `data-testid="event-card-status-chip"` to the chip element
  - [x] T10.5 — Wire `myRegistrationStatus` prop in parent components — **strategy: N+1 per-event calls, parallelised with `Promise.all`, bounded to past 12 months** (BATbern runs ~6-10 events/year, so N ≤ 12 calls):
    - In `UpcomingEventsSection` (or wherever upcoming events are rendered): filter events where `event.date > oneYearAgo`; call `useMyRegistration(event.eventCode)` per event card, passing the result as `myRegistrationStatus`
    - In `ArchivePage` (past events list): apply the same 12-month filter; only fetch status for events within that window — do not fetch for older archived events
    - If `isAuthenticated === false`, skip all `useMyRegistration` calls (the hook guards this internally, so simply not passing `myRegistrationStatus` for unauthenticated users is also acceptable)
    - Do NOT add a new batch endpoint — the per-event hook is sufficient at this scale

- [x] **T11 — Registration Wizard guard** (AC: #6)
  - [x] T11.1 — Find the event registration wizard component (search for component that handles `POST /api/v1/events/{eventCode}/registrations` on submit — likely in `web-frontend/src/components/public/` or `web-frontend/src/pages/public/`)
  - [x] T11.2 — On wizard mount, call `useMyRegistration(eventCode)` (only when `isAuthenticated`)
  - [x] T11.3 — If status is `REGISTERED`, `CONFIRMED`, or `WAITLIST`: show `RegistrationStatusGuard` inline component instead of the wizard form — displays current status + "You are already registered" + "Go back" button
  - [x] T11.4 — If status is `CANCELLED`: show guard with "Register again" button that proceeds to step 1. The backend supports this: T4.6 updates `RegistrationService.createRegistration()` to delete the cancelled record and create a new one (do T4.6 before T11)
  - [x] T11.5 — If status is `null` (not registered) or user not authenticated: show wizard normally (existing behaviour)

- [x] **T12 — i18n keys** (AC: #10)
  - [x] T12.1 — Add to `public/locales/en/registration.json`:
    ```json
    "registrationStatusBanner": {
      "confirmed": "Your registration is confirmed. We'll see you there!",
      "registered": "You are registered for this event. Check your email for confirmation.",
      "waitlist": "You are on the waitlist. We'll notify you if a spot opens.",
      "cancelled": "Your registration was cancelled.",
      "manageLink": "Manage Registration",
      "registerAgain": "Register again"
    },
    "registrationStatusGuard": {
      "alreadyRegistered": "You are already registered for this event",
      "goBack": "Go back",
      "registerAgain": "Register again"
    }
    ```
  - [x] T12.2 — Add `eventCard.statusChip.*` keys to `public/locales/en/events.json`:
    ```json
    "statusChip": {
      "confirmed": "Confirmed",
      "registered": "Registered",
      "waitlist": "Waitlist",
      "cancelled": "Cancelled"
    }
    ```
  - [x] T12.3 — Add German translations for all keys to `public/locales/de/registration.json` and `public/locales/de/events.json`
  - [x] T12.4 — Add `[MISSING]` prefix placeholder translations to all 8 other locale files (fr, it, rm, es, fi, nl, ja, gsw-BE)

- [x] **T13 — Full test run** (AC: #9, #10)
  - [x] T13.1 — `cd web-frontend && npm run test -- --run 2>&1 | tee /tmp/test-10-10-frontend.log && grep -c "pass\|fail" /tmp/test-10-10-frontend.log`
  - [x] T13.2 — `npm run type-check 2>&1 | tee /tmp/typecheck-10-10.log`
  - [x] T13.3 — `npm run lint 2>&1 | tee /tmp/lint-10-10.log`
  - [x] T13.4 — Manual E2E (Playwright): navigate to homepage as authenticated confirmed user → green banner visible; navigate as unauthenticated → no banner

## Dev Notes

### Architecture Compliance

**ADR-006 (Contract-First)**: OpenAPI spec in `docs/api/events.openapi.yml` MUST be updated FIRST (T1), backend types regenerated, frontend types regenerated, THEN implementation begins. Never write controller method before the spec exists.

**ADR-003 (Meaningful Identifiers)**: The endpoint path uses `{eventCode}` (e.g., `BATbern58`), not UUID. The response DTO uses `registrationCode` (e.g., `BATbern58-reg-ABC123`) and `attendeeUsername`, not UUIDs.

**ADR-004 (No User Field Duplication)**: `MyRegistrationResponse` must NOT include `firstName`, `lastName`, `email`, or any other user profile field. These are owned by the User Management Service. The response is minimal: `{ registrationCode, eventCode, status, registrationDate }`.

**Auth extraction pattern** (from existing EventController.java):
```java
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
String username = authentication.getName(); // Cognito sub or username
```

**Cache pattern** (Caffeine, 5-min TTL): This story does NOT implement server-side caching on the backend (the endpoint is fast — single indexed DB query). Client-side TanStack Query `staleTime` provides adequate deduplication. Server-side Caffeine cache can be added in a follow-up if needed.

### Key Files to Modify

| File | Change |
|------|--------|
| `docs/api/events.openapi.yml` | Add `GET /events/{eventCode}/my-registration` + `MyRegistrationResponse` schema |
| `services/event-management-service/.../repository/RegistrationRepository.java` | Add `findByEventCodeAndAttendeeUsername` JPQL query |
| `services/event-management-service/.../service/RegistrationService.java` | Add `getMyRegistration()` method; update `createRegistration()` to allow re-registration for `cancelled` status (T4.6) |
| `services/event-management-service/.../controller/EventController.java` | Add `getMyRegistration` endpoint (check line count — if >2500, extract to `RegistrationController.java`) |
| `web-frontend/src/hooks/useMyRegistration.ts` | New hook |
| `web-frontend/src/services/registrationService.ts` | New or extended service file |
| `web-frontend/src/components/public/RegistrationStatusBanner.tsx` | New component |
| `web-frontend/src/pages/public/HomePage.tsx` | Integrate banner |
| `web-frontend/src/components/public/EventCard.tsx` | Add `myRegistrationStatus` prop + chip |
| `web-frontend/src/components/public/RegistrationWizard*.tsx` | Add guard screen (T11); add `queryClient.invalidateQueries` to success block (T6.8) |
| `public/locales/en/registration.json` | Add `registrationStatusBanner.*` + `registrationStatusGuard.*` keys |
| `public/locales/de/registration.json` | Same keys in German |
| `public/locales/en/events.json` | Add `eventCard.statusChip.*` keys |
| `public/locales/de/events.json` | Same keys in German |
| `public/locales/{fr,it,rm,es,fi,nl,ja,gsw-BE}/registration.json` | `[MISSING]` placeholders |

### New Files

```
services/event-management-service/src/test/java/ch/batbern/events/controller/RegistrationStatusIntegrationTest.java
web-frontend/src/hooks/useMyRegistration.ts
web-frontend/src/hooks/useMyRegistration.test.ts
web-frontend/src/components/public/RegistrationStatusBanner.tsx
web-frontend/src/components/public/__tests__/RegistrationStatusBanner.test.tsx
```
**Note:** `RegistrationStatusGuard` is inlined in `RegistrationWizard.tsx` (no separate file) for simplicity. The guard JSX lives at `RegistrationWizard.tsx:212-265`.

### Registration Status Values

The `Registration.status` field is stored lowercase in the DB (`registered`, `confirmed`, `waitlist`, `cancelled`). The `MyRegistrationResponse.status` field is returned uppercase (`REGISTERED`, `CONFIRMED`, `WAITLIST`, `CANCELLED`) per existing convention in `RegistrationService.enrichRegistrationWithUserData()`:
```java
.status(registration.getStatus() != null ? registration.getStatus().toUpperCase() : null)
```

### EventController Line Count Warning

`EventController.java` is ~2,242 lines (per exploration). Check current length before adding to it:
```bash
wc -l services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java
```
If >2,400 lines, extract `getMyRegistration` + `getRegistrations` + `createRegistration` endpoints into a new `RegistrationController.java`. If still within 2,400, adding to EventController is acceptable.

### Registration Wizard Component Location

The event registration wizard (public-facing, not Cognito account creation) is separate from `useRegistration` in `src/hooks/useRegistration/` (which handles Cognito SignUp). Search for it:
```bash
grep -rn "POST.*registrations\|createRegistration" web-frontend/src/ --include="*.ts" --include="*.tsx"
```

### Frontend Hook Pattern (existing example — follow this)

Existing hooks use TanStack Query with this pattern:
```typescript
// from useEventRegistrations.ts (organizer hook)
const { data, isLoading, error } = useQuery({
  queryKey: ['event-registrations', eventCode, filters],
  queryFn: () => eventRegistrationService.getRegistrations(eventCode, filters),
  enabled: !!eventCode,
  staleTime: 60_000,
});
```
`useMyRegistration` follows the same pattern but adds `enabled: !!eventCode && isAuthenticated`.

### Project Structure Notes

- Backend services: `services/event-management-service/src/main/java/ch/batbern/events/`
  - Controllers: `.../controller/`
  - Services: `.../service/`
  - Repositories: `.../repository/`
  - Domain entities: `.../domain/`
  - Generated DTOs: `build/generated/` (do NOT edit these — edit the OpenAPI spec)
- Frontend: `web-frontend/src/`
  - Hooks: `hooks/` (sub-folder per hook group when hook + test + index > 3 files)
  - Services: `services/`
  - Components (public): `components/public/`
  - Pages (public): `pages/public/`
  - i18n locales: `public/locales/{lang}/{namespace}.json`
- Tests:
  - Backend integration tests: `src/test/java/.../` extending `AbstractIntegrationTest` (PostgreSQL via Testcontainers — never H2)
  - Frontend unit tests: co-located `*.test.ts(x)` files, run with Vitest
  - E2E: `e2e/*.spec.ts` (Playwright)

### References

- Story 10.10 spec: [Source: docs/prd/epic-10-additional-stories.md#Story-10.10]
- Registration entity & service: [Source: services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java]
- Registration repository: [Source: services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java]
- EventCard component: [Source: web-frontend/src/components/public/EventCard.tsx]
- EventController (GET registrations, POST registrations): [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java#L1464-L1630]
- ADR-003 Meaningful Identifiers: [Source: docs/architecture/ADR-003-meaningful-identifiers-public-apis.md]
- ADR-004 Factor User Fields: [Source: docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md]
- ADR-006 Contract-First: [Source: docs/architecture/ADR-006-openapi-contract-first-code-generation.md]
- OpenAPI events spec: [Source: docs/api/events.openapi.yml]
- i18n patterns: [Source: _bmad-output/implementation-artifacts/10-9-i18n-cleanup.md] — use `useTranslation(['registration', 'events'])` if component spans both namespaces
- EventParticipantsTab (organizer): [Source: web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/test-10-10-backend.log`
- `/tmp/test-10-10-frontend.log`
- `/tmp/typecheck-10-10.log`
- `/tmp/lint-10-10.log`

### Completion Notes List

### File List

| File | Change |
|------|--------|
| `docs/api/events-api.openapi.yml` | Added `GET /events/{eventCode}/my-registration` + `MyRegistrationResponse` schema |
| `services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java` | Added `findByEventCodeAndAttendeeUsername` JPQL query |
| `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java` | Added `getMyRegistration()`; updated `createRegistration()` for CANCELLED re-registration (T4.6) |
| `services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java` | Added `getMyRegistration` endpoint with `@PreAuthorize("isAuthenticated()")` |
| `services/event-management-service/src/test/java/ch/batbern/events/controller/RegistrationStatusIntegrationTest.java` | New — 7 GET status tests + T4.6.2 POST re-registration test |
| `web-frontend/src/hooks/useMyRegistration.ts` | New hook (AC1, AC7, AC8) |
| `web-frontend/src/hooks/useMyRegistration.test.ts` | New unit tests |
| `web-frontend/src/services/registrationService.ts` | Added `getMyRegistration()` service function |
| `web-frontend/src/components/public/RegistrationStatusBanner.tsx` | New component (AC2, AC3, AC4) |
| `web-frontend/src/components/public/__tests__/RegistrationStatusBanner.test.tsx` | New tests |
| `web-frontend/src/components/public/Registration/RegistrationWizard.tsx` | Guard screen inline (AC6/T11); cache invalidation (AC7/T6.8) |
| `web-frontend/src/components/public/Registration/__tests__/RegistrationWizard.test.tsx` | Guard tests added (Story 10.10) |
| `web-frontend/src/components/public/EventCard.tsx` | Added `myRegistrationStatus` prop + status chip (AC5) |
| `web-frontend/src/components/public/UpcomingEventsSection.tsx` | `UpcomingEventCardWithStatus` wrapper per-event hook |
| `web-frontend/src/pages/public/HomePage.tsx` | Integrated `RegistrationStatusBanner` (AC2, AC4) |
| `web-frontend/src/pages/public/ArchivePage.tsx` | `ArchiveEventCardWithStatus` with 12-month filter (AC5/T10.5) |
| `web-frontend/src/pages/public/__tests__/ArchivePage.test.tsx` | Updated |
| `web-frontend/src/types/generated/events-api.types.ts` | Regenerated (MyRegistrationResponse schema) |
| `web-frontend/public/locales/en/registration.json` | Added `registrationStatusBanner.*` + `registrationStatusGuard.*` keys |
| `web-frontend/public/locales/de/registration.json` | German translations |
| `web-frontend/public/locales/en/events.json` | Added `eventCard.statusChip.*` keys |
| `web-frontend/public/locales/de/events.json` | German translations |
| `web-frontend/public/locales/{es,fi,fr,it,ja,nl,rm,gsw-BE}/registration.json` | `[MISSING]` placeholders |
| `web-frontend/public/locales/{es,fi,fr,it,ja,nl,rm,gsw-BE}/events.json` | `[MISSING]` placeholders |
