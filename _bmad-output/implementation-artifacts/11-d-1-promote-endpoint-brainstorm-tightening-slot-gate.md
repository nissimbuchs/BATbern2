# Story 11.D.1: Promote-to-READY endpoint + brainstorm-panel tightening + slot-capacity gate

Status: done

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As an** organizer running the speaker-coordination kanban,
**I want** a clear "this is a real, invitable speaker" moment that captures the email and provisions the User, **and** I want the system to stop me from inviting more speakers than I have slots,
**So that** brainstorming and concrete-invitation states stay distinct and I never oversubscribe.

## Phase / Dependencies / Requirements Covered

- **Phase:** D — Workflow semantics update + organizer UX (first of four D stories).
- **Depends on (strict sequencing — must land in order):**
  1. **Story 11.B.2** — `SpeakerWorkflowService.transition()` is the sole status writer with the READY hook (provisioning seam) and INVITED precondition (slot-capacity gate). **Already landed** on `feature/speaker-workflow-refactor` (commit `c53d02c8`). The slot-capacity gate at INVITED already throws `SlotCapacityReachedException` (mapped to HTTP 409 by `GlobalExceptionHandler`).
  2. **Story 11.C.2** — `UserApiClient.provisionUserWithRole(...)` + `ContentSubmissionService` consolidation. **Must merge BEFORE this story.** Per Resolved Open Question #1 (2026-05-16, decided by PM): the READY hook in `SpeakerWorkflowService` is refactored by 11.C.2 to call `provisionUserWithRole(...)` in place of the current `getOrCreateUser(...) + speakerProvisioningHook.grantSpeakerRole(...)` pair. Story 11.D.1's `/promote` endpoint observes the post-11.C.2 hook behaviour — i.e., the test assertions for AC1 verify that `provisionUserWithRole` is the provisioning call (matching the strict PRD wording at epic-11 line 841).
- **Requirements covered:** FR3 (User-provisioning part — Cognito comes in Phase E), FR4 (slot-capacity gate at invitation), FR12 (promote endpoint is the only path that sets `username`), AR22 (new `POST /promote` endpoint), AR23 (tighten `POST /pool` to reject email), UX-DR15 (no email field on IDENTIFIED/CONTACTED form), UX-DR16 ("Promote to speaker" modal).
- **Plan / ADR anchors:**
  - Epic 11 PRD §"Story 11.D.1" lines 820-892 — primary AC list this story implements.
  - ADR-009 §0.2 — `CONTACTED → READY` is the provisioning gate; `READY → INVITED` blocked by slot-capacity.
  - `docs/plans/speaker-workflow-refactor.md` §4 row "`POST /api/v1/events/{code}/speakers/{speakerId}/promote`" + row "`POST /api/v1/events/{code}/speakers/pool` — **Tighten** — reject `email` payload".
  - `docs/architecture/04-api-speaker-coordination.md` — already documents the new `/promote` endpoint shape per Story 11.A.1.

---

## Acceptance Criteria

The AC are pinned to the Epic 11 PRD Story 11.D.1 (lines 820-892), ADR-009 §0.2, and the actual file locations confirmed on `feature/speaker-workflow-refactor`. Each AC names the exact file under change. Slot-capacity AC4 verifies an existing-but-untested behaviour (Story 11.B.2 added the precondition; this story is the first to surface it through a documented HTTP contract).

### AC1 — New endpoint `POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote` exists and drives `CONTACTED → READY` via the workflow service (AR22, FR3, FR12)

**Given** the OpenAPI spec `docs/api/events-api.openapi.yml`,
**When** I read the paths,
**Then** a new path entry `/events/{eventCode}/speakers/{speakerId}/promote` exists with a single `post` operation, `operationId: promoteSpeakerToReady`, tags `[Event Actions]`, `security: [{ BearerAuth: [] }]`, parameters `eventCode` (path, `^BATbern[0-9]+$` pattern) and `speakerId` (path, UUID).

**And** the request body schema `#/components/schemas/PromoteSpeakerRequest` has exactly these fields:

| Field | Type | Required | Validation |
|---|---|---|---|
| `email` | string | **yes** | `format: email`, `maxLength: 320` |
| `firstName` | string | no | `maxLength: 100` |
| `lastName` | string | no | `maxLength: 100` |

**And** the schema has `additionalProperties: false` (consistent with the strict-rejection pattern Resolved Decision §3 of Story 11.C.2 established for refactor-branch endpoints).

**And** the response schema for `200 OK` is the existing `#/components/schemas/SpeakerPoolResponse` (with `status: 'ready'`, `username: <non-null>`, `email: <the submitted email>` populated).

**And** the documented error responses include: `400` (validation — missing/invalid email, malformed body, or unknown fields), `401` (no auth), `403` (not ORGANIZER), `404` (event or speaker not found), `409` (speaker already in READY or later — same-state or invalid-transition), `500` (internal).

**And** a new controller method exists at `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java` (the file already owns `PUT /status` and other per-speaker actions per Story 11.B.2 — co-located there to keep all transition-driving endpoints in one controller):

```java
@PostMapping("/{speakerId}/promote")
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<SpeakerPoolResponse> promoteSpeakerToReady(
        @PathVariable String eventCode,
        @PathVariable UUID speakerId,
        @Valid @RequestBody PromoteSpeakerRequest request) { ... }
```

