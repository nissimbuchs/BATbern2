# ADR-013: REST API CRUD Conventions

## Status
Accepted

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

3. **Three competing state-transition idioms** for one concept. EMS event lifecycle has
   `POST …/publish`, `POST …/workflow/advance`, **and** `PUT …/workflow/transition`. The
   partner domain mixes action-PATCH (`/status`), boolean-in-PATCH (`isActive`),
   action-POST (`/send-invite`), and POST/DELETE toggle (`/vote`).

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
- **Reuse, don't redefine:** specs reference these via the ADR-006 stub pattern
  (`x-java-type` + `x-java-type-import`, the `events-api` style — `x-java-class` is retired).
  No spec carries a full inline copy of `ErrorResponse` / `PaginationMetadata`.
- **`ValidationError`** (`field`, `message`) is promoted to shared-kernel and referenced for
  field-level validation failures.
- Every spec is wired to a generator with the shared `importMappings` / `schemaMappings`
  block. A spec that is implemented but not generator-wired is non-conformant.

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
- **POST-create + PATCH-update split:** `events-api` speaker-pool
  (`addSpeakerToPool` / `patchSpeakerPoolEntry`).
- **Action sub-resources:** EMS speaker `…/confirm`, `…/decline`, `…/promote`;
  CUMS `…/verify`, `…/roles`.
- **Idempotent toggle:** `partner-topics-api` `POST`/`DELETE /partners/topics/{id}/vote`.
- **Single error schema for 100% of error responses:** `events-api` / `topics-api`.

_Related: ADR-003 (meaningful identifiers), ADR-006 (OpenAPI contract-first)._
