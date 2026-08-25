---
project_name: BATbern
user_name: Nissim
date: 2026-06-06
sections_completed: [technology_stack, language_rules, framework_rules, testing_rules, code_quality, workflow_rules, critical_rules]
status: complete
rule_count: 84
optimized_for_llm: true
---

# Project Context for AI Agents

_Critical rules and patterns AI agents must follow when implementing code in BATbern.
Focus on unobvious details that agents otherwise miss._

---

## Technology Stack & Versions

### Frontend
- React 19.2 + TypeScript 6.x
- MUI 7.x (Material-UI) — primary component library
- Radix UI + shadcn/ui — supplemental headless components
- TanStack Query (React Query) 5.90 — server state
- Zustand 5.x — client state
- React Router 7.x
- react-hook-form 7.x + zod 4.x — forms & validation
- i18next 25.x + react-i18next 16.x — i18n (10 locales: de, en, fr, it, rm, es, fi, nl, ja, gsw-BE)
- Tailwind CSS 4.x + Vite 8.x
- aws-amplify 6.x — Cognito auth

### Backend
- Java 21 LTS + Spring Boot 4.x + Spring Security 7.x
- PostgreSQL 15+ (primary DB — never H2, ever)
- Flyway — database migrations
- Caffeine 3.x — application-level in-memory cache (no Redis)
- Gradle 8.x — builds always run from repo root
- OpenAPI Generator 7.14 — Spring Boot 4 interface generation

### Testing
- Vitest 4.x + React Testing Library 16.x
- Playwright 1.x (E2E): 3 projects — `chromium` (organizer), `speaker`, `partner`
- JUnit 6.x + Testcontainers 2.x (PostgreSQL — mandatory for integration tests)

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

### Architecture: Unified Speaker Workflow (ADR-009 — Epic 11)
- `SpeakerWorkflowState` has exactly **8 states**: `IDENTIFIED → CONTACTED → READY → INVITED
  → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED` (+ `DECLINED`, reachable from any state).
- **Sole status writer**: `SpeakerWorkflowService.transition(...)`. NEVER set
  `speaker_pool.status` directly (no `setStatus(...)` outside the service, no raw UPDATE).
- `READY` is the **provisioning gate**: User lookup-or-create + Cognito provisioning + SPEAKER
  role + `PRIMARY_SPEAKER` `session_users` row happen at the transition INTO ready, only via
  `POST /api/v1/events/{code}/speakers/{speakerId}/promote`.
- `speaker_pool.username`/`email` columns are GONE (V103). Canonical speaker identity =
  `PrimarySpeakerResolver.resolve(pool)` (primary `session_users` row + CUMS-backed email).
- **NO content on `speaker_pool`, EVER (ADR-012).** `speaker_pool` is the workflow state machine
  (+ a `source` provenance flag) only. Content — title/abstract/materials — lives in
  `session_users` + `content_submissions`/`session_content_history`. A pre-READY self-nomination
  pitch (which has no session yet) lives in its own `session_proposals` table (FK → `speaker_pool`),
  NOT in columns on `speaker_pool`; on `CONTACTED → READY` the promote hook seeds the canonical
  session from it and the proposal row becomes immutable audit. A schema-fitness test asserts
  `speaker_pool` has no title/abstract/materials columns — do not re-add them.
- Magic-link auth was torn down (Story 11.F.1) — speakers authenticate via Cognito only.

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

### Bundle Boundary: NO MUI on Public Pages (perf-critical)
- Public routes (homepage, `/archive`, `/about`, `/privacy`, `/unsubscribe`, `/verify-email`, …)
  are **Tailwind-only** — they must NEVER import MUI. MUI (~158 KB vendor chunk) lives behind
  the lazy `<MuiLayout>` ThemeProvider boundary in `App.tsx` (auth/organizer/speaker/partner
  routes). Public routes are declared as SIBLINGS after that boundary (React Router ranks by
  path specificity, not source order).
- Adding a new public page: build it with Tailwind + plain elements, register it as a sibling
  of the boundary. Adding one MUI import to an eagerly-loaded public component drags the whole
  MUI vendor chunk into the homepage bundle.

