# BATbern Platform

Enterprise event management platform for Berner Architekten Treffen (BATbern) conferences in Bern, Switzerland.

## Project Status

**Last Updated:** 2026-02-23

**Current Phase:** Post-MVP — Epics 1-6 and 8 complete; Epic 7 (Attendee Experience) deferred; Epic 9 (Speaker Auth) planned

**MVP Status:** ✅ **PRODUCTION READY** — All 5 MVP epics (1-5) complete. Epics 6 (Speaker Portal) and 8 (Partner Coordination) fully implemented.

### Epic Progress Summary

| Epic | Status | Progress | Key Accomplishments |
|------|--------|----------|---------------------|
| **Epic 1: Foundation & Core Infrastructure** | ✅ Complete | 100% | Shared Kernel, API Gateway, Auth, User Mgmt, CI/CD, Monitoring |
| **Epic 2: Entity CRUD & Domain Services** | ✅ Complete | 100% | Company, User, Event, Partner Management (Backend + Frontend) |
| **Epic 3: Historical Data Migration** | ✅ Complete | 100% | All batch imports implemented — production data import pending |
| **Epic 4: Public Website & Content Discovery** | ✅ Complete | 100% | Event landing pages, 3-step registration wizard, archive browsing, SEO |
| **Epic 5: Enhanced Organizer Workflows** | ✅ Complete | 100% | 9-state workflow, speaker coordination, auto-publishing, lifecycle automation |
| **Epic 6: Speaker Self-Service Portal** | ✅ Complete | 100% | All 6 stories (6.0-6.5) deployed — invitation, response portal, materials, dashboard, reminders |
| **Epic 7: Attendee Experience** | 📦 Deferred | — | Phase 3 backlog — personal dashboard, bookmarks, PWA |
| **Epic 8: Partner Coordination** | ✅ Complete | 100% | Partner portal shell, attendance analytics, topic voting, meeting coordination with ICS invites |
| **Epic 9: Speaker Authentication** | 🔨 In Progress | 20% | Story 9.1 (JWT magic link) complete; 9.2–9.5 (Cognito accounts, dual auth, migration, multi-role nav) planned |

### MVP Completion Status

**✅ All MVP Epics Complete (100%)**

**Production Readiness (When Launching):**
- **Epic 3: Production Data Import** — import 2,307 historical participants via batch import modal (~1 day effort)

**Next Steps:**
- Epic 9 Stories 9.2–9.5 — Cognito account creation on invitation accept, dual auth, staging migration, multi-role nav
- Epic 3: Production data import (~1 day effort)
- Epic 7 (Attendee Experience) — personal dashboard, bookmarks, PWA (Phase 3)

## Quick Start

### Prerequisites
- Java 21 LTS
- Node.js 20+
- Docker Desktop
- AWS CLI v2
- AWS CDK v2.110+
- jq (JSON processor: `brew install jq` on macOS)
- AWS credentials configured (`~/.aws/credentials` with profile `batbern-mgmt`)

### Local Development with Native Execution (Recommended)

**Important:** Local development uses Docker PostgreSQL (local) and staging Cognito (AWS) for authentication. **No AWS development environment required** - saves $600-720/year.

```bash
# Clone repository
git clone https://github.com/nissimbuchs/BATbern2.git
cd BATbern

# Install git hooks (pre-commit, pre-push)
./.githooks/install-hooks.sh

# Start local PostgreSQL in Docker
docker compose -f docker-compose-dev.yml up -d

# Start all services natively (recommended - 60-70% less resources)
make dev-native-up

# View logs
make dev-native-logs

# Check service status
make dev-native-status

# Stop all services
make dev-native-down
```

