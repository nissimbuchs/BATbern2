# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## MVP Status

**Last Updated:** 2026-02-03

**Status:** ✅ **MVP 100% COMPLETE & PRODUCTION READY** | **Epic 6 DEPLOYED TO STAGING**

**🎉 MILESTONE:** All MVP epics (1-5) are 100% complete! Epic 6 (Speaker Self-Service Portal) Phase 1 & 2 deployed to staging.

**Epic Status:**
- ✅ **Epic 1**: Foundation & Core Infrastructure - 100% COMPLETE
- ✅ **Epic 2**: Entity CRUD & Domain Services - 100% COMPLETE
- ✅ **Epic 3**: Historical Data Migration - 100% COMPLETE (tooling ready, production import scheduled)
- ✅ **Epic 4**: Public Website & Content Discovery - 100% COMPLETE
- ✅ **Epic 5**: Enhanced Organizer Workflows - 100% COMPLETE (Auto-publishing & lifecycle automation delivered via BAT-16)
- ✅ **Epic 6**: Speaker Self-Service Portal - DEPLOYED TO STAGING (Phase 1 & 2 complete)

**Delivered Capabilities:**
- ✅ All entity CRUD operational (Company, User, Event, Speaker, Partner)
- ✅ Authentication & authorization (AWS Cognito, role-based access)
- ✅ Complete event workflow (9-state machine + speaker coordination + task system)
- ✅ Auto-publishing & CDN integration (speakers @ 30 days, agenda @ 14 days)
- ✅ Event lifecycle automation (EVENT_LIVE, EVENT_COMPLETED transitions)
- ✅ Public website with event registration (3-step wizard + QR codes)
- ✅ Historical archive browsing with content search
- ✅ Historical data migration tooling (production import pending user trigger)

**Scope Note:** Overflow Management (Story 5.6) removed from MVP scope - manual speaker selection sufficient for launch. Democratic voting on overflow speakers moved to Phase 2+ backlog.

**Current Phase:** Phase 2 (Epic 6) - Speaker Self-Service Portal ✅ **DEPLOYED TO STAGING** (2026-02-03)
- Stories 6.0-6.3: Complete (invitation, response, content submission)
- Story 6.4: Partial (dashboard views available)
- Story 6.5: Pending (automated reminders)

## Project Overview

BATbern is an enterprise event management platform for Berner Architekten Treffen conferences in Bern, Switzerland. It's a polyglot monorepo combining Java/Spring Boot microservices with a React/TypeScript frontend, deployed to AWS using CDK.

**Architecture Pattern**: Domain-Driven Design with microservices architecture
- **Shared Kernel**: Common types, domain events, utilities shared across all services
- **API Gateway**: Unified entry point handling authentication, rate limiting, and routing
- **Domain Services**: Event management, speaker coordination, partner coordination, attendee experience, company management
- **Infrastructure**: AWS CDK for Infrastructure as Code
- **Frontend**: React 19 + TypeScript SPA with role-based adaptive UI

## Current Development Phase

**Phase:** ✅ **MVP COMPLETE** + **Epic 6 on Staging**

**Epic Completion Status:**
- ✅ **Epic 1**: Foundation & Core Infrastructure - **100% COMPLETE**
- ✅ **Epic 2**: Entity CRUD & Domain Services - **100% COMPLETE**
- ✅ **Epic 3**: Historical Data Migration - **100% COMPLETE** (tooling ready, production import pending)
- ✅ **Epic 4**: Public Website & Content Discovery - **100% COMPLETE**
- ✅ **Epic 5**: Enhanced Organizer Workflows - **100% COMPLETE** (BAT-16 delivered auto-publishing & lifecycle automation)
- ✅ **Epic 6**: Speaker Self-Service Portal - **DEPLOYED TO STAGING** (Stories 6.0-6.3 complete)

**MVP Completion:**
- ✅ All 5 MVP epics (Epics 1-5) are 100% complete
- ✅ All core functionality delivered and tested
- ✅ Platform ready for production launch
- ✅ Epic 6 Speaker Portal deployed to staging (2026-02-03)

