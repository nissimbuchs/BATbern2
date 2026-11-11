# ADR-013: REST API CRUD Conventions

## Status
Accepted — **implemented** (api-consolidation Phases 0–9, 2026-06-29).

## Implementation status (2026-06-29)

The staged cleanup in `docs/plans/api-consolidation-plan.md` is essentially complete:

- **Phases 0–6** (shared-kernel contract, dead-spec removal, live-route documentation,
  convention conformance, mutation-model fixes, partner consolidation, events-api
  decomposition) — **done**.
- **Phase 7 (contract-first completion)** — **done across all three active services.** Every
  wireable controller in EMS, CUMS, and Partner now `implements <X>Api` against a generated
  OpenAPI interface (no hand-rolled REST controllers remain except dev/test-only
  `DevEmail`/`TestFixtureCleanup` and the external `Watch*` app contract). Combined with the
  frontend's `openapi-typescript` types, **both ends of every service are now generated from
  the OpenAPI specs** — the contract is the single source of truth end-to-end.
- **Phase 8** — `GET /public/settings/features` relocated off `AiAssistController` to a
  dedicated `event-app-settings-api` + `PublicSettingsController` (8.1). `/attendee-portal/dashboard`
  relocation deferred (attendee-experience dormant). Watch-path normalization deliberately
  skipped (the generated `WatchApi` is unimplemented and the iOS watch app vendors its own spec,
  so the inconsistency is latent/harmless).
- **Phase 9** — generator upgraded to 7.14.0 (9a); top-level `tags` declared in the remaining
  specs; stale `workflowService.ts` Javadoc fixed.

See the plan for the per-controller record.

**Story 4.9 (2026-11, API conventions I, breaking, no aliases)** applied §1 to three resources
that still had more than one endpoint to extend:

- **Tasks:** the verbs `PUT /tasks/{taskId}/complete`, `/reassign` and `/status` fold into the
  existing `PATCH /tasks/{taskId}` (`status`, `notes`, `dueDate`, `assignedOrganizerUsername`).
  A status change is a field change here, not a side-effecting transition, so PATCH, not an
  action POST.
- **Settings:** application settings have one store, EMS `AdminSettingsService`
  (`/admin/settings/{key}`); a structured setting is a validated JSON value under its key
  (`presentation`, moved from CUMS). Its anonymous read is `/public/settings/presentation`, an
  allowed read variant (§4, auth scope). A user's settings are one resource, `GET` and
  `PATCH /users/me/settings` (partial); `/users/me/preferences` and the full-replace PUT are gone.
  Derived configuration (`/public/settings/features`, the gateway `/config`) is not stored and
  stays where it is.
- **Nested child:** the abstract analysis moved under its parent,
  `POST /events/{eventCode}/speakers/{speakerId}/abstract-analysis`, loaded with
  `findByIdAndEventId` (§5); the noun avoids a verb segment.

**Story 4.9b (2026-11, one transition idiom for the event lifecycle, breaking, no aliases)**
records the EMS idiom: the action sub-resource of §1 row 4, `POST /{instance}/{verb}`, already the
most common EMS shape (speakers review/promote, registrations promote, mails retry).

- **Phases are instances:** `POST /events/{eventCode}/phases/{phase}/publish` and `…/unpublish`,
  `phase` an enum (`topic`, `speakers`, `agenda`; any other value 400). The verb follows the
  addressed instance, so `no-verb-segment` passes without an exception.
- **Workflow:** `POST /events/{eventCode}/transition` `{targetState, overrideValidation,
  overrideReason}`. `workflowState` left the event PUT/PATCH bodies, so there is one way to change
  the state (an agenda publish moves it as its side effect).
- **Publishing state and schedule are one resource:** `GET`/`PATCH /events/{eventCode}/publishing`
  (status, per-phase validation and the auto-publish schedule; PATCH partial). A schedule is a field
  change, not a transition (§1 row 2).
- **Folded:** `POST /events/{eventCode}/publish` is the agenda publish now; its validation runs
  there next to the timing rule (422 `PublishValidationError` listing every reason).
- Lint: `no-verb-segment` 43 to 39 findings, owner group T1 removed.

**Story 4.10 (2026-11, API conventions II, breaking, no aliases)** removed twins and second keys:

