---
title: 'API conventions II b (breaking): one participants create, newsletter self-service and subscribe on their collections'
type: 'refactor'
ticket: '10c'
created: '2026-11-11'
status: 'built'
baseline_revision: 'f5052221721ec37cf91cdf13dd25d56064f953cc'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/coding-standards.md'
  - '{project-root}/docs/architecture/ADR-013-rest-api-crud-conventions.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Participants are created through three operations (`POST /events/{c}/participants`, `POST /events/batch_registrations`, `POST /events/{c}/enroll-stakeholders`), the last two in `EventController`. Newsletter self-service has two sets (`/newsletter/my-subscription` GET/PATCH for the login address, `/newsletter/my-subscriptions` GET/PUT per address), and subscribing is a verb path (`POST /newsletter/subscribe`, Spectral G10).

**Approach:** Breaking, no aliases. One `POST /events/{eventCode}/participants` in `ParticipantsController` with exactly one of `username` (+`force`, `notify`), `people[]` (historical import, per event) or `stakeholders: true`. One `/newsletter/my-subscriptions` (GET, PATCH partial per address). `POST /newsletter/subscribers` replaces `subscribe`, public in gateway and EMS together. Spec, types, backend, frontend, Bruno, Playwright in one change (C4).

## Boundaries & Constraints

**Always:**
- Owner blanket go 2026-11-10. Commit locally on `feature/17-overnight-2026-11-10`, never open a PR, verify on local dev only. A defect found on the way is fixed here.
- No real outbound mail. Mail is asserted on the `SesRecorder`, each absence with a positive control. Bruno that sends mail or writes registrations/subscriptions runs only in dev-only collections; Playwright for them is guarded to `TEST_ENV=development`, send specs tagged `@sends-real-email`. Never touch `mailcenter.notify-instead-of-forward`.
- Contract first (`docs/api/`, then `npm run generate:api-types`), a spec example per new/changed operation (`@example.com` only), `@PreAuthorize` on every write, test fidelity rules; touched tests come off own-code mocks.
- Gateway and EMS change together (Turnstile, rate limit, permitAll in both chains, `scripts/ci/access-control-public.txt`).
- One heavy job at a time on rack. Gradle `--max-workers=4`, Vitest `--maxWorkers=4`, logs `tee`'d under `~/.cache/batbern-test-logs/` with `set -o pipefail`, long commands wrapped in `timeout`. Restart dev services only with `timeout 600 make dev-native-restart-service SERVICE=<name>`.

**Never:**
- No deprecation aliases or kept paths. No double opt-in or other new mail (see Open Questions).
- Do not touch `POST /newsletter/unsubscribe`, `unsubscribe/verify`, the organizer `/newsletter/subscribers/{id}/*` operations (10b), public self-registration, or the unsubscribe token flow.
- Never edit an applied Flyway migration.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Single add, notify | organizer `{username}` | 201, confirmed, exactly one organizer-added mail in `SesRecorder` | 409 duplicate / `capacity_exceeded` unless `force` |
| Single add, silent (positive control) | `{username, notify:false}` next to the notify case | 201, `SesRecorder` empty; the notify case in the same class records one | — |
| Historical import | `{people:[{email,firstName,lastName,companyId?,status}]}` ≤100 | 200, per-person result; users get-or-created (no Cognito), existing rows skipped, no mail | one failing person does not roll back the others |
| Stakeholders | `{stakeholders:true}` | 200 `{enrolled, skipped}`, idempotent, no mail | — |
| Mode error | none, two modes, `stakeholders:false`, empty or >100 `people` | 400 | — |
| Participants auth | anonymous / attendee | 401 / 403 | — |
| Newsletter per address | user `PATCH /my-subscriptions {email, subscribed}` | 200, only that address changes; absent fields unchanged | address not mine: 403 |
| Anonymous subscribe | `POST /newsletter/subscribers` + `X-Turnstile-Token` | 200 (as today), no mail | 409 active, 400 invalid/reserved; 6th call in 5 min: 429; Turnstile on in prod |
| Anonymous only where it was | anonymous `GET /newsletter/subscribers`, `POST /subscribers/{id}/unsubscribe`, `GET/PATCH /my-subscriptions` | 401 | — |
| Old paths | `batch_registrations`, `enroll-stakeholders`, `my-subscription`, `newsletter/subscribe` | 404/405 | — |

