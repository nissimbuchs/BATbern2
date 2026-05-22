# Story 10.32: Additional Email Addresses per User Profile

Status: ready-for-dev

<!-- Prerequisites: Story 10.26 (SES email forwarding + sender-auth Lambda) MUST be deployed -->

## Story

As a **logged-in user (primarily an organizer)**,
I want to register one or more additional email addresses on my profile,
so that mail forwarded or sent to me by BATbern (`ok@batbern.ch` fan-out, event registration confirmations, etc.) reaches all of my addresses AND mail I send to BATbern from any of those addresses is treated as authorised — without needing my legacy/shared mailbox to be a separate BATbern user.

## Background

### The triggering incident

On **2026-05-20 06:28 UTC** the user **Nissim** (`nissim.buchs@elca.ch`, role `ORGANIZER`) forwarded an external email ("RE: Möglicher Beitrag zu BAT 06.03.2026" from Martin Petersen at Zühlke) to `ok@batbern.ch` from his iPhone Mail app. The send went via the legacy Hostpoint shared mailbox `info@berner-architekten-treffen.ch`, so the SMTP `From:` header was that shared address — not `nissim.buchs@elca.ch`.

Story 10.26's forwarder Lambda (`infrastructure/lambda/email-forwarder/sender-auth.ts:39-46`) restricts `ok@`, `partner@` and `batbern{N}@` senders to addresses currently listed under role `ORGANIZER` in the `role_assignments` table. The shared mailbox is not. The Lambda logged `WARN Unauthorized sender for address { to: 'ok@batbern.ch', sender: 'info@***' }` and **silently dropped** the email — no bounce, no NDR, no organizer received it (including Nissim himself, who would otherwise have received his own forwarded copy because sender-exclusion is intentionally disabled at `infrastructure/lambda/email-forwarder/index.ts:99-118`).

This class of incident will recur every time an organizer sends from a legacy/shared/personal address that isn't the one in `user_profiles.email`.

### What the platform needs

A user — any user, but mostly organizers — should be able to declare additional email addresses on their profile. Those additional addresses are then:

1. **Recipients** for any mail BATbern would have sent to the user's primary email (`ok@` fan-out to organizers, `partner@` fan-out to partners, `batbern{N}@` fan-out where the user is a registrant, event-registration confirmations).
2. **Authorised senders** for the forwarder Lambda's `isAuthorizedSender()` check — so a `From:` header matching any additional email of an `ORGANIZER` user is treated exactly like a `From:` of that user's primary email.

The setting lives on the existing user-settings page (`web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx`), in the Account sub-tab below the (currently read-only) primary email field.

### Architecture sketch

```
User profile (user_profiles)
   ├─ email                     ← primary, NOT changed by this story (Cognito-linked)
   └─ additional emails         ← NEW table `user_additional_emails`
                                   - one row per (user, email)
                                   - unique globally across user_profiles.email + additional rows

Email Forwarder Lambda (Story 10.26)
   ├─ sender-auth.ts            ← UPDATED: getOrganizerEmails() now returns primary ∪ additional
   └─ address-resolver.ts       ← UPDATED: fetchUsersByRole() returns the flattened email list
                                   so ok@ / partner@ / batbern{N}@ deliver to all addresses

CUMS /api/v1/users API
   ├─ GET /users/me             ← UPDATED: response includes `additionalEmails[]`
   ├─ NEW endpoints under /users/me/additional-emails (POST, DELETE)
   └─ GET /users?role=...       ← UPDATED: each user's response carries the flattened list
                                   (Lambda calls this — see Sender Authorization below)
```

### Out of scope (called out so the dev agent does not creep)

- **No Cognito primary-email change.** `user_profiles.email` and the Cognito `email` claim stay as-is and remain the login identifier. This story does NOT touch Cognito.
- **No ownership verification of additional emails (v1).** A verification email + confirmation flow (e.g. signed token with 48 h TTL) is the obvious follow-up but is explicitly deferred — see Open Question #1.
- **Newsletter delivery to additional emails is deferred** — the newsletter has its own `newsletter_subscribers` table keyed by email, separate from `user_profiles`. See Open Question #4.
- **No frontend admin-managed equivalent.** Only `/users/me/...` (self-service) endpoints in this story; organizer-managing-other-user's additional emails is a future story if needed.

