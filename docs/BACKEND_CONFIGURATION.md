# Backend Configuration Guide

## Overview

BATbern backend uses **runtime configuration** via environment variables. Spring Boot reads configuration at startup from environment variables, enabling the same build (JAR files) to work across all environments without recompilation.

## Architecture

### Configuration Flow

```
┌────────────────────────────────────────────────────┐
│  CDK TypeScript Configs (Single Source of Truth)   │
│  - infrastructure/lib/config/dev-config.ts         │
│  - infrastructure/lib/config/staging-config.ts     │
│  - infrastructure/lib/config/prod-config.ts        │
└────────────────┬───────────────────────────────────┘
                 │
                 ↓
┌────────────────────────────────────────────────────┐
│  CDK Deploy → CloudFormation Stacks                │
│  - Database endpoints, Cognito IDs, etc.           │
└────────────────┬───────────────────────────────────┘
                 │
                 ↓
┌────────────────────────────────────────────────────┐
│  Config Sync Script (Fetches & Generates)          │
│  scripts/config/sync-backend-config.sh             │
│  - Reads CloudFormation outputs via AWS CLI        │
│  - Uses template: config/templates/backend.env.template│
│  - Generates environment-specific .env files       │
└────────────────┬───────────────────────────────────┘
                 │
       ┌─────────┴─────────┬───────────────┐
       ↓                   ↓               ↓
  .env (Dev)         .env (Staging)   .env (Prod)
  Docker Compose     ECS Task Def     ECS Task Def
       │                   │               │
       ↓                   ↓               ↓
  Spring Boot        Spring Boot     Spring Boot
  (reads at          (reads at       (reads at
   JVM startup)       JVM startup)    JVM startup)
```

### Key Principle: Runtime Configuration

**Spring Boot reads environment variables at JVM startup:**

```yaml
# application.yml
aws:
  cognito:
    userPoolId: ${COGNITO_USER_POOL_ID}  # Read from environment at startup
    appClientId: ${COGNITO_CLIENT_ID}
```

When Spring Boot starts:
1. JVM loads environment variables
2. Spring Boot replaces `${PLACEHOLDER}` with actual values
3. Application runs with environment-specific configuration

**Same JAR file works in all environments!**

---

## How It Works

### 1. Source of Truth: CDK Configs

CDK TypeScript configs define infrastructure for each environment:

```typescript
// infrastructure/lib/config/dev-config.ts
export const devConfig: EnvironmentConfig = {
  envName: 'development',
  region: 'eu-central-1',
  account: '954163570305',
  rds: {
    instanceClass: ec2.InstanceClass.T3,
    instanceSize: ec2.InstanceSize.MICRO,
    // ...
  },
  // ...
};
```

### 2. Deployed Resources

CDK deploys infrastructure and exports outputs via CloudFormation:

```bash
# Example CDK stack outputs
DatabaseEndpoint: batbern-dev-db.xxxxx.eu-central-1.rds.amazonaws.com
DatabasePort: 5432
DatabaseSecretName: BATbern-dev-DBSecret-xxxxx
CognitoUserPoolId: eu-central-1_xxxxx
```

### 3. Config Generation

Sync script fetches outputs and generates `.env`:

```bash
./scripts/config/sync-backend-config.sh development
```

**What it does:**
1. Connects to AWS using profile (batbern-dev/staging/prod)
2. Fetches CloudFormation stack outputs
3. Fetches secrets from AWS Secrets Manager (DB password)
4. Populates template with values
5. Generates `.env` file

### 4. Spring Boot Startup

```bash
# Docker Compose reads .env and passes to containers
docker-compose up api-gateway

# Spring Boot JVM starts with environment variables
# ${COGNITO_USER_POOL_ID} → actual value from .env
```

---

## Local Development Setup

### Prerequisites

- AWS CLI installed and configured
- jq installed (`brew install jq`)
- AWS credentials configured for development profile

### Initial Setup

```bash
# 1. Deploy infrastructure (if not done)
cd infrastructure
npx cdk deploy --all --profile batbern-dev

# 2. Generate configuration from deployed resources
cd ..
./scripts/config/sync-backend-config.sh development

# 3. Review generated .env file
cat .env

# 4. Start services
docker-compose up -d

# 5. Verify services are running
docker-compose ps
docker-compose logs -f api-gateway
```

### Configuration Files

| File | Purpose | Git Tracked? |
|------|---------|--------------|
| `config/templates/backend.env.template` | Template with placeholders | ✅ Yes |
| `.env` | Generated config (root directory) | ❌ No (git-ignored) |
| `application.yml` | Spring Boot config with ${PLACEHOLDER} | ✅ Yes |
| `docker-compose.yml` | Maps .env to containers | ✅ Yes |

