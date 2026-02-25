---
project_name: BATbern
user_name: Nissim
date: 2026-02-24
sections_completed: [technology_stack, language_rules, framework_rules, testing_rules, code_quality, workflow_rules, critical_rules]
status: complete
rule_count: 65
optimized_for_llm: true
---

# Project Context for AI Agents

_Critical rules and patterns AI agents must follow when implementing code in BATbern.
Focus on unobvious details that agents otherwise miss._

---

## Technology Stack & Versions

### Frontend
- React 19.2.4 + TypeScript 5.3
- MUI 7.x (Material-UI) — primary component library
- Radix UI + shadcn/ui — supplemental headless components
- TanStack Query (React Query) 5.90 — server state
- Zustand 5.x — client state
- React Router 7.x
- react-hook-form 7.x + zod 4.x — forms & validation
- i18next 25.x + react-i18next 16.x — i18n (en + de)
- Tailwind CSS 4.x + Vite 7.x
- aws-amplify 6.x — Cognito auth

### Backend
- Java 21 LTS + Spring Boot 3.x + Spring Security 6.x
- PostgreSQL 15+ (primary DB — never H2, ever)
- Flyway — database migrations
- Caffeine 3.x — application-level in-memory cache (no Redis)
- Gradle 8.x — builds always run from repo root
- OpenAPI Generator 7.2 — Spring Boot 3 interface generation

### Testing
- Vitest 4.x + React Testing Library 16.x
- Playwright 1.x (E2E): 3 projects — `chromium` (organizer), `speaker`, `partner`
- JUnit 5.x + Testcontainers 1.x (PostgreSQL — mandatory for integration tests)

### Infrastructure
- AWS ECS Fargate + Cognito + S3 + CDK 2.x
- GitHub Actions (CI/CD)

## Language-Specific Rules

### TypeScript (Frontend)
- **NEVER** access `process.env` directly — always use `config` objects
- **NEVER** make direct `fetch` or `axios` calls in components — always use the service layer
  (`import { companyService } from '@/services/companyService'`)
- **NEVER** duplicate type definitions — use generated types from `src/types/generated/`
  (`import type { components } from '@/types/generated/events-api.types'`)
- After any OpenAPI spec change, run `npm run generate:api-types` and commit the result
- Frontend generated types live in `src/types/generated/` and ARE committed to Git
- Path alias `@/` maps to `src/` — always use this for imports, never relative `../../`

### Enum Value Flow (Critical — agents always get this wrong)
- **Java code**: `UPPER_CASE` (e.g., `EventWorkflowState.SPEAKER_BRAINSTORMING`)
- **JSON / API requests & responses**: `UPPER_CASE` (e.g., `"SPEAKER_BRAINSTORMING"`)
- **Database storage**: `lowercase_snake_case` (e.g., `'speaker_brainstorming'`)
- Conversion: JPA `AttributeConverter` handles Java ↔ DB. Jackson default handles Java ↔ JSON.
- Do NOT add `@JsonValue`/`@JsonProperty` to enums — the default serialisation is correct.

### Java (Backend)
- Java 21 — use text blocks, records, pattern matching, and sealed classes where appropriate
- All `@ElementCollection` must use `FetchType.LAZY` — EAGER is the #1 N+1 source
- Use `@Transactional(readOnly = true)` on read-only service methods
- `@Retryable` + `@Recover` for transient external calls; do NOT retry inside a `@Transactional` method
- Checkstyle rules that agents regularly miss:
  - `NeedBraces`: ALL `if`/`else`/`for`/`while` must use `{}`
  - `OperatorWrap`: multi-line string concat — `+` goes at the **start** of the next line, not end of current
  - `MemberName`: no underscores — use `sessionAbstract` (not `abstract_` even for Java keyword conflicts)
  - `UnusedImports`: clean up every time; Checkstyle runs in pre-commit hook

## Framework-Specific Rules

### Architecture: Cross-Service Identifiers (ADR-003 — most-missed rule)
- **Public APIs & URLs**: ALWAYS use meaningful IDs — `eventCode` (e.g. `BATbern56`),
  `username` (e.g. `john.doe`), `companyName` (e.g. `GoogleZH`). NEVER expose UUIDs.
