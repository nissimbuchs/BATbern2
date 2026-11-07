# ADR-014: Backend Service Consolidation — EMS + CUMS Primary; Speaker & Attendee Services Dormant

## Status
Accepted (2026-06-26)

## Context

BATbern was originally scaffolded as five domain microservices (event-management,
company-user-management, speaker-coordination, partner-coordination, attendee-experience).
Reality has diverged from that scaffold:

- **`speaker-coordination-service`** is a hollow stub (~4 files, no controllers). All speaker
  logic was migrated **into event-management-service (EMS)** during Epic 11 (Unified Speaker
  Workflow, ADR-009). The speaker workflow, pool, invitations, and portal endpoints are served
  by EMS controllers today.
- **`attendee-experience-service`** is a 1-file stub. The attendee-facing endpoints that exist
  (dashboard, feature flags, community topics) are served by EMS / partner-coordination.
- **EMS** and **CUMS** (company-user-management) carry essentially all backend logic; EMS is
  large enough that it is being internally modularized rather than split further
  (see `docs/plans/ems-modularization-extension-points.md`, whose explicit anti-goal is
  "do not split into more microservices").
- The corresponding OpenAPI specs `docs/api/speakers-api.openapi.yml` and
  `attendees-api.openapi.yml` have their code-generation tasks **commented out** in the
  respective `build.gradle` files, so they are neither generated nor enforced. (Both specs
  and the commented blocks were deleted by Story 17.1, 2026-11; see the decision below.)

During the API-consolidation work (ADR-013) the question arose whether to **re-enable**
generation for the speaker/attendee specs. Doing so would emit API interfaces that no
controller implements and would require a contract-ownership decision (the real
implementations live in EMS). The product owner confirmed the strategic direction instead.

## Decision

**The backend consolidates around two primary services — EMS and CUMS — plus the genuinely
separate partner-coordination-service. We do not add microservices, and we do not revive the
dormant ones.**

1. **`speaker-coordination-service` and `attendee-experience-service` are DORMANT.** They are
   not developed against, and their logic stays in EMS (speaker) / EMS + partner (attendee).
2. **Their OpenAPI specs are NOT generator-wired and are NOT maintained as live contracts.**
   `speakers-api.openapi.yml` / `attendees-api.openapi.yml` are historical/reference only
   (deleted by Story 17.1, 2026-11; they remain in git history). Do
   **not** re-enable their `openApiGenerate` tasks. They are explicitly **exempt** from the
   ADR-013 conformance rules (shared-schema `$ref`, etc.) until/unless a service is revived.
3. **New speaker/attendee API surface is added to EMS** (or partner-coordination where it
   already owns the data), following the existing controller patterns.
4. **EMS is modularized internally, not split** (ADR-006 generation patterns + the
   ems-modularization plan). Partner-coordination and CUMS remain independent services.
5. Reviving a dormant service is a deliberate, separately-decided effort (a new ADR), not a
   side effect of touching its stale spec.

## Consequences

**Positive**
- No effort wasted generating/maintaining contracts for services that implement nothing.
- Clear answer to "where does speaker/attendee code go?" → EMS (or partner), not the stubs.
- Fewer deployables to operate; aligns with the internal-modularization direction.

**Costs / risks**
- The stale `speakers-api` / `attendees-api` specs can mislead a reader into thinking those
  services are live. Mitigation: this ADR + a note in those specs; they are reference-only.
  Resolved by Story 17.1 (2026-11), which deleted both specs.
- EMS keeps growing; the modularization plan is the counter-measure (vertical domain modules,
  including the events-api decomposition in `api-consolidation-plan.md` Phase 6).

## Related
- ADR-009 (Unified Speaker Workflow — moved speaker logic into EMS, Epic 11)
- ADR-006 (OpenAPI contract-first — generation wiring)
- ADR-013 (REST API CRUD conventions — exempts dormant/un-wired specs)
- `docs/plans/ems-modularization-extension-points.md` (internal modularization; anti-goal: no new microservices)
- `docs/plans/api-consolidation-plan.md` (Phase 2 closed as "won't do" per this ADR)

## Decision 2026-11-07: remove both stubs and the event bus

The owner decided after the 2026-11 review (live ECS counts checked that day: SpeakerCoordination
desired 1 / running 1, AttendeeExperience 0 / 0): stop and remove both services and their stacks,
build-matrix entries and the gateway route `/api/v1/content/**`. Remove the EventBridge bus
`batbern-staging` (0 rules) and its publishers as well, keeping in-process Spring events. Delete
`speakers-api.openapi.yml` and `attendees-api.openapi.yml`. Planned as Epic 17 story 17.2 (closes
#1216, the bus part of #1220) and 17.1 (specs). Infrastructure change: not on an event day.
Story 17.1 deleted both specs, their generated frontend types (the frontend now uses
`event-speakers-api` types) and every reference in hooks, CI scans and npm scripts. The stub
modules, stacks and ECS services are still 17.2.

## Operational state (2026-11-04)

Facts as read from CDK and gateway code on `develop` (c4593449). **Live ECS task counts were not
checked.** No decision is recorded here; the options are in #1216.

| | speaker-coordination (SCS) | attendee-experience (AES) |
|---|---|---|
| Java sources (`src/main/java`) | 4 files: application, `CacheConfig`, `SecurityConfig`, `GlobalExceptionHandler`. No controllers | 1 file: `AttendeeExperienceApplication` |
| CDK stack | `BATbern-staging-SpeakerCoordination` | `BATbern-staging-AttendeeExperience` |
| Task size | 256 CPU / 1024 MiB | 256 CPU / 1024 MiB |
| Task count in CDK | `desiredCount: 1`, `minCapacity: 1`, `maxCapacity: 2` (`speaker-coordination-stack.ts`) | `desiredCount: 0`, `disableAutoScaling: true` (`attendee-experience-stack.ts`, parked per `docs/plans/aws-cost-reduction.md` tier 1) |
| Gateway routes (`DomainRouter`) | none | `/api/v1/content/**`, which can only fail while AES runs no task |
| Still built and deployed | yes: `build.yml` matrix, `deploy-staging.yml` layer 4.3, native dev scripts | yes: `build.yml` matrix, `deploy-staging.yml` layer 4.5, native dev scripts |

So the decision above is implemented in code ownership (all speaker and attendee logic lives in
EMS and partner-coordination) but only partly in the runtime: SCS still runs a Fargate task that
serves nothing, and the gateway still routes one prefix to the parked AES. See #1216.

