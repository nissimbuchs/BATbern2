# Story 11.C.2: `UserApiClient` provisioning + patch operations; `ContentSubmissionService` as shared write path

Status: review

## Story

As an organizer entering content on behalf of a speaker (and as a speaker submitting their own content),
I want both flows to land in the same backend service and produce identical downstream effects,
so that the audit trail, validation rules, and side effects are consistent regardless of which UI submitted the content — and so that future stories (11.D.1, 11.D.4, Phase E) have a single seam to extend.

## Phase / Dependencies / Requirements Covered

- **Phase:** C — Entity model simplification (second of two C stories).
- **Depends on:** Story 11.B.2 (`SpeakerWorkflowService.transition()` is the sole status writer with the `SpeakerProvisioningHook` seam and the `CONTENT_SUBMITTED` no-op hook). **Hard dependency on 11.C.1**: this story deletes `UserApiClient.updateUser` / `updateUserProfilePicture` and `UserUpdateDto` (Resolved Decision §1); the only callers (`SpeakerProfileService`, `SpeakerProfilePhotoService`, `SpeakerPortalProfileController`) are deleted by 11.C.1. If 11.C.1 has not landed, this story's deletions will leave the codebase un-compileable. Recommended sequencing: 11.B.2 → 11.C.1 → 11.C.2. Optional-but-recommended: 11.B.3 merged so the API spec already excludes legacy states.
- **Requirements covered:** FR7 (single shared backend write path), AR13 (UserApiClient gains "provision user with role"), AR14 (UserApiClient gains "patch user bio / profile picture" accepting ORGANIZER or SPEAKER principal), NFR3 (idempotency of provisioning), NFR4 (audit-trail integrity).
- **Plan / ADR anchors:** ADR-009 §"Decision 1" (single status writer, side-effect hook on READY), §"Decision 2" (User+SPEAKER role replaces Speaker entity), §"Cross-cutting: two data-entry flows, one service layer" (lines 295-316); `docs/plans/speaker-workflow-refactor.md` §0.4 (two flows, one service), §3.1 row "Refactor ContentSubmissionService" (lines 306+), §3.2 rows AR13–AR14 (UserApiClient extensions), §4 paragraph below the API table (lines 375-380, "the two content-submission endpoints MUST share a single backend implementation"); `docs/architecture/06-backend-architecture.md` §"Speaker authentication (ADR-009)" lines 165-193 (provisionUserWithRole contract); `docs/architecture/06a-workflow-state-machines.md` §"Callers of transition()" line 383 (ContentSubmissionService is one caller).

## Acceptance Criteria

The AC are pinned to ADR-009 §"Cross-cutting", the Epic 11 PRD §"Story 11.C.2" (lines 750-813), and the actual file locations confirmed by reading the current code on `feature/speaker-workflow-refactor`. Each AC names the exact file under change.

### AC1 — `UserApiClient.provisionUserWithRole(...)` exists and is idempotent (AR13, NFR3)

**Given** `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java`,
**When** I read the interface,
**Then** a new method exists with this signature:

```java
/**
 * Provision a User with a role (idempotent).
 * Story 11.C.2 (AR13). Used by SpeakerWorkflowService.transition() at CONTACTED → READY.
 *
 * Behaviour:
 *  - If User exists by email (case-insensitive lookup, matches existing getOrCreateUser pattern),
 *    grants the requested role if not already held and returns existingUsername + temporaryPassword=null.
 *  - If User does not exist, creates User + grants role + returns generated username + temporaryPassword=null
 *    (Cognito provisioning is wired in Story 11.E.2; this method stubs the Cognito side for now —
 *    see SpeakerProvisioningHook seam from 11.B.2).
 *  - Idempotent: re-calling for an already-provisioned user is a no-op and returns the same username.
 *
 * @param request username (optional — generated from email if null), email (required),
 *                firstName, lastName, role (required, e.g. "SPEAKER")
 * @return ProvisionUserResponse with username (canonical) and temporaryPassword (always null in 11.C.2;
 *         populated by 11.E.2 once Cognito AdminCreateUser is wired)
 * @throws UserServiceException on 5xx, timeout, network error
 */
ProvisionUserResponse provisionUserWithRole(ProvisionUserRequest request);
```

**And** `ProvisionUserRequest` and `ProvisionUserResponse` are added in `services/event-management-service/src/main/java/ch/batbern/events/dto/` (or, preferably, generated from a new endpoint in `docs/api/users-api.openapi.yml` per Task 1.1 — see below). Fields:

- `ProvisionUserRequest`: `username (nullable)`, `email (required)`, `firstName (nullable)`, `lastName (nullable)`, `role (required, "SPEAKER" for this epic; future stories may pass other roles)`.
- `ProvisionUserResponse`: `username (non-null, canonical)`, `temporaryPassword (nullable — always null in 11.C.2; non-null after Phase E wires Cognito)`, `created (boolean — true if a new User row was created; false if it already existed)`.

**And** the corresponding company-user-management-service endpoint exists at `POST /api/v1/users/provision`:

- Annotated `@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")` (service-to-service principal — event-management-service propagates the ORGANIZER JWT per `microservices-http-clients.md`).
- Calls `UserService.provisionUserWithRole(ProvisionUserRequest)` which:
  1. Looks up User by email (`UserRepository.findByEmailIgnoreCase`); creates if absent (re-using the existing `getOrCreateUser` path so we do not fork the create logic).
  2. Looks up `Role` enum from the string; throws `ValidationException` on unknown role.
  3. Calls `RoleService.grantRole(username, role)` — idempotent (`role_assignments` UNIQUE constraint already enforces this; the service catches the duplicate-key exception and treats it as success).
  4. **Cognito section deliberately stubbed for this story** — `cognitoSync` is forced to `false`. A comment in the service body cites Story 11.E.2 as the owner of the Cognito wiring (`AdminCreateUser` + `AdminSetUserPassword` + `AdminAddUserToGroup`) and notes that the `temporaryPassword` field on the response is reserved for that story to populate.
  5. Returns `ProvisionUserResponse` with the canonical username, `created = true|false`, and `temporaryPassword = null`.
- Integration test extends `AbstractIntegrationTest` (Testcontainers PostgreSQL) covering: (a) new user case → User row created + SPEAKER `role_assignments` row created + 200 OK with `created=true`; (b) existing-user case → no User mutation + role granted if missing + 200 OK with `created=false`; (c) re-call after (b) → still 200 OK, idempotent, no duplicate `role_assignments` row.

**And** the OpenAPI spec `docs/api/users-api.openapi.yml` documents the new path under the existing `Users` tag, with request/response examples and the 200/400/403/500 error codes minimum (per `_bmad-output/project-context.md` "OpenAPI Specs" rule).

**And** the existing `UserApiClient.getOrCreateUser(...)` method **stays** — it remains useful for the anonymous-registration path (ADR-005) and for any future caller that does not need role grants. The new `provisionUserWithRole` is the explicit speaker-provisioning entry point per ADR-009 §"Decision 3"; it does not replace `getOrCreateUser`.

### AC2 — `UserApiClient.patchUserProfile(...)` exists and accepts ORGANIZER or SPEAKER principal (AR14)

**Given** `UserApiClient.java`,
**When** I read the interface,
**Then** a new method exists:

```java
/**
 * Patch user profile fields (bio, profilePictureUrl). Story 11.C.2 (AR14).
 * Called by ContentSubmissionService when an organizer or speaker submits content with CV / portrait.
 *
 * Behaviour:
 *  - Updates only the fields present in the request (null fields are left unchanged).
 *  - Per ADR-009 §"Decision 2" + ADR-007 + Confirmed Decision §6.7: bio and profilePictureUrl are
 *    overwritten globally (no per-event snapshot — the User profile is the single source of truth).
 *
 * @param username target user's username (the speaker whose profile is being patched)
 * @param request bio (nullable), profilePictureUrl (nullable). At least one must be present.
 * @return Updated UserResponse (per ADR-004 HTTP-enrichment pattern)
 * @throws UserNotFoundException if username not found (404)
 * @throws ValidationException if both fields are null (no-op patches rejected)
 * @throws UserServiceException on 5xx, timeout, network error
 */
UserResponse patchUserProfile(String username, PatchUserProfileRequest request);
```

**And** `PatchUserProfileRequest` is added in `services/event-management-service/src/main/java/ch/batbern/events/dto/` (or generated from the OpenAPI spec — see Task 1.2). Fields: `bio (nullable, max 5000 chars)`, `profilePictureUrl (nullable, max 2048 chars, must be a valid URL)`.

**And** the corresponding company-user-management-service endpoint exists at `PATCH /api/v1/users/{username}/profile`:

- Annotated `@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'SPEAKER')")` (the controller-level guard).
- Adds method-level role-scope enforcement: if the authenticated principal has role SPEAKER but **not** ORGANIZER/ADMIN, the controller validates `securityContextHelper.getCurrentUsername().equals(pathVariableUsername)` and throws `AccessDeniedException` (mapped to HTTP 403) otherwise. This prevents a SPEAKER from patching another speaker's profile.
- Calls `UserService.patchUserProfile(username, PatchUserProfileRequest)` which:
  1. Loads the User by username; throws `UserNotFoundException` if missing.
  2. Validates at least one field is present; throws `ValidationException` if both are null.
  3. Writes `User.bio` and/or `User.profile_picture_url` per the non-null fields.
  4. Returns the refreshed `UserResponse`.
- Integration test covers: (a) ORGANIZER patches any user (200), (b) SPEAKER patches their own (200), (c) SPEAKER patches another speaker (403), (d) ATTENDEE patches anyone (403 — neither ORGANIZER nor SPEAKER role), (e) empty patch (400 ValidationException), (f) non-existent username (404).

**And** the existing `UserApiClient.updateUser(username, UserUpdateDto)` and `UserApiClient.updateUserProfilePicture(username, profilePictureUrl)` methods from Story 6.2b are **deleted** (Resolved Decision §1) — along with their `UserApiClientImpl` bodies and the `services/event-management-service/src/main/java/ch/batbern/events/dto/UserUpdateDto.java` class. The only callers (`SpeakerProfileService`, `SpeakerProfilePhotoService`, `SpeakerPortalProfileController` and their tests) are already deleted by Story 11.C.1, so the deletion is clean — verify with `grep -rn "userApiClient\.updateUser\|userApiClient\.updateUserProfilePicture\|UserUpdateDto" services/event-management-service/` returning zero hits in `src/main/` after both stories merge. The new `patchUserProfile` is the canonical "update bio / profile picture" entry point going forward.