- **Database — same service**: UUID primary keys and UUID foreign keys are fine within one service's schema.
- **Database — cross service**: NEVER store a UUID foreign key to another service's entity.
  Store the meaningful ID instead (e.g. `company_name VARCHAR(12)`, `username VARCHAR(100)`).
  NO database foreign key constraints across service boundaries.
- **Decision tree**:
  - References an entity in THIS service → UUID FK ✅
  - References an entity in ANOTHER service → meaningful string ID ✅, UUID FK ❌

### Architecture: Domain Entity Design (ADR-004)
- Domain entities (Speaker, Attendee, Partner) **NEVER** duplicate user profile fields
  (email, firstName, lastName, bio, profilePictureUrl).
- Store only `username` (meaningful ID) as the cross-service reference to User.
- Enrich response DTOs with User data via HTTP call to company-user-management-service
  (15-min Caffeine cache). Do NOT use JPQL joins across service databases.
- Adding `role = SPEAKER` to a User does NOT auto-create a Speaker entity — that is a
  separate explicit step in the Speaker Coordination Service.

### Architecture: OpenAPI Contract-First (ADR-006)
- OpenAPI specs (`docs/api/*.openapi.yml`) are the single source of truth.
- **Backend**: controllers implement the generated `*Api` interface (never add mapping
  annotations directly — they come from the generated interface).
- **Backend**: DTOs in `build/generated/` are NOT committed — auto-generated on build.
- **Frontend**: types in `src/types/generated/` ARE committed.
- Use builder pattern for generated DTOs (`TopicListResponse.builder()...build()`).
- Shared-kernel types (`ErrorResponse`, `PaginationMetadata`) are imported via
  `importMappings` in Gradle — never re-generated.

### Backend Layered Architecture
```
Controller  →  implements generated *Api interface, delegates to Service
Mapper      →  pure entity↔DTO conversion only, NO business logic, NO repositories
Service     →  business logic, returns generated DTOs, uses Mapper + Repository
Repository  →  JPA only, returns entities
Entity      →  JPA annotations, UUID PK + meaningful ID alternate key (ADR-003)
```
- `GlobalExceptionHandler` MUST have an explicit `@ExceptionHandler(MethodArgumentNotValidException.class)` —
  the catch-all `@ExceptionHandler(Exception.class)` silently shadows Spring's default 400 handler.

### React Patterns
- Roles determine which component tree renders — check role before rendering, not inside.
- `onAppear` / `useEffect` triggered by tab navigation fires on EVERY tab switch.
  Always add a minimum-interval guard (≥ 60 s) before triggering network calls.
- MUI `<Collapse unmountOnExit>`: CSS transition does not complete synchronously.
  Wrap `.not.toBeInTheDocument()` assertions in `waitFor()`.
- Use `useTranslation()` hook for ALL user-facing strings — no hardcoded English.
- File uploads: always obtain a presigned S3 URL first, then PUT directly to S3.
  Never POST file content through the backend.

## Testing Rules

### TDD — Mandatory Workflow (Red-Green-Refactor)
1. **RED**: Write failing tests first (unit + integration + E2E where applicable)
2. **GREEN**: Write minimal code to pass — nothing more
3. **REFACTOR**: Improve while keeping tests green

### Backend Integration Tests — Critical Requirements
- ALL integration tests MUST extend `AbstractIntegrationTest` — provides singleton
  PostgreSQL 16 via Testcontainers with `.withReuse(true)` for performance.
- **NEVER use H2 or `@DataJpaTest` without PostgreSQL** — hides JSONB, function,
  and constraint issues that only surface in production.
- Annotate integration test classes with `@Transactional` so each test rolls back.
- `application-test.properties`: `spring.flyway.enabled=true` — always run real migrations.

### Test Naming Convention
- Pattern: `should_expectedBehavior_when_condition`
- Java: `void should_createEvent_when_validRequestProvided() {}`
- Frontend: `test('should display error message when form validation fails', () => {})`

### Frontend Testing
- Use `screen` queries from React Testing Library — no `container.querySelector`.
- Prefer `userEvent` over `fireEvent` for realistic interaction simulation.
- Use `waitFor()` for any assertion that depends on async state or CSS transitions.
- `msw` 2.x is available for mocking HTTP in unit tests — use it instead of mocking service modules.

