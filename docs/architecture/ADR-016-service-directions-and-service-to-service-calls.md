# ADR-016: Service directions and service-to-service calls

## Status
Accepted (2026-11-07, owner decision after the architecture review of 2026-11)

## Context

BATbern runs three services: CUMS (identity, companies), EMS (the event and everything around it)
and partner-coordination (PCS). The 2026-11 review (`docs/plans/architecture-review-2026-11-actions.md`)
measured how they call each other on develop `e7f76baf`:

- **Two cycles.** EMS calls CUMS and CUMS calls EMS (profile merge, merge-request conversations).
  EMS calls PCS (partner topics, calendar RSVPs) and PCS calls EMS (event summary, attendance).
- **Two transports side by side.** 9 client URLs go to VPC-only `/internal/**`; 12 go to the public
  `/api/v1` API with the end user's JWT. The public calls force machine-only endpoints into the public
  specs (`/users/provision`, `/users/{u}/issue-invitation-credentials`, `PATCH /users/{u}/profile`,
  `/events/attendance-summary`).
- **Contradictory rules.** ADR-008's 2026-10-27 update says callers no longer forward the user's token;
  `project-context.md` said clients must propagate it; the clients do forward it when one exists.
- **No working event bus.** The EventBridge bus `batbern-staging` had 0 rules (checked
  2026-11-07); five publishers sent into it and nobody received. Event propagation was stopped for
  cost. Story 17.2 part B (2026-11) removed the bus, its publishers and grants (see ADR-014).
- **Not every call has a user.** EMS alone has 21 `@Scheduled` methods and 5 SQS listener classes;
  anonymous registration has no token either.

## Decision

1. **Direction.** CUMS is upstream of every service. EMS is upstream of PCS. A service depends only
   on upstream services: it calls an upstream service's `/internal` operations, and calls downstream
   only through a contract it owns itself (decision 4).
2. **Transport.** Service-to-service calls go to `/internal/**`, never to the public API. The Spring
   gateway denies `/internal/**` (ADR-008), so these endpoints are reachable only inside the VPC.
   A public endpoint is never widened to serve a machine caller.
3. **The user's token.** Forward the user's JWT whenever there is one: the receiver records who acted
   and may check ownership. Never require it: a call without a user (scheduler, SQS listener,
   anonymous flow) must succeed and is recorded as `system:<job>`. `/internal` endpoints authorize by
   operation, never by the user's role; the role check stays on the public endpoint where the user
   entered.
