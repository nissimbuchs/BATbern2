# Coding Standards

## Critical Fullstack Rules

- **Type Sharing:** Always define types in shared-kernel and import from there
- **API Calls:** Never make direct HTTP calls - use the service layer
- **Environment Variables:** Access only through config objects, never process.env directly
- **Error Handling:** All API routes must use the standard error handler
- **State Updates:** Never mutate state directly - use proper state management patterns
- **Company Management:** Always check for existing companies before creating new ones
- **File Uploads:** Use presigned URLs for direct S3 uploads, never proxy through backend
- **Presigned links:** build them with `ch.batbern.shared.utils.S3Links` (`uploadUrl`, `downloadUrl`), never with an inline `Put/GetObjectPresignRequest` (#1187). A presigned link is a bearer credential until it expires: never log it, log the S3 key.
- **Role-Based Access:** Always check user roles before displaying/executing functionality
- **Module walls:** frozen ArchUnit rules in the EMS and CUMS `test` task fail a new EMS module-wall violation or a new client toward a downstream service; never edit the store to accept one (`docs/architecture/module-map.md` → "Gates")
- **Tests:** Mock only at the process boundary, never our own code (see "Test fidelity rules" under Testing Standards). A CI ratchet keeps the count of old own-code mocks from rising

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
- **Database Storage**: `lowercase_snake_case` (e.g., `'confirmed'`, `'agenda_published'`)
- **Java Code**: `UPPER_CASE` (e.g., `RegistrationStatus.CONFIRMED`, `EventWorkflowState.AGENDA_PUBLISHED`)
- **JSON/API Request/Response**: `UPPER_CASE` (e.g., `"CONFIRMED"`, `"AGENDA_PUBLISHED"`)
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

**Prettier is enforced in CI for BOTH trees as of #1008.** This is worth stating explicitly
because the obvious assumption is wrong: the pre-commit hook runs `prettier --write` on *staged*
files and re-stages them, so it prevents new drift and is structurally incapable of noticing an
existing backlog. Until #1008 nothing in CI ran prettier on `web-frontend` at all —
`build-frontend` ran `lint:ci` and the tests — so 12 files under `src/` had drifted and
`make format-check` had been red on `develop` with nobody looking.

| tree | pre-commit | CI gate |
|---|---|---|
| `web-frontend` | `lint-staged` (eslint --fix, prettier --write) | `Prettier format check` + `lint:ci` in `build-frontend` |
| `infrastructure` | `lint-staged` (eslint --fix, prettier --write) | `Prettier format check` + `ESLint` in `build-infrastructure` |

Two scope corrections landed with it, both of which had made the check narrower than it looked:

- `format`/`format:check` in `web-frontend` were scoped to `src/**`, leaving **220 tracked files**
  outside — all of `e2e/`, the root configs, and the 110 locale bundles. They now run against `.`
  and rely on `.prettierignore`, which is the honest scope.
- `.prettierignore` gained `playwright-report`, `test-results`, `blob-report` and
  `.playwright-auth-*.json`. All are gitignored, but prettier globs the **filesystem** rather
  than the git index, so without them anyone who had run the E2E suite locally got
  `format:check` failures on generated report HTML — a gate that fails for reasons nobody can act
  on is a gate people stop believing.

`lint:ci` also went from `--max-warnings 50` to `0`. Measured first: the tree produced 0 errors
and 0 warnings, so the allowance was pure headroom for silent accumulation.

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

**ESLint, added as the #975 follow-up.** `infrastructure/eslint.config.mjs` (flat config) runs
`@eslint/js` recommended plus `typescript-eslint` recommended, wired into the same three places
as prettier: the `infrastructure/` lint-staged invocation, `make lint-node`, and an `ESLint` step
in `build-infrastructure` at `--max-warnings 0`. The baseline is clean, so it stays at zero —
`web-frontend` allows 50 only because it inherited a backlog.

**It is deliberately NOT type-aware, and that was measured rather than assumed.** Across all 93
tracked files, `recommendedTypeChecked` reported 1609 problems while the rules that catch real
bugs reported nothing at all:

| rule | findings |
|---|---|
| `no-floating-promises` | 0 |
| `no-misused-promises` | 0 |
| `await-thenable` | 0 |
| `no-base-to-string` (lib/+bin/) | 0 |

Those were confirmed to be evaluated at severity 2 via `eslint --print-config`, not silently
skipped. The 1609 were almost entirely `no-unsafe-*` cascading off `any`-typed Lambda mock events
in tests. Type-aware linting would cost a second `tsconfig` and roughly 5× the runtime to buy 17
`no-unnecessary-type-assertion` hits and no bugs. CDK stack construction is synchronous, which is
precisely why the promise rules find nothing — **revisit this if genuinely async code lands in
`infrastructure/`.**

Two rule relaxations, both scoped:

- `no-explicit-any` is **off under `test/**`**. Test files legitimately cast mock Cognito/SNS
  events as `any`; building fully-typed trigger events adds no coverage and buries the assertion.
  268 of the 274 hits were there, against 6 in `lib/`+`bin/`. Enforcing it would mean ~270 inline
  disables or ~270 pointless type literals.
- `no-require-imports` is off under `test/**` and the Lambda source trees, where `require()` sits
  below a `jest.mock()` call or behind a try/catch for an optional native dep.

`no-unused-vars` is the rule that earned its keep on adoption: `tsconfig.json` sets
`noUnusedLocals` and `noUnusedParameters` to **false**, so `tsc` cannot see unused imports or
parameters at all. It found 18 in `lib/`+`bin/` — six dead `ecsPatterns` imports left over from
the move to Service Connect, an unused `subscriptions` import, and a `FORWARDING_DOMAIN` env read
that nothing consumed.

### Code Review Checklist
- [ ] **TDD Followed**: Tests were written before implementation
- [ ] **Test Coverage**: Each acceptance criterion has corresponding tests
- [ ] **Test Fidelity**: New and touched tests follow the "Test fidelity rules" (mocks at the process boundary only, positive control for absence, effect not call)
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

// Integration tests - MUST extend AbstractIntegrationTest. Prefer no class-level @Transactional on
// a write IT (clean up in @AfterEach); where one exists, FlushBeforeRollback flushes before the
// rollback so DB constraints and triggers run (see "Test fidelity rules").
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

### Test fidelity rules

Story 17.0a, from the test-fidelity analysis (`docs/plans/test-fidelity-proposal-2026-11.md`): in
15 of 57 production bugs since 2026-08, a mock sat exactly on the code that was wrong, and the
test passed because the behaviour under test was mocked. These rules apply to every **new** test
and to every test a story **touches**. They are binding for agents as well (the `bmad-build`
override and `_bmad-output/project-context.md` point here).

#### Mock at the process boundary, nowhere else

A test may replace only what leaves the process: SES, S3, Cognito, OpenAI, SQS, the
clock, and **another service's HTTP API at the wire** (never its Java client). Everything inside
the process runs for real: our services, repositories, mappers, validators, templates, the
security filter chain, our hooks and our service modules.

| Layer | Allowed to replace | Not allowed |
|---|---|---|
| Backend IT | `SesClient`, `S3Client`, Cognito client, OpenAI HTTP, the peer service via `MockRestServiceServer` / WireMock | `@MockitoBean` of own services, repositories, `UserApiClient`, `EmailService`, `SlugGenerationService` |
| Backend unit | pure logic only; a unit test that needs more than 2 mocks is at the wrong test level | `verify(mock).call(eq(<expected value>))` as the main assertion |
| Frontend component | the network via MSW; Amplify; browser APIs that jsdom lacks | `vi.mock('@/services/...')`, `vi.mock('@/hooks/...')`, `vi.spyOn` on an imported service or hook module, `vi.mock` of child components of the screen under test |
| Frontend i18n | real i18next with the real `en` locale files | the per-file `react-i18next` stub |

Unit tests of pure functions stay as they are. The rule targets tests that claim to test
behaviour while the behaviour is mocked. Until the shared harnesses exist (17.0b: S2S contract
harness, in place for public `/api/v1` calls since 17.0b1 and for `/internal/**` since 17.0b2;
17.0c: MSW harness, in place since 17.0c1), a new test that cannot avoid an own-code mock says why
in a comment, and the ratchet below makes that visible in review.

#### S2S contract harness (backend)

Stories 17.0b1 and 17.0b2. A service-to-service client is tested as itself, on its real
`RestTemplate`, against the provider's spec: `docs/api/` for the public `/api/v1` API,
`docs/api/internal/{cums,ems,pcs}-internal.openapi.yml` for `/internal/**`. `ContractServer` (shared-kernel test fixtures,
`ch.batbern.shared.test.contract`) binds `MockRestServiceServer` to the client's `RestTemplate`,
validates every request against the spec (path and method exist, parameters within their limits,
body against its schema, `Authorization` where the spec requires it) and answers with the spec's
example, or with a test body that is first checked against the response schema. A request the
provider would reject fails the test with the validator's message, as an `AssertionError` that a
client's `catch (Exception e)` cannot swallow:

```java
var server = ContractServer.bindTo(restTemplate, "docs/api/companies-api.openapi.yml");
server.expect(GET, "/api/v1/companies").respondWithExample();   // or .respondWith(OK, json)
var companies = client.getAllCompanies();                       // request validated, example returned
server.verify();
```

Client contract tests live next to the client (`UserApiClientImplContractTest`,
`CompanyServiceClientContractTest`, ...). An IT whose code calls a peer service binds the harness to
the context's `RestTemplate` bean in `@BeforeEach` instead of a `@MockitoBean` of the client, and
calls `close()` in `@AfterEach` (the bean is shared by the cached context);
`.ignoringMissingAuthorization()` is allowed there only because a `@WithMockUser` caller has no JWT
to forward. Each provider proves its side in `S2sProviderContractIntegrationTest` (CUMS, EMS, PCS):
every consumed operation runs through the real controller and `ProviderContract` checks the
response, and the examples, against the same spec. A spec without a usable example for a consumed
operation gets one. How the harness reads `null`, `format: time` and the e-mail formats (as the
generated code does) is documented in `GeneratedCodeSemantics`. An e-mail property that can hold a
stored address is `format: idn-email` (CUMS stores IDN domains, `x@müller.ch`); a request property
also carries the #1102 `pattern` (`EmailAddresses.OPENAPI_PATTERN`), which the generator turns into
`@Pattern` (it emits no `@Email` for `idn-email`). The harness checks both e-mail formats as a
mailbox shape, because the validator's IANA TLD list rejects the reserved `.example` test domains.
`null` is valid only where the spec says `nullable` (owner decision 2026-11-09): the services write
an unset field as `null`, so a response property they can leave unset is marked `nullable: true`
(3.1: a nullable reference is `oneOf: [$ref, {type: 'null'}]`), and a `null` the spec does not
allow fails the test, in the S2S harness and in the MSW harness's schema validator alike. The
reverse holds for presence (4.9b, owner decision 2026-11-09): a response property that every
producing code path sends is in the schema's `required`, nullable or not (`required` says present,
`nullable` says it may be `null`), so the generated types do not call it optional. A property
becomes `required` only when the integration suites show it in every response and the producing
code confirms it (a class without `@JsonInclude(NON_NULL)` writes every field; a `NON_NULL` class
needs the value to be always set).

