# Story 10.31: Bot Protection — Cloudflare Turnstile for Newsletter & Event Registration

Status: ready-for-dev

## Story

As a **platform operator**,
I want newsletter subscriptions and event registrations protected by Cloudflare Turnstile,
so that bot submissions are rejected before they reach domain services, without degrading the UX for legitimate users.

## Acceptance Criteria

1. **AC1**: A `TurnstileVerificationFilter` in `api-gateway` intercepts `POST /api/v1/newsletter/subscribe` and `POST /api/v1/events/*/registrations` when `turnstile.enabled=true`; all other endpoints are unaffected.
2. **AC2**: Requests missing the `X-Turnstile-Token` header on protected endpoints return `403` with a JSON error body.
3. **AC3**: Requests with an invalid token (Cloudflare returns `success: false`) return `403`.
4. **AC4**: When Cloudflare's siteverify endpoint is unreachable the filter logs a warning and lets the request through (fail-open).
5. **AC5**: When `turnstile.enabled=false` (default) the filter is a no-op — all existing behaviour is preserved.
6. **AC6**: `GET /api/v1/config` response includes `features.turnstile: boolean` and, when enabled, `turnstile.siteKey: String`.
7. **AC7**: A `useTurnstile` React hook loads the Turnstile widget from CDN (no npm package), exposes `{ getToken, resetWidget, widgetRef }`, and returns `getToken() → null` when disabled.
8. **AC8**: `NewsletterSubscribeWidget` calls `getToken()` before the subscribe mutation and passes the result via the `X-Turnstile-Token` header.
9. **AC9**: `RegistrationWizard` (public) calls `getToken()` before `createRegistration()` and passes the result via the `X-Turnstile-Token` header.
10. **AC10**: On `403` with body containing `turnstile_required` or `turnstile_failed`, both forms show a user-friendly error message and call `resetWidget()`.
11. **AC11**: All new backend code has unit tests; `TurnstileVerificationFilterTest` covers disabled, non-protected, missing-header, valid, invalid, and unreachable scenarios.
12. **AC12**: All new frontend code has vitest unit tests; `useTurnstile.test.ts` covers disabled and enabled paths; component tests verify token header is sent.
13. **AC13**: `npm run build` passes; `./gradlew :api-gateway:test` passes; existing tests pass.

## Tasks / Subtasks

### Phase 1: Backend Config Layer (AC: 6)

- [ ] Task 1: Create `TurnstileProperties` config bean
  - [ ] 1.1 Create `api-gateway/src/main/java/ch/batbern/gateway/config/TurnstileProperties.java` — `@ConfigurationProperties(prefix = "turnstile")` with fields: `boolean enabled`, `String siteKey`, `String secretKey`, `String verifyUrl`, `List<String> protectedEndpoints`
  - [ ] 1.2 Create `api-gateway/src/main/java/ch/batbern/gateway/config/dto/TurnstileConfigDTO.java` — record/POJO with `String siteKey`
  - [ ] 1.3 Add `@EnableConfigurationProperties(TurnstileProperties.class)` to the main application class or a dedicated `@Configuration` class
  - [ ] 1.4 Add to `api-gateway/src/main/resources/application.yml`:
    ```yaml
    turnstile:
      enabled: ${TURNSTILE_ENABLED:false}
      site-key: ${TURNSTILE_SITE_KEY:}
      secret-key: ${TURNSTILE_SECRET_KEY:}
      verify-url: https://challenges.cloudflare.com/turnstile/v0/siteverify
      protected-endpoints:
        - POST:/api/v1/newsletter/subscribe
        - POST:/api/v1/events/*/registrations
    ```

- [ ] Task 2: Extend `FrontendConfigDTO` and `ConfigController` (AC: 6)
  - [ ] 2.1 Add `boolean turnstile` to `api-gateway/src/main/java/ch/batbern/gateway/config/dto/FeatureFlagsDTO.java` (alongside `notifications`, `analytics`, `pwa`)
  - [ ] 2.2 Add `TurnstileConfigDTO turnstile` field to `api-gateway/src/main/java/ch/batbern/gateway/config/dto/FrontendConfigDTO.java`
  - [ ] 2.3 In `api-gateway/src/main/java/ch/batbern/gateway/config/ConfigController.java`: inject `TurnstileProperties turnstileProperties`, conditionally set `features.turnstile` and `turnstile.siteKey` (only include siteKey when enabled — never expose secretKey)

