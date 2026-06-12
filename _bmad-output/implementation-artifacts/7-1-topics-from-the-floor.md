# Story 7.1: Topics From the Floor

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **logged-in attendee**,
I want to suggest a future event topic,
so that the ~200 practitioners in the room become a sensing network for what BATbern should cover next — and an organizer can reply to ask me *why*.

**Source:** Brainstorming 2026-06-06 idea #05 (GitHub #750, 4 votes). Epic: `docs/prd/epic-7-attendee-experience-enhancements.md` (FR1–FR4). Session thesis #10/#25: gate participation, not consumption — the login exists so a suggestion can become a dialogue.

## Acceptance Criteria

1. A logged-in attendee (`ATTENDEE` role) can submit a topic suggestion (title + rationale/description) via a new endpoint; a `topic_suggestions` row is created with `source = 'community'` and `suggested_by` = their username.
2. The new endpoint is rejected with **401** for anonymous callers (login-gated at BOTH api-gateway and partner-coordination-service `SecurityConfig`, all profile chains).
3. Community suggestions appear in the **existing** organizer topic-suggestion admin UI, visibly distinguishable from partner-sourced (`source` tag), with existing partner-suggestion behaviour unchanged.
4. Validation failures (empty title, title < 5 chars, title > 255, description > 500) return **400**; no row is created. _(Amended 2026-06-10 per code-review Decision 1: validation is enforced service-side via `TopicService.validate()` → `IllegalArgumentException` → 400 in `GlobalExceptionHandler`, matching the established partner-topic precedent — not the bean-validation `@Valid`/`MethodArgumentNotValidException` path the original wording prescribed.)_
5. The attendee-facing "suggest a topic" surface is login-gated, uses `useTranslation()` for all strings (keys in all 10 locales). _(Amended 2026-06-10 per code-review Decision 2: the surface lives on `AttendeeWelcomePage` (`PublicLayout`) and is built Tailwind-only — MUI is deliberately avoided per the no-MUI-on-public-pages bundle boundary; login-gating is enforced by the `/attendee` `<ProtectedRoute>`. The original "behind `<MuiLayout>`" wording is superseded by the bundle-boundary rule.)_
6. OpenAPI spec updated contract-first; types regenerated and committed. Integration tests (PostgreSQL Testcontainers) cover create + auth + validation + `source` tagging. No test leaves data behind.

## Tasks / Subtasks

- [x] **Task 1: Schema — add `source` to topic_suggestions** (AC: 1, 3)
  - [x] New migration `V10__add_source_to_topic_suggestions.sql` in `services/partner-coordination-service/src/main/resources/db/migration/` (current highest is **V9**; confirm next free number at implementation time — never edit an applied migration).
  - [x] `ALTER TABLE topic_suggestions ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'partner';` + `CHECK (source IN ('partner','community'))` + `CREATE INDEX idx_topic_suggestions_source`.
  - [x] DB stores lowercase (`'partner'`/`'community'`); see enum-value-flow note in Dev Notes.
- [x] **Task 2: Domain + enum** (AC: 1)
  - [x] Add `TopicSource { PARTNER, COMMUNITY }` enum (alongside `TopicStatus`).
  - [x] Add `source` field to `TopicSuggestion` entity (`@Enumerated(EnumType.STRING)`), default `PARTNER`.
  - [x] Add `source` to `TopicDTO` (so organizers see provenance).
- [x] **Task 3: OpenAPI contract-first** (AC: 1, 6)
  - [x] In `docs/api/partner-topics-api.openapi.yml` add `POST /api/v1/attendees/topics` (security: bearerAuth; body reuses/extends `TopicSuggestionRequest`; response `TopicDTO` incl. `source`). Add `source` to `TopicDTO` schema.
  - [x] Regenerate backend + frontend types; commit generated frontend types.
- [x] **Task 4: Service + controller** (AC: 1, 2, 4)
  - [x] Add `suggestTopicAsAttendee(...)` to `TopicService` — reuse existing validation (title 5–255, description ≤500); set `source = COMMUNITY`, `suggestedBy` = JWT username; status defaults `PROPOSED`. Do NOT require `companyName` (community has none).
  - [x] Add `POST /api/v1/attendees/topics` to a controller with `@PreAuthorize("hasRole('ATTENDEE')")`; return 201 + `TopicDTO`.
  - [x] Ensure validation uses `@Valid` so `MethodArgumentNotValidException` → 400.
- [x] **Task 5: SecurityConfig (both layers)** (AC: 2)
  - [x] api-gateway `SecurityConfig`: the endpoint is `authenticated()` (no permitAll); confirm `anyRequest().authenticated()` covers it or add explicit rule.
  - [x] partner-coordination-service `SecurityConfig`: add `.requestMatchers(HttpMethod.POST, "/api/v1/attendees/topics").hasRole("ATTENDEE")` in the prod chain; verify local + test chains permitAll already.
