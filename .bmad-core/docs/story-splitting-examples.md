# Story Splitting Examples - Before & After

This document provides concrete examples of how to split user stories using the new layered templates, showing the transformation from monolithic stories to focused, parallel-development stories.

---

## Example 1: Topic Selection System (Story 2.2)

### ❌ BEFORE: Monolithic Story (8-10 days)

**Story 2.2: Topic Selection System**

**User Story:**
As an organizer, I want to select topics from our backlog with intelligent suggestions, so that I can choose compelling topics while avoiding recent duplicates.

**Scope:**
- Define API endpoints for topic management
- Implement React topic selector component
- Build backend service with similarity detection
- Create database schema
- Add heat map visualization
- Implement staleness calculation
- Deploy everything

**Problems:**
- ❌ Too large - 8-10 days
- ❌ Developer must context-switch between frontend, backend, database, API
- ❌ No parallelization - one person does everything sequentially
- ❌ Late integration - problems discovered at the end
- ❌ Hard to review - massive PR touching everything
- ❌ Hard to test - need full stack running

---

### ✅ AFTER: Layered Stories (8 days dev, 5 days calendar)

#### **Story 2.2a: Topic API Contract** (1 day)

**Template:** `story-api-contract-tmpl.yaml`

**Focus:** Define API contract for topic management

**Deliverables:**
- OpenAPI 3.0 specification for:
  - `GET /api/v1/topics` - List all topics with search/filter
  - `POST /api/v1/topics` - Create new topic
  - `GET /api/v1/topics/{id}` - Get topic details
  - `GET /api/v1/topics/{id}/similar` - Get similar topics
- Request/response schemas
- Validation rules
- Error response formats
- Contract tests
- Mock data for frontend

**OpenAPI Spec Excerpt:**
```yaml
paths:
  /api/v1/topics:
    get:
      summary: List topics with usage history
      parameters:
        - name: search
          in: query
          schema:
            type: string
        - name: includeStale
          in: query
          schema:
            type: boolean
            default: false
      responses:
        '200':
          description: List of topics
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Topic'
```

**Generated Artifacts:**
- TypeScript types → `web-frontend/src/types/generated/api.ts`
- Java DTOs → `event-management-service/src/main/java/ch/batbern/events/dto/`
- Swagger UI documentation
- Postman collection

**Definition of Done:**
- ✅ OpenAPI spec validates
- ✅ Contract tests written
- ✅ TypeScript types generated
- ✅ Java DTOs generated
- ✅ Mock data prepared
- ✅ Gateway routes configured

**Time:** 1 day
**Owner:** API designer or tech lead

---

#### **Story 2.2b: Topic Selector Frontend** (3 days, parallel with 2.2c)

**Template:** `story-frontend-tmpl.yaml`

**Dependency:** 🔴 Blocked until Story 2.2a is Done

**Focus:** Implement topic selection UI with MSW mocks

**Deliverables:**
- React Components:
  - `TopicSelector` - Main selector component
  - `TopicCard` - Individual topic display
  - `TopicHeatMap` - Usage visualization (Recharts)
  - `StalenessIndicator` - Color-coded freshness
  - `SimilarTopicsWarning` - Duplicate warning dialog

- Custom Hooks:
  - `useTopics()` - Query for topic list
  - `useTopicSimilarity()` - Check for similar topics
  - `useTopicSelection()` - Manage selection state

- MSW Configuration:
  ```typescript
  // src/mocks/handlers/topicHandlers.ts
  export const topicHandlers = [
    rest.get('/api/v1/topics', (req, res, ctx) => {
      return res(ctx.json(mockTopics));
    }),
    rest.get('/api/v1/topics/:id/similar', (req, res, ctx) => {
      return res(ctx.json(mockSimilarTopics));
    })
  ];
  ```

- Testing:
  - Component tests with React Testing Library
  - MSW mock responses
  - User interaction tests
  - Accessibility tests

**User Experience:**
- Search and filter topics
- View usage heat map (24 months)
- See staleness indicators (red/yellow/green)
- Get warnings for similar topics
- Override warnings with justification

**Definition of Done:**
- ✅ All components render correctly
- ✅ MSW mocks working
- ✅ Component tests >90% coverage
- ✅ Mobile responsive
- ✅ Accessibility score >90
- ✅ Storybook stories created
- ✅ Works in isolation without backend

**Time:** 3 days
**Owner:** Frontend developer

---