**When Adding New Features:**
- ✅ MVP platform is 100% feature-complete
- Epic 6 Phase 1 & 2 deployed - remaining: Story 6.4 (dashboard), Story 6.5 (reminders)
- Current focus: Epic 6 testing on staging, bug fixes, production readiness
- Prioritize Epic 6 completion and production deployment

## Build System

This is a **unified polyglot monorepo** managed through a root Makefile that orchestrates both Java/Gradle and Node.js/npm projects.

### Essential Commands

```bash
# Quick start for development
make install                    # Install all dependencies (Java + Node)
make build                      # Build everything
make test                       # Run all tests with coverage (requires Docker for integration tests)

# Development workflow
make verify                     # Pre-commit checks (lint + test)
make docker-up                  # Start all services with Docker Compose
make dev-native-up              # Start services natively (60-70% less resources)

# Native development - parallel instances support
make dev-native-up-instance BASE_PORT=9000    # Run multiple dev environments
make dev-native-list                          # List all running instances
make dev-native-status-all                    # Status of all instances

# Single technology stack
make build-java                 # Build Java projects only
make test-java                  # Run Java tests with coverage (Testcontainers PostgreSQL)
make build-node                 # Build Node.js projects only
make test-node                  # Run Node.js tests with coverage

# Code quality
make lint                       # Run all linters
make format                     # Format all code
make format-check               # Check code formatting
make audit-security             # Run security audits

# Dependency management
make check-outdated             # Check for outdated dependencies
make update-deps                # Update safe dependencies (patch/minor)
```

### Running Individual Services

**IMPORTANT**: All Gradle commands must be run from the repository root directory.

```bash
# Build and run services (from root directory)
./gradlew :shared-kernel:build
./gradlew :api-gateway:bootRun
./gradlew :services:event-management-service:bootRun
./gradlew :services:company-user-management-service:bootRun
./gradlew :services:speaker-coordination-service:bootRun
./gradlew :services:partner-coordination-service:bootRun
./gradlew :services:attendee-experience-service:bootRun

# Run tests for specific service
./gradlew :services:event-management-service:test

# Run Flyway migrations/repair (from root directory)
./gradlew :services:event-management-service:flywayMigrate
./gradlew :services:event-management-service:flywayRepair

# Frontend development
cd web-frontend && npm run dev

# Infrastructure deployment
cd infrastructure && npm run deploy:dev
```

### Test-Driven Development (TDD)

**CRITICAL**: All new features MUST follow TDD practices (Red-Green-Refactor):

1. **RED Phase**: Write failing tests first
2. **GREEN Phase**: Write minimal code to pass tests
3. **REFACTOR Phase**: Improve code while keeping tests green

**Integration tests** MUST use PostgreSQL via Testcontainers (not H2/in-memory) to ensure production parity. All integration tests extend `AbstractIntegrationTest` which provides a singleton PostgreSQL container.

```bash
# Run tests for specific service
./gradlew :api-gateway:test

# Run single test class
./gradlew :api-gateway:test --tests CompanyControllerIntegrationTest

# Run single test method
./gradlew :api-gateway:test --tests CompanyControllerIntegrationTest.should_createCompany_when_validDataProvided
```

## Project Structure

```
BATbern-main/
├── shared-kernel/                    # Common types, events, utilities (foundation)
├── api-gateway/                      # Unified API gateway with auth
├── services/                         # Domain microservices
│   ├── company-user-management-service/
│   ├── event-management-service/
│   ├── speaker-coordination-service/
│   ├── partner-coordination-service/
│   └── attendee-experience-service/
├── web-frontend/                     # React 19 + TypeScript SPA
├── infrastructure/                   # AWS CDK infrastructure as code
├── bruno-tests/                      # API contract tests (Layer 2 E2E)
├── scripts/
│   ├── ci/                          # CI/CD and test scripts
│   └── auth/                        # Authentication helpers
└── docs/
    ├── architecture/                # Architecture documentation
    ├── api/                        # OpenAPI specifications
    └── stories/                    # Development stories
```

### Key Architectural Patterns

