# API Consolidation Plan

**Status:** Proposed (branch `api-consolidation`)
**Date:** 2026-06-25
**Governing standard:** [ADR-013 — REST API CRUD Conventions](../architecture/ADR-013-rest-api-crud-conventions.md)
**Scope:** CUMS (companies, users), EMS (events, topics), Partner (partners + 4 sub-specs),
plus the shared-kernel contract that underpins all 13 specs.

This plan is the actionable counterpart to ADR-013. Each item is tagged
`[REMOVE] / [MERGE] / [RENAME] / [KEEP-BUT-FIX]` and ordered by risk so the low-risk,
high-clarity wins land first. **Staging IS production** — every contract-removing step must
verify no live caller first.

---

## Versioning & deprecation approach

All specs are under `/api/v1`. We are **not** cutting `/api/v2`. The cleanups fall in three
buckets:

1. **Dead-spec removal (no behaviour change).** Endpoints that the server never implemented
   or no longer serves (events-api UUID `/topics/{id}`, partners-api voting subsystem).
   These are spec-only deletions — zero runtime risk once verified the server doesn't serve
   them. **Do these first.**
2. **Documentation-truth fixes (no behaviour change).** Inline `ErrorResponse` /
   `PaginationMetadata` → shared stub; doc corrections; spec for undocumented live routes.
   No client impact.
3. **Genuine contract changes (client impact).** Removing a redundant *live* endpoint
   (second upload initiator), dropping a PUT twin, collapsing a list query vocabulary.
   These get: (a) a usage check against access logs / frontend service layer, (b) the
   redundant endpoint marked `deprecated: true` in the spec for one release, (c) removal in
   the following release once callers are migrated.

Rule: prefer **deprecate-then-remove** for anything a client could call; **delete outright**
only for verified dead spec.

---

## Phase 0 — Shared-kernel contract (do first; unblocks everything)

The shared contract is the foundation; fixing it makes every per-service fix a stub
reference instead of a bespoke schema.

- `[REMOVE]` `shared-kernel/.../utils/ErrorResponse.java` (the `traceId` duplicate). Migrate
  callers (`shared/utils/ErrorHandlingUtils.java`) to `dto.ErrorResponse`.
- `[KEEP-BUT-FIX]` Make `ch.batbern.shared.dto.ErrorResponse` the sole wire shape:
  `timestamp, path, status, error, errorCode, message, correlationId, severity, details`.
- `[KEEP-BUT-FIX]` Reconcile the `errorCode` **field** with the `ErrorCode` **enum**
  (`ERR_*`) — currently unrelated. Either type the field to the enum or document them as
  separate namespaces.
- `[KEEP-BUT-FIX]` Promote `ValidationError {field, message}` into shared-kernel (today only
  `auth-endpoints` defines it).
- `[MERGE]` Create `docs/api/_shared.openapi.yml` holding the single `ErrorResponse`,
  `PaginationMetadata`, `PaginatedResponse`, `ValidationError` — `$ref`/stub-referenced by
  every spec so the wire schema has one literal source, not 13 copies.
- `[KEEP-BUT-FIX]` Rewrite `docs/architecture/04-api-core.md` error + pagination sections to
  match reality (flat error, page-based pagination). It currently documents a
  nested-error/offset-pagination contract nothing implements — the single most misleading
  doc in the set.

**Risk:** low (internal types). **Client impact:** none.

---

## Phase 1 — Dead-spec removal (no runtime risk, big clarity win)

### EMS — kill the duplicate topic surface in events-api
The live `TopicController` serves `{topicCode}` (verified); the events-api `/topics/{id}`
UUID block is stale. `topics-api.openapi.yml` is canonical.

- `[REMOVE]` from events-api: `GET/POST /topics`, `GET /topics/{id}`,
  `GET /topics/{id}/similar`, `GET /topics/{id}/usage-history`,
  `PUT /topics/{id}/override-staleness`, `POST /topics/recalculate-staleness`,
  `POST /topics/calculate-similarities`, `POST /events/{eventCode}/topics` (inline body).
- `[KEEP-BUT-FIX]` `topics-api` becomes the single canonical topic spec. Standardize
  operationIds (`getTopicByCode`, `calculateSimilarities`). If override/recalculate
  staleness are still wanted, re-add them to **topics-api** as
  `POST /topics/{topicCode}/override-staleness` (action, not PUT) + `POST /topics/recalculate-staleness`.

### Partner — kill the orphaned voting subsystem in partners-api
Zero implementation exists (verified: no `voteValue` / `TopicVoteResponse` / `getPartnerVotes`
in code). The live voting API is the toggle in `partner-topics-api`.

- `[REMOVE]` `GET/POST /partners/{companyName}/votes`, `GET/POST /partners/{companyName}/suggestions`.
- `[REMOVE]` schemas `CastVoteRequest`, `TopicVoteResponse`, `SubmitSuggestionRequest`,
  `TopicSuggestionResponse`, `SuggestionStatus` and the `Topic Voting` tag.
