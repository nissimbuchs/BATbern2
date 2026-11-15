# Story 10.15: Newsletter Subscription Integrity & Language Fix

Status: ready-for-dev

<!-- Prerequisite: Story 10.7 (newsletter subscriber tables + NewsletterSubscriberService) -->

## Story

As a **community member**,
I want my newsletter subscription language preference to match the language I was browsing in when I registered,
so that I receive newsletters in my preferred language — not always in German.

As an **organizer**,
I want to confirm that all newsletter opt-in paths (public registration wizard, speaker portal) are wired correctly,
so that no subscriber is silently lost or incorrectly assigned to the wrong language.

## Acceptance Criteria

1. **AC1 — OpenAPI: `preferredLanguage` added to `communicationPreferences`**:
   `CreateRegistrationRequest.communicationPreferences` schema (line ~6082 in `events-api.openapi.yml`) gains an optional `preferredLanguage` string field (nullable, no enum constraint — matches the i18n language code, e.g., "de", "en", "fr").

2. **AC2 — Backend DTO regenerated**: After OpenAPI change, `./gradlew :services:event-management-service:openApiGenerateEvents` produces updated `CreateRegistrationRequestCommunicationPreferences.java` with `preferredLanguage` getter.

3. **AC3 — `RegistrationService` uses `preferredLanguage` (not hardcoded "de")**:
   The hardcoded `"de"` at line ~158 in `RegistrationService.createRegistration()` is replaced with:
   ```java
   String lang = Optional.ofNullable(request.getCommunicationPreferences())
       .map(CreateRegistrationRequestCommunicationPreferences::getPreferredLanguage)
       .filter(l -> l != null && !l.isBlank())
       .orElse("de");
   ```
   `newsletterSubscriberService.subscribe(...)` call passes `lang` as the language param.

4. **AC4 — Unit test (RED→GREEN)**: New `RegistrationServiceTest.java` (pure unit test, no Spring context) proves:
   - When `preferredLanguage = "fr"` → subscriber created with `language = "fr"`
   - When `preferredLanguage = null` → subscriber created with `language = "de"` (fallback)
   - When `newsletterSubscribed = false` → `subscribe()` is never called

5. **AC5 — Integration test**: `RegistrationControllerIntegrationTest` gains 3 new test methods (or a new `NewsletterOptInIntegrationTest`):
   - POST registration with `newsletterSubscribed=true` + `preferredLanguage=fr` → subscriber created with `language=fr`
   - POST registration with `newsletterSubscribed=true` + `preferredLanguage=null` → subscriber created with `language=de`
   - POST registration with `newsletterSubscribed=false` → no subscriber row created

6. **AC6 — Frontend: `RegistrationWizard.tsx` passes `preferredLanguage`**:
   The `formData.communicationPreferences` initial state includes `preferredLanguage: i18n.language` (using the active i18n language at registration time). When locale changes during the wizard, `preferredLanguage` updates accordingly.

7. **AC7 — Speaker portal scaffolding**: Add a TODO comment in `RegistrationService.createRegistration()` and in the speaker coordination service (if any portal registration code exists) pointing to Epic 9. Since Epic 9 is `backlog`, no functional implementation — just a clearly marked placeholder.

8. **AC8 — No regression**: All existing Story 10.7 newsletter tests pass. `./gradlew :services:event-management-service:test` BUILD SUCCESS.

9. **AC9 — Type-check and Checkstyle pass**: `npm run type-check`, `npm run lint`, `./gradlew :services:event-management-service:checkstyleMain` pass.

---

## Tasks / Subtasks

### Phase 1: OpenAPI Contract Update (ADR-006 — FIRST)

