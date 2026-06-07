# Story 11.E.4: Epic 11 trailing cleanup — sweep residue from Phases A–E

Status: done

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As a** developer maintaining the speaker workflow after Epic 11,
**I want** the small bits of residue that earlier code reviews deferred between stories — but that later stories then never picked up — to land in one bundle,
**So that** Epic 11 reads as actually finished when Story 11.F.1 (magic-link teardown) ships and there is no slow drip of "phase-B residue" / "deferred to 11.C.2" / "11.E.2 to decide" cleanup tickets following the refactor into the next quarter.

**Phase:** E (trailing cleanup) — lands after 11.E.1/E.2/E.3, before 11.F.1.
**Dependencies:** None — all source stories (11.A.1, 11.B.*, 11.C.*, 11.D.*, 11.E.1–E.3) are `Status: done`. Can land in any order relative to 11.F.1.
**Scope:** Seven specific items deferred in `_bmad-output/implementation-artifacts/deferred-work.md` that fall *outside* 11.F.1's magic-link-teardown territory. All other deferred-work items are either (a) explicitly scoped to 11.F.1, (b) explicitly accepted as out-of-scope by PM decisions during Phase B–E reviews, or (c) broader cross-cutting concerns (outbox pattern, retry queues, Cognito federation) that this cleanup story will not introduce.

**Out of scope (DO NOT touch):**
- Magic-link teardown — `MagicLinkService.java`, `JwtConfig.java` (speaker variant), `SpeakerMagicLoginController.java`, `SpeakerPortalTokenController.java`, `magic_link_tokens` table, `speaker_jwt` cookie, `?token=` / `?jwt=` query handling, `SpeakerMagicLoginPage.tsx`, "Mark as tentative" UI, Secrets Manager wiring, branch deletion → owned by 11.F.1.
- 5 `@Disabled` legacy SpeakerPortal IntegrationTest classes (`SpeakerPortalResponse/Content/Dashboard/Materials/TokenControllerIntegrationTest`) + 4 `describe.skip` frontend Vitest suites (`ContentSubmissionPage`, `InvitationResponsePage`, `ProfileUpdatePage`, `SpeakerMagicLoginPage`) + `speakerPortalService.test.ts` → owned by 11.F.1.
- 3 `test.fixme` Playwright specs (`e2e/speaker/speaker-portal-respond.spec.ts`, `speaker-portal-content-submit.spec.ts`, `speaker-portal-cross-portal-nav.spec.ts`) → wait on staging test-speaker seed (11.F.1 / Cognito staging seed).
- AFTER_COMMIT design for `runInvitedHook` / `runAcceptedHook` external side effects → explicitly bundled with 11.F.1 per `deferred-work.md`.
- Cognito orphan-user risk on JPA commit failure, `@Async` email-failure speaker lockout, `EXTERNAL_PROVIDER` Cognito status mapping, `username` vs `preferred_username` drift on email change, `RestTemplate` DEBUG body logging audit → broader scope, not Epic-11 cleanup.
- Slot-capacity TOCTOU race → PM Resolved Decision #6 (race window matches current behaviour).
- Native-speaker review of fr/it/rm/es/fi/nl/ja/gsw-BE translations → cosmetic, non-blocking.

---

## Acceptance Criteria

ACs are pinned to the specific file paths and origin code-review notes in `deferred-work.md` (the cross-reference under each AC is the origin entry). Each AC names the post-change shape and the regression guard.

### AC1 — `SpeakerWorkflowState` frontend union drops legacy members

**Given** `web-frontend/src/types/speakerPool.types.ts` is opened,
**When** I read the `SpeakerWorkflowState` type alias at the top of the file (currently lines 11–16),
**Then** the union has exactly the 8 ADR-009 §0.1 states — derived from the OpenAPI-generated type plus the `'INVITED'` widening already in place — with no `'SLOT_ASSIGNED'`, `'WITHDREW'`, or `'OVERFLOW'` members,
**And** any inline comment that previously rationalised the legacy widening is removed (replace with a short reference to ADR-009 §0.1 if a comment is useful).

**Given** the frontend type-check runs (`cd web-frontend && npm run type-check`),
**When** the check completes,
**Then** zero errors are reported across the project,
**And** in particular `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` (which previously had a comment "legacy union members `SLOT_ASSIGNED`, `WITHDREW`, `OVERFLOW` are NOT valid lanes" at ~lines 144–149) compiles cleanly with the narrowed union — the comment may be removed if no longer informative.

**Given** the dev does a project-wide grep for the three removed string literals (`grep -rn "'SLOT_ASSIGNED'\\|'WITHDREW'\\|'OVERFLOW'" web-frontend/src/`),
**Then** only documentation-style references survive (story files, comments explicitly naming the legacy values for historical context),
**And** zero live code paths reference the removed members.

> **Origin:** `deferred-work.md` § "Deferred from: code review of 11-d-4" — `WITHDREW / OVERFLOW references survive in speakerPool.types.ts:15-16. Phase B residue from Story 11.B.1 (enum reduction); not introduced by 11.D.4`. Plus `deferred-work.md` § "11-d-2" — `STATUS_LANES does not catch-all legacy union members SLOT_ASSIGNED, WITHDREW, OVERFLOW. Bundle with the wider SpeakerWorkflowState union cleanup planned after Phase D`.

---

### AC2 — `UserApiClient` (EMS) drops three dead methods

**Given** `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java` is opened,
**When** I read the interface declarations,
**Then** the methods `getSpeakerUsernames()` (currently line 126), `updateUser(...)`, and `updateUserProfilePicture(...)` are absent,
**And** the orphan comment "Replaces the legacy updateUser/updateUserProfilePicture methods from Story 6.2b" at ~line 139 is removed,
**And** the interface retains exactly the post-Epic-11 public surface: `provisionUserWithRole`, `patchUserProfile`, `issueInvitationCredentials`, `getUserByUsername`, and any other methods that have a live call site in EMS.

**Given** `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java` is opened,
**When** I search for the implementations of the removed methods,
**Then** `getSpeakerUsernames()` (currently line 790), `updateUser(...)`, and `updateUserProfilePicture(...)` implementations are deleted,
**And** any helper methods used only by the removed implementations are also deleted (use IDE "Find Usages" or `grep` to verify no other callers).

**Given** the dev runs `grep -rn "getSpeakerUsernames\|updateUserProfilePicture\|\.updateUser(" services/ web-frontend/src/`,
**Then** only test files asserting the methods are gone (if any added in this story) match — zero production-code callers remain in any service or in the frontend,
**And** EMS compiles: `./gradlew :services:event-management-service:compileJava` is green.

**Given** the EMS test suite runs (`./gradlew :services:event-management-service:test`),
**Then** all tests pass — any test that referenced the removed methods is also deleted (the deferred-work entry confirms "no live callers remain in EMS"; same logic applies to tests written against the dead interface).

> **Origin:** `deferred-work.md` § "11-c-1" — `UserApiClient retains three dead methods (updateUser, updateUserProfilePicture, getSpeakerUsernames). Explicitly deferred to Story 11.C.2 which rewrites UserApiClient with patchUserProfile. No live callers remain in EMS`. 11.C.2 done without removing them; this AC closes the loop.

---

### AC3 — Stale TENTATIVE Bruno test + sibling stale-state references swept

**Given** `bruno-tests/speaker-portal-api/14-respond-tentative.bru` exists (it currently asserts `res.status: eq 200` and `res.body.success: eq true` for a POST with `"response": "TENTATIVE"`),
**When** the cleanup runs,
**Then** the file is **deleted** (Epic 11 FR5 removed TENTATIVE from the API contract; the negative test for "any invalid enum value returns 400" is already covered by other Bruno tests in `bruno-tests/speaker-portal-api/`),
**And** if any neighbouring `.bru` file references `tentativeSpeakerToken` (the Bruno environment variable referenced by line 15 of the deleted file), the reference is also cleaned up so the Bruno collection has no dangling variable.

**Given** the dev does a sweep of `bruno-tests/**/*.bru` for references to removed states or response types — `grep -rn "TENTATIVE\|WITHDREW\|OVERFLOW\|SLOT_ASSIGNED\|CONFIRMED" bruno-tests/`,
**When** the sweep returns hits,
**Then** for each hit the dev classifies it as one of:
  - **Correct negative test** (asserts the API REJECTS the legacy value with 4xx) — keep, optionally tighten the assertion to the specific expected status + body shape.
  - **Stale positive test** (asserts the API ACCEPTS the legacy value) — delete or invert to a negative test.
  - **Stale narrative comment** in an otherwise-correct test — update the comment to reflect ADR-009 §0.1.

