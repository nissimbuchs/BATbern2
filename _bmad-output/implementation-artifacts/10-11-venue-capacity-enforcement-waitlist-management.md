# Story 10.11: Venue Capacity Enforcement & Waitlist Management

Status: done

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

- [x] **T11 — EventService: capacity field on CRUD** (AC: #4)
  - [x] T11.1 — `EventMapper.toEntity(CreateEventRequest)` maps `registrationCapacity` ✅
  - [x] T11.2 — `EventMapper.applyUpdateRequest()` maps `registrationCapacity` (nullable PUT, allows clearing) ✅
  - [x] T11.3 — `EventMapper.applyPatchRequest()` maps `registrationCapacity` only when non-null (PATCH semantics) ✅
  - [x] T11.4 — `EventResponse` DTO extended with `registrationCapacity`, `confirmedCount`, `waitlistCount`, `spotsRemaining` ✅
  - [x] T11.5 — `EventController.enrichWithRegistrationCounts()` helper computes counts via `RegistrationRepository` and populates response ✅ (called in getEvent, getCurrentEvent, createEvent, updateEvent, patchEvent, publishEvent)
  - [x] T11.6 — `EventController` injects `WaitlistPromotionService` (via `@RequiredArgsConstructor` + `final` field) ✅
  - [x] T11.7 — Promote endpoint `POST /{eventCode}/registrations/{registrationCode}/promote` added to `EventController` ✅
  - [x] T11.8 — Controller skips regular confirmation email for `waitlist` registrations (service already sent waitlist email); returns 200 OK for duplicate waitlist registration (not 409) ✅
  - [x] T11.9 — All 9 `RegistrationCapacityIntegrationTest` tests pass (GREEN) ✅
  - **Note**: No separate EventService — all logic in `EventController` + `EventMapper` (architectural pattern for this service)

- [x] **T12 — Run all backend tests GREEN** (AC: #10)
  - [x] T12.1 — `./gradlew :services:event-management-service:test` → BUILD SUCCESSFUL, 0 failures ✅

### Phase 6: Frontend

- [x] **T13 — CapacityIndicator component (public)** (AC: #7, #8)
  - [x] T13.1 — Created `web-frontend/src/components/public/Event/CapacityIndicator.tsx` ✅
  - [x] T13.2 — Props: `{ registrationCapacity?, spotsRemaining?, waitlistCount? }` ✅
  - [x] T13.3 — If `registrationCapacity` is null: render nothing (unlimited) ✅
  - [x] T13.4 — If `spotsRemaining > 0`: green badge "X spots remaining" ✅
  - [x] T13.5 — If `spotsRemaining === 0`: amber badge "Full — join waitlist" + optional chip with waitlist count ✅
  - [x] T13.6 — Uses i18n keys: `events.capacity.spotsRemaining`, `events.capacity.fullJoinWaitlist` ✅

- [x] **T14 — WaitlistSection component (organizer)** (AC: #5)
  - [x] T14.1 — Created `web-frontend/src/components/organizer/EventPage/WaitlistSection.tsx` ✅
  - [x] T14.2 — Props: `{ eventCode: string, waitlistCount: number }` ✅
  - [x] T14.3 — Renders collapsible MUI `Accordion` titled "Waitlist (N)" ✅
  - [x] T14.4 — Table columns: Position (#), Name, Email, Company, Registered On, Actions ✅
  - [x] T14.5 — "Promote to Registered" calls `promoteFromWaitlist(eventCode, registrationCode)` ✅
  - [x] T14.6 — Added `promoteFromWaitlist` to `eventRegistrationService.ts` (`POST /events/{eventCode}/registrations/{registrationCode}/promote`) ✅
  - [x] T14.7 — After promotion: invalidates queries `['event-registrations', eventCode]` + `['events', eventCode]` ✅

- [x] **T15 — Promote endpoint (organizer only)** (AC: #3, #5) — DONE in backend phase ✅

- [x] **T16 — EventParticipantsTab capacity bar** (AC: #5)
  - [x] T16.1 — Read `EventParticipantsTab.tsx` ✅
  - [x] T16.2 — Added `LinearProgress` bar (determinate, red when full) ✅
  - [x] T16.3 — Added `<WaitlistSection>` below `<EventParticipantList>` when `registrationCapacity != null` ✅

- [x] **T17 — Event Settings — Registration Capacity field** (AC: #6) ✅
  - [x] T17.1 — Added `registrationCapacity` MUI `TextField` + `handleCapacitySave` to `EventSettingsTab.tsx` ✅
  - [x] T17.2 — Blank value submits `null` (unlimited); disabled when `event.workflowState === 'ARCHIVED'` ✅

- [x] **T18 — HomePage capacity badge** (AC: #7) ✅
  - [x] T18.1 — Imported `CapacityIndicator` in `HomePage.tsx` and rendered in logistics section ✅

- [x] **T19 — RegistrationWizard waitlist acknowledgment** (AC: #8) ✅
  - [x] T19.1 — Added `spotsRemaining?: number | null` prop to `RegistrationWizard`; updated `HeroSection` and `RegistrationPage` callers ✅
  - [x] T19.2 — MUI Alert + Checkbox rendered in Step 2 when `spotsRemaining === 0` ✅
  - [x] T19.3 — Submit disabled until `waitlistAcknowledged === true` when event full ✅
  - [x] T19.4 — Waitlist-specific success view (`wizard.waitlistSuccessTitle`) when `isWaitlistRegistration` ✅

- [x] **T20 — i18n keys** (AC: #11) ✅
  - [x] T20.1 — Added `capacity.*` to `en/events.json` and `de/events.json` ✅
  - [x] T20.2 — Added `participantsTab.confirmed/onWaitlist/capacityBar/waitlistSection/waitlistPromote*` ✅
  - [x] T20.3 — Added `settings.capacityLabel/capacityHelperText` ✅
  - [x] T20.4 — Added `wizard.waitlistWarning/waitlistAcknowledgeLabel/waitlistSuccess*` to `en/registration.json` and `de/registration.json` ✅
  - [x] T20.5 — Added `registrationStatusBanner.waitlistWithPosition` to `en` and `de` ✅
  - [x] T20.6 — Added `[MISSING]` prefix keys to all 8 other locales (fr, it, rm, es, fi, nl, ja, gsw-BE) via Python script ✅

- [x] **T21 — RegistrationStatusBanner waitlist position** (AC: #13) ✅
  - [x] T21.1 — `waitlistPosition?: number | null` prop added to `RegistrationStatusBanner` ✅
  - [x] T21.2 — When `WAITLIST` + `waitlistPosition != null` → uses `waitlistWithPosition` key with `{ position }` interpolation; fallback to `waitlist` when null ✅
  - [x] T21.3 — `HomePage.tsx` passes `myRegistration?.waitlistPosition` to `RegistrationStatusBanner` ✅
  - [x] T21.6 — Two unit tests added: position=3 → `waitlistWithPosition` key; position=null → `waitlist` key ✅
  - Note: `waitlistWithPosition` i18n key already present in en/de from T20 ✅
  - Note: Also fixed `RegistrationStatus` type in `eventParticipant.types.ts`: `WAITLISTED` → `WAITLIST` (consistent with OpenAPI fix in T1.3) ✅
  - Note: Also fixed `EventParticipantTable.tsx` `case 'WAITLISTED'` → `case 'WAITLIST'` ✅

- [x] **T22 — Frontend full test run** (AC: #11, #13) ✅
  - [x] T22.1 — 279 test files passed, 3858 tests passed, 0 failures ✅
  - [x] T22.2 — `npm run type-check` → exit 0 ✅
  - [x] T22.3 — `npm run lint` → exit 0 ✅

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

### Code Review Fixes (2026-03-02)

- **H1 (AC13)**: `RegistrationService.getMyRegistration()` now chains `.waitlistPosition(registration.getWaitlistPosition())` — `RegistrationStatusBanner` can now display "You are #N on the waitlist"
- **H2 (AC11)**: `WaitlistSection.tsx` table headers (`#`, Name, Email, Company, Registered On, Actions) now use `t()` i18n keys; `waitlistTable*` keys added to `en/events.json`, `de/events.json`, and `[MISSING]` in all 8 other locales
- **M1**: Story File List expanded with 9 undocumented git-changed files (OpenAPI spec, generated DTOs, eventRegistrationService.ts, events-api.types.ts, EventMapper.java, V74 migration)
- **M2**: `promoteFromWaitlist` endpoint now validates registration belongs to the requested event before promoting — prevents cross-event promotion by organizers
- **M3**: Created `V74__migrate_waitlisted_to_waitlist.sql` — normalises any legacy `waitlisted` rows to `waitlist` and removes `waitlisted` from the status check constraint

### Completion Notes List

- T1: OpenAPI task name is `openApiGenerate` (not `openApiGenerateEvents`)
- T6: `REGISTRATION_OPEN` does not exist in `EventWorkflowState` enum — used `CREATED` in integration tests
- T7/T8: `UserResponse` has no `getPreferredLanguage()` (Story 10.15 adds it) — locale defaults to `Locale.GERMAN`
- T8: `WaitlistPromotionEmailService` sends both promotion and waitlist-confirmation emails (two methods)
- T9/Email seeding: Added `waitlist-` prefix mapping to `REGISTRATION` category in `EmailTemplateSeedService.deriveCategory()`
- T10: Added `WaitlistPromotionEmailService` as dependency to `RegistrationService`
- Extra files created: `RegistrationNotFoundException.java` and `RegistrationNotOnWaitlistException.java` (needed for T7.5/T15)
- WaitlistPromotionServiceTest: 5 unit tests all GREEN
- T17: Used `useUpdateEvent` (PATCH semantics) for capacity save; disabled when `workflowState === 'ARCHIVED'`
- T18: CapacityIndicator renders nothing when `registrationCapacity` is null (unlimited) — no guard needed in HomePage
- T19: `spotsRemaining` propagated via HeroSection props chain; waitlist-specific success uses `wizard.waitlistSuccessTitle` (no position — API doesn't return it); `isWaitlistRegistration` derived from `isEventFull` at submit time
- T21: Fixed `RegistrationStatus` type in `eventParticipant.types.ts` from `WAITLISTED` → `WAITLIST` (OpenAPI was already fixed in T1.3 but hand-written type was stale); same fix in `EventParticipantTable.tsx`
- T22: 3858/3858 tests pass; type-check clean; lint clean (< 50 warnings)

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
- `web-frontend/src/components/public/RegistrationStatusBanner.tsx` (T21: waitlistPosition prop + waitlistWithPosition key)
- `web-frontend/src/components/public/__tests__/RegistrationStatusBanner.test.tsx` (T21.6: 2 new tests)
- `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx` (T17: registrationCapacity field)
- `web-frontend/src/components/organizer/EventPage/EventParticipantTable.tsx` (fix: WAITLISTED→WAITLIST)
- `web-frontend/src/types/eventParticipant.types.ts` (fix: WAITLISTED→WAITLIST in RegistrationStatus)
- `web-frontend/src/pages/public/HomePage.tsx` (T18: CapacityIndicator; T21: waitlistPosition; T19: spotsRemaining→HeroSection)
- `web-frontend/src/components/public/Hero/HeroSection.tsx` (T19: spotsRemaining prop → RegistrationWizard)
- `web-frontend/src/components/public/Registration/RegistrationWizard.tsx` (T19: spotsRemaining, waitlist alert+checkbox, waitlist success)
- `web-frontend/src/pages/public/RegistrationPage.tsx` (T19: spotsRemaining prop pass-through)
- `docs/api/events-api.openapi.yml` (T1: capacity fields + waitlist position + promote endpoint)
- `services/event-management-service/src/main/java/ch/batbern/events/mapper/EventMapper.java` (T11.1–T11.3: registrationCapacity mapping)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/CreateEventRequest.java` (T1.5: generated from OpenAPI — registrationCapacity)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/EventResponse.java` (T1.5: generated from OpenAPI — capacity response fields)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/PatchEventRequest.java` (T1.5: generated from OpenAPI — registrationCapacity)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/UpdateEventRequest.java` (T1.5: generated from OpenAPI — registrationCapacity)
- `web-frontend/src/services/api/eventRegistrationService.ts` (T14.6: promoteFromWaitlist function)
- `web-frontend/src/types/generated/events-api.types.ts` (T1.6: generated from OpenAPI — capacity types)
- `services/event-management-service/src/main/resources/db/migration/V74__migrate_waitlisted_to_waitlist.sql` (CR fix M3: normalise legacy waitlisted rows)