- This also auto-resolves the duplicate `operationId: castVote` and the conflicting
  slug-vs-UUID `topicId` definitions.

**Risk:** low (verified dead). **Action:** confirm no frontend service-layer or Bruno test
references these paths before deleting.

---

## Phase 2 — Document the real surface (specs under-document live routes)

- `[KEEP-BUT-FIX]` Add to partner specs the **undocumented live endpoints**:
  `GET /partners/me`, `DELETE /partners/topics/{topicId}`,
  `GET /partner-meetings/{id}/rsvps` (+ note the `/internal/...rsvps` callback).
- `[KEEP-BUT-FIX]` Re-enable the **commented-out generators** in
  `services/speaker-coordination-service/build.gradle` and
  `services/attendee-experience-service/build.gradle` (2 commented blocks each), adding the
  shared `importMappings`/`schemaMappings` block. Until then, speakers/attendees contracts
  are unenforced.
- `[KEEP-BUT-FIX]` Add `importMappings`/`schemaMappings` to the partner-coordination client-
  DTO tasks (`generateCompanyClientDtos`, `generateUserClientDtos`).
- `[KEEP-BUT-FIX]` Decide owners + generator wiring for the 6 orphan specs (`auth-endpoints`,
  `file-upload-api`, `partner-analytics-api`, `partner-meetings-api`, `partner-notes-api`,
  `partner-topics-api`) — none feed any generator today.

**Risk:** low–medium (re-wiring may surface latent drift — that is the point).

---

## Phase 3 — Convention conformance (documentation-truth, no behaviour change)

### Reuse shared types instead of redefining
- `[KEEP-BUT-FIX]` Convert `companies-api`, `users-api`, `topics-api` `ErrorResponse` +
  `PaginationMetadata` from full inline bodies to the **stub** form (`x-java-type` +
  `x-java-type-import`, events-api style). They already codegen via name-matched
  `schemaMappings`; this removes silent doc-vs-codegen drift.
- `[RENAME]` Standardize the stub annotation to `x-java-type` everywhere; drop `x-java-class`
  (partners).
- `[RENAME]` `speakers-api`: rename `Pagination` → `PaginationMetadata`, add `hasNext`/`hasPrev`;
  replace its bespoke inline `ErrorResponse` with the stub.
- `[KEEP-BUT-FIX]` Give the no-error-schema specs (`attendees-api`, `partner-analytics`,
  `-meetings`, `-notes`, `-topics`) a referenced `ErrorResponse` on 4xx/5xx.

### One list-query vocabulary
- `[KEEP-BUT-FIX]` `users-api listUsers`: drop ad-hoc `role`/`company`/`sortBy`/`sortDir`;
  keep JSON:API `filter`/`sort`.
- `[KEEP-BUT-FIX]` `events-api GET /newsletter/subscribers`: replace ad-hoc
  `search`/`status`/`sortBy`/`sortDir` with `filter`/`sort`.
- `[KEEP-BUT-FIX]` Add `sort` to `events-api GET /events/{eventCode}/registrations`,
  `partners GET /partners`, and add pagination to `listPartnerMeetings`/`listPartnerNotes`/
  `listTopics` (or document them as bounded).

### get-or-create
- `[RENAME]` `companies-api`: `POST /companies:get-or-create` → `POST /companies/get-or-create`
  (slash), matching users. Mark the colon form `deprecated` for one release if any client
  uses it.

**Risk:** low (mostly spec text); the list-query changes are client-affecting → deprecate-then-remove.

---

## Phase 4 — Mutation-model fixes (genuine contract changes; deprecate-then-remove)

### CUMS — collapse the PUT/PATCH twins
- `[REMOVE]` (deprecate first) `PUT /companies/{name}` and `PUT /users/me` — they reuse the
  all-optional `UpdateXRequest` and are functionally identical to the PATCH. Keep PATCH as
  the single mutation. *Or*, if a true full-replace is wanted, give PUT a required-field
  `Replace<Entity>Request` per ADR-013 §1 — but only if the difference is real.
- `[KEEP-BUT-FIX]` Reconcile `PATCH /users/me` (broad `UpdateUserRequest`) vs
  `PATCH /users/{username}/profile` (narrow `PatchUserProfileRequest`): document the
  ownership boundary or unify the field set. Pick one patch philosophy.

### CUMS — remove the duplicate upload initiator
- `[REMOVE]` (deprecate first) `POST /users/me/picture` (`uploadProfilePicture`). Keep
  `POST /users/me/picture/presigned-url` (clearer name, supports SVG). Both currently
  "generate a presigned S3 upload URL."

### EMS — one event-lifecycle transition model
- `[MERGE]` `POST …/publish` + `POST …/workflow/advance` + `PUT …/workflow/transition` → one
  model. Keep `POST /events/{eventCode}/workflow/transition` (TransitionStateRequest) as the
  general transition (POST, matching the speaker confirm/decline action style); demote
  `publish`/`advance` to documented convenience shortcuts or remove.