**Decisions (2026-11-11, decided by agent under owner's blanket go):**
- Subscribe keeps today's behaviour: no confirmation mail (the code has none). A test pins that no mail is sent; the note in `scripts/ci/access-control-public.txt` is corrected. Double opt-in is the owner's decision, filed as #1288.

- Review pass 1 (intent gaps settled here): a subscribe **without** a Turnstile token still passes (the gateway filter fails open on a missing token by design, Story 10.31 AC2, for blocked widgets); only an invalid token is refused. This keeps today's behaviour as decided above; the after-deploy check expects 403 for an invalid token, not for a missing one. Tightening it belongs with #1288.
- The old `POST /newsletter/subscribe` answers 401 to an anonymous probe (its `permitAll` is gone in both chains); the after-deploy check probes the old paths with an organizer token and expects 404/405.

</frozen-after-approval>

## Code Map

- **Specs:** `docs/api/event-registrations-api.openapi.yml` (participants L305, enroll L825, `batch_registrations` L1124 with a real-looking address in its example, removed); `docs/api/event-newsletter-api.openapi.yml` (subscribe L317, my-subscription L395, my-subscriptions L442, subscribers L501).
- **EMS:** `EventController` L1717 `enrollStakeholders`, L2170 `createBatchRegistrations` (removed; no new endpoint there, module map). `ParticipantsController.addParticipant` L96 becomes the one create. `RegistrationService` `createBatchRegistrations` L366 (per participant → per event), `enrollStakeholders` L831 (kept for `AutoEnrollmentListener`), `addParticipant` L639 (mail only here, `notify`). `NewsletterController` L60-165, `NewsletterSubscriberService` `getMySubscription`/`patchMySubscription` L179-245 (removed), `setAddressSubscription` L268. `config/SecurityConfig` L133.
- **Gateway:** `config/SecurityConfig` L294, `security/RateLimiter` L40, `resources/application.yml` L73 (Turnstile list), comments in `TurnstileProperties`, `TurnstileVerificationFilter`; tests `RateLimitingTest`, `TurnstileVerificationFilterTest`, `InMemoryRateLimitStorageTest`.
- **EMS tests:** `ParticipantsControllerIntegrationTest` (mocks `UserApiClient`, `RegistrationEmailService`, `WaitlistPromotionEmailService`), `BatchRegistrationContractIntegrationTest`, `RegistrationServiceBatchTest`, `EnrollStakeholdersTest`, `NewsletterControllerIntegrationTest`, `securitychain/EmsProductionSecurityFilterChainTest` L103-155. Use `CumsContractHarness` for CUMS.
- **Frontend:** `services/api/eventApi.ts` (`batchRegisterParticipant`), `services/api/eventRegistrationService.ts` L184 (`enrollStakeholders`, `addParticipant`), `hooks/useParticipantBatchImport` (regroup per event, chunks of 100), `types/participantImport.types.ts`, `utils/participantImportUtils.ts`, `EventParticipantsTab.tsx`, `AddParticipantDialog.tsx`; `services/newsletterService.ts`, `hooks/useNewsletter/useNewsletter.ts`, `NewsletterSubscribeWidget.tsx`, `UserSettingsTab.tsx` L438 (single toggle → `ProfileNewsletterAddresses`), `ProfileNewsletterAddresses.tsx`. MSW tests key on operationIds.
- **Lint/CI:** `web-frontend/scripts/api-lint-{baseline,owners}.txt` (G06 2 lines, G10 1), `scripts/ci/access-control-public.txt` L49, `scripts/ci/run-bruno-tests.sh` L132 (`dev_only_collections`).
- **Docs:** `docs/api/batch-registration-usage-guide.md`, `06-backend-architecture.md` L137, `06b-user-lifecycle-sync.md` L998/L1087, ADR-013 note, doc-drift-mapped docs.
- **Not changed (checked):** Watch app (its type closure uses none of these schemas); newsletter mails link to `/unsubscribe?token=` (frontend) and `POST /newsletter/unsubscribe`, both kept; no Bruno or Playwright calls the old paths today; infrastructure has no path list (only `TURNSTILE_ENABLED`).

## Tasks & Acceptance

**Execution:**
- [ ] Record start sizes (table below).
- [ ] Participants: spec first (one request schema, mode rule in description, mode-specific response fields, 201 single / 200 bulk). RED ITs per matrix row on the production chain with `SesRecorder` and `CumsContractHarness`; then `ParticipantsController`, `RegistrationService` (per-person isolation), remove the two `EventController` methods and their DTOs. Commit.
- [ ] Frontend participants: services, import hook regrouped per event (candidate status aggregated over its events), `EventParticipantsTab`, tests on MSW (off `fe-mock-*` where touched). Commit.
- [ ] Newsletter: spec, RED ITs (per-address PATCH, partial, 403, anonymous 401, subscribe 200/409/400, no mail with positive control), controller and service; gateway Turnstile, rate limit, permitAll for `POST /api/v1/newsletter/subscribers` only (exact path); EMS chain; access-control list (fix its false "double opt-in" note); gateway tests. Commit.
- [ ] Frontend newsletter: service, hooks, widget, `UserSettingsTab`, tests. Commit.
- [ ] Bruno dev-only collections `participants-api` and `newsletter-api` (single silent + notify, people, stakeholders, mode 400; GET/PATCH per address; anonymous subscribe with a synthetic non-reserved address unique per run, e.g. `e2e-newsletter+{ts}@batbern.ch`), registered in `dev_only_collections`; prose in `docs {}`.
- [ ] Playwright (local-dev guard): `organizer/event-participants.spec.ts` (add, enrol, notify case `@sends-real-email`), `organizer/participant-import.spec.ts` (synthetic CSV fixture, two events, one person in both), `user-account/newsletter-self-service.spec.ts`, `public/newsletter-subscribe.spec.ts`.
- [ ] Lint `--update`, remove G06/G10 owners, regenerate types, ratchet `--update` (may only shrink), docs. Commit.

**Acceptance Criteria:**
- Given the specs, when `npm run lint:api` runs, then the baseline has 98 entries (101 − 3), G06 and G10 are gone, nothing UNOWNED or STALE; `make check-frontend-api-paths` passes.
- Given EMS ITs, when each participants mode runs, then only single add with notify records a mail, and the silent cases are proven against that positive control.
- Given local dev, when an organizer adds a participant, enrols stakeholders and imports a CSV, a user toggles the newsletter per address and an anonymous visitor subscribes, then each change persists after reload and the dev mail folder holds only the organizer-added mail.
- Given Bruno (development) and the four Playwright specs on local dev, then they pass with no `Skipping invalid file`.
- After deploy (owner): the four old paths answer 404/405 in production, anonymous `POST /newsletter/subscribers` without a Turnstile token is refused, and the access-control DAST passes.

## Implementation Notes

- **Participants response:** one schema `AddParticipantsResponse` for 201 and 200 with
  `@JsonInclude(NON_NULL)` (`x-class-extra-annotation`, precedent in analytics/sessions/core), so
  each mode carries only its fields and the response conformance gate needs no nullable noise.
  `operationId` is `addParticipants` (was `addParticipant`).
- **Import isolation:** `RegistrationService.importParticipants` has no outer transaction; each
  person is `saveAndFlush`ed on its own and a `RuntimeException` (CUMS 409, DB) becomes `FAILED`.
  The realistic failure is CUMS; a DB error after OSIV is not re-tested.
- **Mode rule details:** `force`/`notify` outside the single add are a 400 too; `username` has
  `minLength: 1`.
- **Newsletter:** subscribe is `createNewsletterSubscriber`, the PATCH `patchMyNewsletterSubscriptions`.
  Stale rows (subscribed under the username, address no longer the user's) are listed only while
  subscribed and accept only `subscribed: false` (no language change), so they disappear once
  unsubscribed. `NewsletterAddressSubscription` gained `language` (required, nullable).
- **Turnstile:** the matrix and AC say a missing token is refused; the gateway filter fails open
  on a missing token by design (Story 10.31 AC2), and "Subscribe keeps today's behaviour". Not
  changed; the spec, the filter comment and `access-control-public.txt` now say what happens. The
  after-deploy AC "without a Turnstile token is refused" therefore does not hold; an invalid token
  is refused (403). Changing to fail-closed is the owner's call (with #1288).
- **Frontend:** `ParticipantBatchImportModal.test.tsx` still mocks the hook for its display
  states (ratchet unchanged for it); the new `ParticipantBatchImportModal.import.test.tsx` runs
  the real modal, hook and service on MSW. `UserSettingsTab` got the per-address switches.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens), 2026-11-11: 0 high, 5 medium, 3 low, 0 false (2 intent gaps settled as Decisions).

| # | Finding | Verdict | Route | Evidence / action |
|---|---------|---------|-------|-------------------|
| 1 | One invalid CSV person (empty name, address failing the pattern) fails the whole chunk with 400 | medium | patch | Per-person constraints sit in the request schema; the hook marks the chunk failed. Validate per person in the service, report FAILED with a reason, keep the others. |
| 2 | `importParticipant` writes the registration's search fields from the CSV, not from the CUMS user | medium | patch | Every other creation path in `RegistrationService` uses the returned user. |
| 3 | 100 sequential CUMS calls per request may exceed the 30 s frontend timeout | medium | patch | Not measured; smaller chunks remove the risk without a new mechanism. |
| 4 | `ParticipantImportResult.reason` returns raw exception messages (SQL, constraint text) | medium | patch | Curated reason for known failures, generic text otherwise, details in the log. |
| 5 | Missing Turnstile token is not refused (AC) | medium | intent_gap → settled | Decision in the frozen block; comment on #1288. |
| 6 | Anonymous probe of the old subscribe path gets 401, not 404/405 | low | intent_gap → settled | Decision in the frozen block. |
| 7 | `.spectral.yaml` still allows the `enroll-stakeholders` verb | low | patch | Direct deletion. |
| 8 | i18n key `settings.newsletter.subscribe` unused after the rewrite | low | patch | Remove from all locales. |

## Design Notes

**Architecture note.**
- **Modules:** EMS registrations (L3) for participants; newsletter (L6 edge); gateway security.
- **New dependencies:** none.
- **Endpoints:** `POST /events/{c}/participants` absorbs `batch_registrations` and `enroll-stakeholders`; `PATCH /newsletter/my-subscriptions` replaces `PUT` and `/my-subscription`; `POST /newsletter/subscribers` replaces `subscribe`.
- **Reused:** `setAddressSubscription`, `enrollStakeholders`, `getOrCreateUser` (cognitoSync false), `SesRecorder`, `CumsContractHarness`.
- **Deviations:** none.

**Decisions (agent, blanket go).** Mode check in the controller, not `oneOf` (the Java generator handles it poorly). Stale subscriber rows linked to the username but no longer one of the user's addresses appear in GET `my-subscriptions` and may be unsubscribed by PATCH, so the old "unsubscribe all" stays reachable.

**Epic 17 rule (C15).** Fixed: two create operations beside the collection, two endpoints in `EventController`, a verb path, a PUT without a replacement contract, a public-list note claiming a double opt-in that does not exist.

| Store | Start (`f5052221`) | Expected end |
|---|---|---|
| Spectral baseline | 101 entries | 98 |
| Test ratchet | 617 entries (touched files carry 16) | shrinks |
| ArchUnit EMS / CUMS | 6 / 4 lines | unchanged |
| s2s guard | 5 | 5 |
| API layer, type copies, i18n | 0 | 0 |

## Verification

**Commands:**
- `set -o pipefail; timeout 3600 ./gradlew :services:event-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/410c-ems.log`, then `:api-gateway:test`, one at a time -- expected: BUILD SUCCESSFUL, stores unchanged
- `cd web-frontend && npm run lint:api && npm run check:api-types && npm run type-check && timeout 1800 npx vitest run --maxWorkers=4` -- expected: green, 98 entries
- `make check-test-ratchet && make check-frontend-api-paths && make check-s2s-client-urls` -- expected: exit 0
- `./scripts/ci/run-bruno-tests.sh development` -- expected: all collections incl. the two new dev-only ones PASS
- Playwright against local dev only (`TEST_ENV=development`): the four new specs plus `organizer/event-registrations-management`, `organizer/profile-page`, `registration-flow` -- expected: pass

**Manual checks:**
- After restarting `event-management` and `api-gateway`: walk the AC on local dev; old paths 404/405 through the gateway; `/dev/mails` holds only the organizer-added mail.


Final verification 2026-11-11 after the review patches (ffcfa08c): shared-kernel, CUMS, EMS, PCS and gateway suites green; lint:api (98), lint:api-internal, check:api-types, type-check, check:s2s-client-urls, Vitest 459 files / 5906 passed, test ratchet and API paths green. Local dev before the patches: Bruno participants-api 16/16, newsletter-api 12/12, Playwright 20 passed; the patched import path is covered by its IT. After deploy: old paths 404/405 with an organizer token, invalid Turnstile token 403, access-control DAST; owner: #1288.
