# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BATbern is an enterprise event management platform for Berner Architekten Treffen conferences in Bern, Switzerland. It's a polyglot monorepo combining Java/Spring Boot microservices with a React/TypeScript frontend, deployed to AWS using CDK.

**Architecture Pattern**: Domain-Driven Design with microservices.
- **Shared Kernel**: Common types, domain events, utilities shared across all services
- **API Gateway**: Unified entry point handling authentication, rate limiting, and routing
- **Domain Services**: event-management, speaker-coordination, partner-coordination, attendee-experience, company-user-management
- **Infrastructure**: AWS CDK (Infrastructure as Code)
- **Frontend**: React 19 + TypeScript SPA with role-based adaptive UI

## Project Status

**Last Updated:** 2026-05-25 — **MVP complete & production ready.**

| Epic | Status |
|------|--------|
| 1 — Foundation & Core Infrastructure | ✅ Complete |
| 2 — Entity CRUD & Domain Services | ✅ Complete |
| 3 — Historical Data Migration | ✅ Tooling complete (production import pending user trigger) |
| 4 — Public Website & Content Discovery | ✅ Complete |
| 5 — Enhanced Organizer Workflows | ✅ Complete (auto-publishing & lifecycle automation via BAT-16) |
| 6 — Speaker Self-Service Portal | ✅ Complete (stories 6.0-6.5, 6.4 WCAG 2.1 AA) |
| 7 — Attendee Experience Enhancements | 📦 Deferred to Phase 3 (dashboard, bookmarks, PWA) |
| 8 — Partner Coordination | ✅ Complete (attendance analytics + XLSX, topic voting, ICS meeting invites) |
| 11 — Unified Speaker Workflow Refactor | ✅ Complete (Phase F magic-link teardown, Story 11.F.1, 2026-05-25; supersedes prior Epic 9 plan) |

**Scope note:** Overflow Management (Story 5.6) removed from MVP — manual speaker selection is sufficient for launch; democratic overflow voting moved to Phase 2+ backlog.

## Build System

Unified polyglot monorepo orchestrated by a root Makefile (Java/Gradle + Node.js/npm).

```bash
# Setup & build
make install                    # Install all dependencies (Java + Node)
make build                      # Build everything
make test                       # Run all tests with coverage (needs Docker for integration tests)
make verify                     # Pre-commit checks (lint + test)

# Single stack
make build-java / make test-java        # Java only (Testcontainers PostgreSQL)
make build-node / make test-node        # Node.js only

# Code quality
make lint / make format / make format-check / make audit-security

# Dependencies
make check-outdated / make update-deps  # update-deps = safe patch/minor only
```

### Running Individual Services

**IMPORTANT**: All Gradle commands run from the **repository root** — never `cd` into a service. Use subproject path notation.

```bash
./gradlew :shared-kernel:build
./gradlew :api-gateway:bootRun
./gradlew :services:event-management-service:bootRun        # also: company-user-management, speaker-coordination, partner-coordination, attendee-experience
./gradlew :services:event-management-service:test           # single service tests
./gradlew :api-gateway:test --tests CompanyControllerIntegrationTest                                          # single class
./gradlew :api-gateway:test --tests CompanyControllerIntegrationTest.should_createCompany_when_validDataProvided  # single method
./gradlew :services:event-management-service:flywayMigrate  # or flywayRepair

cd web-frontend && npm run dev          # frontend
cd infrastructure && npm run deploy:dev # infra
```

Shared kernel must be published before dependent services build: `./gradlew :shared-kernel:publishToMavenLocal`.

## Project Structure

```
BATbern/
├── shared-kernel/                    # Common types, events, utilities (foundation)
├── api-gateway/                      # Unified API gateway with auth
├── services/                         # Domain microservices
│   ├── company-user-management-service/
│   ├── event-management-service/
│   ├── speaker-coordination-service/
│   ├── partner-coordination-service/
│   └── attendee-experience-service/
├── web-frontend/                     # React 19 + TypeScript SPA
├── infrastructure/                   # AWS CDK
├── bruno-tests/                      # API contract tests (E2E Layer 2)
├── scripts/{ci,auth,deploy,dev}/     # CI, auth, deploy, dev helpers
└── docs/{architecture,api,stories,guides,plans}/
```

