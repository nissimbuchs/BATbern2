# Story 8.2: Sophisticated Topic Voting System

Status: ready-for-dev

## Story

As a **partner**,
I want to vote on future event topics with weighted influence based on partnership tier,
so that events align with our strategic interests.

## Acceptance Criteria

### Voting Interface
1. **AC1 - Topic List**: View all proposed topics with descriptions, categories, and voting status
2. **AC2 - Vote Allocation**: Allocate votes across topics (weighted by partnership tier)
3. **AC3 - Priority Ranking**: Drag-and-drop to rank topic priorities
4. **AC4 - Vote Submission**: Submit votes with confirmation and validation
5. **AC5 - Voting Deadline**: Clear deadline display with countdown timer

### Weighted Voting
6. **AC6 - Tier-Based Weighting**: Premium partners get more voting weight (configurable multiplier)
7. **AC7 - Weight Transparency**: Show voting weight to partner before submission
8. **AC8 - Fair Distribution**: Algorithm ensures no single partner dominates (max 30% influence cap)
9. **AC9 - Consensus Building**: Show partner consensus level (percentage agreement)

### Topic Suggestions
10. **AC10 - Suggestion Form**: Partners can submit new topic ideas with title, description, category
11. **AC11 - Justification Required**: Business case and strategic alignment fields mandatory
12. **AC12 - Review Workflow**: Organizer reviews and approves/declines suggestions
13. **AC13 - Suggestion Tracking**: Track suggestion status (SUBMITTED → UNDER_REVIEW → APPROVED/DECLINED)

### Voting Results
14. **AC14 - Live Results**: See current voting standings (real-time updates)
15. **AC15 - Historical Trends**: View past voting cycles and outcomes
16. **AC16 - Impact Metrics**: See attendance/engagement for previously voted topics
17. **AC17 - Adoption Status**: Track which topics were selected for events

### Integration
18. **AC18 - Topic Backlog Integration**: Voted topics appear in Epic 5 Story 5.2 topic backlog
19. **AC19 - Heat Map Integration**: Voting results influence topic selection heat map
20. **AC20 - Backward Compatible**: Organizer can still add topics manually (Epic 5 workflow)

### Technical
21. **AC21 - ADR-003 Compliance**: Database uses meaningful IDs (companyName, topicCode, username)
22. **AC22 - i18n Support**: All UI text translated (German primary, English secondary)
23. **AC23 - Performance**: Vote submission <2 seconds, results load <1 second

## Tasks / Subtasks

### Backend Tasks

- [ ] **Task 1: Update OpenAPI Specification** (AC: ALL - contract-first per ADR-006)
  - [ ] Update `docs/api/partners-api.openapi.yml` with voting endpoints
  - [ ] Define `POST /api/v1/topics/{topicCode}/votes` endpoint
  - [ ] Define `GET /api/v1/topics/votes/results` endpoint
  - [ ] Define `POST /api/v1/topic-suggestions` endpoint
  - [ ] Define `GET /api/v1/topic-suggestions` endpoint (with status filter)
  - [ ] Define `PUT /api/v1/topic-suggestions/{id}/review` endpoint
  - [ ] Generate TypeScript types: `npm run generate:api-types:partners`

- [ ] **Task 2: Database Migrations** (AC: 21)
  - [ ] Create Flyway migration `V8.2.1__create_topic_votes_table.sql`
    ```sql
    CREATE TABLE topic_votes (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        company_name VARCHAR(12) NOT NULL,
        topic_code VARCHAR(100) NOT NULL,
        vote_value INTEGER NOT NULL,
        voted_by_username VARCHAR(100) NOT NULL,
        voted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        UNIQUE(company_name, topic_code)
    );
    CREATE INDEX idx_topic_votes_company ON topic_votes(company_name);
    CREATE INDEX idx_topic_votes_topic ON topic_votes(topic_code);
    ```
  - [ ] Create Flyway migration `V8.2.2__create_topic_suggestions_table.sql`
    ```sql
    CREATE TABLE topic_suggestions (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        company_name VARCHAR(12) NOT NULL,
        suggested_by_username VARCHAR(100) NOT NULL,
        title VARCHAR(255) NOT NULL,
        description TEXT,
        category VARCHAR(100),
        business_justification TEXT NOT NULL,
        strategic_alignment TEXT,
        status VARCHAR(50) DEFAULT 'SUBMITTED',
        reviewed_by_username VARCHAR(100),
        review_notes TEXT,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        reviewed_at TIMESTAMP WITH TIME ZONE
    );
    CREATE INDEX idx_suggestions_company ON topic_suggestions(company_name);
    CREATE INDEX idx_suggestions_status ON topic_suggestions(status);
    ```

