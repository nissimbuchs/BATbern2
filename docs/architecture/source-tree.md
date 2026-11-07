# Source Tree Structure

Generated from `git ls-tree` on `develop` (c4593449, 2026-11-04). Only directories that exist are
listed; descriptions are short on purpose. Regenerate rather than hand-edit when the layout
changes.

## Unified Project Structure

One monorepo. The root `settings.gradle` includes `shared-kernel`, `api-gateway` and the five
`services:*` modules; `web-frontend` and `infrastructure` are separate npm projects. Run Gradle
from the repository root.

```
BATbern2/
├── shared-kernel/            # Shared Java library: types, domain events, security, mail, utils
├── api-gateway/              # Spring Boot (Spring MVC) gateway: JWT auth, rate limiting, DomainRouter
├── services/
│   ├── event-management-service/          # EMS, primary (ADR-014)
│   ├── company-user-management-service/   # CUMS, primary
│   └── partner-coordination-service/      # PCS, separate service
├── web-frontend/             # React 19 + TypeScript SPA (public site and role areas)
├── infrastructure/           # AWS CDK app (TypeScript)
├── apps/
│   ├── BATbern-watch/        # watchOS companion app (Swift/SwiftUI, Xcode project)
│   ├── projectdoc/           # documentation-site generator (Node), not deployed with the platform
│   └── workhours/            # local working-hours analysis script (Python)
├── bruno-tests/              # Bruno API contract tests, one folder per collection
├── scripts/                  # Dev, CI, auth, deploy and data scripts (see below)
├── docs/                     # Architecture, API specs, PRDs, plans, guides (see below)
├── config/                   # checkstyle rules, backend env template
├── gradle/                   # Gradle wrapper, trivy-security.gradle
├── .githooks/                # Active git hooks: pre-commit, commit-msg, pre-push (make install-hooks)
├── .github/                  # Workflows, dependabot.yml, doc-drift-mappings.yml, helper scripts
├── _bmad/                    # BMAD method configuration and agents
├── _bmad-output/             # BMAD artifacts: story files (implementation-artifacts/), project-context.md
├── .zap/                     # OWASP ZAP rules
├── .claude/                  # Claude Code settings, hooks, skills
├── Makefile                  # Entry point for install, build, test, dev-native-* targets
├── docker-compose-dev.yml    # Local PostgreSQL for native development
├── docker-compose.yml        # All services in containers (alternative)
└── Dockerfile.flyway
```

## Service Structure

All three active services follow the same layering; packages differ by service.

```
services/{name}-service/
├── src/main/java/ch/batbern/{events|companyuser|partners}/
│   ├── controller/      # REST controllers, implementing generated OpenAPI interfaces (ADR-006)
│   ├── service/         # Business logic
│   ├── repository/      # Spring Data JPA repositories
│   ├── domain/          # JPA entities
│   ├── dto/             # Hand-written DTOs (generated ones live under build/generated)
│   ├── client/          # HTTP clients to other services (/internal/** endpoints)
│   ├── config/          # Spring configuration, SecurityConfig
│   ├── security/        # Service-side security helpers
│   └── exception/       # Exceptions and GlobalExceptionHandler
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/    # Flyway migrations, owned per service. Never edit an applied one
│   └── email-templates/ # (EMS, CUMS) DE + EN mail templates
├── src/test/java/       # Unit and integration tests (Testcontainers PostgreSQL)
├── build.gradle         # openApiGenerate tasks, dependencies
└── Dockerfile
```

Service-specific packages worth knowing:

- **EMS** (`ch.batbern.events`): `mailcenter/` (Epic 16), `forwarding/` (alias forwarding),
  `listener/` (SQS listeners), `scheduler/` and `scheduled/`, `notification/`, `usermerge/`,
  `watch/`, `specification/`.
- **CUMS** (`ch.batbern.companyuser`): `watch/` (pairing), `interceptor/`, `events/` (domain
  events).
- **PCS** (`ch.batbern.partners`): `mail/`, `events/`.

## Shared Kernel Structure

```
shared-kernel/src/
├── main/java/ch/batbern/shared/
│   ├── api/  config/  dto/  types/          # shared API, DTO and value types
│   ├── events/                              # domain event base classes, DomainEventPublisher
│   ├── exception/  exceptions/              # shared exception types
│   ├── mail/                                # mail helpers
│   ├── security/                            # JwtRolesConverter, audit logging
│   ├── service/                             # e.g. SlugGenerationService
│   └── util/  utils/
├── test/
└── testFixtures/                            # e.g. EndpointAuthorizationGuard used by every service
```

## API Gateway Structure

```
api-gateway/src/main/java/ch/batbern/gateway/
├── routing/        # DomainRouter: path prefix → service
├── security/       # RateLimitingFilter, AccountActiveFilter, TurnstileVerificationFilter, security headers
├── filter/         # CorrelationIdFilter, ApiVersionHeaderFilter
├── config/         # SecurityConfig (JWT resource server, /internal/** denyAll)
├── auth/  client/  exception/  gdpr/  health/  middleware/  observability/  util/  validation/
└── ApiGatewayApplication.java
```

## Frontend Structure