**Key patterns:** Shared-kernel types published to Maven Local first; each service independently deployable; all external requests go through the API Gateway; frontend generates TypeScript types from OpenAPI specs; integration tests use real PostgreSQL via Testcontainers (never H2).

## Local Development

**Prerequisites:** Java 21, Node.js 20+, Docker Desktop, AWS CLI v2 (for staging Cognito), jq.

```bash
docker compose -f docker-compose-dev.yml up -d   # Local PostgreSQL 15 (persistent volume)
make dev-native-up                               # Start services natively (60-70% less resources than Docker)

# First time only: sync users from staging Cognito
./scripts/auth/get-token.sh staging your-email@example.com your-password
./scripts/dev/sync-users-from-cognito.sh

# Parallel dev environments
make dev-native-up-instance BASE_PORT=9000
make dev-native-list / make dev-native-status-all
```

**Ports (BASE_PORT=8000):** API Gateway 8000, Company/User 8001, Event Mgmt 8002, Speaker 8003, Partner 8004, Attendee 8005, Frontend 8100.

**Architecture:** PostgreSQL in Docker; all services native (Java + Vite); auth uses **staging Cognito**; zero AWS dev-env cost (~$600-720/yr saved). See `docs/guides/local-development-setup.md`.

**Local DB is a read-only mirror of staging — except Pattern N writes during speaker invitation:** `adminCreateUserSilently` creates the Cognito user in staging while `user_profiles` / `role_assignments` rows land in the **local DB only**. The PreTokenGen Lambda runs against the staging DB, so it emits a JWT with **no `custom:role`** for these users. **Pattern 3b** (DB-fallback in `shared-kernel/.../security/JwtRolesConverter` + `AuthContext.hydrateRolesIfMissing`) resolves roles from the local DB so local-dev speakers can log in. Dormant in staging (the JWT always carries roles there). Do NOT remove either fallback when refactoring auth — it is the only thing that makes the local kanban → speaker-portal flow testable end-to-end. Full pattern: `docs/architecture/06b-user-lifecycle-sync.md` §"Pattern 3b".

### Debugging & Logs (native dev)

Service logs at `/tmp/batbern-1-{service}.log` (instance number is the `1`; a parallel instance=2 uses `batbern-2-*`).

```bash
tail -f /tmp/batbern-1-event-management.log
grep -i "error\|exception" /tmp/batbern-1-*.log               # errors across all services
grep -i "anonymousUser\|SecurityException" /tmp/batbern-1-company-user-management.log  # 401/500 on public endpoints
grep -i "UserApiClient\|CompanyApiClient" /tmp/batbern-1-event-management.log          # cross-service calls
grep -i "HikariPool\|connection" /tmp/batbern-1-*.log         # DB connection issues

make dev-native-status                                        # running services
make dev-native-restart-service SERVICE=company-user-management
make dev-native-down                                          # stop all
```

## Testing

### 4-Layer E2E Framework

1. **Shell scripts** (`scripts/ci/*.sh`) — smoke, CORS, header propagation
2. **Bruno** (`bruno-tests/**/*.bru`) — API contract tests (companies, users, events, partners, partner-meetings, tasks, file-upload)
3. **Playwright** (`web-frontend/e2e/*.spec.ts`) — UI E2E; role projects: `chromium` (organizer), `speaker`, `partner`
4. **Infrastructure** (`infrastructure/test/e2e/*.test.ts`) — CDK / AWS resource validation

```bash
./scripts/ci/run-bruno-tests.sh           # Bruno (loads all role tokens automatically)
cd web-frontend && npm run test:e2e       # Playwright organizer project
make test                                 # all layers
```

### TDD — Mandatory (Red-Green-Refactor)

1. **RED** — write failing tests first. 2. **GREEN** — minimal code to pass. 3. **REFACTOR** — improve while green.

- **Integration tests MUST use PostgreSQL via Testcontainers, never H2/in-memory.** All extend `AbstractIntegrationTest` (singleton container, `.withReuse(true)`). Annotate with `@Transactional` so each test rolls back. `application-test.properties` keeps `spring.flyway.enabled=true`.
- **Test naming:** `should_expectedBehavior_when_condition` (Java + frontend).
- **Frontend:** RTL `screen` queries (no `container.querySelector`), `userEvent` over `fireEvent`, `waitFor()` for async/CSS-transition assertions, `msw` 2.x to mock HTTP.
- Every acceptance criterion needs ≥1 test; complex criteria need several.

