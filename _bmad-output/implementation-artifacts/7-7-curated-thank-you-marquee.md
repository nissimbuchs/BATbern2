# Story 7.7: Curated Thank-You Notes in the Partner Marquee

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **organizer**,
I want to **feature selected logged-in thank-you notes as "testimonial" cards intermingled into the public partner marquee**,
so that **the genuine gratitude attendees leave (Story 7.4) becomes visible social proof on the public site — turning a private organizer-only list into credible, named appreciation that also reassures prospective registrants**.

**Source:** Epic 7 FR11 ("Thank-yous are surfaced as an aggregate counter / **appreciation wall**"). Extends Story 7.4 (Thank-the-Organizers), which today serves notes **organizer-only** (7.4 AC6 — no public note wall). This story adds the *curated* public surface FR11 anticipated, in a troll-safe way: only logged-in (named) notes are eligible, and an organizer must explicitly feature each one.

**Decided with PM (Nissim) 2026-06-13** — see [Resolved Decisions](#resolved-decisions) and [Open Questions](#open-questions).

## Acceptance Criteria

1. **Featured flag + organizer toggle.** An organizer (`ROLE_ORGANIZER`) can mark/unmark an individual logged-in thank-you note as *featured* via `PATCH /api/v1/events/{eventCode}/thanks/{id}` with body `{ "featured": true|false }`. The endpoint is organizer-only (gateway + service `SecurityConfig`). Marking sets `featured_at = now()`; unmarking sets it `NULL`. Attempting to feature an **anonymous** note (`thanked_by_username IS NULL`) is rejected (409 `THANKS_NOT_FEATURABLE`) — anonymous notes are structurally ineligible.
2. **Public featured pool (global, cross-event).** A new **public** endpoint `GET /api/v1/thanks/featured?limit=9` returns up to `limit` (default 9, hard-capped at 9) **random** featured notes drawn from **all events**, each enriched with `thankedByFirstName`, `thankedByLastName`, `thankedByCompanyName`, `thankedByCompanyLogoUrl`, plus `note` and `eventCode`. Only notes with `featured_at IS NOT NULL` **and** `thanked_by_username IS NOT NULL` are eligible. The response never includes anonymous notes, un-featured notes, or the raw username.
3. **Enrichment mirrors the Q&A pattern.** Author name + company display name + company logo are resolved by a native join `user_profiles up LEFT JOIN companies c ON c.name = up.company_id`, mirroring `SessionUserRepository.findQnaAuthorPortraitsByUsernames`. The join **respects `user_profiles.settings_show_company`**: when the author opted out, `thankedByCompanyName` and `thankedByCompanyLogoUrl` are returned `null` (the card then renders name only). A note whose author no longer exists (no `user_profiles` row) is silently omitted from the featured response (null-safe — never 500).
4. **Public marquee intermingles notes with partners.** On the public `HomePage`, the existing partner marquee (`TestimonialSection.tsx`) renders featured thank-you cards **interleaved** with partner cards (every other card a thank-you when enough notes exist), in a single `InfiniteMarquee` row. The hardcoded 20-item testimonial fallback array is **removed** and replaced with real data from the featured endpoint. When fewer than a configurable threshold of featured notes exist, the marquee gracefully degrades to partners-only (no empty/placeholder cards, no layout break).
5. **Thank-you card shows quote + first name + company logo.** Each card renders the note text, the author's **first name** (never the username/email), and the **company logo** (via `buildCdnImageUrl`, the `SpeakerDisplay` logo pattern). If the logo is absent (opted-out or no logo), the card falls back to company text or name-only — no broken image. Built **Tailwind-only (NO MUI)** — it lives in the public bundle. Card sizing matches `PartnerShowcaseCard` (`w-80 h-48`).
6. **Organizer Appreciation surface.** The organizer `EventPage` gains an **Appreciation** tab/panel (MUI side) listing that event's thank-you notes: aggregate count, each note's text + author display name + submitted date + login/anonymous indicator, and a ★ feature toggle (calls AC1's PATCH; disabled for anonymous notes). This is the surface that answers "where do organizers read the notes" (the gap from 7.4).
7. **i18n + tests + cleanup.** New public-facing keys in **all 10 locales** (`de,en,fr,it,rm,es,fi,nl,ja,gsw-BE`), EN+DE first-class; organizer-side keys follow the same 10-locale UI rule. Integration tests (PostgreSQL/Testcontainers) cover: feature/un-feature toggle, anonymous-note rejection, public featured GET returns only featured+logged-in notes, `limit` cap at 9, randomization, enrichment of name+companyLogoUrl, `settings_show_company=false` → logo/company null, deleted-author omission. Frontend unit tests cover interleave logic, card with/without logo, opted-out company hidden. No leftover `organizer_thanks` test rows (staging IS production).

## Tasks / Subtasks

- [x] **Task 1: Schema — new forward migration** (AC: 1, 2)
  - [x] `V112__organizer_thanks_featured.sql` in `services/event-management-service/src/main/resources/db/migration/`. `ALTER TABLE organizer_thanks ADD COLUMN featured_at TIMESTAMPTZ NULL;`. Partial index for the random-featured query: `CREATE INDEX ix_organizer_thanks_featured ON organizer_thanks (featured_at) WHERE featured_at IS NOT NULL AND thanked_by_username IS NOT NULL;`
  - [x] **NEVER edit V111** (already shipped — checksum-frozen). Confirm V112 is the next free number (V109/V110/V111 shipped). Exclude `**/db/migration/**` from any bulk edits.
  - [x] Add `featuredAt` (`Instant`, nullable) field to `OrganizerThanks` entity. No `@PrePersist` change (defaults null).
- [x] **Task 2: Enrichment — mirror Q&A author portraits** (AC: 2, 3)
  - [x] New projection `ThanksAuthorProjection` (mirror `QnaAuthorProjection`: `username, firstName, lastName, showCompany, companyDisplayName, companyLogoUrl`).
  - [x] New repository method on `OrganizerThanksRepository` (or a dedicated query method) cloning `SessionUserRepository.findQnaAuthorPortraitsByUsernames` verbatim — native query `user_profiles up LEFT JOIN companies c ON c.name = up.company_id WHERE up.username IN :usernames`, returning the projection. Batch-load by the set of usernames in the featured result (avoid N+1).
  - [x] In the service, blank out `companyDisplayName`/`companyLogoUrl` when `showCompany` is false/null (same as Q&A). Omit notes whose username has no projection row (deleted author).
- [x] **Task 3: Public featured endpoint** (AC: 2, 3)
  - [x] Repository query: select featured + logged-in rows, `ORDER BY random() LIMIT :limit` (Postgres `random()`; cap `limit` at 9 in the service regardless of query param). Return rows across **all events** (no event filter).
  - [x] New DTO `FeaturedThanksResponse` (list item: `note`, `eventCode`, `thankedByFirstName`, `thankedByLastName`, `thankedByCompanyName`, `thankedByCompanyLogoUrl`). Mirror `QnaPostResponse` field naming (`thankedBy*`). Never expose `thanked_by_username`.
  - [x] `OrganizerThanksController` (or a new `FeaturedThanksController`): `GET /api/v1/thanks/featured` — public, no `@PreAuthorize`. Service enriches + maps.
- [x] **Task 4: Organizer feature-toggle endpoint** (AC: 1, 6)
  - [x] `PATCH /api/v1/events/{eventCode}/thanks/{id}` — `@PreAuthorize` organizer (match the role pattern used by other organizer EMS endpoints, e.g. `SlidesOnlineController` / Q&A `EventQnaController` PATCH). Body `ThanksFeaturePatchRequest { Boolean featured }`.
  - [x] Service: load by id (scoped to eventCode — 404 `THANKS_NOT_FOUND` if mismatch), reject if `thanked_by_username IS NULL` (409 `THANKS_NOT_FEATURABLE`), else set/clear `featured_at`. Return the updated note (organizer view).
  - [x] New exceptions `ThanksNotFoundException` (404) + `ThanksNotFeaturableException` (409) each with explicit `@ExceptionHandler` in `GlobalExceptionHandler` (catch-all would otherwise 500). Reuse `details.code` typed-conflict idiom.
- [x] **Task 5: SecurityConfig (both layers)** (AC: 1, 2)
  - [x] api-gateway `SecurityConfig.defaultSecurityFilterChain` (`@Profile("!test")`): `GET /api/v1/thanks/featured` → `.permitAll()`. event-management `SecurityConfig.filterChain` (`@Profile("!test")`): same. (Mirror the 7.4 thanks permitAll matchers.)
  - [x] The organizer PATCH is `@PreAuthorize`-gated, not permitAll — confirm it is **not** accidentally swept into a permitAll matcher.
- [x] **Task 6: API contract + type-gen** (AC: 2, 6)
  - [x] Update `docs/api/events-api.openapi.yml`: +`GET /thanks/featured` (+`FeaturedThanksResponse` schema), +`PATCH /events/{eventCode}/thanks/{id}` (+`ThanksFeaturePatchRequest`). Run `npm run generate:api-types` and commit generated types.
- [x] **Task 7: Frontend — public marquee** (AC: 4, 5)
  - [x] New `featuredThanksService` + `useFeaturedThanks` hook (mirror `thanksService`/`useThanks`; React Query, public GET, ~5-min staleTime so the random shuffle is stable).
  - [x] Enhance `TestimonialCard.tsx` to render the **company logo** (reuse `buildCdnImageUrl({ h: 128, fit: 'inside' })` + the `SpeakerDisplay.tsx` logo block); graceful fallback to company text / name-only.
  - [x] In `TestimonialSection.tsx`: delete the hardcoded array (L22-149), source featured notes from the hook, **interleave** partner + thank-you cards in the single `InfiniteMarquee` (even/odd interleave; partners-only fallback under threshold). Match `PartnerShowcaseCard` sizing (`w-80 h-48`). Keep `prefers-reduced-motion` + pause-on-hover behavior.
- [x] **Task 8: Frontend — organizer Appreciation panel** (AC: 6)
  - [x] New `EventAppreciationTab` (mirror `EventNewsletterTab` structure), added to `TABS` in `components/organizer/EventPage/EventPage.tsx`. Lists notes (organizer GET already returns `notes[]` from 7.4) + each note's ★ feature toggle (PATCH). Toggle disabled + tooltip for anonymous notes.
  - [x] Extend the organizer `getThanks` typing to surface `id` + `featured`/`featuredAt` per note (the 7.4 organizer GET returns `notes[]`; ensure `id` + featured state are present in `ThanksNoteResponse`).
- [x] **Task 9: i18n** (AC: 7)
  - [x] Public: `thanks.marquee.*` (e.g. card aria-label / "Thank you" heading) in all 10 locales, EN+DE first-class.
  - [x] Organizer: `eventPage.appreciation.*` (tab label, feature/unfeature, anonymous-disabled tooltip, count) in all 10 locales.
- [x] **Task 10: Tests (TDD)** (AC: 1, 2, 3, 7)
  - [x] EMS `FeaturedThanksIntegrationTest` (PostgreSQL/Testcontainers): feature toggle sets/clears `featured_at`; anonymous → 409 `THANKS_NOT_FEATURABLE`; public GET returns only featured+logged-in; `limit` capped at 9; enrichment populates name + companyLogoUrl; `settings_show_company=false` → company/logo null; deleted author omitted; id/eventCode mismatch → 404. Cleanup via `deleteAll` + `@Transactional` rollback (seed `user_profiles`/`companies` rows as the Q&A tests do).
  - [x] FE unit tests: `TestimonialCard` renders logo / falls back; `TestimonialSection` interleaves correctly and degrades to partners-only under threshold; opted-out company hidden. `tsc --noEmit` + ESLint clean.

## Dev Notes

### Service location & patterns
- Feature lives in **event-management-service** (extends 7.4's `organizer_thanks`). Controllers use **direct Spring annotations** (`@GetMapping`/`@PatchMapping`), NOT generated `*Api` interfaces — matches `OrganizerThanksController`, `NewsletterController`, `EventQnaController`. OpenAPI spec still updated for docs + FE type-gen.
- **The enrichment is already solved — clone it, don't invent it.** `SessionUserRepository.findQnaAuthorPortraitsByUsernames` (`SessionUserRepository.java:204-215`) + `QnaAuthorProjection` + `SessionQnaService.loadAuthorPortraits` (`SessionQnaService.java:271-300`) are the canonical username→name+companyLogo path. The native join, the `COALESCE(c.display_name, c.name, up.company_id)` company-name fallback, the `settings_show_company` opt-out, and the null-safety for deleted authors are all proven there. Mirror field naming `postedBy*` → `thankedBy*`.
- **Frontend card pattern:** `SpeakerDisplay.tsx:53-57` resolves the logo (`speaker.companyLogoUrl ?? company?.logo?.url`) and renders it with `buildCdnImageUrl`. The Q&A poster cards (recent commits `be9d052d`, `d69c3d2a`) already render "portrait + name + company logo" from enriched `QnaPostResponse` → `SessionSpeaker` → `SpeakerDisplay`. Use the same logo block; we only need name + logo (no portrait required for a testimonial card).

### Existing surfaces being MODIFIED (read before editing)
- **`web-frontend/src/components/public/Testimonials/TestimonialSection.tsx`** — *Current:* renders a hardcoded 20-item testimonial array (L22-149) as a fallback row (only when <3 event photos) + a second partner row (`PartnerShowcaseCard` via `usePublicPartners`, one `InfiniteMarquee`, `direction="right"`). *This story:* delete the hardcoded array; feed featured notes from the new hook; interleave partner+note in one marquee. *Preserve:* the partner row data path (`usePublicPartners`), `skipPhotoRow` prop behavior, reduced-motion/pause-on-hover, and the always-rendered nature on `HomePage.tsx:370`.
- **`web-frontend/src/components/public/Testimonials/TestimonialCard.tsx`** — *Current:* props `{avatar?, name, quote, company}`; renders initials/avatar + quote + name + company **text only (no logo)**. *This story:* add an optional `companyLogoUrl` prop + logo render. *Preserve:* the existing text-only fallback path for cards without a logo.
- **`web-frontend/src/components/organizer/EventPage/EventPage.tsx`** — *Current:* `TABS` array drives desktop tabs + mobile bottom-nav (overview, speakers, venue, participants, publishing, newsletter, registrant-notices, settings, photos). *This story:* add an `appreciation` tab. *Preserve:* tab ordering conventions + mobile nav behavior (mirror `EventNewsletterTab` wiring).
- **`services/.../controller/OrganizerThanksController.java`** + **`OrganizerThanksService`** (7.4) — *Current:* public POST + GET (count public, notes for organizer), per-(event,IP) rate limit, race-safe upsert. *This story:* add the featured-toggle + featured-list paths. *Preserve:* all 7.4 behavior (dedupe, rate limit, count-public/notes-organizer-only). The 7.4 organizer GET already returns `notes[]`; ensure each note carries `id` + featured state for the Appreciation panel.
- **`services/.../dto/ThanksNoteResponse.java`** — extend with `id` + `featured`/`featuredAt` so the organizer panel can toggle. Do not leak these on the public count GET (still `notes:null` there).

### Schema (V112)
- `ALTER TABLE organizer_thanks ADD COLUMN featured_at TIMESTAMPTZ NULL;` + partial index. A row is "featured" iff `featured_at IS NOT NULL`. Featurable iff also `thanked_by_username IS NOT NULL`.
- ADR-003: `organizer_thanks.thanked_by_username` is the meaningful cross-service ID joined to `user_profiles.username`; `companies.name` is the slug joined from `user_profiles.company_id` (NOT a UUID FK across services). `companies.logo_url` is the CloudFront URL.

### Endpoints
- Public: `GET /api/v1/thanks/featured?limit=9` → `FeaturedThanksResponse[]` (random ≤9, global). permitAll both layers.
- Organizer: `PATCH /api/v1/events/{eventCode}/thanks/{id}` `{featured}` → updated note. `@PreAuthorize` organizer, both layers role-gated (NOT permitAll).
- Exceptions: `ThanksNotFoundException` (404), `ThanksNotFeaturableException` (409) — explicit `@ExceptionHandler` each (GlobalExceptionHandler catch-all at ~L1278 would otherwise 500).

### Privacy / consent (PM-decided — recorded honestly)
- **Curation model = organizer judgment only; NO new submit-time consent checkbox.** PM rationale (Nissim, 2026-06-13): the registration-time GDPR acceptance checkbox is deemed sufficient cover for processing, and organizers curate tastefully. Public cards show **first name + company logo** (never email/username).
- The `settings_show_company` opt-out is still honored (company/logo suppressed). Only **logged-in** notes are featurable; anonymous notes can never reach the public surface. See [Open Questions](#open-questions) Q1 for the legal nuance flagged for the PM.

### Bundle boundary (frontend)
- `TestimonialSection`/`TestimonialCard` are on the public `HomePage` → **Tailwind-only, NO MUI** (perf-critical public bundle). The organizer `EventAppreciationTab` is on the MUI side — MUI is fine there.

### Gotchas
- Flyway: never modify V111; add V112. Keep `**/db/migration/**` out of any repo-wide substitution.
- `ORDER BY random()` is fine at BATbern scale (small `organizer_thanks` table); the partial index keeps the candidate set tiny. React Query staleTime (~5 min) stabilizes the shuffle so it doesn't reshuffle on every render.
- Native-query auto-flush: if the featured query runs in a context with pending writes, an auto-flush can surface unexpected state — keep the read path on its own transaction (read-only), as the Q&A read path does.
- Staging = production: integration tests must clean up `organizer_thanks` (and any seeded `user_profiles`/`companies`) rows; never leave data behind.
- i18n test resilience: assert EN values OR namespace-stripped keys; never lock a non-EN translation.

### Project Structure Notes
- Backend: migration under `.../resources/db/migration`; projection + repository under `.../repository`; DTOs under `.../dto`; exceptions under `.../exception`; controller/service under `.../controller`/`.../service`.
- Frontend public (Tailwind): `src/services/featuredThanksService.ts`, `src/hooks/useFeaturedThanks/`, edits to `src/components/public/Testimonials/{TestimonialSection,TestimonialCard}.tsx`. Frontend organizer (MUI): `src/components/organizer/EventPage/EventAppreciationTab.tsx`.

### References
- [Source: docs/prd/epic-7-attendee-experience-enhancements.md#story-74-thank-the-organizers] (FR11 appreciation wall; lines 68-69, 106, 291-345)
- [Source: _bmad-output/implementation-artifacts/7-4-thank-the-organizers.md] (predecessor — schema, controller, permitAll, organizer-only notes)
- [Source: services/event-management-service/.../repository/SessionUserRepository.java:204-215] [Source: .../repository/QnaAuthorProjection.java] [Source: .../service/SessionQnaService.java:271-300] [Source: .../dto/QnaPostResponse.java] — enrichment pattern to clone
- [Source: services/event-management-service/.../domain/OrganizerThanks.java] [Source: .../controller/OrganizerThanksController.java] [Source: .../dto/ThanksNoteResponse.java]
- [Source: services/event-management-service/.../controller/EventQnaController.java] — organizer PATCH precedent
- [Source: web-frontend/src/components/public/Testimonials/TestimonialSection.tsx] [Source: .../TestimonialCard.tsx] [Source: .../InfiniteMarquee.tsx]
- [Source: web-frontend/src/components/public/Partners/PartnerShowcaseCard.tsx] [Source: web-frontend/src/hooks/usePublicPartners.ts]
- [Source: web-frontend/src/components/public/Event/SpeakerDisplay.tsx:53-57] — logo render via buildCdnImageUrl
- [Source: web-frontend/src/pages/public/HomePage.tsx:370] [Source: web-frontend/src/components/organizer/EventPage/EventPage.tsx]
- [Source: _bmad-output/project-context.md#i18n--localization] [#bundle-boundary-no-mui-on-public-pages] [#backend-integration-tests] [#backend-gotchas]
- [Source: CLAUDE.md#database-migrations-flyway] [#localization-email-templates-de--en-only-ui-i18n-all-10-locales]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) — bmad-dev-story (Amelia), 2026-06-13.

### Debug Log References

- `FeaturedThanksIntegrationTest` (8 tests) + `OrganizerThanksIntegrationTest` (7 regression) — `BUILD SUCCESSFUL`, all PASSED (PostgreSQL/Testcontainers). One setup bug found+fixed mid-run: a second thank-you for the same (event, user) violates the V111 partial unique index → used a distinct seeded author for the unfeatured-note case.
- EMS `compileJava` + `checkstyleMain` — `BUILD SUCCESSFUL`. api-gateway `checkstyleMain` + `DomainRouterTest` (13 tests) — `BUILD SUCCESSFUL`.
- FE: `TestimonialCard` (4) + `TestimonialSection` (2) + `EventAppreciationTab` (3) — 9 PASSED (vitest). `tsc --noEmit` clean; ESLint clean on all changed files. (Partner cards render the company name only as image `alt`/initials, not body text — tests assert via `getAllByAltText`.)

### Completion Notes List

- **All 7 ACs satisfied.** Organizer ★ feature-toggle (anonymous → 409 THANKS_NOT_FEATURABLE), public global random ≤9 featured endpoint, enrichment cloning the Q&A author-portrait join, intermingled marquee (Tailwind-only), organizer Appreciation tab, 10-locale i18n, PostgreSQL ITs + FE unit tests.
- **Deviation — migration number: V115, not the story's V112.** V112–V114 were already taken by the Story 7.5 Q&A migrations; V115 is the next free number. (The story's "confirm next free number" note caught this.)
- **Deviation — single enriched query for the public path.** Rather than a separate enrich step, `OrganizerThanksRepository.findFeaturedRandom` does one native join (organizer_thanks → events → user_profiles → companies) returning `FeaturedThanksProjection`. The `INNER JOIN user_profiles` makes a deleted author drop out at the SQL level, so the random `LIMIT` always fills with live authors (cleaner than post-filtering). A separate `ThanksAuthorProjection` + batch loader enriches the organizer notes list (display name).
- **Gateway routing added.** `/api/v1/thanks/featured` is a NEW top-level path, so `DomainRouter` got `/api/v1/thanks` → event-management-service (mirroring how `/api/v1/topics`/`/api/v1/newsletter` were added). Without it the gateway would 404 the public endpoint.
- **`ThanksNoteResponse` extended** with `id` + `featured` + resolved display-name fields (`thankedByFirstName/LastName/CompanyName`) so the organizer Appreciation panel shows a friendly name and a working ★ toggle. The public count GET still returns `notes: null` (no leak).
- **Hardcoded testimonial array removed.** `TestimonialSection`'s 20 fake quotes (the "never-used footer") are gone; row 1 is now real event photos only (no fake fallback), row 2 interleaves partners + real curated thank-you notes, degrading to partners-only when nothing is featured.
- **Anti-troll guarantee preserved** (Story 7.4 AC6): only logged-in notes (`thanked_by_username NOT NULL`) are featurable; anonymous notes are excluded both at the toggle (409) and structurally in the public query. `settings_show_company=false` suppresses company name + logo (honored, with a test).

### File List

**Backend (event-management-service):**
- `src/main/resources/db/migration/V115__add_organizer_thanks_featured.sql` (new)
- `src/main/java/ch/batbern/events/domain/OrganizerThanks.java` (modified — `featuredAt`)
- `src/main/java/ch/batbern/events/repository/ThanksAuthorProjection.java` (new)
- `src/main/java/ch/batbern/events/repository/FeaturedThanksProjection.java` (new)
- `src/main/java/ch/batbern/events/repository/OrganizerThanksRepository.java` (modified — findByIdAndEventId, findFeaturedRandom, findThanksAuthorPortraitsByUsernames)
- `src/main/java/ch/batbern/events/dto/FeaturedThanksResponse.java` (new)
- `src/main/java/ch/batbern/events/dto/ThanksFeaturePatchRequest.java` (new)
- `src/main/java/ch/batbern/events/dto/ThanksNoteResponse.java` (modified — +id, +display name, +featured)
- `src/main/java/ch/batbern/events/exception/ThanksNotFoundException.java` (new)
- `src/main/java/ch/batbern/events/exception/ThanksNotFeaturableException.java` (new)
- `src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` (modified — +2 handlers)
- `src/main/java/ch/batbern/events/service/OrganizerThanksService.java` (modified — getFeaturedThanks, setFeatured, enriched getThanks)
- `src/main/java/ch/batbern/events/controller/OrganizerThanksController.java` (modified — +GET featured, +PATCH toggle)
- `src/main/java/ch/batbern/events/config/SecurityConfig.java` (modified — +1 permitAll)
- `src/test/java/ch/batbern/events/controller/FeaturedThanksIntegrationTest.java` (new — 8 tests)

**API Gateway:**
- `src/main/java/ch/batbern/gateway/config/SecurityConfig.java` (modified — +1 permitAll)
- `src/main/java/ch/batbern/gateway/routing/DomainRouter.java` (modified — +/api/v1/thanks route)

**API contract:**
- `docs/api/events-api.openapi.yml` (modified — +2 paths, +2 schemas, ThanksNoteResponse extended)

**Frontend (web-frontend):**
- `src/services/featuredThanksService.ts` (new)
- `src/services/thanksService.ts` (modified — setThanksFeatured)
- `src/hooks/useFeaturedThanks/useFeaturedThanks.ts` (new)
- `src/hooks/useThanks/useThanks.ts` (modified — useEventThanks, useSetThanksFeatured)
- `src/components/public/Testimonials/TestimonialCard.tsx` (modified — company logo + badge)
- `src/components/public/Testimonials/TestimonialSection.tsx` (modified — interleave + removed hardcoded array)
- `src/components/organizer/EventPage/EventAppreciationTab.tsx` (new)
- `src/components/organizer/EventPage/EventPage.tsx` (modified — Appreciation tab)
- `src/components/public/Navigation/ThankOrganizersNavButton.tsx` (modified — public-display notice for logged-in users, Q1)
- `src/types/generated/events-api.types.ts` (regenerated)
- `src/components/public/Testimonials/__tests__/TestimonialCard.test.tsx` (new)
- `src/components/public/Testimonials/__tests__/TestimonialSection.test.tsx` (new)
- `src/components/organizer/EventPage/__tests__/EventAppreciationTab.test.tsx` (new)
- `public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json` (modified — thanks.marquee.* + eventPage.tabs.appreciation + appreciation.*)

### Change Log

| Date | Change |
|------|--------|
| 2026-06-13 | Story 7.7 implemented (Amelia / bmad-dev-story). Curated featured thank-you notes intermingled into the public partner marquee: V115 `featured_at` column, organizer ★ PATCH toggle (anonymous → 409), public global random `GET /thanks/featured` enriched via a Q&A-pattern cross-service join, organizer Appreciation tab, Tailwind-only marquee card with company logo, 10-locale i18n. 8 backend ITs + 9 FE unit tests green; tsc/ESLint/checkstyle clean. Status → review. |
| 2026-06-13 | Open-questions resolved in-code (PM Nissim): Q1 quiet public-display notice (`thanks.widget.publicNotice` ×10) for logged-in users in `ThankOrganizersNavButton`; Q2 per-company cap (1 card/company/fetch) via `DISTINCT ON` in `findFeaturedRandom` + IT `should_capOneCardPerCompany` (9 backend ITs total); Q3 silent-omit confirmed. Nav-button suite 7/7 still green. Also seeded local-dev demo data: 80 `organizer_thanks` rows across the last 10 events (10 featured across distinct companies) for visual verification. |

## Resolved Decisions

_Resolved with the PM (Nissim) 2026-06-13._

1. **Curation model = organizer judgment only.** No new submit-time consent checkbox. The registration-time GDPR acceptance checkbox is deemed sufficient cover; organizers feature tastefully. Public cards show first name + company logo. (`settings_show_company` opt-out still honored; anonymous notes never featurable.)
2. **Eligibility = logged-in notes only.** Anonymous notes (`thanked_by_username IS NULL`) are structurally excluded from the public surface — this preserves Story 7.4 AC6's anti-troll guarantee while opening a curated, named subset.
3. **Pool scope = global, cross-event.** The public marquee draws up to 9 random featured notes from all events and shows on every public HomePage (including upcoming events) as registration social proof — not gated to post-event/archive pages.
4. **Placement = intermingled into the existing partner marquee.** Reactivates the dormant `TestimonialSection` shell (replacing its hardcoded array) rather than adding a new vertical section — zero added page real estate, non-intrusive.

## Open Questions

> **All three RESOLVED with the PM (Nissim) 2026-06-13 — folded into the code.** Q1 → added a quiet public-display notice (`thanks.widget.publicNotice`, logged-in only, 10 locales) next to the thank-you input; no profile opt-out gate. Q2 → **cap 1 card per company** per marquee fetch (`findFeaturedRandom` is now `DISTINCT ON` a per-company key, username-fallback so company-less authors aren't collapsed; +IT `should_capOneCardPerCompany`). Q3 → silent omit / degrade confirmed (already implemented). The original questions are retained below for the decision record.

1. **Is registration-time GDPR consent really enough to publish a name + employer logo?** The PM chose "organizer judgment only," reasoning that the GDPR checkbox accepted at registration covers it. That checkbox covers *processing* registration data; *publishing* an attendee's first name next to their employer's logo on the public homepage is arguably a new, more visible use that the attendee didn't specifically opt into when they left a thank-you under 7.4's "organizer-only" promise. This is almost certainly low-risk in practice (first name only, organizer-curated, company opt-out honored), but if you want belt-and-suspenders we could add a tiny "shown publicly with your name" notice next to the thank-you input going forward, or a one-line opt-out in the profile. Flagging the nuance so the decision is on record, not to reopen it.

2. **Should there be a cap so the marquee isn't dominated by one company?** With a global random pool of 9, a single large firm whose employees leave many notes could statistically crowd the rotation. Do you want a per-company cap (e.g. at most 1-2 cards per company per fetch), or is the small scale of BATbern enough that a plain random-9 is fine? A per-company cap is a small query change if you want it, but it adds complexity we can skip if you're comfortable with pure random.

3. **What happens to a featured note if the author later deletes their account or hides their company?** The enrichment join is null-safe: a note whose author no longer has a `user_profiles` row is silently omitted from the public response, and if the author flips `settings_show_company` off, the company name + logo drop to null and the card renders name-only. So nothing breaks and nothing stale is shown — but confirm this "silently disappears / degrades" behavior is what you want, versus, say, keeping the note with a generic placeholder. Current plan: silent omit / graceful degrade, no organizer notification.
