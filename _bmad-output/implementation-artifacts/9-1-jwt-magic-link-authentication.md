# Story 9.1: JWT-Based Magic Link Authentication for Speaker Portal

Status: ready-for-dev

## Story

As a **speaker**,
I want to click a magic link that logs me in automatically with a JWT token,
so that I can access the speaker portal without creating a separate password.

## Acceptance Criteria

1. Magic link emails contain JWT tokens (RS256-signed, 30-day expiry, reusable)
2. Clicking magic link extracts JWT from URL query param, stores in HTTP-only cookie
3. Frontend redirects to speaker dashboard after successful JWT validation
4. JWT tokens support same 30-day reusability as Epic 6 tokens
5. Invalid/expired JWT tokens show clear error message with organizer contact info
6. JWT tokens include: `user_id`, `email`, `roles` (SPEAKER), expiration timestamp

## Tasks / Subtasks

- [ ] Task 1: Add `generateJwtToken()` to `MagicLinkService` (AC: 1, 6)
  - [ ] 1.1 Write failing unit tests for `generateJwtToken(UUID speakerPoolId)` in `MagicLinkServiceTest`
  - [ ] 1.2 Add JJWT library dependency to `event-management-service/build.gradle`
  - [ ] 1.3 Implement `generateJwtToken()` using RS256 with `user_id`, `email`, `roles` claims + 30-day expiry
  - [ ] 1.4 Add RSA key pair config to `application.yml` (PEM env vars or path refs)
  - [ ] 1.5 Verify tests pass (GREEN phase)
- [ ] Task 2: Create `POST /api/v1/auth/speaker-magic-login` endpoint (AC: 2, 3, 5)
  - [ ] 2.1 Write failing integration test for the endpoint in `SpeakerMagicLoginControllerTest extends AbstractIntegrationTest`
  - [ ] 2.2 Create `SpeakerMagicLoginController` in `services/event-management-service/src/main/java/ch/batbern/events/controller/`
  - [ ] 2.3 Create `SpeakerMagicLoginRequest` DTO (fields: `jwtToken: String`)
  - [ ] 2.4 Create `SpeakerAuthResponse` DTO (fields: `speakerPoolId`, `speakerName`, `eventCode`)
  - [ ] 2.5 Implement controller method: validate JWT claims, set HTTP-only cookie, return 200 with speaker context
  - [ ] 2.6 Handle invalid/expired JWT: return 401 with error message "Dieser Link ist nicht mehr gültig. Bitte kontaktiere den Organisator."
  - [ ] 2.7 Verify integration tests pass
- [ ] Task 3: Update `SecurityConfig` in API Gateway (AC: 2, 3)
  - [ ] 3.1 Add `permitAll()` for `POST /api/v1/auth/speaker-magic-login` to `api-gateway/SecurityConfig.java`
  - [ ] 3.2 KEEP existing `permitAll()` entries for `/api/v1/speaker-portal/**` unchanged — DO NOT require JWT here yet (Story 9.4 handles migration; changing this now breaks all existing Epic 6 token-based speakers)
  - [ ] 3.3 Write a test verifying the new auth endpoint is accessible without prior auth
- [ ] Task 4: Update invitation email to include JWT magic link (AC: 1)
  - [ ] 4.1 Open `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java`
  - [ ] 4.2 Add `magicLinkService` as constructor dependency to `SpeakerInvitationEmailService`
  - [ ] 4.3 Add call `magicLinkService.generateJwtToken(speakerPoolId)` to generate JWT before sending email (alongside existing `respondToken` + `dashboardToken` generation)
  - [ ] 4.4 In `buildEmailContent()` method, add new variable: `String jwtMagicLink = baseUrl + "/speaker-portal/magic-login?jwt=" + jwtToken;`
  - [ ] 4.5 Add `{{JWT_MAGIC_LINK}}` variable to both `email-templates/speaker-invitation-de.html` and `speaker-invitation-en.html` (existing token links stay for backward compat)
  - [ ] 4.6 Write test in `SpeakerInvitationEmailServiceTest` verifying new JWT link format is present in rendered email
  - Note: Existing `?token=` links (acceptLink, declineLink, dashboardLink) MUST remain in email for backward compat during grace period
