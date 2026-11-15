# Fix Summary — Speaker self-service portal (Epic 6)
**Fixed:** 2026-03-09

## Changes made

- **M1**: `docs/prd/epic-6-speaker-portal-support.md` line 11 — corrected service name from `event-management-service` to `speaker-coordination-service`. The rest of the doc (Epic Overview, Story 6.0) correctly named the speaker-coordination-service; the introductory Architecture line was an inconsistency.

- **N1**: Added "Operational Requirements" subsection to `docs/prd/epic-6-speaker-portal-support.md` under "Implementation Considerations". Documents the two Spring Boot Actuator endpoints (`GET /actuator/health`, `GET /actuator/info`) validated by `HealthControllerIntegrationTest`, including their unauthenticated access and load-balancer use.

## Skipped — needs manual decision

- **M2**: `AbstractIntegrationTest` Javadoc in `services/speaker-coordination-service/src/test/java/ch/batbern/speakers/AbstractIntegrationTest.java` references "Story 5.4: Speaker Status Management" — copy-pasted from a different service. Needs update to reference Epic 6 / Story 6.0. Cannot be fixed in `docs/` — requires a source-file edit.

- **U1**: "Bulk invitation system handles 50+ speakers" — no test validates bulk send capacity. May need integration test or acceptance criterion downgrade.
- **U2**: "Response form works without authentication via unique link" — no test verifies magic link token validation, expiry, or unauthenticated access enforcement.
- **U3**: "Response automatically updates speaker status in Epic 5 workflow" — no test verifies status transitions triggered by portal responses.
- **U4**: "Tentative removed from UI 2026-02-11; API still supports for backward compat" — no test validates the tentative response type is still accepted at the API level.
- **U5**: "If speaker self-responds after organizer manual update, show warning" — no test covers the conflict detection path.
- **U6**: "Validation: Enforce abstract length (1000 char max)" — no test asserts the 1000-character constraint.
- **U7**: "Materials upload to S3 using presigned URLs" — no test verifies presigned URL generation or S3 integration.
- **U8**: "Session Management: 30-day session expiration" — no test validates session TTL or expiry enforcement.
- **U9**: "GET /api/v1/speakers?include=events,sessions,companies" / "detail+includes <300ms P95" — no test covers CRUD endpoints, `?include` expansion, or performance constraints.
- **U10**: "Domain events publishing to EventBridge (SpeakerCreatedEvent, SpeakerUpdatedEvent, SpeakerInvitedEvent)" — no test verifies domain event publication.
- **U11**: "Deduplication: Don't send reminder if materials already submitted" — no test verifies the deduplication guard.
- **U12**: "Escalation Tiers: Tier 1 (friendly reminder), Tier 2 (urgent), Tier 3 (escalate to organizer)" — no test validates tier selection logic or escalation path.
- **U13**: "Organizer Notification: Organizers notified in real-time of speaker responses" — no test verifies notification dispatch on response events.
