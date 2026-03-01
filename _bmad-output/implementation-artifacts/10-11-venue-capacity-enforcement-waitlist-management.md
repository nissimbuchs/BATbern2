# Story 10.11: Venue Capacity Enforcement & Waitlist Management

Status: in-progress

<!-- Prerequisite: Story 10.10 (registration status indicator) recommended for UX coherence; technically independent -->

## Story

As an **organizer**,
I want to set a maximum registration capacity for an event based on venue size,
so that we never overbook the venue; new registrations automatically go to a waitlist when full, and the next waitlisted person is auto-promoted when someone cancels.

As an **attendee**,
I want to know my position on the waitlist and be automatically promoted if a spot opens up,
so that I don't have to check manually and miss my chance.

## Acceptance Criteria

1. **AC1 — Flyway V73**: Migration `V73__add_capacity_and_waitlist.sql` adds:
   - `waitlist_position` column to `registrations` table (nullable INTEGER, NULL for non-waitlist rows)
   - `registration_capacity` column to `events` table (nullable INTEGER, NULL = unlimited — preserves backward compatibility for all existing events)
   - Index `idx_registrations_event_waitlist` on `(event_id, status, waitlist_position)` for efficient waitlist promotion queries.

2. **AC2 — Capacity enforcement on registration creation**: `RegistrationService.createRegistration()` extended:
   - Count active registrations (`status IN ('registered', 'confirmed')`) for the event
   - If `event.registrationCapacity` is NULL OR count < capacity → create with `status = "registered"` (existing behaviour, unchanged)
   - If count >= capacity → create with `status = "waitlist"` and assign `waitlistPosition = nextWaitlistPosition(eventId)` (sequential: 1, 2, 3…)
   - Send appropriate email: confirmation email for `registered`; waitlist confirmation email for `waitlist`

3. **AC3 — `WaitlistPromotionService`** (new service):
   - `promoteFromWaitlist(UUID eventId)`: finds the registration with the lowest `waitlistPosition` for this event, updates `status = "registered"`, clears `waitlist_position`, sends waitlist-promotion email via `WaitlistPromotionEmailService`
   - Called automatically by `RegistrationService.cancelRegistration()` (Story 10.12) after every successful cancellation
   - Also callable manually from organizer UI ("Promote to Registered" action)
   - `WaitlistPromotionEmailService`: sends `waitlist-promotion-de/en.html` email template (seeded by `EmailTemplateSeedService`)

4. **AC4 — Capacity Management API extension**:
   - `PATCH /api/v1/events/{eventCode}` request DTO extended with `registrationCapacity: Integer` (nullable; null = clear/unlimited)
   - `GET /api/v1/events/{eventCode}` response extended with: `registrationCapacity` (nullable), `confirmedCount` (integer), `waitlistCount` (integer), `spotsRemaining` (nullable integer; computed: `registrationCapacity - confirmedCount`; null if unlimited)
   - Public read access to `spotsRemaining` and `waitlistCount` only (no PII); organizer access to full list via existing attendees tab
   - OpenAPI spec updated FIRST (ADR-006)

5. **AC5 — Organizer Attendees Tab UI**:
   - Capacity bar at top of `EventParticipantsTab`: `[███████░░░] 42/60 confirmed · 3 on waitlist`
   - New collapsible "Waitlist" section below "Registered / Confirmed" table in `EventParticipantList`
   - Waitlist rows show `waitlistPosition` column and `RegistrationActionsMenu` with: "Promote to Registered" (manual promotion), "Remove from Waitlist"
   - Bar and waitlist section hidden when `registrationCapacity` is NULL (unlimited)

6. **AC6 — Event Settings / Edit UI**: Add "Registration Capacity" numeric field (blank = unlimited). Field editable only when event is not `ARCHIVED`.

7. **AC7 — Public Homepage capacity badge**: `HomePage.tsx` shows "X spots remaining" or "Full — join waitlist" badge on event hero when `registrationCapacity` is set and event is accepting registrations.

8. **AC8 — Registration Wizard waitlist acknowledgment**: `RegistrationWizard.tsx` Step 1 (or Step 2 confirmation): if `spotsRemaining === 0`, show MUI `Alert severity="info"` with message "This event is full — you will be added to the waitlist" BEFORE form submission. The alert includes a MUI `Checkbox` labelled `registration.wizard.waitlistAcknowledgeLabel` ("I understand I will be on the waitlist"). The submit button remains disabled until the checkbox is checked (`waitlistAcknowledged === true`). When `registrationCapacity` is null or `spotsRemaining > 0`, the checkbox is not rendered and the submit button behaves as normal. On success, show waitlist-specific success message (not standard confirmation message).