### Coverage Requirements

Unit (business logic) ≥ 90% · Integration (APIs) ≥ 80% · Overall ≥ 85% line coverage. Reports: `build/reports/jacoco/test/html/index.html` (Java), `infrastructure/coverage/index.html` (CDK), `web-frontend/coverage/index.html` (frontend).

### Authentication for Testing

```bash
# Single role (organizer — legacy/backward-compatible)
./scripts/auth/get-token.sh staging your-email@example.com your-password   # → ~/.batbern/staging.json + staging-organizer.json

# Multi-role (Epic 8+) — organizer + speaker + partner in one command
cp .env.test.local.example .env.test.local        # fill in per-role credentials
make setup-test-users                              # staging (default); ENV=development for local
# → ~/.batbern/staging-{organizer,speaker,partner}.json

# Refresh without re-entering credentials
./scripts/auth/refresh-token.sh staging            # organizer (both files)
./scripts/auth/refresh-token.sh staging partner    # partner only
```

- **Bruno** exports `AUTH_TOKEN` (organizer), `ORGANIZER_AUTH_TOKEN`, `SPEAKER_AUTH_TOKEN`, `PARTNER_AUTH_TOKEN` — use `{{partnerAuthToken}}` in partner-scoped `.bru` files.
- **Playwright** `global-setup.ts` writes `.playwright-auth-{role}.json` per available role. `speaker`/`partner` projects activate when the matching `*_AUTH_TOKEN` env var is set. Partner tests in `e2e/partner/`, speaker tests in `e2e/speaker/`.
- **CI/CD** `deploy-staging.yml` reads role tokens from GitHub secrets (`STAGING_{ORGANIZER,SPEAKER,PARTNER}_*`), falling back to `STAGING_TEST_USER_*` for organizer.

### Bruno `.bru` Syntax — Comments Go in `docs { }`, NOT `#`

**CRITICAL — common Claude mistake.** Bruno's `.bru` grammar does NOT support free-floating `#` comments at the top level (between blocks). A `#` line outside a recognised block prints `Warning: Skipping invalid file ...` and **silently skips the entire file** — the request never runs, but `bru run` still exits 0 if every other file passed. The failure is invisible in CI: a "PASS" summary can hide cleanup hooks that never executed.

Put all prose inside a `docs { }` block at the end of the file. The only blocks accepted at file scope: `meta`, `docs`, `settings`, `headers`, `body:*`, `auth:*`, `params:*`, `query`, `tests`, `assert`, `script:pre-request`, `script:post-response`, `vars:*`, `metadata`, and HTTP verb blocks (`get`, `post`, …).

**Detection:** grep CI output for `Skipping invalid file` after any Bruno run — a clean run has zero.

## Critical Development Standards

- **Type sharing:** define types in `shared-kernel` and import (`ch.batbern.shared.types.CompanyId`); never redefine per service. Frontend uses generated types from `src/types/generated/` (committed).
- **API calls:** never `fetch`/`axios` directly in components — use the service layer (`import { companyService } from '@/services/companyService'`).
- **Env vars:** never read `process.env` directly — use the `config` object (`import { config } from '@/config'`).
- **File uploads:** always request a presigned S3 URL then `PUT` directly to S3. Never proxy file bytes through the backend.
- **Integration tests:** extend `AbstractIntegrationTest` (real PostgreSQL). Never `@DataJpaTest` without PostgreSQL — H2 masks JSONB/function/constraint issues.

### Lambda Handler Tests

**CRITICAL**: every Lambda MUST have a handler-level unit test that **imports and runs the handler module**. CDK `Template.fromStack()` tests only verify the CloudFormation declaration — they do NOT test that the handler can load. A missing native dependency (`sharp`, `pg-native`) causes `Runtime.ImportModuleError` at cold-start → 503 for **all** requests; a handler test catches this before deploy.

```typescript
test('module loads without crashing', async () => {
  const { handler } = await import('../../../lib/lambda/image-resize/index');
  expect(typeof handler).toBe('function');
});
```

