# Doc Audit Findings — Backend Architecture Overview
**Audited:** 2026-03-09
**Doc:** `docs/architecture/06-backend-architecture.md`
**Tests searched:** `services/event-management-service/src/test/java`, `services/company-user-management-service/src/test/java`, `api-gateway/src/test/java`

## Summary
- VALIDATED: 8
- MISMATCH: 4
- UNTESTED: 4
- UNDOCUMENTED: 6

---

## MISMATCH

### M1 — `canDemoteOrganizer` threshold: `> 2` vs `>= 2`
**Doc claims:**
```java
public boolean canDemoteOrganizer(String username, String eventCode) {
    long activeOrganizerCount = userRoleRepository.countActiveOrganizers(eventCode);
    return activeOrganizerCount > 2;
}
```
(adjacent comment: `"Cannot demote: minimum 2 organizers required"`)

**Test asserts:** `RoleServiceTest#should_allowRemovingOrganizer_when_twoOrMoreOrganizersExist`
— mocks exactly **2** organizers and expects **success**. `RoleServiceTest#should_throwMinimumOrganizersException_when_removingOrganizerAndOnlyOneRemains`
— mocks **1** organizer and expects `MinimumOrganizersException("minimum of 2 organizers")`.

The implemented rule is: demotion is allowed as long as 2 or more organizers currently exist (`count >= 2`). The doc's `> 2` predicate would require 3+ organizers, contradicting both tests.

**Action:** Update doc code to `return activeOrganizerCount >= 2;`

---

### M2 — Security filter chain: event endpoints require roles vs. anonymous access
**Doc claims:**
```java
.requestMatchers("/api/v1/events").hasAnyRole("ORGANIZER", "ATTENDEE", "SPEAKER", "PARTNER")
.requestMatchers(HttpMethod.POST, "/api/v1/events").hasRole("ORGANIZER")
```

**Test asserts:** `SecurityConfigTest#should_allowAnonymousAccess_when_viewingEventDetails` — `GET /api/v1/events/{code}` returns 404 (not 401/403) with no `Authorization` header, confirming anonymous access is **permitted**.
Similarly `SecurityConfigTest#should_allowAnonymousAccess_when_viewingCurrentEvent` — `GET /api/v1/events/current` passes security without any auth header.
`SecurityConfigTest#should_trustApiGateway_when_noJwtValidationInService` — domain services trust the API Gateway; they do **not** perform JWT validation themselves.

The actual security model for event-management-service is: public GET endpoints are unauthenticated, JWT enforcement lives in the API Gateway, not in each domain service.

**Action:** Replace the security snippet with the actual service-level config showing public event/session/speaker endpoints and the API-Gateway-trust pattern. Remove the role-restricted snippet (`hasAnyRole`) from the service section and clarify it only applies at the gateway layer (if at all).

---

### M3 — Role storage: `role_assignments` table vs. embedded `User.roles`
**Doc claims:** "Roles stored exclusively in PostgreSQL `role_assignments` table" (under User Lifecycle section) and the `RoleManagementService` snippet uses a separate `UserRoleEntity` / `userRoleRepository`.

**Test asserts:** `RoleServiceTest` (the actual role service) injects `UserRepository` only — no `UserRoleRepository`. Roles are stored as `Set<Role>` on the `User` entity (`user.getRoles()`). The test verifies `userRepository.findByRolesContaining(Role.ORGANIZER)` — a query on the User table, not a separate `role_assignments` table.

The doc describes an aspirational `RoleManagementService` with a separate `UserRoleEntity`; the deployed implementation uses `RoleService` with roles embedded on `User`.

**Action:** Update the "User Lifecycle" section to reflect that roles are stored on the `User` entity. Remove or clearly mark the `RoleManagementService` snippet as a design sketch rather than deployed code.

---

