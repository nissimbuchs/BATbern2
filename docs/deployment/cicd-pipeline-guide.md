# CI/CD Pipeline Guide

## Overview

The BATbern platform uses GitHub Actions for continuous integration and deployment. The pipeline automates building, testing, security scanning, and deploying all microservices and frontend applications across dev, staging, and production environments.

## Pipeline Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Build Pipeline                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │Shared Kernel │→ │   Services   │→ │   Frontend   │     │
│  │   Build      │  │ Build (Matrix)│  │    Build     │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                           ↓                                  │
│                  ┌──────────────┐                           │
│                  │ Integration  │                           │
│                  │    Tests     │                           │
│                  └──────────────┘                           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   Security Scanning                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │     Snyk     │  │  SonarQube   │  │   License    │     │
│  │  Scanning    │  │    Scan      │  │   Checking   │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                      Deployments                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │     Dev      │  │   Staging    │  │  Production  │     │
│  │  (Auto)      │  │  (Manual)    │  │   (Manual)   │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│         ↓                  ↓                  ↓             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Smoke Tests  │  │ Smoke Tests  │  │ Smoke Tests  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                              ↓               │
│                                     ┌──────────────┐        │
│                                     │   Rollback   │        │
│                                     │ (On Failure) │        │
│                                     └──────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

## Workflows

### 1. Build Pipeline (`build.yml`)

**Triggers:**
- Push to `develop` or `main` branches
- Pull requests to `develop` or `main`

**Jobs:**
1. **Build Shared Kernel**
   - Builds shared domain types and utilities
   - Runs unit tests with JaCoCo coverage (90% threshold)
   - Publishes to local Maven repository
   - Duration: ~2-3 minutes

2. **Build Services (Parallel Matrix)**
   - Builds all microservices in parallel
   - Creates Docker images using Spring Boot buildpacks
   - Pushes images to AWS ECR (on push events only)
   - Services: event-management, speaker-coordination, partner-coordination, attendee-experience, company-management, api-gateway
   - Duration: ~5-7 minutes per service (parallel)

3. **Build Frontend**
   - Installs npm dependencies with caching
   - Runs ESLint and tests with Vitest
   - Builds production bundle with Vite
   - Uploads build artifacts
   - Duration: ~3-4 minutes

4. **Integration Tests**
   - Spins up PostgreSQL and Redis containers
   - Runs integration tests with Testcontainers
   - Duration: ~3-5 minutes

**Total Pipeline Duration:** ~10-12 minutes

### 2. Security Scanning (`security-scan.yml`)

**Triggers:**
- After successful build pipeline completion
- Pull requests
- Daily at 2 AM UTC (scheduled)

**Jobs:**
1. **Snyk Vulnerability Scanning**
   - Scans Gradle dependencies
   - Scans npm dependencies
   - Fails on high severity vulnerabilities
   - Uploads results to GitHub Security

2. **SonarQube Code Quality**
   - Analyzes code quality and security
   - Enforces quality gate thresholds
   - Tracks technical debt
   - Requires full git history

3. **License Compliance**
   - Checks Gradle dependency licenses
   - Checks npm dependency licenses
   - Fails on GPL, AGPL, or other copyleft licenses

### 3. Development Deployment (`deploy-dev.yml`)

**Triggers:**
- Automatic after successful build on `develop` branch

**Jobs:**
1. **Deploy to Dev**
   - Runs Flyway database migrations
   - Deploys infrastructure with AWS CDK
   - Waits for ECS services to stabilize
   - Runs smoke tests
   - Duration: ~8-10 minutes

**Environment:** https://dev.batbern.ch

### 4. Staging Deployment (`deploy-staging.yml`)

**Triggers:**
- Manual workflow dispatch with version input

**Jobs:**
1. **Pre-deployment Validation**
   - Validates version exists
   - Checks dev deployment status
   - Requires manual approval

2. **Deploy to Staging**
   - Creates database backup snapshot
   - Runs Flyway migrations
   - Deploys with AWS CDK
   - Waits for ECS services
   - Runs smoke tests
   - Duration: ~12-15 minutes