**Bundling rule for native deps:** the `tryBundle` local bundler MUST return `false` outside Jest (forcing Docker) so native packages install for Linux x64. A local esbuild-only bundle silently omits native binaries. Reference: `infrastructure/lib/stacks/storage-stack.ts`.

### Database Migrations (Flyway) — NEVER modify an already-applied migration

**CRITICAL.** Once a migration (`V{n}__*.sql`) is applied to any shared environment (staging/production — **the staging account 188701360969 IS production**), its content is **frozen forever**. Flyway stores each applied migration's checksum; changing even one character changes the checksum → on next boot validation fails with `Migration checksum mismatch` → the JPA `entityManagerFactory` bean fails → **the service crashes on startup**. ECS then circuit-breaker rolls back to the last matching image, so the service silently stays on stale code (a green deploy that never advances).

**Rules:**
- Never edit, rename, renumber, or delete a shipped migration. To change its effect, add a new higher-numbered forward migration.
- **Bulk find-and-replace across the repo is the classic footgun** — exclude `**/db/migration/**` from any repo-wide substitution, then review any migration a bulk edit touched. (#669's `cdn.staging.batbern.ch → cdn.batbern.ch` sweep edited applied `V86` on 2026-05-26, silently freezing event-management on a stale image for ~16 builds; root-caused 2026-05-27. See `docs/plans/bruno-staging-hardening.md`.)
- If a mismatch already shipped: revert the file byte-exact to its applied content (checksum matches again) **plus** a new forward migration for the intended change. `flywayRepair` only re-aligns checksums (does NOT re-run the migration).
- Diagnose a stuck service by comparing its deployed ECS image tag to develop HEAD and grepping CloudWatch for `checksum mismatch` / `FlywayValidateException`.
- Migration filenames must be strictly sequential. In PL/pgSQL `DO` blocks, qualify column names with the table alias to avoid ambiguity.

```sql
-- ❌ WRONG — editing applied V86 (changes checksum, breaks startup)
-- ✅ RIGHT — leave V86 untouched; fix data in a NEW higher-numbered migration:
UPDATE event_photos SET display_url =
  REPLACE(display_url, 'https://cdn.staging.batbern.ch', 'https://cdn.batbern.ch')
WHERE display_url LIKE '%cdn.staging.batbern.ch%';
```

## OpenAPI Type Generation

OpenAPI specs (`docs/api/*.openapi.yml`) are the single source of truth (contract-first). After any spec change, regenerate and commit:

```bash
cd web-frontend && npm run generate:api-types   # all specs; or e.g. npm run generate:api-types:users
```

Backend controllers implement the generated `*Api` interface (mapping annotations come from it; generated DTOs in `build/generated/` are NOT committed). Frontend types in `src/types/generated/` ARE committed.

## Git Workflow & Deployment

**Conventional commits:** `type(scope): description` — types `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`.

**Branches:** `main` (production), `develop` (staging integration / auto-deploy), `feature/{description}`, `hotfix/{description}`.

> **Consolidated environment:** single AWS account **188701360969** (profile `batbern-staging`) serves production. CloudFormation stacks keep `BATbern-staging-*` names; the `isProduction: true` CDK flag controls production behavior. Production URLs: www.batbern.ch, api.batbern.ch, cdn.batbern.ch. The former production account (422940799530) is decommissioned. Management account `batbern-mgmt` (510187933511) handles domain registration + consolidated billing (CFO `cfo-dani-kuehni` has IAM billing access; see `docs/guides/aws-setup-guide.md` Phase 6).

```bash
git push origin develop                  # auto-deploy to production (staging account)
cd infrastructure && npm run deploy:staging   # CDK / infra changes only
```

**Tagged release (manual, 3 steps):** 1) merge develop → main via PR (builds + pushes ECR images tagged with the merge-commit SHA); 2) note the 7-char merge SHA; 3) Actions → Deploy to Production → version: `<sha7>`. A GitHub Release `prod/<sha7>` is created on success; deploys appear on `/deployments` and `/releases`.

> **CI gotcha:** push to `develop` does NOT reliably trigger the build after squash merges, and commits added after a build starts may be silently excluded. If staging is missing changes: `gh workflow run build.yml --ref develop`. Deploy-staging must be called via `workflow_call` from the Build Pipeline — standalone runs fail on image-tag mismatch.