## Acceptance Criteria

### Data model & API

1. **AC1 — `user_additional_emails` table**: New Flyway migration in `services/company-user-management-service/src/main/resources/db/migration/V16__create_user_additional_emails.sql` (next free version after V15) creates a table with: `id` (UUID PK), `user_id` (UUID FK → `user_profiles.id`, cascade delete), `email` (varchar 255), `label` (varchar 100, nullable — free-text user hint such as "Hostpoint shared"), `created_at` (timestamp), `verified_at` (timestamp, nullable — populated by the v2 verification flow; this story always writes NULL). Unique constraint on `(LOWER(email))` across the table. Indexed on `user_id`. Foreign-key cascade-delete from `user_profiles`.

2. **AC2 — Email uniqueness across primary + additional**: A trigger or service-level check rejects an `INSERT` into `user_additional_emails` whose `LOWER(email)` collides with ANY existing `LOWER(user_profiles.email)` or any existing `LOWER(user_additional_emails.email)`. Conversely the existing `idx_users_email` unique index on `user_profiles.email` is augmented so that a future change of the primary email cannot collide with an existing additional email (defensive — primary-email change is out of scope here but the constraint is cheap to add). Return HTTP 409 with `errorCode: ADDITIONAL_EMAIL_DUPLICATE`.

3. **AC3 — Domain entity**: `User.java` (the existing aggregate root at `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/User.java`) gains a `@OneToMany(mappedBy = "user", cascade = ALL, orphanRemoval = true, fetch = LAZY) @BatchSize(50) List<UserAdditionalEmail> additionalEmails`. NEW entity `UserAdditionalEmail` in the same package (`domain/`). Per project convention (CLAUDE.md JPA rules), fetch is `LAZY`; repository methods that need additional emails for a list of users use `JOIN FETCH` to avoid N+1.

4. **AC4 — OpenAPI spec updated**: `docs/api/users-api.openapi.yml` (single source of truth per ADR-006):
   - `UserResponse` schema gains `additionalEmails: array of AdditionalEmail` (always present, may be empty).
   - New schema `AdditionalEmail { email: string (email format), label: string (nullable), createdAt: date-time, verifiedAt: date-time (nullable) }`.
   - New endpoint `POST /users/me/additional-emails` — body `{ email: string, label?: string }` → 201 with the created object, 400 invalid email, 409 duplicate, 422 over the per-user cap (AC5).
   - New endpoint `DELETE /users/me/additional-emails/{email}` (path-encoded email) → 204 on success, 404 if not on the caller's profile.
   - `GET /users/me` response includes the populated `additionalEmails` array.
   - `GET /users` (paginated list) — each item's `UserResponse` also includes `additionalEmails`. This is the contract the Lambda relies on (no separate endpoint for "user with all emails" — keeps the Lambda's existing single API call intact).

5. **AC5 — Per-user cap of 5 additional emails**: Service-level guard. Attempting to add a 6th returns HTTP 422 with `errorCode: ADDITIONAL_EMAIL_LIMIT_REACHED` and a human-readable message. Cap is a Spring `@Value("${batbern.user.additional-emails.max:5}")` constant on the service for future tuning.

6. **AC6 — Code generation regenerated**:
   - Backend: `./gradlew :services:company-user-management-service:openApiGenerateUsers` produces clean DTOs.
   - Frontend: `cd web-frontend && npm run generate:api-types:users` produces clean types in `src/types/generated/users-api.types.ts`. Both committed.

### Frontend (user-settings page)

7. **AC7 — "Additional emails" section in Account sub-tab**: Render directly below the existing read-only primary email field in `UserSettingsTab.tsx` (tab index 0). Section heading `t('settings.account.additionalEmailsTitle')`. Helper paragraph explains the dual purpose (receive copies + authorise as sender). Shows a list of current additional emails (each row: email + optional label + a delete icon + an unverified pill — the unverified pill is rendered statically in v1 since `verifiedAt` is always null). Below the list: a small inline form (`<TextField type="email">` + optional label `<TextField>` + an `Add` `<Button>`). When the user is already at the cap, the form is replaced by an info `<Alert>` (`settings.account.additionalEmailsAtLimit`).