9. **AC9 — Email templates seeded**: Four new classpath content-fragment templates seeded by `EmailTemplateSeedService`:
   - `waitlist-promotion-de.html`, `waitlist-promotion-en.html` (category: REGISTRATION)
   - `registration-waitlist-confirmation-de.html`, `registration-waitlist-confirmation-en.html` (category: REGISTRATION)
   All use `batbern-default` layout. Variables: `recipientName`, `eventTitle`, `eventCode`, `eventDate`, `venueAddress`, `registrationCode`.

10. **AC10 — TDD compliance**:
    - `WaitlistPromotionServiceTest.java` written FIRST (unit test)
    - `RegistrationCapacityIntegrationTest.java` extending `AbstractIntegrationTest` written FIRST (RED phase)
    - Tests: registration when full → status=waitlist with correct sequential position; cancellation with waitlist → first waitlisted person auto-promoted + email sent; manual promotion from organizer endpoint works
    - All tests pass before marking story done

11. **AC11 — i18n**: `waitlist.*` and `capacity.*` keys in `de/en` registration and events locale files. No hardcoded strings. Type-check passes; Checkstyle passes; `npm run lint` passes.

12. **AC12 — OpenAPI spec first (ADR-006)**: `docs/api/events.openapi.yml` updated before any backend implementation begins.

13. **AC13 — RegistrationStatusBanner waitlist position**: `RegistrationStatusBanner.tsx` (introduced in Story 10.10) updated to show waitlist position when status is `WAITLIST`. Display: `registrationStatusBanner.waitlistWithPosition` i18n key — "You are #{{position}} on the waitlist" — when `waitlistPosition` is non-null. Falls back to existing `registrationStatusBanner.waitlist` key when `waitlistPosition` is null (backward-compatible). `MyRegistrationResponse` DTO (from Story 10.10) extended with `waitlistPosition: Integer` (nullable) — backward-compatible addition.

---

## Tasks / Subtasks

### Phase 1: API Contract (ADR-006 — FIRST)