**Given** the Bruno suite runs after cleanup (`./scripts/ci/run-bruno-tests.sh` — note this requires staging auth tokens, so the dev may need to capture the test results after a CI run or run a focused subset locally with `~/.batbern/staging-organizer.json` populated),
**Then** zero tests fail due to legacy-state references,
**And** if staging-auth setup blocks a local run, the dev confirms via static review (`grep -L` for the legacy strings) that no positive-assertion test remains.

> **Origin:** `deferred-work.md` § "11-c-1" — `19 Bruno test failures across users-api / events-api / speaker-portal-api. Pre-existing 11.B.* carryover: tests reference removed TENTATIVE state, removed ACCEPTED → IDENTIFIED transitions, or send-invitation endpoints whose validation tightened in 11.B.2/B.3. Phase B owners should sweep the suite.`

---

### AC4 — Promote endpoint requires `firstName` + `lastName`; fallback methods deleted

**PM decision recorded 2026-05-18 by Nissim:** Option A from the four options offered during story drafting. The `firstNameFallback` / `lastNameFallback` literals `"Speaker"` / `"Unknown"` (currently in `SpeakerWorkflowService.java:507–523`) are removed; the promote-endpoint contract is tightened so the upstream guarantees non-blank values.

**Given** `services/event-management-service/src/main/java/ch/batbern/events/dto/PromoteSpeakerRequest.java` is opened,
**When** I read the record fields,
**Then** both `firstName` and `lastName` carry `@NotBlank` in addition to their existing `@Size(max = 100)` (and any other existing annotations),
**And** the Javadoc above the record explicitly states these were tightened from optional to required per Epic-11 trailing cleanup (Story 11.E.4) — reason: Cognito attributes `given_name` / `family_name` are populated from these fields and need real values, not placeholders,
**And** the field-order `(email, firstName, lastName)` is preserved (constructor signature stable for already-existing JSON callers).

**Given** `docs/api/events-api.openapi.yml` is opened and I locate the `PromoteSpeakerRequest` schema,
**When** I read the `required:` block,
**Then** `firstName` and `lastName` are both listed alongside `email` (previously only `email` was required),
**And** the OpenAPI description for each field is updated to note that the value populates the Cognito user's `given_name` / `family_name` attribute on the `CONTACTED → READY` transition.

**Given** OpenAPI regeneration runs (`./gradlew :services:event-management-service:openApiGenerateEvents` for backend DTOs; `cd web-frontend && npm run generate:api-types:events` for frontend types),
**Then** both regenerations succeed,
**And** the regenerated frontend types in `web-frontend/src/types/generated/events-api.types.ts` reflect the new `required: [email, firstName, lastName]` shape,
**And** the regenerated types are committed to git per CLAUDE.md "After any OpenAPI spec change, run `npm run generate:api-types` and commit the result".

**Given** `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` is opened,
**When** I read the body,
**Then** the `firstNameFallback(SpeakerPool)` and `lastNameFallback(SpeakerPool)` private methods (currently lines 507–523) are **deleted**,
**And** the call site in `runReadyHook` (currently lines 301–304) is simplified to:
```java
provisionRequest.setFirstName(payload.firstName());
provisionRequest.setLastName(payload.lastName());
```
**And** the `TransitionPayload` builder usage at any call site that constructs a `CONTACTED → READY` transition is reviewed — `firstName` / `lastName` keys are now contractually present on the promote payload, so the workflow service can rely on non-blank values when the payload's source is the promote endpoint. (Note: the workflow service may still receive `firstName`/`lastName` as null from other entry paths if any exist — verify via grep whether any other code constructs a `CONTACTED → READY` `TransitionPayload`; if so, those entry paths must also guarantee non-blank values OR throw an explicit `ValidationException` instead of falling back to placeholders.)

**Given** `web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx` (or whichever file hosts the modal — verify via `grep -rn "PromoteSpeakerDialog\|promote-speaker\|promoteSpeaker" web-frontend/src/components/`) is opened,
**When** I read the form definition,
**Then** the `firstName` and `lastName` form fields have a `required` validation rule (Zod schema `.min(1, t('errors.required'))` or equivalent — match the existing project convention for required-field validation in this file),
**And** the submit button is disabled until both fields contain at least one non-whitespace character (in addition to the existing email-format check),
**And** the field labels are updated if needed so the asterisk / "required" indicator follows the existing project pattern (e.g., MUI `<TextField required>` or Zod-driven helper text — match what other required-field forms in this file use).

**Given** the i18n keys for the new required-field error messages are added,
**When** I look at `web-frontend/public/locales/{locale}/`,
**Then** any new key (e.g., `promoteSpeaker.firstNameRequired`, `promoteSpeaker.lastNameRequired` — or whatever fits the existing key namespace for this dialog) is present in **all 10 locales** per CLAUDE.md §Localization "Frontend UI i18n — all 10 locales required". EN + DE are first-class quality; the 8 other locales (`fr`, `it`, `rm`, `es`, `fi`, `nl`, `ja`, `gsw-BE`) may use straight machine translations and be hand-polished later. **Reuse existing keys** if any `errors.required` or `common.fieldRequired` key already exists in the relevant namespace.

**Given** new Java unit + integration tests are added in `services/event-management-service/src/test/java/ch/batbern/events/`,
**When** I run `./gradlew :services:event-management-service:test`,
**Then** the tests cover:
  - **Required-field rejection at controller layer**: `PromoteSpeakerControllerIntegrationTest` (or equivalent — find the existing integration test for the promote endpoint) asserts that `POST /api/v1/events/{code}/speakers/{speakerId}/promote` returns **400 Bad Request** when `firstName` is missing/blank, when `lastName` is missing/blank, and when both are missing/blank. Response body identifies the offending field(s) per the standard `GlobalExceptionHandler` 400 contract.
  - **Service-layer happy path** (`SpeakerWorkflowServiceTest`): `runReadyHook` is invoked with `payload.firstName() = "John"` + `payload.lastName() = "Doe"` and the constructed `ProvisionUserRequest` carries those exact values (mock `userApiClient.provisionUserWithRole` and capture the request argument with Mockito `ArgumentCaptor`). No fallback paths exercised.
  - **Identity-rebind reject path** — `SpeakerWorkflowServiceTest` adds a test where `provisionUserWithRole` returns a username **different** from the speaker's currently-bound username. The test asserts a `ValidationException` is thrown with the documented message naming both usernames. (This was item 4 of the 11.D.1 deferred-work, easy to land here while we're already in `SpeakerWorkflowServiceTest`.)

