# Module map: where code goes, what it may use

Read before adding a class, endpoint or cross-service call. This is the target; today's code
breaks it in 100 known places (measured 2026-11-07). Do not add new ones.

## Services: who may call whom

CUMS (identity) is upstream of all. EMS (event) is upstream of PCS (partners).
- Calls go downstream→upstream (or through a contract the upstream owns, below), to `/internal/**`
  only, never through the public API.
- Forward the user's JWT when there is one: the receiver records who acted and checks ownership.
  Never require it: schedulers, SQS listeners and anonymous flows have none (`system:<job>`).
  `/internal` authorizes by operation, never by the user's role. Build the headers of every S2S
  call with shared-kernel `S2sHeaders.forCurrentUser()`; no client writes its own token code.
- Downstream must react to an upstream change? The upstream calls it through an `/internal`
  contract it owns (its spec, implementers listed in its configuration under
  `batbern.contracts.<name>.implementers`, called through shared-kernel `ContractCalls`, no
  downstream named in code). No polling, no event bus (ADR-016 decision 4, owner 2026-11-11). Contracts: CUMS
  user-references (`/internal/user-references/{preview,merge,primary-email}`, EMS and PCS
  implement; a new service that stores usernames implements it too), CUMS Mail Center requests
  (EMS implements), EMS calendar replies (PCS implements). Do not call downstream any other way.
- Every `/internal` operation is specified in `docs/api/internal/<provider>-internal.openapi.yml`
  (17.0b2; a contract in its owner's spec) and contract-tested on both sides (`ContractServer`, provider
  `S2sProviderContractIntegrationTest`). A new `/internal` endpoint or call extends that spec first.

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
  `GlobalExceptionHandler`; a module's own advice is the model (`mailcenter/MailCenterExceptionHandler`,
  `@Order(HIGHEST_PRECEDENCE)` because the global advice has a catch-all).

## Gates (ArchUnit, frozen, in each service's `test`)

- S2S client URL guard (source scan, Epic 17 entry 6): no string literal in
  `services/*/src/main/java/**/client/**` contains `/api/v1`. `web-frontend/scripts/
  check-s2s-client-urls.ts`, `make check-s2s-client-urls`, CI job `test-ratchet`. The baseline
  `s2s-client-urls-baseline.txt` may only shrink; each line has an owner in
  `s2s-client-urls-owners.txt`. Empty since entry 8 (2026-11-11): PCS calls CUMS and EMS over
  `/internal` only since 6b, and EMS's `PartnerApiClientImpl` is deleted (the topic canvas loads
  the partner topics in the browser). A new call site fails as RAISED: call the provider's
  `/internal` operation instead.

- EMS `architecture/EmsModuleWallsTest`: nothing outside mail center (`mailcenter` + `forwarding`),
  `watch`, `usermerge` uses them (mail core in `mailcenter.render|variables` excepted); mail core
  (`mailcenter.render|variables`) uses no other mail-center class; `notification` uses none of
  them; no `*Partner*Client*`. CUMS `ServiceDirectionTest`: no `*EventManagement*Client*`,
  `*Partner*Client*`. PCS: none, it is the most downstream service.
- EMS `architecture/EmsDataOwnershipTest` (not frozen, it has no violation): no EMS entity maps an
  upload table of another service, `company_user_uploads` or the former shared `logos` (dropped by
  CUMS V31 / EMS V154, #1307); EMS keeps its files in `event_management_uploads` (Story 17.11). The
  fixture test `EmsModuleRulesFixtureTest.should_fail_when_entityMapsUploadTableOfAnotherService`
  proves the rule fires.
- `FrozenRuleKeysTest` (EMS, CUMS) fails when a frozen rule is added, renamed or removed without
  its store: ArchUnit would otherwise accept all its current violations on the first run.
- Today's violations sit in `src/test/resources/archunit_store`. A new one fails the build; a fixed
  one drops out of the store on the next run: commit that. Never edit the store or its properties
  to accept a new one.
- Left frozen after 17.5 (each with its owner): mail core 2 lines, `MailPipeline` using
  `compose.MailRenderer` and `PlaceholderScanner` (17.15); EMS `*Partner*Client*` 0 (entry 8
  deleted `PartnerApiClient(Impl)`, 8b `PartnerMeetingRsvpClient(Impl)`; EMS calls PCS only through
  its calendar reply contract). CUMS
  clients: 0 (entry 7 deleted the Mail Center client pair, 7b the user-merge pair; CUMS calls
  downstream only through its two contracts). Mail center wall, watch, usermerge, notification: 0
  (the mail center wall since 16.8c moved `OwnText` to mail core).

## Reuse before you build

| Need | Use | Do not |
|---|---|---|
| Send a template mail | `MailPipeline` + `EmailService` (shared-kernel) | load or render HTML yourself |
| Mail to an audience | Mail Center: an `AudienceAdapter` for `MailComposerService` | add a `…/send` + `…/preview` endpoint pair |
| User or company data | `UserApiClient` (cached, `/internal`) | store name/email (ADR-004) |
| Headers of a service-to-service call | `S2sHeaders.forCurrentUser()` (shared-kernel) | read the `SecurityContext` in a client |
| A person's proven addresses | `PersonAddresses` | read additional e-mails directly |
| File upload | `uploads-api` `/uploads/{purpose}`: shared-kernel `ch.batbern.shared.upload` (`UploadService`, `UploadRecord`, `ImageUrlFetcher`) + an `UploadPurposeHandler` in the owning service (CUMS `company-logo`, `profile-picture`; EMS `event-theme-image`, `event-photo`, `teaser-image`, `session-material`), its own uploads table (`company_user_uploads`, `event_management_uploads`); frontend `uploadTarget(purpose, target?)` + `useFileUpload` (ADR-002, Stories 17.11 to 17.11d) | new presign code or endpoints; a table of another service (EMS maps no `company_user_uploads` or `logos`: `EmsDataOwnershipTest`) |
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
- CI checks every frontend call through the API client, axios or a `*Client` against `docs/api/`
  (`web-frontend/scripts/check-api-paths.ts`, `make check-frontend-api-paths`), and fails a new
  file outside `services/` that makes one. `fetch` is not scanned.

## Placement

Shared code goes in `shared-kernel`, never copied. One shared database: no new reads of another
service's tables (ADR-016). Frontend: pages in `pages/`, UI in `components/{area}/`, API calls
only in `services/`. Full layout: `docs/architecture/source-tree.md`, or `ls`.

## Story architecture note (5 lines, before first test)

Modules touched · new cross-module or cross-service dependencies · new or changed endpoints, each
with the nearest existing one considered · components reused · deviations from this map and why.
