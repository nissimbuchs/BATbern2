# Epic 2 Story 2.2: Topic Selection System - Split Example

This document demonstrates how to split Story 2.2 (Topic Selection System) from a monolithic story into layered stories for parallel development.

## Original Story (From epic-2-event-creation-publishing-stories.md)

**Story 2.2: Topic Selection System (Workflow Step 2)**

**Duration:** 8 days (sequential implementation)
**Complexity:** High - Full stack + ML features
**Risk:** Late integration discovery of issues

---

## Split Approach: 4 Layered Stories

### Overview

**Total Development Time:** 8 days
**Calendar Time:** 5 days (3 days saved through parallelization!)

```
Timeline:
Day 1: Story 2.2a (API Contract) - 1 developer
Days 2-4: Story 2.2b (Frontend) + Story 2.2c (Backend) - 2 developers in parallel
Day 5: Story 2.2d (Integration) - 1 developer
```

---

## Story 2.2a: Topic Selection API Contract

**Template:** `story-api-contract-tmpl.yaml`
**Duration:** 1 day
**Owner:** API Designer or Tech Lead
**Status:** Ready to start

### Story

**As an** organizer,
**I want** a well-defined API contract for topic management,
**so that** frontend and backend teams can work in parallel with clear expectations.

**API Contract Focus:** This story defines the API contract that both frontend and backend teams will implement against.

### API Contract Specification

#### OpenAPI Specification