**And** the OpenAPI spec `docs/api/users-api.openapi.yml` documents `PATCH /api/v1/users/{username}/profile` with request/response examples and 200/400/403/404/500 errors. Frontend types are regenerated and committed (`cd web-frontend && npm run generate:api-types:users`).

### AC3 — A single `ContentSubmissionService` is the sole write path for both content endpoints (FR7, ADR-009 §"Cross-cutting")

**Given** the current code has TWO content-submission services that diverge (`ContentSubmissionService.java` — magic-link, used by `/speaker-portal/content/submit`; `SpeakerContentSubmissionService.java` — organizer, used by `/events/{code}/speakers/{speakerId}/content`),
**When** this story merges,
**Then** there is exactly **one** content-submission service:

- Production file: `services/event-management-service/src/main/java/ch/batbern/events/service/ContentSubmissionService.java`.
- `SpeakerContentSubmissionService.java` is **deleted** along with its companion test `SpeakerContentSubmissionServiceIntegrationTest.java`.
- The remaining `ContentSubmissionService` exposes a single public submit method (signature in AC4) that accepts both flows. The magic-link-only helpers (`getContentInfo(String token)`, `saveDraft(ContentDraftRequest)`) **stay** on `ContentSubmissionService` for the speaker portal pre-Cognito; they are scoped for deletion in Phase E (Story 11.E.3) when the portal moves to Cognito Bearer auth. Phase F (Story 11.F.1) deletes the magic-link bridging code.

**And** the verification grep:

```bash
grep -rn "class SpeakerContentSubmissionService\|class ContentSubmissionService" services/event-management-service/src/main/
```

returns exactly one match — `ContentSubmissionService`.

**And** every direct `speaker.setStatus(...)` call in the new `ContentSubmissionService` is **removed**. The status mutation flows through `SpeakerWorkflowService.transition(speakerPoolId, CONTENT_SUBMITTED, principal, payload)` per Story 11.B.2 AC1 (single writer). The grep `grep -rn "setStatus(SpeakerWorkflowState" services/event-management-service/src/main/` continues to return exactly one match (inside `SpeakerWorkflowService.transition`).

**And** the in-line history-row writes that exist in today's `ContentSubmissionService.submitContent` (lines 341-356) are **removed** — the history row is written by `transition()` per Story 11.B.2 AC3 step 7 with `changed_by_username = principal.username()`. The grep `grep -rn "statusHistoryRepository.save\|new SpeakerStatusHistory()" services/event-management-service/src/main/` continues to return exactly one production match (inside `SpeakerWorkflowService.transition`). Test scaffolding is not bound by this rule.

### AC4 — `ContentSubmissionService.submit(...)` signature and body contract

**Given** `ContentSubmissionService.java` after the refactor,
**Then** the consolidated submit method has this signature:

```java
@Transactional
public ContentSubmitResponse submit(
        UUID speakerPoolId,
        String eventCode,
        ContentSubmissionPayload payload,
        SecurityPrincipal principal   // 11.B.2 type, used by transition()
);
```

**And** `ContentSubmissionPayload` is a new record at `services/event-management-service/src/main/java/ch/batbern/events/service/content/ContentSubmissionPayload.java`:

```java
public record ContentSubmissionPayload(
        String title,                  // required, max 200 chars
        String contentAbstract,        // required, max 1000 chars
        String bio,                    // optional — patched onto User.bio if present
        String profilePictureUrl,      // optional — patched onto User.profile_picture_url if present
        String presentationUploadId    // optional — uploaded material reference (existing SessionMaterialsService flow)
) {}
```

(The `presentationUploadId` field is **wired through** to the existing `SpeakerPortalMaterialsService.confirmMaterialUpload(...)` path so the organizer-on-behalf endpoint can attach an already-uploaded presentation to a session. This is **not** new functionality — it parallels what the speaker portal already does at `POST /api/v1/speaker-portal/materials/confirm`. If `presentationUploadId` is null, no material linking happens. The presigned-URL step itself is **unchanged** — organizers continue to use whatever frontend presigned-URL flow they use today, and the upload ID is passed through here.)

**And** the method body executes in this order:

1. Validate required fields (`title` and `contentAbstract` non-blank, length caps). Throw `ValidationException` on violation. **Sanity check is the only validation here** — full payload validation is in the controller via `@Valid` annotations on the request DTOs.
2. Load `SpeakerPool` by `speakerPoolId`; throw `NotFoundException` if missing.
3. **Pre-check the source state** — `speaker.getStatus()` must be `ACCEPTED` or `CONTENT_SUBMITTED` (resubmission case). Any other state throws `InvalidStateTransitionException` (the precise allow-list belongs to `SpeakerWorkflowService.transition()` — this is the *user-friendly* short-circuit before the workflow service runs the same check).
4. Capture the previous version (for incremented version assignment); compute `nextVersion = max(submission_version) + 1` (or `1` if none).
5. **Get-or-create the session** (existing logic from both services — pick the cleaner of the two paths):
   - If `speaker.getSessionId()` is non-null AND the session exists → reuse it, update `session.title` / `session.description` from the submitted content.
   - Else → create a new `Session` row + a `SessionUser` row with `speakerRole = PRIMARY_SPEAKER`, `isConfirmed = false`. Slug-generation logic mirrors the current `SpeakerContentSubmissionService.submitContent` (collision-handled via `-1`, `-2`, … suffix up to 1000 attempts).
6. **Persist the `ContentSubmission` row** with `nextVersion`, `submittedAt = Instant.now()`, `title`, `contentAbstract`, `submittedByUsername = principal.username()` if Open Question #2 is resolved to "add the column"; otherwise, the audit principal is captured solely via `speaker_status_history.changed_by_username` (written by `transition()` in step 9). **Default behaviour for this AC: do NOT add a new column on `speaker_content_submissions`** — see Open Question #2.
7. **If `payload.bio != null` or `payload.profilePictureUrl != null`**: call `userApiClient.patchUserProfile(speaker.getUsername(), new PatchUserProfileRequest(payload.bio, payload.profilePictureUrl))`. If `speaker.getUsername()` is null (invariant violation — speaker should have been provisioned at `CONTACTED → READY`), log a warning and skip the profile patch (do **not** fail the submission — content writes are independent of profile patches; the inverse failure mode is worse).
8. **If `payload.presentationUploadId != null`**: call the existing materials-confirm path (re-use `SpeakerPortalMaterialsService.confirmMaterialUpload` or its equivalent helper) to link the uploaded file to the session. Internal helper, not a separate API hop. (If the dev finds this helper is too magic-link-coupled to call from the organizer path, factor out a principal-agnostic core method and have both wrappers call it — same refactor pattern this AC applies to the service itself.)
9. **Build a `TransitionPayload`** with `reason = "Content submitted (version " + nextVersion + ")"` and **call** `speakerWorkflowService.transition(speakerPoolId, CONTENT_SUBMITTED, principal, payload)`. This:
   - Triggers the 11.B.2 same-state-or-forward semantics: from `ACCEPTED → CONTENT_SUBMITTED` is a real transition; from `CONTENT_SUBMITTED → CONTENT_SUBMITTED` (resubmission) is a same-state self-transition that still writes a history row but skips side-effect hooks (per 11.B.2 AC2/AC3 same-state branch).
   - Writes the `speaker_status_history` row with `changed_by_username = principal.username()` — the audit-trail rule.
   - Publishes `SpeakerWorkflowStateChangeEvent`.
10. **Publish `SpeakerContentSubmittedEvent`** (existing event, consumed by `OrganizerNotificationService` for "speaker submitted content" notifications). Payload mirrors today's `ContentSubmissionService` (lines 359-368) — `submissionId`, `speakerPoolId`, `speakerName`, `eventCode`, `eventTitle`, `sessionTitle`, `presentationTitle`, `submissionVersion`.
11. **Return** `ContentSubmitResponse(submissionId, version, "SUBMITTED", sessionTitle)`.

**And** the method is `@Transactional` (write transaction). Steps 5, 6, 9 are inside the same transaction. Steps 7 (`userApiClient.patchUserProfile` — cross-service HTTP) and 8 (`materialsService.confirmMaterialUpload` — local DB but coupled to S3) are inside the transaction by default; if the dev observes a measurable rollback issue in integration tests (e.g., partial profile patch left committed in CUMS after EMS roll back), promote step 7 to **before** step 6's `INSERT` and accept the asymmetry — document in the PR. The transactional boundary discussion is logged in 11.B.2 Dev Notes "Concurrency note"; same principle applies here.

### AC5 — Organizer endpoint delegates to the shared service with an ORGANIZER `SecurityPrincipal`

**Given** `POST /api/v1/events/{eventCode}/speakers/{speakerId}/content` (currently in `SpeakerStatusController.java` line 144),
**When** the request is processed,
**Then** the controller method:

