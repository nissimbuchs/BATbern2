# Story 8.2: Topic Suggestions & Voting

Status: done

## Story

As a **partner**,
I want to suggest topics for future BATbern events and vote on other partners' suggestions,
so that events cover subjects my company cares about.

## Acceptance Criteria

1. **AC1 - Topic List**: Partners see all proposed topics with title, description, suggesting company, and current vote count. Sorted by vote count descending.

2. **AC2 - Simple Vote**: Partner gives one vote per topic (toggle on/off). No weighting, no allocation, no tiers. One partner = one vote per topic.

3. **AC3 - Suggest Topic**: Partner submits a new topic with title and short description. No mandatory business justification.

4. **AC4 - Organizer Review**: Organizer sees all topics ranked by vote count and can set status to **Selected** or **Declined** per topic.

5. **AC5 - Status Visibility**: All partners can see topic status: **Proposed** / **Selected** / **Declined**. Selected topics show which event they are planned for (free text field, organizer fills in).

6. **AC6 - Role-Based Access**: Partners submit suggestions and vote. Organizers review and update status. Partners cannot change status.

7. **AC7 - i18n**: All UI text in German (primary) and English (secondary).

8. **AC8 - Performance**: Page loads in <3 seconds (P95).

## Prerequisites

**Story 8.0 (Partner Portal Shell) must be complete before this story.**

Story 8.0 creates:
- `PartnerPortalLayout` with the **Topics** nav tab linking to `/partners/topics`
- `PartnerTopicsPlaceholder` at `/partners/topics` — this story deletes that file and replaces it
- The organizer view needs a separate entry point — see Task routing note below

## What was deliberately cut

| Removed | Reason |
|---|---|
| Tier-based vote weighting | 5–10 partners, simple count is fair enough |
| 30% influence cap algorithm | Not needed without weighting |
| Drag-and-drop priority ranking | Overkill — sort by vote count is enough |
| Voting deadline + countdown timer | No formal voting period needed |
| Live results / real-time polling | Page load is sufficient |
| Historical voting trends | Not needed |
| Impact metrics (engagement for past topics) | Not needed |
| EventBridge integration | Organizer reads the list directly, no event needed |
| Business justification + strategic alignment fields | Title + description is enough |
| Suggestion review workflow states (UNDER_REVIEW) | Proposed → Selected/Declined is enough |
| Consensus building percentage | Not needed |
| react-beautiful-dnd dependency | Removed with drag-and-drop |

## Tasks / Subtasks

### Task 1: Check existing DB schema (AC: ALL)

- [x] Review `partner-coordination-service` Flyway migrations for existing `topic_votes` and `topic_suggestions` tables (created in Story 2.7)
- [x] If tables already exist and match the schema below — no migration needed
- [x] If tables are missing or schema differs, create migration `V8.2.1__update_topic_tables.sql`:

```sql
-- topic_suggestions: one row per suggested topic
CREATE TABLE IF NOT EXISTS topic_suggestions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name  VARCHAR(255) NOT NULL,       -- ADR-003: meaningful ID
    suggested_by  VARCHAR(100) NOT NULL,        -- username
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    status        VARCHAR(50)  NOT NULL DEFAULT 'PROPOSED', -- PROPOSED | SELECTED | DECLINED
    planned_event VARCHAR(100),                -- e.g. "BATbern58" (organizer fills in when selecting)
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- topic_votes: one row per partner per topic (toggle on = row exists, toggle off = row deleted)
CREATE TABLE IF NOT EXISTS topic_votes (
    topic_id      UUID         NOT NULL REFERENCES topic_suggestions(id) ON DELETE CASCADE,
    company_name  VARCHAR(255) NOT NULL,       -- ADR-003: meaningful ID
    voted_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (topic_id, company_name)       -- one vote per partner per topic
);

CREATE INDEX IF NOT EXISTS idx_topic_suggestions_status ON topic_suggestions(status);
CREATE INDEX IF NOT EXISTS idx_topic_votes_topic ON topic_votes(topic_id);
```