#### **Story 2.2c: Topic Service Backend** (3 days, parallel with 2.2b)

**Template:** `story-backend-tmpl.yaml`

**Dependency:** 🔴 Blocked until Story 2.2a is Done

**Focus:** Implement topic management service with similarity detection

**Deliverables:**
- Domain Model:
  ```java
  @Entity
  public class Topic {
      @Id
      private UUID id;
      private String title;
      private String description;
      private LocalDate lastUsed;
      private Integer usageCount;
      @ElementCollection
      private List<LocalDate> usageHistory;
      private Float stalenessScore;

      // Business methods
      public boolean isTooRecent() {
          return lastUsed.isAfter(LocalDate.now().minusMonths(6));
      }

      public void recordUsage() {
          this.usageHistory.add(LocalDate.now());
          this.usageCount++;
          calculateStalenessScore();
      }
  }
  ```

- Service Layer:
  ```java
  @Service
  public class TopicService {
      public List<Topic> findTopics(TopicSearchCriteria criteria);
      public Topic createTopic(CreateTopicRequest request);
      public List<Topic> findSimilarTopics(UUID topicId);
      public Float calculateSimilarity(Topic t1, Topic t2);
      public Float calculateStalenessScore(Topic topic);
  }
  ```

- Database Schema:
  ```sql
  CREATE TABLE topics (
      id UUID PRIMARY KEY,
      title VARCHAR(255) NOT NULL,
      description TEXT,
      last_used DATE,
      usage_count INTEGER DEFAULT 0,
      staleness_score FLOAT,
      created_at TIMESTAMP NOT NULL,
      updated_at TIMESTAMP NOT NULL
  );

  CREATE TABLE topic_usage_history (
      topic_id UUID REFERENCES topics(id),
      used_date DATE NOT NULL,
      PRIMARY KEY (topic_id, used_date)
  );

  CREATE INDEX idx_topics_staleness ON topics(staleness_score);
  CREATE INDEX idx_topics_last_used ON topics(last_used);
  ```

- Similarity Detection:
  - PostgreSQL full-text search with `ts_rank`
  - TF-IDF-based similarity scoring
  - Cosine similarity calculation
  - Threshold-based warnings (>70% similarity)

- Testing:
  - Unit tests for domain logic (>95% coverage)
  - Repository tests with TestContainers
  - Service integration tests
  - Contract tests validating OpenAPI spec

**Definition of Done:**
- ✅ All endpoints match API contract
- ✅ Domain model implements business rules
- ✅ Database migrations applied
- ✅ Similarity detection working
- ✅ Contract tests pass 100%
- ✅ Integration tests pass
- ✅ Deployed to dev environment

**Time:** 3 days
**Owner:** Backend developer

---

#### **Story 2.2d: Topic Selection Integration** (1 day)

**Template:** `story-integration-tmpl.yaml`

**Dependencies:**
- 🔴 Blocked until Story 2.2b (Frontend) is Done
- 🔴 Blocked until Story 2.2c (Backend) is Done

**Focus:** Connect frontend to real backend and validate E2E

**Deliverables:**
- Remove/Disable MSW Mocks:
  ```typescript
  // .env.production
  REACT_APP_USE_MOCKS=false
  REACT_APP_API_BASE_URL=https://api.batbern.ch
  ```

- API Client Configuration:
  ```typescript
  // Configure real API endpoints
  const apiClient = axios.create({
    baseURL: process.env.REACT_APP_API_BASE_URL,
    timeout: 30000
  });
  ```

- E2E Tests:
  ```typescript
  test('should warn when selecting similar topic', async ({ page }) => {
    await loginAsOrganizer(page);
    await page.goto('/events/new');

    // Select a topic similar to recently used one
    await page.selectOption('[data-testid="topic-selector"]', 'Cloud Security');

    // Should see warning dialog
    await expect(page.locator('[data-testid="similarity-warning"]'))
      .toContainText('70% similar to "Cloud Native Security"');

    // Can override with justification
    await page.fill('[data-testid="override-reason"]', 'Different focus');
    await page.click('[data-testid="confirm-selection"]');

    await expect(page.locator('[data-testid="selected-topic"]'))
      .toContainText('Cloud Security');
  });
  ```

- Performance Validation:
  - Topic list loads <500ms
  - Similarity detection <200ms
  - Heat map renders <500ms
  - No memory leaks

- Integration Testing:
  - All user workflows work E2E
  - Error handling displays correctly
  - Loading states work with real timing
  - Data persists correctly