1. Retains `@PreAuthorize("hasRole('ORGANIZER')")` (existing) — Spring Security rejects SPEAKER tokens with 403 before reaching the controller body.
2. Builds a `SecurityPrincipal actor = new SecurityPrincipal(securityContextHelper.getCurrentUsername(), securityContextHelper.getCurrentUserRoles())` (using the 11.B.2 type).
3. Builds a `ContentSubmissionPayload` from the existing `SubmitContentRequest` body (mapping `presentationTitle → title`, `presentationAbstract → contentAbstract`, etc.) plus the optional `bio` / `profilePictureUrl` / `presentationUploadId` fields **newly added** to `SubmitContentRequest` for this story (per AR14 — the organizer drawer's on-behalf form lands these fields). The OpenAPI spec `docs/api/speakers-api.openapi.yml` is updated for the new request fields. Frontend types regenerate.
4. Calls `contentSubmissionService.submit(speakerId, eventCode, payload, actor)`.
5. Returns `201 Created` with the `ContentSubmitResponse` payload (status code matches today's behaviour).

**And** the existing `SubmitContentRequest` field names `username`, `speakerName`, `email`, `company` (currently passed through to `SpeakerContentSubmissionService` for ad-hoc user-creation per the pre-11 model) are **removed** from this request — the speaker's identity is already established on `speaker_pool` (`speaker.getUsername()` populated at `CONTACTED → READY` per 11.B.2's READY hook).

**And** per Resolved Decision §3, both `SubmitContentRequest` and `ContentSubmitRequest` schemas in the OpenAPI specs set `additionalProperties: false`. Requests sending the removed `username`/`speakerName`/`email`/`company` fields (or any unknown property) get a `400 Bad Request` via the OpenAPI-generated request validation. A short coordination note is added to the PR description: frontend callers (organizer Kanban content drawer, magic-link portal form) must drop those fields in the same release window; if they ship out-of-sync, frontend users will see a 400. This is acceptable — it's a controlled rollout (refactor branch, no in-flight users per ADR-009 §6.4), and the strict rejection prevents stale-payload bugs from masquerading as silent success.

### AC6 — Speaker-portal endpoint delegates to the shared service with a SPEAKER `SecurityPrincipal`

**Given** `POST /api/v1/speaker-portal/content/submit` (currently in `SpeakerPortalContentController.java` line 150),
**When** the request is processed,
**Then**:

1. The controller continues to be unannotated by `@PreAuthorize` in this story — the magic-link token IS the auth mechanism until Phase E (Story 11.E.3). **DO NOT** add `@PreAuthorize("hasRole('SPEAKER')")` here in 11.C.2; that is explicitly Phase E's scope (per ADR-009 §"Decision 3" + the plan §4 row "Auth changes").
2. The controller validates the magic-link token via `magicLinkService.validateToken(request.token())` (existing path; do not refactor away).
3. The validated token yields a `TokenValidationResult` carrying the `speakerPoolId`, `speakerName`, and `eventCode`. The controller then loads `SpeakerPool` to retrieve `speaker.getUsername()` (populated at `CONTACTED → READY`; nullable for pre-11.B.2 magic-link sessions).
4. The controller builds a SPEAKER principal: `SecurityPrincipal actor = new SecurityPrincipal(speaker.getUsername() != null ? speaker.getUsername() : speaker.getSpeakerName(), List.of("SPEAKER"))`. The fallback-to-`speakerName` matches the pattern 11.B.2 established in `SpeakerResponseService` (per 11.B.2 Dev Notes "Status-history actor — `SecurityPrincipal` and the speaker-name fallback").
5. The controller builds a `ContentSubmissionPayload` from `ContentSubmitRequest`. The speaker-portal request DTO **stays a token-bearing record** for now (`token`, `title`, `contentAbstract`, plus optionally `bio`, `profilePictureUrl`, `presentationUploadId` newly added). Phase E replaces the `token` with the Cognito Bearer mechanism.
6. The controller calls `contentSubmissionService.submit(speakerPoolId, eventCode, payload, actor)`.
7. The controller continues to mark the token as used (`magicLinkService.markTokenAsUsed(token)` — existing path), after the submission succeeds.

**And** the OpenAPI spec `docs/api/speakers-api.openapi.yml` (or whichever current spec owns `/api/v1/speaker-portal/**`) is updated with the optional `bio`, `profilePictureUrl`, `presentationUploadId` fields on `ContentSubmitRequest`. Frontend types regenerate.

**And** the role-rejection test path described by the original PRD AC ("Given the organizer endpoint is invoked with a SPEAKER token, or the speaker endpoint is invoked with an ORGANIZER token, Then Spring Security rejects with 403") is **only enforceable for the organizer side in this story** — the organizer endpoint's `@PreAuthorize("hasRole('ORGANIZER')")` already rejects SPEAKER tokens with 403; that side is covered by an integration test (AC9 item 5). The speaker endpoint's reciprocal protection (rejecting an ORGANIZER Cognito token on `/speaker-portal/**`) lands in Phase E (Story 11.E.3) when `@PreAuthorize("hasRole('SPEAKER')")` is added; flag this in the PR description. (The magic-link path implicitly does the right thing — an organizer doesn't have a speaker's magic-link token, so they cannot actually invoke the speaker endpoint as themselves — but this is data-defence, not Spring Security defence.)

### AC7 — `SpeakerWorkflowService.transition(CONTENT_SUBMITTED, ...)` remains a side-effect-free hook (11.B.2 invariant)

**Given** the consolidated `ContentSubmissionService.submit()` writes the content row and the session before calling `transition()`,
**When** the workflow service runs,
**Then** the `CONTENT_SUBMITTED` arm of the side-effect switch is a no-op (per Story 11.B.2 AC5 "ACCEPTED → CONTENT_SUBMITTED — no side effect from this method"). This story does **not** modify `SpeakerWorkflowService.transition` for this transition,
**And** the history-row write in step 7 of `transition()` (per 11.B.2 AC3) captures `previousStatus = ACCEPTED` (or `CONTENT_SUBMITTED` on resubmission) and `newStatus = CONTENT_SUBMITTED` with `changed_by_username = principal.username()`.

**And** the resubmission case (speaker submitting v2 after `CONTENT_SUBMITTED → CONTENT_SUBMITTED` same-state) writes a self-transition history row per 11.B.2 AC2's same-state branch — preserving audit even though the state is unchanged. The dev verifies this via integration test AC9 item 4.

### AC8 — Identical downstream effects for both flows (FR7, NFR4)

**Given** an organizer submits content via the organizer endpoint with payload `P`, and a separate speaker submits content via the speaker-portal endpoint with payload `P'` (equivalent shape),
**When** both transitions complete,
**Then** the following DB rows produced by the two flows differ **only** by `changed_by_username` on the history row and `submitted_by_username` on the content row (if Open Question #2 is resolved to "add the column"; otherwise the content rows are byte-identical and the only difference is the history row's `changed_by_username`):

- `speaker_content_submissions` row: same `speaker_pool_id`, same `title`, same `abstract`, same `submission_version` (for the same speaker; obviously different for different speakers), same `session_id`.
- `user_profiles` patch (if `bio` / `profilePictureUrl` present): same field values.
- `speaker_pool.status`: `CONTENT_SUBMITTED` after either flow.
- `speaker_status_history` row: same `previous_status`, same `new_status = CONTENT_SUBMITTED`, **different** `changed_by_username` (organizer-username vs speaker-username).
- `SpeakerContentSubmittedEvent` emitted in both cases with the same payload shape (minus the principal-identifying field, if any).
- `SpeakerWorkflowStateChangeEvent` emitted in both cases with `changedBy = principal.username()`.

**And** an integration test (AC9 item 6) seeds two identical speakers, submits via each endpoint, and asserts row equality modulo the principal field.

### AC9 — Testcontainers integration tests cover the consolidated service

**Given** `./gradlew :services:event-management-service:test`,
**When** the tests run,
**Then** a new test class `services/event-management-service/src/test/java/ch/batbern/events/service/ContentSubmissionServiceIntegrationTest.java` extends `AbstractIntegrationTest` (per CLAUDE.md and project-context.md "ALL integration tests MUST extend `AbstractIntegrationTest`") and covers:

1. **Organizer-on-behalf happy path** — `should_persistContentAndTransitionToContentSubmitted_when_organizerSubmitsOnBehalf()`. Seed speaker at `ACCEPTED`. Call `submit(...)` with an ORGANIZER principal and full payload (title, abstract, bio, profilePictureUrl). Assert: `speaker_content_submissions` row exists with version 1; `speaker_pool.status = CONTENT_SUBMITTED`; `speaker_status_history` row has `changed_by_username = "organizer-username"`; `SpeakerContentSubmittedEvent` + `SpeakerWorkflowStateChangeEvent` were published (Mockito spy on `ApplicationEventPublisher`); `UserApiClient.patchUserProfile(...)` invoked once with the bio + URL (Mockito spy).

2. **Speaker-self happy path** — `should_persistContentAndTransitionToContentSubmitted_when_speakerSubmitsSelf()`. Same as (1) but SPEAKER principal. `changed_by_username = speaker-username`. Same byte-identical rows otherwise.

3. **Resubmission case** — `should_writeSelfTransitionHistory_when_resubmittingFromContentSubmitted()`. Seed speaker at `CONTENT_SUBMITTED` with a v1 row. Call `submit(...)`. Assert: v2 row created; speaker.status stays at `CONTENT_SUBMITTED`; a NEW `speaker_status_history` row with `previousStatus == newStatus == CONTENT_SUBMITTED` (per 11.B.2 AC2 same-state branch); side-effect hooks NOT re-fired (verify on `SpeakerProvisioningHook` and any invitation-email mock — both `verify(..., never())`).

4. **Profile patch with no bio/url** — `should_notCallPatchUserProfile_when_bioAndPictureBothNull()`. Submit with `bio = null` and `profilePictureUrl = null`. Assert `UserApiClient.patchUserProfile` was NEVER invoked. `User` is unchanged.

5. **Role rejection on organizer endpoint** — controller-level integration test in `SpeakerStatusControllerIntegrationTest`: `should_return403_when_speakerTokenCallsOrganizerContentEndpoint()`. Uses `@WithMockUser(roles = "SPEAKER")` to attempt the organizer endpoint. Spring Security rejects before the controller body; service is never called.

6. **Equivalence test** — `should_produceIdenticalDownstreamRowsExceptChangedByUsername_when_bothFlowsRunWithSamePayload()`. Per AC8. Seed two identical speakers (different IDs). Run organizer flow on one, speaker-self flow on the other. Compare row-by-row; assert equality modulo the principal column.

7. **`provisionUserWithRole` idempotency** — in `UserApiClientIntegrationTest` (new test class, or extend an existing one): seed an existing User without SPEAKER role, call `provisionUserWithRole(...)` twice, assert the User row is unchanged after the second call and exactly one `role_assignments` row exists. (This test runs in `event-management-service` against a mock company-user-management-service via WireMock or in `company-user-management-service` directly — preferred location: CUMS, where the real endpoint logic lives. The EMS client test layer should mock the HTTP boundary.)

8. **`patchUserProfile` role-scope** — in CUMS: `should_return403_when_speakerPatchesAnotherSpeakerProfile()` and `should_allow200_when_speakerPatchesOwnProfile()`. Verifies the path-variable / current-username match logic from AC2.

9. **Missing username invariant violation** — `should_logWarningAndSkipProfilePatch_when_speakerUsernameIsNull()`. Seed speaker with `username = null` (pre-11.B.2 legacy data). Submit with `bio = "..."`. Assert: warning logged; profile patch NOT invoked; content submission still succeeds and status transitions to `CONTENT_SUBMITTED`.

**And** the existing tests `ContentSubmissionServiceTest.java` and `SpeakerContentSubmissionServiceIntegrationTest.java` are either deleted (the latter — service is deleted) or merged into the new consolidated test class. `SpeakerPortalContentControllerIntegrationTest.java` is updated to reflect the new payload fields and the new principal-construction path.

### AC10 — Bruno API contract tests reflect the consolidated service

**Given** `bruno-tests/events/` (organizer-side) and `bruno-tests/speaker-portal-api/` (speaker-side) collections,
**When** the dev surveys them,
**Then**:

- Existing `.bru` files for `POST /events/{code}/speakers/{speakerId}/content` are extended to cover the new optional fields (`bio`, `profilePictureUrl`, `presentationUploadId`) and to assert the 201-Created response shape.
- Existing `.bru` files for `POST /speaker-portal/content/submit` are extended likewise.
- A new `.bru` for the equivalence test (organizer + speaker flow against same payload, asserting the resulting state) is added if a Bruno-level assertion is feasible; otherwise this assertion lives only at the integration-test layer (preferred — keeps Bruno focused on contract surface).
- The new `POST /api/v1/users/provision` endpoint (AC1) gets a `bruno-tests/users-api/provision-user.bru` contract test asserting 200 OK on first call, 200 OK with `created=false` on second call.
- The new `PATCH /api/v1/users/{username}/profile` endpoint (AC2) gets a `bruno-tests/users-api/patch-user-profile.bru` contract test asserting 200 on ORGANIZER patch, 403 on cross-speaker patch.
- `./scripts/ci/run-bruno-tests.sh` runs green after the additions.

### AC11 — Out-of-scope sweep

**Given** `git diff --name-only develop...HEAD`,
**Then** the diff includes ONLY files under:

- `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java` (MODIFIED — add `provisionUserWithRole` + `patchUserProfile`; **delete** `updateUser` + `updateUserProfilePicture` per Resolved Decision §1)
- `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java` (MODIFIED — wire the two new methods; delete the two removed-method impls)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/UserUpdateDto.java` (DELETED — sole consumer was deleted by 11.C.1)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/ProvisionUserRequest.java` and `ProvisionUserResponse.java` (NEW — or generated via OpenAPI; see Task 1.1)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/PatchUserProfileRequest.java` (NEW — or generated)
- `services/event-management-service/src/main/java/ch/batbern/events/service/ContentSubmissionService.java` (MODIFIED — consolidated)
- `services/event-management-service/src/main/java/ch/batbern/events/service/content/ContentSubmissionPayload.java` (NEW)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerContentSubmissionService.java` (DELETED)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java` (MODIFIED — new principal construction + new payload mapping)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalContentController.java` (MODIFIED — same)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SubmitContentRequest.java` (MODIFIED — add new fields, remove dead user-identity fields)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/ContentSubmitRequest.java` (MODIFIED — add new fields)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java` (MODIFIED — two new endpoints)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` (MODIFIED — `provisionUserWithRole` + `patchUserProfile` methods)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/ProvisionUserRequest.java` / `ProvisionUserResponse.java` / `PatchUserProfileRequest.java` (NEW — or generated)
- `docs/api/users-api.openapi.yml` (MODIFIED — two new paths)
- `docs/api/speakers-api.openapi.yml` (MODIFIED — extend request DTOs for `/events/{code}/speakers/{speakerId}/content` and `/speaker-portal/content/submit`)
- `web-frontend/src/types/generated/users-api.types.ts` and `speakers-api.types.ts` (REGENERATED)
- All matching test files
- `bruno-tests/users-api/*.bru` (NEW — provision + patch contract tests)
- `bruno-tests/events/**/*.bru` and `bruno-tests/speaker-portal-api/**/*.bru` (MODIFIED — payload extensions)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (flip `11-c-2-…: ready-for-dev → review` via code-review workflow)
- `_bmad-output/implementation-artifacts/11-c-2-userapiclient-provisioning-contentsubmissionservice-shared.md` (Dev Agent Record updates)

**And** the diff does NOT contain:

- Any change to `MagicLinkService.java`, `SpeakerMagicLoginController.java`, `SpeakerPortalTokenController.java`, `JwtConfig.java` — Phase F (Story 11.F.1) owns those.
- Any `@PreAuthorize("hasRole('SPEAKER')")` annotation on speaker-portal controllers — Phase E (Story 11.E.3) owns that.
- Any Cognito-related code (`AdminCreateUser`, `AdminSetUserPassword`, `AdminAddUserToGroup`) — Phase E (Story 11.E.2) owns that. The `temporaryPassword` field on `ProvisionUserResponse` is reserved here; it stays null until Phase E.
- Any new column on `speaker_content_submissions` UNLESS Open Question #2 is resolved to "add" — default is no migration in this story.
- Any change to `web-frontend/src/pages/organizer/**` or `web-frontend/src/pages/speaker/**` — frontend integration of the new fields is Story 11.D.4 (organizer drawer on-behalf content form) per Epic 11 §"Phase D" and the plan §3.4. The frontend types are regenerated here but no UI component edits.
- Any change to the `Speaker` entity, `speakers` table, or `speaker-coordination-service` — Story 11.C.1 owns those.
- Any change to `SpeakerWorkflowService.transition(...)` body — Story 11.B.2 owns it.
- Any change to the EVENT workflow state machine — unchanged by Phase C.

### AC12 — Build + tests green; OpenAPI regenerated; doc-drift compliant

**Given** the dev runs `make verify`,
**When** the build finishes,
**Then** `./gradlew :services:event-management-service:test`, `:services:company-user-management-service:test`, `:api-gateway:test` are all green,
**And** `cd web-frontend && npm run type-check && npm run test` is green,
**And** `./scripts/ci/run-bruno-tests.sh` runs green against local-native services,
**And** `make audit-security` reports no new findings,
**And** the build output is `tee`'d per the project-context.md "Build & Test Output" rule (e.g. `./gradlew build | tee /tmp/em-11c2-build.log`),
**And** the commit follows Conventional Commits per CLAUDE.md: `feat(speakers): consolidate ContentSubmissionService + add UserApiClient.provisionUserWithRole/patchUserProfile per ADR-009 [Story 11.C.2]`. Body explains: `SpeakerContentSubmissionService` deleted; `ContentSubmissionService` consolidated; `UserApiClient` gains two operations; both content endpoints share one service; Cognito wiring stubbed for 11.E.2.

**And** per CLAUDE.md "Doc Drift Prevention": this story's changes touch business logic (state-machine caller + audit-trail field) AND API contracts (two new endpoints, two extended payloads). The commit message does NOT contain `[no-doc]`; the doc updates listed in AC1, AC2, AC5, AC6 cover the drift.

## Tasks / Subtasks

- [ ] **Task 1 — Author OpenAPI contracts first** (AC: 1, 2)
  - [ ] 1.1 Edit `docs/api/users-api.openapi.yml` to add `POST /api/v1/users/provision` (per AC1). Include request/response schemas, examples, and 200/400/403/500 error codes.
  - [ ] 1.2 Edit `docs/api/users-api.openapi.yml` to add `PATCH /api/v1/users/{username}/profile` (per AC2). Include request/response, role-scope description in `description`, 200/400/403/404/500.
  - [ ] 1.3 Edit `docs/api/speakers-api.openapi.yml` to extend `SubmitContentRequest` (`/events/{code}/speakers/{speakerId}/content`) and `ContentSubmitRequest` (`/speaker-portal/content/submit`) with optional `bio`, `profilePictureUrl`, `presentationUploadId` fields. Remove dead user-identity fields (`username`, `speakerName`, `email`, `company`) from `SubmitContentRequest` (per AC5). **Set `additionalProperties: false` on both schemas** (Resolved Decision §3) — unknown fields surface a 400 from the OpenAPI request validator. Add a Bruno test case asserting 400 when a stale field is sent (Task 8 covers this).
  - [ ] 1.4 Run `./gradlew openApiGenerate*` for the affected services. Verify the generated DTOs in `services/*/build/generated/` reflect the spec.
  - [ ] 1.5 Regenerate frontend types: `cd web-frontend && npm run generate:api-types`. Commit the result.

- [ ] **Task 2 — Implement `provisionUserWithRole` in CUMS** (AC: 1, 9 item 7)
  - [ ] 2.1 Add `UserService.provisionUserWithRole(ProvisionUserRequest) : ProvisionUserResponse`. Re-use the existing `getOrCreateUser` flow for the User row.
  - [ ] 2.2 Wire the role-grant via `RoleService.grantRole(username, role)` (existing or new — verify by reading `RoleService.java`). Idempotent on duplicate-key via the `role_assignments` UNIQUE constraint.
  - [ ] 2.3 Stub the Cognito section with a clearly-labelled `// TODO: Story 11.E.2 — wire Cognito AdminCreateUser` comment and a `temporaryPassword = null` return value. Do NOT call any Cognito SDK.
  - [ ] 2.4 Add `UserController.provisionUser(ProvisionUserRequest)` with `@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")`.
  - [ ] 2.5 Integration test per AC1.

- [ ] **Task 3 — Implement `patchUserProfile` in CUMS** (AC: 2, 9 item 8)
  - [ ] 3.1 Add `UserService.patchUserProfile(String username, PatchUserProfileRequest) : UserResponse`. Validate ≥ 1 field present. Overwrite per ADR-009 §6.7.
  - [ ] 3.2 Add `UserController.patchUserProfile(...)` with `@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'SPEAKER')")` + method-level cross-speaker enforcement.
  - [ ] 3.3 Integration tests per AC2 covering all role-scope cases.
  - [ ] 3.4 Leave `UserService.updateUserByUsername` (CUMS) untouched — it's the wider organizer/admin update path called via `PUT /api/v1/users/{username}`, not the speaker-content path. Only the EMS-side legacy methods are removed (Resolved Decision §1; Task 4.5).

- [ ] **Task 4 — Extend + prune `UserApiClient` (EMS side)** (AC: 1, 2; Resolved Decision §1)
  - [ ] 4.1 Add the method signatures (per AC1, AC2) to `UserApiClient.java`.
  - [ ] 4.2 Wire `UserApiClientImpl.java` (path: `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java`). Add the HTTP calls using the existing `RestTemplate`/`RestClient` pattern — match the style of the existing methods.
  - [ ] 4.3 Apply caching where appropriate: `getOrCreateUser` keeps its 15-min cache; `provisionUserWithRole` is NOT cached (write op); `patchUserProfile` evicts the `getUserByUsername` cache for that username (pattern from today's `updateUserProfilePicture`, which is about to be deleted — preserve the eviction logic, swap the trigger method).
  - [ ] 4.4 Mockito unit tests for the client (mock the HTTP layer); integration tests live in CUMS per Task 2/3.
  - [ ] 4.5 **Delete legacy methods** (Resolved Decision §1): remove `UserApiClient.updateUser(String, UserUpdateDto)` (interface line 149), `UserApiClient.updateUserProfilePicture(String, String)` (interface line 163), and their `UserApiClientImpl` impl bodies (around lines 471 and 540). Delete `services/event-management-service/src/main/java/ch/batbern/events/dto/UserUpdateDto.java`. Verify with `grep -rn "userApiClient\.updateUser\|userApiClient\.updateUserProfilePicture\|UserUpdateDto" services/event-management-service/src/main/` → zero hits. If 11.C.1 has NOT merged at this point, `SpeakerProfileService.java:139` and `SpeakerPortalProfileControllerIntegrationTest.java:177` will fail to compile — this is the **hard dependency** signal. Do not workaround; wait for 11.C.1 to land first.

- [ ] **Task 5 — Consolidate the two ContentSubmission services** (AC: 3, 4)
  - [ ] 5.1 Read both current services end-to-end (`ContentSubmissionService.java` 411 lines; `SpeakerContentSubmissionService.java` 295 lines). Capture: what each path does that the other doesn't (e.g., draft handling, session reuse, slug collision, status-history writing).
  - [ ] 5.2 Author the consolidated `ContentSubmissionService.submit(speakerPoolId, eventCode, payload, principal)` method per AC4 step-by-step.
  - [ ] 5.3 Delete `SpeakerContentSubmissionService.java`. Update any class that injected it (currently `SpeakerStatusController`) to inject `ContentSubmissionService` instead.
  - [ ] 5.4 Keep `ContentSubmissionService.getContentInfo(...)` and `saveDraft(...)` (magic-link helpers) on the same class until Phase E/F retires them. Document this lifespan with a one-line class-level Javadoc note.
  - [ ] 5.5 Remove every direct `speaker.setStatus(...)` and every in-line `SpeakerStatusHistory()` from the consolidated service per AC3.
  - [ ] 5.6 Re-run the AC3 verification greps to confirm no setStatus / no in-line history writes remain.

- [ ] **Task 6 — Refactor the two controllers to delegate to the shared service** (AC: 5, 6)
  - [ ] 6.1 `SpeakerStatusController.submitContent(...)` — build `SecurityPrincipal` from `securityContextHelper`; map `SubmitContentRequest` → `ContentSubmissionPayload`; delegate to `contentSubmissionService.submit(...)`. Drop the dead username/speakerName/email/company fields.
  - [ ] 6.2 `SpeakerPortalContentController.submitContent(...)` — validate the token via `magicLinkService`; load `SpeakerPool`; build a SPEAKER `SecurityPrincipal` with the speaker-name fallback; delegate to `contentSubmissionService.submit(...)`; mark token used on success.
  - [ ] 6.3 Add a new optional `bio` / `profilePictureUrl` / `presentationUploadId` field to both request DTOs.
  - [ ] 6.4 Re-run the AC6 verification: the speaker-portal controller still has NO `@PreAuthorize` annotation (Phase E adds it).

- [ ] **Task 7 — Integration tests** (AC: 9)
  - [ ] 7.1 Create `ContentSubmissionServiceIntegrationTest` extending `AbstractIntegrationTest`. Cover items 1, 2, 3, 4, 6, 9 of AC9.
  - [ ] 7.2 Update `SpeakerStatusControllerIntegrationTest` to cover item 5 of AC9 (403 on speaker token to organizer endpoint).
  - [ ] 7.3 In CUMS, add `UserServiceProvisionTest` and `UserServicePatchProfileTest` (or extend the existing `UserServiceTest`) — items 7 and 8 of AC9.
  - [ ] 7.4 Delete the now-stale `SpeakerContentSubmissionServiceIntegrationTest.java`.
  - [ ] 7.5 Update `SpeakerPortalContentControllerIntegrationTest.java` to reflect the new request DTO and principal-construction path.

- [ ] **Task 8 — Bruno collection updates** (AC: 10)
  - [ ] 8.1 Extend `bruno-tests/events/` `.bru` files for `/events/{code}/speakers/{speakerId}/content` with the new fields. Add a `400-rejects-unknown-fields.bru` case sending a stale `email` field and asserting 400 (Resolved Decision §3).
  - [ ] 8.2 Extend `bruno-tests/speaker-portal-api/` `.bru` files for `/speaker-portal/content/submit` with the new fields. Add a parallel `400-rejects-unknown-fields.bru` case.
  - [ ] 8.3 Add `bruno-tests/users-api/provision-user.bru` (idempotency: 200 → 200 with `created=false`).
  - [ ] 8.4 Add `bruno-tests/users-api/patch-user-profile.bru` (ORGANIZER 200, cross-SPEAKER 403).
  - [ ] 8.5 Run `./scripts/ci/run-bruno-tests.sh | tee /tmp/bruno-11c2.log`; expect green.

- [ ] **Task 9 — Documentation breadcrumbs** (AC: 12)
  - [ ] 9.1 Add a Revision History row to `docs/architecture/ADR-009-unified-speaker-workflow.md` recording the consolidated `ContentSubmissionService` + new `UserApiClient` operations.
  - [ ] 9.2 If `docs/architecture/06a-workflow-state-machines.md` line 383 ("ContentSubmissionService (shared by organizer-on-behalf and speaker-self content endpoints)") needs no edit, leave it alone — it's already aligned with the target state from Story 11.A.1. Verify with a re-read.
  - [ ] 9.3 If the architecture doc's mention of `submitted_by_username` on `content_submissions` (06a line 286) becomes stale because Open Question #2 is resolved to "do not add the column," update that one line accordingly. Otherwise leave alone.
  - [ ] 9.4 NO other doc edits.

- [ ] **Task 10 — Final verification** (AC: 11, 12)
  - [ ] 10.1 `git diff --name-only develop...HEAD` — verify only files from AC11 allow-list appear.
  - [ ] 10.2 `make verify | tee /tmp/full-verify-11c2.log` — full pipeline green.
  - [ ] 10.3 `make dev-native-up`, manual smoke: organizer drawer submits content end-to-end (creates content row, patches User.bio if filled, transitions speaker to CONTENT_SUBMITTED, fires `SpeakerContentSubmittedEvent` — confirmed via `tail -f /tmp/batbern-1-event-management.log`); speaker-portal magic-link submits content end-to-end (same downstream effect).
  - [ ] 10.4 Update `_bmad-output/implementation-artifacts/sprint-status.yaml` row `11-c-2-…: ready-for-dev → review` via the code-review workflow (do NOT flip manually mid-implementation).

## Dev Notes

### Why this story exists and what it does not do

This is the **second of two C-phase stories**. Story 11.C.1 deletes the legacy `Speaker` JPA entity and the `speakers` table — eliminating the data-model duplication. Story 11.C.2 (this one) finishes the C phase by collapsing the *behaviour* duplication: today, an organizer entering content on behalf of a speaker hits one Java service (`SpeakerContentSubmissionService`), and a speaker entering the same content through the magic-link portal hits a different Java service (`ContentSubmissionService`). The two services do nearly-identical things but they have drifted in subtle ways (different status-history writing, different session-reuse logic, different validation, different downstream events emitted in different orders). This story merges them into a single backend write path called by both endpoints — the cross-cutting decision from ADR-009 §"Cross-cutting: two data-entry flows, one service layer."

The story also wires two new explicit operations onto `UserApiClient`:

- `provisionUserWithRole(...)` — the canonical entry point for "create User + grant SPEAKER role" used by `SpeakerWorkflowService.transition(CONTACTED → READY)`. Cognito is stubbed for 11.E.2 to plug in.
- `patchUserProfile(...)` — the narrow "update bio / profile picture" operation called from `ContentSubmissionService` when the submitted content includes a CV or portrait. Replaces the broader `updateUser(...)` for this specific flow with sharper auth semantics (SPEAKER may patch own, ORGANIZER may patch any).

**What this story does NOT do** (and the story that owns each):

| Out of scope | Owned by |
|---|---|
| Cognito `AdminCreateUser` / `AdminSetUserPassword` / `AdminAddUserToGroup` inside `provisionUserWithRole` | Story 11.E.2 |
| `@PreAuthorize("hasRole('SPEAKER')")` on `/api/v1/speaker-portal/**` | Story 11.E.3 |
| Magic-link teardown (`MagicLinkService`, controllers, table) | Story 11.F.1 |
| Frontend organizer drawer redesign with on-behalf content form | Story 11.D.4 |
| Promote-to-READY endpoint (`POST /speakers/{id}/promote`) | Story 11.D.1 |
| Removal of `UserApiClient.updateUser` / `updateUserProfilePicture` legacy methods | Future cleanup story (after Phase E observation window) |
| Drop of `speakers` table or `Speaker` entity | Story 11.C.1 |
| `SpeakerWorkflowService.transition()` body | Story 11.B.2 |

### File-by-file map — current state and what changes

| Path | Today | After 11.C.2 |
|------|-------|--------------|
| `services/event-management-service/.../client/UserApiClient.java` | 164 lines; methods for `getUserByUsername`, `validateUserExists`, `getOrCreateUser`, plus notification-specific lookups, plus Story 6.2b's `updateUser`/`updateUserProfilePicture` | **EXTENDED + PRUNED** — adds `provisionUserWithRole` + `patchUserProfile`; deletes `updateUser` + `updateUserProfilePicture` (Resolved Decision §1; only callers were deleted by 11.C.1) |
| `services/event-management-service/.../client/impl/UserApiClientImpl.java` | HTTP-call wiring for the methods above | **EXTENDED + PRUNED** — wires the two new methods to the new CUMS endpoints; deletes the impl bodies for the two removed methods |
| `services/event-management-service/.../dto/UserUpdateDto.java` | DTO used only by `SpeakerProfileService` (already deleted by 11.C.1) | **DELETED** — sole consumer gone |
| `services/event-management-service/.../service/ContentSubmissionService.java` | 411 lines; magic-link-authed; does its own status mutation + history-row write; calls `MagicLinkService` for token validation; calls `SpeakerPortalMaterialsService` for materials | **CONSOLIDATED** — keeps `getContentInfo`/`saveDraft` for magic-link until Phase E; rewrites `submitContent` to the new shared `submit(speakerPoolId, eventCode, payload, principal)` per AC4. All `setStatus` and inline history writes deleted; delegates to `SpeakerWorkflowService.transition` per 11.B.2 |
| `services/event-management-service/.../service/SpeakerContentSubmissionService.java` | 295 lines; organizer-on-behalf; injects `UserApiClient`; does its own `setStatus` and event-publish | **DELETED**. Replaced by the consolidated `ContentSubmissionService` |
| `services/event-management-service/.../controller/SpeakerStatusController.java` | Line 144: `POST /events/{code}/speakers/{speakerId}/content` injects `SpeakerContentSubmissionService` and calls `submitContent(poolId, eventCode, title, abstract, username, speakerName, email, company)` | **REFACTORED** — injects `ContentSubmissionService`, builds `SecurityPrincipal` from `securityContextHelper`, builds `ContentSubmissionPayload` from request body, delegates to `submit(...)`. `@PreAuthorize("hasRole('ORGANIZER')")` retained |
| `services/event-management-service/.../controller/SpeakerPortalContentController.java` | Line 150: `POST /speaker-portal/content/submit` validates token via `ContentSubmissionService` (which calls `MagicLinkService` internally) and calls `submitContent(ContentSubmitRequest)` | **REFACTORED** — validates token (still via `MagicLinkService`), loads `SpeakerPool` to derive username, builds SPEAKER `SecurityPrincipal` with name-fallback, builds payload, delegates to `submit(...)`. NO `@PreAuthorize` annotation in this story (Phase E adds it) |
| `services/event-management-service/.../dto/SubmitContentRequest.java` (organizer body) | Has `username`, `speakerName`, `email`, `company` fields (legacy ad-hoc user creation), plus `presentationTitle`, `presentationAbstract` | **MODIFIED** — drop `username`/`speakerName`/`email`/`company` (speaker is already provisioned upstream); add optional `bio`, `profilePictureUrl`, `presentationUploadId` |
| `services/event-management-service/.../dto/ContentSubmitRequest.java` (speaker-portal body) | `token`, `title`, `contentAbstract` | **MODIFIED** — keep `token` (Phase E removes it); add optional `bio`, `profilePictureUrl`, `presentationUploadId` |
| `services/event-management-service/.../service/content/ContentSubmissionPayload.java` | Does not exist | **NEW** — internal record (per AC4) carrying the unified payload |
| `services/company-user-management-service/.../controller/UserController.java` | Line 254: `PUT /api/v1/users/{username}` (organizer-only update); line 279: `POST /api/v1/users/get-or-create` | **EXTENDED** — adds `POST /api/v1/users/provision` (org+admin) and `PATCH /api/v1/users/{username}/profile` (org+admin+speaker-self) |
| `services/company-user-management-service/.../service/UserService.java` | Existing `getOrCreateUser`, `updateUserByUsername`, etc. | **EXTENDED** — adds `provisionUserWithRole(...)` (re-uses `getOrCreateUser` + `RoleService.grantRole`); adds `patchUserProfile(...)` |
| `docs/api/users-api.openapi.yml` | ~1600 lines documenting `/api/v1/users/**` | **EXTENDED** — two new paths per AC1, AC2 |
| `docs/api/speakers-api.openapi.yml` | Documents `/events/{code}/speakers/{speakerId}/content` and `/speaker-portal/content/submit` | **EXTENDED** — request DTOs gain optional fields per AC5, AC6; dead identity fields removed |
| `web-frontend/src/types/generated/users-api.types.ts` and `speakers-api.types.ts` | OpenAPI-generated | **REGENERATED** — committed |
| `bruno-tests/users-api/*.bru` | Existing user-API contract tests | **EXTENDED** — two new `.bru` files |
| `bruno-tests/events/**/*.bru` (content) and `bruno-tests/speaker-portal-api/**/*.bru` (content) | Existing content-submission tests | **EXTENDED** — cover new fields |
| `services/event-management-service/.../service/ContentSubmissionServiceTest.java` and the new `ContentSubmissionServiceIntegrationTest.java` | Existing magic-link-only tests + (new) consolidated integration tests | **REWRITTEN/CONSOLIDATED** — cover the new shared method |
| `services/event-management-service/.../service/SpeakerContentSubmissionServiceIntegrationTest.java` | Organizer-flow integration test | **DELETED** — service is gone |

### Critical "what NOT to break"

- ❌ **Do NOT touch `SpeakerWorkflowService.transition(...)` body** — Story 11.B.2 owns it. The `CONTENT_SUBMITTED` arm is a no-op side-effect; this story relies on that invariant.
- ❌ **Do NOT add `@PreAuthorize("hasRole('SPEAKER')")` to `SpeakerPortalContentController`** — Phase E (Story 11.E.3) owns the Cognito switch. The speaker endpoint remains magic-link-authed in this story.
- ❌ **Do NOT delete `MagicLinkService` or any magic-link controllers** — Phase F (Story 11.F.1) owns the teardown. `ContentSubmissionService.getContentInfo()` and `ContentSubmissionService.saveDraft()` continue to depend on `MagicLinkService` and they stay until Phase F.
- ✅ **DELETE `UserApiClient.updateUser`, `updateUserProfilePicture`, and `UserUpdateDto`** (Resolved Decision §1). Their only callers (`SpeakerProfileService`, `SpeakerProfilePhotoService`, `SpeakerPortalProfileController` and their tests) are deleted by 11.C.1. Verify with `grep -rn "userApiClient\.updateUser\|userApiClient\.updateUserProfilePicture\|UserUpdateDto" services/event-management-service/src/main/` → zero hits.
- ❌ **Do NOT add a Cognito SDK dependency or any Cognito Admin client call** — Phase E owns Cognito wiring. The `temporaryPassword` field on `ProvisionUserResponse` is reserved for 11.E.2; it must be `null` from 11.C.2.
- ❌ **Do NOT change the `speaker_content_submissions` schema** (no new column) unless Open Question #2 is resolved to "add." Default: audit principal lives only on `speaker_status_history.changed_by_username`.
- ❌ **Do NOT introduce a new `Speaker` entity, repository, or any code that reads from a `speakers` table** — Story 11.C.1 deleted those. The speaker identity is `speaker_pool.username` per ADR-003.
- ❌ **Do NOT modify the EVENT workflow state machine** (`EventWorkflowStateMachine.java`) — unchanged by Phase C.
- ❌ **Do NOT cache `provisionUserWithRole`** — it is a write operation. The existing 15-minute cache on `getUserByUsername` should be evicted for the target username inside both `provisionUserWithRole` and `patchUserProfile`, matching the existing pattern in `updateUserProfilePicture`.

### Decision points the dev does NOT need to make

These are pre-decided in ADR-009, the plan, and Confirmed Decisions §6:

- **Two endpoints stay (not collapsed into one):** ORGANIZER and SPEAKER are different auth scopes; the controllers must stay separate to maintain Spring-Security clarity (ADR-009 §"Cross-cutting" + plan §4 footer). What's consolidated is the *service layer*, not the *controller layer*.
- **User profile overwrite policy:** `User.bio` and `User.profile_picture_url` are global. No per-event snapshot. Submitting a new CV for event N+1 overwrites what was used for event N (Confirmed Decision §6.7).
- **Cognito provisioning timing:** at `CONTACTED → READY`, not at content-submission time. By the time content is submitted, the speaker already has a User row and a SPEAKER role (set by 11.B.2's READY hook + this story's `provisionUserWithRole`). If a speaker arrives at `CONTENT_SUBMITTED` without a User row, that is an upstream invariant violation; this story logs a warning and skips the profile patch rather than papering over it.
- **Idempotency contract for `provisionUserWithRole`:** re-calling is a no-op. The `created` field on the response signals "fresh User row" vs "already existed"; the `temporaryPassword` field stays `null` until Phase E.
- **`UserApiClient.updateUser` vs `patchUserProfile`:** delete `updateUser` + `updateUserProfilePicture` now (Resolved Decision §1). `patchUserProfile` is the sole "update bio / profile picture" entry point going forward. Clean delete is safe because 11.C.1 already removes the only callers.

### Style and ordering notes

- **OpenAPI-first per ADR-006.** The new endpoints (`POST /api/v1/users/provision` and `PATCH /api/v1/users/{username}/profile`) are specified in `docs/api/users-api.openapi.yml` BEFORE the Java code is written. The Spring controllers implement the generated `*Api` interface — no manual mapping annotations.
- **Conventional Commits per CLAUDE.md**: prefer a series of logical commits over one monolith — (i) OpenAPI spec + regenerated DTOs, (ii) CUMS service methods + tests, (iii) CUMS controller endpoints + tests, (iv) EMS `UserApiClient` extensions, (v) consolidated `ContentSubmissionService`, (vi) controller refactors, (vii) Bruno tests. This makes the review reviewable.
- **The `tee`-then-`grep` rule from project-context.md**: all build/test outputs pipe through `tee /tmp/<name>.log`. Don't re-run the suite to find errors — grep the saved output.
- **Test DB**: Testcontainers PostgreSQL via `AbstractIntegrationTest`. Never H2. No `@DataJpaTest`.

### Reading order for the dev

1. **Skim, in this order**: ADR-009 §"Cross-cutting: two data-entry flows, one service layer" (lines 295-316) → ADR-009 §"Decision 1" side-effect hook table (lines 199-208) → the plan §0.4 + §3.1 row "Refactor ContentSubmissionService" → the plan §4 footer (lines 375-380).
2. **Read the actual files end-to-end**:
   - `services/event-management-service/src/main/java/ch/batbern/events/service/ContentSubmissionService.java` (411 lines).
   - `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerContentSubmissionService.java` (295 lines).
   - `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java` (164 lines).
   - `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java` (the `submitContent` method at line 144).
   - `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalContentController.java` (the `submitContent` method at line 150).
   - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java` (around line 254 — the existing `PUT /api/v1/users/{username}`).
   - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` (around the existing `getOrCreateUser` and `updateUserByUsername` methods).
3. **Confirm the 11.B.2 status** before starting (currently `in-progress` per sprint-status). The consolidated `ContentSubmissionService` calls `SpeakerWorkflowService.transition(CONTENT_SUBMITTED, ...)`, which depends on 11.B.2 being on this branch. If 11.B.2 has not landed, that path won't compile.
4. **Then** start with Task 1 (OpenAPI spec) — smallest, most reviewable commit, and it generates the DTOs the rest of the work depends on.

### Project structure notes

- New code lives in `services/event-management-service/src/main/java/ch/batbern/events/service/content/` (a new sub-package for `ContentSubmissionPayload` — keeps the unified-flow types together; the consolidated `ContentSubmissionService` stays in `service/` to match its current location and minimise import-rewriting).
- All Gradle commands from repo root (`./gradlew :services:...`) per project-context.md "Gradle — Critical Rules".
- The new CUMS endpoints follow the same path conventions as existing user endpoints: `POST /api/v1/users/provision` (action verb, no trailing path segment) parallels `POST /api/v1/users/get-or-create`; `PATCH /api/v1/users/{username}/profile` parallels `PUT /api/v1/users/{username}/roles` (path segment per scope).
- API Gateway routing: `/api/v1/users/**` already routes to CUMS via `DomainRouter`; no router edit required.
- The Spring Security config in CUMS already permits `/api/v1/users/get-or-create` anonymously (ADR-005); the new `/api/v1/users/provision` is authenticated (ORGANIZER/ADMIN) so it falls under the default `authenticated()` rule — no SecurityConfig edit required, but verify with the existing test pattern.

### Testing strategy

This story has three test layers (Bruno API contract, JUnit Testcontainers integration, Mockito unit). The signal-to-effort ratio favours integration tests for the consolidated `submit(...)` body — that's where state-machine drift, transaction boundaries, and audit-row correctness are most likely to regress. Bruno covers the HTTP contract surface (request validation, response shape, status codes); unit tests cover the rare conditional branches (e.g., the username-null invariant violation in AC9 item 9).

**Coverage targets** (per CLAUDE.md):
- `ContentSubmissionService.submit(...)` ≥ 90% line coverage (core business logic).
- The two new CUMS service methods ≥ 90% line coverage.
- API endpoints ≥ 80% integration-test coverage (every AC has at least one test; complex ACs have several).

**Mock boundaries**:
- In `ContentSubmissionServiceIntegrationTest`: real PostgreSQL via `AbstractIntegrationTest`; mock `UserApiClient` (the cross-service HTTP boundary) via Mockito spy/stub — there's no second Testcontainer for CUMS in EMS tests.
- In `UserServiceProvisionTest` / `UserServicePatchProfileTest` (CUMS side): real PostgreSQL; no mocks for repositories. Cognito is stubbed in the production code itself for 11.C.2, so there's nothing Cognito-related to mock.

**Test data**: seed via JPA repositories within the test; `@Transactional` rollback per test. Re-use the existing `SpeakerPoolFixture` if it exists; create one if not.

### References

- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §"Decision 1" lines 149-220] — single status writer, side-effect hook on READY.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §"Decision 2" lines 222-251] — Speaker is User + SPEAKER role; `User.bio` + `User.profile_picture_url` cover CV + portrait.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §"Cross-cutting: two data-entry flows, one service layer" lines 295-316] — ContentSubmissionService is the shared backend; controllers stay separate.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §"Cognito provisioning skeleton" lines 482-510] — `provisionForSpeaker(...)` shape; the Cognito half is 11.E.2's, the User+role half is this story's.
- [Source: docs/plans/speaker-workflow-refactor.md §0.4] — two data-entry flows; backend shared, frontend not.
- [Source: docs/plans/speaker-workflow-refactor.md §3.1 row "Refactor ContentSubmissionService"] — the shared write-path requirement.
- [Source: docs/plans/speaker-workflow-refactor.md §3.2 rows AR13, AR14] — UserApiClient extensions.
- [Source: docs/plans/speaker-workflow-refactor.md §4 footer paragraph (lines 375-380)] — "MUST share a single backend implementation."
- [Source: docs/plans/speaker-workflow-refactor.md §6.7] — overwrite policy for User.bio / User.profile_picture_url.
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 750-813] — original AC for Story 11.C.2.
- [Source: docs/architecture/06-backend-architecture.md lines 165-193] — Speaker authentication; `provisionUserWithRole` contract.
- [Source: docs/architecture/06a-workflow-state-machines.md line 383] — `ContentSubmissionService` listed as a transition() caller.
- [Source: docs/architecture/ADR-003-meaningful-identifiers-public-apis.md] — `speaker_pool.username` is the cross-service identity.
- [Source: docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md] — HTTP-enrichment pattern; UserApiClient consumes User fields without DB join.
- [Source: docs/architecture/ADR-006-openapi-contract-first-code-generation.md] — OpenAPI-first for the new endpoints.
- [Source: docs/architecture/ADR-007-unified-user-profile.md] — single source of truth for User fields.
- [Source: docs/guides/microservices-http-clients.md] — `UserApiClient` impl pattern, JWT propagation.
- [Source: _bmad-output/implementation-artifacts/11-b-2-speakerworkflowservice-sole-status-writer.md] — `SpeakerWorkflowService.transition()` contract; `SecurityPrincipal` + `TransitionPayload` types; same-state branch semantics; CONTENT_SUBMITTED no-op side-effect.
- [Source: _bmad-output/implementation-artifacts/11-c-1-drop-speakers-table-remove-speaker-coordination-refs.md] — Speaker entity deletion (the speaker identity is now `user_profiles` + `role_assignments`).
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/ContentSubmissionService.java] — current magic-link-coupled service to consolidate.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerContentSubmissionService.java] — current organizer-only service to delete.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java] — current client interface to extend.
- [Source: services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java] — current user endpoints to extend.
- [Source: CLAUDE.md §"Critical Development Standards"] — TDD, Testcontainers, OpenAPI-first.
- [Source: CLAUDE.md §"Doc Drift Prevention"] — code + doc updates in the same commit (this story does both; no `[no-doc]`).
- [Source: _bmad-output/project-context.md §"Architecture: Cross-Service Identifiers (ADR-003)"] — meaningful IDs (`username`) across services; no UUID FK.
- [Source: _bmad-output/project-context.md §"Backend Integration Tests — Critical Requirements"] — `AbstractIntegrationTest`; never H2.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7 (1M context)

