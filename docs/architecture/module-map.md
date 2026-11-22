# Module map: where code goes, what it may use

Read before adding a class, endpoint or cross-service call. This is the target; today's code
breaks it in 76 known places (measured 2026-11-15 with `scripts/architecture/ems-module-target.py`;
the 100 of 2026-11-07 came from a script that dropped everything after the first `//`; the fixed one
counts 151 on the same tree with the rules of that day, 144 with today's rules). Do not add new ones.

## Services: who may call whom

CUMS (identity) is upstream of all. EMS (event) is upstream of PCS (partners).
- Calls go downstream→upstream (or through a contract the upstream owns, below), to `/internal/**`
  only, never through the public API.
- Forward the user's JWT when there is one: the receiver records who acted and checks ownership.
  Never require it: schedulers, SQS listeners and anonymous flows have none (`system:<job>`).
  `/internal` authorizes by operation, never by the user's role. Build the headers of every S2S
  call with shared-kernel `S2sHeaders.forCurrentUser()`; no client writes its own token code.
- Attribution: a who-acted column (`created_by`, `updated_by`, `decided_by`, `performed_by`, ...)
  and a who-acted value that leaves the service (`performedBy`, `decidedBy`) record shared-kernel
  `Actors`: the username, `agent:<run> for <username>` when an agent acted for that person, or
  `system:<job>` without a user. Wrap at the sink (`Actors.forUsername`, idempotent) or take
  `SecurityContextHelper.getCurrentActor()` / `actorOr(job)`; never write a bare `system`.
  Identity and ownership read the username, never the actor (`requester_username`,
  `UserOwnership`, `findBy…Username`). The EMS STOMP principal of an agent is its actor.
- The agent service calls no `/internal/**` except CUMS `/internal/agent-*`: it requests
  `ORGANIZER_LOGIN`, `LINKED_RUN` and `SCHEDULER_JOB` grants on `/internal/agent-grants` and mints
  its tokens on `/internal/agent-tokens`, checked by its caller secret (AD-2, epic decision
  2026-11-21). EMS will call CUMS `/internal/agent-grants` (`EMS_HANDOFF`, the mail handoff of the
  epic speaker mail agent) and `/internal/agent-eligibility` with its own caller secret; it has no
  such client yet, and its caller secret is injected but not read. EMS never mints agent tokens
  (AD-3). The `/internal/agent-*` operations are
  the only ones that check who calls: each verifies the `X-Agent-Caller-Secret` header itself
  (`AgentCallerSecrets`); the filter chain keeps `/internal/**` open. Everything else goes through
  the Spring gateway's public API with its delegated agent token; it never reads or writes another
  service's tables.
- agent-service (story 1 of the agent runtime epic, shipped dark with `agent.enabled=false`) serves
  `/api/v1/agent/**` (`agent-api.openapi.yml`; the gateway routes the prefix with its slash, so
  `/api/v1/agent-settings` stays CUMS). Its own API accepts Cognito tokens only (an agent token
  is 401) and organizers only; roles come from `custom:role` without the `JwtRolesConverter` DB
  fallback, which reads CUMS tables. It keeps its data in its own tables (`agent_runs` so far,
  folder `db/agent-migration`, history `flyway_schema_history_agent`), scans only
  `ch.batbern.agent` and uses shared-kernel classes directly. Open naming point for the epic's
  refactor sweep: CUMS already owns `agent_settings`, `agent_grants` and `agent_allowlist`, so
  "agent_* tables belong to agent-service" (AD-9) needs a sharper rule.
- Downstream must react to an upstream change? The upstream calls it through an `/internal`
  contract it owns (its spec, implementers listed in its configuration under
  `batbern.contracts.<name>.implementers`, called through shared-kernel `ContractCalls`, no
  downstream named in code). No polling, no event bus (ADR-016 decision 4, owner 2026-11-11). Contracts: CUMS
  user-references (`/internal/user-references/{preview,merge,primary-email}`, EMS and PCS
  implement; a new service that stores usernames implements it too), CUMS company-references
  (`/internal/company-references/{preview,merge,counts}`, EMS and PCS implement; a new service that
  stores company keys implements it too; #1251, `counts` for the duplicate-company list), CUMS Mail
  Center requests
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
  implement it above. `AudienceAdapter` is the model. Event core's: `EventResponseContributor` (a
  module's part of an event read, per `include=`), `EventWriteHooks` (capacity saved, before delete)
  and `EventThemeAttachment` (theme image); sessions' `SessionVisibility` (public session view,
  implemented in publishing).
- No new endpoints in `EventController`; it keeps HTTP only and calls `EventReadService` and
  `EventWriteService` (event core). A new EMS exception extends `DomainException` (platform,
  `exception/`: status, severity, optional code rendered as `details.code`) and needs no handler:
  `GlobalExceptionHandler` maps the base once. A body with more fields gets a handler in its module's
  own advice built with `DomainErrorResponse` (`exception/SpeakerExceptionHandler`,
  `RegistrationExceptionHandler`, `EventWorkflowExceptionHandler`), `@Order(HIGHEST_PRECEDENCE)`
  because the global advice has a catch-all (Story 17.14). `mailcenter/MailCenterExceptionHandler` is
  the older model: same order, its own `ErrorResponse`.

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
- EMS `architecture/EmsEndpointPlacementTest` (not frozen, it has no violation): `EventController`
  maps no `/registrations` or `/my-registration` path, neither by hand nor through an implemented
  interface (the generated `RegistrationsApi`); the registration endpoints live in
  `RegistrationController`, the counts and the delete guard in `RegistrationService` (Story 17.13).
  `RegistrationController` uses no `RegistrationRepository`: every registration read and write goes
  through a `@Transactional` `RegistrationService` method (#1315); the rule fails when no class has the
  controller's name. Fixture tests in `EmsModuleRulesFixtureTest` prove both rules fire.
- EMS `architecture/EmsEndpointPlacementTest`, second rule (not frozen, no violation):
  `EventController` uses event core and platform only: no Spring Data repository, no generated class
  outside `core`, no class whose module (by the naming rules in `scripts/architecture/ems-modules.json`,
  read by `ems-module-deps.py`, `ems-module-target.py` and `EmsModuleNames`; its `examples` are asserted
  by `test_ems_modules.py` and `EmsModuleNamesSharedRulesTest`) is another. The public read model, the
  analytics and the topic selection have their own controllers (`PublicEventController`,
  `EventAnalyticsController`, `EventTopicController`). Fixture tests in `EmsModuleRulesFixtureTest`.
- EMS `architecture/EmsExceptionMappingTest` (not frozen, it has no violation): `GlobalExceptionHandler`
  uses no EMS class but `DomainException` and `DomainErrorResponse`, so a module exception is mapped
  through the base or in the module's advice, never by hand in the platform advice; and every
  exception in `exception/` extends `DomainException` and has no `@ResponseStatus` (Story 17.14).
  Fixture tests in `EmsModuleRulesFixtureTest` prove both fire. `DomainExceptionResponseTest` pins the
  response of every exception in `exception/` through the real resolver chain.
- `FrozenRuleKeysTest` (EMS, CUMS) fails when a frozen rule is added, renamed or removed without
  its store: ArchUnit would otherwise accept all its current violations on the first run.
- Today's violations sit in `src/test/resources/archunit_store`. A new one fails the build; a fixed
  one drops out of the store on the next run: commit that. Never edit the store or its properties
  to accept a new one.
- Left frozen after 17.5: mail core 2 lines, `MailPipeline` using `compose.MailRenderer` and
  `PlaceholderScanner`. They stay frozen as an owner-accepted exception with no owning story (owner
  2026-11-14: no EMS modularization, 17.15 dropped); the store keeps them from growing. EMS `*Partner*Client*` 0 (entry 8
  deleted `PartnerApiClient(Impl)`, 8b `PartnerMeetingRsvpClient(Impl)`; EMS calls PCS only through
  its calendar reply contract). CUMS
  clients: 0 (entry 7 deleted the Mail Center client pair, 7b the user-merge pair; CUMS calls
  downstream only through its three contracts). Mail center wall, watch, usermerge, notification: 0
  (the mail center wall since 16.8c moved `OwnText` to mail core).

## Reuse before you build

| Need | Use | Do not |
|---|---|---|
| Send a template mail | `MailPipeline` + `EmailService` (shared-kernel) | load or render HTML yourself |
| Mail to an audience | Mail Center: an `AudienceAdapter` for `MailComposerService`. A send to an audience refuses agent tokens: shared-kernel `AudienceGuard.refuseAgentTokens(operation)` as the first statement of the service method (403 for a token carrying `act`, AD-15); it guards `MailComposerService.send`/`retry`, the newsletter and registrant bulk sends under them, the event transition to CANCELLED (it mails every registrant) and PCS `PartnerMeetingService.sendInvite` and the cancellation of an invited meeting. Single-recipient sends stay open | add a `…/send` + `…/preview` endpoint pair; an audience send without the guard |
| User or company data | `UserApiClient` (cached, `/internal`) | store name/email (ADR-004) |
| Headers of a service-to-service call | `S2sHeaders.forCurrentUser()` (shared-kernel) | read the `SecurityContext` in a client |
| A person's proven addresses | `PersonAddresses` | read additional e-mails directly |
| File upload | `uploads-api` `/uploads/{purpose}`: shared-kernel `ch.batbern.shared.upload` (`UploadService`, `UploadRecord`, `ImageUrlFetcher`) + an `UploadPurposeHandler` in the owning service (CUMS `company-logo`, `profile-picture`; EMS `event-theme-image`, `event-photo`, `teaser-image`, `session-material`), its own uploads table (`company_user_uploads`, `event_management_uploads`); frontend `uploadTarget(purpose, target?)` + `useFileUpload` (ADR-002, Stories 17.11 to 17.11d) | new presign code or endpoints; a table of another service (EMS maps no `company_user_uploads` or `logos`: `EmsDataOwnershipTest`) |
| Admin or feature setting | `AdminSettingsService` (`/admin/settings/{key}`). Exception: the agent switch, allowlist and delegate are CUMS `AgentSettingsService` (`/api/v1/agent-settings`, V41), because CUMS issues the agent grants and tokens and cannot call EMS (epic decision 2026-11-21) | a new settings resource |
| Scheduled job | `@Scheduled` + ShedLock | unlocked scheduler. Audience mails are organizer-sent only (#1179) |
| Error → HTTP status | `ClientErrorClassifier` | another catch-all |
| EMS domain error → HTTP response | extend `DomainException` (status, severity, `details.code`); extra fields in the module advice with `DomainErrorResponse` | an `@ExceptionHandler` in `GlobalExceptionHandler`, `@ResponseStatus` |

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