- `[KEEP-BUT-FIX]` `PUT /events/{eventCode}/sessions/{sessionSlug}`: `UpdateSessionRequest`
  is byte-identical to create and mostly optional → either make it a true full-replace
  (require the full session shape) or demote to PATCH.
- `[KEEP-BUT-FIX]` Name the inline `object` request bodies (`assignSpeakerToSession`,
  `declineSpeaker`, `patchMyNewsletterSubscription`, `batchImportSessions`).
- `[MERGE]` Reconcile `UpdateEventSlotConfigurationRequest` ⟷ `UpdateEventAgendaConfigRequest`
  (overlapping slot vocabularies) into one schema or base+extension.

### EMS — registration cancellation (4 entry points)
- `[MERGE]` `POST /registrations/cancel` (email token) and `POST /registrations/deregister`
  (deregistration token) → one token-cancel endpoint (the likely-legacy one is
  `cancelRegistration`). Keep `DELETE …/my-registration` (authed) and
  `/registrations/deregister/by-email` (request-link) — distinct and justified.

### Partner — pick one deactivation path
- `[KEEP-BUT-FIX]` Deactivation exists twice: `isActive=false` in `PATCH /partners/{companyName}`
  AND `DELETE /partners/{companyName}` (soft-delete). Choose one (recommend DELETE = soft
  deactivate; remove `isActive` from the PATCH body, or vice-versa) and document it.

**Risk:** medium (live endpoints). **Process:** usage check → `deprecated: true` one release
→ remove. Regenerate frontend types (`npm run generate:api-types`) and update the service
layer after each spec change.

---

## Phase 5 — Partner spec consolidation (5 specs → 2)

The partner resource is fragmented: core CRUD + contacts live in `partners-api`, but
`notes` and `analytics` are literal `/partners/{companyName}/*` sub-resources living in
*separate* files. No single source of truth for "what can I do with a partner."

- `[MERGE]` Fold `partner-notes-api`, `partner-analytics-api`, `partner-topics-api` into
  `partners-api.openapi.yml` (all under the `/partners/*` tree). Wire the single spec to the
  generator — today only `partners-api` is generated; the others are hand-implemented and
  drift-prone.
- `[KEEP]` `partner-meetings-api` stays separate (top-level `/partner-meetings` aggregate),
  brought to parity (shared error schema, pagination, documented RSVP routes).
- `[KEEP]` `POST /attendees/topics` (suggestCommunityTopic) — relocate into the merged spec
  (served by partner-coordination-service); document the `/attendees` prefix as an
  intentional attendee-facing alias.
- `[RENAME]` Clarify `getPartnerStatistics` vs `analytics/dashboard` boundary (e.g.
  `partnership-summary` vs attendance `analytics`).
- `[KEEP-BUT-FIX]` Normalize tags (top-level `tags` in every spec; `Topic Voting` →
  `Partner Topics`) and servers blocks (relative `/api/v1` everywhere).

**Risk:** low (spec reorganization + generator wiring); behaviour unchanged.

---

## Patterns to standardize on (the org reference, by example)

These already exist in the codebase and become the canonical examples in ADR-013:

1. **PUT-full-replace vs PATCH-partial** — `events-api` `/events/{eventCode}`
   (`UpdateEventRequest` 9-required vs `PatchEventRequest` 0-required, controller enforces it).
2. **POST-create + PATCH-update split** — `events-api` speaker pool.
3. **Action sub-resources for state changes** — EMS speaker `…/confirm`/`…/decline`/`…/promote`;
   CUMS `…/verify`/`…/roles`.
4. **Idempotent toggle** — `partner-topics-api` `POST`/`DELETE /partners/topics/{id}/vote`.
5. **PATCH-only, no PUT** — the entire partner domain (never shipped PUT/PATCH twins).
6. **Single `ErrorResponse` for 100% of error responses** — `events-api`/`topics-api`.
7. **POST-create + explicit `select`/`assign` instead of get-or-create** — EMS topic selection.

---

## Suggested execution order

| Phase | Risk | Client impact | Gate |
|---|---|---|---|
| 0 Shared-kernel | low | none | unit tests green; both ErrorResponse callers migrated |
| 1 Dead-spec removal | low | none (verified dead) | grep frontend + Bruno for the paths; confirm server 404s |
| 2 Document live routes | low–med | none | generators re-enabled, build green |
| 3 Convention conformance | low | low (list query) | codegen unchanged; deprecate list-vocab |
| 4 Mutation-model fixes | med | yes | deprecate-then-remove; regen FE types + service layer |
| 5 Partner consolidation | low | none | merged spec generates; controllers unchanged |

Land 0→1→2→3 as the low-risk consolidation pass; schedule 4 and 5 as follow-ups with the
deprecate-then-remove cycle so no live client breaks.

> **EMS note:** this plan is the input for applying ADR-013 to EMS. The EMS-specific work
> (Phases 1, 4 EMS items) can be tracked alongside the existing
> `ems-modularization-extension-points.md` effort.