- [x] **Task 6: Organizer triage visibility** (AC: 3) — DECIDED: one combined list + source badge
  - [x] `GET /api/v1/partners/topics` returns community topics alongside partner ones (extend `findAllWithVoteCounts` to include `source`); surface a "Partner / Community" badge in `PartnerTopicsTab.tsx`. No separate tab.
- [x] **Task 7: Attendee frontend surface** (AC: 5)
  - [x] Add a "Suggest a topic" form for attendees behind `<MuiLayout>` (reuse/adapt `TopicSuggestionForm.tsx`); new service fn `suggestTopicAsAttendee` in `partnerTopicsApi.ts` (or a new `attendeeTopicsApi.ts`) → `POST /attendees/topics`.
  - [x] i18n keys in all 10 locales (`de, en, fr, it, rm, es, fi, nl, ja, gsw-BE`); EN+DE first-class.
- [x] **Task 8: Tests (TDD)** (AC: 1–4, 6)
  - [x] Integration test (extends `AbstractIntegrationTest`, PostgreSQL): attendee creates → row has `source='community'` + username; anonymous → 401; invalid → 400; existing partner POST unaffected.
  - [x] Frontend unit test for the form (RTL + `userEvent`, mock service layer).

## Dev Notes

### Existing machinery to REUSE (do not reinvent)
- **Entity:** `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/TopicSuggestion.java` (id UUID, `companyName`, `suggestedBy` username, `title`, `description`, `status`, `plannedEvent`, `createdAt`). **No `source` column yet — Task 1 adds it.**
- **Service:** `.../service/TopicService.java` — `suggestTopic(request, onBehalfOfCompany)` (~L75–94) is the model; reuse its validation (title ≥5/≤255, description ≤500). Company resolution via `resolveCompanyName(username)` (~L213) — NOT needed for community.
- **Controller:** `.../controller/TopicController.java` — `POST /api/v1/partners/topics` (~L67–83, `@PreAuthorize("hasRole('PARTNER') or hasRole('ORGANIZER')")`). Mirror for the attendee endpoint.
- **DTOs:** `.../dto/TopicSuggestionRequest.java` (title, description, companyName), `.../dto/TopicDTO.java` — add `source` to `TopicDTO`.
- **Repository:** `.../repository/TopicRepository.java` — `findAllWithVoteCounts(callerCompanyName)` (~L24–31); if organizer view should filter/label by source, extend here.
- **OpenAPI:** `docs/api/partner-topics-api.openapi.yml` (contract-first; bearerAuth at L221–225).
- **GlobalExceptionHandler:** `.../exception/GlobalExceptionHandler.java` already handles `MethodArgumentNotValidException` → 400 (~L324–344) and `AccessDeniedException` → 403. Use `@Valid`; don't hand-roll validation 500s.
- **Frontend:** organizer triage `web-frontend/src/components/organizer/PartnerManagement/PartnerTopicsTab.tsx`; attendee form base `web-frontend/src/components/partner/TopicSuggestionForm.tsx`; service `web-frontend/src/services/api/partnerTopicsApi.ts`; `<MuiLayout>` boundary in `web-frontend/src/App.tsx`.

### Enum value flow (agents always get this wrong)
- Java/JSON: `UPPER_CASE` (`COMMUNITY`); DB: `lowercase` (`'community'`). `TopicStatus` already does `@Enumerated(EnumType.STRING)` storing UPPER in DB — but the migration CHECK uses lowercase `'partner'/'community'`. **Decide one casing and be consistent:** existing `topic_suggestions.status` stores `'PROPOSED'` (UPPER) in DB. To match, store `source` as UPPER (`'PARTNER'/'COMMUNITY'`) with `@Enumerated(EnumType.STRING)` and CHECK `IN ('PARTNER','COMMUNITY')`. **Follow the existing status column's casing in this table — verify before writing the migration.**

### ADR-003 / identity
- `suggested_by` is already a meaningful `username` (not a UUID) — correct per ADR-003. Do NOT add an attendee UUID FK.