`/internal/**` (17.0b2): every internal operation is specified in `docs/api/internal/` (one spec per
provider, server `/`, full paths; schemas `$ref` the public components where the DTO is generated
from them). There is no unvalidated way to answer a call: a path no bound spec has fails, naming
method, path and spec. A client whose one `RestTemplate` reaches several specs binds them all
(`bindTo(restTemplate, USERS, CUMS_INTERNAL)`). In EMS, `CumsContractHarness` (test
`ch.batbern.events.config`, picked up by the application's component scan like `TestAwsConfig`)
is CUMS for every integration test context: the real `UserApiClientImpl` runs, defaults answer the
user, user-list and email-owner reads, and a test registers other answers (`cums.user(...)`,
`cums.answer(POST, "/api/v1/users/provision", ...)`) and reads what was sent (`cums.calls(...)`),
then calls `cums.reset()`. A test that binds its own `ContractServer` to that `RestTemplate` (for
PCS) displaces the CUMS harness for its duration, and CUMS calls then fail as "not an operation of
..."; to keep CUMS, bind with `.onlyFor(uri -> !uri.toString().startsWith(cumsBaseUrl))` so CUMS
requests fall through to the harness, or bind both specs (`bindTo(restTemplate, PARTNERS,
CUMS_INTERNAL)`) and expect the CUMS calls there; `close()` in `@AfterEach` restores the harness. In PCS, `CumsInternalUsers` registers the user lookup on the test's
`ContractServer`. The public tooling (frontend path check, type generation, API similarity, ZAP
coverage, access-control probe, `lint:api`) does not read `docs/api/internal/`; it is linted by
`npm run lint:api-internal` with its own reduced ruleset (`docs/api/internal/.spectral.yaml`).

#### Response conformance gate (backend)

Owner decision 2026-11-09. Every 2xx JSON response an integration test receives through `MockMvc`
must be valid against the response schema of its operation in the service's own specs (CUMS:
companies, users, cums-internal; EMS: events-core, `event-*`, topics, ems-internal; PCS: partners,
partner-meetings, pcs-internal). `AbstractIntegrationTest` imports `ResponseConformanceSetup`,
which registers `ResponseConformance` (shared-kernel test fixtures, `ch.batbern.shared.test.contract`)
as an `alwaysDo` handler on the auto-configured `MockMvc`; the validator is the S2S harness's
(`OpenApiContract` with `GeneratedCodeSemantics`), there is no second one. A request of a path no
own spec has (actuator, dev endpoints), a non-2xx answer, an empty body and non-JSON bodies (CSV,
ICS, XLSX) are not checked. Where several specs match, the most specific template wins
(`/events/attendance-summary` over `/events/{eventCode}`).

