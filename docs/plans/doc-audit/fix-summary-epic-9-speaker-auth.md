# Fix Summary — Speaker Authentication (Epic 9)
**Fixed:** 2026-03-09

## Changes made

- **M1**: Added explicit `Integration tests location: services/event-management-service/src/test/java` note to the Story 9.1 Testing block, and expanded the global Testing Strategy "Integration Tests" subsection with a per-story test location table. This makes clear that Story 9.1 tests live in `event-management-service` (where `MagicLinkService` and `SpeakerMagicLoginController` are implemented), not in `speaker-coordination-service`.

- **N1 + N2**: Added an "Operational Readiness" subsection to the Testing Strategy section documenting that `speaker-coordination-service` exposes `GET /actuator/health` (→ HTTP 200, `status: UP`) and `GET /actuator/info` (→ HTTP 200), covered by the existing `HealthControllerIntegrationTest`. These are service-foundation tests inherited from Story 5.4, not Epic 9 business-rule tests.

## Skipped — needs manual decision

- **U1**: "JWT tokens 30-day expiry (AC1 & AC4)" — no test exists verifying expiry duration or reusability. Should add an integration test in `event-management-service` or remove the specific expiry claim from the AC.
- **U2**: "JWT claims content (AC6)" — no test validates the exact claim set (`user_id`, `email`, `roles`, `expiration`). Needs an integration test asserting the parsed JWT structure.
- **U3**: "HTTP-only cookie attributes (AC2)" — no test verifies `HttpOnly` + `Secure` cookie flags. High security risk; add integration test for `SpeakerMagicLoginController` response headers.
- **U4**: "Invalid/expired JWT error messaging (AC5)" — no test asserts error response shape for bad tokens. Recommend adding a negative-path integration test.
- **U5**: "Email-match branching logic (Story 9.2 AC1)" — new-user-create vs existing-user-role-extend logic is entirely untested. Core Epic 9 invariant; must be covered before 9.2 is marked done.
- **U6**: "Zero duplicate accounts (Story 9.2 AC5)" — deduplication test absent; add Cognito-level uniqueness enforcement test before production deploy.
- **U7**: "Dual auth JWT equivalence (Story 9.3 AC3)" — equivalence between magic-link and password JWTs untested. Add integration test comparing claim sets from both paths.
- **U8**: "7-day grace period for deprecated tokens (Story 9.4 AC3)" — legacy `speaker_tokens` grace-window behavior untested. Needs time-mocked integration test.
- **U9**: "RS256 signing algorithm enforcement" — no test rejects HS256 or unsigned tokens. High security risk; add a security test asserting algorithm validation before production deploy.