### M4 — Circuit breaker: custom `CircuitBreakerService` with count-based threshold vs. Resilience4j rate-based
**Doc claims:**
```java
@Value("${circuit-breaker.failure-threshold:5}")
private int failureThreshold;

@Value("${circuit-breaker.timeout:60000}")
private long timeoutMs;
```
Describes a bespoke `CircuitBreakerService` with a count-based failure threshold of 5 and timeout of 60 000 ms.

**Test asserts (shared-kernel):** `TestResilience4jConfig` configures **Resilience4j** circuit breakers:
- `failureRateThreshold: 60%` (production) — rate-based, not count-based
- `waitDurationInOpenState: 10 seconds` (not 60 000 ms)
- `permittedNumberOfCallsInHalfOpenState: 5`
- Named instance: `eventBridgePublisher`

The production `application-shared.yml` also uses `resilience4j.circuitbreaker` properties. The custom `CircuitBreakerService` class with `ConcurrentHashMap<String, CircuitBreaker>` does not exist as described.

**Action:** Replace the `CircuitBreakerService` snippet with the actual Resilience4j configuration. Update threshold to `failureRateThreshold: 60%` and wait duration to `10s`.

---

## UNTESTED

### U1 — Event date must be at least 30 days in the future
**Doc claims:**
```java
if (request.getEventDate().isBefore(LocalDateTime.now().plusDays(30))) {
    throw new BusinessValidationException("eventDate",
        "Event date must be at least 30 days in the future", ...)
}
```
**Risk:** high — No test was found in the three searched directories validating this constraint. If the code exists, a regression could silently drop the rule. If the code does not exist, the doc is aspirational.

---

### U2 — Only one event per quarter
**Doc claims:**
```java
if (eventRepository.existsByQuarter(getQuarter(request.getEventDate()))) {
    throw new BusinessValidationException("eventDate",
        "Only one event is allowed per quarter", ...)
}
```
**Risk:** high — No test was found validating this constraint. Same risk as U1: either a missing test or a doc-only fiction.

---

### U3 — Retry mechanism with `@Retryable` on `TransientException` / `TemporaryUnavailableException`
**Doc claims:**
```java
@Retryable(
    value = {TransientException.class, TemporaryUnavailableException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
)
```
**Risk:** medium — The Resilience4j retry config (maxAttempts=3, waitDuration=1000ms, multiplier=2.0, maxInterval=10000ms) is consistent in values, but no unit test was found asserting retry behaviour for `TransientException` or `TemporaryUnavailableException`. The Spring-Retry (`@Retryable`) class may or may not exist alongside Resilience4j.

---

### U4 — `X-Correlation-ID` header propagated via `RequestCorrelationFilter`
**Doc claims:** `RequestCorrelationFilter` reads or generates an `X-Correlation-ID` header and stores it in `ThreadLocal`.

**Risk:** low — `GlobalExceptionHandlerTest#should_includeCorrelationId_when_errorResponseGenerated` confirms a correlation ID is included in error responses, but no test verifies that `X-Correlation-ID` is **read from the incoming request header** and propagated through to downstream calls.

---

## UNDOCUMENTED

### N1 — Domain services do not validate JWTs; they trust the API Gateway
**Test:** `SecurityConfigTest#should_trustApiGateway_when_noJwtValidationInService` and `SecurityConfigTest#should_notRejectRequests_when_invalidJwtProvided` — services accept requests with no token or an invalid token on public endpoints without returning 401.

**Action:** Add a note to the "Authentication and Authorization" section: "Domain services delegate JWT validation to the API Gateway. Services are configured to trust requests forwarded by the gateway and do not perform their own JWT signature verification."

---

### N2 — Swagger UI is publicly accessible (no auth required)
**Test:** `SecurityConfigTest#should_allowAccess_when_swaggerUiAccessed` — `GET /swagger-ui/index.html` returns 200 without any token.

**Action:** Add `/swagger-ui/**` and `/v3/api-docs/**` to the documented list of permitted-all endpoints alongside `/actuator/health` and `/actuator/info`.

---