### Task 2: OpenAPI Specification (AC: ALL — ADR-006)

- [x] Update `docs/api/partner-analytics-api.openapi.yml` (or create `partner-topics-api.openapi.yml`)
- [x] Define endpoints:
  - `GET  /api/v1/partners/topics` — list all topics with vote counts (PARTNER + ORGANIZER)
  - `POST /api/v1/partners/topics` — suggest a new topic (PARTNER)
  - `POST /api/v1/partners/topics/{topicId}/vote` — toggle vote on (PARTNER)
  - `DELETE /api/v1/partners/topics/{topicId}/vote` — toggle vote off (PARTNER)
  - `PATCH /api/v1/partners/topics/{topicId}/status` — update status (ORGANIZER only)
- [x] Define DTOs: `TopicDTO`, `TopicSuggestionRequest`, `TopicStatusUpdateRequest`
- [x] Generate TypeScript types: `npm run generate:api-types:partner-topics` (new script added; generates `partner-topics-api.types.ts`)

### Task 3: TopicService (AC: 1–6)

- [x] Create `TopicService.java` in partner-coordination-service
- [x] `getAllTopics(String currentCompanyName): List<TopicDTO>` — query topics + vote counts, mark which ones current partner has voted for
- [x] `suggestTopic(String companyName, String username, TopicSuggestionRequest req)` — insert into topic_suggestions
- [x] `castVote(UUID topicId, String companyName)` — insert into topic_votes (ignore if already voted)
- [x] `removeVote(UUID topicId, String companyName)` — delete from topic_votes (ignore if not voted)
- [x] `updateStatus(UUID topicId, String status, String plannedEvent)` — ORGANIZER only; validate status is SELECTED or DECLINED

### Task 4: TopicController (AC: 6, 8)

- [x] Create `TopicController.java`
- [x] Implement all 5 endpoints from OpenAPI spec
- [x] `@PreAuthorize` on status update: `hasRole('ORGANIZER')`
- [x] `@PreAuthorize` on vote/suggest: `hasRole('PARTNER')`
- [x] Add Micrometer timing

### Task 5: SecurityConfig Update (AC: 6)

- [x] Add topic endpoints to `SecurityConfig.java`
  - `GET  /api/v1/partners/topics` → PARTNER, ORGANIZER
  - `POST /api/v1/partners/topics` → PARTNER
  - `POST/DELETE /api/v1/partners/topics/*/vote` → PARTNER
  - `PATCH /api/v1/partners/topics/*/status` → ORGANIZER

### Task 6: i18n Keys (AC: 7)

- [x] Add keys to `public/locales/de/partner.json` and `en/partner.json`
  - `partner.topics.title`, `partner.topics.suggest`, `partner.topics.vote`, `partner.topics.unvote`
  - `partner.topics.status.proposed`, `.selected`, `.declined`
  - `partner.topics.plannedFor`, `partner.topics.empty`
  - `partner.topics.form.title`, `.description`, `.submit`

### Task 6b: Wire into Partner Portal and Organizer nav (AC: 6)

**Partner portal (Story 8.0 integration):**
- [x] **Delete** `src/pages/PartnerTopicsPlaceholder.tsx` (created by Story 8.0)
- [x] In `App.tsx`, replace `PartnerTopicsPlaceholder` with `TopicListPage`:
  ```tsx
  // BEFORE (Story 8.0 placeholder)
  <Route path="topics" element={<PartnerTopicsPlaceholder />} />
  // AFTER (this story)
  <Route path="topics" element={<TopicListPage />} />
  ```
- [x] `TopicListPage` renders with partner role behaviour (vote/suggest enabled, status-change hidden)

