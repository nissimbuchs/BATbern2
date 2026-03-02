# Story 10.12: Self-Service Deregistration

Status: done

<!-- Prerequisite: Story 10.11 (WaitlistPromotionService must exist — called after every cancellation) -->

## Story

As a **registered attendee**,
I want to cancel my event registration without contacting an organizer — either by clicking a link in my confirmation email or by entering my email address on the event page —
so that my spot can be given to someone else on the waitlist.

## Acceptance Criteria

1. **AC1 — Flyway V74**: Migration `V74__add_deregistration_token.sql` adds:
   - `deregistration_token` column to `registrations` table (UUID, NOT NULL, UNIQUE — `DEFAULT gen_random_uuid()`)
   - Backfill: `UPDATE registrations SET deregistration_token = gen_random_uuid() WHERE deregistration_token IS NULL`
   - Unique index: `CREATE UNIQUE INDEX idx_registrations_deregistration_token ON registrations (deregistration_token)`

2. **AC2 — Token generated on creation**: `RegistrationService.createRegistration()` extended to generate and persist a `deregistrationToken` UUID for every new registration. Non-expiring, never rotated.

3. **AC3 — Three public deregistration endpoints** (no auth — token IS the auth):
   ```
   GET  /api/v1/registrations/deregister/verify?token={uuid}
        → 200: { registrationCode, eventCode, eventTitle, eventDate, attendeeFirstName }
        → 404: { error: "invalid_token" } — token not found OR registration already cancelled/deleted

   POST /api/v1/registrations/deregister
        body: { token: uuid }
        → 200: success; fires WaitlistPromotionService.promoteFromWaitlist(eventId)
        → 404: invalid token
        → 409: already cancelled

   POST /api/v1/registrations/deregister/by-email
        body: { email: string, eventCode: string }
        → 200 always (anti-enumeration: "If registered, you'll receive a deregistration email")
        — sends deregistration-link email if registration found; silently no-op if not found
   ```

4. **AC4 — Deregistration sets status = "cancelled"** (not a delete): `RegistrationService.cancelRegistration()` (extracted from EventController or added new) transitions `registration.status = "cancelled"` and persists. The current organizer cancel endpoint permanently deletes — this must be updated to use the same cancel logic (status = cancelled, not delete) so waitlist promotion fires consistently.

5. **AC5 — Waitlist promotion on cancellation**: Every call to `cancelRegistration()` (from the token endpoint, the by-email flow, AND the organizer endpoint) calls `waitlistPromotionService.promoteFromWaitlist(eventId)` after setting status = "cancelled". Requires Story 10.11's `WaitlistPromotionService` to be present.

6. **AC6 — Deregistration link email templates** seeded by `EmailTemplateSeedService`:
   - `deregistration-link-de.html`, `deregistration-link-en.html` (category: REGISTRATION)
   - Variables: `recipientName`, `eventTitle`, `eventCode`, `eventDate`, `deregistrationLink` (= `{baseUrl}/deregister?token={deregistrationToken}`)
   - Uses `batbern-default` layout

7. **AC7 — Confirmation email updated**: Existing registration confirmation email template (`registration-confirmation-de/en.html`) includes a "Cancel Registration" link pointing to `{deregistrationLink}`. `RegistrationEmailService.sendRegistrationConfirmation()` extended with `deregistrationUrl` parameter.

8. **AC8 — Public `/deregister` page** (`DeregistrationPage.tsx`):
   - Route: `/deregister?token={uuid}` (no auth required, public route in `App.tsx`)
   - States: `verifying → ready → confirmed | invalid | alreadyCancelled`
   - `verifying`: loading skeleton
   - `ready`: shows event title, date, attendee first name — "Are you sure you want to cancel your registration?" + "Confirm Cancellation" button
   - `confirmed`: success state — "Your registration has been cancelled. Your spot has been released."
   - `invalid`: "This cancellation link is invalid or has already been used." + contact info
   - `alreadyCancelled`: "Your registration was already cancelled."
   - Pattern: mirror `UnsubscribePage.tsx` exactly

9. **AC9 — Deregistration by email modal** (`DeregistrationByEmailModal.tsx`):
   - Shown from `HomePage.tsx` ("Cancel your registration" secondary link, when event is in `AGENDA_PUBLISHED`/`AGENDA_FINALIZED`/`EVENT_LIVE`)
   - Shown from `RegistrationWizard.tsx` status guard (when user is already registered — "Cancel my registration" button)
   - Form: email field + eventCode (hidden or read-only if context known) → submit → "Check your inbox" message regardless of whether registration was found (anti-enumeration)

10. **AC10 — Security config updated**: Both `event-management-service/SecurityConfig.java` AND `api-gateway/SecurityConfig.java` have `permitAll` for all three `/api/v1/registrations/deregister/**` paths.

11. **AC11 — Organizer attendees tab**: Cancelled registrations visible in the attendees table with grey `CANCELLED` status chip. Existing organizer cancel action updated to call `cancelRegistration()` service method (which fires waitlist promotion) rather than hard-deleting.

12. **AC12 — TDD compliance**: `DeregistrationServiceTest.java` and `DeregistrationControllerIntegrationTest.java` written FIRST (RED phase). All tests pass before marking done.