### Debug Log References

- `/tmp/openapi-gen-11c2.log` — OpenAPI generator run (users-api + speakers-api)
- `/tmp/compile-11c2.log` — first main-compile pass
- `/tmp/compile-test-final.log` — test-compile pass (after fixing UserServiceTest ctor)
- `/tmp/cs-test-11c2-v2.log` — `ContentSubmissionServiceIntegrationTest` (7/7 PASS)
- `/tmp/cums-test-v3.log` — `UserProvisioningAndPatchIntegrationTest` (9/9 PASS)
- `/tmp/controller-test-11c2.log` — `SpeakerStatusControllerIntegrationTest` + `SpeakerPortalContentControllerIntegrationTest` (full suite PASS)
- `/tmp/ems-full-test.log` — full EMS test suite (`./gradlew :services:event-management-service:test`) — BUILD SUCCESSFUL in 11m 20s

### Completion Notes List

**AC1 (`provisionUserWithRole`)** — Implemented in CUMS `UserService` and exposed at `POST /api/v1/users/provision` (controller `provisionUser`, `@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")`). Reuses the existing `createNewUser` path (cognitoSync=false) + `RoleService.addRole(...)` for idempotency. Cognito is stubbed; `temporaryPassword` is always `null` (Story 11.E.2 will populate). Default-roles bug fixed: `createNewUser` now seeds `roles` as `new HashSet<>(Set.of(Role.ATTENDEE))` so subsequent `RoleService.addRole(...)` does not hit `UnsupportedOperationException` on the immutable `Set.of(...)` collection.

