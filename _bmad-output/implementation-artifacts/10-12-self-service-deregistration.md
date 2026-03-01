# Story 10.12: Self-Service Deregistration

Status: ready-for-dev

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

- [ ] **T1 — Update OpenAPI spec** (AC: #3, #12)
  - [ ] T1.1 — Add `GET /api/v1/registrations/deregister/verify` to `docs/api/events.openapi.yml`
    - Query param: `token` (string, format: uuid, required)
    - Response 200: new schema `DeregistrationVerifyResponse` with `registrationCode`, `eventCode`, `eventTitle`, `eventDate` (date-time), `attendeeFirstName`
    - Response 404: standard `ErrorResponse`
  - [ ] T1.2 — Add `POST /api/v1/registrations/deregister`
    - Body: `DeregistrationRequest` with `token` (string, format: uuid)
    - Response 200: `{ message: string }`
    - Response 404, 409: standard `ErrorResponse`
  - [ ] T1.3 — Add `POST /api/v1/registrations/deregister/by-email`
    - Body: `DeregistrationByEmailRequest` with `email` (string, format: email), `eventCode` (string)
    - Response 200 always: `{ message: string }`
  - [ ] T1.4 — Add `deregistrationToken` to a **new** `RegistrationAdminResponse` schema (string, format: uuid, readOnly: true). Do NOT add it to the existing `RegistrationResponse` (public schema). Organizer endpoints that list/show registrations must reference `RegistrationAdminResponse`; public endpoints keep using `RegistrationResponse`. This schema separation is the security control — it is not sufficient to rely on `@JsonIgnore` alone.
  - [ ] T1.5 — Regenerate backend DTOs: `./gradlew :services:event-management-service:openApiGenerateEvents 2>&1 | tee /tmp/openapi-gen-10-12.log`
  - [ ] T1.6 — Regenerate frontend types: `cd web-frontend && npm run generate:api-types:events 2>&1 | tee /tmp/openapi-gen-frontend-10-12.log`

### Phase 2: Database Migration

- [ ] **T2 — Flyway V74** (AC: #1)
  - [ ] T2.1 — Create `services/event-management-service/src/main/resources/db/migration/V74__add_deregistration_token.sql`
  - [ ] T2.2 — SQL:
    ```sql
    ALTER TABLE registrations ADD COLUMN deregistration_token UUID;
    UPDATE registrations SET deregistration_token = gen_random_uuid() WHERE deregistration_token IS NULL;
    ALTER TABLE registrations ALTER COLUMN deregistration_token SET NOT NULL;
    ALTER TABLE registrations ALTER COLUMN deregistration_token SET DEFAULT gen_random_uuid();
    CREATE UNIQUE INDEX idx_registrations_deregistration_token ON registrations (deregistration_token);
    ```
  - [ ] T2.3 — Note: use two-step (add nullable, backfill, then set NOT NULL) because existing rows have no token yet
  - [ ] T2.4 — Verify: `./gradlew :services:event-management-service:flywayMigrate 2>&1 | tee /tmp/flyway-10-12.log`

### Phase 3: Domain & Repository

- [ ] **T3 — Registration entity** (AC: #2)
  - [ ] T3.1 — Add `@Column(name = "deregistration_token") @GeneratedValue private UUID deregistrationToken;` to `Registration.java`
  - [ ] T3.2 — Check whether Lombok `@Data` or explicit getters are used — add accordingly
  - [ ] T3.3 — Do NOT expose `deregistrationToken` in public registration API response (only in organizer admin context)

- [ ] **T4 — RegistrationRepository extensions** (AC: #3)
  - [ ] T4.1 — Add `Optional<Registration> findByDeregistrationToken(UUID token)` (Spring Data derived query)
  - [ ] T4.2 — Add `Optional<Registration> findByAttendeeEmailAndEventCode(String email, String eventCode)` — for the by-email lookup

### Phase 4: Service Layer — TDD FIRST

- [ ] **T5 — Write tests FIRST (RED phase)** (AC: #12)
  - [ ] T5.1 — Create `DeregistrationServiceTest.java` in `src/test/java/.../service/`
    - Test: `verifyToken()` with valid token → returns DeregistrationVerifyResponse
    - Test: `verifyToken()` with unknown token → throws `EntityNotFoundException`
    - Test: `verifyToken()` with already-cancelled registration → throws `IllegalStateException` (409)
    - Test: `deregisterByToken()` → status set to "cancelled"; `WaitlistPromotionService.promoteFromWaitlist()` called
    - Test: `deregisterByEmail()` with valid email/eventCode → sends email; returns true (don't care in response, but log it)
    - Test: `deregisterByEmail()` with unknown email → returns without error (anti-enumeration: no exception)
  - [ ] T5.2 — Create `DeregistrationControllerIntegrationTest.java` extending `AbstractIntegrationTest`
    - Test: `GET /deregister/verify?token=valid` → 200 with registration info
    - Test: `GET /deregister/verify?token=unknown` → 404
    - Test: `POST /deregister` with valid token → 200; registration status = "cancelled"
    - Test: `POST /deregister` with valid token (2nd call) → 409
    - Test: `POST /deregister` with unknown token → 404
    - Test: `POST /deregister/by-email` with any input → always 200
    - Test: all endpoints accessible without auth (no JWT required)
    - Test: deregister when a waitlisted registration exists → first waitlisted registration promoted to status=`registered`, promotion email sent (end-to-end Story 10.11 + 10.12 integration)
  - [ ] T5.3 — Run to confirm RED: `./gradlew :services:event-management-service:test --tests DeregistrationServiceTest 2>&1 | tee /tmp/test-10-12-red.log`

- [ ] **T6 — `cancelRegistration()` service method** (AC: #4, #5)
  - [ ] T6.1 — Add `public void cancelRegistration(Registration registration)` to `RegistrationService.java`:
    ```java
    @Transactional
    public void cancelRegistration(Registration registration) {
        registration.setStatus("cancelled");
        registrationRepository.save(registration);
        waitlistPromotionService.promoteFromWaitlist(registration.getEventId());
        log.info("Registration {} cancelled; waitlist promotion triggered for event {}",
            registration.getRegistrationCode(), registration.getEventId());
    }
    ```
  - [ ] T6.2 — Update the existing organizer cancel endpoint in `EventController.java` (currently hard-deletes) to call `registrationService.cancelRegistration(registration)` instead of `registrationRepository.delete(registration)` — this ensures waitlist promotion fires for organizer-initiated cancellations too
  - [ ] T6.3 — **Existing confirm/cancel JWT flow** (`/api/v1/events/*/registrations/cancel`): This uses a short-lived JWT cancellation token for the old flow. Leave it in place — it should also call `cancelRegistration()` (not delete). Update if still doing hard-delete.

- [ ] **T7 — Generate deregistration token on createRegistration** (AC: #2)
  - [ ] T7.1 — In `RegistrationService.createRegistration()`, before or in the `Registration` builder, set `deregistrationToken = UUID.randomUUID()`
  - [ ] T7.2 — Verify that the `Registration` builder (or constructor) accepts `deregistrationToken`

- [ ] **T8 — `DeregistrationService.java`** (AC: #3, #5, #6)
  - [ ] T8.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/service/DeregistrationService.java`
  - [ ] T8.2 — `@Service @RequiredArgsConstructor`; inject `RegistrationRepository`, `RegistrationService`, `DeregistrationEmailService`, `UserApiClient`, `EventRepository`
  - [ ] T8.3 — `public DeregistrationVerifyResponseDto verifyToken(UUID token)`:
    - Find registration by token
    - If not found → throw `EntityNotFoundException("invalid_token")`
    - If `status == "cancelled"` → throw `IllegalStateException("already_cancelled")` (→ 409)
    - Return DTO with `registrationCode`, `eventCode`, `eventTitle`, `eventDate`, `attendeeFirstName`
  - [ ] T8.4 — `public void deregisterByToken(UUID token)`:
    - Find registration by token (same checks as verify)
    - Call `registrationService.cancelRegistration(registration)`
  - [ ] T8.5 — `public void deregisterByEmail(String email, String eventCode)`:
    - Find registration by `attendeeEmail` and `eventCode` (use `findByAttendeeEmailAndEventCode`)
    - If not found OR already cancelled: **do nothing, return silently** (anti-enumeration)
    - If found and active: resolve user locale via `UserApiClient`, send `deregistration-link-{locale}.html` email with `deregistrationLink = baseUrl + "/deregister?token=" + registration.getDeregistrationToken()`
    - `baseUrl` injected from `@Value("${app.base-url}")` (follow pattern of other services that construct magic links)

- [ ] **T9 — `DeregistrationEmailService.java`** (AC: #6, #7)
  - [ ] T9.1 — Create alongside other email services; inject `EmailTemplateService`, `MailSender`
  - [ ] T9.2 — `sendDeregistrationLinkEmail(Registration registration, UserResponse userProfile, Event event, Locale locale)`: loads `deregistration-link-{locale}` template, substitutes variables, sends
  - [ ] T9.3 — Follow `RegistrationEmailService` pattern: DB-first template load, fall back to classpath
  - [ ] T9.4 — Update `RegistrationEmailService.sendRegistrationConfirmation()`: add `String deregistrationUrl` param; add `deregistrationUrl` to template model map. Update the method call in `EventController` to pass the URL.
  - [ ] T9.5 — `deregistrationUrl` constructed in EventController (or RegistrationService): `appBaseUrl + "/deregister?token=" + registration.getDeregistrationToken()`

- [ ] **T10 — Email template classpath files** (AC: #6)
  - [ ] T10.1 — Create `services/event-management-service/src/main/resources/email-templates/deregistration-link-de.html`
  - [ ] T10.2 — Create `services/event-management-service/src/main/resources/email-templates/deregistration-link-en.html`
  - [ ] T10.3 — Both start with `<!-- subject: Ihre Abmeldung / Your Cancellation Request -->`, content-only HTML (no `<html>`/`<body>` tags)
  - [ ] T10.4 — Include `{{deregistrationLink}}` as a prominent CTA button link
  - [ ] T10.5 — Also update `registration-confirmation-de.html` and `registration-confirmation-en.html` classpath templates to include a "Cancel Registration" link using `{{deregistrationUrl}}`
  - [ ] T10.6 — Verify `EmailTemplateSeedService.determineCategory(String filename)` handles the `deregistration-` prefix → maps to `REGISTRATION` category. If the method uses a prefix-match map or switch, add: `"deregistration-" → REGISTRATION`. Without this mapping, the templates are created as classpath files but never seeded into the DB — the admin Email Templates tab will not show them (DoD failure).

- [ ] **T11 — `DeregistrationController.java`** (AC: #3, #10)
  - [ ] T11.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/controller/DeregistrationController.java`
  - [ ] T11.2 — `@RestController @RequestMapping("/api/v1/registrations/deregister") @RequiredArgsConstructor`
  - [ ] T11.3 — `GET /verify?token={uuid}` → `deregistrationService.verifyToken(UUID.fromString(token))`; return 200 or 404
  - [ ] T11.4 — `POST /` (body: `DeregistrationRequest`) → `deregistrationService.deregisterByToken(UUID.fromString(request.getToken()))`; return 200 / 404 / 409
  - [ ] T11.5 — `POST /by-email` (body: `DeregistrationByEmailRequest`) → `deregistrationService.deregisterByEmail(request.getEmail(), request.getEventCode())`; always return 200
  - [ ] T11.6 — No `@PreAuthorize` on any method — all are public (token-protected at business logic level)
  - [ ] T11.7 — Exception mapping: `EntityNotFoundException` → 404, `IllegalStateException("already_cancelled")` → 409
  - [ ] T11.8 — Rate limiting: check if BATbern already uses bucket4j or Spring's `RateLimiter`. If so, apply a per-IP limit (e.g., 5 requests/minute) to `by-email` to prevent email spam abuse. If no rate limiting infrastructure exists, document as accepted risk in code comment: `// TODO: add rate limiting — see Issue #XXX`

- [ ] **T12 — Security config updates** (AC: #10)
  - [ ] T12.1 — In `event-management-service/SecurityConfig.java`, add:
    ```java
    .requestMatchers(HttpMethod.GET, "/api/v1/registrations/deregister/verify").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/registrations/deregister").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/registrations/deregister/by-email").permitAll()
    ```
  - [ ] T12.2 — In `api-gateway/SecurityConfig.java`, add the same 3 rules (same paths, same methods)
  - [ ] T12.3 — Add comment `// Story 10.12: Self-service deregistration (token-protected)` above each block

- [ ] **T13 — Run backend tests GREEN** (AC: #12)
  - [ ] T13.1 — `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-12-backend.log && grep -E "BUILD|FAILED|passed|tests" /tmp/test-10-12-backend.log`
  - [ ] T13.2 — `./gradlew :api-gateway:test 2>&1 | tee /tmp/test-10-12-gateway.log && grep -E "BUILD|FAILED" /tmp/test-10-12-gateway.log`

### Phase 5: Frontend

- [ ] **T14 — `useDeregistration` hook** (AC: #8, #9)
  - [ ] T14.1 — Create `web-frontend/src/hooks/useDeregistration.ts`
  - [ ] T14.2 — `useVerifyDeregistrationToken(token: string | null)`: TanStack Query `useQuery`; enabled when `!!token`; fetches `GET /api/v1/registrations/deregister/verify?token={token}`; on 404 set state to "invalid"; on 409 set state to "alreadyCancelled"
  - [ ] T14.3 — `useDeregisterByToken()`: TanStack Query `useMutation`; calls `POST /api/v1/registrations/deregister`; on success invalidates `['my-registration']` query (if auth context available); on 409 → "alreadyCancelled"
  - [ ] T14.4 — `useDeregistrationByEmail()`: `useMutation`; calls `POST /api/v1/registrations/deregister/by-email`; always shows success (never surface errors to user — anti-enumeration)
  - [ ] T14.5 — Add service calls in `web-frontend/src/services/deregistrationService.ts` (follow pattern of other service files — no direct `fetch` in hooks)

- [ ] **T15 — `DeregistrationPage.tsx`** (AC: #8)
  - [ ] T15.1 — Create `web-frontend/src/pages/public/DeregistrationPage.tsx`
  - [ ] T15.2 — Mirror `UnsubscribePage.tsx` structure exactly:
    - Extract `token` from `useSearchParams()` (`?token=`)
    - State machine: `'verifying' | 'ready' | 'confirmed' | 'invalid' | 'alreadyCancelled'`
    - Use `useVerifyDeregistrationToken(token)` and `useDeregisterByToken()`
    - Use `PublicLayout` wrapper + centered MUI `Card`
  - [ ] T15.3 — `verifying` state: `<CircularProgress />` with i18n `deregistration.page.verifying`
  - [ ] T15.4 — `ready` state: event title, date, "Hi {attendeeFirstName}, are you sure?" + "Confirm Cancellation" button (MUI `Button color="error"`) + "Go back" link
  - [ ] T15.5 — `confirmed` state: success icon + `deregistration.page.successTitle` + `deregistration.page.successBody`
  - [ ] T15.6 — `invalid` state: warning icon + `deregistration.page.invalidToken` + support email link
  - [ ] T15.7 — `alreadyCancelled` state: info icon + `deregistration.page.alreadyCancelled`
  - [ ] T15.8 — Add `data-testid="deregistration-page"` to root element

- [ ] **T16 — `DeregistrationByEmailModal.tsx`** (AC: #9)
  - [ ] T16.1 — Create `web-frontend/src/components/public/DeregistrationByEmailModal.tsx`
  - [ ] T16.2 — Props: `{ open: boolean; onClose: () => void; eventCode: string }`
  - [ ] T16.3 — Form: MUI `TextField` for email (controlled, validates email format); `eventCode` passed as prop (hidden)
  - [ ] T16.4 — On submit: call `useDeregistrationByEmail()` mutation; on settled (success or error): show success state "If you are registered, you'll receive an email with a cancellation link." — always show this (anti-enumeration)
  - [ ] T16.5 — "Close" button in success state; loading spinner on submit
  - [ ] T16.6 — i18n keys: `deregistration.modal.*`

- [ ] **T17 — Register route in `App.tsx`** (AC: #8)
  - [ ] T17.1 — Add lazy import: `const DeregistrationPage = lazy(() => import('./pages/public/DeregistrationPage'))`
  - [ ] T17.2 — Add route (public, outside auth wrapper): `<Route path="/deregister" element={<DeregistrationPage />} />`
  - [ ] T17.3 — Confirm it's outside any auth-required layout (follow UnsubscribePage route placement)

- [ ] **T18 — Integrate into `HomePage.tsx`** (AC: #9)
  - [ ] T18.1 — When event is in `AGENDA_PUBLISHED`, `AGENDA_FINALIZED`, or `EVENT_LIVE` state: add secondary "Cancel your registration" text link below the registration CTA
  - [ ] T18.2 — On click: open `<DeregistrationByEmailModal eventCode={currentEvent.eventCode} />`
  - [ ] T18.3 — i18n key: `deregistration.homepage.cancelLink`
  - [ ] T18.4 — Hide link for unauthenticated users? No — any user (including anonymous registrants) should be able to request the link via email. Show always when event is accepting/registered.

- [ ] **T19 — Integrate into `RegistrationWizard.tsx` status guard** (AC: #9)
  - [ ] T19.1 — In the status guard screen (Story 10.10, T11 — shown when user is already registered): add "Cancel my registration" button
  - [ ] T19.2 — On click: open `<DeregistrationByEmailModal eventCode={eventCode} />`
  - [ ] T19.3 — Button only shown for `REGISTERED`, `CONFIRMED`, `WAITLIST` status (not `CANCELLED`)
  - [ ] T19.4 — i18n key: `deregistration.wizard.cancelButton`

- [ ] **T20 — i18n keys** (AC: #13)
  - [ ] T20.1 — Add to `public/locales/en/registration.json`:
    ```json
    "deregistration": {
      "page": {
        "verifying": "Verifying your cancellation link…",
        "title": "Cancel Registration",
        "confirmQuestion": "Hi {{name}}, are you sure you want to cancel your registration for {{eventTitle}} on {{eventDate}}?",
        "confirmButton": "Yes, cancel my registration",
        "goBack": "Go back",
        "successTitle": "Registration Cancelled",
        "successBody": "Your registration has been cancelled. Your spot has been released to the next person on the waitlist.",
        "invalidToken": "This cancellation link is invalid or has already been used. Please contact us if you need help.",
        "alreadyCancelled": "Your registration was already cancelled."
      },
      "modal": {
        "title": "Cancel Registration",
        "body": "Enter your email address and we'll send you a cancellation link.",
        "emailLabel": "Email address",
        "emailError": "Please enter a valid email address",
        "submitButton": "Send cancellation link",
        "successMessage": "If you have a registration for this event, you'll receive an email with a cancellation link shortly."
      },
      "homepage": {
        "cancelLink": "Cancel your registration"
      },
      "wizard": {
        "cancelButton": "Cancel my registration"
      }
    }
    ```
  - [ ] T20.2 — Add corresponding German (de) translations to `public/locales/de/registration.json` (`emailError` DE: `"Bitte geben Sie eine gültige E-Mail-Adresse ein"`)
  - [ ] T20.3 — Add `[MISSING]` prefix placeholder translations to all 8 other locale files (fr, it, rm, es, fi, nl, ja, gsw-BE)

- [ ] **T22 — CANCELLED status chip in organizer attendees tab** (AC: #11)
  - [ ] T22.1 — Read `EventParticipantList.tsx` (or the status chip component it uses) fully before modifying
  - [ ] T22.2 — Locate the status chip/badge component that renders registration status (e.g., `RegistrationStatusChip.tsx` or inline MUI `Chip`). If a status→color map exists, add: `CANCELLED → grey` (MUI: `color="default"` or `sx={{ bgcolor: 'grey.300', color: 'grey.700' }}`)
  - [ ] T22.3 — Ensure cancelled registrations are included in the query result — the attendees tab may currently filter out non-active registrations. If a status filter exists on `GET /api/v1/events/{eventCode}/registrations`, confirm it does NOT exclude `cancelled` for organizers.
  - [ ] T22.4 — i18n key: `eventPage.participantsTab.statusCancelled` → `"Cancelled"` (en) / `"Abgemeldet"` (de); add `[MISSING]` for other 8 locales

- [ ] **T21 — Frontend full test run** (AC: #13)
  - [ ] T21.1 — `cd web-frontend && npm run test -- --run 2>&1 | tee /tmp/test-10-12-frontend.log && grep -E "pass|fail|error" /tmp/test-10-12-frontend.log | tail -20`
  - [ ] T21.2 — `npm run type-check 2>&1 | tee /tmp/typecheck-10-12.log`
  - [ ] T21.3 — `npm run lint 2>&1 | tee /tmp/lint-10-12.log`

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

### File List