A violation fails the request that produced it, inside its test, with the validator's message and
the body: fix the response (production code), the spec, or the test data that the spec rejects. A
violation that needs an owner decision may go into the service's committed baseline
`src/test/resources/response-conformance-baseline.txt`, one line per `operationId pointer kind`
with array indices as `*` (`listCompanies /data/*/createdBy pattern`,
`getCompany / required:updatedAt`). Adding a line is review matter; the file only shrinks.
`gradle/response-conformance.gradle` (applied by each service's `build.gradle`, which lists the
specs) passes the configuration to the test JVM and, after a full run (no `--tests`, no
`-PskipIntegration`), fails on a STALE line that no response produces any more. Remove it, or rewrite
the file from a full run: `./gradlew :services:<svc>:test -PupdateResponseConformanceBaseline`
(refused on a partial run). Outside Gradle (no system properties) the gate is off. Since 4.9b (2026-11-10)
all three baselines (CUMS, EMS, PCS) are empty.

#### Frontend: the MSW harness (17.0c1)

Every Vitest test runs against one MSW server (`web-frontend/src/test/msw/`, started in
`src/test/setup.ts`). It is the frontend twin of `ContractServer`:

- **Every request is checked against `docs/api/*.openapi.yml`** with the 17.3 `specMatcher`: the
  path exists, the method exists for it, every query parameter is declared and within its enum,
  minimum and maximum. `/api/...` (the test baseURL of `apiClient`) and `/api/v1/...` are the same
  operation. A request outside the specs fails as a network error **and** fails the test afterwards
  with the matcher's reason (`SpecViolationError`), so code that swallows the error cannot hide it.
  `docs/api/internal/` is not frontend-callable and not served.
