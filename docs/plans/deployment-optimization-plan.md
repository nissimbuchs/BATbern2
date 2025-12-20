# Plan: Optimize Staging Deployment Workflow

## Problem Statement

The deploy-to-staging GitHub Actions workflow is taking too long (45-90 minutes) when infrastructure changes are detected, causing AWS token expiration issues (1-hour OIDC token validity).

## Current State Analysis

### Deployment Time Breakdown
- **Code-only changes**: 10-15 minutes (selective service deployment)
- **Infrastructure changes**: 45-90+ minutes (full stack deployment)
- **Critical bottlenecks**:
  1. CDK deployment: 5-15 min per stack (sequential due to dependencies)
  2. ECS stabilization: 5-10 min per service
  3. RDS snapshot backup: 3-10 min (when DB changes detected)

### Why Infrastructure Is Slow
1. **Sequential stack execution** - CloudFormation dependencies force serial deployment
2. **Tight coupling** - Infrastructure stacks include ECS service definitions with baked-in Docker images
3. **No granularity** - CDK synthesizes ALL stacks even if only one changed
4. **Cross-region operations** - DNS stack in us-east-1 referenced by eu-central-1 stacks
5. **Limited parallelization** - Only `--concurrency=3` available for microservices

### Database Migrations - Why In Services Not Infrastructure

**Answer**: Database migrations run on service startup via Flyway, NOT in infrastructure/GitHub Actions because:

1. **Security**: Services run inside VPC with RDS access; GitHub Actions cannot access private RDS
2. **Microservices independence**: Each service owns its schema evolution
3. **Deployment pattern**: Migrations execute when ECS tasks start in VPC
4. **Isolation**: One service's migration failure doesn't cascade to others

Each service has:
- `spring.flyway.enabled: true` in application.yml
- Migrations in `src/main/resources/db/migration/`
- Own Flyway schema history table (e.g., `flyway_schema_history_event_management`)

Infrastructure CDK stacks only provision RDS/VPC - they do NOT run migrations.

## Recommended Solution: Phased Optimization Approach

Based on comprehensive analysis by three specialized planning agents, I recommend a **three-phase approach** that delivers immediate value while building toward comprehensive optimization.

---

## Phase 1: Quick Wins (Week 1) - HIGHEST PRIORITY ⭐

**Effort**: 2-3 hours | **Time Savings**: 50-60% (30-40 min → 15-20 min) | **Risk**: LOW

These are low-risk, high-impact changes that can be implemented immediately:

### 1.1 Increase Microservices Concurrency
**File**: `infrastructure/package.json` (line 23)
```json
"deploy:staging:microservices": "... --concurrency=6"  // Change from 3 to 6
```
**Savings**: 5-8 minutes for microservices deployment

### 1.2 Direct Commit SHA for IMAGE_TAG
**File**: `.github/workflows/deploy-staging.yml` (lines 226-262)

Replace GitHub API lookup with:
```yaml
SHORT_SHA="${GITHUB_SHA:0:7}"
IMAGE_TAG="${SHORT_SHA}-staging.${GITHUB_RUN_NUMBER}"
```
**Savings**: 5-10 seconds, eliminates API call overhead

### 1.3 Mid-Deployment Re-Authentication
**File**: `.github/workflows/deploy-staging.yml` (add before line 354)
```yaml
- name: Re-authenticate before ECS stabilization
  uses: aws-actions/configure-aws-credentials@v4
  with:
    role-to-assume: arn:aws:iam::188701360969:role/batbern-staging-github-actions-role
    aws-region: ${{ env.AWS_REGION }}
```
**Benefit**: Prevents token expiration failures on long deployments

### 1.4 Smart RDS Snapshot Strategy
**File**: `.github/workflows/deploy-staging.yml` (lines 141-143)

Only snapshot on actual Flyway migrations:
```yaml
if echo "$CHANGED_FILES" | grep -qE "db/migration/V[0-9]+.*\.sql"; then
  echo "needs_database_backup=true" >> $GITHUB_OUTPUT
fi
```
**Savings**: 3-10 minutes when skipping unnecessary snapshots

### 1.5 Use Hotswap Mode for Code Changes
**File**: `.github/workflows/deploy-staging.yml` (line 264)

