# Coding Standards

## Critical Fullstack Rules

- **Type Sharing:** Always define types in shared-kernel and import from there
- **API Calls:** Never make direct HTTP calls - use the service layer
- **Environment Variables:** Access only through config objects, never process.env directly
- **Error Handling:** All API routes must use the standard error handler
- **State Updates:** Never mutate state directly - use proper state management patterns
- **Company Management:** Always check for existing companies before creating new ones
- **File Uploads:** Use presigned URLs for direct S3 uploads, never proxy through backend
- **Role-Based Access:** Always check user roles before displaying/executing functionality

## Naming Conventions

| Element | Frontend | Backend | Example |
|---------|----------|---------|---------|
| Components | PascalCase | - | `UserProfile.tsx` |
| Hooks | camelCase with 'use' | - | `useAuth.ts` |
| API Routes | - | kebab-case | `/api/user-profile` |
| API Request/Response Fields | camelCase | camelCase | `firstName`, `eventId` |
| Database Tables | - | snake_case | `user_profiles` |
| Company Entities | - | CompanyEntity | `CompanyEntity.java` |
| Domain Events | - | PascalCase + Event | `EventCreatedEvent.java` |

## Git Workflow

### Branch Strategy
- **main** - Production-ready code
- **develop** - Integration branch for features
- **feature/{description}** - Feature development
- **hotfix/{description}** - Critical production fixes
- **release/{version}** - Release preparation

### Commit Message Format
```
type(scope): description

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes
- `refactor`: Code refactoring
- `test`: Adding or fixing tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(event-management): add automated speaker invitation workflow

fix(frontend): resolve infinite loop in event list pagination

docs(api): update OpenAPI specification for partner analytics
```

## Code Quality Standards

### Pre-commit Hooks
```bash
# Install pre-commit hooks
npm run prepare

# Hooks run automatically on commit:
# - ESLint for frontend code
# - Checkstyle for backend code
# - Unit tests for changed files
# - Format checks (Prettier/Spotless)
```

### Code Review Checklist
- [ ] **TDD Followed**: Tests were written before implementation
- [ ] **Test Coverage**: Each acceptance criterion has corresponding tests
- [ ] Code follows established patterns and conventions
- [ ] All tests pass and coverage meets requirements (≥85%)
- [ ] Test names clearly describe expected behavior
- [ ] API documentation is updated if needed
- [ ] Error handling is comprehensive and user-friendly
- [ ] Security considerations are addressed
- [ ] Performance impact is considered
- [ ] Accessibility requirements are met

## Test-Driven Development Standards

### TDD Workflow (Red-Green-Refactor)

**MANDATORY: All new features and bug fixes must follow TDD practices**

1. **RED Phase**: Write failing tests first
   - Write E2E test for the user journey (if applicable)
   - Write integration tests for API endpoints
   - Write unit tests for business logic
   - Verify all tests fail with meaningful error messages

2. **GREEN Phase**: Write minimal code to pass tests
   - Implement only what's needed to make tests pass
   - Don't add features not covered by tests
   - Focus on making it work, not perfect

3. **REFACTOR Phase**: Improve code while keeping tests green
   - Extract methods and classes
   - Remove duplication
   - Improve naming and readability
   - Optimize performance if needed

### Test Naming Conventions

**Frontend Tests:**
```typescript
// Pattern: should_expectedBehavior_when_condition
test('should display error message when form validation fails', () => {});
test('should navigate to dashboard when login succeeds', () => {});
```

**Backend Tests:**
```java
// Pattern: should_expectedBehavior_when_condition
@Test
void should_createEvent_when_validRequestProvided() {}
@Test
void should_throwException_when_duplicateEventExists() {}
```

### Test Organization Structure

```
frontend/
  src/
    components/
      EventCard/
        EventCard.tsx           # Implementation (written after tests)
        EventCard.test.tsx      # Unit tests (written first)
        EventCard.stories.tsx   # Storybook stories

backend/
  src/test/
    unit/                      # Fast, isolated tests
      domain/                  # Domain logic tests
      service/                 # Service layer tests
    integration/               # API and database tests
      controller/              # REST controller tests
      repository/              # Database integration tests
    e2e/                      # Full workflow tests
      workflows/               # Complete user journeys
```

### Coverage Requirements

**Per Acceptance Criteria:**
- Each acceptance criterion must have at least one test
- Complex criteria should have multiple test cases
- Edge cases and error scenarios must be covered

**Minimum Coverage Targets:**
- Unit Tests: 90% for business logic
- Integration Tests: 80% for APIs
- Overall: 85% line coverage

### Test-First Commit Requirements

**Commit Workflow:**
1. First commit: Failing tests with clear test names
2. Second commit: Implementation to make tests pass
3. Third commit: Refactoring (if needed)