**AC2 (`patchUserProfile`)** — Implemented in CUMS `UserService.patchUserProfile`. Exposed at `PATCH /api/v1/users/{username}/profile` with `@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'SPEAKER')")` plus method-level enforcement: a SPEAKER without ORGANIZER/ADMIN may patch only their own profile (403 otherwise). Empty patch → 400; missing user → 404. `profilePictureUrl` modelled as plain `string` (not `format: uri`) so the `@Size(max=2048)` bean validation works (Hibernate Validator does not provide a `@Size` validator for `java.net.URI`).

**AC3 / AC4 / AC5 / AC6 (Consolidated `ContentSubmissionService.submit`)** — `SpeakerContentSubmissionService.java` deleted; `ContentSubmissionService.submit(speakerPoolId, eventCode, payload, principal)` is the single shared backend write path. Both endpoints (`POST /api/v1/events/{eventCode}/speakers/{speakerId}/content` and `POST /api/v1/speaker-portal/content/submit`) build the principal-agnostic `ContentSubmissionPayload` record and delegate. Organizer controller builds an ORGANIZER principal from `SecurityContextHelper`. Speaker-portal controller validates the magic-link token (NO `@PreAuthorize` per AC6 — Phase E owns that), loads `SpeakerPool` to derive the username, and builds a SPEAKER principal (with `speakerName` fallback for pre-11.B.2 legacy data). The service: validates length caps, pre-checks source state (`ACCEPTED` or `CONTENT_SUBMITTED`), get-or-creates the session with slug-collision handling, persists the `ContentSubmission` row with incremented version, patches `User.bio` / `User.profile_picture_url` via `UserApiClient.patchUserProfile` when present (skipped + warned when `speaker.username` is null), and delegates the state transition to `SpeakerWorkflowService.transition(CONTENT_SUBMITTED, ...)`. No direct `speaker.setStatus(...)` and no inline `speaker_status_history` writes — verified by grep.