```yaml
DEPLOY_MODE="deploy:staging:selective"
if [ "${{ steps.changes.outputs.infrastructure }}" != "true" ]; then
  DEPLOY_MODE="deploy:staging:hotswap"  # Bypass CloudFormation
fi
npm run $DEPLOY_MODE -- $STACKS_TO_DEPLOY
```
**Savings**: 10-15 minutes for code-only deployments

---

## Phase 2: Layer-Based Infrastructure (Week 2-3) - MEDIUM PRIORITY

**Effort**: 4-6 hours | **Additional Savings**: 10-15 minutes | **Risk**: MEDIUM

Reorganize CDK stacks into deployment layers for better parallelization:

### 2.1 Add Layer-Based npm Scripts
**File**: `infrastructure/package.json` (add new scripts)

```json
"deploy:staging:layer0-foundation": "... BATbern-staging-CICD BATbern-staging-DNS BATbern-staging-Monitoring BATbern-staging-SES --concurrency=4",
"deploy:staging:layer1-infrastructure": "... BATbern-staging-Network BATbern-staging-Secrets BATbern-staging-EventBus --concurrency=3",
"deploy:staging:layer2-data": "... BATbern-staging-Database BATbern-staging-Storage BATbern-staging-Bastion --concurrency=2",
"deploy:staging:layer3-application": "... BATbern-staging-Cognito BATbern-staging-Cluster --concurrency=2",
"deploy:staging:layer4-services": "... [all 6 microservices] --concurrency=6",
"deploy:staging:layer5-gateway": "... BATbern-staging-ApiGateway BATbern-staging-Frontend --concurrency=2",
"deploy:staging:all-layers": "npm run deploy:staging:layer0-foundation && npm run deploy:staging:layer1-infrastructure && ..."
```

### 2.2 Update Workflow for Layer-Based Deployment
**File**: `.github/workflows/deploy-staging.yml` (lines 264-348)

Replace selective deployment logic with layer detection:
```yaml
if [ "${{ steps.changes.outputs.deploy_all }}" = "true" ]; then
  npm run deploy:staging:all-layers
elif [ -n "${{ steps.changes.outputs.changed_services }}" ]; then
  npm run deploy:staging:layer4-services  # Only microservices
elif [ "${{ steps.changes.outputs.frontend }}" = "true" ]; then
  npm run deploy:staging:layer5-gateway   # Only frontend/gateway
fi
```

**Benefits**:
- Foundation layer stacks deploy in parallel (concurrency=4)
- Services layer uses full concurrency (6 microservices in parallel)
- Independent layers can skip deployment if unchanged

---

## Phase 3: Fast-Path Code Deployments (Week 4) - FUTURE OPTIMIZATION

**Effort**: 8-12 hours | **Additional Savings**: 90-95% for code-only (45 min → 2-5 min) | **Risk**: MEDIUM-HIGH

Create a separate fast-path workflow that bypasses CloudFormation entirely for code-only changes:

### 3.1 Create Fast-Path Deployment Script
**File**: `scripts/ci/update-ecs-task.sh` (NEW)

```bash
#!/bin/bash
# Direct ECS task definition update bypassing CloudFormation
SERVICE_NAME=$1
IMAGE_TAG=$2

# Register new task definition with updated image
# Update ECS service to use new task definition
# Wait for service stability
```

### 3.2 Create Fast-Path Workflow
**File**: `.github/workflows/deploy-code-staging.yml` (NEW)

Triggered only when code changes detected (no Dockerfile, migrations, or infrastructure):
```yaml
- Detect code-only changes
- Build/test already completed in build.yml
- Update ECS task definitions directly
- Skip CloudFormation entirely
```

**Trade-offs**:
- ✅ Massive time savings (2-5 min vs 30-40 min)
- ⚠️ Risk of task definition drift from CDK
- ⚠️ Requires periodic full CDK deployments to resync
- ⚠️ More complex rollback procedures

---

## Why Database Migrations Are In Services (Not Infrastructure)

**Question**: "Why are DB migrations applied on service startup and not in infrastructure?"

**Answer**: This is an intentional architectural decision based on security, microservices independence, and deployment patterns:

### 1. Security Architecture
- **Services run inside VPC** with direct RDS access via security groups
- **GitHub Actions runs in public internet** - cannot access private RDS endpoints
- **Bastion host is development-only** - not available in production CI/CD