**Environment:** https://staging.batbern.ch

### 5. Production Deployment (`deploy-production.yml`)

**Triggers:**
- Manual workflow dispatch with version input
- Requires two approvals

**Jobs:**
1. **Pre-deployment Checks**
   - Verifies version tag exists
   - Validates staging deployment
   - Requires production approval

2. **Database Backup**
   - Creates RDS snapshot
   - Waits for snapshot completion
   - Stores snapshot ID for rollback

3. **Database Migration**
   - Runs Flyway migrations (optional)
   - Can be disabled via workflow input

4. **Blue-Green Deployment**
   - Deploys new version alongside old
   - Gradually shifts traffic
   - Monitors health checks
   - Duration: ~15-20 minutes

5. **Smoke Tests**
   - Verifies critical endpoints
   - Checks service health
   - Validates database and cache connectivity

6. **Rollback on Failure**
   - Automatically triggers on any failure
   - Reverts to previous task definitions
   - Notifies team via GitHub output

**Environment:** https://www.batbern.ch

## Required Secrets

Configure these secrets in GitHub repository settings:

### AWS Credentials
- `AWS_ACCESS_KEY_ID` - AWS access key for deployments
- `AWS_SECRET_ACCESS_KEY` - AWS secret key
- `AWS_ACCOUNT_ID` - AWS account ID for ECR

### Database Credentials
- `DEV_DB_URL` - Dev database JDBC URL
- `DEV_DB_USER` - Dev database username
- `DEV_DB_PASSWORD` - Dev database password
- `STAGING_DB_URL` - Staging database JDBC URL
- `STAGING_DB_USER` - Staging database username
- `STAGING_DB_PASSWORD` - Staging database password
- `PROD_DB_URL` - Production database JDBC URL
- `PROD_DB_USER` - Production database username
- `PROD_DB_PASSWORD` - Production database password

### Security Scanning
- `SNYK_TOKEN` - Snyk API token for vulnerability scanning
- `SONAR_TOKEN` - SonarQube authentication token
- `SONAR_HOST_URL` - SonarQube server URL
- `CODECOV_TOKEN` - (Optional) Codecov upload token

### Notifications (Optional)
- `SLACK_WEBHOOK_URL` - Slack webhook for notifications

## Version Management

### Semantic Versioning

The platform uses semantic versioning: `v{major}.{minor}.{patch}`

**Examples:**
- `v1.0.0` - Major release
- `v1.2.3` - Minor and patch releases
- `v2.0.0-alpha.1` - Pre-release
- `v1.5.0+build.123` - Build metadata

### Image Tagging Strategy

**Development:**
- `{sha}-dev.{run_number}` - e.g., `a1b2c3d-dev.42`
- `latest-dev` - Latest develop branch build

**Staging/Production:**
- `{version}` - e.g., `v1.2.3`
- `latest` - Latest production release

## Deployment Process

### To Development
1. Merge feature branch to `develop`
2. Build pipeline runs automatically
3. On success, deployment to dev triggers automatically
4. Smoke tests validate deployment

### To Staging
1. Navigate to Actions → Deploy to Staging
2. Click "Run workflow"
3. Enter version (commit SHA or tag)
4. Approve deployment
5. Monitor deployment progress
6. Validate with smoke tests

### To Production
1. Create version tag: `git tag v1.2.3 && git push origin v1.2.3`
2. Navigate to Actions → Deploy to Production
3. Click "Run workflow"
4. Enter version tag (e.g., `v1.2.3`)
5. Select whether to run migrations
6. Get two approvals from team
7. Monitor blue-green deployment
8. Validate with smoke tests
9. Automatic rollback on failure

## Monitoring and Troubleshooting

### Build Failures

**Coverage Below Threshold:**
```
Error: Coverage 85% is below 90% threshold
Solution: Add more unit tests or adjust threshold in build.yml
```

