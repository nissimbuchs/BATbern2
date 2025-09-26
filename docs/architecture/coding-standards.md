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
- [ ] Code follows established patterns and conventions
- [ ] All tests pass and coverage meets requirements
- [ ] API documentation is updated if needed
- [ ] Error handling is comprehensive and user-friendly
- [ ] Security considerations are addressed
- [ ] Performance impact is considered
- [ ] Accessibility requirements are met

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
```java
// Unit tests with JUnit 5
@Test
void shouldCreateEventSuccessfully() {
    // Given
    CreateEventRequest request = new CreateEventRequest("BATbern 2024");

    // When
    Event event = eventService.createEvent(request);

    // Then
    assertThat(event.getTitle()).isEqualTo("BATbern 2024");
}

// Integration tests with Testcontainers
@SpringBootTest
@Testcontainers
class EventServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
}
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