- [ ] **T1 — Add `preferredLanguage` to `events-api.openapi.yml`** (AC: #1)
  - [ ] T1.1 — Open `docs/api/events-api.openapi.yml`
  - [ ] T1.2 — Locate `CreateRegistrationRequest.communicationPreferences` properties (~line 6082–6092)
  - [ ] T1.3 — Add `preferredLanguage` after `eventReminders`:
    ```yaml
    communicationPreferences:
      type: object
      description: Email communication preferences
      properties:
        newsletterSubscribed:
          type: boolean
          default: false
          description: Subscribe to newsletter
        eventReminders:
          type: boolean
          default: true
          description: Receive event reminders
        preferredLanguage:
          type: string
          nullable: true
          description: >
            Language preference for newsletter communications (e.g., "de", "en", "fr").
            If omitted, defaults to "de" on the backend. Should match the user's UI language
            at registration time. No enum constraint — must accept any i18n locale code.
    ```
  - [ ] T1.4 — NOTE: There are TWO `communicationPreferences` schemas in the file (~line 5769 and ~line 6082). Story 10.15 only changes the `CreateRegistrationRequest` one (~line 6082). The other is on a different schema — leave it unchanged.

- [ ] **T2 — Regenerate backend DTO** (AC: #2)
  - [ ] T2.1 — From repository root: `./gradlew :services:event-management-service:openApiGenerateEvents 2>&1 | tee /tmp/openapi-10-15.log && grep -E "BUILD|error" /tmp/openapi-10-15.log`
  - [ ] T2.2 — Verify `build/generated/src/main/java/ch/batbern/events/dto/generated/CreateRegistrationRequestCommunicationPreferences.java` now has `private String preferredLanguage` and `getPreferredLanguage()` getter
  - [ ] T2.3 — Regenerate frontend types: `cd web-frontend && npm run generate:api-types 2>&1 | tee /tmp/typegenfront-10-15.log && grep -E "error|done" /tmp/typegenfront-10-15.log`
    - NOTE: `CreateRegistrationRequest` is a **generated type** in `web-frontend/src/types/generated/events-api.types.ts` — it is NOT manually defined in `registrationService.ts`. Always regenerate from OpenAPI rather than editing the generated file manually. (Original T2.3 was incorrect; superseded by this fix — IR check 2026-03-01.)

### Phase 2: Backend — TDD first

- [ ] **T3 — Create `RegistrationServiceTest.java` (RED phase)** (AC: #4)
  - [ ] T3.1 — Create new file: `services/event-management-service/src/test/java/ch/batbern/events/service/RegistrationServiceTest.java`
  - [ ] T3.2 — Pure unit test (no Spring context, no Testcontainers). Mock all dependencies:
    ```java
    package ch.batbern.events.service;

    import ch.batbern.events.client.UserApiClient;
    import ch.batbern.events.domain.Event;
    import ch.batbern.events.domain.Registration;
    import ch.batbern.events.dto.generated.CreateRegistrationRequest;
    import ch.batbern.events.dto.generated.CreateRegistrationRequestCommunicationPreferences;
    import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
    import ch.batbern.events.dto.generated.users.UserResponse;
    import ch.batbern.events.exception.DuplicateSubscriberException;
    import ch.batbern.events.repository.EventRepository;
    import ch.batbern.events.repository.RegistrationRepository;
    import org.junit.jupiter.api.BeforeEach;
    import org.junit.jupiter.api.DisplayName;
    import org.junit.jupiter.api.Test;
    import org.junit.jupiter.api.extension.ExtendWith;
    import org.mockito.ArgumentCaptor;
    import org.mockito.InjectMocks;
    import org.mockito.Mock;
    import org.mockito.junit.jupiter.MockitoExtension;

    import java.util.Optional;
    import java.util.UUID;

    import static org.assertj.core.api.Assertions.assertThat;
    import static org.mockito.ArgumentMatchers.*;
    import static org.mockito.Mockito.*;
    ```
  - [ ] T3.3 — Add test: `createRegistration_withPreferredLanguageFr_subscribesWithFrench`:
    ```java
    @Test
    @DisplayName("newsletter subscribe: when preferredLanguage=fr → subscribe called with language=fr")
    void createRegistration_withPreferredLanguageFr_subscribesWithFrench() {
        // Arrange
        var prefs = new CreateRegistrationRequestCommunicationPreferences()
                .newsletterSubscribed(true);
        prefs.setPreferredLanguage("fr");  // after AC2 DTO regen
        var request = new CreateRegistrationRequest("test@example.com", "Alice", "Smith", true)
                .communicationPreferences(prefs);

        mockEventAndUser();
        when(registrationRepository.existsByRegistrationCode(any())).thenReturn(false);
        when(registrationRepository.findByEventIdAndAttendeeUsername(any(), any())).thenReturn(Optional.empty());

        // Act
        registrationService.createRegistration("BATbern99", request);

        // Assert: language is "fr", not hardcoded "de"
        verify(newsletterSubscriberService).subscribe(
                eq("test@example.com"), eq("Alice"), eq("fr"), eq("registration"), any());
    }
    ```
  - [ ] T3.4 — Add test: `createRegistration_withNullPreferredLanguage_subscribesWithGermanFallback`:
    ```java
    @Test
    @DisplayName("newsletter subscribe: when preferredLanguage=null → subscribe called with language=de (fallback)")
    void createRegistration_withNullPreferredLanguage_subscribesWithGermanFallback() {
        var prefs = new CreateRegistrationRequestCommunicationPreferences()
                .newsletterSubscribed(true);
        prefs.setPreferredLanguage(null);
        var request = new CreateRegistrationRequest("test@example.com", "Bob", "Doe", true)
                .communicationPreferences(prefs);

        mockEventAndUser();
        when(registrationRepository.existsByRegistrationCode(any())).thenReturn(false);
        when(registrationRepository.findByEventIdAndAttendeeUsername(any(), any())).thenReturn(Optional.empty());

        registrationService.createRegistration("BATbern99", request);

        verify(newsletterSubscriberService).subscribe(
                eq("test@example.com"), eq("Bob"), eq("de"), eq("registration"), any());
    }
    ```
  - [ ] T3.5 — Add test: `createRegistration_withNewsletterOptOutFalse_doesNotSubscribe`:
    ```java
    @Test
    @DisplayName("newsletter subscribe: when newsletterSubscribed=false → subscribe never called")
    void createRegistration_withNewsletterOptOutFalse_doesNotSubscribe() {
        var prefs = new CreateRegistrationRequestCommunicationPreferences()
                .newsletterSubscribed(false);
        prefs.setPreferredLanguage("en");
        var request = new CreateRegistrationRequest("test@example.com", "Carol", "Smith", true)
                .communicationPreferences(prefs);

        mockEventAndUser();
        when(registrationRepository.existsByRegistrationCode(any())).thenReturn(false);
        when(registrationRepository.findByEventIdAndAttendeeUsername(any(), any())).thenReturn(Optional.empty());

        registrationService.createRegistration("BATbern99", request);

        verifyNoInteractions(newsletterSubscriberService);
    }
    ```
  - [ ] T3.6 — Add `@BeforeEach mockEventAndUser()` helper that stubs `eventRepository.findByEventCode()` and `userApiClient.getOrCreateUser()` with minimal valid data; stubs `registrationRepository.save()` to return a new `Registration`
  - [ ] T3.7 — Run RED phase: `./gradlew :services:event-management-service:test --tests RegistrationServiceTest 2>&1 | tee /tmp/test-10-15-red.log && grep -E "FAILED|BUILD|error" /tmp/test-10-15-red.log`

- [ ] **T4 — Update `RegistrationService.createRegistration()`** (AC: #3, #7)
  - [ ] T4.1 — Open `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java`
  - [ ] T4.2 — Locate the newsletter subscribe block (~line 153–163):
    ```java
    // Story 10.7 (AC6): Auto-subscribe to newsletter if opted in during registration
    if (request.getCommunicationPreferences() != null
            && Boolean.TRUE.equals(request.getCommunicationPreferences().getNewsletterSubscribed())) {
        try {
            newsletterSubscriberService.subscribe(
                    request.getEmail(), request.getFirstName(), "de", "registration", username);
    ```
  - [ ] T4.3 — Replace with (Story 10.15 language fix):
    ```java
    // Story 10.7 (AC6): Auto-subscribe to newsletter if opted in during registration
    // Story 10.15 (AC3): Use preferredLanguage from request, not hardcoded "de"
    if (request.getCommunicationPreferences() != null
            && Boolean.TRUE.equals(request.getCommunicationPreferences().getNewsletterSubscribed())) {
        try {
            String lang = Optional.ofNullable(request.getCommunicationPreferences())
                    .map(CreateRegistrationRequestCommunicationPreferences::getPreferredLanguage)
                    .filter(l -> l != null && !l.isBlank())
                    .orElse("de");
            newsletterSubscriberService.subscribe(
                    request.getEmail(), request.getFirstName(), lang, "registration", username);
    ```
  - [ ] T4.4 — Add TODO comment for Epic 9 speaker portal (AC7), after the newsletter block:
    ```java
    // TODO(Story 10.15 / Epic 9): When SpeakerPortalAccountService is implemented (Epic 9.2 / story 9.2),
    // wire speaker portal account creation newsletter opt-in here OR via domain event
    // (SpeakerPortalRegisteredEvent → EMS consumer → subscribe). See epic-9 spec.
    ```
  - [ ] T4.5 — Note: Import for `CreateRegistrationRequestCommunicationPreferences` is already present in the file (it's used in the existing null check). No new imports needed.

- [ ] **T5 — Run GREEN tests** (AC: #4, #8)
  - [ ] T5.1 — `./gradlew :services:event-management-service:test --tests RegistrationServiceTest 2>&1 | tee /tmp/test-10-15-green.log && grep -E "BUILD|FAILED|passed" /tmp/test-10-15-green.log`
  - [ ] T5.2 — Full service test suite: `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-15-full.log && grep -E "BUILD|FAILED|tests" /tmp/test-10-15-full.log`

### Phase 3: Integration Tests

- [ ] **T6 — Add integration tests** (AC: #5)
  - [ ] T6.1 — Open `services/event-management-service/src/test/java/ch/batbern/events/controller/RegistrationControllerIntegrationTest.java` (or create `NewsletterOptInIntegrationTest.java` extending `AbstractIntegrationTest` if registration test class is too large)
  - [ ] T6.2 — Add: `registerWithLanguageFr_createsSubscriberWithFrLanguage`:
    ```java
    @Test
    @DisplayName("registration with preferredLanguage=fr creates newsletter subscriber with language=fr")
    void registerWithLanguageFr_createsSubscriberWithFrLanguage() throws Exception {
        // POST /api/v1/events/{eventCode}/registrations with newsletterSubscribed=true, preferredLanguage=fr
        // Assert: newsletter_subscribers table has entry with language='fr'
    }
    ```
  - [ ] T6.3 — Add: `registerWithNoLanguage_createsSubscriberWithDeLanguage`
  - [ ] T6.4 — Add: `registerWithNewsletterFalse_doesNotCreateSubscriber`
  - [ ] T6.5 — Add: `registerWithDuplicateEmail_doesNotThrow_subscriberUnchanged` (idempotency — already subscribed email)
  - [ ] T6.6 — Run: `./gradlew :services:event-management-service:test --tests "RegistrationControllerIntegrationTest|NewsletterOptInIntegrationTest" 2>&1 | tee /tmp/test-10-15-integration.log && grep -E "BUILD|FAILED|passed" /tmp/test-10-15-integration.log`

### Phase 4: Frontend

- [ ] **T7 — Verify frontend `CreateRegistrationRequest` type updated** (AC: #9 / type safety)
  - [ ] T7.1 — Confirm `preferredLanguage` now present: `grep -n "preferredLanguage" web-frontend/src/types/generated/events-api.types.ts`
    - This was already done in T2.3 (type regeneration from OpenAPI). This step is a verification only.
  - [ ] T7.2 — If T2.3 type regen was skipped for any reason, add manually to `web-frontend/src/types/generated/events-api.types.ts` in the `CreateRegistrationRequest.communicationPreferences` block:
    ```typescript
    communicationPreferences?: {
      /** @description Subscribe to newsletter @default false */
      newsletterSubscribed: boolean;
      /** @description Receive event reminders @default true */
      eventReminders: boolean;
      /** @description Language preference for newsletter (e.g. "de", "en", "fr"). Story 10.15. */
      preferredLanguage?: string;
    };
    ```
  - [ ] T7.3 — NOTE: `CreateRegistrationRequest` is confirmed to be a **generated type** in `web-frontend/src/types/generated/events-api.types.ts:3155`. It is NOT in `registrationService.ts`. Always prefer regeneration (T2.3) over manual edits to generated files.

- [ ] **T8 — Update `RegistrationWizard.tsx` to pass `preferredLanguage`** (AC: #6)
  - [ ] T8.1 — Read the FULL current file at `web-frontend/src/components/public/Registration/RegistrationWizard.tsx` before editing
  - [ ] T8.2 — Add `i18n` to the `useTranslation` destructure:
    ```typescript
    const { t, i18n } = useTranslation(['registration', 'common']);
    ```
  - [ ] T8.3 — Update `formData` initial state to include `preferredLanguage`:
    ```typescript
    const [formData, setFormData] = useState<CreateRegistrationRequest>({
      firstName: '',
      lastName: '',
      email: '',
      company: '',
      role: '',
      termsAccepted: false,
      communicationPreferences: {
        newsletterSubscribed: false,
        eventReminders: true,
        preferredLanguage: i18n.language,  // Story 10.15: capture UI language at wizard open time
      },
      specialRequests: '',
    });
    ```
  - [ ] T8.4 — Add a `useEffect` to update `preferredLanguage` if the language changes while the wizard is open (defensive, but important for users who switch language mid-flow):
    ```typescript
    useEffect(() => {
      setFormData(prev => ({
        ...prev,
        communicationPreferences: {
          ...prev.communicationPreferences,
          preferredLanguage: i18n.language,
        },
      }));
    }, [i18n.language]);
    ```
  - [ ] T8.5 — Verify `formData` is passed as-is to `eventApiClient.createRegistration(eventCode, formData)` in `handleSubmit` — no changes needed there.

### Phase 5: Final Verification

- [ ] **T9 — Frontend type-check and lint** (AC: #9)
  - [ ] T9.1 — `cd web-frontend && npm run type-check 2>&1 | tee /tmp/typecheck-10-15.log && grep -c "error" /tmp/typecheck-10-15.log`
  - [ ] T9.2 — `npm run lint 2>&1 | tee /tmp/lint-10-15.log && grep -E "^.*error" /tmp/lint-10-15.log | head -10`
  - [ ] T9.3 — `npm run test -- --run 2>&1 | tee /tmp/test-10-15-frontend.log && grep -E "pass|fail|error" /tmp/test-10-15-frontend.log | tail -10`

- [ ] **T10 — Backend Checkstyle and full test** (AC: #8, #9)
  - [ ] T10.1 — `./gradlew :services:event-management-service:checkstyleMain 2>&1 | tee /tmp/checkstyle-10-15.log && grep -E "BUILD|error" /tmp/checkstyle-10-15.log`
  - [ ] T10.2 — `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-15-final.log && grep -E "BUILD|FAILED|tests" /tmp/test-10-15-final.log`

---

## Dev Notes

### ⚠️ CRITICAL: Epic Spec Deviation — `preferredLanguage` Does NOT Exist Yet

The epic spec (epic-10-additional-stories.md) states:
> "`CreateRegistrationRequest` already has `communicationPreferences.preferredLanguage` (String, nullable)"

**This is WRONG.** The actual generated DTO `CreateRegistrationRequestCommunicationPreferences.java` only has:
- `newsletterSubscribed: Boolean`
- `eventReminders: Boolean`

There is **no `preferredLanguage` field**. This story must add it to the OpenAPI spec FIRST (T1), then regenerate the DTO (T2), THEN use it in `RegistrationService` (T4).

Failure to do this in order will cause compilation errors.

### Architecture Compliance

**ADR-006 (Contract-First)**: Update `events-api.openapi.yml` BEFORE any backend DTO changes. The `CreateRegistrationRequestCommunicationPreferences` schema (at `CreateRegistrationRequest.communicationPreferences`) gets a new optional `preferredLanguage: string` field.

**ADR-004 (No Denormalized User Data)**: The `preferredLanguage` is passed through in the request and used for newsletter subscription only. It is NOT stored on the `Registration` entity — it belongs on the newsletter subscriber record (`language` column), which already exists.

**TDD Mandate**: Write `RegistrationServiceTest.java` FIRST (T3) as pure unit tests, then fix `RegistrationService` (T4). There is no existing `RegistrationServiceTest.java` — this story creates it.

### Critical Implementation Details

#### `RegistrationService` — The Exact Bug Location
File: `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java`
Line ~158:
```java
newsletterSubscriberService.subscribe(
    request.getEmail(), request.getFirstName(), "de", "registration", username);
                                                ^^^^  ← hardcoded "de" — THIS IS THE BUG
```

The fix uses `Optional.ofNullable(request.getCommunicationPreferences()).map(...).orElse("de")`. Note: the outer null check on `communicationPreferences` already exists for the `newsletterSubscribed` guard — the inner `Optional.ofNullable` is for safety if `preferredLanguage` is null.

#### `NewsletterSubscriberService.subscribe()` — Already Handles Language Correctly
The `language` param already works correctly:
- Accepts any string
- Defaults to `"de"` if null/blank (line ~80 in `NewsletterSubscriberService`)
- For reactivations, only updates language if non-blank (line ~65)

No changes needed to `NewsletterSubscriberService`.

#### Frontend — `i18n.language` Values
The BATbern frontend supports: `de, en, fr, it, rm, es, fi, nl, ja, gsw-BE` (9+1 locales).
`i18n.language` returns the current locale code directly (e.g., `"de"`, `"en"`, `"fr"`).
The backend `NewsletterSubscriberService` does NOT validate the language against an enum — it accepts any string. The `language` column on `newsletter_subscribers` is a plain `varchar`. So passing `"fr"`, `"it"`, etc. is safe.

#### Frontend Type — Check if Generated or Manual
Before T7.3, run:
```bash
grep -n "newsletterSubscribed\|CreateRegistrationRequest" web-frontend/src/services/registrationService.ts
```
If found manually defined → update manually (like Story 10.14 `NewsletterSendRequest`).
If not found there → check `web-frontend/src/types/generated/events-api.types.ts` → then regenerate types.

#### `RegistrationWizard.tsx` — `useState` Initial Value and `i18n`
The `useState` initializer with `i18n.language` is fine because `i18n` is always initialized before the component renders. The `useEffect` at T8.4 handles the case where a user switches language while the wizard is already open (unlikely but correct to handle).

No i18n string changes needed — `preferredLanguage` is data, not a UI string.

#### Speaker Portal — Scaffold Only (Epic 9 is Backlog)
Epic 9 is `backlog` status. No `SpeakerPortalAccountService`, no speaker portal registration page exists. The only deliverable for AC7 is a TODO comment in `RegistrationService.java`. Check if there is any Epic 9 code already: `find services/speaker-coordination-service -name "*Portal*" -o -name "*Account*"` — currently returns nothing.

#### No `RegistrationServiceTest.java` Exists Yet
There is:
- `RegistrationServiceBatchTest.java` — tests `createBatchRegistrations()`
- `RegistrationControllerIntegrationTest.java` — integration tests against real DB

There is NO unit test for `createRegistration()`. Story 10.15 creates `RegistrationServiceTest.java` as a pure Mockito unit test. Keep it focused on the newsletter language behavior; don't add other test coverage beyond the AC4 scenarios.

#### Two `communicationPreferences` Schemas in OpenAPI
`events-api.openapi.yml` has two objects with `communicationPreferences` properties:
- ~Line 5769: part of `RegistrationResponse` (or similar response object) — READ-ONLY, **do NOT add `preferredLanguage` here**
- ~Line 6082: part of `CreateRegistrationRequest` — **this is the one to update**

Only change the `CreateRegistrationRequest` one.

### Key Modified Files

| File | Change |
|------|--------|
| `docs/api/events-api.openapi.yml` | Add `preferredLanguage: string (nullable)` to `CreateRegistrationRequest.communicationPreferences` (~line 6082) |
| `services/event-management-service/src/main/java/.../dto/generated/CreateRegistrationRequestCommunicationPreferences.java` | Auto-generated — do NOT edit manually, only via openApiGenerateEvents |
| `services/event-management-service/src/main/java/.../service/RegistrationService.java` | Replace hardcoded `"de"` with `preferredLanguage` extraction; add Epic 9 TODO comment |
| `services/event-management-service/src/test/java/.../service/RegistrationServiceTest.java` | **NEW FILE** — 3 unit tests for newsletter language behavior |
| `services/event-management-service/src/test/java/.../controller/RegistrationControllerIntegrationTest.java` | 3-4 new integration test methods for newsletter opt-in with language |
| `web-frontend/src/services/registrationService.ts` (likely) | Add `preferredLanguage?: string` to `communicationPreferences` type |
| `web-frontend/src/components/public/Registration/RegistrationWizard.tsx` | Add `i18n` to `useTranslation`; include `preferredLanguage: i18n.language` in formData; add `useEffect` for language changes |

### Project Structure Notes

- Backend service root: `services/event-management-service/src/main/java/ch/batbern/events/`
- Service under test: `.../service/RegistrationService.java` (line ~153–164 contains the bug)
- Generated DTO root: `services/event-management-service/build/generated/src/main/java/ch/batbern/events/dto/generated/`
- OpenAPI spec: `docs/api/events-api.openapi.yml` (single file, ~6100+ lines)
- Gradle task for DTO regen: `:services:event-management-service:openApiGenerateEvents`
- Frontend wizard: `web-frontend/src/components/public/Registration/RegistrationWizard.tsx`
- No new services, no new DB migrations, no Flyway changes needed

### Previous Story (10.14) Patterns to Carry Forward

- Dump all test output to `/tmp/test-10-15-*.log` and grep, rather than running tests multiple times
- Check if frontend types are manually defined before deciding whether to regenerate (see 10.14's `newsletterService.ts` pattern)
- Use `2>&1 | tee` for Gradle commands to capture both stdout and stderr

### References

- Bug location: [Source: services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java:158]
- `CreateRegistrationRequestCommunicationPreferences` (missing `preferredLanguage`): [Source: services/event-management-service/build/generated/.../dto/generated/CreateRegistrationRequestCommunicationPreferences.java]
- `NewsletterSubscriberService.subscribe()` signature: [Source: services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterSubscriberService.java:49]
- OpenAPI `communicationPreferences` in `CreateRegistrationRequest`: [Source: docs/api/events-api.openapi.yml:6081–6092]
- OpenAPI `communicationPreferences` in RESPONSE schema (DO NOT change): [Source: docs/api/events-api.openapi.yml:5769]
- `RegistrationWizard.tsx` formData init: [Source: web-frontend/src/components/public/Registration/RegistrationWizard.tsx:66–78]
- Existing batch test (pattern reference): [Source: services/event-management-service/src/test/java/ch/batbern/events/service/RegistrationServiceBatchTest.java]
- Story 10.7 (newsletter infrastructure): [Source: sprint-status.yaml — 10-7-newsletter-subscription-and-sending: done]
- Epic 9 backlog (speaker portal auth): [Source: sprint-status.yaml — epic-9: backlog]

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/openapi-10-15.log`
- `/tmp/test-10-15-red.log`
- `/tmp/test-10-15-green.log`
- `/tmp/test-10-15-full.log`
- `/tmp/test-10-15-integration.log`
- `/tmp/test-10-15-frontend.log`
- `/tmp/typecheck-10-15.log`
- `/tmp/lint-10-15.log`
- `/tmp/checkstyle-10-15.log`
- `/tmp/test-10-15-final.log`

### Completion Notes List

### File List
