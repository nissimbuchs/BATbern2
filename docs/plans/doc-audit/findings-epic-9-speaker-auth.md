# Doc Audit Findings — Speaker Authentication (Epic 9)
**Audited:** 2026-03-09
**Doc:** `docs/prd/epic-9-speaker-authentication.md`
**Tests searched:** `services/speaker-coordination-service/src/test/java`

## Summary
- VALIDATED: 0
- MISMATCH: 1
- UNTESTED: 9
- UNDOCUMENTED: 2

**Key finding:** The entire Epic 9 test suite is absent from the searched directory. The
speaker-coordination-service test folder contains only 3 files — `AbstractIntegrationTest`,
`TestSecurityConfig` (both tagged Story 5.4), and `HealthControllerIntegrationTest` — none of
which exercise any Epic 9 business rule. According to the doc itself, Story 9.1 is implemented
inside `event-management-service`, not `speaker-coordination-service`, which explains the gap.
However the doc's testing strategy ("Integration tests", "E2E tests") never clarifies *which*
service owns the tests, creating an audit blind spot.

---

## MISMATCH

### M1 — Story 9.1 implementation service contradicts audit scope
**Doc claims:** Story 9.1 is "Implemented in:
`services/event-management-service/.../MagicLinkService.java`" and
`services/event-management-service/.../SpeakerMagicLoginController.java`
**Test asserts:** `services/speaker-coordination-service/src/test/java` contains zero tests
referencing JWT, magic link, or `SpeakerMagicLoginController`. The only controller test present
is `HealthControllerIntegrationTest`.
**Action:** The doc's "Testing" section for Story 9.1 should explicitly state that integration
tests live in `services/event-management-service/src/test/java`, not in the speaker-coordination
service. Add: "Integration tests location: `event-management-service`" to each story's Testing
block.

---

## UNTESTED

### U1 — JWT token 30-day expiry (Story 9.1 AC1 & AC4)
**Doc claims:** "Magic link emails contain JWT tokens (30-day expiry, reusable)" and "JWT tokens
support same 30-day reusability as Epic 6 tokens"
**Risk:** High — no test in the searched scope verifies expiry duration or reusability across
multiple uses within 30 days.

### U2 — JWT claims content (Story 9.1 AC6)
**Doc claims:** "JWT tokens include user_id, email, roles (SPEAKER), expiration timestamp"
**Risk:** High — no test validates the exact claim set embedded in generated tokens.

### U3 — HTTP-only cookie storage (Story 9.1 AC2)
**Doc claims:** "Clicking magic link extracts JWT from URL, stores in HTTP-only cookie"
**Risk:** High (security) — no test in scope verifies that the response sets a cookie with
`HttpOnly` and `Secure` attributes.

### U4 — Invalid/expired JWT error messaging (Story 9.1 AC5)
**Doc claims:** "Invalid/expired JWT tokens show clear error message with contact info"
**Risk:** Medium — no test asserts the shape or content of the error response for bad tokens.

### U5 — Email-match role extension vs. new account creation (Story 9.2 AC1)
**Doc claims:** "Email doesn't exist in Cognito → create new user with SPEAKER role + temp
password" and "Email exists (attendee account) → add SPEAKER role to existing account (no
duplicate)"
**Risk:** High — the branching logic (create vs. extend) is the core invariant of Epic 9 and
is completely untested in this service.

### U6 — Zero duplicate accounts (Story 9.2 AC5 & Risk 2 mitigation)
**Doc claims:** "Zero duplicate accounts created (email uniqueness enforced)" and "Integration
tests validate duplicate prevention"
**Risk:** High — no deduplication test exists in the searched directory.

### U7 — Dual auth paths produce equivalent JWTs (Story 9.3 AC3)
**Doc claims:** "Both methods result in same JWT token (same claims, same session)"
**Risk:** Medium — equivalence between magic-link JWT and password JWT is untested.

### U8 — 7-day grace period for deprecated tokens (Story 9.4 AC3)
**Doc claims:** "Old token-based magic links marked as deprecated (still work for 7-day grace
period)"
**Risk:** Medium — no test verifies that legacy `speaker_tokens` still authenticate within the
grace window and are rejected after it.

### U9 — RS256 signing algorithm (Security Considerations)
**Doc claims:** "Tokens signed with RS256 (asymmetric encryption)" and "JWT signature
validation" listed in Security Tests
**Risk:** High — algorithm enforcement (rejecting HS256 or unsigned tokens) has no test
coverage in scope.

---

## UNDOCUMENTED

### N1 — Health endpoint returns UP
**Test:** `HealthControllerIntegrationTest#should_returnHealthStatus_when_healthEndpointCalled`
— asserts `GET /actuator/health` → HTTP 200, `$.status == "UP"`
**Action:** Not a business-rule gap, but Epic 9 doc has no mention of actuator/health SLA.
Low priority; add to a general "operational readiness" section if desired.

### N2 — Info endpoint returns 200
**Test:** `HealthControllerIntegrationTest#should_returnServiceInfo_when_infoEndpointCalled`
— asserts `GET /actuator/info` → HTTP 200
**Action:** Same as N1; operational test unrelated to Epic 9 business rules.

---

## VALIDATED
*(none — no Epic 9 business-rule tests exist in the searched scope)*