### 2. Microservices Independence
Each service owns its schema evolution:
- `services/event-management-service/src/main/resources/db/migration/` - Event schema
- `services/company-user-management-service/src/main/resources/db/migration/` - Company schema
- Each has independent Flyway schema history table

### 3. Deployment Pattern
```
GitHub Actions builds Docker image
  → Pushes to ECR
    → CloudFormation updates ECS task definition
      → ECS starts new task in VPC
        → Spring Boot starts
          → Flyway runs migrations automatically (spring.flyway.enabled: true)
            → JPA validates schema (hibernate.ddl-auto: validate)
              → Service becomes healthy
```

### 4. Configuration Evidence

**File**: `services/event-management-service/src/main/resources/application.yml`
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: validate  # Never auto-create, only validate against migrations
```

**File**: `infrastructure/lib/stacks/event-management-stack.ts` (line 53)
```typescript
JPA_DDL_AUTO: 'none'  // Delegate to Flyway, CDK doesn't touch database
```

This pattern ensures:
- No GitHub Actions database credentials needed (security)
- Service-specific migration isolation (independence)
- Automatic migration on service deployment (simplicity)
- Production parity in development/staging (consistency)

---

## Implementation Roadmap (USER PRIORITY)

### Priority 1: Quick Wins (Items 1.1, 1.3, 1.4, 1.5)
**Effort**: 1.5-2 hours | **Savings**: 15-25 min

**Files to modify**:
1. `infrastructure/package.json` (line 23) - Change `--concurrency=3` to `--concurrency=6`
2. `.github/workflows/deploy-staging.yml`:
   - Lines 141-143: Smart RDS snapshot (only on V*.sql migrations)
   - Add before line 354: Mid-deployment re-authentication
   - Line 264: Add hotswap mode for non-infrastructure changes

**Expected outcome**: Deployments reduced from 30-40 min to 15-20 min

### Priority 2: Fast-Path Code Deployments (Items 3.1, 3.2)
**Effort**: 6-8 hours | **Savings**: 90-95% for code-only (45 min → 2-5 min)

**⚠️ Higher Risk - Requires Careful Implementation**

This is the most complex optimization but delivers massive value for code-only deployments.

#### 3.1 Implementation: Direct ECS Update Script

**File**: `scripts/ci/update-ecs-task.sh` (NEW)

```bash
#!/bin/bash
set -e

# Usage: ./update-ecs-task.sh <cluster> <service> <image-tag>
CLUSTER=$1
SERVICE=$2
IMAGE_TAG=$3
REGION=${AWS_REGION:-eu-central-1}

echo "🚀 Fast-path deployment: $SERVICE with image tag $IMAGE_TAG"

# Get current task definition
CURRENT_TASK_DEF=$(aws ecs describe-services \
  --cluster $CLUSTER \
  --services $SERVICE \
  --region $REGION \
  --query 'services[0].taskDefinition' \
  --output text)

echo "📋 Current task definition: $CURRENT_TASK_DEF"

# Get task definition JSON and update image tag
TASK_DEF_JSON=$(aws ecs describe-task-definition \
  --task-definition $CURRENT_TASK_DEF \
  --region $REGION \
  --query 'taskDefinition')

# Extract ECR repository from current image
ECR_REPO=$(echo $TASK_DEF_JSON | jq -r '.containerDefinitions[0].image' | cut -d: -f1)