**Organizer access:**
- [x] Add `/organizer/partner-topics` route in `App.tsx` → renders `TopicStatusPanel` (note: `/organizer/topics` is already Story 5.2 event topics; used `/organizer/partner-topics` to avoid collision)
- [x] Add "Partner Topics" entry to organizer nav (`navigationConfig.ts` + `navigation.partnerTopics` i18n key)

### Task 7: Topic List Page (AC: 1, 2, 5, 7, 8)

- [x] Create `src/components/partner/TopicListPage.tsx`
- [x] MUI `List` — one row per topic: title, description, company, votes, status badge, vote button
- [x] Vote button: filled ThumbUp when voted, outlined when not. Calls POST or DELETE on click.
- [x] Status badge: chip with colour — grey (Proposed), green (Selected), red (Declined)
- [x] Selected topics show `plannedEvent` field if set (e.g. "BATbern58")
- [x] "Suggest a Topic" button opens the suggestion form (modal)
- [x] Loading skeleton, empty state

### Task 8: Topic Suggestion Form (AC: 3, 7)

- [x] Create `src/components/partner/TopicSuggestionForm.tsx` — MUI Dialog
- [x] Fields: Title (required), Description (optional, max 500 chars)
- [x] Submit → `POST /api/v1/partners/topics` → invalidate topic list query
- [x] Validation: title required, min 5 chars

### Task 9: Organizer Status Panel (AC: 4, 5, 7)

- [x] Create `src/components/organizer/TopicStatusPanel.tsx`
- [x] Same topic list but with additional organizer controls per row:
  - Status dropdown: Proposed / Selected / Declined
  - "Planned for event" text input (shown when status = Selected)
  - Save button per row
- [x] Only rendered at `/organizer/partner-topics` (ProtectedRoute)

### Task 10: API Client (AC: ALL)

- [x] Create `src/services/api/partnerTopicsApi.ts`
- [x] `getTopics()` — React Query, staleTime 2 minutes
- [x] `suggestTopic(req)` — mutation, invalidates topic list
- [x] `castVote(topicId)` — mutation with optimistic update (increment vote count)
- [x] `removeVote(topicId)` — mutation with optimistic update (decrement vote count)
- [x] `updateTopicStatus(topicId, status, plannedEvent)` — mutation (organizer only)

### Task 11: Backend Integration Tests (AC: 1–6)

- [x] `TopicControllerIntegrationTest.java` (extends `AbstractIntegrationTest`)
- [x] Partner can suggest a topic
- [x] Partner can vote and unvote (toggle)
- [x] Duplicate vote is idempotent (no error on double-vote)
- [x] Partner cannot update status → 403
- [x] Organizer can update status
- [x] Vote count accurate after multiple partners vote
- [x] Topics sorted by vote count descending

### Task 12: Frontend Component Tests (AC: 7, 8)

- [x] `TopicListPage.test.tsx`
- [x] Renders topic list with mocked data
- [x] Vote toggle fires correct API call
- [x] Status badges render with correct colour
- [x] i18n DE/EN
- [x] Suggestion form validation (title required)

### Task 13: E2E Test (AC: 2, 3, 4)

- [x] `e2e/partner/topic-voting.spec.ts`
- [x] Partner submits topic → appears in list
- [x] Partner votes → vote count increments
- [x] Partner unvotes → vote count decrements
- [x] Organizer marks topic as Selected with planned event → partner sees "Selected for BATbern58"

## Dev Notes

### Data flow

All data lives in `partner-coordination-service` DB. No cross-service calls needed for this story — partner identity comes from the JWT principal.

```
Partner JWT → companyName claim
     │
     ▼
TopicController → TopicService → topic_suggestions / topic_votes tables
     │
     └── No HTTP calls to other services needed
```

### TopicDTO structure

