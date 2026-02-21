# Story 8.2: Topic Suggestions & Voting

Status: ready-for-dev

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

- [ ] Review `partner-coordination-service` Flyway migrations for existing `topic_votes` and `topic_suggestions` tables (created in Story 2.7)
- [ ] If tables already exist and match the schema below — no migration needed
- [ ] If tables are missing or schema differs, create migration `V8.2.1__update_topic_tables.sql`:

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

- [ ] Update `docs/api/partner-analytics-api.openapi.yml` (or create `partner-topics-api.openapi.yml`)
- [ ] Define endpoints:
  - `GET  /api/v1/partners/topics` — list all topics with vote counts (PARTNER + ORGANIZER)
  - `POST /api/v1/partners/topics` — suggest a new topic (PARTNER)
  - `POST /api/v1/partners/topics/{topicId}/vote` — toggle vote on (PARTNER)
  - `DELETE /api/v1/partners/topics/{topicId}/vote` — toggle vote off (PARTNER)
  - `PATCH /api/v1/partners/topics/{topicId}/status` — update status (ORGANIZER only)
- [ ] Define DTOs: `TopicDTO`, `TopicSuggestionRequest`, `TopicStatusUpdateRequest`
- [ ] Generate TypeScript types: `npm run generate:api-types:partners`

### Task 3: TopicService (AC: 1–6)

- [ ] Create `TopicService.java` in partner-coordination-service
- [ ] `getAllTopics(String currentCompanyName): List<TopicDTO>` — query topics + vote counts, mark which ones current partner has voted for
- [ ] `suggestTopic(String companyName, String username, TopicSuggestionRequest req)` — insert into topic_suggestions
- [ ] `castVote(UUID topicId, String companyName)` — insert into topic_votes (ignore if already voted)
- [ ] `removeVote(UUID topicId, String companyName)` — delete from topic_votes (ignore if not voted)
- [ ] `updateStatus(UUID topicId, String status, String plannedEvent)` — ORGANIZER only; validate status is SELECTED or DECLINED

### Task 4: TopicController (AC: 6, 8)

- [ ] Create `TopicController.java`
- [ ] Implement all 5 endpoints from OpenAPI spec
- [ ] `@PreAuthorize` on status update: `hasRole('ORGANIZER')`
- [ ] `@PreAuthorize` on vote/suggest: `hasRole('PARTNER')`
- [ ] Add Micrometer timing

### Task 5: SecurityConfig Update (AC: 6)

- [ ] Add topic endpoints to `SecurityConfig.java`
  - `GET  /api/v1/partners/topics` → PARTNER, ORGANIZER
  - `POST /api/v1/partners/topics` → PARTNER
  - `POST/DELETE /api/v1/partners/topics/*/vote` → PARTNER
  - `PATCH /api/v1/partners/topics/*/status` → ORGANIZER

### Task 6: i18n Keys (AC: 7)

- [ ] Add keys to `public/locales/de/partner.json` and `en/partner.json`
  - `partner.topics.title`, `partner.topics.suggest`, `partner.topics.vote`, `partner.topics.unvote`
  - `partner.topics.status.proposed`, `.selected`, `.declined`
  - `partner.topics.plannedFor`, `partner.topics.empty`
  - `partner.topics.form.title`, `.description`, `.submit`

### Task 7: Topic List Page (AC: 1, 2, 5, 7, 8)

- [ ] Create `src/components/partner/TopicListPage.tsx`
- [ ] MUI `List` or `Table` — one row per topic: title, description, company, votes, status badge, vote button
- [ ] Vote button: filled heart / thumbs-up when voted, outlined when not. Calls POST or DELETE on click.
- [ ] Status badge: chip with colour — grey (Proposed), green (Selected), red (Declined)
- [ ] Selected topics show `plannedEvent` field if set (e.g. "BATbern58")
- [ ] "Suggest a Topic" button opens the suggestion form (modal or inline)
- [ ] Loading skeleton, empty state

### Task 8: Topic Suggestion Form (AC: 3, 7)

- [ ] Create `src/components/partner/TopicSuggestionForm.tsx` — MUI Dialog
- [ ] Fields: Title (required), Description (optional, max 500 chars)
- [ ] Submit → `POST /api/v1/partners/topics` → invalidate topic list query
- [ ] Validation: title required, min 5 chars

### Task 9: Organizer Status Panel (AC: 4, 5, 7)

- [ ] Create `src/components/organizer/TopicStatusPanel.tsx`
- [ ] Same topic list but with additional organizer controls per row:
  - Status dropdown: Proposed / Selected / Declined
  - "Planned for event" text input (shown when status = Selected)
  - Save button per row
- [ ] Only rendered when user has ORGANIZER role

### Task 10: API Client (AC: ALL)

- [ ] Create `src/services/api/partnerTopicsApi.ts`
- [ ] `getTopics()` — React Query, staleTime 2 minutes
- [ ] `suggestTopic(req)` — mutation, invalidates topic list
- [ ] `castVote(topicId)` — mutation with optimistic update (increment vote count)
- [ ] `removeVote(topicId)` — mutation with optimistic update (decrement vote count)
- [ ] `updateTopicStatus(topicId, status, plannedEvent)` — mutation (organizer only)

### Task 11: Backend Integration Tests (AC: 1–6)

- [ ] `TopicControllerIntegrationTest.java` (extends `AbstractIntegrationTest`)
- [ ] Partner can suggest a topic
- [ ] Partner can vote and unvote (toggle)
- [ ] Duplicate vote is idempotent (no error on double-vote)
- [ ] Partner cannot update status → 403
- [ ] Organizer can update status
- [ ] Vote count accurate after multiple partners vote
- [ ] Topics sorted by vote count descending

### Task 12: Frontend Component Tests (AC: 7, 8)

- [ ] `TopicListPage.test.tsx`
- [ ] Renders topic list with mocked data
- [ ] Vote toggle fires correct API call
- [ ] Status badges render with correct colour
- [ ] i18n DE/EN
- [ ] Suggestion form validation (title required)

### Task 13: E2E Test (AC: 2, 3, 4)

- [ ] `e2e/partner/topic-voting.spec.ts`
- [ ] Partner submits topic → appears in list
- [ ] Partner votes → vote count increments
- [ ] Partner unvotes → vote count decrements
- [ ] Organizer marks topic as Selected with planned event → partner sees "Selected for BATbern58"

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

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