- [ ] Task 5: Frontend - Handle magic link JWT flow (AC: 2, 3, 5)
  - [ ] 5.1 Create `SpeakerMagicLoginPage.tsx` at `web-frontend/src/pages/speaker-portal/SpeakerMagicLoginPage.tsx`
  - [ ] 5.2 Extract `?jwt=` query param from URL using `useSearchParams()`
  - [ ] 5.3 Call `speakerAuthService.validateMagicLink(jwt)` which POSTs to `/api/v1/auth/speaker-magic-login`
  - [ ] 5.4 On success: redirect to `/speaker/dashboard`
  - [ ] 5.5 On error: show error message with contact info (AC: 5)
  - [ ] 5.6 Create `speakerAuthService.ts` at `web-frontend/src/services/speakerAuthService.ts`
  - [ ] 5.7 Add route `/speaker-portal/magic-login` in app router pointing to `SpeakerMagicLoginPage`
  - [ ] 5.8 Write unit tests for `SpeakerMagicLoginPage` with Vitest
- [ ] Task 6: Update `SpeakerDashboardPage` to use cookie-based auth (AC: 3)
  - [ ] 6.1 Replace `useSearchParams()` for `?token=` with JWT cookie read in `SpeakerDashboardPage.tsx`
  - [ ] 6.2 Create `useSpeakerAuth.ts` hook at `web-frontend/src/hooks/useSpeakerAuth.ts`
  - [ ] 6.3 Update all speaker portal pages to use `useSpeakerAuth` instead of `?token=` params
  - [ ] 6.4 Write unit tests for `useSpeakerAuth` hook

## Dev Notes

### Architecture Overview

This story replaces Epic 6's anonymous token-based auth (`speaker_invitation_tokens` table / `?token=xxx` query params) with JWT-based authentication that creates actual user sessions.

**Key architectural principle**: Story 9.1 adds the JWT layer ALONGSIDE the existing token system (which continues to work until Story 9.4 migration). Do NOT remove old token endpoints in this story.

### What Currently Exists (Epic 6 Token System)

The current `MagicLinkService` at [services/event-management-service/src/main/java/ch/batbern/events/service/MagicLinkService.java](services/event-management-service/src/main/java/ch/batbern/events/service/MagicLinkService.java):
- `generateToken(UUID speakerPoolId, TokenAction action)` - generates 32-byte random opaque token, stores SHA-256 hash
- Token validated via `POST /api/v1/speaker-portal/validate-token`
- Current frontend at `SpeakerDashboardPage.tsx` uses `useSearchParams()` to get `?token=` from URL

**CRITICAL**: The existing `speaker_invitation_tokens` table and `?token=xxx` pattern MUST remain functional in Story 9.1. Do not break existing flows. Story 9.4 handles migration.

### New JWT Generation Approach

Story 9.1 adds `generateJwtToken(UUID speakerPoolId)` as a NEW method on `MagicLinkService`. This method:
1. Looks up `SpeakerPool` to get `username` (Cognito userId), speaker email, speaker name
2. Creates a signed JWT with claims: `sub` (user_id / speakerPoolId), `email`, `roles: ["SPEAKER"]`, `speakerPoolId`, `exp` (+30 days), `iss` ("batbern")
3. Signs with RS256 (RSA private key from app config)

**NOTE**: Since Cognito user accounts don't exist yet (that's Story 9.2), for Story 9.1 the JWT `sub` claim will use the `speakerPoolId` UUID as a temporary identifier. This is explicitly acceptable per Epic 9 scope. Full Cognito integration happens in Story 9.2.

### JWT Library

Use **JJWT** (Java JWT) version `0.12.x` (latest stable as of 2025):
```gradle
// event-management-service/build.gradle
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

For RSA key pair in tests, generate inline with `KeyPairGenerator.getInstance("RSA")`.

### API Design

**New endpoint** (to be added to `SpeakerMagicLoginController`):
```
POST /api/v1/auth/speaker-magic-login
Content-Type: application/json