# Create new task definition with updated image
NEW_TASK_DEF=$(echo $TASK_DEF_JSON | jq \
  --arg IMAGE "$ECR_REPO:$IMAGE_TAG" \
  'del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities, .registeredAt, .registeredBy) |
   .containerDefinitions[0].image = $IMAGE')

# Register new task definition
NEW_TASK_ARN=$(echo $NEW_TASK_DEF | \
  aws ecs register-task-definition \
    --region $REGION \
    --cli-input-json file:///dev/stdin \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

echo "✅ Registered new task definition: $NEW_TASK_ARN"

# Update service to use new task definition
aws ecs update-service \
  --cluster $CLUSTER \
  --service $SERVICE \
  --task-definition $NEW_TASK_ARN \
  --region $REGION \
  --force-new-deployment \
  --query 'service.serviceName' \
  --output text

echo "⏳ Waiting for service to stabilize..."

# Wait for service stability (with timeout)
aws ecs wait services-stable \
  --cluster $CLUSTER \
  --services $SERVICE \
  --region $REGION

echo "🎉 Service $SERVICE updated successfully!"
```

#### 3.2 Implementation: Fast-Path Workflow

**File**: `.github/workflows/deploy-code-staging.yml` (NEW)

```yaml
name: Fast-Path Code Deployment (Staging)

on:
  workflow_call:
    inputs:
      changed_services:
        description: 'Comma-separated list of changed services'
        required: true
        type: string
      image_tag:
        description: 'Docker image tag to deploy'
        required: true
        type: string

permissions:
  id-token: write
  contents: read

env:
  AWS_REGION: eu-central-1
  CLUSTER_NAME: batbern-staging

jobs:
  fast-path-deploy:
    name: Fast-Path ECS Deployment
    runs-on: ubuntu-latest
    timeout-minutes: 15  # Much faster than CloudFormation

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::188701360969:role/batbern-staging-github-actions-role
          aws-region: ${{ env.AWS_REGION }}

      - name: Deploy services via fast-path
        run: |
          IFS=',' read -ra SERVICES <<< "${{ inputs.changed_services }}"

          for SERVICE in "${SERVICES[@]}"; do
            echo "🚀 Fast-path deploying: $SERVICE"

            # Map service name to ECS service name
            case $SERVICE in
              event-management)
                ECS_SERVICE="BATbern-staging-EventManagement-Service*"
                ;;
              company-user-management)
                ECS_SERVICE="BATbern-staging-CompanyManagement-Service*"
                ;;
              speaker-coordination)
                ECS_SERVICE="BATbern-staging-SpeakerCoordination-Service*"
                ;;
              partner-coordination)
                ECS_SERVICE="BATbern-staging-PartnerCoordination-Service*"
                ;;
              attendee-experience)
                ECS_SERVICE="BATbern-staging-AttendeeExperience-Service*"
                ;;
              api-gateway)
                ECS_SERVICE="BATbern-staging-ApiGatewayService-Service*"
                ;;
              *)
                echo "::warning::Unknown service: $SERVICE, skipping"
                continue
                ;;
            esac

            # Find exact service name (handles CDK-generated suffixes)
            FULL_SERVICE=$(aws ecs list-services \
              --cluster ${{ env.CLUSTER_NAME }} \
              --query "serviceArns[?contains(@, '$ECS_SERVICE')]" \
              --output text | xargs -n1 basename)

            if [ -z "$FULL_SERVICE" ]; then
              echo "::error::Service not found: $ECS_SERVICE"
              exit 1
            fi

            # Execute fast-path update
            ./scripts/ci/update-ecs-task.sh \
              ${{ env.CLUSTER_NAME }} \
              $FULL_SERVICE \
              ${{ inputs.image_tag }}
          done

      - name: Run smoke tests
        run: |
          # Basic health check
          echo "✅ Fast-path deployment complete, running smoke tests..."
          # Add your smoke test commands here
```

#### Integration with Main Workflow

**File**: `.github/workflows/deploy-staging.yml` (add after change detection, before line 220)

```yaml
      - name: Determine deployment strategy
        id: deployment-strategy
        run: |
          # Check if this qualifies for fast-path deployment
          CODE_ONLY="false"

          # Fast-path conditions:
          # 1. No infrastructure changes
          # 2. No Dockerfile changes
          # 3. No database migrations
          # 4. Only service code changes

          if [ "${{ steps.changes.outputs.infrastructure }}" != "true" ] && \
             ! echo "$CHANGED_FILES" | grep -qE "Dockerfile|db/migration/" && \
             [ -n "${{ steps.changes.outputs.changed_services }}" ]; then
            CODE_ONLY="true"
            echo "🚀 Fast-path eligible: Code-only changes detected"
          else
            echo "📦 Standard deployment required"
          fi

          echo "code_only=$CODE_ONLY" >> $GITHUB_OUTPUT

      # Call fast-path workflow if eligible
      - name: Fast-path code deployment
        if: steps.deployment-strategy.outputs.code_only == 'true'
        uses: ./.github/workflows/deploy-code-staging.yml
        with:
          changed_services: ${{ steps.changes.outputs.changed_services }}
          image_tag: ${{ steps.image-tag.outputs.IMAGE_TAG }}

      # Standard CDK deployment (skip if fast-path used)
      - name: Deploy infrastructure to staging
        if: steps.deployment-strategy.outputs.code_only != 'true'
        working-directory: ./infrastructure
        # ... existing deployment logic ...