1. **Shared Kernel First**: All shared types/events defined in `shared-kernel/`, published to Maven Local
2. **Service Independence**: Each domain service is independently deployable
3. **API Gateway Pattern**: All external requests go through API Gateway (port depends on BASE_PORT given in the make)
4. **Type Sharing**: Frontend generates TypeScript types from OpenAPI specs
5. **Integration Tests**: Use real PostgreSQL via Testcontainers (never H2/in-memory)

## Development Workflow

### Local Development Setup

**Prerequisites**: Java 21, Node.js 20+, Docker Desktop, AWS CLI v2 (for staging Cognito), jq

```bash
# Recommended: Local PostgreSQL + Native Services
docker compose -f docker-compose-dev.yml up -d       # Start local PostgreSQL
make dev-native-up                                   # Start services natively (60-70% less resources)

# First time only: Sync users from staging Cognito
./scripts/auth/get-token.sh staging your-email@example.com your-password  # One-time setup
./scripts/dev/sync-users-from-cognito.sh                                   # Sync users

# Services available at:
# - API Gateway: http://localhost:8000
# - Company/User Management: http://localhost:8001
# - Event Management: http://localhost:8002
# - Speaker Coordination: http://localhost:8003
# - Partner Coordination: http://localhost:8004
# - Attendee Experience: http://localhost:8005
# - Frontend: http://localhost:8100
```

**Local Development Architecture**:
- ✅ PostgreSQL 15 runs in Docker (persistent volume)
- ✅ All services run natively (Java processes + Vite dev server)
- ✅ Uses staging Cognito for authentication (AWS)
- ✅ Local database is read-only mirror synced from staging
- ✅ **Zero AWS development environment costs** (saves $600-720/year)
- ✅ 60-70% less resources than Docker Compose
- See [Local Development Guide](docs/guides/local-development-setup.md) for details

**Alternative: Docker Compose** (for integration testing):
```bash
docker-compose up -d   # All services in containers
```

### Debugging and Logs

**IMPORTANT**: This project uses **native development** (`make dev-native-up`), not Docker containers for services.

**Service Log Locations** (Native Development):
All service logs are written to `/tmp/batbern-1-*.log`:

```bash
# View service logs
tail -f /tmp/batbern-1-api-gateway.log
tail -f /tmp/batbern-1-company-user-management.log
tail -f /tmp/batbern-1-event-management.log
tail -f /tmp/batbern-1-speaker-coordination.log
tail -f /tmp/batbern-1-partner-coordination.log
tail -f /tmp/batbern-1-attendee-experience.log
tail -f /tmp/batbern-1-web-frontend.log

# Search for errors across all services
grep -i "error\|exception" /tmp/batbern-1-*.log

# Check specific service for recent errors
grep -A 10 -B 5 "ERROR\|Exception" /tmp/batbern-1-event-management.log | tail -100

# Monitor multiple services simultaneously
tail -f /tmp/batbern-1-{api-gateway,event-management,company-user-management}.log
```

**Service Process Management**:
```bash
# Check running services
make dev-native-status

# Restart a specific service
make dev-native-restart-service SERVICE=company-user-management

# Stop all services
make dev-native-down

# View service PIDs
cat /tmp/batbern-1-*.pid
```

**Common Debugging Patterns**:
```bash
# 1. Anonymous authentication errors (e.g., 401/500 on public endpoints)
grep -i "anonymousUser\|SecurityException" /tmp/batbern-1-company-user-management.log

# 2. Cross-service communication errors
grep -i "UserApiClient\|CompanyApiClient" /tmp/batbern-1-event-management.log

# 3. Database connection issues
grep -i "HikariPool\|connection" /tmp/batbern-1-*.log

# 4. API Gateway routing issues
grep -i "routing\|proxying" /tmp/batbern-1-api-gateway.log
```

**Note**: Docker logs (`docker logs <container>`) are only relevant when running `docker-compose up` instead of native development.

### Testing Strategy (4-Layer E2E Framework)

1. **Layer 1**: Shell scripts (`scripts/ci/*.sh`) - Infrastructure validation
2. **Layer 2**: Bruno tests (`bruno-tests/**/*.bru`) - API contract tests
3. **Layer 3**: Playwright tests (`web-frontend/e2e/*.spec.ts`) - UI E2E tests
4. **Layer 4**: Infrastructure tests (`infrastructure/test/e2e/*.test.ts`) - CDK tests

