# BATbern Platform

Enterprise event management platform for Berner Architekten Treffen (BATbern) conferences in Bern, Switzerland.

## Project Status

**Last Updated:** 2025-12-18

**Current Phase:** Epics 1-2 Complete, Epic 4 Nearly Complete, Epic 5 in Progress

### Epic Progress Summary

| Epic | Status | Progress | Key Accomplishments |
|------|--------|----------|---------------------|
| **Epic 1: Foundation & Core Infrastructure** | ✅ Complete | 100% (27/27) | Shared Kernel, API Gateway, Auth (all flows), User Mgmt, CI/CD, Monitoring, API Consolidation |
| **Epic 2: Entity CRUD & Domain Services** | ✅ Complete | 100% (8/8) | Company, User, Event, Partner Management (Backend + Frontend) |
| **Epic 3: Historical Data Migration** | 🔄 In Progress | 33% (1/3) | Data Inventory Complete |
| **Epic 4: Public Website & Content Discovery** | 🔄 In Progress | ~85% (8/15+) | Landing Page, Registration Flow Complete |
| **Epic 5: Enhanced Organizer Workflows** | 🔄 In Progress | 25% (4/16) | Event Type, State Machine, Outreach Tracking, Heat Map |

### Key Components Status

| Component | Status | Stories |
|-----------|--------|---------|
| **Infrastructure Foundation** | ✅ Complete | 1.1-1.7, 1.9, 1.11 |
| **API Gateway & Authentication** | ✅ Complete | 1.2 + sub-stories |
| **Shared Kernel & Types** | ✅ Complete | 1.1, 1.15a |
| **Company Management** | ✅ Complete | 1.14, 2.5.1 |
| **User Management** | ✅ Complete | 1.14-2, 2.5.2 |
| **Event Management** | ✅ Complete | 2.2, 2.5.3 |
| **Partner Management** | ✅ Complete | 2.7, 2.8.1-2.8.4 |
| **React Frontend Foundation** | ✅ Complete | 1.17 |
| **Public Website - Registration** | ✅ Complete | 4.1.1-4.1.6 |
| **Public Website - Backend** | 🔄 In Progress | 4.1.7, 2.2a |
| **Event Type & Workflow** | ✅ Complete | 5.1, 5.1a, 5.3 |
| **Topic Selection** | 🔄 Ready for Review | 5.2 |

### Next Priorities

1. Complete Epic 4 (Public Website) - ~2 weeks
   - Finish backend integration (4.1.7, 2.2a)
   - Testing, SEO, and deployment (4.1.8)
2. Continue Epic 5 (Organizer Workflows) - Phase B
   - Review and merge 5.2 (Topic Selection)
   - Implement 5.4 (Speaker Status Management)
   - Implement 5.5 (Speaker Content Collection)

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
git clone https://github.com/nisimbuchs/BATbern2.git
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

### First-Time Setup: Sync Users from Staging Cognito

After starting services for the first time, sync existing Cognito users to your local database:

```bash
# Get staging authentication token (one-time setup)
./scripts/auth/get-token.sh staging your-email@example.com your-password

# Sync users from staging Cognito to local PostgreSQL
./scripts/dev/sync-users-from-cognito.sh
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
- **Dev Deployment** - Automatic on merge to `develop`
- **Staging Deployment** - Manual with approval
- **Production Deployment** - Manual with two approvals

### Deployment Environments

- **Development:** https://dev.batbern.ch (auto-deploy)
- **Staging:** https://staging.batbern.ch (manual)
- **Production:** https://www.batbern.ch (manual)

### Quick Deploy

**To Development:**
```bash
git checkout develop
git merge feature/my-feature
git push origin develop
# Deployment happens automatically
```

**To Staging:**
```bash
# Via GitHub Actions UI
Actions → Deploy to Staging → Run workflow
Version: <commit-sha-or-tag>
```

**To Production:**
```bash
# Create version tag
git tag v1.2.3
git push origin v1.2.3

# Via GitHub Actions UI
Actions → Deploy to Production → Run workflow
Version: v1.2.3
```

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

**Public Website (Epic 4) - 🔄 85% Complete:**
- **Event Landing Pages** - Current event display with countdown
- **Registration Flow** - 3-step wizard with session selection
- **Email Confirmation** - AWS SES integration with QR codes
- **Anonymous Registration** - Backend integration (in progress)

**Organizer Workflows (Epic 5) - 🔄 19% Complete:**
- **Event Type Definition** - Full-day, afternoon, evening formats
- **Workflow State Machine** - Event and speaker lifecycle tracking
- **Speaker Outreach** - Contact strategy and tracking

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

## 4-Layer E2E Testing Framework:
  - Layer 1: Shell scripts (scripts/ci/*.sh)
  - Layer 2: Bruno tests (bruno-tests/companies-api/*.bru)
  - Layer 3: Playwright tests (web-frontend/e2e/*.spec.ts)
  - Layer 4: Infrastructure tests (infrastructure/test/e2e/*.test.ts)
  - Documentation: docs/testing/e2e-testing-guide.md

## Local Authentication System:
  - scripts/auth/get-token.sh - Token retrieval

## License

Copyright © 2025 Berner Architekten Treffen (BATbern). All rights reserved.

## Support

- **Technical Issues:** GitHub Issues
- **Security Issues:** security@berner-architekten-treffen.ch
- **General Inquiries:** info@berner-architekten-treffen.ch
