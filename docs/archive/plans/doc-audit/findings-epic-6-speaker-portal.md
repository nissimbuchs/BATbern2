# Doc Audit Findings — Speaker Self-Service Portal (Epic 6)
**Audited:** 2026-03-09
**Doc:** `docs/prd/epic-6-speaker-portal-support.md`
**Tests searched:** `services/speaker-coordination-service/src/test/java`

## Summary
- VALIDATED: 2
- MISMATCH: 2
- UNTESTED: 13
- UNDOCUMENTED: 1

The `speaker-coordination-service` test suite contains only **3 files** (base class, security config, health endpoint test). None of the Epic 6 business logic — invitations, portal responses, material submission, dashboard, or reminders — has any test coverage in this service.

---

## MISMATCH

### M1 — Claimed implementation service contradicts itself
**Doc claims (line 11):** "Speaker portal functionality implemented in `event-management-service` with magic link authentication, invitation workflow, response handling, content submission, dashboard, and automated reminders."
**Story 6.0 states (line 89):** "Service: `speaker-coordination-service/` (Java 21 + Spring Boot 3.2)"
**Observed:** The `speaker-coordination-service` main source contains only 4 files: `SpeakerCoordinationApplication.java`, `CacheConfig.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`. None of the controllers listed in Stories 6.1–6.3 (`SpeakerInvitationController`, `SpeakerPortalTokenController`, `SpeakerPortalResponseController`, `SpeakerPortalContentController`, `SpeakerPortalProfileController`) are present.
**Action:** Reconcile which service actually hosts portal functionality. If truly in `event-management-service`, correct the Epic Overview and Story 6.0. If in `speaker-coordination-service`, the claimed controllers are absent and the "deployed" status is inaccurate.

### M2 — AbstractIntegrationTest references wrong story
**Doc claims:** Epic 6 is the speaker self-service portal.
**Test asserts:** `AbstractIntegrationTest` Javadoc says "Story 5.4: Speaker Status Management" — copied from a different service, not Epic 6.
**Action:** Update the Javadoc in `AbstractIntegrationTest` to reference Epic 6 / Story 6.0.

---

## UNTESTED

### U1 — Automated Speaker Invitation System (Story 6.1)
**Doc claims:** "Generate unique response link per speaker (no authentication required)" / "Bulk invitation system handles 50+ speakers"
**Risk:** high — no test validates invitation generation, uniqueness, or bulk send capacity.

### U2 — Magic link validation (Story 6.2)
**Doc claims:** "`SpeakerPortalTokenController.java` - magic link validation" — "Response form works without authentication via unique link"
**Risk:** high — no test verifies token validation, expiry, or unauthenticated access enforcement.

### U3 — Accept/Decline response processing (Story 6.2)
**Doc claims:** "Response automatically updates speaker status in Epic 5 workflow" / "Status tracking syncs between self-service and manual"
**Risk:** high — no test verifies status transitions triggered by portal responses.

### U4 — Tentative response backward compatibility (Story 6.2)
**Doc claims:** "Tentative removed from UI 2026-02-11; API still supports for backward compat"
**Risk:** medium — no test validates that the tentative response type is still accepted at the API level after UI removal.

### U5 — Manual override / conflict resolution (Story 6.2)
**Doc claims:** "If speaker self-responds after organizer manual update, show warning"
**Risk:** medium — no test covers the conflict detection path.

### U6 — Abstract length validation (Story 6.3)
**Doc claims:** "Validation: Enforce abstract length (1000 char max)"
**Risk:** high — no test asserts the 1000-character constraint is enforced.

### U7 — Material submission S3 presigned URL (Story 6.3)
**Doc claims:** "Materials upload to S3 using presigned URLs"
**Risk:** medium — no test verifies presigned URL generation or S3 integration flow.

### U8 — Speaker dashboard 30-day session expiration (Story 6.4)
**Doc claims:** "Session Management: 30-day session expiration"
**Risk:** high — no test validates session TTL or expiry enforcement.

### U9 — Speaker CRUD and resource expansion (Story 6.0)
**Doc claims:** "`GET /api/v1/speakers?include=events,sessions,companies`" / "detail+includes <300ms P95"
**Risk:** high — no test covers CRUD endpoints, `?include` expansion, or performance constraints.

### U10 — Domain events published (Story 6.0)
**Doc claims:** "Domain events publishing to EventBridge (SpeakerCreatedEvent, SpeakerUpdatedEvent, SpeakerInvitedEvent)"
**Risk:** medium — no test verifies domain event publication.

### U11 — Automated deadline reminders deduplication (Story 6.5)
**Doc claims:** "Deduplication: Don't send reminder if materials already submitted"
**Risk:** high — no test verifies the deduplication guard against re-sending reminders.

### U12 — Reminder escalation tiers (Story 6.5)
**Doc claims:** "Escalation Tiers: Tier 1 (friendly reminder), Tier 2 (urgent), Tier 3 (escalate to organizer)"
**Risk:** medium — no test validates tier selection logic or escalation path.

### U13 — Organizer notification on speaker response (Story 6.2)
**Doc claims:** "Organizer Notification: Organizers notified in real-time of speaker responses"
**Risk:** medium — no test verifies notification dispatch on response events.

---

## UNDOCUMENTED

### N1 — Health and info endpoint availability
**Test:** `HealthControllerIntegrationTest#should_returnHealthStatus_when_healthEndpointCalled` — asserts `GET /actuator/health` returns HTTP 200 with `$.status = "UP"`
**Test:** `HealthControllerIntegrationTest#should_returnServiceInfo_when_infoEndpointCalled` — asserts `GET /actuator/info` returns HTTP 200
**Action:** These operational requirements are not mentioned in Epic 6. Add to doc section "Implementation Considerations" or a separate operational requirements section if desired.

---

## VALIDATED
- "Integration tests extend `AbstractIntegrationTest` from testFixtures" → `AbstractIntegrationTest` (present, uses real PostgreSQL 16 via Testcontainers)
- "Integration tests use PostgreSQL via Testcontainers (not H2/in-memory)" → `AbstractIntegrationTest` — singleton `PostgreSQLContainer<>("postgres:16-alpine")` started once per JVM