### E2E Testing (Playwright)
- Three projects: `chromium` (organizer — always active), `speaker` (activated by
  `SPEAKER_AUTH_TOKEN` env var), `partner` (activated by `PARTNER_AUTH_TOKEN` env var).
- Auth state stored per role: `.playwright-auth-{role}.json` (written by `global-setup.ts`).
- Partner tests live in `e2e/partner/`, speaker tests in `e2e/speaker/`.
- Run Bruno API contract tests first: `./scripts/ci/run-bruno-tests.sh`.

### Coverage Requirements
- Unit tests (business logic): ≥ 90%
- Integration tests (APIs): ≥ 80%
- Overall line coverage: ≥ 70%
- Every acceptance criterion must have at least one test; complex criteria need multiple.

### Build & Test Output
- Pipe `gradle`/`make` output through `tee /tmp/<name>.log`, then `grep` the log file.
- Never re-run the full suite to find errors — grep the saved output instead.

## Code Quality & Style Rules

### Naming Conventions
| Element | Convention | Example |
|---------|------------|---------|
| React components | PascalCase | `EventCard.tsx` |
| React hooks | camelCase + `use` prefix | `usePartnerNotes.ts` |
| Services (frontend) | camelCase + `Service` suffix | `partnerService.ts` |
| API routes | kebab-case | `/api/v1/partner-meetings` |
| API request/response fields | camelCase | `firstName`, `eventCode` |
| DB tables & columns | snake_case | `partner_contacts`, `company_name` |
| Java entities | PascalCase + `Entity` suffix | `CompanyEntity.java` |
| Java domain events | PascalCase + `Event` suffix | `UserRolePromotedEvent.java` |
| Test files (frontend) | `ComponentName.test.tsx` | `EventCard.test.tsx` |
| Integration tests (Java) | `*IntegrationTest` suffix | `EventControllerIntegrationTest` |

### Frontend Code Organisation
- Components: `src/components/{role}/` or `src/components/shared/`
- Hooks: `src/hooks/use{Name}.ts`
- Services: `src/services/{domain}Service.ts`
- Generated types: `src/types/generated/` (never edit manually)
- Stores: `src/stores/` (Zustand)

### Linting & Formatting
- ESLint 9.x + Prettier 3.x — both run in `lint-staged` on commit
- Max ESLint warnings in CI: 50 (`--max-warnings 50`)
- `npm run format` before committing if not using lint-staged
- Spotless runs on Java code; Checkstyle enforces style rules (both in pre-commit hook)

### Security Rules
- **NEVER commit** `.env` files, `*.csv` with PII, DB dumps, API keys
- Always use presigned URLs for file uploads — never proxy binary data through the backend
- Role checks must happen server-side (Spring Security) AND client-side (UI hiding)
- Never expose internal UUIDs in public APIs or error messages

### OpenAPI Specs
- All endpoints must have OpenAPI 3.1 specs with request/response examples
- Update the spec BEFORE writing the implementation (contract-first)
- Document all error codes; include 400, 401, 403, 404 at minimum

## Development Workflow Rules

### Git & Branching
- **Branch naming**: `feature/{description}`, `hotfix/{description}`, `release/{version}`
- **Main branches**: `main` (production), `develop` (staging integration)
- Merge to `develop` triggers staging auto-deploy — but CI may silently exclude commits
  added AFTER the build starts. If staging is missing changes, manually trigger:
  `gh workflow run build.yml --ref develop`

### Commit Message Format (Conventional Commits)
```
type(scope): description
```
- Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- Examples:
  - `feat(partner): add topic voting API`
  - `fix(auth): resolve token refresh loop on 401`
  - `test(event-management): add integration tests for event creation`

### Gradle — Critical Rules
- **ALL Gradle commands must be run from the repo root directory** — never `cd` into a service.
- `./gradlew :services:event-management-service:test` — always use subproject path notation.
- Shared kernel must be published before dependent services build:
  `./gradlew :shared-kernel:publishToMavenLocal`

### Local Development
- Use `make dev-native-up` (not Docker Compose) — 60-70% less resource usage.
- Service logs: `/tmp/batbern-1-{service-name}.log`
- Local DB: `docker exec batbern-dev-postgres psql -U postgres -d batbern_development`
- Auth uses staging Cognito — run `./scripts/auth/get-token.sh staging {email} {password}` once.
- Multi-role tokens: `make setup-test-users` (reads `.env.test.local`).