4. **No event bus, no polling. Contracts owned by the upstream service.** When a downstream service
   must react to an upstream change, the upstream service calls it synchronously through an
   `/internal` contract it owns: specified in the upstream's internal spec, implemented by each
   downstream that needs it, the implementers listed in the upstream's configuration. The upstream
   names no downstream service in code; it depends on its own contract (dependency inversion).
   Amendment 2026-11-11 (owner): replaces the pull feeds first written here ("`?since=<cursor>` from a
   ShedLock job"). Polling adds constant traffic that almost always finds nothing, a delay of up to
   one interval, and a job, cursor table and lock per feed; CUMS and EMS are almost never down, so a
   synchronous call with no outbox and no retry job is accepted. The EventBridge bus and its
   publishers stay removed; in-process Spring events stay.
5. **Contracts that exist.** Owned by CUMS: the user-reference contract
   `/internal/user-references/preview|merge|primary-email`, implemented by every service that
   stores usernames (EMS, PCS); synchronous because a holder can refuse a merge and must deliver
   preview counts, and because a registration mail must not go to the old address (primary email,
   owner 2026-11-11). Owned by CUMS: the Mail Center request contract (merge and logo requests
   opened and decided), implemented by EMS. Owned by EMS: the calendar reply contract (iMIP RSVP
   replies), implemented by PCS. A new upstream→downstream need gets its own contract the same way.
6. **Direct table reads across services** (the services share one PostgreSQL database) are not a
   substitute for `/internal` calls. The existing ones are listed for the database review; no new ones.

## Consequences

**Positive**
- No service cycle in the model; each service can be reasoned about with its upstreams only.
- Public specs contain only what users call. Machine endpoints stop needing user roles.
- One rule for every caller, with or without a user, and no audit gap: `system:<job>` is explicit.
- Removing the bus deletes a failure mode (circuit breaker, retries) that bought nothing.

**Costs**
- Migration work: 12 client URLs and 4 endpoints move to `/internal` (Epic 17, story 17.6); the
  CUMS→EMS calls become the two CUMS-owned contracts (17.7, entries 7 and 7b); EMS→PCS becomes the
  topic canvas reading PCS itself (17.8, entry 8, live 2026-11-11) and the EMS-owned calendar reply
  contract (8b).
- A contract call fails while its implementer is down. Where the call is part of the action, the
  action fails with it (as before 2026-11): filing a merge or logo request answers 502 and stores
  nothing. Where it reports an action already committed, the action stands and the failure is
  logged as an ERROR and counted: a Mail Center request decision (`mailcenter.request_contract.failures`;
  the conversation then stays open until an organizer closes it). Accepted by the owner 2026-11-11
  for CUMS and EMS, which are almost never down.
- `/internal` trusts the network boundary. If that ever stops holding, service authentication comes
  back as a new decision.

## Enforcement
- ArchUnit in every service: no class that names a downstream service (`*EventManagement*Client*`,
  `*Partner*Client*`; story 17.5). Contract callers are named after the contract.
- OpenAPI lint: no `/internal` path in a public spec (story 17.4).
- S2S client URL guard: no string literal in a service's `client` package contains `/api/v1`
  (`web-frontend/scripts/check-s2s-client-urls.ts`, CI job `test-ratchet`; Epic 17 entry 6,
  2026-11-10). Its baseline only shrinks: 5 lines at the start, 2 since 6b (2026-11-11: PCS calls
  CUMS and EMS over `/internal` only), 0 since entry 8 (2026-11-11: EMS `PartnerApiClientImpl`
  deleted; the topic canvas loads the partner topics from PCS in the browser and has EMS classify
  them with `POST /topics/classifications`).
- One caller for decision 4: shared-kernel `ContractCalls` calls a contract its service owns at every
  implementer in `batbern.contracts.<name>.implementers`, with the headers below; 409 becomes
  `ContractRefusedException`, any other failure `ContractCallFailedException` (entry 7; 7b and 8b
  reuse it).
- One header helper for decision 3: shared-kernel `S2sHeaders.forCurrentUser()` forwards the
  user's JWT when there is one and sends none otherwise; CUMS records the forwarded
  `custom:username`, else `system:<job>` (Epic 17 entry 6). A token that expires within 60 s
  (`EXPIRY_MARGIN`) is not forwarded: the receiver would answer 401 even on `/internal`, and an
  `@Async` task may outlive the token; the call then goes out as a system call. Users: EMS
  `UserApiClientImpl` (entry 6); PCS `UserServiceClientImpl`, `CompanyServiceClientImpl`,
  `EventManagementClientImpl` and `HttpOutboundMailRecorder` (6b). Exception until its entry
  moves it: EMS `PartnerMeetingRsvpClientImpl` (no headers, owner 8b). EMS `PartnerApiClientImpl`
  (own `createHeadersWithJwtToken`) was deleted by entry 8.
- `docs/architecture/module-map.md` carries these rules into every agent workflow.

## Related
- ADR-003 (meaningful IDs; the 2026-11 amendment covers UUIDs in paths)
- ADR-004 (no user fields in domain entities; enrich via CUMS)
- ADR-008 (gateway; its 2026-10-27 token sentence is superseded by decision 3 here)
- ADR-014 (service consolidation; stub and bus removal decided 2026-11-07)
- `docs/plans/architecture-review-2026-11-actions.md`, `docs/architecture/module-map.md`
