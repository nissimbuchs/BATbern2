# Story 7.4: Thank-the-Organizers

Status: ready-for-dev

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
6. An organizer view shows the aggregate count and any submitted notes for an event.
7. The thank-you UI element respects the bundle boundary: if placed on a **public** event/archive page it is **Tailwind-only (no MUI)**. i18n keys in all 10 locales. Integration tests (PostgreSQL) cover logged-in dedupe, anonymous increment + Turnstile/rate-limit rejection, and event-state guard. No leftover test data.

## Tasks / Subtasks

- [ ] **Task 1: Schema** (AC: 4, 5, 6)
  - [ ] New forward migration (current highest **V108**; next free at implementation). `CREATE TABLE organizer_thanks (id UUID PK, event_id UUID NOT NULL, thanked_by_username VARCHAR(100) NULL, note TEXT NULL, created_at TIMESTAMPTZ DEFAULT now())`. Partial unique index for logged-in dedupe: `CREATE UNIQUE INDEX ux_organizer_thanks_user ON organizer_thanks(event_id, thanked_by_username) WHERE thanked_by_username IS NOT NULL`. Index on `event_id`.
  - [ ] `OrganizerThanks` entity (FK `event_id` UUID is in-service, allowed; `thanked_by_username` meaningful ID per ADR-003, nullable for anonymous).
- [ ] **Task 2: Endpoint + service** (AC: 1, 4, 5)
  - [ ] `OrganizerThanksController` `POST /api/v1/events/{eventCode}/thanks` — **no `@PreAuthorize`** (public). Inject `Authentication` and null-check (pattern from `EventPhotoController` ~L76: `username = authentication != null ? authentication.getName() : null`).
  - [ ] Guard: event must be `EVENT_LIVE`/`EVENT_COMPLETED` (load via `eventRepository.findByEventCode`).
  - [ ] Logged-in → upsert by (event, username); anonymous → insert clap row (username null) after guard checks.
  - [ ] GET aggregate: `GET /api/v1/events/{eventCode}/thanks` → `{ count, notes[] }` (organizer-visible; public count optional).
- [ ] **Task 3: Turnstile + rate limit** (AC: 3)
  - [ ] Add `POST:/api/v1/events/{code}/thanks` (or the AntPath equivalent) to `TurnstileProperties.protectedEndpoints` so `TurnstileVerificationFilter` validates anonymous tokens (fail-open behaviour matches existing config when `turnstile.enabled=false`).
  - [ ] Anonymous rate limit: reuse the gateway `RateLimitingFilter` IP bucketing if sufficient, else add a per-event/per-IP guard at the service (no per-entity limiter exists today — document the chosen approach).
- [ ] **Task 4: SecurityConfig (both layers)** (AC: 2)
  - [ ] api-gateway `SecurityConfig`: `POST /api/v1/events/*/thanks` → `.permitAll()`. event-management-service `SecurityConfig`: same `.permitAll()` (add to prod chain; local/test already permitAll). Missing the service-side rule = 401 even when gateway permits.
- [ ] **Task 5: Frontend** (AC: 7)
  - [ ] Add a "Thank the organizers" button + count to the post-event surface. **Determine first** whether that surface is the public (Tailwind-only) archive/event page or a MuiLayout route; if public → Tailwind-only, no MUI imports. Read the Turnstile siteKey from `GET /api/v1/config` (`features.turnstile`) and render the widget for anonymous users.
  - [ ] i18n keys in all 10 locales (`events.json`), EN+DE first-class.
- [ ] **Task 6: Tests (TDD)** (AC: 4, 5, 3, 1)
  - [ ] Integration (PostgreSQL): logged-in thanks once → count 1; repeat → still 1 (+ note update); anonymous with valid token → increments; anonymous missing/invalid token → 403, no increment; event not live/completed → rejected. Clean up `organizer_thanks` rows.

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

### Debug Log References

### Completion Notes List

### File List

## Open Questions

1. **Where does the "thank the organizers" button live?** It could sit on the public event/archive page (where most attendees land after an event) or on a logged-in post-event surface. The answer matters technically: a public page must be Tailwind-only (no MUI), per the bundle boundary. Recommendation: put it on the public event/archive detail page as a Tailwind element so anyone can thank — please confirm the placement.
2. **Are anonymous notes shown publicly, and is there any moderation?** Logged-in notes are attributable; anonymous notes have no author. Showing free-text notes from anonymous senders on an appreciation wall invites the occasional troll. Options: show anonymous notes immediately, show only the count for anonymous (notes for logged-in only), or hold notes for organizer approval. Recommendation: count is public, notes are organizer-visible only for the MVP — confirm.
3. **How aggressive should the anonymous rate limit be?** We want to prevent one person inflating the counter without making it annoying. A simple per-IP cap (e.g. a handful per event per IP) plus Turnstile is probably enough at this scale. Please confirm a sensible cap, or whether the global gateway IP limit is acceptable as-is.