**Definition of Done:**
- ✅ MSW mocks removed in production
- ✅ Frontend calls real APIs
- ✅ All E2E tests passing
- ✅ Performance requirements met
- ✅ Deployed to dev and staging
- ✅ Smoke tests passing
- ✅ Ready for production

**Time:** 1 day
**Owner:** Integration specialist or full-stack developer

---

### 📊 Comparison: Before vs After

| Aspect | Before (Monolithic) | After (Layered) |
|--------|---------------------|-----------------|
| **Total Dev Time** | 8-10 days | 8 days (1+3+3+1) |
| **Calendar Time** | 8-10 days | 5 days (1 day contract, 3 days parallel, 1 day integration) |
| **Time Savings** | - | **37% faster delivery** |
| **Developers Needed** | 1 full-stack | 2 specialists (parallel work) |
| **Context Switching** | High | Low (focused work) |
| **Review Complexity** | 1 massive PR | 4 focused PRs |
| **Testing Isolation** | Difficult | Easy (layer-specific) |
| **Risk** | Late integration | Early contract definition |
| **Parallelization** | None | Frontend + Backend parallel |
| **Rework Cost** | High (everything coupled) | Low (layers isolated) |

---

## Example 2: Security Headers (Story 1.11)

### ❌ BEFORE: Mixed Infrastructure Story

**Story 1.11: Security Compliance Essentials**

**Problems:**
- Mixed concerns (headers, validation, GDPR, logging)
- Spans multiple services
- Hard to scope

---

### ✅ AFTER: Focused Infrastructure Story

**Story 1.11: Security Compliance Essentials**

**Template:** `story-infrastructure-tmpl.yaml`

**Focus:** Implement security headers and compliance controls

**Scope:**
- Security headers in API Gateway
- Input validation framework
- GDPR endpoints
- Audit logging
- Security scanning

**Why not split?**
- Infrastructure story spanning services
- Cross-cutting security concerns
- Better implemented together for consistency
- Single owner (security/platform team)

**Time:** 3-5 days
**Owner:** Platform engineer or security specialist

---

## Example 3: Speaker Dashboard (Frontend-Only)

### ❌ BEFORE: Unnecessary Backend Work

**Story 3.X: Speaker Dashboard Improvements**

**Scope:**
- Update dashboard UI
- Add new visualizations
- Implement unnecessary backend "improvements"
- Create new API endpoints that just proxy existing ones

**Problems:**
- Backend work not needed
- Extends timeline unnecessarily
- Adds complexity

---

### ✅ AFTER: Frontend-Only Story

**Story 3.Xb: Speaker Dashboard Improvements**

**Template:** `story-frontend-tmpl.yaml`

**Focus:** Enhance speaker dashboard UI using existing APIs

**Scope:**
- Update React components
- Add Recharts visualizations
- Improve UX/UI
- Use existing APIs (no changes needed)

**Why frontend-only?**
- Existing APIs sufficient
- UI/UX improvements only
- No business logic changes
- No database changes

**Time:** 2-3 days
**Owner:** Frontend developer

---

## Example 4: Event Archive Service (Backend-Only)

### ❌ BEFORE: Unnecessary Frontend Work

**Story X.X: Event Archive Service**

**Scope:**
- Background job to archive old events
- Admin UI to trigger archives (not needed)
- Dashboard to view archives (not needed yet)

**Problems:**
- UI work premature
- Backend can work independently
- Frontend could be separate story later

---

### ✅ AFTER: Backend-Only Stories

#### **Story X.Xa: Archive Admin API Contract** (1 day)

**Template:** `story-api-contract-tmpl.yaml`

**Focus:** Define admin API for archive management (optional, for future UI)

**Scope:**
- `POST /api/v1/admin/events/archive` - Trigger archive
- `GET /api/v1/admin/events/archives` - List archives
- Admin-only authentication

---

#### **Story X.Xc: Event Archive Service** (4 days)

**Template:** `story-backend-tmpl.yaml`

**Focus:** Implement background archive service

**Scope:**
- Scheduled job (cron) for archiving events >2 years old
- Archive to S3 with compression
- Database cleanup after archive
- Restore capability
- Admin API implementation (optional)
- No UI needed yet

**Why backend-only?**
- Background service doesn't need UI
- Admin API for future use
- Can add UI later as separate story
- Delivers value without frontend

**Time:** 4 days
**Owner:** Backend developer

---