### N3 — Event details, sessions, and speaker lists are anonymous/public
**Tests:**
- `SecurityConfigTest#should_allowAnonymousAccess_when_viewingEventDetails`
- `SecurityConfigTest#should_allowAnonymousAccess_when_viewingSessions`
- `SecurityConfigTest#should_allowAnonymousAccess_when_viewingSpeakers`

**Action:** Add a "Public Endpoints" subsection to the security section documenting that `GET /api/v1/events/**`, `GET /api/v1/events/{code}/sessions/**`, and speaker read endpoints are open to unauthenticated consumers.

---

### N4 — Event registration creation and confirmation are public (no auth required)
**Tests:**
- `SecurityConfigTest#should_allowAnonymousAccess_when_creatingRegistration` — `POST /api/v1/events/{code}/registrations` passes security without auth.
- `SecurityConfigTest#should_allowAnonymousAccess_when_confirmingRegistration` — `POST /api/v1/events/{code}/registrations/confirm` passes security without auth.

**Action:** Document these public POST endpoints in the security section.

---

### N5 — Conflict detection severities: ROOM_OVERLAP / SPEAKER_DOUBLE_BOOKED = ERROR, PREFERENCE_MISMATCH = WARNING
**Tests:**
- `ConflictDetectionServiceTest#should_detectRoomOverlap_when_sessionsOverlapInSameRoom` — `severity=ERROR`
- `ConflictDetectionServiceTest#should_detectSpeakerDoubleBooking_when_speakerInOverlappingSessions` — `severity=ERROR`
- `ConflictDetectionServiceTest#should_detectPreferenceConflict_when_sessionOutsidePreferredTime` — `severity=WARNING`

The doc mentions conflict detection only generically ("Speaker cannot be invited to multiple sessions in same time slot"). It does not document severity levels or distinguish between hard errors and soft warnings.

**Action:** Add to the `validateSpeakerInvitation` / Slot Assignment section: conflict types, their severities (ERROR vs WARNING), and that preference conflicts are non-blocking.

---

### N6 — Role operations are idempotent; no domain event published on no-ops
**Tests:**
- `RoleServiceTest#should_notPublishEvent_when_roleAlreadyExists` — `addRole` when role already present: saves but does **not** publish a domain event.
- `RoleServiceTest#should_notPublishEvent_when_roleNotPresent` — `removeRole` when role absent: saves but does **not** publish a domain event.

**Action:** Add to the Role Management section: "Role add and remove operations are idempotent. A `UserRoleChangedEvent` is only published when the role set actually changes."

---

## VALIDATED
- "custom:role JWT claim extraction" → `CognitoJWTValidatorTest#should_extractUserContext_when_validTokenProvided`, `UserContextExtractorTest#should_extractUserContext_when_tokenContainsAllRequiredClaims`
- "comma-separated roles `ATTENDEE,SPEAKER` → `[ROLE_ATTENDEE, ROLE_SPEAKER]`" → `UserContextExtractorTest#should_extractAllRoles_when_multipleRolesProvided`
- "Algorithm.none() token attack rejected" → `CognitoJWTValidatorTest#should_rejectAlgorithmNone_when_unsignedTokenProvided`
- "9-state event workflow state machine" → `EventWorkflowStateMachineTest#should_transitionSuccessfully_when_validStateTransition_attempted` (CREATED→TOPIC_SELECTION), `should_throwWorkflowException_when_invalidStateTransition_attempted`
- "Retry maxAttempts=3, delay=1000ms, multiplier=2, maxDelay=10000ms" (Resilience4j values match) → `TestResilience4jConfig` / `application-shared.yml`
- "SpeakerAlreadyInvitedException idempotent" → `SpeakerInvitationServiceTest#should_returnExistingSpeakerPool_when_alreadyInvitedToEvent`
- "Error response includes correlationId and path" → `GlobalExceptionHandlerTest#should_includeCorrelationId_when_errorResponseGenerated`, `should_returnStandardErrorResponse_when_BATbernExceptionThrown`
- "Actuator health endpoint is public (permitAll)" → `SecurityConfigTest#should_allowAccess_when_healthEndpointAccessed`