8. **AC8 — Add / delete flows wired via TanStack Query**: New hook `useAdditionalEmails()` in `web-frontend/src/hooks/useUserAccount/` exposing `add(email, label)` (POST) and `remove(email)` (DELETE). Both mutations invalidate the `['user', 'me']` query so the section refreshes. Success toast: `t('settings.account.additionalEmailAdded')` / `t('settings.account.additionalEmailRemoved')`. Validation errors surface inline on the form field (the email input uses `react-hook-form` + `zod` schema in line with the existing pattern in `web-frontend/src/components/registration/RegistrationWizard/`). 409 → "email already on someone's profile"; 422 → "limit reached"; 400 → "invalid email format".

9. **AC9 — i18n keys in all 10 locales**: Per the narrowed CLAUDE.md rule (frontend UI = all 10 locales; emails = de+en only), add the new keys to `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/userManagement.json`:
   - `settings.account.additionalEmailsTitle`
   - `settings.account.additionalEmailsHelp`
   - `settings.account.additionalEmailLabel` (placeholder for the label TextField)
   - `settings.account.additionalEmailAddButton`
   - `settings.account.additionalEmailAtLimit`
   - `settings.account.additionalEmailAdded` (toast)
   - `settings.account.additionalEmailRemoved` (toast)
   - `settings.account.additionalEmailErrorDuplicate`
   - `settings.account.additionalEmailErrorLimit`
   - `settings.account.additionalEmailErrorInvalid`
   - `settings.account.additionalEmailUnverifiedPill`
   - `settings.account.additionalEmailRemoveConfirm` (confirm-dialog body)
   - `settings.account.additionalEmailDeleteAriaLabel` (icon-button aria-label, `{{email}}` interpolation)
   - EN + DE are first-class hand-written copy; the other 8 locales may use a straight translation (existing convention).

10. **AC10 — Vitest unit tests**: New `UserSettingsTab.test.tsx` test cases (or extend the existing file): renders the section, shows existing additional emails from a stub user, calls `add` on submit, calls `remove` on icon click after confirmation, hides the form and shows the limit alert when 5 are already present. Mock `useAdditionalEmails` via `vi.mock`. Tests use namespace-stripped i18n assertions per `_bmad-output/project-context.md` testing rules.

11. **AC11 — Playwright E2E**: New spec `web-frontend/e2e/organizer/user-settings-additional-emails.spec.ts` (runs under the `chromium` (organizer) project): organizer logs in → opens settings page → adds an additional email → asserts row appears → reloads → row still present → deletes the row → row gone. Uses the existing `e2e/fixtures` auth pattern.

### Email Forwarder Lambda (Story 10.26 — consume the new contract)

12. **AC12 — `sender-auth.ts` recognises additional emails**: `getOrganizerEmails()` in `infrastructure/lambda/email-forwarder/sender-auth.ts` is updated so that for each user returned by `GET /api/v1/users?role=ORGANIZER&limit=100&page={n}`, BOTH the primary `email` AND every entry in `additionalEmails[*].email` are pushed (lowercased) into the returned list. The existing 5-minute TTL cache (`organizerCache`) covers the expanded set with no other behavioural change. **Reproducer test**: a Jest test sets up a stub user `nissim` with primary `nissim.buchs@elca.ch` and additional `info@berner-architekten-treffen.ch`, and asserts `isAuthorizedSender('ok@batbern.ch', 'info@berner-architekten-treffen.ch')` returns `true`. This is the literal failing case from 2026-05-20.

13. **AC13 — `address-resolver.ts` fans out to additional emails**: `fetchUsersByRole(role)` in `infrastructure/lambda/email-forwarder/address-resolver.ts` flattens primary + additional emails for each returned user, so `ok@`, `info@`, `events@`, `partner@`, and the role-based portion of `support@` fan-out delivers a copy to every additional email. The flattened list is deduplicated case-insensitively before sending (Lambda already does Set-based dedup in `index.ts:72-85`, so this is just a question of feeding it the larger set).

14. **AC14 — Lambda Jest test sweep**: Existing tests in `infrastructure/test/unit/email-forwarder.test.ts` continue to pass with `additionalEmails: []` stubs. New tests cover the flatten-and-deduplicate path on both the sender-auth side and the address-resolver side. The `truncateEmail` log redaction (utils.ts) is unchanged.