## Example 5: Simple CRUD Feature

### ❌ BEFORE: Monolithic CRUD Story

**Story X.X: Company Management**

**Scope:**
- API for CRUD operations
- React forms
- Backend service
- Database schema
- Everything at once

**Time:** 6-8 days (sequential)

---

### ✅ AFTER: Two Options

#### **Option A: Full Split** (Recommended for teams >3)

1. **Story X.Xa: Company API Contract** (1 day)
2. **Story X.Xb: Company Management UI** (2 days, parallel)
3. **Story X.Xc: Company Service Backend** (2 days, parallel)
4. **Story X.Xd: Integration** (1 day)

**Total:** 4 days calendar time (vs 6-8 sequential)

---

#### **Option B: Two-Story Approach** (Recommended for small teams)

1. **Story X.X1: Company Management Frontend-First** (3 days)
   - Define API contract inline
   - Implement UI with MSW mocks
   - Complete UX

2. **Story X.X2: Company Service Backend** (2 days)
   - Implement against defined contract
   - Auto-integrate when complete
   - No separate integration story needed for simple CRUD

**Total:** 3 days calendar time (if parallel) or 5 days (if sequential)

**Choose Option B when:**
- Small team (1-2 developers)
- Simple CRUD with no complex logic
- Tight deadline
- Low risk

---

## Example 6: Complex Multi-Domain Feature

### ❌ BEFORE: Mega Story

**Story 3.4: Speaker Assignment with Voting**

**Scope:**
- Speaker brainstorming UI
- Organizer voting system
- Assignment algorithm
- Email notifications
- Real-time updates
- Multiple services involved

**Problems:**
- Way too large (15+ days)
- Spans multiple domains
- Complex coordination
- High risk

---

### ✅ AFTER: Split into Multiple Features

#### **Feature 1: Speaker Brainstorming** (5 days)
1. Story 3.4.1a: Brainstorming API Contract (1 day)
2. Story 3.4.1b: Brainstorming UI (2 days)
3. Story 3.4.1c: Brainstorming Service (2 days)
4. Story 3.4.1d: Integration (1 day)

#### **Feature 2: Organizer Voting** (5 days)
1. Story 3.4.2a: Voting API Contract (1 day)
2. Story 3.4.2b: Voting UI (2 days)
3. Story 3.4.2c: Voting Service (2 days)
4. Story 3.4.2d: Integration (1 day)

#### **Feature 3: Assignment Algorithm** (4 days)
1. Story 3.4.3c: Assignment Service (Backend-only, 4 days)

**Why split into features?**
- Each feature delivers value independently
- Reduces risk
- Enables incremental delivery
- Easier to manage and test
- Brainstorming can work without voting
- Voting can work without assignment
- Assignment depends on voting data

---

## Lessons Learned

### ✅ Do This

1. **Split complex stories** (>5 days) into layered stories
2. **Start with API contract** when APIs are involved
3. **Enable parallel work** whenever possible
4. **Keep infrastructure separate** for cross-cutting concerns
5. **Use frontend-only** when APIs are sufficient
6. **Use backend-only** when UI isn't needed
7. **Integrate at the end** to validate complete flow

### ❌ Don't Do This

1. **Don't split trivial stories** (<2 days total)
2. **Don't split when one developer** and tight timeline
3. **Don't split infrastructure** unless truly independent
4. **Don't create unnecessary layers** (UI for background jobs)
5. **Don't skip integration story** for complex features
6. **Don't start backend/frontend** before API contract is done

---

## Quick Reference: When to Split

| Story Characteristics | Recommendation |
|----------------------|----------------|
| Complex feature (8+ days), multiple developers | Split into 4 stories (a, b, c, d) |
| Moderate feature (4-6 days), 2-3 developers | Split into 4 stories (a, b, c, d) |
| Simple CRUD (3-4 days), small team | Two-story approach or full split |
| Frontend-only, existing APIs | Frontend template only |
| Backend-only, no UI needed | Backend template only (optionally API contract) |
| Infrastructure/cross-cutting | Infrastructure template only |
| Trivial change (<2 days) | Don't split, use appropriate single template |

---

## Next Steps

1. **Review your upcoming stories** using the patterns above
2. **Practice splitting** one complex story as an exercise
3. **Discuss with team** which approach fits best
4. **Start with one split story** in next sprint
5. **Retrospect and refine** based on experience

For more guidance, see [Story Template Selection Guide](story-template-guide.md).