**And** a new DTO `services/event-management-service/src/main/java/ch/batbern/events/dto/PromoteSpeakerRequest.java` exists with the three fields above, Bean Validation annotations (`@NotBlank`, `@Email`, `@Size` matching the OpenAPI maxLength), and Jackson `@JsonIgnoreProperties(ignoreUnknown = false)` (or rely on the OpenAPI-generated DTO if the dev opts to generate; matches the project's contract-first pattern per ADR-006).

**And** the controller method body:

1. Builds a `SecurityPrincipal actor` from `SecurityContextHelper.getCurrentUsername()` + `getCurrentUserRoles()` (matches the pattern Story 11.B.2 established in `SpeakerStatusService.updateStatus` per its AC6).
2. Builds a `TransitionPayload payload = TransitionPayload.builder().email(request.email()).firstName(request.firstName()).lastName(request.lastName()).build();`.
3. Calls `speakerWorkflowService.transition(speakerId, SpeakerWorkflowState.READY, actor, payload)`.
4. Maps the returned `TransitionResult` to a `SpeakerPoolResponse` and returns `200 OK`.

**And** the controller does **NOT** call `userApiClient.*` directly, does **NOT** call `speaker.setUsername(...)` directly, does **NOT** write any history row directly — all provisioning, persistence, and audit-trail concerns flow through `transition()` per Story 11.B.2 AC1.

**And** when the call succeeds, the observable downstream state is:
  - `speaker_pool.status = 'ready'`
  - `speaker_pool.username` is populated with the canonical username returned from `UserApiClient.provisionUserWithRole(...)` (added by Story 11.C.2 — strict prereq per the Dependencies section above)
  - `speaker_pool.email = request.email`
  - A new User row in CUMS exists (if not already present by email) with the SPEAKER role granted (idempotent — re-promote on the same email is a no-op for the User+role layer per 11.C.2's NFR3)
  - A `speaker_status_history` row with `previous_status = 'contacted'`, `new_status = 'ready'`, `changed_by_username = <organizer's username>`
  - `SpeakerWorkflowStateChangeEvent` and `SpeakerPromotedToReadyEvent` are published
  - The integration test (AC9 item 1) verifies via a Mockito spy on `UserApiClient.provisionUserWithRole` that it was called exactly once with `(email, firstName?, lastName?, role="SPEAKER")` per Story 11.C.2's `ProvisionUserRequest` contract

---

### AC2 — Promote endpoint rejects missing email with HTTP 400 (FR3)

**Given** the new endpoint is wired per AC1,
**When** I `POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote` with body `{}` or `{ "email": "" }` or `{ "email": "   " }` or `{ "email": "not-an-email" }`,
**Then** the response status is `400 Bad Request`,
**And** the response body is the project's standard `ErrorResponse` shape (per `GlobalExceptionHandler`'s `MethodArgumentNotValidException` handler — per project-context.md "ALWAYS add explicit `@ExceptionHandler(MethodArgumentNotValidException.class)`"; that handler already exists from Epic 1 — do not modify),
**And** the response identifies `email` as the invalid field (Bean Validation surfaces the field name automatically),
**And** the speaker is **not modified** (no status change, no history row written, no events published, no User created in CUMS, no Cognito call). Verify by reading `speaker_pool.status` and counting `speaker_status_history` rows before/after — must be equal.

**And** the same 400 fires if the request includes unknown fields (`additionalProperties: false` per AC1; e.g., a stale `username` field from a pre-refactor frontend gets rejected rather than silently ignored).

---

### AC3 — Promote endpoint returns HTTP 409 when speaker is already in READY or beyond

**Given** a speaker with `status ∈ {READY, INVITED, ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED}`,
**When** I `POST .../promote` with a valid email,
**Then**:
- If `status == READY` → Story 11.B.2's same-state branch fires inside `transition()` (writes a self-transition history row, no precondition/side-effect/events). The endpoint returns `200 OK` with the unchanged `SpeakerPoolResponse`. **This is intentional** — re-affirming a READY speaker is an audit-meaningful no-op per 11.B.2 AC2. Frontend SHOULD avoid posting this redundantly, but the endpoint does not error on it (treat as idempotent). Document this in the OpenAPI description text.
- If `status ∈ {INVITED, ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED}` → `transition()` throws `InvalidStateTransitionException` (no allow-list entry from those states to READY). The exception maps to `400 Bad Request` by the existing handler in `GlobalExceptionHandler.handleInvalidStateTransition` (verify the mapping; if it currently maps to a non-409 status, **add** an explicit mapping or rethrow as a 409-mapped exception to match the PRD's "409 Conflict with a body explaining the speaker has already been promoted").

**Decision for this story (per Open Question #2):** add a controller-level pre-check that loads the speaker and returns `409 Conflict` with a body explaining "speaker is already in {state}; promote is only valid from CONTACTED" when `status ∈ {INVITED, ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED}`. This avoids relying on the generic `InvalidStateTransitionException` → 400 mapping and gives a clean UX message. The check happens **before** `transition()` is called.

**And** for `status == DECLINED` (the only other allowed state on `speaker_pool` post-11.B.1) → the controller returns the same `409 Conflict` with "speaker is DECLINED; cannot be promoted; create a new pool entry instead". `transition()` would throw `InvalidStateTransitionException` anyway (DECLINED is terminal in the allow-list), so the pre-check is a UX nicety.

**And** for `status == IDENTIFIED` → the controller returns `409 Conflict` with "speaker must be in CONTACTED before promoting; log outreach first". (The PRD's flow assumes the organizer has already moved the speaker IDENTIFIED → CONTACTED via the outreach panel; promoting directly from IDENTIFIED would skip that step. `transition()` would reject `IDENTIFIED → READY` as not-allowed anyway.)

**And** the response body for the 409 includes the current state name in the `details` map: `{ "code": "INVALID_PROMOTION_STATE", "currentState": "<state>" }` so the frontend can render a tailored message.

---

### AC4 — `POST /api/v1/events/{eventCode}/speakers/{speakerId}/invite` for a READY speaker returns HTTP 409 with documented body when slot capacity is reached (FR4, AR22)

**Given** Story 11.B.2 already enforces the slot-capacity gate inside `SpeakerWorkflowService.transition()` at the INVITED precondition (file `SpeakerWorkflowService.java:201-210`; throws `SlotCapacityReachedException`),
**And** `GlobalExceptionHandler.handleSlotCapacityReachedException` already maps this exception to HTTP 409 Conflict with a `details` map containing `eventId`, `acceptedCount`, `invitedCount`, `maxSlots` (file `GlobalExceptionHandler.java:795-820`),
**When** I `POST /api/v1/events/{eventCode}/speakers/{username}/send-invitation` (the existing endpoint at `SpeakerInvitationController.java:101-114` — see Open Question #3 for the endpoint-naming note) for a speaker in `READY` and `count(ACCEPTED) + count(INVITED) >= max_slots` for the event,
**Then** the response status is `409 Conflict`,
**And** the response body matches the existing `ErrorResponse` shape with `error: "Conflict"`, `status: 409`, `message: "Slot capacity reached for event {eventId}: {accepted} accepted + {invited} invited >= {maxSlots} slots"`, and a `details` map containing `code: "SLOT_CAPACITY_REACHED"`, `eventId`, `acceptedCount`, `invitedCount`, `maxSlots`,
**And** the speaker is not modified (no status change to INVITED, no history row, no invitation email sent, no domain event published) — verified by `SpeakerWorkflowService.transition()` rolling back inside its `@Transactional` boundary before step 6 (persist) per Story 11.B.2 AC3 ordering.

**And** when capacity is available (`count(ACCEPTED) + count(INVITED) < max_slots`):
- The transition succeeds (READY → INVITED).
- The existing `runInvitedHook` (Story 11.B.2) fires: magic-link tokens are generated, invitation email is sent (via existing `SpeakerInvitationEmailService` — do not modify the email template; Phase E rewrites it), `speaker_pool.invited_at` is stamped.
- The response is `200 OK` with the existing `SendInvitationResponse` shape.

**And** this AC is **observation-only** for the slot-capacity precondition itself — the gate code lives in Story 11.B.2 and is not modified here. This story's contribution is (a) the OpenAPI documentation of the 409 response on `/send-invitation`, and (b) the Bruno + integration tests that verify the 409 surfaces correctly end-to-end (Story 11.B.2 has unit tests for `SpeakerWorkflowService` but does not test the full HTTP path through the invite controller — see AC9 for the new tests).

---

### AC5 — `POST /api/v1/events/{eventCode}/speakers/pool` rejects `email` payload for IDENTIFIED/CONTACTED entries (AR23, FR12)

**Given** the speaker-pool create endpoint at `EventController.java:2510-2527` (today's path; the controller method is `addSpeakerToPool`) and its request DTO `AddSpeakerToPoolRequest.java`,
**Then** the DTO **continues to have no `email` field** (it currently does not — verified by reading the file; this AC enforces that no email field is ever added to this DTO and that any client-supplied `email` value is **rejected**, not silently ignored).

**And** the OpenAPI schema `#/components/schemas/AddSpeakerToPoolRequest` in `docs/api/events-api.openapi.yml` is updated:
- Add `additionalProperties: false` so a body containing `{ "speakerName": "...", "email": "x@y.z" }` returns `400 Bad Request` from the OpenAPI request validator.
- Update the endpoint's description block (lines 988-1003) to add a "**Tightened by ADR-009 (Story 11.D.1):** `email` is not accepted on this endpoint; speakers are only promoted to a real identity via `POST /speakers/{speakerId}/promote` once they reach the READY state per ADR-009 §0.2." bullet under "**Business Rules**".

**And** the endpoint description's 400 example block (lines 1029-1039) is **extended** with a second example showing the `{ ..., "email": "x@y.z" }` rejection path: error message `"Unknown field 'email' is not permitted on this endpoint; use POST /speakers/{speakerId}/promote to provision a speaker with an email"`.

**And** the corresponding Jackson configuration ensures the rejection — either (a) the OpenAPI-generated DTO sets `@JsonIgnoreProperties(ignoreUnknown = false)`, OR (b) the hand-written `AddSpeakerToPoolRequest.java` is annotated with `@JsonIgnoreProperties(ignoreUnknown = false)` explicitly. Choose (b) for this story if the controller currently uses the hand-written DTO (it does per the survey; the controller imports `ch.batbern.events.dto.AddSpeakerToPoolRequest`, not a generated DTO). Verify by adding a Bruno test (`bruno-tests/events-api/35-add-speaker-to-pool-rejects-email.bru` — see AC9) that posts `{ "speakerName": "X", "email": "x@y.z" }` and asserts 400.

**And** the same `additionalProperties: false` + `@JsonIgnoreProperties(ignoreUnknown = false)` treatment is applied to the **PATCH** endpoint `PATCH /events/{eventCode}/speakers/pool/{speakerId}` (`EventController.java:2558`) and its request DTO so that `{ "email": "x@y.z" }` payloads are rejected on PATCH as well. **Rationale**: the plan §4 row for `PATCH .../pool/{speakerId}` says "email may be updated only as part of the promote-to-READY transition" — i.e., not on the generic patch path either.

---

### AC6 — Brainstorming panel UI: no email field on IDENTIFIED/CONTACTED form (UX-DR15)

**Given** the existing `web-frontend/src/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel.tsx` (already has no email input per the survey — fields are `speakerName`, `company`, `expertise`, `assignedOrganizerId`, `notes`),
**Then** the component **continues to have no email input field**. This AC enforces the invariant — any PR that tries to add an email field to this form is rejected by code review per UX-DR15.

**And** the corresponding test file `SpeakerBrainstormingPanel.test.tsx` (already exists) **gains a new test** `it('should_not_renderEmailInput_when_inIdentifiedOrContactedMode', () => { ... })` that asserts `screen.queryByLabelText(/email/i)` is `null` after the form renders. This makes the invariant lint-checkable.

**And** the `AddSpeakerToPoolRequest` type in `web-frontend/src/types/speakerPool.types.ts` (or its generated equivalent in `src/types/generated/events-api.types.ts`) **does not have** an `email` field. If the generated types are regenerated as part of AC5's OpenAPI changes, this falls out automatically — verify with `grep -n "email" web-frontend/src/types/generated/events-api.types.ts` and confirm the only matches are unrelated schemas (User, registration, etc.).

---

### AC7 — Brainstorming panel UI: "Promote to speaker" modal + button on CONTACTED speakers (UX-DR16)

**Given** the speaker pool list rendered by `SpeakerBrainstormingPanel.tsx` (lines 193-280, rendering each speaker as a `ListItem`),
**When** I view a speaker whose `status === 'contacted'`,
**Then** the speaker's list-item has a **"Promote to speaker"** button (MUI `<Button variant="contained" size="small">` with the existing app's accent-button styling — match what is used for analogous primary actions in `SpeakerStatusLanes.tsx`),
**And** the button uses the i18n key `t('speakerBrainstorm.actions.promoteToSpeaker', 'Promote to speaker')` in the `organizer` namespace.

**And** clicking the button opens a modal (MUI `<Dialog>`) titled `t('speakerBrainstorm.promoteDialog.title', 'Promote to speaker')` with:
- A description paragraph explaining the action (e.g., "Once promoted, the speaker is moved to the READY lane and provisioned with a user account. They will receive an invitation email when you click Send Invitation."). i18n key `speakerBrainstorm.promoteDialog.description`.
- A required `email` text field (`<TextField type="email" required>`), label `t('speakerBrainstorm.promoteDialog.emailLabel', 'Email')`.
- Optional `firstName` and `lastName` text fields (`<TextField>`), labels `speakerBrainstorm.promoteDialog.firstNameLabel` and `speakerBrainstorm.promoteDialog.lastNameLabel`. If the speaker's `speakerName` on the pool entry is parseable as "First Last", pre-fill these fields by splitting on the first space (matches the existing `splitName` pattern used elsewhere; the dev should reuse rather than re-implement — search for it under `web-frontend/src/`).
- A "Cancel" button (closes the dialog without action).
- A "Promote" submit button (i18n key `speakerBrainstorm.promoteDialog.submitButton`, label `'Promote'`). Disabled until the email field passes basic validation (uses the project's existing `react-hook-form` + `zod` validation pattern — see `_bmad-output/project-context.md` "Use `useTranslation()` hook for ALL user-facing strings" + the React stack).

**And** on submit, the modal calls a new service method `speakerPoolService.promoteToSpeaker(eventCode, speakerId, { email, firstName?, lastName? })` (see AC8) and:
- On `200 OK`: closes the modal, the kanban / pool list optimistically updates (the speaker's `status` becomes `'ready'` and the entry visually moves to the READY position — reuse the existing TanStack Query invalidation pattern from `useAddSpeakerToPool.ts`).
- On `409 Conflict` with `details.code === 'INVALID_PROMOTION_STATE'`: shows a toast/`<Alert>` with the message from the response body (e.g., "Speaker is already INVITED"). Modal stays open.
- On `400 Bad Request`: surfaces the field validation error inline next to the email field (existing `react-hook-form` error-render pattern).
- On other errors (`500`, network): shows a generic error message using the existing error-handling pattern in `SpeakerBrainstormingPanel.tsx` (line 183 `'speakerBrainstorm.form.error'`).

**And** the i18n keys are added to **all 10 locales** in `web-frontend/public/locales/{locale}/organizer.json`:

```
speakerBrainstorm.actions.promoteToSpeaker      → "Promote to speaker"  (en) and translations
speakerBrainstorm.promoteDialog.title           → "Promote to speaker"
speakerBrainstorm.promoteDialog.description     → "Once promoted, …"
speakerBrainstorm.promoteDialog.emailLabel      → "Email"
speakerBrainstorm.promoteDialog.firstNameLabel  → "First name"
speakerBrainstorm.promoteDialog.lastNameLabel   → "Last name"
speakerBrainstorm.promoteDialog.cancelButton    → "Cancel"
speakerBrainstorm.promoteDialog.submitButton    → "Promote"
speakerBrainstorm.promoteDialog.errorTitle      → "Could not promote speaker"
speakerBrainstorm.promoteDialog.errorAlreadyPromoted → "Speaker is already in {{currentState}}; cannot be promoted again."
```

Per project-context.md ("All 9 locales … + gsw-BE updated; no locale lags behind"), all 10 must be updated in this story. Locales: `de, en, es, fi, fr, gsw-BE, it, ja, nl, rm`. Use the project's translation approach — the dev may use machine translation for the non-DE/EN locales as a baseline (matching the Story 10-9 pattern); a follow-up locale-review pass is acceptable for fr/it/etc.

**And** speakers in `status === 'identified'` get **no** "Promote to speaker" button — they must first be moved to `CONTACTED` via the existing outreach panel. (Per ADR-009 §0.2: "no real person ever existed to invite" from IDENTIFIED; the organizer logs outreach which moves them to CONTACTED first.) Speakers in `status ∈ {READY, INVITED, ACCEPTED, ...}` likewise get no button (they're already past this gate).

---

### AC8 — Frontend service-layer wiring for the promote endpoint

**Given** the existing service module `web-frontend/src/services/speakerPoolService.ts`,
**When** I read the file,
**Then** a new method exists:

```typescript
async promoteToSpeaker(
  eventCode: string,
  speakerId: string,
  request: { email: string; firstName?: string; lastName?: string }
): Promise<SpeakerPoolResponse>
```

The method calls `POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote` using the existing axios client + auth-token-propagation pattern from `speakerPoolService.addSpeakerToPool` (line 30-50 of the file per the survey). The request and response types are imported from `@/types/generated/events-api.types` after `npm run generate:api-types` regenerates them post-AC1.

**And** a new TanStack Query hook `usePromoteSpeakerToReady` is added to `web-frontend/src/hooks/useSpeakerPool.ts` (matching the `useAddSpeakerToPool` pattern at line 63 of the file per the survey):

```typescript
export const usePromoteSpeakerToReady = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ eventCode, speakerId, request }) =>
      speakerPoolService.promoteToSpeaker(eventCode, speakerId, request),
    onSuccess: (_, { eventCode }) => {
      queryClient.invalidateQueries({ queryKey: ['speakerPool', eventCode] });
      // Also invalidate the status lanes so the SpeakerStatusLanes refresh
      queryClient.invalidateQueries({ queryKey: ['speakerStatus', eventCode] });
    },
  });
};
```

**And** the existing `useSpeakerPoolHooks.test.ts` (per the survey) is extended with a happy-path + 409-error-path test for the new hook.

---

### AC9 — Test coverage matches the project's 4-layer pyramid

**Backend — Testcontainers integration tests** (per project-context.md "NEVER use H2"):

1. New `SpeakerPromoteControllerIntegrationTest` (in `services/event-management-service/src/test/java/ch/batbern/events/controller/`) extending `AbstractIntegrationTest`. Cases:
   - `should_returnReady_when_promoteCalledOnContactedSpeaker` — happy path. Assert `200 OK`, `speaker_pool.status = 'ready'`, `speaker_pool.username` non-null, `speaker_pool.email = request.email`, `speaker_status_history` row exists with `changed_by_username = organizer`, `SpeakerPromotedToReadyEvent` published (use existing `DomainEventPublisher` test capture pattern from `SpeakerWorkflowServiceTest`).
   - `should_return400_when_emailMissing`.
   - `should_return400_when_emailMalformed`.
   - `should_return400_when_unknownFieldPresent` (verifies `additionalProperties: false`).
   - `should_return409_when_speakerAlreadyInReady` — actually returns 200 OK per AC3 same-state branch; rename to `should_return200_when_speakerAlreadyInReady_idempotent` and assert no state change (same-state history row OK).
   - `should_return409_when_speakerInInvited` — pre-check fires with `details.code = 'INVALID_PROMOTION_STATE'`.
   - `should_return409_when_speakerInIdentified` — pre-check fires.
   - `should_return409_when_speakerInDeclined` — pre-check fires.
   - `should_return404_when_speakerNotFound`.
   - `should_return403_when_callerIsNotOrganizer`.

2. New `SpeakerInvitationServiceSlotCapacityIntegrationTest` (or extend the existing `SpeakerInvitationServiceTest` / `SpeakerWorkflowServiceIntegrationTest` if the dev finds an appropriate one — the survey lists both):
   - `should_return409_when_sendInvitationCalledAndSlotCapacityReached`. Setup: create an event with `maxSlots = 2`, two speakers in `ACCEPTED` and one in `READY`. Call `POST /api/v1/events/{eventCode}/speakers/{username}/send-invitation` for the READY speaker. Assert `409 Conflict` with `details.code = 'SLOT_CAPACITY_REACHED'`, `details.maxSlots = 2`, `details.acceptedCount = 2`, `details.invitedCount = 0`. Assert the speaker's status is still `READY` (rollback verified), no `speaker_status_history` row added, no `SpeakerInvitedEvent` published, no email sent (mock `SpeakerInvitationEmailService` and verify zero interactions).
   - `should_succeed_when_sendInvitationCalledAndCapacityAvailable`. Setup: same event, one ACCEPTED + one READY (`maxSlots = 2`, no INVITED). Call invite. Assert `200 OK`, status → INVITED, email sent.

3. New `SpeakerPoolControllerEmailRejectionIntegrationTest` (or extend `SpeakerPoolControllerIntegrationTest` if it exists; if not, the dev creates it):
   - `should_return400_when_addSpeakerToPoolWithEmailField` — posts `{ "speakerName": "X", "email": "x@y.z" }`, expects 400.
   - `should_return400_when_patchSpeakerPoolWithEmailField` — patches `{ "email": "x@y.z" }`, expects 400.

**Bruno API contract tests** (per CLAUDE.md Layer 2):

Add the following files under `bruno-tests/events-api/`:

- `45-promote-speaker-happy-path.bru` — POST /promote with valid email; expect 200; chain assertions on the returned `SpeakerPoolResponse` (status=ready, username non-null).
- `46-promote-speaker-missing-email.bru` — POST /promote with empty body; expect 400.
- `47-promote-speaker-already-in-state.bru` — promote a speaker who's already INVITED; expect 409 with `details.code = 'INVALID_PROMOTION_STATE'`.
- `48-add-speaker-to-pool-rejects-email.bru` — POST /speakers/pool with extraneous `email` field; expect 400.
- `54-send-invitation-slot-capacity-409.bru` (or rename 53-speaker-workflow-slot-capacity-409.bru if it already exercises the same path — check the existing file before duplicating) — drives the READY→INVITED transition through `/send-invitation` and asserts the 409 with the SLOT_CAPACITY_REACHED `details` map.

Number these consistently with the existing pattern in `bruno-tests/events-api/` (the survey shows the next available numbers in the 40s-50s range).

**Playwright E2E** (per CLAUDE.md Layer 3, `chromium` project — organizer):

Extend (or add) a test in `web-frontend/e2e/organizer/` (the survey mentions `speaker-brainstorming.spec.ts`):

- `it('should promote a CONTACTED speaker to READY via the brainstorming-panel modal', ...)`. Setup test data via the existing Playwright fixtures (or seed via API in `beforeEach`). Drive: navigate to event page → speakers tab → click "Promote to speaker" on a CONTACTED speaker → fill email in modal → click Promote → assert speaker card visually moves to READY lane (no manual refresh).
- `it('should surface slot-capacity error when sending invitation past capacity', ...)`. Setup: event with `maxSlots = 1`, one ACCEPTED speaker, one READY speaker. Click "Send invitation" on the READY speaker. Assert toast/error appears containing "Slot capacity reached" (i18n key match). Assert the speaker stays in READY lane.

**Frontend unit tests** (Vitest + RTL):

- Extend `SpeakerBrainstormingPanel.test.tsx`:
  - `should_renderPromoteButton_when_speakerIsContacted`.
  - `should_not_renderPromoteButton_when_speakerIsIdentified`.
  - `should_openPromoteDialog_when_promoteButtonClicked`.
  - `should_callPromoteEndpoint_when_dialogSubmitted` (mock `speakerPoolService.promoteToSpeaker` via msw or service-mock).
  - `should_showErrorAlert_when_promote409Returned`.

---

### AC10 — Documentation: OpenAPI + speaker-coordination API doc updated; CLAUDE.md + Epic 11 PRD references reconciled

**Given** the project's doc-drift-prevention policy (CLAUDE.md §"Doc Drift Prevention"; `.github/doc-drift-mappings.yml`),
**Then** the same commit/PR that lands code includes:

1. `docs/api/events-api.openapi.yml` — new path entry for `/promote` (per AC1), tightened `AddSpeakerToPoolRequest` schema (per AC5), tightened `PatchSpeakerPoolRequest` (or whatever the PATCH DTO is named — verify) schema (per AC5).
2. `docs/architecture/04-api-speaker-coordination.md` — Story 11.A.1 already added the `/promote` endpoint stub here. Verify the AC1 endpoint shape matches the doc; reconcile if drifted (e.g., add the `firstName`/`lastName` optional fields to the doc's example if not already present).
3. **No update** to `docs/architecture/03-data-architecture.md`, `06a-workflow-state-machines.md`, or ADR-009 — those describe the state machine + data model, which this story does not change.
4. **No update** to `CLAUDE.md` — the Epic 11 status line and speaker-workflow summary already reflect ADR-009 (per Story 11.A.1 AC11).

**And** the commit message contains `feat(event-mgmt): add POST /speakers/{id}/promote endpoint + brainstorm panel UI [Story 11.D.1]` or similar conventional-commits form. **NO `[no-doc]` marker** — this story explicitly updates the OpenAPI spec and the API doc; doc-drift policy is satisfied by the doc update being in the same commit.

---

### AC11 — Verification of cross-cutting invariants

**Given** the full backend build + Bruno + Playwright suites run on the refactor branch,
**Then**:

1. `./gradlew :services:event-management-service:build` succeeds — compile, unit tests, integration tests (Testcontainers), Spotless, Checkstyle.
2. `./scripts/ci/run-bruno-tests.sh` passes (all existing tests + the 5 new tests from AC9).
3. `cd web-frontend && npm run type-check` passes (after `npm run generate:api-types` regenerates types from the updated OpenAPI spec).
4. `cd web-frontend && npm run test -- SpeakerBrainstormingPanel` passes (existing + new tests).
5. `cd web-frontend && npm run lint` passes (`--max-warnings 50` per project-context.md).
6. `cd web-frontend && npx playwright test --project=chromium e2e/organizer/speaker-brainstorming.spec.ts` passes locally.
7. **Grep invariants**:
   - `grep -rn "promoteSpeaker\|promoteToReady\|POST.*\\/promote" services/event-management-service/src/main/` returns matches inside `SpeakerStatusController` and the OpenAPI-generated DTOs only.
   - `grep -rn "speaker.setStatus(\|setStatus(SpeakerWorkflowState\." services/event-management-service/src/main/` continues to return exactly one match (inside `SpeakerWorkflowService.transition`) — i.e., the new controller does NOT introduce a second status writer (single-writer invariant from 11.B.2 AC1 preserved).
   - `grep -rn "email" services/event-management-service/src/main/java/ch/batbern/events/dto/AddSpeakerToPoolRequest.java` returns zero matches (no email field on the DTO).
   - `grep -rn "getByLabelText.*[Ee]mail\\|name=\"email\"" web-frontend/src/components/SpeakerBrainstormingPanel/` returns zero matches in the main form section (only inside the new promote-dialog code).

---

## Tasks / Subtasks

Tasks are grouped by file area and ordered to compile incrementally. Each task lists the AC it satisfies, the exact files to touch, and the test to run after to lock it in.

### Task 1 — OpenAPI spec updates (AC1, AC5, AC10)

1.1. Open `docs/api/events-api.openapi.yml`. Insert a new path entry `/events/{eventCode}/speakers/{speakerId}/promote` after the existing `/events/{eventCode}/speakers/pool/{speakerId}` block (around line 1104+ per the survey). Use the `operationId: promoteSpeakerToReady`, request body referencing a new `#/components/schemas/PromoteSpeakerRequest`, response 200 referencing the existing `SpeakerPoolResponse`. Document 400, 401, 403, 404, 409, 500 with examples (mirror the format of the existing `/pool` operation block at lines 1015-1057).
1.2. Add `PromoteSpeakerRequest` to the components/schemas section. Fields: `email` (required, format email, maxLength 320), `firstName` (optional, maxLength 100), `lastName` (optional, maxLength 100). `additionalProperties: false`.
1.3. Edit the existing `AddSpeakerToPoolRequest` schema: add `additionalProperties: false`. Edit the path's `post` description (lines 988-1003) to add the AR23 tightening bullet.
1.4. Find and edit the PATCH path entry `/events/{eventCode}/speakers/pool/{speakerId}` and the corresponding `PatchSpeakerPoolRequest` schema: add `additionalProperties: false`. Update description text accordingly.
1.5. Run `cd web-frontend && npm run generate:api-types` to regenerate `src/types/generated/events-api.types.ts`. Commit the regenerated types.

**Verify**: `grep -n "promoteSpeakerToReady\|PromoteSpeakerRequest" docs/api/events-api.openapi.yml` returns multiple hits; `grep -n "promoteSpeaker\|PromoteSpeaker" web-frontend/src/types/generated/events-api.types.ts` returns multiple hits.

### Task 2 — Backend DTO + Controller (AC1, AC2, AC3)

2.1. Create `services/event-management-service/src/main/java/ch/batbern/events/dto/PromoteSpeakerRequest.java` — record or class with the three fields, Bean Validation annotations (`@Email @NotBlank @Size(max=320) String email`, `@Size(max=100) String firstName`, `@Size(max=100) String lastName`), `@JsonIgnoreProperties(ignoreUnknown = false)` class-level annotation.
2.2. Open `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java`. Add the new `promoteSpeakerToReady` method per AC1's signature. Inject `SecurityContextHelper` (already injected per 11.B.2; verify). Add the AC3 pre-check that loads the speaker and returns 409 for `status ∈ {IDENTIFIED, INVITED, ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED}` with `details.code = 'INVALID_PROMOTION_STATE'` and `details.currentState = status.name()`. Call `speakerWorkflowService.transition(speakerId, READY, actor, payload)`. Map `TransitionResult` to `SpeakerPoolResponse` (look for the existing mapper used by `addSpeakerToPool` and reuse).
2.3. If the project uses OpenAPI Generator for controller interfaces (per ADR-006), regenerate the `SpeakersApi` interface and have the controller `implements` it (matches the project's contract-first pattern). If the controller does NOT currently implement a generated interface (verify by reading the existing `SpeakerStatusController` class declaration), follow the same pattern the existing methods use — do NOT switch the controller to a generated interface in this story.
2.4. Verify the existing `GlobalExceptionHandler` handles the new 409 path. Add a new `@ExceptionHandler` if the dev chooses to throw a dedicated `InvalidPromotionStateException`, OR construct the `ResponseEntity<ErrorResponse>` directly in the controller. **Recommendation**: introduce a tiny dedicated exception `InvalidPromotionStateException(currentState)` for clean error handling — matches the project's exception-class-per-domain-error pattern (see `SlotCapacityReachedException`).

**Verify**: `./gradlew :services:event-management-service:compileJava` succeeds.

### Task 3 — Backend AR23: Reject email on POST /pool and PATCH /pool/{id} (AC5)

3.1. Open `AddSpeakerToPoolRequest.java`. Add class-level `@JsonIgnoreProperties(ignoreUnknown = false)` annotation. **Do not add an email field.**
3.2. Locate the PATCH endpoint's request DTO (`grep` for the controller method at `EventController.java:2558`). Apply the same `@JsonIgnoreProperties(ignoreUnknown = false)` treatment. Confirm the DTO has no email field; if it does (it might, for legacy reasons), the dev's task is to **remove** the email field and verify no production code path was setting `speaker_pool.email` through PATCH (the only legitimate path is the new `/promote` endpoint per FR12).

**Verify**: `grep -rn "private.*email\|setEmail\|getEmail" services/event-management-service/src/main/java/ch/batbern/events/dto/AddSpeakerToPoolRequest.java services/event-management-service/src/main/java/ch/batbern/events/dto/Patch*SpeakerPool*.java` returns zero matches.

### Task 4 — Backend tests (AC9)

4.1. Create `SpeakerPromoteControllerIntegrationTest.java` with the 10 cases from AC9. Use `@WithMockUser(roles = "ORGANIZER")` for happy-path; one case uses `roles = "ATTENDEE"` for 403. Use Spring's `MockMvc` (matches `SpeakerStatusControllerIntegrationTest` per the survey).
4.2. Either extend `SpeakerInvitationServiceTest` or create `SpeakerInvitationSlotCapacityIntegrationTest` with the two cases from AC9 item 2. Verify with the actual HTTP layer (use `TestRestTemplate` or `MockMvc` — match what existing tests use).
4.3. Either extend an existing pool-controller integration test or create `SpeakerPoolEmailRejectionIntegrationTest` with the two cases from AC9 item 3.
4.4. Run `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/em-test.log`, then `grep -E "FAILED|BUILD" /tmp/em-test.log`. Iterate until green.

### Task 5 — Bruno API contract tests (AC9)

5.1. Add `45-promote-speaker-happy-path.bru`, `46-promote-speaker-missing-email.bru`, `47-promote-speaker-already-in-state.bru`, `48-add-speaker-to-pool-rejects-email.bru`, and a slot-capacity test (verify number `53-speaker-workflow-slot-capacity-409.bru` already exists per the survey; if it tests via PUT /status, add `54-send-invitation-slot-capacity-409.bru` for the /send-invitation path specifically — they are different code paths exercising the same precondition, both worth testing).
5.2. Run `./scripts/ci/run-bruno-tests.sh 2>&1 | tee /tmp/bruno.log`. Iterate until green.

### Task 6 — Frontend service + hook (AC8)

6.1. Add `promoteToSpeaker` method to `web-frontend/src/services/speakerPoolService.ts` using the existing auth/axios pattern.
6.2. Add `usePromoteSpeakerToReady` hook to `web-frontend/src/hooks/useSpeakerPool.ts`.
6.3. Extend `useSpeakerPoolHooks.test.ts` with happy + 409 cases for the new hook.

### Task 7 — Frontend brainstorming panel UI (AC6, AC7)

7.1. Open `web-frontend/src/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel.tsx`. Inside each `ListItem` in the pool list (around line 230), add a conditional `"Promote to speaker"` MUI `<Button>` shown only when `speaker.status === 'contacted'`.
7.2. Add a new modal component `PromoteSpeakerDialog.tsx` co-located in the same folder (or `SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx`). Use `react-hook-form` + `zod` for validation (matches `_bmad-output/project-context.md` "react-hook-form 7.x + zod 4.x — forms & validation"). Use `useTranslation('organizer')`. Wire to `usePromoteSpeakerToReady`.
7.3. Wire the modal: clicking "Promote to speaker" sets local state `{ open: true, speaker: <selected> }`; the dialog's `onSubmit` calls `mutate({ eventCode, speakerId: speaker.id, request })`; on success, close the dialog and let TanStack Query invalidate; on 409, render the error alert inside the dialog.
7.4. Extend `SpeakerBrainstormingPanel.test.tsx` with the AC9 frontend cases.
7.5. Run `cd web-frontend && npm run test -- SpeakerBrainstormingPanel 2>&1 | tee /tmp/fe-test.log`. Iterate until green.

### Task 8 — i18n (AC7)

8.1. Add the 10 new keys under `speakerBrainstorm.actions.promoteToSpeaker` and `speakerBrainstorm.promoteDialog.*` to all 10 locale files in `web-frontend/public/locales/{locale}/organizer.json`.
8.2. For non-EN/DE locales, use machine translation as a baseline (matches Story 10-9's Phase 4 pattern). Flag in PR description for a follow-up locale-review pass if needed.
8.3. Run `cd web-frontend && npm run type-check && npm run lint` — confirm no missing-key warnings or unused-locale-file warnings.

### Task 9 — Playwright E2E (AC9)

9.1. Add the two E2E cases (promote happy path + slot-capacity error) to `web-frontend/e2e/organizer/speaker-brainstorming.spec.ts` (or split into a new `speaker-promote.spec.ts` if the file is getting unwieldy).
9.2. Run locally: `cd web-frontend && npx playwright test --project=chromium e2e/organizer/speaker-brainstorming.spec.ts 2>&1 | tee /tmp/playwright.log`.

### Task 10 — Full verification + commit (AC10, AC11)

10.1. Run the full grep invariants from AC11 item 7. Capture outputs to `/tmp/grep-invariants.log`. Confirm matches match the expected counts.
10.2. Run `./gradlew :services:event-management-service:build 2>&1 | tee /tmp/em-build.log` and `cd web-frontend && npm run type-check && npm run lint && npm run test 2>&1 | tee /tmp/fe-full.log`. Confirm both green.
10.3. Run `./scripts/ci/run-bruno-tests.sh 2>&1 | tee /tmp/bruno-final.log`. Confirm green.
10.4. Stage and commit per CLAUDE.md commit format: `feat(event-mgmt): add POST /speakers/{id}/promote + reject email on pool create + brainstorm promote modal [Story 11.D.1]`.

### Review Findings (2026-05-16)

Adversarial review (Blind Hunter + Edge Case Hunter + Acceptance Auditor — all Opus). 26 unique findings after dedup: 3 decision-needed (all resolved → patches PT14/PT15/PT16), 16 patches (all applied), 6 deferred (in `deferred-work.md`), 4 dismissed.

**Verification after patch application (2026-05-16):**
- `./gradlew :services:event-management-service:build`: BUILD SUCCESSFUL — 0 checkstyle violations, 0 warnings introduced.
- `:services:event-management-service:test` (targeted: SpeakerPromote/SpeakerPool/SpeakerInvitation/SpeakerWorkflowService/SpeakerStatusController): 121 PASSED, 0 FAILED. New cases verified: `should_return400_when_patchSpeakerPoolWithEmailField` (PT7), `should_return409_when_sendInvitationCalledAndSlotCapacityReachedByMix` (PT8), `should_return409_when_promoteCalledOnReadySpeaker` (PT14).
- `npm run generate:api-types`: clean.
- `npm run type-check`: clean.
- `npm run lint`: 0 warnings.
- `npm run test --run`: 4868 passed / 110 skipped / 23 todo.

Playwright E2E (`should surface slot-capacity error when sending invitation past capacity` per PT15) added but not run in this review pass — requires staging tokens; run locally via `cd web-frontend && npx playwright test --project=chromium e2e/organizer/speaker-brainstorming.spec.ts` before the merge.

#### Decision-needed (all resolved 2026-05-16 — see PT14, PT15, PT16 below)

- [x] **DN1 [P0]** Idempotent READY same-state semantics — **Resolved:** reject as 409. See PT14.
- [x] **DN2 [P2]** Missing Playwright slot-capacity E2E — **Resolved:** add the test now. See PT15.
- [x] **DN3 [P2]** PATCH endpoint not in OpenAPI surface — **Resolved:** add PATCH operation + `PatchSpeakerPoolRequest` schema to OpenAPI. See PT16.

#### Patch

- [x] [Review][Patch] **PT1 [P0] Cross-event privilege boundary not enforced on /promote** [`services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java:93-128`] — `@PathVariable String eventCode` is accepted and logged but never validated against `speaker.eventId`. Allows promoting a speaker from event A via event B's URL. Fix: after `speakerPoolRepository.findById(speakerId)`, fetch event by eventCode and assert `event.id == speaker.eventId`, else 404. (sources: blind+edge)
- [x] [Review][Patch] **PT2 [P1] Status-summary + history Spring caches not evicted after promote** [`SpeakerStatusController.java:93-95`] — sibling `SpeakerStatusService.updateStatus` evicts `STATUS_SUMMARY_CACHE` + `STATUS_HISTORY_CACHE` keyed by eventCode; new `promoteSpeakerToReady` does not. Stale counts served to other clients until TTL. Fix: add `@CacheEvict(value = {STATUS_SUMMARY_CACHE, STATUS_HISTORY_CACHE}, key = "#eventCode")`. (source: edge)
- [x] [Review][Patch] **PT3 [P1] `currentState` enum rendered raw in localized error + IDENTIFIED message wrongly classified as "already promoted"** [`web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx:133-141` + all locale `errorAlreadyPromoted` keys] — backend 409 detail `currentState: "DECLINED"` interpolated as `{{currentState}}` produces non-localised English ("登壇者はすでに DECLINED 状態のため…"). Worse: for IDENTIFIED the template says "already in IDENTIFIED" but IDENTIFIED is BEFORE CONTACTED. Fix: branch on `currentState` — use backend message (or dedicated key) for IDENTIFIED/DECLINED, keep `errorAlreadyPromoted` only for INVITED/ACCEPTED/CONTENT_SUBMITTED/QUALITY_REVIEWED. (source: edge)
- [x] [Review][Patch] **PT4 [P1] TOCTOU race between state pre-check and `transition()` masks 409 as generic 400** [`SpeakerStatusController.java:106-128`] — pre-check reads state, then concurrent `CONTACTED → DECLINED` makes `transition()` throw `InvalidStateTransitionException` (mapped to generic 400) instead of the friendly 409 `INVALID_PROMOTION_STATE`. Fix: catch `InvalidStateTransitionException` after `transition()` and re-classify as `InvalidPromotionStateException` after re-reading current state. (source: edge)
- [x] [Review][Patch] **PT5 [P2] `@Email` allows leading/trailing whitespace; stored email gets spaces** [`services/event-management-service/src/main/java/ch/batbern/events/dto/PromoteSpeakerRequest.java` + `SpeakerStatusController.java:118`] — Jakarta `@Email` is permissive; non-UI callers (curl, Bruno) sending `" jane@example.com "` produce `speaker_pool.email = " jane@example.com "` and a User lookup against the untrimmed key. Fix: trim email in controller before constructing `TransitionPayload`, or add a Jackson trimming deserializer. (source: edge)
- [x] [Review][Patch] **PT6 [P2] Empty-string firstName/lastName bypasses speakerName fallback** [`SpeakerWorkflowService.java:260-263`] — DTO has `@Size(max=100)` but no `@NotBlank` on names; `payload.firstName() != null ? ... : firstNameFallback(speaker)` treats `""` as a real value, sending empty strings to `provisionUserWithRole` instead of the speakerName-derived fallback the docs promise. Fix: change condition to `payload.firstName() != null && !payload.firstName().isBlank()` (same for lastName). (source: edge)
- [x] [Review][Patch] **PT7 [P2] Missing PATCH /speakers/pool/{id} email-rejection integration test** [`services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPoolWorkflowIntegrationTest.java`] — AC9 lists both POST and PATCH cases; only POST `should_return400_when_addSpeakerToPoolWithEmailField` was added. Without the PATCH test, a future revert of `@JsonIgnoreProperties(ignoreUnknown = false)` on `PatchSpeakerPoolRequest` won't be caught by CI. Fix: add `should_return400_when_patchSpeakerPoolWithEmailField` mirroring the POST case. (sources: blind+edge+auditor)
- [x] [Review][Patch] **PT8 [P2] Slot-capacity test only saturates with ACCEPTED — INVITED arm uncovered** [`SpeakerInvitationControllerIntegrationTest.java:353-401`] — `enforceSlotCapacity` counts `ACCEPTED + INVITED`; current test only verifies the ACCEPTED arm. A regression that drops `countByEventIdAndStatus(INVITED)` from the gate would pass. Fix: add a second case that saturates with a 50/50 mix and asserts 409 still fires. (source: edge)
- [x] [Review][Patch] **PT9 [P2] Playwright `text=READY` locator can match column header / unrelated chips** [`web-frontend/e2e/organizer/speaker-brainstorming.spec.ts:375`] — `page.locator('text=READY').first()` is not scoped to the promoted speaker's row; passes spuriously if a "READY" lane header or sibling chip already exists. Fix: scope to `[data-testid="speaker-row-${speakerId}"]` and assert the status chip's text directly. (source: edge)
- [x] [Review][Patch] **PT10 [P3] Locale dialog copy misleads organizers about invitation email timing** [all 10 locale `organizer.json` `promoteDialog.description` keys] — copy says "they will receive an invitation email when you click Send Invitation"; promote does NOT trigger any email — it only provisions the User and moves to READY. The invitation flow is the separate `send-invitation` action later. Fix: rewrite to "The speaker moves to READY and a user account is provisioned. They will not receive any email yet — send the invitation from the READY lane later." Apply to all 10 locales. (source: blind)
- [x] [Review][Patch] **PT11 [P3] PII (email) logged at INFO** [`SpeakerStatusController.java:100-101`] — `log.info("POST /api/v1/events/{}/speakers/{}/promote - email: {}", eventCode, speakerId, request.email())` writes user email to CloudWatch at INFO. CLAUDE.md "Personal Data & Security Guidelines" treats email as PII. Fix: drop the email argument from the log line (speakerId + eventCode already correlate) or mask (`m***@example.com`). (source: edge)
- [x] [Review][Patch] **PT12 [P3] Frontend promote test doesn't verify `eventCode` is forwarded by the dialog** [`web-frontend/src/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel.test.tsx:1297-1301`] — `toMatchObject({ speakerId: '2', request: ... })` passes even when the dialog forgets to pass `eventCode`. Fix: add `eventCode: expect.any(String)` (or the actual eventCode used in `renderComponent`) to the assertion. (source: blind)
- [x] [Review][Patch] **PT13 [P3] `autoFocus` on email field skips `DialogContentText` for screen readers** [`PromoteSpeakerDialog.tsx:160-184`] — focus jumps past the descriptive paragraph explaining the action's consequence ("user account will be provisioned, invitation email later"). a11y regression for an action with side effects. Fix: remove `autoFocus` from the email input, or move the description into `aria-describedby` of the Dialog and keep `autoFocus`. (source: edge)
- [x] [Review][Patch] **PT14 [P0] Reject /promote on already-READY speaker with 409 INVALID_PROMOTION_STATE** [from DN1] — tighten the controller pre-check at `SpeakerStatusController.java:108-112` to also throw `InvalidPromotionStateException` when `current == READY` (drop the `current != READY` allow-clause). Update the OpenAPI `/promote` description in `docs/api/events-api.openapi.yml` to remove the "idempotent no-op" wording and document 409 for "speaker already READY or later". Add an integration test `should_return409_when_promoteCalledOnReadySpeaker`. The kanban's per-card promote affordance already only renders for CONTACTED rows; verify the UI hides it for READY+.
- [x] [Review][Patch] **PT15 [P2] Add Playwright `should surface slot-capacity error when sending invitation past capacity`** [from DN2] [`web-frontend/e2e/organizer/speaker-brainstorming.spec.ts`] — replace the deferred inline comment with the actual test: seed an event with `maxSlots=1` via API in `beforeEach`, one ACCEPTED + one READY speaker, click "Send invitation" on the READY row, assert the localised slot-capacity-reached toast and that the speaker stays in the READY lane. Mirror Java integration test seeding semantics.
- [x] [Review][Patch] **PT16 [P2] Add PATCH /events/{eventCode}/speakers/pool/{speakerId} operation + `PatchSpeakerPoolRequest` schema to OpenAPI** [from DN3] [`docs/api/events-api.openapi.yml` (around line 1124-1174 where DELETE is currently defined)] — add the `patch:` operation alongside `delete:`. Define `PatchSpeakerPoolRequest` under components.schemas with `additionalProperties: false` and the fields currently in `services/event-management-service/src/main/java/ch/batbern/events/dto/PatchSpeakerPoolRequest.java`. Document responses 200/400/401/403/404. Regenerate frontend types (`npm run generate:api-types`).

#### Deferred (pre-existing or out-of-scope)

- [x] [Review][Defer] **DF1 [P2] `lastNameFallback` returns empty string for single-word speaker names** [`SpeakerWorkflowService.java:471-478`] — pre-existing fallback design from 11.B.2; behaviour of `provisionUserWithRole` with empty lastName not verified by this story. Same family of concern as the existing deferred "`firstNameFallback`/`lastNameFallback` literals 'Speaker' / 'Unknown'" item from 11.B.2 review. (source: edge)
- [x] [Review][Defer] **DF2 [P2] maxLength asymmetry — firstName/lastName=100 vs email=320; silently truncated by `inputProps.maxLength`** [`PromoteSpeakerRequest.java` + `PromoteSpeakerDialog.tsx` inputs] — design choice in the spec, not a bug. Bumping to 255 to fit longer composite names is a spec change, not a review patch. (source: edge)
- [x] [Review][Defer] **DF3 [P2] `usePromoteSpeakerToReady` invalidates `speakerStatusSummary` with a hardcoded query-key string** [`web-frontend/src/hooks/useSpeakerPool.ts:138-140`] — consistent with sibling hooks in the same file; consolidating into a `speakerStatusSummaryKeys` factory is a codebase-wide refactor, not story scope. (source: edge)
- [x] [Review][Defer] **DF4 [P3] Identity-rebind guard test mock too generous to exercise the positive case** [`SpeakerWorkflowServiceTest.java`] — mock always returns the same username; the rebind guard's reject path isn't tested with a divergent return. Defensive test improvement. (source: edge)
- [x] [Review][Defer] **DF5 [P3] `saturate = 24` fixed constant in slot-capacity test brittle to future maxSlots bumps** [`SpeakerInvitationControllerIntegrationTest.java:604-617`] — works for all current event types; replace with a data-driven `eventType.getMaxSlots()` read when next touched. (source: blind)
- [x] [Review][Defer] **DF6 [P3] `InOrder` block does not assert `applicationEventPublisher.publishEvent(...)` ordering** [`SpeakerWorkflowServiceTest.java`] — mock is in the `InOrder` group but never verified at the end; would catch a regression where the event publish moves before DB save. (source: blind)

#### Dismissed (4)

- B6 "PromoteSpeakerRequest Java DTO validation not in diff" — Edge Case Hunter located the DTO with `@NotBlank @Email @Size(max=320)`, so Blind Hunter's concern was visibility, not a real gap.
- E6 "Dialog mutation error leaks on close/reopen" — `key={promoteSpeaker.id}` forces remount; not currently exploitable.
- E7 "`isLoading` vs `isPending` mock-field naming" — code reads `isPending` consistently; mock matches. Stylistic only.
- E16 "Form `defaultValues` not reset when speaker changes" — mitigated by parent's `key` strategy; defensive-only.

---

## Dev Notes

### Why this story exists (and why it is the right scope for Phase D's first story)

Phase B finished the **state machine**: `SpeakerWorkflowService.transition()` is the sole writer; READY hook provisions; INVITED precondition enforces slot-capacity. Phase B made the machine correct, but the organizer **cannot drive it from the UI** for the `CONTACTED → READY` step — there is no endpoint, no button, no modal. Story 11.D.1 closes that gap and is also the first story that puts a public HTTP contract on the slot-capacity gate (the gate has existed since 11.B.2 but only via the internal `transition()` API; the `/send-invitation` controller already calls `transition()`, but the 409 response is not currently documented or tested).

This is intentionally a thin, surgical story: it is mostly **plumbing** (controller + DTO + OpenAPI + service-layer method + modal). It does **not** introduce the state-aware primary-action button on every kanban card (Story 11.D.2), the column-header triage chips (Story 11.D.3), the guided drag-drop + unified drawer (Story 11.D.4), or the on-behalf content form (Story 11.D.4). Those are layered on top.

### Strict sequencing: 11.C.2 must merge before this story

Per Resolved Open Question #1 (2026-05-16, decided by PM): Story 11.C.2 MUST land first. This story's AC1 explicitly asserts that `UserApiClient.provisionUserWithRole(...)` is the call the READY hook makes during the promote transition — matching the strict PRD wording at epic-11 line 841 ("And `UserApiClient.provisionUserWithRole` is called (User created if missing, SPEAKER role granted)").

The architecturally correct interpretation remains: the new `/promote` endpoint MUST NOT call `UserApiClient` directly. It calls `speakerWorkflowService.transition(..., READY, ...)`, and the workflow service's READY hook is the sole owner of the provisioning seam. Story 11.C.2 refactors the READY hook from today's `userApiClient.getOrCreateUser(...) + speakerProvisioningHook.grantSpeakerRole(...)` pair to a single `userApiClient.provisionUserWithRole(...)` call. This story's `/promote` endpoint inherits that hook behaviour without touching the controller.

**Operational implication**: do not begin implementation of this story until 11.C.2 has been merged to `feature/speaker-workflow-refactor`. If both stories are worked in parallel, rebase 11.D.1 on top of 11.C.2 before opening the PR so the AC1 integration test (which mocks `provisionUserWithRole`) compiles against the post-11.C.2 `UserApiClient` interface.

### Endpoint naming: `/send-invitation` vs the plan's `/invite`

The plan §4 and the PRD AC4 reference `POST /api/v1/events/{code}/speakers/{speakerId}/invite` for the slot-capacity-gated invitation. The actual current code has TWO related endpoints:
- `POST /api/v1/events/{eventCode}/speakers/invite` — creates a speaker pool entry from `{ email }` (`SpeakerInvitationController.java:49`). Used by batch-invite flows; this is NOT the READY→INVITED transition path.
- `POST /api/v1/events/{eventCode}/speakers/{username}/send-invitation` — drives READY→INVITED via `SpeakerInvitationService.sendInvitation` (`SpeakerInvitationController.java:101`). This is the slot-gated path that AC4 tests.

The plan's `/speakers/{speakerId}/invite` is a renaming of `/speakers/{username}/send-invitation` (per the plan's "now only sends the email and transitions to INVITED" phrasing — exactly what `send-invitation` already does). **This story does NOT rename the endpoint** (out of scope; the rename would touch the speaker-portal frontend, the moderator UI, and any internal callers). Story 11.D.2 or 11.D.4 — both of which redesign the organizer kanban — are the appropriate place to do the rename if the team wants it. See Open Question #3.

### Why slot-capacity AC is "observation-only"

The slot-capacity gate is fully implemented in Story 11.B.2 (file `SpeakerWorkflowService.java:201-210`, exception class `SlotCapacityReachedException.java`, handler `GlobalExceptionHandler.java:795-820`). 11.B.2's unit test suite verifies the precondition fires inside `transition()`. What 11.B.2 did **not** do is verify the 409 surfaces through the full HTTP stack on the `/send-invitation` endpoint with the documented body shape. This story adds that test (AC9 item 2) and documents the 409 in the OpenAPI spec — closing the documentation + integration-test loop that Phase D's UX changes (Story 11.D.2's "disabled-with-tooltip" state, Story 11.D.4's drag-drop rejection) will rely on.

### Brainstorming panel: keep the form lean

The existing form has 5 fields (`speakerName`, `company`, `expertise`, `assignedOrganizerId`, `notes`) and no email field. **Do not add fields** as part of this story. The promote modal is a separate component that captures `{ email, firstName?, lastName? }` only — the speaker's `speakerName`/`company`/`expertise` from the pool entry are already on the entity and do not need to be re-captured.

If the speaker's `speakerName` is parseable as "First Last", the modal SHOULD pre-fill `firstName` and `lastName` (per AC7) to save the organizer a typing step. Reuse `splitName` if it exists in the frontend (search `web-frontend/src/`); otherwise inline a one-liner — do not introduce a new helper module for a 3-line split.

### Status-field naming on the frontend

The frontend's TypeScript types use lowercase status values (`'identified'`, `'contacted'`, `'ready'`, …) — matching the database storage form per project-context.md "Enum Value Flow". The JSON-on-the-wire form is UPPER_CASE (e.g., `"CONTACTED"`). Confirm the existing `SpeakerStatusLanes.tsx` mapping (TypeScript may use either form depending on whether it's reading from `speaker.status` directly or going through a mapper). The dev should match the existing pattern used by `SpeakerStatusLanes.tsx` — do not introduce a new mapping convention.

### Test approach summary

This is a **state-machine driving endpoint** + a **UI modal**. The Test Pyramid:
1. **Unit + Integration (Java, Testcontainers PostgreSQL)** — the controller's happy/error paths, the AR23 rejection, the slot-capacity 409 surfacing.
2. **Bruno** — HTTP contract tests for each new path + the slot-capacity 409 on `/send-invitation`.
3. **Vitest + RTL** — the modal renders, the button is conditional on status, the dialog calls the service, error surfaces render.
4. **Playwright** — end-to-end: organizer clicks → modal opens → fills email → submits → speaker visually moves to READY lane.

Per CLAUDE.md "Quality Standards": pipe gradle/npm output through `tee /tmp/<name>.log`, then `grep` the log file. Saves time when iterating.

### Domain exceptions

This story introduces (or may introduce) one new exception:

- `InvalidPromotionStateException(SpeakerWorkflowState currentState)` — thrown by the controller pre-check when promote is called on a non-CONTACTED speaker (AC3). Maps to HTTP 409 via a new `@ExceptionHandler` in `GlobalExceptionHandler`. Optional — the dev may inline the response building in the controller instead. Either is acceptable; the dedicated exception class matches the project pattern (see `SlotCapacityReachedException`) and surfaces better in stack traces.

No other new exceptions. The dev does **not** introduce `EmailRequiredException`, `InvalidEmailException`, etc. — Bean Validation handles those via the existing `MethodArgumentNotValidException` handler.

### Existing email-service surface — preserve, don't rewrite

Per Story 11.B.2 Dev Notes: do NOT modify `SpeakerInvitationEmailService`, `SpeakerAcceptanceEmailService`, or the magic-link `MagicLinkService` in this story. The READY hook already does the right thing (calls `getOrCreateUser` + `grantSpeakerRole`). The INVITED hook already sends the invitation email. Both are untouched.

Phase E (Story 11.E.2) rewrites the invitation email template to embed the Cognito login URL + temporary password. Phase F (Story 11.F.1) deletes `MagicLinkService`. This story is in Phase D — strictly before those teardowns.

### What this story is NOT doing (scope guard)

- **No new state-aware primary-action button on every kanban card.** That is Story 11.D.2 (UX-DR1-4).
- **No column-header triage chips or time-in-state colour coding.** That is Story 11.D.3 (UX-DR5-7).
- **No guided drag-drop, unified drawer, or on-behalf content form.** That is Story 11.D.4 (UX-DR8-14).
- **No Cognito user provisioning.** The READY hook today calls `getOrCreateUser` + `grantSpeakerRole` (stub). Phase E (Story 11.E.2) replaces the stub with `AdminCreateUser` + `AdminSetUserPassword` + `AdminAddUserToGroup`. This story is unaffected by that switch.
- **No endpoint rename** (`/send-invitation` → `/invite`). See "Endpoint naming" above + Open Question #3.
- **No frontend changes to the speaker portal pages** (`web-frontend/src/pages/speaker/**`). This story only touches the organizer-side brainstorm panel.
- **No deletion of magic-link infrastructure.** Phase F.
- **No changes to other Phase B/C work.** This story builds on 11.B.2 (and inherits from 11.C.2 when it lands).

### Project Structure Notes

- Backend story files live in `services/event-management-service/src/main/java/ch/batbern/events/`. The new controller method lands in `controller/SpeakerStatusController.java` (which already houses all per-speaker per-event transition endpoints per Story 11.B.2). The new DTO lands in `dto/PromoteSpeakerRequest.java`.
- Frontend brainstorm panel lives at `web-frontend/src/components/SpeakerBrainstormingPanel/`. The new dialog component is co-located there.
- All 10 locale files under `web-frontend/public/locales/{locale}/organizer.json` must be updated in this story (per project-context.md: "All 9 locales … + gsw-BE updated; no locale lags behind").

### References

- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 820-892] — Story 11.D.1 AC list (this story's primary spec).
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §0.2 + §0.5] — `CONTACTED → READY` provisioning gate; slot-capacity precondition.
- [Source: docs/plans/speaker-workflow-refactor.md §4] — API surface changes table; new `/promote` endpoint; tightened `/pool` endpoint.
- [Source: docs/architecture/04-api-speaker-coordination.md] — speaker-coordination API doc (already references `/promote` per Story 11.A.1 AC9).
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java:114-162] — `transition()` signature + body.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java:201-210] — slot-capacity precondition (`enforceSlotCapacity`).
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java:250-286] — READY hook (`runReadyHook`).
- [Source: services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java:795-820] — `SlotCapacityReachedException` → HTTP 409 mapping.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java] — existing per-speaker controller (where the new method lands).
- [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerInvitationController.java:101-114] — existing `/send-invitation` endpoint (subject of AC4).
- [Source: services/event-management-service/src/main/java/ch/batbern/events/dto/AddSpeakerToPoolRequest.java] — DTO that must reject unknown fields per AC5.
- [Source: web-frontend/src/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel.tsx] — UI surface for AC6/AC7.
- [Source: web-frontend/src/services/speakerPoolService.ts] — service layer where the new method lands (AC8).
- [Source: web-frontend/src/hooks/useSpeakerPool.ts] — TanStack Query hook layer (AC8).
- [Source: CLAUDE.md §"Critical Development Standards"] — TDD, Testcontainers PostgreSQL (never H2), OpenAPI-first, presigned uploads.
- [Source: _bmad-output/project-context.md §"Enum Value Flow"] — Java UPPER_CASE, JSON UPPER_CASE, DB lowercase_snake_case.
- [Source: _bmad-output/project-context.md §"OpenAPI Contract-First"] — controllers implement generated `*Api` interface; DTOs in `build/generated/` not committed; frontend types in `src/types/generated/` ARE committed.
- [Source: _bmad-output/implementation-artifacts/11-b-2-speakerworkflowservice-sole-status-writer.md] — single-writer invariant + side-effect hook contract.
- [Source: _bmad-output/implementation-artifacts/11-c-2-userapiclient-provisioning-contentsubmissionservice-shared.md] — `provisionUserWithRole` contract (for the future swap).

### Testing Standards (for an event-management transition + UI story)

- Backend: extend `AbstractIntegrationTest` (Testcontainers PostgreSQL with `withReuse(true)`); `@Transactional` so each test rolls back. Mock `DomainEventPublisher` to capture published events; mock `SpeakerInvitationEmailService` to verify no email when transition rolls back; do NOT mock `SpeakerWorkflowService` (the integration tests exercise the real service).
- Frontend unit: Vitest + RTL; use `screen` queries; `userEvent` over `fireEvent`; `waitFor` for async state. Use `msw` 2.x for HTTP mocking.
- Bruno: tests live in `bruno-tests/events-api/`; numbering follows existing sequence. Use the `{{organizerAuthToken}}` variable per CLAUDE.md.
- Playwright: `chromium` project (organizer auth). Run via `npx playwright test --project=chromium`. Auth state is at `.playwright-auth-organizer.json`.

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.7 (1M context) via Claude Code (`bmad-dev-story` skill).

### Debug Log References

- `/tmp/em-test-promote.log` — first run of new `SpeakerPromoteControllerIntegrationTest` + unit tests (39 passed, 0 failed)
- `/tmp/em-test-broader2.log` — workflow + invitation + pool + promote integration tests (103 passed)
- `/tmp/em-full-test.log` — full `:services:event-management-service:test` after all changes (1533 PASSED, 0 FAILED, BUILD SUCCESSFUL in 20m 59s)
- `/tmp/fe-hook-test.log` — `useSpeakerPoolHooks.test.ts` (29 passed)
- `/tmp/fe-bp-test3.log` — `SpeakerBrainstormingPanel.test.tsx` after `key`-based dialog remount fix (11 passed)
- `/tmp/fe-full-test.log` — full frontend vitest run (4868 passed, 110 skipped, 23 todo across 352 files)
- `/tmp/fe-tc2.log` + `/tmp/fe-tc3.log` — `npm run type-check` (clean both runs)
- `/tmp/fe-lint.log` — `npm run lint` (clean)
- `/tmp/grep-invariants.log` — AC11.7 invariant grep results (all clear: single status writer, no email on pool DTOs, no email input in brainstorm form)

### Completion Notes List

- **AC1 — new `POST /promote` endpoint.** Path documented in `docs/api/events-api.openapi.yml` with `operationId: promoteSpeakerToReady`, `additionalProperties: false`, full 200/400/401/403/404/409/500 response set + examples. Controller method at `SpeakerStatusController.promoteSpeakerToReady` builds `SecurityPrincipal` from `SecurityContextHelper`, calls `SpeakerWorkflowService.transition(speakerId, READY, actor, payload)`, returns `SpeakerPoolResponse.fromEntity(promoted)`. The single-writer invariant is preserved — the controller does NOT touch `UserApiClient`, `speaker.setUsername`, or `speaker_status_history` directly.
- **AC1 (READY hook refactor).** Story 11.C.2 added `UserApiClient.provisionUserWithRole(...)` but did not migrate `SpeakerWorkflowService.runReadyHook` to it. This story completes the refactor: `runReadyHook` now calls `provisionUserWithRole(...)` exactly once (with role=SPEAKER) instead of the legacy `getOrCreateUser + speakerProvisioningHook.grantSpeakerRole` pair. Verified end-to-end via Mockito spy in `SpeakerPromoteControllerIntegrationTest#should_returnReady_when_promoteCalledOnContactedSpeaker` and `SpeakerWorkflowServiceIntegrationTest#should_invokeProvisioningSeamAndPublishEvent_when_transitioningContactedToReady`. The `SpeakerProvisioningHook` field remains injected (no production callsite — kept to minimise blast radius; tests assert `verify(speakerProvisioningHook, never()).grantSpeakerRole(...)`).
- **AC2 — Bean Validation 400.** `PromoteSpeakerRequest` is a record with `@NotBlank @Email @Size(max=320)` on `email`. `@JsonIgnoreProperties(ignoreUnknown = false)` rejects unknown fields. Three negative tests cover empty body, malformed email, and unknown-field rejection — all surface 400 via `MethodArgumentNotValidException` / `HttpMessageNotReadableException` and leave the speaker unchanged.
- **AC3 — 409 / idempotent 200.** New `InvalidPromotionStateException(currentState)` + `GlobalExceptionHandler.handleInvalidPromotionStateException` maps to 409 with `details.code = INVALID_PROMOTION_STATE` and `details.currentState`. Pre-check in controller loads the speaker and rejects every state except `CONTACTED` (happy path) and `READY` (idempotent same-state branch — flows through to `transition()`, writes a self-transition audit row, no provisioning call, returns 200). Tailored messages for IDENTIFIED, DECLINED, INVITED/ACCEPTED/CONTENT_SUBMITTED/QUALITY_REVIEWED.
- **AC4 — slot-capacity 409 surfaces on `/send-invitation`.** Observation-only: precondition lives in `SpeakerWorkflowService.enforceSlotCapacity` (Story 11.B.2). New integration test `SpeakerInvitationControllerIntegrationTest#should_return409_when_sendInvitationCalledAndSlotCapacityReached` drives the full HTTP path through the invite controller, saturates ACCEPTED, and asserts the 409 body with `details.code = SLOT_CAPACITY_REACHED` + `eventId/acceptedCount/invitedCount/maxSlots`. Bruno test `54-send-invitation-slot-capacity-409.bru` documents the same contract end-to-end.
- **AC5 — `email` rejected on `/pool` POST + PATCH.** Both `AddSpeakerToPoolRequest` and `PatchSpeakerPoolRequest` are now annotated with `@JsonIgnoreProperties(ignoreUnknown = false)`. The `email` field was removed from `PatchSpeakerPoolRequest` and the corresponding `if (request.getEmail() != null)` block was removed from `SpeakerPoolService.patchEntry`. OpenAPI spec for `AddSpeakerToPoolRequest` now declares `additionalProperties: false` + the tightening bullet + a second 400 example showing the unknown-field rejection. PATCH endpoint is not OpenAPI-documented (Spring-annotated only); the Java-side `@JsonIgnoreProperties` enforces the same contract. Integration test `should_return400_when_addSpeakerToPoolWithEmailField` + Bruno `48-add-speaker-to-pool-rejects-email.bru` lock it in.
- **AC6 — no email input in brainstorm form.** Verified: `grep -n "name=\"email\"|getByLabelText.*[Ee]mail" SpeakerBrainstormingPanel.tsx` returns zero matches outside the new dialog component. New test `should_not_renderEmailInput_when_inIdentifiedOrContactedMode` asserts the invariant.
- **AC7 — Promote modal + button.** New component `PromoteSpeakerDialog.tsx` (react-hook-form + zod) renders only when `promoteSpeaker` state is non-null; the parent panel uses `key={speaker.id}` to force remount on each open (cleaner than `useEffect`-on-`open`, avoids re-render storm in test). Button is rendered as `secondaryAction` on the `<ListItem>` only when `statusUpper === 'CONTACTED'`; IDENTIFIED + READY+ get no button. Pre-fills `firstName`/`lastName` from `speaker.speakerName` via inline `splitFullName` helper. On 409 INVALID_PROMOTION_STATE the modal stays open and renders an interpolated warning `<Alert>`; on other errors a generic error alert.
- **AC8 — frontend service + hook.** `speakerPoolService.promoteToSpeaker(eventCode, speakerId, request)` calls `POST .../promote` via the existing `apiClient`. `usePromoteSpeakerToReady()` mutation invalidates `speakerPool.list(eventCode)` + `speakerStatusSummary` on success. Hook tests cover happy path + 409 error path.
- **AC9 — 4-layer pyramid.** Backend: `SpeakerPromoteControllerIntegrationTest` (10 cases) + new slot-capacity test in `SpeakerInvitationControllerIntegrationTest` + email-rejection test in `SpeakerPoolWorkflowIntegrationTest`. Bruno: 6 new files (43-48 + 54). Frontend unit: 5 new tests in `SpeakerBrainstormingPanel.test.tsx` + 2 in `useSpeakerPoolHooks.test.ts`. Playwright: promote-via-UI happy-path E2E added to `speaker-brainstorming.spec.ts`; slot-capacity surface deferred to Java/Bruno layers (UI surface is Story 11.D.3 per §8.9 story map).
- **AC10 — docs.** OpenAPI spec updated in the same change. `docs/architecture/04-api-speaker-coordination.md` already documents the `/promote` endpoint per Story 11.A.1 — no drift. No CLAUDE.md / ADR-009 / state-machine doc changes (single-writer + state machine unchanged by this story).
- **AC11 — invariants verified.**
  - Backend build: `BUILD SUCCESSFUL` (`/tmp/em-full-test.log`), 1533 PASSED / 0 FAILED.
  - Frontend: 4868 tests passed (`/tmp/fe-full-test.log`); type-check + lint clean.
  - Grep invariants: `grep -rn "speaker.setStatus(|setStatus(SpeakerWorkflowState\."` returns exactly **one** match in production code (`SpeakerWorkflowService.java:149`) — single-writer preserved. Promote endpoint references appear only in the new DTO/controller/exception/handler. `AddSpeakerToPoolRequest` + `PatchSpeakerPoolRequest` have zero email fields. Brainstorm form has zero email inputs.
  - Bruno runner not executed locally (requires live backend + staging auth tokens). Tests are in-tree and will run in CI per `scripts/ci/run-bruno-tests.sh`.

### File List

**Backend (`services/event-management-service/`):**
- `src/main/java/ch/batbern/events/dto/PromoteSpeakerRequest.java` — NEW
- `src/main/java/ch/batbern/events/exception/InvalidPromotionStateException.java` — NEW
- `src/main/java/ch/batbern/events/dto/AddSpeakerToPoolRequest.java` — `@JsonIgnoreProperties(ignoreUnknown = false)` + doc
- `src/main/java/ch/batbern/events/dto/PatchSpeakerPoolRequest.java` — `@JsonIgnoreProperties(ignoreUnknown = false)`, removed `email` field
- `src/main/java/ch/batbern/events/controller/SpeakerStatusController.java` — `promoteSpeakerToReady` method + `SpeakerWorkflowService` + `SpeakerPoolRepository` deps
- `src/main/java/ch/batbern/events/controller/EventController.java` — PATCH `/speakers/pool/{speakerId}` description note
- `src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` — `handleInvalidPromotionStateException`
- `src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` — `runReadyHook` refactored to call `provisionUserWithRole`
- `src/main/java/ch/batbern/events/service/SpeakerPoolService.java` — removed `email` branch from `patchEntry`

**Backend tests:**
- `src/test/java/ch/batbern/events/controller/SpeakerPromoteControllerIntegrationTest.java` — NEW (10 cases)
- `src/test/java/ch/batbern/events/controller/SpeakerInvitationControllerIntegrationTest.java` — added slot-capacity 409 test
- `src/test/java/ch/batbern/events/controller/SpeakerPoolWorkflowIntegrationTest.java` — added AR23 email-rejection test
- `src/test/java/ch/batbern/events/service/SpeakerWorkflowServiceTest.java` — migrated mocks to `provisionUserWithRole`
- `src/test/java/ch/batbern/events/service/SpeakerWorkflowServiceIntegrationTest.java` — migrated mocks + added `clearInvocations` in `@BeforeEach`

**OpenAPI spec:**
- `docs/api/events-api.openapi.yml` — new `/promote` path + `PromoteSpeakerRequest` schema + tightened `AddSpeakerToPoolRequest`

**Bruno API contract tests (`bruno-tests/events-api/`):**
- `43-setup-promote-test-speaker.bru` — NEW
- `44-promote-test-speaker-move-to-contacted.bru` — NEW
- `45-promote-speaker-happy-path.bru` — NEW
- `46-promote-speaker-missing-email.bru` — NEW
- `47-promote-speaker-already-in-state.bru` — NEW (same-state READY idempotent 200)
- `48-add-speaker-to-pool-rejects-email.bru` — NEW
- `54-send-invitation-slot-capacity-409.bru` — NEW

**Frontend (`web-frontend/`):**
- `src/types/speakerPool.types.ts` — added `PromoteSpeakerRequest`, removed `email` from `PatchSpeakerPoolRequest`
- `src/types/generated/events-api.types.ts` — regenerated via `npm run generate:api-types`
- `src/services/speakerPoolService.ts` — `promoteToSpeaker` method
- `src/hooks/useSpeakerPool.ts` — `usePromoteSpeakerToReady` mutation hook
- `src/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel.tsx` — Promote button per CONTACTED entry + dialog wiring
- `src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx` — NEW

**Frontend tests:**
- `src/hooks/useSpeakerPoolHooks.test.ts` — happy + 409 cases for `usePromoteSpeakerToReady`
- `src/components/SpeakerBrainstormingPanel/SpeakerBrainstormingPanel.test.tsx` — 5 new promote-UI tests
- `e2e/organizer/speaker-brainstorming.spec.ts` — promote-happy-path E2E

**i18n (all 10 locales — `web-frontend/public/locales/{locale}/organizer.json`):**
- `de/organizer.json` · `en/organizer.json` · `es/organizer.json` · `fi/organizer.json` · `fr/organizer.json` · `gsw-BE/organizer.json` · `it/organizer.json` · `ja/organizer.json` · `nl/organizer.json` · `rm/organizer.json` — added `speakerBrainstorm.actions.promoteToSpeaker` + full `speakerBrainstorm.promoteDialog.*` keys

**Sprint tracking:**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — `11-d-1-...` → `in-progress` → `review`; `last_updated` bumped to 2026-05-16
- `_bmad-output/implementation-artifacts/11-d-1-promote-endpoint-brainstorm-tightening-slot-gate.md` — Status: `ready-for-dev` → `in-progress` → `review`; Dev Agent Record + File List + Change Log populated

### Change Log

| Date | Change |
|------|--------|
| 2026-05-16 | Story 11.D.1 drafted via `bmad-create-story`. |
| 2026-05-16 | Resolved all 5 Open Questions with PM (Nissim). Q1 → strict 11.C.2 → 11.D.1 sequencing (AC1 + Dependencies + Dev Notes updated to assert `provisionUserWithRole` is the provisioning call). Q2 → controller pre-check with `InvalidPromotionStateException` for clean 409. Q3 → do NOT rename `/send-invitation` to `/invite` in this story. Q4 → idempotent 200 on same-state READY re-promote. Q5 → machine-translation baseline for non-EN/DE locales (follow-up review acceptable). |
| 2026-05-16 | Implementation landed (Amelia / Dev Agent). All 11 ACs satisfied. Backend `BUILD SUCCESSFUL` (1533 PASSED / 0 FAILED). Frontend 4868 tests passed; type-check + lint clean. Note: Story 11.C.2 added `provisionUserWithRole` but had not migrated `SpeakerWorkflowService.runReadyHook` from the legacy `getOrCreateUser + grantSpeakerRole` pair — this story completed that refactor so AC1's Mockito-spy assertion compiles. The legacy `SpeakerProvisioningHook` field remains injected with no production callsite (kept to minimise blast radius; tests assert `verify(speakerProvisioningHook, never())`). Slot-capacity surfacing on `/send-invitation` is covered by Java integration test + Bruno test; UI-surface for the 409 is owned by Story 11.D.3. |

---

## Open Questions (resolved 2026-05-16)

All five questions were resolved with PM (Nissim) before development. The AC, Tasks, and Dev Notes above already reflect the decisions. Listed here for traceability.

1. ✅ **Sequence 11.C.2 → 11.D.1 strictly.** The PRD AC1 wording ("`UserApiClient.provisionUserWithRole` is called") is taken literally. Story 11.C.2 must merge before 11.D.1 begins implementation, so the READY hook in `SpeakerWorkflowService` has been refactored to call `provisionUserWithRole(...)` by the time 11.D.1's integration tests run. The Dependencies section, AC1 (closing bullet list + Mockito spy assertion), and the Dev Note "Strict sequencing: 11.C.2 must merge before this story" all reflect this.

2. ✅ **Controller pre-check with dedicated `InvalidPromotionStateException` for the 409.** When promote is called on a non-CONTACTED speaker, the controller pre-loads the speaker, checks the status, and throws `InvalidPromotionStateException(currentState)` for a tailored 409 with `details.code = 'INVALID_PROMOTION_STATE'` and `details.currentState`. This avoids changing the global `InvalidStateTransitionException` → 400 mapping. AC3 + Task 2.4 capture this.

3. ✅ **Do not rename `/send-invitation` to `/invite` in this story.** The slot-capacity 409 is documented + tested on the existing `POST /speakers/{username}/send-invitation` path. The rename, if the team wants it, lands naturally in Story 11.D.2 (kanban primary-action button) or 11.D.4 (drag-drop). AC4 + the Dev Note "Endpoint naming: `/send-invitation` vs the plan's `/invite`" capture this.

4. ✅ **Same-state promote on a READY speaker returns 200, not 409.** Matches Story 11.B.2's same-state branch semantics — the call writes a self-transition audit row and returns the unchanged speaker. The post-READY cases (INVITED, ACCEPTED, ...) get a 409 from the controller pre-check. AC3 + AC9 item 1 capture this (test case renamed to `should_return200_when_speakerAlreadyInReady_idempotent`).

5. ✅ **Machine translation baseline for the 8 non-EN/DE locales.** Matches the Story 10-9 Phase 4 pattern. A follow-up locale-review pass is acceptable rather than a blocking step. AC7 + Task 8.2 capture this; the PR description should flag the locales that need a native-speaker review.

---

_Story created via `bmad-create-story` skill on 2026-05-16. Authored by PM (Nissim) with comprehensive context-engine analysis. Depends on Story 11.B.2 (single-writer `transition()`, landed on `feature/speaker-workflow-refactor`) AND Story 11.C.2 (`UserApiClient.provisionUserWithRole`, must merge first per Resolved Q#1). Ready for `bmad-dev-story` execution after 11.C.2 merges._