{
  "jwtToken": "<base64url-encoded JWT>"
}

Response 200:
{
  "speakerPoolId": "uuid",
  "speakerName": "Jane Doe",
  "eventCode": "BAT-2025",
  "eventTitle": "BATbern 2025"
}
Set-Cookie: speaker_jwt=<jwt>; HttpOnly; Secure; SameSite=Strict; Max-Age=2592000

Response 401 (invalid/expired JWT):
{
  "error": "INVALID_TOKEN",
  "message": "Dieser Link ist nicht mehr gültig. Bitte kontaktiere den Organisator."
}
```

**Magic link URL format** (used in invitation email):
```
https://www.batbern.ch/speaker-portal/magic-login?jwt=<signed-jwt>
```

### Source Tree Components to Touch

**Backend** (`services/event-management-service/`):
- `src/main/java/ch/batbern/events/service/MagicLinkService.java` — add `generateJwtToken()` method
- `src/main/java/ch/batbern/events/controller/` — create new `SpeakerMagicLoginController.java`
- `src/main/java/ch/batbern/events/dto/` — create `SpeakerMagicLoginRequest.java` and `SpeakerAuthResponse.java`
- `src/main/java/ch/batbern/events/config/` — create `JwtConfig.java` for RSA key loading
- `src/main/resources/application.yml` — add `app.jwt.private-key-path` and `app.jwt.public-key-path`
- `build.gradle` — add JJWT dependencies

**API Gateway** (`api-gateway/`):
- `src/main/java/ch/batbern/gateway/config/SecurityConfig.java` — add `permitAll()` for new auth endpoint

**Frontend** (`web-frontend/src/`):
- `pages/speaker-portal/SpeakerMagicLoginPage.tsx` — NEW: handles JWT URL extraction and validation
- `services/speakerAuthService.ts` — NEW: API client for auth endpoints
- `hooks/useSpeakerAuth.ts` — NEW: Zustand-based hook for speaker auth state
- `pages/speaker-portal/SpeakerDashboardPage.tsx` — MODIFY: remove `?token=` usage, use cookie auth
- App router — add `/speaker-portal/magic-login` route

**Email template** (exact file to modify):
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java`
  - Currently builds: `dashboardLink = baseUrl + "/speaker-portal/dashboard?token=" + dashboardToken`
  - Add: `jwtMagicLink = baseUrl + "/speaker-portal/magic-login?jwt=" + jwtToken`
  - Keep existing token links (backward compat grace period)
- `services/event-management-service/src/main/resources/email-templates/speaker-invitation-de.html`
- `services/event-management-service/src/main/resources/email-templates/speaker-invitation-en.html`

### SecurityConfig: CRITICAL DUAL-UPDATE REQUIREMENT

Per `MEMORY.md`: **MUST add new speaker-portal endpoints to BOTH `api-gateway` AND `event-management-service` SecurityConfig.**

**API Gateway** (`api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java`):
```java
// Story 9.1: Speaker JWT magic link authentication endpoint
.requestMatchers(HttpMethod.POST, "/api/v1/auth/speaker-magic-login").permitAll()
```

The existing `permitAll()` entries for `/api/v1/speaker-portal/**` endpoints MUST remain unchanged in Story 9.1. Changing them to require JWT would immediately break all existing Epic 6 token-based speakers who haven't migrated yet. Full API Gateway JWT enforcement on `/api/v1/speaker-portal/**` is a Story 9.4 task (after migration). For Story 9.1, the JWT cookie is validated at the service/controller layer inside `event-management-service`, not at the API Gateway layer.

### Testing Standards

**Unit tests** for `MagicLinkService.generateJwtToken()`:
```java
// Location: services/event-management-service/src/test/java/ch/batbern/events/service/MagicLinkServiceTest.java
// Pattern: should_expectedBehavior_when_condition
void should_generateJwt_when_validSpeakerPoolId() {}
void should_includeCorrectClaims_when_jwtGenerated() {}
void should_expireAfter30Days_when_jwtGenerated() {}
```