- **Default answer:** for `listUsers`, `searchUsers`, `listEvents` and `listRegistrations`, the
  production-shaped fixture set with real paging (17.0d, below); for every other operation the spec
  example of its lowest 2xx response (an empty body for a 204 or a binary download). Every JSON
  2xx response in the specs has one; `examples.test.ts` guards that it exists, validates against
  the schema and carries no personal data.
- **Overrides by `operationId`**, typed from `src/types/generated/`: a missing required field or a
  wrong shape fails `npm run type-check` (`tsconfig.test-harness.json` covers all of `src`, tests
  included, see "Frontend: tests are type-checked" below). An operationId two specs share is qualified by its spec
  (`topics-api/listTopics`, `partners-api/listTopics`).

```ts
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';

api.on('listCompanies', ({ query }) => ({ data: [company], pagination }));   // typed body
api.on('getCompany', () => HttpResponse.json(problem, { status: 404 }));      // any Response
api.on('createCompany', ({ body }) => created, { status: 201 });
render(<CompanyPage />);                                                      // real hooks, services
expect(api.calls('listCompanies')[0].query.get('search')).toBe('Müller');     // what was sent
```

Overrides and recorded calls are reset after every test. Render the real screen with its real
hooks and services; do not `vi.mock` them. A test that talks to another host (an S3 presigned URL)
adds its own handler with `server.use(...)` from `@/test/msw/server`, never for an `/api` URL.
i18n stays the real i18next with `en` from `setup.ts`; a per-file `vi.mock('react-i18next')` is
counted by the ratchet (`fe-mock-i18n`).

**Auth, stores, WebSockets (17.0c2).** A screen that calls `useAuth` gets its state from
`src/test/auth.tsx`: `renderWithAuth(ui, { auth: signedInAs('organizer') })` (or
`AuthTestProvider` around a custom tree; `signedOut()`, `anAuthState(user, overrides)`) puts a
signed-in or signed-out state into the real `AuthContext`, so `useAuth` and every consumer run for
real, and `hasRole`, `hasPermission` and `canAccess` are the provider's own rules
(`src/contexts/authRules.ts`). Never `vi.mock('@/hooks/useAuth')`. Zustand stores run for real: set
the state a test needs with `useXStore.setState(...)` and reset it in `beforeEach` (a persisted
store also needs `localStorage.clear()`). A STOMP/SockJS connection is a network boundary: mock
the third-party packages (`@stomp/stompjs`, `sockjs-client`) or rely on the condition the client
already uses to stay off, never our own WebSocket hook or client.

New examples come from the local anonymized database:
`cd web-frontend && node ../scripts/dev/capture-spec-examples.ts` against `make dev-native-up`
(refuses any non-localhost URL; scrubs e-mail addresses, names, usernames and free text and checks
the result by value against the database). A spec change that adds a JSON 2xx response adds its
example in the same PR.

#### Production setup in tests (17.0b3)

Integration tests run what production runs; the base class and the shared test config set it up,
a test does not opt in.

- **Security chain.** Every IT runs the service's production `SecurityFilterChain` and
  `JwtDecoder` (EMS: one chain in every profile; PCS and CUMS: the production chain in every profile
  but `local`). There is no permit-all test chain. An anonymous call answers **401** as in
  production, a wrong role **403**. Authenticate with `@WithMockUser`, `.with(user(...))` or
  `.with(jwt()...)`; to exercise the real role mapping, pass the service's
  `JwtAuthenticationConverter` (`jwt().jwt(t -> t.claim("custom:role", "ATTENDEE"))
  .authorities(...)`), as `ProductionChainIntegrationTest` (one per service) does. The test
  properties point the JWK set at `https://jwks.example.invalid/keys`: no test uses a real token.