**What runs locally:**
- ✅ PostgreSQL 15 (Docker container with persistent volume)
- ✅ API Gateway (http://localhost:8080) - uses Caffeine in-memory cache
- ✅ All microservices (ports 8081-8085) - native Java processes
- ✅ Web Frontend (http://localhost:3000) - Vite dev server

**What uses AWS (staging environment):**
- ✅ Cognito User Pool (staging authentication)
- ✅ S3 for file uploads (staging buckets)

### First-Time Setup: Sync Users and Configure Test Auth

After starting services for the first time:

```bash
# 1. Get staging authentication token (organizer)
./scripts/auth/get-token.sh staging your-email@example.com your-password

# 2. Sync users from staging Cognito to local PostgreSQL
./scripts/dev/sync-users-from-cognito.sh

# 3. (Optional) Set up multi-role E2E test tokens (organizer + speaker + partner)
cp .env.test.local.example .env.test.local   # fill in credentials for each role
make setup-test-users                        # authenticates all roles
```

### Alternative: Docker Compose (For Integration Testing)

Use Docker Compose when you need to test containerized behavior:

```bash
# Start all services in containers
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

**Note:** Docker Compose is useful for pre-PR integration testing, but native execution is recommended for daily development due to better performance and resource usage.

### Quick Commands

```bash
# Native development (recommended)
make dev-native-up           # Start all services natively
make dev-native-status       # Check service health
make dev-native-logs         # View service logs
make dev-native-restart      # Restart all services
make dev-native-down         # Stop all services

# Access local PostgreSQL database
docker exec -it batbern-dev-postgres psql -U postgres -d batbern_development

# OR using psql from host
PGPASSWORD=devpass123 psql -h localhost -p 5432 -U postgres -d batbern_development

# Sync users from staging Cognito (uses stored token)
./scripts/dev/sync-users-from-cognito.sh

# Clean up local database
docker compose -f docker-compose-dev.yml down -v
```

### Local Email Inbox (Dev)

When running locally, emails are **not sent via AWS SES** — they are captured in-memory and viewable via a built-in dev inbox. No extra containers needed.

```bash
# View captured emails (JSON list — EMS)
curl http://localhost:8002/dev/emails | jq .

# View captured emails from partner service
curl http://localhost:8004/dev/emails | jq .

# Preview a specific email as rendered HTML in browser
open http://localhost:8002/dev/emails/<id>/preview

# Or use the React inbox UI (two-panel, select email to preview HTML)
open http://localhost:8100/dev/emails

# Simulate an inbound reply (triggers InboundEmailRouter — see reply body keywords below)
curl -X POST http://localhost:8002/dev/emails/<id>/reply \
  -H "Content-Type: application/json" \
  -d '{"replyBody": "CANCEL"}'

# Clear all captured emails
curl -X DELETE http://localhost:8002/dev/emails
```

**Reply simulation** (Story 10.17): The React inbox UI includes a "Simulate Reply" panel below each email preview. Use the quick-fill buttons or type freely:

| Reply body | Action |
|---|---|
| `UNSUBSCRIBE` / `abmelden` / `désinscription` | Unsubscribes sender from newsletter |
| `CANCEL` / `deregister` / `absagen` + event code in subject | Cancels event registration |
| `ACCEPT` / `bestätigen` / `confirmer` / `bevestigen` + event code in subject | Sends attendance confirmation |

The resulting confirmation email will appear in the inbox automatically after a successful reply.

**How it works:** `EmailService` (shared-kernel) detects `sesClient == null` on the `local` Spring profile and routes emails to `LocalEmailCapture` (in-memory ring buffer, max 200). `DevEmailController` (`@Profile("local")`) exposes them at `/dev/emails`. The endpoint is not registered in staging/production.

## Build Commands

The platform includes a unified build system using **Makefile** that orchestrates both Java/Gradle and Node.js/npm projects.

### Quick Reference

```bash
# Show all available commands
make help

# Install all dependencies
make install

# Build everything
make build

# Run all tests
make test

# Verify before commit (lint + test)
make verify

# Complete workflow
make all
```

### Common Tasks

**Setup & Dependencies:**
```bash
make install           # Install all (Java + Node)
make install-java      # Java dependencies only
make install-node      # Node.js dependencies only
```

**Building:**
```bash
make build             # Build all projects
make build-java        # Java/Gradle projects only
make build-node        # Node.js projects only
```

**Testing:**
```bash
make test              # Run all tests with coverage reports
make test-java         # Java tests only (with coverage)
make test-node         # Node.js tests only (with coverage)
make setup-test-users  # Authenticate all E2E test role users (organizer/speaker/partner)
```

**Code Quality:**
```bash
make lint              # Run all linters
make format            # Format all code
make format-check      # Check code formatting
make verify            # Pre-commit checks
make audit-security    # Run security audits and scans
```

**Dependency Management:**
```bash
make check-outdated    # Check for outdated dependencies
make update-deps       # Update safe dependencies (patch/minor)
```

**Docker:**
```bash
make docker-up            # Start services (same as docker-compose up -d)
make docker-down          # Stop services
make docker-restart       # Restart all Docker services
make docker-build         # Build Docker images
make docker-tunnel-stop   # Stop database tunnel
make docker-tunnel-logs   # View database tunnel logs
```

**Native Development (No Docker - 60-70% Less Resources):**
```bash
make dev-native-up            # Start all services natively
make dev-native-down          # Stop all native services
make dev-native-status        # Show service status
make dev-native-logs          # Tail all service logs
make dev-native-restart       # Restart all services
```

**Parallel Instances (Multiple Dev Environments):**
```bash
# Start instance 2 on different ports
make dev-native-up-instance BASE_PORT=9000

# Stop specific instance
make dev-native-down-instance BASE_PORT=9000

# List all running instances
make dev-native-list

# View status of all instances
make dev-native-status-all

# View logs from specific instance
make dev-native-logs-instance BASE_PORT=9000
```

**Cleanup:**
```bash
make clean             # Clean all build artifacts
make clean-java        # Clean Java builds only
make clean-node        # Clean Node.js builds only
```

### CI/CD Commands

```bash
make ci-build          # Full CI build (clean + install + build)
make ci-test           # All CI tests (coverage + lint)
```

## CI/CD Pipeline

The platform uses GitHub Actions for automated building, testing, and deployment.

### Automated Workflows

- **Build Pipeline** - Runs on every push to `develop` or `main`
- **Security Scanning** - Runs after successful builds
- **Staging Deployment** - Automatic on push to `develop` (or manual via Actions)
- **Production Deployment** - Manual via GitHub Actions after merging `develop → main`

### Deployment Environments

- **Staging:** https://staging.batbern.ch (auto-deploy from `develop`)
- **Production:** https://www.batbern.ch (manual, requires `develop → main` PR)

### Quick Deploy

**To Staging** (automatic):
```bash
git push origin develop
# Deploy to staging runs automatically after build passes
```

**To Production** (manual — 3 steps):
```bash
# Step 1: Open a PR on GitHub: develop → main and merge it.
#         The build pipeline runs on main and pushes images to the production ECR
#         with the merge commit SHA as the image tag.

# Step 2: Note the 7-char SHA of the merge commit
#         (visible in the commit list or the build workflow run)

# Step 3: Trigger the deploy
Actions → Deploy to Production → Run workflow → version: <sha7>
# e.g. version: a3f7c91
```

A GitHub Release (`prod/<sha7>`) is created automatically after a successful production deploy.
Each deploy is visible on the [/deployments](../../deployments) and [/releases](../../releases) pages.

## Architecture

### Platform Components

**Foundation (Epic 1) - ✅ Complete:**
- **Shared Kernel** - Common types, domain events, and utilities
- **API Gateway** - Unified API entry point with authentication
- **Infrastructure** - Multi-environment AWS CDK setup
- **CI/CD Pipeline** - Automated build, test, and deployment
- **Monitoring & Alerting** - CloudWatch dashboards and alarms

**Domain Services (Epic 2) - ✅ Complete:**
- **Company Management Service** - Company profiles and employee management
- **User Management Service** - User profiles, roles, and settings
- **Event Management Service** - Event lifecycle and coordination
- **Partner Coordination Service** - Partner collaboration and directory
- **React Frontend Foundation** - Role-adaptive UI components

**Historical Data Migration (Epic 3) - ✅ Complete:**
- **Batch Import System** - Frontend modals for all entity types
- **Participant Import API** - Batch registration endpoint (BAT-12, BAT-14)
- **CSV Processing** - 62-column participant CSV with 2,307 historical attendees
- **Data Migration Tools** - Companies, speakers, events, sessions, participants

**Public Website (Epic 4) - ✅ 100% Complete:**
- **Event Landing Pages** - Current event display with countdown
- **Registration Flow** - 3-step wizard with session selection
- **Email Confirmation** - AWS SES integration with QR codes
- **Archive Browsing** - Historical content discovery with search
- **SEO & Performance** - Optimized for search engines and fast loading

**Organizer Workflows (Epic 5) - ✅ 100% Complete:**
- **Event Type Definition** - Full-day, afternoon, evening formats
- **9-State Workflow Machine** - Event lifecycle from creation to archive
- **Speaker Coordination** - Per-speaker workflow with parallel progression
- **Task Management System** - Configurable tasks with auto-creation
- **Auto-Publishing Engine** - CDN integration with scheduled publishing
- **Lifecycle Automation** - EVENT_LIVE and EVENT_COMPLETED transitions

**Speaker Self-Service Portal (Epic 6) - ✅ 100% Complete:**
- **Automated Invitations** - AWS SES magic link invitation system
- **Self-Service Response Portal** - Speaker accept/decline via unique link (no login required)
- **Material Submission** - Speaker self-upload of title, abstract, CV, photo, presentation
- **Speaker Dashboard** - View upcoming/past events; WCAG 2.1 AA accessible
- **Deadline Reminders** - Configurable automated reminder tiers (friendly → urgent → escalate)

**Partner Coordination (Epic 8) - ✅ 100% Complete:**
- **Attendance Analytics Dashboard** - Per-company attendance table with cost-per-attendee KPI + XLSX export
- **Topic Voting** - Partners suggest topics and toggle-vote; organizers set status (Selected/Declined)
- **Meeting Coordination** - RFC 5545 ICS calendar invites with agenda sent to all partner contacts

### Technology Stack
- **Backend:** Java 21, Spring Boot 3.5+
- **Frontend:** React 18, TypeScript 5.3+, Material-UI
- **Database:** PostgreSQL 15+
- **Cache:** Caffeine 3.x (in-memory)
- **Infrastructure:** AWS ECS, RDS, S3, CloudFront
- **IaC:** AWS CDK 2.110+

## Testing

### Run Tests

**Unified Commands (Recommended):**
```bash
# Run all tests (Java + Node.js) with coverage reports
make test

# Run specific technology tests with coverage
make test-java         # Java/Gradle tests only
make test-node         # Node.js tests only
```

**Individual Component Tests:**
```bash
# Shared kernel tests
cd shared-kernel && ./gradlew test

# API Gateway tests
cd api-gateway && ./gradlew test

# Domain services tests
cd services/event-management-service && ./gradlew test

# Infrastructure tests
cd infrastructure && npm test

# Frontend tests
cd web-frontend && npm test
```

### TDD Standards
All new features must follow Test-Driven Development practices:
1. **RED Phase**: Write failing tests first
2. **GREEN Phase**: Write minimal code to pass tests
3. **REFACTOR Phase**: Improve code while keeping tests green

### Coverage Requirements
- Unit Tests: 90% for business logic
- Integration Tests: 80% for APIs
- Overall: 85% line coverage

## Security

### Vulnerability Scanning
- **Snyk** - Dependency scanning (daily)
- **SonarQube** - Code quality and security
- **License Checker** - License compliance

### Secrets Management
All secrets stored in GitHub Secrets and AWS Secrets Manager.

## Documentation

### Architecture & Standards
- [Architecture Documentation](docs/architecture/)
- [Development Standards](docs/architecture/coding-standards.md)
- [Source Tree Structure](docs/architecture/source-tree.md)
- [Tech Stack](docs/architecture/tech-stack.md)

### API Documentation
- [API Documentation](docs/api/)
- [Authentication Endpoints](docs/api/auth-endpoints.openapi.yml) - OpenAPI 3.1 spec

### User Guides
- [Forgot Password Flow](docs/guides/forgot-password-flow.md) - Password reset functionality (Story 1.2.2)
- [AWS SES Configuration](docs/guides/aws-ses-configuration.md) - Email template setup
- [Rate Limiting Rules](docs/guides/rate-limiting.md) - Security and abuse prevention
- [Troubleshooting Guide](docs/guides/troubleshooting-forgot-password.md) - Common issues and solutions

### Deployment
- [Deployment Guide](docs/deployment/)
- [CI/CD Pipeline Guide](docs/deployment/cicd-pipeline-guide.md)

## Contributing

1. Create feature branch from `develop`
2. Follow TDD practices (Red-Green-Refactor)
3. Ensure all tests pass
4. Meet coverage requirements (90%)
5. Pass security scans
6. Create PR with description
7. Get code review approval
8. Merge to `develop`

### Commit Convention
```
type(scope): description

feat(event): add automated speaker invitations
fix(frontend): resolve pagination bug
docs(api): update OpenAPI spec
test(integration): add contract tests
```

## 4-Layer E2E Testing Framework
  - Layer 1: Shell scripts (`scripts/ci/*.sh`) — smoke, CORS, header propagation
  - Layer 2: Bruno tests (`bruno-tests/`) — API contract validation per collection
  - Layer 3: Playwright tests (`web-frontend/e2e/`) — browser E2E, role-based projects
  - Layer 4: Infrastructure tests (`infrastructure/test/e2e/`) — AWS resource validation
  - Documentation: `docs/testing/e2e-testing-guide.md`

## Multi-Role E2E Authentication (Epic 8+)
Tests can run as three distinct roles: **organizer**, **speaker**, **partner**.

```bash
# Local setup: copy template, fill credentials, authenticate
cp .env.test.local.example .env.test.local
make setup-test-users          # → ~/.batbern/staging-{organizer,speaker,partner}.json

# Run partner E2E tests (Playwright partner project)
cd web-frontend && PARTNER_AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-partner.json) \
  npx playwright test --project=partner

# Bruno tests: use {{partnerAuthToken}} in .bru files instead of {{authToken}}
```

Scripts:
  - `scripts/auth/get-token.sh <env> <email> <pass> [role]` — get token for one role
  - `scripts/auth/setup-test-users.sh [env]` — authenticate all roles at once
  - `scripts/auth/refresh-token.sh [env] [role]` — refresh a role's token
  - `.env.test.local.example` — credential template (copy to `.env.test.local`, gitignored)

## License

Copyright © 2025 Berner Architekten Treffen (BATbern). All rights reserved.

## Support

- **Technical Issues:** GitHub Issues
- **Security Issues:** security@berner-architekten-treffen.ch
- **General Inquiries:** info@berner-architekten-treffen.ch