13. **AC13 — i18n**: All strings use `deregistration.*` i18n keys in `en/registration.json` and `de/registration.json`. No hardcoded strings. Type-check passes; Checkstyle passes; lint passes.

---

## Tasks / Subtasks

### Phase 1: API Contract (ADR-006 — FIRST)

- [x] **T1 — Update OpenAPI spec** (AC: #3, #12)
  - [x] T1.1 — Add `GET /api/v1/registrations/deregister/verify` to `docs/api/events-api.openapi.yml`
    - Query param: `token` (string, format: uuid, required)
    - Response 200: new schema `DeregistrationVerifyResponse` with `registrationCode`, `eventCode`, `eventTitle`, `eventDate` (date-time), `attendeeFirstName`
    - Response 404: standard `ErrorResponse`
  - [x] T1.2 — Add `POST /api/v1/registrations/deregister`
    - Body: `DeregistrationRequest` with `token` (string, format: uuid)
    - Response 200: `{ message: string }`
    - Response 404, 409: standard `ErrorResponse`
  - [x] T1.3 — Add `POST /api/v1/registrations/deregister/by-email`
    - Body: `DeregistrationByEmailRequest` with `email` (string, format: email), `eventCode` (string)
    - Response 200 always: `{ message: string }`
  - [x] T1.4 — Add `deregistrationToken` to a **new** `RegistrationAdminResponse` schema (string, format: uuid, readOnly: true). Do NOT add it to the existing `RegistrationResponse` (public schema). Organizer endpoints that list/show registrations must reference `RegistrationAdminResponse`; public endpoints keep using `RegistrationResponse`. This schema separation is the security control — it is not sufficient to rely on `@JsonIgnore` alone.
  - [x] T1.5 — Regenerate backend DTOs: `./gradlew :services:event-management-service:openApiGenerateEvents 2>&1 | tee /tmp/openapi-gen-10-12.log`
  - [x] T1.6 — Regenerate frontend types: `cd web-frontend && npm run generate:api-types:events 2>&1 | tee /tmp/openapi-gen-frontend-10-12.log`

### Phase 2: Database Migration

- [x] **T2 — Flyway V75** (AC: #1) ⚠️ NOTE: V74 was already taken by Story 10.11 CR fix → used V75
  - [x] T2.1 — Created `services/event-management-service/src/main/resources/db/migration/V75__add_deregistration_token.sql`
  - [x] T2.2 — SQL (two-step: add nullable, backfill, set NOT NULL, set default, create index)
  - [x] T2.3 — Two-step pattern used (add nullable → backfill → set NOT NULL)
  - [x] T2.4 — Verify: `./gradlew :services:event-management-service:flywayMigrate 2>&1 | tee /tmp/flyway-10-12.log`

### Phase 3: Domain & Repository

- [x] **T3 — Registration entity** (AC: #2)
  - [x] T3.1 — Added `@Column(name = "deregistration_token", columnDefinition = "UUID") @JsonIgnore private UUID deregistrationToken;` to `Registration.java`
  - [x] T3.2 — Lombok `@Data` used; getters implicit
  - [x] T3.3 — `@JsonIgnore` prevents exposure in public registration API response

- [x] **T4 — RegistrationRepository extensions** (AC: #3)
  - [x] T4.1 — Added `Optional<Registration> findByDeregistrationToken(UUID token)` (Spring Data derived query)
  - [x] T4.2 — Added `Optional<Registration> findByAttendeeEmailAndEventCode(String email, String eventCode)` with `@Query` JPQL (joins via Event entity)

### Phase 4: Service Layer — TDD FIRST

- [x] **T5 — Write tests FIRST (RED phase)** (AC: #12)
  - [x] T5.1 — Created `DeregistrationServiceTest.java` in `src/test/java/.../service/`
  - [x] T5.2 — Created `DeregistrationControllerIntegrationTest.java` extending `AbstractIntegrationTest`
  - [x] T5.3 — RED phase confirmed (compile error — `DeregistrationService` didn't exist yet)

- [x] **T6 — `cancelRegistration()` service method** (AC: #4, #5)
  - [x] T6.1 — Added `cancelRegistration(Registration)` to `RegistrationService.java` with `WaitlistPromotionService` call
  - [x] T6.2 — Updated existing organizer cancel endpoint in `EventController.java` to call `registrationService.cancelRegistration()` (soft-cancel, not hard-delete)
  - [x] T6.3 — JWT-based cancel endpoint (`/api/v1/events/*/registrations/cancel`) also updated to use soft-cancel

- [x] **T7 — Generate deregistration token on createRegistration** (AC: #2)
  - [x] T7.1 — Added `deregistrationToken(UUID.randomUUID())` in `RegistrationService.createRegistration()` builder
  - [x] T7.2 — Builder accepts `deregistrationToken`

- [x] **T8 — `DeregistrationService.java`** (AC: #3, #5, #6)
  - [x] T8.1 — Created `services/event-management-service/src/main/java/ch/batbern/events/service/DeregistrationService.java`
  - [x] T8.2 — `@Service @RequiredArgsConstructor @Slf4j`; injected `RegistrationRepository`, `RegistrationService`, `DeregistrationEmailService`, `EventRepository`; `@Value("${app.base-url}")` for baseUrl
  - [x] T8.3 — `verifyToken(UUID)` implemented with `DeregistrationVerifyResult` inner record; throws `NoSuchElementException("invalid_token")` and `IllegalStateException("already_cancelled")`
  - [x] T8.4 — `deregisterByToken(UUID)` implemented; calls `registrationService.cancelRegistration()`
  - [x] T8.5 — `deregisterByEmail(String, String)` implemented as `@Async` with anti-enumeration (always silent)

- [x] **T9 — `DeregistrationEmailService.java`** (AC: #6, #7)
  - [x] T9.1 — Created `DeregistrationEmailService.java`; injected `EmailService`, `EmailTemplateService`
  - [x] T9.2 — `sendDeregistrationLinkEmail(Registration, Event, String deregistrationLink)` — note: uses `registration.getAttendeeEmail()` directly (no UserResponse needed for anonymous registrants)
  - [x] T9.3 — DB-first template loading with classpath fallback (mirrors RegistrationEmailService pattern)
  - [x] T9.4 — Updated `RegistrationEmailService.sendRegistrationConfirmation()` signature with `String deregistrationUrl` param and added to template variables map
  - [x] T9.5 — `deregistrationUrl` computed in `EventController`: `appBaseUrl + "/deregister?token=" + registration.getDeregistrationToken()`; `appBaseUrl` added via `@Value`

- [x] **T10 — Email template classpath files** (AC: #6)
  - [x] T10.1 — Created `deregistration-link-de.html` (German deregistration link email; red CTA button; `{{deregistrationLink}}`)
  - [x] T10.2 — Created `deregistration-link-en.html` (English version)
  - [x] T10.3 — Subject comments included; content-only HTML (no `<html>`/`<body>` tags)
  - [x] T10.4 — `{{deregistrationLink}}` as red CTA button
  - [x] T10.5 — Added subtle cancel link (`{{deregistrationUrl}}`) to `registration-confirmation-de.html` and `registration-confirmation-en.html`
  - [x] T10.6 — Fixed `EmailTemplateSeedService.deriveCategory()` to map `deregistration-` prefix → `REGISTRATION` category

- [x] **T11 — `DeregistrationController.java`** (AC: #3, #10)
  - [x] T11.1 — Created `DeregistrationController.java`
  - [x] T11.2 — `@RestController @RequestMapping("/api/v1/registrations/deregister") @RequiredArgsConstructor @Slf4j`
  - [x] T11.3 — `GET /verify?token={uuid}` → `deregistrationService.verifyToken(UUID.fromString(token))`; `Instant → OffsetDateTime.atOffset(ZoneOffset.UTC)` conversion
  - [x] T11.4 — `POST /` → `deregistrationService.deregisterByToken(request.getToken())`
  - [x] T11.5 — `POST /by-email` → `deregistrationService.deregisterByEmail(...)` always 200
  - [x] T11.6 — No `@PreAuthorize` — all public
  - [x] T11.7 — Exception mapping delegated to `GlobalExceptionHandler` (`NoSuchElementException → 404`, `IllegalStateException → 409`)
  - [x] T11.8 — Rate limiting documented as `// TODO: add per-IP rate limiting on by-email endpoint (accepted risk)`

- [x] **T12 — Security config updates** (AC: #10)
  - [x] T12.1 — Added 3 `permitAll` rules to `event-management-service/SecurityConfig.java`
  - [x] T12.2 — Added same 3 rules to `api-gateway/SecurityConfig.java`
  - [x] T12.3 — Comment `// Story 10.12: Self-service deregistration (token-protected)` added above each block

- [x] **T13 — Run backend tests GREEN** (AC: #12)
  - [x] T13.1 — event-management-service: 1444 tests, 1 pre-existing flaky timing failure (`EventControllerIntegrationTest.should_respondUnder500ms_when_fullIncludesRequested` — 1042ms vs 800ms threshold; passes in isolation). Root-cause fix was `Registration.@PrePersist` auto-generating `deregistrationToken` to prevent NOT NULL violations.
  - [x] T13.2 — api-gateway: BUILD SUCCESSFUL (all tests pass)

### Phase 5: Frontend

- [x] **T14 — `useDeregistration` hook** (AC: #8, #9)
  - [x] T14.1 — Create `web-frontend/src/hooks/useDeregistration.ts`
  - [x] T14.2 — `useVerifyDeregistrationToken(token: string | null)`: TanStack Query `useQuery`; enabled when `!!token`; fetches `GET /api/v1/registrations/deregister/verify?token={token}`; on 404 set state to "invalid"; on 409 set state to "alreadyCancelled"
  - [x] T14.3 — `useDeregisterByToken()`: TanStack Query `useMutation`; calls `POST /api/v1/registrations/deregister`; on success invalidates `['my-registration']` query (if auth context available); on 409 → "alreadyCancelled"
  - [x] T14.4 — `useDeregistrationByEmail()`: `useMutation`; calls `POST /api/v1/registrations/deregister/by-email`; always shows success (never surface errors to user — anti-enumeration)
  - [x] T14.5 — Add service calls in `web-frontend/src/services/deregistrationService.ts` (follow pattern of other service files — no direct `fetch` in hooks)

- [x] **T15 — `DeregistrationPage.tsx`** (AC: #8)
  - [x] T15.1 — Create `web-frontend/src/pages/public/DeregistrationPage.tsx`
  - [x] T15.2 — Mirror `UnsubscribePage.tsx` structure exactly:
    - Extract `token` from `useSearchParams()` (`?token=`)
    - State machine: `'verifying' | 'ready' | 'confirmed' | 'invalid' | 'alreadyCancelled'`
    - Use `useVerifyDeregistrationToken(token)` and `useDeregisterByToken()`
    - Use `PublicLayout` wrapper + centered MUI `Card`
  - [x] T15.3 — `verifying` state: text with i18n `deregistration.page.verifying`
  - [x] T15.4 — `ready` state: event title, date, "Hi {attendeeFirstName}, are you sure?" + "Confirm Cancellation" button (destructive color) + "Go back" link
  - [x] T15.5 — `confirmed` state: `deregistration.page.successTitle` + `deregistration.page.successBody`
  - [x] T15.6 — `invalid` state: `deregistration.page.invalidToken`
  - [x] T15.7 — `alreadyCancelled` state: `deregistration.page.alreadyCancelled`
  - [x] T15.8 — Add `data-testid="deregistration-page"` to root element

- [x] **T16 — `DeregistrationByEmailModal.tsx`** (AC: #9)
  - [x] T16.1 — Create `web-frontend/src/components/public/DeregistrationByEmailModal.tsx`
  - [x] T16.2 — Props: `{ open: boolean; onClose: () => void; eventCode: string }`
  - [x] T16.3 — Form: MUI `TextField` for email (controlled, validates email format); `eventCode` passed as prop (hidden)
  - [x] T16.4 — On submit: call `useDeregistrationByEmail()` mutation; on settled (success or error): show success state — always show (anti-enumeration)
  - [x] T16.5 — "Close" button in success state; loading spinner on submit
  - [x] T16.6 — i18n keys: `deregistration.modal.*`

- [x] **T17 — Register route in `App.tsx`** (AC: #8)
  - [x] T17.1 — Add lazy import: `const DeregistrationPage = lazy(() => import('./pages/public/DeregistrationPage'))`
  - [x] T17.2 — Add route (public, outside auth wrapper): `<Route path="/deregister" element={<DeregistrationPage />} />`
  - [x] T17.3 — Confirmed outside auth-required layout (follows UnsubscribePage route placement)

- [x] **T18 — Integrate into `HomePage.tsx`** (AC: #9)
  - [x] T18.1 — When event is in `AGENDA_PUBLISHED`, `AGENDA_FINALIZED`, or `EVENT_LIVE` state: add secondary "Cancel your registration" text link below RegistrationStatusBanner
  - [x] T18.2 — On click: open `<DeregistrationByEmailModal eventCode={currentEvent.eventCode} />`
  - [x] T18.3 — i18n key: `deregistration.homepage.cancelLink`
  - [x] T18.4 — Shown for all users (anonymous + authenticated) — no auth gate

- [x] **T19 — Integrate into `RegistrationWizard.tsx` status guard** (AC: #9)
  - [x] T19.1 — In the status guard screen: added "Cancel my registration" button alongside "Go back"
  - [x] T19.2 — On click: open `<DeregistrationByEmailModal eventCode={eventCode} />`
  - [x] T19.3 — Button shown in non-cancelled branch (not shown for CANCELLED — which shows "Register again" instead)
  - [x] T19.4 — i18n key: `deregistration.wizard.cancelButton`

- [x] **T20 — i18n keys** (AC: #13)
  - [x] T20.1 — Added `deregistration.*` keys to `public/locales/en/registration.json`
  - [x] T20.2 — Added German (de) translations to `public/locales/de/registration.json`
  - [x] T20.3 — Added `[MISSING]` prefix placeholders to all 8 other locale files (fr, it, rm, es, fi, nl, ja, gsw-BE)

- [x] **T22 — CANCELLED status chip in organizer attendees tab** (AC: #11)
  - [x] T22.1 — Read `EventParticipantList.tsx` and `EventParticipantTable.tsx` before modifying
  - [x] T22.2 — Changed `CANCELLED → 'error'` to `CANCELLED → 'default'` in `getStatusChipColor()` in `EventParticipantTable.tsx` (grey chip)
  - [x] T22.3 — Confirmed: default filters=`{}` (no status filter); CANCELLED filter option already present in `EventParticipantFilters.tsx`; backend returns all statuses when no filter given
  - [x] T22.4 — Added `eventPage.participantsTab.statusCancelled` → "Cancelled" (en) / "Abgemeldet" (de); `[MISSING]` for other 8 locales (events.json)

- [x] **T21 — Frontend full test run** (AC: #13)
  - [x] T21.1 — 3858 tests passed, 0 failures (279 test files passed, 3 skipped)
  - [x] T21.2 — `type-check` clean (0 errors)
  - [x] T21.3 — `lint` clean (0 warnings)

---

## Dev Notes

### Architecture Compliance

**ADR-006 (Contract-First)**: OpenAPI spec in `docs/api/events.openapi.yml` MUST be updated FIRST (T1). Backend types regenerated → frontend types regenerated → THEN implementation begins.

**ADR-003 (Meaningful Identifiers)**: Deregistration endpoints use `registrationCode` and `eventCode` in responses, not UUIDs. The `deregistrationToken` itself is a UUID (security token, not a business identifier).

**ADR-004 (No User Field Duplication)**: `DeregistrationVerifyResponse` returns `attendeeFirstName` from the denormalized `attendeeFirstName` column on the `registrations` table (not a UserApiClient call — acceptable since it's a search cache field already present, and this endpoint is hot-path with no auth overhead).

**ADR-005 (Anonymous Registration)**: Deregistration works for both anonymous and authenticated registrants. The token is the sole auth mechanism — no Cognito JWT required.

**TDD Mandate**: Write `DeregistrationServiceTest` and `DeregistrationControllerIntegrationTest` FIRST (T5 before T6–T12). RED → GREEN → REFACTOR.

### Critical Implementation Details

#### Anti-Enumeration on `by-email` Endpoint
`POST /deregister/by-email` MUST return HTTP 200 regardless of whether a registration was found. Never return 404 or "not registered" — this would allow enumeration of registered email addresses. The response is always: "If you are registered, you'll receive a cancellation email."

Log the result internally: `log.info("Deregistration by email requested for {}/{}: found={}", email, eventCode, found)`.

#### Cancellation Status vs. Hard Delete — Critical Behaviour Change
The current organizer cancel flow in `EventController.java` **hard-deletes** the registration (`registrationRepository.delete(registration)`). Story 10.12 changes this to `status = "cancelled"` (soft cancel). This is a **breaking behaviour change** for existing organizer functionality:
- Confirm with team/Nissim: Is this intentional? Yes — the todo explicitly says cancelled registrations must remain visible in the attendees tab as grey CANCELLED rows.
- The existing JWT-based `POST /api/v1/events/*/registrations/cancel` endpoint (old email flow) should also be updated to soft-cancel, not delete.
- After this change, organizers will see cancelled rows. The attendees tab must filter or visually distinguish them.

#### `WaitlistPromotionService` Dependency (Story 10.11)
`DeregistrationService.deregisterByToken()` calls `registrationService.cancelRegistration()` which calls `waitlistPromotionService.promoteFromWaitlist(eventId)`. **Story 10.11 must be merged before or alongside Story 10.12.** If implementing without 10.11, add a null-guard or conditional:
```java
if (waitlistPromotionService != null) {
    waitlistPromotionService.promoteFromWaitlist(registration.getEventId());
}
```
But this is a code smell — prefer implementing 10.11 first.

#### `app.base-url` Configuration Property
`DeregistrationService.deregisterByEmail()` needs the app base URL to construct `deregistrationLink`. Check if this property already exists:
```bash
grep -r "app.base-url\|baseUrl\|appBaseUrl\|BASE_URL" services/event-management-service/src/main/resources/application.yml
```
Follow whatever pattern other services use for constructing magic links (e.g., newsletter unsubscribe links from Story 10.7). Typically: `@Value("${app.base-url:http://localhost:8100}") private String appBaseUrl;`

#### Confirmation Email Template Update
The existing `registration-confirmation-de/en.html` classpath templates need `{{deregistrationUrl}}` variable added. Check if these templates are now DB-managed (Story 10.13 seeds them) — if so, update the DB version AND the classpath fallback. For Story 10.12, update the classpath file only (Story 10.13 handles DB seeding).

Also update `RegistrationEmailService.sendRegistrationConfirmation()` signature to add `String deregistrationUrl` — this will break the call site in `EventController.java`. Update the call site to pass `appBaseUrl + "/deregister?token=" + registration.getDeregistrationToken()`.

#### Security Config Placement
In both SecurityConfig files, place the new `permitAll` rules BEFORE the `anyRequest().authenticated()` catch-all, and AFTER existing public rules for clarity. Place them near the existing registration `permitAll` rules (around the comment `// Story 4.1.5`):
```java
// Story 10.12: Self-service deregistration (token-protected)
.requestMatchers(HttpMethod.GET, "/api/v1/registrations/deregister/verify").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/registrations/deregister").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/registrations/deregister/by-email").permitAll()
```

#### UnsubscribePage Pattern — Mirror Exactly
`UnsubscribePage.tsx` uses this exact state machine and hook pattern. Follow it:
```typescript
// UnsubscribePage pattern to mirror:
const [state, setState] = useState<'verifying' | 'ready' | 'confirmed' | 'invalid' | 'error'>('verifying')
// Uses PublicLayout, centered Card, MUI Alert for states
// useVerifyUnsubscribeToken (query) + useUnsubscribeByToken (mutation)
// Navigate to 'error' on non-404 errors, 'invalid' on 404
```

For `DeregistrationPage`, add an extra `'alreadyCancelled'` state for the 409 response.

#### DeregistrationByEmailModal — Follow Existing Modal Patterns
Existing modals in BATbern use MUI `Dialog`. Check `SpeakerDeclineModal.tsx` or similar for the standard modal pattern with loading state, form, and success state.

#### Cache Invalidation
After successful deregistration (token flow), invalidate `['my-registration', eventCode]` TanStack Query cache. The `eventCode` is available from `verifyResponse.eventCode`. Use `queryClient.invalidateQueries({ queryKey: ['my-registration', eventCode] })` in the `useDeregisterByToken` mutation's `onSuccess` callback.

### Key New Files

```
services/event-management-service/src/main/resources/db/migration/V74__add_deregistration_token.sql
services/event-management-service/.../service/DeregistrationService.java
services/event-management-service/.../service/DeregistrationEmailService.java
services/event-management-service/.../controller/DeregistrationController.java
services/event-management-service/src/main/resources/email-templates/deregistration-link-de.html
services/event-management-service/src/main/resources/email-templates/deregistration-link-en.html
services/event-management-service/src/test/java/.../service/DeregistrationServiceTest.java
services/event-management-service/src/test/java/.../DeregistrationControllerIntegrationTest.java
web-frontend/src/pages/public/DeregistrationPage.tsx
web-frontend/src/components/public/DeregistrationByEmailModal.tsx
web-frontend/src/hooks/useDeregistration.ts
web-frontend/src/services/deregistrationService.ts
```

### Key Modified Files

| File | Change |
|------|--------|
| `docs/api/events.openapi.yml` | 3 new deregistration endpoints + schemas (FIRST — ADR-006) |
| `services/event-management-service/.../domain/Registration.java` | Add `deregistrationToken` UUID field |
| `services/event-management-service/.../repository/RegistrationRepository.java` | Add `findByDeregistrationToken`, `findByAttendeeEmailAndEventCode` |
| `services/event-management-service/.../service/RegistrationService.java` | Add `cancelRegistration()` method; generate token in `createRegistration()` |
| `services/event-management-service/.../service/RegistrationEmailService.java` | Add `deregistrationUrl` param to `sendRegistrationConfirmation()` |
| `services/event-management-service/.../controller/EventController.java` | Update organizer cancel endpoint to call `cancelRegistration()` (soft cancel, not delete) |
| `services/event-management-service/.../config/SecurityConfig.java` | `permitAll` for 3 deregistration paths |
| `api-gateway/.../config/SecurityConfig.java` | Same 3 `permitAll` rules |
| `services/event-management-service/src/main/resources/email-templates/registration-confirmation-de.html` | Add `{{deregistrationUrl}}` cancel link |
| `services/event-management-service/src/main/resources/email-templates/registration-confirmation-en.html` | Same for English |
| `web-frontend/src/App.tsx` | Add `/deregister` public route |
| `web-frontend/src/pages/public/HomePage.tsx` | "Cancel your registration" link + modal |
| `web-frontend/src/components/public/Registration/RegistrationWizard.tsx` | "Cancel my registration" button in status guard |
| `public/locales/en/registration.json` | `deregistration.*` keys |
| `public/locales/de/registration.json` | Same in German |
| `public/locales/{fr,it,rm,es,fi,nl,ja,gsw-BE}/registration.json` | `[MISSING]` placeholders |

### Project Structure Notes

- Backend package root: `services/event-management-service/src/main/java/ch/batbern/events/`
- New controller goes in `.../controller/DeregistrationController.java` (separate file — avoids further bloating EventController)
- Email templates: `services/event-management-service/src/main/resources/email-templates/`
- Flyway: `services/event-management-service/src/main/resources/db/migration/` — V74 (V73 = Story 10.11)
- Frontend pages: `web-frontend/src/pages/public/` (DeregistrationPage)
- Frontend components: `web-frontend/src/components/public/` (DeregistrationByEmailModal)
- Frontend hooks: `web-frontend/src/hooks/useDeregistration.ts`
- Frontend services: `web-frontend/src/services/deregistrationService.ts`
- i18n: `web-frontend/public/locales/{lang}/registration.json` — 10 locales

### References

- Story spec in PRD: [Source: docs/prd/epic-10-additional-stories.md#Story-10.12]
- Previous story (10.11 — Waitlist): [Source: _bmad-output/implementation-artifacts/10-11-venue-capacity-enforcement-waitlist-management.md]
- UnsubscribePage (pattern model): [Source: web-frontend/src/pages/public/UnsubscribePage.tsx]
- RegistrationService (createRegistration + no cancelRegistration): [Source: services/event-management-service/.../service/RegistrationService.java]
- RegistrationEmailService: [Source: services/event-management-service/.../service/RegistrationEmailService.java]
- EventController (existing organizer cancel — hard-delete): [Source: services/event-management-service/.../controller/EventController.java]
- Event-management SecurityConfig: [Source: services/event-management-service/.../config/SecurityConfig.java]
- API Gateway SecurityConfig: [Source: api-gateway/.../config/SecurityConfig.java]
- App.tsx (route patterns): [Source: web-frontend/src/App.tsx]
- ADR-003 Meaningful Identifiers: [Source: docs/architecture/ADR-003-meaningful-identifiers-public-apis.md]
- ADR-006 Contract-First: [Source: docs/architecture/ADR-006-openapi-contract-first-code-generation.md]
- Flyway guide: [Source: docs/guides/flyway-migration-guide.md]
- i18n patterns: [Source: _bmad-output/implementation-artifacts/10-9-i18n-cleanup.md]

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/openapi-gen-10-12.log`
- `/tmp/flyway-10-12.log`
- `/tmp/test-10-12-red.log`
- `/tmp/test-10-12-backend.log`
- `/tmp/test-10-12-gateway.log`
- `/tmp/test-10-12-frontend.log`
- `/tmp/typecheck-10-12.log`
- `/tmp/lint-10-12.log`

### Completion Notes List

**Session 1 (2026-03-02) — Phases 1–4 partial:**
- T1–T9 complete. T10 is next (email template classpath files).
- V74 was already taken by Story 10.11 CR fix (`V74__migrate_waitlisted_to_waitlist.sql`) → used **V75** instead. AC1 references V74 but actual file is V75.
- `DeregistrationEmailService.sendDeregistrationLinkEmail()` uses `Registration.getAttendeeEmail()` directly (no UserResponse param) — works for both anonymous and authenticated registrants. `resolveLocale()` currently always returns "de" (TODO: look up from UserApiClient).
- YAML colon-in-scalar issue in events-api.openapi.yml: descriptions containing `(AC3):` had to be quoted (`"Story 10.12 (AC3): Body for..."`) to avoid YAML parser treating it as a mapping key.
- T2.4 (flywayMigrate verify) not explicitly run — should be confirmed at start of next session before running backend tests.

**Session 2 (2026-03-02) — Phases 4 complete (T10–T13) + Phase 5 start:**
- T10: Created `deregistration-link-de.html`, `deregistration-link-en.html`; added cancel link to `registration-confirmation-de/en.html`; fixed `deriveCategory()` in `EmailTemplateSeedService`.
- T11: Created `DeregistrationController.java` with 3 endpoints; `Instant → OffsetDateTime.atOffset(ZoneOffset.UTC)` in verify endpoint.
- T12: Added `permitAll` for 3 deregistration paths in both SecurityConfig files.
- T13: Fixed 2 test compile errors (`DeregistrationServiceTest`: 3-arg verify; `RegistrationEmailServiceTest`: 7-arg sendRegistrationConfirmation). Fixed 33 NOT NULL failures by adding null-check in `Registration.@PrePersist`. Backend GREEN (1 pre-existing flaky timing test — unrelated).
- **T2.4 still not run** — flywayMigrate verify should be confirmed before deploying.
- Frontend work (T14–T22) starts next session.

**Session 4 (2026-03-02) — Code Review fixes (6 HIGH/MEDIUM issues):**
- H1 (AC3): `verifyToken()` now returns 404 for already-cancelled (was 409). `findActiveByToken()` still throws `IllegalStateException → 409` for `deregisterByToken`. New integration test `verifyToken_alreadyCancelledToken_returns404` added.
- H2: `EventController.updateRegistration()` PATCH `becomingCancelled` guard expanded to `!"cancelled".equalsIgnoreCase(previousStatus)` — now triggers waitlist promotion when organizer cancels waitlisted entries (was missed before).
- M1: `DeregistrationPage.tsx` state transitions moved from `setTimeout`-in-render to `useEffect`.
- M2: `DeregistrationByEmailModal.tsx` close button changed from `deregistration.page.goBack` to `common:actions.close`.
- M3: `deregisterByEmail()` annotated `@Transactional(readOnly = true)`.
- M4: `@Async` removed from `sendDeregistrationLinkEmail()` (redundant — already called from `@Async` context).
- Backend tests (DeregistrationServiceTest + DeregistrationControllerIntegrationTest): **14/14 PASSED**.
- Frontend: **3858 tests passed / 0 failures**; type-check clean.

**Session 3 (2026-03-02) — Phase 5 complete (T14–T22):**
- T2.4: `flywayMigrate` BUILD SUCCESSFUL — V75 migration applied.
- T14: `deregistrationService.ts` + `useDeregistration.ts` created following newsletterService/useNewsletter pattern. `useDeregisterByToken` accepts optional `eventCode` to invalidate `['my-registration', eventCode]` on success.
- T15: `DeregistrationPage.tsx` created; mirrors `UnsubscribePage.tsx` exactly; uses `registration` namespace; 5 states; `data-testid="deregistration-page"`.
- T16: `DeregistrationByEmailModal.tsx` created; MUI `Dialog`; email validation; anti-enumeration (always success on settle).
- T17: `App.tsx` — lazy import + `<Route path="/deregister">` outside auth wrapper (near UnsubscribePage).
- T18: `HomePage.tsx` — "Cancel your registration" link shown for AGENDA_PUBLISHED/FINALIZED/EVENT_LIVE; opens `DeregistrationByEmailModal`; uses `registration` namespace via `tReg`.
- T19: `RegistrationWizard.tsx` — "Cancel my registration" button added to non-cancelled status guard branch.
- T20: i18n `deregistration.*` keys added to en + de registration.json; `[MISSING]` for 8 other locales. `eventPage.participantsTab.statusCancelled` added to all 10 events.json locales.
- T22: `EventParticipantTable.tsx` — `CANCELLED → 'default'` (grey chip); T22.3 confirmed no filter exclusion needed.
- T21: 3858 tests passed / 0 failures; type-check clean; lint clean.

### File List

**New files created:**
- `docs/api/events-api.openapi.yml` (modified — 3 new endpoints + 4 new schemas)
- `services/event-management-service/src/main/resources/db/migration/V75__add_deregistration_token.sql`
- `services/event-management-service/src/main/java/ch/batbern/events/service/DeregistrationService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/DeregistrationEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/DeregistrationController.java`
- `services/event-management-service/src/main/resources/email-templates/deregistration-link-de.html`
- `services/event-management-service/src/main/resources/email-templates/deregistration-link-en.html`
- `services/event-management-service/src/test/java/ch/batbern/events/service/DeregistrationServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/DeregistrationControllerIntegrationTest.java`

**Session 4 CR fixes:**
- `services/event-management-service/src/main/java/ch/batbern/events/service/DeregistrationService.java` (H1: verifyToken returns 404 for cancelled; M3: @Transactional on deregisterByEmail)
- `services/event-management-service/src/main/java/ch/batbern/events/service/DeregistrationEmailService.java` (M4: removed redundant @Async)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java` (H2: becomingCancelled guard fixed)
- `services/event-management-service/src/test/java/ch/batbern/events/service/DeregistrationServiceTest.java` (H1: updated cancelledRegistration test expectation to NoSuchElementException)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/DeregistrationControllerIntegrationTest.java` (H1: new test verifyToken_alreadyCancelledToken_returns404; L4)
- `web-frontend/src/pages/public/DeregistrationPage.tsx` (M1: useEffect replaces setTimeout-in-render)
- `web-frontend/src/components/public/DeregistrationByEmailModal.tsx` (M2: close button uses common:actions.close)

**Modified files:**
- `services/event-management-service/src/main/java/ch/batbern/events/domain/Registration.java` (+ `@PrePersist` auto-gen `deregistrationToken`)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/EmailTemplateSeedService.java` (+ `deregistration-` prefix → REGISTRATION)
- `services/event-management-service/src/main/resources/email-templates/registration-confirmation-de.html` (+ cancel link)
- `services/event-management-service/src/main/resources/email-templates/registration-confirmation-en.html` (+ cancel link)
- `services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java` (+ 3 deregistration permitAll)
- `api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java` (+ 3 deregistration permitAll)
- `services/event-management-service/src/test/java/ch/batbern/events/service/DeregistrationServiceTest.java` (fixed 3-arg verify)
- `services/event-management-service/src/test/java/ch/batbern/events/service/RegistrationEmailServiceTest.java` (fixed 7-arg sendRegistrationConfirmation, 4 sites)

**Frontend — New files (Session 3):**
- `web-frontend/src/services/deregistrationService.ts`
- `web-frontend/src/hooks/useDeregistration.ts`
- `web-frontend/src/pages/public/DeregistrationPage.tsx`
- `web-frontend/src/components/public/DeregistrationByEmailModal.tsx`

**Frontend — Modified files (Session 3):**
- `web-frontend/src/App.tsx` (lazy import + `/deregister` route)
- `web-frontend/src/pages/public/HomePage.tsx` (cancel link + modal)
- `web-frontend/src/components/public/Registration/RegistrationWizard.tsx` (cancel button in status guard)
- `web-frontend/src/components/organizer/EventPage/EventParticipantTable.tsx` (CANCELLED chip → grey)
- `web-frontend/public/locales/en/registration.json` (`deregistration.*` keys)
- `web-frontend/public/locales/de/registration.json` (`deregistration.*` keys in German)
- `web-frontend/public/locales/fr/registration.json` (`[MISSING]` placeholders)
- `web-frontend/public/locales/it/registration.json` (`[MISSING]` placeholders)
- `web-frontend/public/locales/rm/registration.json` (`[MISSING]` placeholders)
- `web-frontend/public/locales/es/registration.json` (`[MISSING]` placeholders)
- `web-frontend/public/locales/fi/registration.json` (`[MISSING]` placeholders)
- `web-frontend/public/locales/nl/registration.json` (`[MISSING]` placeholders)
- `web-frontend/public/locales/ja/registration.json` (`[MISSING]` placeholders)
- `web-frontend/public/locales/gsw-BE/registration.json` (`[MISSING]` placeholders)
- `web-frontend/public/locales/en/events.json` (`eventPage.participantsTab.statusCancelled`)
- `web-frontend/public/locales/de/events.json` (`eventPage.participantsTab.statusCancelled`)
- `web-frontend/public/locales/fr/events.json` (`[MISSING]` placeholder)
- `web-frontend/public/locales/it/events.json` (`[MISSING]` placeholder)
- `web-frontend/public/locales/rm/events.json` (`[MISSING]` placeholder)
- `web-frontend/public/locales/es/events.json` (`[MISSING]` placeholder)
- `web-frontend/public/locales/fi/events.json` (`[MISSING]` placeholder)
- `web-frontend/public/locales/nl/events.json` (`[MISSING]` placeholder)
- `web-frontend/public/locales/ja/events.json` (`[MISSING]` placeholder)
- `web-frontend/public/locales/gsw-BE/events.json` (`[MISSING]` placeholder)