```java
public record TopicDTO(
    UUID id,
    String title,
    String description,
    String suggestedByCompany,
    int voteCount,
    boolean currentPartnerHasVoted,  // derived from companyName in request
    String status,                    // PROPOSED | SELECTED | DECLINED
    String plannedEvent               // nullable, e.g. "BATbern58"
) {}
```

### Vote toggle pattern (optimistic UI)

```typescript
// castVote and removeVote use optimistic updates
const voteMutation = useMutation({
  mutationFn: (topicId: string) => castVote(topicId),
  onMutate: async (topicId) => {
    await queryClient.cancelQueries(['topics']);
    const prev = queryClient.getQueryData(['topics']);
    queryClient.setQueryData(['topics'], (old: Topic[]) =>
      old.map(t => t.id === topicId
        ? { ...t, voteCount: t.voteCount + 1, currentPartnerHasVoted: true }
        : t)
    );
    return { prev };
  },
  onError: (_, __, ctx) => queryClient.setQueryData(['topics'], ctx?.prev),
});
```

### ADR Compliance

- **ADR-003**: `topic_suggestions.company_name` and `topic_votes.company_name` store meaningful string IDs, not UUIDs
- **ADR-006**: OpenAPI spec updated before implementation

### Performance

| Metric | Target |
|--------|--------|
| Topic list load (P95) | <3s |
| Vote toggle response | <500ms |
| DB query (topics + counts) | <100ms |

### References