- **Mail.** The real `EmailService` runs in every IT. The only `SesClient` in a test context is the
  `SesRecorder` (shared-kernel test fixtures, `ch.batbern.shared.test.mail`), registered by each
  service's `TestAwsConfig`: it sends nothing and records what SES received, parsed (To, Cc,
  Reply-To, From, subject, HTML, text, attachment names, MIME headers, tags). Autowire it,
  `reset()` it in `@BeforeEach`, assert on `mails()` / `mailsTo(address)` / `single()`; simulate a
  refusal with `failWith(...)` or `failFor(address, ...)` and `SesRecorder.sesError(...)`.
  `EmailService` sends on the `@Async` executor: `reset()` first waits until that executor has no
  task left (`AsyncTaskTracker`, `ch.batbern.shared.test.async`, registered by `TestAwsConfig` in
  EMS and PCS), so a late send of the previous test cannot reach the next one. A test that asserts
  a mail sent on the async path, or its absence, calls `sesRecorder.awaitAsyncSends()` first
  instead of polling or sleeping. Never
  `@MockitoBean`, `@MockitoSpyBean` or `mock(EmailService.class)`, never `verify(emailService)`.
  Because SES is "live" to the `EmailService`, every wall runs: address validation (one address
  per To:/Cc entry, checked before the SES branch since #1176), reserved domains and the
  test-marker wall. A recipient that must receive mail uses `success+<name>@simulator.amazonses.com`
  (AWS mailbox simulator); `@example.com` stays for data that must never receive mail.
- **Cognito (CUMS).** The real `CognitoIntegrationServiceImpl` runs in every CUMS IT on
  `InMemoryCognito` (CUMS test `config`), the context's only `CognitoIdentityProviderClient`,
  registered by `TestAwsConfig`. It behaves like the BATbern pool for what CUMS calls (e-mail as
  sign-in alias, the sub as username, the password policy, `UsernameExistsException`,
  `UserNotFoundException`, `AliasExistsException`). Autowire it, `reset()` it in `@BeforeEach`,
  seed logins with `givenLogin(email, status)`, make a call fail with `failNext(operation,
  exception)` and assert the login (`login(email)`: enabled, attributes, password state), never
  `verify` a call on `CognitoIntegrationService`.
- **Flush before rollback.** `AbstractIntegrationTest` registers `FlushBeforeRollback`: after the
  test body, before `@AfterEach` and the rollback, the test transaction's persistence context is
  flushed, so a `@Transactional` IT that leaves data production would refuse (NOT NULL, unique,
  bean validation, a transient reference) fails with the database error (#1104). It is skipped when
  the test already failed and when the transaction is aborted (SQLState `25P02`, a test that
  provoked and asserted a violation itself). A test that catches a Hibernate exception and goes on
  calls `entityManager.clear()`: a session that threw must not be flushed again.

#### Production-shaped fixtures (17.0d)

Test data that is ASCII, lower case, unique and small hid 12 of the 57 bugs: the umlaut search and
the 32 "Matthias" of #1034, "Hông Ân" → 500 in #1104, mixed-case e-mails in #1107, sort ties in
#1131. One shared set per side carries those shapes, and it is the default. **An ASCII-only, unique,
small builder is the exception**; a test that needs one says why.

- **Shapes:** `Hông Ân` (NFC), `O’Neill` (typographic apostrophe), `王`, an umlaut surname
  (`Brünnimann`), a mixed-case e-mail, an IDN e-mail domain (ASCII local part: SES has no SMTPUTF8),
  two people with the identical full name (usernames `matthias.keller`, `matthias.keller.2`), more
  than 20 "Matthias" (more than one default page), ties in the sort key (last name), past and future
  events, every registration status. The frontend set also has a tie in `createdAt` (the backend
  set has no `createdAt`). Invented names, `example.com` / `*.example` only. A guard test per side
  fails by name when one of its shapes disappears.
- **Frontend:** `web-frontend/src/test/fixtures/` (`people`, `shapes`, `namesakes`, `nonAsciiNames`,
  `emails`, `aUser()`, `toSearchResult()`, `events()`, `pastEvent()`, `futureEvent()`,
  `registrations()`), typed from `src/types/generated/`, guard `fixtures.test.ts`. **Harness default:**
  without an `api.on` override, `listUsers`, `searchUsers`, `listEvents` and `listRegistrations`
  answer from this set with real paging from `page`/`limit` (`src/test/msw/fixtureDefaults.ts`);
  the search approximates `batbern_fold` (accents, ß/ø/æ/œ/đ/ł/þ, ü = ue = u) and requires every
  word; `listRegistrations` pages 25 by default and applies `status` like EMS. It is a stand-in, not the backend's ranking: a test that depends on ranking, a filter the
  stand-in ignores or a specific body sets `api.on`. Every other operation answers with its spec example.
- **Backend:** shared-kernel test fixtures `ch.batbern.shared.test.fixtures.ProductionShapedPeople`
  (named shapes `VIETNAMESE`, `APOSTROPHE`, `CJK`, `MIXED_CASE_EMAIL`, `IDN_EMAIL`, `BRUENNIMANN`,
  `IDENTICAL_NAMES`, `NAMESAKES`, `ALL`), exposed by `AbstractIntegrationTest` as `PEOPLE`. Each
  person's `username` is what `SlugGenerationService` generates in list order (guard
  `ProductionShapedPeopleTest`). The set is **exposed, not seeded**: an IT persists the people it
  needs, so existing row counts do not change. EMS adds `ch.batbern.events.fixtures.ProductionShapedEvents`
  (past and future event, one registration per status); `ProductionShapedEventsIntegrationTest`
  checks the statuses against `registrations_status_check`, read from the database.
- **Dates are relative to the clock**, never a calendar date (`daysFromNow(40)`,
  `futureEvent(clock)`). A fixed date that is "in the future" today becomes a past one later and
  turns a test red for no code change (#1069, #1241).

#### Assertion rules

- **Asserting absence needs a positive control** in the same test: first show that the thing
  would be there (#1012, #960).
- **Assert the effect, not the call.** Check the parsed MIME `To:` or the stored row, not
  `verify(times(2))` (#1176, #1181).
- **A fallback must be distinguishable from success** in the test data (#1243).
- **No silent catch in `@Async`, schedulers or listeners** without a metric. The test asserts that
  the failure surfaces (#1176, #1181, #1145).
- **Wiring test at the parent** for every callback prop that triggers a mutation (#962).
- **Cache tests read, mutate, then read again** (#1069, #1095).
- **No test copies of production resources** (templates, configs) on the test classpath. Tests
  run against the real ones (#1096).

#### Touch rule

A story that changes a screen or a service method moves that screen's or method's tests off
own-code mocks, onto the harness, once the harness exists (17.0b for backend S2S clients, 17.0c
for the frontend). There are no separate rewrite stories, except one for the top offenders.

#### The ratchet

`web-frontend/scripts/test-ratchet.ts` counts, per test file, what these rules forbid, and CI
(`test-ratchet`, required through `build-frontend`) compares the counts with the committed
baseline `web-frontend/scripts/test-ratchet-baseline.txt`:

| Category | Counts |
|---|---|
| `fe-mock-services` / `fe-mock-hooks` / `fe-mock-components` | `vi.mock(` / `vi.doMock(` of own code in `web-frontend/src/**/*.{test,spec}.ts(x)`, and `vi.spyOn(x, …)` where `x` is imported from own code, by resolved path: `src/services`; `src/hooks` or a `use[A-Z]...` module; `src/components`, `src/pages` or a PascalCase module |
| `fe-mock-i18n` | `vi.mock('react-i18next')` / `vi.doMock('react-i18next')`: a per-file i18n stub instead of the real i18next with `en` (17.0c1; the stub dropped interpolation in #959 and hides missing keys) |
| `be-mock-s2s-client` | `@MockitoBean` (and `@MockitoSpyBean`: a spy stubs and verifies own code like a mock) of a type ending in `Client` that is not a boundary type (`UserApiClient`, `UserServiceClient`, ...), and `mock(XClient.class)` / `Mockito.mock(XClient.class)` / a `@Mock XClient` field of such a type anywhere in test sources (a shared `@Primary` mock such as the EMS `TestUserApiClientConfig` that 17.0b1 retired) |
| `be-mock-in-process` | any other `@MockitoBean` / `@MockitoSpyBean` that is not a boundary type, and `mock(X.class)` of such a type inside a test `@Configuration` / `@TestConfiguration` class (a shared mock bean, such as the `EmailService` mocks the services' `TestAwsConfig` held until 17.0b3) |
| `be-transactional-it` | class-level `@Transactional` on a test class that extends `AbstractIntegrationTest` or is named `*IntegrationTest` / `*IT` |

Boundary types, never counted: `SesClient`, `SesV2Client`, `S3Client`, `S3Presigner`,
`CognitoIdentityProviderClient`, `SqsClient`, `Clock`, `JwtDecoder`, and any
type imported from `software.amazon.awssdk` (for `mock(...)` calls also from `java.*`, e.g.
`java.net.http.HttpClient`). Mentions in comments and Javadoc are not counted;
in Java, mentions in string and text-block literals are not counted either (the frontend scan
skips comments only).

- **RAISED** (a count went up, or a new file has a counted mock): the build fails. Follow the rules
  above instead.
- **STALE** (a count went down, or the file is gone): the build fails until the gain is locked in.
  Run `cd web-frontend && npm run check:test-ratchet -- --update` and commit the baseline in the
  same PR.
- Never use `--update` to accept a rise. A baseline diff with a higher number needs an explicit
  reason in the PR and the owner's go. Exception: when the RAISED lines are exactly a renamed or
  moved file carrying the same counts as its STALE lines (a `git mv`), `--update` is fine and the
  PR says so.

Locally: `make check-test-ratchet`.

#### Frontend: tests are type-checked

`npm run type-check` (CI `build-frontend`) compiles all of `web-frontend/src`, tests included, with
`tsconfig.test-harness.json` (since 2026-11-09; before, only the tests listed there). Its `exclude`
list holds the test files that did not compile at the switch (115). The list only shrinks:
`scripts/check-test-typecheck.ts`, the last step of `type-check`, compiles every file of `src` with
nothing excluded and fails STALE for an excluded file that compiles now (remove the line) and NOT
TEST for an entry that is not a test file. A test that is not excluded and does not compile fails
`tsc` itself, so a new test can only be excluded by editing the list, which is review matter: fix
the test instead, with fixtures in the wire shape (`src/test/fixtures`, the generated types). A test
whose basename equals a `.ts` twin (`x.test.ts` and `x.test.tsx`) is invisible to `tsc`: name the
second one `x.hooks.test.tsx` (or similar).

#### Frontend: i18n keys

The tests run on the real `en` bundles, and the app falls back to `de` (`fallbackLng`), so a
`t('key')` whose key is missing in `en` shows an English visitor German text or the inline default,
and no test notices. `web-frontend/scripts/check-i18n-keys.ts` (CI step "Frontend i18n key check" in
`build-frontend`, locally `make check-i18n-keys`) reads every `t()` call in `web-frontend/src`
(tests and `src/test/` excluded) and looks its key up in `public/locales/en/<namespace>.json`:

- the namespace comes from the binding (`useTranslation('ns')`, an array: each in order, none:
  `common`; `{ t: tX }` renames, `{ keyPrefix }`), from a `ns:key` prefix or a `{ ns: 'x' }`
  option; `i18n.t` / `i18next.t` use `common`; a `t` that is a parameter of the enclosing
  function (`(t: TFunction) => ...`, `function f(t, n)`, `({ t }) => ...`), or has no binding in
  the file, counts as found when any `en` namespace has the key;
- the key must hold a string; an object counts only for a call with `returnObjects: true`
  (i18next otherwise renders "returned an object instead of string");
- plural forms count only for a call that passes `count` (`t('items', { count })` is found as
  `items_one` / `items_other`), context forms only for one that passes `context` (a literal
  context names the one form `greeting_male`);
- a key built at run time (variable, template with `${}`, concatenation) is skipped.

The keys missing when the gate came in (166 calls in 51 files) are frozen per file in
`web-frontend/scripts/i18n-keys-baseline.txt`. A file that gains a missing key fails (RAISED, with
file, line, key and namespace); add the key to `en` and the other 9 locales. A file that loses one
fails until the gain is locked in (STALE): `cd web-frontend && npm run check:i18n-keys -- --update`,
commit the baseline in the same PR. Filling the frozen keys is touch-rule work: a story that
changes a screen fills that screen's keys.

#### Frontend: generated API type copies

The wire types come from `web-frontend/src/types/generated/` (generated from `docs/api/`, ADR-006).
A hand-written copy drifts silently: when the spec changes (e.g. a field becomes optional or
`| null`), the copy still compiles and the UI reads a field the server no longer sends. Alias the
generated type and keep the export name, so imports stay unchanged:

```ts
import type { components } from '@/types/generated/event-speakers-api.types';
export type SendReminderRequest = components['schemas']['SendReminderRequest'];
```

When the alias widens a field (optional, `| null`), fix each caller properly: a real default, a
guard, or a visible empty state, never `!` or `as`. A frontend model that is not the wire shape
(mapped, enriched or a file format) gets its own name (`UserPreferencesView`, `NotificationPage`,
`LegacySpeakerRecord`), not the schema's.

`web-frontend/scripts/check-api-type-copies.ts` (CI step "Generated type copy check" in
`frontend-static`, part of `build-frontend`; locally `make check-api-type-copies`) reads the schema
names of every `src/types/generated/*.types.ts` (the members of `components.schemas`) and fails an
`export interface X` or object-literal `export type X = { ... }` (also generic) in `src/` whose
name is one of them. An alias, union, intersection or `Omit<...>` passes (anything after `=` that
does not start with `{`), and so do other names, non-exported declarations and commented code;
tests, mocks, fixtures and the generated dir are not scanned. The gate is name-based on purpose: a
copy under another name is a review matter.

The copies left are frozen in `web-frontend/scripts/api-type-copies-baseline.txt` (file + type
name, never a line number), and each line has exactly one owner group in
`scripts/api-type-copies-owners.txt` (the story or issue that removes it, or
`exception: <reason>`). A new copy fails NEW; a removed one fails STALE until the gain is locked
in: `cd web-frontend && npm run check:api-type-copies -- --update`, commit the baseline in the same
PR. `--update` only removes lines: it refuses (exit 1, baseline untouched) to write a copy the
committed baseline does not list. `--update --allow-growth` adds one, for an owner-accepted
exception only, with its owner group. UNOWNED, AMBIGUOUS, STALE OWNER and BAD OWNER work as in the
API conventions lint.

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

### API conventions lint

`npm run lint:api` (in `web-frontend/`, `make lint-api`, CI `frontend-static` in the required
`build-frontend` check) runs Spectral with the repo ruleset `docs/api/.spectral.yaml` over the
public specs `docs/api/*.openapi.yml` (not `_shared.openapi.yml`). Six rules, one finding per
operation (Story 17.4):

| Rule | Fails on | Source |
|---|---|---|
| `path-kebab-case` | a literal path segment that is not kebab-case (`speakerSlots`, `batch_registrations`, `companies:get-or-create`); an `.ext` suffix is allowed, path parameters are exempt | ADR-013, naming table above |
| `no-verb-segment` | a verb outside the ADR-013 §1 action shape `POST /{id}/{verb}` (the verb is the last segment of a POST, directly after a parameter or `me`: `POST /companies/{name}/verify`, `POST /users/me/merge` pass). Flagged: a verb after a literal (`POST /users/me/profile-claims/confirm`), with another method (`PUT /tasks/{taskId}/complete`), on a collection (`POST /users/provision`), followed by a parameter or qualifier (`POST …/publish/{phase}`, `POST /registrations/deregister/by-email`); for a read only the last segment counts (`GET …/download` fails, `GET …/publish/status` passes). The verb list in the ruleset only tells a verb from a sub-resource noun; it allows nothing. `search`, `get-or-create`, `vote` are not on it | ADR-013 |
| `no-internal-path` | a path under `/internal` in a public spec | ADR-016 |
| `no-literal-under-events` | a literal directly under `/events/` (`/events/current`): it shares the namespace of `{eventCode}` | ADR-013 |
| `uuid-path-param` | a path parameter with `format: uuid`, or a string named `id` / `*Id` without a format (`uploadId`); an operation parameter replaces the path-level one of the same name. Not seen: a UUID under another name without a format | ADR-003 amendment 2026-11 |
| `no-personal-data-path-param` | a path parameter named `email` or ending in `Email`, case-insensitive (`/users/me/additional-emails/{email}`); path-level and operation-level parameters as for `uuid-path-param`. Personal data never goes in a path | ADR-003 amendment 2026-11 |

**Baseline policy.** Existing violations are listed in `web-frontend/scripts/api-lint-baseline.txt`
(rule, spec file, method, path; no line numbers). The lint fails on a NEW finding and on a STALE one
(fixed in the spec, still in the baseline). After a fix, run `npm run lint:api -- --update` and
commit the smaller baseline in the same PR. Never use `--update` to accept a new violation without
the reason in the PR description. For `uuid-path-param` the reason is the ADR-003 case (no natural
name, never crosses a service, nested under its meaningful parent and loaded scoped to it); a linter
cannot decide that, so every UUID path parameter is listed and reviewed by a person. For
`no-verb-segment` it is why no ADR-013 shape fits.

**Owner file.** Every baseline line matches exactly one group in
`web-frontend/scripts/api-lint-owners.txt` (hand-maintained, reviewed like code): `<group id> TAB
<owner> TAB <rule regex> TAB <operation regex>`, the owner being the story that removes the lines
(`17.9b`), an issue (`#1070`), or `exception: <reason>` accepted by the owner. No exception without a
written reason, and no allow-list of action names: the right verb for the right action. A group
owned by a story or issue covers a family by regex (all `/logos/` lines), so the file is not a
second copy of the baseline; an exception group lists its exact operations (`^(METHOD /path|…)$`),
so no new line falls into an exception without its own entry.
`npm run lint:api` fails on `UNOWNED` (a line no group matches), `AMBIGUOUS` (two or more groups),
`STALE OWNER` (a group that matches nothing any more: remove it with the fix) and `BAD OWNER` (an
unparseable line), also right after `--update`, so a PR that accepts a new line adds its owner
group in the same PR.

**Similarity report.** On a PR that changes `docs/api/`, the CI job `api-similarity` posts one
comment: for every added operation, the 3 most similar existing ones (path and summary). It never
fails a build. Check it for near-duplicates before adding an operation. Locally:
`npm run report:api-similarity -- --base origin/develop`.

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