---

## Configuration Scripts

### Sync Configuration

Generate `.env` for specific environment:

```bash
# Development (default)
./scripts/config/sync-backend-config.sh development

# Staging
./scripts/config/sync-backend-config.sh staging

# Production
./scripts/config/sync-backend-config.sh production
```

### Validate Configuration

Check that all required variables are present:

```bash
./scripts/config/validate-backend-config.sh
```

**Output:**
```
✅ All required variables present and have values

Configuration Summary:
  Environment:    development
  Database:       batbern-dev-db.xxxxx.rds.amazonaws.com
  Cognito Pool:   eu-central-1_xxxxx
```

---

## Environment-Specific Configuration

### Development

**Characteristics:**
- Local Redis (Docker container)
- AWS RDS database (shared dev)
- AWS Cognito (dev user pool)
- Spring Profile: `local`
- Log Level: `DEBUG`

```bash
APP_ENVIRONMENT=development
SPRING_PROFILES_ACTIVE=local
LOG_LEVEL=DEBUG
REDIS_HOST=redis  # Local Docker container
```

### Staging

**Characteristics:**
- AWS ElastiCache (Redis)
- AWS RDS database (staging)
- AWS Cognito (staging user pool)
- Spring Profile: `staging`
- Log Level: `INFO`

```bash
APP_ENVIRONMENT=staging
SPRING_PROFILES_ACTIVE=staging
LOG_LEVEL=INFO
REDIS_HOST=batbern-staging-cache.xxxxx.cache.amazonaws.com
```

### Production

**Characteristics:**
- AWS ElastiCache (Redis) with multi-AZ
- AWS RDS database (production, multi-AZ)
- AWS Cognito (production user pool)
- Spring Profile: `production`
- Log Level: `WARN`

```bash
APP_ENVIRONMENT=production
SPRING_PROFILES_ACTIVE=production
LOG_LEVEL=WARN
REDIS_HOST=batbern-prod-cache.xxxxx.cache.amazonaws.com
```

---

## Adding New Configuration Values

### Step-by-Step

1. **Add to CDK Config** (if infrastructure-related):
```typescript
// infrastructure/lib/config/environment-config.ts
export interface EnvironmentConfig {
  // ... existing
  newFeature: {
    enabled: boolean;
    timeout: number;
  };
}
```

2. **Add to CDK Stack** (export as output):
```typescript
// infrastructure/lib/stacks/some-stack.ts
new cdk.CfnOutput(this, 'NewFeatureEndpoint', {
  value: newResource.endpoint,
  exportName: `${config.envName}-NewFeatureEndpoint`,
});
```

3. **Add to .env Template**:
```bash
# config/templates/backend.env.template
NEW_FEATURE_ENDPOINT={{NEW_FEATURE_ENDPOINT}}
NEW_FEATURE_TIMEOUT={{NEW_FEATURE_TIMEOUT}}
```

4. **Add to Sync Script**:
```bash
# scripts/config/sync-backend-config.sh
NEW_FEATURE_ENDPOINT=$(get_stack_output "${SOME_STACK}" "NewFeatureEndpoint")
# ... in template substitution:
sed "s|{{NEW_FEATURE_ENDPOINT}}|${NEW_FEATURE_ENDPOINT}|g"
```

5. **Add to application.yml**:
```yaml
# api-gateway/src/main/resources/application.yml
new-feature:
  endpoint: ${NEW_FEATURE_ENDPOINT}
  timeout: ${NEW_FEATURE_TIMEOUT:5000}  # Default: 5000ms
```

6. **Re-generate Configuration**:
```bash
./scripts/config/sync-backend-config.sh development
```

---

## Troubleshooting

### Issue: "Could not fetch database endpoint"

**Cause:** CDK stack not deployed

**Solution:**
```bash
cd infrastructure
npx cdk deploy BATbern-development-Database --profile batbern-dev
```

### Issue: "AWS credentials not configured"

**Cause:** Missing AWS profile

**Solution:**
```bash
aws configure --profile batbern-dev
# Enter Access Key ID, Secret Access Key, Region (eu-central-1)
```

### Issue: "Missing required variables"

**Cause:** Incomplete stack deployment or script issue

**Solution:**
```bash
# 1. Validate what's in .env
cat .env

# 2. Check CloudFormation stacks
aws cloudformation describe-stacks \
  --profile batbern-dev \
  --query 'Stacks[?StackName==`BATbern-development-Database`].Outputs'

# 3. Re-run sync script
./scripts/config/sync-backend-config.sh development
```

