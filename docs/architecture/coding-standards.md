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
| Element | Frontend | Backend | Example |
| Enums | UPPER_CASE | UPPER_CASE (Java/API) → lowercase_snake_case (DB via AttributeConverter) | `RegistrationStatus.CONFIRMED` → `'confirmed'` |

**Enum Value Flow (Important!):**
- **Database Storage**: `lowercase_snake_case` (e.g., `'confirmed'`, `'speaker_brainstorming'`)
- **Java Code**: `UPPER_CASE` (e.g., `RegistrationStatus.CONFIRMED`, `EventWorkflowState.SPEAKER_BRAINSTORMING`)
- **JSON/API Request/Response**: `UPPER_CASE` (e.g., `"CONFIRMED"`, `"SPEAKER_BRAINSTORMING"`)
- **Frontend TypeScript**: `UPPER_CASE` (e.g., `RegistrationStatus.CONFIRMED`)
- **Conversion**: JPA `@Converter` (AttributeConverter) handles Java ↔ Database. Jackson uses default serialization (UPPER_CASE).

**Why this pattern?**
- Keeps API consistent with Java enums (no transformation needed for JSON)
- Database uses lowercase for PostgreSQL conventions and readability
- Single conversion point (AttributeConverter) instead of multiple transformers

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

Hooks live in `.githooks/` and are installed by `make install` (#973). Verify with
`git config core.hooksPath` — it should print `.githooks`.

```bash
make install                      # installs deps AND hooks
make install-hooks                # hooks only, if deps are already there
./.githooks/install-hooks.sh      # the installer itself; sets core.hooksPath=.githooks
npm install                       # root `prepare` runs the installer too
```

Until #973 nothing invoked that installer. A fresh clone therefore committed and pushed
with no Checkstyle, no ESLint and no conventional-commit check, while this document claimed
all three were enforced — and the `--no-verify` warning below guarded a gate that was not
there. `scripts/ci/verify-githooks.sh` now asserts the wiring on every PR (`verify-hooks`
job in `build.yml`), so the promise and the mechanism cannot drift apart again silently.

What actually runs (see `.githooks/pre-commit`), all scoped to staged files:
- **Frontend** (anything under `web-frontend/`): `lint-staged`, which runs
  `eslint --fix --max-warnings 0` then `prettier --write` on staged `.ts/.tsx/.js/.jsx`,
  and `prettier --write` on staged `.json/.css/.md`. It **fixes and re-stages** rather
  than rejecting, and it operates on the *staged* content, so partially staged files
  (`git add -p`) are handled correctly. It is invoked from `web-frontend/` because
  lint-staged scopes to its cwd — from the repo root its `*.md` glob would rewrite all of
  `docs/`.
- **Generated API types**: `npm run check:api-types`, but only when `docs/api/`,
  `web-frontend/package.json`, `web-frontend/package-lock.json` or
  `web-frontend/src/types/generated/` is staged
- **Backend** (`.java`): `./gradlew checkstyleMain checkstyleTest`

`.githooks/pre-push` then runs the test suites for whichever components changed, and
`.githooks/commit-msg` validates conventional-commit format via commitlint.

There is no Vitest or Spotless step — `pre-push` runs the full frontend suite, so running
related tests again at commit time only slows down the hook people are most tempted to
bypass. This section used to give `npm run prepare` as the install command at a point when
no `package.json` defined one; #973 added that script to the ROOT `package.json` (delegating
to the same installer), so the command now works — but `make install` is the documented
entry point and the one CLAUDE.md points a newcomer at.

**Infrastructure (CDK), added in #975.** `infrastructure/**/*.ts` had no formatting or lint
check anywhere — not in the hook, not in `make format-check`, not in CI — which made the code
that provisions production the least-checked code in the repo. 55 of its 93 tracked files had
drifted. It now has:

- a second `lint-staged` invocation in `.githooks/pre-commit`, run from `infrastructure/`
  with its own `.lintstagedrc.json` (`*.ts -> prettier --write`). It cannot be folded into the
  frontend one: lint-staged scopes to its cwd, and running it from the repo root would
  `prettier --write` all of `docs/`.
- `npm run format` / `format:check` in `infrastructure/package.json`, wired into
  `make format` and `make format-check`.
- **the authoritative gate:** a `Prettier format check` step in `build.yml`'s
  `build-infrastructure` job. The hook warns and *skips* when `infrastructure/node_modules`
  is absent rather than blocking, so CI is what actually enforces this.
- `infrastructure/.prettierignore`, because prettier globs the filesystem rather than the git
  index — without it `cdk.out` put ~1500 synthesised asset files in front of the 93 real ones.

`prettier` and `lint-staged` are pinned to the same ranges as `web-frontend` on purpose: a
different prettier major between the two trees would format the same file two ways depending
on which subtree's binary ran.