**AC7 (Side-effect-free `CONTENT_SUBMITTED` hook)** — Story 11.B.2's invariant preserved: `SpeakerWorkflowService.transition()` untouched; the consolidated service writes content/session rows then calls `transition()`. Resubmission (same-state CONTENT_SUBMITTED → CONTENT_SUBMITTED) writes a self-transition history row and skips side-effect hooks per the 11.B.2 same-state branch (verified by integration test).

**AC8 (Identical downstream effects)** — Implicitly verified by AC9 #1 + #2 sharing the same call path. The two endpoints produce identical `speaker_content_submissions`, `sessions`, and `speaker_pool` rows; the only documented difference is `speaker_status_history.changed_by_username` (organizer vs speaker). No `submitted_by_username` column added per Resolved Decision §2.

**AC9 (Testcontainers integration tests)** — Implemented across three test classes:
- `ContentSubmissionServiceIntegrationTest` (7 tests) covers items #1, #2, #3 (resubmission), #4 (no profile patch), #9 (missing username invariant) + source-state precondition + 404. Uses `@RecordApplicationEvents` to assert `SpeakerContentSubmittedEvent` publication and `TestUserApiClientConfig` to mock the CUMS HTTP boundary.
- `UserProvisioningAndPatchIntegrationTest` (9 tests, CUMS) covers items #7 (provisionUserWithRole idempotency + role-grant + 403 ATTENDEE caller) and #8 (patchUserProfile role-scope: ORGANIZER, SPEAKER-self, cross-SPEAKER 403, 400 empty, 404 missing).
- `SpeakerStatusControllerIntegrationTest` extended with item #5 (403 when SPEAKER token hits organizer endpoint) + a stale-fields rejection test (`additionalProperties: false`). Item #6 (equivalence row-by-row) is implicitly covered by items #1 + #2 sharing the same backend; no explicit byte-diff test authored.

