# CI/CD Pipeline Guide

## Overview

The BATbern platform uses GitHub Actions for continuous integration and deployment. The pipeline automates building, testing, security scanning, and deploying all microservices and frontend applications to production (single AWS account 188701360969).

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
│                      Deployment                              │
│                  ┌──────────────┐                              │
│                  │  Production  │                              │
│                  │  (Auto from  │                              │
│                  │   develop)   │                              │
│                  └──────────────┘                              │
│                          ↓                                    │
│                  ┌──────────────┐                              │
│                  │ Smoke Tests  │                              │
│                  │ + E2E Tests  │                              │
│                  └──────────────┘                              │
│                          ↓                                    │
│                  ┌──────────────┐                              │
│                  │   Rollback   │                              │
│                  │ (On Failure) │                              │
│                  └──────────────┘                              │
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
   - Spins up PostgreSQL containers
   - Runs integration tests with Testcontainers
   - Uses Caffeine for in-memory caching (no external cache required)
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

### 3. Production Deployment (`deploy-staging.yml`)

> **Note:** The `deploy-staging.yml` workflow deploys to the production environment (staging account 188701360969 serves production traffic at www.batbern.ch).

**Triggers:**
- Automatic after successful build on `develop` branch
- Manual workflow dispatch

**Jobs:**
1. **Detect Changes**
   - Analyzes changed components via git diff
   - Selects deployment strategy (fast-path, hotswap, or full CDK)

2. **Database Backup** (if migrations detected)
   - Creates RDS snapshot before deployment

3. **Deploy**
   - Deploys with AWS CDK (layer-based for infrastructure changes)
   - Waits for ECS services to stabilize
   - Runs full E2E test suite (smoke, CORS, Bruno, Playwright)
   - Duration: 2-30 minutes depending on deployment tier

**Environment:** https://www.batbern.ch

### 4. Tagged Release Deployment (`deploy-production.yml`)

> **Note:** This workflow is being phased out. It targets the former production account (422940799530) which is decommissioned.
> Production deploys now go through `deploy-staging.yml` automatically.

**Environment:** https://www.batbern.ch

## Required Secrets

Configure these secrets in GitHub repository settings:

### AWS Credentials
- AWS credentials are managed via OIDC federation (no long-lived keys)
- Role: `arn:aws:iam::188701360969:role/batbern-staging-github-actions-role`

### Database Credentials
- Database credentials are managed via AWS Secrets Manager
- Microservices access credentials at runtime via ECS task IAM roles
- No database credentials stored in GitHub Secrets

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

**Production (via develop branch):**
- `{sha}-staging.{run_number}` - e.g., `a1b2c3d-staging.142`
- Used for all deployments to production (staging account serves production traffic)

## Deployment Process

### To Production (automatic)
1. Merge feature branch to `develop`
2. Build pipeline runs automatically
3. On success, deployment to production triggers automatically
4. Full E2E test suite validates deployment
5. Automatic rollback on failure

### Tagged Releases (manual)
1. Merge `develop → main` via PR on GitHub
2. Note the 7-char SHA of the merge commit
3. Navigate to Actions → Deploy to Production → Run workflow → version: `<sha7>`
4. GitHub Release is created automatically on success

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

**Current (3-tier deployment):**
- Fast-path (code-only): ~2-5 minutes
- Hotswap (service changes): ~10-20 minutes
- Full CDK (infrastructure): ~20-30 minutes

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
