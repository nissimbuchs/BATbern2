# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BATbern is an enterprise event management platform for Berner Architekten Treffen conferences in Bern, Switzerland. It's a polyglot monorepo combining Java/Spring Boot microservices with a React/TypeScript frontend, deployed to AWS using CDK.

**Architecture Pattern**: Domain-Driven Design with microservices architecture
- **Shared Kernel**: Common types, domain events, utilities shared across all services
- **API Gateway**: Unified entry point handling authentication, rate limiting, and routing
- **Domain Services**: Event management, speaker coordination, partner coordination, attendee experience, company management
- **Infrastructure**: AWS CDK for Infrastructure as Code
- **Frontend**: React 19 + TypeScript SPA with role-based adaptive UI

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

```bash
# Java services use Gradle wrapper at root
./gradlew :shared-kernel:build
./gradlew :api-gateway:bootRun
./gradlew :services:event-management-service:test

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
3. **API Gateway Pattern**: All external requests go through API Gateway (port 8080)
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
# - API Gateway: http://localhost:8080
# - Company/User Management: http://localhost:8081
# - Event Management: http://localhost:8082
# - Speaker Coordination: http://localhost:8083
# - Partner Coordination: http://localhost:8084
# - Attendee Experience: http://localhost:8085
# - Frontend: http://localhost:3000
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