### Issue: Spring Boot can't connect to database

**Symptoms:** Connection refused or authentication errors

**Debug Steps:**
```bash
# 1. Check .env has correct values
grep DB_HOST .env

# 2. Test database connection
docker-compose exec api-gateway env | grep DB_

# 3. Check security groups allow your IP
# AWS Console → RDS → Security Groups

# 4. Verify credentials
aws secretsmanager get-secret-value \
  --secret-id BATbern-development-DBSecret-xxxxx \
  --profile batbern-dev
```

---

## Best Practices

### ✅ DO

- **Keep CDK configs as source of truth** - All infrastructure config in TypeScript
- **Use templates** - Maintain consistency across environments
- **Use ${PLACEHOLDER}** in application.yml - Never hardcode values
- **Validate before deploying** - Run validation script
- **Document new variables** - Update this guide when adding config

### ❌ DON'T

- **Don't hardcode values** in application.yml - Use environment variables
- **Don't commit .env files** - Contains sensitive credentials
- **Don't manually edit .env** - Always use sync script
- **Don't share credentials** - Use AWS Secrets Manager
- **Don't skip validation** - Catch issues early

---

## Parallel to Frontend Configuration

Both frontend and backend use runtime configuration, but with different mechanisms:

| Aspect | Frontend | Backend |
|--------|----------|---------|
| **Source of Truth** | Backend API endpoint | CDK + CloudFormation |
| **Consumer** | React App (browser) | Spring Boot (JVM) |
| **Loading Method** | `fetch('/api/v1/config')` | Environment variables |
| **Loading Time** | Runtime (page load) | Runtime (JVM startup) |
| **Single Build** | ✅ Yes (`dist/` folder) | ✅ Yes (JAR files) |
| **Template-Based** | ❌ No (config from API) | ✅ Yes (.env templates) |

**Key Similarity:** Both read configuration at runtime, enabling "build once, deploy everywhere."

---

## Configuration Variables Reference

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `APP_ENVIRONMENT` | Environment name | `development`, `staging`, `production` |
| `SPRING_PROFILES_ACTIVE` | Spring Boot profile | `local`, `staging`, `production` |
| `AWS_REGION` | AWS region | `eu-central-1` |
| `DB_HOST` | Database endpoint | `batbern-dev.xxxxx.rds.amazonaws.com` |
| `DB_PORT` | Database port | `5432` |
| `DB_NAME` | Database name | `batbern` |
| `DB_USER` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | (from Secrets Manager) |
| `REDIS_HOST` | Redis/ElastiCache endpoint | `redis` or AWS endpoint |
| `REDIS_PORT` | Redis port | `6379` |
| `COGNITO_USER_POOL_ID` | Cognito User Pool ID | `eu-central-1_xxxxx` |
| `COGNITO_CLIENT_ID` | Cognito App Client ID | `xxxxx` |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `LOG_LEVEL` | Logging level | `DEBUG` (dev), `INFO` (staging/prod) |
| `API_GATEWAY_PORT` | API Gateway port | `8080` |
| `ENABLE_COGNITO_AUTH` | Enable Cognito authentication | `true` |

---

## CI/CD Integration

### GitHub Actions

```yaml
jobs:
  deploy-staging:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          role-to-assume: arn:aws:iam::188701360969:role/GitHubActions
          aws-region: eu-central-1

      - name: Generate backend configuration
        run: ./scripts/config/sync-backend-config.sh staging

      - name: Validate configuration
        run: ./scripts/config/validate-backend-config.sh

      - name: Deploy to ECS
        run: |
          # ECS task definition uses environment variables from .env
          # Configuration is baked into task definition, not into JAR
```

---

## Resources

- [Configuration Migration Guide](./CONFIGURATION_MIGRATION.md)
- [Frontend Configuration Guide](../web-frontend/CONFIG.md)
- [CDK Configuration](../infrastructure/lib/config/)
- [Templates](../config/templates/)
- [Sync Scripts](../scripts/config/)

---

## Support

If you encounter issues:

1. Check this guide for troubleshooting steps
2. Validate configuration: `./scripts/config/validate-backend-config.sh`
3. Check AWS resources are deployed: `aws cloudformation list-stacks --profile batbern-dev`
4. Review generated .env file: `cat .env`
5. Check Docker logs: `docker-compose logs -f`

---

**Backend configuration follows the same "single source of truth" principle as frontend - just using environment variables instead of API endpoints!**