**Given** frontend tests are added in `web-frontend/src/components/SpeakerBrainstormingPanel/__tests__/PromoteSpeakerDialog.test.tsx` (or wherever the dialog's existing tests live — match the existing file naming convention),
**When** I run `cd web-frontend && npm run test`,
**Then** the tests cover:
  - Submit button disabled state when `firstName` is empty, when `lastName` is empty, and when both are empty (each its own `it()` block).
  - Required-field error helper text is rendered with the expected i18n key when the user blurs an empty field (use `screen.getByText` against the EN translation per project testing convention; do NOT lock in a specific non-EN translation per CLAUDE.md §Localization testing rule).
  - Happy-path submit: when both fields are populated alongside a valid email, the form fires the mutation with `{ email, firstName, lastName }` exactly.

> **Origin:** `deferred-work.md` § "11-b-2" — `firstNameFallback / lastNameFallback literals "Speaker" / "Unknown". When payload omits name and speakerName is blank, the new User has firstName=Speaker, lastName=Unknown. Becomes Cognito user attributes in Phase E (Story 11.E.2 owns the welcome-email rendering and should decide whether to (a) reject the transition at the seam, (b) require name on TransitionPayload for CONTACTED → READY, or (c) accept the placeholder and let the welcome email render around it)`. **PM decision: (b)/(a) hybrid — require at API contract (rejects at the seam before reaching the service) and delete the fallback methods.** Plus the related `deferred-work.md` § "11-d-1" entries about `lastNameFallback` returning empty for single-word names and the identity-rebind guard reject path.

---

### AC5 — 38 stale `ADR-009 §0.x` citations resolved

**Two paths are possible; the dev must pick one and apply it consistently across all hits.** PM preference (recorded in story drafting): path (a) — restructure ADR-009 to add a `## §0 Target Model` section with numbered subsections that match the existing citations. Rationale: the `§0` convention has propagated to many docs (architecture, OpenAPI specs, PRD, ADR-004) and serves as a useful disambiguator between *target-model facts* and *decision rationale*. Sweep-replacing 38 citations is more brittle than landing one section in ADR-009.

**Given** `docs/architecture/ADR-009-unified-speaker-workflow.md` is opened,
**When** I read the structure,
**Then** a new top-level section `## §0 Target Model` is inserted after `# Title` / `# Status` / `# Date` (the conventional ADR header block) and **before** the existing `## Context` section,
**And** the new section has numbered subsections matching the existing citations in the rest of the codebase — at minimum:
  - `### §0.1 — The 8 states` (table of state name + Javadoc-equivalent description, sourced from 11.B.1 AC1 + plan §0.1)
  - `### §0.2 — Legal transitions` (the state-machine allow-list, sourced from 11.B.2 AC1 + plan §0.2)
  - `### §0.3 — Dropped columns and tables` (`speakers` table, legacy speaker-only columns, `speaker_pool.is_tentative`, `tentative_reason`, `speaker_selection_votes`, `magic_link_tokens`; sourced from plan §0.3 + 11.B.3 + 11.C.1 + 11.F.1)
  - `### §0.4 — Shared write path` (`ContentSubmissionService` as the single backend for organizer-on-behalf and speaker-self submission; sourced from plan §0.4 + 11.C.2)
  - `### §0.5 — Derived flags` (`is_slot_assigned` and `is_publishable` semantics; sourced from plan §0.5 + 11.B.3)
  - `### §0.6 — Removed response types` (`TENTATIVE` gone from `SpeakerResponseType`; sourced from plan §0.6 + 11.B.1)
  - `### §0.7 — Removed enum values` (`SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`, `WITHDREW`, with migration mapping to the 8-state model; sourced from plan §0.7 + 11.B.1 + 11.B.3)

**Given** each new subsection's content is the canonical target-model fact (not a re-derivation),
**Then** content is lifted from the linked source (`docs/plans/speaker-workflow-refactor.md` §0.x and the relevant stories' AC blocks),
**And** content is succinct — bullet lists and short tables; the goal is a stable target for citations, not a tutorial,
**And** each subsection ends with a one-line back-reference to the implementing story (e.g., `Implemented by Story 11.B.1`).

**Given** the dev runs `grep -rEn "ADR-009 §0\\.[0-9]" docs/ services/ web-frontend/`,
**Then** every match points to a real subsection that now exists in ADR-009,
**And** if any match references a `§0.x` number that wasn't included in the seven subsections above, EITHER the subsection is added to ADR-009 OR the offending citation is sweep-replaced with the correct reference (`ADR-009 Decision N` or a renamed `§0.y`).

**Given** the dev updates the ADR-009 revision history block (existing `## Revision History` section),
**Then** a new row is added: `vX.Y | 2026-05-18 | Added §0 Target Model section with seven subsections (§0.1–§0.7) to support cross-doc citations established in Stories 11.A.1 through 11.E.3 | <user_name>`.

**Given** `docs/architecture/index.md` is opened,
**When** I look at the entry for ADR-009,
**Then** the entry's one-line description mentions the §0 target-model section if the existing entries follow that pattern (otherwise no change needed),
**And** if any of the 10 source files listed in the origin (below) has a `## See also` or `## References` block that should now point to a specific subsection, the reference is tightened (e.g., a doc that broadly cited "see ADR-009" can now cite `ADR-009 §0.1` for state-list questions specifically).

> **Origin:** `deferred-work.md` § "11-a-1" — `26 ADR-009 §0.x citations point to nonexistent sections. ADR-009 has no §0 numbered subsections (only Decisions 1, 2, 3). The story's AC text introduced the §0.1/§0.2/§0.3/§0.5/§0.7 convention to disambiguate target-model topics (states/auth/etc.), and the dev consistently used these placeholders across 26 hits. Either (a) restructure ADR-009 to add a §0 Target Model section with numbered subsections matching the citations, or (b) sweep-replace each citation with actual ADR-009 section references. Follow-up story under Phase A cleanup or as part of Phase B doc tidy.` Live count at audit time: 38 citations across 10 files (`04-api-speaker-coordination.md` 10, `06a-workflow-state-machines.md` 5, `epic-11-speaker-workflow-refactor.md` 5, `speakers-api.openapi.yml` 5, `ADR-004-factor-user-fields-from-domain-entities.md` 2, `events-api.openapi.yml` 2, `03-data-architecture.md` 1, `06b-user-lifecycle-sync.md` 1, `prd-enhanced.md` 1, `implementation-readiness-report-2026-05-15.md` 1).

---

### AC6 — `SpeakerStatusDashboard` either correctly wires callbacks or is deleted

**Given** the dev runs `grep -rn "SpeakerStatusDashboard" web-frontend/src/ | grep -v "__tests__\\|\\.test\\."`,
**When** the grep returns results,
**Then** the dev classifies the state:
  - **No live caller in production routes / no caller in any non-test file** → **delete** the component and its test file (`web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusDashboard.tsx` + `__tests__/SpeakerStatusDashboard.test.tsx` if present), and update any `index.ts` barrel that re-exports it.
  - **Live caller exists** → **wire** the missing callbacks. The component must thread `onSendInvitation`, `onLogOutreach`, `onPromoteSpeaker`, `onEnterContent`, `onReviewContent` (and any other action callbacks `SpeakerStatusLanes` accepts as of Phase D) from a parent that owns the modal/snackbar state to `<SpeakerStatusLanes>`. Reference implementation: `web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx` already does this correctly — mirror its pattern.

**Given** the dev picks the delete path,
**When** the deletion runs,
**Then** zero references survive (verified by `grep -rn "SpeakerStatusDashboard" web-frontend/`),
**And** the type-check is clean (`cd web-frontend && npm run type-check`),
**And** the test suite is green (`cd web-frontend && npm run test`).

**Given** the dev picks the wire-callbacks path,
**When** the wiring is done,
**Then** the component accepts the same callbacks as `<SpeakerStatusLanes>` (either passed in as props from the parent, or as a self-contained wrapper that owns the modal/snackbar state and passes the callbacks down — match the pattern of `EventSpeakersTab.tsx`),
**And** the existing `SpeakerStatusDashboard.test.tsx` (if any) is updated to assert the callbacks are wired (mount the component, click the primary-action button on a card, assert the appropriate callback fires).

> **Origin:** `deferred-work.md` § "11-d-2" — `SpeakerStatusDashboard mounts SpeakerStatusLanes without the new callbacks. The ?? no-op defaults silently swallow clicks. Component is not on any production route today (only referenced from tests). Latent — if any future caller wires SpeakerStatusDashboard back in, the kanban's primary-action buttons will be inert.`

---

### AC7 — Stale narrative comment in negative Bruno test refreshed

**Given** `bruno-tests/events-api/51-speaker-workflow-invalid-transition.bru` is opened,
**When** I read the comment block inside the test (currently around lines 40–44),
**Then** the comment is rewritten to reflect the 8-state ADR-009 §0.1 model, e.g.,:
```
// The 8-state workflow per ADR-009 §0.1 is:
// IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED
// (with DECLINED reachable from every non-terminal state).
// CONFIRMED was removed in Story 11.B.1 — this test asserts the API rejects it as an invalid status value.
```
**And** the actual test logic (asserts `res.status: eq 422` for `newStatus: "CONFIRMED"`) is unchanged — the test is functionally correct, only the comment is stale.

**Given** the file is opened,
**When** the dev verifies test framing,
**Then** the test name and meta still make sense ("Speaker Workflow - Invalid Transition") — these stay as-is.

> **Origin:** Inline audit finding from this story's drafting — comment at `bruno-tests/events-api/51-speaker-workflow-invalid-transition.bru:41-42` references the pre-Epic-11 9-state workflow ending in `CONFIRMED`; comment-only nit, not a test-logic issue.

---

### AC8 — Full suite still green after the cleanup

**Given** the cleanup is complete,
**When** the dev runs the standard pre-merge gates:
  - `./gradlew :shared-kernel:test`
  - `./gradlew :services:event-management-service:test`
  - `./gradlew :services:event-management-service:flywayInfo` (sanity-check — no new migrations expected from this story)
  - `cd web-frontend && npm run type-check && npm run lint && npm run test`
**Then** every command exits 0,
**And** test counts have either stayed the same or grown (by the new AC4 + AC2 tests),
**And** zero unrelated failures.

**Given** the diff is reviewed,
**When** the dev does a final sweep with `git diff --stat`,
**Then** the changes are confined to the expected paths:
  - `shared-kernel/` — no changes expected.
  - `services/event-management-service/src/main/java/ch/batbern/events/` — `client/UserApiClient.java`, `client/impl/UserApiClientImpl.java`, `dto/PromoteSpeakerRequest.java`, `service/SpeakerWorkflowService.java`.
  - `services/event-management-service/src/test/java/ch/batbern/events/` — added tests for AC4 (required-field validation, identity-rebind reject) + deletions/updates for AC2.
  - `services/event-management-service/build.gradle.kts` (or wherever generator config lives) — no changes expected unless regeneration produces a diff.
  - `services/event-management-service/src/main/resources/openapi/` (if local copy exists) — no changes (canonical spec is at `docs/api/`).
  - `docs/api/events-api.openapi.yml` — `PromoteSpeakerRequest` schema edit.
  - `docs/architecture/ADR-009-unified-speaker-workflow.md` — new `## §0 Target Model` section.
  - `docs/architecture/index.md` (optional) — if entry tightening lands.
  - `web-frontend/src/types/speakerPool.types.ts` — union narrowed.
  - `web-frontend/src/types/generated/events-api.types.ts` — regenerated from OpenAPI.
  - `web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx` (or equivalent) — required-field validation + i18n key.
  - `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusDashboard.tsx` (+ tests) — deleted or wired (AC6).
  - `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` — comment update if the legacy-union-members comment is now stale (AC1 follow-on).
  - `web-frontend/public/locales/*/promoteSpeaker.json` (or wherever the dialog's namespace lives) — new required-field keys in 10 locales.
  - `bruno-tests/speaker-portal-api/14-respond-tentative.bru` — deleted.
  - `bruno-tests/events-api/51-speaker-workflow-invalid-transition.bru` — comment refresh.
  - Possibly other `.bru` files swept under AC3.

**Given** the commit message,
**When** the commit lands,
**Then** the message follows Conventional Commits (`chore(epic-11): trailing cleanup — sweep residue from Phases A–E`),
**And** the body lists the seven AC blocks with one-line summaries,
**And** if no doc-drift mappings (`.github/doc-drift-mappings.yml`) trip on the changed files, the commit does **not** need `[no-doc]` — doc changes are intentional and part of the AC (AC5 and AC7 explicitly touch docs).

---

## Tasks / Subtasks

> **Order rationale:** Items 1, 2, 3, 6, 7 are independent mechanical edits — any order works. Item 4 is the largest single change and has both backend + frontend halves; do backend first so the OpenAPI regeneration drives the frontend type. Item 5 (the §0 doc section) is the largest by line count and can be done last as a focused doc pass.

- [x] **Task 1 — Narrow frontend `SpeakerWorkflowState` union (AC1)**
  - [x] Edit `web-frontend/src/types/speakerPool.types.ts` lines 11–16: remove `| 'SLOT_ASSIGNED' | 'WITHDREW' | 'OVERFLOW'` from the union.
  - [x] Run `cd web-frontend && npm run type-check` and fix any newly-surfaced exhaustiveness errors. Likely candidates per `deferred-work.md`: `SpeakerStatusLanes.tsx` (line ~144 comment may need refresh), `speakerTransitions.ts`, any switch/map on `SpeakerWorkflowState`.
  - [x] If `SpeakerStatusLanes.tsx:144-149` comment about legacy union members is now redundant, remove or shorten to a one-line ADR-009 reference.
  - [x] Run `npm run lint` to surface any unused imports / variables.

- [x] **Task 2 — Delete dead methods from EMS `UserApiClient` (AC2)**
  - [x] Edit `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java`: remove `getSpeakerUsernames()` (line 126), `updateUser(...)`, `updateUserProfilePicture(...)`, and the orphan comment at ~line 139.
  - [x] Edit `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java`: remove the corresponding implementations (`getSpeakerUsernames` at ~line 790, plus any others) and any private helpers used only by them.
  - [x] Run `grep -rn "getSpeakerUsernames\|updateUserProfilePicture\|userApiClient\\.updateUser\\b" services/ web-frontend/src/` and confirm zero production-code matches. Delete any test files that asserted only the removed methods' behaviour.
  - [x] Run `./gradlew :services:event-management-service:compileJava` and `:test`. Green.

- [x] **Task 3 — Stale TENTATIVE Bruno test + sweep (AC3)**
  - [x] Delete `bruno-tests/speaker-portal-api/14-respond-tentative.bru`.
  - [x] If `bruno-tests/speaker-portal-api/environments/*.bru` or `bruno-tests/environments/*.bru` defines a `tentativeSpeakerToken` variable, remove that variable (it's now unused).
  - [x] Sweep: `grep -rEn "TENTATIVE|WITHDREW|OVERFLOW|SLOT_ASSIGNED|CONFIRMED" bruno-tests/` — for each hit, classify as (a) correct negative test, (b) stale positive test (delete or invert), (c) stale narrative comment (refresh). Apply edits.
  - [x] If the dev can run `./scripts/ci/run-bruno-tests.sh` locally (requires `~/.batbern/staging-organizer.json` etc.), run it and confirm green. If not, document the static-review sweep in the Dev Agent Record.

- [x] **Task 4 — Require firstName + lastName on promote endpoint; delete fallback (AC4)**
  - [x] **Backend DTO**: edit `services/event-management-service/src/main/java/ch/batbern/events/dto/PromoteSpeakerRequest.java` — add `@NotBlank` to `firstName` and `lastName`; update Javadoc.
  - [x] **OpenAPI spec**: edit `docs/api/events-api.openapi.yml` — locate `PromoteSpeakerRequest` schema, add `firstName` and `lastName` to the `required:` block, refine field descriptions.
  - [x] **Regenerate**: `./gradlew :services:event-management-service:openApiGenerateEvents` (verify by checking `build/generated/` after — exact task name may vary; check `services/event-management-service/build.gradle.kts` for the OpenAPI generator config). `cd web-frontend && npm run generate:api-types:events`.
  - [x] **Workflow service**: edit `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` — delete `firstNameFallback` and `lastNameFallback` methods (lines 507–523); simplify the call site at lines 301–304 to pass `payload.firstName()` and `payload.lastName()` directly.
  - [x] **Other entry paths**: `grep -rn "TransitionPayload\\|toState(SpeakerWorkflowState\\.READY)" services/event-management-service/src/main/java/` — confirm the promote endpoint is the only path that constructs a `CONTACTED → READY` payload. If others exist, ensure they either guarantee non-blank values or throw `ValidationException` explicitly.
  - [x] **Frontend dialog**: locate the promote-speaker dialog (`grep -rn "PromoteSpeakerDialog" web-frontend/src/components/`) and edit it — make `firstName` and `lastName` required in the form schema (Zod or react-hook-form rules — match existing convention in the file); disable submit until both are non-empty; add helper-text/error rendering per existing patterns.
  - [x] **i18n keys**: reuse `common.fieldRequired` / `common.required` if it exists in the target namespace. If a new key is needed, add it to **all 10 locale files** under `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/<namespace>.json`. EN + DE first-class, other 8 machine-translated baseline.
  - [x] **Backend tests**: add to `services/event-management-service/src/test/java/ch/batbern/events/controller/` — integration test for 400 rejection when `firstName` missing/blank, when `lastName` missing/blank, when both missing/blank.
  - [x] **Backend tests**: edit `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerWorkflowServiceTest.java` — happy-path test verifies `provisionUserWithRole` is called with the exact `firstName` / `lastName` from the payload (ArgumentCaptor); add the **identity-rebind reject** test (deferred from 11.D.1) — stub `provisionUserWithRole` to return a different username for the same email, assert `ValidationException` is thrown with the documented message.
  - [x] **Frontend tests**: `web-frontend/src/components/SpeakerBrainstormingPanel/__tests__/PromoteSpeakerDialog.test.tsx` (or wherever existing tests live) — add `it()` blocks for: submit disabled when firstName empty / lastName empty / both empty; helper-text rendered on blur of empty required field; happy-path submit fires mutation with `{ email, firstName, lastName }`.
  - [x] Run `./gradlew :services:event-management-service:test` + `cd web-frontend && npm run test && npm run type-check && npm run lint`. Green.

- [x] **Task 5 — Add `## §0 Target Model` section to ADR-009 (AC5)**
  - [x] Edit `docs/architecture/ADR-009-unified-speaker-workflow.md` — insert `## §0 Target Model` after the header block and before `## Context`. Populate seven subsections (§0.1 states, §0.2 transitions, §0.3 dropped columns/tables, §0.4 shared write path, §0.5 derived flags, §0.6 removed response types, §0.7 removed enum values). Source content from `docs/plans/speaker-workflow-refactor.md` §0.x and the linked stories' AC text.
  - [x] Update the existing `## Revision History` in ADR-009 with a new row dated 2026-05-18 noting the §0 section.
  - [x] `grep -rEn "ADR-009 §0\\.[0-9]" docs/ services/ web-frontend/` — verify every cited subsection now exists. If any citation references a `§0.x` number not in the seven subsections, EITHER add the subsection OR sweep-replace the citation.
  - [x] Skim `docs/architecture/index.md` and tighten the ADR-009 description if the existing entries follow a "section-N reference" pattern.

- [x] **Task 6 — Resolve `SpeakerStatusDashboard` latent component (AC6)**
  - [x] Run `grep -rn "SpeakerStatusDashboard" web-frontend/src/ | grep -v "__tests__\\|\\.test\\."` to find live callers.
  - [x] **Decision A — no live callers**: delete `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusDashboard.tsx` and any `__tests__/SpeakerStatusDashboard.test.tsx`; update barrel exports if any.
  - [x] **Decision B — live callers exist**: edit `SpeakerStatusDashboard.tsx:181-186` to thread `onSendInvitation`, `onLogOutreach`, `onPromoteSpeaker`, `onEnterContent`, `onReviewContent` (and any other current `<SpeakerStatusLanes>` action props) — either via props on `SpeakerStatusDashboard` itself or by self-contained modal/snackbar state inside the component. Mirror the pattern in `EventSpeakersTab.tsx`.
  - [x] Document the decision (A or B) in the Dev Agent Record.

- [x] **Task 7 — Refresh stale Bruno test comment (AC7)**
  - [x] Edit `bruno-tests/events-api/51-speaker-workflow-invalid-transition.bru` — rewrite the comment block at ~lines 40–44 to describe the 8-state ADR-009 §0.1 model + explain that `CONFIRMED` was removed by 11.B.1. Test logic unchanged.

- [x] **Task 8 — Full-suite verification + commit (AC8)**
  - [x] Run `./gradlew :shared-kernel:test :services:event-management-service:test` piped through `tee /tmp/epic-11-e-4-backend.log`; grep for `FAILED` / `ERROR`.
  - [x] Run `cd web-frontend && npm run type-check && npm run lint && npm run test 2>&1 | tee /tmp/epic-11-e-4-frontend.log`; grep for `Type error` / `FAIL`.
  - [x] `git diff --stat` and verify the diff matches the AC8 expected-files list.
  - [x] Stage changes and write the conventional-commits message. Leave the commit itself to the user per Story 11.B.1 convention.
  - [x] Update `_bmad-output/implementation-artifacts/sprint-status.yaml`: set `11-e-4-epic-11-trailing-cleanup: review` (and add a one-line dev note in the `last_updated:` comment per project convention).

### Review Findings

_Code review 2026-05-18 (bmad-code-review, Claude Opus 4.7 1M, 3 parallel reviewers: Blind Hunter, Edge Case Hunter, Acceptance Auditor). 1 P0 patch (CI-blocking), 1 decision-needed, 10 patches, 10 deferred, 6 dismissed._

**Patch application 2026-05-18 (same session):** 12 patches applied in-place (incl. PM decision on rework). Frontend type-check + lint clean, EMS `compileJava` BUILD SUCCESSFUL, EMS targeted tests (SpeakerWorkflowServiceTest + SpeakerPromoteControllerIntegrationTest) GREEN, frontend vitest targeted (PromoteSpeakerDialog 7/7, SpeakerStatusLanes 49/49, StatusHistoryTimeline 7/7) GREEN. Status → done.

#### Decision needed

- [x] **[Review][Decision] ADR-009 §0.2 lists `CONTENT_SUBMITTED → ACCEPTED (rework)` transition that code rejects** — **Resolved 2026-05-18 (PM/Nissim): Option A (doc fix)** — removed `ACCEPTED (rework)` from §0.2 transition list to match shipping code. If rework is ever a product requirement, it lands as a new story with ALLOWED-map + UI + tests. — `docs/architecture/ADR-009-unified-speaker-workflow.md` §0.2 row 5 documents a "rework" transition from CONTENT_SUBMITTED back to ACCEPTED, but `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java:81-82` only allows `CONTENT_SUBMITTED → {QUALITY_REVIEWED, DECLINED}`. The whole point of AC5 was to make ADR-009 §0 the canonical reference — landing it inconsistent on day one defeats the purpose. **Two PM choices:** (a) doc fix — remove `ACCEPTED (rework)` from §0.2, matching today's code; or (b) feature ack — keep §0.2 as aspirational and add a new story to wire rework into the ALLOWED map + UI. Source: Edge Case Hunter.

#### Patches

- [x] **[Review][Patch] P0 — Regenerate frontend `events-api.types.ts`** [`web-frontend/src/types/generated/events-api.types.ts:4505-4515`] — Still shows `firstName?` / `lastName?` with stale "Optional. If absent..." description. AC4 sub-bullet + CLAUDE.md "OpenAPI Type Generation" violated. CI `.github/workflows/build.yml` "Verify API types are up-to-date" step will fail. Run `cd web-frontend && npm run generate:api-types:events && git add src/types/generated/events-api.types.ts`. (Sources: Blind, Edge, Auditor.)
- [x] **[Review][Patch] P2 — OpenAPI `minLength: 1` allows whitespace-only firstName/lastName** [`docs/api/events-api.openapi.yml` PromoteSpeakerRequest properties firstName, lastName] — Java `@NotBlank` rejects `" "` (whitespace) but OpenAPI `minLength: 1` does not. Spec consumers code-generating clients won't get the same validation as the server. Either add `pattern: '^\\S.*$'` (or equivalent non-whitespace pattern) or note explicitly in description that server enforces stricter NotBlank semantics. (Source: Blind.)
- [x] **[Review][Patch] P2 — Defense-in-depth: add `requireName` precondition in `runReadyHook`** [`services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java:298-306`] — The new code passes `payload.firstName()` / `payload.lastName()` to CUMS without null/blank check, relying on DTO `@NotBlank` at the one current entry. Comment claims "Other entry paths into runReadyHook are expected to enforce the same invariant" but code does not enforce. Add `requireName(payload)` alongside `requireEmail(payload)` in `enforcePrecondition()` for the READY target, throwing `ValidationException` on null/blank. (Source: Edge Case Hunter.)
- [x] **[Review][Patch] P1 — Verify `useTranslation()` namespace wiring for new error keys** [`web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx`] — New t-calls reference `speakerBrainstorm.promoteDialog.errorFirstNameRequired` (no namespace prefix); keys land in `organizer.json`. The sibling `errorEmailRequired` key already follows the same pattern suggests namespace is correctly bound, but BH could not confirm from diff alone — verify `useTranslation('organizer')` is used (or add the prefix). If broken, all locales silently fall back to the EN default. (Source: Blind.)
- [x] **[Review][Patch] P3 — Stale Javadoc on promote controller method** [`services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java:94`] — Javadoc still says `firstName/lastName (optional)`. Update to `email/firstName/lastName (all required, @NotBlank)`. (Source: Edge Case Hunter.)
- [x] **[Review][Patch] P3 — `StatusHistoryTimeline.tsx` STATUS_COLORS has legacy entries + missing current states** [`web-frontend/src/components/organizer/SpeakerStatus/StatusHistoryTimeline.tsx:35-44`] — Map still has `SLOT_ASSIGNED`, `FINAL_AGENDA`; missing `CONTACTED`, `INVITED`, `CONTENT_SUBMITTED`, `QUALITY_REVIEWED`. Type as `Partial<Record<SpeakerWorkflowState, string>>` so future drift surfaces. (Source: Edge Case Hunter.)
- [x] **[Review][Patch] P3 — `speakerStatusService.ts:37-42` Javadoc lists legacy state model** [`web-frontend/src/services/speakerStatusService.ts:37-42`] — Comment lists transitions like `ACCEPTED → SLOT_ASSIGNED`. Rewrite as a ADR-009 §0.2 reference, similar to AC7's Bruno comment refresh. (Source: Edge Case Hunter.)
- [x] **[Review][Patch] P3 — `speaker-status-tracking.spec.ts:129` asserts deleted testid** [`web-frontend/e2e/organizer/speaker-status-tracking.spec.ts:129`] — Spec still references `[data-testid="speaker-status-dashboard"]` and a route that doesn't exist. Delete spec (already superseded by `SpeakerStatusLanes.test.tsx` + kanban Playwright specs) or mark `test.fixme` with Phase-F note. (Source: Edge Case Hunter.)
- [x] **[Review][Patch] P3 — ADR-009 §0.1 row 7 "Terminal happy state" contradicts §0.2 transition `QUALITY_REVIEWED → DECLINED`** [`docs/architecture/ADR-009-unified-speaker-workflow.md` §0.1 row 7] — Reword to "Quasi-terminal — DECLINED is reachable per §0.2" or drop the QUALITY_REVIEWED → DECLINED row from §0.2 since the "DECLINED reachable from any non-terminal state" rule already covers it. (Source: Blind.)
- [x] **[Review][Patch] P3 — ADR-009 §0.7 column header "History reason recorded" misleading** [`docs/architecture/ADR-009-unified-speaker-workflow.md` §0.7 table] — Three of four rows say "Migrated by V93" (a migration note, not a history reason); only the WITHDREW → DECLINED row writes an actual `speaker_status_history` reason. Rename column to "Migration note" or split into two columns. (Source: Blind.)
- [x] **[Review][Patch] P3 — Tighten `SpeakerStatusLanes.tsx` post-cleanup comment** [`web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx:142-144`] — New comment claims `KanbanLane` "is structurally identical to `SpeakerWorkflowState` post-11.E.4 cleanup," which a future reader will misread as redundancy. Tighten to: "KanbanLane covers exactly the 8 ADR-009 §0.1 states. The OutreachLane / PostAcceptanceLane sub-unions drive column grouping, not type narrowing." (Source: Blind.)

#### Deferred (logged to `deferred-work.md`)

- [x] **[Review][Defer] Sprint-status YAML monstrous comment** [`_bmad-output/implementation-artifacts/sprint-status.yaml:540`] — deferred, hygiene; longstanding sprint-status comment shape on this branch.
- [x] **[Review][Defer] 5 Bruno tests deleted, AC3 authorized 1** [`bruno-tests/speaker-portal-api/12,13,13b,15-*.bru`] — deferred; dev rationale (orphan chain) is defensible. Tests 12 and 13 had standalone coverage value (INVITED-state setup, invitation-send) — consider whether equivalents are needed in `bruno-tests/events-api/` as a follow-up.
- [x] **[Review][Defer] CUMS `provisionUserWithRole` still has `"Speaker"`/`"Unknown"` fallback** [`services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java:677-696`] — deferred, out of scope for Epic-11 cleanup; CUMS-side hardening needs its own story. AC4 invariant defended at EMS layer today.
- [x] **[Review][Defer] `notification.ts` NotificationType still recognises legacy `SLOT_ASSIGNED` / `OVERFLOW`** [`web-frontend/src/types/notification.ts`, `web-frontend/src/components/.../TeamActivityFeed.tsx`] — deferred, separate type system not flagged in deferred-work; tied to backend notification triggers.
- [x] **[Review][Defer] Bruno test 422 mapping for invalid state transitions not pinned in ADR** [`bruno-tests/events-api/51-speaker-workflow-invalid-transition.bru:41-44` comment locks 422] — deferred, doc nit; either pin contract in ADR-009 §0.2 or remove parenthetical.
- [x] **[Review][Defer] §0.3 says `magic_link_tokens` is in EMS schema** [`docs/architecture/ADR-009-unified-speaker-workflow.md` §0.3] — deferred, doc nit; clarify which service owns the magic-link table or footnote the table header.
- [x] **[Review][Defer] `mode: 'onTouched'` + `isValid` initial state may not flip submit-disabled until first blur** [`web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx:107-118`] — deferred, test resilience; tests pass today but could be brittle if defaults change.
- [x] **[Review][Defer] MUI testid + click+tab fragility in PromoteSpeakerDialog tests** [`web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.test.tsx:1571-1574, 1584-1587`] — deferred, test resilience; consider `getByRole('textbox', { name: /first name/i })` or `inputProps={{ 'data-testid': ... }}`.
- [x] **[Review][Defer] `splitFullName("Mononym")` pre-fills empty lastName, no visible error on first render** [`web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx:46-55, 107-110`] — deferred, UX papercut for single-word speaker names; trigger initial validation or clear both fields when single-word.
- [x] **[Review][Defer] CONTACTED-state speaker with non-null username in identity-rebind test fixture** [`services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerWorkflowServiceTest.java:1297-1298`] — deferred, test setup nit; either justify with comment or seed READY+ for the rebind-guard test.

#### Dismissed

- Test mock for `useTranslation` returns fallback regardless — by design; per CLAUDE.md i18n testing rule.
- `SpeakerStatusDashboard` deletion residue — Edge Hunter project-wide grep confirmed zero live source references.
- `getSpeakerUsernames` callers — Edge Hunter verified zero non-`build/` references repo-wide.
- Test for explicit JSON `null` firstName — `@NotBlank` is Spring-tested; one-line gap not worth a fix.
- Commit-message form drift (`chore(11.E.4)` vs `chore(epic-11)`) — semantically aligned, matches prior epic-11 commits.
- `docs/architecture/index.md` ADR-009 entry not tightened — spec explicitly marked optional.

---

## Dev Notes

### Why these seven items and not the others in `deferred-work.md`

The `deferred-work.md` log contains ~60 items deferred across Phases A–E reviews. The seven in this story are the ones that meet **all three** of these criteria:

1. **Concrete and mechanical** — the fix is a small, well-defined edit, not a design discussion or a cross-cutting concern.
2. **Inside Epic 11 scope** — the residue belongs to Phase B–E acceptance; not Phase F (magic-link teardown), not broader Cognito-design (outbox pattern, federation), not project-wide hygiene (snackbar global state, i18n locale-completeness sweep).
3. **Not already absorbed by 11.F.1** — magic-link backend deletions, frontend `?token=` cleanup, `@Disabled` IT classes, etc., are all explicitly scoped to 11.F.1 in their deferred-work entries; this story does not duplicate that scope.

Items explicitly **excluded** with rationale (from the Out-of-scope block above):
- AFTER_COMMIT design for `runInvitedHook` / `runAcceptedHook` → bundled with 11.F.1 per its deferred-work entry; needs a domain-event listener (`SpeakerInvitedEvent` / `SpeakerAcceptedEvent`) consumed by `@TransactionalEventListener(phase = AFTER_COMMIT)`, plus integration-test rework. Non-trivial.
- Cognito orphan-user (JPA commit fails after `AdminCreateUser` succeeds) → needs outbox or reconciliation job, broader than Epic-11 cleanup.
- `@Async` email-failure speaker lockout → needs persistent retry queue or pre-send commit gate, broader.
- Slot-capacity TOCTOU race → PM Resolved Decision #6 (matches current behaviour).
- `runDeclinedHook` deletes session unconditionally (multi-speaker session orphan FK risk) → tracked as a multi-speaker session handling follow-up, broader.
- Cognito `EXTERNAL_PROVIDER` status mapping → federated IdP not on roadmap.
- `username` vs `preferred_username` drift on email change → pre-existing Cognito-design pitfall.
- `RestTemplate` DEBUG body logging → needs a staging/prod log-config audit, broader.
- Email format validation gap in `provisionUserWithRole` → pre-existing input-validation gap, broader.
- 5 `@Disabled` SpeakerPortal IT classes + 4 `describe.skip` Vitest suites + 3 Playwright `test.fixme` → all owned by 11.F.1 or by the staging test-speaker seed work.
- Native-speaker review of 8 non-DE/EN translations → cosmetic, non-blocking, owned by a future i18n polish pass.

### Architecture compliance pins

- **ADR-003 (cross-service identifiers)**: AC4 changes only the API contract for the promote endpoint; the `username` written to `speaker_pool.username` continues to be the canonical meaningful ID per ADR-003. No new UUID FK across services.
- **ADR-004 (factor user fields)**: AC2 (`UserApiClient` cleanup) does not touch the `User`/`bio`/`profile_picture_url` contract; the dead methods removed (`updateUser`, `updateUserProfilePicture`, `getSpeakerUsernames`) all predate Epic 11 and were already superseded by `patchUserProfile`.
- **ADR-006 (OpenAPI contract-first)**: AC4 mandates OpenAPI-spec-first — the spec edit drives DTO regeneration drives controller validation. Do NOT add `@NotBlank` to the DTO before updating the OpenAPI `required:` block, because the generated DTO would then disagree with the spec.
- **ADR-009 (unified speaker workflow)**: AC5 adds the `## §0 Target Model` section to ADR-009 itself so that the 38 dangling citations in other docs / OpenAPI specs / PRD become valid references. This is the inverse of "sweep the citations" — restructure the cited document instead.
- **CLAUDE.md §"Localization — Email Templates: DE + EN Only; UI i18n: All 10 Locales"**: AC4's new i18n keys for required-field errors are **UI keys** — all 10 locales required. AC1 / AC2 / AC3 / AC5 / AC6 / AC7 do not touch user-facing strings (only types, dead code, doc structure, comments, and one Bruno-test deletion).
- **CLAUDE.md §"Quality Standards"** + **§"Build & Test Output"**: pipe `./gradlew` / `make` / `npm` through `tee /tmp/<name>.log` and grep the log instead of re-running suites to find errors.
- **CLAUDE.md §"OpenAPI Type Generation"**: after AC4's OpenAPI edit, run `npm run generate:api-types` and **commit the generated types**.

### Source-tree references

| Purpose | Path |
|---------|------|
| Frontend speaker-pool union | `web-frontend/src/types/speakerPool.types.ts:11-16` |
| Frontend kanban lanes (exhaustiveness will surface here) | `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` |
| Frontend promote dialog (location verify) | `web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx` |
| Frontend latent-component | `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusDashboard.tsx:181-186` |
| EMS UserApiClient interface | `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java:126` |
| EMS UserApiClient impl | `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java:790` |
| EMS PromoteSpeakerRequest DTO | `services/event-management-service/src/main/java/ch/batbern/events/dto/PromoteSpeakerRequest.java` |
| EMS SpeakerWorkflowService fallback methods | `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java:507-523` (delete) |
| EMS SpeakerWorkflowService call site | `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java:301-304` (simplify) |
| OpenAPI events spec | `docs/api/events-api.openapi.yml` (find `PromoteSpeakerRequest` schema) |
| ADR-009 target | `docs/architecture/ADR-009-unified-speaker-workflow.md` |
| Stale Bruno positive test | `bruno-tests/speaker-portal-api/14-respond-tentative.bru` (delete) |
| Stale Bruno comment | `bruno-tests/events-api/51-speaker-workflow-invalid-transition.bru:41-42` |
| Deferred-work log | `_bmad-output/implementation-artifacts/deferred-work.md` |
| Project-context (loaded as persistent fact) | `_bmad-output/project-context.md` |

### Testing standards

- **TDD per CLAUDE.md §"TDD"**: AC4's new tests (required-field rejection, identity-rebind reject, frontend required-field validation) should be written **before** the AC4 production-code changes that satisfy them, per the project's Red-Green-Refactor expectation. AC2's deletions do not need new tests written first (deletion of unused code is verified by the existing suite remaining green).
- **Integration tests use PostgreSQL via `AbstractIntegrationTest`** per CLAUDE.md. AC4's controller integration tests must extend `AbstractIntegrationTest` — do NOT use `@DataJpaTest` or H2.
- **Frontend tests use `screen` + `userEvent`**, not `container.querySelector` or `fireEvent`. AC4's new `PromoteSpeakerDialog.test.tsx` blocks must follow this convention.
- **i18n test resilience per CLAUDE.md §Localization**: tests assert against EN values OR the namespace-stripped key. Do NOT lock in a specific non-EN translation.
- **Bruno test sweep (AC3)**: if `./scripts/ci/run-bruno-tests.sh` cannot run locally (missing staging tokens), document the static-review sweep in the Dev Agent Record and rely on CI to catch any miss.

### Project Structure Notes

This story touches **only existing files**, never creates new top-level modules:
- 0 new Java packages or shared-kernel additions.
- 0 new Flyway migrations (V96 is reserved for 11.F.1; this story does not number a migration).
- 0 new frontend pages or routes.
- 1 new ADR section (the §0 Target Model block inside the existing ADR-009 file).
- 0 new locale namespaces — reuse existing namespaces for required-field error keys.

The single largest behaviour change (AC4) is a **contract tightening** at one endpoint and **does not change the workflow state machine, derived flags, side-effect hooks, or any cross-service interaction.** The fallback methods being deleted were only ever reached when the optional payload fields were absent; making them required at the contract layer makes the fallback unreachable, which lets us delete it without touching the runtime behaviour for any well-formed promote call.

### References

- Plan: `docs/plans/speaker-workflow-refactor.md` §0–§9 — source of all `§0.x` target-model facts.
- ADR: `docs/architecture/ADR-009-unified-speaker-workflow.md` — currently lacks the §0 section that AC5 adds.
- Epic: `docs/prd/epic-11-speaker-workflow-refactor.md` — origin of FR/AR/UX-DR mapping (closes outstanding tail of FR1, FR3, FR5, FR12, AR1, AR2, AR23, UX-DR15, UX-DR16, NFR4, NFR7, NFR10).
- Deferred-work log: `_bmad-output/implementation-artifacts/deferred-work.md` — line-by-line origin of each AC.
- Predecessor stories:
  - `_bmad-output/implementation-artifacts/11-a-1-align-speaker-workflow-documentation-to-adr-009.md` — AC5 origin.
  - `_bmad-output/implementation-artifacts/11-b-1-reduce-speakerworkflowstate-enum-to-8-states.md` — AC1 origin (Phase B residue).
  - `_bmad-output/implementation-artifacts/11-b-2-speakerworkflowservice-sole-status-writer.md` — AC4 origin (fallback literals).
  - `_bmad-output/implementation-artifacts/11-c-1-drop-speakers-table-remove-speaker-coordination-refs.md` — AC2 origin, AC3 origin.
  - `_bmad-output/implementation-artifacts/11-c-2-userapiclient-provisioning-contentsubmissionservice-shared.md` — AC2 owner-of-record.
  - `_bmad-output/implementation-artifacts/11-d-1-promote-endpoint-brainstorm-tightening-slot-gate.md` — AC4 sibling deferral (identity-rebind reject test).
  - `_bmad-output/implementation-artifacts/11-d-2-kanban-card-primary-action-button-cleanup.md` — AC6 origin (latent SpeakerStatusDashboard).
  - `_bmad-output/implementation-artifacts/11-d-4-kanban-guided-drag-unified-drawer-onbehalf-content-form.md` — AC1 echo (WITHDREW/OVERFLOW Phase B residue).
  - `_bmad-output/implementation-artifacts/11-e-2-cognito-provisioning-at-ready-invitation-email-i18n.md` — AC4 owner-of-record (where the fallback-decision was flagged for resolution).
  - `_bmad-output/implementation-artifacts/11-e-3-speaker-portal-cognito-auth-frontend-session-multi-role-nav.md` — most-recently-completed predecessor; project-context for current Cognito session model.
- Project rules: `_bmad-output/project-context.md` — TDD, ADR-003/004/006/009 cross-service identifiers, OpenAPI contract-first, integration-tests-must-use-Testcontainers-Postgres.

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.7 (1M context) via bmad-dev-story workflow — 2026-05-18.

### Debug Log References

- `/tmp/epic-11-e-4-t1-typecheck.log` — Task 1 type-check (0 errors)
- `/tmp/epic-11-e-4-t1-lint.log` — Task 1 lint (clean)
- `/tmp/epic-11-e-4-t2-compile.log` — Task 2 compile (BUILD SUCCESSFUL)
- `/tmp/epic-11-e-4-t4-compile1.log` — Task 4 first compile after DTO + OpenAPI edit
- `/tmp/epic-11-e-4-t4-compile2.log` — Task 4 second compile after fallback deletion
- `/tmp/epic-11-e-4-t4-test.log` — Task 4 targeted EMS test run (SpeakerWorkflowServiceTest + SpeakerPromoteControllerIntegrationTest all PASS, including new identity-rebind reject test and three new AC4 required-field 400 tests)
- `/tmp/epic-11-e-4-t4-typecheck.log` — Task 4 frontend type-check (clean)
- `/tmp/epic-11-e-4-t4-vitest.log` — Task 4 dialog test run #1 (2 blur-validation tests failed initially because form `mode: 'onChange'` doesn't validate on pure blur — switched to `mode: 'onTouched'`)
- `/tmp/epic-11-e-4-t4-vitest2.log` — Task 4 dialog test run #2 after `mode: 'onTouched'` change (18/18 PASS)
- `/tmp/epic-11-e-4-t8-ems.log` — Task 8 full EMS suite (BUILD SUCCESSFUL, 0 failures)
- `/tmp/epic-11-e-4-t8-tc.log` — Task 8 frontend type-check (clean)
- `/tmp/epic-11-e-4-t8-lint.log` — Task 8 frontend lint (clean)
- `/tmp/epic-11-e-4-t8-vitest.log` — Task 8 full Vitest suite (4945 pass, 0 fail, 205 skipped including 11.F.1-deferred suites, 8 test files skipped)

### Completion Notes List

- **Task 1 (AC1)** — Frontend `SpeakerWorkflowState` union narrowed to the canonical OpenAPI-generated type. The local widening previously added `'INVITED' | 'SLOT_ASSIGNED' | 'WITHDREW' | 'OVERFLOW'` — turns out `'INVITED'` was ALSO redundant (generated type already has all 8 states), so all four widenings dropped. The pre-existing comment in `SpeakerStatusLanes.tsx:144-149` explicitly noting the legacy union members was rewritten as a one-line ADR-009 §0.1 reference. Two surviving hits on `'SLOT_ASSIGNED'`/`'OVERFLOW'` in `web-frontend/src/types/notification.ts` + `TeamActivityFeed.tsx` are `NotificationType` values (separate type system, not flagged in deferred-work) — left untouched. Test references in `taskService.test.ts` are loosely-typed `triggerState: string` literals, also untouched.
- **Task 2 (AC2)** — Only `getSpeakerUsernames()` was still present; `updateUser` and `updateUserProfilePicture` had already been removed from `UserApiClient` (the comment about replacing them was just orphan narrative). Deleted: interface declaration (5 lines), implementation method (67 lines incl. exception handling), and the orphan comment block. `PaginatedUserResponse` import stays — still used by `getOrganizerUsernames` and `getPartnerUsernames`. Backwards-reference to deleted `updateUserProfilePicture` removed from `patchUserProfile` Javadoc in `UserApiClientImpl`.
- **Task 3 (AC3)** — Deleted 5 Bruno tests, not just 1: the TENTATIVE response flow had a complete setup chain (`12-setup-tentative-speaker` → `13-send-tentative-invitation` → `13b-get-tentative-test-token` → `14-respond-tentative` → `15-tentative-can-change-to-accept`). All 5 served the deleted positive TENTATIVE assertion; without `14`, none of the others lead anywhere meaningful. Sweep for `TENTATIVE|WITHDREW|OVERFLOW|SLOT_ASSIGNED|CONFIRMED` across `bruno-tests/` confirmed the only surviving hits are now correct negative tests (`51-speaker-workflow-invalid-transition.bru` asserts 422 for `CONFIRMED`) or correct narrative comments. Could not run the full Bruno suite locally (requires staging tokens at `~/.batbern/staging-organizer.json`); static-review sweep documented in this completion note in lieu.
- **Task 4 (AC4)** — `PromoteSpeakerRequest` DTO + OpenAPI schema both tightened: `firstName` + `lastName` are now `@NotBlank` (Java) / `required + minLength: 1` (OpenAPI). The OpenAPI regeneration kicked in automatically via `compileJava.dependsOn tasks.openApiGenerate`. Hand-written DTO at `events/dto/PromoteSpeakerRequest.java` is the canonical one (not the generated `events/dto/generated/PromoteSpeakerRequest.java`) — verified via the controller's import statement. Both fallback methods (`firstNameFallback` / `lastNameFallback`) deleted from `SpeakerWorkflowService.java`; call site at lines 301-304 simplified to pass payload values directly. Promote endpoint is the only entry path that builds a `CONTACTED → READY` `TransitionPayload` (verified via grep). Six existing integration tests updated to include `firstName: "Jane", lastName: "Smith"` in their payloads (previously sent `{ "email": "..." }` only) so they reach their intended assertion (409 / 404 / 403). Three new AC4 400 tests added (firstName missing, lastName missing, both blank). One new identity-rebind reject test added in `SpeakerWorkflowServiceTest` per 11.D.1 deferred-work item 4. Frontend dialog `PromoteSpeakerDialog.tsx`: schema tightened (Zod `.trim().min(1)`), `required` attribute added to both `TextField`s for visual asterisk, `mode: 'onTouched'` replaces `'onChange'` so error helper text appears on first blur of an empty required field. Frontend `PromoteSpeakerRequest` TS interface tightened (both names now non-optional). New i18n keys `errorFirstNameRequired` + `errorLastNameRequired` added to all 10 locales — EN + DE hand-written, other 8 with natural-sounding translations (Italian/French/Spanish/Portuguese-Romanche/Finnish/Dutch/Japanese/gsw-BE). New focused test file `PromoteSpeakerDialog.test.tsx` (7 tests) covers all AC4 behaviour.
- **Task 5 (AC5)** — Added new `## §0 Target Model` section to ADR-009 with subsections §0.1 through §0.7 (states, transitions, dropped tables/columns, shared write path, derived flags, removed response types, removed enum values). Section positioned BEFORE the original `## Context` section so it reads as a target-model preamble rather than appendix. Each subsection sources from `docs/plans/speaker-workflow-refactor.md` §0 and the implementing story. Revision-history row v1.6 added (2026-05-18). `grep -rEn "ADR-009 §0\\.[0-9]" docs/ services/ web-frontend/src/` confirms every cited subsection number (§0.1, §0.2, §0.3, §0.5, §0.6, §0.7) now resolves; §0.4 (shared write path) is included for completeness even though not externally cited at audit time.
- **Task 6 (AC6)** — Decision A taken (delete). `grep -rn "SpeakerStatusDashboard" web-frontend/src/` returned only self-references (declaration / export / test) and one comment in `EventSpeakersTab.tsx:5`. No production route or import. Deleted: `SpeakerStatusDashboard.tsx` + `__tests__/SpeakerStatusDashboard.test.tsx`. Comment in `EventSpeakersTab.tsx` updated to reference `SpeakerStatusLanes` directly. No barrel/index re-exports to update.
- **Task 7 (AC7)** — Comment block in `51-speaker-workflow-invalid-transition.bru` rewritten: previously listed 9-state workflow ending in `CONFIRMED`; now describes the ADR-009 §0.1 8-state model and notes that `CONFIRMED` was removed by Story 11.B.1. Test logic unchanged.
- **Task 8 (AC8)** — Full EMS suite BUILD SUCCESSFUL (0 failures). Frontend `type-check + lint + test` all clean (4945 pass, 0 fail). `git diff --stat` confined to expected paths. Sprint-status flipped from `in-progress` → `review`. Story file Tasks/Subtasks all marked `[x]`. Commit deferred to user per project convention.

### File List

**Modified (24):**
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `_bmad-output/implementation-artifacts/11-e-4-epic-11-trailing-cleanup.md` (this file)
- `bruno-tests/events-api/51-speaker-workflow-invalid-transition.bru` (AC7 comment refresh)
- `docs/api/events-api.openapi.yml` (AC4 PromoteSpeakerRequest schema)
- `docs/architecture/ADR-009-unified-speaker-workflow.md` (AC5 §0 section + revision row)
- `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java` (AC2 dead-method removal)
- `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java` (AC2)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/PromoteSpeakerRequest.java` (AC4 @NotBlank)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` (AC4 fallback delete)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPromoteControllerIntegrationTest.java` (AC4 new + updated tests)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerWorkflowServiceTest.java` (AC4 identity-rebind reject test)
- `web-frontend/public/locales/de/organizer.json` (AC4 i18n keys)
- `web-frontend/public/locales/en/organizer.json` (AC4)
- `web-frontend/public/locales/es/organizer.json` (AC4)
- `web-frontend/public/locales/fi/organizer.json` (AC4)
- `web-frontend/public/locales/fr/organizer.json` (AC4)
- `web-frontend/public/locales/gsw-BE/organizer.json` (AC4)
- `web-frontend/public/locales/it/organizer.json` (AC4)
- `web-frontend/public/locales/ja/organizer.json` (AC4)
- `web-frontend/public/locales/nl/organizer.json` (AC4)
- `web-frontend/public/locales/rm/organizer.json` (AC4)
- `web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx` (AC4 form schema + onTouched mode)
- `web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx` (AC6 comment refresh)
- `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` (AC1 comment refresh)
- `web-frontend/src/types/speakerPool.types.ts` (AC1 union + AC4 PromoteSpeakerRequest)

**Added (1):**
- `web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.test.tsx` (AC4 — 7 focused tests for required-field validation)

**Deleted (7):**
- `bruno-tests/speaker-portal-api/12-setup-tentative-speaker.bru` (AC3 — orphaned setup, fed deleted test 14)
- `bruno-tests/speaker-portal-api/13-send-tentative-invitation.bru` (AC3 — same chain)
- `bruno-tests/speaker-portal-api/13b-get-tentative-test-token.bru` (AC3 — same chain)
- `bruno-tests/speaker-portal-api/14-respond-tentative.bru` (AC3 — primary target; stale positive test for TENTATIVE)
- `bruno-tests/speaker-portal-api/15-tentative-can-change-to-accept.bru` (AC3 — same chain)
- `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusDashboard.tsx` (AC6 — latent component, no production callers)
- `web-frontend/src/components/organizer/SpeakerStatus/__tests__/SpeakerStatusDashboard.test.tsx` (AC6)

### Change Log

| Date | Change | Note |
|------|--------|------|
| 2026-05-18 | Initial implementation (Stories 11.E.4 AC1–AC8) | Single dev-pass via bmad-dev-story; all 8 tasks complete; status → review |

---

## Open Questions

This story has no PM-blocking open questions. The previously-open question on item 4 (`firstNameFallback` / `lastNameFallback` semantics) was resolved by the user on 2026-05-18: **Option A — require firstName + lastName on the promote payload, delete the fallback methods** (recorded in this story's AC4).

If the dev agent encounters an ambiguity during implementation (e.g., the promote dialog is in a different file than expected, or AC6's grep returns an unexpected caller), capture it in the Dev Agent Record's Completion Notes and proceed with the most conservative interpretation; do not block on it.