**Still a gap: no ESLint.** ESLint 10 requires flat config and `infrastructure/` has no
`eslint.config.*`, so `npx eslint` exits 2 there. Authoring one catches real bugs rather than
formatting and is tracked separately in #975.

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
                        "organizerUsername": "john.doe"
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

### Lambda Handler Testing (Infrastructure)

Lambda handlers are **deployed artifacts**, not just code. CDK unit tests (`Template.fromStack`) verify that a Lambda is *declared* in CloudFormation — they do **not** test that the handler module can load and run. These are two completely different things, and only one of them catches production failures.

**Mandatory for every Lambda function:**

1. **Handler unit test** — directly imports and calls the handler with representative inputs:
   ```typescript
   // infrastructure/test/unit/lambda/<handler-name>.test.ts
   test('module loads without crashing', async () => {
     const { handler } = await import('../../../lib/lambda/my-handler/index');
     expect(typeof handler).toBe('function');
   });

   test('passes through / returns expected shape', async () => {
     const { handler } = await import('../../../lib/lambda/my-handler/index');
     const result = await handler(mockEvent as any, {} as any);
     expect(result).toMatchObject({ statusCode: 200 });
   });
   ```

2. This test runs without the production dependencies installed. **If it crashes on import, the Lambda will return 503 in production** — that is the signal the test is designed to catch.

**Bundling rule — native dependencies:**

Any Lambda that uses a native binary package (sharp, pg-native, canvas, etc.) MUST use Docker for production bundling. The local `tryBundle` path MUST NOT include native module installation. Pattern to follow:

```typescript
local: {
  tryBundle(outputDir) {
    if (process.env.JEST_WORKER_ID || process.env.NODE_ENV === 'test') {
      // Lightweight stub for Jest — tests check CFn properties, not Lambda code
      require('fs').writeFileSync(path.join(outputDir, 'index.js'), 'exports.handler = async () => ({});');
      return true;
    }
    return false; // always use Docker for real deploys → correct Linux x64 binary
  },
},
```

**Why TypeScript and ESLint cannot catch this:** Static analysis operates on source code type correctness. It has no model of what files will exist in the deployed Lambda zip. A static `import nativePkg from 'native-pkg'` type-checks fine; the crash happens at Lambda cold-start when the binary is missing. The handler unit test is the only pre-deploy check that catches this class of failure.

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

## JPA Performance Best Practices

### Fetch Strategy Guidelines

**CRITICAL: Always use LAZY fetch for collections to avoid N+1 query problems**

```java
// ✅ Correct - LAZY fetch (JPA default for @OneToMany/@ManyToMany)
@OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
private List<Child> children = new ArrayList<>();

// ✅ Correct - Explicit LAZY fetch for @ElementCollection
@ElementCollection(fetch = FetchType.LAZY)
@CollectionTable(name = "user_roles")
private Set<Role> roles = new HashSet<>();

// ✅ Correct - LAZY fetch for @ManyToOne
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_id")
private Parent parent;

// ❌ Wrong - EAGER causes N+1 queries
@ElementCollection(fetch = FetchType.EAGER)  // DON'T DO THIS
private Set<Role> roles = new HashSet<>();
```

### Use JOIN FETCH for Bulk Loading

When you need to load collections, use repository methods with JOIN FETCH:

```java
// ✅ Correct - Repository method with JOIN FETCH
@Query("""
    SELECT DISTINCT u FROM User u
    LEFT JOIN FETCH u.roles
    ORDER BY u.lastName ASC
    """)
Page<User> findAllWithRoles(Pageable pageable);

// ❌ Wrong - Loading entities in a loop (N+1 query)
List<User> users = userRepository.findAll();
for (User user : users) {
    user.getRoles().size();  // Triggers separate query per user!
}
```

### Database-Level Pagination

Always paginate at the database level, never in memory:

```java
// ✅ Correct - Database pagination
Page<User> users = userRepository.findAll(PageRequest.of(page, limit));

// ❌ Wrong - In-memory pagination (loads everything)
List<User> allUsers = userRepository.findAll();
List<User> pageUsers = allUsers.subList(start, end);
```

### N+1 Query Detection

- **Local Development**: Enable SQL logging to spot N+1 queries
- **Integration Tests**: Use `@Sql` with actual PostgreSQL (Testcontainers)
- **Code Review**: Always check fetch strategies on `@ElementCollection`, `@OneToMany`, `@ManyToMany`

```yaml
# application-development.yml - Enable SQL logging
spring.jpa.show-sql: true
spring.jpa.properties.hibernate.format_sql: true
logging.level.org.hibernate.SQL: DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### Common N+1 Pitfalls

1. **@ElementCollection with EAGER fetch** - Most common (found in User.roles)
2. **Loading parent entities without JOIN FETCH** - Common in list endpoints
3. **Accessing lazy collections in loops** - Classic N+1 pattern
4. **DTO mapping with nested entity access** - Triggers lazy loading

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