```
web-frontend/
├── src/
│   ├── pages/          # Route-level pages: public/, organizer/, speaker-portal/, attendee/, profile/, presentation/, LiveControlPage/, UserAccountPage/ ...
│   ├── components/     # By area: public/, organizer/, speaker-portal/, partner/, attendee/, user/, auth/, shared/ ...
│   ├── features/       # Feature modules (profile/)
│   ├── services/       # API service layer (no direct fetch from components)
│   ├── hooks/  stores/ contexts/   # React Query hooks, Zustand stores, contexts
│   ├── types/generated/            # TypeScript types generated from docs/api/*.openapi.yml
│   ├── config/         # Runtime configuration (no direct process.env access)
│   ├── i18n/           # i18next setup
│   ├── pwa/  mocks/  schemas/  theme/  styles/  utils/  lib/  data/  test/
│   └── App.tsx, main.tsx
├── public/
│   ├── locales/        # 10 locales: de, en, fr, it, rm, es, fi, nl, ja, gsw-BE
│   └── icons/  assets/  manifest.webmanifest  offline.html  robots.txt
├── e2e/                # Playwright: organizer/, speaker/, partner/, auth/, workflows/, regression/, helpers/, fixtures/, *.spec.ts
├── scripts/            # prerender.mjs, preview-prerender.mjs, check-api-types.sh, i18n/
├── vite.config.ts  playwright.config.ts  tailwind.config.js
└── package.json
```

## Infrastructure Structure

```
infrastructure/
├── bin/batbern-infrastructure.ts   # CDK app: instantiates all stacks
├── lib/
│   ├── stacks/        # One file per stack: dns, network, secrets, database, bastion, storage,
│   │                  # event-bus, monitoring, cicd, cognito, ses, inbound-email, cluster,
│   │                  # event-management, partner-coordination,
│   │                  # company-management, api-gateway-service,
│   │                  # api-gateway, frontend (also used for FrontendBeta), auto-shutdown
│   ├── constructs/    # domain-service-construct (Fargate + Service Connect), alarms, rds-cluster, vpc ...
│   ├── lambda/        # image-resize (CDN), triggers/ (Cognito Lambda triggers)
│   ├── config/        # dev-, staging- (= production), prod-config.ts, environment-config.ts
│   └── utils/
├── lambda/            # github-issues-integration (alarm → issue), ses-event-logger
├── test/              # unit/, integration/, e2e/ (Jest)
└── package.json       # deploy:staging:* layer scripts
```

## End-to-End and Contract Tests

```
bruno-tests/                # Layer 2: API contract tests (bruno.json, environments/, one folder per API)
web-frontend/e2e/           # Layer 3: Playwright, role projects chromium (organizer), speaker, partner
infrastructure/test/e2e/    # Layer 4: CDK / AWS resource checks
scripts/ci/                 # Layer 1: smoke, CORS, header propagation, access-control checks
```

## Scripts Structure

```
scripts/
├── dev/       # start-all-native.sh, stop/restart/status, sync-users-from-cognito.sh, anonymized snapshot, seeds
├── ci/        # run-bruno-tests.sh, smoke/CORS/header tests, access-control-check.py, promote-ecr-tag.sh, update-ecs-task.sh ...
├── auth/      # get-token.sh, refresh-token.sh, setup-test-users.sh
├── deploy/    # publish-beta-frontend.sh
├── staging/   # data uploads and bootstrap-user sync against the production account
├── config/    # sync-backend-config.sh, validate-backend-config.sh
├── db/  e2e/  data/  test/
└── update-versions.js   # generates docs/versions.json
```

## Documentation Structure

```
docs/
├── architecture/   # This folder: numbered architecture docs, ADR-*.md, tech-stack, coding standards
├── api/            # OpenAPI specs (*.openapi.yml): the API contract
├── prd/            # Epic PRDs (epic-*.md)
├── plans/          # Implementation and operations plans
├── guides/         # Developer guides (local setup, OpenAPI generation, Flyway, HTTP clients ...)
├── deployment/     # CI/CD pipeline guide and setup
├── operations/     # runbooks.md, post-mortem template
├── security/       # e.g. owasp-llm-agentic-checklist.md
├── watch-app/      # Watch app PRD, architecture, UX
├── stories/archived/  # Older story files, by epic (newer stories are in _bmad-output/implementation-artifacts/)
├── testing/  qa/  specs/  ux/  wireframes/  user-guide/  migration/  presentation/  trainings/  templates/  assessment/
└── versions.json   # Generated technology versions
```

## Key Directory Conventions

### Naming Conventions
- **Directories**: `kebab-case` for multi-word directories (`event-management-service`)
- **Java packages**: lowercase, rooted at `ch.batbern.{service}`
- **TypeScript files**: `camelCase.ts`, `PascalCase.tsx` for components
- **Flyway migrations**: `V{n}__description.sql`, numbered per service

### File Organization Principles
1. **Service ownership**: each service owns its tables and migrations
2. **Contract first**: API shapes live in `docs/api/*.openapi.yml`; Java interfaces and TypeScript
   types are generated from them
3. **Shared code** goes in `shared-kernel`, never duplicated per service
4. **Tests next to code**: `src/test` per module, Playwright in `web-frontend/e2e`

### Integration Points
- **API gateway**: single entry for the frontend and the watch app; routes by path prefix
  (`DomainRouter`)
- **Service to service**: HTTP to VPC-only `/internal/**` endpoints via ECS Service Connect
  (`http://{service}:8080`); the user's JWT is forwarded when there is one, never required
- **Shared kernel**: common types, events and security components
- **Frontend**: one React application consuming all APIs through its service layer