- [Source: docs/prd/epic-8-partner-coordination.md#Story-8.2]
- [Source: docs/architecture/ADR-003-meaningful-identifiers.md]
- [Source: docs/architecture/ADR-006-openapi-contract-first.md]
- [Source: services/partner-coordination-service — existing topic_votes schema]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/topic-test-run-1.log` — first test run (3 failures: short topic titles < 5 chars)
- `/tmp/topic-test-run-2.log` — second test run (20/20 PASSED)

### Completion Notes List

- **Tasks 1–5, 11 DONE** (backend + integration tests, all green) — Session 1
- V4 Flyway migration replaces incompatible V2 topic tables (old schema had `partner_id` UUID FK + `vote_weight`; new schema uses `company_name` per ADR-003)
- Created `docs/api/partner-topics-api.openapi.yml` (separate file from partner-analytics-api)
- Deleted 9 obsolete files: old services, controllers, events, exceptions, domain types, old tests
- `/organizer/topics` route collision: Story 5.2 owns that path. Partner-topic organizer view uses `/organizer/partner-topics`
- **Tasks 2 (final), 6, 6b, 7, 8, 9, 10, 12, 13 DONE** — Session 2 (2026-02-22)
- Added `generate:api-types:partner-topics` npm script → `src/types/generated/partner-topics-api.types.ts`
- i18n keys added to `partners.json` (DE+EN) under `portal.topics.*` and to `common.json` under `navigation.partnerTopics`
- `PartnerTopicsPlaceholder.tsx` deleted; `TopicListPage` wired at `/partners/topics`
- Organizer route `/organizer/partner-topics` added → `TopicStatusPanel`; nav entry added to `navigationConfig.ts` with `Lightbulb` icon
- `partnerTopicsApi.ts`: all 5 API functions (`getTopics`, `suggestTopic`, `castVote`, `removeVote`, `updateTopicStatus`)
- `TopicListPage.tsx`: optimistic vote mutations, status chips, suggestion form integration
- `TopicSuggestionForm.tsx`: MUI Dialog, title min-5 validation, description optional max-500
- `TopicStatusPanel.tsx`: per-row status dropdown + plannedEvent input + save; save button disabled for PROPOSED
- `TopicListPage.test.tsx`: 11/11 tests pass (AC1, AC2, AC3, AC5, AC7)
- `e2e/partner/topic-voting.spec.ts`: partner vote/unvote/suggest + organizer status panel coverage
- Regression: 3602 tests pass; 2 pre-existing failures in `PartnerNotesTab.test.tsx` (window.confirm) — not introduced by this story
- **ALL TASKS COMPLETE — Story ready for review**

### File List

**Created (Session 2 — Frontend):**
- `web-frontend/src/types/generated/partner-topics-api.types.ts`
- `web-frontend/src/services/api/partnerTopicsApi.ts`
- `web-frontend/src/components/partner/TopicListPage.tsx`
- `web-frontend/src/components/partner/TopicSuggestionForm.tsx`
- `web-frontend/src/components/organizer/TopicStatusPanel.tsx`
- `web-frontend/src/components/partner/TopicListPage.test.tsx`
- `web-frontend/e2e/partner/topic-voting.spec.ts`

**Created (Session 1 — Backend):**
- `services/partner-coordination-service/src/main/resources/db/migration/V4__update_topic_tables_for_story_8_2.sql`
- `docs/api/partner-topics-api.openapi.yml`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/TopicStatus.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/TopicSuggestion.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/TopicVoteId.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/TopicVote.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/TopicRepository.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/TopicVoteRepository.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/TopicDTO.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/TopicSuggestionRequest.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/TopicStatusUpdateRequest.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/TopicService.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/controller/TopicController.java`
- `services/partner-coordination-service/src/test/java/ch/batbern/partners/controller/TopicControllerIntegrationTest.java`

**Modified (Session 2 — Frontend):**
- `web-frontend/package.json` (added `generate:api-types:partner-topics` script)
- `web-frontend/src/App.tsx` (wired `TopicListPage` + organizer route + deleted placeholder import)
- `web-frontend/src/config/navigationConfig.ts` (added `partnerTopics` nav entry with `Lightbulb` icon)
- `web-frontend/public/locales/de/partners.json` (expanded `portal.topics.*`)
- `web-frontend/public/locales/en/partners.json` (expanded `portal.topics.*`)
- `web-frontend/public/locales/de/common.json` (added `navigation.partnerTopics`)
- `web-frontend/public/locales/en/common.json` (added `navigation.partnerTopics`)

**Modified (Session 1 — Backend):**
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/config/SecurityConfig.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/PartnerContactRepository.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/exception/GlobalExceptionHandler.java`
- `api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java` (modified as part of Epic 8 backend session, bundled in commit 55bb0a77)

**Deleted (Session 2 — Frontend):**
- `web-frontend/src/pages/PartnerTopicsPlaceholder.tsx`

**Modified (Session 3 — Code Review Fixes):**
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/TopicService.java` (null-status guard + targeted vote count + resolveCallerCompanyNameOrNull())
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/TopicVoteRepository.java` (countByTopicId)
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/controller/TopicController.java` (removed PartnerContactRepository injection; uses service)
- `services/partner-coordination-service/src/test/java/ch/batbern/partners/controller/TopicControllerIntegrationTest.java` (null-status test; castVote helper cleanup)
- `web-frontend/src/components/organizer/TopicStatusPanel.tsx` (i18n table headers — AC7 fix)
- `web-frontend/src/components/partner/TopicListPage.tsx` (onSuccess invalidation in vote mutations)
- `web-frontend/public/locales/de/partners.json` (added portal.topics.organizer.col.*)
- `web-frontend/public/locales/en/partners.json` (added portal.topics.organizer.col.*)

**Deleted (Session 1 — Backend):**
- `domain/SuggestionStatus.java`, `repository/TopicSuggestionRepository.java`
- `service/TopicSuggestionService.java`, `service/TopicVotingService.java`
- `controller/TopicSuggestionController.java`, `controller/TopicVotingController.java`
- `events/TopicSuggestionSubmittedEvent.java`, `events/TopicVoteSubmittedEvent.java`
- `exception/VoteAlreadyExistsException.java`
- Old tests: `TopicSuggestionControllerIntegrationTest.java`, `TopicVotingControllerIntegrationTest.java`, `TopicSuggestionTest.java`, `TopicVoteTest.java`
