# Story 7.1: Topics From the Floor

Status: ready-for-dev

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
4. Validation failures (empty title, title < 5 chars, title > 255, description > 500) return **400** via the explicit `MethodArgumentNotValidException` handler; no row is created.
5. The attendee-facing "suggest a topic" surface lives behind the `<MuiLayout>` boundary (MUI permitted) and uses `useTranslation()` for all strings, with keys added to all 10 locales.
6. OpenAPI spec updated contract-first; types regenerated and committed. Integration tests (PostgreSQL Testcontainers) cover create + auth + validation + `source` tagging. No test leaves data behind.

## Tasks / Subtasks

- [ ] **Task 1: Schema — add `source` to topic_suggestions** (AC: 1, 3)
  - [ ] New migration `V10__add_source_to_topic_suggestions.sql` in `services/partner-coordination-service/src/main/resources/db/migration/` (current highest is **V9**; confirm next free number at implementation time — never edit an applied migration).
  - [ ] `ALTER TABLE topic_suggestions ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'partner';` + `CHECK (source IN ('partner','community'))` + `CREATE INDEX idx_topic_suggestions_source`.
  - [ ] DB stores lowercase (`'partner'`/`'community'`); see enum-value-flow note in Dev Notes.
- [ ] **Task 2: Domain + enum** (AC: 1)
  - [ ] Add `TopicSource { PARTNER, COMMUNITY }` enum (alongside `TopicStatus`).
  - [ ] Add `source` field to `TopicSuggestion` entity (`@Enumerated(EnumType.STRING)`), default `PARTNER`.
  - [ ] Add `source` to `TopicDTO` (so organizers see provenance).
- [ ] **Task 3: OpenAPI contract-first** (AC: 1, 6)
  - [ ] In `docs/api/partner-topics-api.openapi.yml` add `POST /api/v1/attendees/topics` (security: bearerAuth; body reuses/extends `TopicSuggestionRequest`; response `TopicDTO` incl. `source`). Add `source` to `TopicDTO` schema.
  - [ ] Regenerate backend + frontend types; commit generated frontend types.
- [ ] **Task 4: Service + controller** (AC: 1, 2, 4)
  - [ ] Add `suggestTopicAsAttendee(...)` to `TopicService` — reuse existing validation (title 5–255, description ≤500); set `source = COMMUNITY`, `suggestedBy` = JWT username; status defaults `PROPOSED`. Do NOT require `companyName` (community has none).
  - [ ] Add `POST /api/v1/attendees/topics` to a controller with `@PreAuthorize("hasRole('ATTENDEE')")`; return 201 + `TopicDTO`.
  - [ ] Ensure validation uses `@Valid` so `MethodArgumentNotValidException` → 400.
- [ ] **Task 5: SecurityConfig (both layers)** (AC: 2)
  - [ ] api-gateway `SecurityConfig`: the endpoint is `authenticated()` (no permitAll); confirm `anyRequest().authenticated()` covers it or add explicit rule.
  - [ ] partner-coordination-service `SecurityConfig`: add `.requestMatchers(HttpMethod.POST, "/api/v1/attendees/topics").hasRole("ATTENDEE")` in the prod chain; verify local + test chains permitAll already.
- [ ] **Task 6: Organizer triage visibility** (AC: 3) — DECIDED: one combined list + source badge
  - [ ] `GET /api/v1/partners/topics` returns community topics alongside partner ones (extend `findAllWithVoteCounts` to include `source`); surface a "Partner / Community" badge in `PartnerTopicsTab.tsx`. No separate tab.
- [ ] **Task 7: Attendee frontend surface** (AC: 5)
  - [ ] Add a "Suggest a topic" form for attendees behind `<MuiLayout>` (reuse/adapt `TopicSuggestionForm.tsx`); new service fn `suggestTopicAsAttendee` in `partnerTopicsApi.ts` (or a new `attendeeTopicsApi.ts`) → `POST /attendees/topics`.
  - [ ] i18n keys in all 10 locales (`de, en, fr, it, rm, es, fi, nl, ja, gsw-BE`); EN+DE first-class.
- [ ] **Task 8: Tests (TDD)** (AC: 1–4, 6)
  - [ ] Integration test (extends `AbstractIntegrationTest`, PostgreSQL): attendee creates → row has `source='community'` + username; anonymous → 401; invalid → 400; existing partner POST unaffected.
  - [ ] Frontend unit test for the form (RTL + `userEvent`, mock service layer).

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

### Debug Log References

### Completion Notes List

### File List

## Resolved Decisions

_Resolved with the PM 2026-06-10._

1. **Organizer view:** **One combined list** with a "Partner / Community" `source` badge — organizers triage everything in one place (Task 6 + AC3 reflect this). `GET /api/v1/partners/topics` returns community topics too, source-labelled.
2. **Attendee visibility:** **Submit-only** for the MVP — the attendee submits and gets a confirmation; there is NO public community-topics list and no browse/vote (idea #13 stays out of scope). Do not build a read/list endpoint for attendees in this story.