15. **AC15 — `batbern{N}@` is NOT affected**: The event-registrants resolver (`fetchEventRegistrants`) reads `attendeeEmail` from `/api/v1/events/{eventCode}/registrations`, NOT from `/users`, so it is **unchanged** by this story. Receiving event mail at additional emails is covered by AC16 (registration confirmation path), not by this forwarder branch. Document this explicitly in the Lambda module-level comment so a future reader does not "tidy up" symmetry.

### Event registration confirmation emails (close the loop for the "registrations" piece of the user's ask)

16. **AC16 — Registration confirmation CCs additional emails**: When `RegistrationEmailService.sendRegistrationConfirmation` runs (called from `EventController` resend path L1584 + initial-send path L1857) AND the registration belongs to a user with a populated `user_profiles` row (i.e. the attendee email matches a known user OR a `username` is set on the registration), the email is sent with the additional emails of that user as `Cc:` recipients. For anonymous registrants (no `user_profiles` link) the behaviour is unchanged. Waitlist promotion and deregistration confirmation emails follow the same rule.

17. **AC17 — EMS cross-service lookup uses the existing `UserApiClient`**: Resolving "additional emails for the user owning this registration" is a single call to `userApiClient.getUserByUsername(username)` (which now returns `additionalEmails[]` per AC4) inside `RegistrationEmailService`. NO direct JPA join across service boundaries (ADR-003). Failures of the lookup degrade gracefully: log a `WARN` and send to primary only.

### Sender-auth fail-open behaviour

18. **AC18 — Backwards-compatible API contract**: If the Lambda is deployed against a CUMS that has NOT yet shipped this story (e.g. during a rolling deploy), the `additionalEmails` field is missing/undefined on `UserResponse`. The Lambda treats `undefined` as an empty array. Existing organizers continue to be authorised by their primary email exactly as before — no regression in the existing Story 10.26 happy path.

### Operational visibility

19. **AC19 — Audit log on add / delete**: Adding or deleting an additional email writes a row to the existing `user_activity` history table (used by other profile-level changes — see how `UserService.updateProfile` populates it). Action types: `ADDITIONAL_EMAIL_ADDED`, `ADDITIONAL_EMAIL_REMOVED`. Description field includes the email value. This makes incidents like the 2026-05-20 one self-debuggable from the user's activity timeline.

### Testing & coverage

20. **AC20 — Backend tests**: Integration tests (`UserControllerIntegrationTest` extending `AbstractIntegrationTest`) cover: add success → 201; add invalid format → 400; add duplicate (vs primary AND vs another's additional AND vs same user's additional) → 409; add 6th → 422; delete success → 204; delete unknown → 404; the cap is enforced per-user (user A and user B can each hold 5); audit-log row written; GET /users/me + GET /users responses include the new field. PostgreSQL via Testcontainers (NEVER H2, per CLAUDE.md).

21. **AC21 — Bruno API contract tests**: Add `bruno-tests/users/add-additional-email.bru`, `bruno-tests/users/list-with-additional-emails.bru`, `bruno-tests/users/delete-additional-email.bru` exercising the happy paths and the 409 + 422 error cases. Runs via `./scripts/ci/run-bruno-tests.sh`.

22. **AC22 — Coverage**: Per project standard ≥ 80% line coverage on the new service code (`UserService.addAdditionalEmail`, `UserService.removeAdditionalEmail`), ≥ 90% on the validation paths. Lambda tests ≥ 90% on the changed functions.

---

## Tasks / Subtasks

### Phase 1 — Backend data model + API (TDD)