### Beta Frontend Canary — de-risk frontend-only changes before prod

`beta.batbern.ch` (`BATbern-staging-FrontendBeta`: own S3 bucket `batbern-frontend-beta-staging` + CloudFront distribution) serves the same build-once SPA artifact against the **same production API, Cognito, and database** as www. Use it to preview **frontend-only** changes on real infra (real CloudFront, real data, real `cdn.batbern.ch` resize Lambda) before they reach every visitor — bundle/loading refactors, perf, layout/CSS, client routing.

```bash
scripts/deploy/publish-beta-frontend.sh              # build → S3 sync → CloudFront invalidation
SKIP_BUILD=1 scripts/deploy/publish-beta-frontend.sh # publish existing web-frontend/dist as-is
# Infra changes (rare, gated): cd infrastructure && AWS_PROFILE=batbern-staging npx cdk deploy BATbern-staging-FrontendBeta \
#   --context environment=staging --context betaFrontend=true --require-approval never
```

**⚠️ Beta is a UI CANARY, not a sandbox.** It shares the **production** backend/Cognito/DB — every action hits **live production data** with real accounts. Use ONLY for frontend-only changes; never for backend changes, migrations, or destructive flows. Public + `noindex` — do not advertise the URL. The publish script forces the `batbern-staging` profile (a dev shell exporting `AWS_PROFILE=batbern-dev` would point at the wrong account). CORS allow-listing of `https://beta.batbern.ch` is already in place. Full design: `docs/plans/beta-frontend-canary.md`.

## AWS Monitoring & Logs

```bash
export AWS_PROFILE=batbern-staging   # or prefix each command
```

Log groups follow `/aws/ecs/BATbern-staging/{service}` — `api-gateway`, `event-management`, `speaker-coordination`, `partner-coordination`, `attendee-experience`, `company-user-management` (paths keep the `staging` prefix even though this is production).

```bash
# Tail / filter logs
aws logs tail /aws/ecs/BATbern-staging/event-management --since 30m --follow
aws logs tail /aws/ecs/BATbern-staging/event-management --since 1h --filter-pattern "ERROR"

# CloudWatch Insights (results ready after ~3-5s via get-query-results --query-id)
aws logs start-query --log-group-name "/aws/ecs/BATbern-staging/event-management" \
  --start-time $(date -v-1H +%s) --end-time $(date +%s) \
  --query-string 'fields @timestamp, @message | filter @message like /ERROR/ | sort @timestamp desc | limit 50'

# ECS status / tasks
aws ecs describe-services --cluster batbern-staging --services <service-name> \
  --query 'services[0].{Status:status,Running:runningCount,Desired:desiredCount,Events:events[:5]}'
aws ecs list-tasks --cluster batbern-staging --service-name <service-name>
```

**Fargate Spot:** services run 70% FARGATE_SPOT + 30% FARGATE. Spot interruptions cause 4-5 min silent restarts (no logs, task termination) — ECS auto-replaces with on-demand FARGATE. Check deployment history for task-replacement events.
**Log stream naming:** `{service}/Container/{task-id}` — find streams by task ID via `describe-log-streams`.

## Dependency Management

Dependabot runs **monthly** (first Monday): grouped PRs created 3:00-4:30 AM, auto-batch-merge at 10:00 AM. Config `.github/dependabot.yml` (3-5 PRs/ecosystem, related deps grouped). The `dependabot-batch-merge.yml` workflow rebases + merges non-conflicting PRs after full CI, closes conflicting ones (recreated next month). ~10-15 PRs/month, zero manual intervention expected.

```bash
gh workflow run dependabot-batch-merge.yml -f dry_run=true   # manual dry-run (or dry_run=false to run live)
gh pr list --label dependencies                              # status
```

## Troubleshooting

```bash
# Docker
make docker-restart / make docker-down ; docker-compose down -v
make docker-tunnel-logs / make docker-tunnel-stop           # DB tunnel

# Gradle
make clean && make build
cd shared-kernel && ./gradlew clean build publishToMavenLocal   # rebuild shared kernel

# Frontend
cd web-frontend && rm -rf node_modules && npm ci ; npm run type-check
```

