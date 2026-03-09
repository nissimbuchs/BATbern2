# Fix Summary — Backend Architecture Overview
**Fixed:** 2026-03-09
**Doc:** `docs/architecture/06-backend-architecture.md`

## Changes made

### MISMATCH fixes

- **M1** — `canDemoteOrganizer` predicate corrected from `> 2` to `>= 2`. The deployed rule allows demotion when 2 or more organizers remain (consistent with both `RoleServiceTest` cases: 2 organizers → success, 1 organizer → `MinimumOrganizersException`).

- **M2** — Replaced the role-gated security filter chain snippet (which falsely implied domain services enforce `hasAnyRole("ORGANIZER", "ATTENDEE", ...)`) with the actual model: domain services trust the API Gateway and expose public endpoints via `permitAll`. Added a note that JWT validation is the gateway's responsibility. Documented all public endpoints in a table (event details, sessions, speakers, registration create/confirm, actuator, Swagger UI).

- **M3** — Updated the "User Lifecycle" key-point line: roles are stored as `Set<Role>` on the `User` entity, not in a separate `role_assignments` table. Added a note to the Role Management section header clarifying that the `RoleManagementService` snippet is a design reference; the deployed `RoleService` uses `UserRepository` only.

- **M4** — Replaced the bespoke `CircuitBreakerService` snippet (count-based threshold of 5, timeout 60 000 ms, `ConcurrentHashMap`) with the actual Resilience4j YAML configuration: `failureRateThreshold: 60%`, `waitDurationInOpenState: 10s`, `permittedNumberOfCallsInHalfOpenState: 5`, named instance `eventBridgePublisher`.

### UNDOCUMENTED additions

- **N1** — Added a prominent note at the top of the "Role-Based Security Configuration" subsection: domain services do not validate JWT signatures; that responsibility belongs to the API Gateway.

- **N2** — Added `/swagger-ui/**` and `/v3/api-docs/**` to the public-endpoints table (confirmed by `SecurityConfigTest#should_allowAccess_when_swaggerUiAccessed`).

- **N3** — Documented public `GET /api/v1/events/**`, `GET /api/v1/events/{code}/sessions/**`, and `GET /api/v1/events/{code}/speakers/**` endpoints in the new public-endpoints table.

- **N4** — Documented public `POST /api/v1/events/{code}/registrations` and `POST /api/v1/events/{code}/registrations/confirm` in the public-endpoints table.

- **N5** — Added "Conflict Detection Severities" subsection after `validateSpeakerInvitation`: `ROOM_OVERLAP` → ERROR (blocking), `SPEAKER_DOUBLE_BOOKED` → ERROR (blocking), `PREFERENCE_MISMATCH` → WARNING (non-blocking).

- **N6** — Added idempotency note to the Role Management section header: `addRole`/`removeRole` are idempotent; `UserRoleChangedEvent` is only published when the role set actually changes.

## Skipped — needs manual decision

- **U1** — `"Event date must be at least 30 days in the future"` — no test was found in the searched directories. Either a missing test or an aspirational code snippet in the doc. Recommend: add a unit test for `EventBusinessRules.validateEventCreation` or remove the validation claim from the doc if the rule was never implemented.

- **U2** — `"Only one event is allowed per quarter"` — no test was found. Same risk as U1: may be aspirational. Recommend: add a test or remove from doc.

- **U3** — `@Retryable(value = {TransientException.class, TemporaryUnavailableException.class}, ...)` — Resilience4j retry values are consistent with the doc, but no unit test asserts retry behaviour for these exception types. If Spring-Retry (`@Retryable`) and Resilience4j retry coexist, document which layer handles which exception. Otherwise, add a test or clarify that only Resilience4j retry is used.

- **U4** — `X-Correlation-ID` header propagation via `RequestCorrelationFilter` — the error-response test confirms a correlation ID appears in responses, but no test verifies the filter **reads** an incoming `X-Correlation-ID` header and propagates it. Low-risk gap; consider adding a filter-level unit test.