**Security Vulnerabilities:**
```
Error: High severity vulnerabilities found
Solution: Update dependencies or suppress false positives in Snyk
```

**Quality Gate Failed:**
```
Error: SonarQube quality gate failed
Solution: Fix code smells, bugs, or security hotspots
```

### Deployment Failures

**Migration Errors:**
```
Error: Flyway migration failed
Solution: Check database backup, fix migration script, retry
```

**ECS Service Unstable:**
```
Error: Service did not stabilize within timeout
Solution: Check ECS console, CloudWatch logs, increase timeout
```

**Smoke Tests Failed:**
```
Error: Endpoint returned 500
Solution: Check service logs, rollback if necessary
```

### Rollback Procedures

**Automatic Rollback:**
- Production deployments automatically roll back on smoke test failures
- Previous task definitions are restored
- Team is notified via workflow output

**Manual Rollback:**
1. Navigate to AWS ECS Console
2. Select production cluster
3. Update service to previous task definition
4. Force new deployment

**Database Rollback:**
1. Find snapshot ID from deployment logs
2. Restore RDS instance from snapshot
3. Update application configuration
4. Redeploy previous version

## Performance Optimization

### Build Time Optimization

**Current:** ~10-12 minutes
**Target:** < 10 minutes

**Optimizations Implemented:**
- Gradle dependency caching
- npm dependency caching
- Docker layer caching
- Parallel service builds
- Reusable workflows

**Further Optimizations:**
- Use custom Docker images with pre-installed dependencies
- Split integration tests into parallel jobs
- Use GitHub Actions cache for Gradle build cache
- Implement incremental builds

### Deployment Time Optimization

**Current:**
- Dev: ~8-10 minutes
- Staging: ~12-15 minutes
- Production: ~15-20 minutes

**Optimizations:**
- Pre-build Docker images
- Parallel service deployments
- Optimize health check intervals
- Use CDK hotswap for faster dev deployments

## Quality Gates

### Code Coverage
- **Threshold:** 90%
- **Tool:** JaCoCo (Java), Vitest (TypeScript)
- **Enforcement:** Build fails if below threshold

### Security Scanning
- **Tool:** Snyk, SonarQube
- **Threshold:** No high severity vulnerabilities
- **Frequency:** Every build + daily scans

### License Compliance
- **Tool:** license-checker (npm), Gradle dependencies
- **Policy:** No GPL, AGPL, or SSPL licenses
- **Enforcement:** Build fails on violation

### Performance Testing
- **Tool:** K6
- **Thresholds:**
  - P95 response time < 500ms
  - Error rate < 1%
- **Frequency:** Before production deployment

## Best Practices

### Branch Strategy
- Create feature branches from `develop`
- Name: `feature/{description}`
- PR to `develop` for review
- Squash merge to keep history clean

### Commit Messages
Follow conventional commits:
```
feat(cicd): add blue-green deployment
fix(build): correct coverage threshold check
docs(deployment): update runbook
test(integration): add contract tests
```

### Deployment Checklist
- [ ] All tests passing
- [ ] Security scan passed
- [ ] Code reviewed and approved
- [ ] Database migrations tested
- [ ] Deployment window scheduled
- [ ] Team notified
- [ ] Rollback plan ready
- [ ] Monitoring configured

### Emergency Procedures
1. Stop ongoing deployments
2. Roll back to last known good version
3. Restore database from latest snapshot
4. Notify team and stakeholders
5. Investigate root cause
6. Document incident
7. Implement preventive measures

## Support and Contact

**CI/CD Issues:**
- Check workflow logs in GitHub Actions
- Review deployment logs in AWS CloudWatch
- Contact DevOps team

**Access Issues:**
- Verify GitHub repository permissions
- Check AWS IAM permissions
- Request access from team lead

## Further Reading

- [GitHub Actions Documentation](https://docs.github.com/actions)
- [AWS CDK Guide](https://docs.aws.amazon.com/cdk/)
- [AWS ECS Blue/Green Deployments](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-bluegreen.html)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [K6 Performance Testing](https://k6.io/docs/)