- [ ] **Task 3: Topic Voting Service** (AC: 1-9, 14-17)
  - [ ] Create `TopicVotingService.java` in partner-coordination-service
  - [ ] Implement `submitVote(String companyName, String topicCode, int voteValue, String username)`
  - [ ] Implement `getVotingResults()` - aggregated results with weighting
  - [ ] Implement `getPartnerVotes(String companyName)` - partner's own votes
  - [ ] Implement `calculateTierWeight(String companyName)` - lookup partner tier
  - [ ] Implement `getConsensusLevel()` - percentage of partners agreeing on top topics
  - [ ] Add fair distribution cap (max 30% influence per partner)

- [ ] **Task 4: Topic Suggestion Service** (AC: 10-13)
  - [ ] Create `TopicSuggestionService.java`
  - [ ] Implement `submitSuggestion(TopicSuggestionRequest request)`
  - [ ] Implement `getSuggestions(String status)` - filter by status
  - [ ] Implement `reviewSuggestion(UUID id, String decision, String notes, String reviewerUsername)`
  - [ ] Emit `TopicSuggestionApprovedEvent` via EventBridge when approved

- [ ] **Task 5: REST Controllers** (AC: 21, 23)
  - [ ] Create `TopicVotingController.java`
  - [ ] Create `TopicSuggestionController.java`
  - [ ] Add `@PreAuthorize` for role-based access
  - [ ] Add request timing metrics with Micrometer

- [ ] **Task 6: EventBridge Integration** (AC: 18, 19)
  - [ ] Create `VotingEventPublisher.java`
  - [ ] Publish `TopicVotedEvent` when voting cycle closes
  - [ ] Event Management Service subscribes and updates topic backlog
  - [ ] Include vote counts and weighted scores in event payload

- [ ] **Task 7: SecurityConfig Update** (AC: 21)
  - [ ] Add voting endpoints to SecurityConfig
  - [ ] `POST /api/v1/topics/*/votes` → PARTNER role
  - [ ] `GET /api/v1/topics/votes/results` → PARTNER, ORGANIZER roles
  - [ ] `PUT /api/v1/topic-suggestions/*/review` → ORGANIZER role only

### Frontend Tasks

- [ ] **Task 8: i18n Translation Keys** (AC: 22)
  - [ ] Add ~60 translation keys to `public/locales/de/partner.json`
  - [ ] Add ~60 translation keys to `public/locales/en/partner.json`
  - [ ] Include pluralization: `partner.voting.votes_one`, `partner.voting.votes_other`

- [ ] **Task 9: Topic Voting Dashboard** (AC: 1, 5, 14)
  - [ ] Create `src/components/partner/TopicVotingDashboard.tsx`
  - [ ] Display voting deadline with countdown timer
  - [ ] Show current voting status (open/closed)
  - [ ] Real-time results updates via polling (30s interval)

- [ ] **Task 10: Topic List Component** (AC: 1, 2, 3)
  - [ ] Create `src/components/partner/VotableTopicList.tsx`
  - [ ] Display topics with description, category, current votes
  - [ ] Implement vote allocation slider/input per topic
  - [ ] Drag-and-drop priority ranking with react-beautiful-dnd

- [ ] **Task 11: Vote Submission Component** (AC: 4, 7)
  - [ ] Create `src/components/partner/VoteSubmissionPanel.tsx`
  - [ ] Show voting weight based on partner tier
  - [ ] Display total votes allocated vs available
  - [ ] Confirmation dialog before submission

- [ ] **Task 12: Voting Results Component** (AC: 14, 15, 16, 17)
  - [ ] Create `src/components/partner/VotingResultsPanel.tsx`
  - [ ] Use Recharts BarChart for vote distribution
  - [ ] Historical trends with LineChart
  - [ ] Topic adoption status badges