```bash
# Run Bruno API contract tests
./scripts/ci/run-bruno-tests.sh

# Run Playwright E2E tests
cd web-frontend && npm run test:e2e

# Run all layers
make test
```

### Authentication for Testing

```bash
# Get authentication token for local testing
./scripts/auth/get-token.sh

# Token is automatically injected into Bruno tests via environment
```

## Critical Development Standards

### Type Sharing

**ALWAYS** define types in `shared-kernel` and import from there. Never duplicate type definitions.

```java
// ✅ Correct - using shared kernel
import ch.batbern.shared.types.CompanyId;
import ch.batbern.shared.events.CompanyCreatedEvent;

// ❌ Wrong - duplicating types
public class CompanyId { ... }  // Don't redefine in each service
```

### API Development

**NEVER** make direct HTTP calls. Always use the service layer:

```typescript
// ✅ Correct - using service layer
import { companyService } from '@/services/companyService';
const company = await companyService.getCompany(id);

// ❌ Wrong - direct HTTP call
const response = await fetch('/api/companies/123');
```

### Environment Variables

**NEVER** access `process.env` directly. Use config objects:

```typescript
// ✅ Correct - using config
import { config } from '@/config';
const apiUrl = config.apiUrl;

// ❌ Wrong - direct access
const apiUrl = process.env.REACT_APP_API_URL;
```

### Integration Tests

**CRITICAL**: Integration tests MUST use PostgreSQL via Testcontainers, not H2/in-memory databases:

```java
// ✅ Correct - extends AbstractIntegrationTest (provides PostgreSQL)
@Transactional
class CompanyControllerIntegrationTest extends AbstractIntegrationTest {
    // Tests run against real PostgreSQL
}

// ❌ Wrong - using H2 or @DataJpaTest without PostgreSQL
@DataJpaTest  // Uses H2 by default - will miss PostgreSQL-specific issues
class CompanyRepositoryTest {
    // This creates false confidence
}
```

### File Uploads

**ALWAYS** use presigned URLs for direct S3 uploads. Never proxy files through backend:

```typescript
// ✅ Correct - direct S3 upload via presigned URL
const { uploadUrl } = await companyService.requestLogoUpload();
await axios.put(uploadUrl, file);

// ❌ Wrong - proxying through backend
await axios.post('/api/upload', formData);  // Wastes backend resources
```

## OpenAPI Type Generation

Frontend types are generated from OpenAPI specifications:

```bash
cd web-frontend
npm run generate:api-types  # Generates types from ../docs/api/*.openapi.yml
```

**Always regenerate types after API changes** and commit the generated types to version control.

## Git Workflow

```bash
# Conventional commits format
type(scope): description

# Types: feat, fix, docs, style, refactor, test, chore
# Examples:
git commit -m "feat(company): add logo upload functionality"
git commit -m "fix(auth): resolve token refresh race condition"
git commit -m "test(integration): add company API contract tests"
```

### Branching Strategy

- `main` - Production-ready code
- `develop` - Integration branch deployed to staging
- `feature/{description}` - Feature development
- `hotfix/{description}` - Critical production fixes

## Deployment

```bash
# Development (auto-deploy on merge to develop)
git push origin develop

# Staging (manual via GitHub Actions)
# Actions → Deploy to Staging → Run workflow

# Production (manual with approval)
git tag v1.2.3
git push origin v1.2.3
# Actions → Deploy to Production → Run workflow

# CDK deployment (infrastructure changes)
cd infrastructure
npm run deploy:staging
npm run deploy:prod
```

## AWS Monitoring & Logs

### AWS Profiles

The project uses multiple AWS accounts:
- `batbern-dev` - Development environment (Account: 954163570305)
- `batbern-staging` - Staging environment (Account: 188701360969)
- `batbern-prod` - Production environment (Account: 422940799530)

```bash
# Switch AWS profile for staging
export AWS_PROFILE=batbern-staging

# Or prefix commands
AWS_PROFILE=batbern-staging aws <command>
```

### CloudWatch Log Groups

