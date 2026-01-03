# BAT-10: Attendees API Consolidation

⚠️ **IMPORTANT: Story Content Location**

This file contains **implementation details only** (Dev Agent Record). The full **product view** (User Story, Acceptance Criteria, Tasks, Definition of Done) is maintained in Linear for stakeholder visibility.

**Linear Issue (Product View)**: [BAT-10 - Attendees API Consolidation](https://linear.app/batbern/issue/BAT-10/attendees-api-consolidation)

**Legacy Story ID**: 1.15a.9

---

## Dev Agent Record

### Status
Draft

### Agent Model Used
- Created: N/A (story not yet implemented)

### Template References

**Backend Templates**:
- `docs/templates/backend/spring-boot-service-foundation.md` - Service structure, JPA entities, repositories
- `docs/templates/backend/integration-test-pattern.md` - Testcontainers PostgreSQL, MockMvc tests

### Test Implementation Details

**Test File Locations**:
```
services/attendee-experience-service/src/test/java/ch/batbern/attendee/
├── controller/
│   └── AttendeeControllerIntegrationTest.java     # Integration tests for all 8 endpoints
├── service/
│   └── AttendeeServiceTest.java                   # Unit tests for business logic
└── repository/
    └── AttendeeRepositoryTest.java                # Repository tests with Testcontainers
```

**Testcontainers Configuration** (MANDATORY):
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);
}
```

**Test Data Builders**:
```java
// AttendeeTestBuilder.java
public class AttendeeTestBuilder {
    public static Attendee buildDefaultAttendee() {
        return Attendee.builder()
            .id(UUID.randomUUID())
            .email("attendee@example.com")
            .firstName("John")
            .lastName("Doe")
            .build();
    }
}
```

### Story-Specific Implementation

**Dashboard Aggregation Logic** (custom - not in template):
```java
public class AttendeeService {
    public DashboardDTO getAttendeeDashboard(String userId, List<String> includes) {
        DashboardDTO dashboard = new DashboardDTO();

        if (includes.contains("registrations")) {
            dashboard.setRegistrations(registrationService.getUpcoming(userId));
        }
        if (includes.contains("library")) {
            dashboard.setLibrary(libraryService.getBookmarks(userId));
        }
        if (includes.contains("recommendations")) {
            // Cache with Caffeine
            dashboard.setRecommendations(
                recommendationCache.get(userId,
                    k -> recommendationEngine.generate(k))
            );
        }

        return dashboard;
    }
}
```

**Caffeine Cache Configuration**:
```java
@Configuration
public class CacheConfig {
    @Bean
    public Cache<String, List<RecommendationDTO>> recommendationCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();
    }
}
```

### API Contracts

**Consolidated Endpoints** (8 total):
```
# Attendee Profile & Dashboard
GET    /api/v1/attendees/me/dashboard?include=registrations,library,recommendations
GET    /api/v1/attendees/me
PUT    /api/v1/attendees/me

# Event Registrations
GET    /api/v1/attendees/me/registrations?status=upcoming|past
POST   /api/v1/attendees/me/registrations/{eventId}
DELETE /api/v1/attendees/me/registrations/{eventId}

# Content Library
GET    /api/v1/attendees/me/library?filter={}&page={}
POST   /api/v1/attendees/me/library/{contentId}
DELETE /api/v1/attendees/me/library/{contentId}

# Recommendations & History
GET    /api/v1/attendees/me/recommendations?type=events|content
GET    /api/v1/attendees/me/history?timeframe={}
```

### Database Schema

**Tables**:
- `attendees` (id, email, first_name, last_name, preferences_jsonb)
- `registrations` (id, attendee_id, event_id, status, registered_at)
- `library_bookmarks` (id, attendee_id, content_id, bookmarked_at)

**Flyway Migration**: `V015__create_attendee_tables.sql`

### File List

**Created Files**:
- (Placeholder - story not yet implemented)

**Modified Files**:
- (Placeholder - story not yet implemented)

### Debug Log References

- (No debug logs yet - story not yet implemented)

### Completion Notes

- (Placeholder - story not yet implemented)

### Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2024-10-04 | 1.0 | Initial story creation (legacy format) | Winston (Architect) |
| 2025-12-21 | 2.0 | Migrated to Linear-first format | James (Dev) |