**Example Commit Messages:**
```bash
test(event-management): add tests for event creation workflow
feat(event-management): implement event creation to pass tests
refactor(event-management): extract event validation logic
```

## Testing Standards

### Frontend Testing
```typescript
// Component tests with React Testing Library
test('displays event details correctly', () => {
  render(<EventCard event={mockEvent} />);
  expect(screen.getByText(mockEvent.title)).toBeInTheDocument();
});

// Hook tests
test('useAuth returns user data', () => {
  const { result } = renderHook(() => useAuth());
  expect(result.current.user).toBeDefined();
});
```

### Backend Testing

**CRITICAL: Production Parity for Integration Tests**

All integration tests MUST use PostgreSQL via Testcontainers to ensure production parity. Never use H2 or in-memory databases for integration tests, as this creates false confidence and hides database-specific issues (e.g., JSONB types, PostgreSQL functions, etc.).

```java
// Unit tests with JUnit 5 - fast, isolated
@Test
void shouldCreateEventSuccessfully() {
    // Given
    CreateEventRequest request = new CreateEventRequest("BATbern 2024");

    // When
    Event event = eventService.createEvent(request);

    // Then
    assertThat(event.getTitle()).isEqualTo("BATbern 2024");
}

// Integration tests - MUST extend AbstractIntegrationTest
@Transactional
class EventControllerIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_createEvent_when_validDataProvided() throws Exception {
        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "title": "BATbern 2024",
                        "eventNumber": 123,
                        "date": "2024-12-15T18:00:00Z",
                        "registrationDeadline": "2024-12-10T23:59:59Z",
                        "venueName": "Kornhausforum",
                        "venueAddress": "Kornhausplatz 18, 3011 Bern",
                        "venueCapacity": 200,
                        "organizerId": "550e8400-e29b-41d4-a716-446655440000"
                    }
                    """))
                .andExpect(status().isCreated());
    }
}
```

**AbstractIntegrationTest Base Class:**

All integration tests should extend this base class:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    // Singleton PostgreSQL container - reused across all tests for performance
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);  // Performance optimization

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**Test Configuration (application-test.properties):**

```properties
# PostgreSQL via Testcontainers (configured dynamically)
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# Let Flyway manage schema - validate only
spring.jpa.hibernate.ddl-auto=validate

# Enable Flyway migrations for production parity
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

### End-to-End Testing
```typescript
// Playwright E2E tests
test('organizer can create and publish event', async ({ page }) => {
  await page.goto('/organizer/events');
  await page.click('[data-testid="create-event-button"]');
  await page.fill('[data-testid="event-title"]', 'Test Event');
  await page.click('[data-testid="save-event"]');
  await expect(page.locator('[data-testid="event-created-message"]')).toBeVisible();
});
```

## Documentation Standards

### API Documentation
- All endpoints must have OpenAPI 3.1 specifications
- Include request/response examples
- Document all error codes and scenarios
- Provide usage examples for complex workflows

### Code Documentation
```java
/**
 * Creates a new event with automated workflow initialization.
 *
 * @param request The event creation request containing title, date, and venue
 * @return The created event with generated ID and initial status
 * @throws EventValidationException if the request fails business rule validation
 * @throws DuplicateEventException if an event already exists for the same quarter
 */
public Event createEvent(CreateEventRequest request) {
    // Implementation
}
```

### Architecture Documentation
- Update relevant architecture documents when making structural changes
- Include sequence diagrams for complex workflows
- Document integration patterns and dependencies
- Maintain decision records (ADRs) for significant architectural choices

## Performance Standards

### Frontend Performance Targets
- First Contentful Paint (FCP): < 1.5s
- Largest Contentful Paint (LCP): < 2.5s
- Cumulative Layout Shift (CLS): < 0.1
- First Input Delay (FID): < 100ms
- JavaScript Bundle Size: < 250KB

### Backend Performance Targets
- API Response Time (P95): < 200ms
- Database Query Time: < 50ms
- Error Rate: < 0.1%
- Uptime: > 99.9%

### Performance Monitoring
```typescript
// Frontend performance tracking
import { getCLS, getFID, getFCP, getLCP } from 'web-vitals';

getCLS(metric => analytics.track('CLS', metric));
getFID(metric => analytics.track('FID', metric));
getFCP(metric => analytics.track('FCP', metric));
getLCP(metric => analytics.track('LCP', metric));
```

```java
// Backend performance metrics
@Timed(value = "event.creation.time", description = "Event creation time")
@Counter(value = "event.creation.count", description = "Event creation count")
public Event createEvent(CreateEventRequest request) {
    // Implementation with automatic timing
}
```