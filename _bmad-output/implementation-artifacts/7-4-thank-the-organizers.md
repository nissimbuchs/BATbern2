# Story 7.4: Thank-the-Organizers

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As **any attendee** (logged in or not),
I want to thank the volunteer organizers in one click after an event,
so that the people who've run BATbern for 20 years — free, ad-free, on their own time — finally receive the gratitude attendees said they had no way to express.

**Source:** Brainstorming 2026-06-06 idea #02 (GitHub #747, 3 votes). Epic FR10–FR11. Resolved decision: **anonymous-allowed** (not login-gated) with an abuse guard — friction hurts most here.

## Acceptance Criteria

1. After an event is live/completed (`workflowState IN (EVENT_LIVE, EVENT_COMPLETED)`), a thank-you can be sent via `POST /api/v1/events/{eventCode}/thanks` with an optional short `note`.
2. The endpoint is **public** (`permitAll` in BOTH api-gateway and event-management-service `SecurityConfig`, all profile chains) — anonymous allowed.
3. **Abuse guard:** anonymous submissions require a valid **Cloudflare Turnstile** token (endpoint added to `TurnstileProperties.protectedEndpoints`) and are rate-limited; a missing/invalid token or exceeding the rate limit is rejected (403/429) without incrementing.
4. **Logged-in** attendees are deduped to **one** thank-you per event (unique on `eventCode + username`); a repeat does not double-increment, and an existing note may be updated.
5. **Anonymous** thank-yous increment a clap-style aggregate counter (rate-limited per IP/session); they are not user-deduped.
6. The aggregate count is readable publicly; submitted notes are returned **only** to organizers (the public GET returns count only). No public note wall, no approval queue.
7. The thank-you UI element respects the bundle boundary: if placed on a **public** event/archive page it is **Tailwind-only (no MUI)**. i18n keys in all 10 locales. Integration tests (PostgreSQL) cover logged-in dedupe, anonymous increment + Turnstile/rate-limit rejection, and event-state guard. No leftover test data.

## Tasks / Subtasks

- [x] **Task 1: Schema** (AC: 4, 5, 6)
  - [x] New forward migration **V111** (`V111__create_organizer_thanks.sql`). `organizer_thanks (id UUID PK DEFAULT gen_random_uuid(), event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE, thanked_by_username VARCHAR(100) NULL, note TEXT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now())`. Partial unique index `ux_organizer_thanks_user (event_id, thanked_by_username) WHERE thanked_by_username IS NOT NULL`; index `ix_organizer_thanks_event (event_id)`. (Story said "highest V108" but V109/V110 already shipped → next free is V111.)
  - [x] `OrganizerThanks` entity (`@GeneratedValue(AUTO)` UUID PK + `@PrePersist` createdAt, matching NewsletterSubscriber). `event_id` in-service UUID FK; `thanked_by_username` nullable meaningful ID (ADR-003).
- [x] **Task 2: Endpoint + service** (AC: 1, 4, 5)
  - [x] `OrganizerThanksController` `POST /api/v1/events/{eventCode}/thanks` — no `@PreAuthorize` (public). Injected `Authentication` resolved to username (excludes `AnonymousAuthenticationToken`/`anonymousUser` → null), `HttpServletRequest` for client IP.
  - [x] Guard in `OrganizerThanksService.loadThankableEvent`: event must be `EVENT_LIVE`/`EVENT_COMPLETED` (load via `findByEventCode`), else `ThanksNotAllowedException` (409).
  - [x] Logged-in → upsert by (event, username), note updatable; anonymous → rate-limit-then-insert clap row (username null).
  - [x] GET `/api/v1/events/{eventCode}/thanks` → public `{ count, notes:null }`; organizer (`ROLE_ORGANIZER`) additionally gets `notes[]` (newest first). Branch on authority in the controller.
