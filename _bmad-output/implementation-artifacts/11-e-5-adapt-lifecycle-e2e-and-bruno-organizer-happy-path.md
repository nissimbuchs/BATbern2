# Story 11.E.5: Adapt event-lifecycle Playwright training-flow + sweep stale organizer-happy-path Bruno chain (events-api/37–41) to ADR-009

Status: ready-for-dev

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As a** developer maintaining the BATbern training-movie / user-guide E2E flow and the Bruno organizer-happy-path API contract chain,
**I want** the `web-frontend/e2e/workflows/event-lifecycle-e2e.spec.ts` test and the `bruno-tests/events-api/37→41` organizer-happy-path chain rewritten to use the 8-state ADR-009 workflow (CONTACTED → READY via `/promote`, READY → INVITED via the slot-gated invitation flow, INVITED → ACCEPTED as organizer-respond-on-behalf, ACCEPTED → CONTENT_SUBMITTED via the drawer's content-submission sub-view),
**So that** Epic 11 ships with a green end-to-end demonstration of the new organizer-driven happy path — the training video, user-guide screenshots, and Bruno suite all stay aligned with the production state machine; and the legacy `PUT /status` paths that now 4xx (`newStatus=READY` rejected by `ReadyRequiresPromoteException`, `READY → ACCEPTED` rejected by the new `ALLOWED_TRANSITIONS` allow-list) stop being a slow drip of "phase-B residue" failures masking real regressions.

**Phase:** E (trailing cleanup #2) — lands after 11.E.4, before 11.F.1.
**Dependencies:** None — all source stories (11.A.1, 11.B.*, 11.C.*, 11.D.*, 11.E.1–E.4) are `Status: done`. Can land in any order relative to 11.F.1.

**Scope (the seven things that change):**

1. Playwright `web-frontend/e2e/workflows/event-lifecycle-e2e.spec.ts` Phase B rewritten to drive the 8-state workflow as an organizer-only happy path (drag through CONTACTED → READY [with `PromoteSpeakerDialog`] → INVITED [slot-gated] → ACCEPTED [organizer respond-on-behalf] → CONTENT_SUBMITTED [drawer Content sub-view]).
2. Playwright Page Object `web-frontend/e2e/workflows/documentation/page-objects/SpeakerManagementPage.ts` extended with new helpers for the promote dialog + invitation drag + organizer-respond-on-behalf drag.
3. Bruno `bruno-tests/events-api/38-update-speaker-status-to-ready.bru` deleted (its target — `PUT /status` with `newStatus: READY` — is rejected as 422 by `ReadyRequiresPromoteException` at `SpeakerStatusController.java:190`; the promote happy path is already covered by tests 43–47 on a separate seeded speaker).
4. Bruno `bruno-tests/events-api/39-update-speaker-status-to-accepted.bru` rewritten to test `INVITED → ACCEPTED` via `PUT /status` (matches `SpeakerWorkflowService.ALLOWED_TRANSITIONS`'s `INVITED → {ACCEPTED, DECLINED}` row, which is the organizer-respond-on-behalf path used in `speaker-onbehalf-vs-self-byte-identity.spec.ts:159–164`); drops the obsolete "should trigger overflow detection" assertion.
5. Bruno **NEW** `bruno-tests/events-api/38-promote-speaker-to-ready.bru` and `bruno-tests/events-api/38b-send-invitation-to-speaker.bru` inserted between 37 and 39 to drive the speaker (`{{createdSpeakerPoolId}}` — the one used by tests 37/39/41, not the separate `{{promoteTestSpeakerId}}` chain) through `CONTACTED → READY → INVITED` so test 39 has a valid INVITED speaker to flip to ACCEPTED.
6. Bruno `bruno-tests/events-api/41-get-speaker-status-history.bru` history-sequence assertion (currently lines 47–54) updated from the 3-transition pre-Epic-11 chain (`CONTACTED`, `READY`, `ACCEPTED`) to the 4-transition ADR-009 §0.2 chain (`CONTACTED`, `READY`, `INVITED`, `ACCEPTED`).
7. Bruno `bruno-tests/events-api/40-invalid-status-transition.bru` left as-is (the `ACCEPTED → IDENTIFIED` negative is still a correct negative case; the speaker is in ACCEPTED after the rewritten 39, so the precondition holds).

**Out of scope (DO NOT touch):**

- **Magic-link speaker-portal-api Bruno chain (`01–11, 19–29` magic-token tests)** — explicitly owned by Story 11.F.1 per 11.E.4's "Out of scope" block. `auth: none` + `speakerMagicToken`/`speakerViewToken`-driven tests against the deleted `/speaker-portal/respond`, `/speaker-portal/content`, `/speaker-portal/content/submit` endpoints (the OLD endpoints — the new path-prefixed ones at `/speaker-portal/events/{eventCode}/...` are already covered by tests `30–34`). 11.F.1 deletes those tests alongside the backend magic-link code.
- **Speaker-self happy path in the lifecycle E2E.** The training video and user guide narrate an organizer-driven workflow end-to-end; speaker-self submission has separate dedicated coverage in `web-frontend/e2e/organizer/speaker-onbehalf-vs-self-byte-identity.spec.ts` (Story 11.D.4 AC10 case 51) and the three Phase F `test.fixme` specs (`e2e/speaker/speaker-portal-*.spec.ts`) waiting on the staging test-speaker Cognito seed. Keep `event-lifecycle-e2e.spec.ts` strictly organizer-driven.
- **The 3 `test.fixme` Playwright specs** (`speaker-portal-respond.spec.ts`, `speaker-portal-content-submit.spec.ts`, `speaker-portal-cross-portal-nav.spec.ts`) — wait on the staging test-speaker Cognito seed; tracked by 11.F.1.
- **Phase D-already-covered behaviours.** Don't add new component-level coverage of the kanban primary-action button, column triage, guided-drag halo/lock/dim, or unified drawer chrome. Those are covered by `speaker-card-primary-action.spec.ts`, `speaker-column-triage.spec.ts`, `speaker-kanban-guided-drag.spec.ts`. This story is strictly about the lifecycle / training-flow narrative + the four organizer-chain Bruno files.
- **Training-video narration scripts** at `web-frontend/e2e/workflows/documentation/screencast/script-{de,en}-*.md` + the timing configs at `timing-config-{de,en}.ts`. Phase B now has 4 distinct drag operations instead of the old 2 (CONTACTED → READY → INVITED → ACCEPTED instead of CONTACTED → READY → ACCEPTED). The narration text and subtitle timings will drift; flagged in the Dev Agent Record for a separate documentation-content pass owned by the tech writer (Paige).
- **Bruno `35-add-speaker-to-pool` through `48-add-speaker-to-pool-rejects-email`** — already aligned with the new model per Story 11.D.1.
- **Native-speaker review of the 8 non-DE/EN locales** — cosmetic, non-blocking.
- **The `complete-event-workflow.spec.ts` companion test** at `web-frontend/e2e/workflows/documentation/complete-event-workflow.spec.ts` — touched by 11.D.3 git history; ad-hoc spot-check only. If it has the same Phase-B-drag pattern, document it in the Dev Agent Record but don't fix here (out-of-scope).

---

## Acceptance Criteria

ACs are pinned to specific file paths, line numbers, and the post-change shape. The cross-reference under each AC names the source code that makes the current test stale.

### AC1 — Playwright Phase B Step 3 (CONTACTED → READY) drives `PromoteSpeakerDialog` and lands in READY

**Given** `web-frontend/e2e/workflows/event-lifecycle-e2e.spec.ts` Phase B (`test('Phase B: Speaker Outreach (Steps 4-6)', ...)`) currently at lines 376–436 drags each of the four `speakersToMove` from CONTACTED → READY and then probes for `getByTestId('status-change-confirm')`,

**When** I read the rewritten Phase B Step 3 block,

**Then** the rewritten block:
1. Drags each speaker card to `getByTestId('status-lane-ready')` using the existing raw-pointer pattern (the surrounding mouse.move/down/up calls stay — only the post-drop handling changes).
2. After the drop, waits for `getByTestId('promote-email-field')` to be visible (the modal opens because `classifyDrop('CONTACTED', 'READY', false)` returns `{ kind: 'legal-input', modal: 'promote' }` per `web-frontend/src/components/organizer/SpeakerStatus/speakerTransitions.ts:103–105`, and the `legal-input → promote` branch in `SpeakerStatusLanes.tsx:614–615` invokes `onPromoteSpeaker` which opens `PromoteSpeakerDialog`).
3. Fills:
   - `getByTestId('promote-email-field')` with `${speaker.label.toLowerCase()}+promote@e2e.batbern.invalid` (must end in `@e2e.batbern.invalid` so it survives the `48-add-speaker-to-pool-rejects-email.bru` collection sweep convention used by other E2E seeds; the `+promote` qualifier disambiguates from any pre-existing fixture user).
   - `getByTestId('promote-first-name-field')` with `speaker.label` (the existing label strings `"Nissim"`, `"Balti"`, `"Andreas"`, `"Daniel"`).
   - `getByTestId('promote-last-name-field')` with `"E2E"` (literal — keeps it distinct from any real user; the field is `@NotBlank @Size(max=100)` per `PromoteSpeakerRequest.java` and any non-empty value satisfies it).
4. Clicks `getByTestId('promote-submit-button')`.
5. Waits for the modal to close (`getByTestId('promote-email-field').not.toBeVisible({ timeout: 5000 })`).
6. Asserts the card is now inside `status-lane-ready` via `page.locator('[data-testid="status-lane-ready"] [data-testid^="speaker-card-"]').count() === N` where `N` increments by 1 per iteration. Use the testid-prefix selector — the original `getByRole('button', { name: speaker.name })` matcher won't work cleanly because the card name format on a READY card after promote may change (the speaker now has a username, so the avatar initial and aria-label may shift).

**Given** the test runs end-to-end against `make dev-native-up`,

**When** Phase B Step 3 completes,

**Then** all four speakers are in READY, the test does not log "Failed to get boundingBox", and the network log (already enabled at line 80–85) does NOT show any `4xx` response for `POST /api/v1/events/{code}/speakers/{id}/promote`.

> **Origin:** Current test at `event-lifecycle-e2e.spec.ts:393–433` looks for `getByTestId('status-change-confirm')` (the `StatusChangeDialog` confirm — used by `legal-decline` drops, NOT `legal-input` drops). The new modal under 11.D.4 is `PromoteSpeakerDialog` with test-ids `promote-email-field` / `promote-first-name-field` / `promote-last-name-field` / `promote-submit-button` (`web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx:219, 235, 251, 268`). `firstName` and `lastName` are required (`@NotBlank`) per Story 11.E.4 AC4.

---

### AC2 — Playwright Phase B Step 4 (READY → INVITED) inserted as a new step that fires the direct invitation mutation

**Given** the rewritten Phase B has a new step between the old Step 3 and the old Step 4,

**When** I read the new Phase B Step 4 block (READY → INVITED),

**Then** the block:
1. Re-fetches the four speaker cards from the READY lane (they may have re-ordered after the promote modal closed) — use the testid-prefix selector `page.locator('[data-testid="status-lane-ready"] [data-testid^="speaker-card-"]').all()` to get the live card handles.
2. For each card, performs the raw-pointer drag to `getByTestId('status-lane-invited')` (the lane testid is lowercased — see `SpeakerStatusLanes.tsx:902` `data-testid={`status-lane-${status.toLowerCase()}`}`).
3. **Does NOT wait for any modal** — `classifyDrop('READY', 'INVITED', false)` returns `{ kind: 'legal-input', modal: 'invitation' }` per `speakerTransitions.ts:106–110`, BUT the `onSendInvitation` callback in `SpeakerStatusLanes.tsx:1101–1121`'s default `handleSendInvitation` fires `sendInvitationMutation.mutateAsync` **directly** with `responseDeadline = today + 30 days` — no modal is opened, only a success/error snackbar. Wait instead for the snackbar via `page.getByText(/invitation sent|einladung gesendet/i).waitFor({ timeout: 5000 })` (the i18n key `organizer:speakerCard.inviteSent`).
4. After all four invitations, asserts each card has moved from `status-lane-ready` to `status-lane-invited` using the count-based assertion pattern from AC1.

**Given** the event was created in Phase A Step 2 with `venueCapacity: 200`,

**When** the four READY → INVITED drags fire,

**Then** **none** of them is rejected by the slot-capacity gate. The gate fires when `count(ACCEPTED) + count(INVITED) >= max_slots` per `SpeakerWorkflowService.java` line ~283 (`computeSlotCapacity` in `web-frontend/src/components/organizer/SpeakerStatus/getPrimaryAction.ts:42–57`). The Phase-A event's `max_slots` defaults to the event-type's slot count (EVENING → typically 4 slots — **VERIFY at story-impl time** by reading `EventTypeConfig` or the event-creation response). If `max_slots = 4` and we have 4 invitations targeting it, the 4th drag is on the boundary — `accepted (0) + invited (3) < 4` → 4th drag is the last legal one. If `max_slots < 4`, drop one speaker from `speakersToMove` (e.g., move only Nissim + Balti + Andreas, leave Daniel in READY) AND adjust AC3 + Phase B.5 + Phase C accordingly so the test runs with `max_slots`-many speakers throughout. The dev MUST capture the resolved `max_slots` in the Dev Agent Record and document the chosen speaker count.

> **Origin:** Current test at `event-lifecycle-e2e.spec.ts:441–503` drags READY → ACCEPTED, which is **not in `ALLOWED_TRANSITIONS`** (READY can only go to INVITED or DECLINED per `SpeakerWorkflowService.java:75–76` and `speakerTransitions.ts:40`). The drag currently fires `case 'illegal'` in `SpeakerStatusLanes.tsx:579–586`, surfaces a `kanban-drop-toast`, and the card stays in READY. The test then silently passes because it has no post-drag assertion of the card's lane.

---

### AC3 — Playwright Phase B Step 5 (INVITED → ACCEPTED) inserted as a new step that fires the legal-direct mutation

**Given** the rewritten Phase B has a final step replacing the old Step 4,

**When** I read the new Phase B Step 5 block (INVITED → ACCEPTED — organizer respond-on-behalf),

**Then** the block:
1. Re-fetches the four (or fewer per AC2's `max_slots` resolution) speaker cards from the INVITED lane.
2. For each card, performs the raw-pointer drag to `getByTestId('status-lane-accepted')`.
3. **Does NOT wait for any modal** — `classifyDrop('INVITED', 'ACCEPTED', false)` returns `{ kind: 'legal-direct' }` per `speakerTransitions.ts:118` ("INVITED → ACCEPTED is the only legal-direct today"). The dispatcher in `SpeakerStatusLanes.tsx:632–635` fires `updateStatusMutation.mutate({ speakerId, newStatus: 'ACCEPTED' })` with **no reason field**, no modal, no confirmation. Wait for the card to land in the destination lane.
4. After all drops, asserts each card has moved from `status-lane-invited` to `status-lane-accepted` using the count-based assertion pattern from AC1.

**Given** the dev verifies the end-state,

**When** the test reaches the end of Phase B,

**Then** the screenshot the documentation pipeline captures at end-of-Phase-B (existing `screenshot-helpers.ts` invocation, if any) shows the speakers stacked under ACCEPTED — matching what the training video's "now they've accepted" narration beat expects.

> **Origin:** The new flow needs an explicit INVITED step before ACCEPTED. The `INVITED → ACCEPTED` transition represents either the speaker-self acceptance via the Cognito-secured portal OR the organizer responding on behalf via the same `PUT /status` endpoint (audit-trail differs only by `changedByUsername`). The lifecycle test stays organizer-driven, so the organizer is the principal — exactly the pattern at `speaker-onbehalf-vs-self-byte-identity.spec.ts:160–164`.

---

### AC4 — Playwright Phase B.5 (ACCEPTED → CONTENT_SUBMITTED) drives the drawer's content-submission sub-view via the primary-action button

**Given** the existing Phase B.5 at `event-lifecycle-e2e.spec.ts:519–608` clicks the speaker card (`speakerCard.click()` at line 562) to open the drawer at its default view and then fills `presentation-title-field` / `presentation-abstract-field`,

**When** I read the rewritten Phase B.5,

**Then** the rewrite:
1. Replaces `speakerCard.click()` with a click on `page.getByTestId(`primary-action-button-${speakerId}`)` — this is the per-card primary-action button added by Story 11.D.2 (`SpeakerStatusLanes.tsx:1473`). For an ACCEPTED speaker, this button's `testIdSuffix` is `'enter-content'` per `getPrimaryAction.ts:142`, and clicking it invokes `onEnterContent(speaker)`, which sets `initialDrawerView = 'content-submission'` on `EventSpeakersTab.tsx:221` and opens the drawer pre-positioned on the `ContentSubmissionSubView` (`SpeakerDetailDrawer.tsx:316–317`).
2. Discovers the `speakerId` for each ACCEPTED speaker by reading the card's testid: `await page.locator('[data-testid="status-lane-accepted"] [data-testid^="speaker-card-"]').first().getAttribute('data-testid')` returns `speaker-card-{uuid}`; strip the `speaker-card-` prefix.
3. Waits for `getByTestId('presentation-title-field')` to be visible after the button click (confirms the drawer opened at the content-submission sub-view, not the default view).
4. Fills `presentation-title-field` + `presentation-abstract-field` using the existing `testConfig.presentations` data (unchanged).
5. Clicks `getByTestId('submit-speaker-content-button')` (unchanged testid; the form's submit button is now inside `ContentSubmissionSubView.tsx:461`).
6. Waits for the card to move from ACCEPTED to CONTENT_SUBMITTED via the count-based lane assertion.
7. **Removes** the `publish-topic-button` click block at lines 535–537 unless that publish step is genuinely required to enable content submission. Verify by reading `ContentSubmissionService.submit()` in EMS — if title/abstract submission requires the topic to be published, keep it; otherwise drop it. **Likely outcome:** keep the publish-topic click because the user-guide narration depends on the publishing-tab visit (training-video beat); document the verdict in the Dev Agent Record.

**Given** the rewrite removes the legacy "Switch to Sessions view" step (current line 540 `await page.getByTestId('event-tab-speakers').click()`) only IF the new flow does not require returning to the speakers tab after clicking publish-topic,

**Then** the speaker-tab navigation either stays (with a JSDoc-style comment naming the reason — keep for narration continuity) or is removed (with the corresponding `Step 3: Return to Speakers tab` console log dropped).

> **Origin:** Phase B.5 currently calls `speakerCard.click()` which opens the drawer at its default 2-tab view (Story 11.D.4 collapsed the drawer to 2 tabs — `OverviewTabPanel + ActivityTabPanel deleted`). Without the primary-action-button path, the user would have to navigate to the Content sub-tab manually. The training-video beat — "the organizer clicks the per-card action button labelled 'Enter content' and the form opens" — is the new UX flow and is what should be filmed.

---

### AC5 — Playwright Phase C (CONTENT_SUBMITTED → QUALITY_REVIEWED) drives the quality-review primary-action button

**Given** the existing Phase C at lines 615–663 clicks the speaker card and then `getByTestId('approve-content-button')`,

**When** I read the rewritten Phase C,

**Then** the rewrite:
1. Replaces `presentationCard.click()` (line 650) with a click on `page.getByTestId(`primary-action-button-${speakerId}`)` for each speaker now in CONTENT_SUBMITTED — the primary-action's `testIdSuffix` for CONTENT_SUBMITTED is `'review-content'` per `getPrimaryAction.ts:152`, and clicking it sets `initialDrawerView = 'quality-review'`, opening the drawer's quality-review sub-view.
2. Keeps the `getByTestId('approve-content-button')` click — that testid is preserved by Story 11.D.4 inside the quality-review sub-view component (verify the testid is wired in the new component; if it was renamed, update the test to match).
3. Asserts each card moves from CONTENT_SUBMITTED to QUALITY_REVIEWED via the count-based lane assertion.

**Given** the dev runs `grep -rn "approve-content-button" web-frontend/src/`,

**When** the grep returns the canonical owner of the testid,

**Then** the testid is wired and the rewrite is mechanical; otherwise, capture the new testid in the Dev Agent Record and update the test accordingly.

> **Origin:** The quality-review flow is the same UI surface in the drawer as the content-submission flow (just a different `drawerView` value). Story 11.D.4 confirmed the 2-tab layout but did not rename `approve-content-button` — defensive grep stays in scope.

---

### AC6 — Phase D and Phase E unchanged but verified green

**Given** Phase D (Slot Assignment & Publish Agenda — lines 672–775) and Phase E (Archival — lines 784–873) of the lifecycle test,

**When** I read the file after the rewrite,

**Then** Phase D and Phase E are byte-identical to the current versions on `main` except for:
1. Any `getByTestId` that has been renamed since the test was last touched (`9463cd9e`) — if the grep `grep -rn "{the-testid}" web-frontend/src/` returns zero hits, document the rename in the Dev Agent Record and update.
2. Any console-log line whose text references a phase-B step number that has shifted (cosmetic — phase D narration may say "Step 6 of 12 …" if the test internally renumbered).

**Given** the dev runs `cd web-frontend && AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-organizer.json) npx playwright test e2e/workflows/event-lifecycle-e2e.spec.ts --reporter=list` against a local `make dev-native-up` stack,

**When** the run completes,

**Then** all five phases (A through E) report `passed`, and the auto-cleanup at `afterAll` removes the test event without error.

> **Origin:** Phase D and Phase E do not interact with speaker workflow states; they cover slot assignment and event-level archival. The 8-state speaker workflow change should not regress either phase.

---

### AC7 — Bruno chain `events-api/37–41` retargeted to the 4-transition ADR-009 happy path

**Given** the four Bruno files in scope and the current sequence numbers (37, 38, 39, 40, 41),

**When** the dev re-runs `ls bruno-tests/events-api/3[7-9]*.bru bruno-tests/events-api/4[0-1]*.bru`,

**Then** the dev arrives at the following file set:

| File | Action | Final shape |
|---|---|---|
| `37-update-speaker-status.bru` | **Keep** | Unchanged — `PUT /status` `newStatus: CONTACTED` from IDENTIFIED is a legal transition per `ALLOWED_TRANSITIONS.IDENTIFIED → {CONTACTED, DECLINED}` (`SpeakerWorkflowService.java:73–74`). |
| `38-update-speaker-status-to-ready.bru` | **Delete + rename** | Replaced by **NEW** `38-promote-speaker-to-ready.bru` that POSTs to `/promote` with `{ email, firstName, lastName }`. Asserts `res.status: eq 200`, `res.body.status: eq READY`, `res.body.username: isDefined`. Uses `{{createdSpeakerPoolId}}` (the chain's primary speaker). Distinct from `45-promote-speaker-happy-path.bru` which uses the separate `{{promoteTestSpeakerId}}` setup. |
| **NEW** `38b-send-invitation-to-speaker.bru` | **Insert** | POSTs to `/events/{eventCode}/speakers/{speakerId}/invite` with the standard body `{ speakerIds: [{{createdSpeakerPoolId}}], options: { responseDeadline: "<today+30>" } }` OR PUTs to `/status` with `newStatus: INVITED` — whichever matches the convention used by other invitation tests in the suite (verify by reading `bruno-tests/events-api/54-send-invitation-slot-capacity-409.bru` for the canonical happy-path POST shape). Asserts `res.status: in [200, 201]`, `res.body.status: eq INVITED` (if `/status` is used). Note: Bruno sequence `38b` works in alphabetical file ordering — confirm by running `./scripts/ci/run-bruno-tests.sh` that files are executed in `ls`-order. If the runner re-numbers strictly by integer, name it `39-send-invitation-to-speaker.bru` and shift the rest by one. |
| `39-update-speaker-status-to-accepted.bru` | **Rewrite** | Body remains `{ newStatus: "ACCEPTED", reason: ... }` but the test header is renamed to "Update Speaker Status: INVITED → ACCEPTED (organizer respond-on-behalf)". The "should trigger overflow detection if needed - Story 5.4 AC13" test block is **DELETED** (lines 42–46) — `OVERFLOW` was removed by Story 11.B.1 / 11.B.3, and `SpeakerWorkflowState.OVERFLOW` is not in the enum. The `previousStatus: eq READY` assertion (line 27, 38) is changed to `previousStatus: eq INVITED` to match the new chain. |
| `40-invalid-status-transition.bru` | **Keep** | Negative test for `ACCEPTED → IDENTIFIED` backwards — still a correct negative case per ADR-009 §0.2 (no backwards transitions from ACCEPTED). After the rewritten 39, the speaker is in ACCEPTED, so the precondition holds. |
| `41-get-speaker-status-history.bru` | **Update** | Lines 47–54: change the assertion `history.length: >= 3` to `>= 4`; change the sequence expectation from `[CONTACTED, READY, ACCEPTED]` to `[CONTACTED, READY, INVITED, ACCEPTED]`. Update the inline comment "Should have at least 3 transitions: IDENTIFIED→CONTACTED, CONTACTED→READY, READY→ACCEPTED" to the 4-transition version "IDENTIFIED→CONTACTED, CONTACTED→READY (via /promote), READY→INVITED (via /invite), INVITED→ACCEPTED (organizer respond-on-behalf)". |

**Given** the dev runs the focused Bruno chain after rewriting,

**When** the local dev stack is up and the dev has `~/.batbern/staging-organizer.json`,

**Then** `bru run --env development bruno-tests/events-api/37-update-speaker-status.bru bruno-tests/events-api/38-promote-speaker-to-ready.bru bruno-tests/events-api/38b-send-invitation-to-speaker.bru bruno-tests/events-api/39-update-speaker-status-to-accepted.bru bruno-tests/events-api/40-invalid-status-transition.bru bruno-tests/events-api/41-get-speaker-status-history.bru` is GREEN (every test passes, zero 4xx responses).

**Given** the dev cannot run Bruno locally (missing staging tokens),

**Then** the dev documents the static-review sweep in the Dev Agent Record (same convention as Story 11.E.4 AC3) and relies on CI to catch any miss after merge.

> **Origin:** `38-update-speaker-status-to-ready.bru:19` PUTs `newStatus: READY` and asserts `res.status: eq 200` — but `SpeakerStatusController.java:190` now throws `ReadyRequiresPromoteException` (HTTP 422 via `GlobalExceptionHandler`). `39-update-speaker-status-to-accepted.bru:27` asserts `previousStatus: eq READY` — but READY → ACCEPTED is not in `ALLOWED_TRANSITIONS`; the only legal incoming transition to ACCEPTED is from INVITED. `41-get-speaker-status-history.bru:47–54` asserts the legacy 3-transition chain that 38/39 produced; the new chain has 4 transitions.

---

### AC8 — Full suite still green after the cleanup

**Given** the cleanup is complete,

**When** the dev runs the standard pre-merge gates:
- `./gradlew :services:event-management-service:test`
- `cd web-frontend && npm run type-check && npm run lint`
- `cd web-frontend && AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-organizer.json) npx playwright test e2e/workflows/event-lifecycle-e2e.spec.ts --reporter=list`
- `./scripts/ci/run-bruno-tests.sh development` (or document the static-review sweep if no local token)

**Then** every command exits 0, no unrelated failures surface, and the lifecycle test's 5 phases each report `passed`.

**Given** the diff is reviewed,

**When** the dev does a final sweep with `git diff --stat`,

**Then** the changes are confined to the expected paths:
- `web-frontend/e2e/workflows/event-lifecycle-e2e.spec.ts` — Phase B + Phase B.5 + Phase C rewritten; Phase A + Phase D + Phase E only touched if a testid rename forces it.
- `web-frontend/e2e/workflows/documentation/page-objects/SpeakerManagementPage.ts` — optional refactor if Phase B / B.5 / C share enough helper logic to factor out; otherwise unchanged. Document the decision.
- `bruno-tests/events-api/37-update-speaker-status.bru` — no diff.
- `bruno-tests/events-api/38-update-speaker-status-to-ready.bru` — **deleted**.
- `bruno-tests/events-api/38-promote-speaker-to-ready.bru` — **new**.
- `bruno-tests/events-api/38b-send-invitation-to-speaker.bru` (or `39-…` depending on the runner's ordering — verify) — **new**.
- `bruno-tests/events-api/39-update-speaker-status-to-accepted.bru` — rewritten body + assertions.
- `bruno-tests/events-api/40-invalid-status-transition.bru` — no diff.
- `bruno-tests/events-api/41-get-speaker-status-history.bru` — sequence assertion updated.

**Given** the commit message,

**When** the commit lands,

**Then** the message follows Conventional Commits (`test(epic-11): adapt event-lifecycle E2E + Bruno organizer-happy-path chain to ADR-009 8-state workflow`), and the body lists the seven scope items with one-line summaries.

---

## Tasks / Subtasks

> **Order rationale:** Items 1, 2, 3 are independent — Playwright and Bruno work do not depend on each other. Item 4 (full suite green) runs last. Items 1 and 2 within Playwright are sequential (helper extraction first, then the spec rewrite consumes the helpers). Item 3 within Bruno is per-file mechanical.

- [ ] **Task 1 — Playwright: extend `SpeakerManagementPage` helpers if needed (AC1, AC2, AC3, AC4, AC5)**
  - [ ] Read `web-frontend/e2e/workflows/documentation/page-objects/SpeakerManagementPage.ts` fully.
  - [ ] Decide whether to add new helpers (`promoteSpeakerOnDrop(speaker)`, `sendInvitationOnDrop(speaker)`, `acceptOnBehalfOnDrop(speaker)`, `openContentSubmissionViaPrimaryAction(speakerId)`, `openQualityReviewViaPrimaryAction(speakerId)`) or keep all new logic inline in the spec. Either choice is fine — match the style of the surrounding page object.
  - [ ] If helpers are added, they MUST be pure DOM + Playwright API only — no business-logic encoding (the test stays declarative).
  - [ ] Run `cd web-frontend && npm run type-check` after any helper change.

- [ ] **Task 2 — Playwright: rewrite Phase B + Phase B.5 + Phase C in `event-lifecycle-e2e.spec.ts` (AC1, AC2, AC3, AC4, AC5, AC6)**
  - [ ] Phase B Step 3 (current lines 376–436) — rewrite per AC1.
  - [ ] Phase B Step 4 (current lines 441–503) — **delete** and replace with **new** Step 4 (READY → INVITED, AC2) and **new** Step 5 (INVITED → ACCEPTED, AC3). Renumber the console-log strings accordingly.
  - [ ] Phase B.5 (current lines 519–608) — rewrite per AC4. Verify the `publish-topic-button` step is still needed (read `ContentSubmissionService`'s preconditions).
  - [ ] Phase C (current lines 615–663) — rewrite per AC5.
  - [ ] Phase A + Phase D + Phase E — verify all testids still exist via `grep`. Update only if a testid was renamed.
  - [ ] Capture `max_slots` resolution decision in the Dev Agent Record per AC2.

- [ ] **Task 3 — Bruno: retarget `events-api/37–41` to ADR-009 4-transition chain (AC7)**
  - [ ] Delete `bruno-tests/events-api/38-update-speaker-status-to-ready.bru`.
  - [ ] Create `bruno-tests/events-api/38-promote-speaker-to-ready.bru` modelled on `45-promote-speaker-happy-path.bru` but using `{{createdSpeakerPoolId}}` and a fresh email like `promote-chain-{{$randomInt}}@e2e.batbern.invalid`.
  - [ ] Create `bruno-tests/events-api/38b-send-invitation-to-speaker.bru` modelled on the canonical invitation-send shape (read `54-send-invitation-slot-capacity-409.bru` for the working positive shape — strip out the 409 assertion; the new test asserts 200/201). Confirm Bruno's file-ordering convention (`38b` vs `39` numeric shift); document the choice.
  - [ ] Rewrite `bruno-tests/events-api/39-update-speaker-status-to-accepted.bru` per AC7: change `previousStatus` expectations from READY to INVITED; delete the "should trigger overflow detection" block (lines 42–46); rename the test header.
  - [ ] Update `bruno-tests/events-api/41-get-speaker-status-history.bru` per AC7: change `>= 3` to `>= 4`; change the sequence array assertion; update the inline comment.
  - [ ] Run `./scripts/ci/run-bruno-tests.sh development` against the local stack OR document the static-review sweep if no token is available.

- [ ] **Task 4 — Full suite green (AC8)**
  - [ ] `./gradlew :services:event-management-service:test` → exits 0.
  - [ ] `cd web-frontend && npm run type-check && npm run lint` → exits 0.
  - [ ] `cd web-frontend && AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-organizer.json) npx playwright test e2e/workflows/event-lifecycle-e2e.spec.ts --reporter=list` → 5/5 phases pass.
  - [ ] If Bruno was runnable: focused Bruno chain GREEN.
  - [ ] `git diff --stat` — confirm scope matches AC8's path list; nothing else touched.
  - [ ] Document Dev Agent Record completion notes; flag the screencast narration drift for tech writer (see "Out of scope" note 5).
  - [ ] Commit + push per AC8 message convention.

---

## Dev Notes

### Architecture compliance

- **ADR-009 §0.2 Legal transitions** is the contract. The 8 states are IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED (+ DECLINED reachable from every non-terminal state). CONTACTED → READY requires the dedicated `/promote` endpoint (provisions a Cognito user + grants SPEAKER role). READY → INVITED is slot-capacity gated. INVITED → ACCEPTED is the only direct PUT /status transition that lands an organizer-respond-on-behalf.
- **`ALLOWED_TRANSITIONS` is the single source of truth** for legality:
  - Backend: `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java:71–82`
  - Frontend: `web-frontend/src/components/organizer/SpeakerStatus/speakerTransitions.ts:37–48`
  - Source: `docs/architecture/ADR-009-unified-speaker-workflow.md` §0.2 (added by 11.E.4 AC5).
- **`PUT /api/v1/events/{code}/speakers/{speakerId}/status` rejects `newStatus: READY`** at `SpeakerStatusController.java:190` (`ReadyRequiresPromoteException`). The promote path is the only way to reach READY.
- **`POST /api/v1/events/{code}/speakers/{speakerId}/promote`** body is `{ email, firstName, lastName }` — all three `@NotBlank` per Story 11.E.4 AC4. Returns 200 with the updated `SpeakerPoolResponse` (status=READY, username populated). 409 if the speaker is in a state other than CONTACTED (idempotent on READY).
- **The `legal-direct` carve-out** at `speakerTransitions.ts:118` ("INVITED → ACCEPTED is the only legal-direct today") is what makes the organizer respond-on-behalf flow a single drag without a modal. The audit trail records the organizer's username as `changedByUsername` (the speaker's username is `username` on the row, unchanged).

### Files being modified (READ-FIRST)

Per CLAUDE.md "READ FILES BEING MODIFIED — skipping this is the primary cause of implementation failures":

- `web-frontend/e2e/workflows/event-lifecycle-e2e.spec.ts` — the 874-line training-flow spec. Read in full before editing.
- `web-frontend/e2e/workflows/documentation/page-objects/SpeakerManagementPage.ts` — page object; verify the changeStatusButton testid still maps to a valid element after Phase D.
- `bruno-tests/events-api/37–41-*.bru` — five Bruno files; read all five.
- `bruno-tests/events-api/45-promote-speaker-happy-path.bru` — reference shape for the new 38-promote-speaker-to-ready.bru.
- `bruno-tests/events-api/54-send-invitation-slot-capacity-409.bru` — reference shape for the new 38b/39-send-invitation-to-speaker.bru (strip the 409 assertion; the new file asserts the 200 happy path).

### Files to consult but NOT modify

- `web-frontend/src/components/organizer/SpeakerStatus/speakerTransitions.ts` — `ALLOWED_TRANSITIONS` + `classifyDrop`.
- `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` — drag dispatcher (`handleDragEnd` at line 560), the `handleSendInvitation` default at line 1101 (no modal — fires the mutation directly).
- `web-frontend/src/components/organizer/SpeakerStatus/getPrimaryAction.ts` — primary-action testid mapping; ACCEPTED → `enter-content`, CONTENT_SUBMITTED → `review-content`.
- `web-frontend/src/components/SpeakerBrainstormingPanel/PromoteSpeakerDialog.tsx` — promote modal test-ids (lines 219, 235, 251, 268).
- `web-frontend/src/components/organizer/SpeakerDrawer/SpeakerDetailDrawer.tsx` — drawer view router; `drawerView === 'content-submission'` renders `ContentSubmissionSubView`.
- `web-frontend/src/components/organizer/SpeakerDrawer/ContentSubmissionSubView.tsx` — preserves `presentation-title-field` / `presentation-abstract-field` / `submit-speaker-content-button` testids (lines 414, 431, 461).
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` — `ALLOWED_TRANSITIONS` (lines 71–82), `runReadyHook` (provisioning at CONTACTED → READY), the slot-capacity gate at INVITED.
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java` — `ReadyRequiresPromoteException` at line 190.

### Reference implementations to mirror

- **`speaker-kanban-guided-drag.spec.ts`** — same raw-pointer `dragCardToLane(page, speakerId, 'status-lane-...')` helper pattern used across AC1/AC2/AC3. Mirror its structure.
- **`speaker-onbehalf-vs-self-byte-identity.spec.ts:138–164`** — the canonical organizer-driven IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED sequence (the dev wrote this for Story 11.D.4 AC10 case 51). Mirror its endpoint choices.
- **`speaker-card-primary-action.spec.ts`** — for the primary-action-button click pattern used by AC4 / AC5.

### Selector strategy

- Prefer `getByTestId('status-lane-{lowercased}')` over `getByTestId('status-lane-{UPPERCASE}')` — the lane test-id is lowercase per `SpeakerStatusLanes.tsx:902`. The current lifecycle test uses UPPERCASE (`status-lane-READY`, `status-lane-ACCEPTED`) — these WILL match because the testid is in the DOM as lowercase only; Playwright's `getByTestId` is case-sensitive. **Verify by running the existing test first** to see if Phase A passes. If the current `status-lane-READY` matches, the testid generation must be case-preserving in some code path; if not, the rewrite MUST use lowercase.
- Prefer testid-prefix locators (`page.locator('[data-testid="status-lane-ready"] [data-testid^="speaker-card-"]')`) over `getByRole('button', { name: ... })` for counting cards in a lane — the button name will change after a state transition (a CONTACTED card and a READY card have different aria-labels).

### i18n robustness

Per `_bmad-output/project-context.md` testing rules: assert against EN values OR namespace-stripped keys, never against a specific non-EN translation. AC2's snackbar wait uses `/invitation sent|einladung gesendet/i` — a permissive case-insensitive regex that matches both EN ("Invitation sent") and DE ("Einladung gesendet"). If a `gsw-BE` or non-DE/EN locale is the active one, the test may fail to find the snackbar text; document this in the Dev Agent Record and consider adding the `gsw-BE` translation in a follow-up if it blocks CI in a non-default locale.

### Testing strategy

- Per CLAUDE.md "instead of running the test suites several times and grep the output, dump the output to a temp file and grep that file. this saves time" — run the Playwright spec with `--reporter=list 2>&1 | tee /tmp/lifecycle-e2e.log` and grep that file for `failed`, `error`, `Error:`.
- Per CLAUDE.md "whenever you run make, gradlew or git push or git commit, output result via tee to a temp file" — same pattern for the gradle command in AC8.

### Previous story learnings

- Story 11.D.4 added the per-card primary-action button and the per-lane testid lowercasing. The lifecycle test was written BEFORE 11.D.4, so its selectors and click patterns predate the new UX.
- Story 11.E.4 deleted 5 TENTATIVE Bruno tests as a documented chain (12, 13, 13b, 14, 15) — the same "if you delete the head of a chain, document the cascade" discipline applies to deleting old 38 here: the chain (37 → 38 → 39 → 41) depends on each prior file completing. The fix is to insert replacement files (38-promote, 38b-invite) so the chain stays connected.
- Story 11.E.3 added the Cognito-based speaker-portal tests (30–34) but did not retarget the legacy magic-link chain — that's 11.F.1's job. This story stays on the organizer side to avoid colliding with 11.F.1's scope.
- The `legal-direct` carve-out for INVITED → ACCEPTED was a deliberate design decision in 11.D.4 (`speakerTransitions.ts:118`) — it lets organizers respond-on-behalf without a modal. This carve-out is what makes AC3 a clean drag-and-done (no fill-anything modal step).

### Project context reference

- CLAUDE.md §Testing — TDD (Red-Green-Refactor), Testcontainers PostgreSQL for backend, Vitest+RTL for frontend, Playwright for E2E.
- CLAUDE.md §Localization — frontend UI keys in 10 locales (this story adds no new keys); the snackbar text consumed by AC2 already exists in all 10 locales from Story 11.D.2/11.D.4.
- ADR-009 §0.1 §0.2 §0.5 — the target state model that this story aligns the tests to.
- `docs/plans/speaker-workflow-refactor.md` §8 — the kanban UX that this story exercises end-to-end.

---

## Dev Agent Record

### Agent Model Used

_(populated by dev agent at implementation time)_

### Debug Log References

_(populated by dev agent)_

### Completion Notes List

_(populated by dev agent)_

### File List

_(populated by dev agent)_

---

## Open Questions

Each item below is something the PM should resolve before the dev agent picks up the story, or in the first 30 minutes of dev time. The phrasing is intentionally PM-readable (no jargon, no "Alternative not chosen" subsections).

### Q1 — How many speakers should Phase B carry through INVITED → ACCEPTED, given the event's `max_slots`?

The Phase-A event creation uses `venueCapacity: 200` (the venue's physical capacity for attendees) but the speaker `max_slots` is separate — it controls how many speakers can be invited per event and is derived from the event type. EVENING events typically have ~4 speaker slots; AFTERNOON / FULL_DAY have more. The current test moves four speakers (Nissim, Balti, Andreas, Daniel) through the workflow. If `max_slots = 4`, the test runs exactly at the slot-capacity boundary — the 4th invitation fires when `accepted + invited = 3 < 4`, so the gate is not tripped, but the test exercises an edge case. If `max_slots < 4`, the 4th drag will be rejected with a slot-capacity toast and the test fails. If `max_slots > 4`, the test has slack and exercises a non-boundary case.

The cleanest fix is for the dev to read `EventTypeConfig` (or whatever sets `max_slots` for EVENING events) at the start of implementation and choose a speaker count one less than `max_slots`. That gives the test a comfortable safety margin and avoids edge-case fragility for future contributors. Should I instruct the dev to default to "speakers count = max_slots - 1" with the four-speaker option as a fallback if max_slots ≥ 5, or do you want to lock the test to a specific speaker count regardless?

### Q2 — Should the rewritten Phase B.5 keep the `publish-topic-button` click step?

The current lifecycle test, at Phase B.5 step 2 (lines 535–537), clicks `getByTestId('publish-topic-button')` on the publishing tab before any content is submitted. This is the user-guide narration beat that says "now publish the topic so attendees know what the event is about." But it is not technically required for content submission — speakers can submit content for an event whose topic is not yet published. Keeping the click step preserves the training-video narration; dropping it shortens the test by ~5 seconds.

I plan to instruct the dev to keep the click step with a code comment naming the reason ("kept for training-video narration continuity, not a functional precondition") — does this match your intent for the training video?

### Q3 — Does the Bruno `38b-send-invitation-to-speaker.bru` runner-ordering work with `38b` as a suffix, or should I shift `39, 40, 41` by one?

Bruno's CLI sorts files alphabetically within a collection. `38-promote-speaker-to-ready.bru` and `38b-send-invitation-to-speaker.bru` both start with `38`, so `38b` sorts after `38` in alphabetical order — the chain runs in the right sequence. But some Bruno-runner versions normalize the `seq` metadata field strictly by integer; in that case `38b` would be parsed as 38 (parseInt drops the trailing letter) and the runner would race the two `38` files in non-deterministic order. The safer alternative is to rename the new invitation file to `39-send-invitation-to-speaker.bru`, then shift the current `39` → `40`, current `40` → `41`, current `41` → `42`. That keeps the strict-integer-ordering assumption intact but touches three more files than the `38b` approach.

I'm leaning toward the `38b` suffix because Bruno's `seq` field is metadata-only in the current versions (the file system order is what matters), and the rename cascade adds noise to the diff. But this is a guess — the dev should verify at implementation time by running `bru run --env development bruno-tests/events-api --dry-run` (or whatever the right Bruno introspection command is) and confirm file order. Want me to make this verification a hard task subitem, or trust the dev to handle it inline?

### Q4 — Should the rewritten lifecycle test include a screenshot capture beat at the end of each new sub-phase for the training video?

The current test has no explicit `page.screenshot()` calls in Phase B — it relies on the Playwright trace/video capture configured in `playwright.config.ts`. The training video is sliced from the Playwright video output. Adding explicit screenshots at the new sub-phase boundaries (end of Phase B Step 3 = "all in READY", end of Step 4 = "all in INVITED", end of Step 5 = "all in ACCEPTED") would give the screencast pipeline three clean stills it can drop into the user guide and the video transitions. Skipping them keeps the test lean and assumes the video pipeline can find the right frames itself.

I plan to leave screenshots out of this story (matching the current test's style) and flag the screencast-narration drift for the tech writer (Paige) as a separate documentation pass — but if you want the stills as part of this story, say so and I'll add an AC for it.

### Q5 — Out of scope confirmation: `complete-event-workflow.spec.ts` companion test stays untouched even if it has the same broken Phase-B drag pattern?

There's a sibling Playwright spec at `web-frontend/e2e/workflows/documentation/complete-event-workflow.spec.ts` that was touched by Story 11.D.3's git pass. A quick grep suggests it covers a similar end-to-end workflow but in a different framing (maybe with a different test-data set or for a different documentation pipeline). If it has the same Phase-B-drag pattern, it's also broken — but fixing both files in one story doubles the scope and risks one rewrite drifting from the other.

I plan to instruct the dev to spot-check `complete-event-workflow.spec.ts` and document any drag-pattern findings in the Dev Agent Record as a flag for a follow-up story, but NOT fix it here. Acceptable?