```

### Expected Outcomes

**After Priority 1 (Quick Wins)**:
- Infrastructure deployments: 15-20 min (down from 30-40 min)
- Microservices deployments: 8-12 min (down from 15-25 min)
- Token expiration: Eliminated

**After Priority 2 (Fast-Path)**:
- Code-only deployments: 2-5 min (down from 30-40 min) ⭐ **90-95% faster**
- Infrastructure deployments: Still 15-20 min (fast-path bypasses)
- Developer productivity: Massive improvement for iterative development

---

## Critical Files Summary

### High Priority (Phase 1)
- `.github/workflows/deploy-staging.yml` (lines 23, 141-143, 226-262, 264, 354)
- `infrastructure/package.json` (line 23)

### Medium Priority (Phase 2)
- `infrastructure/package.json` (add 7 new layer scripts)
- `.github/workflows/deploy-staging.yml` (lines 264-348 refactor)
- `infrastructure/CLAUDE.md` (documentation)

### Low Priority (Phase 3)
- `scripts/ci/update-ecs-task.sh` (NEW)
- `.github/workflows/deploy-code-staging.yml` (NEW)
- `scripts/ci/detect-changed-stacks.sh` (NEW)

---

## Risk Mitigation

### Phase 1 Risks
- **Concurrency limits**: Test concurrency=6, may need to reduce if AWS throttles
- **Hotswap mode**: Only use in staging, not production
- **Snapshot strategy**: Add manual override for safety

### Phase 2 Risks
- **Layer dependencies**: Validate layer definitions match actual CDK dependencies
- **Script complexity**: Test each layer independently before using all-layers

### Phase 3 Risks (Fast-Path - USER PRIORITY)
- **Task definition drift**: CDK and ECS task definitions may diverge over time
  - **Mitigation**: Weekly full CDK deployment (automated schedule)
  - **Detection**: Add `cdk diff` check in weekly CI job
  - **Recovery**: Full CDK deployment resyncs everything

- **Rollback complexity**: Fast-path bypasses CloudFormation change sets
  - **Mitigation**: Create rollback script using previous task definition ARN
  - **Testing**: Practice rollback in staging before production
  - **Automation**: Add rollback command to fast-path workflow

- **False positives**: Code changes misclassified as infrastructure-free
  - **Mitigation**: Conservative detection (whitelist approach)
  - **Override**: Manual workflow_dispatch for force-CDK mode
  - **Validation**: Comprehensive smoke tests after fast-path

- **Environment variable drift**: Fast-path copies env vars from existing task def
  - **Mitigation**: CDK is source of truth, fast-path inherits existing config
  - **Detection**: Add env var comparison in weekly full deployment
  - **Limitation**: Cannot change env vars via fast-path (requires CDK)

---

## Testing Plan for Fast-Path Implementation

### Stage 1: Script Testing (Local/Bastion)
1. Test `update-ecs-task.sh` against staging cluster manually:
   ```bash
   AWS_PROFILE=batbern-staging ./scripts/ci/update-ecs-task.sh \
     batbern-staging \
     BATbern-staging-EventManagement-ServiceXXXX \
     abc1234-staging.567
   ```
2. Verify task definition registration
3. Verify service update
4. Verify rollback using previous task definition ARN

### Stage 2: Workflow Integration Testing
1. Create test PR with code-only change (single service)
2. Verify fast-path detection logic triggers
3. Monitor deployment time (should be 2-5 min)
4. Validate service health after deployment
5. Run full test suite

### Stage 3: Multi-Service Testing
1. Create test PR with changes to multiple services
2. Verify parallel fast-path deployments
3. Check for race conditions or conflicts
4. Validate all services healthy

### Stage 4: Negative Testing
1. Test Dockerfile change → should NOT use fast-path
2. Test migration file → should NOT use fast-path
3. Test infrastructure change → should NOT use fast-path
4. Validate fallback to standard CDK deployment

### Stage 5: Production Readiness
1. Document rollback procedures
2. Create runbook for fast-path failures
3. Set up monitoring/alerting for fast-path deployments
4. Train team on when to use/avoid fast-path
