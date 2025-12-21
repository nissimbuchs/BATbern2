# Story: Attendees API Consolidation

**Linear Issue**: [BAT-10](https://linear.app/batbern/issue/BAT-10/attendees-api-consolidation) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](https://linear.app/batbern/issue/BAT-10/attendees-api-consolidation)
- **Acceptance Criteria**: [Linear issue](https://linear.app/batbern/issue/BAT-10/attendees-api-consolidation) (see checkboxes)
- **Tasks/Subtasks**: [Linear subtasks](https://linear.app/batbern/issue/BAT-10/attendees-api-consolidation)
- **QA Results**: [Linear comments](https://linear.app/batbern/issue/BAT-10/attendees-api-consolidation)
- **Status**: [Linear workflow state](https://linear.app/batbern/issue/BAT-10/attendees-api-consolidation)

---

## Dev Agent Record

### Agent Model Used
_To be filled by dev agent_

### Template References

**Implementation Patterns to Use**:
- Backend: `docs/templates/backend/spring-boot-service-foundation.md`
- Backend: `docs/templates/backend/integration-test-pattern.md`

**Existing Code References**:
- Similar to: Story 1.15a (API Consolidation Foundation)
- Similar to: Story 1.15a.4 (Content API patterns)

### Test Implementation Details (HOW to test)

**CRITICAL**: All backend integration tests MUST use PostgreSQL via Testcontainers. NEVER use H2 - it creates false confidence and hides PostgreSQL-specific issues (JSONB types, functions, etc.).

#### Test File Locations (Exact Paths)
**Backend Tests**:
- Unit: `services/attendee-experience-service/src/test/unit/controller/AttendeeControllerTest.java`
- Integration: `services/attendee-experience-service/src/test/integration/controller/AttendeeControllerIntegrationTest.java`
- Repository: `services/attendee-experience-service/src/test/integration/repository/AttendeeRepositoryTest.java`

#### Test Data & Mocks Configuration

**Test Data Builders**:
- AttendeeTestDataBuilder for creating test attendees
- RegistrationTestDataBuilder for test event registrations
- LibraryItemTestDataBuilder for content library items

**Mock Services**:
- Mock Event Management Service for event data
- Mock Content Service for content library
- Mock Recommendation Engine for personalized suggestions

**Test Containers (MANDATORY)**:
- PostgreSQL 16 Alpine via Testcontainers for ALL integration tests
  - All integration tests MUST extend AbstractIntegrationTest
  - Use singleton pattern with withReuse(true) for performance
  - Enable Flyway migrations for production parity

**Test Configuration**:
```properties
# application-test.properties
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

### Story-Specific Implementation

**Deviations from Templates** (max 100 lines):
```java
// ONLY code that differs from templates
// To be filled during implementation

// Personal Dashboard Aggregation
@GetMapping("/api/v1/attendees/me/dashboard")
public ResponseEntity<AttendeeDashboardDTO> getDashboard(
    @RequestParam(required = false) String include
) {
    // Custom aggregation logic for dashboard
    // Combines registrations, library, recommendations
}

// Recommendation Caching Strategy
@Cacheable(value = "attendee-recommendations", key = "#userId")
public List<RecommendationDTO> getRecommendations(UUID userId, RecommendationType type) {
    // Caffeine-based caching for recommendations
}
```

### API Contracts (OpenAPI Excerpts)

```yaml
# Attendee Personal Dashboard
paths:
  /api/v1/attendees/me/dashboard:
    get:
      summary: Get attendee personal dashboard
      parameters:
        - name: include
          in: query
          schema:
            type: string
            enum: [registrations, library, recommendations]
      responses:
        200:
          description: Aggregated dashboard data
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AttendeeDashboardDTO'

  /api/v1/attendees/me/library:
    get:
      summary: Get attendee content library
      parameters:
        - name: filter
          in: query
          schema:
            type: object
        - name: page
          in: query
          schema:
            type: integer
      responses:
        200:
          description: Paginated library items
```

### Database Schema (SQL)

```sql
-- Attendee Profile
CREATE TABLE attendees (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    preferences JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Event Registrations
CREATE TABLE attendee_registrations (
    id UUID PRIMARY KEY,
    attendee_id UUID NOT NULL REFERENCES attendees(id),
    event_id UUID NOT NULL REFERENCES events(id),
    status VARCHAR(50) NOT NULL,
    registered_at TIMESTAMP NOT NULL
);

-- Content Library
CREATE TABLE attendee_library (
    id UUID PRIMARY KEY,
    attendee_id UUID NOT NULL REFERENCES attendees(id),
    content_id UUID NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    bookmarked_at TIMESTAMP NOT NULL
);
```

### Implementation Approach
_To be filled by dev agent during implementation_

### Debug Log
See: `.ai/debug-log.md#bat-10` for detailed implementation debugging

### Completion Notes
_To be filled by dev agent_

### File List
**Created**:
- _To be filled during implementation_

**Modified**:
- _To be filled during implementation_

**Deleted**:
- _To be filled during implementation_

### Change Log
- 2025-12-21: Migrated to Linear-first format

### Deployment Notes
**Performance Targets**:
- Dashboard: <400ms (P95)
- Library: <300ms (P95)
- Recommendations: <600ms (P95)

**Caching Strategy**:
- Recommendations cached in Caffeine (15min TTL)
- Dashboard aggregation optimized with includes parameter

### Status
Draft
