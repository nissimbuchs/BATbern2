# BATbern Platform

Enterprise event management platform for Berner Architekten Treffen (BATbern) conferences in Bern, Switzerland.

## Project Status

**Current Phase**: Epic 1 - Foundation & Core Infrastructure

| Component | Status | Story |
|-----------|--------|-------|
| Shared Kernel | ✅ Complete | Story 1.1 |
| API Gateway & Auth | ✅ Complete | Story 1.2 |
| Multi-Env Infrastructure | ✅ Complete | Story 1.3 |
| CI/CD Pipeline | ✅ Complete | Story 1.4 |
| Environment Automation | ✅ Complete | Story 1.5 |
| Monitoring & Alerting | ✅ Complete | Story 1.6 |
| Domain Services | ⏳ Upcoming | Stories 1.14-1.19 |
| React Frontend | ⏳ Upcoming | Story 1.17 |

## Quick Start

### Prerequisites
- Java 21 LTS
- Node.js 20+
- Docker Desktop
- AWS CLI v2
- AWS CDK v2.110+
- jq (JSON processor: `brew install jq` on macOS)
- AWS credentials configured (`~/.aws/credentials` with profile `batbern-mgmt`)

### Local Development with Docker Compose (Recommended)

**Important:** Local development uses AWS DEV environment for infrastructure (RDS, Cognito). Only application containers run locally.

```bash
# Clone repository
git clone https://github.com/nisimbuchs/BATbern2.git
cd BATbern

# Generate environment configuration from AWS
AWS_PROFILE=batbern-mgmt ./scripts/dev/setup-env.sh

# Start all services with Docker Compose
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Stop and remove volumes (clean state)
docker-compose down -v
```

**What gets started locally:**
- ✅ Redis 7.2 (local caching - not deployed to AWS for dev)
- ✅ API Gateway (http://localhost:8080)
- ✅ Web Frontend (http://localhost:3000)

**What connects to AWS DEV environment:**
- ✅ RDS PostgreSQL 15 (AWS managed database)
- ✅ Cognito User Pool (AWS authentication)
- ✅ S3, EventBridge (AWS services)

### Alternative: Manual Setup (Advanced)

```bash
# Build shared kernel (foundation for all services)
cd shared-kernel
./gradlew build
./gradlew publishToMavenLocal

# Start services individually (in separate terminals)
cd ../api-gateway
./gradlew bootRun
```

> **Current Implementation Status**:
> - ✅ Shared Kernel (complete)
> - ✅ API Gateway & Authentication (complete)
> - ✅ Multi-environment infrastructure (complete)
> - ✅ CI/CD Pipeline (complete)
> - ✅ **Docker Compose local setup (complete - Story 1.4a)**
> - ⏳ Domain microservices (upcoming in Epic 1.14-1.19)

### Quick Commands

```bash
# Start services in background
docker-compose up -d

# Rebuild specific service after code changes
docker-compose up -d --build api-gateway

# Check service health
docker-compose ps

# View service logs
docker-compose logs -f api-gateway

# Access local Redis
docker exec -it batbern-redis redis-cli

# Access AWS RDS database (requires AWS credentials)
AWS_PROFILE=batbern-mgmt psql -h $(grep DB_HOST .env | cut -d '=' -f2) -U postgres -d batbern

# Regenerate .env from AWS (after infrastructure changes)
AWS_PROFILE=batbern-mgmt ./scripts/dev/setup-env.sh

# Clean up local services (keeps AWS infrastructure)
docker-compose down -v
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

**Implemented:**
- **Shared Kernel** - Common types, domain events, and utilities
- **API Gateway** - Unified API entry point with authentication
- **Infrastructure** - Multi-environment AWS CDK setup
- **CI/CD Pipeline** - Automated build, test, and deployment

**Planned (Epic 1):**
- **Event Management Service** - Event lifecycle and coordination
- **Speaker Coordination Service** - Speaker management and materials
- **Partner Coordination Service** - Partner collaboration
- **Attendee Experience Service** - Registration and attendee features
- **Company Management Service** - Company profiles and sharing

### Technology Stack
- **Backend:** Java 21, Spring Boot 3.5+
- **Frontend:** React 18, TypeScript 5.3+, Material-UI
- **Database:** PostgreSQL 15+
- **Cache:** Redis 7.2+
- **Infrastructure:** AWS ECS, RDS, ElastiCache, S3, CloudFront
- **IaC:** AWS CDK 2.110+

## Testing

### Run Tests (Current Components)
```bash
# Shared kernel tests
cd shared-kernel
./gradlew test

# API Gateway tests
cd api-gateway
./gradlew test

# Frontend tests (when available)
cd web-frontend
npm test
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

- [Architecture Documentation](docs/architecture/)
- [API Documentation](docs/api/)
- [Deployment Guide](docs/deployment/)
- [CI/CD Pipeline Guide](docs/deployment/cicd-pipeline-guide.md)
- [Development Standards](docs/architecture/coding-standards.md)

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

## License

Copyright © 2025 Berner Architekten Treffen (BATbern). All rights reserved.

## Support

- **Technical Issues:** GitHub Issues
- **Security Issues:** security@berner-architekten-treffen.ch
- **General Inquiries:** info@berner-architekten-treffen.ch