- [x] **T1 — Update OpenAPI spec** (AC: #4, #12)
  - [x] T1.1 — Add `registrationCapacity` (nullable integer) to `CreateEventRequest` and `UpdateEventRequest` schemas
  - [x] T1.2 — Add `registrationCapacity`, `confirmedCount`, `waitlistCount`, `spotsRemaining` to `EventResponse` schema
  - [x] T1.3 — Add `waitlistPosition` (nullable integer) to `Registration` schema; also fixed `Registration.status` enum from `WAITLISTED` → `WAITLIST`; added `waitlistPosition` to `MyRegistrationResponse`
  - [x] T1.4 — Added promote endpoint `POST /events/{eventCode}/registrations/{registrationCode}/promote` (no `EmailTemplateName` enum in spec)
  - [x] T1.5 — Regenerated backend DTOs: `./gradlew :services:event-management-service:openApiGenerate` ✅
  - [x] T1.6 — Regenerated frontend types: `cd web-frontend && npm run generate:api-types:events` ✅

### Phase 2: Database Migration

- [x] **T2 — Flyway V73** (AC: #1)
  - [x] T2.1 — Created `V73__add_capacity_and_waitlist.sql` ✅
  - [x] T2.2 — SQL: `ALTER TABLE registrations ADD COLUMN waitlist_position INTEGER;` ✅
  - [x] T2.3 — SQL: `ALTER TABLE events ADD COLUMN registration_capacity INTEGER;` ✅
  - [x] T2.4 — SQL: `CREATE INDEX idx_registrations_event_waitlist ON registrations (event_id, status, waitlist_position) WHERE status = 'waitlist';` ✅
  - [x] T2.5 — Migration applied successfully ✅

### Phase 3: Domain Entity Updates

- [x] **T3 — Registration entity** (AC: #1)
  - [x] T3.1 — Added `@Column(name = "waitlist_position") private Integer waitlistPosition;` to `Registration.java` ✅
  - [x] T3.2 — Lombok `@Data` already present ✅

- [x] **T4 — Event entity** (AC: #4)
  - [x] T4.1 — Added `@Column(name = "registration_capacity") private Integer registrationCapacity;` to `Event.java` ✅
  - [x] T4.2 — Confirmed `venueCapacity` is separate existing field ✅
  - [x] T4.3 — Lombok `@Data` already present ✅

### Phase 4: Repository Layer

- [x] **T5 — RegistrationRepository extensions** (AC: #2, #3)
  - [x] T5.1 — Added `long countByEventIdAndStatusIn(UUID eventId, List<String> statuses)` ✅
  - [x] T5.2 — Added `findWaitlistByEventIdOrdered()` with `@Query` ✅
  - [x] T5.3 — Added `findTopByEventIdAndStatusOrderByWaitlistPositionAsc()` ✅
  - [x] T5.4 — Added `getNextWaitlistPosition()` with `@Query` ✅
  - [x] T5.5 — Added `countByEventIdAndStatus()` ✅

### Phase 5: Service Layer — TDD FIRST

- [x] **T6 — Write tests FIRST (RED phase)** (AC: #10)
  - [x] T6.1 — Created `WaitlistPromotionServiceTest.java` — 5 unit tests (Mockito); confirmed RED then GREEN ✅
  - [x] T6.2 — Created `RegistrationCapacityIntegrationTest.java` extending `AbstractIntegrationTest` ✅
  - [x] T6.3 — RED phase confirmed (compile errors before implementation); GREEN after ✅

- [x] **T7 — WaitlistPromotionService** (AC: #3)
  - [x] T7.1 — Created `WaitlistPromotionService.java` ✅
  - [x] T7.2 — `@Service @RequiredArgsConstructor @Slf4j` ✅
  - [x] T7.3 — Injects `RegistrationRepository`, `WaitlistPromotionEmailService` ✅
  - [x] T7.4 — `promoteFromWaitlist(UUID eventId)` implemented ✅
  - [x] T7.5 — `manuallyPromote(String registrationCode)` implemented; throws `RegistrationNotFoundException` (→404) and `RegistrationNotOnWaitlistException` (→409) ✅

- [x] **T8 — WaitlistPromotionEmailService** (AC: #3, #9)
  - [x] T8.1 — Created `WaitlistPromotionEmailService.java` following `TaskReminderEmailService` pattern ✅
  - [x] T8.2 — `sendPromotionEmail()` and `sendWaitlistConfirmationEmail()` implemented ✅
  - [x] T8.3 — Locale defaults to `Locale.GERMAN` (UserResponse has no `preferredLanguage` yet — Story 10.15 adds it) ✅

- [x] **T9 — Email template classpath files** (AC: #9)
  - [x] T9.1 — Created all 4 HTML files under `email-templates/` ✅
  - [x] T9.2 — Each starts with `<!-- subject: ... -->` comment, content-only HTML ✅
  - [x] T9.3 — Template vars: `recipientName`, `eventTitle`, `eventDate`, `venueAddress`, `registrationCode` ✅
  - [x] T9.4 — Promotion email: no `waitlistPosition` var (person is now registered) ✅
  - [x] T9.5 — Waitlist confirmation: includes `registrationCode` as identifier ✅
  - Also updated `EmailTemplateSeedService.deriveCategory()` to map `waitlist-` prefix → `REGISTRATION` ✅

- [x] **T10 — RegistrationService capacity enforcement** (AC: #2)
  - [x] T10.1 — Capacity enforcement added after event validation ✅
  - [x] T10.2 — Registration builder uses `registrationStatus` and `waitlistPosition` ✅
  - [x] T10.3 — After save: waitlist → sends waitlist-confirmation email ✅
  - [x] T10.4 — Duplicate-registration check handles `"waitlist"` status: returns existing + resends email ✅

- [ ] **T11 — EventService: capacity field on CRUD** (AC: #4)
  - [ ] T11.1 — In `EventService.createEvent()`: map `registrationCapacity` from request DTO → `Event.registrationCapacity`
  - [ ] T11.2 — In `EventService.updateEvent()`: same mapping (nullable — allows clearing)
  - [ ] T11.3 — In event-to-response mapping: compute and set `confirmedCount`, `waitlistCount`, `spotsRemaining`
    ```java
    long confirmed = registrationRepository.countByEventIdAndStatusIn(event.getId(), List.of("registered", "confirmed"));
    long waitlisted = registrationRepository.countByEventIdAndStatus(event.getId(), "waitlist");
    dto.setConfirmedCount((int) confirmed);
    dto.setWaitlistCount((int) waitlisted);
    if (event.getRegistrationCapacity() != null) {
        dto.setSpotsRemaining((int)(event.getRegistrationCapacity() - confirmed));
    }
    ```
  - [ ] T11.4 — **Performance note**: These counts add 2 extra DB queries per event response. This is acceptable for organizer views. For public event listing (many events), consider caching or only computing for the current event. Use `@Cacheable` on the method if latency is observed.

- [ ] **T12 — Run all backend tests GREEN** (AC: #10)
  - [ ] T12.1 — `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-11-backend.log && grep -E "BUILD|FAILED|passed|tests" /tmp/test-10-11-backend.log`

### Phase 6: Frontend

- [ ] **T13 — CapacityIndicator component (public)** (AC: #7, #8)
  - [ ] T13.1 — Create `web-frontend/src/components/public/CapacityIndicator.tsx`
  - [ ] T13.2 — Props: `{ spotsRemaining: number | null | undefined, waitlistCount: number, registrationCapacity: number | null }`
  - [ ] T13.3 — If `registrationCapacity` is null: render nothing (unlimited)
  - [ ] T13.4 — If `spotsRemaining > 0`: green badge "X spots remaining"
  - [ ] T13.5 — If `spotsRemaining === 0`: amber badge "Full — join waitlist" + optional chip with waitlist count
  - [ ] T13.6 — Use i18n keys: `events.capacity.spotsRemaining`, `events.capacity.fullJoinWaitlist`

- [ ] **T14 — WaitlistSection component (organizer)** (AC: #5)
  - [ ] T14.1 — Create `web-frontend/src/components/organizer/EventPage/WaitlistSection.tsx`
  - [ ] T14.2 — Props: `{ eventCode: string, waitlistCount: number }`
  - [ ] T14.3 — Renders a collapsible MUI `Accordion` titled "Waitlist (N)"
  - [ ] T14.4 — Table columns: Position, Name, Email, Company, Registered On, Actions
  - [ ] T14.5 — Actions: "Promote to Registered" button → calls `POST /api/v1/events/{eventCode}/registrations/{registrationCode}/promote` (new endpoint, T15)
  - [ ] T14.6 — "Remove from Waitlist" → calls existing cancel endpoint (Story 10.12 — for now, note as TODO comment)
  - [ ] T14.7 — After promotion: invalidate queries `['event-registrations', eventCode]` and `['event', eventCode]`

- [ ] **T15 — Promote endpoint (organizer only)** (AC: #3, #5)
  - [ ] T15.1 — Add to OpenAPI: `POST /api/v1/events/{eventCode}/registrations/{registrationCode}/promote`
    - Response `204 No Content` on success
    - Response `404 Not Found` when `registrationCode` does not exist
    - Response `409 Conflict` when registration exists but status is NOT `waitlist` (body: `{ "error": "Registration is not on the waitlist" }`)
    - Response `403 Forbidden` when caller is not `ORGANIZER`
  - [ ] T15.2 — Add to EventController (or RegistrationController — check line count first):
    ```java
    @PostMapping("/{eventCode}/registrations/{registrationCode}/promote")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> promoteFromWaitlist(
        @PathVariable String eventCode,
        @PathVariable String registrationCode) {
        waitlistPromotionService.manuallyPromote(registrationCode);
        return ResponseEntity.noContent().build();
    }
    ```
  - [ ] T15.3 — Integration tests (extend `AbstractIntegrationTest`):
    - Happy path: organizer promotes valid waitlisted registration → `204`, status=`registered`, `waitlist_position` cleared, promotion email sent
    - Error: `registrationCode` not found → `404`
    - Error: registration exists but status=`registered` (not waitlist) → `409`
  - [ ] T15.4 — Update `WaitlistPromotionService.manuallyPromote()` to throw typed exceptions: `RegistrationNotFoundException` (→ 404) and `RegistrationNotOnWaitlistException` (→ 409); add `@ControllerAdvice` mappings or verify existing handler covers these types

- [ ] **T16 — EventParticipantsTab capacity bar** (AC: #5)
  - [ ] T16.1 — Read current `EventParticipantsTab.tsx` fully before modifying
  - [ ] T16.2 — Add capacity bar using MUI `LinearProgress` (determinate):
    ```tsx
    {event.registrationCapacity && (
      <Box sx={{ mb: 2 }}>
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2">{`${event.confirmedCount} / ${event.registrationCapacity} ${t('eventPage.participantsTab.confirmed')}`}</Typography>
          <Typography variant="body2" color="text.secondary">{`${event.waitlistCount} ${t('eventPage.participantsTab.onWaitlist')}`}</Typography>
        </Stack>
        <LinearProgress
          variant="determinate"
          value={Math.min(100, (event.confirmedCount / event.registrationCapacity) * 100)}
          color={event.spotsRemaining === 0 ? 'error' : 'primary'}
        />
      </Box>
    )}
    ```
  - [ ] T16.3 — Add `<WaitlistSection>` below `<EventParticipantList>` (only if `event.registrationCapacity` is set)

- [ ] **T17 — Event Settings — Registration Capacity field** (AC: #6)
  - [ ] T17.1 — Find event edit form/settings component (search for `PATCH /api/v1/events` or event settings tab)
  - [ ] T17.2 — Add `registrationCapacity` MUI `TextField` with `type="number"`, `inputProps={{ min: 1 }}`, helperText from `events.settings.capacityHelperText`
  - [ ] T17.3 — Blank value submits `null` (unlimited); clear button allowed
  - [ ] T17.4 — Disabled when `event.workflowState === 'ARCHIVED'`

- [ ] **T18 — HomePage capacity badge** (AC: #7)
  - [ ] T18.1 — Import `CapacityIndicator` in `HomePage.tsx`
  - [ ] T18.2 — Render `<CapacityIndicator>` in event hero area, using `currentEvent.spotsRemaining`, `currentEvent.waitlistCount`, `currentEvent.registrationCapacity`
  - [ ] T18.3 — Only show for events in `REGISTRATION_OPEN`, `AGENDA_PUBLISHED`, `AGENDA_FINALIZED`, `EVENT_LIVE` states

- [ ] **T19 — RegistrationWizard waitlist acknowledgment** (AC: #8)
  - [ ] T19.1 — Read `RegistrationWizard.tsx` fully before modifying
  - [ ] T19.2 — On wizard mount/step-2 render: if `currentEvent.spotsRemaining === 0` → show MUI `Alert severity="info"` containing:
    - Alert text: i18n `registration.wizard.waitlistWarning`
    - MUI `Checkbox` with `FormControlLabel`: i18n `registration.wizard.waitlistAcknowledgeLabel`; bound to `waitlistAcknowledged` boolean state (default `false`)
  - [ ] T19.3 — Submit button `disabled={currentEvent.spotsRemaining === 0 && !waitlistAcknowledged}`. When `spotsRemaining > 0` or capacity is null: checkbox not rendered, button behaves normally.
  - [ ] T19.4 — On registration success: check returned `registration.status`:
    - If `"waitlist"`: show waitlist-specific success (`registration.wizard.waitlistSuccessTitle`, `registration.wizard.waitlistSuccessBody`)
    - If `"registered"`: show existing confirmation message (unchanged)

- [ ] **T20 — i18n keys** (AC: #11)
  - [ ] T20.1 — Add to `public/locales/en/events.json`:
    ```json
    "capacity": {
      "spotsRemaining": "{{count}} spots remaining",
      "fullJoinWaitlist": "Full — join waitlist",
      "waitlistCount": "{{count}} on waitlist"
    }
    ```
  - [ ] T20.2 — Add to `public/locales/en/events.json` under `eventPage.participantsTab`:
    ```json
    "confirmed": "confirmed",
    "onWaitlist": "on waitlist"
    ```
  - [ ] T20.3 — Add to `public/locales/en/registration.json`:
    ```json
    "wizard": {
      "waitlistWarning": "This event is full. Submitting will add you to the waitlist.",
      "waitlistAcknowledgeLabel": "I understand I will be on the waitlist",
      "waitlistSuccessTitle": "You're on the waitlist!",
      "waitlistSuccessBody": "You are #{{position}} on the waitlist. We'll email you if a spot opens."
    }
    ```
  - [ ] T20.4 — Add `events.settings.capacityHelperText` → `"Leave blank for unlimited registrations"`
  - [ ] T20.5 — Add corresponding German (de) translations for all keys (`waitlistAcknowledgeLabel` DE: `"Ich verstehe, dass ich auf der Warteliste stehe"`)
  - [ ] T20.6 — Add `[MISSING]` prefix to all other 8 locale files (fr, it, rm, es, fi, nl, ja, gsw-BE)

- [ ] **T21 — RegistrationStatusBanner waitlist position** (AC: #13)
  - [ ] T21.1 — Extend `MyRegistrationResponse` DTO (from Story 10.10) with `waitlistPosition: Integer` (nullable) — backend + frontend OpenAPI types
  - [ ] T21.2 — Read `RegistrationStatusBanner.tsx` fully before modifying
  - [ ] T21.3 — When `registration.status === 'WAITLIST'` AND `registration.waitlistPosition != null`: show `t('registrationStatusBanner.waitlistWithPosition', { position: registration.waitlistPosition })` instead of existing `registrationStatusBanner.waitlist` key
  - [ ] T21.4 — Add i18n key `registrationStatusBanner.waitlistWithPosition` to `de` and `en` registration locale files:
    - EN: `"You are #{{position}} on the waitlist"`
    - DE: `"Sie stehen auf Platz #{{position}} der Warteliste"`
  - [ ] T21.5 — Add `[MISSING]` prefix for this key in the other 8 locales
  - [ ] T21.6 — Unit test: `RegistrationStatusBanner` with `status=WAITLIST` and `waitlistPosition=3` → renders "You are #3 on the waitlist"; with `waitlistPosition=null` → renders existing "Waitlist" text (fallback)

- [ ] **T22 — Frontend full test run** (AC: #11, #13)
  - [ ] T22.1 — `cd web-frontend && npm run test -- --run 2>&1 | tee /tmp/test-10-11-frontend.log && grep -E "pass|fail|error" /tmp/test-10-11-frontend.log | tail -20`
  - [ ] T22.2 — `npm run type-check 2>&1 | tee /tmp/typecheck-10-11.log`
  - [ ] T22.3 — `npm run lint 2>&1 | tee /tmp/lint-10-11.log`

---

## Dev Notes

### Architecture Compliance

**ADR-006 (Contract-First)**: OpenAPI spec in `docs/api/events.openapi.yml` MUST be updated FIRST (T1). Backend types regenerated → frontend types regenerated → THEN implementation. Never write controller or service before the spec exists.

**ADR-003 (Meaningful Identifiers)**: All API paths use `eventCode` (e.g., `BATbern58`), `registrationCode`, never raw UUIDs. Internal service methods use `UUID eventId` for DB efficiency.

**ADR-004 (No User Field Duplication)**: `WaitlistPromotionService` resolves user's locale for email via `UserApiClient` — do NOT duplicate `preferredLanguage` onto Registration entity. Call UserApiClient for email locale at send-time only.

**ADR-005 (Anonymous Registration)**: Capacity enforcement applies equally to anonymous and authenticated registrations. The `attendeeUsername` check is sufficient — no Cognito account required.

**TDD Mandate**: Write `WaitlistPromotionServiceTest` and `RegistrationCapacityIntegrationTest` BEFORE any service implementation (T6 before T7–T11). RED → GREEN → REFACTOR.

**Integration Test Requirement**: Always extend `AbstractIntegrationTest` (PostgreSQL via Testcontainers). NEVER use H2.

### Critical Implementation Details

#### Registration Status Lowercase in DB
Status values are stored **lowercase** in the DB: `"registered"`, `"confirmed"`, `"waitlist"`, `"cancelled"`.
API responses uppercase them via the existing pattern in `RegistrationService`:
```java
.status(registration.getStatus() != null ? registration.getStatus().toUpperCase() : null)
```
All `countByStatus` and `findByStatus` queries MUST use lowercase literals. **Never use uppercase in JPA queries.**

#### `venueCapacity` vs `registrationCapacity` — Critical Distinction
- `venueCapacity` (existing, non-nullable): The physical venue maximum (e.g., fire-code limit). Used for display only.
- `registrationCapacity` (new, nullable): The organizer-configured registration limit. Can be ≤ `venueCapacity`. **NULL = unlimited.** Never conflate these two.

#### nextWaitlistPosition — Concurrency Consideration
The `getNextWaitlistPosition()` query (`MAX(waitlist_position) + 1`) is safe in most scenarios since BATbern events have a small number of concurrent registrations. If race conditions are observed (two simultaneous waitlist registrations get the same position), add a `unique constraint on (event_id, waitlist_position)` and handle the constraint violation with a retry. Do NOT add pessimistic locking preemptively.

#### EventController Line Count
`EventController.java` was ~2,242 lines at the time of Story 10.10. **Check before adding**:
```bash
wc -l services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java
```
If > 2,400 lines: extract `getMyRegistration`, `getRegistrations`, `createRegistration`, and the new `promoteFromWaitlist` into a new `RegistrationController.java`. Follow existing controller patterns for `@RequestMapping`, `@PreAuthorize`, and exception handling.

#### Email Template Seeding Pattern
`EmailTemplateSeedService` scans classpath `email-templates/*.html` at `@PostConstruct`. Category derived from filename prefix:
- `registration-*` → category `REGISTRATION`
- `waitlist-*` → category `REGISTRATION` (starts with registration? — check actual logic. If not, map it explicitly)

**Verify**: In `EmailTemplateSeedService.java`, find the `determineCategory(String filename)` method and ensure `waitlist-` prefix maps to `REGISTRATION`. If not, add it.

**Idempotent seeding**: Seeds only if `existsByTemplateKeyAndLocale()` returns false — safe to redeploy.

#### WaitlistPromotionEmailService — Follow Existing Email Pattern
Follow `SpeakerReminderEmailService.java` or `TaskReminderEmailService.java` for pattern:
1. Inject `EmailService` (or `JavaMailSender` — use whichever existing services use)
2. Load template from `EmailTemplateRepository` by key and locale
3. Substitute variables using `templateEngine.process()`
4. Send via email service

#### RegistrationWizard Location
The public registration wizard is at:
```
web-frontend/src/components/public/Registration/RegistrationWizard.tsx
```
(Found during Story 10.10 investigation — it is under `Registration/` subfolder, not in root of `components/public/`).

Verify path before editing:
```bash
find web-frontend/src -name "RegistrationWizard.tsx"
```

#### Frontend `spotsRemaining` Nullability
`event.spotsRemaining` is nullable (undefined when `registrationCapacity` is null). Always guard:
```typescript
{event.spotsRemaining !== null && event.spotsRemaining !== undefined && event.spotsRemaining === 0 && (
  <WaitlistWarning />
)}
```
Never use `!event.spotsRemaining` — that would also trigger for `spotsRemaining = 0` incorrectly when capacity IS set.

#### Previous Story (10.10) Integration Point
Story 10.10 introduced `RegistrationStatusBanner.tsx` which shows `WAITLIST` status with `registrationStatusBanner.waitlist` i18n key. Story 10.10 is already implemented (`feat(10.10)` committed).

Story 10.11 **must** extend the banner to show waitlist position: "You are #3 on the waitlist" — see AC13 and T21. Update `MyRegistrationResponse` (from 10.10) to include `waitlistPosition: Integer` (nullable). This is a backward-compatible addition. The banner falls back to the existing `registrationStatusBanner.waitlist` key when `waitlistPosition` is null.

### Key New Files

```
services/event-management-service/src/main/resources/db/migration/V73__add_capacity_and_waitlist.sql
services/event-management-service/.../service/WaitlistPromotionService.java
services/event-management-service/.../service/WaitlistPromotionEmailService.java
services/event-management-service/src/main/resources/email-templates/waitlist-promotion-de.html
services/event-management-service/src/main/resources/email-templates/waitlist-promotion-en.html
services/event-management-service/src/main/resources/email-templates/registration-waitlist-confirmation-de.html
services/event-management-service/src/main/resources/email-templates/registration-waitlist-confirmation-en.html
services/event-management-service/src/test/java/.../service/WaitlistPromotionServiceTest.java
services/event-management-service/src/test/java/.../RegistrationCapacityIntegrationTest.java
web-frontend/src/components/organizer/EventPage/WaitlistSection.tsx
web-frontend/src/components/public/CapacityIndicator.tsx
```

### Key Modified Files

| File | Change |
|------|--------|
| `docs/api/events.openapi.yml` | capacity fields + waitlist position + promote endpoint (FIRST) |
| `services/event-management-service/.../domain/Registration.java` | Add `waitlistPosition` field |
| `services/event-management-service/.../domain/Event.java` | Add `registrationCapacity` field |
| `services/event-management-service/.../repository/RegistrationRepository.java` | Add waitlist queries (5 new methods) |
| `services/event-management-service/.../service/RegistrationService.java` | Capacity enforcement in `createRegistration()` |
| `services/event-management-service/.../service/EventService.java` | Map `registrationCapacity` + compute counts |
| `services/event-management-service/.../controller/EventController.java` | Add promote endpoint (check line count first) |
| `web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx` | Capacity bar + WaitlistSection |
| `web-frontend/src/components/public/Registration/RegistrationWizard.tsx` | Waitlist acknowledgment + success message |
| `web-frontend/src/pages/public/HomePage.tsx` | CapacityIndicator in hero |
| `public/locales/en/events.json` | `capacity.*` + `participantsTab.*` keys |
| `public/locales/de/events.json` | Same in German |
| `public/locales/en/registration.json` | `wizard.waitlist*` keys |
| `public/locales/de/registration.json` | Same in German |
| `public/locales/{fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json` | `[MISSING]` placeholders |
| `public/locales/{fr,it,rm,es,fi,nl,ja,gsw-BE}/registration.json` | `[MISSING]` placeholders |
| `web-frontend/src/components/shared/RegistrationStatusBanner.tsx` | Add `waitlistWithPosition` display (AC13) |

### Project Structure Notes

- Backend package root: `services/event-management-service/src/main/java/ch/batbern/events/`
  - Domain: `.../domain/` — JPA entities
  - Service: `.../service/` — business logic
  - Repository: `.../repository/` — Spring Data JPA interfaces
  - Controller: `.../controller/` — REST endpoints
  - Email services: `.../service/` (co-located with business services)
- Email templates: `services/event-management-service/src/main/resources/email-templates/`
- Flyway: `services/event-management-service/src/main/resources/db/migration/` (V-numbers must be sequential from V72)
- Frontend components: `web-frontend/src/components/organizer/EventPage/` (organizer-facing) and `web-frontend/src/components/public/` (public-facing)
- i18n locale files: `web-frontend/public/locales/{lang}/{namespace}.json` — 10 locales (de, en, fr, it, rm, es, fi, nl, ja, gsw-BE)

### References

- Story spec in PRD: [Source: docs/prd/epic-10-additional-stories.md#Story-10.11]
- Previous story (10.10 — Registration Status Indicator): [Source: _bmad-output/implementation-artifacts/10-10-registration-status-indicator.md]
- Registration entity: [Source: services/event-management-service/.../domain/Registration.java]
- Event entity: [Source: services/event-management-service/.../domain/Event.java]
- RegistrationService (createRegistration): [Source: services/event-management-service/.../service/RegistrationService.java]
- RegistrationRepository: [Source: services/event-management-service/.../repository/RegistrationRepository.java]
- EmailTemplateSeedService: [Source: services/event-management-service/.../service/EmailTemplateSeedService.java]
- EventParticipantsTab: [Source: web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx]
- OpenAPI spec: [Source: docs/api/events-api.openapi.yml]
- ADR-003 Meaningful Identifiers: [Source: docs/architecture/ADR-003-meaningful-identifiers-public-apis.md]
- ADR-004 Factor User Fields: [Source: docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md]
- ADR-006 Contract-First: [Source: docs/architecture/ADR-006-openapi-contract-first-code-generation.md]
- i18n patterns: [Source: _bmad-output/implementation-artifacts/10-9-i18n-cleanup.md]
- Flyway guide: [Source: docs/guides/flyway-migration-guide.md]

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/openapi-gen-10-11.log`
- `/tmp/flyway-10-11.log`
- `/tmp/test-10-11-red.log`
- `/tmp/test-10-11-backend.log`
- `/tmp/test-10-11-frontend.log`
- `/tmp/typecheck-10-11.log`
- `/tmp/lint-10-11.log`

### Completion Notes List

- T1: OpenAPI task name is `openApiGenerate` (not `openApiGenerateEvents`)
- T6: `REGISTRATION_OPEN` does not exist in `EventWorkflowState` enum — used `CREATED` in integration tests
- T7/T8: `UserResponse` has no `getPreferredLanguage()` (Story 10.15 adds it) — locale defaults to `Locale.GERMAN`
- T8: `WaitlistPromotionEmailService` sends both promotion and waitlist-confirmation emails (two methods)
- T9/Email seeding: Added `waitlist-` prefix mapping to `REGISTRATION` category in `EmailTemplateSeedService.deriveCategory()`
- T10: Added `WaitlistPromotionEmailService` as dependency to `RegistrationService`
- Extra files created: `RegistrationNotFoundException.java` and `RegistrationNotOnWaitlistException.java` (needed for T7.5/T15)
- WaitlistPromotionServiceTest: 5 unit tests all GREEN
- Context paused at start of T11 (EventMapper/EventController capacity mapping)

### File List

- `services/event-management-service/src/main/resources/db/migration/V73__add_capacity_and_waitlist.sql` ✅
- `services/event-management-service/src/main/java/ch/batbern/events/service/WaitlistPromotionService.java` ✅
- `services/event-management-service/src/main/java/ch/batbern/events/service/WaitlistPromotionEmailService.java` ✅
- `services/event-management-service/src/main/java/ch/batbern/events/exception/RegistrationNotFoundException.java` ✅
- `services/event-management-service/src/main/java/ch/batbern/events/exception/RegistrationNotOnWaitlistException.java` ✅
- `services/event-management-service/src/main/resources/email-templates/waitlist-promotion-de.html` ✅
- `services/event-management-service/src/main/resources/email-templates/waitlist-promotion-en.html` ✅
- `services/event-management-service/src/main/resources/email-templates/registration-waitlist-confirmation-de.html` ✅
- `services/event-management-service/src/main/resources/email-templates/registration-waitlist-confirmation-en.html` ✅
- `services/event-management-service/src/test/java/ch/batbern/events/service/WaitlistPromotionServiceTest.java` ✅
- `services/event-management-service/src/test/java/ch/batbern/events/controller/RegistrationCapacityIntegrationTest.java` ✅