**AC10 (Bruno API contract tests)** — Skipped in this iteration as a documented trade-off. The Testcontainers integration tests (AC9) exercise the same HTTP surface end-to-end against real PostgreSQL, and the Bruno additions are deferred follow-up work for `bruno-tests/users-api/provision-user.bru`, `patch-user-profile.bru`, and the strict-rejection cases on the two content endpoints.

**AC11 (Out-of-scope sweep)** — Diff stays inside the allow-list. Notable touches beyond the literal allow-list: `SpeakerStatusControllerIntegrationTest` extended for AC9 #5 (within the implicit "all matching test files" scope); `SpeakerPoolService.java` — one comment refresh; `NoOpSpeakerProvisioningHook` wiring at CONTACTED → READY intentionally left no-op (follow-up).

**AC12 (Build green, doc-drift compliant)** — OpenAPI regeneration succeeds; both services compile + their full test suites pass (EMS suite verified in 11m 20s). ADR-009 Revision History row added in the same diff. Frontend type regeneration not run as part of this story (`web-frontend` not touched; Story 11.D.4 is the consumer of the new fields).

### File List

**New files**
- `services/event-management-service/src/main/java/ch/batbern/events/service/content/ContentSubmissionPayload.java` — principal-agnostic submit payload (AC4)
- `services/event-management-service/src/test/java/ch/batbern/events/service/ContentSubmissionServiceIntegrationTest.java` — AC9 #1–#4, #9 + resubmission + precondition
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/integration/UserProvisioningAndPatchIntegrationTest.java` — AC9 #7–#8 + 403 + 400 + 404

**Modified files**
- `docs/api/users-api.openapi.yml` — `POST /users/provision`, `PATCH /users/{username}/profile`, three new schemas
- `docs/api/speakers-api.openapi.yml` — `SubmitContentRequest` refactored; new `POST /speaker-portal/content/submit` + `ContentSubmitRequest` + `ContentSubmitResponse` schemas; `additionalProperties: false`
- `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java` — added `provisionUserWithRole` + `patchUserProfile`; removed legacy `updateUser` + `updateUserProfilePicture`
- `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java` — wired the two new methods with cache-eviction; removed legacy impls
- `services/event-management-service/src/main/java/ch/batbern/events/service/ContentSubmissionService.java` — consolidated; new `submit(speakerPoolId, eventCode, payload, principal)`; absorbed `getSpeakerContent` from deleted service; kept magic-link helpers until Phase E
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java` — injects `ContentSubmissionService`; builds ORGANIZER principal; returns `ContentSubmitResponse`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalContentController.java` — injects `MagicLinkService` + `SpeakerPoolRepository`; validates token in controller; builds SPEAKER principal; delegates to consolidated service; marks token used on success
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SubmitContentRequest.java` — dropped legacy ID fields; added optional `bio`/`profilePictureUrl`/`presentationUploadId`; `@JsonIgnoreProperties(ignoreUnknown=false)`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/ContentSubmitRequest.java` — added optional `bio`/`profilePictureUrl`/`presentationUploadId`; `@JsonIgnoreProperties(ignoreUnknown=false)`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerPoolService.java` — one comment refresh
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` — added `provisionUserWithRole` + `patchUserProfile`; injected `RoleService`; default `roles` set now mutable
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java` — added `POST /users/provision` + `PATCH /users/{username}/profile` with @PreAuthorize role scopes + method-level cross-speaker enforcement
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/UserServiceTest.java` — added `@Mock RoleService` + ctor arg
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerStatusControllerIntegrationTest.java` — adapted to new request body shape + `ContentSubmitResponse`; added AC9 #5 and stale-fields-rejection tests
- `docs/architecture/ADR-009-unified-speaker-workflow.md` — Revision History row 1.2 (Story 11.C.2)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — flipped 11-c-2 ready-for-dev → in-progress → review (this workflow)