- [ ] **Task 13: Topic Suggestion Form** (AC: 10, 11, 13)
  - [ ] Create `src/components/partner/TopicSuggestionForm.tsx`
  - [ ] Form fields: title, description, category, business justification
  - [ ] Validation: justification required (min 50 chars)
  - [ ] Status tracking display

- [ ] **Task 14: Suggestion Review Component** (AC: 12) - Organizer only
  - [ ] Create `src/components/organizer/SuggestionReviewPanel.tsx`
  - [ ] List pending suggestions with partner info
  - [ ] Approve/Decline buttons with notes field
  - [ ] Bulk actions for efficiency

- [ ] **Task 15: API Client Integration** (AC: ALL)
  - [ ] Create `src/services/api/topicVotingApi.ts`
  - [ ] Implement `submitVote(topicCode, voteValue)`
  - [ ] Implement `getVotingResults()`
  - [ ] Implement `getPartnerVotes()`
  - [ ] Implement `submitSuggestion(suggestion)`
  - [ ] Implement `reviewSuggestion(id, decision, notes)`
  - [ ] Use React Query with optimistic updates

### Testing Tasks

- [ ] **Task 16: Backend Integration Tests** (AC: 6, 8, 21)
  - [ ] Create `TopicVotingControllerIntegrationTest.java`
  - [ ] Test tier-based weighting calculation
  - [ ] Test fair distribution cap (max 30% influence)
  - [ ] Test role-based access (partner vs organizer)
  - [ ] Use PostgreSQL via Testcontainers

- [ ] **Task 17: Frontend Component Tests** (AC: 22, 23)
  - [ ] Create `TopicVotingDashboard.test.tsx`
  - [ ] Test vote allocation validation
  - [ ] Test countdown timer behavior
  - [ ] Test i18n language switching

- [ ] **Task 18: E2E Tests** (AC: 4, 12, 18)
  - [ ] Create `e2e/partner/topic-voting.spec.ts`
  - [ ] Test partner login → vote on topics → confirm submission
  - [ ] Test organizer review workflow
  - [ ] Test Epic 5 topic backlog integration

## Dev Notes

### Architecture Compliance

**ADR-003 (Meaningful Identifiers):**
```sql
-- ✅ CORRECT: Meaningful IDs, no foreign keys across services
CREATE TABLE topic_votes (
    company_name VARCHAR(12) NOT NULL,  -- Not partner_id UUID
    topic_code VARCHAR(100) NOT NULL,   -- Not topic_id UUID
    voted_by_username VARCHAR(100) NOT NULL  -- Not user_id UUID
);
```

**ADR-004 (HTTP Enrichment):**
```java
// Get partner tier via HTTP, not database join
@Cacheable("partner-tiers")
public PartnerTier getPartnerTier(String companyName) {
    return partnerServiceClient.getPartner(companyName).getTier();
}
```

**ADR-006 (OpenAPI Contract-First):**
- Update `docs/api/partners-api.openapi.yml` BEFORE implementation
- Generate types: `npm run generate:api-types:partners`

### Voting Weight Algorithm

```java
public class VotingWeightCalculator {
    private static final Map<PartnerTier, Double> TIER_WEIGHTS = Map.of(
        PartnerTier.PLATINUM, 3.0,
        PartnerTier.GOLD, 2.0,
        PartnerTier.SILVER, 1.5,
        PartnerTier.BRONZE, 1.0
    );

    private static final double MAX_INFLUENCE_CAP = 0.30; // 30% max per partner

    public double calculateWeightedVote(String companyName, int rawVotes) {
        PartnerTier tier = getPartnerTier(companyName);
        double weight = TIER_WEIGHTS.getOrDefault(tier, 1.0);
        double weightedVote = rawVotes * weight;

        // Apply influence cap
        double totalVotes = getTotalVotesInCycle();
        double maxAllowed = totalVotes * MAX_INFLUENCE_CAP;
        return Math.min(weightedVote, maxAllowed);
    }
}
```

### Project Structure Notes