### API Ports (native dev, BASE_PORT=8000)
- API Gateway: `8000`, Company/User: `8001`, Event Mgmt: `8002`
- Speaker Coordination: `8003`, Partner Coordination: `8004`
- Attendee Experience: `8005`, Frontend: `8100`

### Pre-commit Hook
- Runs: ESLint fix, Prettier, Vitest related tests (frontend); Checkstyle, Spotless (Java).
- Fix ALL Checkstyle violations before committing — hook blocks the commit.
- Use `--no-verify` ONLY when explicitly instructed by the user; never as a workaround.

## Critical Don't-Miss Rules

### Identity & Cross-Service — Top Mistakes
- ❌ `private UUID companyId` in Partner entity → ✅ `private String companyName`
- ❌ `FOREIGN KEY (user_id) REFERENCES users(id)` across service DBs → ✅ no FK constraint
- ❌ Duplicate `email`/`firstName`/`lastName` in Speaker/Attendee/Partner entities
- ❌ Calling `userService.getUser()` via JPQL join → ✅ via `UserApiClient` HTTP call

### Authentication & Roles
- Roles stored exclusively in PostgreSQL `role_assignments` — **NOT** in Cognito groups.
  Never sync roles to Cognito groups; they are added to JWT at login via PreTokenGeneration Lambda.
- Role updates take effect on the user's NEXT login (JWT is issued at login time).
- JWT claim for roles is `custom:role` (Cognito) or `role` (Watch JWT) — `extractAuthorities`
  must check both.
- Never call `refreshJWT()` on 401 inside a sync/service loop — triggers infinite auth retry
  (401 → refresh → onChange → sync → 401 → …). JWT refresh is handled only by AuthManager timer.

### Backend Gotchas
- `GlobalExceptionHandler`: ALWAYS add explicit `@ExceptionHandler(MethodArgumentNotValidException.class)`.
  Without it, `@ExceptionHandler(Exception.class)` returns 500 instead of 400 for validation failures.
- `@ElementCollection(fetch = FetchType.EAGER)` causes N+1 on every list query — always LAZY.
- Never use `findAll()` then filter/paginate in memory — always paginate at the DB level.
- Flyway migration filenames must be strictly sequential: `V{n}__{description}.sql`.
  Out-of-order versions cause `flywayMigrate` to fail — run `flywayRepair` first.
- Cross-service HTTP clients must propagate the JWT from `SecurityContext` — do not make
  unauthenticated service-to-service calls.

### Frontend Gotchas
- `onAppear` / tab-triggered `useEffect`: set the debounce timestamp BEFORE launching the
  async Task/Promise — otherwise multiple tab swipes all see the old timestamp and all fire.
- MUI `<Collapse unmountOnExit>` removes DOM nodes asynchronously — always `waitFor()` on
  `.not.toBeInTheDocument()` assertions.
- i18n: ALL user-visible strings go through `useTranslation()`. Add keys to both `en` and `de`
  translation files. Missing keys silently fall back to the key string.
- Never import directly from `src/types/generated/` in test files when testing with MSW —
  mock the service layer instead.

### Build & CI Gotchas
- Build pipeline does NOT reliably trigger on `develop` push after squash merges —
  manually trigger: `gh workflow run build.yml --ref develop`
- Deploy to Staging MUST be called via `workflow_call` from the Build Pipeline — standalone
  runs fail due to image tag mismatch.
- Parallel dev environments: `make dev-native-up-instance BASE_PORT=9000`; list with
  `make dev-native-list`.

### Data & Security
- NEVER commit CSV files with real participant names/emails.
- Event PK is UUID internally — always look up events by `event_code` (e.g. `'BATbern57'`),
  not by UUID, in SQL queries and API calls.

---

## Usage Guidelines

**For AI Agents:**
- Read this file before implementing any code in BATbern
- Follow ALL rules exactly as documented — especially ADR-003 identifier rules and ADR-004 entity design
- When in doubt, prefer the more restrictive option
- Update this file if new stable patterns emerge across multiple interactions

**For Humans:**
- Keep this file lean and focused on agent needs — no obvious rules
- Update when technology stack changes or new ADRs are accepted
- Review quarterly for outdated rules
- Remove rules that become obvious over time

_Last Updated: 2026-02-24_