```yaml
openapi: 3.0.0
info:
  title: Topic Management API
  version: 1.0.0

paths:
  /api/v1/topics:
    get:
      summary: List all topics with usage history and staleness indicators
      tags: [Topics]
      security:
        - BearerAuth: [organizer]
      parameters:
        - name: search
          in: query
          schema:
            type: string
          description: Full-text search across title and description
        - name: includeStale
          in: query
          schema:
            type: boolean
            default: false
          description: Include topics used in last 6 months
        - name: category
          in: query
          schema:
            type: string
        - name: limit
          in: query
          schema:
            type: integer
            default: 50
            maximum: 100
        - name: offset
          in: query
          schema:
            type: integer
            default: 0
      responses:
        '200':
          description: List of topics with usage metadata
          content:
            application/json:
              schema:
                type: object
                properties:
                  topics:
                    type: array
                    items:
                      $ref: '#/components/schemas/TopicWithMetadata'
                  pagination:
                    $ref: '#/components/schemas/Pagination'
        '401':
          $ref: '#/components/responses/UnauthorizedError'
        '403':
          $ref: '#/components/responses/ForbiddenError'

    post:
      summary: Create new topic
      tags: [Topics]
      security:
        - BearerAuth: [organizer]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateTopicRequest'
      responses:
        '201':
          description: Topic created successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Topic'
        '400':
          $ref: '#/components/responses/ValidationError'
        '401':
          $ref: '#/components/responses/UnauthorizedError'
        '409':
          description: Topic with similar title already exists (>70% similarity)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SimilarTopicWarning'

  /api/v1/topics/{id}:
    get:
      summary: Get topic details with full usage history
      tags: [Topics]
      security:
        - BearerAuth: [organizer]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Topic details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TopicDetail'
        '404':
          $ref: '#/components/responses/NotFoundError'

  /api/v1/topics/{id}/similar:
    get:
      summary: Find similar topics using similarity detection
      tags: [Topics]
      security:
        - BearerAuth: [organizer]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
        - name: threshold
          in: query
          schema:
            type: number
            format: float
            default: 0.7
            minimum: 0.0
            maximum: 1.0
          description: Similarity threshold (0.7 = 70% similar)
      responses:
        '200':
          description: List of similar topics
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/SimilarTopic'

  /api/v1/topics/{id}/usage-history:
    get:
      summary: Get 24-month usage history for heat map
      tags: [Topics]
      security:
        - BearerAuth: [organizer]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Monthly usage data for 24 months
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UsageHistory'

components:
  schemas:
    Topic:
      type: object
      required:
        - id
        - title
        - description
        - category
        - createdAt
        - updatedAt
      properties:
        id:
          type: string
          format: uuid
        title:
          type: string
          minLength: 3
          maxLength: 255
          example: "Cloud Native Security"
        description:
          type: string
          minLength: 10
          maxLength: 2000
          example: "Exploring security best practices in cloud-native environments"
        category:
          type: string
          enum: [TECHNICAL, BUSINESS, PROCESS, TOOLS, TRENDS]
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    TopicWithMetadata:
      allOf:
        - $ref: '#/components/schemas/Topic'
        - type: object
          properties:
            lastUsed:
              type: string
              format: date
              nullable: true
            usageCount:
              type: integer
              minimum: 0
            stalenessScore:
              type: number
              format: float
              minimum: 0
              maximum: 100
              description: "0 = too recent, 100 = ready to reuse"
            stalenessIndicator:
              type: string
              enum: [RED, YELLOW, GREEN]
              description: "RED: <6 months, YELLOW: 6-12 months, GREEN: >12 months"
            recommendedWaitMonths:
              type: integer
              minimum: 0
              description: "Months to wait before reusing (0 = ready now)"

    TopicDetail:
      allOf:
        - $ref: '#/components/schemas/TopicWithMetadata'
        - type: object
          properties:
            usageHistory:
              type: array
              items:
                type: object
                properties:
                  eventId:
                    type: string
                    format: uuid
                  eventTitle:
                    type: string
                  eventDate:
                    type: string
                    format: date
            similarTopics:
              type: array
              items:
                $ref: '#/components/schemas/SimilarTopic'

    SimilarTopic:
      type: object
      properties:
        topicId:
          type: string
          format: uuid
        title:
          type: string
        similarityScore:
          type: number
          format: float
          minimum: 0
          maximum: 1
          description: "0.0 = completely different, 1.0 = identical"
        lastUsed:
          type: string
          format: date
          nullable: true

    CreateTopicRequest:
      type: object
      required:
        - title
        - description
        - category
      properties:
        title:
          type: string
          minLength: 3
          maxLength: 255
        description:
          type: string
          minLength: 10
          maxLength: 2000
        category:
          type: string
          enum: [TECHNICAL, BUSINESS, PROCESS, TOOLS, TRENDS]
        checkSimilarity:
          type: boolean
          default: true
          description: "Check for similar topics before creating"

    SimilarTopicWarning:
      type: object
      properties:
        error:
          type: object
          properties:
            code:
              type: string
              example: "SIMILAR_TOPIC_EXISTS"
            message:
              type: string
              example: "A similar topic already exists"
            similarTopics:
              type: array
              items:
                $ref: '#/components/schemas/SimilarTopic'
            allowOverride:
              type: boolean
              description: "Can the user override this warning?"

    UsageHistory:
      type: object
      properties:
        topicId:
          type: string
          format: uuid
        months:
          type: array
          items:
            type: object
            properties:
              year:
                type: integer
              month:
                type: integer
                minimum: 1
                maximum: 12
              usageCount:
                type: integer
                minimum: 0
              events:
                type: array
                items:
                  type: object
                  properties:
                    eventId:
                      type: string
                      format: uuid
                    eventTitle:
                      type: string
                    eventDate:
                      type: string
                      format: date

    Pagination:
      type: object
      properties:
        total:
          type: integer
        limit:
          type: integer
        offset:
          type: integer
        hasMore:
          type: boolean

  responses:
    UnauthorizedError:
      description: Authentication required
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Error'

    ForbiddenError:
      description: Insufficient permissions
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Error'

    NotFoundError:
      description: Resource not found
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Error'

    ValidationError:
      description: Invalid request data
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ValidationErrorResponse'

  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

### Acceptance Criteria

1. ✅ OpenAPI 3.0 specification is syntactically valid
2. ✅ All endpoints have complete request/response schemas
3. ✅ Validation rules documented (min/max lengths, required fields)
4. ✅ Error scenarios defined with standard error format
5. ✅ Authentication requirements specified
6. ✅ TypeScript types generated for frontend team
7. ✅ Java DTOs generated for backend team
8. ✅ Swagger UI documentation accessible
9. ✅ Postman collection created for manual testing
10. ✅ Mock data prepared for frontend development

### Tasks

- [x] Task 1: Define OpenAPI Specification
  - [x] Define all endpoints with methods
  - [x] Define request schemas with validation
  - [x] Define response schemas for all scenarios
  - [x] Validate spec syntax with openapi-generator

- [x] Task 2: Generate Artifacts
  - [x] Generate TypeScript types: `npm run generate:api-types`
  - [x] Generate Java DTOs: `./gradlew openApiGenerate`
  - [x] Generate Swagger UI
  - [x] Create Postman collection

- [x] Task 3: Create Mock Data
  - [x] Success response examples
  - [x] Error response examples
  - [x] Edge case data (empty lists, high similarity, etc.)

- [x] Task 4: API Gateway Configuration
  - [x] Configure routes in CDK
  - [x] Set up request validation
  - [x] Configure CORS policies

### Definition of Done

- [x] OpenAPI spec validates successfully
- [x] TypeScript types available at `web-frontend/src/types/generated/topic-api.ts`
- [x] Java DTOs available at `event-management-service/src/main/java/ch/batbern/events/dto/topic/`
- [x] Swagger UI accessible at https://api-dev.batbern.ch/docs
- [x] Postman collection checked into repository
- [x] Mock data JSON files ready for frontend
- [x] API Gateway routes deployed to dev environment

**Time:** 1 day
**Story Points:** 3
**Status:** ✅ Done (blocks 2.2b and 2.2c)

---

## Story 2.2b: Topic Selector Frontend

**Template:** `story-frontend-tmpl.yaml`
**Duration:** 3 days
**Owner:** Frontend Developer
**Status:** 🔴 Blocked until 2.2a is Done

### Story

**As an** organizer,
**I want** an intuitive topic selection interface with intelligent suggestions,
**so that** I can choose appropriate topics while avoiding recent duplicates.

**Frontend Focus:** This story implements the UI/UX using MSW to mock backend APIs defined in Story 2.2a.

### Component Specifications

#### Components to Create

1. **TopicSelector** (Container Component)
   - Location: `web-frontend/src/components/organizer/EventManagement/TopicSelector/`
   - Manages topic selection state
   - Integrates all sub-components

2. **TopicSearchBar** (Presentational)
   - Full-text search with debouncing
   - Category filtering
   - Clear and reset functionality

3. **TopicList** (Container)
   - Displays paginated topic list
   - Shows staleness indicators
   - Handles selection

4. **TopicCard** (Presentational)
   - Individual topic display
   - Shows metadata (last used, staleness, usage count)
   - Color-coded staleness indicator

5. **TopicHeatMap** (Presentational)
   - Recharts-based 24-month visualization
   - Monthly usage frequency
   - Interactive tooltips

6. **SimilarTopicsDialog** (Container)
   - Warning dialog for similar topics
   - Lists similar topics with scores
   - Override with justification

7. **CreateTopicForm** (Container)
   - Inline topic creation
   - Real-time similarity checking
   - Validation feedback

#### Custom Hooks

```typescript
// useTopics.ts
export const useTopics = (filters: TopicFilters) => {
  return useQuery<TopicWithMetadata[]>({
    queryKey: ['topics', filters],
    queryFn: () => topicApi.getTopics(filters)
  });
};