### Phase 2: Backend Verification Filter (AC: 1–5, 11)

- [ ] Task 3: Implement `TurnstileVerificationFilter` (AC: 1–5)
  - [ ] 3.1 Create `api-gateway/src/main/java/ch/batbern/gateway/security/TurnstileVerificationFilter.java`
    - `@Component`, `@Order(Ordered.LOWEST_PRECEDENCE - 1)` (runs before `RateLimitingFilter` which is `@Order(Ordered.LOWEST_PRECEDENCE)`)
    - Inject `TurnstileProperties`, `RestTemplate` or `RestClient`
    - `doFilter`: if not enabled → `chain.doFilter` (AC5); skip OPTIONS (CORS preflight)
    - Match `request.getMethod() + ":" + request.getRequestURI()` against `protectedEndpoints` using `AntPathMatcher` (Spring's `org.springframework.util.AntPathMatcher`)
    - If no match → `chain.doFilter`
    - Extract `X-Turnstile-Token` header; if absent → return 403 JSON `{"error":"turnstile_required","message":"Turnstile token required"}` (AC2)
    - POST to `turnstile.verifyUrl` with form params `secret={secretKey}&response={token}&remoteip={clientIp}`
    - Parse response JSON: if `success: true` → `chain.doFilter` (AC3)
    - If `success: false` → return 403 JSON `{"error":"turnstile_failed","message":"Bot protection check failed. Please try again."}` (AC3)
    - Catch `Exception` → log warning + `chain.doFilter` (fail-open, AC4)
    - Include `addCorsHeaders` call before all 403 returns (copy pattern from `RateLimitingFilter.addCorsHeaders`)

- [ ] Task 4: Write `TurnstileVerificationFilterTest` (AC: 11)
  - [ ] 4.1 Create `api-gateway/src/test/java/ch/batbern/gateway/security/TurnstileVerificationFilterTest.java`
  - [ ] 4.2 Test: disabled → filter passes all requests through
  - [ ] 4.3 Test: non-protected endpoint → filter passes through
  - [ ] 4.4 Test: missing `X-Turnstile-Token` → 403 with `turnstile_required`
  - [ ] 4.5 Test: valid token (mock REST call returning `{"success":true}`) → passes through
  - [ ] 4.6 Test: invalid token (mock REST call returning `{"success":false}`) → 403 with `turnstile_failed`
  - [ ] 4.7 Test: Cloudflare unreachable (mock throws exception) → passes through (fail-open)
  - [ ] 4.8 Use `MockHttpServletRequest`, `MockHttpServletResponse`, `MockFilterChain` from `spring-test`

### Phase 3: Frontend Hook (AC: 7, 12)

- [ ] Task 5: Implement `useTurnstile` hook (AC: 7)
  - [ ] 5.1 Create `web-frontend/src/hooks/useTurnstile/useTurnstile.ts`
    - Read `useConfig()` for `features.turnstile` and `turnstile?.siteKey`
    - Export `{ getToken: () => Promise<string | null>, resetWidget: () => void, widgetRef: React.RefObject<HTMLDivElement> }`
    - When disabled: `getToken()` returns `Promise.resolve(null)`; `widgetRef` ref is provided but mounting it does nothing harmful
    - When enabled:
      - On mount: dynamically inject `<script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit" async defer>` if not already loaded
      - After script load: call `window.turnstile.render(widgetRef.current, { sitekey, size: 'invisible', callback: resolve })` to render invisible widget
      - `getToken()`: calls `window.turnstile.execute(widgetId)` and returns a Promise that resolves with the token via the callback
      - `resetWidget()`: calls `window.turnstile.reset(widgetId)`
    - No npm dependencies — use `window.turnstile` global (declare type: `declare global { interface Window { turnstile: ... } }`)
  - [ ] 5.2 Create `web-frontend/src/hooks/useTurnstile/index.ts` barrel export
  - [ ] 5.3 Create `web-frontend/src/hooks/useTurnstile/useTurnstile.test.ts`
    - Mock `useConfig()` (via `vi.mock('@/contexts/useConfig')`)
    - Test disabled path: `getToken()` resolves `null`
    - Test enabled path: mock script injection + `window.turnstile`; verify token returned

### Phase 4: Frontend Form Integration (AC: 8–10, 12)

- [ ] Task 6: Update `AppConfig` type (AC: 6, 7)
  - [ ] 6.1 In `web-frontend/src/config/runtime-config.ts`: extend `AppConfig`:
    ```typescript
    features: {
      notifications: boolean;
      analytics: boolean;
      pwa: boolean;
      turnstile: boolean;   // ADD
    };
    turnstile?: {            // ADD — optional, only present when enabled
      siteKey: string;
    };
    ```
  - [ ] 6.2 Update `validateConfig` to handle optional `turnstile` block (do not throw if absent)
  - [ ] 6.3 Update `getDefaultDevelopmentConfig()` to add `features.turnstile: false`

- [ ] Task 7: Update `newsletterService.ts` (AC: 8)
  - [ ] 7.1 In `web-frontend/src/services/newsletterService.ts`, update `subscribe()` signature:
    ```typescript
    export async function subscribe(
      request: NewsletterSubscribeRequest,
      turnstileToken?: string | null
    ): Promise<void>
    ```
  - [ ] 7.2 Pass `X-Turnstile-Token` header when token is non-null:
    ```typescript
    await apiClient.post('/newsletter/subscribe', request, {
      headers: turnstileToken ? { 'X-Turnstile-Token': turnstileToken } : {},
    });
    ```

- [ ] Task 8: Update `eventApiClient.ts` `createRegistration` (AC: 9)
  - [ ] 8.1 In `web-frontend/src/services/eventApiClient.ts`, update `createRegistration()` signature:
    ```typescript
    async createRegistration(
      eventCode: string,
      data: CreateRegistrationRequest,
      turnstileToken?: string | null
    ): Promise<{ message: string; email: string }>
    ```
  - [ ] 8.2 Pass header when token is non-null (same pattern as above)

- [ ] Task 9: Integrate `useTurnstile` into `NewsletterSubscribeWidget` (AC: 8, 10)
  - [ ] 9.1 In `web-frontend/src/components/public/NewsletterSubscribeWidget.tsx`:
    - Add `useTurnstile()` call; place `<div ref={widgetRef} />` inside form (invisible, zero-size)
    - Before calling `subscribe`, call `getToken()` and pass result
    - Catch `403` with `turnstile_required`/`turnstile_failed` → set error state + call `resetWidget()`
  - [ ] 9.2 Update `useNewsletter.ts` (`useNewsletterSubscribe` mutation) to accept and forward the `turnstileToken` argument to `newsletterService.subscribe()`
  - [ ] 9.3 Update `web-frontend/src/components/public/__tests__/NewsletterSubscribeWidget.test.tsx` to verify `X-Turnstile-Token` header behaviour

- [ ] Task 10: Integrate `useTurnstile` into `RegistrationWizard` (AC: 9, 10)
  - [ ] 10.1 In `web-frontend/src/components/public/Registration/RegistrationWizard.tsx`:
    - Add `useTurnstile()` call; place `<div ref={widgetRef} />` near the Step 2 submit area (invisible)
    - Before calling `eventApiClient.createRegistration()`, call `getToken()`
    - Catch `403` turnstile errors → show error snackbar + call `resetWidget()`
  - [ ] 10.2 Update `web-frontend/src/components/public/Registration/__tests__/RegistrationWizard.test.tsx` to verify token header behaviour

### Phase 5: Environment Activation (AC: 5, 13)

- [ ] Task 11: Infrastructure / CDK env vars
  - [ ] 11.1 Verify development default is `TURNSTILE_ENABLED=false` (already in `application.yml` default)
  - [ ] 11.2 Add staging env vars to CDK / ECS task definition for api-gateway: `TURNSTILE_ENABLED=true`, `TURNSTILE_SITE_KEY=1x00000000000000000000AA`, `TURNSTILE_SECRET_KEY=1x0000000000000000000000000000000AA` (Cloudflare always-pass test keys)
  - [ ] 11.3 Add production env vars to CDK secrets / parameter store (real keys from Cloudflare dashboard)
  - [ ] 11.4 Update `.github/workflows/deploy-staging.yml` or CDK stack to include the new env vars

- [ ] Task 12: Final verification
  - [ ] 12.1 Run `./gradlew :api-gateway:test` — all tests pass including `TurnstileVerificationFilterTest`
  - [ ] 12.2 Run `cd web-frontend && npm test` — all unit tests pass
  - [ ] 12.3 Run `npm run build` — no TypeScript errors
  - [ ] 12.4 Verify with `TURNSTILE_ENABLED=false` (local): newsletter + registration still work as before

## Dev Notes

### Architecture Pattern

`TurnstileVerificationFilter` follows the **exact same pattern** as `RateLimitingFilter`:
- File: `api-gateway/src/main/java/ch/batbern/gateway/security/RateLimitingFilter.java`
- Both are `@Component` + `@Order` + implement `jakarta.servlet.Filter`
- Both handle CORS headers on error responses (copy `addCorsHeaders` + `isOriginAllowed` methods)
- Use `@Order(Ordered.LOWEST_PRECEDENCE - 1)` so Turnstile filter runs first (before rate limit)

### Backend Config Pattern

- `TurnstileProperties` → `@ConfigurationProperties(prefix = "turnstile")` — no `@Value` annotations
- Add `@EnableConfigurationProperties(TurnstileProperties.class)` to a `@Configuration` class (or the main `@SpringBootApplication` class)
- Existing pattern in `api-gateway/src/main/java/ch/batbern/gateway/config/CognitoConfig.java` shows how config properties are structured

### Frontend Config Extension

`AppConfig` in `runtime-config.ts` currently has:
```typescript
features: { notifications, analytics, pwa }
```
Extend to:
```typescript
features: { notifications, analytics, pwa, turnstile }
turnstile?: { siteKey: string }
```
`validateConfig` does NOT need to assert `turnstile` presence (optional block).  
`getDefaultDevelopmentConfig()` must add `features.turnstile: false` to avoid runtime errors.

### Frontend Service Layer

The `subscribe()` function in `newsletterService.ts` currently calls `apiClient.post('/newsletter/subscribe', request)` with no headers. The `apiClient` is an axios instance with auth interceptors. To add the Turnstile header, use the third argument (config):
```typescript
await apiClient.post('/newsletter/subscribe', request, {
  headers: turnstileToken ? { 'X-Turnstile-Token': turnstileToken } : {},
});
```

Same pattern for `eventApiClient.ts` `createRegistration()` — lines 288–301.

### Frontend Hook: window.turnstile type

Add a minimal `window.turnstile` interface in `useTurnstile.ts`:
```typescript
declare global {
  interface Window {
    turnstile: {
      render: (el: HTMLElement, opts: object) => string;
      execute: (widgetId: string) => void;
      reset: (widgetId: string) => void;
    };
  }
}
```

### useNewsletter update

`web-frontend/src/hooks/useNewsletter/useNewsletter.ts` contains `useNewsletterSubscribe` — a `useMutation` wrapping `newsletterService.subscribe`. Update the mutation variables type to include optional `turnstileToken?: string | null` and forward it.

### Endpoint Pattern Matching

Use `org.springframework.util.AntPathMatcher` in the filter:
```java
private final AntPathMatcher pathMatcher = new AntPathMatcher();

private boolean isProtectedEndpoint(String method, String uri) {
    return turnstileProperties.getProtectedEndpoints().stream()
        .anyMatch(pattern -> {
            String[] parts = pattern.split(":", 2);
            return parts[0].equalsIgnoreCase(method)
                && pathMatcher.match(parts[1], uri);
        });
}
```
This handles both exact paths (`/api/v1/newsletter/subscribe`) and wildcards (`/api/v1/events/*/registrations`).

### Error Response Format

403 responses from the filter must be JSON (same as rate limit filter):
```java
httpResponse.setStatus(403);
httpResponse.setContentType("application/json");
httpResponse.getWriter().write("{\"error\":\"turnstile_required\",\"message\":\"...\"}");
```

### Cloudflare Turnstile Siteverify API

URL: `https://challenges.cloudflare.com/turnstile/v0/siteverify`  
Method: `POST`, Content-Type: `application/x-www-form-urlencoded`  
Body params: `secret={secretKey}&response={token}&remoteip={clientIp}`  
Success response: `{"success": true, "hostname": "...", "challenge_ts": "..."}`  
Failure response: `{"success": false, "error-codes": ["invalid-input-response"]}`

Use `RestTemplate` (already available in Spring Boot, no new dependency needed).

### Cloudflare Test Keys (always-pass, safe for staging)

- Site Key: `1x00000000000000000000AA`
- Secret Key: `1x0000000000000000000000000000000AA`

These always return `success: true` — safe for CI/CD without real Cloudflare account.

### Security Note

**Never expose `secretKey`** in the frontend config response. Only `siteKey` is safe for public exposure. `ConfigController` must only include `turnstile.siteKey` (not the secret).

### Files NOT to Change

- `OpenAPI` specs — token travels via header, not request body → no spec changes needed
- Domain service controllers — gateway filter handles everything; domain services are unaware
- `SecurityConfig.java` — existing security filter chain is unchanged

### Project Structure Notes

- New backend files go in `api-gateway/src/main/java/ch/batbern/gateway/`:
  - `config/TurnstileProperties.java`
  - `config/dto/TurnstileConfigDTO.java`
  - `security/TurnstileVerificationFilter.java`
- New test goes in `api-gateway/src/test/java/ch/batbern/gateway/security/TurnstileVerificationFilterTest.java`
- New frontend hook goes in `web-frontend/src/hooks/useTurnstile/`

### References

- [Source: RateLimitingFilter] `api-gateway/src/main/java/ch/batbern/gateway/security/RateLimitingFilter.java` — @Order, doFilter, CORS, JSON error body pattern
- [Source: ConfigController] `api-gateway/src/main/java/ch/batbern/gateway/config/ConfigController.java` — feature flags pattern, @Value injection
- [Source: FeatureFlagsDTO] `api-gateway/src/main/java/ch/batbern/gateway/config/dto/FeatureFlagsDTO.java` — add `boolean turnstile`
- [Source: FrontendConfigDTO] `api-gateway/src/main/java/ch/batbern/gateway/config/dto/FrontendConfigDTO.java` — add `TurnstileConfigDTO turnstile`
- [Source: runtime-config.ts] `web-frontend/src/config/runtime-config.ts` — `AppConfig` interface, extend features + add turnstile block
- [Source: newsletterService.ts] `web-frontend/src/services/newsletterService.ts#L80` — `subscribe()` function, add `turnstileToken` param
- [Source: eventApiClient.ts] `web-frontend/src/services/eventApiClient.ts#L288` — `createRegistration()` function, add `turnstileToken` param
- [Source: NewsletterSubscribeWidget] `web-frontend/src/components/public/NewsletterSubscribeWidget.tsx`
- [Source: RegistrationWizard] `web-frontend/src/components/public/Registration/RegistrationWizard.tsx`
- [Source: useNewsletter] `web-frontend/src/hooks/useNewsletter/useNewsletter.ts`
- [Source: GH Issue 582] Bot Protection: Cloudflare Turnstile for Newsletter & Event Registration

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

**New files:**
- `api-gateway/src/main/java/ch/batbern/gateway/config/TurnstileProperties.java`
- `api-gateway/src/main/java/ch/batbern/gateway/config/dto/TurnstileConfigDTO.java`
- `api-gateway/src/main/java/ch/batbern/gateway/security/TurnstileVerificationFilter.java`
- `api-gateway/src/test/java/ch/batbern/gateway/security/TurnstileVerificationFilterTest.java`
- `web-frontend/src/hooks/useTurnstile/useTurnstile.ts`
- `web-frontend/src/hooks/useTurnstile/index.ts`
- `web-frontend/src/hooks/useTurnstile/useTurnstile.test.ts`

**Modified files:**
- `api-gateway/src/main/java/ch/batbern/gateway/config/dto/FeatureFlagsDTO.java`
- `api-gateway/src/main/java/ch/batbern/gateway/config/dto/FrontendConfigDTO.java`
- `api-gateway/src/main/java/ch/batbern/gateway/config/ConfigController.java`
- `api-gateway/src/main/resources/application.yml`
- `web-frontend/src/config/runtime-config.ts`
- `web-frontend/src/services/newsletterService.ts`
- `web-frontend/src/services/eventApiClient.ts`
- `web-frontend/src/hooks/useNewsletter/useNewsletter.ts`
- `web-frontend/src/components/public/NewsletterSubscribeWidget.tsx`
- `web-frontend/src/components/public/Registration/RegistrationWizard.tsx`
- `web-frontend/src/components/public/__tests__/NewsletterSubscribeWidget.test.tsx`
- `web-frontend/src/components/public/Registration/__tests__/RegistrationWizard.test.tsx`
- CDK / ECS task definition for api-gateway (staging + production env vars)