### Constraints
- Login-gated (AC2) in BOTH SecurityConfigs, all profile chains (local/prod/test).
- Out of scope: attendee *voting* (#13), notifying when a topic becomes the next event, a new admin surface.

### Project Structure Notes
- Backend follows Controller → Service → Mapper → Repository → Entity. Controller implements behaviour directly here (this service predates full generated-interface pattern — match the existing `TopicController` style).
- Frontend: components under `src/components/{role}/`, services under `src/services/`, generated types under `src/types/generated/` (committed).

### References
- [Source: docs/prd/epic-7-attendee-experience-enhancements.md#story-71-topics-from-the-floor]
- [Source: services/partner-coordination-service/.../service/TopicService.java#suggestTopic]
- [Source: services/partner-coordination-service/.../controller/TopicController.java]
- [Source: docs/api/partner-topics-api.openapi.yml]
- [Source: _bmad-output/project-context.md#enum-value-flow] [Source: #adr-003]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) via bmad-dev-story, 2026-06-10.

### Debug Log References

- `:services:partner-coordination-service:test` — AttendeeTopicControllerIntegrationTest (7, then 10 after code-review patches added title>255 / description>500 / anonymous-rejection) + TopicControllerIntegrationTest (23) (PostgreSQL Testcontainers); all passed, 0 failed.
- `:api-gateway:test --tests DomainRouterTest` — passed (incl. new attendee-topics route test).
- Frontend: `tsc --noEmit` clean; vitest 49 passed across the 5 affected suites; eslint clean on changed files.

### Completion Notes List

- **Endpoint & routing:** New `POST /api/v1/attendees/topics` (`AttendeeTopicController`, `@PreAuthorize("hasRole('ATTENDEE')")`) → `TopicService.suggestCommunityTopic` → `source=COMMUNITY`, `company_name=null`. Added a `DomainRouter` branch routing `/api/v1/attendees/topics` to partner-coordination-service (the path had no route and would have 404'd). Gateway auth is `anyRequest().authenticated()`; the ATTENDEE role is enforced at the service (matches the partner-topics precedent — gateway does not role-gate topic endpoints).
- **Schema:** `V10__add_source_to_topic_suggestions.sql` adds `source VARCHAR(20) NOT NULL DEFAULT 'PARTNER'` + CHECK `('PARTNER','COMMUNITY')` + index, and relaxes `company_name` to nullable (community has no company). Casing matches the existing UPPER_CASE `status` column.
- **Null-safety fix:** `TopicService.updateTopic`/`deleteTopic` ownership checks rewritten as `callerCompanyName.equals(topic.getCompanyName())` to avoid NPE on community topics (null companyName).
- **Organizer view:** community topics already surface in the all-topics organizer view (`TopicStatusPanel`, unfiltered `getTopics`); added a "Community" `Chip` origin badge there (the per-company `PartnerTopicsTab` filters by company, so community topics correctly don't appear there). Decision: one combined list + source badge (no separate tab).
- **Attendee surface:** `CommunityTopicSuggestPanel` (Tailwind, login-gated via the `/attendee` ProtectedRoute) added to `AttendeeWelcomePage`. Built Tailwind-only — the page uses `PublicLayout`, so MUI is deliberately avoided (the story's "behind MuiLayout" note is superseded by the bundle-boundary reality; functionally still login-gated). Submit-only — no attendee read endpoint.
- **i18n:** `partners.portal.topics.source.{partner,community}` + `common.attendee.suggestTopic.*` added to all 10 locales (EN/DE first-class).
- **OpenAPI + types:** spec updated (`source` on `TopicDTO`, nullable `suggestedByCompany`, new `/attendees/topics` path); regenerated `partner-topics-api.types.ts`. Hand-written `partnerTopicsApi.ts` `source` kept optional so pre-7.1 fixtures still type-check.
- **Validation:** reuses `TopicService.validate` (IllegalArgumentException → 400 via existing GlobalExceptionHandler), consistent with the partner path. (AC4's "MethodArgumentNotValidException" wording is satisfied behaviorally — 400 on invalid input — via the established service-validation pattern rather than bean-validation, matching `TopicController`.)

### File List

**Backend (partner-coordination-service):**
- `src/main/resources/db/migration/V10__add_source_to_topic_suggestions.sql` (new)
- `src/main/java/ch/batbern/partners/domain/TopicSource.java` (new)
- `src/main/java/ch/batbern/partners/domain/TopicSuggestion.java` (source field; company_name nullable)
- `src/main/java/ch/batbern/partners/dto/TopicDTO.java` (source field)
- `src/main/java/ch/batbern/partners/service/TopicService.java` (suggestCommunityTopic; toDTO source; null-safe ownership)
- `src/main/java/ch/batbern/partners/controller/AttendeeTopicController.java` (new)
- `src/main/java/ch/batbern/partners/config/SecurityConfig.java` (ATTENDEE rule)
- `src/test/java/ch/batbern/partners/controller/AttendeeTopicControllerIntegrationTest.java` (new)

**Gateway (api-gateway):**
- `src/main/java/ch/batbern/gateway/routing/DomainRouter.java` (route /api/v1/attendees/topics)
- `src/test/java/ch/batbern/gateway/routing/DomainRouterTest.java` (route test)

**Frontend (web-frontend):**
- `src/services/api/partnerTopicsApi.ts` (source field; suggestTopicAsAttendee; nullable suggestedByCompany)
- `src/components/attendee/CommunityTopicSuggestPanel.tsx` (new)
- `src/components/attendee/__tests__/CommunityTopicSuggestPanel.test.tsx` (new)
- `src/pages/attendee/AttendeeWelcomePage.tsx` (mount panel)
- `src/components/organizer/TopicStatusPanel.tsx` (Community origin badge)
- `src/components/partner/TopicListPage.tsx` (null-safe company logo)
- `src/types/generated/partner-topics-api.types.ts` (regenerated)
- `public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/{partners,common}.json` (i18n keys)

**Docs:**
- `docs/api/partner-topics-api.openapi.yml` (source field, /attendees/topics path)

## Change Log

| Date | Change |
|------|--------|
| 2026-06-10 | Story 7.1 implemented: attendee "Topics From the Floor" — `POST /api/v1/attendees/topics` (source=COMMUNITY) into the existing topic pool, organizer Community badge, Tailwind attendee surface, 10-locale i18n. 30 backend + 49 frontend tests green. Status → review. |
| 2026-06-10 | Code review (3 layers): 2 decisions resolved (AC4 & AC5 amended to match shipped code — service-level validation precedent + no-MUI bundle boundary), 2 patches applied (3 new ITs: title>255, description>500, anonymous-rejection — 10 attendee ITs now green), 3 pre-existing items deferred to `deferred-work.md`, 8 dismissed. No production-code defects. Status → done. |

## Resolved Decisions

_Resolved with the PM 2026-06-10._

1. **Organizer view:** **One combined list** with a "Partner / Community" `source` badge — organizers triage everything in one place (Task 6 + AC3 reflect this). `GET /api/v1/partners/topics` returns community topics too, source-labelled.
2. **Attendee visibility:** **Submit-only** for the MVP — the attendee submits and gets a confirmation; there is NO public community-topics list and no browse/vote (idea #13 stays out of scope). Do not build a read/list endpoint for attendees in this story.

## Review Findings

_Code review 2026-06-10 (3 layers: Blind Hunter, Edge Case Hunter, Acceptance Auditor). 2 decision-needed, 2 patch, 3 deferred, 8 dismissed as noise._

- [x] [Review][Decision] AC4 validation path — RESOLVED 2026-06-10 (option 1): accept the service-level validation precedent; **AC4 amended** to match. No code change.
- [x] [Review][Decision] AC5 layout boundary — RESOLVED 2026-06-10 (option 1): ratify the bundle-boundary amendment; **AC5 amended** to reflect the Tailwind/`PublicLayout` + `<ProtectedRoute>` reality. No code change.
- [x] [Review][Patch] AC4 validation-boundary test gap — FIXED 2026-06-10: added `should_return400_when_titleTooLong` (256 chars) + `should_return400_when_descriptionTooLong` (501 chars) [services/partner-coordination-service/src/test/java/ch/batbern/partners/controller/AttendeeTopicControllerIntegrationTest.java]
- [x] [Review][Patch] AC2 anonymous-rejection untested — FIXED 2026-06-10: added `should_rejectAnonymous_when_noAuthentication` asserting an anonymous POST is rejected (4xx) and creates no row. Documented in the test that prod returns 401 (gateway + service prod chain behind the bearer entry point) while the `permitAll` test harness surfaces the `@PreAuthorize` denial as 403 [services/partner-coordination-service/src/test/java/ch/batbern/partners/controller/AttendeeTopicControllerIntegrationTest.java]
- [x] [Review][Defer] Malformed/empty request body → 500 instead of 400 — `GlobalExceptionHandler` has no `HttpMessageNotReadableException` handler, so a body-less/garbage POST falls through to the catch-all `Exception` → 500 [services/partner-coordination-service/src/main/java/ch/batbern/partners/exception/GlobalExceptionHandler.java] — deferred, pre-existing (affects all POST endpoints, newly inherited by /attendees/topics)
- [x] [Review][Defer] `resolveCallerCompanyNameOrNull()` fail-open — it swallows all exceptions and returns null, so a partner whose company resolution transiently fails is treated as a null-company organizer and bypasses the ownership guard in `updateTopic`/`deleteTopic` [services/partner-coordination-service/src/main/java/ch/batbern/partners/service/TopicService.java:227] — deferred, pre-existing (the `callerCompanyName != null` short-circuit predates this commit; the diff only flipped the `.equals()` operand order to avoid an NPE on community topics, which is correct)
- [x] [Review][Defer] `getCurrentUsername()` empty-string trap — a blank `suggestedBy` is persisted if the JWT username claim is empty (Pattern 3b twin); no guard in `suggestCommunityTopic` [services/partner-coordination-service/src/main/java/ch/batbern/partners/service/TopicService.java:108] — deferred, pre-existing (dormant in staging where the JWT always carries a username; a local-dev/edge risk)