## Doc Drift Prevention

When a `feat`/`fix` changes business logic, scheduler behaviour, state-machine transitions, or API contracts:
1. Consult `.github/doc-drift-mappings.yml` for docs associated with the changed paths.
2. Update those docs in the **same commit**.
3. If no doc update is needed (pure internal refactor), add `[no-doc]` to the commit message.

This keeps the weekly doc-drift-auditor green.

## Localization — Email Templates: DE + EN Only; UI i18n: All 10 Locales

**Narrowed 2026-05-17 (Story 11.E.3 PM Q#5).** The "DE + EN only" rule applies **only to backend email templates** (`services/*/src/main/resources/email-templates/*.{html,txt}`), NOT frontend UI i18n keys.

- **Backend email templates → `de` + `en` only**, first-class wording. The 8 other locales (`fr`, `it`, `rm`, `es`, `fi`, `nl`, `ja`, `gsw-BE`) are a deliberate non-goal; the email-rendering service falls back to English when no template exists.
- **Frontend UI i18n → all 10 locales required.** New `web-frontend/public/locales/{locale}/*.json` keys must land in all 10 (`de`, `en`, `fr`, `it`, `rm`, `es`, `fi`, `nl`, `ja`, `gsw-BE`) before a story moves to `review`. EN + DE first-class; the other 8 may use straight translations, hand-polished later. Falling back to English here is a visible UX degradation (the public site has a genuinely multilingual audience).
- **Why the asymmetry:** email copy is rich, prose-style (multi-paragraph, pricing/legal nuance) for an audience that is overwhelmingly Swiss-German/German + English; frontend keys are short atomic strings that translate cheaply for a multilingual public.
- **Test resilience:** assert against EN values OR the namespace-stripped key — never lock in a non-EN translation.
- This **supersedes** both the older "10-locale parity for everything" pattern (Stories 10.7/10.9) in the email space and the intermediate pre-Q#5 "DE + EN only for everything" framing.

## Personal Data & Security

**CRITICAL: never commit files containing real personal data (PII).**

- **CSV with PII:** never commit real names/emails/etc. Use synthetic/faker data. CSVs in `apps/BATspa-old/` are `.gitignore`d.
- **Test data:** generate with faker; anonymize any production data; never download production DB dumps locally.
- **GDPR:** treat all participant data (names, emails, company associations) as PII; follow retention policies.
- **Sensitive patterns to never commit:** `.env` with credentials, `*.csv` in legacy app dirs, DB dumps (`.sql`/`.dump`), API keys/tokens.
- **Incident response (PII committed):** `git filter-repo` to purge from history → force-push after backup branches → contact GitHub Support (90-day cache) → add patterns to `.gitignore`. Ref: [GitHub — Removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).

## Quality Standards

**Never take shortcuts** to make tests pass — do the work at highest quality. Follow TDD; write tests before implementation; all tests green before committing; maintain coverage; update OpenAPI specs and regenerate types after API changes.

- **Pre-commit hook** runs ESLint fix + Prettier + related Vitest (frontend) and Checkstyle + Spotless (Java). Fix ALL violations — the hook blocks the commit. Use `--no-verify` ONLY when the user explicitly instructs, never as a workaround.
- Checkstyle rules agents miss: `NeedBraces` (all `if`/`else`/`for`/`while` need `{}`), `OperatorWrap` (`+` at **start** of next line), `MemberName` (no underscores — `sessionAbstract`, not `abstract_`), `UnusedImports`.
- **Build/test output:** pipe `make`/`gradlew`/`git push`/`git commit` through `tee /tmp/<name>.log`, then grep the log file. Never re-run a full suite just to find errors — grep the saved output instead.

## Important Context Files

- **Architecture/standards:** `docs/architecture/{coding-standards,tech-stack,source-tree}.md`; `docs/api/*.openapi.yml`
- **Agent rules (read before coding):** `_bmad-output/project-context.md` — 65 condensed rules incl. ADR-003 identifiers, ADR-004 entity design, enum value flow, auth/role fallbacks.
- **Implementation guides** (consult when building services/features): `docs/guides/{service-foundation-pattern,openapi-code-generation,microservices-http-clients,flyway-migration-guide}.md`