- **Profile and picture:** one set, `PATCH /users/{username}` and `/users/{username}/picture*`,
  for the user themself and for organizers (`@userOwnership.isCurrentUser(#username)` next to the
  role rule). `PATCH /users/me`, `PUT /users/{username}` (§1: PUT without a required-field body)
  and the `/users/me/picture*` twins are gone. `GET /users/me` stays the self read (§4) and the
  login bootstrap: a first token may lack `custom:username`, so the client takes the username for
  the write from it. Consent (`termsAccepted`) is the user's own: for another user it is 403.
- **Speakers:** one key `{speakerId}` under `/events/{eventCode}/speakers` (ADR-003 amendment),
  held by the Spectral rule `speaker-path-key`; the reminders switch is a field of the entry
  PATCH (§1, no per-attribute endpoint); the test-only `POST /speakers/invite` is gone; an
  attendee's self-nomination is its own collection, `POST /events/{eventCode}/speaker-nominations`
  (`POST /speakers` is the organizer's create, with another role and body).
- Lint: baseline 104 to 101 entries, owner groups G08, G09 and G11 removed, the 12 `{speakerId}`
  operations accepted one by one in X1. Participants and newsletter self-service follow in 4.10c.

**Story 4.10c (2026-11, API conventions II b, breaking, no aliases)** gave participants and the
newsletter one operation per intent:

- **Participants:** one create, `POST /events/{eventCode}/participants`, with exactly one mode:
  `username` (+ `force`, `notify`; 201), `people` (historical import of up to 100 people for this
  event; 200 with a result per person, each saved on its own) or `stakeholders: true` (200). The
  mode is checked by the service (400), not by `oneOf`. `POST /events/batch_registrations` (a
  create beside the collection, per participant) and `POST /events/{eventCode}/enroll-stakeholders`
  are gone. Only the single add with `notify` sends mail.
- **Newsletter self-service:** one resource per address, `GET` and `PATCH
  /newsletter/my-subscriptions` (PATCH partial: `subscribed`, `language`); the single-address
  `/newsletter/my-subscription` and the PUT without a replacement contract (§1) are gone.
- **Subscribe** is a create on the collection, `POST /newsletter/subscribers` (was the verb path
  `POST /newsletter/subscribe`), public by exact path in the gateway and EMS; Turnstile and the
  per-IP rate limit moved with it. It still sends no mail (double opt-in: #1288).
- Lint: baseline 101 to 98 entries, owner groups G06 and G10 removed.

## Context

BATbern has ~13 OpenAPI specs across five services. An audit during the
**api-consolidation** initiative (2026-06-25) found that the *read* model and *action*
endpoints are largely sound, but the **mutation** model and **shared contract** were
governed per-story rather than by a written rule. The result is drift:

### Problems found

1. **PUT and PATCH are redundant twins.** `companies-api` (`PUT`/`PATCH /companies/{name}`)
   and `users-api` (`PUT`/`PATCH /users/me`) both reuse a single **all-optional**
   `UpdateXRequest` for *both* verbs. PUT is documented as "full replacement" but cannot
   enforce it (no required fields), so the two verbs are functionally identical. Clients
   have no principled way to choose. `events-api` got this **right** (`UpdateEventRequest`
   9-required for PUT vs `PatchEventRequest` 0-required for PATCH) — that is the reference.

2. **No documented PUT-vs-PATCH rule existed.** `coding-standards.md` and `04-api-core.md`
   are silent on verb semantics, so every service invented its own (companies/users:
   PUT+PATCH twins; partner domain: PATCH-only; topics/sessions: PUT reusing the create
   schema for partial updates).

3. **Competing state-transition idioms** for one concept. The EMS event lifecycle had four
   (found 2026-06, recounted 2026-11): `POST …/publish` (validated, jumped to AGENDA_PUBLISHED,
   no CDN invalidation), `POST …/publish/{phase}` and `…/unpublish/{phase}` with an unvalidated
   string phase, `POST …/workflow/transition`, and `workflowState` in the event PUT/PATCH body,
   applied with override; the auto-publish schedule was a verb (`POST …/publish/schedule`). Fixed
   by Story 4.9b, below. The partner domain mixes action-PATCH (`/status`), boolean-in-PATCH
   (`isActive`), action-POST (`/send-invite`), and POST/DELETE toggle (`/vote`).

4. **Convention drift between sibling specs.** get-or-create is `:get-or-create` (colon,
   companies) vs `/get-or-create` (slash, users). List filtering is JSON:API `filter`/`sort`
   in some specs and ad-hoc `role`/`company`/`sortBy`/`sortDir` in others — and `listUsers`
   / `listNewsletterSubscribers` carry **both** vocabularies at once.

5. **Redundant / orphaned endpoints.** `users-api` ships two presigned-URL upload
   initiators doing the same thing. `events-api` carries a stale UUID-based `/topics/{id}`
   block that the live `TopicController` (which serves `{topicCode}`) does not implement.
   `partners-api` documents an entire weighted topic-voting subsystem
   (`/partners/{companyName}/votes`, `/suggestions`) that **has zero implementation**.

6. **Six different error envelopes and two pagination shapes.** shared-kernel even has
   **two** `ErrorResponse` classes (`dto.ErrorResponse` with `correlationId` vs
   `utils.ErrorResponse` with `traceId`). Only `events`/`partners` reference the shared
   types via the ADR-006 stub pattern; `companies`/`users`/`topics` re-declare full inline
   copies that the generator silently ignores; six specs are wired to no generator at all.

This ADR establishes the binding conventions. It governs **simple-entity CRUD** (User,
Company, Partner, Topic, Note, Meeting, …) and the shared contract every spec must use.

## Decision

### 1. Mutation verbs — the decision tree

For any resource, pick exactly one path through this tree. Do not ship two verbs that
accept the same body.

| Intent | Verb + shape | Rule |
|---|---|---|
| **Create** | `POST /collection` + `Create<Entity>Request` | Genuinely-required fields are `required`. Returns `201`. |
| **Partial field update** (default) | `PATCH /collection/{id}` + all-optional `Update<Entity>Request` | This is the default mutation. The body is all-optional; only present fields change. |
| **Full replacement** (only if real) | `PUT /collection/{id}` + `Replace<Entity>Request` with **invariant fields required** | Ship PUT **only** if a caller genuinely replaces the whole document (e.g. a full-form editor). Its body MUST mark the invariant fields `required`, making it semantically distinct from PATCH. **If you cannot articulate that difference, do not ship PUT — PATCH alone suffices.** |
| **State transition w/ side-effects** (verify, publish, deactivate) | `POST /{id}/{verb}` action sub-resource | Idempotent. Not a boolean PATCH. Publishes domain events here, not in CRUD. |
| **Replace a bounded sub-collection** (roles, tags) | `PUT /{id}/roles` with the full set | Whole-set replacement of a child collection. |
| **Toggle a membership/flag relationship** | `POST` / `DELETE /{id}/vote` | Idempotent both directions. Preferred over a boolean field in PATCH. |

**Forbidden:** per-attribute endpoints (`/setEmail`, `/updateBio`) — that is PATCH's job,
*unless* the change is itself a domain event (then it is a state transition, row 4).

**Rule of thumb:** PATCH is the default. PUT is opt-in and must earn its place with a
required-field body. Pick **one** transition idiom per service and hold it (preference:
action-POST sub-resource for side-effecting verbs; POST/DELETE toggle for flags).

### 2. get-or-create

Use **`POST /{collection}/get-or-create`** (slash form) everywhere. Return `200` when an
existing resource is returned, `201` when one is created. The colon `:get-or-create` custom-
method form is retired. (Where feasible, prefer an explicit `POST`-create + separate
`select`/`assign` over get-or-create, as EMS does — it sidesteps the idiom entirely.)

### 3. Querying lists — one vocabulary

A `GET /collection` list endpoint uses the **JSON:API-style** vocabulary only:
`filter`, `sort`, `page`, `limit`, `fields`, `include`. Do **not** also expose ad-hoc
`role` / `company` / `sortBy` / `sortDir` on the same endpoint. A separate `GET
/collection/search?query=` is permitted **only** for full-text search distinct from
structured filtering — it must not duplicate filters already expressible via `filter`.

The vocabulary names what a list may offer, not what it must: a list offers `fields` (a sparse
fieldset) only when a caller needs it, and then its response schema describes the partial items
(owner decision 2026-11-09; `GET /companies` and `GET /users` dropped an unused `fields`).

### 4. Read variants

`/{collection}/me` (self), `/{collection}/{id}` (authenticated/admin), and
`/public/{collection}/{id}` (anonymous, reduced fields) are the **only** sanctioned read
splits — and only where an auth-scope difference justifies them. Do not create read
variants that differ by anything other than auth scope / field exposure.

### 5. Identifiers (reaffirms ADR-003, narrows the "eliminate UUIDs" claim)

Top-level domain resources MUST use meaningful IDs in the path (`eventCode`, `username`,
`companyName`, `topicCode`). UUIDs are **permitted** for transient or purely-internal
sub-resources (photos, posts, uploads, meetings, notes) where no meaningful ID exists —
ADR-003's blanket "eliminate UUIDs" is hereby narrowed to "no UUIDs for top-level domain
resources." Never expose a UUID where a stable meaningful ID exists.

### 6. Shared contract — error + pagination (one source of truth)

- **Error envelope:** every 4xx/5xx references the single canonical
  `ch.batbern.shared.dto.ErrorResponse`
  (`timestamp, path, status, error, errorCode, message, correlationId, severity, details`).
  The duplicate `ch.batbern.shared.utils.ErrorResponse` is deleted. Field is
  `correlationId` (never `traceId` / `requestCode` / `statusCode`).
- **Pagination:** list responses wrap data in `PaginatedResponse<T>` with
  `PaginationMetadata` (`page, limit, totalItems, totalPages, hasNext, hasPrev`).
  Page-based, not offset/cursor.
- **Reuse, don't redefine (single source via `$ref`):** the canonical schemas live **once**
  in `docs/api/_shared.openapi.yml` (full `properties` **+** `x-java-type`). Every spec
  references them: `$ref: './_shared.openapi.yml#/components/schemas/PaginationMetadata'`.
  This works for **both** generators (verified 2026-06-26):
  - `openapi-typescript` 7.x resolves the external `$ref` (Redocly bundling) → **full TS type**.
  - `openapi-generator` resolves the `$ref` **and** maps by *name* via `schemaMappings`/
    `importMappings` → the shared-kernel class, **no duplicate DTO**.
  - ❌ Do **not** use a body-less `x-java-type` stub (no `properties`): it works for Java but
    `openapi-typescript` emits an empty `Record<string, never>` and breaks frontend types.
    (This corrects the earlier stub guidance — see ADR-006.)
- **`ValidationError`** (`field`, `message`) lives in `_shared.openapi.yml` and is referenced
  for field-level validation failures.
- A spec that is implemented and generator-wired must `$ref` the shared schemas, not redefine
  them. (Dormant/un-wired specs for consolidated-away services — see ADR-014 — are exempt.)

### 7. Documentation must match reality

`04-api-core.md` is corrected to document the **flat** `ErrorResponse` and **page-based**
pagination that are actually implemented (it previously documented a nested-error,
offset-pagination contract that nothing implements).

## Consequences

**Positive**
- One answer per question: consumers and the dev agent stop re-forking conventions per story.
- PUT/PATCH redundancy, orphan endpoints, and dead spec blocks are removed.
- A single error + pagination contract makes generated SDKs consistent.

**Costs / risks**
- Removing shipped routes (events-api UUID `/topics/{id}`, partners-api voting) is a
  contract change — verify no live client depends on them first (the servers already
  only serve the canonical routes, so UUID/voting callers are already broken).
- Re-wiring the commented-out speaker/attendee generators may surface latent spec/impl drift.
- The cleanup is staged — see `docs/plans/api-consolidation-plan.md`.

## Reference patterns (the org standard, by example)

- **PUT-as-full-replace vs PATCH-as-partial:** `events-api` `/events/{eventCode}`.
- **POST-create + PATCH-update split:** `event-speakers-api`
  (`addSpeakerToPool` / `patchSpeakerPoolEntry`, `/events/{eventCode}/speakers[/{speakerId}]`).
- **Action sub-resources:** EMS speaker `…/confirm`, `…/decline`, `…/promote`; EMS event
  `…/transition`, `…/phases/{phase}/publish` (4.9b); CUMS `…/verify`, `…/roles`.
- **Idempotent toggle:** `partner-topics-api` `POST`/`DELETE /partners/topics/{id}/vote`.
- **Single error schema for 100% of error responses:** `events-api` / `topics-api`.

_Related: ADR-003 (meaningful identifiers), ADR-006 (OpenAPI contract-first)._