- [ ] **T1 — DB migration** (AC: #1, #2)
  - [ ] T1.1 — Write `V16__create_user_additional_emails.sql`: table, FK, indexes, unique constraint on `LOWER(email)` (use `CREATE UNIQUE INDEX … ON … (LOWER(email))`)
  - [ ] T1.2 — Add a `DEFERRABLE INITIALLY IMMEDIATE` trigger OR a service-level pre-insert check that rejects collisions with `user_profiles.email`. Recommend service-level (simpler, all paths funnel through `UserService.addAdditionalEmail`); document the decision inline.
  - [ ] T1.3 — Verify migration runs cleanly: `./gradlew :services:company-user-management-service:flywayMigrate` against a fresh local DB.

- [ ] **T2 — JPA entity + repository** (AC: #3)
  - [ ] T2.1 — Create `UserAdditionalEmail` entity in `domain/` package with `id`, `email`, `label`, `createdAt`, `verifiedAt`, and `@ManyToOne(fetch = LAZY) @JoinColumn(name = "user_id") private User user`.
  - [ ] T2.2 — Add `@OneToMany(mappedBy = "user", cascade = ALL, orphanRemoval = true, fetch = LAZY) @BatchSize(50)` collection to `User.java`.
  - [ ] T2.3 — `UserAdditionalEmailRepository` (Spring Data JPA) with `existsByEmailIgnoreCase(String email)` and `findByUserAndEmailIgnoreCase(User, String)`.
  - [ ] T2.4 — Update existing repository methods that fetch users for the Lambda (`findAllByRoleWithRoles` or equivalent) to add `LEFT JOIN FETCH u.additionalEmails` — avoids N+1 (CLAUDE.md JPA rule).

- [ ] **T3 — OpenAPI spec + DTO regen** (AC: #4, #6)
  - [ ] T3.1 — Update `docs/api/users-api.openapi.yml`: new `AdditionalEmail` schema, extend `UserResponse`, add the two new endpoints (POST, DELETE) under `/users/me/additional-emails`.
  - [ ] T3.2 — Run `./gradlew :services:company-user-management-service:openApiGenerateUsers` — verify clean compile.
  - [ ] T3.3 — Run `cd web-frontend && npm run generate:api-types:users` — commit `src/types/generated/users-api.types.ts`.

- [ ] **T4 — Service + controller** (AC: #5, #19, #20)
  - [ ] T4.1 — Write `UserControllerIntegrationTest` cases FIRST (RED): add → 201, list reflects, delete → 204, format → 400, duplicate vs primary → 409, duplicate vs same-user additional → 409, duplicate vs other-user → 409, 6th → 422, delete unknown → 404, audit-log row exists.
  - [ ] T4.2 — Implement `UserService.addAdditionalEmail(String username, String email, String label)` — normalize email to lowercase, run uniqueness check, enforce cap from `@Value`, persist, write `ActivityHistoryEntity` row (`ADDITIONAL_EMAIL_ADDED`), return new `AdditionalEmail` DTO.
  - [ ] T4.3 — Implement `UserService.removeAdditionalEmail(String username, String email)` — look up, delete, write activity row (`ADDITIONAL_EMAIL_REMOVED`).
  - [ ] T4.4 — Wire endpoints into `UserController` (`@PostMapping("/me/additional-emails")` + `@DeleteMapping("/me/additional-emails/{email}")`) — both require an authenticated principal; both resolve "me" via the existing `SecurityContextHelper.getCurrentUsername()` (see Pattern 3b + its username twin in memory).
  - [ ] T4.5 — Tests GREEN.

- [ ] **T5 — UserResponse + listUsers mapper update** (AC: #4)
  - [ ] T5.1 — `UserMapper` enriches `UserResponse.additionalEmails` from the entity's collection.
  - [ ] T5.2 — `GET /users` paginated list returns the populated field — extend test `UserControllerIntegrationTest.should_returnAdditionalEmails_when_listUsersWithRole_called()`.

- [ ] **T6 — Bruno tests** (AC: #21)
  - [ ] T6.1 — Add the three `.bru` files; verify they pass against a running local stack.

### Phase 2 — Frontend user-settings UI (TDD)

- [ ] **T7 — Service layer + hook** (AC: #8)
  - [ ] T7.1 — Extend `userService.ts` (or `userAccountService.ts` — whichever already houses `updateProfile`) with `addAdditionalEmail` and `removeAdditionalEmail`.
  - [ ] T7.2 — New hook `useAdditionalEmails()` under `web-frontend/src/hooks/useUserAccount/` — wraps the service methods in `useMutation`, invalidates `['user', 'me']` on success.
  - [ ] T7.3 — Vitest tests for the hook (success path, 409 surfaces as a typed error, 422 surfaces with `errorCode`).

- [ ] **T8 — UserSettingsTab UI section** (AC: #7, #9, #10)
  - [ ] T8.1 — Write `UserSettingsTab.test.tsx` cases FIRST (RED): section renders, list shows existing emails, add submits, delete confirms then submits, limit alert appears at cap, error messages render.
  - [ ] T8.2 — Implement the section. Reuse the existing react-hook-form + zod pattern from the registration wizard for the inline form. Use the existing MUI Paper/Box patterns from the surrounding sub-tab so the section visually fits.
  - [ ] T8.3 — Add the i18n keys to en + de userManagement.json (first-class quality).
  - [ ] T8.4 — Add the same keys to the other 8 locales — straight translation acceptable per CLAUDE.md; existing locale-sync tooling (see `web-frontend/scripts/i18n/` from Story 10.9) detects missing keys.
  - [ ] T8.5 — Tests GREEN.

- [ ] **T9 — Playwright E2E** (AC: #11)
  - [ ] T9.1 — `e2e/organizer/user-settings-additional-emails.spec.ts` — full add → reload → delete cycle. Cleanup step deletes any leftover row at the end (so the test is idempotent against re-runs in shared staging).

### Phase 3 — Email forwarder Lambda (TDD)

- [ ] **T10 — Sender-auth recognises additional emails** (AC: #12, #18)
  - [ ] T10.1 — Write Jest test FIRST (RED) in `infrastructure/test/unit/email-forwarder.test.ts`: stub `/api/v1/users?role=ORGANIZER` response to return a user with `additionalEmails: [{ email: 'info@berner-architekten-treffen.ch' }]`. Assert `isAuthorizedSender('ok@batbern.ch', 'info@berner-architekten-treffen.ch')` is `true`. **This is the 2026-05-20 regression test.**
  - [ ] T10.2 — Test: `additionalEmails` undefined (old API) → still authorised by primary only.
  - [ ] T10.3 — Implement: flatten `(u.email + u.additionalEmails?.map(a => a.email) ?? []).map(lowercase)` in `getOrganizerEmails()`.
  - [ ] T10.4 — Tests GREEN.

- [ ] **T11 — Address-resolver fans out** (AC: #13, #14, #15)
  - [ ] T11.1 — Jest test FIRST (RED): two organizers, one with two additional emails → recipient list contains 4 unique addresses (2 primary + 2 additional).
  - [ ] T11.2 — Test: case-insensitive dedup if a primary equals another user's additional (shouldn't happen given AC2's global unique constraint, but defensive).
  - [ ] T11.3 — Implement the same flatten in `fetchUsersByRole()`.
  - [ ] T11.4 — Add a comment to the top of `address-resolver.ts` noting that `fetchEventRegistrants` does NOT participate in additional-email fan-out (AC15).
  - [ ] T11.5 — Tests GREEN.

### Phase 4 — Event registration confirmation CC (TDD)

- [ ] **T12 — RegistrationEmailService CCs additional emails** (AC: #16, #17)
  - [ ] T12.1 — Integration test FIRST (RED) in EMS: registration belongs to a known user → `MimeMessage.getRecipients(CC)` contains the additional emails.
  - [ ] T12.2 — Test: anonymous registration (no linked user) → no CC, no `UserApiClient` call.
  - [ ] T12.3 — Test: `UserApiClient.getUserByUsername` throws → send proceeds to primary only, WARN logged.
  - [ ] T12.4 — Implement in `RegistrationEmailService.sendRegistrationConfirmation` — fetch the user via `UserApiClient` (only if `registration.username` is set), pass `additionalEmails` into the SES `SendEmailRequest.destination.ccAddresses`.
  - [ ] T12.5 — Apply the same pattern to the waitlist-promotion and deregistration-confirmation email paths (search for other callers of the email-templates rendering — see Story 10.13 era code).
  - [ ] T12.6 — Tests GREEN.

### Phase 5 — Manual staging smoke test (post-deploy)

- [ ] **T13 — Manual E2E** (AC: #12, #16)
  - [ ] T13.1 — As an organizer on staging, add `info@berner-architekten-treffen.ch` as an additional email. Confirm row appears in settings.
  - [ ] T13.2 — From that mailbox, send a test mail to `ok@batbern.ch`. Verify CloudWatch log line `Forwarded email { ..., outcome: 'forwarded' }` (NOT `Unauthorized sender`). Verify all organizers receive the forwarded copy, including the sender at both addresses.
  - [ ] T13.3 — Register an attendee account (or use a test account) with one additional email. Register for an upcoming event. Verify the confirmation email arrives at both addresses.
  - [ ] T13.4 — Document the procedure in this story's Dev Agent Record on completion.

---

## Dev Notes

### Files touched (UPDATE vs NEW)

**NEW:**
- `services/company-user-management-service/src/main/resources/db/migration/V16__create_user_additional_emails.sql`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/UserAdditionalEmail.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/repository/UserAdditionalEmailRepository.java`
- `web-frontend/src/hooks/useUserAccount/useAdditionalEmails.ts` + `.test.ts`
- `bruno-tests/users/add-additional-email.bru`
- `bruno-tests/users/list-with-additional-emails.bru`
- `bruno-tests/users/delete-additional-email.bru`
- `web-frontend/e2e/organizer/user-settings-additional-emails.spec.ts`

**UPDATE — read the current state before editing (per workflow Step 3 mandate):**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/User.java` — add `additionalEmails` collection. Preserve all existing fields, indexes, `@PrePersist`, business methods.
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java` — add 2 endpoints (POST, DELETE). Preserve all existing 24 endpoints listed at lines 79–847.
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` — add 2 methods + reuse existing audit-log writer.
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/mapper/UserMapper.java` — enrich `UserResponse` with `additionalEmails`.
- `docs/api/users-api.openapi.yml` — extend `UserResponse` + add 2 paths.
- `web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx` — extend Account sub-tab (tab index 0). Preserve the existing newsletter sub-section, password change, theme/timezone, and Notification + Privacy sub-tabs intact.
- `web-frontend/src/services/userService.ts` (or wherever `updateProfile` lives — confirm via grep) — add 2 method wrappers.
- `infrastructure/lambda/email-forwarder/sender-auth.ts` — flatten in `getOrganizerEmails`.
- `infrastructure/lambda/email-forwarder/address-resolver.ts` — flatten in `fetchUsersByRole`.
- `services/event-management-service/.../service/RegistrationEmailService.java` — CC additional emails.
- `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/userManagement.json` — 13 new keys × 10 locales.

### Critical patterns to follow

- **ADR-003 cross-service IDs**: The Lambda already calls `/api/v1/users` and uses meaningful emails — no UUIDs cross service boundaries. This story keeps that pattern; do NOT add a UUID-keyed lookup.
- **ADR-004 entity design**: `UserAdditionalEmail` is a child entity of `User` *within CUMS only*. No other service stores its own copy. Other services (EMS for registration CC) call `UserApiClient.getUserByUsername(username)` (HTTP, cached via Caffeine) to read the additional list.
- **CLAUDE.md JPA rules**: `@OneToMany` defaults to LAZY — keep it LAZY. Use `@BatchSize(50)`. The repository method that the forwarder Lambda's `/api/v1/users?role=…` endpoint runs against MUST do `LEFT JOIN FETCH u.additionalEmails` to avoid N+1.
- **Empty-claim DB fallback (Pattern 3b + its username twin, Epic 11.E.7)**: The new endpoints rely on `SecurityContextHelper.getCurrentUsername()` and `JwtRolesConverter`. Both already handle empty Cognito claims by falling back to the DB. Don't reinvent.
- **i18n key reuse**: Before adding a new key (AC9), grep `userManagement.json` for any similar key (`addEmail`, `emailLabel`, etc.) and reuse if char-for-char identical. Avoid duplicate keys (Story 10.9 cleanup discipline).
- **Test resilience**: Component tests assert namespace-stripped keys (e.g. `'settings.account.additionalEmailAdded'`), not literal translated values.
- **OpenAPI-first**: Update the YAML BEFORE writing the controller — Spring Boot 3 interface is regenerated from it.

### Things the dev agent must NOT do

- Do **NOT** change `user_profiles.email` semantics, the Cognito `email` claim, or the login identifier.
- Do **NOT** add a "verified" / "send verification email" flow — explicitly out of scope (Open Question #1).
- Do **NOT** auto-subscribe additional emails to the newsletter (Open Question #4). Newsletter remains email-keyed and independent of this story.
- Do **NOT** wire `fetchEventRegistrants` in `address-resolver.ts` to read additional emails (AC15) — that path stays attendee-email-driven.
- Do **NOT** expose admin-managed endpoints for editing *another user's* additional emails (out of scope).
- Do **NOT** use `--no-verify` to bypass pre-commit hooks.

### References

- 2026-05-20 forwarder log evidence: CloudWatch `/aws/lambda/batbern-email-forwarder-staging` entry `2026-05-20T06:28:21.217Z … Unauthorized sender for address { to: 'ok@batbern.ch', sender: 'info@***' }`
- S3 object for the rejected mail: `s3://batbern-inbound-emails-staging/forwarding/j608mgmovctqgh5l4ajrmd7vaep11mo21o49t801`
- Story 10.26 forwarder ([Source: `_bmad-output/implementation-artifacts/10-26-ses-email-forwarding-distribution-lists.md`])
- Sender auth implementation: [`infrastructure/lambda/email-forwarder/sender-auth.ts`]
- Address resolver implementation: [`infrastructure/lambda/email-forwarder/address-resolver.ts`]
- Forwarder Lambda main: [`infrastructure/lambda/email-forwarder/index.ts`]
- User aggregate root: [`services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/User.java`]
- User-settings page surface: [`web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx`]
- Users API spec: [`docs/api/users-api.openapi.yml`]
- ADR-003: cross-service identifiers ([Source: `docs/architecture/03-data-architecture.md`])
- ADR-004: entity design ([Source: `docs/architecture/03-data-architecture.md`])
- ADR-006: OpenAPI contract-first ([Source: `docs/architecture/04-api-design.md`])
- CLAUDE.md §"Localization — Email Templates: DE + EN Only; UI i18n: All 10 Locales"

---

## Dev Agent Record

### Agent Model Used

_(filled in by dev agent on implementation)_

### Debug Log References

### Completion Notes List

### File List

---

## Open Questions (resolved 2026-05-22 by PM Nissim)

All six items below were resolved at story-drafting time. The dev agent should treat every decision as binding scope unless a follow-up note in this section says otherwise.

1. **Ownership verification of additional emails — RESOLVED: not in v1.** Any logged-in user can claim any email address as an "additional email" on their own profile, with no proof of ownership. This matches the trust model BATbern already uses for other profile fields. The `verified_at` column stays in the schema (AC1) so a v2 verification flow can be bolted on without a migration, but no verification email is sent and the unverified pill is purely informational in v1.

2. **Schema shape — RESOLVED: separate `user_additional_emails` table.** Not JSONB on `user_profiles`, not `@ElementCollection`. Matches how `role_assignments` is already modelled, keeps the global uniqueness constraint (AC2) cheap to enforce with a `CREATE UNIQUE INDEX … ON … (LOWER(email))`, and leaves room for the future `verified_at` and `label` fields to grow per-row metadata cleanly.

3. **Per-user cap of additional emails — RESOLVED: 5.** Enforced server-side (AC5) and configurable via `batbern.user.additional-emails.max`. The default ships at 5. Reaching the cap surfaces an info `<Alert>` in the UI in place of the add form (AC7), and the API returns HTTP 422 with `errorCode: ADDITIONAL_EMAIL_LIMIT_REACHED`.

4. **Newsletter routing to additional emails — RESOLVED: out of scope.** Newsletter subscription stays keyed by email address (not by user), exactly as Stories 10.7 / 10.28 / 10.29 left it. Users who want newsletter copies at an additional address subscribe that address separately via the existing public widget. The UI helper text under "Additional emails" should call this out explicitly so users do not assume newsletter follows automatically — exact copy: "Newsletter copies go to your primary email only — subscribe each address separately if needed."

5. **`additionalEmails` visibility on `GET /users` — RESOLVED: include the field for every caller who can already see the user.** No extra DTO-level gating. The Lambda needs the flattened list to authorise senders and fan out forwards, and the existing organizer-only protection on the external `/users` endpoint is sufficient for everyone else. Internal VPC-only `permitAll` access (as Story 10.26 wired) continues to work.

6. **Audit-log granularity — RESOLVED: include the email value.** `ActivityHistoryEntity` rows for `ADDITIONAL_EMAIL_ADDED` and `ADDITIONAL_EMAIL_REMOVED` include the actual email in the description (AC19). This makes incidents like 2026-05-20 self-debuggable from the user's activity timeline. The email belongs to the user whose timeline it appears on, so GDPR-wise this is fine; no additional masking needed.

---

_Story created via `bmad-create-story` skill on 2026-05-22 by Amelia. All six Open Questions resolved by PM Nissim same day. Ready for `bmad-dev-story` execution._