// useTopicSimilarity.ts
export const useTopicSimilarity = (topicId: string) => {
  return useQuery<SimilarTopic[]>({
    queryKey: ['topic-similarity', topicId],
    queryFn: () => topicApi.getSimilarTopics(topicId)
  });
};

// useTopicSelection.ts
export const useTopicSelection = () => {
  const [selectedTopic, setSelectedTopic] = useState<Topic | null>(null);
  const [overrideReason, setOverrideReason] = useState<string>('');
  // ... selection logic
};
```

#### MSW Mock Configuration

```typescript
// web-frontend/src/mocks/handlers/topicHandlers.ts
import { rest } from 'msw';
import { mockTopics, mockSimilarTopics, mockUsageHistory } from '../data/topics';

export const topicHandlers = [
  // List topics
  rest.get('/api/v1/topics', (req, res, ctx) => {
    const search = req.url.searchParams.get('search');
    const includeStale = req.url.searchParams.get('includeStale') === 'true';

    let filtered = mockTopics;
    if (search) {
      filtered = filtered.filter(t =>
        t.title.toLowerCase().includes(search.toLowerCase())
      );
    }
    if (!includeStale) {
      filtered = filtered.filter(t => t.stalenessIndicator !== 'RED');
    }

    return res(
      ctx.delay(100), // Simulate network delay
      ctx.status(200),
      ctx.json({ topics: filtered, pagination: { total: filtered.length } })
    );
  }),

  // Get similar topics
  rest.get('/api/v1/topics/:id/similar', (req, res, ctx) => {
    return res(
      ctx.delay(50),
      ctx.status(200),
      ctx.json(mockSimilarTopics)
    );
  }),

  // Create topic (with similarity warning)
  rest.post('/api/v1/topics', async (req, res, ctx) => {
    const body = await req.json();
    const { title, checkSimilarity } = body;

    if (checkSimilarity && title.includes('Security')) {
      // Simulate similarity warning
      return res(
        ctx.status(409),
        ctx.json({
          error: {
            code: 'SIMILAR_TOPIC_EXISTS',
            message: 'A similar topic already exists',
            similarTopics: mockSimilarTopics,
            allowOverride: true
          }
        })
      );
    }

    return res(
      ctx.status(201),
      ctx.json({ id: 'new-uuid', ...body })
    );
  }),

  // Get usage history
  rest.get('/api/v1/topics/:id/usage-history', (req, res, ctx) => {
    return res(
      ctx.status(200),
      ctx.json(mockUsageHistory)
    );
  })
];
```

### Acceptance Criteria

1. ✅ Topic search with real-time filtering works smoothly
2. ✅ Topic list displays with color-coded staleness indicators
3. ✅ Heat map visualizes 24-month usage history
4. ✅ Similar topics warning appears when selecting stale/similar topics
5. ✅ Override functionality allows justification entry
6. ✅ Create new topic with inline similarity checking
7. ✅ Loading states display during API calls
8. ✅ Error states handled gracefully
9. ✅ Mobile responsive design (works on tablets)
10. ✅ Accessibility score >90 (Lighthouse)

### Definition of Done

- [x] All React components implemented
- [x] MSW mocks configured and working
- [x] Component tests passing (>90% coverage)
- [x] Hook tests passing (>90% coverage)
- [x] Storybook stories created
- [x] Mobile responsive tested
- [x] Accessibility validated
- [x] Works completely without backend

**Time:** 3 days
**Story Points:** 8
**Status:** Ready for integration (2.2d)

---

## Story 2.2c: Topic Service Backend

**Template:** `story-backend-tmpl.yaml`
**Duration:** 3 days (parallel with 2.2b)
**Owner:** Backend Developer
**Status:** 🔴 Blocked until 2.2a is Done

### Story

**As an** organizer,
**I want** a robust topic management service with intelligent similarity detection,
**so that** the system can prevent duplicate topics and provide usage insights.

**Backend Focus:** This story implements business logic and data layer validating against Story 2.2a contract.

### Service Specifications

#### Database Schema

```sql
-- V1__create_topics_table.sql
CREATE TABLE topics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL CHECK (category IN ('TECHNICAL', 'BUSINESS', 'PROCESS', 'TOOLS', 'TRENDS')),
    last_used DATE,
    usage_count INTEGER NOT NULL DEFAULT 0,
    staleness_score FLOAT,
    staleness_indicator VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT title_min_length CHECK (LENGTH(title) >= 3),
    CONSTRAINT description_min_length CHECK (LENGTH(description) >= 10)
);

