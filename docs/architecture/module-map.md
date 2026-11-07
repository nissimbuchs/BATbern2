# Module map: where code goes, what it may use

Read before adding a class, endpoint or cross-service call. This is the target; today's code
breaks it in 101 known places (review 2026-11). Do not add new ones.

## Services: who may call whom

CUMS (identity) is upstream of all. EMS (event) is upstream of PCS (partners).
- Calls go downstream→upstream, to `/internal/**` only, never through the public API.
- Forward the user's JWT when there is one: the receiver records who acted and checks ownership.
  Never require it: schedulers, SQS listeners and anonymous flows have none (`system:<job>`).
  `/internal` authorizes by operation, never by the user's role.
- Downstream must react to an upstream change? It pulls an `/internal` feed (`?since=cursor`,
  ShedLock job). No event bus.
- Only allowed upstream→downstream call: the CUMS profile merge (`/internal/users/merge*`).
  Do not add another.

## EMS modules: dependencies point down only, never sideways

| Layer | Modules |
|---|---|
| L6 edge | mail center (compose, conversations, forwarding, newsletter), watch, ai, analytics, usermerge, organizer notifications |
| L5 | agenda planning (slot assignment, conflicts, preferences), publishing (public shaping, CDN) |
| L4 | speakers (pool, workflow, portal, content) |
| L3 | sessions, registrations, topics, tasks, media |
| L2 | event core (Event, workflow, event types) |
| L1 | mail core (`MailPipeline`, templates, delivery), notify core |
| L0 | platform (config, security, clients, exception base) |

- A lower module needs to trigger a higher one? Define an interface in the lower module and
  implement it above. `AudienceAdapter` is the model.
- No new endpoints in `EventController`. New exceptions go in their own module, not in
  `GlobalExceptionHandler`.

## Reuse before you build

| Need | Use | Do not |
|---|---|---|
| Send a template mail | `MailPipeline` + `EmailService` (shared-kernel) | load or render HTML yourself |
| Mail to an audience | Mail Center: an `AudienceAdapter` for `MailComposerService` | add a `…/send` + `…/preview` endpoint pair |
| User or company data | `UserApiClient` (cached, `/internal`) | store name/email (ADR-004) |
| A person's proven addresses | `PersonAddresses` | read additional e-mails directly |
| File upload | presign + confirm per ADR-002 (shared service pending) | a ninth presign service; check first |
| Admin or feature setting | `AdminSettingsService` (`/admin/settings/{key}`) | a new settings resource |
| Scheduled job | `@Scheduled` + ShedLock | unlocked scheduler. Audience mails are organizer-sent only (#1179) |
| Error → HTTP status | `ClientErrorClassifier` | another catch-all |

## API rules

- Before adding an operation, grep `docs/api/` for the nearest existing one. Extend it if it
  differs by one field or filter.
- State changes go through `PATCH` on the resource, not verb endpoints (`/complete`, `/reassign`).
- Paths are kebab-case and plural. A literal segment under `/events/` collides with
  `{eventCode}`: avoid.
- IDs (ADR-003): use a meaningful ID for anything cross-service, stored elsewhere, or bookmarked.
  A UUID is allowed only when the thing has no natural name, never crosses a service, and is
  nested under its meaningful parent and loaded with `findByIdAnd{Parent}`. Personal data
  (e-mail) never goes in a path.
- `me` and `{username}` are one resource with an ownership rule, not two endpoint sets.
- Every write needs `@PreAuthorize` (ADR-008). Machine-only endpoints never go in a public spec.

## Placement

Code used by two services goes in `shared-kernel`, never copied (`GenericLogoService` exists twice:
don't add a third). Frontend: route pages in `pages/`, UI in `components/{area}/`, API calls only
in `services/`. Full layout: `docs/architecture/source-tree.md`, or `ls`.

## Story architecture note (5 lines, before the first test)

Modules touched · new cross-module or cross-service dependencies · new or changed endpoints, each
with the nearest existing one considered · components reused · deviations from this map and why.