All services log to CloudWatch with the naming pattern: `/aws/ecs/BATbern-{env}/{service-name}`

**Staging Environment Log Groups:**
```bash
/aws/ecs/BATbern-staging/api-gateway
/aws/ecs/BATbern-staging/event-management
/aws/ecs/BATbern-staging/speaker-coordination
/aws/ecs/BATbern-staging/partner-coordination
/aws/ecs/BATbern-staging/attendee-experience
/aws/ecs/BATbern-staging/company-user-management
```

### Quick Log Access

```bash
# Tail logs (last 30 minutes)
AWS_PROFILE=batbern-staging aws logs tail /aws/ecs/BATbern-staging/event-management --since 30m --follow

# Search for errors
AWS_PROFILE=batbern-staging aws logs tail /aws/ecs/BATbern-staging/event-management --since 1h --filter-pattern "ERROR"

# CloudWatch Insights query
QUERY_ID=$(AWS_PROFILE=batbern-staging aws logs start-query \
  --log-group-name "/aws/ecs/BATbern-staging/event-management" \
  --start-time $(date -v-1H +%s) \
  --end-time $(date +%s) \
  --query-string 'fields @timestamp, @message | filter @message like /ERROR/ | sort @timestamp desc | limit 50' \
  --output text)

# Get results after 3-5 seconds
AWS_PROFILE=batbern-staging aws logs get-query-results --query-id $QUERY_ID
```

### ECS Service Status

```bash
# List ECS clusters
AWS_PROFILE=batbern-staging aws ecs list-clusters

# Check service status
AWS_PROFILE=batbern-staging aws ecs describe-services \
  --cluster batbern-staging \
  --services BATbern-staging-EventManagement-ServiceD69D759B-nKXW8QZhG6Gy \
  --query 'services[0].{Status:status,Running:runningCount,Desired:desiredCount,Events:events[:5]}'

# View running tasks
AWS_PROFILE=batbern-staging aws ecs list-tasks \
  --cluster batbern-staging \
  --service-name BATbern-staging-EventManagement-ServiceD69D759B-nKXW8QZhG6Gy
```

### Common Issues

**Fargate Spot Interruptions:**
- Services use FARGATE_SPOT (70%) + FARGATE (30%) capacity providers
- Spot interruptions cause 4-5 minute service restarts (no logs, silent task termination)
- ECS automatically replaces with FARGATE task
- Check deployment history for task replacement events

**Log Stream Naming:**
- Pattern: `{service-name}/Container/{task-id}`
- Example: `event-management/Container/25e22b04a1984e288bbf09f8c8662035`
- Use `describe-log-streams` to find streams by task ID

## Coverage Requirements

- Unit Tests: 90% for business logic
- Integration Tests: 80% for APIs
- Overall: 85% line coverage

```bash
make test  # Run all tests with coverage reports
# Coverage reports generated at:
# - build/reports/jacoco/test/html/index.html (Java)
# - infrastructure/coverage/index.html (CDK)
# - web-frontend/coverage/index.html (Frontend)
```

## Dependency Management

### Automated Dependabot Updates

Dependencies are automatically updated monthly by Dependabot with zero maintenance required.

**Schedule**: First Monday of each month
- 3:00-4:30 AM: Dependabot creates grouped PRs
- 10:00 AM: Automated batch merge workflow processes PRs

**Configuration**: `.github/dependabot.yml`
- **Frequency**: Monthly (reduced from weekly to prevent PR accumulation)
- **Grouping**: Related dependencies bundled together (AWS SDK, Spring Boot, Vitest, etc.)
- **PR Limit**: 3-5 PRs per ecosystem (reduced from 10+ to minimize conflicts)

**Automated Merge**: `.github/workflows/dependabot-batch-merge.yml`
- Automatically rebases and merges non-conflicting PRs
- Closes conflicting PRs (will be recreated next month with latest versions)
- Runs full CI before merge (tests, lint, security scans)
- Zero manual intervention required