CREATE INDEX idx_topics_staleness ON topics(staleness_score);
CREATE INDEX idx_topics_last_used ON topics(last_used);
CREATE INDEX idx_topics_category ON topics(category);

-- Full-text search index
CREATE INDEX idx_topics_search ON topics USING gin(to_tsvector('english', title || ' ' || description));

-- V2__create_topic_usage_history.sql
CREATE TABLE topic_usage_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id UUID NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    event_title VARCHAR(255) NOT NULL,
    event_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(topic_id, event_id)
);

CREATE INDEX idx_usage_history_topic ON topic_usage_history(topic_id);
CREATE INDEX idx_usage_history_date ON topic_usage_history(event_date);
```

#### Domain Model

```java
// Topic.java
@Entity
@Table(name = "topics")
public class Topic {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TopicCategory category;

    @Column(name = "last_used")
    private LocalDate lastUsed;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "staleness_score")
    private Float stalenessScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "staleness_indicator", length = 10)
    private StalenessIndicator stalenessIndicator;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TopicUsageHistory> usageHistory = new ArrayList<>();

    // Domain behavior methods
    public void recordUsage(UUID eventId, String eventTitle, LocalDate eventDate) {
        this.lastUsed = eventDate;
        this.usageCount++;
        this.usageHistory.add(new TopicUsageHistory(this, eventId, eventTitle, eventDate));
        calculateStalenessScore();
        updateStalenessIndicator();
    }

    public boolean isTooRecent() {
        if (lastUsed == null) return false;
        return lastUsed.isAfter(LocalDate.now().minusMonths(6));
    }

    public void calculateStalenessScore() {
        if (lastUsed == null) {
            this.stalenessScore = 100.0f; // Never used, fully stale
            return;
        }

        long daysSinceLastUse = ChronoUnit.DAYS.between(lastUsed, LocalDate.now());
        long sixMonthsInDays = 180;
        long twelveMonthsInDays = 365;

        if (daysSinceLastUse < sixMonthsInDays) {
            // 0-6 months: 0-50 score (not ready)
            this.stalenessScore = (float) (daysSinceLastUse * 50.0 / sixMonthsInDays);
        } else if (daysSinceLastUse < twelveMonthsInDays) {
            // 6-12 months: 50-80 score (caution)
            this.stalenessScore = 50 + (float) ((daysSinceLastUse - sixMonthsInDays) * 30.0 / sixMonthsInDays);
        } else {
            // 12+ months: 80-100 score (ready)
            this.stalenessScore = Math.min(100.0f, 80 + (float) ((daysSinceLastUse - twelveMonthsInDays) * 20.0 / twelveMonthsInDays));
        }
    }

    private void updateStalenessIndicator() {
        if (stalenessScore == null) {
            this.stalenessIndicator = StalenessIndicator.GREEN;
        } else if (stalenessScore < 50) {
            this.stalenessIndicator = StalenessIndicator.RED;
        } else if (stalenessScore < 80) {
            this.stalenessIndicator = StalenessIndicator.YELLOW;
        } else {
            this.stalenessIndicator = StalenessIndicator.GREEN;
        }
    }

    public Integer getRecommendedWaitMonths() {
        if (lastUsed == null) return 0;
        long monthsSinceLastUse = ChronoUnit.MONTHS.between(lastUsed, LocalDate.now());
        return Math.max(0, (int) (12 - monthsSinceLastUse));
    }
}
```

#### Service Layer

```java
// TopicService.java
@Service
@Transactional
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicSimilarityService similarityService;

    public Page<TopicWithMetadata> findTopics(TopicSearchCriteria criteria, Pageable pageable) {
        Specification<Topic> spec = TopicSpecifications.fromCriteria(criteria);
        return topicRepository.findAll(spec, pageable)
            .map(this::toTopicWithMetadata);
    }

    public Topic createTopic(CreateTopicRequest request) {
        // Check for similar topics if requested
        if (request.getCheckSimilarity()) {
            List<SimilarTopic> similar = similarityService.findSimilarTopics(request.getTitle(), 0.7f);
            if (!similar.isEmpty()) {
                throw new SimilarTopicExistsException(similar);
            }
        }

        Topic topic = new Topic();
        topic.setTitle(request.getTitle());
        topic.setDescription(request.getDescription());
        topic.setCategory(request.getCategory());
        topic.calculateStalenessScore();

        return topicRepository.save(topic);
    }

    @Transactional(readOnly = true)
    public List<SimilarTopic> findSimilarTopics(UUID topicId, float threshold) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new TopicNotFoundException(topicId));

        return similarityService.findSimilarTopics(topic.getTitle(), threshold);
    }

    @Transactional(readOnly = true)
    public UsageHistory getUsageHistory(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
            .orElseThrow(() -> new TopicNotFoundException(topicId));

        // Generate 24-month history
        LocalDate now = LocalDate.now();
        LocalDate start = now.minusMonths(24);

        List<MonthlyUsage> months = new ArrayList<>();
        for (LocalDate month = start; month.isBefore(now); month = month.plusMonths(1)) {
            LocalDate monthStart = month.withDayOfMonth(1);
            LocalDate monthEnd = month.withDayOfMonth(month.lengthOfMonth());

            List<TopicUsageHistory> usage = topic.getUsageHistory().stream()
                .filter(u -> !u.getEventDate().isBefore(monthStart) && !u.getEventDate().isAfter(monthEnd))
                .collect(Collectors.toList());

            months.add(new MonthlyUsage(month.getYear(), month.getMonthValue(), usage.size(), usage));
        }

        return new UsageHistory(topicId, months);
    }

    private TopicWithMetadata toTopicWithMetadata(Topic topic) {
        return TopicWithMetadata.builder()
            .id(topic.getId())
            .title(topic.getTitle())
            .description(topic.getDescription())
            .category(topic.getCategory())
            .lastUsed(topic.getLastUsed())
            .usageCount(topic.getUsageCount())
            .stalenessScore(topic.getStalenessScore())
            .stalenessIndicator(topic.getStalenessIndicator())
            .recommendedWaitMonths(topic.getRecommendedWaitMonths())
            .build();
    }
}
```

#### Similarity Service

```java
// TopicSimilarityService.java
@Service
public class TopicSimilarityService {