### i18n / Localization (narrowed rule, 2026-05-17)
- **Frontend UI keys: ALL 10 locales required** (`de, en, fr, it, rm, es, fi, nl, ja, gsw-BE`)
  in `web-frontend/public/locales/{locale}/*.json`. EN + DE first-class copy; other 8 may be
  straight translations.
- **Backend email templates: DE + EN ONLY** (`services/*/src/main/resources/email-templates/
  {key}-{de|en}.html`). Other locales fall back to EN at render time. Do NOT create 10-locale
  email templates. Locale matching: treat any `de*` language pref as German.

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

### Bruno `.bru` Files
- **NO free-floating `#` comments** between blocks — the parser silently SKIPS the whole file
  (`Warning: Skipping invalid file`) while `bru run` still exits 0. Prose goes in a `docs { }`
  block at the end of the file. After any Bruno run, grep output for `Skipping invalid file`.
- Staging IS production: no Bruno/E2E test may trigger real outbound communications
  (invites, cancellations, reminders) or leave test data behind — always add cleanup steps.

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
- ESLint 10.x + Prettier 3.x — run by `lint-staged`, invoked from `web-frontend/` by
  `.githooks/pre-commit`. It **auto-fixes and re-stages** (`eslint --fix`,
  `prettier --write`); it does not merely reject.
- Max ESLint warnings in CI: 50 (`--max-warnings 50`) — the hook is stricter (`0`)
- `infrastructure/**/*.ts` is NOT covered by any pre-commit lint — lint-staged is scoped
  to `web-frontend/`. `infrastructure/` also has its own `.prettierrc.json` as of
  2026-08-18; before that `prettier --check` there graded against defaults.
- Checkstyle enforces Java style in the hook. **Spotless does NOT run in the hook** —
  only `checkstyleMain` + `checkstyleTest` do.

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
- **Branches**: `develop` is the PRODUCTION branch — it deploys to www.batbern.ch. `main` is
  **vestigial**: nothing has merged to it since 2026-06-07 and it deploys nowhere. It will
  always appear "ahead" of `develop` because develop→main PR merges create merge commits that
  never flow back — that count means nothing, do NOT treat it as missing code.
- Merge to `develop` triggers a production deploy — but CI may silently exclude commits
  added AFTER the build starts. If production is missing changes, manually trigger:
  `gh workflow run build.yml --ref develop`

### CI/CD — A PULL REQUEST DEPLOYS TO PRODUCTION (most dangerous unobvious rule)
- `deploy-staging.yml` is `workflow_call` only; the `deploy-to-staging` job in `build.yml`
  invokes it when:
  ```yaml
  (github.event_name == 'push'         && github.ref == 'refs/heads/develop') ||
  (github.event_name == 'pull_request' && github.event.pull_request.base.ref == 'develop' &&
   github.actor != 'dependabot[bot]')
  ```
- **Opening or updating a PR against `develop` ships that branch to www.batbern.ch** —
  unmerged, unreviewed, drafts included. There is NO `environment:` protection gate.
  `concurrency: deploy-staging` only serialises deploys; it does not gate them.
- Treat "open a PR" as a production action. Merging N queued PRs = N sequential prod deploys.
- Dependabot PRs are excluded by the `github.actor` clause — they build/test but never deploy.
- `develop` branch protection: 8 required checks (`build-frontend`, `build-shared-kernel`,
  `build-services (×6)`), `strict: true` (branch must be current before merge), 0 required
  reviews. A required check that never RUNS blocks a PR exactly like a failing one.

### Maintenance-mode operational facts (2026-08-09)
- **Nightly E2E is the health signal.** `Nightly E2E (full @gate suite)` runs daily against
  production. Check it before assuming the system is fine. `@gate` = proven suite;
  `@quarantine` = excluded from the gate and auto-promoted back when it goes green
  (`scripts/ci/run-playwright-tests.sh` composes `--grep @gate --grep-invert @quarantine`).
