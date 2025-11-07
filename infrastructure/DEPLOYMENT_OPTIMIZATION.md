# CDK Deployment Optimization Guide

This guide explains the optimized deployment workflows to dramatically reduce deployment times from 30+ minutes to 2-8 minutes.

## Quick Reference

| Command | Use Case | Time | When to Use |
|---------|----------|------|-------------|
| `npm run deploy:staging:fast` | Most common code changes | ~3-5 min | Changed Event or Company service code |
| `npm run deploy:staging:microservices` | All service code changes | ~8-12 min | Changed multiple services |
| `npm run deploy:staging:frontend` | Frontend changes only | ~3-5 min | Changed React app |
| `npm run deploy:staging:infra` | Infrastructure changes | ~5-8 min | Changed VPC, DB, Storage, Cognito |
| `npm run deploy:staging:hotswap` | Lambda code changes | ~30-60 sec | Changed Lambda trigger code |
| `npm run deploy:staging` | Full deployment | ~30+ min | Initial setup or major changes |

## Optimizations Applied

### 1. Selective Stack Deployment (70-80% time savings)

**Before**: Deployed all 20 stacks even when only 1 changed
**After**: Deploy only the stacks you need

```bash
# Deploy just the services you changed
npm run deploy:staging:fast

# Deploy a specific service
./infrastructure/deploy-selective.sh -s EventManagement

# Deploy multiple specific services
./infrastructure/deploy-selective.sh -s EventManagement,CompanyManagement
```

### 2. Docker BuildKit Caching (50-70% build time savings)

**Before**: Rebuilt Gradle dependencies on every Docker build
**After**: Cached Gradle dependencies and downloads between builds

Docker files now use:
- BuildKit syntax (`# syntax=docker/dockerfile:1.4`)
- Cache mounts for Gradle (`--mount=type=cache,target=/root/.gradle`)
- Optimized layer ordering (dependencies downloaded before source copy)

**Enable BuildKit (automatic in scripts)**:
```bash
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1
```

### 3. Parallel Stack Deployments

Using `--concurrency=3` to deploy multiple independent stacks simultaneously.

## Recommended Workflows

### For Daily Development

**Scenario 1: Changed Event Management Service Code**
```bash
cd infrastructure
npm run deploy:staging:fast
```
⏱️ Time: ~3-5 minutes (vs 30+ minutes before)

**Scenario 2: Changed Multiple Microservices**
```bash
cd infrastructure
npm run deploy:staging:microservices
```
⏱️ Time: ~8-12 minutes (vs 30+ minutes before)

**Scenario 3: Lambda Trigger Code Changes**
```bash
cd infrastructure
npm run deploy:staging:hotswap
```
⏱️ Time: ~30-60 seconds (hotswap skips CloudFormation)

### For Infrastructure Changes

**Database, Network, or Cognito Changes**
```bash
cd infrastructure
npm run deploy:staging:infra
```
⏱️ Time: ~5-8 minutes

**Frontend-Only Changes**
```bash
cd infrastructure
npm run deploy:staging:frontend
```
⏱️ Time: ~3-5 minutes

### For Initial Setup or Major Changes

```bash
cd infrastructure
npm run deploy:staging
```
⏱️ Time: ~30+ minutes (deploys everything)

## Advanced: Selective Deployment Script

The `deploy-selective.sh` script provides fine-grained control:

```bash
cd infrastructure

# Show help
./deploy-selective.sh --help

# Deploy only microservices
./deploy-selective.sh -m

# Deploy specific stacks
./deploy-selective.sh -s EventManagement,CompanyManagement

# Deploy infrastructure
./deploy-selective.sh -i

# Dry run (see what would deploy)
./deploy-selective.sh -m --dry-run

# Production deployment
./deploy-selective.sh -e production -m
```

## Performance Tips

### 1. Pre-build Shared Kernel Locally

If you're developing locally and frequently changing shared-kernel:

```bash
cd /path/to/BATbern-main
./gradlew :shared-kernel:build publishToMavenLocal
```

This publishes shared-kernel to your local Maven cache, speeding up service builds.

### 2. Use CDK Watch for Active Development

For rapid iteration on Lambda functions:

```bash
cd infrastructure
AWS_PROFILE=batbern-staging cdk watch --context environment=staging
```

CDK watch automatically detects changes and deploys them using hotswap (seconds instead of minutes).

### 3. Docker Layer Caching

BuildKit caches are stored in Docker. To clear if needed:

```bash
docker builder prune
```

### 4. Check What Changed Before Deploying

```bash
cd infrastructure
AWS_PROFILE=batbern-staging cdk diff --context environment=staging
```

Shows exactly what would change before you deploy.

## Troubleshooting

### "No stacks specified"

Make sure you're using one of the npm scripts or the selective deployment script with arguments.

### Docker Build Still Slow

1. Ensure BuildKit is enabled: `export DOCKER_BUILDKIT=1`
2. Check Docker has enough resources (Settings → Resources)
3. Verify `.dockerignore` is present in project root

### Deployment Fails with "Stack not found"

Stack names are case-sensitive. Use exact names:
- `BATbern-staging-EventManagement` ✅
- `BATbern-staging-eventmanagement` ❌

### Need to Deploy Everything

Use the original command:
```bash
npm run deploy:staging
```

## Monitoring Deployment Performance

Time your deployments to verify improvements:

```bash
time npm run deploy:staging:fast
```

Expected results:
- **Before optimization**: 25-35 minutes
- **After optimization** (selective): 3-8 minutes
- **After optimization** (hotswap): 30-60 seconds

## Further Optimization (Future)

These optimizations are planned but not yet implemented:

1. **Pre-built Images in CI/CD**: Build Docker images in GitHub Actions, push to ECR, reference in CDK
2. **Gradle Remote Cache**: Use Gradle Enterprise or S3 for remote build caching
3. **ECR Image Promotion**: Build once in DEV, promote same image hash to STAGING/PROD
4. **Separate Service Deployments**: Independent pipeline for each microservice

## Questions?

See the main README or infrastructure documentation for more details.