**Integration tests** must extend `AbstractIntegrationTest`:
```java
// Location: services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerMagicLoginControllerTest.java
@Transactional
class SpeakerMagicLoginControllerTest extends AbstractIntegrationTest {
    @BeforeEach
    void setUp() {
        // Clean repos in FK order per MEMORY.md
        tokenRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();
        // Use System.currentTimeMillis() for unique IDs per MEMORY.md
    }
}
```

**Frontend tests** use Vitest + React Testing Library:
```typescript
// Pattern: test('should redirect to dashboard when valid magic link clicked')
// Location: web-frontend/src/pages/speaker-portal/__tests__/SpeakerMagicLoginPage.test.tsx
```

### Cookie Security Requirements

HTTP-only cookie settings (per Epic 9 security requirements + `08-operations-security.md`):
- `HttpOnly` — prevents JavaScript XSS access
- `Secure` — HTTPS-only (disable for local dev profile only)
- `SameSite=Strict` — prevents CSRF
- `Max-Age=2592000` — 30 days in seconds (= 30 × 24 × 60 × 60)
- `Path=/` — accessible on all paths

### Frontend API Client Pattern

The `apiClient` baseURL is `http://localhost:8080/api/v1` — paths MUST EXCLUDE the `/api/v1` prefix. Follow the exact pattern from `web-frontend/src/services/speakerPortalService.ts`:
```typescript
// services/speakerAuthService.ts
import apiClient from '@/services/api/apiClient'; // same import as speakerPortalService.ts
const AUTH_API_PATH = '/auth'; // NOT '/api/v1/auth'

export const speakerAuthService = {
  validateMagicLink: (jwtToken: string) =>
    apiClient.post<SpeakerAuthResponse>(`${AUTH_API_PATH}/speaker-magic-login`, { jwtToken }),
};
```

Use the existing `apiClient` instance (same pattern as `speakerPortalService.ts`). Never use direct `fetch()`.

### Project Structure Notes

- Alignment with unified project structure (paths, modules, naming) ✅
- New controller follows existing pattern: `ch.batbern.events.controller.SpeakerMagicLoginController`
- New DTOs follow existing pattern: `ch.batbern.events.dto.SpeakerMagicLoginRequest`, `SpeakerAuthResponse`
- Frontend components: PascalCase (`SpeakerMagicLoginPage.tsx`), hooks: camelCase with `use` prefix (`useSpeakerAuth.ts`)
- No new DB tables needed in this story

### References

- Epic 9 full specification: [Source: docs/prd/epic-9-speaker-authentication.md]
- Existing `MagicLinkService`: [Source: services/event-management-service/src/main/java/ch/batbern/events/service/MagicLinkService.java]
- API Gateway SecurityConfig patterns: [Source: docs/architecture/06-backend-architecture.md]
- JWT auth claim extraction pattern (`custom:role`): [Source: docs/architecture/06-backend-architecture.md]
- SecurityConfig dual-update rule: [Source: MEMORY.md — "SecurityConfig dual update"]
- `AbstractIntegrationTest` base class: [Source: services/event-management-service/src/test/java/ch/batbern/events/AbstractIntegrationTest.java]
- Frontend API client pattern: [Source: docs/architecture/05-frontend-architecture.md]
- ADR-001 — Invitation-Based User Registration: [Source: docs/architecture/ADR-001-invitation-based-user-registration.md]
- ADR-007 — Unified User Profile: [Source: docs/architecture/ADR-007-unified-user-profile.md]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-5-20250929 (via create-story workflow)

### Debug Log References

### Completion Notes List

- Story created via create-story workflow on 2026-02-17
- Story 9.1 is the foundation for Epic 9 — JWT must be working before Stories 9.2-9.5 can proceed
- Implementation uses JJWT 0.12.x for JWT generation/validation (latest stable as of Jan 2025)
- Existing `speaker_invitation_tokens` table and `?token=xxx` pattern MUST remain functional (Story 9.4 handles migration/deprecation)
- Story 9.2 (Cognito user creation) is blocked on Story 9.1 completion
- Pre-implementation condition: Add AC for updated invitation email template with JWT URL (flagged by Implementation Readiness Report 2026-02-16, Major Issue M4)

### File List
