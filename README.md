# BATbern Platform

Enterprise event management platform for Business Analytics Today (BAT) conferences in Bern, Switzerland.

## Quick Start

### Prerequisites
- Java 21 LTS
- Node.js 20+
- Docker Desktop
- AWS CLI v2
- AWS CDK v2.110+

### Local Development

```bash
# Clone repository
git clone https://github.com/batbern/platform.git
cd platform

# Start local services
docker-compose up -d

# Build shared kernel
cd shared-kernel
./gradlew build

# Build services (example)
cd services/event-management-service
./gradlew bootRun

# Start frontend
cd web-frontend
npm install
npm run dev
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

### Microservices
- **Event Management Service** - Event lifecycle and coordination
- **Speaker Coordination Service** - Speaker management and materials
- **Partner Coordination Service** - Partner collaboration
- **Attendee Experience Service** - Registration and attendee features
- **Company Management Service** - Company profiles and sharing
- **API Gateway** - Unified API entry point

### Technology Stack
- **Backend:** Java 21, Spring Boot 3.5+
- **Frontend:** React 18, TypeScript 5.3+, Material-UI
- **Database:** PostgreSQL 15+
- **Cache:** Redis 7.2+
- **Infrastructure:** AWS ECS, RDS, ElastiCache, S3, CloudFront
- **IaC:** AWS CDK 2.110+

## Testing

### Run All Tests
```bash
# Backend tests
./gradlew test

# Frontend tests
cd web-frontend
npm test

# Integration tests
./gradlew integrationTest

# E2E tests
cd e2e-tests
npm run test
```

### Coverage Requirements
- Unit Tests: 90%
- Integration Tests: 80%
- Overall: 85%

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

Copyright © 2025 Business Analytics Today (BAT). All rights reserved.

## Support

- **Technical Issues:** GitHub Issues
- **Security Issues:** security@batbern.ch
- **General Inquiries:** info@batbern.ch