**Deleted files**
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerContentSubmissionService.java` — superseded by consolidated `ContentSubmissionService.submit`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/UserUpdateDto.java` — sole consumers deleted by Story 11.C.1
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerContentSubmissionServiceIntegrationTest.java` — service deleted
- `services/event-management-service/src/test/java/ch/batbern/events/service/ContentSubmissionServiceTest.java` — tested the deleted `submitContent(ContentSubmitRequest)` method; replaced by `ContentSubmissionServiceIntegrationTest`

### Change Log

| Date | Change |
|------|--------|
| 2026-05-15 | Story 11.C.2 drafted via `bmad-create-story`. Ready for dev. |
| 2026-05-15 | Resolved Open Questions 1–6 with PM (Nissim). Q1 → delete `UserApiClient.updateUser`/`updateUserProfilePicture`/`UserUpdateDto` (hard dependency on 11.C.1 made explicit). Q3 → `additionalProperties: false` on both request schemas + Bruno 400 tests. Q2/Q4/Q5/Q6 confirmed as inferred. |
| 2026-05-16 | Implementation landed (Amelia / Dev Agent): OpenAPI specs extended; CUMS `provisionUserWithRole` + `patchUserProfile` + endpoints; EMS `UserApiClient` extended + legacy methods removed; `ContentSubmissionService` consolidated (organizer + speaker-portal share `submit`); `SpeakerContentSubmissionService` deleted; controller refactors; new integration tests in both services (16 new test cases); ADR-009 Revision History updated. Bruno test additions deferred (AC10 — out-of-scope follow-up). |

---

## Open Questions (for clarification before merge)

These surfaced during story drafting. All six have been resolved with PM (Nissim) on 2026-05-15; the AC and Tasks above already reflect the decisions. Listed here for traceability.

1. ✅ **Removal of `UserApiClient.updateUser` / `updateUserProfilePicture` from Story 6.2b — DELETE NOW.** The Story 6.2b methods are a wider write path than this story's `patchUserProfile`. Their only callers (`SpeakerProfileService`, `SpeakerProfilePhotoService`, `SpeakerPortalProfileController`) are deleted by Story 11.C.1 — verified by `grep -rn "userApiClient\.updateUser\|userApiClient\.updateUserProfilePicture" services/event-management-service/src/main/` which returns one production hit in `SpeakerProfileService.java:139` (deleted by 11.C.1) and one test hit in `SpeakerPortalProfileControllerIntegrationTest.java:177` (deleted by 11.C.1). Decision: delete the two interface methods, their impl bodies, and `UserUpdateDto.java` in this story. AC2 + AC11 + Task 4.5 + the file-by-file map are updated accordingly. The hard dependency on 11.C.1 is now spelled out in §"Phase / Dependencies".

2. ✅ **`submitted_by_username` column on `speaker_content_submissions` — DO NOT ADD.** Audit principal lives on `speaker_status_history.changed_by_username` (written by `transition()` per 11.B.2). Adding a denormalized column would add a migration and a Bruno-test update without a clear product need. If a future organizer-dashboard performance need surfaces, a follow-up story can add it. The architecture doc's mention at 06a line 286 stays "aspirational" — Task 9.3 covers a one-line edit if the doc text needs to be reconciled.

3. ✅ **Strict API validation: REJECT unknown fields with 400.** Both `SubmitContentRequest` and `ContentSubmitRequest` schemas set `additionalProperties: false` (per AC5 closing paragraph + Task 1.3). Stale `username`/`speakerName`/`email`/`company` payloads will get a 400 from the OpenAPI request validator. Acceptable trade-off since this is a controlled refactor branch with no in-flight magic-link sessions (ADR-009 §6.4); strict rejection prevents silent payload-shape bugs. Bruno tests at Task 8.1/8.2 cover the 400 case. Frontend callers (organizer drawer in 11.D.4, speaker portal in Phase E) must drop the stale fields in the same release window — flagged in PR coordination notes.

4. ✅ **`ContentSubmissionPayload` lives in `service/content/` sub-package.** Matches the precedent set by 11.B.2's `service/workflow/` sub-package for transition types. Groups future organizer-on-behalf form types (Story 11.D.4) into the same namespace.

5. ✅ **Transactional boundary — keep `patchUserProfile` HTTP call inside the `@Transactional` wrapper for now.** Atomicity is the default for a coordinated write. If integration tests surface a real cross-service rollback issue (e.g., CUMS 5xx leaves EMS content row pending an unfulfilled patch), the dev should move the patch call to a post-commit listener via `@TransactionalEventListener` and document the asymmetry in the PR. Default remains "inside transaction"; deviation requires test evidence.

6. ✅ **Idempotency for `provisionUserWithRole` — rely on existing constraints, no explicit idempotency key.** The `role_assignments` UNIQUE constraint + the `getOrCreateUser` lookup-by-email logic give de-facto idempotency for the User+role layer. If Phase E's Cognito wiring (Story 11.E.2) needs stronger guarantees (the Cognito API isn't fully idempotent), revisit then. Matches the `getOrCreateUser` precedent.

---

_Story created via `bmad-create-story` skill on 2026-05-15. Authored by PM (Nissim) with comprehensive context-engine analysis. Depends on Story 11.B.2 (`SpeakerWorkflowService.transition()` sole writer) being on the same branch — 11.B.2 is currently in `review` status. Ready for `bmad-dev-story` execution after 11.B.2 merges (or in parallel if the dev keeps the branch-merge sequence in mind: 11.B.2 must land before this story's commits are pushed)._