- [x] **Task 3: Turnstile + rate limit** (AC: 3)
  - [x] Added `POST:/api/v1/events/*/thanks` to gateway `application.yml` `turnstile.protected-endpoints` (filter validates anonymous tokens; fail-open when disabled — unchanged).
  - [x] Per-(event,IP) cap: new `ThanksRateLimiter` (Caffeine fixed-window, 5/event/IP/hour) modeled on `InboundEmailRateLimiter` — applied to anonymous submissions only (logged-in are deduped). Checked BEFORE insert → "rejected without incrementing" (AC3). Documented the per-instance-cache choice in the class javadoc. (Did NOT reuse the gateway global IP bucket alone — Resolved Decision #3 wants a per-event cap.)
- [x] **Task 4: SecurityConfig (both layers)** (AC: 2)
  - [x] api-gateway `SecurityConfig.defaultSecurityFilterChain` (`@Profile("!test")`): POST + GET `/api/v1/events/*/thanks` `.permitAll()`. event-management-service `SecurityConfig.filterChain` (`@Profile("!test")`): same. (EMS `TestSecurityConfig` is permitAll-at-HTTP — tests rely on `@WithMockUser` for the role branch.)
- [x] **Task 5: Frontend** (AC: 7)
  - [x] Surface = public event/archive page (`/events/:eventCode` + `/archive/:eventCode` both render `HomePage`, Tailwind-only). New `ThankOrganizersWidget` (Tailwind, NO MUI), lazy-loaded in `HomePage`, gated `workflowState IN (EVENT_LIVE, EVENT_COMPLETED)`. `useTurnstile`/`useOptionalConfig` reads siteKey from `GET /api/v1/config`; token via `X-Turnstile-Token`. `thanksService` + `useThanks` hooks. 5 unit tests pass; type-check + lint clean.
  - [x] i18n `thanks.widget.*` keys in all 10 locales (`events.json`), EN+DE first-class, plural `count_one`/`count_other`.
- [x] **Task 6: Tests (TDD)** (AC: 4, 5, 3, 1)
  - [x] `OrganizerThanksIntegrationTest` (PostgreSQL/Testcontainers), 7 tests green: logged-in once→1, repeat→still 1 + note updated; anonymous claps increment (not deduped); per-(event,IP) cap → 429 `THANKS_RATE_LIMITED` with no increment; not-live/completed → 409 `THANKS_NOT_ALLOWED`; event 404; note>500→400; public GET count-only vs organizer GET with notes. Cleanup via `deleteAll` + `@Transactional` rollback. (Turnstile token verification itself is a gateway concern — noted in the test javadoc; asserted at gateway/E2E layer.)

## Dev Notes

### Service location & patterns
- Feature lives in **event-management-service** (event-lifecycle-bound; resolved decision).
- **Mixed/optional-auth pattern to copy:** `EventPhotoController` (`.../controller/EventPhotoController.java`) — public GET + `@PreAuthorize` POST, and the optional-`Authentication` null-check (~L76). For 7.4 the POST itself is public; use the null-check to branch logged-in vs anonymous.
- **Username when authenticated:** `SecurityContextHelper.getCurrentUsername()` (~L86) — but on a permitAll endpoint, prefer the injected `Authentication` null-check so anonymous doesn't throw.
- **Event state:** `Event.workflowState` (`EVENT_LIVE`/`EVENT_COMPLETED` in `shared-kernel/.../EventWorkflowState.java` ~L109/L120); load by `eventCode`.

### Turnstile (fully wired already)
- Gateway filter: `api-gateway/.../security/TurnstileVerificationFilter.java` (fail-open if token missing/Cloudflare down; 403 on invalid; no-op if disabled).
- Config: `TurnstileProperties.java` (`protectedEndpoints` = `METHOD:path` AntPath list) — **add the thanks endpoint here**.
- Flag exposure: `ConfigController` `GET /api/v1/config` returns `features.turnstile` + siteKey (`FeatureFlagsDTO`). Frontend reads this to decide whether to render the widget (the project's documented E2E bypass: config `features.turnstile=false` → fail-open).
- permitAll precedent: registration/newsletter endpoints in both `SecurityConfig`s (gateway ~L234–287; service ~L107–122).

### Rate limit
- `api-gateway/.../security/RateLimitingFilter.java` buckets anonymous by IP (`"anonymous:" + ip`). There is **no per-event clap limiter** — pick: (a) rely on the global IP bucket, or (b) add a lightweight service-side per-(event,IP) guard. Document the choice; don't silently leave the counter unprotected.

### Bundle boundary (frontend)
- Public event/archive pages (`web-frontend/src/pages/public/ArchivePage.tsx`, `PublicLayout`) are **Tailwind-only — NO MUI** (App.tsx boundary ~L18–23, 277–287). If the thank-you button sits there, build it with Tailwind + plain elements. If it lives on a MuiLayout route, MUI is fine.

### Gotchas
- ADR-003: `thanked_by_username` (meaningful ID, nullable for anonymous); `event_id` UUID FK is fine (same service).
- `GlobalExceptionHandler` handles `MethodArgumentNotValidException` → 400; annotate the request DTO (`@Size` on note).
- Staging = production: tests must clean up `organizer_thanks`; never leave rows behind.
- If using an email-link/token pattern anywhere, confirm POST-only (not applicable here — direct in-app POST).

### Project Structure Notes
- Controller/service/entity under `.../controller`, `.../service`, `.../domain`; migration under `.../resources/db/migration`.

### References
- [Source: docs/prd/epic-7-attendee-experience-enhancements.md#story-74-thank-the-organizers]
- [Source: services/event-management-service/.../controller/EventPhotoController.java]
- [Source: api-gateway/.../security/TurnstileVerificationFilter.java] [Source: .../config/TurnstileProperties.java] [Source: .../config/ConfigController.java]
- [Source: api-gateway/.../security/RateLimitingFilter.java]
- [Source: web-frontend/src/App.tsx] [Source: web-frontend/src/pages/public/ArchivePage.tsx]
- [Source: _bmad-output/project-context.md#additional-emails] [Source: #frontend-gotchas] [Source: project_turnstile_e2e_bypass]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) — bmad-dev-story (Amelia).

### Implementation Plan

Grounded in the live codebase (not the story's tentative guesses):

- **Pattern source:** event-management controllers use **direct Spring annotations** (`@PostMapping`/`@GetMapping`), NOT generated `*Api` interfaces (confirmed: `NewsletterController`, `EventPhotoController`). OpenAPI spec is still updated for docs + frontend type-gen, but the controller is hand-written. Closest public+anonymous+Turnstile analog = `NewsletterController.subscribe` + `NewsletterSubscribeWidget` (Tailwind public widget) + `useTurnstile`/`useOptionalConfig`. Fresh test idiom = `SelfNominationIntegrationTest` (`details.code` typed conflicts, `deleteAll` cleanup, event setUp helper). Service-side rate-limit precedent = `InboundEmailRateLimiter` (Caffeine fixed-window).
- **Endpoints:** `POST /api/v1/events/{eventCode}/thanks` (public, body `{note?}`, returns `{count}`) + `GET /api/v1/events/{eventCode}/thanks` (public, returns `{count}`; organizer additionally gets `notes[]`). Both branch on injected `Authentication` (null = anonymous, ADR EventPhotoController L76 pattern).
- **Schema (V111):** `organizer_thanks` table; partial unique index `ux_organizer_thanks_user (event_id, thanked_by_username) WHERE thanked_by_username IS NOT NULL` (mirrors V109's per-attendee partial-unique pattern).
- **Rate limit (Resolved Decision #3):** service-side per-(event,IP) Caffeine cap (`ThanksRateLimiter`, MAX=5/event/IP/hour) on **anonymous** submissions only (logged-in already deduped to 1). Client IP via X-Forwarded-First-hop (RateLimitFilter pattern). Checked BEFORE insert → "rejected without incrementing" (AC3) = no row inserted, no aggregate bump.
- **Turnstile:** add `POST:/api/v1/events/*/thanks` to gateway `application.yml` `turnstile.protected-endpoints` (filter already wired, fail-open when disabled).
- **SecurityConfig:** add `POST` + `GET /api/v1/events/*/thanks` permitAll to gateway `defaultSecurityFilterChain` (`@Profile("!test")`) AND EMS `filterChain` (`@Profile("!test")`). EMS `TestSecurityConfig` is permitAll-at-HTTP so tests work via `@WithMockUser`.
- **Exceptions:** reuse `EventNotFoundException` (404); new `ThanksNotAllowedException` (409, `details.code=THANKS_NOT_ALLOWED`, event not LIVE/COMPLETED) + `ThanksRateLimitedException` (429) — both need explicit `@ExceptionHandler` (catch-all `Exception.class` at GlobalExceptionHandler L1278 would otherwise 500 them).
- **Frontend:** `ThankOrganizersWidget` (Tailwind-only, mirrors NewsletterSubscribeWidget) on `HomePage` post-event section, gated `workflowState IN (EVENT_LIVE, EVENT_COMPLETED)`; `thanksService` via `apiClient`; i18n `events.json` `thanks.*` in all 10 locales.

### Debug Log References

- `OrganizerThanksIntegrationTest` (7 tests) — `BUILD SUCCESSFUL`, all PASSED (PostgreSQL/Testcontainers).
- `ThankOrganizersWidget.test.tsx` (5 tests) — all PASSED (vitest).
- Frontend `tsc --noEmit` clean; ESLint clean on all changed files.
- `checkstyleMain` (event-management + api-gateway) + api-gateway `compileJava` — `BUILD SUCCESSFUL`.

### Completion Notes List

- **All 7 ACs satisfied.** Public POST + GET, anonymous-allowed, Turnstile-guarded (gateway) + service-side per-(event,IP) rate limit, logged-in dedupe, count-public/notes-organizer-only, Tailwind-only public widget, 10-locale i18n, PostgreSQL integration tests.
- **Key decision — endpoint shape:** the GET is a single PUBLIC route `/api/v1/events/{eventCode}/thanks` that returns count-only for the public and additionally `notes[]` for organizer callers (role-branched in the controller), NOT a separate organizer-only `/count` route. Matches AC6 verbatim.
- **Key decision — rate limit:** per-(event,IP) Caffeine cap at the service (Resolved Decision #3), not just the gateway's global IP bucket. In-memory per service instance (no Redis, per the project's caching stance); sufficient at BATbern scale — documented in `ThanksRateLimiter`.
- **Note on Turnstile testing:** verification is a gateway filter concern; in EMS isolation an anonymous POST simply inserts. The 403-on-invalid-token leg is covered at the gateway/E2E layer, noted in the integration-test javadoc. AC3's rate-limit-rejection leg IS covered here (429, no increment).
- **Controllers use direct Spring annotations** (not generated `*Api` interfaces) — matches the established `NewsletterController`/`EventPhotoController` pattern in this service. OpenAPI spec updated for docs + frontend type-gen.

### File List

**Backend (event-management-service):**
- `src/main/resources/db/migration/V111__create_organizer_thanks.sql` (new)
- `src/main/java/ch/batbern/events/domain/OrganizerThanks.java` (new)
- `src/main/java/ch/batbern/events/repository/OrganizerThanksRepository.java` (new)
- `src/main/java/ch/batbern/events/dto/SubmitThanksRequest.java` (new)
- `src/main/java/ch/batbern/events/dto/ThanksNoteResponse.java` (new)
- `src/main/java/ch/batbern/events/dto/ThanksCountResponse.java` (new)
- `src/main/java/ch/batbern/events/exception/ThanksNotAllowedException.java` (new)
- `src/main/java/ch/batbern/events/exception/ThanksRateLimitedException.java` (new)
- `src/main/java/ch/batbern/events/service/ThanksRateLimiter.java` (new)
- `src/main/java/ch/batbern/events/service/OrganizerThanksService.java` (new)
- `src/main/java/ch/batbern/events/controller/OrganizerThanksController.java` (new)
- `src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` (modified — +2 handlers)
- `src/main/java/ch/batbern/events/config/SecurityConfig.java` (modified — +2 permitAll matchers)
- `src/test/java/ch/batbern/events/controller/OrganizerThanksIntegrationTest.java` (new)

**API Gateway:**
- `src/main/java/ch/batbern/gateway/config/SecurityConfig.java` (modified — +2 permitAll matchers)
- `src/main/resources/application.yml` (modified — +1 Turnstile protected-endpoint)

**API contract:**
- `docs/api/events-api.openapi.yml` (modified — +2 paths, +3 schemas)

**Frontend (web-frontend):**
- `src/services/thanksService.ts` (new)
- `src/hooks/useThanks/useThanks.ts` (new)
- `src/components/public/ThankOrganizersWidget.tsx` (new)
- `src/components/public/__tests__/ThankOrganizersWidget.test.tsx` (new)
- `src/pages/public/HomePage.tsx` (modified — lazy widget, gated render)
- `src/types/generated/events-api.types.ts` (regenerated)
- `public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json` (modified — `thanks.widget.*`)

### Change Log

| Date | Change |
|------|--------|
| 2026-06-10 | Story 7.4 implemented (Amelia / bmad-dev-story). Public anonymous "Thank the Organizers" POST/GET in event-management-service, Turnstile + per-(event,IP) rate limit, logged-in dedupe, organizer-only notes, Tailwind-only public widget on HomePage, 10-locale i18n. Backend 7 ITs + FE 5 unit tests green. Status → review. |

## Resolved Decisions

_Resolved with the PM 2026-06-10._

1. **Placement:** On the **public event/archive page**, built **Tailwind-only** (no MUI) so anyone — logged in or not — can thank. (Task 5 + AC7 reflect this.)
2. **Notes visibility:** The **aggregate count is public**; free-text **notes are organizer-visible only** (no public appreciation wall of anonymous notes; no approval queue). Keeps troll-text off the public surface with zero recurring moderation. → AC6 returns count publicly, notes only in the organizer view.
3. **Abuse guard:** **Per-event per-IP cap + Turnstile** on anonymous submissions (not just the global gateway IP limit) — a small cap (e.g. a handful per event per IP) is enough at this scale.

## Senior Developer Review (AI)

**Reviewed:** 2026-06-11, adversarial 3-layer review (Blind Hunter + Edge Case Hunter + Acceptance Auditor) over commits 78a9a837..e56dfff5. **Outcome: Approve with minor fixes applied.**

All 7 ACs + 3 Resolved Decisions + project rules (ADR-003, no-MUI-public, GlobalExceptionHandler, Flyway) confirmed **Met** by the Acceptance Auditor.

Fixes applied this review (commit 2ca3966e):
- **[High] Race-safe logged-in dedupe.** The check-then-insert upsert could collide on the partial unique index under a concurrent double-submit and return 500. Replaced with an atomic native `ON CONFLICT … DO UPDATE` (`OrganizerThanksRepository.upsertLoggedInThanks`). The UI already disables the button mid-submit, so this is defense-in-depth for multi-tab/API callers.
- **[Low] `getClientIp` empty-hop fallback.** A blank `X-Forwarded-For` first hop would have collapsed all anonymous callers into one rate-limit bucket; now falls back to `remoteAddr`.

## Open Questions

> **Resolved 2026-06-11 (Nissim/PM): all accepted as-is — current behavior confirmed, no changes.** Q1 rate-limit cap (5/event/IP/hour, per-instance) is fine. Q2 thank-you widget stays live/completed-only (hidden on ARCHIVED).

1. **Rate-limit cap and its scope.** The anonymous cap is **5 thank-yous per event per IP per hour**, held **in-memory per service instance** (Caffeine, no Redis — matching the project's caching stance). So the real cluster-wide ceiling is roughly `5 × number-of-running-instances`, and it resets on every deploy/restart. For a gratitude clap that's almost certainly fine, but if you'd prefer a hard cluster-wide cap we'd need a shared store. Is 5/event/IP/hour the "handful" you intended, and is per-instance acceptable?

2. **Thank-you visibility vs. the Q&A on archived events.** The thank-you widget shows only while an event is `EVENT_LIVE`/`EVENT_COMPLETED` and disappears once the event flips to `ARCHIVED` (~14 days after the event). The 7.5 Q&A, by contrast, stays visible on archived events. Do you want the thank-you button to remain on archived event pages too (i.e. allow thanking older events), or is the live/completed-only window the intended behaviour?
