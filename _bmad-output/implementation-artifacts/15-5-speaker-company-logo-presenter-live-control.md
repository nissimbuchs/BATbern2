# Story 15.5: Speaker company logo on presenter + live control

Status: review

## Story

As an **organizer running an event**,
I want **the speaker's company logo shown next to their name on the live-control screen (and the presenter view)**,
so that **the company is recognizable at a glance during the live session, legibly even on the dark surfaces**.

> Source: `docs/prd/epic-15-post-event-2-hardening.md` → **Story 15.5 (Item #5)**. Same branch as 15.7 (`feature/epic-15-7-qna-notify`).

## Acceptance Criteria

1. **AC1** — GIVEN a speaker whose company has a logo, WHEN the live-control active-session card renders, THEN the logo shows inside a white chip (`LogoBadge`) next to the speaker. (Presenter already does this — see Dev Notes.)
2. **AC2** — GIVEN a transparent logo on the dark live-control / presenter background, THEN it stays legible (white chip behind it).
3. **AC3** — GIVEN a speaker with no company logo, THEN a graceful fallback (name only, no broken image).
4. **AC4** (enrichment, ADR-004) — the watch active-event speaker payload (`WatchSpeakerDetail`/`SpeakerDetail`) populates `companyLogoUrl` (+ human-readable `company`) via the existing read-only `findUserPortraitsByUsernames` join — the same path the presenter's `SessionSpeaker` already uses — not a per-speaker JPQL/HTTP fan-out and not stored on `session_users`.

## Tasks / Subtasks

- [x] **Task 1 — Backend: enrich watch speaker detail with company + logo** (AC: 1, 4)
  - [x] In `WatchEventController`, batch-load `sessionUserRepository.findUserPortraitsByUsernames(speakerUsernames)` into a `Map<String, UserPortraitProjection>` keyed by username — exactly as `EventController` does at `:368` for the presenter path.
  - [x] Thread the map through `mapToSessionDetail(session, userMap, portraitMap)` → `mapToSpeakerDetail(su, userMap, portraitMap)`.
  - [x] In `mapToSpeakerDetail`, replace the two `null`s (`:227-228`) with `portrait != null ? portrait.getCompanyDisplayName() : null` (company — human-readable for the logo alt/label) and `portrait != null ? portrait.getCompanyLogoUrl() : null` (companyLogoUrl). Null-safe — both may be null (AC3).
- [x] **Task 2 — OpenAPI: drop the "always null" note + regen** (AC: 4)
  - [x] In `docs/api/events-api.openapi.yml`, update `WatchSpeakerDetail.company` and `.companyLogoUrl` descriptions (remove "Cross-service lookup deferred. Always null in W2.3."; describe them as the company display name + logo CloudFront URL, nullable).
  - [x] Regenerate frontend types: `cd web-frontend && npm run generate:api-types`. Commit the generated diff.
- [x] **Task 3 — Frontend: render the logo in LiveControl `ActiveSessionCard`** (AC: 1, 2, 3)
  - [x] Replace the plain `speakerNames()` text line for the **active** session with a per-speaker row: name + an optional `<LogoBadge src={companyLogoUrl} alt={company} .../>` when `companyLogoUrl` is set. `LogoBadge` is framework-agnostic (plain span/img) so it works in this MUI-free shadcn/Tailwind card.
  - [x] Size the logo small (e.g. `imgStyle={{ height: 20, maxWidth: 80 }}`) so it sits inline with the name; white chip handles dark-bg legibility (AC2).
  - [x] No logo → render name only (AC3). Keep the existing comma-joined names as the layout baseline; add chips beside names.
- [x] **Task 4 — Tests** (AC: 1–4)
  - [x] Backend: extend `WatchEventControllerIntegrationTest` — seed a company (`companies.name`, `display_name`, `logo_url`) + a `user_profiles` row (`company_id` = the company name) for a session speaker, then assert the active-event response exposes that speaker's `companyLogoUrl` + `company`. (See `SessionQnaIntegrationTest.seedAuthor` for the exact insert shape.) Add a no-logo speaker → `companyLogoUrl` null (AC3).
  - [x] Frontend: new `ActiveSessionCard.test.tsx` — a speaker with `companyLogoUrl` → an `<img>` with that src is rendered (inside the chip); a speaker without → no img / no broken image.

## Dev Notes

### What's already done vs the gap
- **Presenter (`web-frontend/src/pages/presentation/SpeakerCard.tsx`) is DONE** — it already renders `speaker.companyLogoUrl` via `LogoBadge` (white chip, Vanessa #791 / quick-wins #8b). Its data is the events-api `SessionSpeaker`, whose `companyLogoUrl` the presenter event endpoint (`EventController:489-491`) already populates from `findUserPortraitsByUsernames`. **No presenter change needed.**
- **The gap is LiveControl.** `ActiveSessionCard` (`web-frontend/src/components/organizer/LiveControl/ActiveSessionCard.tsx`) renders only `speakerNames()` (text). Its data is `WatchSessionDetail.speakers` (`WatchSpeakerDetail`), whose `companyLogoUrl` is **"Always null in W2.3"** — the watch active-event path (`WatchEventController.mapToSpeakerDetail`) passes `null`. So this story is: enrich that backend path + render the logo in the card.

### Key facts (cite when implementing)
- Enrichment site: `services/event-management-service/.../watch/WatchEventController.java` — `mapToSpeakerDetail` (`:214`, the two `null`s at `:227-228`); `userMap` is built at `:119-137`; `mapToSessionDetail` at `:172`.
- Mirror target: `EventController.java:368` (`findUserPortraitsByUsernames` → `portraitByUsername`) + `:489-491` (sets `company`/`companyDisplayName`/`companyLogoUrl`).
- Projection: `SessionUserRepository.findUserPortraitsByUsernames(Collection<String>)` → `UserPortraitProjection` with `getCompanyDisplayName()` (COALESCE display_name → name → company_id) + `getCompanyLogoUrl()` (`companies.logo_url`). Read-only cross-service DB join, ADR-004-sanctioned for this monorepo; already used by the presenter single-event path, so consistent here. Both fields nullable.
- DTO: `watch/dto/SpeakerDetail.java` — constructor args order `(username, firstName, lastName, company, companyLogoUrl, profilePictureUrl, bio, speakerRole)`.
- Reuse component: `web-frontend/src/components/shared/LogoBadge/LogoBadge.tsx` — `<LogoBadge src alt imgStyle style />`; white chip; framework-agnostic.
- LiveControl card: `ActiveSessionCard.tsx` — `WatchSessionDetail` prop; `speakerNames()` helper joins `firstName+lastName`. shadcn `Card`/Tailwind (no MUI) — `LogoBadge` is fine here.

### Thumbnail / 503 decision
The epic suggested a `?w=≤512` thumbnail to dodge the resize-Lambda OOM-503. That 503 affects **high-megapixel photos** (lightbox), not company logos, which are small — and the **already-shipped presenter renders the raw `companyLogoUrl`** in `LogoBadge` with no 503. So this story renders the **raw** logo URL too (matching the proven presenter), keeping behaviour identical across both surfaces. (If a logo ever 503s, the fallback is a missing image, not a broken layout — AC3.)

### Project constraints
- ADR-004: no duplicated user/company fields persisted; enrich at read time via the existing join. No new `session_users` columns.
- No MUI on the presenter (already satisfied — `LogoBadge` is MUI-free). LiveControl is organizer-side shadcn; `LogoBadge` works there too.
- No migration. No new i18n keys (logo is an image; alt text uses the company name).

### References
- [Source: docs/prd/epic-15-post-event-2-hardening.md#Story 15.5]
- [Source: services/event-management-service/.../watch/WatchEventController.java#mapToSpeakerDetail L214-234, userMap L119-137]
- [Source: services/event-management-service/.../controller/EventController.java#L368,489-491 (mirror)]
- [Source: services/event-management-service/.../repository/SessionUserRepository.java#findUserPortraitsByUsernames; UserPortraitProjection.java]
- [Source: web-frontend/src/pages/presentation/SpeakerCard.tsx (presenter, already done); components/shared/LogoBadge/LogoBadge.tsx]
- [Source: web-frontend/src/components/organizer/LiveControl/ActiveSessionCard.tsx]
- [Source: docs/api/events-api.openapi.yml#WatchSpeakerDetail]

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1m] (Amelia / dev-story)

### Debug Log References

### Completion Notes List

- Presenter was already done (`SpeakerCard` renders `companyLogoUrl` via `LogoBadge`) — no change.
- The real gap was the watch/live-control path: `WatchSpeakerDetail.companyLogoUrl` was hardcoded `null`. Enriched `WatchEventController.mapToSpeakerDetail` via the same `findUserPortraitsByUsernames` read-only join the presenter's `EventController` already uses (ADR-004; nothing stored on `session_users`).
- `ActiveSessionCard` now renders each speaker as name + optional `LogoBadge` (white chip → AC2 legibility on dark bg); no logo → name only (AC3).
- Thumbnail `?w=` deliberately not used — logos are small and the shipped presenter renders raw with no 503 (documented in Dev Notes).
- Tests: 2 backend (`WatchEventControllerIntegrationTest`: logo enriched + no-logo null), 2 frontend (`ActiveSessionCard.test.tsx`). Full EMS suite green (0 failures); FE type-check + lint clean.

### File List

- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchEventController.java` (portrait map + enrich company/companyLogoUrl)
- `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchEventControllerIntegrationTest.java` (+2 tests, JDBC seed helper)
- `docs/api/events-api.openapi.yml` (WatchSpeakerDetail company/companyLogoUrl descriptions)
- `web-frontend/src/types/generated/events-api.types.ts` (regenerated)
- `web-frontend/src/components/organizer/LiveControl/ActiveSessionCard.tsx` (per-speaker LogoBadge)
- `web-frontend/src/components/organizer/LiveControl/ActiveSessionCard.test.tsx` (NEW, 2 tests)

### Change Log

| Date | Change |
|------|--------|
| 2026-06-20 | Story created (ready-for-dev) on the 15.7 branch. |
| 2026-06-20 | Implemented: backend watch-speaker company/logo enrichment via `findUserPortraitsByUsernames`; `ActiveSessionCard` renders `LogoBadge` per speaker; OpenAPI + types updated. 2 backend + 2 frontend tests; full EMS suite green. Presenter unchanged (already done). Status → review. |