**Manual Intervention**:
```bash
# Trigger batch merge manually (dry-run)
gh workflow run dependabot-batch-merge.yml -f dry_run=true

# Trigger batch merge manually (live)
gh workflow run dependabot-batch-merge.yml -f dry_run=false

# Check dependabot PRs status
gh pr list --label dependencies

# Manually merge a specific PR (if needed)
gh pr merge <PR_NUMBER> --auto --squash
```

**Monitoring**:
- Workflow runs: [Actions → Dependabot Batch Merge](../../actions/workflows/dependabot-batch-merge.yml)
- PR summary: Check workflow run output for merge statistics
- Failed runs: Review workflow logs, may require manual intervention

**Expected PR Volume**: ~10-15 PRs per month (down from 40+ with weekly schedule)

## Troubleshooting

### Docker Issues

```bash
# Restart services
make docker-restart

# View logs
docker-compose logs -f api-gateway

# Database tunnel management
make docker-tunnel-logs     # View tunnel logs
make docker-tunnel-stop     # Stop database tunnel

# Clean state
make docker-down
docker-compose down -v
```

### Gradle Issues

```bash
# Clean build
make clean && make build

# Rebuild shared kernel
cd shared-kernel && ./gradlew clean build publishToMavenLocal
```

### Frontend Issues

```bash
# Clear node_modules and reinstall
cd web-frontend && rm -rf node_modules && npm ci

# Type check
npm run type-check
```

## Important Context Files

When working on architecture or standards, refer to:
- `docs/architecture/coding-standards.md` - Development standards and TDD practices
- `docs/architecture/tech-stack.md` - Technology choices and versions
- `docs/architecture/source-tree.md` - Detailed project structure
- `docs/api/*.openapi.yml` - API specifications

### Implementation Guides

When implementing new microservices, refer to these reusable implementation guides:
- `docs/guides/service-foundation-pattern.md` - Standard service structure, package layout, layer responsibilities
- `docs/guides/openapi-code-generation.md` - Hybrid contract-first approach with OpenAPI Generator
- `docs/guides/microservices-http-clients.md` - Cross-service communication, JWT propagation, HTTP enrichment
- `docs/guides/flyway-migration-guide.md` - Service-specific migrations, ADR-003 compliance, PostgreSQL patterns

These guides consolidate implementation patterns from across all BATbern microservices and should be consulted when implementing new services or features.

## Quality Standards

**Never take shortcuts** when implementing or trying to get tests to work. Always take the time to do the work at highest quality.

- Follow TDD practices (Red-Green-Refactor)
- Write tests before implementation
- Ensure all tests pass before committing
- Maintain 80%+ code coverage
- Update OpenAPI specs when changing APIs
- Regenerate types after API changes
- instead of running the test suites several times and grep the output, dump the output to a temp file and grep that file. this saves time
- whenever you run make, gradlew or git push or git comit, output result via tee to a temp file and then analyse or grep that one
## Personal Data & Security Guidelines

**CRITICAL**: Never commit files containing real personal data (PII) to version control.

### Data Handling Rules

1. **CSV Files with Personal Data**: NEVER commit CSV files containing real names, emails, or other PII
   - Use synthetic/anonymized test data for development
   - Real participant data should only exist in secure databases
   - CSV files in `apps/BATspa-old/` are blocked by `.gitignore`

2. **Test Data Guidelines**:
   - Use faker libraries to generate realistic but fake test data
   - Anonymize production data before using in development/testing
   - Never download production database dumps to local machines

3. **GDPR Compliance**:
   - Treat all participant data (names, emails, company associations) as PII
   - Ensure data retention policies are followed
   - Document data handling in privacy impact assessments

4. **Security Incident Response**:
   - If personal data is accidentally committed, follow the git history purge procedure:
     1. Use `git filter-repo` to remove from all history
     2. Force push to remote after creating backup branches
     3. Contact GitHub Support to purge cached commits (90-day retention)
     4. Add file patterns to `.gitignore` to prevent re-commit

5. **Sensitive File Patterns**:
   - `.env` files with credentials
   - `*.csv` files in legacy application directories
   - Database dumps (`.sql`, `.dump`)
   - API keys and tokens

### References
- GitHub Guide: [Removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
- GDPR compliance documentation: `docs/compliance/gdpr-guidelines.md` (if exists)