- **Dependabot automation is broken and reports success.** Its workflow runs sit at
  `action_required` (repo policy `fork-pr-contributor-approval: first_time_contributors`), so
  the required checks never run and every PR is `MERGEABLE` + `BLOCKED` forever. The batch-merge
  job counts "auto-merge enabled" as "Merged: N" and exits 0 — a green workflow that merges
  nothing. Do not trust that summary.
- Deploy safety nets that DO exist: pre-deploy RDS snapshot when migrations are detected, the
  post-deploy E2E suite, and automatic rollback on failure.

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

### Git Hooks (`.githooks/`)
- **Installed by `make install`** as of #973 (also by a root `npm install`, via the
  `prepare` script; `make install-hooks` does hooks only). Verify with
  `git config core.hooksPath` — should print `.githooks`. Before #973 nothing invoked the
  installer, so a fresh clone was ungated while the docs claimed enforcement;
  `scripts/ci/verify-githooks.sh` guards the wiring in CI now.
- `pre-commit` runs, on staged files only: `lint-staged` when ANY `web-frontend/` file is
  staged (gated on the directory, not the extension — a locale `.json` or `.css`-only
  commit used to be skipped entirely), `npm run check:api-types` (only when `docs/api/`,
  `web-frontend/package*.json` or `src/types/generated/` is staged), and
  `./gradlew checkstyleMain checkstyleTest` (Java). No Vitest, no Spotless.
- `commit-msg` enforces conventional commits via commitlint. `type-empty` and
  `subject-empty` are at error severity — a message with no type is rejected.
- `pre-push` runs test suites for the components that changed. `infrastructure/*`,
  `docs/*` and `*.md` are skipped entirely. A `shared-kernel/*` change fans out to ALL
  six Java components with full Testcontainers integration suites — the slow path.
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
- **Empty `custom:role` → fall back to the DB** (Pattern 3b, Epic 11.E.7): every service's
  `JwtAuthenticationConverter` uses `shared-kernel/.../security/JwtRolesConverter`, which
  queries `user_profiles` ⨝ `role_assignments` by `cognito_user_id = jwt.sub` whenever the
  claim is empty. The frontend mirrors this in `AuthContext.hydrateRolesIfMissing` via
  `GET /users/me?include=roles`. Dormant in staging (the JWT always carries roles there) —
  this exists for local-dev where CUMS-provisioned speakers have their Cognito user in
  staging but their `user_profiles` row only in the local DB, so PreTokenGen finds nothing.
  Do NOT remove either fallback when refactoring auth — it is the only thing that makes
  the local kanban → speaker-portal flow testable end-to-end.
- Never call `refreshJWT()` on 401 inside a sync/service loop — triggers infinite auth retry
  (401 → refresh → onChange → sync → 401 → …). JWT refresh is handled only by AuthManager timer.

### Federated Identity / Google SSO (ADR-010 — Epic 12)
- **Cognito Lambda triggers (pre-signup, pre-token-generation, post-authentication) must NEVER
  throw** — a throw 503s EVERY sign-in. Wrap everything in try/catch, emit a CloudWatch metric,
  return the event. Test this fail-open behavior explicitly.
- PreSignUp (`PreSignUp_ExternalProvider`) links a federated identity to an existing account by
  **case-insensitive email match** via `AdminLinkProviderForUser` (destination = native user,
  `sub` preserved). Fallback (2026-06-06 amendment): verified additional emails
  (`user_additional_emails.verified_at IS NOT NULL` AND IdP attribute `email_verified='true'`
  — Cognito attributes are STRINGS). Never link on unverified emails.
- JIT provisioning (`JITUserProvisioningInterceptor`, CUMS) creates the `user_profiles` row on
  first authenticated API call (default role ATTENDEE); it must NEVER overwrite an existing
  `cognito_user_id`, and it skips creation when the JWT email is someone's verified additional
  email (duplicate guard).
- Runtime kill-switch: `FEATURES_SSO_ENABLED` — SSO code paths must tolerate being disabled.
- Lambda triggers need handler-level unit tests that import and RUN the handler — CDK
  `Template.fromStack()` assertions do NOT count (they miss `Runtime.ImportModuleError`).