    private final TopicRepository topicRepository;

    public List<SimilarTopic> findSimilarTopics(String title, float threshold) {
        // Use PostgreSQL full-text search for similarity
        String searchQuery = Arrays.stream(title.split("\\s+"))
            .filter(word -> word.length() > 3)
            .collect(Collectors.joining(" | "));

        List<Topic> candidates = topicRepository.findSimilarByFullText(searchQuery);

        return candidates.stream()
            .map(topic -> {
                float similarity = calculateSimilarity(title, topic.getTitle());
                return new SimilarTopic(topic.getId(), topic.getTitle(), similarity, topic.getLastUsed());
            })
            .filter(st -> st.getSimilarityScore() >= threshold)
            .sorted(Comparator.comparing(SimilarTopic::getSimilarityScore).reversed())
            .limit(10)
            .collect(Collectors.toList());
    }

    private float calculateSimilarity(String s1, String s2) {
        // Simple cosine similarity with TF-IDF
        Set<String> words1 = tokenize(s1);
        Set<String> words2 = tokenize(s2);

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        if (words1.isEmpty() || words2.isEmpty()) return 0.0f;

        return (float) intersection.size() / (float) Math.sqrt(words1.size() * words2.size());
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("\\s+"))
            .filter(word -> word.length() > 3)
            .collect(Collectors.toSet());
    }
}
```

### Acceptance Criteria

1. ✅ All API endpoints match OpenAPI contract exactly
2. ✅ Topic creation enforces validation rules
3. ✅ Similarity detection finds topics >70% similar
4. ✅ Staleness score calculated accurately
5. ✅ Usage history tracks all events
6. ✅ Full-text search works with PostgreSQL
7. ✅ Contract tests pass 100%
8. ✅ Integration tests with TestContainers pass
9. ✅ Performance: Search <500ms, Similarity <200ms
10. ✅ Deployed to dev environment

### Definition of Done

- [x] Database migrations applied
- [x] Domain model implements business rules
- [x] Service layer complete with all methods
- [x] Similarity detection working
- [x] Repository tests passing (>90% coverage)
- [x] Service tests passing (>90% coverage)
- [x] Contract tests validating OpenAPI spec
- [x] Integration tests passing
- [x] Deployed and healthy in dev

**Time:** 3 days (parallel with 2.2b)
**Story Points:** 8
**Status:** Ready for integration (2.2d)

---

## Story 2.2d: Topic Selection Integration

**Template:** `story-integration-tmpl.yaml`
**Duration:** 1 day
**Owner:** Integration Specialist
**Status:** 🔴 Blocked until 2.2b AND 2.2c are Done

### Story

**As an** organizer,
**I want** the topic selection feature to work end-to-end with real data,
**so that** I can use it in production to select topics for events.

**Integration Focus:** This story replaces MSW mocks with real backend APIs and validates complete E2E functionality.

### Integration Tasks

- [ ] Task 1: Disable MSW Mocks
  - [ ] Configure environment variables for production
  - [ ] Ensure MSW only runs in development mode
  - [ ] Update API client to use real endpoints

- [ ] Task 2: E2E Testing
  - [ ] Test topic search and filtering
  - [ ] Test similarity detection warning
  - [ ] Test override with justification
  - [ ] Test create new topic
  - [ ] Test heat map visualization

- [ ] Task 3: Performance Validation
  - [ ] Measure API response times
  - [ ] Measure page load times
  - [ ] Verify <500ms search response

- [ ] Task 4: Deploy and Monitor
  - [ ] Deploy to dev environment
  - [ ] Deploy to staging environment
  - [ ] Run smoke tests
  - [ ] Monitor logs and metrics

### Acceptance Criteria

1. ✅ MSW mocks disabled in production builds
2. ✅ Frontend successfully calls real backend APIs
3. ✅ All user workflows work end-to-end
4. ✅ Similarity warnings appear correctly with real data
5. ✅ Heat map displays actual usage data
6. ✅ Performance requirements met (<500ms search)
7. ✅ E2E tests passing with real backend
8. ✅ Deployed to dev and staging
9. ✅ No regressions in existing features
10. ✅ Ready for production deployment

### Definition of Done

- [x] MSW mocks removed/disabled in production
- [x] All E2E tests passing
- [x] Performance validated
- [x] Deployed to dev successfully
- [x] Deployed to staging successfully
- [x] Smoke tests passing
- [x] CloudWatch monitoring enabled
- [x] Ready for production

**Time:** 1 day
**Story Points:** 3
**Status:** Completes Story 2.2

---

## Summary

### Original Approach
- **1 Story:** 8-10 days sequential
- **1 Developer:** Full-stack, context switching
- **Risk:** Late integration
- **Review:** One massive PR

### Layered Approach
- **4 Stories:** 8 days development, 5 days calendar
- **2-3 Developers:** Parallel work in expertise zones
- **Risk:** Early contract definition, isolated layers
- **Review:** Four focused PRs

### Benefits Realized
- ✅ **37% faster** delivery (5 days vs 8 days)
- ✅ **Parallel development** reduces calendar time
- ✅ **Focused work** improves quality
- ✅ **Early contract** reduces integration risk
- ✅ **Smaller PRs** easier to review
- ✅ **Layer isolation** enables independent testing

### Dependencies Graph
```
2.2a (API Contract - 1 day)
  ├─> 2.2b (Frontend - 3 days parallel)
  └─> 2.2c (Backend - 3 days parallel)
        └─> 2.2d (Integration - 1 day)
```

**Total Calendar Time:** 5 days (with parallel work)
**Total Development Time:** 8 days
**Time Savings:** 3 days (37% improvement)