**Backend Files:**
```
services/partner-coordination-service/src/main/java/ch/batbern/partners/
├── controller/
│   ├── TopicVotingController.java
│   └── TopicSuggestionController.java
├── service/
│   ├── TopicVotingService.java
│   ├── TopicSuggestionService.java
│   └── VotingWeightCalculator.java
├── domain/
│   ├── TopicVote.java
│   ├── TopicSuggestion.java
│   └── SuggestionStatus.java (enum)
├── dto/
│   ├── VoteRequest.java
│   ├── VoteResultDTO.java
│   ├── TopicSuggestionRequest.java
│   └── SuggestionReviewRequest.java
├── repository/
│   ├── TopicVoteRepository.java
│   └── TopicSuggestionRepository.java
└── event/
    ├── TopicVotedEvent.java
    └── VotingEventPublisher.java
```

**Frontend Files:**
```
web-frontend/src/
├── components/partner/
│   ├── TopicVotingDashboard.tsx
│   ├── VotableTopicList.tsx
│   ├── VoteSubmissionPanel.tsx
│   ├── VotingResultsPanel.tsx
│   └── TopicSuggestionForm.tsx
├── components/organizer/
│   └── SuggestionReviewPanel.tsx
├── services/api/
│   └── topicVotingApi.ts
└── hooks/
    └── useTopicVoting.ts
```

**Database Migrations:**
```
services/partner-coordination-service/src/main/resources/db/migration/
├── V8.2.1__create_topic_votes_table.sql
└── V8.2.2__create_topic_suggestions_table.sql
```

### i18n Translation Keys

```json
{
  "voting": {
    "title": "Topic Voting",
    "deadline": "Voting closes: {{date}}",
    "countdown": "{{days}} days, {{hours}} hours remaining",
    "status": {
      "open": "Voting Open",
      "closed": "Voting Closed"
    },
    "topics": {
      "list": "Available Topics",
      "category": "Category",
      "description": "Description"
    },
    "allocate": {
      "votes": "Allocate Your Votes",
      "weight": "Your voting weight: {{weight}}x",
      "remaining": "{{count}} votes remaining"
    },
    "submit": "Submit Votes",
    "confirm": {
      "title": "Confirm Vote Submission",
      "message": "Are you sure you want to submit your votes? This cannot be undone."
    },
    "results": {
      "live": "Live Results",
      "historical": "Past Voting Cycles",
      "consensus": "Partner Consensus: {{percentage}}%"
    },
    "votes_one": "{{count}} vote",
    "votes_other": "{{count}} votes",
    "suggestion": {
      "submit": "Suggest a Topic",
      "title": "Topic Title",
      "justification": "Business Justification",
      "status": {
        "SUBMITTED": "Submitted",
        "UNDER_REVIEW": "Under Review",
        "APPROVED": "Approved",
        "DECLINED": "Declined"
      }
    },
    "success": {
      "voted": "Your votes have been recorded!",
      "suggested": "Topic suggestion submitted for review"
    },
    "error": {
      "submitFailed": "Failed to submit votes",
      "loadFailed": "Failed to load voting data"
    }
  }
}
```

### EventBridge Integration

```java
@Component
public class VotingEventPublisher {
    private final EventBridgeClient eventBridge;

    public void publishVotingCycleClosed(List<VoteResultDTO> results) {
        PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
            .source("partner-coordination-service")
            .detailType("TopicVotingCycleClosed")
            .detail(objectMapper.writeValueAsString(results))
            .eventBusName("batbern-events")
            .build();

        eventBridge.putEvents(PutEventsRequest.builder()
            .entries(entry)
            .build());
    }
}
```

**Event Management Service Subscription:**
```java
@EventListener(condition = "#event.detailType == 'TopicVotingCycleClosed'")
public void handleVotingResults(TopicVotingEvent event) {
    // Update topic backlog with voting scores
    event.getResults().forEach(result -> {
        topicBacklogService.updateVotingScore(
            result.getTopicCode(),
            result.getWeightedScore()
        );
    });
}
```

### Performance Requirements

| Metric | Target | Measurement |
|--------|--------|-------------|
| Vote Submission | <2s | Micrometer timer |
| Results Load | <1s | React Query timing |
| Real-time Update | 30s polling | Frontend interval |
| Database Query | <50ms | PostgreSQL EXPLAIN |

### References

- [Source: docs/prd/epic-8-partner-coordination.md#Story-8.2]
- [Source: docs/architecture/ADR-003-meaningful-identifiers.md]
- [Source: docs/architecture/05-frontend-architecture.md#i18n]
- [Source: docs/architecture/coding-standards.md#TDD-Workflow]

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