- Lambda bundling: native deps (sharp, pg-native) require Docker bundling — the local
  `tryBundle` must return `false` outside Jest so Linux x64 binaries are installed.

### Additional Emails (Story 10.32 + verification flow 2026-06-06)
- `user_additional_emails`: max 5/user, **globally unique case-insensitively across primary +
  additional emails** — DB triggers reject collisions in both directions. Never assume an email
  can belong to two users.
- `verified_at` is set ONLY by the verification flow (signed stateless HMAC JWT link, 48h TTL,
  claims bound to the row UUID so delete/re-add invalidates). SSO linking and the JIT guard gate
  on `verified_at IS NOT NULL`. Do not gate email fan-out/CC on it (deliberate).
- Token-credentialed public endpoints (verification, unsubscribe, registration confirm): the
  token IS the credential → `permitAll` required in **BOTH** the api-gateway SecurityConfig AND
  the owning service's SecurityConfig — and the service config has MULTIPLE profile chains
  (local + prod + test): add to ALL of them.
- Email-link confirm endpoints are **POST-only** — mail scanners prefetch GET links, so a GET
  must never mutate state (GET = read-only check, POST = confirm).

### Backend Gotchas
- `GlobalExceptionHandler`: ALWAYS add explicit `@ExceptionHandler(MethodArgumentNotValidException.class)`.
  Without it, `@ExceptionHandler(Exception.class)` returns 500 instead of 400 for validation failures.
- `@ElementCollection(fetch = FetchType.EAGER)` causes N+1 on every list query — always LAZY.
- Never use `findAll()` then filter/paginate in memory — always paginate at the DB level.
- Flyway migration filenames must be strictly sequential: `V{n}__{description}.sql`.
  Out-of-order versions cause `flywayMigrate` to fail — run `flywayRepair` first.
- **NEVER edit an already-applied migration** (staging = production). A changed checksum makes
  Flyway validation fail on boot → service crash-loops → ECS rolls back to a stale image and
  deploys silently stop advancing. Fix data/schema with a NEW higher-numbered migration. Always
  exclude `**/db/migration/**` from repo-wide find-and-replace sweeps (the #669 incident).
- Cross-service HTTP clients must propagate the JWT from `SecurityContext` — do not make
  unauthenticated service-to-service calls.

### Frontend Gotchas
- `onAppear` / tab-triggered `useEffect`: set the debounce timestamp BEFORE launching the
  async Task/Promise — otherwise multiple tab swipes all see the old timestamp and all fire.
- MUI `<Collapse unmountOnExit>` removes DOM nodes asynchronously — always `waitFor()` on
  `.not.toBeInTheDocument()` assertions.
- i18n: ALL user-visible strings go through `useTranslation()`. Add keys to ALL 10 locale
  files (see i18n/Localization rule). Missing keys silently fall back to the key string.
- Never import directly from `src/types/generated/` in test files when testing with MSW —
  mock the service layer instead.

### Build & CI Gotchas
- **Opening a PR against `develop` deploys to production** — see the CI/CD section above.
  This is the single most surprising rule in the repo; it is not in most people's mental model.
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

_Last Updated: 2026-08-14 (corrected stale version claims to match the actual estate:
Spring Boot 4.x, Spring Security 7.x, TypeScript 6.x, Vite 8.x, JUnit 6.x, Testcontainers 2.x,
ESLint 10.x, OpenAPI Generator 7.14. `docs/versions.json` is the generated source of truth —
it had been correct; the hand-written docs had drifted away from it.)_

_Previously: 2026-08-09 (added: CI/CD section — a PR against `develop` deploys to production;
branch-protection required checks; `main` is vestigial; maintenance-mode operational facts —
nightly E2E as the health signal, Dependabot automation broken while reporting success)_

_Previously: 2026-06-06 (folded in: ADR-009, ADR-010 + verified-additional-email amendment,
Epics 11/12 outcomes, narrowed email-localization rule, additional-emails + verification flow,
no-MUI-on-public-pages bundle boundary, Bruno docs{} rule, Flyway never-edit-applied rule,
Lambda handler-test + Docker-bundling rules)_
